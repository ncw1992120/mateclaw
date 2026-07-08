<template>
  <div class="insight-editor-view">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <el-input
          v-model="dashboardName"
          class="toolbar-name-input"
          size="small"
          :placeholder="t('insight.editor')"
          @change="handleNameChange"
        />
        <el-input
          v-model="dashboardDescription"
          class="toolbar-desc-input"
          size="small"
          :placeholder="t('insight.description')"
          @change="handleDescriptionChange"
        />
        <el-input
          v-model="dashboardOwnerName"
          class="toolbar-owner-input"
          size="small"
          :placeholder="t('insight.ownerName')"
          @change="handleOwnerNameChange"
        />
      </div>
      <div class="toolbar-right">
        <el-button @click="handleSave" :loading="saving">{{ t('insight.save') }}</el-button>
        <el-button @click="handlePreview" :disabled="!dashboard">{{ t('insight.preview') }}</el-button>
      </div>
    </div>

    <!-- 三栏布局 -->
    <div class="editor-body">
      <div class="editor-palette">
        <ComponentPalette />
      </div>
      <div class="editor-canvas">
        <DashboardCanvas
          :components="schema.components"
          :component-data-map="componentDataMap"
          :editable="true"
          :selected-id="selectedComponentId"
          @add-component="handleAddComponent"
          @update-layout="handleUpdateLayout"
          @select-component="handleSelectComponent"
          @delete-component="handleDeleteComponent"
        />
      </div>
      <div class="editor-property">
        <PropertyPanel
          :component="selectedComponent"
          @change="handleComponentChange"
          @preview="handlePreviewResult"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { InsightDashboardSchema, InsightComponent, InsightComponentType, ChartType, InsightComponentData } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import * as insightDashboardApi from '@/api/insight-dashboard'
import ComponentPalette from './components/ComponentPalette.vue'
import DashboardCanvas from './components/DashboardCanvas.vue'
import PropertyPanel from './components/PropertyPanel.vue'

defineOptions({
  name: 'InsightDashboardEditorView',
})

const props = defineProps<{
  /** 仪表盘 ID */
  dashboardId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const { t } = useI18n()
const store = useInsightDashboardStore()

const dashboard = computed(() => store.currentDashboard)
const saving = ref(false)
const selectedComponentId = ref<string>('')
const dashboardName = ref('')
const dashboardDescription = ref('')
const dashboardOwnerName = ref('')

/** 本地 Schema 副本 */
const schema = reactive<InsightDashboardSchema>({
  version: '1.0',
  components: [],
})

/** 组件渲染数据映射（编辑模式自动预览） */
const componentDataMap = ref<Record<string, InsightComponentData>>({})

/** 预览防抖定时器 */
let previewTimer: ReturnType<typeof setTimeout> | null = null

/** 当前选中的组件 */
const selectedComponent = computed<InsightComponent | null>(() => {
  if (!selectedComponentId.value) {
    return null
  }
  return schema.components.find((c) => c.id === selectedComponentId.value) ?? null
})

onMounted(async () => {
  await store.selectDashboard(props.dashboardId)
  if (dashboard.value) {
    dashboardName.value = dashboard.value.name
    dashboardDescription.value = dashboard.value.description ?? ''
    dashboardOwnerName.value = dashboard.value.ownerName ?? ''
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson) as InsightDashboardSchema
      schema.version = parsed.version ?? '1.0'
      schema.components = parsed.components ?? []
    } catch {
      // Schema 解析失败时使用空 Schema
      schema.components = []
    }
  }
})

/** 监听 dashboardId 变化时重新加载 */
watch(
  () => props.dashboardId,
  async (newId) => {
    if (newId) {
      await store.selectDashboard(newId)
      if (dashboard.value) {
        dashboardName.value = dashboard.value.name
        dashboardDescription.value = dashboard.value.description ?? ''
        dashboardOwnerName.value = dashboard.value.ownerName ?? ''
        try {
          const parsed = JSON.parse(dashboard.value.schemaJson) as InsightDashboardSchema
          schema.version = parsed.version ?? '1.0'
          schema.components = parsed.components ?? []
        } catch {
          schema.components = []
        }
      }
    }
  }
)

