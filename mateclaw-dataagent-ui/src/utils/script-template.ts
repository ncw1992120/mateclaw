import type { DashboardDatasetInput, DashboardScriptParameter, DashboardScriptFilterBinding } from '@/types'

export const SYSTEM_SCRIPT_START = '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） ====='
export const SYSTEM_SCRIPT_END = '# ===== 系统生成区域结束 ====='
export const USER_SCRIPT_START = '# ===== 用户处理区域：Join、合并、计算和业务规则 ====='
export const PYTHON_SAMPLE_START = '# ===== 自动更新的造数示例 ====='
export const PYTHON_SAMPLE_END = '# ===== 自动更新的造数示例结束 ====='

function sampleCommentLines(
  inputs: DashboardDatasetInput[],
  samplesByInputName: Record<string, Record<string, unknown>[]>,
): string[] {
  return inputs.flatMap((input) => {
    const rows = samplesByInputName[input.inputName] ?? []
    return rows.length
      ? [`# ${input.inputName} = pl.DataFrame(json.loads(${JSON.stringify(JSON.stringify(rows.slice(0, 2)))}))`]
      : [`# ${input.inputName}：尚未获取真实查询结果行，不生成 schema 造数；请检查上游数据集查询。`]
  })
}

function sampleCommentBlock(
  inputs: DashboardDatasetInput[],
  samplesByInputName: Record<string, Record<string, unknown>[]>,
): string[] {
  if (!inputs.length) return []
  return [PYTHON_SAMPLE_START, ...sampleCommentLines(inputs, samplesByInputName), PYTHON_SAMPLE_END]
}

/**
 * 生成只读的系统区域（实施计划任务 6 契约）：
 * - 唯一标准读取方式是 `datasets.input(input_name="...").to_polars()`；
 * - 返回的 DatasetInput 已经是查询计划处理后的输入（页面筛选/排序/分页在读取前应用），
 *   系统区不再生成 `datasets.read`、`columns=[]`、`datasets.params` 或任何筛选/排序/分页拼接；
 * - parameters/bindings 参数仅为兼容旧调用签名保留，不再参与生成。
 */
export function buildSystemScript(
  inputs: DashboardDatasetInput[],
  _parameters: DashboardScriptParameter[] = [],
  _bindings: DashboardScriptFilterBinding[] = [],
  outputContract?: { kind: string; example: string; fieldRules: { minColumns: number; minDimensionColumns: number; minNumericColumns: number } },
  samplesByInputName: Record<string, Record<string, unknown>[]> = {},
  queryParameters: Array<{ field: string; title: string; parameterName: string; operators: string[] }> = [],
  includeDataExamples = true,
): string {
  const validInputs = inputs.filter(input => input.datasetId && input.inputName)
  const lines = [
    SYSTEM_SCRIPT_START,
    '# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。',
  ]
  if (outputContract) {
    lines.push(
      '',
      `# 最终输出契约：kind=${outputContract.kind}；最少列=${outputContract.fieldRules.minColumns}；`
        + `维度列=${outputContract.fieldRules.minDimensionColumns}；数值列=${outputContract.fieldRules.minNumericColumns}`,
      '# Python 代码最后的 result 必须能被 Runner 包装为以下 JSON 信封：',
      ...outputContract.example.split('\n').map(line => `# ${line}`),
    )
  }
  if (queryParameters.length) {
    lines.push('', '# 查询参数参考：以下参数由「查看数据」查询配置应用于 Python 输出结果，不会作为 Python 变量注入。')
    queryParameters.forEach((parameter) => {
      lines.push(`# 参数 ${parameter.parameterName}：筛选字段「${parameter.title}」(${parameter.field})，可用操作符 ${parameter.operators.join('、') || '未指定'}`)
    })
  }

  if (validInputs.length && includeDataExamples) {
    lines.push(
      '',
      '# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。',
      '# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。',
      '# import json',
      '# import polars as pl',
      ...sampleCommentBlock(validInputs, samplesByInputName),
    )
  }

  validInputs.forEach((input) => {
    lines.push(
      '',
      `if ${JSON.stringify(input.inputName)} not in locals():`,
      `    ${input.inputName} = datasets.input(`,
      `        input_name=${JSON.stringify(input.inputName)},`,
      '    ).to_polars()',
    )
  })
  if (!validInputs.length) {
    lines.push('', '# 当前没有已绑定的数据集输入。')
  }
  lines.push('', SYSTEM_SCRIPT_END)
  return lines.join('\n')
}

