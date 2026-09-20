<template>
  <div class="data-workbench">
    <!-- 顶栏：返回 / 标题 / 运行 -->
    <div class="wb-header">
      <el-button text @click="emit('close')">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <div class="wb-title">
        <span class="wb-name">查看数据</span>
        <span class="wb-source">{{ dataset.sourceLabel }} · [{{ dataset.alias }}]</span>
      </div>
      <el-button type="primary" :loading="loading" @click="run()">运行</el-button>
    </div>

    <!-- ① 定义区 -->
    <section class="wb-section">
      <div class="wb-section-head">
        <span class="wb-section-title">定义</span>
        <span class="wb-section-hint">{{ definitionHint }}</span>
      </div>
      <div class="wb-definition">
        <el-input
          v-if="isSql"
          v-model="sql"
          type="textarea"
          :rows="6"
          class="wb-code"
          placeholder="SELECT ... WHERE created_at >= :start_date"
        />
        <div v-else-if="isApi" class="wb-kv">
          <div class="wb-kv-row"><span>地址</span><code>{{ apiHost }}{{ apiPath }}</code></div>
          <div class="wb-kv-row"><span>方法</span><code>{{ dataset.api?.method || 'POST' }}</code></div>
          <div class="wb-kv-row"><span>请求参数</span><code>{{ dataset.api?.params || '—' }}</code></div>
        </div>
        <div v-else-if="isFile" class="wb-kv">
          <div class="wb-kv-row"><span>文件</span><code>{{ dataset.file?.fileName || '—' }}</code></div>
          <div class="wb-kv-row"><span>类型</span><code>{{ dataset.file?.fileType || '—' }}</code></div>
        </div>
        <div v-else class="wb-kv">
          <div class="wb-kv-row">
            <span>指标</span><code>{{ (dataset.aloudata?.metrics ?? []).join('、') || '—' }}</code>
          </div>
          <div class="wb-kv-row">
            <span>维度</span><code>{{ (dataset.aloudata?.dims ?? []).join('、') || '—' }}</code>
          </div>
          <div v-if="dataset.aloudata?.metricView" class="wb-kv-row">
            <span>指标视图</span><code>{{ dataset.aloudata.metricView }}</code>
          </div>
        </div>
      </div>
    </section>

    <!-- ② 参数区：从定义自动提取 -->
    <section class="wb-section">
      <div class="wb-section-head">
        <span class="wb-section-title">参数</span>
        <span class="wb-section-hint">
          {{ parameters.length ? `从定义自动提取 · ${parameters.length} 项` : '该类型没有可提取的参数' }}
        </span>
      </div>

      <el-alert
        v-if="pendingBackendHint"
        class="wb-note"
        type="info"
        :closable="false"
        :title="pendingBackendHint"
      />

      <div v-if="parameters.length" class="wb-param-grid">
        <div v-for="param in parameters" :key="param.name" class="wb-param">
          <label class="wb-param-label">
            {{ param.name }}
            <span class="wb-param-type">{{ param.type }}</span>
            <span v-if="param.required" class="wb-param-req">必填</span>
          </label>
          <el-date-picker
            v-if="param.type === 'date'"
            v-model="values[param.name]"
            type="date"
            size="small"
            value-format="YYYY-MM-DD"
            class="wb-param-control"
          />
          <el-date-picker
            v-else-if="param.type === 'date_range'"
            v-model="values[param.name]"
            type="daterange"
            size="small"
            value-format="YYYY-MM-DD"
            class="wb-param-control"
          />
          <el-select
            v-else-if="param.type === 'enum' && param.options?.length"
            v-model="values[param.name]"
            size="small"
            class="wb-param-control"
          >
            <el-option v-for="opt in param.options" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-select
            v-else-if="param.type === 'boolean'"
            v-model="values[param.name]"
            size="small"
            class="wb-param-control"
          >
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
          <el-input-number
            v-else-if="param.type === 'number'"
            v-model="values[param.name]"
            size="small"
            class="wb-param-control"
          />
          <el-input
            v-else
            v-model="values[param.name]"
            size="small"
            class="wb-param-control"
            :placeholder="param.type === 'string[]' ? '多个值用逗号分隔' : ''"
          />
        </div>
      </div>
      <div v-else class="wb-empty">无需参数，直接运行即可查看数据</div>

      <!-- 遗留条件：存量筛选里字段已不在当前可筛选项内的条目 —— 可见、可清理，不静默失效 -->
      <div v-if="legacyFilters.length" class="wb-legacy">
        <div class="wb-legacy-title">遗留条件（字段已不在当前可筛选项内）</div>
        <div v-for="item in legacyFilters" :key="item.field" class="wb-legacy-item">
          <code>{{ item.field }}</code>
          <span class="wb-legacy-op">{{ item.operator || '=' }}</span>
          <span class="wb-legacy-val">{{ String(item.value ?? '') }}</span>
          <el-button size="small" text type="danger" @click="removeLegacy(item.field)">移除</el-button>
        </div>
      </div>
    </section>

    <!-- ③ 结果区：滚动加载 + 计数 -->
    <section class="wb-section wb-result">
      <div class="wb-section-head">
        <span class="wb-section-title">结果</span>
        <span class="wb-section-hint">
          {{ loading ? '查询中…' : `${rows.length} 行${elapsed ? ` · ${elapsed}` : ''}` }}
        </span>
      </div>

      <div ref="scrollRef" class="wb-result-body" @scroll="onScroll">
        <el-table v-if="columns.length" :data="rows" border size="small" height="100%">
          <el-table-column
            v-for="col in columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
        <el-empty v-else-if="!loading" :description="error || '暂无数据'" />
        <div v-if="hasMore" class="wb-more">向下滚动加载更多</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { previewDatasetDraft } from './card-attribute/useInsightBackend'
