import api from './index'
import type {
  HubSkillInfo,
  Skill,
  SkillInstallRequest,
  SkillInstallTask,
  SkillPage,
} from '@/types'

/** 技能 CRUD 路径 */
const BASE_URL = '/dataagent/api/v1/skills'

/** 技能安装路径 */
const INSTALL_URL = '/dataagent/api/v1/skills/install'

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

// ==================== 技能导入 ====================

/** 在 ClawHub 市场搜索技能 */
export function searchHub(query: string, limit = 20) {
  return api.get<HubSkillInfo[]>(`${INSTALL_URL}/hub/search`, {
    params: { q: query, limit },
  })
}

/** 启动一个异步安装任务（GitHub URL / ClawHub 市场） */
export function startInstall(data: SkillInstallRequest) {
  return api.post<SkillInstallTask>(`${INSTALL_URL}/start`, data)
}

/** 查询安装任务状态 */
export function getInstallStatus(taskId: string) {
  return api.get<SkillInstallTask>(`${INSTALL_URL}/status/${taskId}`)
}

/** 取消安装任务 */
export function cancelInstall(taskId: string) {
  return api.post<void>(`${INSTALL_URL}/cancel/${taskId}`)
}

/**
 * 上传 ZIP 包安装技能
 *
 * @param file         ZIP 文件
 * @param options      其它安装选项
 */
export function installFromZip(
  file: File,
  options: { enable?: boolean; overwrite?: boolean; targetName?: string; workspaceId?: number } = {},
) {
  const form = new FormData()
  form.append('file', file)
  return api.post<Record<string, unknown>>(`${INSTALL_URL}/upload`, form, {
    params: {
      enable: options.enable !== false,
      overwrite: options.overwrite === true,
      targetName: options.targetName,
      workspaceId: options.workspaceId,
    },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 通过名称卸载技能 */
export function uninstallByName(skillName: string, workspaceId?: number) {
  return api.delete<{ message: string }>(`${INSTALL_URL}/${encodeURIComponent(skillName)}`, {
    params: { workspaceId },
  })
}
