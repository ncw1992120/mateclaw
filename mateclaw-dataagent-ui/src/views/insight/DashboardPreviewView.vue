<template>
  <div class="dashboard-preview-view">
    <!-- 顶部工具栏 -->
    <div class="preview-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <span class="toolbar-title">{{ dashboard?.name ?? t('insight.preview') }}</span>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" :icon="Document" :loading="generatingReport" @click="handleAiReport">
          {{ t('insight.aiReport') }}
        </el-button>
      </div>
    </div>

    <!-- 仪表盘预览区 -->
    <div class="preview-body">
      <div v-if="schema.components.length === 0" class="preview-empty">
        <div class="empty-icon">📭</div>
        <div class="empty-text">{{ t('insight.previewEmpty') }}</div>
      </div>
      <DashboardCanvas
        v-else
        :components="schema.components"
        :component-data-map="componentDataMap"
        :editable="false"
        :ai-analysis-generating-ids="aiAnalysisGeneratingIds"
        @filter-change="handleFilterChange"
        @time-filter-change="handleTimeFilterChange"
        @component-time-range-change="handleComponentTimeRangeChange"
        @ai-analysis-generate="handleAiAnalysisGenerate"
      />
    </div>

    <!-- AI 报告抽屉 -->
    <el-drawer
      v-model="reportDrawerVisible"
      :title="t('insight.aiReport')"
      direction="rtl"
      size="50%"
      @close="handleDrawerClose"
    >
      <div class="report-content">
        <div v-if="generatingReport" class="report-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('insight.generatingReport') }}</span>
        </div>
        <div v-else-if="reportContent" class="report-markdown" v-html="renderedReport"></div>
        <div v-else class="report-placeholder">{{ t('insight.reportPlaceholder') }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type {
  InsightDashboardSchema,
  InsightComponentData,
  TimeRangeValue,
  DashboardFilterContext,
} from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { preview } from '@/api/insight-dashboard'
import { streamReport, generateReport } from '@/api/insight-report'
import { useDashboardFilterContext } from '@/composables/useDashboardFilterContext'
import DashboardCanvas from './components/DashboardCanvas.vue'

defineOptions({
  name: 'DashboardPreviewView',
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
const schema = reactive<InsightDashboardSchema>({ version: '1.0', components: [] })
const componentDataMap = ref<Record<string, InsightComponentData>>({})

/** 组件级时间范围状态（componentId → TimeRangeValue） */
const componentTimeRanges = reactive<Record<string, TimeRangeValue>>({})

/** 正在生成 AI 分析的组件 ID 集合 */
const aiAnalysisGeneratingIds = reactive<Set<string>>(new Set())

/** AI 分析内容状态（componentId → analysisSection） */
const aiAnalysisContents = reactive<Record<string, string>>({})
/** AI 报告相关 */
const reportDrawerVisible = ref(false)
const generatingReport = ref(false)
const reportContent = ref<string>('')
let abortController: AbortController | null = null

/** 防抖定时器 */
let filterReloadTimer: ReturnType<typeof setTimeout> | null = null

/** 筛选上下文管理 */
const {
  filterContext,
  setTimeRange,
  setDimensionFilter,
} = useDashboardFilterContext(
  () => schema.components,
  (context) => scheduleReloadWithFilters(context),
)

/** 渲染 Markdown 报告（含 XSS 过滤） */
const renderedReport = computed(() => {
  if (!reportContent.value) {
    return ''
  }
  const raw = marked.parse(reportContent.value, { async: false }) as string
  return DOMPurify.sanitize(raw)
})

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
      const parsed = JSON.parse(dashboard.value.schemaJson) as InsightDashboardSchema
      schema.version = parsed.version ?? '1.0'
      schema.components = parsed.components ?? []
    } catch {
      schema.components = []
    }
    await reloadComponentData(filterContext.value)
  }
}

/** 带筛选条件重新加载组件数据（全量替换） */
async function reloadComponentData(context: DashboardFilterContext): Promise<void> {
  try {
    const dataList = await preview(props.dashboardId, context) as unknown as InsightComponentData[]
    const dataMap: Record<string, InsightComponentData> = {}
    for (const item of dataList ?? []) {
      dataMap[item.componentId] = item
    }
    componentDataMap.value = dataMap
  } catch {
    ElMessage.warning(t('insight.previewDataFailed'))
  }
}

/** 筛选变化时重新加载组件数据 */
async function reloadScopedComponentData(context: DashboardFilterContext): Promise<void> {
  await reloadComponentData(context)
}

/** 防抖重载（筛选频繁变化时避免过多请求） */
function scheduleReloadWithFilters(context: DashboardFilterContext): void {
  if (filterReloadTimer) {
    clearTimeout(filterReloadTimer)
  }
  filterReloadTimer = setTimeout(() => {
    reloadScopedComponentData(context)
  }, 300)
}

/** 筛选组件值变化 */
function handleFilterChange(payload: { componentId: string; field: string; value: string }): void {
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
  reloadSingleComponentData(payload.componentId, payload.timeRange)
}

/** 重新加载单个组件数据（组件级时间筛选变化时） */
async function reloadSingleComponentData(componentId: string, componentTimeRange?: TimeRangeValue): Promise<void> {
  // 构建该组件专属的筛选上下文：合并全局筛选 + 组件级时间覆盖
  const context: DashboardFilterContext = {
    ...filterContext.value,
    timeRange: componentTimeRange ?? filterContext.value.timeRange,
    // 标记仅影响指定组件
    sourceFilterId: `__component_${componentId}`,
  }
  try {
    const dataList = await preview(props.dashboardId, context) as unknown as InsightComponentData[]
    const dataMap = { ...componentDataMap.value }
    for (const item of dataList ?? []) {
      if (item.componentId === componentId) {
        dataMap[item.componentId] = item
      }
    }
    componentDataMap.value = dataMap
  } catch {
    ElMessage.warning(t('insight.previewDataFailed'))
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

/** 生成 AI 解读报告（SSE 流式） */
async function handleAiReport(): Promise<void> {
  reportDrawerVisible.value = true
  generatingReport.value = true
  reportContent.value = ''

  abortController = streamReport(
    props.dashboardId,
    (chunk) => {
      reportContent.value += chunk
    },
    (err) => {
      ElMessage.error(t('insight.reportGenerateFailed') + ': ' + err)
      generatingReport.value = false
    },
    () => {
      generatingReport.value = false
    }
  )
}

/** 抽屉关闭时取消请求 */
function handleDrawerClose(): void {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  generatingReport.value = false
}

/** 返回列表 */
function handleBack(): void {
  emit('back')
}
</script>

<style scoped>
.dashboard-preview-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-bg);
  overflow: hidden;
}

.preview-toolbar {
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
  align-items: center;
  gap: 8px;
}

.preview-body {
  flex: 1;
  overflow: hidden;
}

.preview-empty {
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

.report-content {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}

.report-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--theme-text-secondary);
  font-size: 14px;
}

.report-markdown {
  font-size: 14px;
  line-height: 1.7;
  color: var(--theme-text);
}

.report-markdown :deep(h1),
.report-markdown :deep(h2),
.report-markdown :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: var(--theme-text);
}

.report-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.report-markdown :deep(th),
.report-markdown :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 8px 12px;
  text-align: left;
}

.report-markdown :deep(th) {
  background: var(--theme-surface-hover);
  font-weight: 600;
}

.report-placeholder {
  color: var(--theme-text-muted);
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}
</style>
