/**
 * 参数自动提取：从数据集的「定义」（SQL / 脚本 / 接口 / 文件 / 维度）中认出可用的参数，
 * 供「查看数据」工作台中间那块直接渲染成筛选项。
 *
 * 这是 Hue 式工作台的核心能力 —— 筛选项不再靠用户手工配「字段 + 操作符 + 值」，
 * 而是从定义里解析出来。设计约定见 docs/策略解读/查看数据工作台设计.md。
 *
 * 提取出的参数只是「有哪些、什么类型」；**值**由用户在工作台里填，随查询下推。
 */
import type { DashboardScriptParameter } from '@/types'

/** 参数的类型枚举与后端脚本参数保持一致，便于复用同一套求值逻辑 */
export type ParameterType = DashboardScriptParameter['type']

/** 参数是从哪认出来的 —— UI 上用于向用户解释「这个筛选项哪来的」 */
export type ParameterSource = 'sql' | 'api' | 'script' | 'dimension' | 'file' | 'declared'

/** 自动提取出来的参数项 */
export interface ExtractedParameter {
  name: string
  type: ParameterType
  source: ParameterSource
  /** 必填：SQL 绑定占位符不填就跑不了；维度筛选可留空 */
  required: boolean
  defaultValue?: unknown
  options?: string[]
}

/** 合法标识符（参数名） */
const IDENT = '[A-Za-z_][A-Za-z0-9_]*'

/**
 * SQL 里的 `:param` 绑定占位符（项目约定：只读 SELECT / WITH，参数用绑定 :param）。
 *
 * 用 lookbehind 排除 `::` 类型转换（PostgreSQL 的 `col::text`）：
 * 第一个冒号后面还是冒号、不匹配标识符；第二个冒号被 `(?<!:)` 挡掉。
 * 时间字面量（'2026-01-01 10:30:00'）里的 `:30` 以数字开头，也不会命中标识符规则。
 */
export function extractSqlParameters(sql: string | undefined | null): ExtractedParameter[] {
  if (!sql || !sql.trim()) return []
  const names = collectMatches(sql, new RegExp(`(?<!:):(${IDENT})`, 'g'))
  return names.map((name) => ({
    name,
    type: inferType(name),
    source: 'sql' as const,
    required: true,
  }))
}

/**
 * 接口配置里的占位符：支持 `{{ name }}` 与 `:name` 两种写法。
 *
 * `:name` 在 URL 里可能与端口冲突（http://host:8080），但端口是数字、
 * 不满足标识符首字符规则，所以不会误提取。
 */
export function extractApiParameters(api: {
  path?: string
  headers?: string
  params?: string
}): ExtractedParameter[] {
  const text = [api?.path, api?.headers, api?.params].filter(Boolean).join('\n')
  if (!text.trim()) return []
  const names = new Set<string>([
    ...collectMatches(text, /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}/g),
    ...collectMatches(text, new RegExp(`(?<!:):(${IDENT})`, 'g')),
  ])
  return [...names].map((name) => ({
    name,
    type: inferType(name),
    source: 'api' as const,
    required: false,
  }))
}

/**
 * Python 脚本里引用的参数：`parameters['x']` / `params["x"]` / `parameters.get('x')`。
 * 系统生成区的 `别名 = inputs["别名"]` 是数据集输入，不是参数，不参与提取。
 */
export function extractScriptParameters(script: string | undefined | null): ExtractedParameter[] {
  if (!script || !script.trim()) return []
  const re = new RegExp(
    `(?:parameters|params)\\s*(?:\\[\\s*['"](${IDENT})['"]\\s*\\]|\\.get\\s*\\(\\s*['"](${IDENT})['"])`,
    'g',
  )
  const names = new Set<string>()
  let match: RegExpExecArray | null
  while ((match = re.exec(script)) !== null) {
    names.add(match[1] || match[2])
  }
  return [...names].map((name) => ({
    name,
    type: inferType(name),
    source: 'script' as const,
    required: false,
  }))
}

