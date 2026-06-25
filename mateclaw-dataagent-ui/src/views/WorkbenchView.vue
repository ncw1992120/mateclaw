<template>
  <div class="workbench">
    <!-- Main: Left Menu + History Sidebar + Chat -->
    <div class="main">
      <!-- 左侧可展开菜单 -->
      <div class="left-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <span v-if="!sidebarCollapsed" class="sidebar-title">{{ t('nav.smartAsk') }}</span>
          <button class="collapse-btn" :title="sidebarCollapsed ? t('conversation.expand') : t('conversation.collapse')" @click="sidebarCollapsed = !sidebarCollapsed">
            <span class="collapse-btn-svg" aria-hidden="true">
              <svg v-if="sidebarCollapsed" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </span>
          </button>
        </div>
        <nav class="sidebar-menu">
          <a
            v-for="item in sidebarItems"
            :key="item.key"
            class="sidebar-item"
            :class="{ active: activeSidebarItem === item.key }"
            @click="activeSidebarItem = item.key"
            :title="sidebarCollapsed ? t(item.label) : ''"
          >
            <span class="sidebar-icon" v-html="item.icon"></span>
            <span v-if="!sidebarCollapsed" class="sidebar-label">{{ t(item.label) }}</span>
          </a>
        </nav>
      </div>

      <!-- 历史对话侧栏（问数、洞察、报告页面展示） -->
      <div v-if="showSelectorPanel" class="history-sidebar" :class="{ collapsed: historyCollapsed }">
        <div class="history-header">
          <div class="header-spacer"></div>
          <button v-if="!historyCollapsed" class="new-chat-btn" :title="t('conversation.newChat')" @click="handleNewChat">
            <span class="new-chat-icon" aria-hidden="true">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </span>
            <span class="new-chat-label">{{ t('conversation.newChat') }}</span>
          </button>
          <!-- 操作按钮行 -->
          <div class="history-actions">
            <button class="history-collapse-btn" :title="historyCollapsed ? t('conversation.expand') : t('conversation.collapse')" @click="historyCollapsed = !historyCollapsed">
              <span v-if="historyCollapsed" class="collapse-svg" aria-hidden="true">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </span>
              <span v-else class="collapse-svg" aria-hidden="true">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="15 18 9 12 15 6"/>
                </svg>
              </span>
            </button>
          </div>
        </div>
        <template v-if="!historyCollapsed">
          <!-- 历史对话列表（仅问数页面展示） -->
          <template v-if="activeSidebarItem === 'qa'">
            <div class="history-list">
              <template v-for="group in groupedConversations" :key="group.key">
                <div class="history-group-title">{{ group.label }}</div>
                <div
                  v-for="conv in group.items"
                  :key="conv.conversationId"
                  class="history-item"
                  :class="{ active: chatStore.conversationId === conv.conversationId, 'menu-open': openMenuConvId === conv.conversationId, streaming: chatStore.isConversationStreaming(conv.conversationId) }"
                  @click="handleSwitchConversation(conv.conversationId)"
                >
                  <template v-if="editingConvId === conv.conversationId">
                    <input
                      v-model="editingTitle"
                      class="history-item-edit-input"
                      :maxlength="100"
                      autofocus
                      @click.stop
                      @keydown.enter="handleConfirmRename(conv.conversationId)"
                      @keydown.esc="handleCancelRename"
                      @blur="handleConfirmRename(conv.conversationId)"
                    />
                  </template>
                  <template v-else>
                    <span
                      v-if="chatStore.isConversationStreaming(conv.conversationId)"
                      class="history-item-spinner"
                      :title="t('conversation.streaming')"
                      aria-hidden="true"
                    ></span>
                    <div class="history-item-content">
                      <span class="history-item-title" :title="conv.title || t('conversation.untitled')">
                        <span v-if="isConversationPinned(conv)" class="history-pin-mark" aria-hidden="true">↗</span>
                        {{ conv.title || t('conversation.untitled') }}
                      </span>
                      <span class="history-item-meta">
                        <span class="history-item-time">{{ formatRelativeTime(conv.lastActiveTime) }}</span>
                        <span class="meta-sep">·</span>
                        <span class="history-item-count">{{ t('conversation.messageCount', { n: conv.messageCount }) }}</span>
                      </span>
                    </div>
                    <div class="history-item-actions" :class="{ visible: openMenuConvId === conv.conversationId }">
                      <button class="history-item-action" :title="t('conversation.more')" @click.stop="handleToggleMenu(conv.conversationId)">
                        <span class="dot-icon" aria-hidden="true">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                            <circle cx="5" cy="12" r="2"/>
                            <circle cx="12" cy="12" r="2"/>
                            <circle cx="19" cy="12" r="2"/>
                          </svg>
                        </span>
                      </button>
                    </div>
                  </template>
                  <!-- 浮层操作菜单 -->
                  <div v-if="openMenuConvId === conv.conversationId" class="history-item-menu" @click.stop>
                    <button class="history-menu-item" @click="handleTogglePin(conv.conversationId)">
                      <span class="menu-icon" aria-hidden="true">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 2l3 7 7 .6-5.3 4.6 1.6 6.8L12 17.4 5.7 21l1.6-6.8L2 9.6 9 9z"/>
                        </svg>
                      </span>
                      <span>{{ isConversationPinned(conv) ? '取消置顶' : '置顶' }}</span>
                    </button>
                    <button class="history-menu-item" @click="handleStartRename(conv)">
                      <span class="menu-icon" aria-hidden="true">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 20h9"/>
                          <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"/>
                        </svg>
                      </span>
                      <span>{{ t('conversation.rename') }}</span>
                    </button>
                    <button class="history-menu-item danger" @click="handleConfirmDelete(conv.conversationId)">
                      <span class="menu-icon" aria-hidden="true">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <polyline points="3 6 5 6 21 6"/>
                          <path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/>
                          <path d="M10 11v6"/>
                          <path d="M14 11v6"/>
                          <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/>
                        </svg>
                      </span>
                      <span>{{ t('conversation.delete') }}</span>
                    </button>
                  </div>
                </div>
              </template>
              <div v-if="chatStore.conversations.length === 0" class="history-empty">
                {{ t('conversation.history') }}
              </div>
            </div>
          </template>
        </template>
      </div>

      <!-- 中间聊天区域 -->
      <div class="col-mid">
        <!-- 智能体/模型选择器（顶部并排） -->
        <div v-if="showSelectorPanel" class="mid-topbar">
          <div class="topbar-selector">
            <span class="selector-label">{{ t('agent.title') }}</span>
            <el-select
              v-model="chatStore.currentAgentId"
              size="small"
              :placeholder="t('agentConfig.selectAgent')"
              :loading="agentStore.loading"
              class="agent-select-header"
              @change="handleAgentChange"
            >
              <el-option
                v-for="agent in enabledAgents"
                :key="agent.id"
                :label="agent.name"
                :value="agent.id"
              >
                <span class="agent-option">
                  <span class="agent-option-icon">{{ agent.icon || '🤖' }}</span>
                  <span class="agent-option-name">{{ agent.name }}</span>
                  <span class="agent-option-type">{{ agent.agentType }}</span>
                </span>
              </el-option>
            </el-select>
          </div>
          <div class="topbar-selector">
            <span class="selector-label">{{ t('modelConfig.model') }}</span>
            <el-select
              v-model="selectedModelId"
              size="small"
              :placeholder="availableModels.length ? t('modelConfig.selectModel') : t('modelConfig.configureFirst')"
              :loading="modelStore.loading"
              :no-data-text="t('modelConfig.noAvailableModels')"
              class="model-select"
              @change="handleModelChange"
            >
              <el-option
                v-for="model in availableModels"
                :key="model.id"
                :label="model.name"
                :value="model.id"
              >
                <span class="model-option">
                  <span class="model-option-name">{{ model.name }}</span>
                  <span class="model-option-provider">{{ model.provider }}</span>
                  <el-tag v-if="model.isDefault" type="warning" size="small">{{ t('modelConfig.default') }}</el-tag>
                </span>
              </el-option>
            </el-select>
          </div>
        </div>
        <ChatView v-if="activeSidebarItem === 'qa'" class="chat-container" />
        <ConfigCenter v-else-if="activeSidebarItem === 'skill'" class="chat-container" />
        <HelpCenterView v-else-if="activeSidebarItem === 'help'" class="chat-container" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import { usePersistedRef, usePersistedState } from '@/composables/usePersistedRef'
