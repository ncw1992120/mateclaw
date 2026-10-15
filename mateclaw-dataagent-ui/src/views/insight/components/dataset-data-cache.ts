import type { DatasetLastQueryState } from '@/types'

/**
 * 「查看数据」弹窗的会话级结果缓存：按数据集记住最近一次执行结果。
 *
 * 结果行只留在内存，避免大结果随仪表盘持久化；最近一次筛选值则另存到数据集输入的
 * `lastQueryState`，随仪表盘 Schema 保存并在重新打开时恢复。
 *
 * 关掉弹窗再打开时，结果仍在；页面刷新后结果回到「点「查询」获取数据」空态，
 * 但查询条件和值保留，用户无需重新填写。
 *
 * `signature` 记录产生这份结果的条件（SQL + 条件行 + 遗留条件）：重新打开时若当前条件已变，
 * 结果照常展示但标注「条件已变更」，让用户自己决定要不要重查 —— 不静默丢弃，也不假装它还是最新的。
 */

export interface CachedQueryResult {
  rows: Record<string, unknown>[]
  columns: string[]
  /** 上一页取满时才认为可能还有下一页（恢复后滚动加载可继续） */
  hasMore: boolean
  totalCount?: number | null
  elapsed: string
  queriedAt: number
  signature: string
}

export type CachedQueryState = DatasetLastQueryState

const cache = new Map<string, CachedQueryResult>()
const stateCache = new Map<string, CachedQueryState>()

export function getCachedQuery(datasetId: string): CachedQueryResult | undefined {
  return cache.get(datasetId)
}

export function setCachedQuery(datasetId: string, result: CachedQueryResult): void {
  if (!datasetId) return
  cache.set(datasetId, result)
}

export function getCachedQueryState(datasetId: string): CachedQueryState | undefined {
  return stateCache.get(datasetId)
}

export function setCachedQueryState(datasetId: string, state: CachedQueryState): void {
  if (!datasetId) return
  stateCache.set(datasetId, state)
}

export function clearCachedQuery(datasetId: string): void {
  cache.delete(datasetId)
  stateCache.delete(datasetId)
}

/** 测试用：清空全部缓存 */
export function clearAllCachedQueries(): void {
  cache.clear()
  stateCache.clear()
}
