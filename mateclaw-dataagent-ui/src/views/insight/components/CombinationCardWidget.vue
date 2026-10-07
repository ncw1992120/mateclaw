<template>
  <div
    ref="rootRef"
    class="combination-card"
    :class="{ editing: editable, selected, 'cc-interacting': movingId !== null || resizingId !== null }"
    :style="[rootStyle, { '--cc-h-scale': String(ccHScale) }]"
    @click="onRootClick"
    @dragover.prevent
    @drop="onBodyDrop"
  >
    <!-- 容器标题：仅预览态渲染（编辑态由画布 grid-item-toolbar 统一展示标题，避免双标题） -->
    <div v-if="!editable && component.titleBarStyle !== 'hidden'" class="cc-head" :class="`title-bar-${component.titleBarStyle ?? 'standard'}`">
      <span class="cc-title"><DashboardComponentIcon type="combination" :dashboard-theme="dashboardTheme" :title-icon-style="componentTitleIconStylePreview ?? component.titleIconStyle" :theme-accent-group="component.themeAccentGroup" :component-color="component.componentColor" />{{ component.title }}</span>
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
          <span class="cc-tab-label" @dblclick.stop="editable && startTabRename(tab)">
            <DashboardTabTitle
              :title="tab.title"
              :title-icon-style="tab.titleIconStyle"
              :title-icon-style-preview="tabIconStylePreview(tab)"
              :dashboard-theme="dashboardTheme"
              :variant="tabIndex"
              :editable="editable"
              @edit="(anchor) => emit('edit-tab-title-icon-style', { componentId: component.id, tabId: tab.id, tabKind: 'combination', anchor })"
            />
          </span>
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
    <div ref="ccBodyRef" class="cc-body" :class="{ 'mode-free': cfg.layoutMode === 'free', 'mode-grid': cfg.layoutMode === 'grid', 'mode-vertical': cfg.layoutMode === 'vertical', 'drop-hint': editable && dropHint }">
      <!-- 画布正在拖动顶层组件：显示可放置提示（pointer-events:none，不拦截 drop） -->
      <div v-if="editable && dropHint" class="cc-drop-hint" data-testid="combination-drop-hint">
        <span>{{ t('insight.combination.dropIntoHint') }}</span>
      </div>
      <!-- 空状态（共享 EmptyState 组件，线性图标替代 emoji） -->
      <div v-if="activeChildren.length === 0" class="cc-empty">
        <EmptyState :text="editable ? t('insight.combination.emptyEditable') : t('insight.combination.empty')" />
      </div>

      <div
        v-for="(child, childIndex) in activeChildren"
        :key="child.id"
        class="cc-child"
        :class="[`mode-${cfg.layoutMode}`, { selected: selectedChildId === child.id, moving: movingId === child.id, 'is-dragout': dragOutChild?.childId === child.id, 'inline-filter-child': isInlineFilterChild(child) }]"
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
            <span class="cc-child-title-group">
              <DashboardComponentIcon
                :type="child.type"
                :chart-type="child.chartType"
                :title="child.title"
                :dashboard-theme="dashboardTheme"
                :title-icon-style="childIconTitleStyle(child)"
                :theme-accent-group="child.themeAccentGroup"
                :component-color="child.componentColor"
                :variant="childIndex"
              />
              <button
                v-if="editable"
                type="button"
                class="cc-child-icon-style-trigger"
                :aria-label="`编辑标题图标 ${child.title}`"
                title="修改标题图标样式"
              @click.stop="emit('edit-child-title-icon-style', { containerId: component.id, childId: child.id, anchor: $event.currentTarget as HTMLElement })"
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
            </span>
          </template>
          <button v-if="editable" class="cc-child-del" @click.stop="deleteChild(child.id)" :title="t('insight.combination.deleteChild')">
            <el-icon :size="10"><Close /></el-icon>
          </button>
          <div
            v-if="isSampleData(child)"
            class="cc-sample-data-watermark"
            data-testid="cc-sample-data-watermark"
            aria-label="当前展示的是样例数据"
            title="未绑定数据源，当前为示例内容"
          >示例数据</div>
        </div>
        <!-- 子组件真实渲染（复用顶层 widget 组件；v1 子卡片数据接入下轮） -->
        <div class="cc-child-body">
          <ComponentQueryState
            v-if="queryDisplayStatus(childComponentData(child))"
            :status="queryDisplayStatus(childComponentData(child))!"
            :title="queryStateTitle(childComponentData(child))"
            :message="childComponentData(child)?.error"
            :retry-label="t('insight.componentQueryRetry')"
            @retry="emit('retry-component-query', child.id)"
          />
          <KpiCardWidget
            v-else-if="child.type === 'kpi'"
            :component="childWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :editable="editable"
            :dashboard-theme="dashboardTheme"
            :tab-title-icon-style-preview="tabTitleIconStylePreview"
            @open-metric-style="(payload) => emit('open-metric-style', { ...payload, containerId: component.id, childId: child.id })"
            @edit-tab-title-icon-style="(payload) => emit('edit-tab-title-icon-style', payload)"
          />
          <ChartWidget
            v-else-if="child.type === 'chart'"
            :component="toWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :editable="editable"
            :dashboard-theme="dashboardTheme"
            :tab-title-icon-style-preview="tabTitleIconStylePreview"
            @edit-tab-title-icon-style="(payload) => emit('edit-tab-title-icon-style', payload)"
          />
          <DataTableWidget
            v-else-if="child.type === 'table'"
            :component="toWidgetComponent(child)"
            :component-data="childComponentData(child)"
            :show-title="false"
            :sample-mode="isSampleData(child)"
            :editable="editable"
            :dashboard-theme="dashboardTheme"
            :tab-title-icon-style-preview="tabTitleIconStylePreview"
            @edit-tab-title-icon-style="(payload) => emit('edit-tab-title-icon-style', payload)"
          />
          <FilterSelectWidget
            v-else-if="child.type === 'filter'"
            :component="childWidgetComponent(child)"
            :model-value="runtimeFilterState?.[child.id] ? (runtimeFilterState[child.id].value ?? null) : undefined"
            :show-title="true"
            :dashboard-theme="dashboardTheme"
            @change="(payload) => emit('filter-change', { componentId: child.id, ...payload })"
          />
          <TimeFilterWidget
            v-else-if="child.type === 'timeFilter'"
            :component="toWidgetComponent(child)"
            :model-value="runtimeFilterState?.[child.id]?.value"
            :time-granularity="runtimeFilterState?.[child.id]?.timeGranularity"
            :show-title="true"
            :dashboard-theme="dashboardTheme"
            @change="(payload) => emit('time-filter-change', { componentId: child.id, ...payload })"
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
            :runtime-filter-state="runtimeFilterState"
            :editable="editable"
            :container-resizing="resizingId === child.id"
            :selected="selectedChildId === child.id"
            :drop-hint="dropHint"
            :dashboard-theme="dashboardTheme"
            :title-icon-style-preview="titleIconStylePreview"
            :tab-title-icon-style-preview="tabTitleIconStylePreview"
            @filter-change="(payload) => emit('filter-change', payload)"
            @time-filter-change="(payload) => emit('time-filter-change', payload)"
            @retry-component-query="(id) => emit('retry-component-query', id)"
            @select-child="(payload) => emit('select-child', payload)"
            @add-tab="(payload) => emit('add-tab', payload)"
            @remove-tab="(payload) => emit('remove-tab', payload)"
            @delete-child="(payload) => emit('delete-child', payload)"
            @move-component-into="(payload) => emit('move-component-into', payload)"
            @add-component-into="(payload) => emit('add-component-into', payload)"
            @move-child-out="(payload) => emit('move-child-out', payload)"
            @copy-child="(payload) => emit('copy-child', payload)"
            @paste-child="(payload) => emit('paste-child', payload)"
            @context-menu="(payload) => emit('context-menu', payload)"
            @edit-child-title-icon-style="(payload) => emit('edit-child-title-icon-style', payload)"
            @edit-tab-title-icon-style="(payload) => emit('edit-tab-title-icon-style', payload)"
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

    <!-- 子卡片拖出提示：Teleport 到 body，避免被容器/画布的 overflow 与 transform 裁剪或偏移 -->
    <Teleport to="body">
      <div
        v-if="dragOutChild"
        class="cc-dragout-ghost"
        data-testid="combination-dragout-ghost"
        :style="{ left: `${dragOutChild.x + 12}px`, top: `${dragOutChild.y + 12}px` }"
      >
        ⤴ {{ t('insight.combination.dragOutHint', { name: dragOutChild.title }) }}
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { Close, Plus, EditPen } from '@element-plus/icons-vue'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import ComponentQueryState from './ComponentQueryState.vue'
import DashboardTabTitle from './DashboardTabTitle.vue'
import type {
  ChartType,
  InsightComponent,
  InsightComponentType,
  InsightCombinationChild,
  InsightCombinationConfig,
  InsightComponentData,
  DashboardRuntimeFilterState,
  TimeGranularity,
  ComponentTitleIconStyle,
  DashboardTabTitleIconStylePreview,
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
import { projectPythonResultKpi } from '@/utils/kpi-result-projection'

defineOptions({ name: 'CombinationCardWidget' })

const props = withDefaults(
  defineProps<{
    component: InsightComponent
    componentDataMap?: Record<string, InsightComponentData>
    runtimeFilterState?: DashboardRuntimeFilterState
    /** 容器本身不展示样例数据；内部子组件按自身类型决定，保留字段兼容旧调用方。 */
    sampleMode?: boolean
    editable?: boolean
    selected?: boolean
    /** 预览画布启用自适应宽度时，按组合卡片内宽比例映射自由布局子组件的位置。 */
    previewFillWidth?: boolean
    /** 外层正在缩放此组合卡片；期间冻结子组件像素宽度。 */
    containerResizing?: boolean
    /** 画布正在拖动顶层组件：组合卡片显示可放置提示 */
    dropHint?: boolean
    dashboardTheme?: ResolvedDashboardTheme
    titleIconStylePreview?: { childId: string; style: ComponentTitleIconStyle }
    componentTitleIconStylePreview?: ComponentTitleIconStyle
    tabTitleIconStylePreview?: DashboardTabTitleIconStylePreview
  }>(),
  { editable: false, selected: false, previewFillWidth: false, containerResizing: false },
)

const emit = defineEmits<{
  /** 子组件选中/取消选中（childId=null 表示回到容器自身），供编辑器属性面板联动 */
  (e: 'select-child', payload: { containerId: string; childId: string | null }): void
  /** 新增页签（首次新增时由编辑器把容器内已有子卡片平移进该页签） */
  (e: 'add-tab', payload: { containerId: string }): void
  /** 删除页签（编辑器负责二次确认与「最后一个页签组件平移回容器」） */
  (e: 'remove-tab', payload: { containerId: string; tabId: string }): void
  /** 删除筛选类子组件交由编辑器统一确认，并清理全仪表盘绑定。 */
  (e: 'delete-child', payload: { containerId: string; childId: string }): void
  /** 将画布中的已有组件移入当前组合容器/页签 */
  (e: 'move-component-into', payload: { containerId: string; componentId: string; x: number; y: number }): void
  /** 将物料面板新组件直接添加到当前组合卡片的落点。 */
  (e: 'add-component-into', payload: { containerId: string; type: InsightComponentType; chartType?: ChartType; x: number; y: number }): void
  /** 子卡片拖出组合卡片：释放点为视口坐标，由画布换算栅格落点后转回顶层组件 */
  (e: 'move-child-out', payload: { containerId: string; childId: string; clientX: number; clientY: number }): void
  /** 组合卡片内部子组件剪贴板操作 */
  (e: 'copy-child', payload: { containerId: string; childId: string }): void
  (e: 'paste-child', payload: { containerId: string; childId: string | null }): void
  (e: 'context-menu', payload: { containerId: string; childId: string; x: number; y: number }): void
  (e: 'edit-child-title-icon-style', payload: { containerId: string; childId: string; anchor?: HTMLElement }): void
  (e: 'open-metric-style', payload: { containerId: string; childId: string; fieldKey: string; field: string }): void
  (e: 'edit-tab-title-icon-style', payload: { componentId: string; tabId: string; tabKind: 'component' | 'combination'; anchor: HTMLElement }): void
  /** 筛选事件需要携带实际子组件 ID，才能命中组件的数据集绑定。 */
  (e: 'filter-change', payload: { componentId: string; field: string; value: string | string[] | undefined }): void
  (e: 'time-filter-change', payload: { componentId: string; field: string; timeRange: TimeRangeValue | undefined; timeGranularity: TimeGranularity }): void
  (e: 'retry-component-query', componentId: string): void
}>()

const { t } = useI18n()

const ccBodyRef = ref<HTMLElement | null>(null)
const childWidthsBeforeContainerResize = new Map<string, number>()
let containerResizeStartWidth: number | undefined

watch(() => props.containerResizing, (resizing, wasResizing) => {
  const body = ccBodyRef.value
  if (resizing) {
    containerResizeStartWidth = props.component.position?.w
    childWidthsBeforeContainerResize.clear()
    if (cfg.value.layoutMode !== 'free') return
    for (const child of activeChildren.value) {
      const el = body?.querySelector<HTMLElement>(`[data-child="${child.id}"]`)
      const width = el?.offsetWidth ?? 0
      if (width > 0) childWidthsBeforeContainerResize.set(child.id, width)
    }
    return
  }
  if (!wasResizing) return
  const widthChanged = containerResizeStartWidth !== undefined
    && props.component.position?.w !== containerResizeStartWidth
  if (cfg.value.layoutMode === 'free' && widthChanged) {
    for (const child of activeChildren.value) {
      const width = childWidthsBeforeContainerResize.get(child.id)
      if (width && child.layout.widthPx !== width) child.layout.widthPx = width
    }
  }
  childWidthsBeforeContainerResize.clear()
  containerResizeStartWidth = undefined
}, { flush: 'sync' })

// ── 预览态「自适应宽度」：free 子卡片横向坐标按「当前内宽 / 基准内宽」等比映射 ──
// 基准内宽在挂载时捕获（预览默认 100% 模式 = 编辑器同款 1440 舞台，与保存布局时的
// 内宽一致）；开启自适应铺满后内宽变化，由 ResizeObserver 实时重算映射比例，宽度
// （col/12 百分比）与 left（px × 比例）因此完全自洽，不会重叠交错。编辑态恒为 1。
const baseCcBodyWidth = ref(0)
const currentCcBodyWidth = ref(0)
let ccBodyWidthObserver: ResizeObserver | null = null
const ccHScale = computed(() => {
  if (props.editable || !baseCcBodyWidth.value || !currentCcBodyWidth.value) return 1
  return currentCcBodyWidth.value / baseCcBodyWidth.value
})

onMounted(() => {
  if (props.editable || !ccBodyRef.value) return
  // 用 offsetWidth（布局宽）：预览窄视口下舞台有 zoom 视觉缩放，getBoundingClientRect 会被污染
  baseCcBodyWidth.value = ccBodyRef.value.offsetWidth
  if (typeof ResizeObserver === 'undefined') return
  ccBodyWidthObserver = new ResizeObserver(() => {
    const width = ccBodyRef.value?.offsetWidth ?? 0
    currentCcBodyWidth.value = width
    // 初次渲染/字体和栅格布局稳定前，ResizeObserver 可能报告与最终自然宽度不同的值。
    // 非自适应状态持续校准基准宽度；只有进入自适应后才冻结基准并缩放子组件位置。
    if (!props.previewFillWidth && width > 0) {
      baseCcBodyWidth.value = width
    } else if (!baseCcBodyWidth.value && width > 0) {
      baseCcBodyWidth.value = width
    }
  })
  ccBodyWidthObserver.observe(ccBodyRef.value)
})

onBeforeUnmount(() => {
  ccBodyWidthObserver?.disconnect()
  ccBodyWidthObserver = null
})

watch(() => props.previewFillWidth, (isFilling) => {
  if (!isFilling) {
    const width = ccBodyRef.value?.offsetWidth ?? 0
    if (width > 0) {
      baseCcBodyWidth.value = width
      currentCcBodyWidth.value = width
    }
  }
})

const rootRef = ref<HTMLElement | null>(null)
const selectedChildId = ref<string | null>(null)
const hoverChildId = ref<string | null>(null)
const movingId = ref<string | null>(null)
/** 子卡片拖出模式：跟随指针的提示标签（Teleport 到 body，避免被容器 overflow:hidden 裁剪） */
const dragOutChild = ref<{ childId: string; title: string; x: number; y: number; grabOffsetX: number; grabOffsetY: number } | null>(null)
const resizingId = ref<string | null>(null)
/**
 * 拖动/缩放中的子卡片预览盒（布局像素）：预览走响应式绑定而非直接改 DOM。
 * Vue 的 patchStyle 每次重渲染都会无条件重写全部样式键（无等值跳过），手动内联值
 * 会被重渲染冲回旧绑定值（如 hover 邻卡触发重渲染时），表现为拖动中盒子回缩跳动；
 * 让绑定本身渲染预览值即可从根上消除这一竞争，松手写回 layout 后数值不变、零跳变。
 */
const childPreview = ref<{ id: string; x: number; y: number; col: number; h: number; widthPx?: number } | null>(null)
const editingTab = ref<string | null>(null)
const editingTabTitle = ref('')
const tabEditInput = ref<HTMLInputElement | null>(null)
const editingChildId = ref<string | null>(null)
const editingChildTitle = ref('')
const childTitleInput = ref<HTMLInputElement | null>(null)

function childIconTitleStyle(child: InsightCombinationChild): ComponentTitleIconStyle | undefined {
  const preview = props.titleIconStylePreview
  return preview?.childId === child.id ? preview.style : child.titleIconStyle
}

function tabIconStylePreview(tab: { id: string }): ComponentTitleIconStyle | undefined {
  const preview = props.tabTitleIconStylePreview
  return preview?.componentId === props.component.id && preview.tabId === tab.id
    ? preview.titleIconStyle
    : undefined
}

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
    ...componentThemeStyle(props.dashboardTheme, 'combination', 0, props.component.themeAccentGroup, props.component.componentColor),
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
    componentColor: child.componentColor,
    visualStyle: child.visualStyle,
    position: { x: 0, y: 0, w: child.layout.col, h: child.layout.h ? Math.round(child.layout.h / 30) : 4 },
    chartType: child.chartType,
    config: child.config,
    tabs: child.tabs,
    dataSource: child.dataSource,
    children: child.children,
    containerConfig: child.containerConfig,
    boundFilterIds: child.boundFilterIds,
    enableTimeFilter: child.enableTimeFilter,
    multiKpi: child.multiKpi,
    kpiMetrics: child.kpiMetrics,
  }
}

