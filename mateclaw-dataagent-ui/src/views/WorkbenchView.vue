<template>
  <div class="workbench">
    <!-- Main: History Sidebar + Chat -->
    <div class="main">
      <!-- 历史对话侧栏 -->
      <div v-if="showSelectorPanel" class="history-sidebar" :class="{ collapsed: historyCollapsed }">
        <div class="history-header">
          <span v-if="!historyCollapsed" class="history-title">{{ t('conversation.history') }}</span>
          <div class="header-spacer"></div>
          <!-- 操作按钮行 -->
          <div class="history-actions">
            <button v-if="!historyCollapsed" class="history-collapse-btn" :title="t('conversation.searchPlaceholder')" @click="searchOpen = !searchOpen">
              <span class="collapse-svg" aria-hidden="true">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
              </span>
            </button>
            <button class="history-collapse-btn" :title="historyCollapsed ? t('conversation.expand') : t('conversation.collapse')" @click="historyCollapsed = !historyCollapsed">
              <span class="collapse-svg" aria-hidden="true">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2"/>
                  <line x1="9" y1="3" x2="9" y2="21"/>
                  <polyline points="15 9 12 12 15 15"/>
                </svg>
              </span>
            </button>
          </div>
        </div>
        <!-- 新对话按钮独占一行 -->
        <div v-if="!historyCollapsed" class="history-new-chat-row">
          <button class="new-chat-btn" :title="t('conversation.newChat')" @click="handleNewChat">
            <span class="new-chat-icon" aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </span>
            <span class="new-chat-label">{{ t('conversation.newChat') }}</span>
          </button>
        </div>
        <template v-if="!historyCollapsed">
          <!-- 历史对话列表 -->
          <div class="history-list">
              <el-collapse v-model="expandedGroupKeys" class="history-collapse">
                <el-collapse-item
                  v-for="group in groupedConversations"
                  :key="group.key"
                  :name="group.key"
                  :title="group.label"
                >
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
                      class="history-item-breathing"
                      :title="t('conversation.streaming')"
                      aria-hidden="true"
                    ></span>
                    <span
                      v-else-if="chatStore.backgroundCompletedConversations.has(conv.conversationId)"
                      class="history-item-unread"
                      :title="t('conversation.unread')"
                      aria-hidden="true"
                    ></span>
                    <div class="history-item-content">
                      <span class="history-item-title" :title="conv.title || t('conversation.untitled')">
                        {{ conv.title || t('conversation.untitled') }}
                      </span>
                      <span class="history-item-meta">
                        <span class="history-item-time">{{ formatRelativeTime(conv.lastActiveTime) }}</span>
                      </span>
                    </div>
                    <div class="history-item-actions" :class="{ visible: openMenuConvId === conv.conversationId }">
                      <button class="history-item-action" :data-conv-id="conv.conversationId" :title="t('conversation.more')" @click.stop="handleToggleMenu(conv.conversationId)">
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
                </div>
                </el-collapse-item>
              </el-collapse>
              <div v-if="chatStore.conversations.length === 0" class="history-empty">
                {{ t('conversation.history') }}
              </div>
            </div>
        </template>
      </div>

      <!-- 中间聊天区域 -->
      <div class="col-mid">
        <ChatView class="chat-container" />
      </div>
    </div>
  </div>

  <!-- 历史对话操作菜单（Teleport 到 body，避免被 overflow 裁剪） -->
  <Teleport to="body">
    <div
      v-if="openMenuConvId"
      class="history-item-menu"
      :style="{ top: menuPosition.top + 'px', left: menuPosition.left + 'px' }"
      @click.stop
    >
      <button class="history-menu-item" @click="handleTogglePin(openMenuConvId!)">
        <span class="menu-icon" aria-hidden="true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2l3 7 7 .6-5.3 4.6 1.6 6.8L12 17.4 5.7 21l1.6-6.8L2 9.6 9 9z"/>
          </svg>
        </span>
        <span>{{ openMenuConvId && isConversationPinned(chatStore.conversations.find(c => c.conversationId === openMenuConvId)!) ? '取消置顶' : '置顶' }}</span>
      </button>
      <button class="history-menu-item" @click="openMenuConvId && handleStartRename(chatStore.conversations.find(c => c.conversationId === openMenuConvId)!)">
        <span class="menu-icon" aria-hidden="true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 20h9"/>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"/>
          </svg>
        </span>
        <span>{{ t('conversation.rename') }}</span>
      </button>
      <button class="history-menu-item danger" @click="handleConfirmDelete(openMenuConvId!)">
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
  </Teleport>

  <!-- 历史对话搜索弹框（DeepSeek 风格：全屏遮罩 + 顶部居中面板） -->
  <ConversationSearchDialog v-model:open="searchOpen" @select="handleSwitchConversation" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useChatStore } from '@/stores/useChatStore'
