import { defineStore, acceptHMRUpdate } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatAttachment, ChatMessage, ContextUsage, Conversation, DelegationNode, DelegationTimeline, DelegationToolEntry, MessageVO, PlanMeta, SseEvent } from '@/types'
import { streamChat, stopStream, reconnectStream, type MessageContentPart } from '@/api/chat'
import * as conversationApi from '@/api/conversation'
import { usePersistedState } from '@/composables/usePersistedRef'
import { classifySseError, type ChatErrorInfo } from '@/types/chatError'

/** 会话续连状态在 sessionStorage 的存储 key，用于刷新页面后恢复 lastEventId */
const RECONNECT_STORAGE_KEY = 'mateclaw.chat.reconnect'

/** 聊天附件文件访问地址前缀（与后端 ChatUploadRuntime.FILE_URL_PREFIX 保持一致） */
const CHAT_FILE_URL_PREFIX = '/dataagent/api/v1/chat/files/'

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

/**
 * 用户主动停止的会话标记（存储会话 ID）。
 * <p>
 * stopChat 时写入：刷新后 tryResumeStream 据此识别"停止后立即刷新"场景，
 * 跳过对该会话的续连回放（后端 finalize 异步落库存在 stream_status 残留 running 的窗口），
 * 避免数据流被重新推送一遍。同一会话重新发送消息时清除。
 */
const STOPPED_STORAGE_KEY = 'mateclaw.chat.stopped'

function saveStoppedMarker(conversationId: string): void {
  try {
    sessionStorage.setItem(STOPPED_STORAGE_KEY, conversationId)
  } catch {
    // 忽略存储失败（隐私模式 / 配额耗尽）
  }
}

function loadStoppedMarker(): string | null {
  try {
    return sessionStorage.getItem(STOPPED_STORAGE_KEY)
  } catch {
    return null
  }
}