import { draftRequestForDataset } from './card-attribute/useInsight'
import type { DatasetConfig } from './card-attribute/useInsight'
import type { DatasetFilter } from '@/types'
import {
  extractApiParameters,
  extractDimensionParameters,
  extractFileParameters,
  extractScriptParameters,
  extractSqlParameters,
  mergeParameters,
  type ExtractedParameter,
} from '@/utils/parameter-extract'

const props = defineProps<{ dataset: DatasetConfig }>()
const emit = defineEmits<{ (e: 'close'): void }>()

/** 每页条数（滚动到底追加一页） */
const PAGE_SIZE = 50

const loading = ref(false)
const error = ref('')
const elapsed = ref('')
const columns = ref<string[]>([])
const rows = ref<Record<string, unknown>[]>([])
/**
 * 用存量筛选条件回填参数值 —— 这次换的是**产生它的界面**，不是**存它的结构**，
 * 所以已配好的筛选不能因为换了工作台就丢掉。
 */
function seedValues(ds: DatasetConfig): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const filter of ds.filters ?? []) {
    const field = (filter as { field?: string }).field
    if (!field) continue
    out[field] = (filter as { value?: unknown }).value
  }
  return out
}

const values = reactive<Record<string, unknown>>(seedValues(props.dataset))
const scrollRef = ref<HTMLElement | null>(null)

/* ── 类型判定（脚本是管道级的，不属于单个数据集的定义区） ── */
const isSql = computed(() => props.dataset.sourceType === 'jdbc')
const isApi = computed(() => props.dataset.sourceType === 'api')
const isFile = computed(() => props.dataset.sourceType === 'file')

/* ── 定义区（SQL 可编辑，改完自动重跑） ── */
const sql = ref(props.dataset.jdbc?.sql ?? '')
const apiHost = computed(() => props.dataset.api?.host ?? '')
const apiPath = computed(() => props.dataset.api?.path ?? '')

const definitionHint = computed(() => {
  if (isSql.value) return 'SQL 语句 · 可编辑'
  if (isApi.value) return '接口信息 · 只读'
  if (isFile.value) return '文件信息 · 只读'
  return '指标 / 维度 · 只读'
})

