<template>
  <div class="report-list-view">
    <!-- 悬浮纸面卡片：页头 + 搜索行 + Tab 行 + 列表，与灰底导航形成两层视觉层级 -->
    <div class="page-card">
    <!-- 页头：标题组（左） + 搜索（右）同一行；页名层级与筛选入口不分行 -->
    <div class="list-header">
      <div class="list-title-group">
        <span class="list-title-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        </span>
        <div class="list-title-text">
          <h2 class="list-title">{{ t('insight.reportList') }}</h2>
          <p class="list-subtitle">{{ t('insight.reportListSubtitle') }}</p>
        </div>
      </div>
      <el-input
        v-model="searchKeyword"
        :placeholder="t('insight.reportSearchPlaceholder')"
        :prefix-icon="Search"
        clearable
        class="search-input"
      />
    </div>
    <div class="list-body">
    <div class="list-content">
    <!-- Tab 行：胶囊 Tab（左） + 计数 + 排序 / 视图切换（右），与洞察页一致 -->
    <div class="tab-row">
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        <svg v-if="tab.key === 'mine'" class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        <svg v-else-if="tab.key === 'square'" class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
        <svg v-else class="tab-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
        {{ t(tab.label) }}
        <span v-if="tabCounts[tab.key] != null" class="tab-count">{{ tabCounts[tab.key] }}</span>
      </button>
    </div>
    <span class="result-count">{{ t('insight.reportResultCount', { n: filteredReports.length }) }}</span>
    <button type="button" class="filter-sort" @click="toggleSortOrder">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 8 4-4 4 4"/><path d="M7 4v16"/><path d="m21 16-4 4-4-4"/><path d="M17 20V4"/></svg>
      {{ sortOrder === 'desc' ? t('insight.sortByRecent') : t('insight.sortByOldest') }}
    </button>
    <div class="view-toggle">
      <button
        type="button"
        class="view-toggle-btn"
        :class="{ on: viewMode === 'grid' }"
        :title="t('insight.viewGrid')"
        @click="viewMode = 'grid'"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
      </button>
      <button
        type="button"
        class="view-toggle-btn"
        :class="{ on: viewMode === 'list' }"
        :title="t('insight.viewList')"
        @click="viewMode = 'list'"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
      </button>
    </div>
    </div>

    <!-- 报告列表 -->
    <div v-loading="loading" class="list-scroll">
      <div v-if="filteredReports.length === 0 && !loading" class="empty-state">
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
        <div class="empty-text">{{ searchKeyword ? t('insight.reportSearchNoResult') : emptyText }}</div>
      </div>

      <div v-else ref="cardGridRef" class="card-grid" :class="{ 'view-list': viewMode === 'list' }">
        <div
          v-for="report in filteredReports"
          :key="report.id"
          class="report-card"
          @click="handleViewReport(report)"
        >
          <div class="card-header">
            <span class="card-name">{{ report.name }}</span>
            <el-tag
              class="card-status"
              :type="report.status === 'published' ? 'success' : 'warning'"
              effect="light"
              size="small"
              round
            >
              <span class="status-dot"></span>{{ report.status === 'published' ? t('insight.status.published') : t('insight.status.draft') }}
            </el-tag>
          </div>
          <el-tooltip
            :content="report.description"
            placement="top"
            :show-after="150"
            :disabled="!truncatedDescs[report.id]"
            popper-class="card-desc-tooltip"
          >
            <div class="card-desc" :data-id="report.id">{{ report.description || t('insight.noDescription') }}</div>
          </el-tooltip>
          <div class="card-meta">
            <span class="card-time">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              {{ formatTime(report.updateTime) }}
            </span>
            <span class="card-owner">
              <span class="owner-avatar">{{ (report.ownerName || '--').charAt(0) }}</span>
              {{ report.ownerName || '--' }}
            </span>
          </div>
          <div class="card-actions" @click.stop>
            <button type="button" class="card-action-btn" @click="handleViewReport(report)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              {{ t('insight.reportViewDetail') }}
            </button>
            <!-- 洞察广场：可订阅/取消订阅（非自己发布的报告） -->
            <button
              v-if="activeTab === 'square' && !isOwner(report)"
              type="button"
              class="card-action-btn action-subscribe"
              @click="handleToggleSubscribe(report)"
            >
              <svg v-if="report.subscribed" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              {{ report.subscribed ? t('insight.reportUnsubscribe') : t('insight.reportSubscribe') }}
            </button>
            <!-- 我的订阅：可取消订阅 -->
            <button
              v-if="activeTab === 'subscribed'"
              type="button"
              class="card-action-btn action-subscribe"
              @click="handleUnsubscribe(report)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              {{ t('insight.reportUnsubscribe') }}
            </button>
            <!-- 我的洞察：可删除（仅创建者本人或工作区管理员） -->
            <button
              v-if="activeTab === 'mine' && canModifyReport(report)"
              type="button"
              class="card-action-btn action-delete"
              @click="handleDeleteReport(report)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              {{ t('insight.reportDelete') }}
            </button>
          </div>
        </div>
      </div>
    </div>
    </div>
    </div>
    </div>

    <!-- 报告详情抽屉 -->
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
            <span class="rd-header-title">{{ currentReport?.name || t('insight.reportTitle') }}</span>
            <span class="rd-header-sub">{{ t('reportDrawer.subtitle') }}</span>
          </div>
          <el-button class="rd-download" type="primary" plain size="small" :icon="Download" @click="handleDownloadReport">
            {{ t('insight.reportDownload') }}
          </el-button>
        </div>
      </template>
      <div class="report-container">
        <div ref="reportContentRef" class="report-content" v-html="currentReportHtml"></div>
        <p class="report-end-note">{{ t('insight.aiDisclaimer') }}</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { formatRelativeTime } from '@/utils/time'
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
import { usePersistedRef } from '@/composables/usePersistedRef'
import { usePermission } from '@/composables/usePermission'
import { useUserStore } from '@/stores/useUserStore'

