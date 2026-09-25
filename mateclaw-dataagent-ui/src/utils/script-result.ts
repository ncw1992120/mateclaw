/**
 * 统一脚本结果解析与组件适配。
 *
 * DataAgent `/executions/{id}/result` 返回统一 envelope（table/scalar/message），
 * 前端只消费本模块的解析结果，不再对任意对象做猜测式渲染。
 */

import type { ChartType, InsightComponentType } from '@/types'
import { resolveOutputSpec } from './component-output-spec'

export type ScriptDataType = 'string' | 'number' | 'boolean' | 'date' | 'datetime'

export interface ScriptResultColumn {
  name: string
  title: string
  dataType: ScriptDataType
  nullable: boolean
}

export interface ScriptResultMeta {
  rowCount: number
  truncated: boolean
  sourceInputs?: string[]
}

export interface ScriptTableEnvelope {
  schemaVersion: '1.0'
  kind: 'table'
  data: { columns: ScriptResultColumn[]; rows: Record<string, unknown>[] }
  meta: ScriptResultMeta
}

export interface ScriptScalarEnvelope {
  schemaVersion: '1.0'
  kind: 'scalar'
  data: { value: string | number | boolean; label?: string; dataType: ScriptDataType }
  meta: ScriptResultMeta
}

export interface ScriptMessageEnvelope {
  schemaVersion: '1.0'
  kind: 'message'
  data: { level: 'info' | 'warning'; message: string }
  meta: ScriptResultMeta
}

export type ScriptResultEnvelope = ScriptTableEnvelope | ScriptScalarEnvelope | ScriptMessageEnvelope

export interface ResultSchema {
  fingerprint: string
  kind: ScriptResultEnvelope['kind']
  columns: ScriptResultColumn[]
  rowCount: number
}

function fnv1a(value: string): string {
  let hash = 2166136261
  for (const char of value) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}

/** 只根据结果类型和字段契约计算指纹，不让行数变化导致配置失效。 */
export function fingerprintResultSchema(schema: Omit<ResultSchema, 'fingerprint'> | ResultSchema): string {
  return fnv1a(JSON.stringify({
    kind: schema.kind,
    columns: schema.columns.map(({ name, title, dataType, nullable }) => ({ name, title, dataType, nullable })),
  }))
}

/** 从已解析且已通过基础 envelope 校验的结果中提取可持久化 Schema。 */
export function extractResultSchema(envelope: ScriptResultEnvelope): ResultSchema {
  const columns = envelope.kind === 'table'
    ? envelope.data.columns.map((column) => ({
      ...column,
      nullable: column.nullable || envelope.data.rows.length === 0 || envelope.data.rows.some((row) => row[column.name] == null),
    }))
    : []
  const schema = {
    kind: envelope.kind,
    columns,
    rowCount: envelope.meta.rowCount,
  } satisfies Omit<ResultSchema, 'fingerprint'>
  return { ...schema, fingerprint: fingerprintResultSchema(schema) }
}

/** 把数据集直出行归一成与 Python 结果相同的强类型 table envelope，供统一契约校验。 */
export function tableEnvelopeFromRows(rows: Record<string, unknown>[]): ScriptTableEnvelope {
  const sample = rows[0] ?? {}
  const columns = Object.keys(sample).map((name): ScriptResultColumn => {
    const value = sample[name]
    const dataType: ScriptDataType = typeof value === 'number'
      ? 'number'
      : typeof value === 'boolean'
        ? 'boolean'
        : value instanceof Date
          ? 'datetime'
          : typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
            ? 'date'
            : 'string'
    return { name, title: name, dataType, nullable: rows.some((row) => row[name] == null) }
  })
  return {
    schemaVersion: '1.0',
    kind: 'table',
    data: { columns, rows },
    meta: { rowCount: rows.length, truncated: false },
  }
}

/** DataAgent 执行结果 API 响应 */
export interface ScriptExecutionResultResponse {
  executionId: string
  status: string
  envelope: unknown
  inline: boolean
  outputRef?: unknown
}

/** 执行失败的稳定状态与可读信息 */
export interface ScriptResultError {
  status?: string
  path?: string
  expected?: string
  actual?: string
  suggestion?: string
  message?: string
}

const DATA_TYPES: readonly string[] = ['string', 'number', 'boolean', 'date', 'datetime']

