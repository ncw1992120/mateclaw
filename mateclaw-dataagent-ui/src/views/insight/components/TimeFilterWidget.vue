<template>
    <div class="time-filter-widget" :class="`title-bar-${props.component.titleBarStyle ?? 'standard'}`">
    <div class="time-filter-body">
      <div v-if="showTitle !== false && component.titleBarStyle !== 'hidden'" class="time-filter-label"><DashboardComponentIcon type="timeFilter" :dashboard-theme="dashboardTheme" />{{ component.title }}</div>
      <el-date-picker
        v-model="customDateRange"
        type="daterange"
        size="small"
        value-format="YYYY-MM-DD"
        unlink-panels
        :shortcuts="dateShortcuts"
        :start-placeholder="t('insight.timeRange.startPlaceholder')"
        :end-placeholder="t('insight.timeRange.endPlaceholder')"
        @change="handleDateChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, TimeRangePreset, TimeRangeValue, TimeFilterComponentConfig, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'

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
.time-filter-widget {
  width: 100%;
  height: 100%;
  container-type: inline-size;
  display: flex;
  align-items: center;
  padding: var(--space-md);
  box-sizing: border-box;
  background: var(--component-surface, var(--db-card));
  border-radius: var(--component-radius, var(--radius-lg));
}

/* 同筛选器：标题语义是表单标签，故左右排列。
   日期范围控件本身天然更宽（两个日期 + 分隔 + 图标），
   因此回退阈值比普通筛选器更高（380px）。 */
.time-filter-body {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-width: 0;
}

.time-filter-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--db-text-secondary);
  white-space: nowrap;
}

.time-filter-body :deep(.el-date-editor) {
  flex: 1 1 0%;
  min-width: 0;
}

@container (max-width: 380px) {
  .time-filter-body {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-xs);
  }

  .time-filter-body :deep(.el-date-editor) {
    flex: 0 0 auto;
    width: 100%;
  }
}

.time-filter-body :deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
  background: var(--db-hover);
  box-shadow: 0 0 0 1px var(--db-border) inset;
}

.time-filter-body :deep(.el-input__inner) {
  color: var(--db-text);
}

.time-filter-body :deep(.el-input__inner::placeholder) {
  color: var(--db-text-muted);
}

.time-filter-body :deep(.el-range-separator),
.time-filter-body :deep(.el-range__icon),
.time-filter-body :deep(.el-range__close-icon) {
  color: var(--db-text-secondary);
}
</style>
