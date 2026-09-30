<template>
  <div class="dashboard-preview-view">
    <!-- 顶部工具栏 -->
    <div class="preview-toolbar mc-toolbar">
      <div class="toolbar-left mc-toolbar-left">
        <button type="button" class="back-btn" :title="t('common.back')" :aria-label="t('common.back')" @click="handleBack">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <div class="toolbar-title-block">
          <h2 class="toolbar-title mc-toolbar-title">{{ dashboard?.name ?? t('insight.preview') }}</h2>
          <div class="toolbar-subtitle">
            {{ t('insight.previewMode') }}<span v-if="dashboard?.status" class="toolbar-status-dot" :class="dashboard.status"></span><span v-if="dashboard?.status">{{ dashboard.status === 'published' ? t('insight.status.published') : t('insight.status.draft') }}</span>
          </div>
        </div>
      </div>
      <div class="toolbar-right mc-toolbar-right">
        <el-button
          v-if="canCreate && !reportGenerating"
          type="primary"
          class="toolbar-btn"
          :icon="Document"
          @click="handleGenerateReport"
        >
          {{ t('insight.reportGenerate') }}
        </el-button>
        <el-button
          v-if="hasReport && !reportGenerating"
          class="toolbar-btn"
          :icon="View"
          @click="handleViewReport"
        >
          {{ t('insight.reportView') }}
        </el-button>
        <el-button
          v-if="canCreate && hasReport && !reportGenerating"
          class="toolbar-btn"
          :icon="Upload"
          @click="handlePublishReport"
        >
          {{ t('insight.reportPublish') }}
        </el-button>
        <div v-if="reportGenerating" class="generating-indicator">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('insight.generatingReport') }}</span>
        </div>
      </div>
    </div>

    <!-- 仪表盘预览区 -->
    <div class="preview-body">
      <!-- 页面菜单 Tab 栏（多页面时显示） -->
      <div v-if="schema.pages.length > 1" class="page-bar mc-tabs" role="tablist" aria-label="仪表盘页面">
        <button
          v-for="page in topLevelPages"
          :key="page.id"
          type="button"
          role="tab"
          :data-page-id="page.id"
          class="page-tab mc-tab"
          :class="{ active: activePageId === page.id || isDescendantPage(activePageId, page.id) }"
          :aria-selected="activePageId === page.id || isDescendantPage(activePageId, page.id)"
          :tabindex="activePageId === page.id || isDescendantPage(activePageId, page.id) ? 0 : -1"
          @keydown="handlePageTabKeydown($event, topLevelPages.map(item => item.id), page.id)"
          @click="handlePageChange(page.id)"
        >
          <span v-if="page.icon" class="page-icon">{{ page.icon }}</span>
          <span class="page-name">{{ page.name }}</span>
        </button>
      </div>
      <!-- 子页面 Tab 栏（当前页面有子页面时显示） -->
      <div v-if="activeSubPages.length > 0" class="page-bar sub-page-bar mc-tabs" role="tablist" aria-label="仪表盘子页面">
        <button
          v-for="sub in activeSubPages"
          :key="sub.id"
          type="button"
          role="tab"
          :data-page-id="sub.id"
          class="page-tab mc-tab mc-tab-sub"
          :class="{ active: activePageId === sub.id }"
          :aria-selected="activePageId === sub.id"
          :tabindex="activePageId === sub.id ? 0 : -1"
          @keydown="handlePageTabKeydown($event, activeSubPages.map(item => item.id), sub.id)"
          @click="handlePageChange(sub.id)"
        >
          <span v-if="sub.icon" class="page-icon">{{ sub.icon }}</span>
          <span class="page-name">{{ sub.name }}</span>
        </button>
      </div>

      <div v-if="currentPageComponents.length === 0" class="preview-empty">
        <div class="empty-illustration">
          <svg width="80" height="80" viewBox="0 0 80 80" aria-hidden="true">
            <rect x="12" y="14" width="56" height="52" rx="10" fill="var(--db-muted)" opacity=".45"/>
            <rect x="22" y="38" width="9" height="20" rx="3" fill="var(--main-orange)" opacity=".55"/>
            <rect x="35" y="28" width="9" height="30" rx="3" fill="var(--main-orange)" opacity=".75"/>
            <rect x="48" y="34" width="9" height="24" rx="3" fill="var(--main-orange)" opacity=".55"/>
            <circle cx="60" cy="22" r="10" fill="var(--db-card)" stroke="var(--db-border-strong)" stroke-width="1.5"/>
            <path d="M57 22 h6 M60 19 v6" stroke="var(--db-text-muted)" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="empty-copy">
          <div class="empty-title">{{ t('insight.previewEmpty') }}</div>
          <div class="empty-hint">{{ t('insight.previewEmptyHint') }}</div>
        </div>
        <div class="empty-actions">
          <el-button class="toolbar-btn" @click="handleBack">{{ t('common.back') }}</el-button>
          <el-button v-if="canCreate" type="primary" class="toolbar-btn" :icon="EditPen" @click="handleGoEdit">{{ t('insight.goEdit') }}</el-button>
        </div>
      </div>
      <DashboardCanvas
        v-else
        :components="currentPageComponents"
        :component-data-map="componentDataMap"
        :runtime-filter-state="getRuntimeFilterState()"
        :dataset-inputs="schema.datasetInputs"
        :editable="false"
        :dashboard-theme="dashboardTheme"
        :ai-analysis-generating-ids="aiAnalysisGeneratingIds"
        @filter-change="handleFilterChange"
        @time-filter-change="handleTimeFilterChange"
        @component-time-range-change="handleComponentTimeRangeChange"
        @retry-component-query="retryComponentQuery"
        @ai-analysis-generate="handleAiAnalysisGenerate"
      />
    </div>

    <!-- 报告抽屉（样式对齐报告页报告详情抽屉） -->
    <el-drawer
      v-model="reportDrawerVisible"
      direction="rtl"
      size="50%"
      :close-on-press-escape="true"
      class="report-detail-drawer"
      @close="handleReportDrawerClose"
    >
      <template #header>
        <div class="rd-header">
          <div class="rd-header-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          </div>
          <div class="rd-header-text">
            <span class="rd-header-title">{{ dashboard?.name || t('insight.reportTitle') }}</span>
            <span class="rd-header-sub">{{ t('reportDrawer.subtitle') }}</span>
          </div>
          <el-button class="rd-download" type="primary" plain size="small" :icon="Download" @click="handleDownloadReport">
            {{ t('insight.reportDownload') }}
          </el-button>
        </div>
      </template>
      <div class="report-container">
        <div ref="reportContentRef" class="report-content" v-html="reportHtmlContent"></div>
        <p class="report-end-note">{{ t('insight.aiDisclaimer') }}</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, View, Loading, Upload, EditPen, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

import type {
  InsightDashboardSchema,
  InsightComponentData,
  InsightComponent,
  TimeRangeValue,
  DashboardFilterContext,
  DashboardPage,
  DashboardRuntimeFilterState,
} from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import * as insightDashboardApi from '@/api/insight-dashboard'
import * as datasetApi from '@/api/dataset'
import { generateReport, getReport, publishReport } from '@/api/insight-report'
import { collectDashboardComponents, useDashboardFilterContext } from '@/composables/useDashboardFilterContext'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import DashboardCanvas from './components/DashboardCanvas.vue'
import { migrateInsightDashboardSchema } from '@/utils/dashboard-schema'
import { extractResultSchema, parseScriptResultEnvelope, resultEnvelopeToComponentData } from '@/utils/script-result'
import { toComponentData } from './composables/useResultSetRestore'
import { buildScriptParameters } from '@/utils/script-parameters'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'
import { buildComponentQueryParameters } from '@/utils/dashboard-preview-query'
import { readComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { finalResultQueryConfigStatus, isFinalResultQueryConfigured } from '@/utils/final-result-query'

defineOptions({
  name: 'DashboardPreviewView',
})

const props = defineProps<{
  /** 仪表盘 ID */
  dashboardId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'edit'): void
}>()