defineOptions({
  name: 'ReportListView',
})

const { t } = useI18n()
const { canModifyResource } = usePermission()
const userStore = useUserStore()

/** Tab 定义 */
const tabs = [
  { key: 'mine', label: 'insight.reportTabMine' },
  { key: 'square', label: 'insight.reportTabSquare' },
  { key: 'subscribed', label: 'insight.reportTabSubscribed' },
] as const

type TabKey = (typeof tabs)[number]['key']

/** 当前激活的 Tab（刷新后保留） */
const activeTab = usePersistedRef<TabKey>(
  'mc-report-active-tab',
  'mine',
  (value) => tabs.map((t) => t.key).includes(value as TabKey),
)

/** 报告列表数据 */
const reports = ref<InsightReport[]>([])

/** 各 Tab 报告计数（null = 未加载） */
const tabCounts = ref<Record<TabKey, number | null>>({ mine: null, square: null, subscribed: null })

/** 搜索关键词 */
const searchKeyword = ref('')

/** 排序顺序：desc = 按最近更新（默认），与洞察页一致 */
const sortOrder = ref<'desc' | 'asc'>('desc')

/** 视图模式：grid = 网格 / list = 单列，与洞察页一致 */
const viewMode = ref<'grid' | 'list'>('grid')

/** 切换排序顺序 */
function toggleSortOrder(): void {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
}

/** 卡片网格容器 ref：用于测量描述截断状态 */
const cardGridRef = ref<HTMLElement | null>(null)

/** 描述被截断的报告 id 集合：仅截断的卡片悬停时弹出完整描述 */
const truncatedDescs = ref<Record<string, boolean>>({})

/** 测量各卡片描述是否被 line-clamp 截断（scrollHeight 超出两行可视高度即为截断） */
function measureDescTruncation(): void {
  const root = cardGridRef.value
  if (!root) {
    return
  }
  const map: Record<string, boolean> = {}
  root.querySelectorAll<HTMLElement>('.card-desc').forEach((el) => {
    const id = el.dataset.id
    if (id) {
      map[id] = el.scrollHeight > el.clientHeight
    }
  })
  truncatedDescs.value = map
}

/** 窗口尺寸变化会改变卡片宽度与换行数，需重新测量 */
function handleWindowResize(): void {
  measureDescTruncation()
}

/** 按关键词过滤并按更新时间排序报告列表 */
const filteredReports = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const base = keyword
    ? reports.value.filter((r) => {
        return r.name?.toLowerCase().includes(keyword)
          || r.description?.toLowerCase().includes(keyword)
          || r.ownerName?.toLowerCase().includes(keyword)
      })
    : [...reports.value]
  base.sort((a, b) => {
    const ta = a.updateTime || ''
    const tb = b.updateTime || ''
    return sortOrder.value === 'desc' ? tb.localeCompare(ta) : ta.localeCompare(tb)
  })
  return base
})

