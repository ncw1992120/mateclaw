<template>
  <div class="workbench">
    <!-- Main: Left Menu + History Sidebar + Chat -->
    <div class="main">
      <!-- 左侧可展开菜单 -->
      <div class="left-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <!-- 顶部标题栏 -->
        <div class="sidebar-topbar" :class="{ collapsed: sidebarCollapsed }">
          <span v-if="!sidebarCollapsed" class="sidebar-brand">{{ t('nav.smartAsk') }}</span>
          <button
            class="collapse-btn top-collapse-btn"
            :title="sidebarCollapsed ? t('conversation.expand') : t('conversation.collapse')"
            @click="sidebarCollapsed = !sidebarCollapsed"
          >
            <span class="collapse-btn-svg" aria-hidden="true">
              <!-- 参考 DSH IconPanelLeftOutline16：面板左栏图标 -->
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">
                <rect x="1.4" y="1.4" width="13.2" height="13.2" rx="2"/>
                <line x1="5.4" y1="1.4" x2="5.4" y2="14.6"/>
              </svg>
            </span>
          </button>
        </div>

        <!-- 工作区切换器 -->
        <div v-if="!sidebarCollapsed" class="sidebar-workspace">
          <el-dropdown trigger="click" @command="handleWorkspaceCommand">
            <div class="workspace-trigger">
              <div class="workspace-info">
                <span class="workspace-current-name">{{ workspaceDisplayName(userStore.currentWorkspace) }}</span>
                <span v-if="userStore.currentWorkspace?.memberRole" class="workspace-current-role">{{ userStore.currentWorkspace.memberRole }}</span>
              </div>
              <span class="workspace-trigger-arrow">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="ws in userStore.workspaces"
                  :key="ws.id"
                  :command="ws.id"
                  :class="{ 'is-active': ws.id === userStore.currentWorkspaceId }"
                  :title="workspaceDisplayName(ws)"
                >
                  <span class="workspace-item-name">{{ workspaceDisplayName(ws) }}</span>
                  <el-icon v-if="ws.id === userStore.currentWorkspaceId" class="workspace-check"><Check /></el-icon>
                  <span v-if="ws.memberRole" class="ws-role">{{ ws.memberRole }}</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="canManageWorkspace" divided command="manage">
                  <span class="workspace-manage-text">{{ t('workspace.manage') }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 折叠态：工作区图标入口 -->
        <div v-else class="sidebar-workspace-collapsed">
          <el-dropdown trigger="click" @command="handleWorkspaceCommand">
            <div class="workspace-trigger-collapsed" :title="workspaceDisplayName(userStore.currentWorkspace)">
              <span class="workspace-icon"><el-icon><OfficeBuilding /></el-icon></span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="ws in userStore.workspaces"
                  :key="ws.id"
                  :command="ws.id"
                  :class="{ 'is-active': ws.id === userStore.currentWorkspaceId }"
                >
                  <span class="workspace-item-name">{{ workspaceDisplayName(ws) }}</span>
                  <el-icon v-if="ws.id === userStore.currentWorkspaceId" class="workspace-check"><Check /></el-icon>
                </el-dropdown-item>
                <el-dropdown-item v-if="canManageWorkspace" divided command="manage">{{ t('workspace.manage') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <nav class="sidebar-menu">
          <div
            v-for="group in sidebarGroups"
            :key="group.title"
            class="sidebar-group"
            :class="{ collapsed: sidebarCollapsed }"
          >
            <a
              v-for="item in group.items"
              :key="item.key"
              class="sidebar-item"
              :class="{ active: activeSidebarItem === item.key }"
              @click="activeSidebarItem = item.key"
              :title="sidebarCollapsed ? t(item.label) : ''"
            >
              <span class="sidebar-icon"><component :is="item.icon" /></span>
              <span v-if="!sidebarCollapsed" class="sidebar-label">{{ t(item.label) }}</span>
            </a>
          </div>
        </nav>

        <!-- 底部用户区 -->
        <div class="sidebar-footer" :class="{ collapsed: sidebarCollapsed }">
          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="user-card" :class="{ collapsed: sidebarCollapsed }" :title="userStore.username">
              <div class="user-avatar">
                <span class="avatar-text">{{ avatarText }}</span>
              </div>
              <div v-if="!sidebarCollapsed" class="user-info">
                <span class="user-name">{{ userStore.nickname || userStore.username || '用户' }}</span>
                <span class="user-role">{{ userStore.role }}</span>
              </div>
              <span v-if="!sidebarCollapsed" class="user-card-arrow">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span class="dropdown-user-name">{{ userStore.nickname || userStore.username }}</span>
                  <span class="dropdown-user-account">@{{ userStore.username }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <span class="dropdown-logout-item">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                      <polyline points="16 17 21 12 16 7"/>
                      <line x1="21" y1="12" x2="9" y2="12"/>
                    </svg>
                    <span>退出登录</span>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

      </div>

      <!-- 历史对话侧栏（问数、洞察、报告页面展示） -->
      <div v-if="showSelectorPanel" class="history-sidebar" :class="{ collapsed: historyCollapsed }">
        <div class="history-header">
          <span v-if="!historyCollapsed" class="history-title">{{ t('conversation.history') }}</span>
          <div class="header-spacer"></div>
          <!-- 操作按钮行 -->
          <div class="history-actions">
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
          <!-- 搜索框 -->
          <div class="history-search">
            <el-input
              v-model="conversationSearchKeyword"
              :placeholder="t('conversation.searchPlaceholder')"
              clearable
              class="history-search-input"
              :prefix-icon="Search"
            />
          </div>
          <!-- 历史对话列表（仅问数页面展示） -->
          <template v-if="activeSidebarItem === 'qa'">
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
              <div v-else-if="groupedConversations.length === 0 && conversationSearchKeyword" class="history-empty">
                {{ t('conversation.noSearchResult') }}
              </div>
            </div>
          </template>
        </template>
      </div>

      <!-- 中间聊天区域 -->
      <div class="col-mid" :class="{ 'with-floating-actions': showSelectorPanel && historyCollapsed }">
        <!-- 历史侧栏收缩后：悬浮在聊天面板左侧的快捷按钮 -->
        <div v-if="showSelectorPanel && historyCollapsed" class="history-floating">
          <button class="history-float-btn" :title="t('conversation.expand')" @click="historyCollapsed = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              <line x1="8" y1="9" x2="16" y2="9"/>
              <line x1="8" y1="13" x2="13" y2="13"/>
            </svg>
          </button>
          <button class="history-float-btn" :title="t('conversation.newChat')" @click="handleNewChat">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
          </button>
        </div>
        <ChatView v-if="activeSidebarItem === 'qa'" class="chat-container" />
        <DashboardListView v-else-if="activeSidebarItem === 'interpret'" class="chat-container" />
        <ReportListView v-else-if="activeSidebarItem === 'report'" class="chat-container" />
        <ConfigCenter v-else-if="activeSidebarItem === 'skill'" class="chat-container" />
        <HelpCenterView v-else-if="activeSidebarItem === 'help'" class="chat-container" />
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
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useChatStore } from '@/stores/useChatStore'
import { useUserStore } from '@/stores/useUserStore'
import { usePersistedRef } from '@/composables/usePersistedRef'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import ChatView from '@/views/ChatView.vue'
import DashboardListView from '@/views/insight/DashboardListView.vue'
import ReportListView from '@/views/report/ReportListView.vue'
import ConfigCenter from '@/views/config/ConfigCenter.vue'
import HelpCenterView from '@/views/help/HelpCenterView.vue'
import {
  OfficeBuilding,
  Check,
  Search,
  ChatDotRound,
  DataLine,
  Document,
  Setting,
  QuestionFilled,
} from '@element-plus/icons-vue'
import type { Conversation, Workspace } from '@/types'

/** 左侧菜单可选取值（一级菜单 key） */
const SIDEBAR_ITEM_KEYS = ['qa', 'interpret', 'report', 'skill', 'help'] as const

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()
const { hasPermission } = usePermission()

/** 当前用户是否有管理工作区权限（workspace:manage） */
const canManageWorkspace = computed<boolean>(() => hasPermission(PERMISSION.WORKSPACE_MANAGE))

/** 用户头像文字 */
const avatarText = computed(() => {
  const name = userStore.nickname || userStore.username || '用'
  return name.charAt(0).toUpperCase()
})

/** 左侧菜单是否折叠 */
const sidebarCollapsed = ref(false)

/** 历史对话侧栏是否折叠（独立于左侧菜单折叠状态） */
const historyCollapsed = ref(false)

/** 当前打开操作菜单的会话 id（仅一个） */
const openMenuConvId = ref<string | null>(null)

/** 菜单浮层的定位坐标（Teleport 到 body，避免被 overflow 裁剪） */
const menuPosition = ref<{ top: number; left: number }>({ top: 0, left: 0 })

/** 当前正在重命名的会话 id（进入内联编辑态） */
const editingConvId = ref<string | null>(null)

/** 重命名输入框的临时值 */
const editingTitle = ref('')

/** 历史会话搜索关键词 */
const conversationSearchKeyword = ref('')

/** 已折叠的历史分组 key 集合（供 expandedGroupKeys 计算属性桥接使用） */
const collapsedGroupKeys = ref(new Set<string>())

/** 当前选中的侧边栏菜单项（刷新后保留） */
const activeSidebarItem = usePersistedRef<(typeof SIDEBAR_ITEM_KEYS)[number]>(
  'mc-workbench-active-sidebar-item',
  'qa',
  (value) => (SIDEBAR_ITEM_KEYS as readonly string[]).includes(value),
)

/** 是否显示选择器面板（问数页面展示历史会话） */
const showSelectorPanel = computed(() => activeSidebarItem.value === 'qa')

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

/** 侧边栏分组配置 */
const sidebarGroups = [
  {
    title: 'sidebar.core',
    items: [
      {
        key: 'qa',
        label: 'nav.subQa',
        // 问数：与登录页一致的对话图标
        icon: ChatDotRound,
      },
      {
        key: 'interpret',
        label: 'nav.subInterpret',
        // 洞察：与登录页一致的数据折线图标
        icon: DataLine,
      },
      {
        key: 'report',
        label: 'nav.subReport',
        // 报告：与登录页一致的文档图标
        icon: Document,
      },
    ],
  },
  {
    title: 'sidebar.system',
    items: [
      {
        key: 'skill',
        label: 'nav.subSkill',
        // 配置：Element Plus 风格设置图标
        icon: Setting,
      },
      {
        key: 'help',
        label: 'nav.subHelp',
        // 帮助：Element Plus 风格问号图标
        icon: QuestionFilled,
      },
    ],
  },
]

/** 按最后活跃时间分组后的历史会话列表（支持搜索过滤） */
const groupedConversations = computed<ConversationGroup[]>(() => {
  const groupMap = new Map<string, ConversationGroup>()
  const now = new Date()
  const todayStart = getDayStart(now).getTime()
  const yesterdayStart = todayStart - DAY_MILLISECONDS
  const sevenDaysStart = todayStart - 6 * DAY_MILLISECONDS
  const thirtyDaysStart = todayStart - 29 * DAY_MILLISECONDS

  let sourceConversations = [...chatStore.conversations]

  // 搜索过滤：按标题关键词匹配
  const keyword = conversationSearchKeyword.value.trim().toLowerCase()
  if (keyword) {
    sourceConversations = sourceConversations.filter(conv =>
      (conv.title || '').toLowerCase().includes(keyword)
    )
  }

  const sortedConversations = sourceConversations.sort((a, b) => getConversationTime(b) - getConversationTime(a))
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

/** 工作区显示名称：优先使用 name，空则回退 description */
function workspaceDisplayName(ws: Workspace | null): string {
  if (!ws) {
    return '选择工作区'
  }
  return ws.name?.trim() || ws.description?.trim() || ''
}

/** 工作区下拉命令处理 */
function handleWorkspaceCommand(command: string | number): void {
  if (command === 'manage') {
    if (!canManageWorkspace.value) {
      ElMessage.warning(t('workspace.manageNoPermission'))
      return
    }
    // 通过 localStorage 预置配置中心 Tab 和工作空间子菜单，实现直达"工作空间-工作区"
    localStorage.setItem('mc-config-center-active-tab', 'workspace')
    localStorage.setItem('mc-workspace-active-sub-menu', 'workspaceManage')
    // 发送自定义事件，通知已挂载的子组件（ConfigCenter、WorkspaceConfigView）即时响应
    window.dispatchEvent(new CustomEvent('navigate-to-workspace-manage'))
    // 若当前不在配置中心页面，通过路由跳转触发侧边栏切换
    if (activeSidebarItem.value !== 'skill') {
      router.push({ path: '/', query: { menu: 'skill' } })
    }
    return
  }
  // 切换工作空间前清理当前会话相关缓存，避免刷新后恢复旧工作空间的脏数据
  chatStore.resetForWorkspaceSwitch()
  userStore.setCurrentWorkspace(command)
  ElMessage.success('已切换工作区')
  window.location.reload()
}

/** 用户菜单命令处理 */
function handleUserCommand(command: string): void {
  if (command === 'logout') {
    // 先清理聊天内存状态（会话列表、流式状态等），再清除登录态，避免切换用户后脏数据
    chatStore.resetForWorkspaceSwitch()
    userStore.logout()
    router.push('/login')
  }
}

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
 * 监听路由 query.menu，支持同页跳转（如点击“管理工作区”）。
 * onMounted 只处理页面首次挂载；watch 处理组件已挂载后的 query 变化。
 */
watch(
  () => route.query.menu as string | undefined,
  (menuQuery) => {
    if (menuQuery && (SIDEBAR_ITEM_KEYS as readonly string[]).includes(menuQuery)) {
      activeSidebarItem.value = menuQuery as (typeof SIDEBAR_ITEM_KEYS)[number]
      router.replace({ path: '/', query: {} })
    }
  }
)

onMounted(() => {
  // 处理外部跳转入口：如“管理工作区”会携带 ?menu=skill，需要激活对应侧边栏菜单并清理 query
  const menuQuery = route.query.menu as string
  if (menuQuery && (SIDEBAR_ITEM_KEYS as readonly string[]).includes(menuQuery)) {
    activeSidebarItem.value = menuQuery as (typeof SIDEBAR_ITEM_KEYS)[number]
    router.replace({ path: '/', query: {} })
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

/* 左侧侧边栏 */
.left-sidebar {
  width: 210px;
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), background 0.25s ease, border-color 0.25s ease;
  overflow: hidden;
}

.left-sidebar.collapsed {
  width: 56px;
}

/* 顶部伸缩按钮 */
.sidebar-topbar {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 0 14px;
  flex-shrink: 0;
}

.sidebar-topbar.collapsed {
  justify-content: center;
  padding: 0 8px;
}

.sidebar-brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--theme-text);
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.top-collapse-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
}

/* 工作区切换器 */
.sidebar-workspace {
  padding: 8px 10px 12px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--theme-border);
}

.sidebar-workspace :deep(.el-dropdown) {
  display: block;
  width: 100%;
}

.workspace-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--theme-bg);
  border: 1px solid var(--theme-border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.workspace-trigger:hover {
  border-color: color-mix(in srgb, var(--main-orange) 20%, transparent);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--main-orange) 5%, transparent);
}

.workspace-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.workspace-current-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.workspace-current-role {
  flex-shrink: 0;
  font-size: 10px;
  color: #fff;
  background: linear-gradient(135deg, var(--main-orange), var(--dark-orange));
  padding: 2px 6px;
  border-radius: 10px;
  line-height: 1.3;
  font-weight: 500;
}

.workspace-trigger-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-text-muted);
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.workspace-trigger:hover .workspace-trigger-arrow {
  color: var(--main-orange);
}

.sidebar-workspace-collapsed {
  padding: 12px 8px 10px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  border-bottom: 1px solid var(--theme-border);
}

.workspace-trigger-collapsed {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  color: var(--main-orange);
  transition: all 0.2s ease;
}

.workspace-trigger-collapsed:hover {
  background: var(--theme-surface-elevated);
  border-color: color-mix(in srgb, var(--main-orange) 18%, transparent);
}

.workspace-icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.workspace-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-check {
  margin-left: 8px;
  color: var(--main-orange);
  font-size: 12px;
}

.workspace-manage-text {
  color: var(--theme-text-secondary);
}

.ws-role {
  margin-left: 8px;
  font-size: 11px;
  color: var(--muted);
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
  transition: background-color 120ms ease, color 120ms ease;
}

.collapse-btn:hover {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
}

.collapse-btn:active {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
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
  padding: 6px 10px;
  flex: 1;
  overflow-y: auto;
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-group.collapsed {
  gap: 6px;
}

.sidebar-group.collapsed .sidebar-item {
  justify-content: center;
  padding: 9px 8px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
  text-decoration: none;
  white-space: nowrap;
  font-weight: 500;
}

.sidebar-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-text-muted);
  background: transparent;
  transition: all 0.2s ease;
}

