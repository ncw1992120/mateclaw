<template>
  <div
    ref="rootRef"
    class="combination-card"
    :class="{ editing: editable, selected, 'cc-drop': editable && paletteOver, 'cc-interacting': movingId !== null || resizingId !== null }"
    :style="rootStyle"
    @click="onRootClick"
    @dragenter="onBodyDragEnter"
    @dragleave="onBodyDragLeave"
    @dragover.prevent="onBodyDragOver"
    @drop.stop.prevent="onBodyDrop"
  >
    <!-- 容器标题：仅预览态渲染（编辑态由画布 grid-item-toolbar 统一展示标题，避免双标题） -->
    <div v-if="!editable && component.titleBarStyle !== 'hidden'" class="cc-head" :class="`title-bar-${component.titleBarStyle ?? 'standard'}`">
      <span class="cc-title"><DashboardComponentIcon type="combination" :dashboard-theme="dashboardTheme" :title-icon-style="component.titleIconStyle" :theme-accent-group="component.themeAccentGroup" />{{ component.title }}</span>
    </div>

    <!-- 页签栏（编辑态常驻渲染：无页签时也能从「+」建出第一个页签） -->
    <div v-if="cfg.tabs.length || editable" class="cc-tabs" role="tablist" :aria-label="t('insight.combination.tabs')">
      <div
        v-for="(tab, tabIndex) in cfg.tabs"
        :key="tab.id"
        class="cc-tab"
        :class="{ active: tab.id === cfg.activeTab }"
        role="tab"
        :aria-selected="tab.id === cfg.activeTab"
        :tabindex="tab.id === cfg.activeTab || (!cfg.activeTab && tab.id === cfg.tabs[0]?.id) ? 0 : -1"
        :data-tab-id="tab.id"
        @click.stop="selectTab(tab.id)"
        @keydown="onTabKeydown($event, tab.id)"
      >
        <input
          v-if="editable && editingTab === tab.id"
          ref="tabEditInput"
          class="tab-edit"
          v-model="editingTabTitle"
          @click.stop
          @blur="commitTabRename(tab)"
          @keydown.esc.prevent="cancelTabRename"
          @keyup.enter="commitTabRename(tab)"
        />
        <template v-else>
          <span class="cc-tab-label" @dblclick.stop="editable && startTabRename(tab)"><DashboardComponentIcon type="tab" :title="tab.title" :dashboard-theme="dashboardTheme" :variant="tabIndex" />{{ tab.title }}</span>
          <!-- 重命名 affordance：hover 淡入铅笔图标，提示该页签可双击重命名 -->
          <button
            v-if="editable"
            type="button"
            class="tab-rename-button"
            :aria-label="`编辑页签 ${tab.title}`"
            @click.stop="startTabRename(tab)"
          >
            <el-icon class="tab-rename-hint" :size="11"><EditPen /></el-icon>
          </button>
        </template>
        <button v-if="editable" class="tab-x" @click.stop="deleteTab(tab.id)" :title="t('insight.combination.deleteTab')">
          <el-icon :size="10"><Close /></el-icon>
        </button>
      </div>
      <button v-if="editable" class="cc-tab-add" @click.stop="addTab" :title="t('insight.combination.addTab')">
        <el-icon :size="14"><Plus /></el-icon>
      </button>
    </div>

    <!-- 主体：自由布局作为绝对定位参考系；整卡为拖入落区 -->
    <div ref="ccBodyRef" class="cc-body" :class="{ 'mode-free': cfg.layoutMode === 'free', 'mode-grid': cfg.layoutMode === 'grid', 'mode-vertical': cfg.layoutMode === 'vertical' }">
      <!-- 空状态（共享 EmptyState 组件，线性图标替代 emoji） -->
      <div v-if="activeChildren.length === 0" class="cc-empty">
        <div v-if="sampleMode" class="cc-sample-preview" aria-label="组合卡片样例内容">
          <div class="cc-sample-kpi"><span>总量</span><strong>1,284</strong><small>样例指标</small></div>
          <div class="cc-sample-chart" aria-hidden="true"><i style="height: 38%" /><i style="height: 68%" /><i style="height: 52%" /><i style="height: 86%" /></div>
        </div>
        <EmptyState v-else :text="editable ? t('insight.combination.emptyEditable') : t('insight.combination.empty')" />
      </div>

      <div
        v-for="(child, childIndex) in activeChildren"
        :key="child.id"
        class="cc-child"
        :class="[`mode-${cfg.layoutMode}`, { selected: selectedChildId === child.id, moving: movingId === child.id, 'inline-filter-child': isInlineFilterChild(child) }]"
        :style="childStyle(child)"
        :data-child="child.id"
        :tabindex="editable ? 0 : undefined"
        :aria-label="editable ? `移动子组件 ${child.title}` : undefined"
          @click.stop="selectChild(child.id)"
          @keydown="onChildKeydown($event, child)"
          @contextmenu.stop.prevent="onChildContextMenu($event, child)"
        @mouseenter="hoverChildId = child.id"
        @mouseleave="hoverChildId = null"
        @mousedown="onChildMouseDown($event, child)"
        @dragstart.stop.prevent
      >
        <div v-if="isTitleVisible(child.titleBarStyle) && !isInlineFilterChild(child)" class="cc-child-head" :class="`title-bar-${child.titleBarStyle ?? 'standard'}`">
          <input
            v-if="editable && editingChildId === child.id"
            ref="childTitleInput"
            class="cc-child-title-input"
            aria-label="子组件标题"
            v-model="editingChildTitle"
            @click.stop
            @blur="commitChildTitle(child)"
            @keydown.esc.prevent="cancelChildTitle"
            @keyup.enter="commitChildTitle(child)"
          />
          <template v-else>
            <DashboardComponentIcon
              :type="child.type"
              :chart-type="child.chartType"
              :title="child.title"
              :dashboard-theme="dashboardTheme"
              :title-icon-style="child.titleIconStyle"
              :theme-accent-group="child.themeAccentGroup"
              :variant="childIndex"
            />
            <button
              v-if="editable"
              type="button"
              class="cc-child-icon-style-trigger"
              :aria-label="`编辑标题图标 ${child.title}`"
              title="修改标题图标样式"
              @click.stop="emit('edit-child-title-icon-style', { containerId: component.id, childId: child.id })"
            >
              <el-icon :size="11"><EditPen /></el-icon>
            </button>
            <button
              v-if="editable"
              type="button"
              class="cc-child-title-trigger"
              :aria-label="`编辑子组件标题 ${child.title}`"
              @click.stop="startChildTitleEdit(child)"
            >
              <span class="cc-child-title">{{ child.title }}</span>
              <el-icon class="cc-child-title-edit" :size="11"><EditPen /></el-icon>
            </button>
            <span v-else class="cc-child-title">{{ child.title }}</span>
          </template>
          <button v-if="editable" class="cc-child-del" @click.stop="deleteChild(child.id)" :title="t('insight.combination.deleteChild')">
            <el-icon :size="10"><Close /></el-icon>
          </button>
        </div>
        <!-- 子组件真实渲染（复用顶层 widget 组件；v1 子卡片数据接入下轮） -->
        <div class="cc-child-body">
          <KpiCardWidget
            v-if="child.type === 'kpi'"
            :component="childWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :dashboard-theme="dashboardTheme"
          />
          <ChartWidget
            v-else-if="child.type === 'chart'"
            :component="toWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :dashboard-theme="dashboardTheme"
          />
          <DataTableWidget
            v-else-if="child.type === 'table'"
            :component="toWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :dashboard-theme="dashboardTheme"
          />
          <FilterSelectWidget
            v-else-if="child.type === 'filter'"
            :component="childWidgetComponent(child)"
            :show-title="true"
            :dashboard-theme="dashboardTheme"
          />
          <TimeFilterWidget
            v-else-if="child.type === 'timeFilter'"
            :component="toWidgetComponent(child)"
            :show-title="true"
            :dashboard-theme="dashboardTheme"
          />
          <AiAnalysisWidget
            v-else-if="child.type === 'aiAnalysis'"
            :component="toWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :dashboard-theme="dashboardTheme"
          />
          <CombinationCardWidget
            v-else-if="child.type === 'combination'"
            :component="toWidgetComponent(child)"
            :component-data-map="componentDataMap"
            :editable="editable"
            :selected="selectedChildId === child.id"
            :dashboard-theme="dashboardTheme"
            @select-child="(payload) => emit('select-child', payload)"
            @add-tab="(payload) => emit('add-tab', payload)"
            @remove-tab="(payload) => emit('remove-tab', payload)"
            @copy-child="(payload) => emit('copy-child', payload)"
            @paste-child="(payload) => emit('paste-child', payload)"
            @context-menu="(payload) => emit('context-menu', payload)"
            @edit-child-title-icon-style="(payload) => emit('edit-child-title-icon-style', payload)"
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
            @click.stop
            @dragstart.stop.prevent
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { Close, Plus, EditPen } from '@element-plus/icons-vue'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import type {
  InsightComponent,
  InsightComponentType,
  InsightCombinationChild,
  InsightCombinationConfig,
  ChartType,
  InsightComponentData,
  ResolvedDashboardTheme,
} from '@/types'
import { resolveComponentVisualStyle } from '@/utils/component-visual-style'
import { componentThemeStyle } from '@/utils/dashboard-theme'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import FilterSelectWidget from './FilterSelectWidget.vue'
import TimeFilterWidget from './TimeFilterWidget.vue'
import AiAnalysisWidget from './AiAnalysisWidget.vue'
import EmptyState from './EmptyState.vue'
import { useTabKeyboard } from '../composables/useTabKeyboard'
import { calculateCombinationChildResize } from './combinationChildLayout'
import { defaultCombinationChildLayout } from '@/utils/combination-tabs'
import { hasConfiguredDataset, resolveComponentSample } from '@/utils/component-sample-data'

