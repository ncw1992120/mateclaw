<template>
  <span class="dashboard-tab-title">
    <DashboardComponentIcon
      type="tab"
      :title="title"
      :dashboard-theme="dashboardTheme"
      :title-icon-style="titleIconStylePreview ?? titleIconStyle"
      :variant="variant"
      :size="13"
    />
    <button
      v-if="editable"
      type="button"
      class="dashboard-tab-title-style-trigger"
      :aria-label="`编辑页签图标 ${title}`"
      title="修改页签图标样式"
      @click.stop="emit('edit', $event.currentTarget as HTMLElement)"
    >
      <el-icon :size="11"><EditPen /></el-icon>
    </button>
    <span class="dashboard-tab-title-text">{{ title }}</span>
  </span>
</template>

<script setup lang="ts">
import { EditPen } from '@element-plus/icons-vue'
import type { ComponentTitleIconStyle, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'

withDefaults(defineProps<{
  title: string
  dashboardTheme?: ResolvedDashboardTheme
  titleIconStyle?: ComponentTitleIconStyle
  titleIconStylePreview?: ComponentTitleIconStyle
  variant?: number
  editable?: boolean
}>(), { editable: false, variant: 0 })

const emit = defineEmits<{
  (event: 'edit', anchor: HTMLElement): void
}>()
</script>

<style scoped>
.dashboard-tab-title {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 3px;
}

.dashboard-tab-title :deep(.dashboard-component-icon) {
  margin-right: 0;
}

.dashboard-tab-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-tab-title-style-trigger {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  background: transparent;
  cursor: pointer;
}

.dashboard-tab-title-style-trigger:hover,
.dashboard-tab-title-style-trigger:focus-visible {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.dashboard-tab-title-style-trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
</style>