.sidebar-icon :deep(svg) {
  width: 18px;
  height: 18px;
  display: block;
}

.sidebar-label {
  flex: 1;
}

.sidebar-item:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.sidebar-item:hover .sidebar-icon {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  transform: scale(1.05);
}

.sidebar-item.active {
  color: var(--main-orange);
  font-weight: 600;
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.sidebar-item.active .sidebar-icon {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
}

/* 侧边栏底部用户区 */
.sidebar-footer {
  padding: 8px 10px;
  border-top: 1px solid var(--theme-border);
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.sidebar-footer :deep(.el-dropdown) {
  display: block;
  width: 100%;
}

.sidebar-footer.collapsed {
  padding: 8px;
  justify-content: center;
}

.sidebar-footer.collapsed :deep(.el-dropdown) {
  width: auto;
}

.user-card {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 6px 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: transparent;
}

.user-card:hover {
  background: var(--theme-surface-hover);
  border-color: var(--theme-border);
}

.user-card.collapsed {
  width: 36px;
  height: 36px;
  padding: 0;
  justify-content: center;
  border-radius: 50%;
}

.user-card.collapsed:hover {
  background: var(--theme-surface-hover);
  border-color: transparent;
}

.user-card-arrow {
  margin-left: auto;
  color: var(--theme-text-muted);
  flex-shrink: 0;
  transition: color 0.2s;
}

.user-card:hover .user-card-arrow {
  color: var(--theme-text-secondary);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--main-orange) 0%, var(--dark-orange) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.user-role {
  font-size: 11px;
  color: var(--theme-text-muted);
  line-height: 1.2;
}

/* 下拉菜单用户信息样式 */
.dropdown-user-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  line-height: 1.4;
}

.dropdown-user-account {
  display: block;
  font-size: 11px;
  color: var(--theme-text-muted);
  line-height: 1.3;
}

.dropdown-logout-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--theme-text-secondary);
}

