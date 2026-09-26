<template>
  <el-dialog
    v-model="ui.preview.visible"
    class="dataset-data-dialog"
    title="查看数据 · Python 最终结果"
    width="1020px"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div class="dd-body">
      <section class="dd-block">
        <div class="dd-head"><span class="dd-title">展示字段</span><span class="dd-hint">来自查询配置 · 只读</span></div>
        <div v-if="displayFields.length" class="dd-table-wrap">
          <table class="dd-table dd-display-table" data-testid="python-display-fields-table">
            <thead><tr><th>字段类型</th><th>字段名</th><th>展示名</th></tr></thead>
            <tbody><tr v-for="field in displayFields" :key="field.field"><td>{{ field.role === 'measure' ? '指标' : '维度' }}</td><td><code class="dd-field-name">{{ field.field }}</code></td><td>{{ field.title || field.field }}</td></tr></tbody>
          </table>
        </div>
        <div v-else class="dd-display-empty">尚未配置展示字段，请先在查询配置中选择字段。</div>
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
        <el-tabs v-model="resultView" data-testid="python-result-tabs">
          <el-tab-pane label="查询结果" name="data">
            <div class="dd-head"><span class="dd-title">Python 原始结果</span><span class="dd-hint">{{ resultHint }}</span></div>
            <div class="dd-result-body">
              <el-table v-if="visibleColumns.length" :data="pagedRows" border size="small" height="320" @sort-change="onSortChange">
                <el-table-column v-for="column in visibleColumns" :key="column.name" :prop="column.name" :label="fieldTitle(column.name)" min-width="120" show-overflow-tooltip :sortable="isSortable(column.name) ? 'custom' : false" />
              </el-table>
              <el-empty v-else-if="loading" description="查询中…" />
              <el-empty v-else :description="error || '点击「查询」获取数据'" />
            </div>
            <div v-if="paginationEnabled && visibleColumns.length" class="dd-pagination">
              <span>第 {{ page }} 页 · 共 {{ filteredRows.length }} 条</span>
              <el-button size="small" :disabled="page <= 1" @click="page -= 1">上一页</el-button>
              <el-button size="small" :disabled="page * pageSize >= filteredRows.length" @click="page += 1">下一页</el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="组件预览" name="component">
            <div class="dd-head"><span class="dd-title">{{ component?.title || '组件' }}预览</span><span class="dd-hint">仅预览 Python 当前输出，不影响脚本与配置</span></div>
            <ComponentRenderPreview
              v-if="resultView === 'component'"
              :component="component ?? null"
              :rows="sortedRows"
              :columns="componentPreviewColumns"
              :field-roles="componentPreviewFieldRoles"
              :loading="loading"
              :error="error"
              :has-queried="Boolean(previewState.payload)"
            />
          </el-tab-pane>
        </el-tabs>
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
import type { InsightComponent } from '@/types'
import ComponentRenderPreview from '../ComponentRenderPreview.vue'

const props = defineProps<{ component?: InsightComponent | null }>()
const emit = defineEmits<{
  (event: 'resultset', payload: {
    componentId: string
    status: 'ready'
    source: 'script'
    rows: Record<string, unknown>[]
    fieldLabels?: Record<string, string>
    error: ''
  }): void
}>()
const { state, previewState, loadResultPreview } = useInsight()
const ui = state.ui
const loading = computed(() => previewState.loading)
const error = computed(() => previewState.error)
const config = computed(() => state.finalResultQueryConfig)
const displayFields = computed(() => config.value?.displayFields ?? [])
const filterFields = computed(() => config.value?.filterFields ?? [])
const paginationEnabled = computed(() => config.value?.paginationPolicy.enabled === true)
const pageSize = computed(() => config.value?.paginationPolicy.defaultPageSize || 100)
const page = ref(1)
const conditionRows = ref<ReturnType<typeof createPythonResultFilterRows>>([])
const appliedConditions = ref<FilterCondition[]>([])
const sortState = ref<QuerySortSpec | null>(null)
const resultView = ref<'data' | 'component'>('data')

const visibleColumns = computed(() => {
  const columns = previewState.payload?.dataColumns ?? []
  if (!displayFields.value.length) return columns
  const allowed = new Set(displayFields.value.map((field) => field.field))
  return columns.filter((column) => allowed.has(column.name))
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
const pagedRows = computed(() => paginationEnabled.value ? sortedRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value) : sortedRows.value)
const componentPreviewColumns = computed(() => visibleColumns.value.map((column) => ({
  ...column,
  title: fieldTitle(column.name),
  role: displayFields.value.find((field) => field.field === column.name)?.role,
})))
const componentPreviewFieldRoles = computed(() => Object.fromEntries(displayFields.value.map((field) => [field.field, field.role])))
const queryHint = computed(() => conditionRows.value.length ? '筛选条件来自查询配置；关闭条件后点击查询可查看全部结果' : '未配置筛选字段，本次将展示全部 Python 输出')
const resultHint = computed(() => previewState.payload ? `${filteredRows.value.length} / ${(previewState.payload.dataRows ?? []).length} 行` : '请点击查询')

function fieldTitle(field: string): string {
  return displayFields.value.find((item) => item.field === field)?.title
    || previewState.payload?.dataColumns.find((column) => column.name === field)?.title
    || field
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
  if (!props.component || !previewState.payload) return
  const fieldLabels = {
    ...(state.resultSet.fieldLabels ?? {}),
    ...Object.fromEntries(displayFields.value.map((field) => [field.field, field.title || field.field])),
  }
  emit('resultset', {
    componentId: props.component.id,
    status: 'ready',
    source: 'script',
    rows: componentRows.value,
    fieldLabels,
    error: '',
  })
}

function initialize(): void {
  resultView.value = 'data'
  page.value = 1
  sortState.value = null
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
.dd-result-body { min-height: 160px; }
.dd-pagination { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: var(--db-text-muted); }
</style>