defineOptions({ name: 'CombinationCardWidget' })

const props = withDefaults(
  defineProps<{
    component: InsightComponent
    componentDataMap?: Record<string, InsightComponentData>
    sampleMode?: boolean
    editable?: boolean
    selected?: boolean
    dashboardTheme?: ResolvedDashboardTheme
  }>(),
  { editable: false, selected: false },
)

const emit = defineEmits<{
  /** 子组件选中/取消选中（childId=null 表示回到容器自身），供编辑器属性面板联动 */
  (e: 'select-child', payload: { containerId: string; childId: string | null }): void
  /** 新增页签（首次新增时由编辑器把容器内已有子卡片平移进该页签） */
  (e: 'add-tab', payload: { containerId: string }): void
  /** 删除页签（编辑器负责二次确认与「最后一个页签组件平移回容器」） */
  (e: 'remove-tab', payload: { containerId: string; tabId: string }): void
  /** 将画布中的已有组件移入当前组合容器/页签 */
  (e: 'move-component-into', payload: { containerId: string; componentId: string; x: number; y: number }): void
  /** 组合卡片内部子组件剪贴板操作 */
  (e: 'copy-child', payload: { containerId: string; childId: string }): void
  (e: 'paste-child', payload: { containerId: string; childId: string | null }): void
  (e: 'context-menu', payload: { containerId: string; childId: string; x: number; y: number }): void
  (e: 'edit-child-title-icon-style', payload: { containerId: string; childId: string }): void
}>()

