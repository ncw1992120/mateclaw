import api from './index'
import type { HelpCategory, HelpCategoryRequest, HelpDocument, HelpDocumentRequest, HelpSearchResult, HelpFeedbackRequest, HelpFeedback, HelpFeedbackSummary } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/help-center'

/** 获取分类树 */
export function listCategoryTree() {
  return api.get<HelpCategory[]>(`${BASE_URL}/categories/tree`)
}

/** 创建分类 */
export function createCategory(data: HelpCategoryRequest) {
  return api.post<HelpCategory>(`${BASE_URL}/categories`, data)
}

/** 更新分类 */
export function updateCategory(id: string, data: HelpCategoryRequest) {
  return api.put<HelpCategory>(`${BASE_URL}/categories/${id}`, data)
}

/** 删除分类 */
export function deleteCategory(id: string) {
  return api.delete(`${BASE_URL}/categories/${id}`)
}

/** 获取分类下的文档列表 */
export function listDocuments(categoryId: string) {
  return api.get<HelpDocument[]>(`${BASE_URL}/categories/${categoryId}/documents`)
}

/** 获取文档详情 */
export function getDocument(id: string) {
  return api.get<HelpDocument>(`${BASE_URL}/documents/${id}`)
}

/** 创建文档 */
export function createDocument(data: HelpDocumentRequest) {
  return api.post<HelpDocument>(`${BASE_URL}/documents`, data)
}

/** 更新文档 */
export function updateDocument(id: string, data: HelpDocumentRequest) {
  return api.put<HelpDocument>(`${BASE_URL}/documents/${id}`, data)
}

/** 删除文档 */
export function deleteDocument(id: string) {
  return api.delete(`${BASE_URL}/documents/${id}`)
}

/** 发布文档 */
export function publishDocument(id: string) {
  return api.post<HelpDocument>(`${BASE_URL}/documents/${id}/publish`)
}

/** 取消发布文档 */
export function unpublishDocument(id: string) {
  return api.post<HelpDocument>(`${BASE_URL}/documents/${id}/unpublish`)
}

/** 搜索文档 */
export function searchDocuments(keyword: string, limit?: number) {
  return api.get<HelpSearchResult[]>(`${BASE_URL}/documents/search`, { params: { keyword, limit } })
}

/** 获取相关文档推荐 */
export function getRelatedDocuments(id: string, limit?: number) {
  return api.get<HelpDocument[]>(`${BASE_URL}/documents/${id}/related`, { params: { limit } })
}

/** 提交文档反馈 */
export function submitFeedback(id: string, data: HelpFeedbackRequest) {
  return api.post<HelpFeedback>(`${BASE_URL}/documents/${id}/feedback`, data)
}

/** 获取文档反馈汇总 */
export function getFeedbackSummary(id: string) {
  return api.get<HelpFeedbackSummary>(`${BASE_URL}/documents/${id}/feedback/summary`)
}
