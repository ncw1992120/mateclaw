import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Agent } from '@/types'
import * as agentApi from '@/api/agent'

/** Agent 状态管理 */
export const useAgentStore = defineStore('agent', () => {
  /** Agent 列表 */
  const agents = ref<Agent[]>([])
  /** 当前选中的 Agent */
  const currentAgent = ref<Agent | null>(null)
  /** 加载状态 */
  const loading = ref(false)

  /** 获取 Agent 列表 */
  async function fetchAgents(workspaceId: number): Promise<void> {
    loading.value = true
    try {
      const data = await agentApi.list(workspaceId)
      agents.value = data as unknown as Agent[]
    } finally {
      loading.value = false
    }
  }

  /** 选中 Agent */
  async function selectAgent(id: number): Promise<void> {
    const data = await agentApi.get(id)
    currentAgent.value = data as unknown as Agent
  }

  /** 创建 Agent */
  async function createAgent(data: Partial<Agent>): Promise<void> {
    await agentApi.create(data)
    if (data.workspaceId) {
      await fetchAgents(data.workspaceId)
    }
  }

  /** 更新 Agent */
  async function updateAgent(id: number, data: Partial<Agent>): Promise<void> {
    await agentApi.update(id, data)
    if (data.workspaceId) {
      await fetchAgents(data.workspaceId)
    }
    if (currentAgent.value?.id === id) {
      await selectAgent(id)
    }
  }

  /** 删除 Agent */
  async function deleteAgent(id: number): Promise<void> {
    const workspaceId = currentAgent.value?.workspaceId
    await agentApi.remove(id)
    if (currentAgent.value?.id === id) {
      currentAgent.value = null
    }
    if (workspaceId) {
      await fetchAgents(workspaceId)
    }
  }

  /** 应用模板创建 Agent */
  async function applyTemplate(templateId: number, workspaceId: number): Promise<void> {
    await agentApi.applyTemplate(templateId, workspaceId)
    await fetchAgents(workspaceId)
  }

  return {
    agents,
    currentAgent,
    loading,
    fetchAgents,
    selectAgent,
    createAgent,
    updateAgent,
    deleteAgent,
    applyTemplate,
  }
})
