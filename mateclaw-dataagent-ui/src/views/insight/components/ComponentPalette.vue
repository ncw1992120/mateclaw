<template>
  <div class="component-palette">
    <div class="palette-header">
      <span>{{ t('insight.paletteTitle') }}</span>
      <button type="button" class="palette-collapse-btn" title="收起面板" @click="emit('collapse')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="11 17 6 12 11 7"/><polyline points="18 17 13 12 18 7"/></svg>
      </button>
    </div>
    <div class="palette-list">
      <div v-for="group in groups" :key="group.id" class="palette-group">
        <button
          type="button"
          class="palette-group-header"
          :aria-expanded="!collapsed[group.id]"
          @click="toggleGroup(group.id)"
        >
          <span class="palette-group-title">{{ t(group.labelKey) }}</span>
          <span class="palette-group-count">{{ group.items.length }}</span>
          <svg
            class="palette-group-arrow"
            :class="{ collapsed: collapsed[group.id] }"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>
        <div v-show="!collapsed[group.id]" class="palette-group-body">
          <div
            v-for="item in group.items"
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponentType, ChartType } from '@/types'

defineOptions({
  name: 'ComponentPalette',
})

const emit = defineEmits<{
  (e: 'collapse'): void
}>()

const { t } = useI18n()

/** 物料项定义 */
interface PaletteItem {
  type: InsightComponentType
  chartType?: ChartType
  icon: string
  labelKey: string
}

/** 分组定义 */
interface PaletteGroup {
  id: string
  labelKey: string
  items: PaletteItem[]
}

