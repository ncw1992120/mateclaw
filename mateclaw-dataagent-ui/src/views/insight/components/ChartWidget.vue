<template>
  <div class="chart-widget">
    <div class="chart-header">
      <div v-if="showTitle" class="chart-title">{{ component.title }}</div>
      <div v-if="showTimeFilter" class="chart-time-filter">
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
    <div ref="chartContainerRef" class="chart-container"></div>
    <div v-if="!hasOption" class="chart-placeholder">{{ activeTabError || t('insight.chartNoData') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData, TimeRangeValue, ComponentTab } from '@/types'
import { useEChartsRenderer } from '@/composables/useEChartsRenderer'

defineOptions({
  name: 'ChartWidget',
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

const chartContainerRef = ref<HTMLElement | null>(null)
const { renderECharts, disposeChart } = useEChartsRenderer()

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

/** 当前激活 Tab 的 option */
const activeTabOption = computed(() => {
  if (!hasTabs.value || !activeTabId.value) return null
  return props.componentData?.tabs?.[activeTabId.value]?.option ?? null
})
const activeTabError = computed(() => {
  if (!hasTabs.value || !activeTabId.value) return null
  return props.componentData?.tabs?.[activeTabId.value]?.error ?? null
})

/** 当前生效的 option（Tab 模式取 activeTabOption，否则取主 option） */
const effectiveOption = computed(() => {
  if (hasTabs.value) return activeTabOption.value
  return props.componentData?.option
})

const hasOption = computed(() => !!effectiveOption.value)
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

/** 渲染（或重渲染）图表 */
async function render(): Promise<void> {
  await nextTick()
  if (!chartContainerRef.value) {
    return
  }
  if (!effectiveOption.value) {
    disposeChart(chartContainerRef.value)
    chartContainerRef.value.innerHTML = ''
    return
  }
  renderECharts(chartContainerRef.value, effectiveOption.value)
}

onMounted(() => {
  render()
})

// 主 option 变化时重绘
watch(
  () => effectiveOption.value,
  (newOption, oldOption) => {
    if (newOption !== oldOption) {
      render()
    }
  },
)

// Tab 切换时重绘
watch(activeTabId, () => {
  render()
})

/** 容器尺寸变化时重绘 */
watch(chartContainerRef, (el) => {
  if (el) {
    render()
  }
})
</script>

<style scoped>
.chart-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  overflow: hidden;
  position: relative;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  min-height: 32px;
  flex-shrink: 0;
}

.chart-title {
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}

.chart-time-filter {
  display: flex;
  align-items: center;
}

.chart-container {
  flex: 1;
  min-height: 200px;
  width: 100%;
}

.widget-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 12px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  overflow-x: auto;
}

.widget-tab {
  padding: 6px 12px;
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

.chart-placeholder {
  position: absolute;
  top: 60%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: var(--theme-text-muted);
  pointer-events: none;
}
</style>
