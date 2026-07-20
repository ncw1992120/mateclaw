<template>
  <div
    class="dashboard-canvas"
    @dragover.prevent="handleDragOver"
    @drop.prevent="handleDrop"
  >
    <!-- 全局联动栏（仅预览态且有全局筛选器时显示） -->
    <div v-if="!editable && globalFilterComponents.length > 0" class="global-filter-bar">
      <div class="global-filter-items">
        <template v-for="comp in globalFilterComponents" :key="comp.id">
          <div class="global-filter-item">
            <span class="global-filter-label">{{ comp.title }}</span>
            <FilterSelectWidget
              v-if="comp.type === 'filter'"
              :component="comp"
              :show-title="false"
              @change="(payload) => handleFilterChange(comp.id, payload)"
            />
            <TimeFilterWidget
              v-else-if="comp.type === 'timeFilter'"
              :component="comp"
              :show-title="false"
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
      :is-draggable="editable"
      :is-resizable="editable"
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
        @click.stop="handleSelectComponent(item.i)"
      >
        <div
          class="grid-item-content mc-card grid-item-animated"
          :class="{ selected: selectedId === item.i, 'mc-card-hover': !editable }"
          :style="{ animationDelay: `${index * 40}ms` }"
        >
          <!-- 四边拖动热区（仅编辑态） -->
          <template v-if="editable">
            <div class="resize-handle resize-handle-top" @mousedown.stop="startResize($event, item.i, 'top')" />
            <div class="resize-handle resize-handle-right" @mousedown.stop="startResize($event, item.i, 'right')" />
            <div class="resize-handle resize-handle-bottom" @mousedown.stop="startResize($event, item.i, 'bottom')" />
            <div class="resize-handle resize-handle-left" @mousedown.stop="startResize($event, item.i, 'left')" />
          </template>
          <div v-if="editable" class="grid-item-toolbar">
            <span class="grid-item-title">{{ getComponentTitle(item.i) }}</span>
            <button class="grid-item-delete" @click.stop="handleDeleteComponent(item.i)">✕</button>
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
                :show-title="!editable"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
              />
              <ChartWidget
                v-else-if="getComponent(item.i)?.type === 'chart'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :show-title="!editable"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
              />
              <DataTableWidget
                v-else-if="getComponent(item.i)?.type === 'table'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :show-title="!editable"
                @component-time-range-change="(payload) => emit('component-time-range-change', payload)"
              />
              <FilterSelectWidget
                v-else-if="getComponent(item.i)?.type === 'filter'"
                :component="getComponent(item.i)!"
                :show-title="!editable"
                @change="(payload) => handleFilterChange(item.i, payload)"
              />
              <TimeFilterWidget
                v-else-if="getComponent(item.i)?.type === 'timeFilter'"
                :component="getComponent(item.i)!"
                :show-title="!editable"
                @change="(payload) => handleTimeFilterChange(item.i, payload)"
              />
              <AiAnalysisWidget
                v-else-if="getComponent(item.i)?.type === 'aiAnalysis'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
                :show-title="!editable"
                :generating="aiAnalysisGeneratingIds.has(item.i)"
                @generate="(id) => emit('ai-analysis-generate', id)"
              />
            </template>
          </div>
        </div>
      </GridItem>
    </GridLayout>
    <div v-if="gridLayout.length === 0 && globalFilterComponents.length === 0" class="canvas-empty">
      <div class="empty-icon">🎨</div>
      <div class="empty-text">{{ t('insight.canvasEmpty') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { GridLayout, GridItem } from 'grid-layout-plus'
import type { InsightComponent, InsightComponentType, ChartType, InsightComponentData, TimeRangeValue, FilterComponentConfig, TimeFilterComponentConfig } from '@/types'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import FilterSelectWidget from './FilterSelectWidget.vue'
import TimeFilterWidget from './TimeFilterWidget.vue'
import AiAnalysisWidget from './AiAnalysisWidget.vue'

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
  /** 正在生成 AI 分析的组件 ID 集合 */
  aiAnalysisGeneratingIds?: Set<string>
}>(), {
  aiAnalysisGeneratingIds: () => new Set(),
})

