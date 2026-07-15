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
    <!-- Tab 栏（多 Tab 模式） -->
    <div v-if="hasTabs" class="widget-tabs">
      <div
        v-for="tab in tabList"
        :key="tab.id"
        class="widget-tab"
        :class="{ active: activeTabId === tab.id }"
        @click="activeTabId = tab.id"
      >
        {{ tab.title }}
      </div>
    </div>
    <div class="kpi-body">
      <div class="kpi-value">{{ activeKpiData?.value ?? '--' }}</div>
      <div v-if="showTitle && activeKpiData?.name" class="kpi-name">{{ activeKpiData.name }}</div>
      <div v-if="activeKpiData?.chg" class="kpi-chg" :class="activeKpiData.up ? 'up' : 'down'">
        <span>{{ activeKpiData.up ? '↑' : '↓' }} {{ activeKpiData.chg }}</span>
      </div>
      <div v-else class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData, TimeRangeValue, ComponentTab } from '@/types'

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

/** 是否有多 Tab 数据 */
const hasTabs = computed(() => {
  const tabs = props.componentData?.tabs
  return tabs && Object.keys(tabs).length > 0
})
const tabList = computed<ComponentTab[]>(() => props.component.tabs ?? [])
const activeTabId = ref('')

watch(hasTabs, (val) => {
  if (val && !activeTabId.value) {
    activeTabId.value = tabList.value[0]?.id ?? ''
  }
  if (!val) {
    activeTabId.value = ''
  }
}, { immediate: true })

/** 当前生效的 KPI 数据（Tab 模式取 activeTab，否则取主 kpi） */
const activeKpiData = computed(() => {
  if (!hasTabs.value || !activeTabId.value) return kpiData.value
  return props.componentData?.tabs?.[activeTabId.value]?.kpi ?? null
})

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

.widget-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  overflow-x: auto;
  margin-bottom: 8px;
}

.widget-tab {
  padding: 4px 10px;
  font-size: 12px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  transition: all 0.15s ease;
}

.widget-tab:hover {
  color: var(--theme-text);
}

.widget-tab.active {
  color: var(--main-orange);
  border-bottom-color: var(--main-orange);
  font-weight: 600;
}

.kpi-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
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
