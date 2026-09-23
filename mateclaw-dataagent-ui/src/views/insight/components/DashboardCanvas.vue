<template>
  <div
    ref="canvasRef"
    class="dashboard-canvas"
    :class="{ 'is-resizing': isCustomResizing }"
    data-canvas-workspace="expanded"
    :style="{ ...canvasWorkspaceStyle, ...dashboardThemeVariables }"
    tabindex="0"
    @dragover.prevent="handleDragOver"
    @drop.prevent="handleDrop"
    @keydown="handleCanvasKeydown"
    @contextmenu.prevent="handleCanvasContextMenu"
  >
    <!-- 全局联动栏（仅预览态且有全局筛选器时显示） -->
    <div v-if="!editable && globalFilterComponents.length > 0" class="global-filter-bar">
      <div class="global-filter-items">
        <template v-for="(comp, index) in globalFilterComponents" :key="comp.id">
          <div class="global-filter-item">
            <span class="global-filter-label">
              <DashboardComponentIcon
                :type="comp.type"
                :title="comp.title"
                :dashboard-theme="dashboardTheme"
                :title-icon-style="comp.titleIconStyle"
                :variant="index"
              />{{ comp.title }}
            </span>
            <FilterSelectWidget
              v-if="comp.type === 'filter'"
              :component="{ ...comp, titleBarStyle: 'hidden' }"
              @change="(payload) => handleFilterChange(comp.id, payload)"
            />
            <TimeFilterWidget
              v-else-if="comp.type === 'timeFilter'"
              :component="{ ...comp, titleBarStyle: 'hidden' }"
              @change="(payload) => handleTimeFilterChange(comp.id, payload)"
            />
          </div>
        </template>
      </div>
    </div>

    <GridLayout
      v-if="gridLayout.length > 0"
      :layout="gridLayout"
      :col-num="24"
      :row-height="30"
      :is-draggable="editable && !isCustomResizing"
      :is-resizable="editable && !isCustomResizing"
      :vertical-compact="false"
      :margin="[12, 12]"
      @layout-updated="handleLayoutUpdated"
    >
      <GridItem
        v-for="(item, index) in gridLayout"
        :key="item.i"
        :i="item.i"
        :x="item.x"
        :y="item.y"
        :w="item.w"
        :h="item.h"
        :static="!editable"
        :drag-ignore-from="'a, button, .cc-child, .grid-item-toolbar'"
        @click.stop="handleSelectComponent(item.i)"
      >
        <div
          class="grid-item-content mc-card grid-item-animated"
          :data-component-id="item.i"
          tabindex="0"
          :class="{ selected: selectedId === item.i, 'mc-card-hover': !editable, 'inline-filter-component': isInlineFilterComponent(getComponent(item.i)) }"
          :style="{ ...componentThemeStyle(dashboardTheme, getComponent(item.i)?.type ?? 'kpi', 0, getComponent(item.i)?.themeAccentGroup), ...resolveComponentVisualStyle(getComponent(item.i)?.visualStyle, getComponent(item.i)?.type ?? 'kpi'), animationDelay: `${index * 40}ms` }"
          @keydown="handleComponentKeydown($event, item.i)"
          @contextmenu.stop.prevent="handleComponentContextMenu($event, item.i)"
        >
          <!-- 四边拖动热区（仅编辑态） -->
          <template v-if="editable">
            <div class="resize-handle resize-handle-top" @pointerdown.stop.prevent="startResize($event, item.i, 'top')" />
            <div class="resize-handle resize-handle-right" @pointerdown.stop.prevent="startResize($event, item.i, 'right')" />
            <div class="resize-handle resize-handle-bottom" @pointerdown.stop.prevent="startResize($event, item.i, 'bottom')" />
            <div class="resize-handle resize-handle-left" @pointerdown.stop.prevent="startResize($event, item.i, 'left')" />
          </template>
          <div
            v-if="editable"
            class="grid-item-toolbar"
            :class="`title-bar-${getComponent(item.i)?.titleBarStyle ?? 'standard'}`"
            draggable="true"
            @dragstart.stop="handleComponentDragStart($event, item.i)"
          >
            <template v-if="isToolbarTitleVisible(item.i)">
              <input
                v-if="editingTitleId === item.i"
                ref="titleInput"
                class="grid-item-title-input"
                aria-label="组件标题"
                v-model="editingTitleValue"
                @click.stop
                @blur="commitTitleEdit(item.i)"
                @keydown.esc.prevent="cancelTitleEdit"
                @keyup.enter="commitTitleEdit(item.i)"
              />
              <template v-else>
                <span class="grid-item-title-group">
                  <DashboardComponentIcon
                    :type="getComponent(item.i)?.type ?? 'kpi'"
                    :chart-type="getComponent(item.i)?.chartType"
                    :title="getComponentTitle(item.i)"
                    :dashboard-theme="dashboardTheme"
                    :title-icon-style="toolbarTitleIconStyle(item.i)"
                    :theme-accent-group="getComponent(item.i)?.themeAccentGroup"
                    :variant="sameRowIconVariant(item)"
                  />
                  <button
                    type="button"
                    class="grid-item-icon-style-trigger"
                    :aria-label="`编辑标题图标 ${getComponentTitle(item.i)}`"
                    title="修改标题图标样式"
                    @click.stop="openTitleIconStyle(item.i, $event.currentTarget as HTMLElement)"
                  >
                    <el-icon :size="12"><EditPen /></el-icon>
                  </button>
                  <button
                    type="button"
                    class="grid-item-title-trigger"
                    :aria-label="`编辑组件标题 ${getComponentTitle(item.i)}`"
                    @click.stop="startTitleEdit(item.i)"
                  >
                    <span class="grid-item-title">{{ getComponentTitle(item.i) }}</span>
                    <el-icon class="grid-item-title-edit" :size="11"><EditPen /></el-icon>
                  </button>
                </span>
              </template>
            </template>
            <button
              class="grid-item-delete"
              :aria-label="`删除组件 ${getComponentTitle(item.i)}`"
              @click.stop="handleDeleteComponent(item.i)"
            >✕</button>
          </div>
          <div class="grid-item-body">
            <div v-if="getComponentData(item.i)?.error" class="grid-item-error">
              {{ getComponentData(item.i)?.error }}
            </div>
            <template v-else>
              <KpiCardWidget
                v-if="getComponent(item.i)?.type === 'kpi'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :editable="editable"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                :tab-title-icon-style-preview="tabTitleIconStylePreview"
                @open-metric-style="(payload) => emit('open-metric-style', payload)"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
                @edit-tab-title-icon-style="openTabTitleIconStyle"
              />
              <ChartWidget
                v-else-if="getComponent(item.i)?.type === 'chart'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :editable="editable"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                :tab-title-icon-style-preview="tabTitleIconStylePreview"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
                @edit-tab-title-icon-style="openTabTitleIconStyle"
              />
              <DataTableWidget
                v-else-if="getComponent(item.i)?.type === 'table'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :editable="editable"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                :tab-title-icon-style-preview="tabTitleIconStylePreview"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
                @edit-tab-title-icon-style="openTabTitleIconStyle"
              />
              <FilterSelectWidget
                v-else-if="getComponent(item.i)?.type === 'filter'"
                :component="getWidgetComponent(item.i)!"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                @change="(payload) => handleFilterChange(item.i, payload)"
              />
              <TimeFilterWidget
                v-else-if="getComponent(item.i)?.type === 'timeFilter'"
                :component="getWidgetComponent(item.i)!"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                @change="(payload) => handleTimeFilterChange(item.i, payload)"
              />
              <AiAnalysisWidget
                v-else-if="getComponent(item.i)?.type === 'aiAnalysis'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :generating="aiAnalysisGeneratingIds.has(item.i)"
                :dashboard-theme="dashboardTheme"
                :title-icon-style-preview="componentTitleIconStylePreview"
                @generate="(id) => emit('ai-analysis-generate', id)"
              />
              <CombinationCardWidget
                v-else-if="getComponent(item.i)?.type === 'combination'"
                :component="getComponent(item.i)!"
                :component-data-map="componentDataMap"
                :editable="editable"
                :sample-mode="isSampleData(item.i)"
                :selected="selectedId === item.i"
                :dashboard-theme="dashboardTheme"
                :component-title-icon-style-preview="componentTitleIconStylePreview"
                :title-icon-style-preview="childTitleIconStylePreview"
                :tab-title-icon-style-preview="tabTitleIconStylePreview"
                @select-child="handleSelectChild"
                @add-tab="(p) => emit('combination-add-tab', p)"
                @remove-tab="(p) => emit('combination-remove-tab', p)"
                @move-component-into="(p) => emit('move-component-into', p)"
                @copy-child="(p) => emit('copy-child', p)"
                @paste-child="(p) => emit('paste-child', p)"
                @context-menu="(p) => emit('context-menu', { ...p, componentId: null })"
                @edit-child-title-icon-style="openChildTitleIconStyle"
                @edit-tab-title-icon-style="openTabTitleIconStyle"
              />
            </template>
            <div v-if="isSampleData(item.i)" class="sample-data-watermark" data-testid="sample-data-watermark" aria-label="当前展示的是样例数据">样例数据</div>
          </div>
        </div>
      </GridItem>
    </GridLayout>
    <DashboardTitleIconStyleDialog
      v-if="editingTitleIconTarget"
      :model-value="titleIconDialogVisible"
      :title="editingTitleIconTitle"
      :title-icon-style="editingTitleIconStyle"
      :default-color="editingTitleIconDefaultColor"
      :top="titleIconDialogTop"
      :left="titleIconDialogLeft"
      :draggable="true"
      @save="saveTitleIconStyle"
      @preview="previewTitleIconStyle"
      @update:model-value="setTitleIconDialogVisible"
    />

    <div v-if="gridLayout.length === 0 && globalFilterComponents.length === 0" class="canvas-empty">
      <div class="empty-icon" aria-hidden="true">—</div>
      <div class="empty-text">{{ t('insight.canvasEmpty') }}</div>
      <button v-if="editable" type="button" class="canvas-empty-action" aria-label="从组件库添加组件" @click="emit('add-component', { type: 'kpi' })">从组件库添加组件</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { EditPen } from '@element-plus/icons-vue'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import type { ComponentTitleIconStyle, InsightCombinationChild, InsightComponent, InsightComponentType, ChartType, InsightComponentData, TimeRangeValue, FilterComponentConfig, TimeFilterComponentConfig, ResolvedDashboardTheme } from '@/types'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import FilterSelectWidget from './FilterSelectWidget.vue'
