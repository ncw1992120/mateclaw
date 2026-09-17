<template>
  <div
    class="combination-card"
    :class="{ editing: editable, selected, 'cc-drop': editable && paletteOver }"
    :style="rootStyle"
    @click="onRootClick"
    @dragenter="onBodyDragEnter"
    @dragleave="onBodyDragLeave"
    @dragover.prevent="onBodyDragOver"
    @drop.stop.prevent="onBodyDrop"
  >
    <!-- 容器标题（cfg.showTitle） -->
    <div v-if="cfg.showTitle" class="cc-head">
      <span class="cc-title">{{ cfg.title || component.title }}</span>
    </div>

    <!-- 页签栏 -->
    <div v-if="cfg.tabs.length" class="cc-tabs">
      <div
        v-for="tab in cfg.tabs"
        :key="tab.id"
        class="cc-tab"
        :class="{ active: tab.id === cfg.activeTab }"
        @click.stop="selectTab(tab.id)"
      >
        <input
          v-if="editable && editingTab === tab.id"
          class="tab-edit"
          :value="tab.title"
          @click.stop
          @input="tab.title = ($event.target as HTMLInputElement).value"
          @blur="editingTab = null"
          @keyup.enter="editingTab = null"
        />
        <span v-else @dblclick.stop="editable && (editingTab = tab.id)">{{ tab.title }}</span>
        <button v-if="editable" class="tab-x" @click.stop="deleteTab(tab.id)" title="删除页签">✕</button>
      </div>
      <button v-if="editable" class="cc-tab-add" @click.stop="addTab" title="添加页签">+</button>
    </div>

    <!-- 主体：自由布局作为绝对定位参考系；整卡为拖入落区 -->
    <div ref="ccBodyRef" class="cc-body" :class="{ 'mode-free': cfg.layoutMode === 'free', 'mode-grid': cfg.layoutMode === 'grid', 'mode-vertical': cfg.layoutMode === 'vertical' }">
      <!-- 空状态 -->
      <div v-if="activeChildren.length === 0" class="cc-empty">
        <div class="empty-icon">🗂️</div>
        <div class="empty-text">{{ editable ? '从左侧组件库拖入子组件' : '暂无子组件' }}</div>
      </div>

      <div
        v-for="child in activeChildren"
        :key="child.id"
        class="cc-child"
        :class="[`mode-${cfg.layoutMode}`, { selected: selectedChildId === child.id, moving: movingId === child.id }]"
        :style="childStyle(child)"
        :data-child="child.id"
        @click.stop="selectChild(child.id)"
        @mouseenter="hoverChildId = child.id"
        @mouseleave="hoverChildId = null"
        @mousedown="onChildMouseDown($event, child)"
      >
        <div class="cc-child-head">
          <span class="cc-child-title">{{ child.title }}</span>
          <button v-if="editable" class="cc-child-del" @click.stop="deleteChild(child.id)" title="删除子组件">✕</button>
        </div>
        <!-- 子组件真实渲染（复用顶层 widget 组件；v1 子卡片数据接入下轮） -->
        <div class="cc-child-body">
          <KpiCardWidget
            v-if="child.type === 'kpi'"
            :component="toWidgetComponent(child)"
            :component-data="componentDataMap?.[child.id]"
            :show-title="false"
          />
          <ChartWidget
            v-else-if="child.type === 'chart'"
            :component="toWidgetComponent(child)"
            :component-data="componentDataMap?.[child.id]"
            :show-title="false"
          />
          <DataTableWidget
            v-else-if="child.type === 'table'"
            :component="toWidgetComponent(child)"
            :component-data="componentDataMap?.[child.id]"
            :show-title="false"
          />
          <FilterSelectWidget
            v-else-if="child.type === 'filter'"
            :component="toWidgetComponent(child)"
            :show-title="false"
          />
          <TimeFilterWidget
            v-else-if="child.type === 'timeFilter'"
            :component="toWidgetComponent(child)"
            :show-title="false"
          />
          <AiAnalysisWidget
            v-else-if="child.type === 'aiAnalysis'"
            :component="toWidgetComponent(child)"
            :component-data="componentDataMap?.[child.id]"
            :show-title="false"
          />
        </div>

        <!-- 八向缩放手柄（编辑态 + 选中/悬停/拖动时显示） -->
        <template v-if="editable && (selectedChildId === child.id || hoverChildId === child.id || resizingId === child.id || movingId === child.id) && cfg.layoutMode === 'free'">
          <span
            v-for="dir in ['nw','n','ne','e','se','s','sw','w']"
            :key="dir"
            class="rs"
            :class="dir"
            @mousedown.stop.prevent="onChildResizeDown($event, child, dir)"
            @dragstart.stop.prevent
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type {
  InsightComponent,
  InsightComponentType,
  InsightCombinationChild,
  InsightCombinationConfig,
  ChartType,
  InsightComponentData,
} from '@/types'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import FilterSelectWidget from './FilterSelectWidget.vue'
import TimeFilterWidget from './TimeFilterWidget.vue'
import AiAnalysisWidget from './AiAnalysisWidget.vue'