const { t } = useI18n()

const ccBodyRef = ref<HTMLElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const selectedChildId = ref<string | null>(null)
const hoverChildId = ref<string | null>(null)
const movingId = ref<string | null>(null)
const resizingId = ref<string | null>(null)
const editingTab = ref<string | null>(null)
const editingTabTitle = ref('')
const tabEditInput = ref<HTMLInputElement | null>(null)
const editingChildId = ref<string | null>(null)
const editingChildTitle = ref('')
const childTitleInput = ref<HTMLInputElement | null>(null)

function startChildTitleEdit(child: { id: string; title: string }): void {
  if (!props.editable) return
  editingChildId.value = child.id
  editingChildTitle.value = child.title
  void nextTick(() => {
    const input = childTitleInput.value as unknown as { focus?: () => void; select?: () => void } | null
    input?.focus?.()
    input?.select?.()
  })
}

function commitChildTitle(child: { id: string; title: string }): void {
  if (editingChildId.value !== child.id) return
  const title = editingChildTitle.value.trim()
  if (title) child.title = title
  editingChildId.value = null
  editingChildTitle.value = ''
}

function cancelChildTitle(): void {
  editingChildId.value = null
  editingChildTitle.value = ''
}

function startTabRename(tab: { id: string; title: string }): void {
  if (!props.editable) return
  editingTab.value = tab.id
  editingTabTitle.value = tab.title
  void nextTick(() => {
    const input = tabEditInput.value as unknown as { focus?: () => void; select?: () => void } | null
    input?.focus?.()
    input?.select?.()
  })
}

function commitTabRename(tab: { id: string; title: string }): void {
  if (editingTab.value !== tab.id) return
  const nextTitle = editingTabTitle.value.trim()
  if (nextTitle) tab.title = nextTitle
  editingTab.value = null
  editingTabTitle.value = ''
}

function cancelTabRename(): void {
  editingTab.value = null
  editingTabTitle.value = ''
}

/** 兜底容器配置（防御性） */
function defaultConfig(): InsightCombinationConfig {
  return {
    layoutMode: 'free',
    tabs: [],
    activeTab: undefined,
  }
}
const cfg = computed<InsightCombinationConfig>(() => props.component.containerConfig ?? defaultConfig())

function isTitleVisible(titleBarStyle: InsightComponent['titleBarStyle']): boolean {
  return titleBarStyle !== 'hidden'
}

const rootStyle = computed<Record<string, string>>(() => {
  const shared = resolveComponentVisualStyle(props.component.visualStyle, 'combination')
  return {
    ...componentThemeStyle(props.dashboardTheme, 'combination', 0, props.component.themeAccentGroup),
    ...shared,
  }
})

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
    titleBarStyle: child.titleBarStyle,
    titleIconStyle: child.titleIconStyle,
    themeAccentGroup: child.themeAccentGroup,
    visualStyle: child.visualStyle,
    position: { x: 0, y: 0, w: child.layout.col, h: child.layout.h ? Math.round(child.layout.h / 30) : 4 },
    chartType: child.chartType,
    config: child.config,
    dataSource: child.dataSource,
    children: child.children,
    containerConfig: child.containerConfig,
    boundFilterIds: child.boundFilterIds,
    enableTimeFilter: child.enableTimeFilter,
    multiKpi: child.multiKpi,
  }
}

function childWidgetComponent(child: InsightCombinationChild): InsightComponent {
  const component = toWidgetComponent(child)
  if (!props.sampleMode || hasConfiguredDataset(component) || child.type !== 'filter') return component
  const sample = JSON.parse(resolveComponentSample(component).json) as { data?: { options?: Array<{ label: string; value: string }> } }
  return {
    ...component,
    config: {
      ...(component.config ?? {}),
      optionSource: 'static',
      staticOptions: sample.data?.options ?? [],
    },
  }
}

function childComponentData(child: InsightCombinationChild): InsightComponentData | undefined {
  const configured = props.componentDataMap?.[child.id]
  if (configured) return configured
  const component = toWidgetComponent(child)
  if (!props.sampleMode || hasConfiguredDataset(component)) return undefined
  return resolveComponentSample(component).renderData
}