// ─── SVG 图标常量 ──────────────────────────────────────
const ICONS = {
  kpi: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><text x="12" y="14" text-anchor="middle" font-size="10" fill="currentColor" stroke="none">KPI</text></svg>',
  line: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18L8 12L13 15L21 7"/><circle cx="21" cy="7" r="1.5" fill="currentColor"/></svg>',
  bar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="4" height="10"/><rect x="10" y="6" width="4" height="14"/><rect x="16" y="14" width="4" height="6"/></svg>',
  pie: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 3A9 9 0 0 1 21 12L12 12Z" fill="currentColor" opacity="0.5"/></svg>',
  area: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 18L8 12L13 15L21 7V18H3Z" fill="currentColor" opacity="0.3"/><path d="M3 18L8 12L13 15L21 7"/></svg>',
  scatter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="18" r="1.5" fill="currentColor"/><circle cx="10" cy="10" r="1.5" fill="currentColor"/><circle cx="14" cy="14" r="1.5" fill="currentColor"/><circle cx="18" cy="6" r="1.5" fill="currentColor"/></svg>',
  effectScatter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="16" r="2" fill="currentColor" opacity="0.3"/><circle cx="8" cy="16" r="1.5" fill="currentColor"/><circle cx="16" cy="8" r="3" fill="currentColor" opacity="0.2"/><circle cx="16" cy="8" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="2.5" fill="currentColor" opacity="0.2"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/></svg>',
  candlestick: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="6" y1="4" x2="6" y2="8"/><rect x="4" y="8" width="4" height="6" fill="currentColor" opacity="0.3"/><line x1="6" y1="14" x2="6" y2="20"/><line x1="14" y1="3" x2="14" y2="9"/><rect x="12" y="9" width="4" height="5" fill="currentColor" opacity="0.3"/><line x1="14" y1="14" x2="14" y2="21"/></svg>',
  radar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12,2 22,8 18,20 6,20 2,8"/><circle cx="12" cy="12" r="3" fill="currentColor" opacity="0.3"/></svg>',
  heatmap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="6" height="6" fill="currentColor" opacity="0.2"/><rect x="9" y="3" width="6" height="6" fill="currentColor" opacity="0.5"/><rect x="15" y="3" width="6" height="6" fill="currentColor" opacity="0.8"/><rect x="3" y="9" width="6" height="6" fill="currentColor" opacity="0.5"/><rect x="9" y="9" width="6" height="6" fill="currentColor" opacity="0.8"/><rect x="15" y="9" width="6" height="6" fill="currentColor" opacity="0.2"/><rect x="3" y="15" width="6" height="6" fill="currentColor" opacity="0.8"/><rect x="9" y="15" width="6" height="6" fill="currentColor" opacity="0.2"/><rect x="15" y="15" width="6" height="6" fill="currentColor" opacity="0.5"/></svg>',
  boxplot: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="6" y1="3" x2="6" y2="8"/><rect x="3" y="8" width="6" height="8" fill="currentColor" opacity="0.3"/><line x1="3" y1="12" x2="9" y2="12"/><line x1="6" y1="16" x2="6" y2="21"/><line x1="18" y1="4" x2="18" y2="9"/><rect x="15" y="9" width="6" height="7" fill="currentColor" opacity="0.3"/><line x1="15" y1="13" x2="21" y2="13"/><line x1="18" y1="16" x2="18" y2="20"/></svg>',
  map: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z"/><line x1="9" y1="3" x2="9" y2="18"/><line x1="15" y1="6" x2="15" y2="21"/></svg>',
  lines: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6C8 4 8 10 12 8S16 2 21 4" stroke-dasharray="2 2"/><path d="M3 12C8 10 8 16 12 14S16 8 21 10" stroke-dasharray="2 2"/><path d="M3 18C8 16 8 22 12 20S16 14 21 16" stroke-dasharray="2 2"/></svg>',
  graph: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="6" r="2" fill="currentColor"/><circle cx="18" cy="6" r="2" fill="currentColor"/><circle cx="12" cy="14" r="2" fill="currentColor"/><circle cx="6" cy="20" r="2" fill="currentColor"/><circle cx="18" cy="20" r="2" fill="currentColor"/><line x1="6" y1="6" x2="12" y2="14"/><line x1="18" y1="6" x2="12" y2="14"/><line x1="12" y1="14" x2="6" y2="20"/><line x1="12" y1="14" x2="18" y2="20"/></svg>',
  tree: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="4" r="2" fill="currentColor"/><line x1="12" y1="6" x2="6" y2="12"/><line x1="12" y1="6" x2="18" y2="12"/><circle cx="6" cy="14" r="2" fill="currentColor"/><circle cx="18" cy="14" r="2" fill="currentColor"/><line x1="6" y1="16" x2="4" y2="20"/><line x1="6" y1="16" x2="8" y2="20"/><line x1="18" y1="16" x2="16" y2="20"/><line x1="18" y1="16" x2="20" y2="20"/></svg>',
  treemap: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="12" height="12" fill="currentColor" opacity="0.2"/><rect x="15" y="3" width="6" height="6" fill="currentColor" opacity="0.4"/><rect x="15" y="9" width="6" height="6" fill="currentColor" opacity="0.3"/><rect x="3" y="15" width="8" height="6" fill="currentColor" opacity="0.5"/><rect x="11" y="15" width="10" height="6" fill="currentColor" opacity="0.3"/></svg>',
  sunburst: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3" fill="currentColor" opacity="0.5"/><path d="M12 3A9 9 0 0 1 21 12L15 12A3 3 0 0 0 12 9Z" fill="currentColor" opacity="0.2"/><path d="M21 12A9 9 0 0 1 12 21L12 15A3 3 0 0 0 15 12Z" fill="currentColor" opacity="0.3"/><path d="M12 21A9 9 0 0 1 3 12L9 12A3 3 0 0 0 12 15Z" fill="currentColor" opacity="0.2"/><path d="M3 12A9 9 0 0 1 12 3L12 9A3 3 0 0 0 9 12Z" fill="currentColor" opacity="0.4"/></svg>',
  parallel: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="3" x2="4" y2="21"/><line x1="10" y1="3" x2="10" y2="21"/><line x1="16" y1="3" x2="16" y2="21"/><line x1="22" y1="3" x2="22" y2="21"/><path d="M4 8L10 14L16 6L22 16"/></svg>',
  gauge: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 18A8 8 0 0 1 20 18"/><line x1="12" y1="18" x2="17" y2="10"/><circle cx="12" cy="18" r="1.5" fill="currentColor"/></svg>',
  funnel: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="4,4 20,4 16,12 16,20 8,20 8,12"/></svg>',
  sankey: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6C8 6 8 10 12 10S16 6 21 6" fill="currentColor" opacity="0.2"/><path d="M3 12C8 12 8 16 12 16S16 12 21 12" fill="currentColor" opacity="0.3"/><path d="M3 6C8 6 8 12 12 12S16 18 21 18" fill="currentColor" opacity="0.2"/></svg>',
  themeRiver: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12C6 8 9 16 12 12S18 8 21 12" fill="currentColor" opacity="0.2"/><path d="M3 14C6 10 9 18 12 14S18 10 21 14" fill="currentColor" opacity="0.3"/><path d="M3 10C6 6 9 14 12 10S18 6 21 10" fill="currentColor" opacity="0.2"/></svg>',
  pictorialBar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="14" width="4" height="2" fill="currentColor"/><rect x="4" y="11" width="4" height="2" fill="currentColor"/><rect x="4" y="8" width="4" height="2" fill="currentColor"/><rect x="10" y="12" width="4" height="2" fill="currentColor"/><rect x="10" y="9" width="4" height="2" fill="currentColor"/><rect x="10" y="6" width="4" height="2" fill="currentColor"/><rect x="16" y="16" width="4" height="2" fill="currentColor"/><rect x="16" y="13" width="4" height="2" fill="currentColor"/></svg>',
  table: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><line x1="3" y1="10" x2="21" y2="10"/><line x1="9" y1="4" x2="9" y2="20"/><line x1="15" y1="4" x2="15" y2="20"/></svg>',
  filter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="4,4 20,4 14,12 14,20 10,18 10,12"/></svg>',
  timeFilter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><polyline points="12,6 12,12 16,14"/></svg>',
  aiAnalysis: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>',
} as const