function childWidgetComponent(child: InsightCombinationChild): InsightComponent {
  return projectPythonResultKpi(toWidgetComponent(child), props.componentDataMap?.[child.id])
}

function childComponentData(child: InsightCombinationChild): InsightComponentData | undefined {
  const actualData = props.componentDataMap?.[child.id]
  if (actualData) return actualData
  if (!isSampleData(child)) return undefined
  return resolveComponentSample(toWidgetComponent(child)).renderData
}

function queryDisplayStatus(data: InsightComponentData | undefined): 'loading' | 'empty' | 'timeout' | 'error' | undefined {
  if (data?.queryStatus && data.queryStatus !== 'success') return data.queryStatus
  return data?.error ? 'error' : undefined
}

function queryStateTitle(data: InsightComponentData | undefined): string {
  switch (queryDisplayStatus(data)) {
    case 'loading': return t('insight.componentQueryLoading')
    case 'empty': return t('insight.componentQueryEmpty')
    case 'timeout': return t('insight.componentQueryTimeout')
    default: return t('insight.componentQueryFailed')
  }
}

function isSampleData(child: InsightCombinationChild): boolean {
  if (['filter', 'timeFilter', 'combination'].includes(child.type)) return false
  if (props.componentDataMap?.[child.id]) return false
  return !hasConfiguredDataset(toWidgetComponent(child))
}