/**
 * 维度即筛选项：Aloudata / 指标视图没有 SQL 可解析，
 * 就用已选的维度作为筛选项（维度才需要过滤，指标是聚合结果）。
 */
export function extractDimensionParameters(dimensions: string[] | undefined | null): ExtractedParameter[] {
  return (dimensions ?? []).filter(Boolean).map((name) => ({
    name,
    // 维度筛选通常可多选，用 string[] 让工作台渲染成多选控件
    type: 'string[]' as const,
    source: 'dimension' as const,
    required: false,
  }))
}

/** 文件类型没有占位符，用固定的解析选项（工作表 / 编码 / 分隔符 / 表头行） */
export function extractFileParameters(): ExtractedParameter[] {
  return [
    { name: 'sheet', type: 'string', source: 'file', required: false },
    {
      name: 'encoding',
      type: 'enum',
      source: 'file',
      required: false,
      defaultValue: 'UTF-8',
      options: ['UTF-8', 'GBK', 'ISO-8859-1'],
    },
    { name: 'delimiter', type: 'string', source: 'file', required: false, defaultValue: ',' },
    { name: 'headerRow', type: 'number', source: 'file', required: false, defaultValue: 1 },
  ]
}

/** 已有的人工声明参数（DashboardScriptParameter）转成统一形状 */
export function fromDeclaredParameters(
  definitions: DashboardScriptParameter[] | undefined | null,
): ExtractedParameter[] {
  return (definitions ?? []).map((definition) => ({
    name: definition.name,
    type: definition.type,
    source: 'declared' as const,
    required: Boolean(definition.required),
    defaultValue: definition.defaultValue,
    options: definition.options,
  }))
}

/**
 * 合并多来源参数，按名称去重。
 * 顺序决定优先级：靠前的保留，因此**人工声明 > 自动提取** ——
 * 用户显式定过的类型/默认值不该被自动推断覆盖。
 */
export function mergeParameters(...groups: ExtractedParameter[][]): ExtractedParameter[] {
  const merged = new Map<string, ExtractedParameter>()
  for (const group of groups) {
    for (const parameter of group) {
      if (!parameter?.name) continue
      const existing = merged.get(parameter.name)
      if (existing) {
        // 同名时补齐缺失的元信息，但不覆盖已有的类型与默认值
        merged.set(parameter.name, {
          ...parameter,
          type: existing.type ?? parameter.type,
          defaultValue: existing.defaultValue ?? parameter.defaultValue,
          options: existing.options ?? parameter.options,
          required: existing.required || parameter.required,
        })
        continue
      }
      merged.set(parameter.name, parameter)
    }
  }
  return [...merged.values()]
}

/** 按名称为参数推断类型：够用即可，用户可在工作台里改 */
function inferType(name: string): ParameterType {
  const normalized = name.toLowerCase()
  if (normalized.includes('date') || normalized.includes('time')) {
    const isRange = /start|end|begin|from|to|range/.test(normalized)
    return isRange ? 'date_range' : 'date'
  }
  // 计数/分页类用数字；ID 不在此列 —— 业务 ID 多为字符串标识符，按数字推断反而别扭
  if (/^(limit|size|count|offset|page|top|num|days)$/.test(normalized) || /(^|_)(cnt|num)$/.test(normalized)) {
    return 'number'
  }
  // 复数 / 明显的列表命名 → 多选
  if (normalized.endsWith('s') || /list|ids|types|codes/.test(normalized)) {
    return 'string[]'
  }
  return 'string'
}

/** 收集正则第 1 捕获组的全部匹配（去重、保序） */
function collectMatches(text: string, pattern: RegExp): string[] {
  const names = new Set<string>()
  let match: RegExpExecArray | null
  const re = new RegExp(pattern.source, pattern.flags)
  while ((match = re.exec(text)) !== null) {
    if (match[1]) names.add(match[1])
  }
  return [...names]
}
