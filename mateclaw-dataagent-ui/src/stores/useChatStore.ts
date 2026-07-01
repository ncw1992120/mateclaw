import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage, Conversation, MessageVO, SseEvent } from '@/types'
import { streamChat, stopStream, reconnectStream } from '@/api/chat'
import * as conversationApi from '@/api/conversation'
import { usePersistedState } from '@/composables/usePersistedRef'

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
  /** 当前选中的智能体 ID（刷新后保留） */
  const currentAgentId = usePersistedState<number | string | null>('mc-chat-current-agent-id', null)
  /** 当前选中的模型名称（刷新后保留，发消息时传给后端） */
  const selectedModelName = usePersistedState<string>('mc-chat-selected-model-name', '')
  /** 当前选中模型的 Provider ID（刷新后保留，与 selectedModelName 成对使用） */
  const selectedModelProvider = usePersistedState<string>('mc-chat-selected-model-provider', '')

  // 从 sessionStorage 恢复 conversationId，确保刷新后能继续订阅同一个会话的事件流
  const initialReconnect = loadPersistedReconnectState()
  // 优先使用 reconnect 状态中的 conversationId（说明有未完成的流），否则从 localStorage 恢复上次选中的会话
  const initialConversationId = initialReconnect?.conversationId || ''
  // 使用 usePersistedState 自动持久化到 localStorage
  const conversationId = usePersistedState<string>('mc-chat-current-conversation-id', initialConversationId)

  const isStreaming = ref(false)
  /** 用户在前端勾选的数据源 ID 白名单（刷新后保留）；空数组表示不限制（由 LLM 自主选择） */
  const selectedDatasourceIds = usePersistedState<string[]>('mc-chat-selected-datasource-ids', [])
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

  /** 每个会话的续连状态（conversationId -> lastEventId），切换对话时保留，切回后可重连 */
  const reconnectStates = new Map<string, string>()

  /** 是否因切换对话而断开 SSE 连接（非用户主动停止） */
  let disconnectedBySwitch = false

  /** 用户是否主动点击了停止按钮（取消后禁止自动重连） */
  let userStopped = false

  /**
   * 后台会话消息缓存（key: conversationId）。
   * 切换会话时，当前会话的消息会暂存于此，后台 SSE 流继续更新；
   * 切回时从此处恢复，避免历史消息丢失后台流式更新。
   */
  const backgroundConversationMessages = new Map<string, ChatMessage[]>()

  /**
   * 在后台运行完成的会话集合。
   * 被收录的会话在侧栏显示"未读"标识，用户切回后自动清除。
   */
  const backgroundCompletedConversations = ref(new Set<string>())

  /**
   * 是否存在待续连的 SSE 流。
   * <p>
   * 通过 sessionStorage 中的 lastEventId 判断：刷新前若有正在进行的对话，
   * SSE 接收逻辑会持续把 lastEventId 写入 sessionStorage；刷新后 MainLayout
   * 会触发 tryResumeStream 续连。ChatView 等组件挂载时应据此跳过会破坏
   * 续连状态的强制历史拉取（见 switchConversation 的清空 lastEventId/seenEventIds）。
   */
  function hasPendingReconnect(): boolean {
    const persisted = loadPersistedReconnectState()
    return !!(persisted && persisted.lastEventId)
  }

  function generateConversationId(): string {
    const id = self.crypto.randomUUID ? self.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    conversationId.value = id
    selectedDatasourceIds.value = []
    clearPersistedReconnectState()
    savePersistedReconnectState({ conversationId: id, lastEventId: lastEventId.value })
    return id
  }

  function setAgent(agentId: number | string): void {
    currentAgentId.value = agentId
  }

  function clearMessages(): void {
    messages.value = []
    lastEventId.value = null
    seenEventIds.value.clear()
    clearPersistedReconnectState()
    // 保留已选中的模型（通过 usePersistedState 持久化到 localStorage），
    // 新建对话时继续使用用户上次选择的模型
    generateConversationId()
  }

  /**
   * 切换工作空间时重置与会话相关的状态。
   * <p>
   * 工作空间切换后，原工作空间下的会话 ID、历史消息、后台缓存等都不再适用，
   * 需要从 localStorage / sessionStorage 及内存中全部清理，避免刷新页面后恢复脏数据。
   */
  function resetForWorkspaceSwitch(): void {
    // 清理 localStorage 中持久化的聊天状态
    localStorage.removeItem('mc-chat-current-conversation-id')
    localStorage.removeItem('mc-chat-current-agent-id')
    localStorage.removeItem('mc-chat-selected-model-name')
    localStorage.removeItem('mc-chat-selected-model-provider')
    localStorage.removeItem('mc-chat-selected-datasource-ids')
    // 清理续连状态
    clearPersistedReconnectState()

    // 清理内存状态
    messages.value = []
    conversations.value = []
    streamingConversations.value = new Set()
    backgroundConversationMessages.clear()
    backgroundCompletedConversations.value = new Set()
    reconnectStates.clear()
    lastEventId.value = null
    seenEventIds.value.clear()
    isStreaming.value = false
    disconnectedBySwitch = false
    userStopped = false

    // 生成新的会话 ID，避免复用旧工作空间的会话
    generateConversationId()
  }

  async function fetchConversations(): Promise<void> {
    conversationsLoading.value = true
    try {
      const list = await conversationApi.listConversations() as unknown as Conversation[]
      conversations.value = list
      // 重连进行中时不修改 conversationId，避免 fetchConversations 与 tryResumeStream 并发执行时
      // 竞态修改 conversationId 导致重连使用了错误的会话 ID
      if (isStreaming.value || hasPendingReconnect()) {
        return
      }
      // 检查当前选中的会话是否仍然存在于列表中
      // 如果不存在（比如被删除了），清除 localStorage 中的状态
      if (conversationId.value && !list.some(c => c.conversationId === conversationId.value)) {
        // 如果有会话列表，选中第一个；否则生成新会话
        if (list.length > 0) {
          // 按最后活跃时间排序，选中最新的会话
          const sorted = [...list].sort((a, b) =>
            new Date(b.lastActiveTime || b.updateTime || b.createTime).getTime() -
            new Date(a.lastActiveTime || a.updateTime || a.createTime).getTime()
          )
          conversationId.value = sorted[0].conversationId
        } else {
          generateConversationId()
        }
      }
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
    // 相同会话且消息已加载时短路，避免重复请求；
    // 刷新页面后 conversationId 会从 sessionStorage 恢复但 messages 为空，
    // 此时不能跳过，否则会出现"第一条历史点不进去"的 bug
    if (!force && conversationId.value === convId && messages.value.length > 0) return

    const oldConvId = conversationId.value
    const isSameConversation = oldConvId === convId

    // 切换到不同会话时，如果当前正在流式生成，则将当前消息存入后台缓存
    // 后续 sendMessage 的 for-await 循环检测到 conversationId 变更后会自动转为后台模式
    if (!isSameConversation && isStreaming.value && oldConvId) {
      backgroundConversationMessages.set(oldConvId, messages.value)
      // 清除前台流式状态，允许新会话正常发送消息
      isStreaming.value = false
    }

    // 切换到不同会话时，保存当前会话的续连状态（如果有），以便切回后可重连
    const oldHadStream = !isSameConversation && !!lastEventId.value && !!oldConvId
    if (oldHadStream) {
      reconnectStates.set(oldConvId, lastEventId.value!)
    }

    // 切换会话时同步模型选择状态：从会话列表中查找目标会话的 pinned model
    if (!isSameConversation) {
      const targetConv = conversations.value.find(c => c.conversationId === convId)
      if (targetConv?.modelProvider && targetConv?.modelName) {
        selectedModelProvider.value = targetConv.modelProvider
        selectedModelName.value = targetConv.modelName
      } else {
        selectedModelProvider.value = ''
        selectedModelName.value = ''
      }
    }

    historyLoading.value = true
    try {
      // 优先从后台缓存恢复消息（包含后台流式更新）
      const cachedMsgs = backgroundConversationMessages.get(convId)
      if (cachedMsgs && cachedMsgs.length > 0) {
        conversationId.value = convId
        messages.value = cachedMsgs
        // 切回后台会话时，清除未读标记
        if (backgroundCompletedConversations.value.has(convId)) {
          const nextCompleted = new Set(backgroundCompletedConversations.value)
          nextCompleted.delete(convId)
          backgroundCompletedConversations.value = nextCompleted
        }
        // 若后台会话仍在运行中，恢复前台流式状态
        if (streamingConversations.value.has(convId)) {
          isStreaming.value = true
        }
      } else {
        const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
        conversationId.value = convId
        messages.value = msgList
          .filter(m => m.role === 'user' || m.role === 'assistant')
          .map(buildChatMessageFromVO)
      }
      if (!isSameConversation) {
        // 检查目标会话是否有保存的续连状态，如果有则恢复并尝试重连
        const savedLastEventId = reconnectStates.get(convId)
        if (savedLastEventId) {
          lastEventId.value = savedLastEventId
          seenEventIds.value.clear()
          reconnectStates.delete(convId)
          savePersistedReconnectState({ conversationId: convId, lastEventId: savedLastEventId })
          // 异步尝试重连，不阻塞历史消息加载
          reconnect()
        } else {
          lastEventId.value = null
          seenEventIds.value.clear()
          if (!oldHadStream) {
            clearPersistedReconnectState()
          }
        }
      }
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

  /**
   * 设置会话置顶状态。
   * @param convId 目标会话 id
   * @param pinned 是否置顶
   */
  async function setConversationPinned(convId: string, pinned: boolean): Promise<void> {
    await conversationApi.setPinned(convId, pinned)
    const target = conversations.value.find(c => c.conversationId === convId)
    if (target) {
      target.pinned = pinned ? 1 : 0
    }
  }

  async function stopChat(): Promise<void> {
    const stoppedConvId = conversationId.value
    if (!isStreaming.value || !stoppedConvId) return
    userStopped = true
    try {
      await stopStream(stoppedConvId)
    } catch (e) {
      console.warn('[ChatStore] stop request failed:', e)
    }
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant') {
      const segments = ensureSegments(messages.value, messages.value.length - 1)
      finalizeAllRunningSegments(segments)
    }
    isStreaming.value = false
    markConversationStreaming(stoppedConvId, false)
    reconnectStates.delete(stoppedConvId)
    backgroundConversationMessages.delete(stoppedConvId)
    const nextCompleted = new Set(backgroundCompletedConversations.value)
    nextCompleted.delete(stoppedConvId)
    backgroundCompletedConversations.value = nextCompleted
    // 流结束，重置 lastEventId 并清除持久化状态，避免下次刷新误触发续连
    lastEventId.value = null
    seenEventIds.value.clear()
    clearPersistedReconnectState()
  }

  /**
   * 断开前端 SSE 连接，但不停止后端流。
   * 用于切换对话时保留后端流的续连能力，切回后可通过 reconnect 恢复。
   * 同时清除当前会话的流式标记，避免侧栏一直显示旋转图标；
   * 切回时 reconnect 会重新设置流式标记。
   */
  function disconnectStream(): void {
    disconnectedBySwitch = true
    isStreaming.value = false
    const convId = conversationId.value
    if (convId) {
      markConversationStreaming(convId, false)
    }
  }

  /**
   * 流式内容批量刷新缓冲区
   * <p>
   * 使用 requestAnimationFrame 对齐浏览器渲染周期，将高频的 content_delta
   * 合并到同一帧内更新，避免每个 delta 都触发 Vue 重新渲染。
   * 低频事件（tool_call、message_complete 等）会立即 flush 确保数据一致性。
   */
  class FlushBuffer {
    private contentBuf = ''
    private rafId: number | null = null
    private msgIndex: number
    private getMsgs: () => ChatMessage[]

    constructor(msgIndex: number, getMsgs: () => ChatMessage[]) {
      this.msgIndex = msgIndex
      this.getMsgs = getMsgs
    }

    appendContent(delta: string): void {
      this.contentBuf += delta
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
      const msgs = this.getMsgs()
      const msg = msgs[this.msgIndex]
      if (!msg || msg.role !== 'assistant') return

      if (this.contentBuf) {
        msg.content += this.contentBuf
        this.contentBuf = ''
      }
    }
  }

  async function sendMessage(agentId: number | string, message: string): Promise<void> {
    if (isStreaming.value) return

    if (!conversationId.value) {
      generateConversationId()
    }

    const convId = conversationId.value

    if (streamingConversations.value.has(convId)) return

    // 重置用户停止标记，新消息允许正常重连
    userStopped = false

    const isNewConversation = !conversations.value.some(c => c.conversationId === convId)

    const modelProvider = selectedModelProvider.value || undefined
    const modelName = selectedModelName.value || undefined

    // 使用本地引用，后台切换时仍指向原会话消息数组
    let targetMsgs = messages.value

    targetMsgs.push({
      role: 'user',
      content: message,
      timestamp: Date.now(),
    })

    const assistantMessage: ChatMessage = {
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      metadata: {},
    }
    targetMsgs.push(assistantMessage)

    isStreaming.value = true
    markConversationStreaming(convId, true)
    currentAgentId.value = agentId

    const assistantIdx = targetMsgs.length - 1
    const flushBuf = new FlushBuffer(assistantIdx, () => targetMsgs)

    lastEventId.value = null
    seenEventIds.value.clear()
    savePersistedReconnectState({ conversationId: convId, lastEventId: null })

    /** 当前会话是否已切到后台（用户切到其他会话后，本流继续运行） */
    let isBackground = false

    try {
      const streamOptions = {
        onLastEventId: (id: string) => {
          lastEventId.value = id
          savePersistedReconnectState({ conversationId: convId, lastEventId: id })
        },
        seenEventIds: seenEventIds.value,
      }

      let streamFinished = false
      for await (const sse of streamChat(agentId, message, convId, modelProvider, modelName, streamOptions, selectedDatasourceIds.value)) {
        // 检测是否切换到其他会话
        if (conversationId.value !== convId) {
          if (!isBackground) {
            // 首次检测到切换：保存当前消息到后台缓存
            backgroundConversationMessages.set(convId, targetMsgs)
            isBackground = true
          }
        }

        // 后台模式下，仅用户主动停止才打断
        if (isBackground) {
          if (userStopped) break
        } else {
          if (!isStreaming.value) break
        }

        handleSseEvent(sse, flushBuf, targetMsgs, convId, () => { streamFinished = true })
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
      // 但用户主动取消、后台模式时跳过重连。
      if (lastEventId.value && convId && !userStopped && !isBackground) {
        try {
          await reconnect()
          return
        } catch (reconnectErr) {
          console.warn('[ChatStore] Auto-reconnect after stream error failed:', reconnectErr)
        }
      }
      const lastMsg = targetMsgs[targetMsgs.length - 1]
      if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
        lastMsg.content = '抱歉，请求出现异常，请稍后重试。'
      }
    } finally {
      flushBuf.destroy()
      if (isBackground) {
        // 后台会话：不修改前台 streaming 状态；流结束后更新后台缓存
        markConversationStreaming(convId, false)
        backgroundConversationMessages.set(convId, targetMsgs)
      } else {
        isStreaming.value = false
        markConversationStreaming(convId, false)
      }
      disconnectedBySwitch = false
    }
  }

  async function reconnect(): Promise<void> {
    if (!conversationId.value || !lastEventId.value) return
    if (isStreaming.value) return

    const convId = conversationId.value
    const savedLastEventId = lastEventId.value

    isStreaming.value = true
    markConversationStreaming(convId, true)

    // 续连前先拉取历史消息，确保 UI 上能看到完整对话上下文。
    // 后端在流终态前不会持久化 assistant 消息，因此历史里的最后一条
    // 通常是 user；下面会补一条 assistant 占位用于承接回放的 content_delta。
    try {
      const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
      messages.value = msgList
        .filter(m => m.role === 'user' || m.role === 'assistant')
        .map(buildChatMessageFromVO)
    } catch (e) {
      console.warn('[ChatStore] Reconnect: failed to load history messages', e)
    }

    // 若历史末尾不是 assistant（说明对话仍在进行中、回复尚未落库），补一条空占位，
    // 让回放的 content_delta 有正确目标可写入；否则 FlushBuffer
    // 会指向 user 消息，applyToMessage 因 role 不匹配而静默丢弃事件。
    const last = messages.value[messages.value.length - 1]
    if (!last || last.role !== 'assistant') {
      messages.value.push({
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        metadata: {},
      })
    }

    const streamOptions = {
      onLastEventId: (id: string) => {
        lastEventId.value = id
        savePersistedReconnectState({ conversationId: convId, lastEventId: id })
      },
      seenEventIds: seenEventIds.value,
    }

    const flushBuf = new FlushBuffer(messages.value.length - 1, () => messages.value)

    try {
      let streamFinished = false
      for await (const sse of reconnectStream(convId, savedLastEventId, streamOptions)) {
        if (!isStreaming.value) break
        handleSseEvent(sse, flushBuf, messages.value, convId, () => { streamFinished = true })
        if (streamFinished) break
      }
      flushBuf.flush()
    } catch (error) {
      flushBuf.flush()
      console.warn('[ChatStore] Reconnect failed:', error)
    } finally {
      flushBuf.destroy()
      isStreaming.value = false
      markConversationStreaming(convId, false)
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
  /**
   * 获取指定消息的 segments 数组（确保一定返回数组引用）。
   * 流式阶段由前端实时构建；历史消息由后端持久化。
   */
  function ensureSegments(msgs: ChatMessage[], msgIdx: number): Array<Record<string, unknown>> {
    const msg = msgs[msgIdx]
    if (!msg) return []
    if (!msg.metadata) msg.metadata = {}
    const meta = msg.metadata as Record<string, unknown>
    if (!Array.isArray(meta.segments)) {
      meta.segments = []
    }
    return meta.segments as Array<Record<string, unknown>>
  }

  /** 找到最后一个 status=running 的指定类型 segment */
  function findLastRunningSegment(segments: Array<Record<string, unknown>>, type: string): Record<string, unknown> | undefined {
    for (let i = segments.length - 1; i >= 0; i--) {
      if (segments[i].type === type && segments[i].status === 'running') return segments[i]
    }
    return undefined
  }

  /** 关闭所有 status=running 的指定类型 segment */
  function finalizeRunningSegments(segments: Array<Record<string, unknown>>, types: string[]): void {
    for (const seg of segments) {
      if (seg.status === 'running' && types.includes(seg.type as string)) {
        seg.status = 'completed'
      }
    }
  }

  /** 关闭所有 status=running 的 segment（对话结束时调用） */
  function finalizeAllRunningSegments(segments: Array<Record<string, unknown>>): void {
    for (const seg of segments) {
      if (seg.status === 'running') seg.status = 'completed'
    }
  }

  function handleSseEvent(sse: SseEvent, flushBuf: FlushBuffer, targetMsgs: ChatMessage[], onFinished?: () => void): void {
    const evt = sse.event
    const data = sse.data

    if (typeof data !== 'object') return

    const msgIdx = targetMsgs.length - 1

    switch (evt) {
      case 'content_delta': {
        const delta = data.delta as string | undefined
        if (delta) {
          flushBuf.appendContent(delta)
          // 同步构建 content segment
          const segments = ensureSegments(targetMsgs, msgIdx)
          let contentSeg = findLastRunningSegment(segments, 'content')
          if (!contentSeg) {
            // 关闭可能存在的 running thinking/content segment
            finalizeRunningSegments(segments, ['thinking', 'content'])
            contentSeg = { type: 'content', status: 'running', text: '' }
            segments.push(contentSeg)
          }
          contentSeg.text = (contentSeg.text as string || '') + delta
        }
        break
      }
      case 'thinking_delta': {
        const delta = data.delta as string | undefined
        if (delta) {
          const segments = ensureSegments(targetMsgs, msgIdx)
          let thinkSeg = findLastRunningSegment(segments, 'thinking')
          if (!thinkSeg) {
            thinkSeg = { type: 'thinking', status: 'running', thinkingText: '' }
            segments.push(thinkSeg)
          }
          thinkSeg.thinkingText = (thinkSeg.thinkingText as string || '') + delta
        }
        break
      }
      case 'tool_call_started': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
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
          // 关闭 running content/thinking segment，创建 running tool_call segment
          const segments = ensureSegments(targetMsgs, msgIdx)
          finalizeRunningSegments(segments, ['content', 'thinking'])
          segments.push({
            type: 'tool_call',
            status: 'running',
            toolName,
            toolCallId: toolCallId || '',
            toolArgs: toolArgs || '',
          })
          targetMsgs[msgIdx] = {
            ...prev,
            metadata: {
              ...prevMeta,
              toolCalls: nextToolCalls,
              currentPhase: 'executing_tool',
              segments,
            },
          }
        }
        break
      }
      case 'tool_call_completed': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
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
          // 更新对应 tool_call segment 为 completed
          const segments = ensureSegments(targetMsgs, msgIdx)
          for (const seg of segments) {
            if (seg.type === 'tool_call' && seg.status === 'running'
              && ((seg.toolCallId && seg.toolCallId === toolCallId)
                || (seg.toolName === toolName))) {
              seg.status = 'completed'
              seg.toolSuccess = success !== false
              if (result) seg.toolResult = result
              break
            }
          }
          targetMsgs[msgIdx] = {
            ...prev,
            metadata: {
              ...prevMeta,
              toolCalls: nextToolCalls,
              segments,
            },
          }
        }
        break
      }
      case 'message_complete': {
        flushBuf.flush()
        const lastMsg = targetMsgs[targetMsgs.length - 1]
        if (lastMsg.role !== 'assistant') return
        const status = data.status as string | undefined
        if (status === 'stopped') {
          lastMsg.content += '\n[已停止生成]'
        } else if (status === 'failed') {
          lastMsg.content += '\n[生成失败]'
        }
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
        break
      }
      case 'done': {
        flushBuf.flush()
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
        // 后台会话完成：不清除前台 isStreaming/lastEventId，仅标记未读
        if (conversationId.value !== convId) {
          markConversationStreaming(convId, false)
          backgroundConversationMessages.set(convId, targetMsgs)
          const nextCompleted = new Set(backgroundCompletedConversations.value)
          nextCompleted.add(convId)
          backgroundCompletedConversations.value = nextCompleted
        } else {
          isStreaming.value = false
          lastEventId.value = null
          clearPersistedReconnectState()
        }
        onFinished?.()
        break
      }
      case 'error': {
        flushBuf.flush()
        const lastMsg = targetMsgs[targetMsgs.length - 1]
        if (lastMsg.role !== 'assistant') return
        const msg = data.message as string | undefined
        if (msg) {
          lastMsg.content += `\n[错误] ${msg}`
        }
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
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

  /**
   * 从消息 VO 构建前端 ChatMessage。
   *
   * @param m 后端返回的消息 VO
   * @return 前端使用的 ChatMessage
   */
  function buildChatMessageFromVO(m: MessageVO): ChatMessage {
    return {
      role: m.role as 'user' | 'assistant',
      content: m.content || '',
      timestamp: new Date(m.createTime).getTime(),
      metadata: m.metadata as Record<string, unknown> | undefined,
    }
  }

  /**
   * 重新生成指定 AI 消息：删除该消息及其之后的所有消息，重新发送对应的用户问题
   * @param msgIndex AI 消息在 messages 数组中的索引
   */
  async function regenerateMessage(msgIndex: number): Promise<void> {
    if (isStreaming.value) return
    const msg = messages.value[msgIndex]
    if (!msg || msg.role !== 'assistant') return

    // 找到该 AI 消息之前的最近一条用户消息
    let userMsgIndex = -1
    for (let i = msgIndex - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') {
        userMsgIndex = i
        break
      }
    }
    if (userMsgIndex === -1) return

    const userContent = messages.value[userMsgIndex].content

    // 删除从该 AI 消息起的所有后续消息（含自身）
    messages.value.splice(msgIndex)

    // 重新发送用户消息（模型信息由 sendMessage 内部从 store 同步快照读取）
    const agentId = currentAgentId.value
    if (!agentId) return
    await sendMessage(agentId, userContent)
  }

  return {
    messages,
    currentAgentId,
    selectedModelName,
    selectedModelProvider,
    conversationId,
    isStreaming,
    selectedDatasourceIds,
    streamingConversations,
    backgroundCompletedConversations,
    conversations,
    conversationsLoading,
    historyLoading,
    lastEventId,
    setAgent,
    clearMessages,
    resetForWorkspaceSwitch,
    generateConversationId,
    sendMessage,
    regenerateMessage,
    stopChat,
    disconnectStream,
    reconnect,
    tryResumeStream,
    hasPendingReconnect,
    fetchConversations,
    switchConversation,
    deleteConversation,
    renameConversation,
    setConversationPinned,
    isConversationStreaming: (convId: string): boolean => streamingConversations.value.has(convId),
  }
})
