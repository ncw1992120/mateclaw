<template>
  <div class="chart-widget">
    <div v-if="showTitle" class="chart-title">{{ component.title }}</div>
    <div ref="chartContainerRef" class="chart-container"></div>
    <div v-if="!hasOption" class="chart-placeholder">{{ t('insight.chartNoData') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData } from '@/types'
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

const chartContainerRef = ref<HTMLElement | null>(null)
const { renderECharts } = useEChartsRenderer()

const hasOption = computed(() => !!props.componentData?.option)

/** 渲染（或重渲染）图表 */
async function render(): Promise<void> {
  await nextTick()
  if (!chartContainerRef.value || !props.componentData?.option) {
    return
  }
  renderECharts(chartContainerRef.value, props.componentData.option)
}

onMounted(() => {
  render()
})

watch(
  () => props.componentData,
  () => {
    render()
  },
  { deep: true }
)
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

.chart-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.chart-container {
  flex: 1;
  min-height: 200px;
  width: 100%;
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
