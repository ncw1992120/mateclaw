<template>
  <div class="dashboard-preview-view">
    <!-- 顶部工具栏 -->
    <div class="preview-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <span class="toolbar-title">{{ dashboard?.name ?? t('insight.preview') }}</span>
      </div>
      <div class="toolbar-right">
        <el-button
          v-if="!reportGenerating"
          size="small"
          :icon="Document"
          @click="handleGenerateReport"
        >
          生成报告
        </el-button>
        <el-button
          v-if="hasReport && !reportGenerating"
          size="small"
          :icon="View"
          @click="handleViewReport"
        >
          查看报告
        </el-button>
        <div v-if="reportGenerating" class="generating-indicator">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>报告生成中...</span>
        </div>
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

    <!-- 报告抽屉 -->
    <el-drawer
      v-model="reportDrawerVisible"
      title="AI 分析报告"
      direction="rtl"
      size="50%"
      :close-on-press-escape="true"
      @close="handleReportDrawerClose"
    >
      <div class="report-container">
        <div ref="reportContentRef" class="report-content" v-html="reportHtmlContent"></div>
      </div>
      <template #footer>
        <div class="report-footer">
          <span class="disclaimer">AI 分析仅供参考</span>
          <el-button size="small" @click="handleDownloadReport">下载报告</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, View, Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

import type {
  InsightDashboardSchema,
  InsightComponentData,
  TimeRangeValue,
  DashboardFilterContext,
} from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { preview } from '@/api/insight-dashboard'
import { generateReport, getReport } from '@/api/insight-report'
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

/** 筛选上下文管理 */
const {
  filterContext,
  setTimeRange,
  setDimensionFilter,
} = useDashboardFilterContext(
  () => schema.components,
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
      const parsed = JSON.parse(dashboard.value.schemaJson) as InsightDashboardSchema
      schema.version = parsed.version ?? '1.0'
      schema.components = parsed.components ?? []
    } catch {
      schema.components = []
    }
    await reloadComponentData(filterContext.value)
    // 加载已生成的报告
    await loadReport()
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

/** 生成报告 */
async function handleGenerateReport(): Promise<void> {
  reportGenerating.value = true

  try {
    const htmlContent = await generateReport(props.dashboardId)
    reportHtmlContent.value = htmlContent
    ElMessage.success('报告生成完成，点击"查看报告"查看')
  } catch (err: any) {
    ElMessage.error('报告生成失败: ' + (err?.message || String(err)))
  } finally {
    reportGenerating.value = false
  }
}

/** 查看报告 */
function handleViewReport(): void {
  reportDrawerVisible.value = true
  nextTick(() => renderReportCharts())
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
  <title>AI 分析报告 - ${dashboardName}</title>
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
  <div class="disclaimer">AI 分析仅供参考</div>
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

.generating-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--theme-text-muted);
}

.report-container {
  height: 100%;
  overflow-y: auto;
}

.report-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 8px;
  color: #333;
  line-height: 1.8;
}

.report-content :deep(h1) {
  font-size: 24px;
  border-bottom: 2px solid #e8e8e8;
  padding-bottom: 8px;
}

.report-content :deep(h2) {
  font-size: 20px;
  border-bottom: 1px solid #e8e8e8;
  padding-bottom: 6px;
  margin-top: 24px;
}

.report-content :deep(h3) {
  font-size: 16px;
  margin-top: 16px;
}

.report-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.report-content :deep(th),
.report-content :deep(td) {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}

.report-content :deep(th) {
  background-color: #f5f5f5;
  font-weight: 600;
}

.report-content :deep(tr:nth-child(even)) {
  background-color: #fafafa;
}

.report-content :deep(code) {
  background-color: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 14px;
}

.report-content :deep(blockquote) {
  border-left: 4px solid #ddd;
  margin: 12px 0;
  padding: 8px 16px;
  color: #666;
}

.report-content :deep(.echarts-container) {
  width: 100%;
  height: 300px;
  margin: 16px 0;
}

.report-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.disclaimer {
  font-size: 12px;
  color: var(--theme-text-muted);
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
</style>
