<template>
  <div class="data-table-widget">
    <div class="table-header" :class="`title-bar-${props.component.titleBarStyle ?? 'standard'}`">
      <div class="table-header-left">
        <div v-if="showTitle !== false && component.titleBarStyle !== 'hidden'" class="table-title"><DashboardComponentIcon type="table" :dashboard-theme="dashboardTheme" :title-icon-style="titleIconStylePreview ?? component.titleIconStyle" :theme-accent-group="component.themeAccentGroup" :component-color="component.componentColor" />{{ component.title }}</div>
      </div>
      <div class="table-header-right">
        <div v-if="showTimeFilter" class="table-time-filter">
          <el-date-picker
            v-model="localDateRange"
            type="daterange"
            size="small"
            style="width: 220px"
            value-format="YYYY-MM-DD"
            unlink-panels
            :shortcuts="dateShortcuts"
            :start-placeholder="t('insight.timeRange.startPlaceholder')"
            :end-placeholder="t('insight.timeRange.endPlaceholder')"
            @change="handleDateChange"
          />
        </div>
        <el-tooltip v-if="activeTableData && activeTableData.rows.length > 0" :content="t('insight.tableExportCsv')" placement="top">
          <el-button text size="small" :icon="Download" :aria-label="t('insight.tableExportCsv')" @click="handleExportCsv" />
        </el-tooltip>
      </div>
    </div>
    <!-- Tab 栏（多 Tab 模式） -->
    <div v-if="hasTabs" class="widget-tabs" role="tablist" aria-label="数据表分页">
      <div
        v-for="(tab, tabIndex) in tabList"
        :key="tab.id"
        class="widget-tab"
        :class="{ active: activeTabId === tab.id }"
        role="tab"
        :aria-selected="activeTabId === tab.id"
        :tabindex="activeTabId === tab.id ? 0 : -1"
        :data-tab-id="tab.id"
        @click="selectTab(tab.id)"
        @keydown="handleTabKeydown($event, tab.id)"
      >
        <DashboardTabTitle
          :title="tab.title"
          :title-icon-style="tab.titleIconStyle"
          :title-icon-style-preview="tabIconStylePreview(tab)"
          :dashboard-theme="dashboardTheme"
          :variant="tabIndex"
          :editable="editable"
          @edit="(anchor) => emit('edit-tab-title-icon-style', { componentId: component.id, tabId: tab.id, tabKind: 'component', anchor })"
        />
      </div>
    </div>
    <div class="table-wrapper">
      <el-table
        v-if="activeTableData && activeTableData.rows.length > 0"
        :data="pagedTableRows"
        @sort-change="handleSortChange"
        border
        size="small"
        height="100%"
        show-header
        style="width: 100%"
      >
        <el-table-column
          v-for="(col, idx) in activeTableData.columns"
          :key="idx"
          :prop="`col_${idx}`"
          :label="componentData?.fieldLabels?.[col] ?? col"
          min-width="120"
          :sortable="isFieldSortable(col, idx) ? 'custom' : false"
          :sort-orders="['ascending', 'descending', null]"
          show-overflow-tooltip
        />
      </el-table>
      <div v-else class="table-placeholder" role="status">
        <template v-if="activeTabError">{{ activeTabError }}</template>
        <template v-else-if="!hasConfiguredDataset(props.component)">尚未绑定数据源，请在属性配置中添加数据。</template>
        <template v-else>暂无数据，请检查查询配置或筛选条件。</template>
      </div>
    </div>
    <!-- 分页 -->
    <div v-if="showPagination" class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="pageSizes"
        :total="activeTableRows.length"
        :pager-count="5"
        layout="total, sizes, prev, pager, next, jumper"
        size="small"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download } from '@element-plus/icons-vue'