/** 生成组件 ID */
function generateId(): string {
  return `comp_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 生成默认标题 */
function getDefaultTitle(type: InsightComponentType, chartType?: ChartType): string {
  const titleMap: Record<string, string> = {
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
  }
  const key = type === 'chart' && chartType ? `chart-${chartType}` : type
  return titleMap[key] ?? type
}

/** 添加新组件 */
function handleAddComponent(payload: { type: InsightComponentType; chartType?: ChartType }): void {
  const maxY = schema.components.reduce((max, c) => Math.max(max, c.position.y + c.position.h), 0)
  const newComponent: InsightComponent = {
    id: generateId(),
    type: payload.type,
    title: getDefaultTitle(payload.type, payload.chartType),
    position: { x: 0, y: maxY, w: 6, h: 4 },
    chartType: payload.chartType,
    dataSource: payload.type !== 'filter' && payload.type !== 'timeFilter' ? {
      datasourceId: '',
      metrics: [],
      dimensions: [],
      filters: [],
      limit: 100,
    } : undefined,
    config: payload.type === 'timeFilter' ? {
      field: 'metric_time',
      availablePresets: ['today', '7d', '30d', '90d', 'custom'],
    } : undefined,
  }
  schema.components.push(newComponent)
  selectedComponentId.value = newComponent.id
}

/** 更新布局（拖动/缩放后） */
function handleUpdateLayout(layout: Array<{ id: string; x: number; y: number; w: number; h: number }>): void {
  layout.forEach((item) => {
    const comp = schema.components.find((c) => c.id === item.id)
    if (comp) {
      comp.position = { x: item.x, y: item.y, w: item.w, h: item.h }
    }
  })
}

/** 选中组件 */
function handleSelectComponent(id: string): void {
  selectedComponentId.value = id
}

/** 删除组件 */
function handleDeleteComponent(id: string): void {
  const idx = schema.components.findIndex((c) => c.id === id)
  if (idx >= 0) {
    schema.components.splice(idx, 1)
    if (selectedComponentId.value === id) {
      selectedComponentId.value = ''
    }
  }
}

/** 组件属性变更（保留画布管理的 position） */
function handleComponentChange(updated: InsightComponent): void {
  const idx = schema.components.findIndex((c) => c.id === updated.id)
  if (idx >= 0) {
    const existing = schema.components[idx]
    schema.components[idx] = {
      ...updated,
      // 保留画布拖拽/缩放管理的 position，不被属性面板覆盖
      position: existing.position,
    }
  }
  // 数据源变更时触发自动预览
  schedulePreview()
}

/** 处理属性面板验证数据结果，写入 componentDataMap 让画布组件渲染 */
function handlePreviewResult(data: InsightComponentData): void {
  if (data.componentId) {
    componentDataMap.value[data.componentId] = data
  }
}

/** 延迟预览：数据源变更后 500ms 自动获取组件数据 */
function schedulePreview(): void {
  if (previewTimer) {
    clearTimeout(previewTimer)
  }
  previewTimer = setTimeout(() => {
    previewAllConfiguredComponents()
  }, 500)
}

/** 为所有已配置数据源的组件获取预览数据 */
async function previewAllConfiguredComponents(): Promise<void> {
  const tasks = schema.components
    .filter((c) => c.type !== 'filter' && c.type !== 'timeFilter' && c.dataSource?.datasourceId && c.dataSource?.metrics?.length)
    .map(async (c) => {
      try {
        const result = await insightDashboardApi.previewComponent(c) as unknown as InsightComponentData
        componentDataMap.value[c.id] = result
      } catch (e: any) {
        componentDataMap.value[c.id] = {
          componentId: c.id,
          renderType: 'table',
          error: e.message ?? '预览失败',
        }
      }
    })
  await Promise.allSettled(tasks)
}

/** 保存仪表盘 */
async function handleSave(): Promise<void> {
  if (!dashboard.value) {
    return
  }
  saving.value = true
  try {
    await store.updateDashboard(dashboard.value.id, {
      name: dashboardName.value,
      description: dashboardDescription.value,
      ownerName: dashboardOwnerName.value,
      schemaJson: JSON.stringify(schema),
    })
    ElMessage.success(t('insight.saveSuccess'))
  } catch {
    ElMessage.error(t('insight.saveFailed'))
  } finally {
    saving.value = false
  }
}

/** 名称变更时自动保存 */
function handleNameChange(): void {
  if (dashboard.value && dashboardName.value.trim()) {
    store.updateDashboard(dashboard.value.id, { name: dashboardName.value.trim() }).catch(() => {
      // 静默失败
    })
  }
}

/** 描述变更时自动保存 */
function handleDescriptionChange(): void {
  if (dashboard.value) {
    store.updateDashboard(dashboard.value.id, { description: dashboardDescription.value }).catch(() => {
      // 静默失败
    })
  }
}

/** 负责人变更时自动保存 */
function handleOwnerNameChange(): void {
  if (dashboard.value) {
    store.updateDashboard(dashboard.value.id, { ownerName: dashboardOwnerName.value }).catch(() => {
      // 静默失败
    })
  }
}

/** 预览 */
function handlePreview(): void {
  handleSave().then(() => {
    emit('back')
  })
}

/** 返回列表 */
function handleBack(): void {
  emit('back')
}
</script>

<style scoped>
.insight-editor-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-bg);
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-name-input {
  width: 200px;
  font-size: 15px;
  font-weight: 600;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--theme-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--theme-text);
    font-weight: 600;
  }
}

.toolbar-desc-input {
  width: 300px;
  font-size: 13px;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--theme-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--theme-text-secondary);
  }
}

.toolbar-owner-input {
  width: 120px;
  font-size: 13px;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--theme-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--theme-text-secondary);
  }
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-palette {
  width: 200px;
  flex-shrink: 0;
  overflow: hidden;
}

.editor-canvas {
  flex: 1;
  overflow: hidden;
}

.editor-property {
  width: 280px;
  flex-shrink: 0;
  overflow: hidden;
}
</style>
