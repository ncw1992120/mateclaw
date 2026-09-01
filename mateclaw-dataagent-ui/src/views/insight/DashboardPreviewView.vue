<template>
  <div class="dashboard-preview-view">
    <!-- 顶部工具栏 -->
    <div class="preview-toolbar mc-toolbar">
      <div class="toolbar-left mc-toolbar-left">
        <button type="button" class="back-btn" :title="t('common.back')" @click="handleBack">
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
      <div v-if="schema.pages.length > 1" class="page-bar mc-tabs">
        <button
          v-for="page in topLevelPages"
          :key="page.id"
          class="page-tab mc-tab"
          :class="{ active: activePageId === page.id || isDescendantPage(activePageId, page.id) }"
          @click="handlePageChange(page.id)"
        >
          <span v-if="page.icon" class="page-icon">{{ page.icon }}</span>
          <span class="page-name">{{ page.name }}</span>
        </button>
      </div>
      <!-- 子页面 Tab 栏（当前页面有子页面时显示） -->
      <div v-if="activeSubPages.length > 0" class="page-bar sub-page-bar mc-tabs">
        <button
          v-for="sub in activeSubPages"
          :key="sub.id"
          class="page-tab mc-tab mc-tab-sub"
          :class="{ active: activePageId === sub.id }"
          @click="handlePageChange(sub.id)"
        >
          <span v-if="sub.icon" class="page-icon">{{ sub.icon }}</span>
          <span class="page-name">{{ sub.name }}</span>
        </button>
      </div>

      <div v-if="dataLoading" class="preview-loading">
        <el-icon class="loading-icon is-loading"><Loading /></el-icon>
        <div class="loading-text">{{ t('insight.loadingData') }}</div>
      </div>
      <div v-else-if="currentPageComponents.length === 0" class="preview-empty">
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
      :title="t('insight.reportTitle')"
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
          <span class="disclaimer">{{ t('insight.aiDisclaimer') }}</span>
          <el-button size="small" @click="handleDownloadReport">{{ t('insight.reportDownload') }}</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, View, Loading, Upload, EditPen } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

import type {
  InsightDashboardSchema,
  InsightComponentData,
  InsightComponent,
  TimeRangeValue,
  DashboardFilterContext,
  DashboardPage,
} from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { preview } from '@/api/insight-dashboard'
import { generateReport, getReport, publishReport } from '@/api/insight-report'
import { useDashboardFilterContext } from '@/composables/useDashboardFilterContext'
import { usePermission, PERMISSION } from '@/composables/usePermission'
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

/** 组件级时间范围状态（componentId → TimeRangeValue） */
const componentTimeRanges = reactive<Record<string, TimeRangeValue>>({})

/** 正在生成 AI 分析的组件 ID 集合 */
const aiAnalysisGeneratingIds = reactive<Set<string>>(new Set())

/** AI 分析内容状态（componentId → analysisSection） */
const aiAnalysisContents = reactive<Record<string, string>>({})

/** 组件数据加载中状态（预览/筛选刷新时） */
const dataLoading = ref(false)

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
  // 新格式：已有 pages 数组
  if (parsed.pages && Array.isArray(parsed.pages)) {
    return parsed as InsightDashboardSchema
  }
  // 旧格式：components + perspectives，迁移为单页面
  const oldComponents = parsed.components ?? []
  return {
    version: parsed.version ?? '1.0',
    pages: [{
      id: generateId('page'),
      name: t('insight.firstPageName'),
      components: oldComponents,
    }],
  }
}

/** 筛选上下文管理（基于当前页面组件） */
const {
  filterContext,
  setTimeRange,
  setDimensionFilter,
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
  dataLoading.value = true
  try {
    const dataList = await preview(props.dashboardId, context) as unknown as InsightComponentData[]
    const dataMap: Record<string, InsightComponentData> = {}
    for (const item of dataList ?? []) {
      dataMap[item.componentId] = item
    }
    componentDataMap.value = dataMap
  } catch {
    ElMessage.warning(t('insight.previewDataFailed'))
  } finally {
    dataLoading.value = false
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
  // 页面切换后重新加载数据（筛选上下文不变，但可见组件变化）
  scheduleReloadWithFilters(filterContext.value)
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
}

.preview-toolbar {
  height: 72px;
  min-height: 72px;
  background: var(--db-card);
  border-bottom: 1px solid var(--db-border);
  gap: 14px;
}

.toolbar-left {
  gap: 12px;
  min-width: 0;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--db-border-strong);
  border-radius: 8px;
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

.toolbar-title-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.toolbar-title {
  margin: 0;
  font-size: 18px;
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
  background: #14a05a;
}

.toolbar-status-dot.draft {
  background: #dd8a1d;
}

.toolbar-right {
  gap: 10px;
}

.toolbar-right :deep(.el-button.toolbar-btn) {
  height: 36px;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 13.5px;
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
  font-size: 13px;
  font-weight: 500;
  color: var(--db-accent);
  padding: 6px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}

.report-container {
  height: 100%;
  overflow-y: auto;
}

.report-content {
  max-width: 800px;
  margin: 0 auto;
  padding: var(--space-lg);
  color: var(--db-text);
  line-height: 1.8;
  animation: fadeIn var(--transition-base) both;
}

.report-content :deep(h1) {
  font-size: 24px;
  border-bottom: 2px solid var(--db-border);
  padding-bottom: var(--space-sm);
  color: var(--db-text);
}

.report-content :deep(h2) {
  font-size: 20px;
  border-bottom: 1px solid var(--db-border);
  padding-bottom: 6px;
  margin-top: var(--space-xl);
  color: var(--db-text);
}

.report-content :deep(h3) {
  font-size: 16px;
  margin-top: var(--space-lg);
  color: var(--db-text);
}

.report-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: var(--space-md) 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.report-content :deep(th),
.report-content :deep(td) {
  border: 1px solid var(--db-border);
  padding: 8px 12px;
  text-align: left;
}

.report-content :deep(th) {
  background: var(--db-hover);
  font-weight: 600;
  color: var(--db-text-secondary);
}

.report-content :deep(tr:nth-child(even)) {
  background: var(--db-hover);
}

.report-content :deep(code) {
  background: var(--db-hover);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.report-content :deep(blockquote) {
  border-left: 4px solid var(--db-border-strong);
  margin: var(--space-md) 0;
  padding: var(--space-sm) var(--space-md);
  color: var(--db-text-secondary);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--db-hover);
}

.report-content :deep(.echarts-container) {
  width: 100%;
  height: 300px;
  margin: var(--space-md) 0;
}

.report-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.report-footer .disclaimer {
  font-size: 12px;
  color: var(--db-text-muted);
}

.preview-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.page-bar {
  animation: fadeIn var(--transition-base) both;
}

.sub-page-bar {
  background: var(--db-bg);
  border-bottom: 1px solid var(--db-border);
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
  gap: 20px;
  padding: 48px 24px;
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
  height: 36px;
  border-radius: 8px;
  padding: 0 16px;
  font-size: 13.5px;
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
  .preview-toolbar {
    padding: 0 var(--space-md);
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
