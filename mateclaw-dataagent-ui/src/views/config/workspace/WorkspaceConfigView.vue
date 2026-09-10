<template>
  <div class="workspace-config-page">
    <!-- 左侧二级菜单：与配置中心主导航同族（db-* token、图标+文字、激活主题淡底），透明底融入纸面卡片 -->
    <aside class="workspace-sidebar">
      <nav class="sub-menu">
        <button
          v-for="item in visibleSubMenuItems"
          :key="item.key"
          type="button"
          class="sub-menu-item"
          :class="{ active: activeSubMenu === item.key }"
          @click="activeSubMenu = item.key"
        >
          <span class="sub-menu-icon" aria-hidden="true"><el-icon :size="15"><component :is="item.icon" /></el-icon></span>
          <span class="sub-menu-label">{{ t(item.labelKey) }}</span>
        </button>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <section class="workspace-content">
      <AgentContextView v-if="activeSubMenu === 'agentContext'" />
      <WorkspaceManageView v-else-if="activeSubMenu === 'workspaceManage'" />
      <MemberManageView v-else-if="activeSubMenu === 'memberManage'" />
      <ResourceGrantView v-else-if="activeSubMenu === 'grantManage'" />
      <CronJobView v-else-if="activeSubMenu === 'cronJob'" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import type { Component } from 'vue'
import { Document, Folder, User, Lock, Clock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import { usePersistedRef } from '@/composables/usePersistedRef'
import AgentContextView from '@/views/workspace/AgentContextView.vue'
import WorkspaceManageView from '@/views/workspace/WorkspaceManageView.vue'
import MemberManageView from '@/views/workspace/MemberManageView.vue'
import ResourceGrantView from '@/views/workspace/ResourceGrantView.vue'
import CronJobView from '@/views/workspace/CronJobView.vue'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 二级菜单项 key 类型 */
type SubMenuKey = 'agentContext' | 'workspaceManage' | 'memberManage' | 'grantManage' | 'cronJob'

/** 二级菜单项配置（含权限点与图标） */
interface SubMenuItem {
  key: SubMenuKey
  labelKey: string
  /** 显示该菜单项所需的权限点 */
  permission: string
  /** 侧栏图标（Element Plus 图标组件，15px，与配置中心主导航同语言） */
  icon: Component
}

/** 子菜单图标：Element Plus 图标组件（15px 线性，与配置中心主导航同族） */
const SUB_MENU_ICONS: Record<SubMenuKey, Component> = {
  agentContext: Document,
  workspaceManage: Folder,
  memberManage: User,
  grantManage: Lock,
  cronJob: Clock,
}

const subMenuItems: SubMenuItem[] = [
  {
    key: 'agentContext',
    labelKey: 'workspaceMenu.agentContext',
    permission: PERMISSION.AGENT_VIEW,
    icon: SUB_MENU_ICONS.agentContext,
  },
  {
    key: 'workspaceManage',
    labelKey: 'workspaceMenu.workspaceManage',
    permission: PERMISSION.WORKSPACE_MANAGE,
    icon: SUB_MENU_ICONS.workspaceManage,
  },
  {
    key: 'memberManage',
    labelKey: 'workspaceMenu.memberManage',
    permission: PERMISSION.WORKSPACE_MEMBER_VIEW,
    icon: SUB_MENU_ICONS.memberManage,
  },
  {
    key: 'grantManage',
    labelKey: 'workspaceMenu.grantManage',
    permission: PERMISSION.WORKSPACE_MANAGE,
    icon: SUB_MENU_ICONS.grantManage,
  },
  {
    key: 'cronJob',
    labelKey: 'workspaceMenu.cronJob',
    permission: PERMISSION.CRON_JOB_VIEW,
    icon: SUB_MENU_ICONS.cronJob,
  },
]

/** 按权限过滤后的可见菜单项 */
const visibleSubMenuItems = computed<SubMenuItem[]>(() => {
  return subMenuItems.filter((item) => hasPermission(item.permission))
})

/** 当前激活的二级菜单（持久化到 localStorage，刷新后保留选中状态） */
const activeSubMenu = usePersistedRef<SubMenuKey>(
  'mc-workspace-active-sub-menu',
  'agentContext',
  (v) => subMenuItems.some((item) => item.key === v),
)

/** 当激活的菜单因权限不可见时，自动切换到第一个可见菜单 */
watch(visibleSubMenuItems, (list) => {
  if (list.length === 0) return
  const activeVisible = list.some((item) => item.key === activeSubMenu.value)
  if (!activeVisible) {
    activeSubMenu.value = list[0].key
  }
}, { immediate: true })

/** 监听自定义事件，支持从其他组件（如工作区下拉菜单）直达"工作区管理"子菜单 */
function handleNavigateToWorkspaceManage(): void {
  activeSubMenu.value = 'workspaceManage'
}

onMounted(() => {
  window.addEventListener('navigate-to-workspace-manage', handleNavigateToWorkspaceManage)
})

onBeforeUnmount(() => {
  window.removeEventListener('navigate-to-workspace-manage', handleNavigateToWorkspaceManage)
})
</script>

<style scoped>
.workspace-config-page {
  display: flex;
  gap: 16px;
  width: 100%;
  height: 100%;
  /* 透明底：与配置中心内容画布一致，子视图自带的工作区底色直接呈现 */
  background: transparent;
  overflow: hidden;
}

/* 左侧二级菜单：与主导航同族（透明底融入纸面，层级靠激活淡底与留白表达，不用异色底/硬边框切分） */
.workspace-sidebar {
  width: 180px;
  flex-shrink: 0;
  background: transparent;
  overflow-y: auto;
}

.sub-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* 子菜单项：与配置中心 .nav-item 同规格（13px/500、9px 圆角、8px 10px 内边距） */
.sub-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-secondary);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  white-space: nowrap;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.sub-menu-item:hover:not(.active) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.sub-menu-item.active {
  color: var(--main-orange);
  font-weight: 600;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.sub-menu-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0.9;
}

.sub-menu-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧内容区：子视图自带 topbar 与工作区底色，壳层透明传递 */
.workspace-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: transparent;
}
</style>
