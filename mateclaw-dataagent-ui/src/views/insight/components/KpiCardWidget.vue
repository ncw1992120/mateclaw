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
      <div v-if="hasTabs" class="widget-tabs" role="tablist" aria-label="指标卡分页">
        <div
          v-for="tab in tabList"
          :key="tab.id"
          class="widget-tab"
          :class="{ active: activeTabId === tab.id }"
          role="tab"
          :aria-selected="activeTabId === tab.id"
          :tabindex="activeTabId === tab.id ? 0 : -1"
          :data-tab-id="tab.id"
          @click="selectTab(tab.id)"
          @keydown="handleTabKeydown($event, tab.id)"
        >
          {{ tab.title }}
        </div>
      </div>

      <!-- 指标分组（自由布局）：由结果集字段逐列投影，指标可拖拽定位 + 8 向缩放 -->
      <div v-if="hasMetricGroup" ref="groupRef" class="kpi-metric-group" :class="{ editing: editable, interacting: movingId !== null || resizingId !== null }">
        <div
          v-for="metric in visibleMetrics"
          :key="metric.fieldKey"
          class="kpi-metric"
          :class="{ selected: selectedKey === metric.fieldKey, moving: movingId === metric.fieldKey }"
          :style="metricStyle(metric)"
          :data-metric="metric.fieldKey"
          @mousedown="onMetricMouseDown($event, metric)"
          @click.stop="selectMetric(metric.fieldKey)"
          @dragstart.stop.prevent
        >
          <div class="kpi-metric-name" :style="css(metric.styles.name)">{{ metric.displayName }}</div>
          <div class="kpi-metric-valuerow">
            <span class="kpi-metric-value" :style="css(metric.styles.value)">{{ metricValue(metric) }}</span>
            <span v-if="metric.unit" class="kpi-metric-unit" :style="css(metric.styles.unit)">{{ metric.unit }}</span>
          </div>
          <div v-if="metric.helperText" class="kpi-metric-helper" :style="css(metric.styles.helper)">{{ metric.helperText }}</div>

          <button
            v-if="editable"
            class="kpi-metric-style-btn"
            title="配置字段样式"
            @mousedown.stop.prevent
            @click.stop="openMetricStyle(metric)"
          >:</button>

          <!-- 八向缩放手柄（编辑态 + 选中/悬停/拖动时显示） -->
          <template v-if="editable && (selectedKey === metric.fieldKey || hoverKey === metric.fieldKey || resizingId === metric.fieldKey || movingId === metric.fieldKey)">
            <span
              v-for="dir in RESIZE_DIRS"
              :key="dir"
              class="kpi-rs"
              :class="dir"
              @mousedown.stop.prevent="onMetricResizeDown($event, metric, dir)"
              @dragstart.stop.prevent
            />
          </template>
        </div>

        <div v-if="!visibleMetrics.length" class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
      </div>

      <!-- 多指标模式（旧行为：响应式等分列） -->
      <div v-else-if="isMultiKpi" class="kpi-multi-body">
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
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import type { InsightComponent, InsightComponentData, KpiItemData, KpiMetricConfig, TimeRangeValue, ComponentTab } from '@/types'
import { styleToCss, type KpiMetricField } from '@/utils/kpi-metrics'

defineOptions({
  name: 'KpiCardWidget',
})

const { t } = useI18n()

const props = withDefaults(defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否显示标题 */
  showTitle?: boolean
  /** 是否编辑态（编辑态下指标可拖拽 / 缩放 / 打开样式弹窗） */
  editable?: boolean
}>(), {
  editable: false,
})

const emit = defineEmits<{
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
  /** 打开某指标的字段样式弹窗（默认定位到「指标值」字段） */
  (e: 'open-metric-style', payload: { componentId: string; fieldKey: string; field: KpiMetricField }): void
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

function selectTab(tabId: string): void {
  activeTabId.value = tabId
}

function handleTabKeydown(event: KeyboardEvent, tabId: string): void {
  const currentIndex = tabList.value.findIndex(tab => tab.id === tabId)
  if (currentIndex < 0 || tabList.value.length < 2) return
  let nextIndex = currentIndex
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % tabList.value.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + tabList.value.length) % tabList.value.length
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = tabList.value.length - 1
  else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectTab(tabId); return }
  else return
  event.preventDefault()
  const nextTab = tabList.value[nextIndex]
  if (!nextTab) return
  selectTab(nextTab.id)
  nextTick(() => {
    const tab = Array.from(document.querySelectorAll<HTMLElement>('.kpi-card-widget [role="tab"]'))
      .find(candidate => candidate.dataset.tabId === nextTab.id)
    tab?.focus()
  })
}