/* watch 必须位于 filteredReports 声明之后：
   setup 同步执行时求值源数组会访问未初始化的 const，抛出 TDZ ReferenceError 导致整页崩溃 */
watch([filteredReports, viewMode], () => {
  void nextTick(measureDescTruncation)
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
  window.addEventListener('resize', handleWindowResize)
  loadReports()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
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
    tabCounts.value[activeTab.value] = (data ?? []).length
    // 异步同步其余两个 Tab 的计数，不阻塞当前列表展示
    void loadTabCounts(activeTab.value)
  } catch {
    ElMessage.error(t('insight.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 各 Tab 计数加载器 */
const tabLoaders: Record<TabKey, () => Promise<InsightReport[] | null>> = {
  mine: listMyReports,
  square: listReports,
  subscribed: listSubscribedReports,
}

/** 加载除 skip 外的 Tab 计数（当前激活 Tab 的计数由 loadReports 同步填充） */
async function loadTabCounts(skip: TabKey): Promise<void> {
  const entries = tabs.filter((tab) => tab.key !== skip)
  const results = await Promise.allSettled(entries.map((tab) => tabLoaders[tab.key]()))
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      tabCounts.value[entries[index].key] = (result.value ?? []).length
    }
  })
}

/** 判断是否为当前用户发布的报告 */
function isOwner(report: InsightReport): boolean {
  // 洞察广场中，自己发布的报告不显示订阅按钮
  if (report.ownerId != null) {
    return String(report.ownerId) === String(userStore.userId)
  }
  return false
}

/**
 * 是否可删除该报告：创建者本人 或 工作区 admin/owner
 * （与后端 deleteReport 的归属校验对齐）
 */
function canModifyReport(report: InsightReport): boolean {
  return canModifyResource(report.ownerId)
}

/** 格式化时间 */
/** 格式化时间：相对时间（刚刚 / x 分钟前 / 昨天 HH:mm…），空值回退 -- */
function formatTime(time: string): string {
  return formatRelativeTime(time) || '--'
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

  // 清理未被图表替换的占位符文本（如 [echarts:chart_0]），避免原始标记外露
  reportContentRef.value.querySelectorAll('p, div, span, li').forEach((node) => {
    const el = node as HTMLElement
    if (el.classList.contains('echarts-container')) return
    const text = (el.textContent || '').trim()
    if (el.children.length === 0 && /^\[echarts:[\w-]+\]$/.test(text)) {
      const parent = el.parentElement
      el.remove()
      // 外层包裹元素若因此变空，一并移除
      if (parent && parent !== reportContentRef.value && !parent.children.length && !(parent.textContent || '').trim()) {
        parent.remove()
      }
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

  // 从报告的 echartsOptions 字段收集图表数据，内联到 HTML 中供离线渲染
  const echartsData: Record<string, Record<string, unknown>> = {}
  if (currentReport.value.echartsOptions) {
    try {
      const parsed = JSON.parse(currentReport.value.echartsOptions)
      for (const [key, val] of Object.entries(parsed)) {
        if (typeof val === 'string') {
          echartsData[key] = JSON.parse(val)
        } else {
          echartsData[key] = val as Record<string, unknown>
        }
      }
    } catch (e) {
      console.error('[Report] Parse echartsOptions error:', e)
    }
  }
  const echartsDataJson = JSON.stringify(echartsData)

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
  /* 四周留灰底边距，让纸面卡片悬浮于页面底色之上，与透明导航拉开层级 */
  padding: var(--space-md) var(--space-lg) var(--space-lg);
}

/* 悬浮纸面卡片：承载工具行与列表，白色表面 + 圆角 + 投影，与灰底页面分层 */
.page-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

/* 页头：标题组（左） + 搜索（右）同一行，建立页面层级且不占多余行数 */
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 20px var(--space-xl) 12px;
  flex-shrink: 0;
}

/* 标题组：图标 chip（左） + 标题/副标题（右），横向排布 */
.list-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 标题图标：主题色淡底圆角 chip，轻量不抢顶导层级 */
.list-title-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.list-title-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.list-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 var(--space-xl) var(--space-xl);
}

/* Tab 行：Tab 胶囊（左，随内容收缩） + 计数 + 排序 / 视图切换（右） */
.tab-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 0 0 var(--space-md);
  flex-shrink: 0;
}

/* Tab 容器：灰底分段胶囊（次级控件，内 3px 衬住激活项） */
.tab-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 999px;
  background: var(--db-bg);
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.tab-bar::-webkit-scrollbar {
  display: none;
}

/* Tab 项：默认灰容器上透明，hover 浅灰底；
   激活为白面浮起 + 主题色描边/文字（次级高亮，实心 pill 只留给顶导） */
.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
  flex-shrink: 0;
  transition: color var(--transition-fast), background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.tab-btn:hover:not(.active) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.tab-btn.active {
  background: var(--theme-surface-elevated);
  border-color: color-mix(in srgb, var(--main-orange) 40%, transparent);
  color: var(--main-orange);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.tab-icon {
  width: 14px;
  height: 14px;
  color: var(--db-text-muted);
  flex-shrink: 0;
  transition: color var(--transition-fast);
}

.tab-btn:hover:not(.active) .tab-icon {
  color: var(--db-text-secondary);
}

.tab-btn.active .tab-icon {
  color: var(--main-orange);
}

/* Tab 计数徽标：默认半透明灰 chip，激活项上主题色淡底 */
.tab-count {
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  border-radius: 999px;
  padding: 0 7px;
  min-width: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.tab-btn.active .tab-count {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 排序按钮与视图切换：与洞察页完全一致 */
.filter-sort {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  font-size: 12.5px;
  color: var(--db-text-secondary);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
  font-family: inherit;
}

.filter-sort:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.view-toggle {
  display: flex;
  border: 1px solid var(--db-border-strong);
  border-radius: 7px;
  overflow: hidden;
  background: var(--db-card);
  flex-shrink: 0;
}

.view-toggle-btn {
  width: 32px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--db-text-muted);
  cursor: pointer;
  padding: 0;
}

.view-toggle-btn.on {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--main-orange);
}

/* 搜索行：搜索居左、计数居右；作为卡片首行顶部留白加大，与下方 Tab 行分隔 */
.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-lg) 0 var(--space-md);
  flex-shrink: 0;
}

