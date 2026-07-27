<template>
  <div class="report-list-view">
    <!-- 顶部工具栏 -->
    <div class="list-header mc-toolbar">
      <h2 class="list-title mc-toolbar-title">{{ t('insight.reportList') }}</h2>
      <el-input
        v-model="searchKeyword"
        :placeholder="t('insight.reportSearchPlaceholder')"
        :prefix-icon="Search"
        clearable
        size="default"
        class="search-input"
      />
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ t(tab.label) }}
      </button>
    </div>

    <!-- 报告列表 -->
    <div v-loading="loading" class="list-content">
      <div v-if="filteredReports.length === 0 && !loading" class="empty-state">
        <div class="empty-icon">📋</div>
        <div class="empty-text">{{ searchKeyword ? t('insight.searchNoResult') : emptyText }}</div>
      </div>

      <div v-else class="card-grid">
        <div
          v-for="report in filteredReports"
          :key="report.id"
          class="report-card"
        >
          <div class="card-header">
            <span class="card-name">{{ report.name }}</span>
            <el-tag :type="report.status === 'published' ? 'success' : 'info'" size="small">
              {{ report.status === 'published' ? t('insight.status.published') : t('insight.status.draft') }}
            </el-tag>
          </div>
          <div class="card-desc">{{ report.description || t('insight.noDescription') }}</div>
          <div class="card-meta">
            <span class="card-owner">{{ report.ownerName || '--' }}</span>
            <span class="card-time">{{ formatTime(report.updateTime) }}</span>
          </div>
          <div class="card-actions">
            <el-tooltip :content="t('insight.reportViewDetail')" placement="top">
              <el-button text size="small" :icon="View" @click="handleViewReport(report)" />
            </el-tooltip>
            <!-- 我的洞察：可删除 -->
            <el-tooltip v-if="activeTab === 'mine'" :content="t('insight.reportDelete')" placement="top">
              <el-button text size="small" type="danger" :icon="Delete" @click="handleDeleteReport(report)" />
            </el-tooltip>
            <!-- 洞察广场：可订阅/取消订阅（非自己发布的报告） -->
            <el-tooltip
              v-if="activeTab === 'square' && !isOwner(report)"
              :content="report.subscribed ? t('insight.reportUnsubscribe') : t('insight.reportSubscribe')"
              placement="top"
            >
              <el-button
                text
                size="small"
                :type="report.subscribed ? 'warning' : 'primary'"
                :icon="report.subscribed ? StarFilled : Star"
                @click="handleToggleSubscribe(report)"
              />
            </el-tooltip>
            <!-- 我的订阅：可取消订阅 -->
            <el-tooltip v-if="activeTab === 'subscribed'" :content="t('insight.reportUnsubscribe')" placement="top">
              <el-button text size="small" type="warning" :icon="StarFilled" @click="handleUnsubscribe(report)" />
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>

    <!-- 报告详情抽屉 -->
    <el-drawer
      v-model="reportDrawerVisible"
      :title="currentReport?.name || t('insight.reportTitle')"
      direction="rtl"
      size="50%"
      :close-on-press-escape="true"
      @close="handleReportDrawerClose"
    >
      <div class="report-container">
        <div ref="reportContentRef" class="report-content" v-html="currentReportHtml"></div>
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
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Delete, Star, StarFilled, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import type { InsightReport } from '@/types'
import {
  listMyReports,
  listReports,
  listSubscribedReports,
  getReportDetail,
  deleteReport,
  subscribeReport,
  unsubscribeReport,
} from '@/api/insight-report'

defineOptions({
  name: 'ReportListView',
})

const { t } = useI18n()

/** Tab 定义 */
const tabs = [
  { key: 'mine', label: 'insight.reportTabMine' },
  { key: 'square', label: 'insight.reportTabSquare' },
  { key: 'subscribed', label: 'insight.reportTabSubscribed' },
] as const