/** 子卡片定位样式 */
function childStyle(child: InsightCombinationChild): Record<string, string> {
  const visualStyle = resolveComponentVisualStyle(child.visualStyle, child.type)
  const themeStyle = componentThemeStyle(props.dashboardTheme, child.type, 1, child.themeAccentGroup)
  if (cfg.value.layoutMode === 'free') {
    return {
      ...themeStyle,
      ...visualStyle,
      position: 'absolute',
      left: child.layout.x + 'px',
      top: child.layout.y + 'px',
      width: `calc(${child.layout.col} / 12 * 100%)`,
      ...(child.layout.h != null ? { height: child.layout.h + 'px' } : {}),
    }
  }
  if (cfg.value.layoutMode === 'grid') {
    return { ...themeStyle, ...visualStyle, gridColumn: `span ${child.layout.col}` }
  }
  return { ...themeStyle, ...visualStyle }
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
    const payload = JSON.parse(raw) as { kind?: string; componentId?: string; componentType?: InsightComponentType; type?: InsightComponentType; chartType?: ChartType }
    if (payload.kind === 'canvas-component' && payload.componentId) {
      const layout = defaultCombinationChildLayout(payload.componentType ?? 'kpi')
      const pos = dropPosFromEvent(e, layout.col, layout.h ?? 180)
      emit('move-component-into', { containerId: props.component.id, componentId: payload.componentId, ...pos })
    } else if (payload.type) {
      const size = defaultSize(payload.type)
      addChild(payload.type, payload.chartType, dropPosFromEvent(e, size.col, size.h))
    }
  } catch (err) {
    console.error('[CombinationCardWidget] drop parse error:', err)
  }
  paletteOver.value = false
}
/**
 * 落点 → 合法初始位置。
 * 必须用**新子组件的真实默认宽高**换算可放置范围：早期版本用硬编码 200/90 估算，
 * 落点会超出「完整可见」的合法区间（例如容器 530 宽、子组件 265 宽时合法上限只有 265，
 * 却允许落到 330），结果子组件一放上去就贴在边界上，向右再也拖不动。
 */
function dropPosFromEvent(e: DragEvent, col: number, h: number): { x: number; y: number } {
  const rect = ccBodyRef.value?.getBoundingClientRect()
  if (!rect) return { x: 24, y: 24 }
  const w = (col / 12) * rect.width
  const x = Math.min(Math.max(e.clientX - rect.left, 0), Math.max(0, rect.width - w))
  const y = Math.min(Math.max(e.clientY - rect.top, 0), Math.max(0, rect.height - h))
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

/** 子组件默认尺寸（col 为 12 栅格列数，h 为像素高）—— 落点换算与新增子组件共用同一来源 */
function defaultSize(type: InsightComponentType): { col: number; h: number } {
  const layout = defaultCombinationChildLayout(type)
  return { col: layout.col, h: layout.h ?? 180 }
}

function addChild(type: InsightComponentType, chartType: ChartType | undefined, pos: { x: number; y: number }): void {
  const arr = getActiveChildren()
  const size = defaultSize(type)
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
    children: type === 'combination' ? [] : undefined,
    containerConfig: type === 'combination' ? defaultConfig() : undefined,
    layout: {
      x: pos.x,
      y: pos.y,
      col: size.col,
      h: size.h,
    },
  }
  arr.push(child)
  selectedChildId.value = child.id
}

// ── 子组件自由拖动（鼠标事件）──────────────────────────
// 交互要点：
// 1) mousedown stopPropagation：阻断事件冒泡到 GridItem，否则 grid-layout-plus 会同时发起
//    容器级拖拽，导致拖子组件时整个组合卡片跟着抖动；
// 2) mousedown preventDefault：阻止文本选中/原生图片拖拽；
// 3) 拖动过程直接改 DOM style + requestAnimationFrame 节流，不写响应式 layout，
//    避免每帧触发整卡重渲染造成卡顿；
// 4) **过程与落点共用同一套纯计算**（computeMove）：mouseup 时用最后一次鼠标坐标重算一次，
//    即使取消了 pending 的 rAF 也不会丢掉最后一段位移（原来直接读 DOM style 会少算一帧）；
// 5) 边界钳制让子组件**完整留在容器内**（而不是「保留多少像素在容器内」）：
//    组合卡片是固定容器，一旦允许子组件探出右/下边界，八向缩放手柄会被容器的
//    overflow:hidden 裁掉，用户就抓不到 se/e/ne 手柄 —— 那才是「缩放用不了」的真凶。
/** 容器边界快照：**只在鼠标按下时读一次**，绝不在每帧的 compute 里读 DOM。 */
interface ChildBounds { width: number; height: number }

/**
 * 读一次容器边界。
 *
 * ⚠️ `getBoundingClientRect()` 会强制浏览器立即同步布局（forced reflow）；若放在每帧的
 * `compute` 里，rAF 回调就变成「读布局 → 写 left/top」的读写交错，每一帧都被迫完整重排，
 * 拖起来发涩。本次拖动/缩放期间容器尺寸不变，按下时取一次即可（过程与落点共用同一份）。
 */
