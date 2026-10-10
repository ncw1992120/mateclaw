import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  InsightDashboard,
  InsightDashboardCreateInput,
  InsightDashboardUpdateInput,
  InsightDashboardSaveAsTemplateInput,
  InsightDashboardAiChatInput,
  InsightDashboardSummary,
  InsightDashboardSummaryQuery,
  InsightDashboardCounts,
} from '@/types'
import * as insightDashboardApi from '@/api/insight-dashboard'
import type { StreamAiChatCallbacks } from '@/api/insight-dashboard'

/** 洞察仪表盘状态管理 */
export const useInsightDashboardStore = defineStore('insightDashboard', () => {
  /** 仪表盘列表（完整契约，仅详情类消费方使用） */
  const dashboards = ref<InsightDashboard[]>([])
  /** 列表摘要：分页查询的当前页记录 */
  const summaries = ref<InsightDashboardSummary[]>([])
  /** 当前可见范围内的总数 */
  const summaryTotal = ref(0)
  /** 当前页码（从 1 开始） */
  const summaryPage = ref(1)
  /** 当前每页条数 */
  const summarySize = ref(20)
  /** 当前可见范围内的状态计数（不受分页影响） */
  const summaryCounts = ref<InsightDashboardCounts>({ all: 0, draft: 0, published: 0 })
  /** 当前生效的列表查询条件（翻页/刷新复用） */
  const summaryQuery = ref<InsightDashboardSummaryQuery>({})
  /** 列表是否在取数 */
  const listLoading = ref(false)
  /** 列表是否已过期：创建/复制等操作后返回列表需要重新查询 */
  const listNeedsRefresh = ref(false)
  /** 加载状态（详情与列表共用，兼容旧消费方） */
  const loading = ref(false)
  /** 当前选中的仪表盘 */
  const currentDashboard = ref<InsightDashboard | null>(null)

  /** 重置所有状态（用于切换用户/工作区时清理脏数据） */
  function reset(): void {
    dashboards.value = []
    summaries.value = []
    summaryTotal.value = 0
    summaryPage.value = 1
    summarySize.value = 20
    summaryCounts.value = { all: 0, draft: 0, published: 0 }
    summaryQuery.value = {}
    listLoading.value = false
    listNeedsRefresh.value = false
    currentDashboard.value = null
    loading.value = false
  }

  /** 标记列表已过期（下次进入列表会重新查询） */
  function markListNeedsRefresh(): void {
    listNeedsRefresh.value = true
  }

  /**
   * 查询仪表盘列表摘要（分页）。
   * 传入的查询条件会成为当前生效条件，后续翻页/刷新复用它。
   */
  async function fetchDashboardSummaries(query: InsightDashboardSummaryQuery = {}): Promise<void> {
    summaryQuery.value = { ...query }
    listLoading.value = true
    loading.value = true
    try {
      const data = await insightDashboardApi.listSummary(query)
      const page = data as unknown as {
        records?: InsightDashboardSummary[]
        total?: number
        page?: number
        size?: number
        counts?: InsightDashboardCounts
      }
      summaries.value = page.records ?? []
      summaryTotal.value = Number(page.total ?? 0)
      summaryPage.value = Number(page.page ?? 1)
      summarySize.value = Number(page.size ?? 20)
      summaryCounts.value = page.counts ?? { all: summaryTotal.value, draft: 0, published: 0 }
      listNeedsRefresh.value = false
    } finally {
      listLoading.value = false
      loading.value = false
    }
  }

  /**
   * 按当前生效条件重新查询列表摘要（后台刷新用）。
   * 删除导致当前页为空时自动回退到上一页。
   */
  async function refreshDashboardSummaries(): Promise<void> {
    const base = { ...summaryQuery.value, page: summaryPage.value, size: summarySize.value }
    await fetchDashboardSummaries(base)
    if (summaries.value.length === 0 && summaryPage.value > 1) {
      await fetchDashboardSummaries({ ...base, page: summaryPage.value - 1 })
    }
  }

  /** 后台刷新列表：失败不外抛，避免打断调用方的关键路径 */
  function refreshDashboardSummariesInBackground(): void {
    void refreshDashboardSummaries().catch(() => {
      listNeedsRefresh.value = true
    })
  }

  /**
   * 获取仪表盘列表（完整契约，兼容旧调用方）。
   * 列表主链路已改为摘要分页接口，此方法仅用于需要 schemaJson 的场景。
   */
  async function fetchDashboards(params?: { visibility?: string }): Promise<void> {
    loading.value = true
    try {
      const data = await insightDashboardApi.list(params)
      dashboards.value = data as unknown as InsightDashboard[]
    } finally {
      loading.value = false
    }
  }

  /** 选中仪表盘（加载详情） */
  async function selectDashboard(id: string): Promise<void> {
    const data = await insightDashboardApi.get(id)
    currentDashboard.value = data as unknown as InsightDashboard
  }

  /**
   * 创建仪表盘。
   * 只依赖创建接口返回：不再同步等待列表刷新，列表改为标记过期后后台刷新。
   */
  async function createDashboard(data: InsightDashboardCreateInput): Promise<InsightDashboard> {
    const created = await insightDashboardApi.create(data)
    listNeedsRefresh.value = true
    refreshDashboardSummariesInBackground()
    return created as unknown as InsightDashboard
  }

  /** 更新仪表盘 */
  async function updateDashboard(id: string, data: InsightDashboardUpdateInput): Promise<void> {
    await insightDashboardApi.update(id, data)
    await refreshDashboardSummaries()
    if (currentDashboard.value?.id === id) {
      await selectDashboard(id)
    }
  }

  /** 删除仪表盘 */
  async function deleteDashboard(id: string): Promise<void> {
    await insightDashboardApi.remove(id)
    if (currentDashboard.value?.id === id) {
      currentDashboard.value = null
    }
    await refreshDashboardSummaries()
  }

  /** 复制仪表盘 */
  async function copyDashboard(id: string): Promise<InsightDashboard> {
    const copied = await insightDashboardApi.copy(id)
    await refreshDashboardSummaries()
    return copied as unknown as InsightDashboard
  }

  /** 存为样例模板：派生为团队共享样例模板，副本带示例数据可直接使用 */
  async function saveAsTemplate(id: string, data: InsightDashboardSaveAsTemplateInput): Promise<InsightDashboard> {
    const created = await insightDashboardApi.saveAsTemplate(id, data)
    await refreshDashboardSummaries()
    return created as unknown as InsightDashboard
  }

  /**
   * AI助手流式对话
   * @param data 请求参数
   * @param callbacks 事件回调
   * @returns 关闭SSE连接的函数
   */
  function streamAiChatDashboard(
    data: InsightDashboardAiChatInput,
    callbacks: StreamAiChatCallbacks,
  ): () => void {
    return insightDashboardApi.streamAiChat(
      data,
      {
        onReasoning: callbacks.onReasoning,
        onContent: callbacks.onContent,
        onResult: (dashboard) => {
          void refreshDashboardSummaries()
          callbacks.onResult(dashboard)
        },
        onError: callbacks.onError,
      },
    )
  }

  return {
    dashboards,
    summaries,
    summaryTotal,
    summaryPage,
    summarySize,
    summaryCounts,
    summaryQuery,
    listLoading,
    listNeedsRefresh,
    loading,
    currentDashboard,
    fetchDashboards,
    fetchDashboardSummaries,
    refreshDashboardSummaries,
    markListNeedsRefresh,
    selectDashboard,
    createDashboard,
    updateDashboard,
    deleteDashboard,
    copyDashboard,
    saveAsTemplate,
    streamAiChatDashboard,
    reset,
  }
})