/* 搜索框：白底描边胶囊（36px、中等宽度），置于页头右侧与标题同行，聚焦主题色光环 */
.search-input {
  width: 280px;
  max-width: 100%;
  flex-shrink: 0;
}

.search-input :deep(.el-input__wrapper) {
  height: 36px;
  border-radius: 999px;
  background: var(--theme-surface-elevated);
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--theme-border-strong);
  padding: 0 14px;
  transition: box-shadow var(--transition-fast), background var(--transition-fast);
}

.search-input :deep(.el-input__inner) {
  font-size: 13px;
}

.search-input :deep(.el-input__wrapper:hover) {
  box-shadow:
    var(--shadow-sm),
    inset 0 0 0 1px color-mix(in srgb, var(--main-orange) 45%, var(--theme-border-strong));
}

.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent),
    inset 0 0 0 1px var(--main-orange);
}

.search-input :deep(.el-input__prefix) {
  color: var(--db-text-muted);
  transition: color var(--transition-fast);
}

.search-input :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--main-orange);
}

/* 结果计数：位于 Tab 行右侧，随搜索实时变化 */
.result-count {
  font-size: 12px;
  color: var(--db-text-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 窄屏：页头换行，搜索独占整行 */
@media (max-width: 640px) {
  .list-header {
    flex-wrap: wrap;
  }

  .search-input {
    flex: 1 1 100%;
    width: 100%;
  }
}

.list-scroll {
  flex: 1;
  overflow-y: auto;
  /* 顶部留白：为首卡片 hover 上浮预留空间，避免上边框/投影被滚动容器裁切 */
  padding: 4px 0 var(--space-xl);
}

.empty-state {
  width: 100%;
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 48px 24px;
  color: var(--db-text-muted);
}

.empty-illustration svg {
  display: block;
  filter: drop-shadow(0 4px 12px color-mix(in srgb, var(--main-orange) 12%, transparent));
}

.empty-text {
  font-size: 13px;
  line-height: 1.5;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-lg);
}

.card-grid.view-list {
  grid-template-columns: 1fr;
}

.report-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  /* 白色纸面上卡片以边框区分，默认无阴影，悬浮时抬升 */
  cursor: pointer;
  transition: box-shadow var(--transition-base), border-color var(--transition-fast), transform var(--transition-fast);
  overflow: hidden;
}