function readChildBounds(): ChildBounds | null {
  const r = ccBodyRef.value?.getBoundingClientRect()
  // jsdom 以及尚未完成布局的隐藏容器会返回 0×0 的 DOMRect。此时不能把可移动范围
  // 误判成只有原点，否则键盘微调会被 clampBox 永久钳在 (0, 0)。
  return r && r.width > 0 && r.height > 0 ? { width: r.width, height: r.height } : null
}

/** 位置钳制：保证子组件整体可见 —— 这也保证八向手柄永远落在容器里、永远抓得到 */
function clampBox(
  bounds: ChildBounds | undefined,
  x: number, y: number, w: number, h: number,
): { x: number; y: number } {
  if (!bounds) return { x: Math.max(0, Math.round(x)), y: Math.max(0, Math.round(y)) }
  const maxX = Math.max(0, bounds.width - w)
  const maxY = Math.max(0, bounds.height - h)
  return {
    x: Math.round(Math.min(Math.max(x, 0), maxX)),
    y: Math.round(Math.min(Math.max(y, 0), maxY)),
  }
}
/** 刚发生过拖动/缩放的时间戳：用于抑制紧随 mouseup 的 click，避免八向手柄刚拉完就被清掉选中态 */
let lastInteractAt = 0
function markInteracted(): void { lastInteractAt = Date.now() }

let mv: { id: string; sx: number; sy: number; ox: number; oy: number; el: HTMLElement | null; bounds: ChildBounds | null; elW: number; elH: number } | null = null
let mvRaf = 0
let mvLast: { x: number; y: number } | null = null

/** 起点 + 当前鼠标坐标 → 目标位置（拖动过程与落点共用，保证两者完全一致）。全程不读 DOM。 */
function computeMove(start: NonNullable<typeof mv>, last: { x: number; y: number }): { x: number; y: number } {
  return clampBox(
    start.bounds ?? undefined,
    start.ox + last.x - start.sx,
    start.oy + last.y - start.sy,
    start.elW,
    start.elH,
  )
}

function onChildMouseDown(e: MouseEvent, child: InsightCombinationChild) {
  if (!props.editable) return
  const target = e.target as HTMLElement
  // 子卡片内部的交互控件（尤其是嵌套组合的页签）不能被父级自由布局拖拽接管。
  // 否则父级 mousedown 的 preventDefault 会阻止浏览器继续派发 click，页签看似可点但无法切换。
  if (target.closest('.cc-child-del, .rs, .cc-tabs, .cc-tab, .cc-tab-add, button, input, select, textarea')) return
  e.preventDefault()
  e.stopPropagation()
  selectChild(child.id)
  const el = (ccBodyRef.value?.querySelector(`[data-child="${child.id}"]`) as HTMLElement | null) ?? null
  // preventDefault 会阻止浏览器默认的鼠标聚焦；主动聚焦后，用户松开鼠标即可直接用方向键微调。
  el?.focus()
  mv = {
    id: child.id,
    sx: e.clientX,
    sy: e.clientY,
    ox: child.layout.x,
    oy: child.layout.y,
    el,
    // 按下时一次性读布局，过程与落点提交复用（详见 readChildBounds 注释）
    bounds: readChildBounds(),
    elW: el?.offsetWidth ?? 0,
    elH: el?.offsetHeight ?? 0,
  }
  mvLast = { x: e.clientX, y: e.clientY }
  markInteracted()
  movingId.value = child.id
  window.addEventListener('mousemove', onChildMouseMove)
  window.addEventListener('mouseup', onChildMouseUp)
}

function isInlineFilterChild(child: InsightCombinationChild): boolean {
  return child.type === 'filter' || child.type === 'timeFilter'
}

function onChildMouseMove(e: MouseEvent) {
  if (!mv) return
  mvLast = { x: e.clientX, y: e.clientY }
  if (mvRaf) return
  mvRaf = requestAnimationFrame(() => {
    mvRaf = 0
    if (!mv || !mvLast) return
    const p = computeMove(mv, mvLast)
    mv.el?.style.setProperty('left', p.x + 'px')
    mv.el?.style.setProperty('top', p.y + 'px')
  })
}
function onChildMouseUp() {
  if (mvRaf) { cancelAnimationFrame(mvRaf); mvRaf = 0 }
  if (mv && mvLast) {
    const child = getActiveChildren().find((c) => c.id === mv!.id)
    if (child) {
      const p = computeMove(mv, mvLast)
      child.layout.x = p.x
      child.layout.y = p.y
      // 同步写回 DOM。这里不能 removeProperty：Vue 的 style patch 只下发「发生变化」的
      // 属性，若移除内联值而 layout 又没变（例如纯点击未拖动），元素会瞬间丢失 left/top
      // 定位。保留内联值更稳，后续 layout 变化时 Vue 自然会覆盖它。
      mv.el?.style.setProperty('left', p.x + 'px')
      mv.el?.style.setProperty('top', p.y + 'px')
    }
  }
  mv = null
  mvLast = null
  movingId.value = null
  window.removeEventListener('mousemove', onChildMouseMove)
  window.removeEventListener('mouseup', onChildMouseUp)
}

