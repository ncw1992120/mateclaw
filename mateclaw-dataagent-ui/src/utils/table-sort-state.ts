import type { QuerySortSpec } from '@/types'

/**
 * 表头「升序 → 降序 → 取消」三态（实施计划任务 5 步骤 5）：
 * - 点击新字段：从升序开始；
 * - 同字段再点：升序 → 降序 → 取消；
 * - 取消后返回 null（无排序）。
 */
export function nextSortState(current: QuerySortSpec | null | undefined, field: string): QuerySortSpec | null {
  if (!field) return current ?? null
  if (!current || current.field !== field) return { field, direction: 'asc' }
  if (current.direction === 'asc') return { field, direction: 'desc' }
  return null
}

/** el-table sort-change 的 order → 统一方向。 */
export function elOrderToSort(field: string, order: 'ascending' | 'descending' | null | undefined): QuerySortSpec | null {
  if (order === 'ascending') return { field, direction: 'asc' }
  if (order === 'descending') return { field, direction: 'desc' }
  return null
}

/**
 * 排序/筛选变化时页码必须回到 1；页大小变化保留筛选与排序（页码回 1 由调用方决定）。
 * 返回 true 表示应重置页码。
 */
export function shouldResetPage(prev: { sort?: QuerySortSpec | null; parameters?: unknown } | null | undefined,
  next: { sort?: QuerySortSpec | null; parameters?: unknown }): boolean {
  const prevKey = JSON.stringify(prev?.sort ?? null) + JSON.stringify(prev?.parameters ?? null)
  const nextKey = JSON.stringify(next.sort ?? null) + JSON.stringify(next.parameters ?? null)
  return prevKey !== nextKey
}
