import api from './index'
import type { InsightDashboard, InsightDashboardCreateInput, InsightDashboardUpdateInput, InsightComponentData } from '@/types'

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

/** 预览仪表盘（获取所有组件渲染数据） */
export function preview(id: string) {
  return api.post<InsightComponentData[]>(`${BASE_URL}/${id}/preview`)
}
