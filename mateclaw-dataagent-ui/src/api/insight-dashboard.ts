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

/** 复制仪表盘 */
export function copy(id: string) {
  return api.post<InsightDashboard>(`${BASE_URL}/${id}/copy`)
}

/** 预览仪表盘（获取所有组件渲染数据，支持运行时筛选条件） */
export function preview(id: string, filterContext?: DashboardFilterContext) {
  return api.post<InsightComponentData[]>(`${BASE_URL}/${id}/preview`, filterContext ?? {})
}

/** 预览单个组件数据（编辑器即时验证） */
export function previewComponent(component: InsightComponent) {
  return api.post<InsightComponentData>(`${BASE_URL}/preview-component`, component)
}

/** SSE流式事件回调 */
export interface StreamAiChatCallbacks {
  /** 收到reasoning事件：AI思考过程增量 */
  onReasoning?: (text: string) => void
  /** 收到content事件：AI最终结果文本增量 */
  onContent: (text: string) => void
  /** 收到result事件：最终仪表盘数据 */
  onResult: (dashboard: InsightDashboard) => void
  /** 收到error事件 */
  onError: (message: string) => void
}

/**
 * AI助手对话（流式SSE）
 *
 * SSE事件类型：
 * - reasoning: AI思考过程增量
 * - content: AI最终结果文本增量
 * - tool_result: 工具调用结果
 * - result: 最终仪表盘数据（JSON格式）
 * - error: 错误信息
 *
 * @param data 请求参数
 * @param callbacks 事件回调
 * @returns 关闭SSE连接的函数
 */
export function streamAiChat(
  data: InsightDashboardAiChatInput,
  callbacks: StreamAiChatCallbacks,
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
      callbacks.onError(`请求失败: ${response.status}`)
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      callbacks.onError('无法读取响应流')
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''
    // SSE事件状态，需跨chunk保持
    let currentEvent = ''
    let currentData = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 处理buffer中剩余内容
        if (currentEvent && currentData) {
          handleSseEvent(currentEvent, currentData, callbacks)
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })

      // 按双换行分割SSE事件（每个事件以空行结尾）
      const events = buffer.split('\n\n')
      // 最后一段可能不完整，保留在buffer中
      buffer = events.pop() || ''

      for (const eventBlock of events) {
        currentEvent = ''
        currentData = ''
        const lines = eventBlock.split('\n')
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            // 多行data时用换行拼接
            const dataLine = line.substring(5)
            currentData = currentData ? currentData + '\n' + dataLine : dataLine
          }
        }
        if (currentEvent && currentData) {
          handleSseEvent(currentEvent, currentData, callbacks)
        }
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      callbacks.onError(err.message || '网络异常')
    }
  })

  return () => controller.abort()
}

/** 处理SSE事件 */
function handleSseEvent(
  event: string,
  data: string,
  callbacks: StreamAiChatCallbacks,
): void {
  switch (event) {
    case 'reasoning':
      callbacks.onReasoning?.(data)
      break
    case 'content':
      callbacks.onContent(data)
      break
    case 'tool_result':
      // 工具调用结果，暂不展示
      break
    case 'result': {
      try {
        const dashboard = JSON.parse(data) as InsightDashboard
        callbacks.onResult(dashboard)
      } catch {
        callbacks.onError('解析仪表盘数据失败')
      }
      break
    }
    case 'error':
      callbacks.onError(data)
      break
    default:
      break
  }
}