import ChatView from '@/views/ChatView.vue'
import ConfigCenter from '@/views/skill/ConfigCenter.vue'
import HelpCenterView from '@/views/help/HelpCenterView.vue'
import type { Conversation } from '@/types'

/** 左侧菜单可选取值 */
const SIDEBAR_ITEM_KEYS = ['qa', 'interpret', 'report', 'skill', 'help'] as const

const { t } = useI18n()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const modelStore = useModelStore()

/** 当前选中的模型 ID（刷新后保留） */
const selectedModelId = usePersistedState<number | undefined>('mc-workbench-selected-model-id', undefined)

/** 左侧菜单是否折叠 */
const sidebarCollapsed = ref(false)

/** 历史对话侧栏是否折叠（独立于左侧菜单折叠状态） */
const historyCollapsed = ref(false)

/** 当前打开操作菜单的会话 id（仅一个） */
const openMenuConvId = ref<string | null>(null)

/** 当前正在重命名的会话 id（进入内联编辑态） */
const editingConvId = ref<string | null>(null)

/** 重命名输入框的临时值 */
const editingTitle = ref('')

/** 当前选中的侧边栏菜单项（刷新后保留） */
const activeSidebarItem = usePersistedRef<(typeof SIDEBAR_ITEM_KEYS)[number]>(
  'mc-workbench-active-sidebar-item',
  'qa',
  (value) => (SIDEBAR_ITEM_KEYS as readonly string[]).includes(value),
)

