<template>
  <div class="kpi-card-widget">
    <div v-if="showTimeFilter" class="kpi-time-filter">
      <el-date-picker
        v-model="localDateRange"
        type="daterange"
        size="small"
        style="width: 220px"
        value-format="YYYY-MM-DD"
        unlink-panels
        :shortcuts="dateShortcuts"
        :start-placeholder="t('insight.timeRange.startPlaceholder')"
        :end-placeholder="t('insight.timeRange.endPlaceholder')"
        @change="handleDateChange"
      />
    </div>
    <div v-if="showTitle" class="kpi-header">{{ component.title }}</div>
    <div class="kpi-value">{{ kpiData?.value ?? '--' }}</div>
    <div v-if="showTitle && kpiData?.name" class="kpi-name">{{ kpiData.name }}</div>
    <div v-if="kpiData?.chg" class="kpi-chg" :class="kpiData.up ? 'up' : 'down'">
      <span>{{ kpiData.up ? '↑' : '↓' }} {{ kpiData.chg }}</span>
    </div>
    <div v-else class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData, TimeRangeValue } from '@/types'

defineOptions({
  name: 'KpiCardWidget',
})

const { t } = useI18n()

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否显示标题 */
  showTitle?: boolean
}>()

const emit = defineEmits<{
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
}>()

const kpiData = computed(() => props.componentData?.kpi)
const showTimeFilter = computed(() => props.component.enableTimeFilter)

/** 组件级时间选择器绑定值 */
const localDateRange = ref<[string, string] | null>(null)

/** 快捷选项 */
const dateShortcuts = computed(() => [
  { text: t('insight.timeRange.today'), value: () => { const today = new Date(); return [today, today] } },
  { text: t('insight.timeRange.7d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 6); return [start, end] } },
  { text: t('insight.timeRange.30d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 29); return [start, end] } },
  { text: t('insight.timeRange.90d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 89); return [start, end] } },
])

/** 日期选择变化 → 转换为 TimeRangeValue 并 emit */
function handleDateChange(val: [string, string] | null): void {
  if (!val) {
    emit('component-time-range-change', { componentId: props.component.id, timeRange: undefined })
    return
  }
  const [start, end] = val
  const timeRange: TimeRangeValue = { preset: 'custom', start, end }
  emit('component-time-range-change', { componentId: props.component.id, timeRange })
}
</script>

<style scoped>
.kpi-card-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 16px;
  box-sizing: border-box;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  position: relative;
}

.kpi-time-filter {
  position: absolute;
  top: 4px;
  right: 8px;
  z-index: 1;
}

.kpi-header {
  position: absolute;
  top: 8px;
  left: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--theme-text);
  line-height: 1.2;
  margin-bottom: 6px;
}

.kpi-name {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin-bottom: 4px;
}

.kpi-chg {
  font-size: 12px;
  font-weight: 500;
}

.kpi-chg.up {
  color: #52c41a;
}

.kpi-chg.down {
  color: #f5222d;
}

.kpi-placeholder {
  font-size: 12px;
  color: var(--theme-text-muted);
}
</style>
