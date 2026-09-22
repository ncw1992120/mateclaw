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
      <!-- ① 定义：这份数据是怎么来的 -->
      <section class="dd-block">
        <div class="dd-head">
          <span class="dd-title">定义</span>
          <span class="dd-hint">{{ definitionHint }}</span>
        </div>
        <el-input
          v-if="isSql"
          v-model="sql"
          type="textarea"
          :rows="5"
          class="dd-code"
          placeholder="SELECT ... WHERE created_at >= :start_date"
        />
        <div v-else class="dd-kv">
          <div v-for="row in definitionRows" :key="row.label" class="dd-kv-row">
            <span class="dd-kv-label">{{ row.label }}</span>
            <code class="dd-kv-value">{{ row.value }}</code>
          </div>
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

      <!-- ③ 筛选条件：字段 + 操作符 + 取值，查询时下推到源查询 -->
      <section v-if="filterSupported" class="dd-block">
        <div class="dd-head">
          <span class="dd-title">筛选条件</span>
        </div>

        <div v-if="runtimeBoundRows.length" class="dd-bound-conditions">
          <div class="dd-subtitle">绑定筛选条件（本次查询）</div>
          <div
            v-for="(row, index) in runtimeBoundRows"
            :key="`${row.field}-${row.parameterName}-${index}`"
            class="dd-condition dd-bound-condition"
            data-testid="bound-filter-row"
          >
            <span class="dd-bound-field">{{ row.field }}</span>
            <code class="dd-bound-op">{{ row.operator }}</code>
            <span class="dd-bound-param">${{ row.parameterName }}</span>
            <el-input v-model="row.value" class="dd-value" size="small" placeholder="可选，未填写不参与查询" />
            <el-button size="small" text type="danger" :icon="Delete" @click="removeBoundCondition(index)" />
          </div>
        </div>

        <el-alert
          v-if="!optionsLoading && !fieldOptions.length"
          class="dd-note"
          type="info"
          :closable="false"
          title="未取到可筛选字段，可直接输入字段名；字段名需与数据源一致。"
        />

        <div v-if="conditions.length" class="dd-conditions">
          <div v-for="(row, index) in conditions" :key="index" class="dd-condition">
            <el-select
              v-model="row.field"
              class="dd-field"
              placeholder="字段"
              size="small"
              filterable
              allow-create
              default-first-option
              :loading="optionsLoading"
            >
              <el-option v-for="option in fieldOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select v-model="row.op" class="dd-op" size="small" @change="onOperatorChange(row)">
              <el-option v-for="option in operatorOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-input
              v-model="row.value"
              class="dd-value"
              size="small"
              :disabled="!needsValue(row.op)"
              :placeholder="needsValue(row.op) ? valueHintOf(row.op) : '无需取值'"
            />
            <el-button size="small" text type="danger" :icon="Delete" @click="removeCondition(index)" />
          </div>
        </div>
        <div v-else class="dd-empty">暂无筛选条件，点下方「添加筛选条件」新建一条；不加条件则取全量数据。</div>

        <!-- 添加入口放在条件行下方、查询上方：先铺条件，再统一查询 -->
        <div class="dd-add-row">
          <el-button size="small" :icon="Plus" :loading="optionsLoading" @click="addCondition">添加筛选条件</el-button>
        </div>

        <!-- 遗留条件：字段已不在当前可筛选项内 —— 可见、可清理，不静默失效 -->
        <div v-if="legacyConditions.length" class="dd-legacy">
          <div class="dd-legacy-title">遗留条件（字段已不在当前可筛选项内，仍会参与下推）</div>
          <div v-for="item in legacyConditions" :key="item.field" class="dd-legacy-item">
            <code>{{ item.field }}</code>
            <span class="dd-legacy-op">{{ item.op }}</span>
            <span class="dd-legacy-value">{{ item.value || '—' }}</span>
            <el-button size="small" text type="danger" @click="removeLegacy(item.field)">移除</el-button>
          </div>
        </div>
      </section>

      <!-- ④ 查询：紧跟在筛选条件下方 —— 改完条件顺手点，视线不用跳到弹窗底部 -->
      <div class="dd-query-row">
        <span class="dd-hint">{{ queryHint }}</span>
        <el-button type="primary" :loading="loading" @click="query()">查询</el-button>
      </div>

      <!-- ⑤ 结果 -->
      <section class="dd-block dd-result">
        <div class="dd-head">
          <span class="dd-title">结果</span>
          <span class="dd-hint">{{ resultHint }}</span>
        </div>
        <div ref="scrollRef" class="dd-result-body" @scroll="onScroll">
          <el-table v-if="columns.length" :data="rows" border size="small" height="100%">
            <el-table-column
              v-for="column in columns"
              :key="column"
              :prop="column"
              :label="column"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
          <el-empty v-else-if="loading" description="查询中…" />
          <el-empty v-else :description="error || '点「查询」获取数据'" />
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useInsight } from './card-attribute/useInsight'
import { previewDatasetDraft } from './card-attribute/useInsightBackend'
import { previewInput } from '@/api/dataset'
import { draftRequestForDataset, isPersistedBackendDatasetId } from './card-attribute/useInsight'
import type { DatasetConfig } from './card-attribute/useInsight'
import { listAnalysisViewFields } from '@/api/datasource'
import { resolveFieldLabel } from '@/utils/field-mapping'
import { extractApiParameters, extractSqlParameters, type ExtractedParameter } from '@/utils/parameter-extract'
import { getCachedQuery, setCachedQuery } from './dataset-data-cache'
import {
  DIMENSION_OPERATORS,
  GENERIC_OPERATORS,
  completeConditions,
  emptyCondition,
  needsValue,
  partitionConditions,
  valueHintOf,
  withoutField,
  type FilterCondition,
} from '@/utils/filter-conditions'
import {
  buildExecutionParameters,
  defaultRuntimeRows,
  toDatasetFilters,
  type RuntimeFilterRow,
} from '@/utils/runtime-filter-bindings'
import type { DashboardScriptFilterBinding } from '@/types'

