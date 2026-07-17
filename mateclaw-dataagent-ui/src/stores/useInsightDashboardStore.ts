import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { InsightDashboard, InsightDashboardCreateInput, InsightDashboardUpdateInput, InsightDashboardGenerateInput, InsightDashboardAiChatInput } from '@/types'
import * as insightDashboardApi from '@/api/insight-dashboard'

/** 洞察仪表盘状态管理 */
export const useInsightDashboardStore = defineStore('insightDashboard', () => {
  /** 仪表盘列表 */
  const dashboards = ref<InsightDashboard[]>([])
  /** 当前选中的仪表盘 */
  const currentDashboard = ref<InsightDashboard | null>(null)
  /** 加载状态 */
  const loading = ref(false)

  /** 重置所有状态（用于切换用户/工作区时清理脏数据） */
  function reset(): void {
    dashboards.value = []
    currentDashboard.value = null
    loading.value = false
  }

  /** 获取仪表盘列表 */
  async function fetchDashboards(): Promise<void> {
    loading.value = true
    try {
      const data = await insightDashboardApi.list()
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

  /** 创建仪表盘 */
  async function createDashboard(data: InsightDashboardCreateInput): Promise<InsightDashboard> {
    const created = await insightDashboardApi.create(data)
    await fetchDashboards()
    return created as unknown as InsightDashboard
  }

  /** 更新仪表盘 */
  async function updateDashboard(id: string, data: InsightDashboardUpdateInput): Promise<void> {
    await insightDashboardApi.update(id, data)
    await fetchDashboards()
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
    await fetchDashboards()
  }

  /** AI助手对话（统一生成和修改） */
  async function aiChatDashboard(data: InsightDashboardAiChatInput): Promise<InsightDashboard> {
    const result = await insightDashboardApi.aiChat(data)
    await fetchDashboards()
    return result as unknown as InsightDashboard
  }

  /** AI生成仪表盘 */
  async function generateDashboard(data: InsightDashboardGenerateInput): Promise<InsightDashboard> {
    const created = await insightDashboardApi.generate(data)
    await fetchDashboards()
    return created as unknown as InsightDashboard
  }

  return {
    dashboards,
    currentDashboard,
    loading,
    fetchDashboards,
    selectDashboard,
    createDashboard,
    updateDashboard,
    deleteDashboard,
    aiChatDashboard,
    generateDashboard,
    reset,
  }
})
