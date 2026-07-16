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
      :vertical-compact="true"
      :margin="[8, 8]"
      @layout-updated="handleLayoutUpdated"
    >
      <GridItem
        v-for="item in gridLayout"
        :key="item.i"
        :i="item.i"
        :x="item.x"
        :y="item.y"
        :w="item.w"
        :h="item.h"
        :static="!editable"
        @click.stop="handleSelectComponent(item.i)"
      >
        <div class="grid-item-content" :class="{ selected: selectedId === item.i }">
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

/** 从 effectiveComponents 同步到 gridLayout（组件增删时触发） */
watch(
  () => effectiveComponents.value.map((c) => c.id).join(','),
  () => {
    isSyncingFromProps = true
    gridLayout.value = effectiveComponents.value.map((c) => ({
      i: c.id,
      x: c.position.x,
      y: c.position.y,
      w: c.position.w,
      h: c.position.h,
    }))
    // nextTick 后重置标记，确保 grid-layout-plus 内部 layoutUpdate 触发时标记已清除
    nextTick(() => {
      isSyncingFromProps = false
    })
  },
  { immediate: true }
)

/** 布局更新回调（拖拽/缩放/compact 后触发） */
function handleLayoutUpdated(newLayout: GridLayoutItem[]): void {
  // 始终更新本地 gridLayout，让 :layout prop 与 GridLayout 内部 currentLayout 保持一致
  // 避免重渲染时 :layout 传旧值导致位置被重置
  gridLayout.value = newLayout

  // 仅在非 props 同步时 emit 给 Editor
  if (isSyncingFromProps) {
    return
  }

  // 检查是否有实际变化，避免无意义 emit
  const changed = newLayout.some((item) => {
    const comp = props.components.find((c) => c.id === item.i)
    if (!comp) return true
    return comp.position.x !== item.x
      || comp.position.y !== item.y
      || comp.position.w !== item.w
      || comp.position.h !== item.h
  })
  if (!changed) return

  // 向上 emit 让 Editor 更新 schema.components 的 position
  emit(
    'update-layout',
    newLayout.map((item) => ({ id: item.i, x: item.x, y: item.y, w: item.w, h: item.h }))
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
  padding: 16px;
  box-sizing: border-box;
  background: var(--theme-bg);
  display: flex;
  flex-direction: column;
}

.global-filter-bar {
  flex-shrink: 0;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
}

.global-filter-items {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.global-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
}

.global-filter-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  flex-shrink: 0;
}

.global-filter-item :deep(.filter-select-widget),
.global-filter-item :deep(.time-filter-widget) {
  background: transparent;
  border: none;
  padding: 0;
  min-width: 180px;
}

.global-filter-item :deep(.filter-select-widget) {
  height: auto;
}

.global-filter-item :deep(.time-filter-widget) {
  height: auto;
}

.grid-item-content {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  box-sizing: border-box;
}

.grid-item-content.selected {
  border-color: var(--main-orange);
}

.grid-item-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  background: var(--theme-surface-elevated);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.grid-item-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-item-delete {
  border: none;
  background: transparent;
  color: var(--theme-text-muted);
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1;
}

.grid-item-delete:hover {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
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
  padding: 12px;
  color: var(--el-color-danger);
  font-size: 13px;
  text-align: center;
}

.canvas-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--theme-text-muted);
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}
</style>
