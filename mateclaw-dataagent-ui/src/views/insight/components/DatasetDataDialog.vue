<template>
  <el-dialog
    v-model="ui.dataDialog.visible"
    class="dataset-data-dialog"
    :title="`查看数据 · ${dataset.alias}`"
    width="1020px"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div class="dd-body">
      <!-- ① 展示字段：直接取查询配置，只读 -->
      <section class="dd-block">
        <div class="dd-head">
          <span class="dd-title">展示字段</span>
          <span class="dd-hint">来自查询配置 · 只读</span>
        </div>
        <div v-if="displayFields.length" class="dd-table-wrap">
          <table class="dd-table dd-display-table" data-testid="display-fields-table">
            <thead><tr><th scope="col">字段类型</th><th scope="col">字段名</th><th scope="col">展示名</th></tr></thead>
            <tbody>
              <tr v-for="field in displayFields" :key="field.field" data-testid="display-field-row">
                <td>{{ field.role === 'measure' ? '指标' : '维度' }}</td>
                <td><code class="dd-field-name">{{ field.field }}</code></td>
                <td>{{ field.title || field.field }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dd-display-empty" data-testid="display-fields-empty">尚未配置展示字段，请先在查询配置中选择字段。</div>
      </section>

      <section v-if="isSql" class="dd-block">
        <div class="dd-head">
          <span class="dd-title">SQL</span>
          <span class="dd-hint">数据集定义 · 只读</span>
        </div>
        <pre class="dd-sql-readonly" data-testid="dataset-sql-readonly"><code>{{ sql }}</code></pre>
      </section>

      <section v-if="fixedFilters.length" class="dd-block" data-testid="fixed-filter-section">
        <div class="dd-head">
          <span class="dd-title">固定筛选条件</span>
          <span class="dd-hint">来自查询配置 · 始终生效 · 只读</span>
        </div>
        <div class="dd-table-wrap">
          <table class="dd-table dd-filter-table" data-testid="fixed-filter-table">
            <thead><tr><th scope="col">字段</th><th scope="col">操作符</th><th scope="col">固定值</th></tr></thead>
            <tbody>
              <tr v-for="(filter, index) in fixedFilters" :key="`${filter.field}-${index}`" data-testid="fixed-filter-row">
                <td>{{ fieldTitle(filter.field) }} <code>{{ filter.field }}</code></td>
                <td>{{ operatorLabel(filter.operator) }}</td>
                <td>{{ filter.value === undefined ? '—' : Array.isArray(filter.value) ? filter.value.join('，') : filter.value }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ② 参数：从定义里自动提取的占位符（SQL / 接口） -->
      <section v-if="parameters.length" class="dd-block">
        <div class="dd-head">
          <span class="dd-title">参数</span>
          <span class="dd-hint">从定义自动提取 · {{ parameters.length }} 项</span>
        </div>
        <div class="dd-param-grid">
          <div v-for="param in parameters" :key="param.name" class="dd-param">
            <label class="dd-param-label">
              <code>:{{ param.name }}</code>
              <span class="dd-param-type">{{ param.type }}</span>
              <span class="dd-param-req">必填</span>
            </label>
            <el-date-picker
              v-if="param.type === 'date'"
              v-model="paramValues[param.name]"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              class="dd-param-control"
            />
            <el-date-picker
              v-else-if="param.type === 'date_range'"
              v-model="paramValues[param.name]"
              type="daterange"
              size="small"
              value-format="YYYY-MM-DD"
              class="dd-param-control"
            />
            <el-input-number
              v-else-if="param.type === 'number'"
              v-model="paramValues[param.name]"
              size="small"
              class="dd-param-control"
            />
            <el-input
              v-else
              v-model="paramValues[param.name]"
              size="small"
              class="dd-param-control"
              :placeholder="param.type === 'string[]' ? '多个值用逗号分隔' : '值'"
            />
          </div>
        </div>
      </section>

      <!-- ④ 页面筛选器绑定：仅填写本次查询值；固定筛选条件独立、始终生效 -->
      <section v-if="filterSupported" class="dd-block">
        <div class="dd-head">
          <span class="dd-title">筛选器绑定</span>
          <div class="dd-filter-head-actions">
            <span v-if="hasTimeFilterRows" class="dd-hint">时间范围左闭右开：包含开始时间，不包含结束时间</span>
            <label v-if="hasTimeFilterRows" class="dd-time-granularity">
              <span>时间粒度</span>
              <el-select v-model="timeGranularity" size="small" data-testid="time-granularity" aria-label="本次查询时间粒度">
                <el-option v-for="option in TIME_GRANULARITY_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </label>
          </div>
        </div>
        <div v-if="queryFilterRows.length" class="dd-table-wrap dd-conditions">
          <table class="dd-table dd-filter-table" data-testid="query-filter-table">
            <thead><tr><th scope="col">筛选器</th><th scope="col">映射字段</th><th scope="col">操作符</th><th scope="col">本次查询值</th><th scope="col">启用</th></tr></thead>
            <tbody>
              <tr
                v-for="(row, index) in queryFilterRows"
                :key="`${row.filterComponentId}-${row.field}-${row.parameterName}-${index}`"
                data-testid="query-filter-row"
                :data-time-boundary="row.timeBoundary"
              >
                <td class="dd-bound-filter">{{ row.timeBoundary ? `${row.filterTitle} · ${row.timeBoundary === 'start' ? '开始时间' : '结束时间'}` : row.filterTitle }}</td>
                <td class="dd-bound-field">{{ row.fieldTitle }} <code>{{ row.field }}</code></td>
                <td class="dd-fixed-operator" data-testid="query-filter-operator">{{ operatorLabel(row.operator) }}</td>
                <td>
                  <el-date-picker
                    v-if="row.timeBoundary"
                    v-model="row.value"
                    type="date"
                    value-format="YYYY-MM-DD"
                    class="dd-value"
                    size="small"
                    :disabled="!row.enabled"
                    :placeholder="row.timeBoundary === 'start' ? '选择开始时间' : '选择结束时间（不包含）'"
                    :aria-label="`${row.filterTitle}${row.timeBoundary === 'start' ? '开始时间' : '结束时间'}本次查询值`"
                  />
                  <el-input
                    v-else
                    v-model="row.value"
                    class="dd-value"
                    size="small"
                    :disabled="!row.enabled || !operatorNeedsValue(row.operator)"
                    :placeholder="operatorNeedsValue(row.operator) ? '填写本次查询值' : '无需取值'"
                    :aria-label="`${row.filterTitle}本次查询值`"
                  />
                </td>
                <td>
                  <el-switch
                    :model-value="row.enabled"
                    data-testid="query-filter-enabled"
                    :aria-label="`${row.filterTitle}${row.timeBoundary ? (row.timeBoundary === 'start' ? '开始时间' : '结束时间') : ''}筛选条件启用`"
                    @change="setQueryFilterEnabled(row, $event)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dd-empty" data-testid="query-filter-empty" role="status">
          {{ fixedFilters.length ? '未绑定页面筛选器；固定筛选条件仍会生效。' : '未绑定筛选器，本次查询将不附加筛选条件。' }}
        </div>
      </section>

      <!-- ④ 查询：紧跟在筛选条件下方 —— 改完条件顺手点，视线不用跳到弹窗底部 -->
      <div class="dd-query-row">
        <span class="dd-hint">{{ queryHint }}</span>
        <label class="dd-limit">
          <span>限制条数</span>
          <el-input-number
            v-model="queryLimit"
            :min="1"
            :max="100000"
            :step="1000"
            :disabled="paginationEnabled"
            size="small"
            controls-position="right"
            data-testid="query-limit"
            aria-label="限制条数"
            :title="paginationEnabled ? '分页模式下由每页条数控制返回量' : '单次查询最多返回的行数，默认 10000'"
          />
        </label>
        <el-button data-testid="run-query" type="primary" :loading="loading" :disabled="queryDisabled" @click="query()">查询</el-button>
      </div>
      <el-alert
        v-if="componentValidationError"
        data-testid="component-preview-validation"
        type="error"
        :title="componentValidationError"
        :closable="false"
        show-icon
      />

      <!-- ⑤ 结果 -->
      <section class="dd-block dd-result">
        <div class="dd-head">
          <div class="dd-result-tabs" role="tablist" aria-label="结果视图">
            <button
              type="button"
              class="dd-tab"
              :class="{ active: resultTab === 'raw' }"
              role="tab"
              :aria-selected="resultTab === 'raw'"
              data-testid="result-tab-raw"
              @click="resultTab = 'raw'"
            >原始数据</button>
            <button
              v-if="component"
              type="button"
              class="dd-tab"
              :class="{ active: resultTab === 'render' }"
              role="tab"
              :aria-selected="resultTab === 'render'"
              data-testid="component-render"
              :disabled="!canRenderComponent"
              @click="switchToRenderTab"
            >应用</button>
          </div>
          <div class="dd-result-actions">
            <span class="dd-hint">{{ resultHint }}</span>
          </div>
        </div>
        <template v-if="!component || resultTab === 'raw'">
        <div ref="scrollRef" class="dd-result-body" @scroll="onScroll">
          <el-table
            v-if="columns.length"
            :data="rows"
            :default-sort="sortState ? { prop: sortState.field, order: sortState.direction === 'asc' ? 'ascending' : 'descending' } : undefined"
            border
            size="small"
            height="100%"
            @sort-change="onSortChange"
          >
            <el-table-column
              v-for="column in columns"
              :key="column"
              :prop="column"
              :label="fieldTitle(column)"
              min-width="120"
              show-overflow-tooltip
              :sortable="isFieldSortable(column) ? 'custom' : false"
              :sort-orders="['ascending', 'descending', null]"
            />
          </el-table>
          <el-empty v-else-if="loading" description="查询中…" />
          <el-empty v-else :description="error || '点「查询」获取数据'" />
        </div>
        <div v-if="paginationEnabled && columns.length" class="dd-pagination" data-testid="query-pagination">
          <span data-testid="pagination-status">
            {{ totalCount === null ? `第 ${currentPage} 页` : `第 ${currentPage} 页 · 共 ${totalCount} 条` }}
          </span>
          <label class="dd-page-size">
            <span>每页</span>
            <select :value="currentPageSize" aria-label="每页条数" data-testid="page-size" @change="onPageSizeChange">
              <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
            </select>
            <span>条</span>
          </label>
          <label class="dd-page-jump">
            <span>跳至</span>
            <input
              v-model="pageDraft"
              type="number"
              min="1"
              :max="maxPage ?? undefined"
              aria-label="跳转页码"
              data-testid="page-jump-input"
              :disabled="loading"
              @keydown.enter.prevent="jumpToPage"
            />
            <span>页</span>
            <el-button size="small" data-testid="page-jump" :disabled="loading" @click="jumpToPage">跳转</el-button>
          </label>
          <el-button size="small" :disabled="currentPage <= 1 || loading" data-testid="page-previous" @click="changePage(currentPage - 1)">上一页</el-button>
          <el-button size="small" :disabled="!hasMore || loading" data-testid="page-next" @click="changePage(currentPage + 1)">下一页</el-button>
        </div>
        </template>
        <div v-else class="dd-result-body dd-render-pane" data-testid="component-preview-section">
          <ComponentDataPreview v-if="componentRenderData" :component="component" :component-data="componentRenderData" />
          <el-empty v-else description="暂无可渲染的组件数据" />
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { InsightComponent, InsightComponentData, TimeGranularity } from '@/types'
import { useInsight } from './card-attribute/useInsight'
import { previewDatasetDraft } from './card-attribute/useInsightBackend'
import { list as listDatasets, previewInput } from '@/api/dataset'
import { draftRequestForDataset, isPersistedBackendDatasetId } from './card-attribute/useInsight'
import { isPersistedDatasetReferenceAvailable } from '@/utils/dataset-reference'
import type { DatasetConfig } from './card-attribute/useInsight'
import { extractApiParameters, extractSqlParameters, type ExtractedParameter } from '@/utils/parameter-extract'
import { getCachedQuery, getCachedQueryState, setCachedQuery, setCachedQueryState } from './dataset-data-cache'
import type { QueryDisplayField, QueryParameterBinding, QuerySortSpec } from '@/types'
import {
  GENERIC_OPERATORS,
  normalizeOperator,
} from '@/utils/filter-conditions'
import {
  buildExecutionParameters,
  toDatasetFilters,
  type RuntimeFilterRow,
} from '@/utils/runtime-filter-bindings'
import { toFixedDatasetFilters } from '@/utils/fixed-dataset-filters'
import { componentPreviewData } from '@/utils/component-preview-data'
import ComponentDataPreview from './ComponentDataPreview.vue'

const props = defineProps<{ dataset: DatasetConfig; component?: InsightComponent | null }>()
const emit = defineEmits<{ (event: 'render', data: InsightComponentData): void }>()
const { state } = useInsight()
const ui = state.ui

/** 未配置分页时沿用预览安全批次，滚动到底继续追加。 */
const DEFAULT_BATCH_SIZE = 50
const persistedDatasetAvailability = new Map<string, boolean>()

/** 未分页时的「限制条数」：默认值与上限（上限与后端数据集预览上限保持一致） */
const DEFAULT_QUERY_LIMIT = 10000
const MAX_QUERY_LIMIT = 100000
const TIME_GRANULARITY_OPTIONS: Array<{ label: string; value: TimeGranularity }> = [
  { label: '天', value: 'DAY' },
  { label: '周', value: 'WEEK' },
  { label: '月', value: 'MONTH' },
  { label: '季度', value: 'QUARTER' },
  { label: '年', value: 'YEAR' },
]

const loading = ref(false)
const error = ref('')
const componentValidationError = ref('')
/** 结果区页签：原始数据 / 组件渲染 */
const resultTab = ref<'raw' | 'render'>('raw')
const elapsed = ref('')
const columns = ref<string[]>([])
const rows = ref<Record<string, unknown>[]>([])
const hasMore = ref(false)
const totalCount = ref<number | null>(null)
const scrollRef = ref<HTMLElement | null>(null)
/** 表头三态排序（升序 → 降序 → 取消）；变化时页码回 1 */
const sortState = ref<QuerySortSpec | null>(null)
const currentPage = ref(1)
const currentPageSize = ref(DEFAULT_BATCH_SIZE)
const pageDraft = ref('1')
/** 未分页模式的取数上限（限制条数），默认 10000、上限 100000；分页模式下由每页条数接管 */
const queryLimit = ref(DEFAULT_QUERY_LIMIT)
const timeGranularity = ref<TimeGranularity>('DAY')
const stateReady = ref(false)
/** 递增请求序号：排序/翻页快速切换时丢弃旧请求晚返回的响应 */
let requestSequence = 0

const isSql = computed(() => props.dataset.sourceType === 'jdbc')
const isApi = computed(() => props.dataset.sourceType === 'api')
const fixedFilters = computed(() => toFixedDatasetFilters(props.dataset.filters, props.dataset.fields))

/* ── 查询配置只读展示 ── */
const sql = computed(() => props.dataset.jdbc?.sql ?? '')
const displayFields = computed<QueryDisplayField[]>(() => {
  const configured = props.dataset.queryConfig?.displayFields
  if (configured) return configured

  // 首次查看数据时可能还没有保存过查询配置。与 QueryConfigDialog 的默认行为一致，
  // 将当前字段目录视为默认展示字段（维度在前、指标在后）；schema 异步刷新后也会响应更新。
  return [...(props.dataset.fields ?? [])]
    .map((field) => ({
      field: field.name,
      title: field.displayName?.trim() || field.name,
      role: ['measure', 'metric'].includes((field.role ?? '').trim().toLowerCase()) ? 'measure' : 'dimension',
      ...(field.dataType ? { dataType: field.dataType } : {}),
    }))
    .sort((left, right) => Number(left.role === 'measure') - Number(right.role === 'measure'))
})
const componentPreviewColumns = computed(() => {
  const names = columns.value.length ? columns.value : resultDisplayFieldList().map((field) => field.field)
  return names.map((name) => {
    const field = displayFields.value.find((item) => item.field === name)
      ?? props.dataset.queryConfig?.queryableFields?.find((item) => item.name === name)
      ?? props.dataset.fields.find((item) => item.name === name)
    return {
      name,
      title: displayFields.value.find((item) => item.field === name)?.title || field?.displayName || name,
      dataType: field && 'dataType' in field ? String(field.dataType ?? '') : undefined,
      role: field && 'role' in field ? String(field.role ?? '') : undefined,
    }
  })
})
const paginationPolicy = computed(() => props.dataset.queryConfig?.paginationPolicy)
const paginationEnabled = computed(() => paginationPolicy.value?.enabled === true)
const maxPage = computed(() => totalCount.value === null
  ? null
  : Math.max(1, Math.ceil(totalCount.value / currentPageSize.value)))
const sortPolicy = computed(() => props.dataset.queryConfig?.sortPolicy)
const pageSizeOptions = computed(() => {
  const max = Math.max(1, paginationPolicy.value?.maxPageSize || 500)
  const initial = Math.min(max, Math.max(1, paginationPolicy.value?.defaultPageSize || DEFAULT_BATCH_SIZE))
  return [...new Set([initial, 1, 2, 5, 10, 20, 50, 100, 200, 500].filter((size) => size <= max))].sort((a, b) => a - b)
})

/** 「限制条数」收敛：空/非数回退默认值，限制在 1..100000 */
function clampQueryLimit(value: number | null | undefined): number {
  const normalized = Math.trunc(Number(value))
  if (!Number.isFinite(normalized)) return DEFAULT_QUERY_LIMIT
  return Math.min(Math.max(normalized, 1), MAX_QUERY_LIMIT)
}

function isFieldSortable(field: string): boolean {
  return resultDisplayFieldList().some((item) => item.field === field)
    && sortPolicy.value?.enabled === true
    && sortPolicy.value.allowedFields.includes(field)
}

/* ── 参数区：SQL / 接口的占位符（绑进查询内部，与筛选条件不是一回事） ── */
const parameters = computed<ExtractedParameter[]>(() => {
  if (isSql.value) return extractSqlParameters(sql.value)
  if (isApi.value) {
    return extractApiParameters({
      path: props.dataset.api?.path,
      headers: props.dataset.api?.headers,
      params: props.dataset.api?.params,
    })
  }
  return []
})
const paramValues = reactive<Record<string, unknown>>({})

/**
 * 参数值 → request.parameters。只收 SQL / 接口的占位符；
 * 维度筛选走 filters —— 两条下推通道不能混（占位符名不是列名，塞进 filters 会被按列名去找）。
 */
function namedParameters(): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const param of parameters.value) {
    const raw = paramValues[param.name]
    if (raw === undefined || raw === null) continue
    if (Array.isArray(raw) ? raw.length === 0 : String(raw).trim() === '') continue
    out[param.name] = raw
  }
  return out
}

