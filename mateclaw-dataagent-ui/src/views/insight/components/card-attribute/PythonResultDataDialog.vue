<template>
  <el-dialog
    v-model="ui.preview.visible"
    class="dataset-data-dialog"
    title="查看数据 · Python 最终结果"
    width="1020px"
    top="5vh"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="dd-body">
      <section class="dd-block">
        <div class="dd-head"><span class="dd-title">结果字段</span><span class="dd-hint">全部 Python 输出 · 可编辑展示名</span></div>
        <div v-if="displayRows.length" class="dd-table-wrap">
          <table class="dd-table dd-display-table" data-testid="python-display-fields-table">
            <thead><tr><th>字段类型</th><th>字段名</th><th>展示名</th></tr></thead>
            <tbody><tr v-for="field in displayRows" :key="field.name" class="result-column"><td>{{ field.role === 'measure' ? '指标' : '维度' }}</td><td><code class="dd-field-name">{{ field.name }}</code></td><td><el-input :model-value="field.title" size="small" :data-testid="`python-display-name-${field.name}`" @update:model-value="(value: string) => updateDisplayName(field.name, value)" /></td></tr></tbody>
          </table>
        </div>
        <div v-else class="dd-display-empty">查询后显示 Python 输出字段。</div>
      </section>

      <section class="dd-block">
        <div class="dd-head"><span class="dd-title">筛选条件</span><span class="dd-hint">填写本次查看的实际参数</span></div>
        <div v-if="conditionRows.length" class="dd-table-wrap dd-conditions">
          <table class="dd-table dd-filter-table" data-testid="python-query-filter-table">
            <thead><tr><th>筛选器</th><th>字段</th><th>操作符</th><th>本次查询值</th><th>启用</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in conditionRows" :key="`${row.filterComponentId ?? row.field}-${row.field}-${row.timeBoundary ?? 'value'}-${index}`" data-testid="python-query-filter-row" :data-time-boundary="row.timeBoundary">
                <td>
                  {{ row.filterTitle }}
                  <span v-if="row.bindingError" class="dd-binding-error" role="alert">绑定的页面筛选器已失效，请先重新绑定</span>
                  <span v-else-if="row.timeBoundary" class="dd-hint">{{ row.timeBoundary === 'start' ? '包含开始时间' : '不包含结束时间' }}</span>
                </td>
                <td>{{ row.title }} <code>{{ row.field }}</code></td>
                <td data-testid="python-query-filter-operator">{{ row.op }}</td>
                <td>
                  <el-date-picker
                    v-if="row.timeBoundary"
                    v-model="row.value"
                    type="date"
                    value-format="YYYY-MM-DD"
                    size="small"
                    data-testid="python-query-date-input"
                    :disabled="row.bindingError || !row.enabled"
                    :placeholder="row.timeBoundary === 'start' ? '选择开始时间' : '选择结束时间（不包含）'"
                  />
                  <el-input v-else v-model="row.value" size="small" :disabled="row.bindingError || !row.enabled || !needsValue(row.op)" placeholder="填写本次查询值" />
                </td>
                <td><el-switch v-model="row.enabled" :disabled="row.bindingError" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dd-empty">暂无筛选字段；本次将展示全部 Python 输出。</div>
      </section>

      <div class="dd-query-row">
        <span class="dd-hint">{{ queryHint }}</span>
        <el-button type="primary" :loading="loading" data-testid="python-run-query" @click="query">查询</el-button>
      </div>

      <section class="dd-block dd-result">
        <div class="dd-head">
          <div class="dd-result-tabs" role="tablist" aria-label="结果视图">
            <button
              type="button"
              class="dd-tab"
              :class="{ active: resultTab === 'raw' }"
              role="tab"
              :aria-selected="resultTab === 'raw'"
              data-testid="python-result-tab-raw"
              @click="resultTab = 'raw'"
            >Python 原始结果</button>
            <button
              v-if="component"
              type="button"
              class="dd-tab"
              :class="{ active: resultTab === 'render' }"
              role="tab"
              :aria-selected="resultTab === 'render'"
              data-testid="python-component-render"
              :disabled="!canRenderComponent"
              @click="switchToRenderTab"
            >应用</button>
          </div>
          <div class="dd-result-actions">
            <span class="dd-hint">{{ resultHint }}</span>
          </div>
        </div>
        <template v-if="!component || resultTab === 'raw'">
        <div class="dd-result-body">
          <el-table v-if="visibleColumns.length" :data="pagedRows" border size="small" height="320" @sort-change="onSortChange">
            <el-table-column v-for="column in visibleColumns" :key="column.name" :prop="column.name" :label="fieldTitle(column.name)" min-width="120" show-overflow-tooltip :sortable="isSortable(column.name) ? 'custom' : false" />
          </el-table>
          <el-empty v-else-if="loading" description="查询中…" />
          <el-empty v-else :description="error || '点击「查询」获取数据'" />
        </div>
        <div v-if="paginationEnabled && visibleColumns.length" class="dd-pagination" data-testid="python-result-pagination">
          <el-pagination
            :current-page="page"
            :page-size="pageSize"
            :total="filteredRows.length"
            layout="total, prev, pager, next, jumper"
            size="small"
            @current-change="page = $event"
          />
        </div>
        </template>
        <div v-else class="dd-result-body dd-render-pane" data-testid="python-component-preview-section">
          <ComponentDataPreview v-if="componentRenderData" :component="component" :component-data="componentRenderData" />
          <el-empty v-else description="暂无可渲染的组件数据" />
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInsight } from './useInsight'
import type { QuerySortSpec } from '@/types'
import { needsValue, type FilterCondition } from '@/utils/filter-conditions'
import { applyResultFilters } from '@/utils/result-preview-filter'
import { createPythonResultFilterRows, enabledPythonResultConditions } from '@/utils/python-result-filter-conditions'
import type { InsightComponent, InsightComponentData } from '@/types'
import { componentPreviewData } from '@/utils/component-preview-data'
import ComponentDataPreview from '../ComponentDataPreview.vue'

