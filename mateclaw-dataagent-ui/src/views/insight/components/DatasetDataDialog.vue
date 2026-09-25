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

      <!-- ③ 筛选条件来自查询配置；当前预览只允许填写值和启停 -->
      <section v-if="filterSupported" class="dd-block">
        <div class="dd-head">
          <span class="dd-title">筛选条件</span>
          <span v-if="hasTimeFilterRows" class="dd-hint">时间范围左闭右开：包含开始时间，不包含结束时间</span>
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
          尚未绑定筛选器，请先在查询配置中添加筛选器绑定，再填写条件值。
        </div>
      </section>

      <!-- ④ 查询：紧跟在筛选条件下方 —— 改完条件顺手点，视线不用跳到弹窗底部 -->
      <div class="dd-query-row">
        <span class="dd-hint">{{ queryHint }}</span>
        <el-button data-testid="run-query" type="primary" :loading="loading" :disabled="queryDisabled" @click="query()">查询</el-button>
      </div>

      <!-- ⑤ 结果 -->
      <section class="dd-block dd-result">
        <div class="dd-head">
          <span class="dd-title">结果</span>
          <span class="dd-hint">{{ resultHint }}</span>
        </div>
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
          <el-button size="small" :disabled="currentPage <= 1 || loading" data-testid="page-previous" @click="changePage(currentPage - 1)">上一页</el-button>
          <el-button size="small" :disabled="!hasMore || loading" data-testid="page-next" @click="changePage(currentPage + 1)">下一页</el-button>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useInsight } from './card-attribute/useInsight'
import { previewDatasetDraft } from './card-attribute/useInsightBackend'
import { previewInput } from '@/api/dataset'
import { draftRequestForDataset, isPersistedBackendDatasetId } from './card-attribute/useInsight'
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

const props = defineProps<{ dataset: DatasetConfig }>()
const { state } = useInsight()
const ui = state.ui

/** 未配置分页时沿用预览安全批次，滚动到底继续追加。 */
const DEFAULT_BATCH_SIZE = 50

const loading = ref(false)
const error = ref('')
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
const stateReady = ref(false)
/** 递增请求序号：排序/翻页快速切换时丢弃旧请求晚返回的响应 */
let requestSequence = 0

const isSql = computed(() => props.dataset.sourceType === 'jdbc')
const isApi = computed(() => props.dataset.sourceType === 'api')

/* ── 查询配置只读展示 ── */
const sql = computed(() => props.dataset.jdbc?.sql ?? '')
const displayFields = computed<QueryDisplayField[]>(() => props.dataset.queryConfig?.displayFields ?? [])
const paginationPolicy = computed(() => props.dataset.queryConfig?.paginationPolicy)
const paginationEnabled = computed(() => paginationPolicy.value?.enabled === true)
const sortPolicy = computed(() => props.dataset.queryConfig?.sortPolicy)
const pageSizeOptions = computed(() => {
  const max = Math.max(1, paginationPolicy.value?.maxPageSize || 500)
  const initial = Math.min(max, Math.max(1, paginationPolicy.value?.defaultPageSize || DEFAULT_BATCH_SIZE))
  return [...new Set([initial, 10, 20, 50, 100, 200, 500].filter((size) => size <= max))].sort((a, b) => a - b)
})