/* ── 筛选条件区 ── */

/** 文件类型上游不做筛选下推（后端只把它们记进 residualFilters），因此不给编辑入口，避免「能配但不生效」 */
const filterSupported = computed(() => props.dataset.sourceType !== 'file')

const queryFilterRows = ref<PreviewQueryFilterRow[]>([])
const hasTimeFilterRows = computed(() => queryFilterRows.value.some((row) => row.timeBoundary !== undefined))

function resultDisplayFieldList(): QueryDisplayField[] {
  const timeFilterFields = new Set(queryFilterRows.value
    .filter((row) => row.timeBoundary !== undefined)
    .map((row) => row.field))
  return displayFields.value.filter((field) =>
    field.role !== 'dimension' || !timeFilterFields.has(field.field),
  )
}

interface PreviewQueryFilterRow extends RuntimeFilterRow {
  filterTitle: string
  fieldTitle: string
  enabled: boolean
  timeBoundary?: 'start' | 'end'
}

function fieldTitle(field: string): string {
  return displayFields.value.find((row) => row.field === field)?.title
    || props.dataset.queryConfig?.queryableFields?.find((row) => row.name === field)?.displayName
    || props.dataset.fields.find((row) => row.name === field)?.displayName
    || field
}

function operatorLabel(operator: QueryParameterBinding['operator']): string {
  const normalized = normalizeOperator(operator)
  return GENERIC_OPERATORS.find((option) => option.value === normalized)?.label ?? operator
}

