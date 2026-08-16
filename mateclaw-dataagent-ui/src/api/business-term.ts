import api from './index'
import type { BusinessTerm, BusinessTermCreateRequest, BusinessTermReferenceOptions, BusinessTermSearchResult, BusinessTermUpdateRequest } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/business-terms'

/** 列出所有已存在术语数据的租户编码 */
export function listTenantCodes() {
  return api.get<string[]>(`${BASE_URL}/tenants`)
}

/** 查询术语列表（管理界面传 includeDisabled=true 以展示停用术语） */
export function list(tenantCode: string, category?: string, includeDisabled = false) {
  return api.get<BusinessTerm[]>(BASE_URL, {
    params: { tenantCode, category, includeDisabled },
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

/** 查询关联引用候选（跨数据源的指标/维度） */
export function referenceOptions(keyword?: string, limit = 20) {
  return api.get<BusinessTermReferenceOptions>(`${BASE_URL}/reference-options`, {
    params: { keyword, limit },
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
