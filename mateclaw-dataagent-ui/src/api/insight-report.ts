import api from './index'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/insight/dashboards'

/** 同步生成报告 */
export function generateReport(dashboardId: string) {
  return api.post<string>(`${BASE_URL}/${dashboardId}/report`)
}

/**
 * SSE 流式生成报告
 * @param dashboardId 仪表盘 ID
 * @param onContent 内容回调（每个 chunk 触发一次）
 * @param onError 错误回调
 * @param onComplete 完成回调
 * @returns AbortController（可调用 .abort() 取消）
 */
export function streamReport(
  dashboardId: string,
  onContent: (chunk: string) => void,
  onError?: (err: string) => void,
  onComplete?: () => void
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token') ?? ''

  fetch(`${BASE_URL}/${dashboardId}/report/stream`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        onError?.(`HTTP ${response.status}`)
        return
      }
      const reader = response.body?.getReader()
      if (!reader) {
        onError?.('无法读取响应流')
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
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        let pendingError = false
        for (const line of lines) {
          if (line.startsWith('event:error')) {
            pendingError = true
          } else if (line.startsWith('data:')) {
            const data = line.slice(5)
            if (pendingError) {
              onError?.(data)
              pendingError = false
            } else {
              onContent(data)
            }
          }
        }
      }
      onComplete?.()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err.message)
      }
    })

  return controller
}