const { t } = useI18n()
// 生成/发布报告为 member 级写操作，viewer 只读
const { hasPermission } = usePermission()
const canCreate = computed(() => hasPermission(PERMISSION.INSIGHT_CREATE))
const store = useInsightDashboardStore()

const dashboard = computed(() => store.currentDashboard)
const schema = reactive<InsightDashboardSchema>({ version: '1.0', pages: [] })
const componentDataMap = ref<Record<string, InsightComponentData>>({})
const dashboardTheme = computed(() => resolveDashboardTheme(schema.theme, 'light'))

/** 组件级时间范围状态（componentId → TimeRangeValue） */
const componentTimeRanges = reactive<Record<string, TimeRangeValue>>({})

/** 正在生成 AI 分析的组件 ID 集合 */
const aiAnalysisGeneratingIds = reactive<Set<string>>(new Set())

/** AI 分析内容状态（componentId → analysisSection） */
const aiAnalysisContents = reactive<Record<string, string>>({})

/** 报告生成中状态 */
const reportGenerating = ref(false)

/** 报告抽屉可见性 */
const reportDrawerVisible = ref(false)

/** 报告 HTML 内容 */
const reportHtmlContent = ref('')

/** 报告内容 DOM ref */
const reportContentRef = ref<HTMLElement | null>(null)

/** 是否已有报告 */
const hasReport = computed(() => !!reportHtmlContent.value)

/** 报告中的 ECharts 实例列表 */
const reportChartInstances = ref<echarts.ECharts[]>([])

/** 防抖定时器 */
let filterReloadTimer: ReturnType<typeof setTimeout> | null = null
/** 旧直连 preview 与组件查询共用的单调请求序列；晚到的整页响应不能覆盖新查询。 */
let queryRequestSequence = 0
const latestComponentRequest = new Map<string, number>()
let previousRuntimeFilterState: DashboardRuntimeFilterState = {}
let previewDatasetMaterialization: Promise<void> | null = null

/** 当前激活的页面 ID */
const activePageId = ref<string>('')

/** 顶级页面列表（无 parentId 的页面） */
const topLevelPages = computed<DashboardPage[]>(() => {
  return schema.pages
    .filter((p) => !p.parentId)
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
})

/** 当前顶级页面下的子页面列表 */
const activeSubPages = computed<DashboardPage[]>(() => {
  // 找到当前激活页面所属的顶级页面
  const currentTopPage = findTopLevelPage(activePageId.value)
  if (!currentTopPage) {
    return []
  }
  return schema.pages
    .filter((p) => p.parentId === currentTopPage.id)
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
})

/** 当前激活页面的组件列表 */
const currentPageComponents = computed<InsightComponent[]>(() => {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  return page?.components ?? []
})

/** 查找页面所属的顶级页面 */
function findTopLevelPage(pageId: string): DashboardPage | undefined {
  let page = schema.pages.find((p) => p.id === pageId)
  let depth = 0
  while (page?.parentId && depth < 20) {
    page = schema.pages.find((p) => p.id === page!.parentId)
    depth++
  }
  return page
}

/** 判断 pageId 是否是 parentPageId 的后代 */
function isDescendantPage(pageId: string, parentPageId: string): boolean {
  let page = schema.pages.find((p) => p.id === pageId)
  let depth = 0
  while (page?.parentId && depth < 20) {
    if (page.parentId === parentPageId) {
      return true
    }
    page = schema.pages.find((p) => p.id === page!.parentId)
    depth++
  }
  return false
}

/** 生成 ID */
function generateId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

/** 迁移旧 Schema（单 components 数组 → pages[0]） */
function migrateSchema(parsed: any): InsightDashboardSchema {
  return migrateInsightDashboardSchema(parsed, t('insight.firstPageName'))
}

/** 筛选上下文管理（基于当前页面组件） */
const {
  filterContext,
  setTimeRange,
  setDimensionFilter,
  initializeDefaults,
  getRuntimeFilterState,
} = useDashboardFilterContext(
  () => currentPageComponents.value,
  (context) => scheduleReloadWithFilters(context),
)

onMounted(async () => {
  await loadDashboard()
})

watch(
  () => props.dashboardId,
  async () => {
    await loadDashboard()
  }
)

/** 加载仪表盘 */
async function loadDashboard(): Promise<void> {
  if (!props.dashboardId) {
    return
  }
  await store.selectDashboard(props.dashboardId)
  if (dashboard.value) {
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson)
      const migrated = migrateSchema(parsed)
      schema.version = migrated.version
      schema.pages = migrated.pages
      schema.datasetInputs = migrated.datasetInputs ?? []
      schema.script = migrated.script
      schema.parameters = migrated.parameters ?? []
      schema.executionPolicy = migrated.executionPolicy ?? {}
      schema.scriptBindings = migrated.scriptBindings ?? []
      schema.theme = migrated.theme
    } catch {
      schema.pages = [{
        id: generateId('page'),
        name: t('insight.firstPageName'),
        components: [],
      }]
    }
    // 默认选中第一个页面
    if (schema.pages.length > 0) {
      activePageId.value = schema.pages[0].id
    }
    initializeDefaults()
    await materializePreviewDatasetInputs()
    previousRuntimeFilterState = getRuntimeFilterState()
    await reloadComponentData(filterContext.value)
    await reloadScriptBindings(filterContext.value)
    // 首次预览与筛选刷新共用组件管线，避免沿用上次编辑保存的静态快照。
    await refreshPipelineComponents(getRuntimeFilterState(), true)
    // 加载已生成的报告
    await loadReport()
  }
}

/**
 * QueryPlanPreview 与脚本执行接口只接受已落库的数字 datasetId。旧看板可能保存
 * `ds-*` 草稿 ID；预览时按来源定义复用现有数据集或确认草稿，并只回填当前内存
 * Schema，避免把临时 ID 发送到 long 参数接口，也不在只读预览时覆盖看板记录。
 */
async function materializePreviewDatasetInputs(): Promise<void> {
  if (previewDatasetMaterialization) return previewDatasetMaterialization
  const operation = materializePreviewDatasetInputsOnce()
  previewDatasetMaterialization = operation
  try {
    await operation
  } finally {
    if (previewDatasetMaterialization === operation) previewDatasetMaterialization = null
  }
}