/** 是否显示选择器面板（问数、洞察、报告页面展示） */
const showSelectorPanel = computed(() => ['qa', 'interpret', 'report'].includes(activeSidebarItem.value))

/** 历史会话分组 */
interface ConversationGroup {
  key: string
  label: string
  items: Conversation[]
}

const DAY_MILLISECONDS = 24 * 60 * 60 * 1000

const HISTORY_GROUP_PINNED = '置顶'
const HISTORY_GROUP_TODAY = '今天'
const HISTORY_GROUP_YESTERDAY = '昨天'
const HISTORY_GROUP_SEVEN_DAYS = '7 天内'
const HISTORY_GROUP_THIRTY_DAYS = '30 天内'

/** 侧边栏菜单项配置（图标对齐 mateclaw-ui 的 SVG 风格） */
const sidebarItems = [
  {
    key: 'qa',
    label: 'nav.subQa',
    // 问数：对话气泡 + 问号，凸显"提问"语义
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
  },
  {
    key: 'interpret',
    label: 'nav.subInterpret',
    // 洞察：灯泡，代表"灵感/顿悟/洞见"
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14a6 6 0 0 0 1.41-8.94 6 6 0 0 0-9.5 7.94"/><path d="M9.5 14h5"/></svg>`,
  },
  {
    key: 'report',
    label: 'nav.subReport',
    // 报告：文档 + 柱状图，凸显"分析报告"语义
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="15" x2="8" y2="17"/><line x1="12" y1="13" x2="12" y2="17"/><line x1="16" y1="11" x2="16" y2="17"/></svg>`,
  },
  {
    key: 'skill',
    label: 'nav.subSkill',
    // 配置：齿轮，标准设置图标
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.09a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06-.06a1.65 1.65 0 0 0-.33 1.82V9c0 .66.26 1.3.73 1.77.47.47 1.11.73 1.77.73H21a2 2 0 1 1 0 4h-.09c-.66 0-1.3.26-1.77.73-.47.47-.73 1.11-.73 1.77z"/></svg>`,
  },
  {
    key: 'help',
    label: 'nav.subHelp',
    // 帮助：书本 + 问号，凸显"查阅文档/手册"语义
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/><path d="M9.5 9a2.5 2.5 0 0 1 5 0c0 1.5-2.5 2-2.5 3.5"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
  },
]