/* ── 参数自动提取 ── */
const parameters = computed<ExtractedParameter[]>(() => {
  const ds = props.dataset
  if (isSql.value) return extractSqlParameters(sql.value)
  if (isApi.value) {
    return extractApiParameters({ path: ds.api?.path, headers: ds.api?.headers, params: ds.api?.params })
  }
  if (isFile.value) return extractFileParameters()
  // Aloudata / 指标视图：维度即筛选项
  return extractDimensionParameters(ds.aloudata?.dims)
})

/** 参数是否已有值（空串 / null / 空数组都算没填） */
function hasValue(name: string): boolean {
  const raw = values[name]
  if (raw === undefined || raw === null) return false
  if (Array.isArray(raw)) return raw.length > 0
  return String(raw).trim() !== ''
}

/**
 * 占位符没填完时提前告知 —— 后端对缺失的 SQL 参数是**直接报错**的（`missing SQL parameter: xxx`）。
 * 与其让用户撞一句后端异常，不如在界面上先点出来。
 */
const pendingBackendHint = computed(() => {
  const missing = parameters.value
    .filter((param) => param.source === 'sql' && !hasValue(param.name))
    .map((param) => `:${param.name}`)
  if (!missing.length) return ''
  return `以下占位符未赋值，运行会失败：${missing.join('、')}`
})

/**
 * 占位符参数值 → request.parameters（绑进 SQL 内部）。
 * 只收 SQL / 接口的占位符；维度类走 filters —— 这是两条不同的下推通道，不能混。
 */
function toNamedParameters(): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const param of parameters.value) {
    if (param.source !== 'sql' && param.source !== 'api') continue
    if (!hasValue(param.name)) continue
    out[param.name] = values[param.name]
  }
  return out
}

/**
 * 参数值 → 后端 DatasetFilter（真正下推到查询）。
 *
 * 只有**维度类**参数能转成真实字段条件；SQL / 接口的占位符（`:start_date`、`{{x}}`）不是字段名，
 * 塞进 filters 会让后端按列名去找，属于错误用法，因此不下推 —— 其状态由 pendingBackendHint 如实告知。
 */
function toDatasetFilters(): DatasetFilter[] {
  const out: DatasetFilter[] = []
  for (const param of parameters.value) {
    if (param.source !== 'dimension') continue
    const raw = values[param.name]
    if (raw === undefined || raw === null || raw === '') continue
    if (Array.isArray(raw)) {
      if (!raw.length) continue
      out.push({
        field: param.name,
        role: 'dimension',
        operator: param.type === 'date_range' ? 'between' : 'in',
        value: raw,
      })
      continue
    }
    out.push({ field: param.name, role: 'dimension', operator: 'eq', value: raw })
  }
  return out
}

/**
 * 遗留条件：存量 filters 里字段名已不在当前可筛选项内的条目（维度被删了、或当初筛的是指标字段）。
 * 原则是**可见、可清理，绝不静默失效** —— 不让用户在不知情的情况下丢掉已配好的东西。
 */
const legacyFilters = computed(() => {
  const known = new Set(parameters.value.map((param) => param.name))
  return (props.dataset.filters ?? [])
    .map((filter) => {
      const raw = filter as unknown as { field?: string; operator?: string; op?: string; value?: unknown }
      return { field: raw.field ?? '', operator: raw.operator ?? raw.op ?? '', value: raw.value }
    })
    .filter((item) => item.field && !known.has(item.field))
})

/** 移除一条遗留条件，并立即用新的条件集重查 */
function removeLegacy(field: string): void {
  props.dataset.filters = (props.dataset.filters ?? []).filter(
    (filter) => (filter as { field?: string }).field !== field,
  )
  void run()
}

/** 是否还有下一页（上一页取满了才认为可能有） */
const hasMore = ref(false)

/**
 * 取数：reset=true 回到第一页（改定义 / 参数时触发），false 则以当前行数作 offset 追加下一页。
 *
 * 分页交给**服务端**（limit/offset），不再前端切片 —— 否则受控预览的单次上限会让人误以为
 * 「数据就这么多」，而实际是没往后取。
 */
