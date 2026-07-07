<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <TopNavBar />

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 工作台模式 -->
      <template v-if="activeNav === 'workspace'">
        <!-- 工作台首页（无侧边栏） -->
        <WorkspaceHome v-if="currentSubPage === 'home'" @navigate="handlePageChange" />

        <!-- 工作台子页面（带侧边栏菜单） -->
        <template v-else>
          <SideMenu :collapsed="sideCollapsed" :active-page="currentSubPage" @update:collapsed="sideCollapsed = $event" @page-change="handlePageChange" />
          <div class="content-area">
            <!-- 面包屑导航 -->
            <div class="breadcrumb-bar">
              <span class="breadcrumb-path">{{ t('nav.workspace') }} / {{ currentPageTitle }}</span>
            </div>
            <!-- 页面内容 -->
            <component :is="currentComponent" :key="pageKey" class="page-body" />
          </div>
        </template>
      </template>

      <!-- 智能问数（默认工作台） -->
      <template v-else-if="activeNav === 'smart-ask'">
        <WorkbenchView />
      </template>

      <!-- 帮助 -->
      <template v-else-if="activeNav === 'help'">
        <HelpCenterView />
      </template>

      <!-- 其他页面占位 -->
      <template v-else>
        <div class="placeholder-page">
          <div class="placeholder-icon">🚧</div>
          <h2>{{ t('nav.' + activeNav) }}</h2>
          <p>{{ t('common.comingSoon') }}</p>
        </div>
      </template>
    </div>

    <!-- 模型配置弹窗（仅全局管理员可见） -->
    <ModelConfigDialog v-if="userStore.isAdmin" v-model:visible="showModelConfig" />

    <!-- Agent 配置弹窗 -->
    <AgentConfigDialog v-model:visible="showAgentConfig" :editing-agent="editingAgent" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import { useUserStore } from '@/stores/useUserStore'
import TopNavBar from './TopNavBar.vue'
import SideMenu from './SideMenu.vue'
import WorkspaceHome from '../workspace/WorkspaceHome.vue'
import WorkbenchView from '../WorkbenchView.vue'
import DatasourceView from '../DatasourceView.vue'
import DatasetView from '../DatasetView.vue'
import HelpCenterView from '../help/HelpCenterView.vue'
import PlaceholderPage from '../common/PlaceholderPage.vue'
import ModelConfigDialog from '../dialog/ModelConfigDialog.vue'
import AgentConfigDialog from '../dialog/AgentConfigDialog.vue'
import type { Agent } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const modelStore = useModelStore()
const userStore = useUserStore()

/** 当前激活的顶部导航 */
const activeNav = computed(() => (route.query.nav as string) || 'smart-ask')

/** 左侧菜单是否折叠 */
const sideCollapsed = ref(false)

/** 当前工作台子页面 */
const currentSubPage = ref('home')

/** 模型配置弹窗 */
const showModelConfig = ref(false)
/** 智能体配置弹窗 */
const showAgentConfig = ref(false)
/** 正在编辑的 Agent */
const editingAgent = ref<Agent | null>(null)

/** 子页面标题映射 */
const pageTitleMap: Record<string, string> = {
  newCreate: 'sideMenu.newCreate',
  datasource: 'sideMenu.datasource',
  dashboard: 'sideMenu.dashboard',
  spreadsheet: 'sideMenu.spreadsheet',
  dataScreen: 'sideMenu.dataScreen',
  adHocAnalysis: 'sideMenu.adHocAnalysis',
  selfService: 'sideMenu.selfService',
  dataEntry: 'sideMenu.dataEntry',
  dataPrep: 'sideMenu.dataPrep',
  dataset: 'sideMenu.dataset',
  memberPermission: 'sideMenu.memberPermission',
  budgetAnalysis: 'sideMenu.budgetAnalysis',
  dimensionAnalysis: 'sideMenu.dimensionAnalysis',
  quickEngine: 'sideMenu.quickEngine',
  selfCheck: 'sideMenu.selfCheck',
  recycleBin: 'sideMenu.recycleBin',
  fromTemplate: 'workspaceHome.fromTemplateCreate',
  localFile: 'workspaceHome.localFile',
  dataPortal: 'workspaceHome.dataPortal',
}

/** 当前页面标题 */
const currentPageTitle = computed(() => {
  const key = pageTitleMap[currentSubPage.value]
  return key ? t(key) : currentSubPage.value
})

/** 子页面切换计数器（用于强制重新渲染组件） */
const pageKey = ref(0)

/** 切回标签页时尝试续连的处理函数（onUnmounted 时清理） */
let handleVisibilityChange: (() => void) | null = null

/** 当前渲染组件 */
const currentComponent = computed(() => {
  const componentMap: Record<string, any> = {
    datasource: DatasourceView,
    dataset: DatasetView,
  }
  return componentMap[currentSubPage.value] || PlaceholderPage
})

/** 处理侧栏页面切换 */
function handlePageChange(page: string): void {
  if (page === 'workbench') {
    currentSubPage.value = 'home'
    sideCollapsed.value = false
  } else {
    currentSubPage.value = page
    pageKey.value++
  }
}

onMounted(async () => {
  agentStore.fetchAgents(1)
  modelStore.fetchEnabledModels()
  modelStore.fetchActiveModel()
  // Provider 列表仅全局管理员可访问
  if (userStore.isAdmin) {
    modelStore.fetchProviders()
  }
  // 先加载会话列表，续连时才能校验 conversationId 是否有效
  await chatStore.fetchConversations()
  // 刷新页面时尝试续连上一次未完成的 SSE 流（后端 RunState 5 分钟内可恢复）
  await chatStore.tryResumeStream()
  // 没有进入续连时，恢复当前选中会话的历史消息，避免刷新后显示为空态
  if (chatStore.conversationId && !chatStore.isStreaming) {
    await chatStore.switchConversation(chatStore.conversationId, true)
  }

  // 用户切回该 tab 时再次尝试续连，覆盖：刷新→离开→回来 的场景
  handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      chatStore.tryResumeStream()
    }
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  if (handleVisibilityChange) {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--theme-bg);
}

.main-content {
  display: flex;
  flex: 1;
  width: 100%;
  overflow: hidden;
}

.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.breadcrumb-bar {
  padding: 12px 24px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.breadcrumb-path {
  font-size: 13px;
  color: var(--theme-text-muted);
}

.page-body {
  flex: 1;
  overflow: auto;
  background: var(--theme-bg);
}

.placeholder-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.placeholder-icon {
  font-size: 64px;
}

.placeholder-page h2 {
  font-size: 20px;
  color: var(--theme-text);
  margin: 0;
}

.placeholder-page p {
  font-size: 14px;
  color: var(--theme-text-muted);
  margin: 0;
}
</style>