import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, Conversation, MessageVO, SseEvent } from '@/types'
import { streamChat, stopStream, reconnectStream } from '@/api/chat'
import * as conversationApi from '@/api/conversation'

/** 会话续连状态在 sessionStorage 的存储 key，用于刷新页面后恢复 lastEventId */
const RECONNECT_STORAGE_KEY = 'mateclaw.chat.reconnect'

interface PersistedReconnectState {
  conversationId: string
  lastEventId: string | null
}

function loadPersistedReconnectState(): PersistedReconnectState | null {
  try {
    const raw = sessionStorage.getItem(RECONNECT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PersistedReconnectState
    if (!parsed || typeof parsed.conversationId !== 'string' || !parsed.conversationId) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function savePersistedReconnectState(state: PersistedReconnectState): void {
  try {
    sessionStorage.setItem(RECONNECT_STORAGE_KEY, JSON.stringify(state))
  } catch {
    // 忽略存储失败（隐私模式 / 配额耗尽）
  }
}

function clearPersistedReconnectState(): void {
  try {
    sessionStorage.removeItem(RECONNECT_STORAGE_KEY)
  } catch {
    // 忽略
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const currentAgentId = ref<number | null>(null)

  // 从 sessionStorage 恢复 conversationId，确保刷新后能继续订阅同一个会话的事件流
  const initialReconnect = loadPersistedReconnectState()
  const conversationId = ref<string>(initialReconnect?.conversationId || '')

  const isStreaming = ref(false)
  /**
   * 正在生成响应的会话 id 集合。
   * <p>
   * 用 Set 而非单一 ref 的原因：未来若支持后台流（用户切到其他对话后旧会话继续生成），
   * 历史侧栏的旋转图标仍能正确反映每个会话的真实状态。Set 替换为新实例以触发 Vue 响应式。
   */
  const streamingConversations = ref(new Set<string>())
  const conversations = ref<Conversation[]>([])
  const conversationsLoading = ref(false)
  const historyLoading = ref(false)

  /**
   * 把指定会话标记为"正在生成"。同一时间通常只有一个会话在生成，但用 Set 跟踪
   * 可以在历史侧栏上精确展示旋转图标（不依赖当前激活的 conversationId）。
   */
  function markConversationStreaming(convId: string, active: boolean): void {
    if (!convId) return
    const next = new Set(streamingConversations.value)
    if (active) next.add(convId)
    else next.delete(convId)
    streamingConversations.value = next
  }

  /** Last SSE event id seen on this connection — used for reconnect dedup */
  const lastEventId = ref<string | null>(initialReconnect?.lastEventId || null)
  /** Set of event ids already dispatched — prevents double-processing on reconnect replay */
  const seenEventIds = ref(new Set<string>())

  function generateConversationId(): string {
    const id = crypto.randomUUID()
    conversationId.value = id
    savePersistedReconnectState({ conversationId: id, lastEventId: lastEventId.value })
    return id
  }

  function setAgent(agentId: number): void {
    currentAgentId.value = agentId
  }

  function clearMessages(): void {
    messages.value = []
    lastEventId.value = null
    seenEventIds.value.clear()
    clearPersistedReconnectState()
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

  /**
   * 切换到指定会话并加载历史消息
   * @param convId 目标会话 id
   * @param force 是否强制重新拉取：true 时忽略"同一会话已有消息"的短路逻辑，
   *              用于菜单切换等场景下重新拉取最新数据并触发组件重新挂载图表
   */
  async function switchConversation(convId: string, force = false): Promise<void> {
    if (isStreaming.value) return
    // 相同会话且消息已加载时短路，避免重复请求；
    // 刷新页面后 conversationId 会从 sessionStorage 恢复但 messages 为空，
    // 此时不能跳过，否则会出现"第一条历史点不进去"的 bug
    if (!force && conversationId.value === convId && messages.value.length > 0) return

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

  /**
   * 重命名会话标题
   * @param convId 目标会话 id
   * @param title 新标题
   */
  async function renameConversation(convId: string, title: string): Promise<void> {
    const newTitle = title.trim()
    if (!newTitle) return
    await conversationApi.renameConversation(convId, newTitle)
    const target = conversations.value.find(c => c.conversationId === convId)
    if (target) {
      target.title = newTitle
    }
  }

  async function stopChat(): Promise<void> {
    const stoppedConvId = conversationId.value
    if (!isStreaming.value || !stoppedConvId) return
    try {
      await stopStream(stoppedConvId)
    } catch (e) {
      console.warn('[ChatStore] stop request failed:', e)
    }
    isStreaming.value = false
    markConversationStreaming(stoppedConvId, false)
    // 流结束，重置 lastEventId 并清除持久化状态，避免下次刷新误触发续连
    lastEventId.value = null
    clearPersistedReconnectState()
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
    markConversationStreaming(conversationId.value, true)
    currentAgentId.value = agentId

    const assistantIdx = messages.value.length - 1
    const flushBuf = new FlushBuffer(assistantIdx)

    lastEventId.value = null
    seenEventIds.value.clear()
    savePersistedReconnectState({ conversationId: conversationId.value, lastEventId: null })

    try {
      const streamOptions = {
        onLastEventId: (id: string) => {
          lastEventId.value = id
          savePersistedReconnectState({ conversationId: conversationId.value, lastEventId: id })
        },
        seenEventIds: seenEventIds.value,
      }

      // 注意：后端在发送 done 事件后并不会立即关闭 SSE 连接（连接常驻以支持续连/心跳），
      // 因此仅靠流关闭触发的 finally 无法及时把 UI 切回非生成态。
      // 这里识别 done 后显式置位并打断 for-await，UI 才能立刻从"正在生成"切回正常。
      let streamFinished = false
      for await (const sse of streamChat(agentId, message, conversationId.value, modelName, streamOptions)) {
        if (!isStreaming.value) break
        handleSseEvent(sse, flushBuf, () => { streamFinished = true })
        if (streamFinished) break
      }

      flushBuf.flush()

      if (isNewConversation) {
        fetchConversations()
      }
    } catch (error) {
      flushBuf.flush()
      // 流式连接中途断开：若后端仍有活流（已有 lastEventId），自动续连，
      // 不直接告知用户失败。常见场景：网络抖动 / SSE 临时断流。
      if (lastEventId.value && conversationId.value) {
        try {
          await reconnect()
          return
        } catch (reconnectErr) {
          console.warn('[ChatStore] Auto-reconnect after stream error failed:', reconnectErr)
        }
      }
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = '抱歉，请求出现异常，请稍后重试。'
      }
    } finally {
      flushBuf.destroy()
      isStreaming.value = false
      markConversationStreaming(conversationId.value, false)
    }
  }

  async function reconnect(): Promise<void> {
    if (!conversationId.value || !lastEventId.value) return
    if (isStreaming.value) return

    isStreaming.value = true
    markConversationStreaming(conversationId.value, true)

    const streamOptions = {
      onLastEventId: (id: string) => {
        lastEventId.value = id
        savePersistedReconnectState({ conversationId: conversationId.value, lastEventId: id })
      },
      seenEventIds: seenEventIds.value,
    }

    const flushBuf = new FlushBuffer(messages.value.length - 1)

    try {
      let streamFinished = false
      for await (const sse of reconnectStream(conversationId.value, lastEventId.value, streamOptions)) {
        if (!isStreaming.value) break
        handleSseEvent(sse, flushBuf, () => { streamFinished = true })
        if (streamFinished) break
      }
      flushBuf.flush()
    } catch (error) {
      flushBuf.flush()
      console.warn('[ChatStore] Reconnect failed:', error)
    } finally {
      flushBuf.destroy()
      isStreaming.value = false
      markConversationStreaming(conversationId.value, false)
    }
  }

  /**
   * 刷新页面 / 切回 tab 时尝试续连上一次的 SSE 流。
   * <p>
   * 前置条件：sessionStorage 中持久化了 conversationId 和 lastEventId（由 sendMessage / reconnect
   * 持续维护）。若不存在则直接返回，避免误触发"上一个会话的流已经结束"场景。
   * 真正的"是否仍有活流"由后端 RunState 决定；5 分钟窗口内可重连，否则后端返回 done 事件。
   */
  async function tryResumeStream(): Promise<void> {
    const persisted = loadPersistedReconnectState()
    if (!persisted || !persisted.lastEventId) return
    if (isStreaming.value) return

    // 若用户在刷新前并未真正发送过任何消息（conversationId 仅是 generate 出来的占位），
    // 且没有 lastEventId，则不需要重连。这里 lastEventId 必存在才进入重连。
    conversationId.value = persisted.conversationId
    lastEventId.value = persisted.lastEventId
    await reconnect()
  }

  /**
   * 处理 SSE 事件
   * <p>
   * 高频事件（content_delta / thinking_delta）通过 FlushBuffer 批量缓冲，对齐渲染周期；
   * 低频事件（tool_call、message_complete 等）先 flush 缓冲区再直接更新消息对象。
   *
   * @param onFinished 当收到 done 事件（对话在后端语义上已结束）时回调，
   *                   由调用方决定是否立即打断 for-await；
   *                   后端不会因 done 而关闭 SSE 连接，仅靠流关闭触发的 finally
   *                   无法把 UI 切回非生成态，必须由调用方主动打断。
   */
  function handleSseEvent(sse: SseEvent, flushBuf: FlushBuffer, onFinished?: () => void): void {
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
        // 后端在 done 后并不会关闭 SSE 流，必须在此处把 UI 切回非生成态，
        // 否则状态栏会一直显示"正在生成"、按钮一直是"停止"。
        isStreaming.value = false
        lastEventId.value = null
        clearPersistedReconnectState()
        onFinished?.()
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
    streamingConversations,
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
    tryResumeStream,
    fetchConversations,
    switchConversation,
    deleteConversation,
    renameConversation,
    isConversationStreaming: (convId: string): boolean => streamingConversations.value.has(convId),
  }
})