/** 子卡片定位样式 */
function childStyle(child: InsightCombinationChild): Record<string, string> {
  const visualStyle = resolveComponentVisualStyle(child.visualStyle, child.type)
  const themeStyle = componentThemeStyle(props.dashboardTheme, child.type, 1, child.themeAccentGroup, child.componentColor)
  if (cfg.value.layoutMode === 'free') {
    const l = childPreview.value && childPreview.value.id === child.id
      ? { ...child.layout, ...childPreview.value }
      : child.layout
    const widthPx = l.widthPx ?? (props.containerResizing ? childWidthsBeforeContainerResize.get(child.id) : undefined)
    return {
      ...themeStyle,
      ...visualStyle,
      position: 'absolute',
      // free 子卡片横向坐标按 ccHScale 等比映射（预览「自适应宽度」铺满后随画布一起加宽）；
      // 纵向 top/height 不映射，保证加宽后纵向布局不变、不重叠交错。
      left: `calc(${l.x}px * var(--cc-h-scale, 1))`,
      top: l.y + 'px',
      width: widthPx
        ? `${widthPx * (props.previewFillWidth ? ccHScale.value : 1)}px`
        : `calc(${l.col} / 12 * 100%)`,
      ...(l.h != null ? { height: l.h + 'px' } : {}),
    }
  }
  if (cfg.value.layoutMode === 'grid') {
    return { ...themeStyle, ...visualStyle, gridColumn: `span ${child.layout.col}` }
  }
  return { ...themeStyle, ...visualStyle }
}