const props = defineProps<{ dataset: DatasetConfig }>()
const { state } = useInsight()
const ui = state.ui

/** 每页条数（滚动到底按 offset 追加一页，分页交给服务端） */
const PAGE_SIZE = 50

const loading = ref(false)
const error = ref('')
const elapsed = ref('')
const columns = ref<string[]>([])
const rows = ref<Record<string, unknown>[]>([])
const hasMore = ref(false)
const scrollRef = ref<HTMLElement | null>(null)

const isSql = computed(() => props.dataset.sourceType === 'jdbc')
const isApi = computed(() => props.dataset.sourceType === 'api')
const isAloudata = computed(() => props.dataset.sourceType === 'aloudata')

/* ── 定义区 ── */
const sql = ref(props.dataset.jdbc?.sql ?? '')

const definitionHint = computed(() => {
  if (isSql.value) return 'SQL 语句 · 可编辑，改完点「查询」生效'
  if (isApi.value) return '接口信息 · 只读'
  if (isAloudata.value) return props.dataset.aloudata?.mode === 'metric-view' ? '指标视图 · 只读' : '指标 / 维度 · 只读'
  return '文件信息 · 只读'
})

const definitionRows = computed<{ label: string; value: string }[]>(() => {
  const ds = props.dataset
  if (isApi.value) {
    const endpoint = `${ds.api?.host ?? ''}${ds.api?.path ?? ''}`
    return [
      { label: '地址', value: endpoint || '—' },
      { label: '方法', value: ds.api?.method || 'GET' },
      { label: '请求参数', value: ds.api?.params || '—' },
    ]
  }
  if (isAloudata.value) {
    const aloudata = ds.aloudata
    const list: { label: string; value: string }[] = [
      { label: '指标', value: (aloudata?.metrics ?? []).join('、') || '—' },
      { label: '维度', value: (aloudata?.dims ?? []).join('、') || '—' },
    ]
    if (aloudata?.metricView) list.unshift({ label: '指标视图', value: aloudata.metricView })
    return list
  }
  return [
    { label: '文件', value: ds.file?.fileName || '—' },
    { label: '类型', value: ds.file?.fileType || '—' },
    { label: '字段数', value: String(ds.file?.columns?.length ?? 0) },
  ]
})

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

const conditions = ref<FilterCondition[]>([])
const legacyConditions = ref<FilterCondition[]>([])
const boundRows = ref<RuntimeFilterRow[]>([])
const runtimeBoundRows = computed(() => boundRows.value)
const fieldOptions = ref<{ value: string; label: string }[]>([])
const optionsLoading = ref(false)

/**
 * 可筛选字段：
 *  - Aloudata 指标视图：视图定义里的**维度**（指标是聚合结果，不作为筛选项）
 *  - Aloudata 指标&维度：已选维度
 *  - 其余类型：字段注册表（已配「字段名称」时），回退文件列
 */