/** 严格解析 envelope；未知版本/kind/列类型抛出带路径的前端契约错误。 */
export function parseScriptResultEnvelope(value: unknown): ScriptResultEnvelope {
  const fail = (path: string, expected: string, actual: string, suggestion: string): never => {
    throw new Error(`输出契约错误：${path} 期望 ${expected}，实际 ${actual}；${suggestion}`)
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return fail('result', 'object', Array.isArray(value) ? 'array' : typeof value, '结果必须是标准信封对象')
  }
  const raw = value as Record<string, unknown>
  if (raw.schemaVersion !== '1.0') {
    return fail('result.schemaVersion', '1.0', String(raw.schemaVersion ?? 'missing'), '结果 schemaVersion 必须是 1.0')
  }
  const kind = raw.kind
  if (kind !== 'table' && kind !== 'scalar' && kind !== 'message') {
    return fail('result.kind', 'table|scalar|message', String(kind ?? 'missing'), '结果类型只允许 table、scalar 或 message')
  }
  const meta = raw.meta as ScriptResultMeta | undefined
  if (!meta || typeof meta.rowCount !== 'number') {
    return fail('result.meta', 'object(rowCount)', typeof meta, '信封必须携带 meta.rowCount')
  }
  const data = raw.data as Record<string, unknown> | null
  if (!data || typeof data !== 'object') {
    return fail('result.data', 'object', typeof data, '信封必须携带 data')
  }

  if (kind === 'message') {
    const level = (data as ScriptMessageEnvelope['data']).level
    const message = (data as ScriptMessageEnvelope['data']).message
    if (level !== 'info' && level !== 'warning') return fail('result.data.level', 'info|warning', String(level), '消息级别不合法')
    if (typeof message !== 'string' || !message) return fail('result.data.message', 'string', typeof message, '消息内容不能为空')
    return raw as unknown as ScriptMessageEnvelope
  }

  if (kind === 'scalar') {
    const scalar = data as ScriptScalarEnvelope['data']
    if (!DATA_TYPES.includes(scalar.dataType)) {
      return fail('result.data.dataType', 'string|number|boolean|date|datetime', String(scalar.dataType), 'scalar dataType 不受支持')
    }
    if (typeof scalar.value === 'object' && scalar.value !== null) {
      return fail('result.data.value', '基础类型', typeof scalar.value, 'scalar 值必须是基础类型')
    }
    return raw as unknown as ScriptScalarEnvelope
  }

  // table
  const columns = data.columns
  if (!Array.isArray(columns)) return fail('result.data.columns', 'array', typeof columns, 'table 结果必须声明列')
  const names = new Set<string>()
  for (const column of columns as ScriptResultColumn[]) {
    if (!column || typeof column.name !== 'string' || !column.name) {
      return fail('result.data.columns.name', 'string', typeof column?.name, '列名不能为空')
    }
    if (names.has(column.name)) {
      return fail('result.data.columns', '唯一列名', column.name, '列名必须唯一')
    }
    names.add(column.name)
    if (!DATA_TYPES.includes(column.dataType)) {
      return fail('result.data.columns.dataType', 'string|number|boolean|date|datetime', String(column.dataType), `列 ${column.name} 的 dataType 不受支持`)
    }
  }
  const rows = data.rows
  if (!Array.isArray(rows)) return fail('result.data.rows', 'array', typeof rows, 'table 结果必须带行数据')
  return raw as unknown as ScriptTableEnvelope
}

/** 执行失败 → 用户可读信息；OUTPUT_CONTRACT_ERROR 展示阶段、路径与修复建议。 */
export function formatScriptResultError(error: ScriptResultError | string | null | undefined): string {
  if (!error) return '执行失败'
  if (typeof error === 'string') return error
  if (error.status === 'OUTPUT_CONTRACT_ERROR') {
    const parts = [
      '数据格式不匹配',
      error.path ? `路径 ${error.path}` : '',
      error.expected && error.actual ? `期望 ${error.expected}，实际 ${error.actual}` : '',
      error.suggestion ? `建议：${error.suggestion}` : '',
    ].filter(Boolean)
    return `${parts.join('；')}；请参考样例数据格式或使用 Python 脚本继续处理。`
  }
  if (error.message) return error.message
  const statusText: Record<string, string> = {
    TIMEOUT: '执行超时',
    CANCELLED: '执行已取消',
    OUTPUT_LIMIT: '输出超过限制',
    RESULT_LIMIT: '结果超过限制',
    FAILED: '执行失败',
  }
  return (error.status && statusText[error.status]) || '执行失败'
}

/** 组件适配结果的统一形态：明确 data/empty/message/error 状态，不做猜测渲染。 */
export interface EnvelopeComponentResult {
  state: 'data' | 'empty' | 'message' | 'error'
  message?: string
  rows: Record<string, unknown>[]
  columns: ScriptResultColumn[]
  /** 脚本字段显式标题；应用展示时由字段注册表标签覆盖。 */
  fieldLabels?: Record<string, string>
  /** table 组件数据 */
  table?: { columns: string[]; rows: string[][] }
  /** 图表 option（按组件配置的维度/指标映射生成） */
  option?: Record<string, unknown>
  /** KPI 取值 */
  value?: string | number | boolean
  kpiList?: { name: string; value: string | number | boolean }[]
}

export interface EnvelopeComponentSpec {
  id?: string
  type?: string
  chartType?: string
  config?: {
    dimensionField?: string
    metricFields?: string[]
    valueField?: string
    aggregation?: 'sum' | 'avg' | 'max' | 'min' | 'count'
  } & Record<string, unknown>
}

function isNumericColumn(column: ScriptResultColumn): boolean {
  return column.dataType === 'number'
}