import TimeFilterWidget from './TimeFilterWidget.vue'
import AiAnalysisWidget from './AiAnalysisWidget.vue'
import CombinationCardWidget from './CombinationCardWidget.vue'
import DashboardTitleIconStyleDialog from './DashboardTitleIconStyleDialog.vue'
import { DASHBOARD_CANVAS_MIN_HEIGHT, DASHBOARD_CANVAS_MIN_WIDTH } from './dashboardCanvasConstants'
import { themeCssVariables, componentThemeStyle, componentIconStyle } from '@/utils/dashboard-theme'
import { resolveComponentVisualStyle } from '@/utils/component-visual-style'
import { hasConfiguredDataset, resolveComponentSample } from '@/utils/component-sample-data'
import { calculateGridResize, type GridResizeEdge, type GridResizeMetrics } from './dashboardCanvasResize'

defineOptions({
  name: 'DashboardCanvas',
})

const { t } = useI18n()

const props = withDefaults(defineProps<{
  /** 仪表盘组件列表 */
  components: InsightComponent[]
  /** 组件渲染数据映射（componentId -> data） */
  componentDataMap?: Record<string, InsightComponentData>
  /** 是否可编辑 */
  editable?: boolean
  /** 当前选中的组件 ID */
  selectedId?: string
  /** 当前仪表盘解析后的主题 */
  dashboardTheme?: ResolvedDashboardTheme
  /** 正在生成 AI 分析的组件 ID 集合 */
  aiAnalysisGeneratingIds?: Set<string>
}>(), {
  aiAnalysisGeneratingIds: () => new Set(),
})