.dropdown-logout-item svg {
  flex-shrink: 0;
}

.col-mid {
  flex: 1;
  background: var(--theme-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  position: relative;
  padding: 12px;
}

/* 历史对话侧栏 */
.history-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: var(--theme-surface);
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

/* 历史侧栏收缩后：悬浮在聊天面板左侧的快捷按钮列 */
.history-floating {
  position: absolute;
  left: 28px;
  top: 24px;
  z-index: 20;
  display: flex;
  flex-direction: row;
  gap: 10px;
  animation: history-float-in 0.25s ease-out both;
}

@keyframes history-float-in {
  from { opacity: 0; transform: translateX(-8px); }
  to   { opacity: 1; transform: translateX(0); }
}

.history-float-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--theme-border);
  border-radius: 50%;
  background: var(--theme-surface-elevated);
  color: var(--theme-text-secondary);
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
}

.history-float-btn:hover {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 35%, transparent);
  background: color-mix(in srgb, var(--main-orange) 8%, var(--theme-surface-elevated));
  transform: scale(1.06);
}

.history-float-btn:active {
  transform: scale(0.98);
}

.history-header {
  padding: 12px 12px 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  box-sizing: border-box;
}

.history-sidebar.collapsed .history-header {
  display: none;
}

/* 搜索框 */
.history-search {
  padding: 0 12px 10px;
  flex-shrink: 0;
}