function operatorNeedsValue(operator: QueryParameterBinding['operator']): boolean {
  return operator !== 'is_null' && operator !== 'is_not_null'
}

function hasFilterValue(value: unknown): boolean {
  if (Array.isArray(value)) return value.some((item) => item !== null && String(item).trim() !== '')
  if (value === null || value === undefined) return false
  return typeof value === 'string' ? value.trim() !== '' : true
}

const hasExecutableFilter = computed(() => queryFilterRows.value.some((row) =>
  row.enabled && (!operatorNeedsValue(row.operator) || hasFilterValue(row.value)),
))
/** JDBC / API 定义中的占位符与页面筛选条件分开处理。 */
const hasNamedQueryParameters = computed(() => Object.keys(namedParameters()).length > 0)
/** 筛选绑定和值均可省略；若 SQL / API 定义了占位符，仍需要输入参数值。 */
const queryDisabled = computed(() => loading.value ||
  (parameters.value.length > 0 && !hasNamedQueryParameters.value),
)

function createQueryFilterRows(): PreviewQueryFilterRow[] {
  return (props.dataset.queryConfig?.parameterBindings ?? []).flatMap((binding) => {
    const parameterName = binding.parameterName || binding.filterComponentId
    const filter = state.filterCatalog.find((item) => item.id === binding.filterComponentId)
    const base: PreviewQueryFilterRow = {
      inputName: props.dataset.alias,
      field: binding.field,
      operator: binding.operator,
      parameterNames: [parameterName],
      parameterName,
      filterComponentId: binding.filterComponentId,
      filterTitle: filter?.title ?? binding.filterComponentId,
      fieldTitle: fieldTitle(binding.field),
      value: '',
      enabled: true,
    }
    if (filter?.type !== 'timeFilter') return [base]

    return [
      { ...base, operator: 'gte', timeBoundary: 'start' },
      // The persisted binding has one parameter name; the exclusive upper bound is
      // transmitted as a field filter only, without inventing a new schema parameter.
      { ...base, operator: 'lt', parameterName: '', parameterNames: [], timeBoundary: 'end' },
    ]
  })
}