// ── 拖入组合卡片（标题栏 HTML5 通道）──────────────────────
/**
 * 组合卡片接管两种 drop：组件库物料新增为当前组合子组件，画布顶层组件移动进当前组合。
 */
function onBodyDrop(e: DragEvent) {
  if (!props.editable || !e.dataTransfer) return
  const raw = e.dataTransfer.getData('application/json')
  if (!raw) return
  try {
    const payload = JSON.parse(raw) as {
      kind?: string
      componentId?: string
      componentType?: InsightComponentType
      type?: InsightComponentType
      chartType?: ChartType
    }
    if (payload.kind === 'canvas-component' && payload.componentId) {
      e.stopPropagation()
      e.preventDefault()
      const layout = defaultCombinationChildLayout(payload.componentType ?? 'kpi')
      const pos = dropPosFromEvent(e, layout.col, layout.h ?? 180)
      emit('move-component-into', { containerId: props.component.id, componentId: payload.componentId, ...pos })
    } else if (payload.type) {
      e.stopPropagation()
      e.preventDefault()
      const layout = defaultCombinationChildLayout(payload.type)
      const pos = dropPosFromEvent(e, layout.col, layout.h ?? 180)
      emit('add-component-into', {
        containerId: props.component.id,
        type: payload.type,
        chartType: payload.chartType,
        ...pos,
      })
    }
  } catch (err) {
    console.error('[CombinationCardWidget] drop parse error:', err)
  }
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
  const body = ccBodyRef.value
  if (!body) return null
  const r = body.getBoundingClientRect()
  // 画布 CSS zoom 下 getBoundingClientRect 是视觉像素，offsetWidth 才是布局像素。
  // 子卡片落格宽度是 col/12 百分比（布局像素体系），边界、列宽、内联 left/width 必须同基准，
  // 否则 zoom≠1 时拖动预览与松手落格差一个 zoom 系数（表现为松手跳变）。
  const width = body.offsetWidth || r.width
  const height = body.offsetHeight || r.height
  return width > 0 && height > 0 ? { width, height } : null
}