/** 画布根元素（drop 落点换算用） */
const canvasRef = ref<HTMLElement | null>(null)
const editingTitleId = ref<string | null>(null)
const editingTitleValue = ref('')
const titleInput = ref<HTMLInputElement | null>(null)
const titleIconDialogVisible = ref(false)
const editingTitleIconTarget = ref<{
  componentId: string
  containerId?: string
  childId?: string
  tabId?: string
  tabKind?: 'component' | 'combination'
} | null>(null)
const titleIconDialogTop = ref('16px')
const titleIconDialogLeft = ref('16px')
const previewTitleIconStyleValue = ref<ComponentTitleIconStyle | null>(null)

const componentTitleIconStylePreview = computed(() => {
  const target = editingTitleIconTarget.value
  return target && !target.tabId && !target.childId && previewTitleIconStyleValue.value
    ? previewTitleIconStyleValue.value
    : undefined
})

const tabTitleIconStylePreview = computed(() => {
  const target = editingTitleIconTarget.value
  return target?.tabId && target.tabKind && previewTitleIconStyleValue.value
    ? { componentId: target.componentId, tabId: target.tabId, titleIconStyle: previewTitleIconStyleValue.value }
    : undefined
})

const editingTitleIconComponent = computed(() => {
  const target = editingTitleIconTarget.value
  if (!target) return undefined
  if (!target.childId) return findCanvasComponent(target.componentId)
  return findChildComponent(target.containerId ?? '', target.childId)
})
const editingTitleIconStyle = computed(() => {
  const target = editingTitleIconTarget.value
  const owner = editingTitleIconComponent.value
  if (!target?.tabId || !owner) return owner?.titleIconStyle
  if (target.tabKind === 'component') return owner.tabs?.find((tab) => tab.id === target.tabId)?.titleIconStyle
  return owner.containerConfig?.tabs.find((tab) => tab.id === target.tabId)?.titleIconStyle
})
const editingTitleIconTitle = computed(() => {
  const target = editingTitleIconTarget.value
  const owner = editingTitleIconComponent.value
  if (target?.tabId && owner) {
    if (target.tabKind === 'component') return owner.tabs?.find((tab) => tab.id === target.tabId)?.title ?? ''
    return owner.containerConfig?.tabs.find((tab) => tab.id === target.tabId)?.title ?? ''
  }
  return owner?.title ?? ''
})
const editingTitleIconDefaultColor = computed(() => {
  const target = editingTitleIconTarget.value
  const owner = editingTitleIconComponent.value
  const type = target?.tabId ? 'tab' : owner?.type ?? 'kpi'
  const iconStyle = componentIconStyle(
    props.dashboardTheme,
    type,
    editingTitleIconTitle.value,
    0,
    owner?.themeAccentGroup,
  )
  return iconStyle['--dashboard-icon-color'] ?? props.dashboardTheme?.textSecondary ?? '#8c4a2f'
})
const childTitleIconStylePreview = computed(() => {
  const target = editingTitleIconTarget.value
  return target?.childId && previewTitleIconStyleValue.value
    ? { childId: target.childId, style: previewTitleIconStyleValue.value }
    : undefined
})

const canvasWorkspaceStyle = computed(() => {
  if (!props.editable) return undefined
  return {
    minWidth: `${DASHBOARD_CANVAS_MIN_WIDTH}px`,
    minHeight: `${DASHBOARD_CANVAS_MIN_HEIGHT}px`,
  }
})
const dashboardThemeVariables = computed(() => props.dashboardTheme ? themeCssVariables(props.dashboardTheme) : {})

const emit = defineEmits<{
  (e: 'add-component', payload: { type: InsightComponentType; chartType?: ChartType; position?: { x: number; y: number } }): void
  (e: 'update-layout', payload: Array<{ id: string; x: number; y: number; w: number; h: number }>): void
  (e: 'select-component', id: string): void
  (e: 'rename-component', payload: { componentId: string; title: string }): void
  (e: 'select-child', payload: { containerId: string; childId: string | null }): void
  (e: 'combination-add-tab', payload: { containerId: string }): void
  (e: 'combination-remove-tab', payload: { containerId: string; tabId: string }): void
  (e: 'move-component-into', payload: { containerId: string; componentId: string; x: number; y: number }): void
  (e: 'copy-child', payload: { containerId: string; childId: string }): void
  (e: 'paste-child', payload: { containerId: string; childId: string | null }): void
  (e: 'delete-component', id: string): void
  (e: 'copy-component', id: string): void
  (e: 'paste-component'): void
  (e: 'context-menu', payload: { componentId: string | null; containerId?: string; childId?: string; x: number; y: number }): void
  (e: 'filter-change', payload: { componentId: string; field: string; value: string | string[] | undefined }): void
  (e: 'time-filter-change', payload: { componentId: string; field: string; timeRange: TimeRangeValue }): void
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
  (e: 'ai-analysis-generate', componentId: string): void
  (e: 'open-metric-style', payload: { componentId: string; fieldKey: string }): void
  (e: 'update-title-icon-style', payload: { componentId: string; titleIconStyle: ComponentTitleIconStyle }): void
  (e: 'update-child-title-icon-style', payload: { containerId: string; childId: string; titleIconStyle: ComponentTitleIconStyle }): void
  (e: 'update-tab-title-icon-style', payload: { componentId: string; tabId: string; tabKind: 'component' | 'combination'; titleIconStyle: ComponentTitleIconStyle }): void
}>()