async function materializePreviewDatasetInputsOnce(): Promise<void> {
  const temporaryInputs = pipelineComponents().flatMap((component) => {
    const pipeline = readComponentDatasetPipeline(component)
    return (pipeline?.datasetInputs ?? [])
      .filter((input) => (!/^\d+$/.test(String(input.datasetId)) || Number(input.datasetId) <= 0)
        && Boolean(input.sourceType && input.sourceConfig))
      .map((input) => ({ component, pipeline: pipeline!, input }))
  })
  if (!temporaryInputs.length) return

  const existing = await datasetApi.list() as unknown as Array<{
    id: string; datasourceId?: string; sourceType?: string; sourceConfig?: string | Record<string, unknown>
  }>
  const sourceConfigFor = (sourceType: string, config: Record<string, unknown>): Record<string, unknown> => {
    switch (sourceType) {
      case 'ALOUDATA_METRICS': return { metrics: config.metrics ?? [], dimensions: config.dimensions ?? [] }
      case 'ALOUDATA_ANALYSIS_VIEW': return { analysisViewId: config.analysisViewId ?? '' }
      case 'JDBC_SQL': return { sql: config.sql ?? '' }
      case 'HTTP_API': return { apiDefinitionId: config.apiDefinitionId ?? '' }
      case 'FILE': return { objectId: config.objectId ?? '', format: config.format ?? 'CSV' }
      default: throw new Error(`暂不支持自动落库的数据集类型：${sourceType || '未知'}`)
    }
  }
  const stableValue = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(stableValue)
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, child]) => [key, stableValue(child)]))
    }
    return value
  }
  const canonical = (value: Record<string, unknown>) => JSON.stringify(stableValue(value))
  const hashSource = (value: string): string => {
    const mask = (1n << 64n) - 1n
    let hash = 14695981039346656037n
    for (let index = 0; index < value.length; index += 1) {
      hash = ((hash ^ BigInt(value.charCodeAt(index))) * 1099511628211n) & mask
    }
    return hash.toString(36)
  }
  const confirmedBySource = new Map<string, string>()
  for (const { component, pipeline, input } of temporaryInputs) {
    const sourceType = String(input.sourceType ?? '').toUpperCase()
    const source = input.sourceConfig as Record<string, unknown> | undefined
    const datasourceId = String(source?.datasourceId ?? '')
    if (!sourceType || !source || !datasourceId) {
      throw new Error(`组件「${component.title || component.id}」的数据集「${input.inputName}」缺少来源配置，无法解析临时 ID`)
    }
    const definition = sourceConfigFor(sourceType, source)
    const signature = `${sourceType}:${datasourceId}:${canonical(definition)}`
    const datasetName = `preview_${props.dashboardId}_${component.id.slice(-8)}_${input.inputName}_${hashSource(signature)}`
    let datasetId = confirmedBySource.get(signature)
    if (!datasetId) {
      const match = existing.find((candidate) => {
        // DatasetVO intentionally omits sourceConfig; the stable name embeds a hash of it.
        return candidate.name === datasetName
          && String(candidate.sourceType ?? '').toUpperCase() === sourceType
          && String(candidate.datasourceId ?? '') === datasourceId
      })
      if (match) datasetId = String(match.id)
      else {
        const result = await datasetApi.confirmDraft({
          sourceType,
          datasourceId,
          sourceConfig: definition,
          name: datasetName,
          description: `看板预览自动确认的数据集：${component.title || component.id}`,
        }) as unknown as { datasetId: string }
        datasetId = String(result.datasetId)
        existing.push({ id: datasetId, datasourceId, sourceType, sourceConfig: { ...definition, datasourceId, sourceType } })
      }
      confirmedBySource.set(signature, datasetId)
    }
    input.datasetId = datasetId
    component.config = writeComponentDatasetPipeline(component, pipeline).config
  }
}

/** 加载已生成的报告 */
async function loadReport(): Promise<void> {
  try {
    const content = await getReport(props.dashboardId)
    if (content) {
      reportHtmlContent.value = content
    }
  } catch {
    // 报告未生成，忽略
  }
}

function componentRenderType(component: InsightComponent): InsightComponentData['renderType'] {
  return component.type === 'chart' ? 'echarts' : component.type === 'kpi' ? 'kpi' : 'table'
}

function isQueryTimeout(error: unknown): boolean {
  const value = error as { name?: string; code?: string; message?: string } | null
  return /timeout|timed out|超时/i.test(`${value?.name ?? ''} ${value?.code ?? ''} ${value?.message ?? error ?? ''}`)
}

function resolvedQueryData(data: InsightComponentData): InsightComponentData {
  if (data.error) return { ...data, queryStatus: isQueryTimeout(new Error(data.error)) ? 'timeout' : 'error' }
  let empty = false
  if (data.renderType === 'table') empty = !data.table?.rows.length
  else if (data.renderType === 'kpi') empty = !data.kpi && !data.kpiList?.length
  else if (data.renderType === 'echarts') {
    const series = data.option?.series
    empty = !Array.isArray(series) || series.length === 0 || series.every((item) => {
      const values = item && typeof item === 'object' ? (item as { data?: unknown[] }).data : undefined
      return Array.isArray(values) && values.length === 0
    })
  }
  return { ...data, queryStatus: empty ? 'empty' : 'success' }
}

function writeComponentQueryState(component: InsightComponent, queryStatus: NonNullable<InsightComponentData['queryStatus']>, error?: string): void {
  const previous = componentDataMap.value[component.id]
  componentDataMap.value = {
    ...componentDataMap.value,
    [component.id]: {
      ...previous,
      componentId: component.id,
      renderType: previous?.renderType ?? componentRenderType(component),
      queryStatus,
      error,
    },
  }
}

/** 旧版直连组件仍走预览接口，但按组件合并并拒绝过期整页响应。 */
async function reloadComponentData(context: DashboardFilterContext): Promise<void> {
  const requestId = ++queryRequestSequence
  const directComponents = collectDashboardComponents(currentPageComponents.value)
    .filter((component) => component.dataSource && !readComponentDatasetPipeline(component))
  const directIds = new Set(directComponents.map((component) => component.id))
  if (directComponents.length === 0) return
  directIds.forEach((componentId) => latestComponentRequest.set(componentId, requestId))
  directComponents.forEach((component) => writeComponentQueryState(component, 'loading'))
  try {
    const dataList = await insightDashboardApi.preview(props.dashboardId, context) as unknown as InsightComponentData[]
    const dataMap = { ...componentDataMap.value }
    const returned = new Set<string>()
    for (const rawItem of dataList ?? []) {
      if (directIds.has(rawItem.componentId) && latestComponentRequest.get(rawItem.componentId) === requestId) {
        dataMap[rawItem.componentId] = resolvedQueryData(rawItem)
        returned.add(rawItem.componentId)
      }
    }
    for (const component of directComponents) {
      if (latestComponentRequest.get(component.id) !== requestId || returned.has(component.id)) continue
      dataMap[component.id] = { componentId: component.id, renderType: componentRenderType(component), queryStatus: 'empty' }
    }
    componentDataMap.value = dataMap
  } catch (error) {
    const dataMap = { ...componentDataMap.value }
    for (const componentId of directIds) {
      if (latestComponentRequest.get(componentId) !== requestId) continue
      const component = collectDashboardComponents(currentPageComponents.value).find((item) => item.id === componentId)
      if (!component) continue
      dataMap[componentId] = {
        componentId,
        renderType: componentRenderType(component),
        queryStatus: isQueryTimeout(error) ? 'timeout' : 'error',
        error: error instanceof Error ? error.message : t('insight.previewDataFailed'),
      }
    }
    componentDataMap.value = dataMap
  }
}

function pipelineComponents(): InsightComponent[] {
  return collectDashboardComponents(currentPageComponents.value)
    .filter((component) => ['kpi', 'chart', 'table'].includes(component.type)
      && Boolean(readComponentDatasetPipeline(component)?.datasetInputs?.length))
}