async function fetchRows(reset: boolean): Promise<void> {
  loading.value = true
  error.value = ''
  const started = Date.now()
  try {
    const request = draftRequestForDataset({ ...props.dataset, jdbc: { ...props.dataset.jdbc, sql: sql.value } })
    if (!request) {
      error.value = '该类型数据集暂不支持取数'
      rows.value = []
      columns.value = []
      return
    }
    // 两条下推通道：维度类 → filters（按列追加外层谓词）；SQL / 接口占位符 → parameters（绑进 SQL 内部）
    request.filters = [...toDatasetFilters(), ...(legacyFilters.value as unknown as DatasetFilter[])]
    request.parameters = toNamedParameters()
    request.limit = PAGE_SIZE
    request.offset = reset ? 0 : rows.value.length

    const batch = await previewDatasetDraft(request)
    const nextRows = (batch.rows as Record<string, unknown>[] | null) ?? []
    rows.value = reset ? nextRows : [...rows.value, ...nextRows]
    if (reset) columns.value = nextRows.length ? Object.keys(nextRows[0]) : []
    hasMore.value = nextRows.length >= PAGE_SIZE
    elapsed.value = `${((Date.now() - started) / 1000).toFixed(1)}s`
    if (reset) {
      await nextTick()
      if (scrollRef.value) scrollRef.value.scrollTop = 0
    }
  } catch (e) {
    error.value = (e as Error)?.message || '查询失败'
    if (reset) {
      rows.value = []
      columns.value = []
    }
  } finally {
    loading.value = false
  }
}

async function run(): Promise<void> {
  await fetchRows(true)
}

/** 滚动到底部拉下一页 */
function onScroll(event: Event): void {
  const el = event.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight > 24) return
  if (!hasMore.value || loading.value) return
  void fetchRows(false)
}

/* ── 改完定义或参数，防抖自动重跑 ── */
let timer: ReturnType<typeof setTimeout> | null = null
watch(
  () => [sql.value, JSON.stringify(values)],
  () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => void run(), 600)
  },
)

void run()
</script>

<style scoped>
.data-workbench {
  /* 全屏覆盖：fixed 不依赖父级定位，避免受编辑器布局的 overflow 裁剪 */
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: var(--db-bg, var(--color-background-primary));
  overflow: hidden;
}
.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}
.wb-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}
.wb-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text);
}
.wb-source {
  font-size: 12px;
  color: var(--db-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-bottom: 1px solid var(--db-border);
}
.wb-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  flex-shrink: 0;
}
.wb-section-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--db-text);
}
.wb-section-hint {
  font-size: 12px;
  color: var(--db-text-muted);
}
.wb-definition {
  padding: 0 14px 12px;
  flex-shrink: 0;
}
.wb-code :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
}
.wb-kv {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--db-muted);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.wb-kv-row {
  display: flex;
  gap: 10px;
  font-size: 12px;
}
.wb-kv-row span {
  flex-shrink: 0;
  width: 68px;
  color: var(--db-text-muted);
}
.wb-kv-row code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-note {
  margin: 0 14px 10px;
}
.wb-param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
  padding: 0 14px 12px;
  flex-shrink: 0;
}
.wb-param-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary);
  margin-bottom: 5px;
}
.wb-param-type {
  color: var(--db-text-muted);
  opacity: 0.8;
}
.wb-param-req {
  color: var(--db-warning);
}
.wb-param-control {
  width: 100%;
}
.wb-legacy {
  margin: 0 14px 12px;
  border: 1px dashed var(--db-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.wb-legacy-title {
  font-size: 12px;
  color: var(--db-text-muted);
  margin-bottom: 8px;
}
.wb-legacy-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  font-size: 12px;
}
.wb-legacy-item code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--db-text);
}
.wb-legacy-op {
  color: var(--db-text-secondary);
}
.wb-legacy-val {
  flex: 1;
  min-width: 0;
  color: var(--db-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb-empty {
  padding: 0 14px 12px;
  font-size: 12px;
  color: var(--db-text-muted);
}
.wb-result {
  flex: 1;
  border-bottom: none;
}
.wb-result-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 14px 14px;
}
.wb-more {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  color: var(--db-text-muted);
}
</style>