/** grid-layout-plus 需要的布局格式 */
interface GridLayoutItem {
  i: string
  x: number
  y: number
  w: number
  h: number
}

/** 用 ref 管理布局，传给 grid-layout-plus 的 :layout prop */
const gridLayout = ref<GridLayoutItem[]>([])

/** 标记：是否正在从 props 同步，避免 layout-updated → emit → props 变化 → watch 循环 */
let isSyncingFromProps = false

/** 判断筛选器组件是否为全局作用范围 */
function isGlobalFilterComponent(c: InsightComponent): boolean {
  if (c.type === 'filter') {
    const config = c.config as FilterComponentConfig | undefined
    return !config?.scope || config.scope === 'global'
  }
  if (c.type === 'timeFilter') {
    const config = c.config as TimeFilterComponentConfig | undefined
    return !config?.scope || config.scope === 'global'
  }
  return false
}

/** 全局筛选器组件列表（预览态下提取到顶部联动栏独立渲染） */
const globalFilterComponents = computed<InsightComponent[]>(() => {
  if (props.editable) return []
  return props.components.filter((c) => isGlobalFilterComponent(c))
})

/** 全局筛选器组件 ID 集合 */
const globalFilterComponentIds = computed<Set<string>>(() => {
  return new Set(globalFilterComponents.value.map((c) => c.id))
})

/** 当前实际可见的组件列表（编辑态显示全部；预览态下全局筛选器提取到联动栏） */
const effectiveComponents = computed<InsightComponent[]>(() => {
  if (props.editable) {
    return props.components
  }
  // 预览态：排除全局筛选器（它们在联动栏中渲染）
  return props.components.filter((c) => !globalFilterComponentIds.value.has(c.id))
})

/** 构建 grid-layout-plus 所需的布局（预览态去除全局筛选器后整体向上补齐，避免顶部留白） */
function buildGridLayout(components: InsightComponent[]): GridLayoutItem[] {
  if (components.length === 0) {
    return []
  }
  if (props.editable) {
    return components.map((c) => ({
      i: c.id,
      x: c.position.x,
      y: c.position.y,
      w: c.position.w,
      h: c.position.h,
    }))
  }
  const minY = Math.min(...components.map((c) => c.position.y))
  return components.map((c) => ({
    i: c.id,
    x: c.position.x,
    y: c.position.y - minY,
    w: c.position.w,
    h: c.position.h,
  }))
}

/** 从 effectiveComponents 同步到 gridLayout */
watch(
  () => effectiveComponents.value.map((c) => `${c.id}:${c.position.x},${c.position.y},${c.position.w},${c.position.h}`).join('|'),
  () => {
    isSyncingFromProps = true
    gridLayout.value = buildGridLayout(effectiveComponents.value)
    // 使用双重 nextTick 确保 grid-layout-plus 内部 layout-updated 事件在标记有效期内触发
    nextTick(() => {
      nextTick(() => {
        isSyncingFromProps = false
      })
    })
  },
  { immediate: true }
)

/** 布局更新回调（拖拽/缩放/compact 后触发） */
function handleLayoutUpdated(newLayout: GridLayoutItem[]): void {
  // 始终更新本地 gridLayout，让 :layout prop 与 GridLayout 内部 currentLayout 保持一致
  // 避免重渲染时 :layout 传旧值导致位置被重置
  gridLayout.value = newLayout

  // 仅在编辑态且非 props 同步时 emit 给 Editor（预览态为 static，不应回写 schema）
  if (isSyncingFromProps || !props.editable || isCustomResizing.value) {
    return
  }

  // 过滤掉非用户操作导致的位置变更（如 grid-layout-plus 内部碰撞下推产生的副作用）
  // 只保留用户主动拖拽/缩放产生的真实变化
  const realChanges = newLayout.filter((item) => {
    const comp = props.components.find((c) => c.id === item.i)
    if (!comp) return true
    return comp.position.x !== item.x
      || comp.position.y !== item.y
      || comp.position.w !== item.w
      || comp.position.h !== item.h
  })
  if (realChanges.length === 0) return

  // 向上 emit 让 Editor 更新 schema.components 的 position
  emit(
    'update-layout',
    realChanges.map((item) => ({ id: item.i, x: item.x, y: item.y, w: item.w, h: item.h }))
  )
}

/** 根据 ID 获取组件 */
function getComponent(id: string): InsightComponent | undefined {
  return props.components.find((c) => c.id === id)
}

/** 根据 ID 获取组件标题 */
function getComponentTitle(id: string): string {
  return getComponent(id)?.title ?? ''
}

/** 同一行组件按从左到右轮换同主题深色阶，避免并列组件视觉黏连。 */
function sameRowIconVariant(item: GridLayoutItem): number {
  return gridLayout.value
    .filter(candidate => candidate.y === item.y)
    .sort((left, right) => left.x - right.x)
    .findIndex(candidate => candidate.i === item.i)
}

function startTitleEdit(id: string): void {
  if (!props.editable) return
  editingTitleId.value = id
  editingTitleValue.value = getComponentTitle(id)
  void nextTick(() => {
    const input = titleInput.value as unknown as { focus?: () => void; select?: () => void } | null
    input?.focus?.()
    input?.select?.()
  })
}

function findChildComponent(containerId: string, childId: string): InsightCombinationChild | undefined {
  const visit = (children: InsightCombinationChild[] | undefined): InsightCombinationChild | undefined => {
    for (const child of children ?? []) {
      if (child.id === childId) return child
      const nested = visit([
        ...(child.children ?? []),
        ...((child.containerConfig?.tabs ?? []).flatMap((tab) => tab.children)),
      ])
      if (nested) return nested
    }
    return undefined
  }
  const container = props.components.find((component) => component.id === containerId)
  if (!container) return undefined
  return visit([
    ...(container.children ?? []),
    ...((container.containerConfig?.tabs ?? []).flatMap((tab) => tab.children)),
  ])
}

