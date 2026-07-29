<template>
  <div class="workspace-config-page">
    <!-- 左侧二级菜单 -->
    <aside class="workspace-sidebar">
      <nav class="sub-menu">
        <a
          v-for="item in visibleSubMenuItems"
          :key="item.key"
          class="sub-menu-item"
          :class="{ active: activeSubMenu === item.key }"
          @click="activeSubMenu = item.key"
        >
          {{ t(item.labelKey) }}
        </a>
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

/** 二级菜单项配置（含权限点） */
interface SubMenuItem {
  key: SubMenuKey
  labelKey: string
  /** 显示该菜单项所需的权限点 */
  permission: string
}

const subMenuItems: SubMenuItem[] = [
  {
    key: 'agentContext',
    labelKey: 'workspaceMenu.agentContext',
    permission: PERMISSION.AGENT_VIEW,
  },
  {
    key: 'workspaceManage',
    labelKey: 'workspaceMenu.workspaceManage',
    permission: PERMISSION.WORKSPACE_MANAGE,
  },
  {
    key: 'memberManage',
    labelKey: 'workspaceMenu.memberManage',
    permission: PERMISSION.WORKSPACE_MEMBER_VIEW,
  },
  {
    key: 'grantManage',
    labelKey: 'workspaceMenu.grantManage',
    permission: PERMISSION.WORKSPACE_MANAGE,
  },
  {
    key: 'cronJob',
    labelKey: 'workspaceMenu.cronJob',
    permission: PERMISSION.CRON_JOB_VIEW,
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
  width: 100%;
  height: 100%;
  background: var(--theme-bg);
  overflow: hidden;
}

/* 左侧二级菜单 */
.workspace-sidebar {
  width: 180px;
  flex-shrink: 0;
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
  padding: 16px 0;
  overflow-y: auto;
}

.sub-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 12px;
}

.sub-menu-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  text-decoration: none;
  white-space: nowrap;
  font-weight: 500;
}

.sub-menu-item:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.sub-menu-item.active {
  color: var(--main-orange);
  font-weight: 600;
  background: rgba(240, 90, 35, 0.1);
}

/* 右侧内容区 */
.workspace-content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  background: var(--theme-bg);
}
</style>