const props = withDefaults(
  defineProps<{
    component: InsightComponent
    componentDataMap?: Record<string, InsightComponentData>
    editable?: boolean
    selected?: boolean
  }>(),
  { editable: false, selected: false },
)

const { t } = useI18n()

const ccBodyRef = ref<HTMLElement | null>(null)
const selectedChildId = ref<string | null>(null)
const hoverChildId = ref<string | null>(null)
const movingId = ref<string | null>(null)
const resizingId = ref<string | null>(null)
const editingTab = ref<string | null>(null)

/** 兜底容器配置（防御性） */
function defaultConfig(): InsightCombinationConfig {
  return {
    title: '',
    showTitle: true,
    background: '#ffffff',
    radius: 12,
    padding: 16,
    layoutMode: 'free',
    tabs: [],
    activeTab: undefined,
    style: { border: { enabled: false, color: 'transparent' } },
  }
}
const cfg = computed<InsightCombinationConfig>(() => props.component.containerConfig ?? defaultConfig())

const rootStyle = computed<Record<string, string>>(() => ({
  background: cfg.value.background,
  borderRadius: cfg.value.radius + 'px',
  padding: cfg.value.padding + 'px',
  border: cfg.value.style.border.enabled ? `1px solid ${cfg.value.style.border.color}` : '1px solid transparent',
}))

/** 当前激活页签的子卡片数组（无页签则用 component.children） */
function getActiveChildren(): InsightCombinationChild[] {
  const c = props.component
  if (c.containerConfig?.tabs?.length && c.containerConfig.activeTab) {
    const tab = c.containerConfig.tabs.find((x) => x.id === c.containerConfig!.activeTab)
    if (tab) return tab.children
  }
  if (!c.children) c.children = []
  return c.children
}
const activeChildren = computed<InsightCombinationChild[]>(() => getActiveChildren())

/** 子卡片 → 顶层 widget 所需 InsightComponent（position 仅占位，不影响自由布局渲染） */
function toWidgetComponent(child: InsightCombinationChild): InsightComponent {
  return {
    id: child.id,
    type: child.type,
    title: child.title,
    position: { x: 0, y: 0, w: child.layout.col, h: child.layout.h ? Math.round(child.layout.h / 30) : 4 },
    chartType: child.chartType,
    config: child.config,
    dataSource: child.dataSource,
  }
}

/** 子卡片定位样式 */
function childStyle(child: InsightCombinationChild): Record<string, string> {
  if (cfg.value.layoutMode === 'free') {
    return {
      position: 'absolute',
      left: child.layout.x + 'px',
      top: child.layout.y + 'px',
      width: `calc(${child.layout.col} / 12 * 100%)`,
      ...(child.layout.h != null ? { height: child.layout.h + 'px' } : {}),
    }
  }
  if (cfg.value.layoutMode === 'grid') {
    return { gridColumn: `span ${child.layout.col}` }
  }
  return {}
}