// ── 八向缩放（与拖动同策略：过程改 DOM，落点提交）──────
let rz: { id: string; dir: string; sx: number; sy: number; ox: number; oy: number; ocol: number; oh: number; el: HTMLElement | null; bounds: ChildBounds | null; colW: number } | null = null
let rzRaf = 0
let rzLast: { x: number; y: number } | null = null

/** 起点 + 当前鼠标坐标 → 目标盒子（缩放过程与落点共用）。col/h/位置三者联动一致，全程不读 DOM。 */
function computeResize(
  start: NonNullable<typeof rz>,
  last: { x: number; y: number },
): { x: number; y: number; col: number; h: number } {
  const box = calculateCombinationChildResize(
    {
      direction: start.dir,
      startX: start.sx,
      startY: start.sy,
      originX: start.ox,
      originY: start.oy,
      originCol: start.ocol,
      originHeight: start.oh,
      bounds: start.bounds,
      columnWidth: start.colW,
    },
    last,
  )
  return { x: box.x, y: box.y, col: box.col, h: box.height }
}

function onChildResizeDown(e: MouseEvent, child: InsightCombinationChild, dir: string) {
  if (!props.editable) return
  e.preventDefault()
  e.stopPropagation()
  selectChild(child.id)
  const bounds = readChildBounds()
  rz = {
    id: child.id,
    dir,
    sx: e.clientX,
    sy: e.clientY,
    ox: child.layout.x,
    oy: child.layout.y,
    ocol: child.layout.col,
    oh: child.layout.h ?? 120,
    el: (ccBodyRef.value?.querySelector(`[data-child="${child.id}"]`) as HTMLElement | null) ?? null,
    // 按下时一次性读布局：列宽基准与边界都在此固定，缩放过程不再触发同步布局
    bounds,
    colW: bounds ? bounds.width / 12 : 40,
  }
  rzLast = { x: e.clientX, y: e.clientY }
  markInteracted()
  resizingId.value = child.id
  window.addEventListener('mousemove', onChildResizeMove)
  window.addEventListener('mouseup', onChildResizeUp)
}
function onChildResizeMove(e: MouseEvent) {
  if (!rz) return
  rzLast = { x: e.clientX, y: e.clientY }
  if (rzRaf) return
  rzRaf = requestAnimationFrame(() => {
    rzRaf = 0
    if (!rz || !rzLast || !rz.el) return
    const colW = rz.colW
    const box = computeResize(rz, rzLast)
    rz.el.style.setProperty('left', box.x + 'px')
    rz.el.style.setProperty('top', box.y + 'px')
    rz.el.style.setProperty('width', Math.round(box.col * colW) + 'px')
    rz.el.style.setProperty('height', box.h + 'px')
  })
}
function onChildResizeUp() {
  if (rzRaf) { cancelAnimationFrame(rzRaf); rzRaf = 0 }
  if (rz && rzLast) {
    const child = getActiveChildren().find((c) => c.id === rz!.id)
    if (child) {
      const colW = rz.colW
      const box = computeResize(rz, rzLast)
      child.layout.x = box.x
      child.layout.y = box.y
      child.layout.col = box.col
      child.layout.h = box.h
      // 与拖动同理：保留内联值而非 removeProperty，避免纯点击时丢失定位
      rz.el?.style.setProperty('left', box.x + 'px')
      rz.el?.style.setProperty('top', box.y + 'px')
      rz.el?.style.setProperty('width', Math.round(box.col * colW) + 'px')
      rz.el?.style.setProperty('height', box.h + 'px')
    }
  }
  rz = null
  rzLast = null
  resizingId.value = null
  window.removeEventListener('mousemove', onChildResizeMove)
  window.removeEventListener('mouseup', onChildResizeUp)
}

// ── 选中 / 删除 ───────────────────────────────────────
function selectChild(id: string) {
  selectedChildId.value = id
  emit('select-child', { containerId: props.component.id, childId: id })
}

/** 编辑态下，获得焦点的子组件可用方向键微调位置；交互控件保留自身键盘行为。 */
function onChildKeydown(event: KeyboardEvent, child: InsightCombinationChild): void {
  if (!props.editable) return
  const target = event.target as HTMLElement | null
  if (target?.closest('input, textarea, select, button, [role="tab"]')) return
  if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'c') {
    event.preventDefault()
    event.stopPropagation()
    selectChild(child.id)
    emit('copy-child', { containerId: props.component.id, childId: child.id })
    return
  }
  if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'v') {
    event.preventDefault()
    event.stopPropagation()
    selectChild(child.id)
    emit('paste-child', { containerId: props.component.id, childId: child.id })
    return
  }
  if (cfg.value.layoutMode !== 'free') return
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return

  event.preventDefault()
  event.stopPropagation()
  selectChild(child.id)

  const bounds = readChildBounds()
  const childEl = ccBodyRef.value?.querySelector(`[data-child="${child.id}"]`) as HTMLElement | null
  const width = childEl?.offsetWidth ?? (bounds ? bounds.width * child.layout.col / 12 : 0)
  const height = childEl?.offsetHeight ?? child.layout.h ?? 120
  const step = event.shiftKey ? 10 : 1
  const dx = event.key === 'ArrowRight' ? step : event.key === 'ArrowLeft' ? -step : 0
  const dy = event.key === 'ArrowDown' ? step : event.key === 'ArrowUp' ? -step : 0
  const next = clampBox(bounds, child.layout.x + dx, child.layout.y + dy, width, height)
  child.layout.x = next.x
  child.layout.y = next.y
  childEl?.style.setProperty('left', next.x + 'px')
  childEl?.style.setProperty('top', next.y + 'px')
}