function clearStoppedMarker(conversationId: string): void {
  try {
    if (sessionStorage.getItem(STOPPED_STORAGE_KEY) === conversationId) {
      sessionStorage.removeItem(STOPPED_STORAGE_KEY)
    }
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

  /**
   * 是否正在生成响应（派生状态）。
   * <p>
   * 借鉴 mateclaw-ui 的派生状态设计：从消息列表的 status 字段计算得出，
   * 只要 assistant 消息的 status 离开 'streaming'，UI 自动回到 idle，
   * 不存在"忘记 set false"的可能。
   */
  const isStreaming = computed(() => messages.value.some(m => m.status === 'streaming'))
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
  /** 当前会话的上下文使用情况 */
  const contextUsage = ref<ContextUsage | null>(null)
  /** 上下文使用面板是否展开 */
  const contextUsagePanelOpen = ref(false)
  /**
   * 当前会话已进行的对话轮数（= 用户提问条数）。
   * <p>
   * 用于「多轮对话后建议新开对话窗口」提示：随消息列表派生，
   * 重开对话 / 切换会话时自动归零，无需额外维护计数器。
   */
  const dialogueRoundCount = computed(() =>
    messages.value.filter(m => m.role === 'user').length
  )
  /**
   * 当前会话中「建议新开对话」提示是否已被用户手动关闭。
   * <p>
   * 只影响当前会话内的一次性关闭；新建/切换会话时重置，保证每个长对话都会重新提示。
   */
  const newConversationHintDismissed = ref(false)

  /**
   * 调试日志辅助函数（已禁用，不再输出）。
   * 保留空函数体避免散布各处的调用点报错；如需重新启用调试，恢复 console.log 即可。
   */
  function logDebug(_message: string, ..._args: unknown[]): void {
    // no-op
  }

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

  /**
   * 将会话的最后活跃时间刷新为当前时间，使左侧会话列表按更新时间即时重新排序。
   * 仅更新本地已存在的会话；新会话由 fetchConversations 拉取后自动置顶。
   */
  function touchConversationActiveTime(convId: string): void {
    if (!convId) return
    const idx = conversations.value.findIndex(c => c.conversationId === convId)
    if (idx !== -1) {
      // 用新对象替换该项：既更新时间，又通过数组变更确保依赖此列表的计算属性一定重算
      conversations.value.splice(idx, 1, {
        ...conversations.value[idx],
        lastActiveTime: new Date().toISOString(),
      })
    }
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
  /** 当前活跃 SSE 流的中断函数（由 streamChat/reconnectStream 通过 onAbortController 注册）；
   * stopChat 借此真正断开前端连接，此前 AbortController 封在 api 层内部无法触达 */
  let activeAbort: (() => void) | null = null

  /**
   * 断连后自动重连的退避轮询状态机。
   * <p>
   * SSE 连接断开但后端可能仍在运行时启动：定时探 /status，
   * running → reconnect 接回流；idle → listMessages 对齐终态；
   * 探测失败 → 退避重试。带重试次数上限，达上限停止并提示用户手动重试，
   * 不无限刷后端。
   */
  let reconnectPollTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectPollAttempt = 0
  const RECONNECT_POLL_MAX = 15
  const RECONNECT_POLL_BASE_MS = 5000
  const RECONNECT_POLL_MAX_MS = 30000

  /** 第 attempt 次轮询的退避间隔（5→30s，×1.5 递增，封顶 30s） */
  function reconnectPollDelay(attempt: number): number {
    return Math.min(RECONNECT_POLL_BASE_MS * Math.pow(1.5, attempt), RECONNECT_POLL_MAX_MS)
  }

  /** 拉取后端历史消息对齐终态（轮询发现流已结束时调用） */
  async function refreshMessagesFromBackend(convId: string): Promise<void> {
    try {
      const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
      if (conversationId.value !== convId) return
      const parsed = msgList
        .filter(m => m.role === 'user' || m.role === 'assistant')
        .map(buildChatMessageFromVO)
      if (parsed.length > 0) {
        messages.value = parsed
        const last = messages.value[messages.value.length - 1]
        if (last?.role === 'assistant' && last.content) {
          last.status = 'completed'
        }
      }
      markConversationStreaming(convId, false)
      clearReconnectState()
    } catch (e) {
      logDebug('refreshMessagesFromBackend failed', e)
    }
  }

  /** 启动断连轮询：定时探 /status，running 就 reconnect 接回 */
  function startReconnectPoll(convId: string): void {
    stopReconnectPoll()
    reconnectPollAttempt = 0
    scheduleReconnectPoll(convId)
  }

  function scheduleReconnectPoll(convId: string): void {
    if (reconnectPollAttempt >= RECONNECT_POLL_MAX) {
      // 达上限：停止轮询，标记失败提示用户手动重试
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'assistant' && lastMsg.status === 'streaming') {
        lastMsg.status = 'failed'
        if (!lastMsg.content) lastMsg.content = '连接已断开，请刷新页面或重新发送消息。'
      }
      markConversationStreaming(convId, false)
      logDebug('reconnectPoll: max attempts reached, giving up. convId=', convId)
      return
    }
    const delay = reconnectPollDelay(reconnectPollAttempt)
    logDebug('reconnectPoll: scheduling attempt', reconnectPollAttempt, 'in', delay, 'ms, convId=', convId)
    reconnectPollTimer = setTimeout(() => void pollReconnectOnce(convId), delay)
  }

  async function pollReconnectOnce(convId: string): Promise<void> {
    // 会话已切走 / 已在生成 / 页面隐藏 → 停止轮询
    if (conversationId.value !== convId || isStreaming.value ||
        (typeof document !== 'undefined' && document.hidden)) {
      stopReconnectPoll()
      return
    }
    try {
      const res = await conversationApi.getStatus(convId) as { streamStatus?: string } | undefined
      if (conversationId.value !== convId) return  // 探测期间切走了
      if (res?.streamStatus === 'running') {
        // 后端仍在跑 → 接回流
        stopReconnectPoll()
        await reconnect()
      } else {
        // 后端已结束 → 停止轮询，拉一次 listMessages 对齐终态
        stopReconnectPoll()
        await refreshMessagesFromBackend(convId)
      }
    } catch {
      // 探测失败 → 退避后重试
      reconnectPollAttempt++
      scheduleReconnectPoll(convId)
    }
  }

  function stopReconnectPoll(): void {
    if (reconnectPollTimer) {
      clearTimeout(reconnectPollTimer)
      reconnectPollTimer = null
    }
    reconnectPollAttempt = 0
  }

  /**
   * 当前 SSE 流所属的会话 ID。
   * 用于 isStaleEvent 判断：切会话后旧流的事件直接丢弃，防止污染新会话。
   */
  let streamConversationId = ''

  /**
   * 判断 SSE 事件是否属于已过期的会话（用户已切换到其他会话）。
   * 借鉴 mateclaw-ui 的 isStaleEvent 设计。
   */
  function isStaleEvent(data: unknown): boolean {
    if (typeof data !== 'object' || data === null) return false
    const eventConvId = (data as Record<string, unknown>).conversationId as string | undefined
    if (eventConvId && streamConversationId && eventConvId !== streamConversationId) {
      return true
    }
    return false
  }

  /** stopChat 的 3 秒兜底定时器：done 事件没来时强制清理状态 */
  let stopFallbackTimer: ReturnType<typeof setTimeout> | null = null

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
   * 通过 sessionStorage 中的 conversationId 判断：刷新前若发送过消息，
   * sendMessage 会立即把 conversationId 写入 sessionStorage；刷新后 MainLayout
   * 会触发 tryResumeStream 续连。ChatView 等组件挂载时应据此跳过会破坏
   * 续连状态的强制历史拉取（见 switchConversation 的清空 lastEventId/seenEventIds）。
   * <p>
   * 注意：此处仅做本地标记判断，真正的"后端是否仍有活流"由 tryResumeStream
   * 内部在 fetchConversations 之后校验 streamStatus。
   */
  function hasPendingReconnect(): boolean {
    const persisted = loadPersistedReconnectState()
    return !!(persisted && persisted.conversationId)
  }

  function isConversationRunningOnServer(convId: string): boolean {
    const conv = conversations.value.find(c => c.conversationId === convId)
    return conv?.streamStatus === 'running'
  }

  function clearReconnectState(): void {
    lastEventId.value = null
    seenEventIds.value.clear()
    clearPersistedReconnectState()
  }

  /**
   * 准备新对话：清空消息和流式状态，将 conversationId 置空。
   * <p>
   * 不再预生成 UUID——真实会话 ID 由后端在首条消息时创建并通过 SSE session 事件返回；
   * 刷新页面后空 conversationId 不会触发对不存在会话的请求。
   */
  function prepareNewConversation(): void {
    messages.value = []
    conversationId.value = ''
    // 新对话保留已勾选的数据源（与模型选择一致，通过 usePersistedState 持久化），
    // 用户无需每次重新勾选；切换工作空间时由 resetForWorkspaceSwitch 显式重置
    // 新会话没有上下文使用数据：清空旧会话残留的占比，并收起展开面板，
    // 避免「上下文按钮」子元素展示上一个会话的占比
    contextUsage.value = null
    contextUsagePanelOpen.value = false
    // 新的会话从头开始计数，重新启用「建议新开对话」提示
    newConversationHintDismissed.value = false
    clearReconnectState()
  }

  function setAgent(agentId: number | string): void {
    currentAgentId.value = agentId
  }

  function clearMessages(): void {
    // 保留已选中的模型（通过 usePersistedState 持久化到 localStorage），
    // 新建对话时继续使用用户上次选择的模型

    // 流式生成中点"新对话"：与切换历史会话同理，将当前流转入后台继续生成，
    // 而不是 stopChat 终止后端流。直接引用 messages.value 数组，sendMessage
    // 的 for-await 循环会继续更新同一数组；保留 streamingConversations 标记
    // 使侧栏持续转圈。仅停止对该会话的断连轮询（不轮询非当前会话），
    // prepareNewConversation 会把 conversationId 置空，SSE 循环据此自动进入后台。
    const oldConvId = conversationId.value
    if (isStreaming.value && oldConvId && messages.value.length > 0) {
      backgroundConversationMessages.set(oldConvId, messages.value)
      // 保存续连状态，切回时可重连
      if (lastEventId.value) {
        reconnectStates.set(oldConvId, lastEventId.value)
      }
      stopReconnectPoll()
    }

    prepareNewConversation()
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
    // 同时清空内存中的已选数据源（不同工作空间的数据源不同），
    // 否则持久化 watcher 会把旧值重新写回 localStorage
    selectedDatasourceIds.value = []
    // 清理续连状态
    clearReconnectState()

    // 清理内存状态
    messages.value = []
    conversations.value = []
    streamingConversations.value = new Set()
    backgroundConversationMessages.clear()
    backgroundCompletedConversations.value = new Set()
    reconnectStates.clear()
    lastEventId.value = null
    seenEventIds.value.clear()
    disconnectedBySwitch = false
    userStopped = false
    streamConversationId = ''
    if (stopFallbackTimer) {
      clearTimeout(stopFallbackTimer)
      stopFallbackTimer = null
    }

    // 重置会话状态，避免复用旧工作空间的会话
    prepareNewConversation()
  }

  async function fetchConversations(): Promise<void> {
    conversationsLoading.value = true
    try {
      const list = await conversationApi.listConversations() as unknown as Conversation[]
      conversations.value = list
      // 重连进行中时不修改 conversationId，避免 fetchConversations 与 tryResumeStream 并发执行时
      // 竞态修改 conversationId 导致重连使用了错误的会话 ID。
      // 若后端已是 idle，则说明本地续连状态是残留脏数据，需要清理后继续正常加载。
      if (isStreaming.value) {
        return
      }
      const pendingReconnect = loadPersistedReconnectState()
      if (pendingReconnect?.conversationId) {
        const pendingConversation = list.find(c => c.conversationId === pendingReconnect.conversationId)
        if (pendingConversation?.streamStatus === 'running') {
          // 后端仍在运行，保留 reconnect 状态等 tryResumeStream 接管
          return
        }
        clearReconnectState()
      }
      // 检查当前选中的会话是否仍然存在于列表中
      // 如果不存在（比如被删除了，或刷新后恢复了无效的旧 ID），清除
      if (conversationId.value && !list.some(c => c.conversationId === conversationId.value)) {
        // 如果有会话列表，选中第一个；否则置空等待用户新建
        if (list.length > 0) {
          // 按最后活跃时间排序，选中最新的会话
          const sorted = [...list].sort((a, b) =>
            new Date(b.lastActiveTime || b.updateTime || b.createTime).getTime() -
            new Date(a.lastActiveTime || a.updateTime || a.createTime).getTime()
          )
          conversationId.value = sorted[0].conversationId
          // 自动落到其他会话后，重新启用「建议新开对话」提示
          newConversationHintDismissed.value = false
        } else {
          conversationId.value = ''
          messages.value = []
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
    if (!convId) return

    // 相同会话且消息已加载时短路，避免重复请求；
    // 刷新页面后 conversationId 会从 sessionStorage 恢复但 messages 为空，
    // 此时不能跳过，否则会出现"第一条历史点不进去"的 bug
    if (!force && conversationId.value === convId && messages.value.length > 0) return

    // 若目标会话既不是已有会话，也没有后台缓存，仍尝试 listMessages ——
    // 会话可能已在后端创建但尚未反映到本地列表（fetchConversations 竞态）。
    // 只有 listMessages 也失败/返回空时，才清空前台消息。
    const existsInList = conversations.value.some(c => c.conversationId === convId)
    const hasCached = backgroundConversationMessages.has(convId)
    if (!existsInList && !hasCached) {
      const oldConvId = conversationId.value
      if (oldConvId && !isStreaming.value && oldConvId !== convId) {
        backgroundConversationMessages.delete(oldConvId)
      }
      conversationId.value = convId
      // 切换会话时清空上下文使用数据并收起面板，避免展示上个会话的占比
      contextUsage.value = null
      contextUsagePanelOpen.value = false
      // 新会话从头计数，重新启用「建议新开对话」提示
      newConversationHintDismissed.value = false
      // Try listMessages anyway — the conversation may exist server-side
      // but not yet be in the local conversations list (race condition).
      try {
        const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
        const parsed = msgList
          .filter(m => m.role === 'user' || m.role === 'assistant')
          .map(buildChatMessageFromVO)
        if (parsed.length > 0) {
          messages.value = parsed
          clearReconnectState()
          fetchContextUsage()
          return
        }
      } catch (e) {
        logDebug('switchConversation: listMessages failed for conv not in list', e)
      }
      messages.value = []
      clearReconnectState()
      return
    }

    const oldConvId = conversationId.value
    const isSameConversation = oldConvId === convId

    // 切换到不同会话时，清空上下文使用数据并收起面板，避免展示上一个会话的占比；
    // 下文 fetchContextUsage() 会重新拉取目标会话的数据
    if (!isSameConversation) {
      contextUsage.value = null
      contextUsagePanelOpen.value = false
      // 目标会话从头计数，重新启用「建议新开对话」提示
      newConversationHintDismissed.value = false
    }

    // 切换到不同会话时，将当前消息存入后台缓存
    // - 流式生成中切换：直接引用 messages.value，sendMessage 的 for-await 循环会继续更新同一数组
    // - 对话完成后切换：浅拷贝消息数组，切回时可直接恢复，避免因后端 listMessages 延迟或异常导致空白
    // 派生 isStreaming 状态会因 messages.value 被替换为新会话消息而自动变为 false
    if (!isSameConversation && oldConvId && messages.value.length > 0) {
      if (isStreaming.value) {
        backgroundConversationMessages.set(oldConvId, messages.value)
      } else {
        backgroundConversationMessages.set(oldConvId, [...messages.value])
      }
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
        // 若后台会话仍在运行中，恢复最后一条 assistant 消息的 streaming 状态
        // 派生 isStreaming 会自动变为 true
        if (streamingConversations.value.has(convId)) {
          const lastCached = cachedMsgs[cachedMsgs.length - 1]
          if (lastCached?.role === 'assistant') {
            lastCached.status = 'streaming'
          }
        }
      } else {
        // 先设置 conversationId，避免 await 期间短路逻辑失效或 fetchConversations 竞态修改
        conversationId.value = convId
        try {
          const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
          messages.value = msgList
            .filter(m => m.role === 'user' || m.role === 'assistant')
            .map(buildChatMessageFromVO)
        } catch (e) {
          // listMessages 失败时恢复 conversationId，避免停留在不一致状态
          logDebug('switchConversation: listMessages failed, restoring conversationId', e)
          conversationId.value = oldConvId
          throw e
        }
      }
      if (!isSameConversation) {
        // 检查目标会话是否有保存的续连状态，如果有则恢复并尝试重连
        const savedLastEventId = reconnectStates.get(convId)
        if (savedLastEventId && isConversationRunningOnServer(convId)) {
          lastEventId.value = savedLastEventId
          seenEventIds.value.clear()
          reconnectStates.delete(convId)
          savePersistedReconnectState({ conversationId: convId, lastEventId: savedLastEventId })
          // 异步尝试重连，不阻塞历史消息加载
          reconnect()
        } else {
          reconnectStates.delete(convId)
          clearReconnectState()
        }
      }
      // 切换会话后刷新上下文使用情况（圆圈指示器需要实时展示占用百分比）
      fetchContextUsage()
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

  /**
   * 停止当前对话生成。
   * <p>
   * 借鉴 mateclaw-ui 的 stopGeneration 设计：
   * 1. 立即设置消息状态为 'stopped'，给用户即时反馈（派生 isStreaming 自动变 false）
   * 2. 启动 3 秒兜底定时器，防止 done 事件未到达时状态卡死
   * 3. 发送 stop 请求到后端（fire-and-forget）
   */
  async function stopChat(): Promise<void> {
    const stoppedConvId = conversationId.value
    if (!isStreaming.value || !stoppedConvId) return
    userStopped = true
    // 用户主动停止：禁止断连轮询重连
    stopReconnectPoll()

    // 立即更新消息状态，给用户即时反馈
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant') {
      lastMsg.status = 'stopped'
      const segments = ensureSegments(messages.value, messages.value.length - 1)
      stopFinalizeSegments(segments)
      // 顶层计划同步置 stopped：否则 PlanStepsPanel 在步骤未齐时头图标会永远转圈，
      // 步骤恰好都有结果时又会被 allDone 误判成绿色“已完成”勾
      const topPlan = (lastMsg.metadata as Record<string, unknown> | undefined)?.plan as PlanMeta | undefined
      if (topPlan && topPlan.planStatus === 'running') {
        topPlan.planStatus = 'stopped'
      }
    }
    // 立即断开本地 SSE 连接：stopChat 此前只改本地状态 + 发 stop 请求，
    // 连接不关会使旧流悬挂在 reader.read()，重发后 isStreaming 因新流重新为 true，
    // 旧流的迟到事件会穿过 break 检查写回已停止的消息（复活）。
    // 断连后流循环在 finally 里统一清除流式标记；在连接真正关闭前的窗口内，
    // sendMessage 的 streamingConversations 守卫会拦住同会话重发，
    // 关闭“后端两个 run 并存 → 历史出现连续两条相同用户消息”的重叠窗口。
    activeAbort?.()
    activeAbort = null
    reconnectStates.delete(stoppedConvId)
    backgroundConversationMessages.delete(stoppedConvId)
    const nextCompleted = new Set(backgroundCompletedConversations.value)
    nextCompleted.delete(stoppedConvId)
    backgroundCompletedConversations.value = nextCompleted
    clearReconnectState()
    // 写入停止标记：后端 finalize 异步落库存在 stream_status 残留 running 的窗口，
    // 刷新后 tryResumeStream 据此跳过对该会话的续连回放，避免数据流被重新推送
    saveStoppedMarker(stoppedConvId)

    // 3 秒兜底定时器：done 事件没来时确保状态已清理（消息状态已设置，此处为安全网）
    if (stopFallbackTimer) clearTimeout(stopFallbackTimer)
    stopFallbackTimer = setTimeout(() => {
      stopFallbackTimer = null
      console.warn('[ChatStore] Stop fallback: done event not received within 3s, force cleanup')
      const msg = messages.value[messages.value.length - 1]
      if (msg?.role === 'assistant' && msg.status === 'streaming') {
        msg.status = 'stopped'
      }
      // 安全网：流循环若仍未退出（异常路径），在这里断开并清除流式标记
      activeAbort?.()
      activeAbort = null
      markConversationStreaming(stoppedConvId, false)
    }, 3000)

    // 发送 stop 请求到后端（fire-and-forget，不阻塞 UI）
    try {
      await stopStream(stoppedConvId)
    } catch (e) {
      console.warn('[ChatStore] stop request failed:', e)
    }
  }

  /**
   * 断开前端 SSE 连接，但不停止后端流。
   * 用于切换对话时保留后端流的续连能力，切回后可通过 reconnect 恢复。
   * 同时清除当前会话的流式标记，避免侧栏一直显示旋转图标；
   * 切回时 reconnect 会重新设置流式标记。
   */
  function disconnectStream(): void {
    disconnectedBySwitch = true
    // 切走会话：停止对该会话的断连轮询（不轮询非当前会话）
    stopReconnectPoll()
    // 派生 isStreaming 会因 messages.value 被替换而自动变 false
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

  /** 从 SSE 事件中提取后端确认的真实会话 ID。 */
  function getSseConversationId(sse: SseEvent): string | null {
    if (!sse.data || typeof sse.data !== 'object') {
      return null
    }
    const id = sse.data.conversationId
    return typeof id === 'string' && id ? id : null
  }

  async function sendMessage(agentId: number | string, message: string, contentParts?: MessageContentPart[], preferredConversationId?: string): Promise<void> {
    if (isStreaming.value) return

    // 新对话时生成临时 UUID 传给后端（后端 getOrCreateConversation 据此创建会话），
    // 但不持久化到 localStorage——等 SSE session 事件返回真实 ID 后再持久化。
    // 若上传附件时已生成本地临时 ID（附件按该 ID 存储），优先复用，
    // 保证附件目录与会话 ID 一致。
    if (!conversationId.value) {
      const tempId = preferredConversationId
        || (self.crypto.randomUUID ? self.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`)
      conversationId.value = tempId
    }

    let convId = conversationId.value

    if (streamingConversations.value.has(convId)) {
      console.warn('[ChatStore] 该会话已有流在运行中，请等待当前对话完成后再发送消息')
      return
    }

    // 重置用户停止标记，新消息允许正常重连
    userStopped = false
    // 清除上一次 stopChat 的兜底定时器
    if (stopFallbackTimer) {
      clearTimeout(stopFallbackTimer)
      stopFallbackTimer = null
    }
    // 设置当前流所属会话，用于 isStaleEvent 判断
    streamConversationId = convId

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

    // 发送后立即刷新会话活跃时间，使左侧列表按更新时间即时置顶（新会话由末尾 fetchConversations 处理）
    touchConversationActiveTime(convId)

    const assistantMessage: ChatMessage = {
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      metadata: {},
      status: 'streaming',
      datasourceIds: selectedDatasourceIds.value.length > 0 ? [...selectedDatasourceIds.value] : undefined,
    }
    targetMsgs.push(assistantMessage)

    // isStreaming 为派生状态，assistant 消息 status='streaming' 使其自动为 true
    markConversationStreaming(convId, true)
    currentAgentId.value = agentId

    const assistantIdx = targetMsgs.length - 1
    const flushBuf = new FlushBuffer(assistantIdx, () => targetMsgs)

    clearReconnectState()
    // 同一会话重新发送消息：旧的停止标记失效（新流开始后恢复正常的续连语义）
    clearStoppedMarker(convId)
    savePersistedReconnectState({ conversationId: convId, lastEventId: null })

    /** 当前会话是否已切到后台（用户切到其他会话后，本流继续运行） */
    let isBackground = false
    /** 本流的 abort 句柄（用于 finally 中按身份清理 activeAbort，避免误清新流的注册） */
    let myAbort: (() => void) | null = null

    try {
      const streamOptions = {
        onLastEventId: (id: string) => {
          lastEventId.value = id
          savePersistedReconnectState({ conversationId: convId, lastEventId: id })
        },
        seenEventIds: seenEventIds.value,
        onAbortController: (c: AbortController) => {
          myAbort = () => c.abort()
          activeAbort = myAbort
        },
      }

      let streamFinished = false
      /** 新会话是否已触发列表刷新（避免重复调用 fetchConversations） */
      let listRefreshedForNewConv = false
      for await (const sse of streamChat(agentId, message, convId, modelProvider, modelName, streamOptions, selectedDatasourceIds.value, contentParts)) {
        // 收到首个事件后立即刷新会话列表，使新会话出现在侧栏中。
        // 后端在 SSE 端点入口处 getOrCreateConversation 已创建会话记录，
        // 此时刷新可让用户在流式生成中切换到其他会话后，仍能在侧栏看到并切回新会话。
        if (isNewConversation && !listRefreshedForNewConv) {
          listRefreshedForNewConv = true
          fetchConversations()
        }

        const eventConversationId = getSseConversationId(sse)
        if (eventConversationId && eventConversationId !== convId) {
          const wasActiveConversation = conversationId.value === convId
          markConversationStreaming(convId, false)
          convId = eventConversationId
          markConversationStreaming(convId, true)
          if (wasActiveConversation) {
            conversationId.value = convId
          }
          savePersistedReconnectState({ conversationId: convId, lastEventId: lastEventId.value })
        }

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
          // 按“本流自己的” assistant 消息状态判断，而非全局 isStreaming：
          // 用户停止后立即重发时，isStreaming 会因新流重新为 true，
          // 若用全局值，本已停止的旧流的迟到事件会穿过检查写回旧消息（复活）
          const ownMsg = targetMsgs[assistantIdx]
          if (!ownMsg || ownMsg.status !== 'streaming') {
            break
          }
        }

        handleSseEvent(sse, flushBuf, targetMsgs, convId, () => { streamFinished = true })
        if (streamFinished) break
      }

      flushBuf.flush()

      if (!streamFinished && !isBackground) {
        clearReconnectState()
      }

      if (isNewConversation) {
        fetchConversations()
      }
    } catch (error) {
      flushBuf.flush()
      // 流式连接中途断开：若非用户主动取消、非后台模式，启动退避轮询重连。
      // 不立即 reconnect——复用同一 STREAM_TIMEOUT_MS 容易再断，改为退避轮询更稳。
      // 常见场景：网络抖动 / SSE 临时断流 / 长任务心跳未到达前端。
      if (convId && !userStopped && !isBackground) {
        // pollReconnectOnce 内 isStreaming 检查会拦截，先把占位消息设为非 streaming
        const errLastMsg = targetMsgs[targetMsgs.length - 1]
        if (errLastMsg?.role === 'assistant' && errLastMsg.status === 'streaming') {
          errLastMsg.status = 'completed'
        }
        startReconnectPoll(convId)
        return
      }
      const lastMsg = targetMsgs[assistantIdx]
      if (lastMsg && lastMsg.role === 'assistant' && lastMsg.status === 'streaming') {
        lastMsg.status = 'failed'
        if (!lastMsg.content) {
          lastMsg.content = '抱歉，请求出现异常，请稍后重试。'
        }
      }
    } finally {
      flushBuf.destroy()
      if (isBackground) {
        // 后台会话：不修改前台消息状态；流结束后更新后台缓存
        markConversationStreaming(convId, false)
        backgroundConversationMessages.set(convId, targetMsgs)
      } else {
        // 安全网：确保“本流自己的”消息状态不再为 'streaming'；
        // 不能用 messages.value 末尾——旧流 finally 滞后于新流启动时会误标新流消息
        const finOwnMsg = targetMsgs[assistantIdx]
        if (finOwnMsg?.role === 'assistant' && finOwnMsg.status === 'streaming') {
          finOwnMsg.status = 'completed'
        }
        markConversationStreaming(convId, false)
      }
      if (activeAbort === myAbort) {
        activeAbort = null
      }
      disconnectedBySwitch = false
    }
  }

  async function reconnect(): Promise<void> {
    logDebug('reconnect: entry, conversationId=', conversationId.value, 'isStreaming=', isStreaming.value)
    if (!conversationId.value) return
    if (isStreaming.value) return

    const convId = conversationId.value

    // 会话不在列表中说明已被删除或 ID 无效，只清理续连状态，不修改用户选中的会话
    if (!conversations.value.some(c => c.conversationId === convId)) {
      logDebug('reconnect: conversation not in list, clearing state')
      clearReconnectState()
      return
    }

    // 续连时始终从头（id=0）回放整个 buffer，并清空去重集合。
    // 原因：reconnect 会用持久化历史重建 messages（见下方 listMessages），
    // 而流式生成中的 assistant 增量在终态前不会落库，历史里没有这些事件；
    // 若沿用刷新前中途的 lastEventId，后端只回放该 id 之后的事件，导致刷新前
    // 已推送的 content/thinking/tool 事件全部丢失，表现为"消息流没有回放"。
    // 从 0 回放 + 空 seenEventIds 可在全新的占位消息上完整重建消息流。
    const savedLastEventId = '0'
    seenEventIds.value.clear()

    // 派生 isStreaming 通过下方 assistant 占位消息的 status='streaming' 自动为 true
    markConversationStreaming(convId, true)
    streamConversationId = convId

    // 续连前先拉取历史消息，确保 UI 上能看到完整对话上下文。
    // 后端在流终态前不会持久化 assistant 消息，因此历史里的最后一条
    // 通常是 user；下面会补一条 assistant 占位用于承接回放的 content_delta。
    let hasPersistedAssistant = false
    try {
      const msgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
      messages.value = msgList
        .filter(m => m.role === 'user' || m.role === 'assistant')
        .map(buildChatMessageFromVO)
      // 检查历史末尾是否已有 assistant 消息（说明后端已持久化）
      const lastMsg = messages.value[messages.value.length - 1]
      hasPersistedAssistant = lastMsg?.role === 'assistant' && !!lastMsg.content
    } catch (e: unknown) {
      console.warn('[ChatStore] Reconnect: failed to load history messages', e)
      // 会话不存在（404）或无权访问（403）：清理脏数据，不重连
      const status = (e as { response?: { status?: number } })?.response?.status
      if (status === 404 || status === 403) {
        clearReconnectState()
        markConversationStreaming(convId, false)
        return
      }
    }

    // 关键判断：如果 listMessages 已返回有内容的 assistant 消息，
    // 且后端已不再运行（streamStatus !== 'running'），说明对话已完成，
    // 不需要走 SSE buffer 回放——直接渲染 listMessages 的结果即可。
    // 必须同时满足两个条件：有持久化内容 + 后端已空闲，避免对话进行中误判。
    if (hasPersistedAssistant && !isConversationRunningOnServer(convId)) {
      // 已有持久化的 assistant 消息，直接标记为 completed
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.status = 'completed'
      }
      markConversationStreaming(convId, false)
      clearReconnectState()
      logDebug('Reconnect: SHORT-CIRCUIT (assistant persisted + stream idle), skipping SSE replay')
      return
    }

    // ---- 以下为 SSE 回放逻辑 ----
    // 两种情况会走到这里：
    // A. listMessages 没有 assistant（对话进行中，未落库）→ 补空占位
    // B. listMessages 有 assistant 且对话仍在运行 → 清空 content 让 SSE 重建，避免重复追加

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
        status: 'streaming',
      })
    } else {
      // 已有 assistant 消息：清空 content，让 SSE 回放从头构建。
      // 不清空会导致 content_delta 追加到已有内容上，造成"双重内容"。
      // SSE 回放完成后，done 事件的 authoritativeContent 会覆盖为正确内容。
      // 同时清空 segments，让 SSE 事件重建执行过程。
      if (hasPersistedAssistant) {
        last.content = ''
        if (last.metadata) {
          const meta = last.metadata as Record<string, unknown>
          delete meta.segments
          delete meta.toolCalls
        }
        logDebug('Reconnect: cleared persisted assistant content for SSE replay rebuild')
      }
      last.status = 'streaming'
    }

    const replayIdx = messages.value.length - 1
    /** 本流的 abort 句柄（用于 finally 中按身份清理 activeAbort，避免误清新流的注册） */
    let myAbort: (() => void) | null = null

    const streamOptions = {
      onLastEventId: (id: string) => {
        lastEventId.value = id
        savePersistedReconnectState({ conversationId: convId, lastEventId: id })
      },
      seenEventIds: seenEventIds.value,
      onAbortController: (c: AbortController) => {
        myAbort = () => c.abort()
        activeAbort = myAbort
      },
    }

    const flushBuf = new FlushBuffer(replayIdx, () => messages.value)

    try {
      let streamFinished = false
      let gotDoneWithStatus = ''
      // 回放模式下批量消费 buffer 中的高频事件，避免逐帧渲染导致"重新打字"的视觉效果。
      // 累积 content_delta 到一定量后一次性 flush，或遇到低频事件（tool_call/done 等）时立即 flush。
      let replayBatchCount = 0
      const REPLAY_BATCH_SIZE = 20

      let reconnectEventCount = 0
      for await (const sse of reconnectStream(convId, savedLastEventId, streamOptions)) {
        reconnectEventCount++
        if (reconnectEventCount === 1) {
          logDebug('Reconnect: first SSE event received, evt=', sse.event, 'id=', sse.id)
        }
        const ownMsg = messages.value[replayIdx]
        if (!ownMsg || ownMsg.status !== 'streaming') {
          logDebug('Reconnect: own replay message no longer streaming, breaking loop at event count=', reconnectEventCount)
          break
        }

        const evt = sse.event
        // 低频事件：先 flush 累积的 content，再处理
        if (evt !== 'content_delta' && evt !== 'thinking_delta' && evt !== 'heartbeat') {
          flushBuf.flush()
          replayBatchCount = 0
        }

        handleSseEvent(sse, flushBuf, messages.value, convId, () => { streamFinished = true })

        // 高频事件：批量累积后 flush
        if (evt === 'content_delta' || evt === 'thinking_delta') {
          replayBatchCount++
          if (replayBatchCount >= REPLAY_BATCH_SIZE) {
            flushBuf.flush()
            replayBatchCount = 0
          }
        }

        // 捕获 done 事件中的 status，用于判断对话是否真正完成
        if (evt === 'done' && sse.data && typeof sse.data === 'object') {
          gotDoneWithStatus = (sse.data as Record<string, unknown>).status as string || ''
        }

        if (streamFinished) break
      }
      flushBuf.flush()
      logDebug('Reconnect: loop ended, total events=', reconnectEventCount, 'streamFinished=', streamFinished, 'gotDoneWithStatus=', gotDoneWithStatus)
      // 流未结束、也没收到 done（SSE 连接断开）时，保留 sessionStorage 的 reconnect state
      // 让新页面 tryResumeStream 能读到 conversationId 并重新 reconnect 回放 buffer。
      // 同时启动退避轮询：覆盖"非刷新的网络断连"场景（刷新场景下旧 store 随页面销毁，
      // timer 自然失效，新页面的 tryResumeStream 会接管）。
      if (!streamFinished && !gotDoneWithStatus) {
        logDebug('Reconnect: SSE connection closed before stream finished, starting reconnect poll')
        startReconnectPoll(convId)
      }

      // 对话完成后，如果 assistant 消息内容仍为空（可能 SSE 回放的 done 事件
      // 没有携带 authoritativeContent，或 listMessages 返回时后端还没持久化），
      // 再请求一次 listMessages 拿到后端已落库的完整内容，避免"空消息"或"只有执行过程"。
      if (gotDoneWithStatus || streamFinished) {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg?.role === 'assistant' && !lastMsg.content) {
          try {
            const refreshedMsgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
            const refreshedAssistant = refreshedMsgList
              .filter(m => m.role === 'assistant')
              .map(buildChatMessageFromVO)
              .pop()
            if (refreshedAssistant && refreshedAssistant.content) {
              // 用后端持久化的完整内容替换空占位
              lastMsg.content = refreshedAssistant.content
              if (refreshedAssistant.metadata) {
                lastMsg.metadata = refreshedAssistant.metadata
              }
              if (refreshedAssistant.cards) {
                lastMsg.cards = refreshedAssistant.cards
              }
              logDebug('Reconnect: backfilled assistant content from listMessages after done')
            }
          } catch (e) {
            logDebug('Reconnect: failed to backfill assistant content after done', e)
          }
        }
      }
      // When stream_not_local is received and content is still empty,
      // the backend RunState has expired. Fall back to listMessages
      // with a short delay to load the persisted history. If the assistant
      // is still not persisted, clear the reconnect state so the next refresh
      // will use switchConversation instead.
      if (gotDoneWithStatus === 'stream_not_local') {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg?.role === 'assistant' && !lastMsg.content) {
          logDebug('Reconnect: stream_not_local with empty content, retrying listMessages with delay')
          try {
            await new Promise(r => setTimeout(r, 500))
            const refreshedMsgList = await conversationApi.listMessages(convId) as unknown as MessageVO[]
            const refreshed = refreshedMsgList
              .filter(m => m.role === 'user' || m.role === 'assistant')
              .map(buildChatMessageFromVO)
            if (refreshed.length > 0) {
              const lastRefreshed = refreshed[refreshed.length - 1]
              if (lastRefreshed?.role === 'assistant' && lastRefreshed.content) {
                messages.value = refreshed
                lastRefreshed.status = 'completed'
                logDebug('Reconnect: stream_not_local fallback loaded persisted messages')
              } else {
                // Assistant still not persisted — clear reconnect state
                // so the next refresh uses switchConversation's listMessages
                clearReconnectState()
                logDebug('Reconnect: stream_not_local fallback: assistant still not persisted, cleared reconnect state')
              }
            }
          } catch (e) {
            logDebug('Reconnect: stream_not_local fallback listMessages failed', e)
            clearReconnectState()
          }
        }
      }
    } catch (error) {
      flushBuf.flush()
      const isAbort = error instanceof Error && error.name === 'AbortError'
      console.warn('[ChatStore] Reconnect failed (isAbort=' + isAbort + '):', error)
      // AbortError 通常是页面卸载/刷新导致 fetch 被取消，保留 sessionStorage 让新页面能重连
      if (!isAbort) {
        clearReconnectState()
      }
      const errMsg = messages.value[replayIdx]
      if (errMsg?.role === 'assistant' && errMsg.status === 'streaming') {
        errMsg.status = 'failed'
      }
    } finally {
      flushBuf.destroy()
      // 安全网：确保“本流自己的”消息状态不再为 'streaming'
      const finMsg = messages.value[replayIdx]
      if (finMsg?.role === 'assistant' && finMsg.status === 'streaming') {
        finMsg.status = finMsg.content ? 'completed' : 'failed'
      }
      markConversationStreaming(convId, false)
      if (activeAbort === myAbort) {
        activeAbort = null
      }
    }
  }

  /**
   * 刷新页面 / 切回 tab 时尝试续连上一次的 SSE 流。
   * <p>
   * 前置条件：sessionStorage 中持久化了 conversationId（由 sendMessage 启动时写入）。
   * <p>
   * 工作流程：始终调用 reconnect()，由其统一处理——
   * <ul>
   *   <li>后端 RunState 仍存在 → SSE buffer 回放（含进行中和已完成但 5 分钟保留期内的对话）</li>
   *   <li>后端 RunState 不存在 → 返回 stream_not_local / completed 的 done 事件，
   *       reconnect 内部收到后清理状态，listMessages 已渲染完整内容</li>
   * </ul>
   * 不再在入口处用 streamStatus 短路，避免竞态导致进行中的对话被误判为已完成。
   */
  async function tryResumeStream(): Promise<boolean> {
    // 刷新/切回续连接管：停止可能残留的断连轮询，避免与 reconnect 叠加
    stopReconnectPoll()
    const persisted = loadPersistedReconnectState()
    logDebug('tryResumeStream: persisted=', persisted, 'isStreaming=', isStreaming.value)
    if (isStreaming.value) return false

    await fetchConversations()

    // 续连候选 conversationId：优先 sessionStorage 里的 reconnect 状态，
    // 没有时回退到 localStorage 恢复的当前选中会话（场景：流生成中刷新，
    // 但 sessionStorage 被上一次 done 清掉 / 新会话刚发消息还没写 sessionStorage）。
    const candidateConvId = persisted?.conversationId || conversationId.value
    if (!candidateConvId) return false

    // 用户在刷新前已主动停止该会话（stopChat 写入标记）：跳过续连回放，
    // 否则"停止后立即刷新"会因 stream_status 残留 running 而重新推送整个数据流。
    // 返回 false 走正常历史加载（switchConversation → listMessages）。
    if (loadStoppedMarker() === candidateConvId) {
      clearStoppedMarker(candidateConvId)
      logDebug('tryResumeStream: conversation stopped by user before refresh, skipping reconnect')
      return false
    }

    // 若候选 conversationId 不在已有会话列表中，说明会话已被删除或 ID 无效，
    // 只清理续连脏数据，不影响用户当前选中的会话
    const existsInList = conversations.value.some(c => c.conversationId === candidateConvId)
    const convInList = conversations.value.find(c => c.conversationId === candidateConvId)
    logDebug('tryResumeStream: candidateConvId=', candidateConvId, 'existsInList=', existsInList, 'streamStatus=', convInList?.streamStatus)
    if (!existsInList) {
      if (persisted?.conversationId) {
        clearReconnectState()
      }
      return false
    }

    // 实时探测为准，列表快照兜底（对齐 mateclaw-ui ChatConsole.vue 两层判断）：
    // 快照读的是 DB 的 stream_status（最终一致，可能残留 running，如停止后落库失败场景），
    // 实时 getStatus 后端以内存 RunState 优先解析、零延迟，是权威状态。
    // 因此无论快照结果如何都实时探测一次，仅在探测失败时回退到快照。
    let shouldReconnect = false
    try {
      const statusRes = await conversationApi.getStatus(candidateConvId) as { streamStatus?: string } | undefined
      shouldReconnect = statusRes?.streamStatus === 'running'
    } catch {
      shouldReconnect = convInList?.streamStatus === 'running'
    }
    if (!shouldReconnect) {
      logDebug('tryResumeStream: stream not running, skipping reconnect')
      return false
    }

    conversationId.value = candidateConvId
    // 从头（id=0）回放整个 buffer：流生成中 assistant 未落库，历史里没有这些事件，
    // 若沿用 lastEventId 会只回放该 id 之后的事件，导致刷新前已推送的内容丢失。
    lastEventId.value = '0'
    // 同步写 sessionStorage，保证后续再次刷新仍能走 reconnect 路径
    savePersistedReconnectState({ conversationId: candidateConvId, lastEventId: null })

    // 统一走 reconnect：由 SSE 请求判断后端是否仍有 buffer 可回放
    await reconnect()
    return true
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

  /** 关闭所有 status=running 的指定类型 segment，并计算耗时（仅对有 startTime 且无 durationMs 的 segment）。
   *  endTs 为可选的结束时刻：优先用事件携带的 timestamp（如 tool_call_started.timestamp，
   *  即工具开始执行时刻 T_tool_start），而非 Date.now()（事件到达时刻 ≈ T_tool_end）。
   *  这样 thinking segment 的 durationMs = T_tool_start - T_think_start，严格不含工具执行时间。 */
  function finalizeRunningSegments(segments: Array<Record<string, unknown>>, types: string[], endTs?: number): void {
    const end = typeof endTs === 'number' && endTs > 0 ? endTs : Date.now()
    for (const seg of segments) {
      if (seg.status === 'running' && types.includes(seg.type as string)) {
        seg.status = 'completed'
        finalizeDuration(seg, end)
      }
    }
  }

  /** 关闭所有 status=running 的 segment（对话结束时调用） */
  function finalizeAllRunningSegments(segments: Array<Record<string, unknown>>): void {
    for (const seg of segments) {
      if (seg.status === 'running') {
        seg.status = 'completed'
        finalizeDuration(seg)
      }
    }
  }

  /**
   * 用户主动停止时的执行树收尾：所有 running 的 segment / 委派节点 / 子工具 / 子计划
   * 统一置为 'stopped'（中性终态）而非 'completed'。
   * 若沿用 completed，UI 会把被打断的执行画成绿色成功勾；若留在 running，
   * spinner 会永远转。渲染层对 'stopped' 画中性灰停止图标。
   */
  function stopFinalizeSegments(segments: Array<Record<string, unknown>>): void {
    for (const seg of segments) {
      if (seg.status === 'running') {
        seg.status = 'stopped'
        finalizeDuration(seg)
      }
      const tl = seg.childTimeline as DelegationTimeline | undefined
      if (tl?.children) finalizeDelegNodesStopped(tl.children)
    }
  }

  /** 递归把委派子节点及其工具/计划从 running 收尾为 stopped */
  function finalizeDelegNodesStopped(nodes: DelegationNode[] | undefined): void {
    if (!nodes) return
    for (const n of nodes) {
      if (n.status === 'running') n.status = 'stopped'
      if (n.tools) {
        for (const t of n.tools) {
          if (t.status === 'running') t.status = 'stopped'
        }
      }
      if (n.plan && n.plan.planStatus === 'running') {
        n.plan.planStatus = 'stopped'
      }
      finalizeDelegNodesStopped(n.children)
    }
  }

  /**
   * 为已完成的 segment 计算耗时（毫秒）。
   * thinking / content segment 由前端在创建时记 startTime，finalize 时据此算 durationMs；
   * tool_call segment 的 durationMs 由 tool_call_completed 事件携带（后端精确值），此处不覆盖。
   * endTs 为可选结束时刻，默认 Date.now()。
   */
  function finalizeDuration(seg: Record<string, unknown>, endTs?: number): void {
    if (seg.durationMs != null) return
    const start = seg.startTime as number | undefined
    if (!start) return
    const end = typeof endTs === 'number' && endTs > 0 ? endTs : Date.now()
    seg.durationMs = Math.max(0, end - start)
  }

  /**
   * 解析工具耗时（毫秒）。
   * 优先后端精确值 backendDur（≥0）；否则用 eventTs - startTime 回退；都无法确定返回 undefined。
   * backendDur = -1 表示后端未知（如 guard 拦截未执行），按未知处理。
   */
  function resolveDuration(backendDur: number | undefined, eventTs: number | undefined,
                            startTime: number | undefined): number | undefined {
    if (typeof backendDur === 'number' && backendDur >= 0) return backendDur
    if (typeof eventTs === 'number' && typeof startTime === 'number' && eventTs > startTime) {
      return eventTs - startTime
    }
    return undefined
  }

  /**
   * 判断后端权威 segment 的 durationMs 是否合理可信。
   * 后端 thinking/content 的 durationMs 仅对首轮被 startTimeCorrected 修正（准确）；
   * 若值 ≤0（空转/时钟回拨）或超出整流 wall-clock（修正错配到错误 segment 产生的横跨多轮大值），
   * 视为不可信，回退到前端实时流同类型同顺序的值。
   * tool_call 的 durationMs 由后端精确测量（nanoTime 差值），不在此校验。
   */
  function isPlausibleDuration(seg: Record<string, unknown>, streamWallMs: number): boolean {
    const t = String(seg.type ?? '')
    if (t !== 'thinking' && t !== 'content') return true
    const d = seg.durationMs
    return typeof d === 'number' && d > 0 && d <= streamWallMs + 2000
  }

  // ===== Agent delegation tree helpers =====
  // 委派事件形成一棵树：depth-1 子 agent 复用 type='tool_call' segment（id=subagentId），
  // depth-2+ 子 agent 是 DelegationNode，通过 parentSubagentId 挂到祖先的 childTimeline.children。
  // 每个 delegation_* 事件携带 subagentId/parentSubagentId/depth，扁平事件流据此重建为树。
  // 逻辑移植自 mateclaw-ui useChat.ts:942-1021，适配 dataagent-ui 的 Record<string, unknown> segment。

  type DelegContainer = { plan?: PlanMeta; tools?: DelegationToolEntry[]; children?: DelegationNode[] }

  /** 生成 segment/node 的兜底 id（subagentId 缺失时使用） */
  function genSegId(): string {
    return 'seg-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
  }

  /** 按 subagentId 或 childConversationId 查找 depth-1 委派 segment */
  function findDelegSegment(segs: Array<Record<string, unknown>>, subagentId?: string, childConvId?: string): Record<string, unknown> | undefined {
    if (subagentId) {
      const byId = segs.find(s => s.type === 'tool_call' && s.id === subagentId)
      if (byId) return byId
    }
    if (childConvId) return segs.find(s => s.type === 'tool_call' && s.id === childConvId)
    return undefined
  }

  /** 递归查找嵌套 DelegationNode by subagentId */
  function findDelegNode(nodes: DelegationNode[] | undefined, subagentId: string): DelegationNode | undefined {
    if (!nodes) return undefined
    for (const n of nodes) {
      if (n.subagentId === subagentId) return n
      const deep = findDelegNode(n.children, subagentId)
      if (deep) return deep
    }
    return undefined
  }

  /** 懒初始化 depth-1 segment 的 childTimeline 容器 */
  function ensureTimeline(seg: Record<string, unknown>): DelegContainer {
    let t = seg.childTimeline as DelegationTimeline | undefined
    if (!t) {
      t = { tools: [], children: [] }
      seg.childTimeline = t
    }
    if (!t.tools) t.tools = []
    if (!t.children) t.children = []
    return t
  }

  /** 懒初始化嵌套 DelegationNode 的 tools/children */
  function ensureNodeContainer(node: DelegationNode): DelegContainer {
    if (!node.tools) node.tools = []
    if (!node.children) node.children = []
    return node
  }

  /** 解析任意深度子 agent 的进度容器（segment 的 childTimeline 或嵌套 node） */
  function resolveContainer(segs: Array<Record<string, unknown>>, subagentId?: string, childConvId?: string): DelegContainer | undefined {
    const seg = findDelegSegment(segs, subagentId, childConvId)
    if (seg) return ensureTimeline(seg)
    if (subagentId) {
      for (const s of segs) {
        const node = findDelegNode((s.childTimeline as DelegationTimeline | undefined)?.children, subagentId)
        if (node) return ensureNodeContainer(node)
      }
    }
    return undefined
  }

  /** 标记子 agent（segment 或嵌套 node）完成 */
  function markDelegComplete(segs: Array<Record<string, unknown>>, subagentId: string | undefined, childConvId: string | undefined,
                             success: boolean, resultPreview?: string, durationMs?: number): boolean {
    const seg = findDelegSegment(segs, subagentId, childConvId)
    if (seg) {
      seg.status = success ? 'completed' : 'error'
      seg.toolSuccess = success
      if (resultPreview) seg.toolResult = resultPreview
      if (durationMs) seg.toolArgs = ((seg.toolArgs as string) || '').trimEnd() + ` (${Math.round(durationMs / 1000)}s)`
      return true
    }
    if (subagentId) {
      for (const s of segs) {
        const node = findDelegNode((s.childTimeline as DelegationTimeline | undefined)?.children, subagentId)
        if (node) {
          node.status = success ? 'completed' : 'error'
          if (resultPreview) node.result = resultPreview
          if (durationMs) node.durationMs = durationMs
          return true
        }
      }
    }
    return false
  }

  /** 创建 depth-1 segment（树顶）或嵌套 DelegationNode（更深层） */
  function addDelegation(segs: Array<Record<string, unknown>>, info: Record<string, unknown>, opts: { async?: boolean } = {}) {
    const subagentId = info.subagentId as string | undefined
    const parentSubagentId = info.parentSubagentId as string | undefined
    const agentName = (info.childAgentName as string) || 'Agent'
    const depth = (info.depth as number) || 1
    const task = (info.task as string) || ''

    if (parentSubagentId) {
      // depth-2+：挂到父 subagent 的容器
      const parent = resolveContainer(segs, parentSubagentId)
      if (!parent) return
      if (!findDelegNode(parent.children, subagentId || '')) {
        parent.children!.push({
          subagentId: subagentId || genSegId(),
          agentName, status: 'running', depth, task,
          tools: [], children: [],
          ...(opts.async ? { async: true } : {})
        })
      }
      return
    }
    // depth-1：顶层 segment，id=subagentId 用于稳定查找。去重防止 SSE 回放重复创建。
    const segId = subagentId || (info.childConversationId as string) || genSegId()
    if (segs.some(s => s.type === 'tool_call' && s.id === segId)) return
    segs.push({
      id: segId,
      type: 'tool_call',
      status: 'running',
      toolName: `→ ${agentName}`,
      toolArgs: task,
      childTimeline: { tools: [], children: [] },
      timestamp: Date.now(),
      ...(opts.async ? { delegationAsync: true } : {})
    })
  }

  /** 递归关闭所有嵌套 DelegationNode 中 status=running 的节点（流结束时调用） */
  function finalizeDelegNodes(nodes: DelegationNode[] | undefined): void {
    if (!nodes) return
    for (const n of nodes) {
      if (n.status === 'running') n.status = 'completed'
      if (n.tools) {
        for (const t of n.tools) {
          if (t.status === 'running') t.status = 'completed'
        }
      }
      finalizeDelegNodes(n.children)
    }
  }

  function handleSseEvent(sse: SseEvent, flushBuf: FlushBuffer, targetMsgs: ChatMessage[], convId: string, onFinished?: () => void): void {
    const evt = sse.event
    const data = sse.data

    if (typeof data !== 'object') return

    // 过滤过期会话的事件，防止切会话后旧流事件污染新会话
    if (isStaleEvent(data)) return

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
            contentSeg = { type: 'content', status: 'running', text: '', startTime: Date.now() }
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
            thinkSeg = { type: 'thinking', status: 'running', thinkingText: '', startTime: Date.now() }
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
        // 事件携带的 timestamp = 工具开始执行时刻（GraphEventPublisher.toolStart 在
        // ToolExecutionExecutor 执行工具前生成），即 T_tool_start = T_think_end。
        // 用它 finalize thinking/content segment，使 durationMs = T_tool_start - startTime，
        // 严格不含工具执行时间（若用 Date.now()，事件经 ACTION_NODE output 转发已延迟到
        // T_tool_end，会把工具执行时间计入思考耗时，导致思考耗时 ≈ 工具耗时）。
        const toolStartTs = data.timestamp as number | undefined
        if (toolName) {
          const prevMeta = (prev.metadata || {}) as Record<string, unknown>
          const prevToolCalls = (prevMeta.toolCalls as Array<Record<string, unknown>>) || []
          // 防御性去重：如果已存在相同 toolCallId 且仍为 running 的 toolCall，跳过重复事件
          const isDuplicate = prevToolCalls.some((tc: Record<string, unknown>) =>
            tc.status === 'running'
            && ((toolCallId && tc.toolCallId === toolCallId) || (!toolCallId && tc.name === toolName))
          )
          if (isDuplicate) break
          const nextToolCalls = [
            ...prevToolCalls,
            {
              toolCallId: toolCallId || '',
              name: toolName,
              arguments: toolArgs,
              status: 'running',
              startTime: toolStartTs ?? Date.now(),
            },
          ]
          // 关闭 running content/thinking segment（用 T_tool_start 而非 Date.now()），创建 running tool_call segment
          const segments = ensureSegments(targetMsgs, msgIdx)
          finalizeRunningSegments(segments, ['content', 'thinking'], toolStartTs)
          segments.push({
            type: 'tool_call',
            status: 'running',
            toolName,
            toolCallId: toolCallId || '',
            toolArgs: toolArgs || '',
            startTime: toolStartTs ?? Date.now(),
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
        // 工具耗时：优先后端精确值（durationMs），回退到 timestamp 差值
        const backendDur = data.durationMs as number | undefined
        const eventTs = data.timestamp as number | undefined
        if (toolName && prev.metadata) {
          const prevMeta = prev.metadata as Record<string, unknown>
          const prevToolCalls = (prevMeta.toolCalls as Array<Record<string, unknown>>) || []
          const nextToolCalls = prevToolCalls.map((tc: Record<string, unknown>) => {
            const isMatch = (tc.toolCallId && tc.toolCallId === toolCallId)
              || (!tc.toolCallId && tc.name === toolName && tc.status === 'running')
            if (isMatch) {
              const dur = resolveDuration(backendDur, eventTs, tc.startTime as number | undefined)
              return {
                ...tc,
                status: 'completed',
                success: success !== false,
                ...(result ? { result } : {}),
                ...(dur != null ? { durationMs: dur } : {}),
              }
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
              const dur = resolveDuration(backendDur, eventTs, seg.startTime as number | undefined)
              if (dur != null) seg.durationMs = dur
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
          lastMsg.status = 'stopped'
        } else if (status === 'failed') {
          lastMsg.status = 'failed'
          // 若尚无 errorInfo（后端未发送 error 事件），补充默认错误信息
          if (!lastMsg.errorInfo) {
            lastMsg.errorInfo = { category: 'unknown', rawMessage: '生成失败', retryable: true, timestamp: Date.now() }
          }
        }
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
        // 递归关闭委派子树中仍 running 的嵌套 DelegationNode / tool 条目
        for (const s of segments) {
          finalizeDelegNodes((s.childTimeline as DelegationTimeline | undefined)?.children)
        }
        break
      }
      case 'recommended_questions': {
        flushBuf.flush()
        const questions = data.questions as string[] | undefined
        if (questions && questions.length > 0) {
          const lastMsg = targetMsgs[targetMsgs.length - 1]
          if (lastMsg && lastMsg.role === 'assistant') {
            if (!lastMsg.cards) {
              lastMsg.cards = []
            }
            lastMsg.cards.push({
              type: 'recommended_questions',
              data: { questions },
            })
          }
        }
        break
      }
      case 'context_usage': {
        const usage = data as Record<string, unknown>
        if (usage.conversationId === conversationId.value) {
          updateContextUsage(usage as unknown as ContextUsage)
        }
        break
      }
      case 'done': {
        flushBuf.flush()
        // 流真正结束：停止断连轮询
        stopReconnectPoll()
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
        // 递归关闭委派子树中仍 running 的嵌套 DelegationNode / tool 条目
        for (const s of segments) {
          finalizeDelegNodes((s.childTimeline as DelegationTimeline | undefined)?.children)
        }
        // 用后端权威 segments 覆盖实时流中缺失 segmentOnly 标记的 segments
        const authoritativeSegments = data.segments as Array<Record<string, unknown>> | undefined
        if (authoritativeSegments && authoritativeSegments.length > 0) {
          const doneMsg2 = targetMsgs[msgIdx]
          if (doneMsg2?.role === 'assistant' && doneMsg2.metadata) {
            const meta = doneMsg2.metadata as Record<string, unknown>
            // 整流 wall-clock：用于校验后端 thinking/content durationMs 是否合理。
            // assistant 消息的 timestamp 是流起始时刻（消息创建时 Date.now()）。
            const streamWallMs = typeof doneMsg2.timestamp === 'number'
              ? Math.max(0, Date.now() - doneMsg2.timestamp) : Number.MAX_SAFE_INTEGER
            // 委派树（→ Agent名 segment 及其 childTimeline）由前端实时构建，
            // 后端 StreamAccumulator 不持久化 delegation 事件为 segment，
            // 权威覆盖会丢失整棵委派树。这里先提取实时 segments 中的委派 segment，
            // 覆盖后再按原顺序回填，使运行结束 / 刷新后委派树仍可见。
            const prevSegments = (meta.segments as Array<Record<string, unknown>>) || []
            const delegSegs = prevSegments.filter(s =>
              s.type === 'tool_call'
              && (s.childTimeline != null || (s.toolName as string)?.startsWith('→'))
            )
            // 合并前端实时流的 durationMs：后端权威 segments 中 thinking/content 的
            // durationMs 仅对首轮被修正（多轮 ReAct 的后续 segment 后端 startTime 不准，
            // 不在后端计算 durationMs）。前端实时流用 tool_call_started.timestamp
            // （T_tool_start）finalize 每轮 thinking，durationMs 严格不含工具执行时间。
            // 对后端缺少 durationMs 的 segment，按同类型同顺序从前端补充。
            const prevByType = new Map<string, Array<number | undefined>>()
            for (const ps of prevSegments) {
              const t = String(ps.type ?? '')
              if (!prevByType.has(t)) prevByType.set(t, [])
              prevByType.get(t)!.push(ps.durationMs as number | undefined)
            }
            const typeCursors = new Map<string, number>()
            for (const as of authoritativeSegments) {
              // 后端 thinking/content 的 durationMs 若异常（≤0 或超出整流时长，例如
              // 修正逻辑错配到错误 segment 产生的横跨多轮大值），视为不可信并清空，
              // 让下方"同类型同顺序补充"逻辑用前端实时流的准确值填入。
              if (as.durationMs != null && !isPlausibleDuration(as, streamWallMs)) {
                delete as.durationMs
              }
              if (as.durationMs != null) continue
              const t = String(as.type ?? '')
              const bucket = prevByType.get(t)
              if (!bucket) continue
              const cursor = typeCursors.get(t) ?? 0
              for (let i = cursor; i < bucket.length; i++) {
                if (bucket[i] != null) {
                  as.durationMs = bucket[i]
                  typeCursors.set(t, i + 1)
                  break
                }
              }
            }
            if (delegSegs.length > 0) {
              // 权威 segments 不含委派 segment，直接追加到末尾；
              // 顺序上委派发生在 delegateToAgent 工具调用之后，末尾追加符合时序。
              meta.segments = [...authoritativeSegments, ...delegSegs]
            } else {
              meta.segments = authoritativeSegments
            }
          }
        }
        // 用后端权威最终答案覆盖 msg.content：实时流累积的 content 含中间旁白，
        // 与持久化不一致（影响复制、以及无最终答案段时 getFinalAnswer 的兜底）。
        const authoritativeContent = data.content as string | undefined
        if (typeof authoritativeContent === 'string' && authoritativeContent.length > 0) {
          const contentMsg = targetMsgs[msgIdx]
          if (contentMsg?.role === 'assistant') {
            contentMsg.content = authoritativeContent
          }
        }
        // 设置消息状态为 completed（派生 isStreaming 自动变 false）
        const doneMsg = targetMsgs[msgIdx]
        if (doneMsg?.role === 'assistant') {
          doneMsg.status = 'completed'
        }
        // 后台会话完成：标记未读
        if (conversationId.value !== convId) {
          markConversationStreaming(convId, false)
          backgroundConversationMessages.set(convId, targetMsgs)
          const nextCompleted = new Set(backgroundCompletedConversations.value)
          nextCompleted.add(convId)
          backgroundCompletedConversations.value = nextCompleted
        } else {
          // Only clear reconnect state if the assistant message has content.
          // If content is empty (e.g. stream_not_local where RunState expired),
          // keep reconnect state so the next refresh can retry via switchConversation's
          // listMessages fallback, rather than leaving an empty completed message.
          const doneLastMsg = targetMsgs[targetMsgs.length - 1]
          if (doneLastMsg?.role === 'assistant' && doneLastMsg.content) {
            clearReconnectState()
          } else {
            logDebug('Reconnect: keeping reconnect state (assistant content empty on done)')
          }
        }
        // 取消 stopChat 的兜底定时器
        if (stopFallbackTimer) {
          clearTimeout(stopFallbackTimer)
          stopFallbackTimer = null
        }
        onFinished?.()
        break
      }
      case 'error': {
        flushBuf.flush()
        const lastMsg = targetMsgs[targetMsgs.length - 1]
        if (lastMsg.role !== 'assistant') return
        // 设置消息状态为 failed（派生 isStreaming 自动变 false）
        lastMsg.status = 'failed'
        // 结构化错误信息
        const errorInfo: ChatErrorInfo = classifySseError(data as Record<string, unknown>)
        lastMsg.errorInfo = errorInfo
        // 不再将原始错误追加到 msg.content，避免错误原文出现在历史摘要中
        // 结构化错误提示由 ChatView 中的 errorInfo 渲染组件展示
        // 关闭所有 running segments
        const segments = ensureSegments(targetMsgs, msgIdx)
        finalizeAllRunningSegments(segments)
        // 递归关闭委派子树中仍 running 的嵌套 DelegationNode / tool 条目
        for (const s of segments) {
          finalizeDelegNodes((s.childTimeline as DelegationTimeline | undefined)?.children)
        }
        // 清理流式状态
        markConversationStreaming(convId, false)
        if (conversationId.value === convId) {
          clearReconnectState()
        }
        // 取消 stopChat 的兜底定时器
        if (stopFallbackTimer) {
          clearTimeout(stopFallbackTimer)
          stopFallbackTimer = null
        }
        onFinished?.()
        break
      }
      case 'heartbeat':
      case 'stream_started':
      case 'message_start':
        break
      case 'session': {
        // 后端返回真实 conversationId（新对话首条消息时），持久化到 localStorage
        const serverConvId = data.conversationId as string | undefined
        if (serverConvId && serverConvId !== conversationId.value) {
          conversationId.value = serverConvId
        }
        break
      }

      // ---- Plan-Execute 模式事件 ----
      case 'plan_created': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const planId = data.planId as string | number | undefined
        const steps = data.steps as string[] | undefined
        if (planId !== undefined && steps) {
          const prevMeta = (prev.metadata || {}) as Record<string, unknown>
          const plan: PlanMeta = { planId, steps, currentStep: 0, stepResults: [], planStatus: 'running' }
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan } }
        }
        break
      }
      case 'plan_step_started': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const index = data.index as number | undefined
        if (index === undefined) break
        const prevMeta = (prev.metadata || {}) as Record<string, unknown>
        const plan = prevMeta.plan as PlanMeta | undefined
        if (plan) {
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan: { ...plan, currentStep: index } } }
        }
        break
      }
      case 'plan_step_completed': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const index = data.index as number | undefined
        const result = data.result as string | undefined
        if (index === undefined) break
        const prevMeta = (prev.metadata || {}) as Record<string, unknown>
        const plan = prevMeta.plan as PlanMeta | undefined
        if (plan) {
          const stepResults = [...(plan.stepResults || [])]
          stepResults[index] = { result: result ?? '', status: 'completed' }
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan: { ...plan, stepResults } } }
        }
        break
      }
      case 'plan_step_failed': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const index = data.index as number | undefined
        const error = data.error as string | undefined
        if (index === undefined) break
        const prevMeta = (prev.metadata || {}) as Record<string, unknown>
        const plan = prevMeta.plan as PlanMeta | undefined
        if (plan) {
          const stepResults = [...(plan.stepResults || [])]
          stepResults[index] = { result: error ?? '', status: 'failed' }
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan: { ...plan, stepResults } } }
        }
        break
      }
      case 'plan_completed': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const prevMeta = (prev.metadata || {}) as Record<string, unknown>
        const plan = prevMeta.plan as PlanMeta | undefined
        if (plan) {
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan: { ...plan, planStatus: 'completed' } } }
        }
        break
      }
      case 'plan_failed': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const prevMeta = (prev.metadata || {}) as Record<string, unknown>
        const plan = prevMeta.plan as PlanMeta | undefined
        if (plan) {
          targetMsgs[msgIdx] = { ...prev, metadata: { ...prevMeta, plan: { ...plan, planStatus: 'failed' } } }
        }
        break
      }

      // ===== Agent delegation events =====
      // 委派形成一棵树：depth-1 子 agent 是顶层 tool_call segment（id=subagentId）；
      // depth-2+ 子 agent 是 DelegationNode，通过 parentSubagentId 嵌到祖先 childTimeline.children。
      // 移植自 mateclaw-ui useChat.ts:1061-1210，适配动态 Record segment。
      case 'delegation_start': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const segments = ensureSegments(targetMsgs, msgIdx)
        if (data.parallel && Array.isArray(data.children)) {
          // 仅当存在 depth-1（无 parentSubagentId）委派时关闭根级 running content/thinking
          const topLevel = (data.children as Array<Record<string, unknown>>).some(c => !c.parentSubagentId)
          if (topLevel) {
            finalizeRunningSegments(segments, ['content', 'thinking'])
          }
          for (const child of data.children as Array<Record<string, unknown>>) {
            addDelegation(segments, child)
          }
        } else {
          if (!data.parentSubagentId) {
            finalizeRunningSegments(segments, ['content', 'thinking'])
          }
          addDelegation(segments, data as Record<string, unknown>)
        }
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
            currentPhase: 'executing_tool',
          },
        }
        break
      }
      case 'delegation_async_spawned': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const segments = ensureSegments(targetMsgs, msgIdx)
        // 异步委派：父 agent 继续运行，不关闭父级 running content/thinking segment
        addDelegation(segments, data as Record<string, unknown>, { async: true })
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
          },
        }
        break
      }
      case 'delegation_progress': {
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const segments = ensureSegments(targetMsgs, msgIdx)
        const container = resolveContainer(segments, data.subagentId as string, data.childConversationId as string)
        if (!container) break
        if (!container.tools) container.tools = []

        // data.data 是子 agent 原始事件载荷（对象或 JSON 字符串），兼容旧后端
        const rawPayload = (data as Record<string, unknown>).data
        const childData: Record<string, any> = rawPayload && typeof rawPayload === 'object'
          ? rawPayload as Record<string, unknown>
          : (() => { try { return JSON.parse(String(rawPayload || '{}')) } catch { return {} } })()

        switch (data.originalEvent) {
          case 'tool_call_started': {
            const name = childData?.toolName || ''
            if (name) container.tools.push({ name, status: 'running' })
            break
          }
          case 'tool_call_completed': {
            const name = childData?.toolName || ''
            const ok = childData?.success !== false
            const entry = [...container.tools].reverse().find(t => t.name === name && t.status === 'running')
            if (entry) entry.status = ok ? 'completed' : 'error'
            break
          }
          case 'plan_created': {
            const steps = childData?.steps
            if (Array.isArray(steps)) {
              container.plan = { planId: childData?.planId ?? '', steps, currentStep: 0, stepResults: [] }
            }
            break
          }
          case 'plan_step_started': {
            if (container.plan && typeof childData?.index === 'number') {
              container.plan.currentStep = childData.index
            }
            break
          }
          case 'plan_step_completed': {
            if (container.plan && typeof childData?.index === 'number') {
              const results = [...(container.plan.stepResults || [])]
              results[childData.index] = { result: childData.result ?? '', status: 'completed' }
              container.plan.stepResults = results
            }
            break
          }
          default:
            break
        }
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
          },
        }
        break
      }
      case 'delegation_child_complete': {
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const segments = ensureSegments(targetMsgs, msgIdx)
        markDelegComplete(segments, data.subagentId as string, data.childConversationId as string,
          !!data.success, data.resultPreview as string | undefined, data.durationMs as number | undefined)
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
          },
        }
        break
      }
      case 'delegation_end': {
        flushBuf.flush()
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        const segments = ensureSegments(targetMsgs, msgIdx)
        if (data.parallel) {
          const childResults = data.childResults as Array<Record<string, unknown>> | undefined
          if (childResults && childResults.length > 0) {
            for (const cr of childResults) {
              // delegation_child_complete 通常已关闭各子项；这里仅补齐仍 running 的（如超时未触发 child_complete）
              const seg = findDelegSegment(segments, cr.subagentId as string, cr.childConversationId as string)
              const stillRunning = seg
                ? seg.status === 'running'
                : !!((cr.subagentId as string) && findDelegNode(
                    segments.flatMap(s => (s.childTimeline as DelegationTimeline | undefined)?.children || []), cr.subagentId as string)?.status === 'running')
              if (stillRunning) {
                markDelegComplete(segments, cr.subagentId as string, cr.childConversationId as string,
                  !!cr.success, (cr.error as string) || undefined, cr.durationMs as number | undefined)
              }
            }
          } else {
            // 旧版兜底：标记所有仍 running 的顶层委派 segment
            segments
              .filter(s => s.type === 'tool_call' && s.status === 'running' && (s.toolName as string)?.startsWith('→'))
              .forEach(s => { s.status = data.success ? 'completed' : 'error' })
          }
        } else {
          markDelegComplete(segments, data.subagentId as string, data.childConversationId as string,
            !!data.success, data.resultPreview as string | undefined, data.durationMs as number | undefined)
        }
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
          },
        }
        break
      }
      case 'subagent_stale': {
        const prev = targetMsgs[msgIdx]
        if (!prev || prev.role !== 'assistant') break
        if (!data.subagentId) break
        const segments = ensureSegments(targetMsgs, msgIdx)
        const seg = findDelegSegment(segments, data.subagentId as string)
        if (seg) {
          seg.delegationStale = true
        } else {
          for (const s of segments) {
            const node = findDelegNode((s.childTimeline as DelegationTimeline | undefined)?.children, data.subagentId as string)
            if (node) { node.stale = true; break }
          }
        }
        targetMsgs[msgIdx] = {
          ...prev,
          metadata: {
            ...((prev.metadata || {}) as Record<string, unknown>),
            segments,
          },
        }
        break
      }

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
    const meta = m.metadata as Record<string, unknown> | undefined
    // 从 metadata 中恢复推荐问题到 cards（持久化恢复）
    const cards = recoverCardsFromMetadata(meta, m.role)

    // 从持久化的 contentParts 恢复用户上传的附件（图片/文件），用于历史消息回显。
    const attachments = m.role === 'user' ? reconstructAttachmentsFromVO(m) : []

    // 后端 renderMessageContent 会把附件渲染成 "[图片]/[附件] xxx（路径: yyy）" 文本标记，
    // 前端有独立的附件展示区，故当存在附件时改用纯文本片段作为气泡内容，剥离这些标记避免重复。
    let content = m.content || ''
    if (attachments.length > 0) {
      const parts = (m.contentParts as MessageContentPart[] | null | undefined) || []
      content = parts
        .filter((p) => p && p.type === 'text' && p.text)
        .map((p) => p.text as string)
        .join('\n')
        .trim()
    }

    // 从持久化的 delegationEvents 重建委派树到 segments。
    // 后端在流终态时将 delegation_* 事件流序列化到 metadata.delegationEvents，
    // 前端在加载历史消息时回放这些事件，复用 addDelegation/markDelegComplete 等函数重建委派树。
    if (meta && m.role === 'assistant') {
      const delegEvents = meta.delegationEvents as Array<Record<string, unknown>> | undefined
      if (delegEvents && delegEvents.length > 0) {
        if (!Array.isArray(meta.segments)) meta.segments = []
        const segments = meta.segments as Array<Record<string, unknown>>
        // 闭环所有已有 segments（历史消息不应有 running 状态）
        finalizeAllRunningSegments(segments)
        for (const ev of delegEvents) {
          const evt = ev.event as string
          const evData = ev.data as Record<string, unknown> | undefined
          if (!evt || !evData) continue
          // 复用 delegation 事件处理逻辑，但跳过 SSE 特有操作（flushBuf、targetMsgs 更新等）
          switch (evt) {
            case 'delegation_start': {
              if (evData.parallel && Array.isArray(evData.children)) {
                for (const child of evData.children as Array<Record<string, unknown>>) {
                  addDelegation(segments, child)
                }
              } else {
                addDelegation(segments, evData)
              }
              break
            }
            case 'delegation_async_spawned': {
              addDelegation(segments, evData, { async: true })
              break
            }
            case 'delegation_progress': {
              const container = resolveContainer(segments, evData.subagentId as string, evData.childConversationId as string)
              if (!container) break
              if (!container.tools) container.tools = []
              const rawPayload = evData.data
              const childData: Record<string, any> = rawPayload && typeof rawPayload === 'object'
                ? rawPayload as Record<string, unknown>
                : (() => { try { return JSON.parse(String(rawPayload || '{}')) } catch { return {} } })()
              switch (evData.originalEvent) {
                case 'tool_call_started': {
                  const name = childData?.toolName || ''
                  if (name) container.tools.push({ name, status: 'completed' })
                  break
                }
                case 'tool_call_completed': {
                  const name = childData?.toolName || ''
                  const entry = [...container.tools].reverse().find(t => t.name === name && t.status === 'running')
                  if (entry) entry.status = 'completed'
                  break
                }
                case 'plan_created': {
                  const steps = childData?.steps
                  if (Array.isArray(steps)) {
                    const stepResults = childData?.steps?.map((_: unknown) => ({ result: '', status: 'completed' as const })) || []
                    container.plan = { planId: childData?.planId ?? '', steps, currentStep: steps.length - 1, stepResults, planStatus: 'completed' }
                  }
                  break
                }
              }
              break
            }
            case 'delegation_child_complete':
            case 'delegation_end': {
              // delegation_end 可能含 childResults（并行委派）
              const childResults = evData.childResults as Array<Record<string, unknown>> | undefined
              if (childResults && childResults.length > 0) {
                for (const cr of childResults) {
                  markDelegComplete(segments, cr.subagentId as string, cr.childConversationId as string,
                    !!cr.success, undefined, cr.durationMs as number | undefined)
                }
              } else {
                markDelegComplete(segments, evData.subagentId as string, evData.childConversationId as string,
                  !!evData.success, evData.resultPreview as string | undefined, evData.durationMs as number | undefined)
              }
              break
            }
          }
        }
        // 递归闭环所有委派树节点（历史消息不应有 running 状态）
        for (const s of segments) {
          finalizeDelegNodes((s.childTimeline as DelegationTimeline | undefined)?.children)
        }
      }
    }

    return {
      role: m.role as 'user' | 'assistant',
      content,
      timestamp: new Date(m.createTime).getTime(),
      metadata: meta,
      status: 'completed',
      ...(cards.length > 0 ? { cards } : {}),
      ...(attachments.length > 0 ? { attachments } : {}),
    }
  }

  /**
   * 从消息 VO 的 contentParts 重建前端附件列表。
   * <p>
   * 用户上传时前端只持久化了 storedName/path/contentType 等（未存可访问 URL），
   * 这里按「会话 ID + storedName」重建后端文件访问地址（与后端 ChatUploadRuntime 一致），
   * 使刷新页面 / 切换历史会话后仍能展示图片缩略图与附件卡片。
   *
   * @param m 后端返回的消息 VO
   * @return 重建的附件列表（无附件时为空数组）
   */
  function reconstructAttachmentsFromVO(m: MessageVO): ChatAttachment[] {
    const parts = m.contentParts as MessageContentPart[] | null | undefined
    if (!parts || parts.length === 0) return []
    const result: ChatAttachment[] = []
    for (const p of parts) {
      if (!p || (p.type !== 'image' && p.type !== 'file' && p.type !== 'video' && p.type !== 'audio')) {
        continue
      }
      const storedName = p.storedName || ''
      // 优先用可公开访问的 fileUrl（如 IM 渠道），否则按会话目录重建本地文件访问地址
      const url = p.fileUrl || (storedName ? `${CHAT_FILE_URL_PREFIX}${m.conversationId}/${storedName}` : '')
      if (!url) continue
      result.push({
        fileName: p.fileName || storedName || '附件',
        storedName,
        url,
        path: p.path || '',
        size: p.fileSize || 0,
        contentType: p.contentType || '',
      })
    }
    return result
  }

  /**
   * 从后端持久化的 metadata 中恢复富内容卡片。
   * <p>
   * 当前仅恢复推荐问题卡片（recommendedQuestions 字段），
   * 未来可扩展恢复其他卡片类型。
   *
   * @param metadata 后端返回的消息 metadata
   * @param role     消息角色
   * @return 恢复的卡片列表
   */
  function recoverCardsFromMetadata(metadata: Record<string, unknown> | undefined, role: string): import('@/types').ChatCard[] {
    if (!metadata || role !== 'assistant') {
      return []
    }
    const cards: import('@/types').ChatCard[] = []
    // 恢复推荐问题
    const recommendedQuestions = metadata.recommendedQuestions
    if (Array.isArray(recommendedQuestions) && recommendedQuestions.length > 0) {
      cards.push({
        type: 'recommended_questions',
        data: { questions: recommendedQuestions as string[] },
      })
    }
    return cards
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

    // 删除从对应用户消息起的所有后续消息（含用户消息与 AI 消息自身）：
    // sendMessage 会重新 push 用户消息，若只从 AI 消息起删，本地会残留旧用户气泡，
    // 形成“连续两条相同用户消息”
    messages.value.splice(userMsgIndex)

    // 重新发送用户消息（模型信息由 sendMessage 内部从 store 同步快照读取）
    const agentId = currentAgentId.value
    if (!agentId) return
    await sendMessage(agentId, userContent)
  }

  /**
   * 拉取当前会话的上下文使用情况
   */
  async function fetchContextUsage(): Promise<void> {
    const convId = conversationId.value
    if (!convId) {
      contextUsage.value = null
      return
    }
    try {
      const res = await conversationApi.getContextUsage(convId)
      contextUsage.value = res as unknown as ContextUsage
    } catch (e) {
      // 静默失败，不阻塞聊天主流程
      logDebug('fetchContextUsage failed', e)
    }
  }

  /**
   * 更新当前会话的上下文使用情况（来自 SSE context_usage 事件）
   */
  function updateContextUsage(usage: ContextUsage): void {
    if (!usage || !usage.conversationId) return
    if (usage.conversationId !== conversationId.value) return
    contextUsage.value = usage
  }

  /**
   * 切换上下文使用面板展开状态
   */
  function toggleContextUsagePanel(): void {
    contextUsagePanelOpen.value = !contextUsagePanelOpen.value
    if (contextUsagePanelOpen.value) {
      fetchContextUsage()
    }
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
    contextUsage,
    contextUsagePanelOpen,
    dialogueRoundCount,
    newConversationHintDismissed,
    lastEventId,
    setAgent,
    clearMessages,
    resetForWorkspaceSwitch,
    prepareNewConversation,
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
    fetchContextUsage,
    updateContextUsage,
    toggleContextUsagePanel,
    isConversationStreaming: (convId: string): boolean => streamingConversations.value.has(convId),
  }
})

// 开发环境下让本 store 的改动可热更新，避免 store 逻辑修改后仍运行旧实例（需手动整页刷新才生效）
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useChatStore, import.meta.hot))
}
