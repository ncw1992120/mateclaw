import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, Conversation, MessageVO, SseEvent } from '@/types'
import { streamChat, stopStream, reconnectStream } from '@/api/chat'
import * as conversationApi from '@/api/conversation'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const currentAgentId = ref<number | null>(null)
  const conversationId = ref<string>('')
  const isStreaming = ref(false)
  const conversations = ref<Conversation[]>([])
  const conversationsLoading = ref(false)
  const historyLoading = ref(false)

  /** Last SSE event id seen on this connection — used for reconnect dedup */
  const lastEventId = ref<string | null>(null)
  /** Set of event ids already dispatched — prevents double-processing on reconnect replay */
  const seenEventIds = ref(new Set<string>())

  function generateConversationId(): string {
    const id = crypto.randomUUID()
    conversationId.value = id
    return id
  }

  function setAgent(agentId: number): void {
    currentAgentId.value = agentId
  }

  function clearMessages(): void {
    messages.value = []
    lastEventId.value = null
    seenEventIds.value.clear()
    generateConversationId()
  }

  async function fetchConversations(): Promise<void> {
    conversationsLoading.value = true
    try {
      const list = await conversationApi.listConversations() as unknown as Conversation[]
      conversations.value = list
    } finally {
      conversationsLoading.value = false
    }
  }

  async function switchConversation(convId: string): Promise<void> {
    if (isStreaming.value) return
    if (conversationId.value === convId) return

    historyLoading.value = true
    try {
      const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
      conversationId.value = convId
      messages.value = msgList
        .filter(m => m.role === 'user' || m.role === 'assistant')
        .map(m => ({
          role: m.role as 'user' | 'assistant',
          content: m.content || '',
          timestamp: new Date(m.createTime).getTime(),
          metadata: m.metadata || undefined,
        }))
      lastEventId.value = null
      seenEventIds.value.clear()
    } finally {
      historyLoading.value = false
    }
  }

  async function deleteConversation(convId: string): Promise<void> {
    await conversationApi.deleteConversation(convId)
    conversations.value = conversations.value.filter(c => c.conversationId !== convId)
    if (conversationId.value === convId) {
      clearMessages()
    }
  }

  async function stopChat(): Promise<void> {
    if (!isStreaming.value || !conversationId.value) return
    try {
      await stopStream(conversationId.value)
    } catch (e) {
      console.warn('[ChatStore] stop request failed:', e)
    }
    isStreaming.value = false
  }

  /**
   * 流式内容批量刷新缓冲区
   * <p>
   * 使用 requestAnimationFrame 对齐浏览器渲染周期，将高频的 content_delta/thinking_delta
   * 合并到同一帧内更新，避免每个 delta 都触发 Vue 重新渲染。
   * 低频事件（tool_call、message_complete 等）会立即 flush 确保数据一致性。
   */
  class FlushBuffer {
    private contentBuf = ''
    private thinkingBuf = ''
    private rafId: number | null = null
    private msgIndex: number

    constructor(msgIndex: number) {
      this.msgIndex = msgIndex
    }

    appendContent(delta: string): void {
      this.contentBuf += delta
      this.scheduleFlush()
    }

    appendThinking(delta: string): void {
      this.thinkingBuf += delta
      this.scheduleFlush()
    }

    flush(): void {
      if (this.rafId !== null) {
        cancelAnimationFrame(this.rafId)
        this.rafId = null
      }
      this.applyToMessage()
    }

    destroy(): void {
      if (this.rafId !== null) {
        cancelAnimationFrame(this.rafId)
        this.rafId = null
      }
    }

    private scheduleFlush(): void {
      if (this.rafId === null) {
        this.rafId = requestAnimationFrame(() => {
          this.rafId = null
          this.applyToMessage()
        })
      }
    }

    private applyToMessage(): void {
      const msg = messages.value[this.msgIndex]
      if (!msg || msg.role !== 'assistant') return

      if (this.contentBuf) {
        msg.content += this.contentBuf
        this.contentBuf = ''
      }
      if (this.thinkingBuf) {
        msg.thinking = (msg.thinking || '') + this.thinkingBuf
        this.thinkingBuf = ''
      }
    }
  }

  async function sendMessage(agentId: number, message: string, modelName?: string): Promise<void> {
    if (isStreaming.value) return

    if (!conversationId.value) {
      generateConversationId()
    }

    const isNewConversation = !conversations.value.some(c => c.conversationId === conversationId.value)

    messages.value.push({
      role: 'user',
      content: message,
      timestamp: Date.now(),
    })

    const assistantMessage: ChatMessage = {
      role: 'assistant',
      content: '',
      thinking: '',
      timestamp: Date.now(),
      metadata: {},
    }
    messages.value.push(assistantMessage)

    isStreaming.value = true
    currentAgentId.value = agentId

    const assistantIdx = messages.value.length - 1
    const flushBuf = new FlushBuffer(assistantIdx)

    lastEventId.value = null
    seenEventIds.value.clear()

    try {
      const streamOptions = {
        onLastEventId: (id: string) => { lastEventId.value = id },
        seenEventIds: seenEventIds.value,
      }

      for await (const sse of streamChat(agentId, message, conversationId.value, modelName, streamOptions)) {
        if (!isStreaming.value) break
        handleSseEvent(sse, flushBuf)
      }

      flushBuf.flush()

      if (isNewConversation) {
        fetchConversations()
      }
    } catch (error) {
      flushBuf.flush()
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = '抱歉，请求出现异常，请稍后重试。'
      }
    } finally {
      flushBuf.destroy()
      isStreaming.value = false
    }
  }

  async function reconnect(): Promise<void> {
    if (!conversationId.value || !lastEventId.value) return

    isStreaming.value = true

    const assistantIdx = messages.value.length - 1
    const flushBuf = new FlushBuffer(assistantIdx)

    const streamOptions = {
      onLastEventId: (id: string) => { lastEventId.value = id },
      seenEventIds: seenEventIds.value,
    }

    try {
      for await (const sse of reconnectStream(conversationId.value, lastEventId.value, streamOptions)) {
        if (!isStreaming.value) break
        handleSseEvent(sse, flushBuf)
      }
      flushBuf.flush()
    } catch (error) {
      flushBuf.flush()
      console.warn('[ChatStore] Reconnect failed:', error)
      isStreaming.value = false
    } finally {
      flushBuf.destroy()
    }
  }

  /**
   * 处理 SSE 事件
   * <p>
   * 高频事件（content_delta / thinking_delta）通过 FlushBuffer 批量缓冲，对齐渲染周期；
   * 低频事件（tool_call、message_complete 等）先 flush 缓冲区再直接更新消息对象。
   */
  function handleSseEvent(sse: SseEvent, flushBuf: FlushBuffer): void {
    const evt = sse.event
    const data = sse.data

    if (typeof data !== 'object') return

    switch (evt) {
      case 'content_delta': {
        const delta = data.delta as string | undefined
        if (delta) {
          flushBuf.appendContent(delta)
        }
        break
      }
      case 'thinking_delta': {
        const delta = data.delta as string | undefined
        if (delta) {
          flushBuf.appendThinking(delta)
        }
        break
      }
      case 'tool_call_started': {
        flushBuf.flush()
        const idx = messages.value.length - 1
        const prev = messages.value[idx]
        if (!prev || prev.role !== 'assistant') return
        const toolName = data.toolName as string | undefined
        const toolCallId = data.toolCallId as string | undefined
        const toolArgs = data.arguments as string | undefined
        if (toolName) {
          const prevMeta = (prev.metadata || {}) as Record<string, unknown>
          const prevToolCalls = (prevMeta.toolCalls as Array<Record<string, unknown>>) || []
          const nextToolCalls = [
            ...prevToolCalls,
            {
              toolCallId: toolCallId || '',
              name: toolName,
              arguments: toolArgs,
              status: 'running',
              startTime: Date.now(),
            },
          ]
          messages.value[idx] = {
            ...prev,
            metadata: {
              ...prevMeta,
              toolCalls: nextToolCalls,
              currentPhase: 'executing_tool',
            },
          }
        }
        break
      }
      case 'tool_call_completed': {
        flushBuf.flush()
        const idx = messages.value.length - 1
        const prev = messages.value[idx]
        if (!prev || prev.role !== 'assistant') return
        const toolName = data.toolName as string | undefined
        const toolCallId = data.toolCallId as string | undefined
        const success = data.success as boolean | undefined
        const result = data.result as string | undefined
        if (toolName && prev.metadata) {
          const prevMeta = prev.metadata as Record<string, unknown>
          const prevToolCalls = (prevMeta.toolCalls as Array<Record<string, unknown>>) || []
          const nextToolCalls = prevToolCalls.map((tc: Record<string, unknown>) => {
            const isMatch = (tc.toolCallId && tc.toolCallId === toolCallId)
              || (!tc.toolCallId && tc.name === toolName && tc.status === 'running')
            if (isMatch) {
              return { ...tc, status: 'completed', success: success !== false, ...(result ? { result } : {}) }
            }
            return tc
          })
          messages.value[idx] = {
            ...prev,
            metadata: {
              ...prevMeta,
              toolCalls: nextToolCalls,
            },
          }
        }
        break
      }
      case 'message_complete': {
        flushBuf.flush()
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg.role !== 'assistant') return
        const status = data.status as string | undefined
        if (status === 'stopped') {
          lastMsg.content += '\n[已停止生成]'
        } else if (status === 'failed') {
          lastMsg.content += '\n[生成失败]'
        }
        break
      }
      case 'done': {
        flushBuf.flush()
        break
      }
      case 'error': {
        flushBuf.flush()
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg.role !== 'assistant') return
        const msg = data.message as string | undefined
        if (msg) {
          lastMsg.content += `\n[错误] ${msg}`
        }
        break
      }
      case 'heartbeat':
      case 'stream_started':
      case 'session':
      case 'message_start':
        break
      default:
        break
    }
  }

  return {
    messages,
    currentAgentId,
    conversationId,
    isStreaming,
    conversations,
    conversationsLoading,
    historyLoading,
    lastEventId,
    setAgent,
    clearMessages,
    generateConversationId,
    sendMessage,
    stopChat,
    reconnect,
    fetchConversations,
    switchConversation,
    deleteConversation,
  }
})