const boundTimeGranularityDefaults = computed(() => [...new Set(
  queryFilterRows.value
    .filter((row) => row.timeBoundary)
    .map((row) => (state.filterCatalog.find((filter) => filter.id === row.filterComponentId) as
      (typeof state.filterCatalog[number] & { defaultTimeGranularity?: TimeGranularity }) | undefined)?.defaultTimeGranularity)
    .filter((value): value is TimeGranularity => TIME_GRANULARITY_OPTIONS.some((option) => option.value === value)),
)])
const initialTimeGranularity = computed<TimeGranularity>(() =>
  boundTimeGranularityDefaults.value.length === 1 ? boundTimeGranularityDefaults.value[0] : 'DAY',
)

function setQueryFilterEnabled(row: PreviewQueryFilterRow, enabled: boolean): void {
  row.enabled = enabled
  if (!enabled) row.value = ''
}

/* ── 结果区 ── */
const queryHint = computed(() => {
  if (!filterSupported.value) return '文件类型暂不支持筛选下推，可直接预览文件数据'
  if (!queryFilterRows.value.length) {
    if (fixedFilters.value.length) return '固定筛选条件始终生效；当前没有页面筛选器参数'
    return hasNamedQueryParameters.value
      ? '未绑定页面筛选器；本次不附加筛选条件，按已填写的数据源参数查询'
      : '未绑定筛选器，本次查询将不附加筛选条件'
  }
  if (!hasExecutableFilter.value) return fixedFilters.value.length
    ? '页面筛选条件为空或已关闭；固定筛选条件仍会生效'
    : '筛选条件均为空或已关闭，本次查询将不附加筛选条件'
  return fixedFilters.value.length
    ? '固定筛选条件始终生效；关闭的页面筛选条件不参与本次查询'
    : '筛选条件来自查询配置；关闭的条件不参与本次查询'
})

