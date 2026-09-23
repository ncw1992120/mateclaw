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
            <thead><tr><th>字段</th><th>操作符</th><th>本次查询值</th><th>启用</th></tr></thead>
            <tbody>
              <tr v-for="row in conditionRows" :key="row.field" data-testid="python-query-filter-row">
                <td>{{ row.title }} <code>{{ row.field }}</code></td>
                <td><el-select v-model="row.op" size="small"><el-option v-for="op in operatorOptions(row.operators)" :key="op.value" :label="op.label" :value="op.value" /></el-select></td>
                <td><el-input v-model="row.value" size="small" :disabled="!row.enabled || !needsValue(row.op)" placeholder="填写本次查询值" /></td>
                <td><el-switch v-model="row.enabled" /></td>
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
        <div class="dd-head"><span class="dd-title">结果</span><span class="dd-hint">{{ resultHint }}</span></div>
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
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInsight } from './useInsight'
import type { FinalResultFilterField, QuerySortSpec } from '@/types'
import { GENERIC_OPERATORS, completeConditions, needsValue, type FilterCondition, type OperatorOption } from '@/utils/filter-conditions'
import { applyResultFilters } from '@/utils/result-preview-filter'

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
const conditionRows = ref<Array<FilterCondition & Pick<FinalResultFilterField, 'title' | 'field' | 'operators'>>>([])
const appliedConditions = ref<FilterCondition[]>([])
const sortState = ref<QuerySortSpec | null>(null)

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
const pagedRows = computed(() => paginationEnabled.value ? sortedRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value) : sortedRows.value)
const queryHint = computed(() => conditionRows.value.length ? '筛选条件来自查询配置；关闭条件后点击查询可查看全部结果' : '未配置筛选字段，本次将展示全部 Python 输出')
const resultHint = computed(() => previewState.payload ? `${filteredRows.value.length} / ${(previewState.payload.dataRows ?? []).length} 行` : '请点击查询')

function fieldTitle(field: string): string {
  return displayFields.value.find((item) => item.field === field)?.title || field
}

function operatorOptions(operators: FinalResultFilterField['operators']): OperatorOption[] {
  return GENERIC_OPERATORS.filter((option) => operators.includes(option.value as FinalResultFilterField['operators'][number]))
}

function isSortable(field: string): boolean {
  return config.value?.sortPolicy.enabled === true && config.value.sortPolicy.allowedFields.includes(field)
}

function onSortChange(detail: { prop?: string; order?: 'ascending' | 'descending' | null }): void {
  sortState.value = detail.prop && detail.order ? { field: detail.prop, direction: detail.order === 'ascending' ? 'asc' : 'desc' } : null
  page.value = 1
}

function query(): void {
  appliedConditions.value = completeConditions(conditionRows.value.map(({ field, op, value }) => ({ field, op, value })))
  page.value = 1
}

function initialize(): void {
  page.value = 1
  sortState.value = null
  appliedConditions.value = []
  conditionRows.value = filterFields.value.map((field) => ({ field: field.field, title: field.title, operators: field.operators, op: field.operators[0] ?? 'eq', value: '', enabled: false }))
  void loadResultPreview()
}

watch(() => [ui.preview.visible, ui.preview.kind], ([visible, kind]) => {
  if (visible && kind === 'result') initialize()
})
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
.dd-query-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dd-result-body { min-height: 160px; }
.dd-pagination { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: var(--db-text-muted); }
</style>
