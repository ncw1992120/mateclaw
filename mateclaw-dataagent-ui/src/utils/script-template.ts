import type { DashboardDatasetInput, DashboardScriptParameter, DashboardScriptFilterBinding } from '@/types'

export const SYSTEM_SCRIPT_START = '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） ====='
export const SYSTEM_SCRIPT_END = '# ===== 系统生成区域结束 ====='
export const USER_SCRIPT_START = '# ===== 用户处理区域：Join、合并、计算和业务规则 ====='

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
): string {
  const validInputs = inputs.filter(input => input.datasetId && input.inputName)
  const lines = [
    SYSTEM_SCRIPT_START,
    '# 每个输入已是查询计划处理后的结果：页面筛选、排序和分页在读取前应用。',
    '# 只读区域：请勿在此拼接 filters/orders/limit/offset 等页面查询参数。',
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
  validInputs.forEach((input) => {
    lines.push('', `${input.inputName} = datasets.input(`, `    input_name=${JSON.stringify(input.inputName)},`, ').to_polars()')
  })
  if (!validInputs.length) {
    lines.push('', '# 当前没有已绑定的数据集输入。')
  }
  lines.push('', SYSTEM_SCRIPT_END)
  return lines.join('\n')
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