import type { InsightComponent, InsightComponentData, TimeRangeValue, ComponentTab, ComponentTitleIconStyle, DashboardTabTitleIconStylePreview, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import DashboardTabTitle from './DashboardTabTitle.vue'
import { hasConfiguredDataset } from '@/utils/component-sample-data'
import { readComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'

defineOptions({
  name: 'DataTableWidget',
})

const { t } = useI18n()

const props = withDefaults(defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否由组件内部显示标题；画布编辑态由统一标题栏显示 */
  showTitle?: boolean
  /** 当前内容是否为未绑定数据源时的默认样例数据 */
  sampleMode?: boolean
  dashboardTheme?: ResolvedDashboardTheme
  /** 编辑态显示页签图标设置操作 */
  editable?: boolean
  /** 正在编辑的组件标题图标样式即时预览 */
  titleIconStylePreview?: ComponentTitleIconStyle
  /** 正在编辑的页签图标样式即时预览 */
  tabTitleIconStylePreview?: DashboardTabTitleIconStylePreview
}>(), { showTitle: true, sampleMode: false, editable: false })

const emit = defineEmits<{
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
  (e: 'edit-tab-title-icon-style', payload: { componentId: string; tabId: string; tabKind: 'component'; anchor: HTMLElement }): void
}>()

/** 分页选项；最终仍受查询配置的 maxPageSize 限制。 */
const PAGE_SIZE_OPTIONS = [1, 2, 5, 10, 20, 50, 100, 200, 500]

const tableData = computed(() => props.componentData?.table)
const showTimeFilter = computed(() => props.component.enableTimeFilter)

/** 表格消费的是组件最终结果：直连数据集读唯一输入配置，Python 读最终结果配置。 */
const queryPolicy = computed(() => {
  const pipeline = readComponentDatasetPipeline(props.component)
  if (pipeline?.script?.trim()) {
    const config = pipeline.finalResultQueryConfig
    return config?.confirmed === true ? config : undefined
  }
  return pipeline?.datasetInputs.length === 1 ? pipeline.datasetInputs[0]?.queryConfig : undefined
})
const sortPolicy = computed(() => queryPolicy.value?.sortPolicy)
const paginationPolicy = computed(() => queryPolicy.value?.paginationPolicy)
const paginationEnabled = computed(() => paginationPolicy.value?.enabled === true)

function clampPageSize(size: number | undefined): number {
  const max = Math.min(500, Math.max(1, paginationPolicy.value?.maxPageSize || 500))
  const configured = size && size > 0 ? size : 20
  return Math.min(configured, max)
}

const configuredDefaultPageSize = computed(() => clampPageSize(paginationPolicy.value?.defaultPageSize))
const pageSizes = computed(() => {
  const max = Math.min(500, Math.max(1, paginationPolicy.value?.maxPageSize || 500))
  const options = PAGE_SIZE_OPTIONS.filter((size) => size <= max)
  if (!options.includes(configuredDefaultPageSize.value)) options.push(configuredDefaultPageSize.value)
  return options.sort((a, b) => a - b)
})

/** 是否有多 Tab 模式（基于组件配置判断，而非后端返回数据） */
const hasTabs = computed(() => {
  return !!(props.component.tabs && props.component.tabs.length > 0)
})

/** Tab 列表（从组件配置构建） */
const tabList = computed<ComponentTab[]>(() => props.component.tabs ?? [])
function tabIconStylePreview(tab: ComponentTab) {
  const preview = props.tabTitleIconStylePreview
  return preview?.componentId === props.component.id && preview.tabId === tab.id
    ? preview.titleIconStyle
    : undefined
}

/** 当前激活的 Tab ID */
const activeTabId = ref('')

function selectTab(tabId: string): void {
  activeTabId.value = tabId
}

function handleTabKeydown(event: KeyboardEvent, tabId: string): void {
  const currentIndex = tabList.value.findIndex(tab => tab.id === tabId)
  if (currentIndex < 0 || tabList.value.length < 2) return
  let nextIndex = currentIndex
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % tabList.value.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + tabList.value.length) % tabList.value.length
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = tabList.value.length - 1
  else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectTab(tabId); return }
  else return
  event.preventDefault()
  const nextTab = tabList.value[nextIndex]
  if (!nextTab) return
  selectTab(nextTab.id)
  nextTick(() => {
    const tab = Array.from(document.querySelectorAll<HTMLElement>('.data-table-widget [role="tab"]'))
      .find(candidate => candidate.dataset.tabId === nextTab.id)
    tab?.focus()
  })
}

// 初始化 / 切换组件或 Tab 定义时自动选中一个仍然存在的 Tab。
// 不能只监听 hasTabs：编辑器替换 Tab 定义后，旧 activeTabId 可能已经不存在。
watch(() => tabList.value.map(tab => tab.id).join('|'), () => {
  if (!hasTabs.value) {
    activeTabId.value = ''
    return
  }
  if (!tabList.value.some(tab => tab.id === activeTabId.value)) {
    activeTabId.value = tabList.value[0]?.id ?? ''
  }
}, { immediate: true })

