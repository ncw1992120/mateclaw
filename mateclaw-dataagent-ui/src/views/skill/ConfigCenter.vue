<template>
  <div class="config-center-page">
    <!-- 顶部标题 -->
    <header class="config-topbar">
      <h1 class="page-title">{{ t('nav.subSkill') }}</h1>
    </header>

    <!-- Tab 横向栏 -->
    <nav class="config-tabs">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ t(tab.labelKey) }}
      </button>
    </nav>

    <!-- 内容区 -->
    <section class="config-content">
      <SkillManage v-if="activeTab === 'skill'" />
      <DataConfigView v-else-if="activeTab === 'data'" />
      <BusinessDictionaryView v-else-if="activeTab === 'dictionary'" />
      <AgentConfigView v-else-if="activeTab === 'agent'" />
      <ModelConfigView v-else-if="activeTab === 'model'" />
      <KnowledgeConfigView v-else-if="activeTab === 'knowledge'" />
      <WorkspaceConfigView v-else-if="activeTab === 'workspace'" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePersistedRef } from '@/composables/usePersistedRef'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import SkillManage from './SkillManage.vue'
import DataConfigView from './DataConfigView.vue'
import BusinessDictionaryView from './BusinessDictionaryView.vue'
import AgentConfigView from './AgentConfigView.vue'
import ModelConfigView from './ModelConfigView.vue'
import KnowledgeConfigView from './KnowledgeConfigView.vue'
import WorkspaceConfigView from './WorkspaceConfigView.vue'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** Tab 可选取值 */
const TAB_KEYS = ['skill', 'data', 'dictionary', 'agent', 'model', 'knowledge', 'workspace'] as const
type TabKey = (typeof TAB_KEYS)[number]

/** 当前激活的 Tab（刷新后保留） */
const activeTab = usePersistedRef<TabKey>(
  'mc-config-center-active-tab',
  'skill',
  (value) => (TAB_KEYS as readonly string[]).includes(value),
)

/** Tab 配置：i18n key + 所需权限点 */
interface TabConfig {
  key: TabKey
  labelKey: string
  /** 显示该 Tab 所需的权限点，未配置则对所有登录用户可见 */
  permission?: string
}

const tabs: TabConfig[] = [
  {
    key: 'skill',
    labelKey: 'configCenter.tabSkill',
    permission: PERMISSION.SKILL_VIEW,
  },
  {
    key: 'data',
    labelKey: 'configCenter.tabData',
    permission: PERMISSION.DATASOURCE_VIEW,
  },
  {
    key: 'dictionary',
    labelKey: 'configCenter.tabDictionary',
    permission: PERMISSION.BUSINESS_TERM_VIEW,
  },
  {
    key: 'agent',
    labelKey: 'configCenter.tabAgent',
    permission: PERMISSION.AGENT_VIEW,
  },
  {
    key: 'model',
    labelKey: 'configCenter.tabModel',
    permission: PERMISSION.MODEL_VIEW,
  },
  {
    key: 'knowledge',
    labelKey: 'configCenter.tabKnowledge',
    permission: PERMISSION.KNOWLEDGE_VIEW,
  },
  {
    key: 'workspace',
    labelKey: 'configCenter.tabWorkspace',
    permission: PERMISSION.WORKSPACE_VIEW,
  },
]

/** 按权限过滤后的可见 Tab */
const visibleTabs = computed<TabConfig[]>(() => {
  return tabs.filter((tab) => !tab.permission || hasPermission(tab.permission))
})

/** 当激活的 Tab 因权限不可见时，自动切换到第一个可见 Tab */
watch(visibleTabs, (list) => {
  if (list.length === 0) return
  const activeVisible = list.some((tab) => tab.key === activeTab.value)
  if (!activeVisible) {
    activeTab.value = list[0].key
  }
}, { immediate: true })

/** 监听自定义事件，支持从其他组件（如工作区下拉菜单）直达指定 Tab */
function handleNavigateToWorkspaceManage(): void {
  activeTab.value = 'workspace'
}

onMounted(() => {
  window.addEventListener('navigate-to-workspace-manage', handleNavigateToWorkspaceManage)
})

onBeforeUnmount(() => {
  window.removeEventListener('navigate-to-workspace-manage', handleNavigateToWorkspaceManage)
})
</script>

<style scoped>
.config-center-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: var(--theme-bg);
  overflow: hidden;
}

/* 顶部标题区 */
.config-topbar {
  padding: 18px 24px 14px;
  background: var(--theme-surface);
  flex-shrink: 0;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--theme-text);
  margin: 0;
  letter-spacing: 0.5px;
}

/* Tab 横向栏 */
.config-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 24px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.tab-item {
  position: relative;
  padding: 12px 16px;
  font-size: 14px;
  color: var(--theme-text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
  margin-bottom: -1px;
}

.tab-item::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 0;
  height: 3px;
  background: var(--main-orange);
  border-radius: 2px 2px 0 0;
  transition: width 0.2s;
}

.tab-item:hover {
  color: var(--main-orange);
}

.tab-item:hover::after {
  width: 60%;
}

.tab-item.active {
  color: var(--main-orange);
  font-weight: 600;
}

.tab-item.active::after {
  width: 80%;
}

/* 内容区 */
.config-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--theme-bg);
}
</style>
