import api from './index'
import type { ModelConfig, ModelProvider } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/models'

/** 获取启用的 Provider 列表 */
export function listEnabledProviders() {
  return api.get<ModelProvider[]>(`${BASE_URL}`)
}

/** 获取 Provider 全量目录（含未启用） */
export function listCatalog() {
  return api.get<ModelProvider[]>(`${BASE_URL}/catalog`)
}

/** 启用 Provider */
export function enableProvider(providerId: string) {
  return api.post(`${BASE_URL}/${providerId}/enable`)
}

/** 禁用 Provider */
export function disableProvider(providerId: string) {
  return api.post(`${BASE_URL}/${providerId}/disable`)
}

/** 获取启用模型列表 */
export function listEnabledModels() {
  return api.get<ModelConfig[]>(`${BASE_URL}/enabled`)
}

/** 获取所有已启用的模型（含 chat 和 embedding 类型） */
export function listAllEnabledModels() {
  return api.get<ModelConfig[]>(`${BASE_URL}/all-enabled`)
}

/** 获取所有模型（含启用和禁用） */
export function listAllModels() {
  return api.get<ModelConfig[]>(`${BASE_URL}/all`)
}

/** 获取默认模型 */
export function getDefaultModel() {
  return api.get<ModelConfig>(`${BASE_URL}/default`)
}

/** 获取当前激活模型 */
export function getActiveModel() {
  return api.get<ModelConfig>(`${BASE_URL}/active`)
}

/** 设置当前激活模型 */
export function setActiveModel(modelId: number) {
  return api.put(`${BASE_URL}/active`, { modelId })
}

/** 更新 Provider 配置 */
export function updateProviderConfig(providerId: string, data: Partial<ModelProvider>) {
  return api.put(`${BASE_URL}/${providerId}/config`, data)
}

/** 创建自定义 Provider */
export function createCustomProvider(data: Partial<ModelProvider>) {
  return api.post(`${BASE_URL}/custom-providers`, data)
}

/** 删除自定义 Provider */
export function deleteCustomProvider(providerId: string) {
  return api.delete(`${BASE_URL}/custom-providers/${providerId}`)
}

/** 向 Provider 添加模型 */
export function addModelToProvider(providerId: string, data: Partial<ModelConfig>) {
  return api.post(`${BASE_URL}/${providerId}/models`, data)
}

/** 从 Provider 删除模型 */
export function removeModelFromProvider(providerId: string, modelId: string) {
  return api.delete(`${BASE_URL}/${providerId}/models/${modelId}`)
}

/** 获取模型详情 */
export function getModel(id: number) {
  return api.get<ModelConfig>(`${BASE_URL}/${id}`)
}

/** 创建模型 */
export function createModel(data: Partial<ModelConfig>) {
  return api.post<ModelConfig>(`${BASE_URL}`, data)
}

/** 更新模型 */
export function updateModel(id: number, data: Partial<ModelConfig>) {
  return api.put<ModelConfig>(`${BASE_URL}/${id}`, data)
}

/** 删除模型 */
export function deleteModel(id: number) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 设置默认模型 */
export function setDefaultModel(id: number) {
  return api.post(`${BASE_URL}/${id}/default`)
}

/** 发现远端模型 */
export function discoverModels(providerId: string) {
  return api.post(`${BASE_URL}/${providerId}/discover`)
}

/** 批量添加发现的模型 */
export function applyDiscoveredModels(providerId: string, modelNames: string[]) {
  return api.post(`${BASE_URL}/${providerId}/discover/apply`, { modelNames })
}

/** 测试供应商连接 */
export function testProviderConnection(providerId: string) {
  return api.post(`${BASE_URL}/${providerId}/test-connection`)
}

/** 测试单个模型可用性 */
export function testModel(providerId: string, modelName: string) {
  return api.post<TestResult>(`${BASE_URL}/${providerId}/models/${modelName}/test`)
}

/** 测试 Embedding 模型连通性 */
export function testEmbeddingModel(modelId: number) {
  return api.post(`${BASE_URL}/embedding/${modelId}/test`)
}

/** 按类型筛选模型 */
export function listModelsByType(modelType: string, modality?: string) {
  return api.get<ModelConfig[]>(`${BASE_URL}/by-type`, { params: { modelType, modality } })
}

/** 获取默认向量模型 */
export function getDefaultEmbeddingModel() {
  return api.get<ModelConfig>(`${BASE_URL}/default-embedding`)
}

/**
 * 设置默认向量模型
 * 后端走 is_default 字段统一管理，与对话模型共用同一存储，通过 model_type 区分互斥。
 */
export function setDefaultEmbeddingModel(id: number) {
  return api.post<ModelConfig>(`${BASE_URL}/${id}/default-embedding`)
}

/** 测试 Rerank 模型连通性 */
export function testRerankModel(modelId: number) {
  return api.post(`${BASE_URL}/rerank/${modelId}/test`)
}

/** 获取默认 Rerank 模型 */
export function getDefaultRerankModel() {
  return api.get<ModelConfig>(`${BASE_URL}/rerank/default`)
}

/**
 * 设置默认 Rerank 模型
 * 后端走 is_default 字段统一管理，通过 model_type 区分互斥。
 */
export function setDefaultRerankModel(id: number) {
  return api.post<ModelConfig>(`${BASE_URL}/${id}/default-rerank`)
}
