/**
 * 「查看数据」弹窗的**会话级**查询缓存：按数据集记住最近一次执行结果（含产生它的条件）。
 *
 * 目的：关掉弹窗再打开，结果还在 —— 不必重新点「查询」，也不必重新跑一遍后端。
 *
 * 只放内存、不进 schema：行数据可能很大，落进 `DatasetConfig` 会跟着仪表盘一起持久化，
 * 体积和语义都不对。刷新页面后缓存消失，回到「点「查询」获取数据」的空态，这是有意的。
 *
 * `signature` 记录产生这份结果的条件（SQL + 条件行 + 遗留条件）：重新打开时若当前条件已变，
 * 结果照常展示但标注「条件已变更」，让用户自己决定要不要重查 —— 不静默丢弃，也不假装它还是最新的。
 */

export interface CachedQueryResult {
  rows: Record<string, unknown>[]
  columns: string[]
  /** 上一页取满时才认为可能还有下一页（恢复后滚动加载可继续） */
  hasMore: boolean
  elapsed: string
  queriedAt: number
  signature: string
}

const cache = new Map<string, CachedQueryResult>()

export function getCachedQuery(datasetId: string): CachedQueryResult | undefined {
  return cache.get(datasetId)
}

export function setCachedQuery(datasetId: string, result: CachedQueryResult): void {
  if (!datasetId) return
  cache.set(datasetId, result)
}

export function clearCachedQuery(datasetId: string): void {
  cache.delete(datasetId)
}

/** 测试用：清空全部缓存 */
export function clearAllCachedQueries(): void {
  cache.clear()
}