function componentUsesFilter(component: InsightComponent, filterId: string): boolean {
  const pipeline = readComponentDatasetPipeline(component)
  const inputBound = pipeline?.datasetInputs?.some((input) =>
    input.queryConfig?.parameterBindings?.some((binding) => binding.filterComponentId === filterId))
  const outputBound = pipeline?.finalResultQueryConfig?.filterFields?.some((field) => field.filterComponentId === filterId)
  return Boolean(inputBound || outputBound)
}

function filterTargetsComponent(filterId: string, component: InsightComponent, state: DashboardRuntimeFilterState): boolean {
  const filter = state[filterId]
  return !filter || filter.scope !== 'scoped' || filter.targetComponentIds.includes(component.id)
}

function finalResultFilterParameters(component: InsightComponent, state: DashboardRuntimeFilterState): Record<string, unknown> {
  const pipeline = readComponentDatasetPipeline(component)
  const config = pipeline?.finalResultQueryConfig
  const parameters: Record<string, unknown> = {}
  for (const field of config?.filterFields ?? []) {
    if (!field.filterComponentId) continue
    const runtimeFilter = state[field.filterComponentId]
    if (!runtimeFilter || runtimeFilter.value == null || runtimeFilter.value === ''
      || (Array.isArray(runtimeFilter.value) && runtimeFilter.value.length === 0)) continue
    const value = runtimeFilter.value
    const operators = field.operators ?? []
    let operator: string | undefined
    let parameterValue: unknown = value
    if (Array.isArray(value)) operator = operators.includes('in') ? 'in' : operators[0]
    else if (typeof value === 'object' && 'preset' in value) {
      if (operators.includes('between') && value.start && value.end) {
        operator = 'between'
        parameterValue = [value.start, value.end]
      } else if (operators.includes('gte') && value.start) {
        operator = 'gte'
        parameterValue = value.start
      } else if (operators.includes('lt') && value.end) {
        operator = 'lt'
        parameterValue = value.end
      }
    } else operator = operators.includes('eq') ? 'eq' : operators[0]
    if (!operator) continue
    parameters[field.parameterName] = parameterValue
  }
  return parameters
}

function finalResultQueryConfig(component: InsightComponent, state: DashboardRuntimeFilterState): Record<string, unknown> | undefined {
  const config = readComponentDatasetPipeline(component)?.finalResultQueryConfig
  if (!config || !isFinalResultQueryConfigured(config)) return undefined
  return {
    ...config,
    parameterBindings: config.filterFields
      .filter((field) => field.filterComponentId && field.operators?.length)
      .map((field) => ({
        parameterName: field.parameterName,
        field: field.field,
        operator: (() => {
          const value = field.filterComponentId ? state[field.filterComponentId]?.value : undefined
          if (Array.isArray(value)) return field.operators.includes('in') ? 'in' : field.operators[0]
          if (value && typeof value === 'object' && 'preset' in value) {
            return field.operators.includes('between') ? 'between'
              : field.operators.includes('gte') ? 'gte' : field.operators[0]
          }
          return field.operators.includes('eq') ? 'eq' : field.operators[0]
        })(),
      })),
  }
}

/** 将图表私有时间范围覆盖到该管道实际绑定的时间筛选器，仅影响本次组件查询。 */
function filtersWithComponentTimeRange(
  component: InsightComponent,
  filters: DashboardRuntimeFilterState,
  timeRange: TimeRangeValue,
): DashboardRuntimeFilterState {
  const pipeline = readComponentDatasetPipeline(component)
  const filterIds = new Set<string>()
  for (const input of pipeline?.datasetInputs ?? []) {
    for (const binding of input.queryConfig?.parameterBindings ?? []) {
      filterIds.add(binding.filterComponentId)
    }
  }
  for (const field of pipeline?.finalResultQueryConfig?.filterFields ?? []) {
    if (field.filterComponentId) filterIds.add(field.filterComponentId)
  }

  const timeFilters = collectDashboardComponents(currentPageComponents.value).filter((candidate) => {
    if (candidate.type !== 'timeFilter' || !filterIds.has(candidate.id)) return false
    const config = candidate.config as { scope?: string; targetComponentIds?: string[] } | undefined
    return config?.scope !== 'scoped' || config.targetComponentIds?.includes(component.id) === true
  })
  if (timeFilters.length === 0) {
    throw new Error('组件级时间筛选无法作用于此数据集管道，请先在查询配置中绑定时间筛选器')
  }

  const nextFilters = { ...filters }
  for (const timeFilter of timeFilters) {
    const config = timeFilter.config as { field?: string; scope?: 'global' | 'scoped'; targetComponentIds?: string[] } | undefined
    nextFilters[timeFilter.id] = {
      field: config?.field || 'metric_time',
      scope: config?.scope ?? 'global',
      targetComponentIds: [...(config?.targetComponentIds ?? [])],
      value: { ...timeRange },
    }
  }
  return nextFilters
}

/** 对一个当前页管道组件执行受控输入查询，必要时再运行 Python 和输出阶段过滤。 */
async function refreshPipelineComponent(
  component: InsightComponent,
  filters: DashboardRuntimeFilterState,
  componentTimeRange?: TimeRangeValue,
): Promise<void> {
  const pipeline = readComponentDatasetPipeline(component)
  if (!pipeline?.datasetInputs?.length) return
  const requestId = ++queryRequestSequence
  latestComponentRequest.set(component.id, requestId)
  writeComponentQueryState(component, 'loading')
  try {
    const effectiveFilters = componentTimeRange
      ? filtersWithComponentTimeRange(component, filters, componentTimeRange)
      : filters
    const invalidInput = pipeline.datasetInputs.find((input) => !/^\d+$/.test(String(input.datasetId)) || Number(input.datasetId) <= 0)
    if (invalidInput) throw new Error(`数据集「${invalidInput.inputName}」尚未落库，无法在预览中查询`)
    const queryContext = {
      dashboardId: props.dashboardId,
      componentId: component.id,
      parameters: buildComponentQueryParameters(component, effectiveFilters),
      requestId: String(requestId),
    }
    let rows: Record<string, unknown>[]
    let fieldLabels: Record<string, string> | undefined
    const script = pipeline.script?.trim()
    if (script) {
      const created = await insightDashboardApi.executeComponent(
        props.dashboardId, component.id, {}, JSON.stringify(schema), queryContext,
      ) as unknown as { executionId?: string }
      if (!created.executionId) throw new Error('未获取到组件执行 ID')
      let envelope: unknown
      let terminalStatus = ''
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const status = await insightDashboardApi.getExecutionStatus(created.executionId) as unknown as {
          status?: string; result?: string; error?: string
        }
        if (status.status === 'SUCCEEDED') {
          envelope = status.result ? JSON.parse(status.result) : undefined
          terminalStatus = status.status
          break
        }
        if (status.status === 'RESULT_REF') {
          const result = await insightDashboardApi.getExecutionResult(created.executionId) as unknown as { envelope?: unknown }
          envelope = result.envelope
          terminalStatus = status.status
          break
        }
        if (status.status && status.status !== 'RUNNING') throw new Error(status.error || `组件执行失败：${status.status}`)
        await new Promise((resolve) => setTimeout(resolve, 500))
      }
      if (!terminalStatus) throw new Error('组件执行等待超时')
      const outputConfig = finalResultQueryConfig(component, effectiveFilters)
      const parsed = parseScriptResultEnvelope(envelope)
      if (parsed.kind !== 'table') throw new Error(parsed.kind === 'message' ? parsed.message : 'Python 输出不是表格结果')
      const pipelineConfig = pipeline.finalResultQueryConfig
      const outputConfigStatus = finalResultQueryConfigStatus(
        pipelineConfig,
        outputConfig ? extractResultSchema(parsed) : undefined,
      )
      if (outputConfig && outputConfigStatus !== 'conflict') {
        const result = await insightDashboardApi.previewExecutionResult(created.executionId, {
          parameters: finalResultFilterParameters(component, effectiveFilters), requestId: String(requestId),
          finalResultQueryConfig: outputConfig,
        }) as unknown as { rows?: Record<string, unknown>[]; columns?: string[] }
        rows = result.rows ?? []
        const configured = pipeline.finalResultQueryConfig?.displayFields ?? []
        fieldLabels = Object.fromEntries(configured.map((field) => [field.field, field.title]))
      } else {
        rows = parsed.data.rows
        fieldLabels = Object.fromEntries(parsed.data.columns.map((column) => [column.name, column.title ?? column.name]))
      }
    } else {
      if (pipeline.datasetInputs.length !== 1) {
        throw new Error(`组件 ${component.title || component.id} 配置了多个输入但没有 Python 脚本，无法确定组合方式`)
      }
      const input = pipeline.datasetInputs[0]
      const result = await datasetApi.previewQueryPlan({
        datasetId: input.datasetId,
        inputName: input.inputName,
        queryConfig: input.queryConfig,
        queryContext,
      })
      rows = result.rows ?? []
      fieldLabels = Object.fromEntries((input.queryConfig?.displayFields ?? []).map((field) => [field.field, field.title]))
    }
    if (latestComponentRequest.get(component.id) !== requestId) return
    const queryStatus = rows.length === 0 ? 'empty' : 'success'
    componentDataMap.value = {
      ...componentDataMap.value,
      [component.id]: { ...toComponentData(component, rows, fieldLabels), queryStatus },
    }
  } catch (error) {
    if (latestComponentRequest.get(component.id) !== requestId) return
    componentDataMap.value = {
      ...componentDataMap.value,
      [component.id]: {
        componentId: component.id,
        renderType: componentRenderType(component),
        queryStatus: isQueryTimeout(error) ? 'timeout' : 'error',
        error: error instanceof Error ? error.message : String(error),
      },
    }
  }
}

