import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ModelConfig, ModelProvider } from '@/types'
import * as modelApi from '@/api/model'
import { useUserStore } from '@/stores/useUserStore'

/** 模型配置状态管理 */
export const useModelStore = defineStore('model', () => {
  /** 启用的模型列表 */
  const enabledModels = ref<ModelConfig[]>([])
  /** 所有模型列表（含启用和禁用） */
  const allModels = ref<ModelConfig[]>([])
  /** Provider 目录列表 */
  const providers = ref<ModelProvider[]>([])
  /** 当前激活模型 */
  const activeModel = ref<ModelConfig | null>(null)
  /** 默认模型 */
  const defaultModel = ref<ModelConfig | null>(null)
  /** 默认向量模型 */
  const defaultEmbeddingModel = ref<ModelConfig | null>(null)
  /** 加载状态 */
  const loading = ref(false)

  /** 获取启用模型列表（含 chat 和 embedding 类型），同时更新全量模型列表 */
  async function fetchEnabledModels(): Promise<void> {
    loading.value = true
    try {
      const enabled = await modelApi.listAllEnabledModels() as unknown as ModelConfig[]
      enabledModels.value = enabled.filter(m => m.enabled)
      // 全量模型列表（含禁用）仅全局管理员可访问
      const userStore = useUserStore()
      if (userStore.isAdmin) {
        const all = await modelApi.listAllModels() as unknown as ModelConfig[]
        allModels.value = all
      } else {
        allModels.value = enabledModels.value
      }
    } finally {
      loading.value = false
    }
  }

  /** 获取 Provider 目录（仅全局管理员可访问） */
  async function fetchProviders(): Promise<void> {
    const userStore = useUserStore()
    if (!userStore.isAdmin) {
      providers.value = []
      return
    }
    loading.value = true
    try {
      const list = await modelApi.listCatalog() as unknown as Record<string, unknown>[]
      providers.value = list.map(p => ({
        ...p,
        providerId: p.id,
        models: (p.models as Record<string, unknown>[])?.map((m: Record<string, unknown>) => ({ ...m, modelId: m.id })),
        extraModels: (p.extraModels as Record<string, unknown>[])?.map((m: Record<string, unknown>) => ({ ...m, modelId: m.id })),
      })) as unknown as ModelProvider[]
    } finally {
      loading.value = false
    }
  }

  /** 获取默认模型 */
  async function fetchDefaultModel(): Promise<void> {
    try {
      defaultModel.value = await modelApi.getDefaultModel() as unknown as ModelConfig
    } catch {
      defaultModel.value = null
    }
  }

  /** 获取当前激活模型 */
  async function fetchActiveModel(): Promise<void> {
    try {
      const info = await modelApi.getActiveModel() as unknown as { activeLlm: { providerId: string; model: string } }
      if (info?.activeLlm?.model) {
        const matched = enabledModels.value.find(m => m.modelName === info.activeLlm.model && m.provider === info.activeLlm.providerId)
        if (matched) {
          activeModel.value = matched
        } else {
          activeModel.value = { modelName: info.activeLlm.model, provider: info.activeLlm.providerId } as ModelConfig
        }
      } else {
        activeModel.value = null
      }
    } catch {
      activeModel.value = null
    }
  }

  /** 设置当前激活模型 */
  async function setActiveModelById(modelId: number): Promise<void> {
    await modelApi.setActiveModel(modelId)
    await fetchActiveModel()
  }

  /** 启用 Provider */
  async function enableProvider(providerId: string): Promise<void> {
    await modelApi.enableProvider(providerId)
    await fetchProviders()
    await fetchEnabledModels()
  }

  /** 禁用 Provider */
  async function disableProvider(providerId: string): Promise<void> {
    await modelApi.disableProvider(providerId)
    await fetchProviders()
    await fetchEnabledModels()
  }

  /** 更新 Provider 配置 */
  async function updateProvider(providerId: string, data: Partial<ModelProvider>): Promise<void> {
    await modelApi.updateProviderConfig(providerId, data)
    await fetchProviders()
  }

  /** 创建模型 */
  async function createModel(data: Partial<ModelConfig>): Promise<void> {
    await modelApi.createModel(data)
    await fetchEnabledModels()
  }

  /** 更新模型 */
  async function updateModel(id: number, data: Partial<ModelConfig>): Promise<void> {
    await modelApi.updateModel(id, data)
    await fetchEnabledModels()
    if (activeModel.value?.id === id) {
      await fetchActiveModel()
    }
  }

  /** 删除模型 */
  async function deleteModel(id: number): Promise<void> {
    await modelApi.deleteModel(id)
    await fetchEnabledModels()
    if (activeModel.value?.id === id) {
      activeModel.value = null
    }
  }

  /** 切换模型启用/禁用状态 */
  async function toggleModelEnabled(id: number, enabled: boolean): Promise<void> {
    const model = allModels.value.find(m => m.id === id)
    if (!model) {
      return
    }
    await modelApi.updateModel(id, { ...model, enabled } as Partial<ModelConfig>)
    await fetchEnabledModels()
    if (!enabled && activeModel.value?.id === id) {
      activeModel.value = null
    }
  }

  /** 设置默认模型 */
  async function setDefaultModel(id: number): Promise<void> {
    await modelApi.setDefaultModel(id)
    await fetchDefaultModel()
    await fetchEnabledModels()
  }

  /** 测试供应商连接，返回 { success, message, latencyMs } */
  async function testConnection(providerId: string): Promise<{ success: boolean; message?: string; latencyMs?: number }> {
    try {
      const result = await modelApi.testProviderConnection(providerId) as { success: boolean; message?: string; errorMessage?: string; latencyMs?: number }
      return {
        success: result.success,
        message: result.success ? result.message : result.errorMessage,
        latencyMs: result.latencyMs,
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      return { success: false, message: msg }
    }
  }

  /** 测试模型可用性，返回 { success, message, latencyMs } */
  async function testModelAvailability(providerId: string, modelName: string): Promise<{ success: boolean; message?: string; latencyMs?: number }> {
    try {
      const result = await modelApi.testModel(providerId, modelName) as { success: boolean; message?: string; errorMessage?: string; latencyMs?: number }
      return {
        success: result.success,
        message: result.success ? result.message : result.errorMessage,
        latencyMs: result.latencyMs,
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      return { success: false, message: msg }
    }
  }

  /** 测试 Embedding 模型连通性，返回 { success, message, dimensions } */
  async function testEmbeddingModelAvailability(modelId: number): Promise<{ success: boolean; message?: string; dimensions?: number }> {
    try {
      const result = await modelApi.testEmbeddingModel(modelId) as { success: boolean; message?: string; dimensions?: number }
      return {
        success: result.success,
        message: result.message,
        dimensions: result.dimensions,
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      return { success: false, message: msg }
    }
  }

  /** 创建自定义 Provider */
  async function createCustomProvider(data: Record<string, unknown>): Promise<void> {
    await modelApi.createCustomProvider(data as Partial<ModelProvider>)
    await fetchProviders()
  }

  /** 删除自定义 Provider */
  async function deleteProvider(providerId: string): Promise<void> {
    await modelApi.deleteCustomProvider(providerId)
    await fetchProviders()
    await fetchEnabledModels()
  }

  /** 发现远端模型 */
  async function discoverModels(providerId: string): Promise<void> {
    await modelApi.discoverModels(providerId)
    await fetchProviders()
    await fetchEnabledModels()
  }

  /** 获取默认向量模型（仅全局管理员可访问） */
  async function fetchDefaultEmbeddingModel(): Promise<void> {
    const userStore = useUserStore()
    if (!userStore.isAdmin) {
      defaultEmbeddingModel.value = null
      return
    }
    try {
      defaultEmbeddingModel.value = await modelApi.getDefaultEmbeddingModel() as unknown as ModelConfig
    } catch {
      defaultEmbeddingModel.value = null
    }
  }

  /** 设置默认向量模型 */
  async function setDefaultEmbeddingModelById(id: number): Promise<void> {
    await modelApi.setDefaultEmbeddingModel(id)
    await fetchDefaultEmbeddingModel()
    await fetchEnabledModels()
  }

  return {
    enabledModels,
    allModels,
    providers,
    activeModel,
    defaultModel,
    defaultEmbeddingModel,
    loading,
    fetchEnabledModels,
    fetchProviders,
    fetchDefaultModel,
    fetchActiveModel,
    setActiveModelById,
    enableProvider,
    disableProvider,
    updateProvider,
    createModel,
    updateModel,
    deleteModel,
    toggleModelEnabled,
    setDefaultModel,
    testConnection,
    testModelAvailability,
    testEmbeddingModelAvailability,
    createCustomProvider,
    deleteProvider,
    discoverModels,
    fetchDefaultEmbeddingModel,
    setDefaultEmbeddingModelById,
  }
})