// ── 拖拽添加子组件（整卡落区）────────────────────────────
// 注意：dragover 阶段浏览器禁止读取 dataTransfer 内容，故高亮在 dragenter/over 直接点亮，
// 真实类型在 drop 时解析（drop 阶段 getData 可用）。
const paletteOver = ref(false)
function onBodyDragEnter() { if (props.editable) paletteOver.value = true }
function onBodyDragLeave(e: DragEvent) {
  if (e.relatedTarget && !(e.currentTarget as HTMLElement).contains(e.relatedTarget as Node)) paletteOver.value = false
}
function onBodyDragOver(e: DragEvent) {
  if (props.editable) { e.preventDefault(); paletteOver.value = true }
}
function onBodyDrop(e: DragEvent) {
  if (!props.editable || !e.dataTransfer) return
  const raw = e.dataTransfer.getData('application/json')
  if (!raw) return
  try {
    const payload = JSON.parse(raw) as { type: InsightComponentType; chartType?: ChartType }
    addChild(payload.type, payload.chartType, dropPosFromEvent(e))
  } catch (err) {
    console.error('[CombinationCardWidget] drop parse error:', err)
  }
  paletteOver.value = false
}
function dropPosFromEvent(e: DragEvent): { x: number; y: number } {
  const rect = ccBodyRef.value?.getBoundingClientRect()
  if (!rect) return { x: 24, y: 24 }
  const x = Math.max(0, Math.min(e.clientX - rect.left, Math.max(0, rect.width - 200)))
  const y = Math.max(0, Math.min(e.clientY - rect.top, Math.max(0, rect.height - 90)))
  return { x: Math.round(x), y: Math.round(y) }
}

const TITLE_MAP: Record<string, string> = {
  kpi: t('insight.component.kpi'),
  'chart-line': t('insight.component.line'),
  'chart-bar': t('insight.component.bar'),
  'chart-pie': t('insight.component.pie'),
  'chart-area': t('insight.component.area'),
  'chart-scatter': t('insight.component.scatter'),
  'chart-radar': t('insight.component.radar'),
  table: t('insight.component.table'),
  filter: t('insight.component.filter'),
  timeFilter: t('insight.component.timeFilter'),
  aiAnalysis: t('insight.component.aiAnalysis'),
  combination: t('insight.component.combination'),
}
function defaultTitle(type: InsightComponentType, chartType?: ChartType): string {
  const key = type === 'chart' && chartType ? `chart-${chartType}` : type
  return TITLE_MAP[key] ?? type
}

function genId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

function addChild(type: InsightComponentType, chartType: ChartType | undefined, pos: { x: number; y: number }): void {
  const arr = getActiveChildren()
  const child: InsightCombinationChild = {
    id: genId('cc'),
    type,
    title: defaultTitle(type, chartType),
    chartType,
    config:
      type === 'timeFilter'
        ? { field: 'metric_time', availablePresets: ['today', '7d', '30d', '90d', 'custom'] }
        : type === 'aiAnalysis'
          ? { autoGenerate: false }
          : undefined,
    dataSource:
      type !== 'filter' && type !== 'timeFilter' && type !== 'aiAnalysis'
        ? { datasourceId: '', metrics: [], dimensions: [], filters: [], limit: 100 }
        : undefined,
    layout: {
      x: pos.x,
      y: pos.y,
      col: type === 'chart' || type === 'table' ? 7 : 6,
      h: type === 'kpi' ? 96 : type === 'aiAnalysis' ? 160 : 180,
    },
  }
  arr.push(child)
  selectedChildId.value = child.id
}

