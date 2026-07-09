<template>
  <div class="time-filter-widget">
    <div v-if="showTitle" class="time-filter-label">{{ component.title }}</div>
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
      @change="handleDateChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, TimeRangePreset, TimeRangeValue, TimeFilterComponentConfig } from '@/types'

defineOptions({
  name: 'TimeFilterWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 是否显示标题 */
  showTitle?: boolean
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
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  box-sizing: border-box;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
}

.time-filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}
</style>
