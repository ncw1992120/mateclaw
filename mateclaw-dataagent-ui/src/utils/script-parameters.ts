import type { DashboardFilterContext, DashboardScriptParameter } from '@/types'

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
): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  const declared = new Map(definitions.map((definition) => [definition.name, definition]))

  for (const filter of context.dimensionFilters ?? []) {
    if (declared.has(filter.field)) values[filter.field] = filter.value
  }

  if (context.timeRange) {
    const dateRangeDefinitions = definitions.filter((definition) => definition.type === 'date_range')
    const target = declared.get('timeRange') ?? declared.get('dateRange')
      ?? (dateRangeDefinitions.length === 1 ? dateRangeDefinitions[0] : undefined)
    if (target) values[target.name] = context.timeRange
  }

  return values
}
