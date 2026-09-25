import type { FinalResultFilterField, FilterCondition } from '@/types'
import { completeConditions } from './filter-conditions'

export interface PythonResultFilterOption {
  id: string
  title: string
  type?: string
  selectionMode?: 'single' | 'multiple'
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
  return fields.flatMap((field) => {
    const filter = filterOptions.find((item) => item.id === field.filterComponentId)
    const base: PythonResultFilterRow = {
      field: field.field,
      title: field.title || field.field,
      filterTitle: filter?.title ?? '筛选器未绑定',
      filterComponentId: field.filterComponentId,
      op: filter?.selectionMode === 'multiple' ? 'in' : '=',
      value: '',
      enabled: false,
      bindingError: !field.filterComponentId || !filter,
    }

    if (base.bindingError) return [base]
    if (filter.type !== 'timeFilter') return [base]

    return [
      { ...base, filterTitle: `${filter.title} · 开始时间`, op: '>=', timeBoundary: 'start' },
      { ...base, filterTitle: `${filter.title} · 结束时间`, op: '<', timeBoundary: 'end' },
    ]
  })
}

/** 仅将用户启用、且值完整的条件应用到 Python 结果。 */
export function enabledPythonResultConditions(rows: readonly PythonResultFilterRow[]): FilterCondition[] {
  return completeConditions(rows
    .filter((row) => row.enabled && !row.bindingError)
    .map(({ field, op, value }) => ({ field, op, value })))
}