function findCanvasComponent(componentId: string): InsightComponent | InsightCombinationChild | undefined {
  for (const component of props.components) {
    if (component.id === componentId) return component
    const visit = (children: InsightCombinationChild[] | undefined): InsightCombinationChild | undefined => {
      for (const child of children ?? []) {
        if (child.id === componentId) return child
        const nested = visit([
          ...(child.children ?? []),
          ...((child.containerConfig?.tabs ?? []).flatMap((tab) => tab.children)),
        ])
        if (nested) return nested
      }
      return undefined
    }
    const child = visit([
      ...(component.children ?? []),
      ...((component.containerConfig?.tabs ?? []).flatMap((tab) => tab.children)),
    ])
    if (child) return child
  }
  return undefined
}

function openTitleIconStyle(componentId: string, anchor?: HTMLElement | null): void {
  if (!props.editable) return
  previewTitleIconStyleValue.value = null
  editingTitleIconTarget.value = { componentId }
  setTitleIconDialogPosition(anchor)
  titleIconDialogVisible.value = true
}

function openChildTitleIconStyle(payload: { containerId: string; childId: string; anchor?: HTMLElement }): void {
  if (!props.editable) return
  previewTitleIconStyleValue.value = null
  editingTitleIconTarget.value = { ...payload, componentId: payload.containerId }
  setTitleIconDialogPosition(payload.anchor)
  titleIconDialogVisible.value = true
}

function openTabTitleIconStyle(payload: {
  componentId: string
  tabId: string
  tabKind: 'component' | 'combination'
  anchor?: HTMLElement
}): void {
  if (!props.editable) return
  previewTitleIconStyleValue.value = null
  editingTitleIconTarget.value = payload
  setTitleIconDialogPosition(payload.anchor)
  titleIconDialogVisible.value = true
}

function setTitleIconDialogPosition(anchor?: HTMLElement | null): void {
  if (!anchor) {
    titleIconDialogTop.value = '16px'
    titleIconDialogLeft.value = '16px'
    return
  }
  const rect = anchor.getBoundingClientRect()
  const viewportHeight = window.innerHeight || 768
  const viewportWidth = window.innerWidth || 1024
  const dialogWidth = Math.min(520, viewportWidth - 32)
  const maxTop = Math.max(16, viewportHeight - 520)
  let top = rect.bottom + 12
  if (top > maxTop) top = Math.max(16, rect.top - 520 - 12)
  const left = Math.min(Math.max(16, rect.left), Math.max(16, viewportWidth - dialogWidth - 16))
  titleIconDialogTop.value = `${Math.round(top)}px`
  titleIconDialogLeft.value = `${Math.round(left)}px`
}

function toolbarTitleIconStyle(componentId: string): ComponentTitleIconStyle | undefined {
  const target = editingTitleIconTarget.value
  if (!target?.childId && target?.componentId === componentId && previewTitleIconStyleValue.value) {
    return previewTitleIconStyleValue.value
  }
  return getComponent(componentId)?.titleIconStyle
}

function previewTitleIconStyle(style: ComponentTitleIconStyle): void {
  previewTitleIconStyleValue.value = style
}

function clearTitleIconDialog(): void {
  previewTitleIconStyleValue.value = null
  editingTitleIconTarget.value = null
  titleIconDialogVisible.value = false
}

function setTitleIconDialogVisible(visible: boolean): void {
  if (visible) {
    titleIconDialogVisible.value = true
    return
  }
  clearTitleIconDialog()
}

function saveTitleIconStyle(style: ComponentTitleIconStyle): void {
  const target = editingTitleIconTarget.value
  if (!target) return
  if (target.tabId && target.tabKind) {
    emit('update-tab-title-icon-style', {
      componentId: target.componentId,
      tabId: target.tabId,
      tabKind: target.tabKind,
      titleIconStyle: style,
    })
  } else if (target.childId && target.containerId) {
    emit('update-child-title-icon-style', {
      containerId: target.containerId,
      childId: target.childId,
      titleIconStyle: style,
    })
  } else {
    emit('update-title-icon-style', { componentId: target.componentId, titleIconStyle: style })
  }
  clearTitleIconDialog()
}

function commitTitleEdit(id: string): void {
  if (editingTitleId.value !== id) return
  const title = editingTitleValue.value.trim()
  if (title) emit('rename-component', { componentId: id, title })
  editingTitleId.value = null
  editingTitleValue.value = ''
}

function cancelTitleEdit(): void {
  editingTitleId.value = null
  editingTitleValue.value = ''
}

/** 编辑态工具栏标题是否展示（组合卡片「显示标题」关闭时隐藏，让开关在编辑态可见生效） */
function isToolbarTitleVisible(id: string): boolean {
  const comp = getComponent(id)
  if (!comp) return true
  return isComponentTitleVisible(comp) && !isInlineFilterComponent(comp)
}

function isInlineFilterComponent(comp: InsightComponent | undefined): boolean {
  return comp?.type === 'filter' || comp?.type === 'timeFilter'
}

function isComponentTitleVisible(comp: InsightComponent | undefined): boolean {
  return Boolean(comp) && comp?.titleBarStyle !== 'hidden'
}

/** 根据 ID 获取组件渲染数据 */
function getComponentData(id: string): InsightComponentData | undefined {
  const configured = props.componentDataMap?.[id]
  if (configured) return configured
  const component = getComponent(id)
  if (!component || hasConfiguredDataset(component)) return undefined
  return resolveComponentSample(component).renderData
}

function isSampleData(id: string): boolean {
  const component = getComponent(id)
  return Boolean(component && !props.componentDataMap?.[id] && !hasConfiguredDataset(component))
}

