<template>
  <div class="workspace-config-page">
    <!-- 左侧二级菜单 -->
    <aside class="workspace-sidebar">
      <nav class="sub-menu">
        <a
          v-for="item in subMenuItems"
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AgentContextView from '../workspace/AgentContextView.vue'

const { t } = useI18n()

/** 二级菜单项 */
const subMenuItems = [
  { key: 'agentContext', labelKey: 'workspaceMenu.agentContext' },
]

/** 当前激活的二级菜单 */
const activeSubMenu = ref('agentContext')
</script>

<style scoped>
.workspace-config-page {
  display: flex;
  width: 100%;
  height: 100%;
  background: #f5f6f8;
  overflow: hidden;
}

/* 左侧二级菜单 */
.workspace-sidebar {
  width: 180px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e8ecf2;
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
  color: #4e5969;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  text-decoration: none;
  white-space: nowrap;
  font-weight: 500;
}

.sub-menu-item:hover {
  background: rgba(240, 90, 35, 0.06);
  color: #f05a23;
}

.sub-menu-item.active {
  color: #f05a23;
  font-weight: 600;
  background: rgba(240, 90, 35, 0.1);
}

/* 右侧内容区 */
.workspace-content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  background: #f5f6f8;
}
</style>
