<template>
  <div class="config-center-page">
    <!-- 悬浮纸面卡片：左栏导航 + 内容画布；页头已并入侧栏，顶导已指示当前分区，不再占用整行 -->
    <div class="page-card">
      <div class="config-split">
        <!-- 左侧分组导航：身份锚点 + 分组标题 + 子项常驻，承载多层级菜单；可整体收起为图标轨（状态持久化） -->
        <nav class="config-nav" :class="{ collapsed: navCollapsed }">
          <!-- 收起态图标轨：展开把手入流置于轨顶，与内容区无重叠 -->
          <button
            v-if="navCollapsed"
            type="button"
            class="nav-expand-btn"
            :title="t('configCenter.expandNav')"
            :aria-label="t('configCenter.expandNav')"
            @click="navCollapsed = false"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="3" y1="5" x2="21" y2="5" />
              <line x1="3" y1="12" x2="15" y2="12" />
              <line x1="3" y1="19" x2="21" y2="19" />
            </svg>
          </button>

          <!-- 侧栏头部：图标 chip + 页面标题 + 收起按钮，作为侧栏视觉锚点与语义 heading -->
          <div class="nav-header">
            <span class="nav-header-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
            </span>
            <h2 class="nav-header-title">{{ t('nav.subSkill') }}</h2>
            <button
              type="button"
              class="nav-collapse-btn"
              :title="t('configCenter.collapseNav')"
              :aria-label="t('configCenter.collapseNav')"
              @click="navCollapsed = true"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <polyline points="11 17 6 12 11 7" />
                <polyline points="18 17 13 12 18 7" />
              </svg>
            </button>
          </div>
          <template v-for="group in visibleGroups" :key="group.key">
            <div class="nav-group-label">{{ t(group.labelKey) }}</div>
            <button
              v-for="tab in group.items"
              :key="tab.key"
              type="button"
              class="nav-item"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              <span class="nav-item-icon" v-html="tab.icon"></span>
              <span class="nav-item-label">{{ t(tab.labelKey) }}</span>
            </button>
          </template>
        </nav>

        <!-- 内容画布：透明底，子视图自带的工作区底色直接呈现 -->
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePersistedRef } from '@/composables/usePersistedRef'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import SkillManage from './skill/SkillManage.vue'
import DataConfigView from './datasource/DataConfigView.vue'
import BusinessDictionaryView from './dictionary/BusinessDictionaryView.vue'
import AgentConfigView from './agent/AgentConfigView.vue'
import ModelConfigView from './model/ModelConfigView.vue'
import KnowledgeConfigView from './knowledge/KnowledgeConfigView.vue'
import WorkspaceConfigView from './workspace/WorkspaceConfigView.vue'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** Tab 可选取值 */
const TAB_KEYS = ['skill', 'data', 'dictionary', 'knowledge', 'agent', 'model', 'workspace'] as const
type TabKey = (typeof TAB_KEYS)[number]

/** 当前激活的 Tab（刷新后保留） */
const activeTab = usePersistedRef<TabKey>(
  'mc-config-center-active-tab',
  'skill',
  (value) => (TAB_KEYS as readonly string[]).includes(value),
)

/** 侧栏收起状态（刷新后保留） */
const navCollapsed = usePersistedRef<boolean>(
  'mc-config-nav-collapsed',
  false,
  (value) => typeof value === 'boolean',
)

/** 一级分组键 */
type GroupKey = 'capability' | 'data' | 'platform'

/** Tab 配置：i18n key + 所需权限点 + 所属分组 + 侧栏图标 */
interface TabConfig {
  key: TabKey
  labelKey: string
  /** 显示该 Tab 所需的权限点，未配置则对所有登录用户可见 */
  permission?: string
  /** 所属一级分组 */
  group: GroupKey
  /** 侧栏图标（内联 SVG） */
  icon: string
}

/** 侧栏图标：统一 15px 线性描边，与页面其它图标语言一致 */
const ICONS: Record<TabKey, string> = {
  skill: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2 3 14h7l-1 8 10-12h-7z"/></svg>',
  data: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/></svg>',
  dictionary: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h11a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3z"/><path d="M8 8h6"/></svg>',
  knowledge: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h7a2 2 0 0 1 2 2v11a2 2 0 0 0-2-2H3z"/><path d="M21 6h-7a2 2 0 0 0-2 2v11a2 2 0 0 1 2-2h7z"/></svg>',
  agent: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="8" width="16" height="12" rx="2"/><circle cx="9" cy="14" r="1"/><circle cx="15" cy="14" r="1"/><path d="M12 8V4"/></svg>',
  model: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 3v4M12 17v4M3 12h4M17 12h4"/></svg>',
  workspace: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/></svg>',
}