/** 当前 Tab 的渲染数据 */
const activeTabData = computed(() => {
  if (!hasTabs.value || !activeTabId.value) return null
  return props.componentData?.tabs?.[activeTabId.value] ?? null
})

/** 当前 Tab 的表格数据（Tab 模式下不 fallback 到主数据，避免未配置数据源的 Tab 显示其他 Tab 数据） */
const activeTableData = computed(() => {
  if (hasTabs.value) {
    return activeTabData.value?.table ?? null
  }
  return tableData.value
})
const activeTabError = computed(() => activeTabData.value?.error)

/** 组件级时间选择器绑定值 */
const localDateRange = ref<[string, string] | null>(null)

/** 快捷选项 */
const dateShortcuts = computed(() => [
  { text: t('insight.timeRange.today'), value: () => { const today = new Date(); return [today, today] } },
  { text: t('insight.timeRange.7d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 6); return [start, end] } },
  { text: t('insight.timeRange.30d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 29); return [start, end] } },
  { text: t('insight.timeRange.90d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 89); return [start, end] } },
])

/** 日期选择变化 → 转换为 TimeRangeValue 并 emit */
function handleDateChange(val: [string, string] | null): void {
  if (!val) {
    emit('component-time-range-change', { componentId: props.component.id, timeRange: undefined })
    return
  }
  const [start, end] = val
  const timeRange: TimeRangeValue = { preset: 'custom', start, end }
  emit('component-time-range-change', { componentId: props.component.id, timeRange })
}

/** 将行列数据转为 el-table 需要的对象数组格式 */
const activeTableRows = computed(() => {
  if (!activeTableData.value) {
    return []
  }
  return activeTableData.value.rows.map((row) => {
    const obj: Record<string, string> = {}
    row.forEach((val, idx) => {
      obj[`col_${idx}`] = val
    })
    return obj
  })
})

/** 分页状态 */
const currentPage = ref(1)
const pageSize = ref(20)
const sortState = ref<{ field: string; order: 'ascending' | 'descending' } | null>(null)

watch(configuredDefaultPageSize, (size) => {
  pageSize.value = size
  currentPage.value = 1
}, { immediate: true })

/** 数据变化时重置页码 */
watch(activeTableRows, () => {
  currentPage.value = 1
  sortState.value = null
})

watch(() => props.sampleMode, () => {
  currentPage.value = 1
  sortState.value = null
})

function isFieldSortable(field: string, index: number): boolean {
  if (sortPolicy.value?.enabled) return sortPolicy.value.allowedFields.includes(field)
  // 未配置数据源的样例仍保留原本的演示排序行为。
  return props.sampleMode && index === 1
}

function handleSortChange(sort: { prop: string; order: 'ascending' | 'descending' | null }): void {
  const columnIndex = Number(sort.prop.replace(/^col_/, ''))
  const field = Number.isInteger(columnIndex) ? activeTableData.value?.columns[columnIndex] : undefined
  if (!field || !isFieldSortable(field, columnIndex) || !sort.order) {
    sortState.value = null
    currentPage.value = 1
    return
  }
  sortState.value = { field, order: sort.order }
  currentPage.value = 1
}

/** 画布结果集已完整物化；按查询配置排序后再分页展示。 */
const sortedTableRows = computed(() => {
  const sort = sortState.value
  if (!sort) return activeTableRows.value
  const columnIndex = activeTableData.value?.columns.indexOf(sort.field) ?? -1
  if (columnIndex < 0) return activeTableRows.value
  const prop = `col_${columnIndex}`
  const direction = sort.order === 'ascending' ? 1 : -1
  return [...activeTableRows.value].sort((left, right) => {
    const leftValue = left[prop] ?? ''
    const rightValue = right[prop] ?? ''
    const leftNumber = Number(leftValue)
    const rightNumber = Number(rightValue)
    const bothNumeric = leftValue !== '' && rightValue !== '' && Number.isFinite(leftNumber) && Number.isFinite(rightNumber)
    const comparison = bothNumeric
      ? leftNumber - rightNumber
      : leftValue.localeCompare(rightValue, undefined, { numeric: true, sensitivity: 'base' })
    return comparison * direction
  })
})

/** 正式数据分页由查询配置决定；样例预览在行数较多时展示分页，便于体验表格交互。 */
const showPagination = computed(() => {
  return paginationEnabled.value || (props.sampleMode && activeTableRows.value.length > pageSize.value)
})

/** 当前页数据 */
const pagedTableRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedTableRows.value.slice(start, end)
})

/** 导出 CSV */
function handleExportCsv(): void {
  if (!activeTableData.value || activeTableData.value.rows.length === 0) {
    return
  }
  const columns = activeTableData.value.columns
  const rows = activeTableData.value.rows
  const title = props.component.title || 'table'
  const timestamp = new Date().toISOString().slice(0, 10)

  // BOM + CSV 内容（确保 Excel 正确识别 UTF-8）
  const bom = '\uFEFF'
  const headerLine = columns.map(escapeCsvField).join(',')
  const dataLines = rows.map((row) => row.map(escapeCsvField).join(','))
  const csvContent = bom + [headerLine, ...dataLines].join('\n')

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title}_${timestamp}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** CSV 字段转义：包含逗号、双引号或换行时用双引号包裹 */
function escapeCsvField(field: string): string {
  if (field == null) {
    return ''
  }
  const str = String(field)
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return '"' + str.replace(/"/g, '""') + '"'
  }
  return str
}
</script>

<style scoped>
.data-table-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: var(--space-md) var(--space-lg);
  min-height: 48px;
  gap: var(--space-sm);
  border-bottom: 1px solid var(--db-border);
}

.table-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.table-header-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.table-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-time-filter {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.table-wrapper {
  flex: 1;
  min-height: 180px;
  overflow: auto;
  padding: 0 var(--space-lg) var(--space-lg);
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-xs) var(--space-lg) var(--space-sm);
  flex-shrink: 0;
  border-top: 1px solid var(--db-border);
}

.widget-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) var(--space-lg);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  overflow-x: auto;
}

.widget-tab {
  padding: var(--space-xs) var(--space-sm);
  font-size: 12px;
  color: var(--db-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  border-radius: var(--radius-sm);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.widget-tab:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.widget-tab.active {
  color: var(--db-accent);
  background: var(--db-accent-light);
  font-weight: 600;
}

.table-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: var(--space-sm);
  font-size: 13px;
  color: var(--db-text-muted);
}

.table-placeholder::before {
  content: '📋';
  font-size: 32px;
  opacity: 0.6;
}

/* Element Plus 表格样式覆盖 */
.table-wrapper :deep(.el-table) {
  min-height: 160px;
  font-size: 13px;
  --el-table-bg-color: var(--db-card);
  --el-table-tr-bg-color: var(--db-card);
  --el-table-border-color: var(--db-border);
  --el-table-header-bg-color: var(--db-card);
  --el-table-row-hover-bg-color: var(--db-hover);
  --el-fill-color-lighter: var(--db-hover);
  --el-text-color-regular: var(--db-text);
  --el-text-color-secondary: var(--db-text-secondary);
}

.table-wrapper :deep(.el-table th.el-table__cell) {
  background: var(--db-card);
  color: var(--db-text-secondary);
  font-weight: 500;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  padding: 10px 16px;
  border-bottom: 1px solid var(--db-border);
}

.table-wrapper :deep(.el-table td.el-table__cell) {
  padding: 10px 16px;
  color: var(--db-text);
  font-variant-numeric: tabular-nums;
  border-bottom: 1px solid var(--db-border);
}

.table-wrapper :deep(.el-table--small .el-table__cell) {
  padding: 8px 12px;
}

/* 只隐藏 Element Plus 用来画外框的装饰元素。
   注意：不要在这里带上 `.el-table--border .el-table__cell` —— 那是所有 td/th，
   一旦 display:none 整张表格会塌成 0 高度、只剩空壳（2026-09-18 修复）。 */
.table-wrapper :deep(.el-table--border::after),
.table-wrapper :deep(.el-table--border::before),
.table-wrapper :deep(.el-table__border-left-patch) {
  display: none;
}

.table-wrapper :deep(.el-table__body tr:last-child td.el-table__cell) {
  border-bottom: none;
}

.table-wrapper :deep(.el-table__body tr.current-row > td.el-table__cell) {
  background: var(--db-accent-light);
}

@media (max-width: 767px) {
  .table-header {
    flex-direction: column;
    align-items: flex-start;
    padding: var(--space-sm) var(--space-md);
  }

  .table-header-right {
    width: 100%;
  }

  .table-time-filter {
    width: 100%;
  }

  .table-time-filter :deep(.el-date-editor) {
    width: 100% !important;
  }

  .widget-tabs {
    padding: var(--space-xs) var(--space-md);
  }

  .table-wrapper {
    padding: 0 var(--space-md) var(--space-md);
  }
}
</style>