// Tab 定义替换时校正失效的 activeTabId，避免组件进入无数据空态。
watch(() => tabList.value.map(tab => tab.id).join('|'), () => {
  if (!hasTabs.value) {
    activeTabId.value = ''
    return
  }
  if (!tabList.value.some(tab => tab.id === activeTabId.value)) {
    activeTabId.value = tabList.value[0]?.id ?? ''
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

/* ── 指标分组（自由布局）────────────────────────────────── */

const RESIZE_DIRS = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const

const groupRef = ref<HTMLElement | null>(null)
const selectedKey = ref<string | null>(null)
const hoverKey = ref<string | null>(null)
const movingId = ref<string | null>(null)
const resizingId = ref<string | null>(null)

/** 组件是否启用了「指标分组」形态（存在 kpiMetrics 即启用，向后兼容旧卡） */
const hasMetricGroup = computed(() => Array.isArray(props.component.kpiMetrics) && props.component.kpiMetrics.length > 0)

/** 需要展示的指标（按配置顺序，隐藏项不渲染） */
const visibleMetrics = computed<KpiMetricConfig[]>(() => (props.component.kpiMetrics ?? []).filter((m) => m.visible !== false))

/** 当前生效的指标值列表（Tab 模式跟随 Tab） */
const effectiveKpiList = computed<KpiItemData[]>(() => {
  if (!hasTabs.value) return kpiListData.value ?? []
  if (!activeTabId.value) return []
  return props.componentData?.tabs?.[activeTabId.value]?.kpiList ?? []
})

/** 按 fieldKey 建立索引，兼容后端未下发 fieldKey 时按顺序对齐 */
const kpiListByField = computed<Record<string, KpiItemData>>(() => {
  const map: Record<string, KpiItemData> = {}
  effectiveKpiList.value.forEach((item) => {
    if (item?.fieldKey) map[item.fieldKey] = item
  })
  return map
})

const kpiListOrdered = computed<KpiItemData[]>(() => (props.component.kpiMetrics ?? []).map((metric, index) => {
  const byField = kpiListByField.value[metric.fieldKey]
  if (byField) return byField
  return effectiveKpiList.value[index] ?? null
}).filter((x): x is KpiItemData => !!x))

/** 取某指标的渲染值：优先按 fieldKey 命中，缺失时按下标对齐，最后回落 kpi */
function metricValue(metric: KpiMetricConfig): string {
  const byField = kpiListByField.value[metric.fieldKey]
  if (byField) return byField.value ?? '--'
  const index = (props.component.kpiMetrics ?? []).findIndex((m) => m.fieldKey === metric.fieldKey)
  const byIndex = effectiveKpiList.value[index]
  if (byIndex) return byIndex.value ?? '--'
  return kpiData.value?.value ?? '--'
}

function css(style: KpiMetricConfig['styles']['name']): string {
  return styleToCss(style)
}

function metricStyle(metric: KpiMetricConfig): Record<string, string> {
  return {
    position: 'absolute',
    left: metric.x + 'px',
    top: metric.y + 'px',
    width: metric.w + 'px',
    height: metric.h + 'px',
  }
}

function selectMetric(fieldKey: string): void {
  selectedKey.value = fieldKey
}

function openMetricStyle(metric: KpiMetricConfig): void {
  emit('open-metric-style', { componentId: props.component.id, fieldKey: metric.fieldKey, field: 'value' })
}

// ── 指标自由拖动（鼠标事件）──────────────────────────────
// 与组合卡片同策略：mousedown 阻断冒泡（否则触发 GridItem 的容器拖拽）+
// 过程只改 DOM style 并 rAF 节流，mouseup 才把最终位置提交回响应式 kpiMetrics。
let mv: { key: string; sx: number; sy: number; ox: number; oy: number; el: HTMLElement | null } | null = null
let mvRaf = 0
let mvLast: { x: number; y: number } | null = null

function onMetricMouseDown(e: MouseEvent, metric: KpiMetricConfig): void {
  if (!props.editable) return
  const target = e.target as HTMLElement
  if (target.closest('.kpi-metric-style-btn, .kpi-rs, button, input, select, textarea')) return
  e.preventDefault()
  e.stopPropagation()
  selectMetric(metric.fieldKey)
  mv = {
    key: metric.fieldKey,
    sx: e.clientX,
    sy: e.clientY,
    ox: metric.x,
    oy: metric.y,
    el: document.querySelector(`[data-metric="${metric.fieldKey}"]`) as HTMLElement | null,
  }
  movingId.value = metric.fieldKey
  window.addEventListener('mousemove', onMetricMouseMove)
  window.addEventListener('mouseup', onMetricMouseUp)
}

function onMetricMouseMove(e: MouseEvent): void {
  if (!mv) return
  mvLast = { x: e.clientX, y: e.clientY }
  if (mvRaf) return
  mvRaf = requestAnimationFrame(() => {
    mvRaf = 0
    if (!mv || !mvLast) return
    let nx = mv.ox + mvLast.x - mv.sx
    let ny = mv.oy + mvLast.y - mv.sy
    const rect = groupRef.value?.getBoundingClientRect()
    if (rect && mv.el) {
      nx = Math.max(0, Math.min(nx, Math.max(0, rect.width - mv.el.offsetWidth)))
      ny = Math.max(0, Math.min(ny, Math.max(0, rect.height - mv.el.offsetHeight)))
    }
    mv.el?.style.setProperty('left', Math.round(nx) + 'px')
    mv.el?.style.setProperty('top', Math.round(ny) + 'px')
  })
}

function onMetricMouseUp(): void {
  if (mvRaf) { cancelAnimationFrame(mvRaf); mvRaf = 0 }
  if (mv?.el) {
    const metric = (props.component.kpiMetrics ?? []).find((m) => m.fieldKey === mv!.key)
    const nx = parseInt(mv.el.style.left, 10)
    const ny = parseInt(mv.el.style.top, 10)
    if (metric) {
      if (Number.isFinite(nx)) metric.x = nx
      if (Number.isFinite(ny)) metric.y = ny
    }
    mv.el.style.removeProperty('left')
    mv.el.style.removeProperty('top')
  }
  mv = null
  movingId.value = null
  window.removeEventListener('mousemove', onMetricMouseMove)
  window.removeEventListener('mouseup', onMetricMouseUp)
}

// ── 指标八向缩放（同拖动策略）────────────────────────────
let rz: { key: string; dir: string; sx: number; sy: number; ox: number; oy: number; ow: number; oh: number; el: HTMLElement | null } | null = null
let rzRaf = 0
let rzLast: { x: number; y: number } | null = null

function onMetricResizeDown(e: MouseEvent, metric: KpiMetricConfig, dir: string): void {
  if (!props.editable) return
  e.preventDefault()
  e.stopPropagation()
  selectMetric(metric.fieldKey)
  rz = {
    key: metric.fieldKey,
    dir,
    sx: e.clientX,
    sy: e.clientY,
    ox: metric.x,
    oy: metric.y,
    ow: metric.w,
    oh: metric.h,
    el: document.querySelector(`[data-metric="${metric.fieldKey}"]`) as HTMLElement | null,
  }
  resizingId.value = metric.fieldKey
  window.addEventListener('mousemove', onMetricResizeMove)
  window.addEventListener('mouseup', onMetricResizeUp)
}

function onMetricResizeMove(e: MouseEvent): void {
  if (!rz) return
  rzLast = { x: e.clientX, y: e.clientY }
  if (rzRaf) return
  rzRaf = requestAnimationFrame(() => {
    rzRaf = 0
    if (!rz || !rzLast || !rz.el) return
    const dx = rzLast.x - rz.sx
    const dy = rzLast.y - rz.sy
    const dir = rz.dir
    let { ox, oy, ow, oh } = rz
    if (dir.includes('e')) ow = rz.ow + dx
    if (dir.includes('s')) oh = rz.oh + dy
    if (dir.includes('w')) { ow = rz.ow - dx; ox = rz.ox + dx }
    if (dir.includes('n')) { oh = rz.oh - dy; oy = rz.oy + dy }
    const rect = groupRef.value?.getBoundingClientRect()
    if (ow < 120) { if (dir.includes('w')) ox -= 120 - ow; ow = 120 }
    if (oh < 56) { if (dir.includes('n')) oy -= 56 - oh; oh = 56 }
    if (ox < 0) { ow += ox; ox = 0 }
    if (oy < 0) { oh += oy; oy = 0 }
    if (rect) {
      if (ox + ow > rect.width) ow = rect.width - ox
      if (oy + oh > rect.height) oh = rect.height - oy
    }
    rz.el.style.setProperty('left', Math.round(ox) + 'px')
    rz.el.style.setProperty('top', Math.round(oy) + 'px')
    rz.el.style.setProperty('width', Math.round(ow) + 'px')
    rz.el.style.setProperty('height', Math.round(oh) + 'px')
  })
}

function onMetricResizeUp(): void {
  if (rzRaf) { cancelAnimationFrame(rzRaf); rzRaf = 0 }
  if (rz?.el) {
    const metric = (props.component.kpiMetrics ?? []).find((m) => m.fieldKey === rz!.key)
    const nx = parseInt(rz.el.style.left, 10)
    const ny = parseInt(rz.el.style.top, 10)
    const nw = parseInt(rz.el.style.width, 10)
    const nh = parseInt(rz.el.style.height, 10)
    if (metric) {
      if (Number.isFinite(nx)) metric.x = nx
      if (Number.isFinite(ny)) metric.y = ny
      if (Number.isFinite(nw)) metric.w = nw
      if (Number.isFinite(nh)) metric.h = nh
    }
    ;['left', 'top', 'width', 'height'].forEach((p) => rz!.el!.style.removeProperty(p))
  }
  rz = null
  resizingId.value = null
  window.removeEventListener('mousemove', onMetricResizeMove)
  window.removeEventListener('mouseup', onMetricResizeUp)
}

/** 退出编辑态时清掉内部选中，避免高亮残留 */
watch(() => props.editable, (v) => {
  if (!v) {
    selectedKey.value = null
    hoverKey.value = null
  }
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

.kpi-metric-group .kpi-placeholder {
  position: absolute;
  left: 0;
  top: 0;
  padding: 8px;
}

.kpi-sub {
  font-size: 12px;
  color: var(--db-text-quaternary);
}

/* 多指标模式样式（旧行为） */
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

/* ── 指标分组（自由布局）────────────────────────────── */
.kpi-metric-group {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
}

.kpi-metric-group.editing {
  cursor: default;
  user-select: none;
  -webkit-user-select: none;
}

.kpi-metric {
  position: absolute;
  box-sizing: border-box;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: move;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  /* overflow 必须可见：八向缩放手柄探出边界，hidden 会裁掉可点击区域 */
  overflow: visible;
  transition: background 0.15s, box-shadow 0.15s;
}

.kpi-metric-name,
.kpi-metric-value,
.kpi-metric-unit,
.kpi-metric-helper {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}

.kpi-metric-valuerow {
  display: flex;
  align-items: baseline;
  gap: 3px;
  overflow: hidden;
}

.kpi-metric:hover {
  background: var(--db-hover);
}

.kpi-metric.selected {
  background: var(--db-hover);
  box-shadow: 0 0 0 2px var(--db-accent-light);
  z-index: 5;
}

.kpi-metric.moving { opacity: 0.85; }

.kpi-metric-style-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  z-index: 7;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 4px;
  background: var(--db-accent-light);
  color: var(--db-accent);
  font-weight: 700;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s;
}

.kpi-metric:hover .kpi-metric-style-btn,
.kpi-metric.selected .kpi-metric-style-btn { opacity: 1; }
.kpi-metric-style-btn:hover { background: var(--db-accent); color: #fff; }

.kpi-metric-group.interacting .kpi-metric { cursor: grabbing; }

/* 八向缩放手柄 */
.kpi-rs {
  position: absolute;
  width: 12px;
  height: 12px;
  background: var(--db-accent);
  border: 2px solid #fff;
  border-radius: 50%;
  z-index: 6;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .25);
}
.kpi-rs.nw { left: -6px; top: -6px; cursor: nwse-resize; }
.kpi-rs.n  { left: 50%; top: -6px; transform: translateX(-50%); cursor: ns-resize; }
.kpi-rs.ne { right: -6px; top: -6px; cursor: nesw-resize; }
.kpi-rs.e  { right: -6px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
.kpi-rs.se { right: -6px; bottom: -6px; cursor: nwse-resize; }
.kpi-rs.s  { left: 50%; bottom: -6px; transform: translateX(-50%); cursor: ns-resize; }
.kpi-rs.sw { left: -6px; bottom: -6px; cursor: nesw-resize; }
.kpi-rs.w  { left: -6px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }

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
