import type { DashboardDatasetInput, QueryContext } from '@/types'

type SavedInputFilter = NonNullable<DashboardDatasetInput['lastQueryState']>['filters'][number]

function hasValue(value: unknown): boolean {
  if (value === undefined || value === null) return false
  if (typeof value === 'string') return value.trim() !== ''
  if (Array.isArray(value)) return value.length > 0
  return true
}

/** 输入查看数据以右开区间保存结束日期；Planner 接收闭区间并自行转右开，故向前折一天。 */
function inclusiveEndDate(value: unknown): unknown {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const parsed = Date.parse(`${value}T00:00:00.000Z`)
  if (!Number.isFinite(parsed)) return value
  return new Date(parsed - 86_400_000).toISOString().slice(0, 10)
}

function setParameter(parameters: Record<string, unknown>, name: string, value: unknown): void {
  if (!name || !hasValue(value)) return
  if (Object.hasOwn(parameters, name) && JSON.stringify(parameters[name]) !== JSON.stringify(value)) {
    throw new Error(`上游数据集的筛选值冲突：参数 ${name}`)
  }
  parameters[name] = value
}

/** 从每个输入最近一次「查看数据」查询状态恢复页面筛选值，参数仍只使用已声明绑定名。 */
function parametersFromLastInputQueries(inputs: DashboardDatasetInput[]): Record<string, Record<string, unknown>> {
  const datasetParameters: Record<string, Record<string, unknown>> = {}
  for (const input of inputs) {
    const parameters: Record<string, unknown> = {}
    const savedFilters = input.lastQueryState?.filters ?? []
    for (const binding of input.queryConfig?.parameterBindings ?? []) {
      const rows = savedFilters.filter((row: SavedInputFilter) =>
        row.filterComponentId === binding.filterComponentId && row.field === binding.field,
      )
      const start = rows.find((row) => row.timeBoundary === 'start' && row.enabled && hasValue(row.value))
      const end = rows.find((row) => row.timeBoundary === 'end' && row.enabled && hasValue(row.value))
      const explicitEnd = input.queryConfig?.parameterBindings.some((candidate) =>
        candidate.filterComponentId === binding.filterComponentId
        && candidate.field === binding.field
        && ['lt', 'lte'].includes(candidate.operator),
      )

      if (binding.operator === 'gte' && start) {
        const value = end && !explicitEnd
          ? [start.value, inclusiveEndDate(end.value)]
          : start.value
        setParameter(parameters, binding.parameterName, value)
      } else if ((binding.operator === 'lt' || binding.operator === 'lte') && end) {
        setParameter(parameters, binding.parameterName, end.value)
      } else {
        const valueRow = rows.find((row) => !row.timeBoundary && row.enabled && hasValue(row.value))
        if (valueRow) setParameter(parameters, binding.parameterName, valueRow.value)
      }
    }
    if (Object.keys(parameters).length) datasetParameters[input.inputName] = parameters
  }
  return datasetParameters
}

/** 构造 Python 编辑器预览的运行时查询上下文，恢复上游输入最近一次查询的筛选值。 */
export function createComponentPreviewQueryContext(
  dashboardId: string,
  componentId: string,
  inputs: DashboardDatasetInput[] = [],
): QueryContext {
  return {
    dashboardId,
    componentId,
    parameters: {},
    datasetParameters: parametersFromLastInputQueries(inputs),
    requestId: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  }
}