function getWidgetComponent(id: string): InsightComponent | undefined {
  const component = getComponent(id)
  if (!component || !isSampleData(id) || component.type !== 'filter') return component
  const sample = JSON.parse(resolveComponentSample(component).json) as { data?: { options?: Array<{ label: string; value: string }> } }
  return {
    ...component,
    config: { ...(component.config ?? {}), optionSource: 'static', staticOptions: sample.data?.options ?? [] },
  }
}

/** 拖拽悬停（允许 drop） */
function handleDragOver(event: DragEvent): void {
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

/** 组件标题栏是进入组合容器的专用拖拽把手，避免和画布栅格移动冲突。 */
function handleComponentDragStart(event: DragEvent, componentId: string): void {
  if (!props.editable || !event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/json', JSON.stringify({
    kind: 'canvas-component',
    componentId,
    componentType: getComponent(componentId)?.type,
  }))
}

/** 栅格参数（与 GridLayout 的 col-num / row-height / margin 保持一致） */
const GRID_COLS = 24
const GRID_ROW_HEIGHT = 30
const GRID_GAP = 12

/**
 * 鼠标 drop 位置 → 栅格坐标。
 * 换算公式与 grid-layout-plus 的排布一致：
 *   colWidth = (容器宽 - margin × (cols + 1)) / cols；left = margin + col × (colWidth + margin)
 * 优先用 .vgl-layout 网格容器的边界换算（列宽随画布宽度自适应），
 * 空画布没有网格容器时退化为画布根元素边界。列坐标钳制到 [0, COLS-1]，
 * 行坐标只钳下界（画布可向下无限增长）。
 */
function dropToGrid(event: DragEvent): { x: number; y: number } {
  const grid = canvasRef.value?.querySelector<HTMLElement>('.vgl-layout')
  const rect = (grid ?? canvasRef.value)?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  const colWidth = (rect.width - GRID_GAP * (GRID_COLS + 1)) / GRID_COLS
  if (colWidth <= 0) return { x: 0, y: 0 }
  const col = Math.floor(((event.clientX - rect.left) - GRID_GAP) / (colWidth + GRID_GAP))
  const row = Math.floor(((event.clientY - rect.top) - GRID_GAP) / (GRID_ROW_HEIGHT + GRID_GAP))
  return {
    x: Math.max(0, Math.min(col, GRID_COLS - 1)),
    y: Math.max(0, row),
  }
}

/** 从物料面板拖入新组件（携带鼠标落点的栅格坐标，由编辑器按此放置） */
function handleDrop(event: DragEvent): void {
  if (!event.dataTransfer) {
    return
  }
  const raw = event.dataTransfer.getData('application/json')
  if (!raw) {
    return
  }
  try {
    const payload = JSON.parse(raw) as { type: InsightComponentType; chartType?: ChartType }
    if ((payload as { kind?: string }).kind === 'canvas-component') return
    emit('add-component', { ...payload, position: dropToGrid(event) })
  } catch (e) {
    console.error('[DashboardCanvas] drop parse error:', e)
  }
}

/** 选中组件 */
function handleSelectComponent(id: string): void {
  emit('select-component', id)
}

function handleComponentKeydown(event: KeyboardEvent, id: string): void {
  if (!props.editable) return
  if ((event.ctrlKey || event.metaKey) && !event.altKey) {
    if (event.key.toLowerCase() === 'c') {
      event.preventDefault()
      event.stopPropagation()
      emit('copy-component', id)
      return
    }
    if (event.key.toLowerCase() === 'v') {
      event.preventDefault()
      event.stopPropagation()
      emit('paste-component')
      return
    }
  }
  const item = gridLayout.value.find((entry) => entry.i === id)
  if (!item) return
  const direction = event.key
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(direction)) return
  event.preventDefault()
  const next = { ...item }
  if (event.shiftKey) {
    if (direction === 'ArrowRight') next.w += 1
    if (direction === 'ArrowLeft') next.w = Math.max(1, next.w - 1)
    if (direction === 'ArrowDown') next.h += 1
    if (direction === 'ArrowUp') next.h = Math.max(1, next.h - 1)
  } else {
    if (direction === 'ArrowRight') next.x += 1
    if (direction === 'ArrowLeft') next.x = Math.max(0, next.x - 1)
    if (direction === 'ArrowDown') next.y += 1
    if (direction === 'ArrowUp') next.y = Math.max(0, next.y - 1)
  }
  gridLayout.value = gridLayout.value.map((entry) => entry.i === id ? next : entry)
  emit('update-layout', gridLayout.value.map((entry) => ({ id: entry.i, x: entry.x, y: entry.y, w: entry.w, h: entry.h })))
}

function handleComponentContextMenu(event: MouseEvent, id: string): void {
  if (!props.editable) return
  emit('context-menu', { componentId: id, x: event.clientX, y: event.clientY })
}

function handleCanvasKeydown(event: KeyboardEvent): void {
  if (!props.editable || event.target !== canvasRef.value) return
  if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'v') {
    event.preventDefault()
    event.stopPropagation()
    emit('paste-component')
  }
}

function handleCanvasContextMenu(event: MouseEvent): void {
  if (!props.editable) return
  emit('context-menu', { componentId: null, x: event.clientX, y: event.clientY })
}

/** 组合卡片子组件选中/取消（透传给编辑器，联动属性面板） */
function handleSelectChild(payload: { containerId: string; childId: string | null }): void {
  emit('select-child', payload)
}

/** 删除组件 */
function handleDeleteComponent(id: string): void {
  emit('delete-component', id)
}

/** 自定义边缘拖动状态 */
const resizingItem = ref<{
  id: string
  edge: GridResizeEdge
  startX: number
  startY: number
  startW: number
  startH: number
  startXPos: number
  startYPos: number
  pointerId: number
  metrics: GridResizeMetrics
} | null>(null)