const props = defineProps<{ component?: InsightComponent | null }>()
const emit = defineEmits<{ (event: 'render', data: InsightComponentData): void }>()
const { state, previewState, loadResultPreview } = useInsight()
const ui = state.ui
const loading = computed(() => previewState.loading)
const error = computed(() => previewState.error)
const config = computed(() => state.finalResultQueryConfig)
const displayFields = computed(() => config.value?.displayFields ?? [])
const displayRows = computed(() => (previewState.payload?.dataColumns ?? []).map((column) => {
  const configured = displayFields.value.find((field) => field.field === column.name)
  const sampleValue = previewState.payload?.dataRows?.[0]?.[column.name] ?? column.sampleValue
  const role = column.type === 'number' || typeof sampleValue === 'number'
    ? 'measure'
    : configured?.role ?? 'dimension'
  return { name: column.name, title: configured?.title || column.title || column.name, role }
}))
const filterFields = computed(() => config.value?.filterFields ?? [])
const paginationEnabled = computed(() => config.value?.paginationPolicy.enabled === true)
const pageSize = computed(() => config.value?.paginationPolicy.defaultPageSize || 100)
const page = ref(1)
const conditionRows = ref<ReturnType<typeof createPythonResultFilterRows>>([])
const appliedConditions = ref<FilterCondition[]>([])
const sortState = ref<QuerySortSpec | null>(null)
const hasQueried = ref(false)
/** 结果区页签：Python 原始结果 / 组件渲染 */
const resultTab = ref<'raw' | 'render'>('raw')

const visibleColumns = computed(() => {
  return previewState.payload?.dataColumns ?? []
})
const filteredRows = computed(() => applyResultFilters(previewState.payload?.dataRows ?? [], appliedConditions.value))
const sortedRows = computed(() => {
  const rows = [...filteredRows.value]
  if (!sortState.value) return rows
  const { field, direction } = sortState.value
  return rows.sort((a, b) => {
    const av = a[field]
    const bv = b[field]
    if (av === bv) return 0
    if (av === null || av === undefined) return -1
    if (bv === null || bv === undefined) return 1
    const result = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv))
    return direction === 'asc' ? result : -result
  })
})
const componentRows = computed(() => {
  const fields = visibleColumns.value.map((column) => column.name)
  return sortedRows.value.map((row) => Object.fromEntries(fields.map((field) => [field, row[field]])))
})
const componentRenderData = computed(() => {
  if (!props.component) return undefined
  return {
    ...componentPreviewData(props.component, componentRows.value, displayRows.value.map(({ name, title }) => ({ name, title }))),
    pythonResultPreview: true,
  }
})
const pagedRows = computed(() => paginationEnabled.value ? sortedRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value) : sortedRows.value)
const queryHint = computed(() => conditionRows.value.length ? '筛选条件来自查询配置；关闭条件后点击查询可查看全部结果' : '未配置筛选字段，本次将展示全部 Python 输出')
const resultHint = computed(() => previewState.payload ? `${filteredRows.value.length} / ${(previewState.payload.dataRows ?? []).length} 行` : '请点击查询')

