<template>
  <div class="insight-editor-view">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <span class="toolbar-title">{{ dashboard?.name ?? t('insight.editor') }}</span>
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
import type { InsightDashboardSchema, InsightComponent, InsightComponentType, ChartType } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
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

/** 本地 Schema 副本 */
const schema = reactive<InsightDashboardSchema>({
  version: '1.0',
  components: [],
})

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
    table: t('insight.component.table'),
    filter: t('insight.component.filter'),
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
    dataSource: payload.type !== 'filter' ? {
      datasourceId: '',
      metrics: [],
      dimensions: [],
      filters: [],
      limit: 100,
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

/** 组件属性变更 */
function handleComponentChange(updated: InsightComponent): void {
  const idx = schema.components.findIndex((c) => c.id === updated.id)
  if (idx >= 0) {
    schema.components[idx] = updated
  }
}

/** 保存仪表盘 */
async function handleSave(): Promise<void> {
  if (!dashboard.value) {
    return
  }
  saving.value = true
  try {
    await store.updateDashboard(dashboard.value.id, {
      schemaJson: JSON.stringify(schema),
    })
    ElMessage.success(t('insight.saveSuccess'))
  } catch {
    ElMessage.error(t('insight.saveFailed'))
  } finally {
    saving.value = false
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

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
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
