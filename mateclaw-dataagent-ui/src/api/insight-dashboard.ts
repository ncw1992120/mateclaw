import api from './index'
import type { InsightDashboard, InsightDashboardCreateInput, InsightDashboardUpdateInput, InsightDashboardGenerateInput, InsightDashboardModifyInput, InsightDashboardAiChatInput, InsightComponentData, InsightComponent, DashboardFilterContext } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/insight/dashboards'

/** 查询仪表盘列表 */
export function list() {
  return api.get<InsightDashboard[]>(BASE_URL)
}

/** 查询仪表盘详情 */
export function get(id: string) {
  return api.get<InsightDashboard>(`${BASE_URL}/${id}`)
}

/** 创建仪表盘 */
export function create(data: InsightDashboardCreateInput) {
  return api.post<InsightDashboard>(BASE_URL, data)
}

/** 更新仪表盘（含保存 Schema） */
export function update(id: string, data: InsightDashboardUpdateInput) {
  return api.put<InsightDashboard>(`${BASE_URL}/${id}`, data)
}

/** 删除仪表盘 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 预览仪表盘（获取所有组件渲染数据，支持运行时筛选条件） */
export function preview(id: string, filterContext?: DashboardFilterContext) {
  return api.post<InsightComponentData[]>(`${BASE_URL}/${id}/preview`, filterContext ?? {})
}

/** 预览单个组件数据（编辑器即时验证） */
export function previewComponent(component: InsightComponent) {
  return api.post<InsightComponentData>(`${BASE_URL}/preview-component`, component)
}

/** AI助手对话（统一AI生成和AI修改） */
export function aiChat(data: InsightDashboardAiChatInput) {
  return api.post<InsightDashboard>(`${BASE_URL}/ai-chat`, data)
}

/** AI生成仪表盘 */
export function generate(data: InsightDashboardGenerateInput) {
  return api.post<InsightDashboard>(`${BASE_URL}/generate`, data)
}

/** AI对话修改仪表盘 */
export function modify(data: InsightDashboardModifyInput) {
  return api.post<InsightDashboard>(`${BASE_URL}/modify`, data)
}