/** 最近一次执行的时间（展示用 HH:mm） */
const queriedAt = ref(0)
/** 最近一次成功查询对应的条件指纹；不用非响应式结果缓存驱动 computed。 */
const queriedSignature = ref<string | null>(null)

/**
 * 当前定义、SQL 参数和查询配置筛选条件的指纹：用于判断缓存结果是否过期。
 * 条件变了 → 结果照常展示但标注已过期，不静默丢弃，也不假装它还是最新的。
 */
const currentSignature = computed(() =>
  JSON.stringify([
    sql.value,
    fixedFilters.value,
    displayFields.value.map(({ field, title, role }) => [field, title, role]),
    parameters.value.map((p) => [p.name, paramValues[p.name]]),
    queryFilterRows.value.map((row) => ({ field: row.field, operator: row.operator, parameterName: row.parameterName, value: row.value, enabled: row.enabled })),
    hasTimeFilterRows.value ? timeGranularity.value : null,
    sortState.value,
    paginationEnabled.value ? [currentPage.value, currentPageSize.value] : queryLimit.value,
  ]),
)

function persistQueryState(): void {
  if (!stateReady.value) return
  const queryState = {
    filters: queryFilterRows.value.map((row) => ({
      filterComponentId: row.filterComponentId,
      field: row.field,
      parameterName: row.parameterName,
      timeBoundary: row.timeBoundary,
      value: Array.isArray(row.value) ? [...row.value] : row.value,
      enabled: row.enabled,
    })),
    parameters: { ...paramValues },
    sort: sortState.value ? { ...sortState.value } : null,
    page: currentPage.value,
    pageSize: currentPageSize.value,
    queryLimit: queryLimit.value,
    ...(hasTimeFilterRows.value ? { timeGranularity: timeGranularity.value } : {}),
  }
  // 查询条件和值是轻量的用户配置：随数据集输入写入仪表盘 Schema，跨刷新/登录恢复。
  props.dataset.lastQueryState = queryState
  setCachedQueryState(props.dataset.id, queryState)
}

watch(
  () => JSON.stringify([
    queryFilterRows.value.map((row) => [row.filterComponentId, row.field, row.parameterName, row.timeBoundary, row.value, row.enabled]),
    paramValues,
    sortState.value,
    currentPage.value,
    currentPageSize.value,
    queryLimit.value,
  ]),
  persistQueryState,
)

/** 缓存的结果是否还对应当前条件 */
const cacheStale = computed(() => {
  return queriedAt.value > 0 && queriedSignature.value !== currentSignature.value
})
const hasCurrentResult = computed(() => queriedAt.value > 0 && !cacheStale.value && !loading.value && !error.value)
const componentRenderData = computed(() => props.component && columns.value.length
  ? componentPreviewData(props.component, rows.value, componentPreviewColumns.value)
  : undefined)
/** 「组件渲染」页签可用条件：有组件、结果新鲜且通过组件校验 */
const canRenderComponent = computed(() => Boolean(props.component)
  && hasCurrentResult.value
  && !componentValidationError.value
  && !!componentRenderData.value)