/** 已启用的 Agent 列表 */
const enabledAgents = computed(() => agentStore.agents.filter(a => a.enabled))

/**
 * 可用模型列表：仅来自已启用且已配置的 Provider，且仅展示对话模型
 * 未配置 Provider 的模型不展示在顶部选择器中
 */
const availableModels = computed(() => {
  const configuredProviderIds = new Set(
    modelStore.providers
      .filter(p => p.enabled && (p.apiKey || p.baseUrl))
      .map(p => p.providerId)
  )
  return modelStore.enabledModels.filter(m =>
    configuredProviderIds.has(m.provider) && (!m.modelType || m.modelType === 'chat')
  )
})

/** 按最后活跃时间分组后的历史会话列表 */
const groupedConversations = computed<ConversationGroup[]>(() => {
  const groupMap = new Map<string, ConversationGroup>()
  const now = new Date()
  const todayStart = getDayStart(now).getTime()
  const yesterdayStart = todayStart - DAY_MILLISECONDS
  const sevenDaysStart = todayStart - 6 * DAY_MILLISECONDS
  const thirtyDaysStart = todayStart - 29 * DAY_MILLISECONDS

  const sortedConversations = [...chatStore.conversations].sort((a, b) => getConversationTime(b) - getConversationTime(a))
  const pinnedItems = sortedConversations.filter(conv => isConversationPinned(conv))

  sortedConversations
    .filter(conv => !isConversationPinned(conv))
    .forEach(conv => {
      const group = getConversationGroup(conv, todayStart, yesterdayStart, sevenDaysStart, thirtyDaysStart)
      if (!groupMap.has(group.key)) {
        groupMap.set(group.key, { ...group, items: [] })
      }
      groupMap.get(group.key)?.items.push(conv)
    })

  const groups = Array.from(groupMap.values())
  if (pinnedItems.length > 0) {
    groups.unshift({ key: 'pinned', label: HISTORY_GROUP_PINNED, items: pinnedItems })
  }
  return groups
})

/** 模型切换（仅更新前端选择状态，不调用后端 API） */
function handleModelChange(modelId: number): void {
  selectedModelId.value = modelId
  const model = availableModels.value.find(m => m.id === modelId)
  chatStore.selectedModelName = model?.modelName ?? ''
  chatStore.selectedModelProvider = model?.provider ?? ''
}

/** Agent 切换 */
async function handleAgentChange(agentId: number | string): Promise<void> {
  await agentStore.selectAgent(agentId)
}

/**
 * 新对话：清空当前消息并生成新的会话 ID
 * 若当前正在流式输出，则先停止
 */
async function handleNewChat(): Promise<void> {
  if (chatStore.isStreaming) {
    await chatStore.stopChat()
  }
  chatStore.clearMessages()
  openMenuConvId.value = null
  editingConvId.value = null
}

/** 切换到历史会话 */
async function handleSwitchConversation(convId: string): Promise<void> {
  // 处于内联编辑态时，item 的 click 不应该切换会话
  if (editingConvId.value) return
  openMenuConvId.value = null
  if (chatStore.conversationId === convId && chatStore.messages.length > 0) return
  if (chatStore.isStreaming) {
    // 仅断开前端 SSE 连接，不停止后端流，保留续连能力
    chatStore.disconnectStream()
  }
  await chatStore.switchConversation(convId)
}