async function refreshPipelineComponents(filters: DashboardRuntimeFilterState, initial = false): Promise<void> {
  const changedFilterIds = Object.keys({ ...previousRuntimeFilterState, ...filters }).filter((filterId) =>
    JSON.stringify(previousRuntimeFilterState[filterId]?.value) !== JSON.stringify(filters[filterId]?.value))
  previousRuntimeFilterState = filters
  const components = pipelineComponents().filter((component) => initial
    || changedFilterIds.some((filterId) => filterTargetsComponent(filterId, component, filters)
      && componentUsesFilter(component, filterId)))
  await Promise.all(components.map((component) => refreshPipelineComponent(component, filters)))
}

/** 执行已保存脚本，并将用户确认过的结果绑定覆盖到对应组件。 */
async function reloadScriptBindings(
  context: DashboardFilterContext = filterContext.value,
  componentIds?: Set<string>,
): Promise<void> {
  if (!schema.script?.trim() || !schema.scriptBindings?.length) return
  const bindings = schema.scriptBindings.filter((binding) => !componentIds || componentIds.has(binding.componentId))
  if (!bindings.length) return
  const requestId = ++queryRequestSequence
  const components = collectDashboardComponents(schema.pages.flatMap((page) => page.components))
  bindings.forEach((binding) => {
    latestComponentRequest.set(binding.componentId, requestId)
    const component = components.find((item) => item.id === binding.componentId)
    if (component) writeComponentQueryState(component, 'loading')
  })
  try {
    const created = await insightDashboardApi.execute(
      props.dashboardId,
      buildScriptParameters(schema.parameters ?? [], context, schema.scriptFilterBindings ?? []),
    )
    const executionId = (created as unknown as { executionId?: string }).executionId
    if (!executionId) throw new Error('未获取到脚本执行 ID')
    let resultEnvelope: unknown
    let completed = false
    for (let attempt = 0; attempt < 120; attempt += 1) {
      const status = await insightDashboardApi.getExecutionStatus(executionId) as unknown as {
        status?: string
        result?: string
        error?: string
      }
      if (status.status === 'SUCCEEDED') {
        resultEnvelope = status.result ? JSON.parse(status.result) : undefined
        completed = true
        break
      }
      if (status.status === 'RESULT_REF') {
        const result = await insightDashboardApi.getExecutionResult(executionId)
        resultEnvelope = (result as unknown as { envelope?: unknown }).envelope
        completed = true
        break
      }
      if (status.status && status.status !== 'RUNNING') {
        throw new Error(status.error || `脚本执行未成功：${status.status}`)
      }
      await new Promise((resolve) => setTimeout(resolve, 500))
    }
    if (!completed) throw new Error('脚本执行等待超时')
    for (const binding of bindings) {
      if (latestComponentRequest.get(binding.componentId) !== requestId) continue
      const component = components.find((item) => item.id === binding.componentId)
      if (!component) continue
      const envelope = parseScriptResultEnvelope(resultEnvelope)
      const adapted = resultEnvelopeToComponentData({
        id: component.id,
        type: component.type,
        chartType: component.chartType,
        config: component.config,
        kpiMetrics: component.kpiMetrics,
      }, envelope)
      if (adapted.state === 'message') {
        const queryStatus = isQueryTimeout(new Error(adapted.message)) ? 'timeout' : 'error'
        componentDataMap.value[binding.componentId] = { componentId: binding.componentId, renderType: binding.renderType, queryStatus, error: adapted.message }
      } else if (adapted.state === 'empty') {
        componentDataMap.value[binding.componentId] = { componentId: binding.componentId, renderType: binding.renderType, queryStatus: 'empty', fieldLabels: adapted.fieldLabels, table: { columns: adapted.columns.map((column) => column.name), rows: [] } }
      } else if (binding.renderType === 'echarts') {
        componentDataMap.value[binding.componentId] = { componentId: binding.componentId, renderType: 'echarts', queryStatus: 'success', option: adapted.option, fieldLabels: adapted.fieldLabels }
      } else if (binding.renderType === 'kpi') {
        const kpiList = (adapted.kpiList ?? (adapted.value === undefined ? [] : [{ name: '值', value: adapted.value }]))
          .map((item, index) => ({
            fieldKey: item.fieldKey ?? component.kpiMetrics?.[index]?.fieldKey ?? `value_${index}`,
            name: item.name,
            value: String(item.value),
            unit: component.kpiMetrics?.[index]?.unit,
          }))
        componentDataMap.value[binding.componentId] = {
          componentId: binding.componentId,
          renderType: 'kpi',
          queryStatus: kpiList.length > 0 ? 'success' : 'empty',
          kpi: kpiList[0],
          kpiList,
          fieldLabels: adapted.fieldLabels,
        }
      } else {
        componentDataMap.value[binding.componentId] = { componentId: binding.componentId, renderType: 'table', queryStatus: adapted.table.rows.length > 0 ? 'success' : 'empty', table: adapted.table, fieldLabels: adapted.fieldLabels }
      }
    }
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    for (const binding of bindings) {
      if (latestComponentRequest.get(binding.componentId) !== requestId) continue
      componentDataMap.value[binding.componentId] = {
        componentId: binding.componentId,
        renderType: binding.renderType,
        queryStatus: isQueryTimeout(error) ? 'timeout' : 'error',
        error: errorMessage,
      }
    }
  }
}