const resultHint = computed(() => {
  if (loading.value) return '查询中…'
  if (!columns.value.length) return error.value || '尚未查询'
  const time = queriedAt.value ? ` · ${new Date(queriedAt.value).toTimeString().slice(0, 5)} 查询` : ''
  const stale = cacheStale.value ? ' · 条件已变更，点「查询」刷新' : ''
  const count = totalCount.value === null ? `${rows.value.length} 行` : `${rows.value.length} 行 · 共 ${totalCount.value} 行`
  const paging = paginationEnabled.value ? ` · 第 ${currentPage.value} 页` : hasMore.value ? ' · 可滚动加载更多' : ''
  return `${count}${elapsed.value ? ` · ${elapsed.value}` : ''}${time}${stale}${paging}`
})

async function fetchRows(reset: boolean): Promise<void> {
  const requestSignature = currentSignature.value
  loading.value = true
  error.value = ''
  if (reset) {
    componentValidationError.value = ''
    resultTab.value = 'raw'
  }
  const started = Date.now()
  try {
    const enabledFilterRows = queryFilterRows.value.filter((row) => row.enabled)
    const filters = [...fixedFilters.value, ...toDatasetFilters(enabledFilterRows)]
    const request = draftRequestForDataset({
      ...props.dataset,
      filters: filters as unknown as DatasetConfig['filters'],
      jdbc: { ...props.dataset.jdbc, sql: sql.value },
    })
    if (!request) {
      error.value = '该类型数据集暂不支持取数（需先登记数据源 / 接口定义）'
      columns.value = []
      rows.value = []
      return
    }
    const parameters = { ...namedParameters(), ...buildExecutionParameters(enabledFilterRows) }
    const limit = paginationEnabled.value ? currentPageSize.value : clampQueryLimit(queryLimit.value)
    const offset = paginationEnabled.value
      ? (currentPage.value - 1) * currentPageSize.value
      : reset ? 0 : rows.value.length
    const orders = sortState.value && isFieldSortable(sortState.value.field) ? [sortState.value] : []
    const requestTotalCount = paginationEnabled.value
      && (paginationPolicy.value?.returnTotalCount === true || props.component?.type === 'kpi')
    const hasConfiguredDisplayFields = displayFields.value.length > 0
    const requestedColumns = hasConfiguredDisplayFields
      ? resultDisplayFieldList().map(({ field }) => field)
      : []
    // 已落库数据集优先复用统一读取接口：仪表盘 Schema 只保存 datasetId，
    // 数据定义在查看数据弹窗中只读；已落库数据集统一走输入读取接口。
    let savedDefinitionUnchanged = false
    if (isPersistedBackendDatasetId(props.dataset.backendDatasetId)) {
      const backendDatasetId = props.dataset.backendDatasetId
      let available = persistedDatasetAvailability.get(backendDatasetId)
      if (available === undefined) {
        try {
          const datasets = await listDatasets()
          available = isPersistedDatasetReferenceAvailable(backendDatasetId, datasets)
        } catch {
          // 若数据集目录暂时不可用，保留原读取路径；不能因此改写配置或误判 ID 失效。
          available = true
        }
        persistedDatasetAvailability.set(backendDatasetId, available)
      }
      savedDefinitionUnchanged = available
    }
    const currentRequest = ++requestSequence
    const batch = savedDefinitionUnchanged
      ? await previewInput({
          datasetId: props.dataset.backendDatasetId as string,
          inputName: props.dataset.alias,
          filters: filters as unknown as DatasetConfig['filters'],
          columns: requestedColumns,
          orders,
          requestTotalCount,
          limit,
          offset,
          parameters,
          ...(hasTimeFilterRows.value ? { timeGranularity: timeGranularity.value } : {}),
        })
      : await previewDatasetDraft({
          ...request,
          columns: requestedColumns,
          parameters,
          orders,
          requestTotalCount,
          limit,
          offset,
          ...(hasTimeFilterRows.value ? { timeGranularity: timeGranularity.value } : {}),
        })
    // 旧请求晚返回：丢弃，不覆盖新结果（表头快速切换场景）
    if (currentRequest !== requestSequence) return
    const nextRows = (batch.rows as Record<string, unknown>[] | null) ?? []
    rows.value = paginationEnabled.value || reset ? nextRows : [...rows.value, ...nextRows]
    if (reset) {
      columns.value = hasConfiguredDisplayFields
        ? requestedColumns
        : batch.schema?.length ? batch.schema : nextRows.length ? Object.keys(nextRows[0]) : []
      queriedAt.value = Date.now()
    }
    queriedSignature.value = requestSignature
    hasMore.value = typeof batch.hasNext === 'boolean'
      ? batch.hasNext
      : typeof batch.last === 'boolean'
        ? !batch.last
        : nextRows.length >= limit
    totalCount.value = typeof batch.totalCount === 'number' ? batch.totalCount : null
    elapsed.value = `${((Date.now() - started) / 1000).toFixed(1)}s`
    // 记住这次执行（追加页也一并缓存）：关掉再打开，条件和结果都还在
    // —— 运行时条件只进入本次查询和会话缓存，不写回 Dashboard Schema
    setCachedQuery(props.dataset.id, {
      rows: rows.value,
      columns: columns.value,
      hasMore: hasMore.value,
      elapsed: elapsed.value,
      queriedAt: queriedAt.value,
      signature: requestSignature,
      totalCount: totalCount.value,
    })
    persistQueryState()
    const resultRowCount = totalCount.value ?? rows.value.length
    if (props.component?.type === 'kpi' && resultRowCount > 1) {
      componentValidationError.value = `指标卡只支持 0–1 行结果，当前查询返回 ${resultRowCount} 行；原始数据仍可查看，请检查查询聚合配置。`
    }
    if (reset) {
      await nextTick()
      if (scrollRef.value) scrollRef.value.scrollTop = 0
    }
  } catch (e) {
    error.value = (e as Error)?.message || '查询失败'
    if (reset) componentValidationError.value = ''
    if (reset) {
      columns.value = []
      rows.value = []
    }
  } finally {
    loading.value = false
  }
}

