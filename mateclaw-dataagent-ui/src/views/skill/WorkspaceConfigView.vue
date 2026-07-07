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
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import { usePersistedRef } from '@/composables/usePersistedRef'
import AgentContextView from '../workspace/AgentContextView.vue'
import WorkspaceManageView from '../workspace/WorkspaceManageView.vue'
import MemberManageView from '../workspace/MemberManageView.vue'
import ResourceGrantView from '../workspace/ResourceGrantView.vue'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 二级菜单项 key 类型 */
type SubMenuKey = 'agentContext' | 'workspaceManage' | 'memberManage' | 'grantManage'

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
