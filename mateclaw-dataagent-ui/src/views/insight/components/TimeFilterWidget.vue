<template>
  <FilterControlShell
    class="time-filter-widget"
    :title="component.title"
    :title-bar-style="component.titleBarStyle"
    :show-label="showLabel"
  >
    <template #icon>
      <DashboardComponentIcon type="timeFilter" :dashboard-theme="dashboardTheme" :title-icon-style="component.titleIconStyle" />
    </template>
    <el-date-picker
      v-model="customDateRange"
      type="daterange"
      size="small"
      style="width: 100%"
      value-format="YYYY-MM-DD"
      unlink-panels
      :shortcuts="dateShortcuts"
      :start-placeholder="t('insight.timeRange.startPlaceholder')"
      :end-placeholder="t('insight.timeRange.endPlaceholder')"
      :aria-label="component.title || t('insight.timeRange.startPlaceholder')"
      @change="handleDateChange"
    />
  </FilterControlShell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, TimeRangePreset, TimeRangeValue, TimeFilterComponentConfig, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import FilterControlShell from './FilterControlShell.vue'

defineOptions({
  name: 'TimeFilterWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 是否由组件内部显示标题；画布编辑态由统一标题栏显示 */
  showTitle?: boolean
  dashboardTheme?: ResolvedDashboardTheme
}>()

const showLabel = computed(() => props.showTitle !== false && props.component.titleBarStyle !== 'hidden')

const emit = defineEmits<{
  (e: 'change', payload: { field: string; timeRange: TimeRangeValue }): void
}>()

const { t } = useI18n()

const customDateRange = ref<[string, string] | null>(null)

/** 从组件 config 提取时间筛选配置 */
const timeFilterConfig = computed<TimeFilterComponentConfig>(() => {
  return (props.component.config as TimeFilterComponentConfig) ?? { field: 'metric_time' }
})

/** 所有快捷选项定义 */
const allShortcuts: Array<{ key: TimeRangePreset; text: string; value: () => [Date, Date] }> = [
  {
    key: 'today',
    text: t('insight.timeRange.today'),
    value: () => {
      const today = new Date()
      return [today, today]
    },
  },
  {
    key: '7d',
    text: t('insight.timeRange.7d'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 6)
      return [start, end]
    },
  },
  {
    key: '30d',
    text: t('insight.timeRange.30d'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 29)
      return [start, end]
    },
  },
  {
    key: '90d',
    text: t('insight.timeRange.90d'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 89)
      return [start, end]
    },
  },
]

/** 根据配置过滤可用快捷选项 */
const dateShortcuts = computed(() => {
  const allowed = timeFilterConfig.value.availablePresets
  if (!allowed) return allShortcuts
  const allowedSet = new Set(allowed)
  return allShortcuts.filter((s) => allowedSet.has(s.key))
})

/** 日期范围变化（快捷选项或自定义日期都会触发） */
function handleDateChange(dates: [string, string] | null): void {
  if (!dates || !dates[0] || !dates[1]) {
    emit('change', { field: timeFilterConfig.value.field, timeRange: undefined as unknown as TimeRangeValue })
    return
  }
  const range: TimeRangeValue = {
    preset: 'custom',
    start: dates[0],
    end: dates[1],
  }
  emit('change', { field: timeFilterConfig.value.field, timeRange: range })
}
</script>

<style scoped>
.time-filter-widget :deep(.el-date-editor) {
  width: 100%;
}

.time-filter-widget :deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
  background: var(--db-hover);
  box-shadow: 0 0 0 1px var(--db-border) inset;
}

.time-filter-widget :deep(.el-input__inner) {
  color: var(--db-text);
}

.time-filter-widget :deep(.el-input__inner::placeholder) {
  color: var(--db-text-muted);
}

.time-filter-widget :deep(.el-range-separator),
.time-filter-widget :deep(.el-range__icon),
.time-filter-widget :deep(.el-range__close-icon) {
  color: var(--db-text-secondary);
}
</style>