/** 将最近一次有效查询结果显式应用到画布，并在「组件渲染」页签展示同一份组件数据。 */
function renderComponent(): void {
  if (!props.component || !hasCurrentResult.value || componentValidationError.value || !componentRenderData.value) return
  emit('render', componentRenderData.value)
}

/** 切到「组件渲染」页签：同步画布 + 页签内预览 */
function switchToRenderTab(): void {
  if (!canRenderComponent.value) return
  renderComponent()
  resultTab.value = 'render'
}

/** 表头三态：新字段从升序开始；同字段 asc → desc → 取消；变化后页码回 1 并立即重新查询 */
function onSortChange({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }): void {
  if (!isFieldSortable(prop)) return
  const next = order ? elOrderToSortState(prop, order) : null
  const changed = JSON.stringify(next) !== JSON.stringify(sortState.value)
  sortState.value = next
  if (changed) {
    currentPage.value = 1
    pageDraft.value = '1'
    void fetchRows(true)
  }
}

function elOrderToSortState(prop: string, order: 'ascending' | 'descending'): QuerySortSpec {
  return { field: prop, direction: order === 'ascending' ? 'asc' : 'desc' }
}

/** 显式查询：改条件后必须点一下才取数（不再自动跑，避免「一打开数据就出来了」） */
async function query(): Promise<void> {
  if (queryDisabled.value) return
  currentPage.value = 1
  pageDraft.value = '1'
  await fetchRows(true)
}

function changePage(page: number): void {
  if (page < 1 || page === currentPage.value) return
  currentPage.value = page
  pageDraft.value = String(page)
  void fetchRows(true)
}

/** 可在总数未知时按 offset 直达任意正整数页；总数已知时限制在最后一页。 */
function jumpToPage(): void {
  const requestedPage = Number.parseInt(pageDraft.value, 10)
  if (!Number.isFinite(requestedPage) || requestedPage < 1) {
    pageDraft.value = String(currentPage.value)
    return
  }
  const targetPage = maxPage.value === null
    ? requestedPage
    : Math.min(requestedPage, maxPage.value)
  pageDraft.value = String(targetPage)
  changePage(targetPage)
}

function onPageSizeChange(event: Event): void {
  const value = Number((event.target as HTMLSelectElement).value)
  const max = Math.max(1, paginationPolicy.value?.maxPageSize || 500)
  currentPageSize.value = Math.min(max, Math.max(1, value))
  currentPage.value = 1
  pageDraft.value = '1'
  void fetchRows(true)
}

/** 滚动到底部按 offset 拉下一页 */
function onScroll(event: Event): void {
  const el = event.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight > 24) return
  if (paginationEnabled.value || !hasMore.value || loading.value) return
  void fetchRows(false)
}

/* ── 打开时初始化：不取数，只把查询配置/参数铺好，等用户点「查询」 ── */
async function open(): Promise<void> {
  stateReady.value = false
  for (const key of Object.keys(paramValues)) delete paramValues[key]
  for (const param of parameters.value) {
    if (param.defaultValue !== undefined) paramValues[param.name] = param.defaultValue
  }

  queryFilterRows.value = createQueryFilterRows()
  timeGranularity.value = initialTimeGranularity.value
  sortState.value = sortPolicy.value?.enabled ? sortPolicy.value.defaultSort ?? null : null
  currentPage.value = 1
  pageDraft.value = '1'
  queryLimit.value = DEFAULT_QUERY_LIMIT
  currentPageSize.value = Math.min(
    Math.max(1, paginationPolicy.value?.defaultPageSize || DEFAULT_BATCH_SIZE),
    Math.max(1, paginationPolicy.value?.maxPageSize || 500),
  )

  const cachedState = props.dataset.lastQueryState ?? getCachedQueryState(props.dataset.id)
  if (cachedState) {
    if (cachedState.timeGranularity && TIME_GRANULARITY_OPTIONS.some((option) => option.value === cachedState.timeGranularity)) {
      timeGranularity.value = cachedState.timeGranularity
    }
    const restoredRows = new Set<number>()
    cachedState.filters.forEach((saved) => {
      // Bindings can be reordered or have their parameter name regenerated when the
      // dashboard is reopened. Match by the stable filter + field + time-boundary
      // identity instead of relying on array position or the editable parameter name.
      const rowIndex = queryFilterRows.value.findIndex((candidate, index) =>
        !restoredRows.has(index) &&
        candidate.filterComponentId === saved.filterComponentId &&
        candidate.field === saved.field &&
        candidate.timeBoundary === saved.timeBoundary,
      )
      if (rowIndex < 0) return
      restoredRows.add(rowIndex)
      const row = queryFilterRows.value[rowIndex]
      row.value = Array.isArray(saved.value) ? [...saved.value] : saved.value
      row.enabled = saved.enabled
    })
    for (const param of parameters.value) {
      if (Object.hasOwn(cachedState.parameters, param.name)) paramValues[param.name] = cachedState.parameters[param.name]
    }
    sortState.value = cachedState.sort && isFieldSortable(cachedState.sort.field)
      ? cachedState.sort
      : sortPolicy.value?.enabled ? sortPolicy.value.defaultSort ?? null : null
    currentPageSize.value = Math.min(
      Math.max(1, cachedState.pageSize || currentPageSize.value),
      Math.max(1, paginationPolicy.value?.maxPageSize || 500),
    )
    queryLimit.value = clampQueryLimit(cachedState.queryLimit ?? queryLimit.value)
    currentPage.value = paginationEnabled.value ? Math.max(1, cachedState.page || 1) : 1
    pageDraft.value = String(currentPage.value)
  }
  columns.value = []
  rows.value = []
  queriedAt.value = 0
  queriedSignature.value = null
  hasMore.value = false
  totalCount.value = null
  elapsed.value = ''
  error.value = ''
  resultTab.value = 'raw'

  // 恢复最近一次执行结果：条件与结果都还在，不必重新点「查询」。
  // 若条件已被改过，结果照常展示、结果区标注「条件已变更」，由用户决定是否重查。
  const cached = getCachedQuery(props.dataset.id)
  if (cached) {
    rows.value = cached.rows
    columns.value = cached.columns
    hasMore.value = cached.hasMore
    totalCount.value = cached.totalCount ?? null
    elapsed.value = cached.elapsed
    queriedAt.value = cached.queriedAt
    queriedSignature.value = cached.signature
    error.value = ''
  }
  stateReady.value = true
  persistQueryState()
}

