<template>
  <div ref="rootRef" class="kpi-card-widget">
    <div class="kpi-card-inner">
      <div v-if="(showTitle !== false && component.titleBarStyle !== 'hidden') || showTimeFilter" class="kpi-card-header" :class="`title-bar-${props.component.titleBarStyle ?? 'standard'}`">
        <div class="kpi-title-row">
          <span v-if="showTitle !== false && component.titleBarStyle !== 'hidden'" class="kpi-header-title"><DashboardComponentIcon type="kpi" :dashboard-theme="dashboardTheme" :title-icon-style="component.titleIconStyle" />{{ component.title }}</span>
        </div>
        <div v-if="showTimeFilter" class="kpi-time-filter">
          <el-date-picker
            v-model="localDateRange"
            type="daterange"
            size="small"
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
      <div v-if="hasTabs" class="widget-tabs" role="tablist" :aria-label="t('insight.kpiTabsLabel')">
        <div
          v-for="tab in tabList"
          :key="tab.id"
          class="widget-tab"
          :class="{ active: activeTabId === tab.id }"
          role="tab"
          :aria-selected="activeTabId === tab.id"
          :tabindex="activeTabId === tab.id || (!activeTabId && tab.id === tabList[0]?.id) ? 0 : -1"
          :data-tab-id="tab.id"
          @click="selectTab(tab.id)"
          @keydown="onTabKeydown($event, tab.id)"
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
          <div class="kpi-metric-name" :style="css(metric.styles.name, metricVisual(metric).textColors.name)">
            <el-icon v-if="metricIcon(metric)" class="kpi-metric-icon" :style="{ color: metricVisual(metric).accentColor }" aria-hidden="true"><component :is="metricIcon(metric)" /></el-icon>
            <span>{{ metric.displayName }}</span>
          </div>
          <div class="kpi-metric-valuerow">
            <span class="kpi-metric-value" :style="css(metric.styles.value, metricVisual(metric).textColors.value)">{{ metricValue(metric) }}</span>
            <span v-if="metric.unit" class="kpi-metric-unit" :style="css(metric.styles.unit, metricVisual(metric).textColors.unit)">{{ metric.unit }}</span>
          </div>
          <div v-if="metric.helperText" class="kpi-metric-helper" :style="css(metric.styles.helper, metricVisual(metric).textColors.helper)">{{ metric.helperText }}</div>

          <button
            v-if="editable"
            class="kpi-metric-style-btn"
            :title="t('insight.kpiMetricStyle')"
            @mousedown.stop.prevent
            @click.stop="openMetricStyle(metric)"
          >
            <el-icon :size="12"><MoreFilled /></el-icon>
          </button>

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
        <div v-if="component.titleBarStyle !== 'hidden' && activeKpiData?.name" class="kpi-name">{{ activeKpiData.name }}</div>
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
import { ArrowUp, ArrowDown, MoreFilled } from '@element-plus/icons-vue'
import type { InsightComponent, InsightComponentData, KpiItemData, KpiMetricConfig, TimeRangeValue, ComponentTab, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import { resolveMetricVisual, styleToCss, type KpiMetricField } from '@/utils/kpi-metrics'
import { resolveDashboardIcon } from '@/utils/dashboard-icon-registry'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'
import { useFreeInteraction } from '../composables/useFreeInteraction'
import { useTabKeyboard } from '../composables/useTabKeyboard'

defineOptions({
  name: 'KpiCardWidget',
})

const { t } = useI18n()

const props = withDefaults(defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否编辑态（编辑态下指标可拖拽 / 缩放 / 打开样式弹窗） */
  editable?: boolean
  /** 是否由组件内部显示标题；画布编辑态由统一标题栏显示 */
  showTitle?: boolean
  /** 仪表盘解析后的主题；未传时保持旧卡片视觉 */
  dashboardTheme?: ResolvedDashboardTheme
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
const rootRef = ref<HTMLElement | null>(null)

function selectTab(tabId: string): void {
  activeTabId.value = tabId
}

/** 页签键盘导航（WAI-ARIA tabs 模式，逻辑抽到 composable 与组合卡片复用） */
const { onTabKeydown } = useTabKeyboard(
  () => tabList.value,
  selectTab,
  (id) => rootRef.value?.querySelector(`[role="tab"][data-tab-id="${id}"]`) ?? null,
)

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

const dashboardTheme = computed(() => props.dashboardTheme ?? resolveDashboardTheme(undefined, 'light'))

function metricVisual(metric: KpiMetricConfig, index?: number) {
  const metricIndex = index ?? (props.component.kpiMetrics ?? []).findIndex((item) => item.fieldKey === metric.fieldKey)
  return resolveMetricVisual(metric, undefined, dashboardTheme.value, Math.max(0, metricIndex))
}

function metricIcon(metric: KpiMetricConfig) {
  return resolveDashboardIcon(metricVisual(metric).iconKey)
}

function css(style: KpiMetricConfig['styles']['name'], themeColor?: string): string {
  return styleToCss(style, themeColor)
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

// ── 指标自由拖动/八向缩放（共享交互内核）──────────────────
// 与组合卡片同策略：mousedown 阻断冒泡（否则触发 GridItem 的容器拖拽）+
// 过程只改 DOM style 并 rAF 节流，mouseup 由 useFreeInteraction 用「最后一次鼠标
// 坐标 + 纯函数」重算落点提交 —— 禁止回读 DOM style，否则取消 pending rAF 后
// 会丢掉末帧位移（快速甩动时指标差一点没到位）。
// 元素引用直接来自 e.currentTarget / closest，避免全局 querySelector 跨卡串元素。
const interaction = useFreeInteraction()

/** 把计算出的 CSS 属性写到元素内联样式（过程渲染与落点同步共用） */
function applyCss(el: HTMLElement | null, props: Record<string, string>): void {
  if (!el) return
  for (const [k, v] of Object.entries(props)) el.style.setProperty(k, v)
}

/** 指标分组容器的边界快照（宽/高） */
interface MetricBounds { w: number; h: number }

/**
 * 读一次指标分组容器尺寸。
 *
 * ⚠️ **只允许在鼠标按下时调用一次，绝不要在 `compute` 里每帧调用**：
 * `getBoundingClientRect()` / `offsetWidth` 会强制浏览器立即同步布局（forced reflow），
 * 而 rAF 回调里「先读布局 → 再写 left/top」会让每一帧都被迫完整重排，
 * 卡片内指标多、同页图表多时就会把帧预算吃满，表现为**拖动发涩、跟手迟滞**。
 * 本次拖动/缩放期间容器尺寸不会变化，按下时取一次即可（过程与落点共用同一份）。
 */
function readGroupBounds(): MetricBounds | null {
  const r = groupRef.value?.getBoundingClientRect()
  return r ? { w: r.width, h: r.height } : null
}

function onMetricMouseDown(e: MouseEvent, metric: KpiMetricConfig): void {
  if (!props.editable) return
  const target = e.target as HTMLElement
  if (target.closest('.kpi-metric-style-btn, .kpi-rs, button, input, select, textarea')) return
  e.preventDefault()
  e.stopPropagation()
  selectMetric(metric.fieldKey)
  const el = e.currentTarget as HTMLElement | null
  // 按下时读一次布局，拖动过程与落点提交共用（详见 readGroupBounds 注释）
  const bounds = readGroupBounds()
  const elW = el?.offsetWidth ?? 0
  const elH = el?.offsetHeight ?? 0
  const start = { sx: e.clientX, sy: e.clientY, ox: metric.x, oy: metric.y }
  movingId.value = metric.fieldKey
  interaction.begin<Record<string, string>>({
    compute: (last) => {
      let nx = start.ox + last.x - start.sx
      let ny = start.oy + last.y - start.sy
      if (bounds) {
        nx = Math.max(0, Math.min(nx, Math.max(0, bounds.w - elW)))
        ny = Math.max(0, Math.min(ny, Math.max(0, bounds.h - elH)))
      }
      return { left: Math.round(nx) + 'px', top: Math.round(ny) + 'px' }
    },
    apply: (p) => applyCss(el, p),
    commit: (p) => {
      const m = (props.component.kpiMetrics ?? []).find((x) => x.fieldKey === metric.fieldKey)
      if (m) {
        m.x = parseInt(p.left, 10)
        m.y = parseInt(p.top, 10)
      }
      // 保留内联值而非 removeProperty：纯点击未拖动时，Vue 不会重发未变化的 style，
      // 移除内联值会瞬间丢失定位。
      applyCss(el, p)
    },
  })
}

/** 缩放起点 + 当前鼠标坐标 → 目标盒子（过程与落点共用同一纯函数） */
function computeMetricResize(
  start: { dir: string; sx: number; sy: number; ox: number; oy: number; ow: number; oh: number },
  last: { x: number; y: number },
  bounds: MetricBounds | null,
): Record<string, string> {
  const dx = last.x - start.sx
  const dy = last.y - start.sy
  const dir = start.dir
  let ox = start.ox, oy = start.oy, ow = start.ow, oh = start.oh
  if (dir.includes('e')) ow = start.ow + dx
  if (dir.includes('s')) oh = start.oh + dy
  if (dir.includes('w')) { ow = start.ow - dx; ox = start.ox + dx }
  if (dir.includes('n')) { oh = start.oh - dy; oy = start.oy + dy }
  if (ow < 120) { if (dir.includes('w')) ox -= 120 - ow; ow = 120 }
  if (oh < 56) { if (dir.includes('n')) oy -= 56 - oh; oh = 56 }
  if (ox < 0) { ow += ox; ox = 0 }
  if (oy < 0) { oh += oy; oy = 0 }
  if (bounds) {
    if (ox + ow > bounds.w) ow = bounds.w - ox
    if (oy + oh > bounds.h) oh = bounds.h - oy
  }
  return {
    left: Math.round(ox) + 'px',
    top: Math.round(oy) + 'px',
    width: Math.round(ow) + 'px',
    height: Math.round(oh) + 'px',
  }
}

function onMetricResizeDown(e: MouseEvent, metric: KpiMetricConfig, dir: string): void {
  if (!props.editable) return
  e.preventDefault()
  e.stopPropagation()
  selectMetric(metric.fieldKey)
  const el = (e.currentTarget as HTMLElement).closest('.kpi-metric') as HTMLElement | null
  // 同上：按下时读一次布局，缩放过程与落点提交共用
  const bounds = readGroupBounds()
  const start = {
    dir,
    sx: e.clientX,
    sy: e.clientY,
    ox: metric.x,
    oy: metric.y,
    ow: metric.w,
    oh: metric.h,
  }
  resizingId.value = metric.fieldKey
  interaction.begin<Record<string, string>>({
    compute: (last) => computeMetricResize(start, last, bounds),
    apply: (p) => applyCss(el, p),
    commit: (p) => {
      const m = (props.component.kpiMetrics ?? []).find((x) => x.fieldKey === metric.fieldKey)
      if (m) {
        m.x = parseInt(p.left, 10)
        m.y = parseInt(p.top, 10)
        m.w = parseInt(p.width, 10)
        m.h = parseInt(p.height, 10)
      }
      applyCss(el, p)
    },
  })
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

/* 时间筛选宽度统一走类规则（替代原行内 style="width: 200px"） */
.kpi-time-filter :deep(.el-date-editor) {
  width: 200px;
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
  color: var(--db-up);
  background: var(--db-up-bg);
}

.kpi-chg.down {
  color: var(--db-down);
  background: var(--db-down-bg);
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

/* 数值排版：等宽数字，拖拽/缩放过程中宽度不抖动 */
.kpi-metric-value {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
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

/* 拖动/缩放期间：cursor 提示 + **禁用 hover 过渡**。
   指针在拖动中会扫过其它指标，每一次 :hover 进出都会重新启动 background / box-shadow
   的 0.15s 过渡，指标多时是可见的重绘抖动源 —— 交互期间直接关掉过渡最省。 */
.kpi-metric-group.interacting .kpi-metric {
  cursor: grabbing;
  transition: none;
}

/* 八向缩放手柄（::after 扩大触达热区，视觉尺寸不变） */
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
.kpi-rs::after {
  content: '';
  position: absolute;
  inset: -5px;
  border-radius: 50%;
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
    width: 100%;
  }
}
</style>
