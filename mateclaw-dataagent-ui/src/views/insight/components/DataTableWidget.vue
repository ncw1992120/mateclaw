<template>
  <div class="data-table-widget">
    <div class="table-header">
      <div class="table-header-left">
        <div v-if="showTitle" class="table-title">{{ component.title }}</div>
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
          <el-button text size="small" :icon="Download" @click="handleExportCsv" />
        </el-tooltip>
      </div>
    </div>
    <!-- Tab 栏（多 Tab 模式） -->
    <div v-if="hasTabs" class="widget-tabs">
      <div
        v-for="tab in tabList"
        :key="tab.id"
        class="widget-tab"
        :class="{ active: activeTabId === tab.id }"
        @click="activeTabId = tab.id"
      >
        {{ tab.title }}
      </div>
    </div>
    <div class="table-wrapper">
      <el-table
        v-if="activeTableData && activeTableData.rows.length > 0"
        :data="pagedTableRows"
        border
        size="small"
        height="100%"
        style="width: 100%"
      >
        <el-table-column
          v-for="(col, idx) in activeTableData.columns"
          :key="idx"
          :prop="`col_${idx}`"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <div v-else class="table-placeholder">
        {{ activeTabError || t('insight.tableNoData') }}
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
        layout="total, sizes, prev, pager, next"
        size="small"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download } from '@element-plus/icons-vue'
import type { InsightComponent, InsightComponentData, TimeRangeValue, ComponentTab } from '@/types'

defineOptions({
  name: 'DataTableWidget',
})

const { t } = useI18n()

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否显示标题 */
  showTitle?: boolean
}>()

const emit = defineEmits<{
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
}>()

/** 分页相关常量 */
const PAGE_SIZE_DEFAULT = 20
const PAGE_SIZE_OPTIONS = [20, 50, 100, 200]

const tableData = computed(() => props.componentData?.table)
const showTimeFilter = computed(() => props.component.enableTimeFilter)

/** 是否有多 Tab 模式（基于组件配置判断，而非后端返回数据） */
const hasTabs = computed(() => {
  return !!(props.component.tabs && props.component.tabs.length > 0)
})

/** Tab 列表（从组件配置构建） */
const tabList = computed<ComponentTab[]>(() => props.component.tabs ?? [])

/** 当前激活的 Tab ID */
const activeTabId = ref('')

// 初始化 / 切换组件时自动选中第一个 Tab
watch(hasTabs, (val) => {
  if (val && !activeTabId.value) {
    activeTabId.value = tabList.value[0]?.id ?? ''
  }
  if (!val) {
    activeTabId.value = ''
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
const pageSize = ref(PAGE_SIZE_DEFAULT)
const pageSizes = PAGE_SIZE_OPTIONS

/** 数据变化时重置页码 */
watch(activeTableRows, () => {
  currentPage.value = 1
})

/** 是否显示分页（数据量超过一页时显示） */
const showPagination = computed(() => {
  return activeTableRows.value.length > PAGE_SIZE_DEFAULT
})

/** 当前页数据 */
const pagedTableRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return activeTableRows.value.slice(start, end)
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
  overflow: hidden;
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
  font-size: 13px;
  --el-table-border-color: var(--db-border);
  --el-table-header-bg-color: var(--db-card);
  --el-table-row-hover-bg-color: var(--db-hover);
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

.table-wrapper :deep(.el-table--border::after),
.table-wrapper :deep(.el-table--border::before),
.table-wrapper :deep(.el-table__border-left-patch),
.table-wrapper :deep(.el-table--border .el-table__cell) {
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
