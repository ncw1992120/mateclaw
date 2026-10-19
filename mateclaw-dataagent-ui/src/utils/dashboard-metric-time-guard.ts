import type { DashboardDatasetInput } from '@/types'

const ALOUDATA_SOURCE_TYPES = new Set(['ALOUDATA', 'ALOUDATA_METRICS', 'ALOUDATA_ANALYSIS_VIEW'])
export const METRIC_TIME_REQUIRED_MESSAGE = '请配置指标日期（metric_time）筛选条件后再查询'

export function isAloudataSourceType(sourceType: unknown): boolean {
  return typeof sourceType === 'string' && ALOUDATA_SOURCE_TYPES.has(sourceType.toUpperCase())
}

function hasValue(value: unknown): boolean {
  if (value == null || value === '') return false
  if (Array.isArray(value)) return value.length > 0 && value.every(hasValue)
  return true
}

function isMetricTimeField(field: unknown): boolean {
  return typeof field === 'string' && /^metric_time(?:__[a-z0-9_]+)?$/i.test(field)
}

export function hasMetricTimeCondition(
  filters: Array<{ field?: unknown; operator?: unknown; value?: unknown }> = [],
): boolean {
  return filters.some((filter) => isMetricTimeField(filter.field)
    && hasValue(filter.value)
    && !['is_null', 'is_not_null'].includes(String(filter.operator ?? '').toLowerCase()))
}

/** Returns the first Aloudata input that would query without a non-empty metric_time condition. */
export function findAloudataInputMissingMetricTime(
  inputs: DashboardDatasetInput[],
  runtimeParameters: Record<string, unknown>,
): DashboardDatasetInput | undefined {
  return inputs.find((input) => {
    if (!isAloudataSourceType(input.sourceType)) return false

    const hasFixedMetricTime = hasMetricTimeCondition(input.filters)
    if (hasFixedMetricTime) return false

    const hasBoundMetricTime = (input.queryConfig?.parameterBindings ?? []).some((binding) =>
      isMetricTimeField(binding.field) && hasValue(runtimeParameters[binding.parameterName]))
    return !hasBoundMetricTime
  })
}
