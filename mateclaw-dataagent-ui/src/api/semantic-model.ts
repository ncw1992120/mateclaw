import api from './index'
import type { SemanticModel, SemanticModelCreateRequest, SemanticModelUpdateRequest } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/semantic-models'

/** 查询语义模型列表 */
export function list(datasourceId: string, tableNames?: string) {
  return api.get<SemanticModel[]>(BASE_URL, {
    params: { datasourceId, tableNames },
  })
}

/** 获取语义模型详情 */
export function get(id: string) {
  return api.get<SemanticModel>(`${BASE_URL}/${id}`)
}

/** 创建语义模型 */
export function create(data: SemanticModelCreateRequest) {
  return api.post<SemanticModel>(BASE_URL, data)
}

/** 更新语义模型 */
export function update(id: string, data: SemanticModelUpdateRequest) {
  return api.put<SemanticModel>(`${BASE_URL}/${id}`, data)
}

/** 删除语义模型 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 启用语义模型 */
export function enable(id: string) {
  return api.put(`${BASE_URL}/${id}/enable`)
}

/** 停用语义模型 */
export function disable(id: string) {
  return api.put(`${BASE_URL}/${id}/disable`)
}

/** 关键词搜索语义模型 */
export function search(datasourceId: string, keyword: string) {
  return api.get<SemanticModel[]>(`${BASE_URL}/search`, {
    params: { datasourceId, keyword },
  })
}

/** 从物理 Schema 自动初始化语义模型 */
export function autoInit(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/auto-init`, null, {
    params: { datasourceId },
  })
}

/** 从 Aloudata 指标平台同步语义模型 */
export function syncFromAloudata(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/sync-aloudata`, null, { params: { datasourceId } })
}
