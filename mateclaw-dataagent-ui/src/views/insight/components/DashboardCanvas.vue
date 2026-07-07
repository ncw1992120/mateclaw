<template>
  <div
    class="dashboard-canvas"
    @dragover.prevent="handleDragOver"
    @drop.prevent="handleDrop"
  >
    <GridLayout
      v-if="components.length > 0"
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
              />
              <ChartWidget
                v-else-if="getComponent(item.i)?.type === 'chart'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
              />
              <DataTableWidget
                v-else-if="getComponent(item.i)?.type === 'table'"
                :component="getComponent(item.i)!"
                :component-data="getComponentData(item.i)"
              />
              <FilterSelectWidget
                v-else-if="getComponent(item.i)?.type === 'filter'"
                :component="getComponent(item.i)!"
              />
            </template>
          </div>
        </div>
      </GridItem>
    </GridLayout>
    <div v-else class="canvas-empty">
      <div class="empty-icon">🎨</div>
      <div class="empty-text">{{ t('insight.canvasEmpty') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { GridLayout, GridItem } from 'grid-layout-plus'
import type { InsightComponent, InsightComponentType, ChartType, InsightComponentData } from '@/types'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import FilterSelectWidget from './FilterSelectWidget.vue'

defineOptions({
  name: 'DashboardCanvas',
})

const { t } = useI18n()

const props = defineProps<{
  /** 仪表盘组件列表 */
  components: InsightComponent[]
  /** 组件渲染数据映射（componentId -> data） */
  componentDataMap?: Record<string, InsightComponentData>
  /** 是否可编辑 */
  editable?: boolean
  /** 当前选中的组件 ID */
  selectedId?: string
}>()

const emit = defineEmits<{
  (e: 'add-component', payload: { type: InsightComponentType; chartType?: ChartType }): void
  (e: 'update-layout', payload: Array<{ id: string; x: number; y: number; w: number; h: number }>): void
  (e: 'select-component', id: string): void
  (e: 'delete-component', id: string): void
}>()

/** grid-layout-plus 需要的布局格式 */
interface GridLayoutItem {
  i: string
  x: number
  y: number
  w: number
  h: number
}

/** 将 InsightComponent 转为 GridLayoutItem */
const gridLayout = computed<GridLayoutItem[]>(() => {
  return props.components.map((c) => ({
    i: c.id,
    x: c.position.x,
    y: c.position.y,
    w: c.position.w,
    h: c.position.h,
  }))
})

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

/** 布局更新（拖动/缩放后），仅在布局实际变化时 emit，避免无限循环 */
function handleLayoutUpdated(newLayout: GridLayoutItem[]): void {
  // 比较新旧布局，只有位置/尺寸真正变化才向上 emit
  const changed = newLayout.some((item) => {
    const comp = props.components.find((c) => c.id === item.i)
    if (!comp) {
      return true
    }
    return comp.position.x !== item.x
      || comp.position.y !== item.y
      || comp.position.w !== item.w
      || comp.position.h !== item.h
  })
  if (!changed) {
    return
  }
  emit(
    'update-layout',
    newLayout.map((item) => ({ id: item.i, x: item.x, y: item.y, w: item.w, h: item.h }))
  )
}

/** 选中组件 */
function handleSelectComponent(id: string): void {
  emit('select-component', id)
}

/** 删除组件 */
function handleDeleteComponent(id: string): void {
  emit('delete-component', id)
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
