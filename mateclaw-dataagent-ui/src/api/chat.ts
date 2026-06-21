import api from './index'
import type { ChatRequest, SseEvent } from '@/types'

const CHAT_URL = '/dataagent/api/v1/chat'
const STREAM_URL = '/dataagent/api/v1/chat/stream'
const STOP_URL = '/dataagent/api/v1/chat/stream'

const STREAM_TIMEOUT_MS = 120_000

export function chat(data: ChatRequest) {
  return api.post(CHAT_URL, data)
}

export function stopStream(conversationId: string) {
  return api.delete(`${STOP_URL}/${conversationId}`)
}

/**
 * 标准 SSE 协议解析器
 * <p>
 * 基于 RFC 6202 / WHATWG EventSource 规范，支持 event/id/data 三字段。
 * 内部将 \r\n 统一规范化为 \n，确保 Windows/Unix 跨平台兼容。
 */
class SSEParser {
  private buffer = ''

  parse(chunk: string): SseEvent[] {
    this.buffer += chunk
    const events: SseEvent[] = []

    const parts = this.buffer.split(/\r?\n\r?\n/)
    this.buffer = parts.pop() || ''

    for (const part of parts) {
      const event = this.parseEvent(part)
      if (event) {
        events.push(event)
      }
    }

    return events
  }

  flush(): SseEvent[] {
    if (!this.buffer.trim()) return []
    const event = this.parseEvent(this.buffer)
    this.buffer = ''
    return event ? [event] : []
  }

  private parseEvent(part: string): SseEvent | null {
    const lines = part.split(/\r?\n/)
    let eventType = ''
    let dataStr = ''
    let hasData = false
    let eventId: string | undefined

    for (const line of lines) {
      if (!line.trim()) continue

      const colonIndex = line.indexOf(':')
      if (colonIndex === -1) continue

      const key = line.slice(0, colonIndex).trim()
      const value = line.slice(colonIndex + 1).trim()

      if (key === 'event') {
        eventType = value
      } else if (key === 'id') {
        eventId = value
      } else if (key === 'data') {
        hasData = true
        dataStr = value
      }
    }

    if (!hasData) return null

    let parsedData: Record<string, unknown> | string
    try {
      parsedData = JSON.parse(dataStr)
    } catch {
      parsedData = dataStr
    }

    return {
      event: eventType || 'message',
      data: parsedData,
      id: eventId,
    }
  }
}

export interface StreamOptions {
  /** Called when the last event id is updated */
  onLastEventId?: (id: string) => void
  /** Set of already-seen event ids for dedup */
  seenEventIds?: Set<string>
}

export async function* streamChat(
  agentId: number | string,
  message: string,
  conversationId: string,
  modelProvider?: string,
  modelName?: string,
  options?: StreamOptions,
  datasourceIds?: string[]
): AsyncGenerator<SseEvent> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const body: Record<string, unknown> = { agentId, message, conversationId }
  if (modelProvider) {
    body.modelProvider = modelProvider
  }
  if (modelName) {
    body.modelName = modelName
  }
  if (datasourceIds && datasourceIds.length > 0) {
    body.datasourceIds = datasourceIds
  }

  const abortController = new AbortController()
  let timeoutTimer: ReturnType<typeof setTimeout> | null = null

  const resetTimeout = () => {
    if (timeoutTimer) clearTimeout(timeoutTimer)
    timeoutTimer = setTimeout(() => {
      abortController.abort()
    }, STREAM_TIMEOUT_MS)
  }

  try {
    const response = await fetch(STREAM_URL, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: abortController.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('ReadableStream not supported')
    }

    const decoder = new TextDecoder('utf-8')
    const parser = new SSEParser()

    resetTimeout()

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        resetTimeout()
        const chunk = decoder.decode(value, { stream: true })
        const events = parser.parse(chunk)
        for (const event of events) {
          if (event.id) {
            if (options?.seenEventIds?.has(event.id)) {
              continue
            }
            options?.seenEventIds?.add(event.id)
            options?.onLastEventId?.(event.id)
          }
          if (event.event === 'heartbeat') {
            resetTimeout()
            continue
          }
          yield event
        }
      }

      const remaining = decoder.decode()
      if (remaining) {
        const events = parser.parse(remaining)
        for (const event of events) {
          if (event.id) {
            if (options?.seenEventIds?.has(event.id)) continue
            options?.seenEventIds?.add(event.id)
            options?.onLastEventId?.(event.id)
          }
          if (event.event === 'heartbeat') continue
          yield event
        }
      }

      const flushEvents = parser.flush()
      for (const event of flushEvents) {
        if (event.id) {
          if (options?.seenEventIds?.has(event.id)) continue
          options?.seenEventIds?.add(event.id)
          options?.onLastEventId?.(event.id)
        }
        if (event.event === 'heartbeat') continue
        yield event
      }
    } catch (e) {
      if (e instanceof Error && e.name === 'AbortError') {
        return
      }
      throw e
    } finally {
      reader.releaseLock()
    }
  } finally {
    if (timeoutTimer) {
      clearTimeout(timeoutTimer)
      timeoutTimer = null
    }
  }
}

export async function* reconnectStream(
  conversationId: string,
  lastEventId: string,
  options?: StreamOptions
): AsyncGenerator<SseEvent> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const body = {
    conversationId,
    reconnect: true,
    lastEventId: Number(lastEventId),
  }

  const abortController = new AbortController()
  let timeoutTimer: ReturnType<typeof setTimeout> | null = null

  const resetTimeout = () => {
    if (timeoutTimer) clearTimeout(timeoutTimer)
    timeoutTimer = setTimeout(() => {
      abortController.abort()
    }, STREAM_TIMEOUT_MS)
  }

  try {
    const response = await fetch(STREAM_URL, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: abortController.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('ReadableStream not supported')

    const decoder = new TextDecoder('utf-8')
    const parser = new SSEParser()

    resetTimeout()

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        resetTimeout()
        const chunk = decoder.decode(value, { stream: true })
        const events = parser.parse(chunk)
        for (const event of events) {
          if (event.id) {
            if (options?.seenEventIds?.has(event.id)) continue
            options?.seenEventIds?.add(event.id)
            options?.onLastEventId?.(event.id)
          }
          if (event.event === 'heartbeat') {
            resetTimeout()
            continue
          }
          yield event
        }
      }

      const remaining = decoder.decode()
      if (remaining) {
        const events = parser.parse(remaining)
        for (const event of events) {
          if (event.id) {
            if (options?.seenEventIds?.has(event.id)) continue
            options?.seenEventIds?.add(event.id)
            options?.onLastEventId?.(event.id)
          }
          if (event.event === 'heartbeat') continue
          yield event
        }
      }

      const flushEvents = parser.flush()
      for (const event of flushEvents) {
        if (event.id) {
          if (options?.seenEventIds?.has(event.id)) continue
          options?.seenEventIds?.add(event.id)
          options?.onLastEventId?.(event.id)
        }
        if (event.event === 'heartbeat') continue
        yield event
      }
    } catch (e) {
      if (e instanceof Error && e.name === 'AbortError') return
      throw e
    } finally {
      reader.releaseLock()
    }
  } finally {
    if (timeoutTimer) {
      clearTimeout(timeoutTimer)
      timeoutTimer = null
    }
  }
}