const tabs: TabConfig[] = [
  {
    key: 'skill',
    labelKey: 'configCenter.tabSkill',
    permission: PERMISSION.SKILL_VIEW,
    group: 'capability',
    icon: ICONS.skill,
  },
  {
    key: 'agent',
    labelKey: 'configCenter.tabAgent',
    permission: PERMISSION.AGENT_VIEW,
    group: 'capability',
    icon: ICONS.agent,
  },
  {
    key: 'data',
    labelKey: 'configCenter.tabData',
    permission: PERMISSION.DATASOURCE_VIEW,
    group: 'data',
    icon: ICONS.data,
  },
  {
    key: 'dictionary',
    labelKey: 'configCenter.tabDictionary',
    permission: PERMISSION.BUSINESS_TERM_VIEW,
    group: 'data',
    icon: ICONS.dictionary,
  },
  {
    key: 'knowledge',
    labelKey: 'configCenter.tabKnowledge',
    permission: PERMISSION.KNOWLEDGE_VIEW,
    group: 'data',
    icon: ICONS.knowledge,
  },
  {
    key: 'model',
    labelKey: 'configCenter.tabModel',
    permission: PERMISSION.MODEL_VIEW,
    group: 'platform',
    icon: ICONS.model,
  },
  {
    key: 'workspace',
    labelKey: 'configCenter.tabWorkspace',
    permission: PERMISSION.WORKSPACE_VIEW,
    group: 'platform',
    icon: ICONS.workspace,
  },
]

/** 按权限过滤后的可见 Tab */
const visibleTabs = computed<TabConfig[]>(() => {
  return tabs.filter((tab) => !tab.permission || hasPermission(tab.permission))
})

/** 一级分组定义（顺序即侧栏展示顺序） */
const NAV_GROUPS: { key: GroupKey; labelKey: string }[] = [
  { key: 'capability', labelKey: 'configCenter.groupCapability' },
  { key: 'data', labelKey: 'configCenter.groupData' },
  { key: 'platform', labelKey: 'configCenter.groupPlatform' },
]

/** 按分组聚合后的可见导航（无可见子项的分组自动隐藏） */
const visibleGroups = computed(() =>
  NAV_GROUPS
    .map((group) => ({ ...group, items: visibleTabs.value.filter((tab) => tab.group === group.key) }))
    .filter((group) => group.items.length > 0),
)

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
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
  overflow: hidden;
  /* 四周留灰底边距，让纸面卡片悬浮于页面底色之上，与透明导航拉开层级 */
  padding: var(--space-md) var(--space-lg) var(--space-lg);
}

/* 纸面卡片：白面 + 圆角 + 投影，与报告/洞察页统一 */
.page-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

/* 主体分栏：左侧分组导航 + 右侧内容画布 */
.config-split {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 分组导航：与内容区同处一张纸面（不再用硬边框/异色底切分），层级靠分组标题、激活淡底与留白表达；收起时收窄为图标轨 */
.config-nav {
  width: 208px;
  flex-shrink: 0;
  padding: 4px 10px 16px;
  overflow-x: hidden;
  overflow-y: auto;
  transition: width 0.22s ease, padding 0.22s ease, opacity 0.15s ease;
}
.config-nav.collapsed {
  width: 44px;
  padding-left: 7px;
  padding-right: 7px;
  overflow: hidden;
}
/* 收起态只保留轨顶展开把手，其余子项隐藏（不卸载，展开即恢复） */
.config-nav.collapsed > *:not(.nav-expand-btn) {
  display: none;
}

/* 收起按钮：侧栏头部右侧幽灵图标按钮 */
.nav-collapse-btn {
  margin-left: auto;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--db-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast), background var(--transition-fast);
}
.nav-collapse-btn:hover {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
}

/* 展开把手：收起态图标轨顶部的幽灵按钮，入流居中，不遮挡内容区 */
.nav-expand-btn {
  margin: 14px auto 0;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: color var(--transition-fast), border-color var(--transition-fast);
}
.nav-expand-btn:hover {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 45%, transparent);
}

/* 侧栏头部：图标 chip + 标题 + 收起按钮，页面身份的紧凑锚点（不再占用整行页头） */
.nav-header {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 8px 12px;
  /* 收起动画期间禁止内部内容撑宽：标题区允许被压缩 */
  min-width: 0;
}

.nav-header-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-header-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 分组标题：11px 灰字，仅作层级锚点不抢子项视觉 */
.nav-group-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--db-text-muted);
  letter-spacing: 0.05em;
  padding: 14px 8px 6px;
}

/* 子项：默认透明，hover 浅灰底；激活为主题色淡底 + 主题色文字（实心 pill 只留给顶导） */
.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 2px;
  border: none;
  border-radius: 9px;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-secondary);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.nav-item:hover:not(.active) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.nav-item.active {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  font-weight: 600;
}

.nav-item-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0.9;
}

.nav-item-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 内容画布：占满右侧剩余空间，内部自行滚动 */
.config-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: var(--space-lg) var(--space-xl) var(--space-xl);
}
</style>