function aggregate(values: number[], aggregation: NonNullable<EnvelopeComponentSpec['config']>['aggregation']): number {
  switch (aggregation) {
    case 'avg': return values.length ? values.reduce((a, b) => a + b, 0) / values.length : 0
    case 'max': return values.length ? Math.max(...values) : 0
    case 'min': return values.length ? Math.min(...values) : 0
    case 'count': return values.length
    default: return values.reduce((a, b) => a + b, 0)
  }
}

/**
 * envelope → 组件数据。
 * - message 显示说明文本，不伪造成表格行；
 * - 空 table 是合法空结果（state=empty）；
 * - 图表按组件保存的维度/指标映射生成 option，不猜测列；
 * - KPI 接受 scalar，或按配置从 table 聚合（无 valueField 时取最后一行首列，与既有口径一致）。
 */
export function resultEnvelopeToComponentData(
  component: EnvelopeComponentSpec,
  envelope: ScriptResultEnvelope,
): EnvelopeComponentResult {
  // message 对所有组件都是合法的「说明文本」结果，不走规范校验
  if (envelope.kind === 'message') {
    return { state: 'message', message: envelope.data.message, rows: [], columns: [] }
  }

  const spec = resolveOutputSpec(component.type as InsightComponentType, component.chartType as ChartType | undefined)
  if (!spec) {
    // 非脚本渲染组件（filter / aiAnalysis / combination 等）：无规范可套，原样空态
    return { state: 'empty', rows: [], columns: [] }
  }

  if (envelope.kind === 'scalar') {
    return { state: 'data', rows: [], columns: [], value: envelope.data.value, kpiList: [{ name: '值', value: envelope.data.value }] }
  }

  const columns = envelope.data.columns as ScriptResultColumn[]
  const fieldLabels = Object.fromEntries(
    columns.filter((column) => column.title?.trim()).map((column) => [column.name, column.title.trim()]),
  )
  const rows = envelope.data.rows
  if (rows.length === 0) {
    return { state: 'empty', rows: [], columns, fieldLabels }
  }

  if (component.type === 'chart') {
    if (spec.family === 'chartNameValue') {
      // 饼图/漏斗/仪表盘：名称列 + 数值列（单列时该列直接作为值）
      const nameField = component.config?.dimensionField
        || columns.find((column) => column.dataType !== 'number')?.name
        || columns[0]?.name
      const valueField = (component.config?.metricFields && component.config.metricFields[0])
        || columns.find((column) => column.dataType === 'number')?.name
        || columns[1]?.name
        || columns[0]?.name
      const data = rows.map((row) => ({ name: String(row[nameField] ?? ''), value: Number(row[valueField] ?? 0) || 0 }))
      const seriesType = component.chartType === 'funnel' ? 'funnel' : component.chartType === 'gauge' ? 'gauge' : 'pie'
      const series: Record<string, unknown> = { type: seriesType, data }
      if (seriesType === 'gauge') {
        const max = Math.max(1, ...data.map((item) => Number(item.value) || 0))
        series.min = 0
        series.max = Math.ceil(max * 1.2)
      }
      return { state: 'data', rows, columns, fieldLabels, option: { tooltip: { trigger: 'item' }, series: [series] } }
    }

    const config = component.config ?? {}
    const dimensionField = config.dimensionField || columns[0]?.name
    const numericColumns = columns.filter(isNumericColumn).map((column) => column.name)
    const metricFields = (config.metricFields?.length ? config.metricFields : numericColumns)
      .filter((field) => field !== dimensionField)
    const chartType = component.chartType || 'line'
    return {
      state: 'data',
      rows,
      columns,
      fieldLabels,
      option: {
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: rows.map((row) => String(row[dimensionField] ?? '')) },
        yAxis: { type: 'value' },
        series: metricFields.map((field) => ({ name: field, type: chartType, data: rows.map((row) => row[field]) })),
      },
    }
  }

  if (component.type === 'kpi') {
    const config = component.config ?? {}
    const last = rows[rows.length - 1] ?? {}
    let value: string | number | boolean
    if (config.valueField && config.aggregation) {
      const values = rows
        .map((row) => Number(row[config.valueField as string]))
        .filter((number) => Number.isFinite(number))
      value = aggregate(values, config.aggregation)
    } else if (config.valueField) {
      value = (last[config.valueField as string] ?? '') as string | number | boolean
    } else {
      value = (last[columns[0]?.name] ?? '') as string | number | boolean
    }
    return { state: 'data', rows, columns, fieldLabels, value, kpiList: [{ name: String(config.valueField || columns[0]?.name || '值'), value }] }
  }

  // table：按列顺序输出字符串化表格（与既有 DataTableWidget 渲染契约一致）
  const names = columns.map((column) => column.name)
  return {
    state: 'data',
    rows,
    columns,
    fieldLabels,
    table: { columns: names, rows: rows.map((row) => names.map((name) => formatCell(row[name]))) },
  }
}

function formatCell(value: unknown): string {
  if (value == null) return ''
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