// ── 子组件自由拖动（鼠标事件，实时跟随）──────────────────
let mv: { id: string; sx: number; sy: number; ox: number; oy: number } | null = null
function onChildMouseDown(e: MouseEvent, child: InsightCombinationChild) {
  if (!props.editable) return
  const target = e.target as HTMLElement
  if (target.closest('.cc-child-del, .rs, button, input, select, textarea')) return
  selectChild(child.id)
  mv = { id: child.id, sx: e.clientX, sy: e.clientY, ox: child.layout.x, oy: child.layout.y }
  movingId.value = child.id
  window.addEventListener('mousemove', onChildMouseMove)
  window.addEventListener('mouseup', onChildMouseUp)
}
function onChildMouseMove(e: MouseEvent) {
  if (!mv) return
  const dx = e.clientX - mv.sx
  const dy = e.clientY - mv.sy
  const child = getActiveChildren().find((c) => c.id === mv!.id)
  if (!child) return
  const rect = ccBodyRef.value?.getBoundingClientRect()
  let nx = mv.ox + dx
  let ny = mv.oy + dy
  if (rect) {
    const el = document.querySelector(`[data-child="${mv.id}"]`) as HTMLElement | null
    const elW = el?.offsetWidth ?? 0
    const elH = el?.offsetHeight ?? 90
    nx = Math.max(0, Math.min(nx, Math.max(0, rect.width - elW)))
    ny = Math.max(0, Math.min(ny, Math.max(0, rect.height - elH)))
  }
  child.layout.x = Math.round(nx)
  child.layout.y = Math.round(ny)
}
function onChildMouseUp() {
  mv = null
  movingId.value = null
  window.removeEventListener('mousemove', onChildMouseMove)
  window.removeEventListener('mouseup', onChildMouseUp)
}

// ── 八向缩放 ───────────────────────────────────────────
let rz: { id: string; dir: string; sx: number; sy: number; ox: number; oy: number; ocol: number; oh: number } | null = null
function onChildResizeDown(e: MouseEvent, child: InsightCombinationChild, dir: string) {
  if (!props.editable) return
  selectChild(child.id)
  rz = { id: child.id, dir, sx: e.clientX, sy: e.clientY, ox: child.layout.x, oy: child.layout.y, ocol: child.layout.col, oh: child.layout.h ?? 120 }
  resizingId.value = child.id
  window.addEventListener('mousemove', onChildResizeMove)
  window.addEventListener('mouseup', onChildResizeUp)
}
function onChildResizeMove(e: MouseEvent) {
  if (!rz) return
  const child = getActiveChildren().find((c) => c.id === rz!.id)
  if (!child) return
  const dx = e.clientX - rz.sx
  const dy = e.clientY - rz.sy
  const rect = ccBodyRef.value?.getBoundingClientRect()
  const cols = 12
  const colW = rect ? rect.width / cols : 40
  const dh = 6 // 高度步进 px
  const dir = rz.dir
  let { ox, oy, ocol, oh } = rz
  if (dir.includes('e')) ocol = Math.max(1, Math.min(cols, rz.ocol + Math.round(dx / colW)))
  if (dir.includes('w')) {
    const dCol = Math.round(dx / colW)
    ocol = Math.max(1, Math.min(cols, rz.ocol - dCol))
    ox = rz.ox + dCol * colW
  }
  if (dir.includes('s')) oh = Math.max(60, rz.oh + dy)
  if (dir.includes('n')) {
    oh = Math.max(60, rz.oh - dy)
    oy = rz.oy + dy
  }
  // 边界保护
  if (ox < 0) ox = 0
  if (oy < 0) oy = 0
  if (rect) {
    if (ox > rect.width - colW) ox = rect.width - colW
    if (oy > rect.height - 60) oy = rect.height - 60
  }
  child.layout.col = ocol
  child.layout.x = Math.round(ox)
  child.layout.y = Math.round(oy)
  child.layout.h = Math.round(oh)
}
function onChildResizeUp() {
  rz = null
  resizingId.value = null
  window.removeEventListener('mousemove', onChildResizeMove)
  window.removeEventListener('mouseup', onChildResizeUp)
}

// ── 选中 / 删除 ───────────────────────────────────────
function selectChild(id: string) { selectedChildId.value = id }
function deleteChild(id: string) {
  const arr = getActiveChildren()
  const i = arr.findIndex((c) => c.id === id)
  if (i >= 0) arr.splice(i, 1)
  if (selectedChildId.value === id) selectedChildId.value = null
}
function onRootClick() {
  if (props.editable) selectedChildId.value = null
}