/** 筛选变化时重新加载组件数据 */
async function reloadScopedComponentData(context: DashboardFilterContext): Promise<void> {
  await reloadComponentData(context)
}

/** 防抖重载（筛选频繁变化时避免过多请求） */
function scheduleReloadWithFilters(context: DashboardFilterContext, refreshAllPipelines = false): void {
  if (filterReloadTimer) {
    clearTimeout(filterReloadTimer)
  }
  filterReloadTimer = setTimeout(() => {
    const state = getRuntimeFilterState()
    void Promise.all([
      reloadScopedComponentData(context),
      reloadScriptBindings(context),
      refreshPipelineComponents(state, refreshAllPipelines),
    ])
  }, 300)
}

/** 筛选组件值变化 */
function handleFilterChange(payload: { componentId: string; field: string; value: string | string[] | undefined }): void {
  if (!payload.field) {
    return
  }
  setDimensionFilter(payload.field, payload.value, payload.componentId)
}

/** 时间筛选组件值变化 */
function handleTimeFilterChange(payload: { componentId: string; field: string; timeRange: TimeRangeValue }): void {
  if (!payload.timeRange?.preset) {
    setTimeRange(undefined, payload.componentId)
    return
  }
  setTimeRange(payload.timeRange, payload.componentId)
}