/** 只刷新编辑器系统区域中的查询样例，保留用户脚本及其余手动编辑内容。 */
export function refreshPythonEditorSamples(
  document: string,
  inputs: DashboardDatasetInput[],
  samplesByInputName: Record<string, Record<string, unknown>[]>,
): string {
  const userMarker = '# ===== 自定义处理代码（可编辑） ====='
  const userMarkerIndex = document.indexOf(userMarker)
  const systemEnd = userMarkerIndex >= 0 ? userMarkerIndex : document.length
  let system = document.slice(0, systemEnd)
  const start = system.indexOf(PYTHON_SAMPLE_START)
  const end = system.indexOf(PYTHON_SAMPLE_END)
  if (start >= 0 && end >= start) {
    system = `${system.slice(0, start)}${system.slice(end + PYTHON_SAMPLE_END.length)}`
  }
  // 兼容此前保存的、没有样例区域标记的脚本：去除旧字段 JSON 与已删除输入的占位行。
  system = system.split('\n').filter((line) =>
    !/^#\s*[A-Za-z_][A-Za-z0-9_]*\s*=\s*pl\.DataFrame\(json\.loads\(/.test(line)
    && !/^#\s*[A-Za-z_][A-Za-z0-9_]*：尚未获取真实查询结果行/.test(line),
  ).join('\n')

  const activeNames = new Set(inputs.filter((input) => input.datasetId && input.inputName).map((input) => input.inputName))
  const lines = system.split('\n')
  const synchronizedLines: string[] = []
  const existingReaders = new Set<string>()
  for (let index = 0; index < lines.length; index += 1) {
    const readerMatch = lines[index].match(/^if ("(?:[^"\\]|\\.)+") not in locals\(\):$/)
    if (!readerMatch) {
      synchronizedLines.push(lines[index])
      continue
    }
    const inputName = JSON.parse(readerMatch[1]) as string
    if (activeNames.has(inputName)) {
      existingReaders.add(inputName)
      synchronizedLines.push(lines[index])
      continue
    }
    // 该条件是系统生成的一个完整读取块；移除块内缩进行和紧随的空行。
    while (index + 1 < lines.length && (lines[index + 1].startsWith(' ') || !lines[index + 1].trim())) index += 1
  }
  system = synchronizedLines.join('\n')

  const missingReaders = inputs.filter((input) => input.datasetId && input.inputName && !existingReaders.has(input.inputName))
  if (missingReaders.length) {
    const readerBlocks = missingReaders.flatMap((input) => [
      `if ${JSON.stringify(input.inputName)} not in locals():`,
      `    ${input.inputName} = datasets.input(`,
      `        input_name=${JSON.stringify(input.inputName)},`,
      '    ).to_polars()',
      '',
    ]).join('\n')
    const endMarker = '# ===== 数据读取结束 ====='
    const endIndex = system.indexOf(endMarker)
    const insertAt = endIndex >= 0 ? endIndex : system.length
    system = `${system.slice(0, insertAt).trimEnd()}\n\n${readerBlocks.trimEnd()}\n\n${system.slice(insertAt).trimStart()}`
  }

  const block = sampleCommentBlock(inputs.filter((input) => input.datasetId && input.inputName), samplesByInputName).join('\n')
  if (block) {
    const readerIndex = system.search(/^if .+ not in locals\(\):/m)
    const insertAt = readerIndex >= 0 ? readerIndex : system.length
    const before = system.slice(0, insertAt).trimEnd()
    const after = system.slice(insertAt).trimStart()
    system = [before, block, after].filter(Boolean).join('\n\n')
  }
  return userMarkerIndex >= 0
    ? `${system.trimEnd()}\n\n${document.slice(userMarkerIndex)}`
    : system
}

/** 替换旧系统区域，保留用户区域；没有标记时将现有脚本整体视为用户代码。 */
export function mergeBaseScript(existing: string | undefined, generated: string): string {
  const current = existing?.trim() ?? ''
  const start = current.indexOf(SYSTEM_SCRIPT_START)
  const end = current.indexOf(SYSTEM_SCRIPT_END)
  if (start >= 0 && end >= start) {
    const before = current.slice(0, start).trim()
    const after = current.slice(end + SYSTEM_SCRIPT_END.length).trim()
    return [before, generated, after].filter(Boolean).join('\n\n')
  }
  if (!current) return `${generated}\n\n${USER_SCRIPT_START}\nresult = inputs[0] if inputs else None\nreturn result\n`
  return `${generated}\n\n${USER_SCRIPT_START}\n${current}\n`
}