.report-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态标签：圆角胶囊 + 圆点（与洞察卡片统一） */
.card-status.el-tag {
  margin-left: auto;
  border: none;
  border-radius: 20px;
  font-weight: 600;
  flex-shrink: 0;
}

.card-status .status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 5px;
}

.card-status.el-tag--success {
  background: #e7f8ef;
  color: #14a05a;
}

.card-status.el-tag--warning {
  background: #fdf1e0;
  color: #dd8a1d;
}

.card-desc {
  /* 上下间距用 margin 而非 padding：line-clamp 的裁剪边界是 padding-box，
     垂直 padding 会让被截断的第三行上半部分漏绘到 padding 区、与 meta 行重叠 */
  margin: 8px 0;
  font-size: 12px;
  color: var(--db-text-secondary);
  /* 整数行高，避免小数行盒在 clamp 边界产生像素级溢出 */
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 10px;
  gap: var(--space-md);
  /* 等高卡片中吸收剩余空间：短描述卡片的 meta/操作区与长描述卡片底部对齐 */
  margin-top: auto;
}

.card-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--db-text-muted);
  white-space: nowrap;
}

.card-time svg {
  flex-shrink: 0;
}

.card-owner {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--db-text-secondary);
  white-space: nowrap;
}

.owner-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--db-accent-light);
  color: var(--db-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  border-top: 1px solid var(--db-border);
  padding-top: 8px;
}

/* 轻量文字操作按钮（与洞察列表页卡片操作按钮统一） */
.card-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  font-size: 11.5px;
  color: var(--db-text-secondary);
  cursor: pointer;
  border-radius: 4px;
  padding: 4px 6px;
  transition: color var(--transition-fast), background var(--transition-fast);
  white-space: nowrap;
}

.card-action-btn:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.card-action-btn.action-subscribe {
  color: var(--main-orange);
  font-weight: 600;
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.card-action-btn.action-subscribe:hover {
  background: color-mix(in srgb, var(--main-orange) 14%, transparent);
}

.card-action-btn.action-delete {
  color: #ef4444;
}

.card-action-btn.action-delete:hover {
  color: #dc2626;
  background: var(--db-danger-bg);
}

/* 报告抽屉样式 */
.report-container {
  height: 100%;
  overflow-y: auto;
  /* 层级一：抽屉内页面底色，与内容白纸卡形成对比 */
  background: var(--db-bg);
  padding: 20px;
}

/* 抽屉自定义头部（#header 插槽内容） */
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

/* 抽屉头部右侧的下载按钮（轻量 plain 样式，与关闭按钮视觉平衡） */
.rd-download {
  margin-left: auto;
  flex-shrink: 0;
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  font-weight: 500;
}

/* 报告结尾免责声明 */
.report-end-note {
  max-width: 800px;
  margin: 16px auto 0;
  text-align: center;
  font-size: 12px;
  color: var(--db-text-muted);
}
</style>

<style>
/* el-drawer teleport 到 body，scoped 选择器无法命中其内部节点，
   故用非 scoped 块 + 自定义类名限定作用域 */
.report-detail-drawer {
  --el-color-primary: var(--main-orange, #4176E6);
  --el-color-primary-light-3: color-mix(in srgb, var(--main-orange, #4176E6) 70%, var(--theme-surface, #fff));
  --el-color-primary-light-5: color-mix(in srgb, var(--main-orange, #4176E6) 50%, var(--theme-surface, #fff));
  --el-color-primary-light-7: color-mix(in srgb, var(--main-orange, #4176E6) 30%, var(--theme-surface, #fff));
  --el-color-primary-light-8: color-mix(in srgb, var(--main-orange, #4176E6) 20%, var(--theme-surface, #fff));
  --el-color-primary-light-9: color-mix(in srgb, var(--main-orange, #4176E6) 10%, var(--theme-surface, #fff));
  --el-color-primary-dark-2: color-mix(in srgb, var(--main-orange, #4176E6) 85%, #000);
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
