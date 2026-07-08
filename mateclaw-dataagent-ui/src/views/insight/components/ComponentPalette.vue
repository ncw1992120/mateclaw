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
        <span class="palette-icon">{{ item.icon }}</span>
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

/** 物料列表 */
const paletteItems: PaletteItem[] = [
  { type: 'kpi', icon: '🎴', labelKey: 'insight.component.kpi' },
  { type: 'chart', chartType: 'line', icon: '📈', labelKey: 'insight.component.line' },
  { type: 'chart', chartType: 'bar', icon: '📊', labelKey: 'insight.component.bar' },
  { type: 'chart', chartType: 'pie', icon: '🥧', labelKey: 'insight.component.pie' },
  { type: 'chart', chartType: 'area', icon: '🏔️', labelKey: 'insight.component.area' },
  { type: 'chart', chartType: 'scatter', icon: '⚫', labelKey: 'insight.component.scatter' },
  { type: 'chart', chartType: 'radar', icon: '🕸️', labelKey: 'insight.component.radar' },
  { type: 'table', icon: '📋', labelKey: 'insight.component.table' },
  { type: 'filter', icon: '🔧', labelKey: 'insight.component.filter' },
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
  font-size: 18px;
  line-height: 1;
}

.palette-label {
  font-size: 13px;
  color: var(--theme-text);
}
</style>
