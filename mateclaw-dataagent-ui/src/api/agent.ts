import api from './index'
import type { Agent } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/agents'

/** 查询 Agent 列表 */
export function list(workspaceId: number) {
  return api.get<Agent[]>(BASE_URL, { params: { workspaceId } })
}

/** 查询 Agent 详情 */
export function get(id: number) {
  return api.get<Agent>(`${BASE_URL}/${id}`)
}

/** 创建 Agent */
export function create(data: Partial<Agent>) {
  return api.post<Agent>(BASE_URL, data)
}

/** 更新 Agent */
export function update(id: number, data: Partial<Agent>) {
  return api.put<Agent>(`${BASE_URL}/${id}`, data)
}

/** 删除 Agent */
export function remove(id: number) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 应用模板创建 Agent */
export function applyTemplate(templateId: number, workspaceId: number) {
  return api.post(`${BASE_URL}/apply-template`, { templateId, workspaceId })
}
