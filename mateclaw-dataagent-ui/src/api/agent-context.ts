import api from './index'
import type { WorkspaceFile } from '@/types'

/** API 路径前缀（按 agentId 拼接） */
function baseUrl(agentId: number | string): string {
  return `/dataagent/api/v1/agents/${agentId}/context`
}

/** 列出 Agent 的所有工作区文件（不含内容） */
export function listFiles(agentId: number | string) {
  return api.get<WorkspaceFile[]>(`${baseUrl(agentId)}/files`)
}

/** 读取单个工作区文件内容 */
export function getFile(agentId: number | string, filename: string) {
  return api.get<WorkspaceFile>(`${baseUrl(agentId)}/files/${filename}`)
}

/** 创建或更新工作区文件 */
export function saveFile(agentId: number | string, filename: string, content: string) {
  return api.put<WorkspaceFile>(`${baseUrl(agentId)}/files/${filename}`, { content })
}

/** 删除工作区文件 */
export function removeFile(agentId: number | string, filename: string) {
  return api.delete(`${baseUrl(agentId)}/files/${filename}`)
}

/** 获取启用的系统提示文件名列表（有序） */
export function getPromptFiles(agentId: number | string) {
  return api.get<string[]>(`${baseUrl(agentId)}/prompt-files`)
}

/** 设置启用的系统提示文件列表（有序） */
export function setPromptFiles(agentId: number | string, files: string[]) {
  return api.put<void>(`${baseUrl(agentId)}/prompt-files`, { files })
}