/** 是否正在自定义拉伸（期间禁用 grid-layout-plus 内置拖拽/缩放，避免碰撞检测推走位置） */
const isCustomResizing = ref(false)
let resizeRaf = 0
let resizeLastPoint: { x: number; y: number } | null = null

function readGridResizeMetrics(): GridResizeMetrics {
  const grid = canvasRef.value?.querySelector<HTMLElement>('.vgl-layout')
  const gridWidth = grid?.getBoundingClientRect().width ?? canvasRef.value?.getBoundingClientRect().width ?? 0
  return { gridWidth, columns: GRID_COLS, marginX: GRID_GAP, rowHeight: GRID_ROW_HEIGHT, marginY: GRID_GAP }
}

/** 开始自定义边缘拖动 */
function startResize(event: PointerEvent, id: string, edge: GridResizeEdge): void {
  const item = gridLayout.value.find((entry) => entry.i === id)
  if (!item) return
  event.preventDefault()
  event.stopPropagation()
  isCustomResizing.value = true
  resizingItem.value = {
    id,
    edge,
    startX: event.clientX,
    startY: event.clientY,
    startW: item.w,
    startH: item.h,
    startXPos: item.x,
    startYPos: item.y,
    pointerId: event.pointerId,
    metrics: readGridResizeMetrics(),
  }
  document.addEventListener('pointermove', handleResizeMove)
  document.addEventListener('pointerup', handleResizeEnd)
}

function applyResizePoint(point: { x: number; y: number }): void {
  const start = resizingItem.value
  if (!start) return
  const result = calculateGridResize({
    edge: start.edge,
    startX: start.startX,
    startY: start.startY,
    startW: start.startW,
    startH: start.startH,
    startXPos: start.startXPos,
    startYPos: start.startYPos,
    dx: point.x - start.startX,
    dy: point.y - start.startY,
  }, start.metrics)
  const item = gridLayout.value.find((g) => g.i === start.id)
  if (item) Object.assign(item, { x: result.newX, y: result.newY, w: result.newW, h: result.newH })
}

/** 拖动中：合并到下一帧，避免每个 pointermove 都触发布局重算。 */
function handleResizeMove(event: PointerEvent): void {
  if (!resizingItem.value || event.pointerId !== resizingItem.value.pointerId) return
  event.preventDefault()
  resizeLastPoint = { x: event.clientX, y: event.clientY }
  if (!resizeRaf) {
    resizeRaf = requestAnimationFrame(() => {
      resizeRaf = 0
      if (resizeLastPoint) applyResizePoint(resizeLastPoint)
    })
  }
}

/** 拖动结束：将最终结果 emit 给父组件持久化 */
function handleResizeEnd(event?: PointerEvent): void {
  if (event && resizingItem.value && event.pointerId !== resizingItem.value.pointerId) return
  if (resizeRaf) {
    cancelAnimationFrame(resizeRaf)
    resizeRaf = 0
  }
  if (resizeLastPoint) applyResizePoint(resizeLastPoint)
  if (resizingItem.value) {
    const item = gridLayout.value.find((g) => g.i === resizingItem.value.id)
    if (item) {
      emit('update-layout', [{
        id: resizingItem.value.id,
        x: item.x,
        y: item.y,
        w: item.w,
        h: item.h,
      }])
    }
  }
  resizeLastPoint = null
  resizingItem.value = null
  isCustomResizing.value = false
  document.removeEventListener('pointermove', handleResizeMove)
  document.removeEventListener('pointerup', handleResizeEnd)
}

/** 筛选组件值变化 */
function handleFilterChange(componentId: string, payload: { field: string; value: string | string[] | undefined }): void {
  emit('filter-change', { componentId, field: payload.field, value: payload.value })
}

/** 时间筛选组件值变化 */
function handleTimeFilterChange(componentId: string, payload: { field: string; timeRange: TimeRangeValue }): void {
  emit('time-filter-change', { componentId, field: payload.field, timeRange: payload.timeRange })
}
</script>

<style scoped>
/* 覆盖 grid-layout-plus 默认拖拽占位色（库内联 --vgl-placeholder-bg: red / opacity 20%）
   → 改为品牌强调色低透明：既无粉红残影，又保留「松手会落在哪里」的落点反馈 */
.dashboard-canvas :deep(.vgl-layout) {
  --vgl-placeholder-bg: var(--db-accent);
  --vgl-placeholder-opacity: 0.08;
  --vgl-placeholder-radius: 8px;
}

.dashboard-canvas {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: var(--space-xl);
  box-sizing: border-box;
  background: var(--db-bg);
  display: flex;
  flex-direction: column;
}

.global-filter-bar {
  flex-shrink: 0;
  margin-bottom: var(--space-lg);
  animation: fadeIn var(--transition-base) both;
}

.global-filter-items {
  display: flex;
  align-items: flex-end;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.global-filter-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-width: 200px;
}

.global-filter-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--db-text-secondary);
  white-space: nowrap;
}

.global-filter-item :deep(.filter-select-widget),
.global-filter-item :deep(.time-filter-widget) {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  padding: var(--space-sm) var(--space-md);
  min-width: 180px;
  transition: border-color var(--transition-fast);
}

.global-filter-item :deep(.filter-select-widget:hover),
.global-filter-item :deep(.time-filter-widget:hover) {
  border-color: var(--db-border-strong);
}

.global-filter-item :deep(.filter-select-widget) {
  height: auto;
}

.global-filter-item :deep(.time-filter-widget) {
  height: auto;
}

.grid-item-animated {
  opacity: 0;
  animation: fadeIn var(--transition-base) both;
}