const emit = defineEmits<{
  (e: 'add-component', payload: { type: InsightComponentType; chartType?: ChartType }): void
  (e: 'update-layout', payload: Array<{ id: string; x: number; y: number; w: number; h: number }>): void
  (e: 'select-component', id: string): void
  (e: 'delete-component', id: string): void
  (e: 'filter-change', payload: { componentId: string; field: string; value: string }): void
  (e: 'time-filter-change', payload: { componentId: string; field: string; timeRange: TimeRangeValue }): void
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
  (e: 'ai-analysis-generate', componentId: string): void
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
  if (isSyncingFromProps || !props.editable) {
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

/** 根据 ID 获取组件渲染数据 */
function getComponentData(id: string): InsightComponentData | undefined {
  return props.componentDataMap?.[id]
}

/** 拖拽悬停（允许 drop） */
function handleDragOver(event: DragEvent): void {
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

/** 从物料面板拖入新组件 */
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
    emit('add-component', payload)
  } catch (e) {
    console.error('[DashboardCanvas] drop parse error:', e)
  }
}

/** 选中组件 */
function handleSelectComponent(id: string): void {
  emit('select-component', id)
}

/** 删除组件 */
function handleDeleteComponent(id: string): void {
  emit('delete-component', id)
}

/** 自定义边缘拖动状态 */
const resizingItem = ref<{ id: string; edge: string; startX: number; startY: number; startW: number; startH: number; startXPos: number; startYPos: number } | null>(null)

/** 开始自定义边缘拖动 */
function startResize(event: MouseEvent, id: string, edge: string): void {
  const comp = getComponent(id)
  if (!comp) return
  resizingItem.value = {
    id,
    edge,
    startX: event.clientX,
    startY: event.clientY,
    startW: comp.position.w,
    startH: comp.position.h,
    startXPos: comp.position.x,
    startYPos: comp.position.y,
  }
  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', handleResizeEnd)
}

/** 拖动中 */
function handleResizeMove(event: MouseEvent): void {
  if (!resizingItem.value) return
  const comp = getComponent(resizingItem.value.id)
  if (!comp) return

  const dx = event.clientX - resizingItem.value.startX
  const dy = event.clientY - resizingItem.value.startY
  const colWidth = 30 // 近似列宽
  const rowHeight = 30 // 行高

  let newW = resizingItem.value.startW
  let newH = resizingItem.value.startH
  let newX = resizingItem.value.startXPos
  let newY = resizingItem.value.startYPos

  if (resizingItem.value.edge === 'left') {
    const colDelta = Math.round(dx / colWidth)
    newW = Math.max(1, resizingItem.value.startW - colDelta)
    newX = resizingItem.value.startXPos
  } else if (resizingItem.value.edge === 'right') {
    const colDelta = Math.round(dx / colWidth)
    newW = Math.max(1, resizingItem.value.startW + colDelta)
    newX = resizingItem.value.startXPos
  } else if (resizingItem.value.edge === 'top') {
    const rowDelta = Math.round(dy / rowHeight)
    newH = Math.max(1, resizingItem.value.startH - rowDelta)
    newY = resizingItem.value.startYPos
  } else if (resizingItem.value.edge === 'bottom') {
    const rowDelta = Math.round(dy / rowHeight)
    newH = Math.max(1, resizingItem.value.startH + rowDelta)
    newY = resizingItem.value.startYPos
  }

  // 边界保护：确保组件不超出画布边界
  if (newX < 0) {
    newX = 0
  }
  if (newY < 0) {
    newY = 0
  }
  if (newX + newW > 24) {
    newW = 24 - newX
  }

  emit('update-layout', [{
    id: resizingItem.value.id,
    x: newX,
    y: newY,
    w: newW,
    h: newH,
  }])
}

/** 拖动结束 */
function handleResizeEnd(): void {
  resizingItem.value = null
  document.removeEventListener('mousemove', handleResizeMove)
  document.removeEventListener('mouseup', handleResizeEnd)
}

/** 筛选组件值变化 */
function handleFilterChange(componentId: string, payload: { field: string; value: string }): void {
  emit('filter-change', { componentId, field: payload.field, value: payload.value })
}

/** 时间筛选组件值变化 */
function handleTimeFilterChange(componentId: string, payload: { field: string; timeRange: TimeRangeValue }): void {
  emit('time-filter-change', { componentId, field: payload.field, timeRange: payload.timeRange })
}
</script>

<style scoped>
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
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-base), border-color var(--transition-fast);
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
  background: var(--db-hover);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.grid-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
