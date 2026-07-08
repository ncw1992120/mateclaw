<template>
  <div class="component-palette">
    <div class="palette-header">{{ t('insight.paletteTitle') }}</div>
    <div class="palette-list">
      <div
        v-for="item in paletteItems"
        :key="item.type + (item.chartType ?? '')"
        class="palette-item"
        draggable="true"
        @dragstart="handleDragStart($event, item)"
      >
        <span class="palette-icon" v-html="item.icon"></span>
        <span class="palette-label">{{ t(item.labelKey) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { InsightComponentType, ChartType } from '@/types'

defineOptions({
  name: 'ComponentPalette',
})

const { t } = useI18n()

/** 物料项定义 */
interface PaletteItem {
  type: InsightComponentType
  chartType?: ChartType
  icon: string
  labelKey: string
}

/** 物料列表 —— 使用内联 SVG 图标，精确匹配 ECharts 图表类型 */
const paletteItems: PaletteItem[] = [
  { type: 'kpi', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><text x="12" y="14" text-anchor="middle" font-size="10" fill="currentColor" stroke="none">KPI</text></svg>', labelKey: 'insight.component.kpi' },
  { type: 'chart', chartType: 'line', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18L8 12L13 15L21 7"/><circle cx="21" cy="7" r="1.5" fill="currentColor"/></svg>', labelKey: 'insight.component.line' },
  { type: 'chart', chartType: 'bar', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="4" height="10"/><rect x="10" y="6" width="4" height="14"/><rect x="16" y="14" width="4" height="6"/></svg>', labelKey: 'insight.component.bar' },
  { type: 'chart', chartType: 'pie', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 3A9 9 0 0 1 21 12L12 12Z" fill="currentColor" opacity="0.5"/></svg>', labelKey: 'insight.component.pie' },
  { type: 'chart', chartType: 'area', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18L8 12L13 15L21 7V18H3Z" fill="currentColor" opacity="0.3"/><path d="M3 18L8 12L13 15L21 7"/></svg>', labelKey: 'insight.component.area' },
  { type: 'chart', chartType: 'scatter', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="18" r="1.5" fill="currentColor"/><circle cx="10" cy="10" r="1.5" fill="currentColor"/><circle cx="14" cy="14" r="1.5" fill="currentColor"/><circle cx="18" cy="6" r="1.5" fill="currentColor"/></svg>', labelKey: 'insight.component.scatter' },
  { type: 'chart', chartType: 'radar', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12,2 22,8 18,20 6,20 2,8"/><circle cx="12" cy="12" r="3" fill="currentColor" opacity="0.3"/></svg>', labelKey: 'insight.component.radar' },
  { type: 'table', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><line x1="3" y1="10" x2="21" y2="10"/><line x1="9" y1="4" x2="9" y2="20"/><line x1="15" y1="4" x2="15" y2="20"/></svg>', labelKey: 'insight.component.table' },
  { type: 'filter', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="4,4 20,4 14,12 14,20 10,18 10,12"/></svg>', labelKey: 'insight.component.filter' },
  { type: 'timeFilter', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><polyline points="12,6 12,12 16,14"/></svg>', labelKey: 'insight.component.timeFilter' },
]

/** 拖拽开始时携带物料信息 */
function handleDragStart(event: DragEvent, item: PaletteItem): void {
  if (!event.dataTransfer) {
    return
  }
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/json', JSON.stringify(item))
}
</script>

<style scoped>
.component-palette {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
  overflow: hidden;
}

.palette-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.palette-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--theme-surface-elevated);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  cursor: grab;
  transition: all 0.15s ease;
  user-select: none;
}

.palette-item:hover {
  border-color: var(--main-orange);
  background: var(--theme-surface-hover);
}

.palette-item:active {
  cursor: grabbing;
}

.palette-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-text-secondary);
  flex-shrink: 0;
}

.palette-icon svg {
  width: 100%;
  height: 100%;
}

.palette-label {
  font-size: 13px;
  color: var(--theme-text);
}
</style>