watch(() => ui.dataDialog.visible, (visible) => {
  if (visible) void open()
}, { immediate: true })
</script>

<style scoped>
.dd-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 4px;
}
.dd-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dd-filter-head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
}
.dd-time-granularity {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--db-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.dd-time-granularity :deep(.el-select) {
  width: 112px;
}
.dd-result-tabs {
  display: flex;
  align-items: center;
  gap: 18px;
}
.dd-tab {
  padding: 4px 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--db-text-muted);
  cursor: pointer;
}
.dd-tab.active {
  color: var(--db-text);
  font-weight: 600;
  border-bottom-color: var(--el-color-primary, var(--db-text));
}
.dd-tab:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.dd-render-pane {
  display: flex;
  flex-direction: column;
}
.dd-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text);
}
.dd-hint {
  font-size: 12px;
  color: var(--db-text-muted);
}
.dd-table-wrap {
  overflow-x: auto;
  overflow-y: hidden;
  background: #fff;
  border-radius: var(--radius-md);
}
.dd-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.dd-table th,
.dd-table td {
  padding: 7px 10px;
  border-bottom: 1px solid var(--db-border);
  text-align: left;
  vertical-align: middle;
}
.dd-filter-table {
  min-width: 760px;
}
.dd-display-table {
  min-width: 560px;
}
.dd-table th {
  color: var(--db-text-muted);
  font-weight: 500;
  white-space: nowrap;
}
.dd-table td {
  color: var(--db-text-secondary);
}
.dd-table tbody tr:last-child td {
  border-bottom: 0;
}
.dd-field-name,
.dd-sql-readonly code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--db-text);
  word-break: break-all;
}
.dd-field-name {
  color: var(--db-text);
}
.dd-display-empty {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: #fff;
  color: var(--db-text-muted);
  font-size: 12px;
}
.dd-sql-readonly {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--db-muted);
  color: var(--db-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: 12px/1.7 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.dd-param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.dd-param-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary);
  margin-bottom: 5px;
}
.dd-param-type {
  color: var(--db-text-muted);
}
.dd-param-req {
  color: var(--db-warning);
}
.dd-param-control {
  width: 100%;
}
.dd-bound-field {
  min-width: 0;
  white-space: nowrap;
}
.dd-bound-filter,
.dd-fixed-operator {
  color: var(--db-text-secondary);
  white-space: nowrap;
}
.dd-bound-field code {
  margin-left: 4px;
  color: var(--db-text-muted);
}
.dd-value {
  display: block;
  width: 100%;
  min-width: 0;
}
.dd-empty {
  padding: 10px 12px;
  border: 1px dashed var(--db-border);
  border-radius: var(--radius-md);
  background: #fff;
  font-size: 12px;
  color: var(--db-text-muted);
}
.dd-query-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dd-query-row .dd-hint {
  flex: 1;
  min-width: 0;
}
.dd-limit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary, #6b7280);
  white-space: nowrap;
}
.dd-result-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dd-result-body {
  height: 300px;
  overflow: auto;
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
}
.dd-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 8px;
  color: var(--db-text-muted);
  font-size: 12px;
}
.dd-page-size {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dd-page-size select {
  height: 26px;
  padding: 0 6px;
  border: 1px solid var(--db-border);
  border-radius: var(--radius-sm);
  color: var(--db-text-secondary);
  background: var(--db-surface);
}
.dd-page-jump {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.dd-page-jump input {
  width: 58px;
  height: 26px;
  padding: 0 5px;
  border: 1px solid var(--db-border);
  border-radius: var(--radius-sm);
  color: var(--db-text-secondary);
  background: var(--db-surface);
  font: inherit;
}
.dd-page-jump input:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>

<style>
/* 弹窗本体由 Element Plus 渲染在 teleport 里，槽内样式管不到它的 body 内边距 */
.dataset-data-dialog .el-dialog__body {
  padding: 8px 20px 4px;
}
</style>