/** 画布缩放比（视觉像素/布局像素）：鼠标 clientX/Y 位移需除以它换算回布局像素。 */
function readBodyScale(): number {
  const body = ccBodyRef.value
  if (!body) return 1
  const r = body.getBoundingClientRect()
  const layout = body.offsetWidth
  return r.width > 0 && layout > 0 ? r.width / layout : 1
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

let mv: { id: string; sx: number; sy: number; ox: number; oy: number; ocol: number; oh: number; el: HTMLElement | null; bounds: ChildBounds | null; elW: number; elH: number; scale: number } | null = null
let mvRaf = 0
let mvLast: { x: number; y: number } | null = null

/** 起点 + 当前鼠标坐标 → 目标位置（拖动过程与落点共用，保证两者完全一致）。全程不读 DOM。 */
function computeMove(start: NonNullable<typeof mv>, last: { x: number; y: number }): { x: number; y: number } {
  const scale = start.scale || 1
  return clampBox(
    start.bounds ?? undefined,
    start.ox + (last.x - start.sx) / scale,
    start.oy + (last.y - start.sy) / scale,
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
    ocol: child.layout.col,
    oh: child.layout.h ?? 120,
    el,
    // 按下时一次性读布局，过程与落点提交复用（详见 readChildBounds 注释）
    bounds: readChildBounds(),
    elW: el?.offsetWidth ?? 0,
    elH: el?.offsetHeight ?? 0,
    scale: readBodyScale(),
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
  // 指针离开组合卡片内容区（含少量缓冲）→ 进入「拖出」模式：冻结容器内位置，
  // 显示跟随指针的提示标签；拖回容器内则恢复普通拖动。
  if (isOutsideBody(e.clientX, e.clientY)) {
    if (!dragOutChild.value) {
      const child = getActiveChildren().find((c) => c.id === mv.id)
      if (child) {
        const rect = mv.el?.getBoundingClientRect()
        dragOutChild.value = {
          childId: child.id,
          title: child.title,
          x: e.clientX,
          y: e.clientY,
          grabOffsetX: rect ? mv.sx - rect.left : 0,
          grabOffsetY: rect ? mv.sy - rect.top : 0,
        }
        movingId.value = null
      }
    } else {
      dragOutChild.value = { ...dragOutChild.value, x: e.clientX, y: e.clientY }
    }
    return
  }
  if (dragOutChild.value) dragOutChild.value = null
  if (mvRaf) return
  mvRaf = requestAnimationFrame(() => {
    mvRaf = 0
    if (!mv || !mvLast) return
    const p = computeMove(mv, mvLast)
    // 预览走响应式绑定（childStyle 优先读 childPreview），不直接改 DOM
    childPreview.value = { id: mv.id, x: p.x, y: p.y, col: mv.ocol, h: mv.oh }
  })
}

/** 指针是否已离开组合卡片内容区（rect 为缩放后的屏幕坐标，与 clientX/Y 同一坐标系） */
function isOutsideBody(clientX: number, clientY: number): boolean {
  const r = ccBodyRef.value?.getBoundingClientRect()
  if (!r || r.width <= 0 || r.height <= 0) return false
  const margin = 8
  return clientX < r.left - margin || clientX > r.right + margin
    || clientY < r.top - margin || clientY > r.bottom + margin
}

function onChildMouseUp() {
  if (mvRaf) { cancelAnimationFrame(mvRaf); mvRaf = 0 }
  if (dragOutChild.value) {
    const { childId, x, y, grabOffsetX, grabOffsetY } = dragOutChild.value
    dragOutChild.value = null
    emit('move-child-out', {
      containerId: props.component.id,
      childId,
      clientX: x - grabOffsetX,
      clientY: y - grabOffsetY,
    })
  } else if (mv && mvLast) {
    const child = getActiveChildren().find((c) => c.id === mv!.id)
    if (child) {
      const p = computeMove(mv, mvLast)
      child.layout.x = p.x
      child.layout.y = p.y
    }
  }
  mv = null
  mvLast = null
  // 同 tick 清预览：绑定随即渲染新 layout，数值与预览一致，零跳变
  childPreview.value = null
  movingId.value = null
  window.removeEventListener('mousemove', onChildMouseMove)
  window.removeEventListener('mouseup', onChildMouseUp)
}

// ── 八向缩放（预览走响应式 childPreview 绑定，落点提交 layout）──────
let rz: { id: string; dir: string; sx: number; sy: number; ox: number; oy: number; ocol: number; oh: number; el: HTMLElement | null; bounds: ChildBounds | null; colW: number; scale: number } | null = null
let rzRaf = 0
let rzLast: { x: number; y: number } | null = null

/** 起点 + 当前鼠标坐标 → 目标盒子（预览与落点共用），横向列宽按鼠标连续变化。 */
function computeResize(
  start: NonNullable<typeof rz>,
  last: { x: number; y: number },
  snap = true,
): { x: number; y: number; col: number; h: number } {
  const scale = start.scale || 1
  // 鼠标 clientX/Y 是视觉像素，先除以缩放比换算回布局像素，与 colW/bounds 同基准
  const layoutLast = {
    x: start.sx + (last.x - start.sx) / scale,
    y: start.sy + (last.y - start.sy) / scale,
  }
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
    layoutLast,
    snap,
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
    scale: readBodyScale(),
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
    if (!rz || !rzLast) return
    // 拖动预览与松手落格同用吸附结果（snap=true）且都渲染自绑定：所见即所得，松手零跳变
    const box = computeResize(rz, rzLast, false)
    const resizedChild = getActiveChildren().find((child) => child.id === rz!.id)
    childPreview.value = {
      id: rz.id, x: box.x, y: box.y, col: box.col, h: box.h,
      ...(resizedChild?.layout.widthPx != null ? { widthPx: box.col * rz.colW } : {}),
    }
  })
}
function onChildResizeUp() {
  if (rzRaf) { cancelAnimationFrame(rzRaf); rzRaf = 0 }
  if (rz && rzLast) {
    const child = getActiveChildren().find((c) => c.id === rz!.id)
    if (child) {
      const box = computeResize(rz, rzLast, false)
      child.layout.x = box.x
      child.layout.y = box.y
      child.layout.col = box.col
      child.layout.h = box.h
      if (child.layout.widthPx != null) child.layout.widthPx = box.col * rz.colW
    }
  }
  rz = null
  rzLast = null
  // 同 tick 清预览：绑定随即渲染新 layout，数值与预览一致，零跳变
  childPreview.value = null
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
  if (child?.type === 'filter' || child?.type === 'timeFilter') {
    emit('delete-child', { containerId: props.component.id, childId: id })
    return
  }
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
  border-bottom: 1px solid color-mix(in srgb, var(--db-border) 55%, transparent);
  background: transparent;
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
/* 空态内容（图标 + 文案）由共享 EmptyState 组件渲染 */

.cc-child {
  position: relative;
  box-sizing: border-box;
  border: var(--component-border, 1px solid color-mix(in srgb, var(--component-group-border, var(--db-border)) 55%, transparent));
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
.cc-child::before { content: ''; position: absolute; inset: 0; z-index: 2; border-radius: inherit; background: linear-gradient(to bottom, var(--component-group-accent, transparent) 0 6px, transparent 6px); pointer-events: none; }
.cc-body.mode-vertical .cc-child { position: relative; }
.cc-child.selected { border-color: var(--db-accent); box-shadow: 0 0 0 2px var(--db-accent-light); z-index: 5; }
.cc-child.moving { opacity: 0.85; }
/* 拖出模式：冻结容器内位置，交出视觉焦点给跟随指针的提示标签 */
.cc-child.moving.is-dragout { opacity: 0.45; }
/* 画布正在拖动顶层组件：整卡高亮为可放置落区 */
.cc-body.drop-hint { outline: 2px dashed var(--db-accent, var(--el-color-primary)); outline-offset: -2px; }
.cc-drop-hint {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  background: color-mix(in srgb, var(--db-accent, var(--el-color-primary)) 8%, transparent);
}
.cc-drop-hint span {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--db-accent, var(--el-color-primary));
  color: #fff;
  font-size: 12px;
  white-space: nowrap;
}
/* 子卡片拖出提示标签：跟随指针，位于 body 层级不受画布缩放/裁剪影响 */
.cc-dragout-ghost {
  position: fixed;
  z-index: 9999;
  pointer-events: none;
  padding: 5px 12px;
  border-radius: 8px;
  background: var(--db-accent, var(--el-color-primary));
  color: #fff;
  font-size: 12px;
  box-shadow: 0 4px 14px rgb(0 0 0 / 22%);
  white-space: nowrap;
}
.cc-child-head {
  position: relative;
  display: flex; align-items: center; justify-content: space-between;
  /* 标题条跟随子卡片表面色：自定义背景时整个子卡片（含标题条）统一变色；
     未自定义时 --component-surface 仍由主题注入（--db-surface-card），观感与原组头表面一致 */
  padding: 6px 10px; background: var(--component-surface, var(--component-group-header-surface, transparent)); border-bottom: 1px solid color-mix(in srgb, var(--component-group-border, var(--db-border)) 55%, transparent);
  border-radius: 7px 7px 0 0;
  flex-shrink: 0;
}
.cc-sample-data-watermark {
  position: absolute;
  z-index: 1;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border: 1px solid rgba(190, 45, 45, 0.18);
  border-radius: 999px;
  color: rgba(159, 37, 37, 0.68);
  background: rgba(255, 239, 239, 0.58);
  box-shadow: 0 1px 3px rgba(80, 20, 20, 0.04);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
  opacity: 0.72;
  filter: blur(0.15px);
  pointer-events: none;
  user-select: none;
}
.cc-child-title-trigger { display: inline-flex; align-items: center; gap: 4px; min-width: 0; border: none; padding: 0; background: transparent; color: inherit; cursor: text; }
.cc-child-title-group { display: inline-flex; align-items: center; gap: 2px; min-width: 0; }
.cc-child-title-group :deep(.dashboard-component-icon) { margin-right: 0; }
.cc-child-icon-style-trigger { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 20px; height: 20px; margin: 0 -4px 0 0; border: 0; border-radius: 3px; background: transparent; color: var(--db-text-muted); cursor: pointer; }
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
