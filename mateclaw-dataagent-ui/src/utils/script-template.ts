import type { DashboardDatasetInput, DashboardScriptParameter, DashboardScriptFilterBinding } from '@/types'

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

const OPTIONAL_FILTER_RUNTIME = `def _optional_filter(field, operator, parameter_name=None):
    """可选运行时筛选：参数未填写时返回空条件，不改变全量查询语义。"""
    if operator in ("is_null", "is_not_null"):
        return [{"field": field, "operator": operator}]
    value = datasets.params.get(parameter_name)
    if value is None or value == "" or (isinstance(value, (list, tuple)) and len(value) == 0):
        return []
    return [{"field": field, "operator": operator, "value": value}]`

/** 生成只读的系统区域；字段默认与参数同名，用户可在脚本中调整映射。 */
export function buildSystemScript(inputs: DashboardDatasetInput[], parameters: DashboardScriptParameter[], bindings: DashboardScriptFilterBinding[] = []): string {
  const validInputs = inputs.filter(input => input.datasetId && input.inputName)
  const validParameters = parameters.filter(parameter => parameter.name.trim())
  const lines = [SYSTEM_SCRIPT_START, '# 页面筛选参数会通过 datasets.params 注入，并在 datasets.read 时下推。']
  const hasExplicitConditions = bindings.some(binding => (binding.conditions || []).some(condition => condition.field && condition.operator))
  if (hasExplicitConditions) lines.push(OPTIONAL_FILTER_RUNTIME, '# 未填写时不下推绑定筛选条件。')
  if (validParameters.length) {
    lines.push('# 默认约定：参数名与数据集字段名相同；多选使用 in，日期范围使用 between。')
  } else {
    lines.push('# 当前未声明页面筛选参数；可在“脚本参数”中添加参数后重新生成。')
  }
  validInputs.forEach((input) => {
    lines.push('', `${input.inputName} = datasets.read(`, `    input_name=${JSON.stringify(input.inputName)},`, '    columns=[],')
    const filters = [...(input.filters || [])]
    const boundConditions = bindings
      .filter(binding => binding.inputNames.includes(input.inputName))
      .flatMap(binding => (binding.conditions || []).filter(condition => condition.inputName === input.inputName && condition.field))
    const boundParameterNames = new Set<string>()
    bindings.filter(binding => binding.inputNames.includes(input.inputName)).forEach(binding => {
      const mapped = binding.fieldMappings?.[input.inputName]
      if (mapped) boundParameterNames.add(mapped)
    })
    const scopedParameters = bindings.length === 0 ? validParameters : validParameters.filter(parameter => boundParameterNames.has(parameter.name))
    if (boundConditions.length || scopedParameters.length || filters.length) {
      lines.push('    filters=[')
      boundConditions.forEach(condition => lines.push(
        `        *_optional_filter(${JSON.stringify(condition.field)}, ${JSON.stringify(condition.operator)}, ${JSON.stringify(condition.parameterNames[0])}),`,
      ))
      scopedParameters.forEach(parameter => lines.push(`        ${readExpression(parameter)},`))
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