async function loadFieldOptions(): Promise<void> {
  const ds = props.dataset
  const aloudata = ds.aloudata
  if (isAloudata.value && aloudata?.datasourceId && aloudata.mode === 'metric-view' && aloudata.metricView) {
    optionsLoading.value = true
    try {
      const fields = await listAnalysisViewFields(aloudata.datasourceId, aloudata.metricView)
      fieldOptions.value = fields
        .filter((field) => (field.role ?? 'dimension').toLowerCase() === 'dimension')
        .map((field) => ({ value: field.name, label: field.displayName || field.name }))
    } catch {
      fieldOptions.value = []
      ElMessage.warning('指标视图维度加载失败，可手动输入字段名')
    } finally {
      optionsLoading.value = false
    }
    return
  }
  if (isAloudata.value) {
    fieldOptions.value = (aloudata?.dims ?? []).filter(Boolean).map((name) => ({ value: name, label: name }))
    return
  }
  const fields = ds.fields ?? []
  fieldOptions.value = fields.length
    ? fields.map((field) => ({ value: field.name, label: resolveFieldLabel(fields, field.name) }))
    : (ds.file?.columns ?? []).map((column) => ({ value: column.name, label: column.name }))
}

/** Aloudata 语义层只接受 = <> > >= < <= IN NotIn，因此维度筛选的运算符范围更窄 */
const operatorOptions = computed(() => (isAloudata.value ? DIMENSION_OPERATORS : GENERIC_OPERATORS))

function addCondition(): void {
  conditions.value.push(emptyCondition())
}

function removeCondition(index: number): void {
  conditions.value.splice(index, 1)
}

function removeBoundCondition(index: number): void {
  boundRows.value.splice(index, 1)
}

/** 从「为空」这类无需取值的运算符切回来时，把残留的禁用值清掉，避免下推一个看不见的值 */
function onOperatorChange(row: FilterCondition): void {
  if (!needsValue(row.op)) row.value = ''
}

function removeLegacy(field: string): void {
  legacyConditions.value = withoutField(legacyConditions.value, field)
}

/* ── 结果区 ── */
const queryHint = computed(() =>
  filterSupported.value ? '绑定条件和附加条件按 AND 组合；多个条件按 AND 组合；空的绑定值不参与查询' : '文件类型暂不支持筛选下推，直接查询即可',
)

/** 最近一次执行的时间（展示用 HH:mm） */
const queriedAt = ref(0)

/**
 * 当前「定义 + 条件」的指纹：用来判断缓存的结果还是不是这份条件查出来的。
 * 条件变了 → 结果照常展示但标注已过期，不静默丢弃，也不假装它还是最新的。
 */
const currentSignature = computed(() =>
  JSON.stringify([
    sql.value,
    parameters.value.map((p) => [p.name, paramValues[p.name]]),
    boundRows.value.map((row) => ({ field: row.field, operator: row.operator, parameterName: row.parameterName, value: row.value })),
    completeConditions(conditions.value),
    legacyConditions.value,
  ]),
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
  return `${rows.value.length} 行${elapsed.value ? ` · ${elapsed.value}` : ''}${time}${stale}${hasMore.value ? ' · 可滚动加载更多' : ''}`
})

async function fetchRows(reset: boolean): Promise<void> {
  loading.value = true
  error.value = ''
  const started = Date.now()
  try {
    const filters = [
      ...completeConditions(conditions.value),
      ...legacyConditions.value,
      ...toDatasetFilters(boundRows.value),
    ]
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
    const parameters = { ...namedParameters(), ...buildExecutionParameters(boundRows.value) }
    const offset = reset ? 0 : rows.value.length
    // 已落库数据集优先复用统一读取接口：仪表盘 Schema 只保存 datasetId，
    // 不应要求前端重新携带 SQL 才能查看数据。用户在弹窗内修改 SQL 后，
    // 才降级到草稿预览，以保留“改完点查询”的编辑能力。
    const savedDefinitionUnchanged = isPersistedBackendDatasetId(props.dataset.backendDatasetId)
      && (!isSql.value || sql.value === (props.dataset.jdbc?.sql ?? ''))
    const batch = savedDefinitionUnchanged
      ? await previewInput({
          datasetId: props.dataset.backendDatasetId as string,
          inputName: props.dataset.alias,
          filters: filters as DatasetConfig['filters'],
          limit: PAGE_SIZE,
          offset,
          parameters,
        })
      : await previewDatasetDraft({ ...request, parameters, limit: PAGE_SIZE, offset })
    const nextRows = (batch.rows as Record<string, unknown>[] | null) ?? []
    rows.value = reset ? nextRows : [...rows.value, ...nextRows]
    if (reset) {
      columns.value = nextRows.length ? Object.keys(nextRows[0]) : []
      queriedAt.value = Date.now()
    }
    hasMore.value = nextRows.length >= PAGE_SIZE
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
    })
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

