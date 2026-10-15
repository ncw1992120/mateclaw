import type { FinalResultFilterField, FilterCondition } from '@/types'
import type { TimeFilterDefaultPreset } from '@/types'
import { resolveTimeFilterDefault } from '@/composables/useDashboardFilterContext'
import { completeConditions } from './filter-conditions'

export interface PythonResultFilterOption {
  id: string
  title: string
  type?: string
  selectionMode?: 'single' | 'multiple'
  optionSource?: 'static' | 'dynamic'
  staticOptions?: Array<{ label: string; value: string }>
  datasourceId?: string | number
  field?: string
  defaultValue?: string | string[] | null
  defaultPreset?: TimeFilterDefaultPreset
  maxRangeDays?: number
}

export interface PythonResultFilterRow extends FilterCondition {
  title: string
  filterTitle: string
  filterComponentId?: string
  enabled: boolean
  timeBoundary?: 'start' | 'end'
  bindingError?: boolean
}

/** 按绑定的页面筛选器类型构造 Python 最终结果筛选条件。 */
export function createPythonResultFilterRows(
  fields: readonly FinalResultFilterField[],
  filterOptions: readonly PythonResultFilterOption[],
): PythonResultFilterRow[] {
  // 历史版本会自动为所有输出列生成未绑定项；它们不是用户配置的筛选条件。
  return fields.filter((field) => Boolean(field.filterComponentId)).flatMap((field) => {
    const filter = filterOptions.find((item) => item.id === field.filterComponentId)
    const timeRange = filter?.type === 'timeFilter'
      ? resolveTimeFilterDefault(filter.defaultPreset, filter.maxRangeDays)
      : undefined
    const defaultValue = filter?.type === 'timeFilter' ? timeRange : filter?.defaultValue
    const base: PythonResultFilterRow = {
      field: field.field,
      title: field.title || field.field,
      filterTitle: filter?.title ?? '筛选器未绑定',
      filterComponentId: field.filterComponentId,
      op: filter?.selectionMode === 'multiple' ? 'in' : '=',
      value: defaultValue == null ? '' : Array.isArray(defaultValue) ? [...defaultValue] : typeof defaultValue === 'object' ? '' : defaultValue,
      enabled: defaultValue != null && (Array.isArray(defaultValue) ? defaultValue.length > 0 : String(defaultValue).trim() !== ''),
      bindingError: !filter,
    }

    if (base.bindingError) return [base]
    if (filter.type !== 'timeFilter') return [base]

    return [
      { ...base, value: timeRange?.start ?? '', enabled: Boolean(timeRange?.start), filterTitle: `${filter.title} · 开始时间`, op: '>=', timeBoundary: 'start' },
      { ...base, value: timeRange?.end ?? '', enabled: Boolean(timeRange?.end), filterTitle: `${filter.title} · 结束时间`, op: '<', timeBoundary: 'end' },
    ]
  })
}

/** 仅将用户启用、且值完整的条件应用到 Python 结果。 */
export function enabledPythonResultConditions(rows: readonly PythonResultFilterRow[]): FilterCondition[] {
  return completeConditions(rows
    .filter((row) => row.enabled && !row.bindingError)
    .map(({ field, op, value }) => ({ field, op, value })))
}
