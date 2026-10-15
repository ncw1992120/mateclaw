<template>
  <FilterControlShell
    class="time-filter-widget"
    :title="component.title"
    :title-bar-style="component.titleBarStyle"
    :show-label="showLabel"
  >
    <template #icon>
      <DashboardComponentIcon type="timeFilter" :dashboard-theme="dashboardTheme" :title-icon-style="titleIconStylePreview ?? component.titleIconStyle" />
    </template>
    <div class="time-filter-controls">
      <el-date-picker
        v-model="customDateRange"
        class="time-filter-date-range"
        type="daterange"
        size="small"
        value-format="YYYY-MM-DD"
        unlink-panels
        :shortcuts="dateShortcuts"
        :disabled-date="disabledDate"
        :start-placeholder="t('insight.timeRange.startPlaceholder')"
        :end-placeholder="t('insight.timeRange.endPlaceholder')"
        :aria-label="component.title || t('insight.timeRange.startPlaceholder')"
        @calendar-change="handleCalendarChange"
        @visible-change="handlePanelVisibleChange"
        @change="handleDateChange"
      />
      <el-select
        v-if="showTimeGranularity"
        v-model="selectedTimeGranularity"
        class="time-filter-granularity"
        size="small"
        :aria-label="t('insight.timeRange.granularity')"
        @change="handleGranularityChange"
      >
        <el-option value="DAY" :label="t('insight.timeRange.granularityDay')" />
        <el-option value="WEEK" :label="t('insight.timeRange.granularityWeek')" />
        <el-option value="MONTH" :label="t('insight.timeRange.granularityMonth')" />
        <el-option value="QUARTER" :label="t('insight.timeRange.granularityQuarter')" />
        <el-option value="YEAR" :label="t('insight.timeRange.granularityYear')" />
      </el-select>
    </div>
  </FilterControlShell>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, TimeGranularity, TimeRangePreset, TimeRangeValue, TimeFilterComponentConfig, ComponentTitleIconStyle, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import FilterControlShell from './FilterControlShell.vue'

defineOptions({
  name: 'TimeFilterWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 预览态由仪表盘会话管理的时间范围。 */
  modelValue?: TimeRangeValue | string | string[]
  /** 预览会话保留的当前粒度，优先于配置默认值。 */
  timeGranularity?: TimeGranularity
  /** 是否由组件内部显示标题；画布编辑态由统一标题栏显示 */
  showTitle?: boolean
  /** 正在编辑的组件标题图标样式即时预览 */
  titleIconStylePreview?: ComponentTitleIconStyle
  dashboardTheme?: ResolvedDashboardTheme
}>()

// 隐藏标题栏（titleBarStyle: 'hidden'）对时间筛选器同样生效：即便外层把 show-title 置为 true，
// 只要组件自身标记为隐藏，内联标签仍随标题栏一并摘掉，仅保留控件 + 画布悬浮删除按钮。
const showLabel = computed(() => props.showTitle !== false && props.component.titleBarStyle !== 'hidden')

const emit = defineEmits<{
  (e: 'change', payload: { field: string; timeRange: TimeRangeValue | undefined; timeGranularity: TimeGranularity }): void
}>()

const { t } = useI18n()

const customDateRange = ref<[string, string] | null>(null)
watch(() => props.modelValue, (value) => {
  customDateRange.value = value && typeof value === 'object' && !Array.isArray(value) && 'start' in value && 'end' in value
    ? [value.start, value.end]
    : null
}, { immediate: true })

/** 从组件 config 提取时间筛选配置 */
const timeFilterConfig = computed<TimeFilterComponentConfig>(() => {
  return (props.component.config as TimeFilterComponentConfig) ?? { field: 'metric_time' }
})
const showTimeGranularity = computed(() => timeFilterConfig.value.showTimeGranularity !== false)
const selectedTimeGranularity = ref<TimeGranularity>(props.timeGranularity ?? timeFilterConfig.value.defaultTimeGranularity ?? 'DAY')
watch(() => [props.timeGranularity, timeFilterConfig.value.defaultTimeGranularity] as const, ([selected, configured]) => {
  selectedTimeGranularity.value = selected ?? configured ?? 'DAY'
}, { immediate: true })

function currentRange(): TimeRangeValue | undefined {
  if (!customDateRange.value?.[0] || !customDateRange.value?.[1]) return undefined
  return { preset: 'custom', start: customDateRange.value[0], end: customDateRange.value[1] }
}

function handleGranularityChange(value: TimeGranularity): void {
  selectedTimeGranularity.value = value
  emit('change', {
    field: timeFilterConfig.value.field,
    timeRange: currentRange(),
    timeGranularity: value,
  })
}

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
  let shortcuts = allShortcuts
  if (allowed) {
    const allowedSet = new Set(allowed)
    shortcuts = shortcuts.filter((s) => allowedSet.has(s.key))
  }
  const max = maxRangeDays.value
  if (max) shortcuts = shortcuts.filter((s) => shortcutSpanDays(s) <= max)
  return shortcuts
})