.history-search-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  background: var(--theme-surface-hover);
  box-shadow: none !important;
}

.history-search-input :deep(.el-input__wrapper:hover) {
  box-shadow: none !important;
}

.history-search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--main-orange) 40%, transparent) !important;
}

.history-search-input :deep(.el-input__prefix) {
  color: var(--muted);
}

.history-search-input :deep(.el-input__clear) {
  color: var(--muted);
}

/* 工作区切换器 */
.workspace-switcher {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px 5px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  flex-shrink: 0;
  max-width: 130px;
}

.workspace-switcher:hover {
  background: var(--theme-surface-elevated);
  border-color: color-mix(in srgb, var(--main-orange) 18%, transparent);
}

.workspace-icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.workspace-name {
  font-size: 12px;
  color: var(--theme-text);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workspace-arrow {
  font-size: 10px;
  color: var(--main-orange);
  opacity: 0.7;
  flex-shrink: 0;
}

.ws-role {
  margin-left: 8px;
  font-size: 11px;
  color: var(--muted);
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
  padding: 0 12px 10px;
  flex-shrink: 0;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 32px;
  border: 1px solid color-mix(in srgb, var(--main-orange) 25%, transparent);
  border-radius: 8px;
  background: transparent;
  color: var(--main-orange);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  box-sizing: border-box;
}

.new-chat-btn:hover {
  border-color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
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
  border-radius: 16px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.04),
    0 8px 28px rgba(0, 0, 0, 0.06);
  transition: padding-top 0.2s ease;
}

.col-mid.with-floating-actions .chat-container {
  padding-top: 56px;
}
</style>