/** 切换操作菜单的显示 */
function handleToggleMenu(convId: string): void {
  // 如果该条正在重命名，先退出编辑态
  if (editingConvId.value && editingConvId.value !== convId) {
    editingConvId.value = null
    editingTitle.value = ''
  }
  openMenuConvId.value = openMenuConvId.value === convId ? null : convId
}

/** 切换会话置顶状态 */
async function handleTogglePin(convId: string): Promise<void> {
  const conv = chatStore.conversations.find(c => c.conversationId === convId)
  if (!conv) return
  const newPinned = !isConversationPinned(conv)
  await chatStore.setConversationPinned(convId, newPinned)
  openMenuConvId.value = null
}

/** 判断会话是否已置顶 */
function isConversationPinned(conv: Conversation): boolean {
  return conv.pinned === 1
}

/** 点击页面其他位置关闭菜单 */
function handleDocumentClick(event: MouseEvent): void {
  if (!openMenuConvId.value) return
  const target = event.target as HTMLElement | null
  if (target && target.closest('.history-item')) return
  openMenuConvId.value = null
}

/** 进入重命名内联编辑态 */
function handleStartRename(conv: Conversation): void {
  editingConvId.value = conv.conversationId
  editingTitle.value = conv.title || ''
  openMenuConvId.value = null
}

/** 确认重命名 */
async function handleConfirmRename(convId: string): Promise<void> {
  // 避免在按 Esc 取消后再次触发保存
  if (editingConvId.value !== convId) return
  const newTitle = editingTitle.value.trim()
  const original = chatStore.conversations.find(c => c.conversationId === convId)?.title || ''
  editingConvId.value = null
  editingTitle.value = ''
  if (!newTitle || newTitle === original) return
  try {
    await chatStore.renameConversation(convId, newTitle)
  } catch {
    // store 已做错误提示，无需额外处理
  }
}

/** 取消重命名 */
function handleCancelRename(): void {
  editingConvId.value = null
  editingTitle.value = ''
}

