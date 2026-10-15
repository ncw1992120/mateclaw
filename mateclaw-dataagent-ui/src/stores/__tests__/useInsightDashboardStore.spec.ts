import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  listSummary: vi.fn(),
  create: vi.fn(),
  remove: vi.fn(),
}))

vi.mock('@/api/insight-dashboard', () => ({
  listSummary: mocks.listSummary,
  create: mocks.create,
  remove: mocks.remove,
  list: vi.fn(),
  get: vi.fn(),
  update: vi.fn(),
  copy: vi.fn(),
  saveAsTemplate: vi.fn(),
  streamAiChat: vi.fn(),
}))

import { useInsightDashboardStore } from '../useInsightDashboardStore'

const PAGE_1 = {
  records: [
    { id: '1', name: '策略解读', status: 'draft', visibility: 'private', chartKind: 'bar' },
    { id: '2', name: '销售看板', status: 'published', visibility: 'private', chartKind: 'kpi-grid' },
  ],
  total: 7,
  page: 1,
  size: 20,
  counts: { all: 7, draft: 5, published: 2 },
}

describe('useInsightDashboardStore 列表摘要分页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.listSummary.mockReset()
    mocks.create.mockReset()
    mocks.remove.mockReset()
    mocks.listSummary.mockResolvedValue(PAGE_1)
  })

  it('分页查询写入摘要、总数与状态计数', async () => {
    const store = useInsightDashboardStore()
    await store.fetchDashboardSummaries({ visibility: 'private,workspace', page: 1, size: 20 })

    expect(mocks.listSummary).toHaveBeenCalledWith({ visibility: 'private,workspace', page: 1, size: 20 })
    expect(store.summaries).toHaveLength(2)
    expect(store.summaryTotal).toBe(7)
    expect(store.summaryCounts).toEqual({ all: 7, draft: 5, published: 2 })
    expect(store.listNeedsRefresh).toBe(false)
    expect(store.summaryQuery).toEqual({ visibility: 'private,workspace', page: 1, size: 20 })
  })

  it('后续刷新复用当前生效的查询条件', async () => {
    // 服务端原样回显分页参数，刷新应使用上一次生效的条件
    mocks.listSummary.mockImplementation((query: Record<string, unknown>) =>
      Promise.resolve({ ...PAGE_1, page: query.page ?? 1, size: query.size ?? 20 }))
    const store = useInsightDashboardStore()
    await store.fetchDashboardSummaries({ keyword: '策略', page: 3, size: 10 })
    await store.refreshDashboardSummaries()

    expect(mocks.listSummary).toHaveBeenLastCalledWith({ keyword: '策略', page: 3, size: 10 })
  })

  it('删除后当前页为空时回退到上一页', async () => {
    const store = useInsightDashboardStore()
    await store.fetchDashboardSummaries({ page: 2, size: 20 })
    mocks.listSummary.mockImplementation((query: Record<string, unknown>) => Promise.resolve(
      (query.page ?? 1) === 2
        ? { ...PAGE_1, records: [], total: 20, page: 2 }
        : { ...PAGE_1, total: 20, page: 1 },
    ))
    await store.refreshDashboardSummaries()

    expect(mocks.listSummary).toHaveBeenLastCalledWith({ page: 1, size: 20 })
    expect(store.summaryPage).toBe(1)
  })

  it('创建只依赖创建接口返回，列表刷新在后台进行', async () => {
    const store = useInsightDashboardStore()
    mocks.create.mockResolvedValue({ id: 'new-1', name: '未命名仪表盘' })
    let releaseRefresh!: (value: unknown) => void
    mocks.listSummary.mockReturnValue(new Promise((resolve) => { releaseRefresh = resolve }))

    const created = await store.createDashboard({ name: '未命名仪表盘' })

    expect(created.id).toBe('new-1')
    expect(store.listNeedsRefresh).toBe(true)
    releaseRefresh(PAGE_1)
    await nextTick()
  })

  it('后台刷新失败时只标记列表过期，不抛出未捕获异常', async () => {
    const store = useInsightDashboardStore()
    mocks.listSummary.mockRejectedValue(new Error('网络异常'))
    const errors: unknown[] = []
    const onError = (reason: unknown) => errors.push(reason)
    process.once('unhandledRejection', onError)

    await store.refreshDashboardSummaries().catch(() => {})
    expect(errors).toHaveLength(0)
  })
})
