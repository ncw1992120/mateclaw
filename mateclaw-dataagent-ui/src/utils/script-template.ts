import type { DashboardDatasetInput, DashboardScriptParameter } from '@/types'

export const SYSTEM_SCRIPT_START = '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） ====='
export const SYSTEM_SCRIPT_END = '# ===== 系统生成区域结束 ====='
export const USER_SCRIPT_START = '# ===== 用户处理区域：Join、合并、计算和业务规则 ====='

function readExpression(parameter: DashboardScriptParameter): string {
  const value = `datasets.params.get(${JSON.stringify(parameter.name)})`
  if (parameter.type === 'string[]' || parameter.type === 'number[]') {
    return `{ "field": ${JSON.stringify(parameter.name)}, "operator": "in", "value": ${value} or [] }`
  }
  if (parameter.type === 'date_range') {
    const rangeValue = `(lambda value: [value.get("start"), value.get("end")] if isinstance(value, dict) else (value or []))(${value})`
    return `{ "field": ${JSON.stringify(parameter.name)}, "operator": "between", "value": ${rangeValue} }`
  }
  return `{ "field": ${JSON.stringify(parameter.name)}, "operator": "eq", "value": ${value} }`
}

function datasetFilterExpression(filter: { field: string; operator: string; value?: unknown }): string {
  return `{ "field": ${JSON.stringify(filter.field)}, "operator": ${JSON.stringify(filter.operator)}, "value": ${JSON.stringify(filter.value)} }`
}

/** 生成只读的系统区域；字段默认与参数同名，用户可在脚本中调整映射。 */
export function buildSystemScript(inputs: DashboardDatasetInput[], parameters: DashboardScriptParameter[]): string {
  const validInputs = inputs.filter(input => input.datasetId && input.inputName)
  const validParameters = parameters.filter(parameter => parameter.name.trim())
  const lines = [SYSTEM_SCRIPT_START, '# 页面筛选参数会通过 datasets.params 注入，并在 datasets.read 时下推。']
  if (validParameters.length) {
    lines.push('# 默认约定：参数名与数据集字段名相同；多选使用 in，日期范围使用 between。')
  } else {
    lines.push('# 当前未声明页面筛选参数；可在“脚本参数”中添加参数后重新生成。')
  }
  validInputs.forEach((input) => {
    lines.push('', `${input.inputName} = datasets.read(`, `    input_name=${JSON.stringify(input.inputName)},`, '    columns=[],')
    const filters = [...(input.filters || [])]
    if (validParameters.length || filters.length) {
      lines.push('    filters=[')
      validParameters.forEach(parameter => lines.push(`        ${readExpression(parameter)},`))
      filters.forEach(filter => lines.push(`        ${datasetFilterExpression(filter)},`))
      lines.push('    ],')
    } else {
      lines.push('    filters=[],')
    }
    lines.push(').to_polars()')
  })
  lines.push('', SYSTEM_SCRIPT_END)
  return lines.join('\n')
}

/** 替换旧系统区域，保留用户区域；没有标记时将现有脚本整体视为用户代码。 */
export function mergeBaseScript(existing: string | undefined, generated: string): string {
  const current = existing?.trim() ?? ''
  const start = current.indexOf(SYSTEM_SCRIPT_START)
  const end = current.indexOf(SYSTEM_SCRIPT_END)
  const userStart = current.indexOf(USER_SCRIPT_START)
  if (start >= 0 && end >= start) {
    const before = current.slice(0, start).trim()
    const after = current.slice(end + SYSTEM_SCRIPT_END.length).trim()
    return [before, generated, after].filter(Boolean).join('\n\n')
  }
  if (!current) return `${generated}\n\n${USER_SCRIPT_START}\nresult = inputs[0] if inputs else None\nreturn result\n`
  return `${generated}\n\n${USER_SCRIPT_START}\n${current}\n`
}