const DAY_MS = 24 * 3600 * 1000

/** 最大可选时间跨度（天，闭区间）；不填表示不限制 */
const maxRangeDays = computed<number | undefined>(() => {
  const value = timeFilterConfig.value.maxRangeDays
  return typeof value === 'number' && Number.isFinite(value) && value >= 1 ? Math.floor(value) : undefined
})

/** 计算快捷选项的闭区间跨度天数 */
function shortcutSpanDays(shortcut: { value: () => [Date, Date] }): number {
  const [start, end] = shortcut.value()
  const startMs = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime()
  const endMs = new Date(end.getFullYear(), end.getMonth(), end.getDate()).getTime()
  return Math.round((endMs - startMs) / DAY_MS) + 1
}

/** 日历面板首个选中日期（用于把第二次选择限制在最大跨度内） */
const calendarPick = ref<Date | null>(null)

function handleCalendarChange(dates: [Date, Date] | null): void {
  calendarPick.value = dates && dates[0] ? dates[0] : null
}

function handlePanelVisibleChange(visible: boolean): void {
  if (!visible) calendarPick.value = null
}

function disabledDate(date: Date): boolean {
  const max = maxRangeDays.value
  if (!max || !calendarPick.value) return false
  const pickMs = new Date(calendarPick.value.getFullYear(), calendarPick.value.getMonth(), calendarPick.value.getDate()).getTime()
  const dayMs = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  return Math.abs(dayMs - pickMs) > (max - 1) * DAY_MS
}

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 兜底收敛：保持 start 不动，将 end 限制在 start+(max-1) 天内 */
function clampEnd(start: string, end: string): string {
  const max = maxRangeDays.value
  if (!max) return end
  const startMs = new Date(`${start}T00:00:00`).getTime()
  const endMs = new Date(`${end}T00:00:00`).getTime()
  const maxEndMs = startMs + (max - 1) * DAY_MS
  if (endMs <= maxEndMs) return end
  return formatDate(new Date(maxEndMs))
}

/** 日期范围变化（快捷选项或自定义日期都会触发） */
function handleDateChange(dates: [string, string] | null): void {
  calendarPick.value = null
  if (!dates || !dates[0] || !dates[1]) {
    emit('change', { field: timeFilterConfig.value.field, timeRange: undefined, timeGranularity: selectedTimeGranularity.value })
    return
  }
  const clampedEnd = clampEnd(dates[0], dates[1])
  if (clampedEnd !== dates[1]) customDateRange.value = [dates[0], clampedEnd]
  const range: TimeRangeValue = {
    preset: 'custom',
    start: dates[0],
    end: clampedEnd,
  }
  emit('change', { field: timeFilterConfig.value.field, timeRange: range, timeGranularity: selectedTimeGranularity.value })
}
</script>

<style scoped>
.time-filter-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.time-filter-controls :deep(.time-filter-date-range) {
  flex: 1 1 auto;
  width: 0;
  min-width: 0;
}

.time-filter-controls :deep(.time-filter-granularity) {
  flex: 0 0 92px;
}

.time-filter-widget :deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
  background: var(--db-surface-card, var(--db-card));
  box-shadow: 0 0 0 1px var(--db-border) inset;
}

.time-filter-widget :deep(.el-date-editor.is-disabled.el-input__wrapper) {
  background: var(--db-hover);
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