type TabKey = (typeof tabs)[number]['key']

/** 当前激活的 Tab */
const activeTab = ref<TabKey>('mine')

/** 报告列表数据 */
const reports = ref<InsightReport[]>([])

/** 搜索关键词 */
const searchKeyword = ref('')

/** 按关键词过滤报告列表 */
const filteredReports = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return reports.value
  }
  return reports.value.filter((r) => {
    return r.name?.toLowerCase().includes(keyword)
      || r.description?.toLowerCase().includes(keyword)
      || r.ownerName?.toLowerCase().includes(keyword)
  })
})

/** 加载状态 */
const loading = ref(false)

/** 报告抽屉可见性 */
const reportDrawerVisible = ref(false)

/** 当前查看的报告 */
const currentReport = ref<InsightReport | null>(null)

/** 当前报告HTML内容 */
const currentReportHtml = ref('')

/** 报告内容 DOM ref */
const reportContentRef = ref<HTMLElement | null>(null)

/** 报告中的 ECharts 实例列表 */
const reportChartInstances = ref<echarts.ECharts[]>([])

/** 空状态文案 */
const emptyText = computed(() => {
  if (activeTab.value === 'mine') {
    return t('insight.reportListEmptyMine')
  }
  if (activeTab.value === 'square') {
    return t('insight.reportListEmptySquare')
  }
  return t('insight.reportListEmptySubscribed')
})

onMounted(() => {
  loadReports()
})

/** 切换 Tab */
function switchTab(key: TabKey): void {
  if (activeTab.value === key) {
    return
  }
  activeTab.value = key
  loadReports()
}