import ChatView from '@/views/ChatView.vue'
import ConversationSearchDialog from '@/components/ConversationSearchDialog.vue'
import { formatRelativeTime } from '@/utils/time'
import type { Conversation } from '@/types'
import { usePersistedState } from '@/composables/usePersistedRef'

/** 历史对话侧栏是否显示（问数页面始终展示） */
const showSelectorPanel = true

const { t } = useI18n()
const chatStore = useChatStore()

/** 历史对话侧栏是否折叠（独立于左侧菜单折叠状态；持久化到 localStorage，刷新后保持） */
const historyCollapsed = usePersistedState<boolean>('mc-chat-history-collapsed', false)

/** 向子组件 ChatView 提供收缩态状态与新建对话回调，用于在 chat-header 同行渲染浮动按钮 */
provide('historyCollapsed', historyCollapsed)
provide('handleNewChat', handleNewChat)

/** 搜索框是否展开（默认隐藏，点击搜索图标切换） */
const searchOpen = ref(false)

/** 当前打开操作菜单的会话 id（仅一个） */
const openMenuConvId = ref<string | null>(null)

/** 菜单浮层的定位坐标（Teleport 到 body，避免被 overflow 裁剪） */
const menuPosition = ref<{ top: number; left: number }>({ top: 0, left: 0 })

/** 当前正在重命名的会话 id（进入内联编辑态） */
const editingConvId = ref<string | null>(null)

/** 重命名输入框的临时值 */
const editingTitle = ref('')

/** 已折叠的历史分组 key 集合（供 expandedGroupKeys 计算属性桥接使用） */
const collapsedGroupKeys = ref(new Set<string>())



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


/** 按最后活跃时间分组后的历史会话列表（支持搜索过滤） */
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

/** 历史分组展开的 key，绑定到 el-collapse 的 v-model；默认全部展开，新分组也自动展开 */
const expandedGroupKeys = computed<string[]>({
  get: () => groupedConversations.value.filter(g => !collapsedGroupKeys.value.has(g.key)).map(g => g.key),
  set: (keys) => {
    const expanded = new Set(keys)
    collapsedGroupKeys.value = new Set(groupedConversations.value.map(g => g.key).filter(k => !expanded.has(k)))
  },
})


/**
 * 新对话：清空当前消息并生成新的会话 ID
 * 若当前正在流式输出，则将其转入后台继续生成（切回可恢复），而非终止
 */
async function handleNewChat(): Promise<void> {
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
  await chatStore.switchConversation(convId)
}

/** 切换操作菜单的显示 */
async function handleToggleMenu(convId: string): Promise<void> {
  // 如果该条正在重命名，先退出编辑态
  if (editingConvId.value && editingConvId.value !== convId) {
    editingConvId.value = null
    editingTitle.value = ''
  }
  if (openMenuConvId.value === convId) {
    openMenuConvId.value = null
    return
  }
  // 计算按钮位置，用于 Teleport 菜单定位
  const btn = document.querySelector(`.history-item-action[data-conv-id="${convId}"]`) as HTMLElement | null
  if (btn) {
    const rect = btn.getBoundingClientRect()
    menuPosition.value = { top: rect.bottom + 4, left: rect.right - 130 }
  }
  openMenuConvId.value = convId
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

onMounted(() => {
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
  background: var(--theme-bg);
  flex: 1;
}

/** 选择器面板容器（在历史侧栏 header 中） */
.selector-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.main {
  display: flex;
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}


.col-mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  position: relative;
  padding: 4px;
}

/* 历史对话侧栏 */
.history-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: transparent;
  border-right: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1), background 0.25s ease, border-color 0.25s ease;
}

.history-sidebar.collapsed {
  width: 0;
  border-right: none;
  overflow: hidden;
}


.history-header {
  padding: 14px 14px 6px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  box-sizing: border-box;
}

.history-sidebar.collapsed .history-header {
  display: none;
}

/** 操作按钮行（新对话 + 折叠按钮） */
.history-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  white-space: nowrap;
  flex-shrink: 0;
  letter-spacing: 0.2px;
}

.header-spacer {
  flex: 1;
}