.grid-item-content {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  background: var(--component-surface, var(--component-group-surface, var(--db-surface-card, var(--db-card))));
  border: var(--component-border, 1px solid var(--component-group-border, var(--db-border)));
  border-radius: var(--component-radius, var(--radius-lg));
  box-shadow: var(--component-shadow, var(--shadow-card));
  padding: var(--component-padding, 0px);
  transition: box-shadow var(--transition-base), border-color var(--transition-fast);
}
.grid-item-content::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  z-index: 2;
  height: 2px;
  background: var(--component-group-accent, transparent);
  pointer-events: none;
}

.dashboard-canvas.is-resizing,
.dashboard-canvas.is-resizing * {
  user-select: none;
}

.dashboard-canvas.is-resizing .grid-item-content {
  transition: none;
}

/* 扩大 resizer 热区 */
.grid-item-content :deep(.vgl-item__resizer) {
  width: 20px;
  height: 20px;
  right: 0;
  bottom: 0;
}

.grid-item-content :deep(.vgl-item__resizer::before) {
  inset: 0 3px 3px 0;
  border-right-width: 3px;
  border-bottom-width: 3px;
}

/* 自定义边缘拖动热区 */
.resize-handle {
  position: absolute;
  z-index: 10;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.resize-handle-top {
  top: 0;
  left: 8px;
  right: 8px;
  height: 6px;
  cursor: ns-resize;
}

.resize-handle-right {
  right: 0;
  top: 8px;
  bottom: 8px;
  width: 6px;
  cursor: ew-resize;
}

.resize-handle-bottom {
  bottom: 0;
  left: 8px;
  right: 8px;
  height: 6px;
  cursor: ns-resize;
}

.resize-handle-left {
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 6px;
  cursor: ew-resize;
}

.grid-item-content:hover .resize-handle {
  opacity: 1;
}

/* 边缘拖动时的视觉指示 */
.resize-handle-top::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  background: var(--db-accent);
  border-radius: 2px;
  opacity: 0.6;
}

.resize-handle-right::before {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background: var(--db-accent);
  border-radius: 2px;
  opacity: 0.6;
}

.resize-handle-bottom::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  background: var(--db-accent);
  border-radius: 2px;
  opacity: 0.6;
}

.resize-handle-left::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background: var(--db-accent);
  border-radius: 2px;
  opacity: 0.6;
}

.grid-item-content:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
}

.grid-item-content.selected {
  border-color: var(--db-accent);
  box-shadow: 0 0 0 2px var(--db-accent-light), var(--shadow-card-hover);
}

.editable .grid-item-content {
  border-style: dashed;
}

.editable .grid-item-content:hover {
  border-style: solid;
  border-color: var(--db-accent);
}

.editable .grid-item-content.selected {
  border-style: solid;
}

.grid-item-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  background: var(--db-surface-nested, var(--db-hover));
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  cursor: grab;
}

.grid-item-toolbar:active {
  cursor: grabbing;
}

.grid-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-item-title-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  border: none;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: text;
}

.grid-item-title-group {
  display: inline-flex;
  align-items: center;
  flex: 0 1 auto;
  min-width: 0;
  gap: 2px;
}

.grid-item-title-group :deep(.dashboard-component-icon) {
  margin-right: 0;
}

.grid-item-icon-style-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  margin: 0 -4px 0 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--db-text-muted);
  cursor: pointer;
}
.grid-item-icon-style-trigger:hover {
  background: var(--db-hover);
  color: var(--db-accent);
}
.grid-item-icon-style-trigger:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 1px;
}

.grid-item-title-edit {
  color: var(--db-text-muted);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.grid-item-title-trigger:hover .grid-item-title-edit {
  opacity: 0.8;
}

.grid-item-title-input {
  min-width: 120px;
  max-width: 280px;
  height: 24px;
  border: 1px solid var(--db-accent);
  border-radius: var(--radius-sm);
  padding: 2px 6px;
  background: var(--db-surface-control, var(--db-card));
  color: var(--db-text);
  font-size: 13px;
  outline: none;
}

.grid-item-delete {
  border: none;
  background: transparent;
  color: var(--db-text-muted);
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  line-height: 1;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.grid-item-delete:hover {
  background: var(--db-danger-bg);
  color: var(--db-danger);
}

.grid-item-body {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.sample-data-watermark {
  position: absolute;
  z-index: 5;
  top: 8px;
  right: 8px;
  padding: 3px 8px;
  border: 1px solid rgba(210, 55, 55, 0.16);
  border-radius: 4px;
  color: rgba(190, 45, 45, 0.48);
  background: rgba(255, 235, 235, 0.35);
  font-size: 11px;
  line-height: 1.4;
  letter-spacing: 0.04em;
  pointer-events: none;
  user-select: none;
}

/* 筛选器自身提供横向标题与控件布局，编辑态工具栏只保留删除入口。 */
.grid-item-content.inline-filter-component .grid-item-toolbar {
  position: absolute;
  inset: 0;
  z-index: 3;
  min-height: 0;
  height: 0;
  padding: 0;
  border: 0;
  background: transparent;
  pointer-events: none;
}

.grid-item-content.inline-filter-component .grid-item-delete {
  position: absolute;
  top: 6px;
  right: 8px;
  pointer-events: auto;
}

.grid-item-content.inline-filter-component .grid-item-body {
  height: 100%;
}

.grid-item-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-md);
  color: var(--db-danger);
  font-size: 13px;
  text-align: center;
  background: var(--db-danger-bg);
  border-radius: var(--radius-lg);
}

.canvas-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  color: var(--db-text-muted);
  text-align: center;
}

.canvas-empty .empty-icon {
  font-size: 56px;
  line-height: 1;
  opacity: 0.8;
}

.canvas-empty .empty-text {
  font-size: 14px;
}

@media (max-width: 767px) {
  .dashboard-canvas {
    padding: var(--space-md);
  }

  .global-filter-items {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-sm);
  }

  .global-filter-item {
    min-width: auto;
  }
}
</style>