/** 加载报告列表 */
async function loadReports(): Promise<void> {
  loading.value = true
  try {
    let data: InsightReport[] | null = null
    if (activeTab.value === 'mine') {
      data = await listMyReports()
    } else if (activeTab.value === 'square') {
      data = await listReports()
    } else {
      data = await listSubscribedReports()
    }
    reports.value = data ?? []
  } catch {
    ElMessage.error(t('insight.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 判断是否为当前用户发布的报告 */
function isOwner(report: InsightReport): boolean {
  // 洞察广场中，自己发布的报告不显示订阅按钮
  return false // 后端已通过 subscribed 字段标记，自己的报告不需要订阅
}

/** 格式化时间 */
function formatTime(time: string): string {
  if (!time) {
    return '--'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

/** 查看报告详情 */
async function handleViewReport(report: InsightReport): Promise<void> {
  try {
    const detail = await getReportDetail(report.id)
    currentReport.value = detail
    currentReportHtml.value = detail.reportContent || ''
    reportDrawerVisible.value = true
    setTimeout(() => {
      renderReportCharts()
    }, 300)
  } catch {
    ElMessage.error(t('insight.loadFailed'))
  }
}

/** 渲染报告中的 ECharts 图表 */
function renderReportCharts(): void {
  if (!reportContentRef.value) {
    return
  }

  // 先销毁旧实例
  disposeReportCharts()

  // 从报告的 echartsOptions 字段构建 chartId → option 映射
  const echartsOptionMap: Record<string, Record<string, unknown>> = {}
  if (currentReport.value?.echartsOptions) {
    try {
      const parsed = JSON.parse(currentReport.value.echartsOptions)
      for (const [key, val] of Object.entries(parsed)) {
        if (typeof val === 'string') {
          echartsOptionMap[key] = JSON.parse(val)
        } else {
          echartsOptionMap[key] = val as Record<string, unknown>
        }
      }
    } catch (e) {
      console.error('[Report] Parse echartsOptions error:', e)
    }
  }

  // 扫描报告中的 echarts-container div
  const containers = reportContentRef.value.querySelectorAll('.echarts-container')
  containers.forEach((container) => {
    const el = container as HTMLElement
    const chartId = el.dataset.chartId
    if (!chartId || !echartsOptionMap[chartId]) {
      return
    }

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
    if (!chart.isDisposed()) {
      chart.dispose()
    }
  })
  reportChartInstances.value = []
}

/** 报告抽屉关闭时清理 ECharts 实例 */
function handleReportDrawerClose(): void {
  disposeReportCharts()
  currentReport.value = null
  currentReportHtml.value = ''
}

/** 下载报告 */
function handleDownloadReport(): void {
  if (!currentReportHtml.value || !currentReport.value) {
    return
  }
  const reportName = currentReport.value.name || 'report'
  const timestamp = new Date().toISOString().slice(0, 10)

  const fullHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${t('insight.reportTitle')} - ${reportName}</title>
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
${currentReportHtml.value}
  <div class="disclaimer">${t('insight.aiDisclaimer')}</div>
  <script>
    (function() {
      var containers = document.querySelectorAll('.echarts-container');
      containers.forEach(function(el) {
        var optionStr = el.getAttribute('data-option');
        if (optionStr && typeof echarts !== 'undefined') {
          try {
            var option = JSON.parse(optionStr);
            var chart = echarts.init(el);
            chart.setOption(option);
          } catch(e) { console.error(e); }
        }
      });
      window.addEventListener('resize', function() {
        containers.forEach(function(el) {
          var instance = echarts.getInstanceByDom(el);
          if (instance) instance.resize();
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
  link.download = `${reportName}_${timestamp}.html`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 删除报告 */
async function handleDeleteReport(report: InsightReport): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('insight.reportDeleteConfirm', { name: report.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await deleteReport(report.id)
    ElMessage.success(t('insight.reportDeleteSuccess'))
    await loadReports()
  } catch (e) {
    // 用户取消删除时不报错
    if (e !== 'cancel') {
      ElMessage.error(t('insight.reportDeleteFailed'))
    }
  }
}

/** 切换订阅状态（洞察广场） */
async function handleToggleSubscribe(report: InsightReport): Promise<void> {
  try {
    if (report.subscribed) {
      await unsubscribeReport(report.id)
      ElMessage.success(t('insight.reportUnsubscribeSuccess'))
    } else {
      await subscribeReport(report.id)
      ElMessage.success(t('insight.reportSubscribeSuccess'))
    }
    await loadReports()
  } catch {
    ElMessage.error(t('insight.reportSubscribeFailed'))
  }
}

/** 取消订阅（我的订阅） */
async function handleUnsubscribe(report: InsightReport): Promise<void> {
  try {
    await unsubscribeReport(report.id)
    ElMessage.success(t('insight.reportUnsubscribeSuccess'))
    await loadReports()
  } catch {
    ElMessage.error(t('insight.reportSubscribeFailed'))
  }
}
</script>

<style scoped>
.report-list-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
  overflow: hidden;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  min-height: 56px;
  background: var(--db-card);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.list-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text);
}

.search-input {
  width: 200px;
}

/* Tab 栏 */
.tab-bar {
  display: flex;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-xl);
  background: var(--db-card);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.tab-btn {
  padding: 6px 16px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--db-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  line-height: 22px;
}

.tab-btn:hover {
  background: var(--db-hover);
  color: var(--db-text);
}

.tab-btn.active {
  background: var(--db-accent-light);
  color: var(--db-accent);
}

.list-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  color: var(--db-text-muted);
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-lg);
}

.report-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-base), border-color var(--transition-fast);
}

.report-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.card-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: var(--db-text-secondary);
  min-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.card-owner {
  font-size: 12px;
  font-weight: 500;
  color: var(--db-accent);
  background: var(--db-accent-light);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.card-time {
  font-size: 12px;
  color: var(--db-text-muted);
}

.card-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-xs);
  border-top: 1px solid var(--db-border);
  padding-top: var(--space-sm);
  margin-top: var(--space-xs);
}

/* 报告抽屉样式 */
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
</style>
