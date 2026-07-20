import api from './index'
import type { InsightDashboard, InsightDashboardCreateInput, InsightDashboardUpdateInput, InsightDashboardAiChatInput, InsightComponentData, InsightComponent, DashboardFilterContext } from '@/types'

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

/**
 * AI助手对话（流式SSE）
 * <p>
 * 通过EventSource接收SSE流式事件，事件类型：
 * - content: AI推理文本增量
 * - result: 最终仪表盘数据（JSON格式）
 * - error: 错误信息
 *
 * @param data 请求参数
 * @param onContent 收到content事件的回调
 * @param onResult 收到result事件的回调
 * @param onError 收到error事件的回调
 * @returns 关闭SSE连接的函数
 */
export function streamAiChat(
  data: InsightDashboardAiChatInput,
  onContent: (text: string) => void,
  onResult: (dashboard: InsightDashboard) => void,
  onError: (message: string) => void,
): () => void {
  const token = localStorage.getItem('token')
  const workspaceIdRaw = localStorage.getItem('workspaceId')
  let workspaceId = ''
  if (workspaceIdRaw) {
    try {
      workspaceId = String(JSON.parse(workspaceIdRaw))
    } catch {
      // ignore
    }
  }

  // 使用fetch发送POST请求并接收SSE流
  const url = `${BASE_URL}/ai-chat/stream`
  const controller = new AbortController()

  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Workspace-Id': workspaceId,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      onError(`请求失败: ${response.status}`)
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      onError('无法读取响应流')
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })

      // 解析SSE事件
      const lines = buffer.split('\n')
      buffer = ''

      let currentEvent = ''
      let currentData = ''

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          currentData = line.substring(5).trim()
        } else if (line === '') {
          // 空行表示事件结束
          if (currentEvent && currentData) {
            handleSseEvent(currentEvent, currentData, onContent, onResult, onError)
          }
          currentEvent = ''
          currentData = ''
        } else {
          // 不完整的行，放回buffer
          if (i === lines.length - 1) {
            buffer = line
          }
        }
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError(err.message || '网络异常')
    }
  })

  return () => controller.abort()
}

/** 处理SSE事件 */
function handleSseEvent(
  event: string,
  data: string,
  onContent: (text: string) => void,
  onResult: (dashboard: InsightDashboard) => void,
  onError: (message: string) => void,
): void {
  switch (event) {
    case 'content':
      onContent(data)
      break
    case 'result': {
      try {
        const dashboard = JSON.parse(data) as InsightDashboard
        onResult(dashboard)
      } catch {
        onError('解析仪表盘数据失败')
      }
      break
    }
    case 'error':
      onError(data)
      break
    default:
      // 忽略其他事件
      break
  }
}