// ── 页签 ───────────────────────────────────────────────
function addTab() {
  const c = props.component
  if (!c.containerConfig) c.containerConfig = defaultConfig()
  const id = genId('tab')
  c.containerConfig.tabs.push({ id, title: `页签 ${c.containerConfig.tabs.length + 1}`, children: [] })
  c.containerConfig.activeTab = id
}
function deleteTab(id: string) {
  const c = props.component
  if (!c.containerConfig) return
  const i = c.containerConfig.tabs.findIndex((t) => t.id === id)
  if (i >= 0) c.containerConfig.tabs.splice(i, 1)
  if (c.containerConfig.activeTab === id) {
    c.containerConfig.activeTab = c.containerConfig.tabs[0]?.id
  }
}
function selectTab(id: string) {
  if (props.component.containerConfig) props.component.containerConfig.activeTab = id
}
</script>

<style scoped>
.combination-card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
}
.combination-card.editing {
  cursor: default;
}
.cc-head {
  flex-shrink: 0;
  margin-bottom: 8px;
}
.cc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
}
.cc-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--db-border);
  margin-bottom: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.cc-tab {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border: none;
  background: transparent;
  color: var(--db-text-secondary);
  font-size: 13px;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}
.cc-tab:hover { color: var(--db-accent); }
.cc-tab.active { color: var(--db-accent); border-bottom-color: var(--db-accent); font-weight: 600; }
.tab-edit { width: 64px; border: 1px solid var(--db-accent); border-radius: 4px; padding: 2px 4px; font-size: 12px; }
.cc-tab-add { border: none; background: transparent; color: var(--db-text-muted); font-size: 18px; width: 28px; height: 28px; border-radius: 6px; cursor: pointer; }
.cc-tab-add:hover { background: var(--db-hover); color: var(--db-accent); }
.tab-x { margin-left: 6px; border: none; background: transparent; color: var(--db-text-muted); font-size: 12px; line-height: 1; cursor: pointer; opacity: 0; transition: opacity 0.15s; }
.cc-tab:hover .tab-x, .cc-tab.active .tab-x { opacity: 1; }
.tab-x:hover { color: var(--db-danger); }

.cc-body { flex: 1 1 auto; position: relative; min-height: 120px; }
.cc-body.mode-grid { display: grid; gap: 12px; grid-template-columns: repeat(12, 1fr); align-content: start; }
.cc-body.mode-vertical { display: flex; flex-direction: column; gap: 12px; }

.cc-empty {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; color: var(--db-text-muted); text-align: center;
  border: 1px dashed var(--db-border); border-radius: 8px;
}
.cc-empty .empty-icon { font-size: 32px; }

.cc-child {
  box-sizing: border-box;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.15s;
}
.cc-body.mode-vertical .cc-child { position: relative; }
.cc-child.selected { border-color: var(--db-accent); box-shadow: 0 0 0 2px var(--db-accent-light); z-index: 5; }
.cc-child.moving { opacity: 0.85; }
.cc-child-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 10px; background: var(--db-hover); border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}
.cc-child-title { font-size: 12px; font-weight: 500; color: var(--db-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cc-child-del { border: none; background: transparent; color: var(--db-text-muted); cursor: pointer; font-size: 13px; padding: 1px 5px; border-radius: 4px; line-height: 1; }
.cc-child-del:hover { background: var(--db-danger-bg); color: var(--db-danger); }
.cc-child-body { flex: 1; overflow: hidden; min-height: 0; }

/* 八向缩放手柄 */
.rs {
  position: absolute; width: 14px; height: 14px; background: var(--db-accent);
  border: 2px solid #fff; border-radius: 50%; z-index: 6; box-shadow: 0 1px 4px rgba(0,0,0,.25);
}
.rs.nw { left: -7px; top: -7px; cursor: nwse-resize; }
.rs.n  { left: 50%; top: -7px; transform: translateX(-50%); cursor: ns-resize; }
.rs.ne { right: -7px; top: -7px; cursor: nesw-resize; }
.rs.e  { right: -7px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
.rs.se { right: -7px; bottom: -7px; cursor: nwse-resize; }
.rs.s  { left: 50%; bottom: -7px; transform: translateX(-50%); cursor: ns-resize; }
.rs.sw { left: -7px; bottom: -7px; cursor: nesw-resize; }
.rs.w  { left: -7px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
.rs:hover { background: var(--db-accent-strong, #4f46e5); }

/* 拖入落区高亮 */
.combination-card.editing.cc-drop { outline: 2px dashed var(--db-accent); }
</style>
