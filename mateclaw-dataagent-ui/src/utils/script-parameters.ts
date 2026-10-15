import type {
  DashboardFilterContext,
  DashboardScriptFilterBinding,
  DashboardScriptParameter,
} from '@/types'

/**
 * 将页面运行时筛选转换为脚本参数。
 *
 * 约定：维度筛选字段名与脚本参数名一致；日期范围使用唯一的 date_range
 * 参数，或名为 timeRange/dateRange 的 date_range 参数。没有声明的筛选不透传，
 * 由后端参数契约继续保持 fail-closed。
 */
export function buildScriptParameters(
  definitions: DashboardScriptParameter[] = [],
  context: DashboardFilterContext = { dimensionFilters: [] },
  bindings: DashboardScriptFilterBinding[] = [],
): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  const declared = new Map(definitions.map((definition) => [definition.name, definition]))
  const boundNames = new Set(bindings.flatMap((binding) =>
    (binding.conditions ?? []).flatMap((condition) => condition.parameterNames),
  ))
  const accepts = (name: string): boolean => definitions.length === 0
    ? boundNames.has(name)
    : declared.has(name)

  const put = (name: string | undefined, value: unknown): void => {
    if (!name || !accepts(name) || value === undefined || value === null || value === '') return
    if (Array.isArray(value) && value.length === 0) return
    values[name] = value
  }

  for (const filter of context.dimensionFilters ?? []) {
    if (declared.has(filter.field)) put(filter.field, filter.value)
    for (const binding of bindings) {
      if (context.sourceFilterId && binding.filterComponentId !== context.sourceFilterId) continue
      for (const condition of binding.conditions ?? []) {
        if (condition.field !== filter.field) continue
        if (condition.parameterNames.length === 1) {
          put(condition.parameterNames[0], filter.value)
        } else if (Array.isArray(filter.value)) {
          condition.parameterNames.forEach((name, index) => put(name, filter.value[index]))
        }
      }
    }
  }

  if (context.timeRange) {
    const dateRangeDefinitions = definitions.filter((definition) => definition.type === 'date_range')
    const target = declared.get('timeRange') ?? declared.get('dateRange')
      ?? (dateRangeDefinitions.length === 1 ? dateRangeDefinitions[0] : undefined)
    if (target) put(target.name, context.timeRange)
    for (const binding of bindings) {
      if (context.sourceFilterId && binding.filterComponentId !== context.sourceFilterId) continue
      for (const condition of binding.conditions ?? []) {
        const parameterName = condition.parameterNames[0]
        if (!parameterName) continue
        if (condition.operator === 'gte' || condition.operator === 'gt') put(parameterName, context.timeRange.start)
        if (condition.operator === 'lt' || condition.operator === 'lte') put(parameterName, context.timeRange.end)
        if (condition.operator === 'between') put(parameterName, context.timeRange.start && context.timeRange.end
          ? [context.timeRange.start, context.timeRange.end]
          : undefined)
      }
    }
  }

  return values
}