function fieldTitle(field: string): string {
  return displayFields.value.find((item) => item.field === field)?.title
    || previewState.payload?.dataColumns.find((column) => column.name === field)?.title
    || field
}

function updateDisplayName(field: string, title: string): void {
  const fields = [...displayFields.value]
  const index = fields.findIndex((item) => item.field === field)
  const column = previewState.payload?.dataColumns.find((item) => item.name === field)
  const role = fields[index]?.role ?? (typeof column?.sampleValue === 'number' ? 'measure' : 'dimension')
  const next = { field, title, role }
  if (index >= 0) fields[index] = { ...fields[index], title }
  else fields.push(next)
  state.finalResultQueryConfig = { ...config.value!, displayFields: fields }
}

function isSortable(field: string): boolean {
  return config.value?.sortPolicy.enabled === true && config.value.sortPolicy.allowedFields.includes(field)
}

function onSortChange(detail: { prop?: string; order?: 'ascending' | 'descending' | null }): void {
  sortState.value = detail.prop && detail.order ? { field: detail.prop, direction: detail.order === 'ascending' ? 'asc' : 'desc' } : null
  page.value = 1
}

function query(): void {
  appliedConditions.value = enabledPythonResultConditions(conditionRows.value)
  page.value = 1
  hasQueried.value = Boolean(previewState.payload)
  resultTab.value = 'raw'
}

/** 「组件渲染」页签可用条件：有组件且已查询 */
const canRenderComponent = computed(() => Boolean(props.component) && hasQueried.value)

function renderComponent(): void {
  if (!props.component || !hasQueried.value || !componentRenderData.value) return
  emit('render', componentRenderData.value)
}

/** 切到「组件渲染」页签：同步画布 + 页签内预览 */
function switchToRenderTab(): void {
  if (!canRenderComponent.value) return
  renderComponent()
  resultTab.value = 'render'
}

function initialize(): void {
  page.value = 1
  sortState.value = null
  hasQueried.value = false
  resultTab.value = 'raw'
  appliedConditions.value = []
  conditionRows.value = createPythonResultFilterRows(filterFields.value, state.filterCatalog)
  void loadResultPreview()
}

watch(() => [ui.preview.visible, ui.preview.kind], ([visible, kind]) => {
  if (visible && kind === 'result') initialize()
}, { immediate: true })
</script>

<style scoped>
.dd-body { display: flex; flex-direction: column; gap: 14px; max-height: 80vh; overflow: auto; }
.dd-block { border: 1px solid var(--db-border); border-radius: var(--radius-md); padding: 12px; background: #fff; }
.dd-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.dd-result-tabs { display: flex; align-items: center; gap: 18px; }
.dd-tab { padding: 4px 2px; border: 0; border-bottom: 2px solid transparent; background: transparent; font: inherit; font-size: 13px; color: var(--db-text-muted); cursor: pointer; }
.dd-tab.active { color: var(--db-text); font-weight: 600; border-bottom-color: var(--el-color-primary, var(--db-text)); }
.dd-tab:disabled { cursor: not-allowed; opacity: 0.5; }
.dd-render-pane { display: flex; flex-direction: column; }
.dd-title { font-weight: 600; font-size: 13px; color: var(--db-text); }
.dd-hint { font-size: 12px; color: var(--db-text-muted); }
.dd-table-wrap { overflow: auto; }
.dd-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.dd-table th, .dd-table td { padding: 8px 10px; border-bottom: 1px solid var(--db-border); text-align: left; }
.dd-table th { color: var(--db-text-muted); font-weight: 600; background: var(--db-muted); }
.dd-field-name, .dd-bound-field code { font-family: var(--font-mono, monospace); }
.dd-display-empty, .dd-empty { padding: 12px 0; color: var(--db-text-muted); font-size: 12px; text-align: center; }
.dd-binding-error { display: block; color: var(--el-color-danger); font-size: 12px; margin-top: 4px; }
.dd-query-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dd-result-actions { display: flex; align-items: center; gap: 10px; }
.dd-result-body { min-height: 160px; }
.dd-pagination { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: var(--db-text-muted); }
</style>