/** 按语义分组的物料列表 */
const groups: PaletteGroup[] = [
  {
    id: 'basic',
    labelKey: 'insight.paletteGroup.basic',
    items: [
      { type: 'kpi', icon: ICONS.kpi, labelKey: 'insight.component.kpi' },
      { type: 'table', icon: ICONS.table, labelKey: 'insight.component.table' },
      { type: 'filter', icon: ICONS.filter, labelKey: 'insight.component.filter' },
      { type: 'timeFilter', icon: ICONS.timeFilter, labelKey: 'insight.component.timeFilter' },
      { type: 'aiAnalysis', icon: ICONS.aiAnalysis, labelKey: 'insight.component.aiAnalysis' },
    ],
  },
  {
    id: 'common-chart',
    labelKey: 'insight.paletteGroup.commonChart',
    items: [
      { type: 'chart', chartType: 'line', icon: ICONS.line, labelKey: 'insight.component.line' },
      { type: 'chart', chartType: 'bar', icon: ICONS.bar, labelKey: 'insight.component.bar' },
      { type: 'chart', chartType: 'pie', icon: ICONS.pie, labelKey: 'insight.component.pie' },
      { type: 'chart', chartType: 'area', icon: ICONS.area, labelKey: 'insight.component.area' },
      { type: 'chart', chartType: 'scatter', icon: ICONS.scatter, labelKey: 'insight.component.scatter' },
    ],
  },
  {
    id: 'advanced-chart',
    labelKey: 'insight.paletteGroup.advancedChart',
    items: [
      { type: 'chart', chartType: 'radar', icon: ICONS.radar, labelKey: 'insight.component.radar' },
      { type: 'chart', chartType: 'heatmap', icon: ICONS.heatmap, labelKey: 'insight.component.heatmap' },
      { type: 'chart', chartType: 'boxplot', icon: ICONS.boxplot, labelKey: 'insight.component.boxplot' },
      { type: 'chart', chartType: 'map', icon: ICONS.map, labelKey: 'insight.component.map' },
      { type: 'chart', chartType: 'lines', icon: ICONS.lines, labelKey: 'insight.component.lines' },
      { type: 'chart', chartType: 'graph', icon: ICONS.graph, labelKey: 'insight.component.graph' },
      { type: 'chart', chartType: 'tree', icon: ICONS.tree, labelKey: 'insight.component.tree' },
      { type: 'chart', chartType: 'treemap', icon: ICONS.treemap, labelKey: 'insight.component.treemap' },
      { type: 'chart', chartType: 'sunburst', icon: ICONS.sunburst, labelKey: 'insight.component.sunburst' },
      { type: 'chart', chartType: 'parallel', icon: ICONS.parallel, labelKey: 'insight.component.parallel' },
      { type: 'chart', chartType: 'gauge', icon: ICONS.gauge, labelKey: 'insight.component.gauge' },
      { type: 'chart', chartType: 'funnel', icon: ICONS.funnel, labelKey: 'insight.component.funnel' },
      { type: 'chart', chartType: 'sankey', icon: ICONS.sankey, labelKey: 'insight.component.sankey' },
      { type: 'chart', chartType: 'themeRiver', icon: ICONS.themeRiver, labelKey: 'insight.component.themeRiver' },
      { type: 'chart', chartType: 'pictorialBar', icon: ICONS.pictorialBar, labelKey: 'insight.component.pictorialBar' },
      { type: 'chart', chartType: 'candlestick', icon: ICONS.candlestick, labelKey: 'insight.component.candlestick' },
      { type: 'chart', chartType: 'effectScatter', icon: ICONS.effectScatter, labelKey: 'insight.component.effectScatter' },
    ],
  },
]