/** 显式查询：改条件后必须点一下才取数（不再自动跑，避免「一打开数据就出来了」） */
async function query(): Promise<void> {
  await fetchRows(true)
}

/** 滚动到底部按 offset 拉下一页 */
function onScroll(event: Event): void {
  const el = event.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight > 24) return
  if (!hasMore.value || loading.value) return
  void fetchRows(false)
}

/* ── 打开时初始化：不取数，只把定义 / 参数 / 条件铺好，等用户点「查询」 ── */
async function open(): Promise<void> {
  sql.value = props.dataset.jdbc?.sql ?? ''
  for (const key of Object.keys(paramValues)) delete paramValues[key]
  for (const param of parameters.value) {
    if (param.defaultValue !== undefined) paramValues[param.name] = param.defaultValue
  }

  conditions.value = []
  legacyConditions.value = []
  boundRows.value = []
  fieldOptions.value = []
  columns.value = []
  rows.value = []
  hasMore.value = false
  elapsed.value = ''
  error.value = ''

  await loadFieldOptions()
  const runtimeBindings: DashboardScriptFilterBinding[] = state.filterBindings.map((binding) => ({
    filterComponentId: binding.filterName,
    inputNames: state.datasets
      .filter((item) => binding.scope[item.id] || binding.scope[item.alias])
      .map((item) => item.alias),
    fieldMappings: Object.fromEntries(binding.fieldMap.map((item) => [item.datasetId, item.field])),
    conditions: binding.conditions ?? [],
  }))
  boundRows.value = defaultRuntimeRows(props.dataset.alias, runtimeBindings)
  // 可选项就绪后再分流：此时才判断得出哪些存量条件属于「遗留」
  const saved = (props.dataset.filters ?? []) as unknown as Parameters<typeof partitionConditions>[0]
  const known = new Set(fieldOptions.value.map((option) => option.value))
  const { rows: editable, legacy } = partitionConditions(saved, known)
  conditions.value = editable.length ? editable : [emptyCondition()]
  legacyConditions.value = legacy

  // 恢复最近一次执行结果：条件与结果都还在，不必重新点「查询」。
  // 若条件已被改过，结果照常展示、结果区标注「条件已变更」，由用户决定是否重查。
  const cached = getCachedQuery(props.dataset.id)
  if (cached) {
    rows.value = cached.rows
    columns.value = cached.columns
    hasMore.value = cached.hasMore
    elapsed.value = cached.elapsed
    queriedAt.value = cached.queriedAt
    error.value = ''
  }
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
.dd-note {
  margin: 0;
}
.dd-code :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
}
.dd-kv {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--db-muted);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.dd-kv-row {
  display: flex;
  gap: 10px;
  font-size: 12px;
}
.dd-kv-label {
  flex-shrink: 0;
  width: 68px;
  color: var(--db-text-muted);
}
.dd-kv-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--db-text);
  word-break: break-all;
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
.dd-conditions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dd-bound-conditions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  background: var(--db-muted);
}
.dd-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-secondary);
}
.dd-bound-field {
  min-width: 140px;
  color: var(--db-text);
}
.dd-bound-op,
.dd-bound-param {
  flex-shrink: 0;
  color: var(--db-text-secondary);
}
.dd-condition {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dd-field {
  flex: 1.2;
  min-width: 0;
}
.dd-op {
  width: 120px;
  flex-shrink: 0;
}
.dd-value {
  flex: 1;
  min-width: 0;
}
.dd-empty {
  font-size: 12px;
  color: var(--db-text-muted);
}
.dd-legacy {
  border: 1px dashed var(--db-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.dd-legacy-title {
  font-size: 12px;
  color: var(--db-text-muted);
  margin-bottom: 6px;
}
.dd-legacy-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 0;
  font-size: 12px;
}
.dd-legacy-item code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--db-text);
}
.dd-legacy-op {
  color: var(--db-text-secondary);
}
.dd-legacy-value {
  flex: 1;
  min-width: 0;
  color: var(--db-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dd-add-row {
  display: flex;
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
</style>

<style>
/* 弹窗本体由 Element Plus 渲染在 teleport 里，槽内样式管不到它的 body 内边距 */
.dataset-data-dialog .el-dialog__body {
  padding: 8px 20px 4px;
}
</style>
