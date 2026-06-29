import api from './index'
import type { BusinessTerm, BusinessTermCreateRequest, BusinessTermSearchResult, BusinessTermUpdateRequest } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/business-terms'

/** 列出所有已存在术语数据的租户编码 */
export function listTenantCodes() {
  return api.get<string[]>(`${BASE_URL}/tenants`)
}

/** 查询术语列表 */
export function list(tenantCode: string, category?: string) {
  return api.get<BusinessTerm[]>(BASE_URL, {
    params: { tenantCode, category },
  })
}

/** 获取术语详情 */
export function get(id: string) {
  return api.get<BusinessTerm>(`${BASE_URL}/${id}`)
}

/** 创建术语 */
export function create(data: BusinessTermCreateRequest) {
  return api.post<BusinessTerm>(BASE_URL, data)
}

/** 更新术语 */
export function update(id: string, data: BusinessTermUpdateRequest) {
  return api.put<BusinessTerm>(`${BASE_URL}/${id}`, data)
}

/** 删除术语 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 按租户删除所有术语 */
export function removeByTenantCode(tenantCode: string) {
  return api.delete(`${BASE_URL}`, {
    params: { tenantCode },
  })
}

/** 启用术语 */
export function enable(id: string) {
  return api.put(`${BASE_URL}/${id}/enable`)
}

/** 停用术语 */
export function disable(id: string) {
  return api.put(`${BASE_URL}/${id}/disable`)
}

/** 关键词搜索术语 */
export function search(tenantCode: string, keyword: string) {
  return api.get<BusinessTerm[]>(`${BASE_URL}/search`, {
    params: { tenantCode, keyword },
  })
}

/** 语义混合检索术语 */
export function semanticSearch(tenantCode: string, query: string, topK = 10, threshold = 0.3) {
  return api.get<BusinessTermSearchResult>(`${BASE_URL}/semantic-search`, {
    params: { tenantCode, query, topK, threshold },
  })
}

/** 为租户下的所有术语生成嵌入向量并写入 ES 索引 */
export function embedAndIndex(tenantCode: string) {
  return api.post<number>(`${BASE_URL}/embed`, null, {
    params: { tenantCode },
  })
}

/** 重建租户的术语 ES 索引 */
export function rebuildEsIndex(tenantCode: string) {
  return api.post<number>(`${BASE_URL}/rebuild-es`, null, {
    params: { tenantCode },
  })
}