function onChildContextMenu(event: MouseEvent, child: InsightCombinationChild): void {
  if (!props.editable) return
  selectChild(child.id)
  emit('context-menu', {
    containerId: props.component.id,
    childId: child.id,
    x: event.clientX,
    y: event.clientY,
  })
}

async function deleteChild(id: string) {
  const child = getActiveChildren().find((c) => c.id === id)
  try {
    await ElMessageBox.confirm(
      t('insight.combination.deleteChildConfirm', { name: child?.title ?? id }),
      '',
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return // 用户取消
  }
  const arr = getActiveChildren()
  const i = arr.findIndex((c) => c.id === id)
  if (i >= 0) arr.splice(i, 1)
  if (selectedChildId.value === id) {
    selectedChildId.value = null
    emit('select-child', { containerId: props.component.id, childId: null })
  }
}
function onRootClick() {
  if (!props.editable || selectedChildId.value === null) return
  // 拖动/缩放结束会紧跟一个 click 冒泡到根节点；若此刻清空选中态，八向手柄会被立刻
  // 取消渲染，用户拉完一次就再也抓不到手柄（表现为「缩放用不了」）。这里留一个短窗口忽略。
  if (Date.now() - lastInteractAt < 300) return
  selectedChildId.value = null
  emit('select-child', { containerId: props.component.id, childId: null })
}

/** 容器失去选中态时，同步清掉内部子组件选中，避免高亮残留 */
watch(() => props.selected, (v) => {
  if (!v && selectedChildId.value !== null) selectedChildId.value = null
})

// ── 页签（增删统一交由编辑器对 schema 执行）─────────────
// 页签增删涉及「首次加页签把容器内已有子卡片平移进页签」「删页签需二次确认」
// 「删最后一个页签把组件平移回容器」等跨组件逻辑，统一在编辑器里做，避免画布/属性面板两处入口行为不一致。
function addTab() {
  emit('add-tab', { containerId: props.component.id })
}
function deleteTab(id: string) {
  // 被删页签内的子卡片若正被选中，先取消选中，避免残留高亮
  const tab = props.component.containerConfig?.tabs.find((x) => x.id === id)
  if (tab && selectedChildId.value && tab.children.some((c) => c.id === selectedChildId.value)) {
    selectedChildId.value = null
  }
  emit('remove-tab', { containerId: props.component.id, tabId: id })
}
function selectTab(id: string) {
  if (props.component.containerConfig) props.component.containerConfig.activeTab = id
}

/** 页签键盘导航（与 KPI 卡共用同一 composable；容器内局部查找，避免跨卡串元素） */
const { onTabKeydown } = useTabKeyboard(
  () => cfg.value.tabs,
  selectTab,
  (id) => rootRef.value?.querySelector(`[role="tab"][data-tab-id="${id}"]`) ?? null,
)
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
  user-select: none;
  -webkit-user-select: none;
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
  background: var(--db-surface-control, transparent);
  margin-bottom: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.cc-tab {
  display: inline-flex;
  align-items: center;
  gap: 3px;
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
.cc-tab:focus-visible { outline: 2px solid var(--db-accent-border); border-radius: 4px; }
.tab-edit { width: 64px; border: 1px solid var(--db-accent); border-radius: 4px; padding: 2px 4px; font-size: 12px; }
.tab-rename-button { border: none; background: transparent; color: inherit; display: inline-flex; align-items: center; padding: 2px; border-radius: 4px; cursor: pointer; }
.tab-rename-button:hover { background: var(--db-hover); }
.cc-tab-add { border: none; background: transparent; color: var(--db-text-muted); width: 28px; height: 28px; border-radius: 6px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.cc-tab-add:hover { background: var(--db-hover); color: var(--db-accent); }
/* 重命名 affordance：hover 页签时淡入铅笔图标，提示可双击重命名 */
.tab-rename-hint { color: var(--db-text-muted); opacity: 0; transition: opacity 0.15s; }
.cc-tab:hover .tab-rename-hint { opacity: 0.7; }
.tab-x { margin-left: 2px; border: none; background: transparent; color: var(--db-text-muted); line-height: 1; cursor: pointer; opacity: 0; transition: opacity 0.15s; display: inline-flex; align-items: center; justify-content: center; padding: 2px; border-radius: 4px; }
.cc-tab:hover .tab-x, .cc-tab.active .tab-x { opacity: 1; }
.tab-x:hover { color: var(--db-danger); background: var(--db-danger-bg); }

.cc-body { flex: 1 1 auto; position: relative; min-height: 120px; }
.cc-body.mode-grid { display: grid; gap: 12px; grid-template-columns: repeat(12, 1fr); align-content: start; }
.cc-body.mode-vertical { display: flex; flex-direction: column; gap: 12px; }

.cc-empty {
  position: absolute; inset: 0;
  border: 1px dashed var(--db-border); border-radius: 8px;
}
.cc-sample-preview { display: grid; grid-template-columns: 1fr 1.2fr; align-items: center; gap: 12px; width: min(420px, 90%); text-align: left; }
.cc-sample-kpi { display: flex; flex-direction: column; gap: 4px; color: var(--db-text-muted); font-size: 12px; }
.cc-sample-kpi strong { color: var(--db-text); font-size: 24px; font-variant-numeric: tabular-nums; }
.cc-sample-kpi small { font-size: 11px; }
.cc-sample-chart { display: flex; height: 72px; align-items: end; justify-content: space-around; gap: 8px; padding: 0 12px; border-bottom: 1px solid var(--db-border); }
.cc-sample-chart i { display: block; width: 18%; border-radius: 3px 3px 0 0; background: var(--db-accent); opacity: 0.55; }
/* 空态内容（图标 + 文案）由共享 EmptyState 组件渲染 */

.cc-child {
  position: relative;
  box-sizing: border-box;
  border: var(--component-border, 1px solid var(--component-group-border, var(--db-border)));
  border-radius: var(--component-radius, 8px);
  background: var(--component-surface, var(--component-group-surface, var(--db-surface-card, var(--db-card))));
  box-shadow: var(--component-shadow, none);
  padding: var(--component-padding, 0px);
  display: flex;
  flex-direction: column;
  /* overflow 必须可见：八向缩放手柄有 7px 探出子卡片边界，hidden 会把可点击区域裁掉，
     导致按在手柄边界上时命中不到手柄（表现为误拖整个容器） */
  overflow: visible;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.15s;
}
.cc-child::before { content: ''; position: absolute; inset: 0 0 auto; z-index: 2; height: 2px; border-radius: inherit; background: var(--component-group-accent, transparent); pointer-events: none; }
.cc-body.mode-vertical .cc-child { position: relative; }
.cc-child.selected { border-color: var(--db-accent); box-shadow: 0 0 0 2px var(--db-accent-light); z-index: 5; }
.cc-child.moving { opacity: 0.85; }
.cc-child-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 10px; background: var(--component-group-header-surface, var(--db-surface-nested, var(--db-hover))); border-bottom: 1px solid var(--component-group-border, var(--db-border));
  border-radius: 7px 7px 0 0;
  flex-shrink: 0;
}
.cc-child-title-trigger { display: inline-flex; align-items: center; gap: 4px; min-width: 0; border: none; padding: 0; background: transparent; color: inherit; cursor: text; }
.cc-child-icon-style-trigger { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 20px; height: 20px; margin: 0 5px 0 -4px; border: 0; border-radius: 4px; background: transparent; color: var(--db-text-muted); cursor: pointer; }
.cc-child-icon-style-trigger:hover { background: var(--db-hover); color: var(--db-accent); }
.cc-child-icon-style-trigger:focus-visible { outline: 2px solid var(--db-accent); outline-offset: 1px; }
.cc-child-title-edit { color: var(--db-text-muted); opacity: 0; transition: opacity var(--transition-fast); }
.cc-child-title-trigger:hover .cc-child-title-edit { opacity: 0.8; }
.cc-child-title-input { min-width: 100px; max-width: 220px; height: 22px; border: 1px solid var(--db-accent); border-radius: var(--radius-sm); padding: 2px 5px; background: var(--db-surface-control, var(--db-card)); color: var(--db-text); font-size: 12px; outline: none; }
.cc-child-title { font-size: 12px; font-weight: 500; color: var(--db-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cc-child-del { border: none; background: transparent; color: var(--db-text-muted); cursor: pointer; padding: 2px; border-radius: 4px; line-height: 1; display: inline-flex; align-items: center; justify-content: center; }
.cc-child-del:hover { background: var(--db-danger-bg); color: var(--db-danger); }
.cc-child-body { flex: 1; overflow: hidden; min-height: 0; border-radius: 0 0 7px 7px; }
.cc-child.inline-filter-child { border: 0; background: transparent; box-shadow: none; }
.cc-child.inline-filter-child .cc-child-body { overflow: visible; border-radius: 0; }

/* 拖动/缩放期间：屏蔽子卡片内部（图表/表格等）的鼠标事件，并关掉子卡片的过渡动画
   —— 指针移动会让 :hover 在子卡片之间反复进出，每次都会重启 box-shadow 过渡，指标/子组件
   多时是可见的重绘抖动源。 */
.combination-card.cc-interacting .cc-child-body { pointer-events: none; }
.combination-card.cc-interacting .cc-child { cursor: grabbing; transition: none; }
.combination-card.cc-interacting .cc-child img,
.combination-card.cc-interacting .cc-child canvas { -webkit-user-drag: none; }

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
.rs::after { content: ''; position: absolute; inset: -5px; border-radius: 50%; }
.rs:hover { background: var(--db-accent-strong, #4f46e5); }

/* 拖入落区高亮 */
.combination-card.editing.cc-drop { outline: 2px dashed var(--db-accent); }
</style>
