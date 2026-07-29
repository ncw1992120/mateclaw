import api from './index'

/** 定时任务 API 基础路径（通过 dataagent 后端 SDK 代理） */
const BASE_URL = '/dataagent/api/v1/cron-jobs'

/** 定时任务 DTO（与后端 CronJobDTO 一一对应） */
export interface CronJob {
  id: number | string
  workspaceId: number | string
  name: string
  cronExpression: string
  timezone: string
  agentId: number | string
  agentName: string
  taskType: string
  triggerMessage: string
  requestBody: string
  enabled: boolean
  nextRunTime: string | null
  lastRunTime: string | null
  createTime: string
  updateTime: string
  channelId: number | string | null
  channelName: string | null
  deliveryConfig: DeliveryConfig | null
  lastDeliveryStatus: string
  lastDeliveryError: string | null
}

/** 投递配置 */
export interface DeliveryConfig {
  targetId?: string
  threadId?: string
  accountId?: string
  userId?: string
  suppressAgentReply?: boolean
}

/** 创建/更新定时任务请求体 */
export interface CronJobForm {
  name: string
  cronExpression: string
  timezone?: string
  agentId: number | string
  taskType: string
  triggerMessage?: string
  requestBody?: string
  enabled?: boolean
  channelId?: number | string | null
  deliveryConfig?: DeliveryConfig | null
}

/** 获取定时任务列表 */
export function listCronJobs() {
  return api.get<CronJob[]>(BASE_URL)
}

/** 获取定时任务详情 */
export function getCronJob(id: number | string) {
  return api.get<CronJob>(`${BASE_URL}/${id}`)
}

/** 创建定时任务 */
export function createCronJob(data: CronJobForm) {
  return api.post<CronJob>(BASE_URL, data)
}

/** 更新定时任务 */
export function updateCronJob(id: number | string, data: CronJobForm) {
  return api.put<CronJob>(`${BASE_URL}/${id}`, data)
}

/** 删除定时任务 */
export function deleteCronJob(id: number | string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 启用/禁用定时任务 */
export function toggleCronJob(id: number | string, enabled: boolean) {
  return api.put(`${BASE_URL}/${id}/toggle`, null, { params: { enabled } })
}

/** 立即执行定时任务 */
export function runCronJobNow(id: number | string) {
  return api.post(`${BASE_URL}/${id}/run`)
}
