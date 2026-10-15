import type { DatasetFilter } from '@/types'
import { normalizeOperator } from './filter-conditions'

export interface FixedDatasetFilterInput {
  field: string
  op?: string
  operator?: string
  value?: unknown
}

/** 将仪表盘 Schema 中的固定筛选条件归一为数据集查询接口使用的结构。 */
export function toFixedDatasetFilters(
  filters: readonly FixedDatasetFilterInput[] = [],
  fields: readonly { name: string; role?: string }[] = [],
): DatasetFilter[] {
  const roleByField = new Map(fields.map((field) => [field.name, field.role?.toLowerCase()]))
  return filters
    .filter((filter) => filter.field?.trim())
    .map((filter) => {
      const normalized = normalizeOperator(filter.operator ?? filter.op)
      const operator = ({
        '=': 'eq', '!=': 'neq', '>': 'gt', '>=': 'gte', '<': 'lt', '<=': 'lte',
        'not in': 'not_in', 'is null': 'is_null', 'is not null': 'is_not_null',
      } as Record<string, string>)[normalized] ?? normalized
      const needsValue = operator !== 'is_null' && operator !== 'is_not_null'
      const rawValue = filter.value
      const value = !needsValue
        ? undefined
        : ['in', 'not_in', 'between'].includes(operator) && typeof rawValue === 'string'
          ? rawValue.split(',').map((part) => part.trim()).filter(Boolean)
          : rawValue
      return {
        field: filter.field.trim(),
        role: ['measure', 'metric'].includes(roleByField.get(filter.field.trim()) ?? '') ? 'measure' : 'dimension',
        operator,
        ...(needsValue ? { value } : {}),
      }
    })
}
