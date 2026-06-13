import api from './index'
import type { Skill, SkillPage } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/skills'

/** 技能分页查询 */
export function page(params: {
  page?: number
  size?: number
  keyword?: string
  skillType?: string
  enabled?: boolean
  workspaceId?: number
} = {}) {
  return api.get<SkillPage>(BASE_URL, { params })
}

/** 获取所有技能列表（不分页） */
export function list(workspaceId?: number) {
  return api.get<Skill[]>(`${BASE_URL}/all`, { params: { workspaceId } })
}

/** 获取已启用技能列表 */
export function listEnabled(workspaceId?: number) {
  return api.get<Skill[]>(`${BASE_URL}/enabled`, { params: { workspaceId } })
}

/** 获取技能详情 */
export function get(id: number) {
  return api.get<Skill>(`${BASE_URL}/${id}`)
}

/** 创建技能 */
export function create(data: Partial<Skill>) {
  return api.post<Skill>(BASE_URL, data)
}

/** 更新技能 */
export function update(id: number, data: Partial<Skill>) {
  return api.put<Skill>(`${BASE_URL}/${id}`, data)
}

/** 删除技能 */
export function remove(id: number) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 切换技能启停状态 */
export function toggle(id: number, enabled: boolean) {
  return api.put<Skill>(`${BASE_URL}/${id}/toggle`, null, { params: { enabled } })
}