.history-actions {
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
  border: none;
  background: transparent;
  color: var(--theme-text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  border-radius: 50%;
  transition: background-color 120ms ease, color 120ms ease;
}

.history-collapse-btn:hover {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
}

.history-collapse-btn:active {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
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

/** 新对话按钮独占一行 */
.history-new-chat-row {
  margin: 4px 14px 10px;
  flex-shrink: 0;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  border: 1px dashed color-mix(in srgb, var(--main-orange) 50%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
  color: var(--main-orange);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  box-sizing: border-box;
}

.new-chat-btn:hover {
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
  border-style: solid;
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
  padding: 4px 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 历史分组折叠（element-plus el-collapse）：箭头在右侧，默认全部展开 */
.history-collapse {
  border: none;
  --el-collapse-border-color: transparent;
  --el-collapse-header-bg-color: transparent;
  --el-collapse-header-text-color: var(--theme-text-muted);
  --el-collapse-header-height: 26px;
  --el-collapse-header-font-size: 11px;
  --el-collapse-content-bg-color: transparent;
  --el-collapse-content-text-color: var(--theme-text);
  --el-collapse-content-font-size: 13px;
}

.history-collapse :deep(.el-collapse-item__header) {
  height: var(--el-collapse-header-height);
  line-height: var(--el-collapse-header-height);
  font-weight: 500;
  letter-spacing: 0.3px;
  border-bottom: none;
  padding: 0 6px;
  margin-top: 6px;
  border-radius: 4px;
  transition: background-color 0.15s ease;
}

.history-collapse :deep(.el-collapse-item:first-child .el-collapse-item__header) {
  margin-top: 0;
}

.history-collapse :deep(.el-collapse-item__header:hover) {
  background: var(--theme-surface-hover);
}

.history-collapse :deep(.el-collapse-item__header.is-active) {
  color: var(--theme-text-muted);
}

.history-collapse :deep(.el-collapse-item__arrow) {
  color: var(--theme-text-muted);
  margin-right: 2px;
}

.history-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
  background: transparent;
}

.history-collapse :deep(.el-collapse-item__content) {
  padding: 0 0 2px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  min-height: 36px;
  border: 1px solid transparent;
  position: relative;
}

.history-item:hover:not(.active) {
  background: var(--theme-surface-hover);
}

.history-item.pinned:not(.active) {
  background: var(--theme-surface-hover);
}

.history-item.active {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  border-color: color-mix(in srgb, var(--main-orange) 20%, transparent);
  padding-left: 14px;
}

.history-item-content {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.history-item-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--theme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
  font-weight: 500;
}

.history-item.active .history-item-title {
  color: var(--main-orange);
  font-weight: 600;
}

.history-item.active .history-item-meta {
  color: color-mix(in srgb, var(--main-orange) 55%, var(--muted));
}

.history-item-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.3;
  white-space: nowrap;
  flex-shrink: 0;
  margin-left: auto;
  transition: opacity 0.15s ease;
}

/* hover / 菜单展开时隐藏时间，让位给右侧操作按钮 */
.history-item:hover .history-item-meta,
.history-item.menu-open .history-item-meta {
  opacity: 0;
}

.history-item-time {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 呼吸动画：会话正在生成时显示在历史项最左侧 */
.history-item-breathing {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--main-orange);
  flex-shrink: 0;
  animation: history-breathe 1.6s ease-in-out infinite;
}

@keyframes history-breathe {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--main-orange) 40%, transparent);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.75);
    box-shadow: 0 0 0 4px color-mix(in srgb, var(--main-orange) 0%, transparent);
  }
}

/* 未读标识：后台会话完成时显示的小圆点 */
.history-item-unread {
  display: inline-block;
  width: 7px;
  height: 7px;
  background: var(--main-orange);
  border-radius: 50%;
  flex-shrink: 0;
  margin: 0 2px;
  box-shadow: 0 0 0 2px var(--theme-surface);
}

/* 历史项右侧"三点"操作按钮：绝对定位覆盖右侧，与时间互换显示 */
.history-item-actions {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.history-item:hover .history-item-actions,
.history-item.menu-open .history-item-actions,
.history-item-actions.visible {
  opacity: 1;
}

.history-item-action {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  padding: 0;
  transition: all 0.15s ease;
}

.history-item-action:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.dot-icon {
  display: inline-flex;
  align-items: center;
}

/* 浮层操作菜单（重命名/删除） */
.history-item-menu {
  position: fixed;
  background: var(--theme-surface-elevated);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  min-width: 130px;
  padding: 5px;
  z-index: 2000;
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
  color: var(--theme-text);
  cursor: pointer;
  border-radius: 5px;
  text-align: left;
  width: 100%;
  font-family: inherit;
  transition: all 0.15s ease;
}

.history-menu-item:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.history-menu-item.danger {
  color: #e53e3e;
}

.history-menu-item.danger:hover {
  background: rgba(229, 62, 62, 0.08);
}

.menu-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

/* 内联重命名输入框 */
.history-item-edit-input {
  flex: 1;
  height: 28px;
  padding: 0 8px;
  font-size: 13px;
  border: 1px solid color-mix(in srgb, var(--main-orange) 40%, transparent);
  border-radius: 6px;
  background: var(--theme-surface-elevated);
  color: var(--theme-text);
  outline: none;
  font-family: inherit;
  min-width: 0;
  font-weight: 500;
}

.history-item-edit-input:focus {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 让 .history-item 作为 menu 浮层的定位锚点 */
.history-item {
  position: relative;
}

.history-empty {
  padding: 32px 12px;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  transition: padding-top 0.2s ease;
  border-radius: 16px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
}

</style>