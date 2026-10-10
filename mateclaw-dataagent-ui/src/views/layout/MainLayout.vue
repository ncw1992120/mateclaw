<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <TopNavBar />

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 智能问数（默认工作台） -->
      <template v-if="activeNav === 'smart-ask'">
        <WorkbenchView />
      </template>

      <!-- 洞察仪表盘 -->
      <template v-else-if="activeNav === 'insight'">
        <DashboardListView />
      </template>

      <!-- 报告 -->
      <template v-else-if="activeNav === 'report'">
        <ReportListView />
      </template>

      <!-- 配置中心 -->
      <template v-else-if="activeNav === 'config'">
        <ConfigCenter />
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
import { ref, computed, onMounted, onUnmounted, watch, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import { useUserStore } from '@/stores/useUserStore'
import { shouldLoadActiveModel } from './mainLayoutModelLoad'
import TopNavBar from './TopNavBar.vue'
import WorkbenchView from '../WorkbenchView.vue'
import ReportListView from '../report/ReportListView.vue'
import ConfigCenter from '../config/ConfigCenter.vue'
import HelpCenterView from '../help/HelpCenterView.vue'
import ModelConfigDialog from '../dialog/ModelConfigDialog.vue'
import AgentConfigDialog from '../dialog/AgentConfigDialog.vue'
import type { Agent } from '@/types'

// 洞察列表视图传递依赖较重（Schema 解析/分页等），按需加载以减轻首页解析负担；
// 仅改变打包拆分，不改路由与 query 语义。显式解包 .default，保证运行时与测试环境下
// loader 的 resolve 值恒为组件对象本身。
const DashboardListView = defineAsyncComponent(
  async () => (await import('../insight/DashboardListView.vue')).default,
)

const { t } = useI18n()
const route = useRoute()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const modelStore = useModelStore()
const userStore = useUserStore()

/** 当前激活的顶部导航 */
const activeNav = computed(() => (route.query.nav as string) || 'smart-ask')

/** 模型配置弹窗 */
const showModelConfig = ref(false)
/** 智能体配置弹窗 */
const showAgentConfig = ref(false)
/** 正在编辑的 Agent */
const editingAgent = ref<Agent | null>(null)

/** 切回标签页时尝试续连的处理函数（onUnmounted 时清理） */
let handleVisibilityChange: (() => void) | null = null

/**
 * 导航作用域后台数据：会话列表、历史消息恢复与 SSE 续连只属于智能问数。
 * 进入洞察/报告/配置等导航时不发起这些请求，缩短首次进入的无关请求链路。
 */
const initializedNavScopes = new Set<string>()
/** 当前导航作用域令牌：用户快速切换导航时，旧作用域尚未完成的异步回调据此失效 */
let navScopeToken = 0

/** 智能问数作用域：加载会话列表并尝试恢复上次会话/续连（导航切换后失效则不写状态） */
async function initSmartAskScope(token: number): Promise<void> {
  await Promise.all([
    agentStore.fetchAgents(1),
    chatStore.fetchConversations(),
  ])
  if (token !== navScopeToken) {
    return
  }
  // 刷新页面时尝试续连上一次未完成的 SSE 流（后端 RunState 5 分钟内可恢复）
  // tryResumeStream 内部对已完成对话会直接用 listMessages 渲染（不走 SSE 回放），
  // 对仍在运行的对话走 SSE buffer 回放；返回 true 时 MainLayout 无需再 switchConversation
  const resumed = await chatStore.tryResumeStream()
  if (token !== navScopeToken) {
    return
  }
  // 没有进入续连/恢复流程时，恢复当前选中会话的历史消息，避免刷新后显示为空态
  if (!resumed && chatStore.conversationId && !chatStore.isStreaming) {
    await chatStore.switchConversation(chatStore.conversationId, true)
  }
}

/** 按当前导航初始化所需的后台数据（每个作用域只执行一次） */
function ensureNavScopeData(nav: string): void {
  if (initializedNavScopes.has(nav)) {
    return
  }
  initializedNavScopes.add(nav)
  if (nav !== 'smart-ask') {
    return
  }
  const token = ++navScopeToken
  void initSmartAskScope(token)
}

onMounted(() => {
  // 模型/供应商列表只被配置弹窗消费：后台加载不阻塞首屏导航渲染
  void modelStore.fetchEnabledModels().catch(() => {})
  if (userStore.isAdmin) {
    void modelStore.fetchProviders().catch(() => {})
  }
  if (shouldLoadActiveModel(activeNav.value)) {
    modelStore.fetchActiveModel()
  }
  ensureNavScopeData(activeNav.value)

  // 用户切回该 tab 时再次尝试续连，覆盖：刷新→离开→回来 的场景（仅问数作用域有效）
  handleVisibilityChange = () => {
    if (document.visibilityState === 'visible' && activeNav.value === 'smart-ask') {
      chatStore.tryResumeStream()
    }
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

/** 导航切换：进入问数时才恢复原会话初始化流程 */
watch(activeNav, (nav) => {
  ensureNavScopeData(nav)
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
