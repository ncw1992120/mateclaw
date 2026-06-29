import api from './index'
import type { Workspace, WorkspaceMember } from '@/types'

/** 工作区 API 基础路径（通过 dataagent 后端 SDK 代理） */
const BASE_URL = '/dataagent/api/v1/workspaces'

/** 获取当前用户可见的工作区列表 */
export function listWorkspaces() {
  return api.get<Workspace[]>(BASE_URL)
}

/** 获取工作区详情 */
export function getWorkspace(id: number | string) {
  return api.get<Workspace>(`${BASE_URL}/${id}`)
}

/** 创建工作区 */
export function createWorkspace(data: Partial<Workspace>) {
  return api.post<Workspace>(BASE_URL, data)
}

/** 更新工作区 */
export function updateWorkspace(id: number | string, data: Partial<Workspace>) {
  return api.put<Workspace>(`${BASE_URL}/${id}`, data)
}

/** 删除工作区 */
export function deleteWorkspace(id: number | string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 获取工作区成员列表 */
export function listWorkspaceMembers(workspaceId: number | string) {
  return api.get<WorkspaceMember[]>(`${BASE_URL}/${workspaceId}/members`)
}

/** 添加工作区成员 */
export function addWorkspaceMember(
  workspaceId: number | string,
  data: { username: string; nickname?: string; password?: string; role?: string },
) {
  return api.post<WorkspaceMember>(`${BASE_URL}/${workspaceId}/members`, data)
}

/** 更新成员角色 */
export function updateWorkspaceMemberRole(
  workspaceId: number | string,
  userId: number | string,
  role: string,
) {
  return api.put<WorkspaceMember>(`${BASE_URL}/${workspaceId}/members/${userId}`, { role })
}

/** 移除工作区成员 */
export function removeWorkspaceMember(workspaceId: number | string, userId: number | string) {
  return api.delete(`${BASE_URL}/${workspaceId}/members/${userId}`)
}
