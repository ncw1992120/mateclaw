import type {
  DashboardRuntimeFilterState,
  InsightComponent,
  QueryParameterBinding,
  TimeRangeValue,
} from '@/types'

interface PipelineQueryConfig {
  parameterBindings?: QueryParameterBinding[]
}

interface PipelineDatasetInput {
  queryConfig?: PipelineQueryConfig
}

interface ComponentPipelineConfig {
  datasetInputs?: PipelineDatasetInput[]
}

function isTimeRange(value: unknown): value is TimeRangeValue {
  return !!value && typeof value === 'object' && 'preset' in value
}

function isUnset(value: unknown): boolean {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0)
}

function parameterValue(binding: QueryParameterBinding, value: unknown, expandHalfOpenRange: boolean): unknown {
  if (!isTimeRange(value)) return value

  switch (binding.operator) {
    case 'gt':
      return value.start
    case 'gte':
      return expandHalfOpenRange && value.start && value.end ? [value.start, value.end] : value.start
    case 'lt':
    case 'lte':
      return value.end
    case 'between':
      return value.start && value.end ? [value.start, value.end] : undefined
    default:
      throw new Error(`unsupported time-range operator for parameter ${binding.parameterName}: ${binding.operator}`)
  }
}

function equalParameterValue(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true
  if (Array.isArray(left) || Array.isArray(right)) {
    return Array.isArray(left) && Array.isArray(right)
      && left.length === right.length
      && left.every((value, index) => equalParameterValue(value, right[index]))
  }
  return false
}

/**
 * Build only the runtime parameter map accepted by QueryPlanner. Filter IDs,
 * field names, operators, and parameter names are all taken from the saved
 * per-input queryConfig; this function never invents a binding from labels.
 */
export function buildComponentQueryParameters(
  component: InsightComponent,
  runtimeFilters: DashboardRuntimeFilterState,
): Record<string, unknown> {
  const pipeline = (component.config?.datasetPipeline ?? {}) as ComponentPipelineConfig
  const parameters: Record<string, unknown> = {}

  for (const input of pipeline.datasetInputs ?? []) {
    const bindings = input.queryConfig?.parameterBindings ?? []
    for (const binding of bindings) {
      const filter = runtimeFilters[binding.filterComponentId]
      if (!filter || isUnset(filter.value)) continue
      if (filter.scope === 'scoped' && !filter.targetComponentIds.includes(component.id)) continue

      const explicitEndBoundary = bindings.some((candidate) =>
        candidate.filterComponentId === binding.filterComponentId
        && candidate.field === binding.field
        && ['lt', 'lte'].includes(candidate.operator))
      const value = parameterValue(binding, filter.value, !explicitEndBoundary)
      if (isUnset(value)) continue
      if (Object.hasOwn(parameters, binding.parameterName)) {
        if (!equalParameterValue(parameters[binding.parameterName], value)) {
          throw new Error(`conflicting dashboard filter values for parameter ${binding.parameterName}`)
        }
        continue
      }
      parameters[binding.parameterName] = Array.isArray(value) ? [...value] : value
    }
  }

  return parameters
}