/** 组件级时间筛选变化（图表右上角时间选择器） */
function handleComponentTimeRangeChange(payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void {
  if (payload.timeRange) {
    componentTimeRanges[payload.componentId] = payload.timeRange
  } else {
    delete componentTimeRanges[payload.componentId]
  }
  const component = collectDashboardComponents(currentPageComponents.value)
    .find((item) => item.id === payload.componentId)
  if (component && readComponentDatasetPipeline(component)?.datasetInputs?.length) {
    void refreshPipelineComponent(component, getRuntimeFilterState(), payload.timeRange)
    return
  }
  reloadSingleComponentData(payload.componentId, payload.timeRange)
}

/** 重新加载单个组件数据（组件级时间筛选变化时） */
async function reloadSingleComponentData(componentId: string, componentTimeRange?: TimeRangeValue): Promise<void> {
  const component = collectDashboardComponents(currentPageComponents.value).find((item) => item.id === componentId)
  if (!component) return
  const requestId = ++queryRequestSequence
  latestComponentRequest.set(componentId, requestId)
  writeComponentQueryState(component, 'loading')
  // 构建该组件专属的筛选上下文：合并全局筛选 + 组件级时间覆盖
  const context: DashboardFilterContext = {
    ...filterContext.value,
    timeRange: componentTimeRange ?? filterContext.value.timeRange,
    // 标记仅影响指定组件
    sourceFilterId: `__component_${componentId}`,
  }
  try {
    const dataList = await insightDashboardApi.preview(props.dashboardId, context) as unknown as InsightComponentData[]
    if (latestComponentRequest.get(componentId) !== requestId) return
    const item = (dataList ?? []).find((candidate) => candidate.componentId === componentId)
    componentDataMap.value = {
      ...componentDataMap.value,
      [componentId]: item
        ? resolvedQueryData(item)
        : { componentId, renderType: componentRenderType(component), queryStatus: 'empty' },
    }
  } catch (error) {
    if (latestComponentRequest.get(componentId) !== requestId) return
    componentDataMap.value = {
      ...componentDataMap.value,
      [componentId]: {
        componentId,
        renderType: componentRenderType(component),
        queryStatus: isQueryTimeout(error) ? 'timeout' : 'error',
        error: error instanceof Error ? error.message : t('insight.previewDataFailed'),
      },
    }
  }
}

function retryComponentQuery(componentId: string): void {
  const component = collectDashboardComponents(currentPageComponents.value).find((item) => item.id === componentId)
  if (!component) return
  if (readComponentDatasetPipeline(component)?.datasetInputs?.length) {
    void refreshPipelineComponent(component, getRuntimeFilterState(), componentTimeRanges[componentId])
    return
  }
  if (component.dataSource) {
    void reloadSingleComponentData(componentId, componentTimeRanges[componentId])
    return
  }
  if (schema.scriptBindings?.some((binding) => binding.componentId === componentId)) {
    void reloadScriptBindings(filterContext.value, new Set([componentId]))
  }
}

/** AI 分析组件触发生成（同步接口） */
async function handleAiAnalysisGenerate(componentId: string): Promise<void> {
  if (aiAnalysisGeneratingIds.has(componentId)) return
  aiAnalysisGeneratingIds.add(componentId)

  try {
    const analysisMarkdown = await generateReport(props.dashboardId)
    aiAnalysisContents[componentId] = analysisMarkdown
    const dataMap = { ...componentDataMap.value }
    const existing = dataMap[componentId]
    if (existing) {
      dataMap[componentId] = {
        ...existing,
        aiAnalysis: {
          dataSection: existing.aiAnalysis?.dataSection ?? '',
          analysisSection: analysisMarkdown,
        },
      }
      componentDataMap.value = dataMap
    }
  } catch (err: any) {
    ElMessage.error(t('insight.aiAnalysis.generateFailed') + ': ' + (err?.message || String(err)))
  } finally {
    aiAnalysisGeneratingIds.delete(componentId)
  }
}

/** 生成报告 */
async function handleGenerateReport(): Promise<void> {
  reportGenerating.value = true

  try {
    const htmlContent = await generateReport(props.dashboardId)
    reportHtmlContent.value = htmlContent
    ElMessage.success(t('insight.reportGenerated'))
  } catch (err: any) {
    ElMessage.error(t('insight.reportGenerateFailed') + ': ' + (err?.message || String(err)))
  } finally {
    reportGenerating.value = false
  }
}

/** 查看报告 */
function handleViewReport(): void {
  reportDrawerVisible.value = true
  nextTick(() => renderReportCharts())
}

/** 发布报告 */
async function handlePublishReport(): Promise<void> {
  try {
    await publishReport({
      dashboardId: props.dashboardId,
      name: dashboard.value?.name,
      // 报告描述沿用看板描述；此前漏传导致列表卡片始终显示"暂无描述"
      description: dashboard.value?.description,
    })
    ElMessage.success(t('insight.reportPublishSuccess'))
  } catch (err: any) {
    ElMessage.error(t('insight.reportPublishFailed') + ': ' + (err?.message || String(err)))
  }
}

/** 渲染报告中的 ECharts 图表 */
function renderReportCharts(): void {
  if (!reportContentRef.value) return

  // 先销毁旧实例
  disposeReportCharts()

  // 构建 chartId → option 映射（与后端 collectEchartsOptions 逻辑一致）
  const echartsOptionMap: Record<string, Record<string, unknown>> = {}
  let chartIndex = 0
  for (const compId of Object.keys(componentDataMap.value)) {
    const data = componentDataMap.value[compId]
    if (data?.renderType === 'echarts' && data?.option) {
      echartsOptionMap['chart_' + chartIndex] = data.option as Record<string, unknown>
      chartIndex++
    }
  }

  // 扫描报告中的 echarts-container div
  const containers = reportContentRef.value.querySelectorAll('.echarts-container')
  containers.forEach((container) => {
    const chartId = (container as HTMLElement).dataset.chartId
    if (!chartId || !echartsOptionMap[chartId]) return

    const el = container as HTMLElement
    el.style.width = '100%'
    el.style.height = '300px'

    try {
      const chart = echarts.init(el)
      chart.setOption(echartsOptionMap[chartId])
      reportChartInstances.value.push(chart)
    } catch (e) {
      console.error('[Report] ECharts render error:', e)
    }
  })
}

/** 销毁报告中的 ECharts 实例 */
function disposeReportCharts(): void {
  reportChartInstances.value.forEach((chart) => {
    if (!chart.isDisposed()) chart.dispose()
  })
  reportChartInstances.value = []
}

/** 报告抽屉关闭时清理 ECharts 实例 */
function handleReportDrawerClose(): void {
  disposeReportCharts()
}

/** 下载报告 */
function handleDownloadReport(): void {
  if (!reportHtmlContent.value) return
  const dashboardName = dashboard.value?.name ?? 'report'
  const timestamp = new Date().toISOString().slice(0, 10)

  // 收集 echarts option 数据，内联到 HTML 中供离线渲染
  const echartsData: Record<string, Record<string, unknown>> = {}
  let chartIndex = 0
  for (const compId of Object.keys(componentDataMap.value)) {
    const data = componentDataMap.value[compId]
    if (data?.renderType === 'echarts' && data?.option) {
      echartsData['chart_' + chartIndex] = data.option as Record<string, unknown>
      chartIndex++
    }
  }
  const echartsDataJson = JSON.stringify(echartsData)

  // 将 HTML 片段包装为完整 HTML 文档，内嵌 ECharts CDN + 渲染脚本
  const fullHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${t('insight.reportTitle')} - ${dashboardName}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
           max-width: 800px; margin: 0 auto; padding: 24px; color: #333; line-height: 1.8; }
    h1 { font-size: 24px; border-bottom: 2px solid #e8e8e8; padding-bottom: 8px; }
    h2 { font-size: 20px; border-bottom: 1px solid #e8e8e8; padding-bottom: 6px; margin-top: 24px; }
    h3 { font-size: 16px; margin-top: 16px; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0; }
    th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
    th { background-color: #f5f5f5; font-weight: 600; }
    tr:nth-child(even) { background-color: #fafafa; }
    code { background-color: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 14px; }
    blockquote { border-left: 4px solid #ddd; margin: 12px 0; padding: 8px 16px; color: #666; }
    ul, ol { padding-left: 24px; }
    li { margin: 4px 0; }
    .echarts-container { width: 100%; height: 300px; margin: 16px 0; }
    .disclaimer { font-size: 12px; color: #999; text-align: center; margin-top: 32px;
                 padding-top: 12px; border-top: 1px solid #e8e8e8; }
  </style>
  <script src="https://cdn.jsdelivr.net/npm/echarts@6/dist/echarts.min.js"><\/script>
</head>
<body>
${reportHtmlContent.value}
  <div class="disclaimer">${t('insight.aiDisclaimer')}</div>
  <script>
    (function() {
      var data = ${echartsDataJson};
      var containers = document.querySelectorAll('.echarts-container');
      containers.forEach(function(el) {
        var chartId = el.getAttribute('data-chart-id');
        if (chartId && data[chartId] && typeof echarts !== 'undefined') {
          var chart = echarts.init(el);
          chart.setOption(data[chartId]);
        }
      });
      window.addEventListener('resize', function() {
        containers.forEach(function(el) {
          var chartId = el.getAttribute('data-chart-id');
          if (chartId && data[chartId]) {
            var instance = echarts.getInstanceByDom(el);
            if (instance) instance.resize();
          }
        });
      });
    })();
  <\/script>
</body>
</html>`

  const blob = new Blob([fullHtml], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${dashboardName}_${timestamp}.html`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 返回列表 */
function handleBack(): void {
  emit('back')
}

/** 去编辑仪表盘（空态引导） */
function handleGoEdit(): void {
  emit('edit')
}

/** 切换页面 */
function handlePageChange(pageId: string): void {
  if (activePageId.value === pageId) {
    return
  }
  activePageId.value = pageId
  initializeDefaults()
  // 页面切换后重新加载数据（筛选上下文不变，但可见组件变化）
  scheduleReloadWithFilters(filterContext.value, true)
}

function handlePageTabKeydown(event: KeyboardEvent, pageIds: string[], pageId: string): void {
  if (pageIds.length < 2 || !['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    return
  }
  event.preventDefault()
  const currentIndex = pageIds.indexOf(pageId)
  if (currentIndex < 0) return
  let nextIndex = currentIndex
  if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = pageIds.length - 1
  else if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % pageIds.length
  else nextIndex = (currentIndex - 1 + pageIds.length) % pageIds.length
  const nextPageId = pageIds[nextIndex]
  handlePageChange(nextPageId)
  nextTick(() => {
    const nextTab = document.querySelector<HTMLElement>(`.page-tab[data-page-id="${CSS.escape(nextPageId)}"]`)
    nextTab?.focus()
  })
}
</script>

<style scoped>
.dashboard-preview-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
  overflow: hidden;
  /* 与列表页/编辑页一致：四周留灰底边距，页头与预览卡片悬浮于页面底色之上 */
  padding: var(--space-md) var(--space-lg) var(--space-lg);
}

/* 页头工具栏：白色纸面卡片（描边+圆角+投影），与列表页 .page-card / 编辑页工具栏同一层级语言，
   覆盖 .mc-toolbar 的默认通栏白底/下边框/定高 */
.preview-toolbar {
  padding: 10px 16px;
  gap: 14px;
  flex-shrink: 0;
  margin-bottom: var(--space-md);
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  height: auto;
  min-height: 0;
}

.toolbar-left {
  gap: 10px;
  min-width: 0;
}

.back-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--db-border-strong);
  border-radius: 999px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast);
}

.back-btn:hover {
  color: var(--db-accent);
  border-color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 6%, transparent);
}

.back-btn:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--main-orange) 55%, transparent);
  outline-offset: 2px;
}

.toolbar-title-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.toolbar-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-subtitle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toolbar-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.toolbar-status-dot.published {
  background: var(--db-status-success-fg);
}

.toolbar-status-dot.draft {
  background: var(--db-status-warning-fg);
}

.toolbar-right {
  gap: 8px;
}

.toolbar-right :deep(.el-button.toolbar-btn) {
  height: 32px;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
}

.toolbar-right :deep(.el-button.toolbar-btn:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.toolbar-right :deep(.el-button.toolbar-btn:not(.el-button--primary):hover) {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.toolbar-right :deep(.el-button--primary.toolbar-btn) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  box-shadow: var(--shadow-md);
}

.toolbar-right :deep(.el-button--primary.toolbar-btn:hover),
.toolbar-right :deep(.el-button--primary.toolbar-btn:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

.generating-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--db-accent);
  padding: 5px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}

/* 抽屉自定义头部（#header 插槽内容，与报告页报告详情抽屉一致） */
.rd-header {
  display: flex;
  align-items: center;
  gap: 11px;
  flex: 1;
  min-width: 0;
}

.rd-header-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--main-orange) 0%, color-mix(in srgb, var(--main-orange) 60%, #fff) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--main-orange) 30%, transparent);
}