/** 确认删除（带二次确认） */
async function handleConfirmDelete(convId: string): Promise<void> {
  openMenuConvId.value = null
  try {
    await ElMessageBox.confirm(t('conversation.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await chatStore.deleteConversation(convId)
    ElMessage.success(t('conversation.deleteSuccess'))
  } catch {
    // 错误已在 axios 拦截器提示
  }
}

/** 获取会话排序时间 */
function getConversationTime(conv: Conversation): number {
  const time = new Date(conv.lastActiveTime || conv.updateTime || conv.createTime).getTime()
  if (Number.isNaN(time)) {
    return 0
  }
  return time
}

/** 获取日期所在自然日的开始时间 */
function getDayStart(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

/** 获取会话所属历史分组 */
function getConversationGroup(
  conv: Conversation,
  todayStart: number,
  yesterdayStart: number,
  sevenDaysStart: number,
  thirtyDaysStart: number,
): Omit<ConversationGroup, 'items'> {
  const date = new Date(conv.lastActiveTime || conv.updateTime || conv.createTime)
  const time = date.getTime()
  if (Number.isNaN(time) || time >= todayStart) {
    return { key: 'today', label: HISTORY_GROUP_TODAY }
  }
  if (time >= yesterdayStart) {
    return { key: 'yesterday', label: HISTORY_GROUP_YESTERDAY }
  }
  if (time >= sevenDaysStart) {
    return { key: 'seven-days', label: HISTORY_GROUP_SEVEN_DAYS }
  }
  if (time >= thirtyDaysStart) {
    return { key: 'thirty-days', label: HISTORY_GROUP_THIRTY_DAYS }
  }
  const year = date.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  return { key: `${year}-${month}`, label: `${year}年${month}月` }
}

/**
 * 把后端返回的时间戳格式化为相对时间（用于历史侧栏紧凑展示）。
 * <p>
 * 不到 1 分钟：刚刚；1-59 分钟：x 分钟前；1-23 小时：x 小时前；
 * 当天但更早：今天 HH:mm；昨天：昨天 HH:mm；7 天内：x 天前；
 * 更早：直接 yyyy-MM-dd。
 */
function formatRelativeTime(value: string | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  const time = date.getTime()
  if (Number.isNaN(time)) return ''
  const now = Date.now()
  const diff = Math.max(0, now - time)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return t('time.justNow')
  if (diff < hour) return t('time.minutesAgo', { n: Math.floor(diff / minute) })
  if (diff < day) return t('time.hoursAgo', { n: Math.floor(diff / hour) })

  const pad = (n: number): string => n.toString().padStart(2, '0')
  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

  const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const yesterday = new Date(now - day)
  if (isSameDay(date, new Date(now))) {
    return t('time.todayAt', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` })
  }
  if (isSameDay(date, yesterday)) {
    return t('time.yesterdayAt', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` })
  }
  if (diff < 7 * day) return t('time.daysAgo', { n: Math.floor(diff / day) })
  return ymd
}

/**
 * 初始化默认选择：
 * 1. 如果当前未选中 Agent，自动选中第一个启用的 Agent
 * 2. 如果当前未选中模型，自动选中标记为"默认"的模型
 */
function initDefaultSelection(): void {
  // Agent：自动选中第一个
  if (!chatStore.currentAgentId && enabledAgents.value.length > 0) {
    const firstAgent = enabledAgents.value[0]
    chatStore.setAgent(firstAgent.id)
    // 静默选中（不触发 API 调用）
    if (firstAgent.id) {
      chatStore.currentAgentId = firstAgent.id
    }
  }
  // Model 默认选择由 currentModelId 计算属性自动派生，无需手动初始化
}

/** 监听 agents 加载完成，自动初始化选择 */
watch(() => enabledAgents.value.length, (len) => {
  if (len > 0) {
    initDefaultSelection()
  }
}, { immediate: true })

/**
 * 模型列表加载完成后，自动选中默认模型（优先 isDefault，否则第一个）
 * 保证下拉框始终展示一个模型值
 */
watch(availableModels, (models) => {
  if (models.length === 0) return
  // 如果 chatStore 已有模型信息（切换会话恢复的），根据 provider+modelName 反查 modelId
  if (chatStore.selectedModelProvider && chatStore.selectedModelName) {
    const matched = models.find(m =>
      m.provider === chatStore.selectedModelProvider && m.modelName === chatStore.selectedModelName
    )
    if (matched) {
      selectedModelId.value = matched.id
      return
    }
  }
  // 如果已有选中 ID（localStorage 恢复），同步恢复 modelName 和 provider
  if (selectedModelId.value) {
    const model = models.find(m => m.id === selectedModelId.value)
    if (model) {
      chatStore.selectedModelName = model.modelName
      chatStore.selectedModelProvider = model.provider
      return
    }
  }
  // 否则选中默认/第一个模型
  const def = models.find(m => m.isDefault) ?? models[0]
  selectedModelId.value = def.id
  chatStore.selectedModelName = def.modelName
  chatStore.selectedModelProvider = def.provider
}, { immediate: true })

/**
 * 监听 chatStore 中模型状态变化（切换会话/新建对话时），反向同步 selectedModelId
 * 确保下拉框显示与 chatStore 一致的模型
 */
watch(
  () => [chatStore.selectedModelProvider, chatStore.selectedModelName] as const,
  ([provider, modelName]) => {
    if (!provider || !modelName) {
      // 模型被清空（新建对话），重置 selectedModelId 让上面的 watch 重新选择默认模型
      selectedModelId.value = undefined
      return
    }
    // 根据 provider+modelName 反查 modelId
    const matched = availableModels.value.find(m =>
      m.provider === provider && m.modelName === modelName
    )
    if (matched && matched.id !== selectedModelId.value) {
      selectedModelId.value = matched.id
    }
  }
)

/**
 * 挂载时确保模型数据已就绪
 * 注意：fetchActiveModel 依赖 enabledModels，需要按顺序调用
 */
onMounted(async () => {
  if (modelStore.enabledModels.length === 0) {
    await modelStore.fetchEnabledModels()
  }
  if (modelStore.providers.length === 0) {
    modelStore.fetchProviders()
  }
  // 点击非历史项区域时，关闭弹出的操作菜单
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #fff;
  flex: 1;
}

/** 选择器面板容器（在历史侧栏 header 中） */
.selector-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

/** 中间内容区顶部工具栏（智能体/模型选择器） */
.mid-topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 60px;
  padding: 0 20px;
  background: var(--white);
  border-bottom: 1px solid var(--light-grey);
  flex-shrink: 0;
}

.topbar-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-selector-header {
  display: flex;
  align-items: center;
  gap: 4px;
}

.agent-select-header {
  width: 200px;
}

.agent-select-header :deep(.el-input__wrapper) {
  border-radius: 16px;
}

.model-selector {
  display: flex;
  align-items: center;
  gap: 4px;
}

.selector-label {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
}

.model-select {
  width: 220px;
  min-width: 220px;
}

.model-select :deep(.el-input__wrapper) {
  border-radius: 16px;
}

.model-select :deep(.el-select__selected-item) {
  max-width: 100%;
}

.model-select :deep(.el-input__inner) {
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

.model-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.model-option-name {
  font-size: 12px;
  font-weight: 600;
}

.model-option-provider {
  font-size: 10px;
  color: var(--muted);
}

.main {
  display: flex;
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}

/* 左侧侧边栏 */
.left-sidebar {
  width: 160px;
  background: var(--white);
  border-right: 1px solid var(--light-grey);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s ease;
  overflow: hidden;
}

.left-sidebar.collapsed {
  width: 48px;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid var(--light-grey);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--body-text);
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  border-radius: 8px;
  flex-shrink: 0;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.collapse-btn:hover {
  background: var(--very-light-orange);
  color: var(--main-orange);
  border-color: var(--main-orange);
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(240, 90, 35, 0.12);
}

.collapse-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.collapse-btn-svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.collapse-btn-svg :deep(svg) {
  display: block;
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 4px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  font-size: 13px;
  color: var(--body-text);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  text-decoration: none;
  white-space: nowrap;
  font-weight: 500;
  position: relative;
}

.sidebar-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  transition: color 0.2s;
}

.sidebar-icon :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.sidebar-label {
  flex: 1;
}

.sidebar-item:hover {
  background: rgba(240, 90, 35, 0.06);
  color: var(--main-orange);
}

.sidebar-item:hover .sidebar-icon {
  color: var(--main-orange);
}

.sidebar-item.active {
  color: var(--main-orange);
  font-weight: 600;
  background: rgba(240, 90, 35, 0.1);
}

.sidebar-item.active .sidebar-icon {
  color: var(--main-orange);
}

.col-mid {
  flex: 1;
  background: var(--near-white);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  position: relative;
}

/* 历史对话侧栏 */
.history-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--white);
  border-right: 1px solid var(--light-grey);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.25s ease;
}