/** 折叠状态（默认全部展开） */
const collapsed = reactive<Record<string, boolean>>({})

function toggleGroup(id: string): void {
  collapsed[id] = !collapsed[id]
}

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
  background: var(--db-card);
  border-right: 1px solid var(--db-border);
  overflow: hidden;
}

.palette-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--db-text-secondary);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.palette-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--db-text-muted);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.palette-collapse-btn:hover {
  color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}

.palette-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ─── 分组 ────────────────────────────────────── */
.palette-group {
  border-radius: var(--radius-md);
  overflow: hidden;
  flex-shrink: 0;
}

.palette-group-header {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  background: transparent;
  border: none;
  cursor: pointer;
  gap: 6px;
  transition: background var(--transition-fast);
}

.palette-group-header:hover {
  background: var(--db-hover);
}

.palette-group-title {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-secondary);
  text-align: left;
  letter-spacing: 0.03em;
}

.palette-group-count {
  font-size: 11px;
  color: var(--db-text-tertiary);
  background: var(--db-hover);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 18px;
  min-width: 18px;
  text-align: center;
}

.palette-group-arrow {
  width: 14px;
  height: 14px;
  color: var(--db-text-tertiary);
  transition: transform var(--transition-fast);
  flex-shrink: 0;
}

.palette-group-arrow.collapsed {
  transform: rotate(-90deg);
}

.palette-group-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0 6px 0;
}

/* ─── 物料项 ──────────────────────────────────── */
.palette-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin: 0 4px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: grab;
  transition: all var(--transition-fast);
  user-select: none;
}

.palette-item:hover {
  background: var(--db-hover);
  border-color: var(--db-border);
}

.palette-item:active {
  cursor: grabbing;
  background: var(--db-accent-light, var(--db-hover));
}

.palette-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--db-text-secondary);
  flex-shrink: 0;
}

.palette-icon svg {
  width: 100%;
  height: 100%;
}

.palette-label {
  font-size: 13px;
  color: var(--db-text);
}
</style>