.rd-header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.rd-header-title {
  font-size: 15.5px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rd-header-sub {
  font-size: 11.5px;
  color: var(--db-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 抽屉头部右侧的下载按钮（轻量 plain 样式，与关闭按钮视觉平衡） */
.rd-download {
  margin-left: auto;
  flex-shrink: 0;
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  font-weight: 500;
}

.report-container {
  height: 100%;
  overflow-y: auto;
  /* 层级一：抽屉内页面底色，与内容白纸卡形成对比 */
  background: var(--db-bg);
  padding: 20px;
}

.report-content {
  max-width: 800px;
  margin: 0 auto;
  /* 层级二：白纸内容卡 */
  padding: 28px 32px 32px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  color: var(--db-text);
  line-height: 1.8;
  font-size: 14px;
  animation: fadeIn var(--transition-base) both;
}

.report-content :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  color: var(--db-text);
  margin: 0 0 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--db-border);
}

.report-content :deep(h2) {
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.4;
  margin: 32px 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--main-orange);
}

.report-content :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text);
  margin: 22px 0 10px;
}

.report-content :deep(p) {
  margin: 0 0 14px;
  color: var(--db-text-secondary);
}

.report-content :deep(strong) {
  color: var(--db-text);
  font-weight: 600;
}

.report-content :deep(ul),
.report-content :deep(ol) {
  padding-left: 22px;
  margin: 0 0 14px;
}

.report-content :deep(li) {
  margin: 5px 0;
  color: var(--db-text-secondary);
}

.report-content :deep(li)::marker {
  color: var(--main-orange);
}

.report-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  font-size: 13px;
}

.report-content :deep(th),
.report-content :deep(td) {
  border: none;
  border-bottom: 1px solid var(--db-border);
  padding: 9px 12px;
  text-align: left;
}

.report-content :deep(th) {
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
  font-weight: 600;
  color: var(--db-text);
}

.report-content :deep(tr:nth-child(even)) {
  background: color-mix(in srgb, var(--db-text-muted) 5%, transparent);
}

.report-content :deep(code) {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--db-text);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.report-content :deep(blockquote) {
  position: relative;
  margin: 16px 0;
  padding: 12px 16px 12px 40px;
  border: 1px solid color-mix(in srgb, var(--main-orange) 16%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--main-orange) 4%, transparent);
  color: var(--db-text-secondary);
}

/* 左上角信息徽标，替代左边框的视觉锚点 */
.report-content :deep(blockquote)::before {
  content: "i";
  position: absolute;
  left: 14px;
  top: 15px;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--main-orange) 14%, transparent);
  color: var(--main-orange);
  font-family: Georgia, 'Times New Roman', serif;
  font-style: italic;
  font-size: 12px;
  font-weight: 700;
  line-height: 17px;
  text-align: center;
}

.report-content :deep(blockquote > p:last-child) {
  margin-bottom: 0;
}

.report-content :deep(.echarts-container) {
  width: 100%;
  height: 300px;
  margin: 16px 0;
  padding: 8px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
}

/* 统一垂直节奏：内容卡首尾子元素归零边距 */
.report-content :deep(> *:first-child) {
  margin-top: 0;
}

.report-content :deep(> *:last-child) {
  margin-bottom: 0;
}

/* 报告结尾免责声明 */
.report-end-note {
  max-width: 800px;
  margin: 16px auto 0;
  text-align: center;
  font-size: 12px;
  color: var(--db-text-muted);
}

/* 预览区卡片容器：Tab 行与内容一起框进同一张卡片，
   Tab 行是卡片白色头部，画布保持灰底作为卡片内嵌区域 */
.preview-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  /* 外边距已由根容器统一提供，与列表页灰底留边一致 */
  margin: 0;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
}

/* Tab 行：卡片头部平铺式标签，激活项主题色下划线，
   覆盖 .mc-tabs 的白底/边框与 .mc-tab 的胶囊高亮 */
.page-bar {
  animation: fadeIn var(--transition-base) both;
  background: transparent;
  border-bottom: 1px solid var(--db-border);
  padding: 8px 16px 0;
  gap: 4px;
  width: auto;
  max-width: none;
  margin: 0;
}

.page-tab {
  padding: 10px 2px;
  margin: 0 10px;
  border-radius: 0;
  font-size: 13px;
  color: var(--db-text-secondary);
  /* 下划线为 inset box-shadow，需纳入过渡避免切换生硬 */
  transition: color var(--transition-fast), box-shadow var(--transition-fast);
}

.page-tab:hover {
  background: transparent;
  color: var(--db-text);
}

.page-tab.active {
  background: transparent;
  color: var(--db-text);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--main-orange);
}

.page-tab.mc-tab-sub {
  font-size: 12px;
  padding: 8px 2px;
}

/* 子 Tab 行略紧凑 */
.sub-page-bar {
  padding-top: 6px;
}

.page-icon {
  font-size: 14px;
  line-height: 1;
}

.page-name {
  line-height: 1;
}

.preview-loading {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  color: var(--db-text-muted);
}

.preview-loading .loading-icon {
  font-size: 36px;
  color: var(--db-accent);
}

.preview-loading .loading-text {
  font-size: 14px;
}

.preview-empty {
  width: 100%;
  height: 100%;
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 40px 24px;
}

.preview-empty .empty-illustration svg {
  display: block;
  filter: drop-shadow(0 4px 12px color-mix(in srgb, var(--main-orange) 12%, transparent));
}

.preview-empty .empty-copy {
  text-align: center;
  max-width: 360px;
}

.preview-empty .empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text);
  line-height: 1.4;
  margin-bottom: 6px;
}

.preview-empty .empty-hint {
  font-size: 13px;
  color: var(--db-text-muted);
  line-height: 1.5;
}

.preview-empty .empty-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.preview-empty .empty-actions :deep(.el-button) {
  height: 32px;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
}

.preview-empty .empty-actions :deep(.el-button:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.preview-empty .empty-actions :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.preview-empty .empty-actions :deep(.el-button--primary) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  box-shadow: var(--shadow-md);
}

.preview-empty .empty-actions :deep(.el-button--primary:hover),
.preview-empty .empty-actions :deep(.el-button--primary:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

@media (max-width: 767px) {
  .dashboard-preview-view {
    padding: var(--space-sm) var(--space-md) var(--space-md);
  }

  .preview-toolbar {
    padding: 10px var(--space-md);
    gap: 10px;
  }

  .toolbar-left,
  .toolbar-right {
    width: auto;
    min-width: 0;
  }

  .toolbar-right {
    justify-content: flex-end;
  }

  .toolbar-title {
    font-size: 16px;
  }
}
</style>

<style>
/* el-drawer teleport 到 body，scoped 选择器无法命中其内部节点，
   故用非 scoped 块 + 自定义类名限定作用域（与报告页报告详情抽屉一致） */
.report-detail-drawer {
  background: var(--theme-surface, #fff);
}

.report-detail-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 14px 16px;
  border-bottom: 1px solid var(--theme-border, #e5e7eb);
  background: var(--theme-surface, #fff);
  gap: 10px;
}

.report-detail-drawer .el-drawer__close-btn {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 16px;
  color: var(--db-text-muted);
  transition: background var(--transition-fast), color var(--transition-fast);
}

.report-detail-drawer .el-drawer__close-btn:hover {
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  color: var(--db-text);
}

.report-detail-drawer .el-drawer__close-btn:hover i,
.report-detail-drawer .el-drawer__close-btn:focus i {
  color: var(--db-text);
}

.report-detail-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
  background: var(--theme-surface, #fff);
}
</style>