.history-sidebar.collapsed {
  width: 48px;
}

.history-header {
  height: 60px;
  padding: 12px;
  border-bottom: 1px solid var(--light-grey);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
}

.history-sidebar.collapsed .history-header {
  padding: 12px 8px;
  justify-content: center;
}

/** 操作按钮行（新对话 + 折叠按钮） */
.header-spacer {
  flex: 1;
}

.history-actions {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.history-collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  color: var(--muted);
  cursor: pointer;
  flex-shrink: 0;
  border-radius: 8px;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.history-collapse-btn:hover {
  color: var(--main-orange);
  border-color: var(--main-orange);
  background: var(--very-light-orange);
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(240, 90, 35, 0.12);
}

.history-collapse-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.collapse-svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.collapse-svg :deep(svg) {
  display: block;
}

.new-chat-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 28px;
  padding: 0 48px;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  background: var(--white);
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.new-chat-btn:hover {
  border-color: var(--main-orange);
  background: var(--very-light-orange);
  color: var(--main-orange);
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(240, 90, 35, 0.12);
}

.new-chat-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.new-chat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.new-chat-icon :deep(svg) {
  display: block;
}

.new-chat-label {
  white-space: nowrap;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-group-title {
  align-self: flex-start;
  margin: 14px 0 10px 2px;
  padding: 2px 7px;
  border-radius: 4px;
  background: #f5f6f8;
  color: #8a8f99;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
}

.history-group-title:first-child {
  margin-top: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  min-height: 46px;
}

.history-item:hover {
  background: var(--very-light-orange);
}

.history-item.pinned:not(.active) {
  background: rgba(240, 90, 35, 0.035);
}

.history-item.active {
  background: var(--very-light-orange);
  color: var(--main-orange);
  font-weight: 600;
}

.history-item-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.history-item-title {
  font-size: 13px;
  color: var(--dark-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.history-pin-mark {
  color: var(--main-orange);
  font-size: 11px;
  margin-right: 3px;
}

.history-item.active .history-item-title {
  color: var(--main-orange);
}

.history-item.active .history-item-meta {
  color: var(--main-orange);
  opacity: 0.8;
}

.history-item-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-item-time,
.history-item-count {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-sep {
  opacity: 0.6;
}

/* 旋转图标：会话正在生成时显示在历史项最左侧 */
.history-item-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--main-orange);
  border-top-color: transparent;
  border-radius: 50%;
  color: var(--main-orange);
  animation: history-spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes history-spin {
  to { transform: rotate(360deg); }
}

/* 历史项右侧"三点"操作按钮 */
.history-item-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-item:hover .history-item-actions,
.history-item.menu-open .history-item-actions,
.history-item-actions.visible {
  opacity: 1;
}

.history-item-action {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  padding: 0;
  transition: all 0.15s;
}

.history-item-action:hover {
  background: var(--lighter-grey);
  color: var(--main-orange);
}

.dot-icon {
  display: inline-flex;
  align-items: center;
}

/* 浮层操作菜单（重命名/删除） */
.history-item-menu {
  position: absolute;
  top: 100%;
  right: 8px;
  margin-top: 4px;
  background: var(--white);
  border: 1px solid var(--light-grey);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 120px;
  padding: 4px;
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.history-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--dark-text);
  cursor: pointer;
  border-radius: 4px;
  text-align: left;
  width: 100%;
  font-family: inherit;
  transition: background 0.15s;
}

.history-menu-item:hover {
  background: var(--very-light-orange);
}

.history-menu-item.danger {
  color: #e53e3e;
}

.history-menu-item.danger:hover {
  background: #fef0ef;
}

.menu-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

/* 内联重命名输入框 */
.history-item-edit-input {
  flex: 1;
  height: 26px;
  padding: 0 6px;
  font-size: 13px;
  border: 1px solid var(--main-orange);
  border-radius: 4px;
  background: var(--white);
  color: var(--dark-text);
  outline: none;
  font-family: inherit;
  min-width: 0;
}

.history-item-edit-input:focus {
  box-shadow: 0 0 0 2px var(--very-light-orange);
}

/* 让 .history-item 作为 menu 浮层的定位锚点 */
.history-item {
  position: relative;
}

.history-empty {
  padding: 24px 12px;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}

.agent-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-option-icon {
  font-size: 14px;
}

.agent-option-name {
  font-size: 12px;
  font-weight: 600;
}

.agent-option-type {
  font-size: 10px;
  color: var(--muted);
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
}
</style>