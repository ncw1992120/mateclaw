import type { DashboardScriptFilterBinding, DashboardScriptFilterCondition, DatasetFilter } from '@/types'

export interface RuntimeFilterRow extends DashboardScriptFilterCondition {
  /** 当前查看数据草稿中的参数名；一个条件的首个参数作为单值输入。 */
  parameterName: string
  value?: unknown
}

function hasValue(value: unknown): boolean {
  if (value === undefined || value === null) return false
  if (typeof value === 'string') return value.trim() !== ''
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.values(value as Record<string, unknown>).some(hasValue)
  return true
}

function valueForParameter(row: RuntimeFilterRow, parameterName: string, index: number): unknown {
  if (row.parameterNames.length === 1) return row.value
  if (Array.isArray(row.value)) return row.value[index]
  if (row.value && typeof row.value === 'object') return (row.value as Record<string, unknown>)[parameterName]
  return undefined
}

/** 把绑定模板转成查看数据弹窗的空白运行时行，不携带上一次查询值。 */
export function defaultRuntimeRows(inputName: string, bindings: DashboardScriptFilterBinding[]): RuntimeFilterRow[] {
  return bindings.flatMap((binding) => (binding.conditions ?? [])
    .filter((condition) => condition.inputName === inputName || binding.inputNames.includes(inputName))
    .map((condition) => ({
      ...condition,
      parameterName: condition.parameterNames[0] ?? '',
      value: undefined,
    })))
}

/** 只提交有值的可选参数；不把空字符串、空数组或 null 送入 Runner。 */
export function buildExecutionParameters(rows: RuntimeFilterRow[]): Record<string, unknown> {
  const parameters: Record<string, unknown> = {}
  rows.forEach((row) => {
    if (!hasValue(row.value)) return
    row.parameterNames.forEach((name, index) => {
      const value = valueForParameter(row, name, index)
      if (hasValue(value)) parameters[name] = value
    })
  })
  return parameters
}

/** 把有值的模板行转换为后端结构化过滤条件；单边时间自然保留为单边条件。 */
export function toDatasetFilters(rows: RuntimeFilterRow[]): DatasetFilter[] {
  return rows
    .filter((row) => row.field.trim() && (row.operator === 'is_null' || row.operator === 'is_not_null' || hasValue(row.value)))
    .map((row) => ({
      field: row.field.trim(),
      operator: row.operator,
      ...(row.operator === 'is_null' || row.operator === 'is_not_null' ? {} : { value: row.value }),
      role: 'dimension' as const,
    }))
}