function isFieldSortable(field: string): boolean {
  return sortPolicy.value?.enabled === true && sortPolicy.value.allowedFields.includes(field)
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

interface PreviewQueryFilterRow extends RuntimeFilterRow {
  filterTitle: string
  fieldTitle: string
  enabled: boolean
  timeBoundary?: 'start' | 'end'
}

function fieldTitle(field: string): string {
  return displayFields.value.find((row) => row.field === field)?.title || field
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
/** JDBC / API 预定义的命名参数也是本次查询条件；空参数不能作为无条件查询放行。 */
const hasNamedQueryParameters = computed(() => Object.keys(namedParameters()).length > 0)
/** 文件数据集没有可下推筛选器，保留其直接预览路径；其他类型至少需要一个有效条件。 */
const queryDisabled = computed(() => loading.value ||
  (filterSupported.value && !hasExecutableFilter.value && !hasNamedQueryParameters.value),
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

function setQueryFilterEnabled(row: PreviewQueryFilterRow, enabled: boolean): void {
  row.enabled = enabled
  if (!enabled) row.value = ''
}

/* ── 结果区 ── */
const queryHint = computed(() => {
  if (!filterSupported.value) return '文件类型暂不支持筛选下推，可直接预览文件数据'
  if (!queryFilterRows.value.length) {
    return hasNamedQueryParameters.value
      ? '尚未绑定页面筛选器；本次将使用已填写的数据源参数查询'
      : '请先在查询配置中绑定筛选器，再执行查询'
  }
  if (!hasExecutableFilter.value && !hasNamedQueryParameters.value) return '至少启用一个筛选条件并填写本次查询值后，才能查询'
  return '筛选条件来自查询配置；关闭的条件不参与本次查询'
})

/** 最近一次执行的时间（展示用 HH:mm） */
const queriedAt = ref(0)

/**
 * 当前定义、SQL 参数和查询配置筛选条件的指纹：用于判断缓存结果是否过期。
 * 条件变了 → 结果照常展示但标注已过期，不静默丢弃，也不假装它还是最新的。
 */
const currentSignature = computed(() =>
  JSON.stringify([
    sql.value,
    displayFields.value.map(({ field, title, role }) => [field, title, role]),
    parameters.value.map((p) => [p.name, paramValues[p.name]]),
    queryFilterRows.value.map((row) => ({ field: row.field, operator: row.operator, parameterName: row.parameterName, value: row.value, enabled: row.enabled })),
    sortState.value,
    paginationEnabled.value ? [currentPage.value, currentPageSize.value] : null,
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
  ]),
  persistQueryState,
)

/** 缓存的结果是否还对应当前条件 */
const cacheStale = computed(() => {
  const cached = getCachedQuery(props.dataset.id)
  return !!cached && cached.signature !== currentSignature.value
})

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
  loading.value = true
  error.value = ''
  const started = Date.now()
  try {
    const enabledFilterRows = queryFilterRows.value.filter((row) => row.enabled)
    const filters = toDatasetFilters(enabledFilterRows)
    const request = draftRequestForDataset({
      ...props.dataset,
      filters: filters as DatasetConfig['filters'],
      jdbc: { ...props.dataset.jdbc, sql: sql.value },
    })
    if (!request) {
      error.value = '该类型数据集暂不支持取数（需先登记数据源 / 接口定义）'
      columns.value = []
      rows.value = []
      return
    }
    const parameters = { ...namedParameters(), ...buildExecutionParameters(enabledFilterRows) }
    const limit = paginationEnabled.value ? currentPageSize.value : DEFAULT_BATCH_SIZE
    const offset = paginationEnabled.value
      ? (currentPage.value - 1) * currentPageSize.value
      : reset ? 0 : rows.value.length
    const orders = sortState.value && isFieldSortable(sortState.value.field) ? [sortState.value] : []
    const requestTotalCount = paginationEnabled.value && paginationPolicy.value?.returnTotalCount === true
    // 已落库数据集优先复用统一读取接口：仪表盘 Schema 只保存 datasetId，
    // 数据定义在查看数据弹窗中只读；已落库数据集统一走输入读取接口。
    const savedDefinitionUnchanged = isPersistedBackendDatasetId(props.dataset.backendDatasetId)
    const currentRequest = ++requestSequence
    const batch = savedDefinitionUnchanged
      ? await previewInput({
          datasetId: props.dataset.backendDatasetId as string,
          inputName: props.dataset.alias,
          filters: filters as DatasetConfig['filters'],
          columns: displayFields.value.map(({ field }) => field),
          orders,
          requestTotalCount,
          limit,
          offset,
          parameters,
        })
      : await previewDatasetDraft({
          ...request,
          columns: displayFields.value.map(({ field }) => field),
          parameters,
          orders,
          requestTotalCount,
          limit,
          offset,
        })
    // 旧请求晚返回：丢弃，不覆盖新结果（表头快速切换场景）
    if (currentRequest !== requestSequence) return
    const nextRows = (batch.rows as Record<string, unknown>[] | null) ?? []
    rows.value = paginationEnabled.value || reset ? nextRows : [...rows.value, ...nextRows]
    if (reset) {
      columns.value = batch.schema?.length ? batch.schema : nextRows.length ? Object.keys(nextRows[0]) : displayFields.value.map(({ field }) => field)
      queriedAt.value = Date.now()
    }
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
      signature: currentSignature.value,
      totalCount: totalCount.value,
    })
    persistQueryState()
    if (reset) {
      await nextTick()
      if (scrollRef.value) scrollRef.value.scrollTop = 0
    }
  } catch (e) {
    error.value = (e as Error)?.message || '查询失败'
    if (reset) {
      columns.value = []
      rows.value = []
    }
  } finally {
    loading.value = false
  }
}

/** 表头三态：新字段从升序开始；同字段 asc → desc → 取消；变化后页码回 1 并立即重新查询 */
function onSortChange({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }): void {
  if (!isFieldSortable(prop)) return
  const next = order ? elOrderToSortState(prop, order) : null
  const changed = JSON.stringify(next) !== JSON.stringify(sortState.value)
  sortState.value = next
  if (changed) {
    currentPage.value = 1
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
  await fetchRows(true)
}

function changePage(page: number): void {
  if (page < 1 || page === currentPage.value) return
  currentPage.value = page
  void fetchRows(true)
}

function onPageSizeChange(event: Event): void {
  const value = Number((event.target as HTMLSelectElement).value)
  const max = Math.max(1, paginationPolicy.value?.maxPageSize || 500)
  currentPageSize.value = Math.min(max, Math.max(1, value))
  currentPage.value = 1
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
  sortState.value = sortPolicy.value?.enabled ? sortPolicy.value.defaultSort ?? null : null
  currentPage.value = 1
  currentPageSize.value = Math.min(
    Math.max(1, paginationPolicy.value?.defaultPageSize || DEFAULT_BATCH_SIZE),
    Math.max(1, paginationPolicy.value?.maxPageSize || 500),
  )

  const cachedState = props.dataset.lastQueryState ?? getCachedQueryState(props.dataset.id)
  if (cachedState) {
    cachedState.filters.forEach((saved, index) => {
      const row = queryFilterRows.value[index]
      if (!row || row.filterComponentId !== saved.filterComponentId || row.field !== saved.field || row.parameterName !== saved.parameterName || row.timeBoundary !== saved.timeBoundary) return
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
    currentPage.value = paginationEnabled.value ? Math.max(1, cachedState.page || 1) : 1
  }
  columns.value = []
  rows.value = []
  hasMore.value = false
  totalCount.value = null
  elapsed.value = ''
  error.value = ''

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
.dd-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
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
</style>

<style>
/* 弹窗本体由 Element Plus 渲染在 teleport 里，槽内样式管不到它的 body 内边距 */
.dataset-data-dialog .el-dialog__body {
  padding: 8px 20px 4px;
}
</style>
