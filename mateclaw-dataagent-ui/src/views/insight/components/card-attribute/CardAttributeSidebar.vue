<template>
  <div class="card-attr-sidebar">
    <!-- 原型版「卡片属性配置」面板（唯一交互依据：docs/策略解读/原型设计.md §10~§12） -->
    <AttributePanel />

    <!-- 各配置弹窗：内部各自绑定 state.ui.*.visible，由 AttributePanel / DatasetCard 触发 -->
    <DataSourceTreeDialog />
    <JdbcSqlDialog />
    <AloudataDialog />
    <ApiConfigDialog />
    <FileConfigDialog />
    <FieldMappingDialog />
    <PythonScriptDialog />
    <PreviewDialog v-if="state.ui.preview.kind !== 'result'" />
    <PythonResultDataDialog v-else :component="component" />
    <MetricConfigDialog />
    <MetricStyleDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { InsightComponent, InsightDashboardSchema } from '@/types'
import { useInsight } from './useInsight'
import type { ResultSetStatus } from './useInsight'
import { hydratePanel, panelToPipeline, buildComponentPatch } from './useCardAttributeBridge'
import type { PanelFilterComponent } from './useCardAttributeBridge'
import { writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import AttributePanel from './AttributePanel.vue'
import DataSourceTreeDialog from './DataSourceTreeDialog.vue'
import JdbcSqlDialog from './dataset/JdbcSqlDialog.vue'
import AloudataDialog from './dataset/AloudataDialog.vue'
import ApiConfigDialog from './dataset/ApiConfigDialog.vue'
import FileConfigDialog from './dataset/FileConfigDialog.vue'
import FieldMappingDialog from './FieldMappingDialog.vue'
import PythonScriptDialog from './PythonScriptDialog.vue'
import PreviewDialog from './PreviewDialog.vue'
import PythonResultDataDialog from './PythonResultDataDialog.vue'
import MetricConfigDialog from './MetricConfigDialog.vue'
import MetricStyleDialog from './MetricStyleDialog.vue'
import { useComponentPropertyDraft } from '../property/useComponentPropertyDraft'

const props = defineProps<{
  /** 当前选中的数据组件（kpi / chart / table） */
  component: InsightComponent | null
  /** 仪表盘 ID（用于后端联调态与回显） */
  dashboardId: string
  executionPolicy?: InsightDashboardSchema['executionPolicy']
  /** 当前页面筛选组件索引，用于历史 pipeline 绑定回显与保存兼容 */
  filterComponents?: PanelFilterComponent[]
}>()

/** 结果集状态变化负载：交给画布侧渲染卡片或标记数据过期 */
export interface ResultSetEmitPayload {
  componentId: string
  status: ResultSetStatus
  source: 'dataset' | 'script'
  rows: Record<string, unknown>[]
  fieldLabels?: Record<string, string>
  error: string
}

const emit = defineEmits<{
  (e: 'change', component: InsightComponent): void
  (e: 'resultset', payload: ResultSetEmitPayload): void
}>()

const { state, scheduleResultSet } = useInsight()
// 面板仍复用 useInsight 的领域动作，但最终组件回写经过独立草稿控制器，
// 避免切换组件时把未确认的配置引用带到下一张卡片。
const propertyDraft = useComponentPropertyDraft()

/** 灌入中标记：避免 hydrate 重置 state 时误触发回写 */
const hydrating = ref(false)

/** 选中组件变化时，把该组件的 datasetPipeline + 字段回显到面板单例 state */
function hydrate(): void {
  const component = props.component
  if (!component) return
  hydrating.value = true
  hydratePanel(component, props.dashboardId, props.filterComponents ?? [], props.executionPolicy)
  propertyDraft.load(component)
  // 下一拍解除标记：让本次 state 同步（reactive 赋值）先完成，再允许回写
  nextTick(() => {
    hydrating.value = false
    // 旧版多指标 KPI 只保存 multiKpi，画布仍渲染 legacy kpiList，
    // 而属性面板已经按结果集投影出 kpiMetrics。首次选中时把投影回写组件，
    // 使画布切换到支持单项拖动和样式编辑的新渲染分支。
    if (component.type === 'kpi' && !component.kpiMetrics?.length && state.kpiMetrics.length > 0) {
      scheduleEmit()
    }
  })
}

watch(() => props.component?.id, hydrate, { immediate: true })

/**
 * 画布上的指标拖拽/缩放会【原地】修改组件的 kpiMetrics 布局（x/y/w/h），
 * 这里把布局增量同步回面板 state，避免「指标配置」弹窗重建投影时用旧布局覆盖画布改动。
 * 仅在值真正变化时写入，防止 state → 组件回写 → watcher 再触发的循环。
 */
watch(
  () => props.component?.kpiMetrics,
  (metrics) => {
    if (hydrating.value || !metrics) return
    metrics.forEach((m) => {
      const s = state.kpiMetrics.find((x) => x.fieldKey === m.fieldKey)
      if (!s) return
      if (s.x !== m.x) s.x = m.x
      if (s.y !== m.y) s.y = m.y
      if (s.w !== m.w) s.w = m.w
      if (s.h !== m.h) s.h = m.h
    })
  },
  { deep: true },
)

/** 面板状态变更 → 回写为组件级 datasetPipeline + 组件字段补丁 */
let emitTimer: ReturnType<typeof setTimeout> | null = null
function scheduleEmit(): void {
  if (hydrating.value || !props.component) return
  if (emitTimer) clearTimeout(emitTimer)
  emitTimer = setTimeout(() => {
    if (!props.component) return
    const pipeline = panelToPipeline()
    const patch = buildComponentPatch(props.component)
    const next = writeComponentDatasetPipeline(patch, pipeline)
    propertyDraft.load(next)
    emit('change', propertyDraft.commit() ?? next)
  }, 300)
}

// 仅监听数据字段（datasets / 筛选器绑定 / Python / 卡片元信息 / KPI 指标分组），避开 state.ui 弹窗开关引发的噪声
watch(
  () => [state.datasets, state.filterBindings, state.pythonUser, state.cards, state.kpiMetrics, state.finalResultQueryConfig],
  () => scheduleEmit(),
  { deep: true },
)

/**
 * 输入配置变更 → 结果集标记过期；无脚本时防抖自动重算，有脚本时等用户点「生成结果集」。
 * 只监听「产出结果集的输入」（数据集 / 筛选器绑定 / 脚本），卡片标题等元信息不影响数据。
 */
watch(
  () => [
    // lastQueryState 是「查看数据」的运行时输入，仅需随 Schema 保存；它不改变
    // 组件结果集的静态查询定义，不能因此自动重跑 KPI / 图表结果校验。
    state.datasets.map((dataset) => ({
      id: dataset.id,
      sourceType: dataset.sourceType,
      backendDatasetId: dataset.backendDatasetId,
      alias: dataset.alias,
      fields: dataset.fields,
      schema: dataset.schema,
      filters: dataset.filters,
      jdbc: dataset.jdbc,
      aloudata: dataset.aloudata,
      api: dataset.api,
      file: dataset.file,
      queryConfig: dataset.queryConfig,
    })),
    state.filterBindings,
    state.pythonUser,
    state.hasPython,
  ],
  () => {
    if (hydrating.value) return
    scheduleResultSet()
  },
  { deep: true },
)

/** 结果集状态变化 → 推给画布（卡片唯一数据来源） */
watch(
  () => state.resultSet.status,
  (status) => {
    if (hydrating.value || !props.component) return
    emit('resultset', {
      componentId: props.component.id,
      status,
      source: state.resultSet.source,
      rows: state.resultSet.rows,
      fieldLabels: state.resultSet.fieldLabels,
      error: state.resultSet.error,
    })
  },
)
</script>

<style scoped>
.card-attr-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.card-attr-sidebar :deep(.attr-panel) {
  flex: 1;
  min-height: 0;
  border-left: none;
}
</style>
