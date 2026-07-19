<template>
  <div class="kpi-card-widget">
    <div class="kpi-card-inner">
      <div class="kpi-card-header">
        <div class="kpi-title-row">
          <span v-if="showTitle" class="kpi-header-title">{{ component.title }}</span>
        </div>
        <div v-if="showTimeFilter" class="kpi-time-filter">
          <el-date-picker
            v-model="localDateRange"
            type="daterange"
            size="small"
            style="width: 200px"
            value-format="YYYY-MM-DD"
            unlink-panels
            :shortcuts="dateShortcuts"
            :start-placeholder="t('insight.timeRange.startPlaceholder')"
            :end-placeholder="t('insight.timeRange.endPlaceholder')"
            @change="handleDateChange"
          />
        </div>
      </div>

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

      <!-- 多指标模式 -->
      <div v-if="isMultiKpi" class="kpi-multi-body">
        <div
          v-for="(item, idx) in activeKpiListData"
          :key="idx"
          class="kpi-multi-item"
        >
          <div class="kpi-value">{{ item?.value ?? '--' }}</div>
          <div v-if="item?.name" class="kpi-name">{{ item.name }}</div>
          <div v-if="item?.chg" class="kpi-chg" :class="item.up ? 'up' : 'down'">
            <el-icon class="kpi-trend-icon"><component :is="item.up ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
            <span>{{ item.chg }}</span>
          </div>
        </div>
        <div v-if="!activeKpiListData || activeKpiListData.length === 0" class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
      </div>

      <!-- 单指标模式 -->
      <div v-else class="kpi-body">
        <div class="kpi-value">{{ activeKpiData?.value ?? '--' }}</div>
        <div v-if="showTitle && activeKpiData?.name" class="kpi-name">{{ activeKpiData.name }}</div>
        <div v-if="activeKpiData?.chg" class="kpi-chg" :class="activeKpiData.up ? 'up' : 'down'">
          <el-icon class="kpi-trend-icon"><component :is="activeKpiData.up ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          <span>{{ activeKpiData.chg }}</span>
        </div>
        <div v-else-if="!activeKpiData?.value" class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
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
const kpiListData = computed(() => props.componentData?.kpiList)
const showTimeFilter = computed(() => props.component.enableTimeFilter)

/** 是否启用多指标模式 */
const isMultiKpi = computed(() => !!props.component.multiKpi)

/** 是否有多 Tab 模式（基于组件配置判断，而非后端返回数据） */
const hasTabs = computed(() => {
  return !!(props.component.tabs && props.component.tabs.length > 0)
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

/** 当前生效的 KPI 数据（Tab 模式下不 fallback 到主数据） */
const activeKpiData = computed(() => {
  if (!hasTabs.value) return kpiData.value
  if (!activeTabId.value) return null
  return props.componentData?.tabs?.[activeTabId.value]?.kpi ?? null
})

/** 当前生效的 KPI 多指标数据列表 */
const activeKpiListData = computed(() => {
  if (!hasTabs.value) return kpiListData.value ?? []
  if (!activeTabId.value) return []
  return props.componentData?.tabs?.[activeTabId.value]?.kpiList ?? []
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
  box-sizing: border-box;
}

.kpi-card-inner {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: var(--space-lg);
  box-sizing: border-box;
}

.kpi-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
  min-height: 24px;
}

.kpi-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
}

.kpi-icon {
  font-size: 16px;
  line-height: 1;
}

.kpi-header-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-time-filter {
  flex-shrink: 0;
}

.widget-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  width: 100%;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  overflow-x: auto;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-xs);
}

.widget-tab {
  padding: var(--space-xs) var(--space-sm);
  font-size: 12px;
  color: var(--db-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  border-radius: var(--radius-sm);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.widget-tab:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.widget-tab.active {
  color: var(--db-accent);
  background: var(--db-accent-light);
  font-weight: 600;
}

.kpi-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  flex: 1;
  gap: var(--space-xs);
}

.kpi-value-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: var(--space-xs);
}

.kpi-value {
  font-size: 36px;
  font-weight: 500;
  color: var(--db-text);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

.kpi-name {
  font-size: 13px;
  color: var(--db-text-secondary);
  font-weight: 500;
  margin-bottom: 8px;
}

.kpi-chg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 14px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-variant-numeric: tabular-nums;
  background: var(--db-hover);
}

.kpi-chg.up {
  color: var(--db-positive);
  background: var(--db-positive-bg);
}

.kpi-chg.down {
  color: var(--db-danger);
  background: var(--db-danger-bg);
}

.kpi-trend-icon {
  font-size: 10px;
}

.kpi-placeholder {
  font-size: 13px;
  color: var(--db-text-muted);
}

.kpi-sub {
  font-size: 12px;
  color: var(--db-text-quaternary);
}

/* 多指标模式样式 */
.kpi-multi-body {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  flex: 1;
  width: 100%;
  gap: 0;
}

.kpi-multi-item {
  flex: 1 1 0;
  min-width: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-sm) var(--space-xs);
  box-sizing: border-box;
  gap: var(--space-xs);
}

.kpi-multi-item + .kpi-multi-item {
  border-left: 1px solid var(--db-border);
}

.kpi-multi-item .kpi-value {
  font-size: 22px;
  font-weight: 500;
  color: var(--db-text);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.kpi-multi-item .kpi-name {
  font-size: 11px;
  color: var(--db-text-secondary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.kpi-multi-item .kpi-chg {
  font-size: 11px;
}

@media (max-width: 767px) {
  .kpi-card-inner {
    padding: var(--space-md);
  }

  .kpi-value {
    font-size: 28px;
  }

  .kpi-multi-item .kpi-value {
    font-size: 18px;
  }

  .kpi-time-filter {
    width: 100%;
  }

  .kpi-time-filter :deep(.el-date-editor) {
    width: 100% !important;
  }
}
</style>
