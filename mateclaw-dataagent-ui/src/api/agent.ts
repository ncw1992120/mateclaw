import api from './index'
import type {
  Agent,
  AgentProviderPreference,
  AgentSkillBinding,
  AgentToolBinding,
  AvailableKnowledgeBase,
  AvailableTool,
  ModelProvider,
  Skill,
} from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/agents'

/** 查询 Agent 列表 */
export function list(workspaceId: number) {
  return api.get<Agent[]>(BASE_URL, { params: { workspaceId } })
}

/** 查询 Agent 详情 */
export function get(id: number | string) {
  return api.get<Agent>(`${BASE_URL}/${id}`)
}

/** 创建 Agent */
export function create(data: Partial<Agent>) {
  return api.post<Agent>(BASE_URL, data)
}

/** 更新 Agent */
export function update(id: number | string, data: Partial<Agent>) {
  return api.put<Agent>(`${BASE_URL}/${id}`, data)
}

/** 删除 Agent */
export function remove(id: number | string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 应用模板创建 Agent */
export function applyTemplate(templateId: number, workspaceId: number) {
  return api.post(`${BASE_URL}/apply-template`, { templateId, workspaceId })
}

/** 可绑定技能列表（已启用） */
export function listAvailableSkills(workspaceId?: number) {
  return api.get<Skill[]>(`${BASE_URL}/skills/available`, { params: { workspaceId } })
}

/** 可绑定工具列表（含内置 + MCP） */
export function listAvailableTools() {
  return api.get<AvailableTool[]>(`${BASE_URL}/tools/available`)
}

/** 可绑定 Provider 列表（已启用） */
export function listAvailableProviders() {
  return api.get<ModelProvider[]>(`${BASE_URL}/providers/available`)
}

/** 可绑定知识库列表 */
export function listAvailableKnowledgeBases(workspaceId = 1) {
  return api.get<AvailableKnowledgeBase[]>(`${BASE_URL}/knowledge-bases/available`, {
    params: { workspaceId },
  })
}

/** 查询 Agent 已绑定的技能 */
export function listAgentSkills(agentId: number | string) {
  return api.get<AgentSkillBinding[]>(`${BASE_URL}/${agentId}/skills`)
}

/** 设置 Agent 的技能绑定（替换模式） */
export function setAgentSkills(agentId: number | string, skillIds: number[]) {
  return api.put<void>(`${BASE_URL}/${agentId}/skills`, skillIds)
}

/** 查询 Agent 已绑定的工具 */
export function listAgentTools(agentId: number | string) {
  return api.get<AgentToolBinding[]>(`${BASE_URL}/${agentId}/tools`)
}

/** 设置 Agent 的工具绑定（替换模式） */
export function setAgentTools(agentId: number | string, toolNames: string[]) {
  return api.put<void>(`${BASE_URL}/${agentId}/tools`, toolNames)
}

/** 查询 Agent 偏好供应商 */
export function listAgentProviderPreferences(agentId: number | string) {
  return api.get<AgentProviderPreference[]>(`${BASE_URL}/${agentId}/provider-preferences`)
}

/** 设置 Agent 偏好供应商（按顺序替换模式） */
export function setAgentProviderPreferences(agentId: number | string, providerIds: string[]) {
  return api.put<void>(`${BASE_URL}/${agentId}/provider-preferences`, providerIds)
}
