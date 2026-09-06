<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="500px"
    :title="t('metricQuery.title')"
    :before-close="handleClose"
    class="metric-query-drawer"
  >
    <template #header>
      <div class="mq-header">
        <div class="mq-header-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
        </div>
        <div class="mq-header-text">
          <span class="mq-header-title">{{ t('metricQuery.title') }}</span>
          <span class="mq-header-sub">{{ t('metricQuery.subtitle') }}</span>
        </div>
      </div>
    </template>

    <div class="mq-body">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="left" label-width="76px" size="default">
        <!-- ── 分区 1：数据设置 ── -->
        <section class="mq-section">
          <div class="mq-section-title">{{ t('metricQuery.sectionData') }}</div>
        <!-- 数据源 -->
        <el-form-item :label="t('metricQuery.datasource')" prop="datasourceId">
          <el-select
            v-model="localDatasourceId"
            :placeholder="t('metricQuery.selectDatasource')"
            filterable
            style="width: 100%"
            @change="handleDatasourceChange"
          >
            <el-option
              v-for="ds in datasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>

        <!-- 指标 -->
        <el-form-item :label="t('metricQuery.metrics')" prop="metrics">
          <el-select
            v-model="localMetrics"
            :placeholder="t('metricQuery.selectMetrics')"
            multiple
            filterable
            remote
            :remote-method="searchMetrics"
            :loading="metricsLoading"
            style="width: 100%"
            @change="handleMetricsChange"
          >
            <el-option
              v-for="m in metricsOptions"
              :key="m.metricName"
              :label="m.metricDisplayName || m.metricName"
              :value="m.metricName"
            >
              <span>{{ m.metricDisplayName || m.metricName }}</span>
              <span v-if="m.unit" class="mq-option-extra">{{ m.unit }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        </section>

        <!-- ── 分区 2：时间与维度 ── -->
        <section class="mq-section">
          <div class="mq-section-title">{{ t('metricQuery.sectionTime') }}</div>

        <!-- 时间范围 -->
        <el-form-item :label="t('metricQuery.timeRange')">
          <el-date-picker
            v-model="localTimeRange"
            type="daterange"
            :start-placeholder="t('metricQuery.startDate')"
            :end-placeholder="t('metricQuery.endDate')"
            style="width: 100%"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
          />
        </el-form-item>

        <!-- 时间粒度选择 -->
        <el-form-item :label="t('chart.timeGranularity')">
          <el-radio-group v-model="timeGranularity">
            <el-radio-button value="minute">{{ t('chart.granularityMinute') }}</el-radio-button>
            <el-radio-button value="hour">{{ t('chart.granularityHour') }}</el-radio-button>
            <el-radio-button value="day">{{ t('chart.granularityDay') }}</el-radio-button>
            <el-radio-button value="week">{{ t('chart.granularityWeek') }}</el-radio-button>
            <el-radio-button value="month">{{ t('chart.granularityMonth') }}</el-radio-button>
            <el-radio-button value="quarter">{{ t('chart.granularityQuarter') }}</el-radio-button>
            <el-radio-button value="year">{{ t('chart.granularityYear') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 维度 -->
        <el-form-item :label="t('metricQuery.dimensions')">
          <el-select
            v-model="localDimensions"
            :placeholder="t('metricQuery.selectDimensions')"
            multiple
            filterable
            remote
            :remote-method="searchDimensions"
            :loading="dimensionsLoading"
            style="width: 100%"
          >
            <el-option
              v-for="d in dimensionsOptions"
              :key="d.dimName"
              :label="d.dimDisplayName || d.dimName"
              :value="d.dimName"
            >
              <span>{{ d.dimDisplayName || d.dimName }}</span>
              <span v-if="d.isTimeDimension" class="mq-option-tag">{{ t('metricQuery.timeDim') }}</span>
            </el-option>
          </el-select>
          <div class="mq-field-hint">{{ t('metricQuery.dimHint') }}</div>
          <template #error>
            <span></span>
          </template>
        </el-form-item>
        </section>

        <!-- ── 分区 3：筛选与排序 ── -->
        <section class="mq-section">
          <div class="mq-section-title">{{ t('metricQuery.sectionFilter') }}</div>

        <!-- 筛选条件 -->
        <el-form-item :label="t('chart.fieldFilters')">
          <div class="mq-filter-list">
            <div
              v-for="(f, idx) in filterItems"
              :key="idx"
              class="mq-filter-row"
            >
              <el-select
                v-model="f.dim"
                size="small"
                filterable
                clearable
                :placeholder="t('chart.selectFilterDim')"
                style="width: 130px"
                @change="onFilterDimChange(f)"
              >
                <el-option
                  v-for="d in dimensionsOptions"
                  :key="d.dimName"
                  :label="d.dimDisplayName || d.dimName"
                  :value="d.dimName"
                />
              </el-select>
              <el-select
                v-model="f.op"
                size="small"
                :placeholder="t('chart.selectFilterOp')"
                style="width: 110px"
              >
                <el-option
                  v-for="op in getFilterOps(f.dim)"
                  :key="op.value"
                  :label="t(op.label)"
                  :value="op.value"
                />
              </el-select>
              <el-input
                v-model="f.value"
                size="small"
                :placeholder="t('chart.selectFilterValue')"
                style="flex: 1"
              />
              <el-button
                size="small"
                type="danger"
                text
                @click="removeFilterItem(idx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-button size="small" text type="primary" @click="addFilterItem">
            + {{ t('chart.addFilter') }}
          </el-button>
        </el-form-item>

        <!-- 排序条件 -->
        <el-form-item :label="t('chart.orderBy')">
          <div class="mq-order-list">
            <div
              v-for="(o, idx) in orderItems"
              :key="idx"
              class="mq-orderby-row"
            >
              <el-select
                v-model="o.col"
                size="small"
                filterable
                allow-create
                default-first-option
                clearable
                :placeholder="t('chart.selectOrderByCol')"
                style="flex: 1"
              >
                <el-option-group
                  v-if="orderByMetricOptions.length > 0"
                  :label="t('chart.orderByMetricGroup')"
                >
                  <el-option
                    v-for="m in orderByMetricOptions"
                    :key="m.name"
                    :label="m.displayName || m.name"
                    :value="m.name"
                  />
                </el-option-group>
                <el-option-group :label="t('chart.orderByTimeGroup')">
                  <el-option
                    :label="orderByTimeDimOption.label"
                    :value="orderByTimeDimOption.value"
                  />
                </el-option-group>
                <el-option-group
                  v-if="orderByDimOptions.length > 0"
                  :label="t('chart.orderByDimGroup')"
                >
                  <el-option
                    v-for="d in orderByDimOptions"
                    :key="d.dimName"
                    :label="d.dimDisplayName || d.dimName"
                    :value="d.dimName"
                  />
                </el-option-group>
              </el-select>
              <el-radio-group v-model="o.desc" size="small">
                <el-radio-button :value="false">{{ t('chart.asc') }}</el-radio-button>
                <el-radio-button :value="true">{{ t('chart.desc') }}</el-radio-button>
              </el-radio-group>
              <el-button
                size="small"
                type="danger"
                text
                @click="removeOrderItem(idx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-button size="small" text type="primary" @click="addOrderItem">
            + {{ t('chart.addOrder') }}
          </el-button>
        </el-form-item>

        <!-- 分页设置 -->
        <el-form-item :label="t('chart.pagination')">
          <div class="mq-pagination-row">
            <span class="mq-pagination-label">{{ t('chart.pageSize') }}</span>
            <el-input-number
              v-model="pageSize"
              :min="10"
              :max="500"
              :step="10"
              controls-position="right"
            />
            <span class="mq-pagination-label">{{ t('chart.pageNum') }}</span>
            <el-input-number
              v-model="pageNum"
              :min="1"
              controls-position="right"
            />
          </div>
        </el-form-item>
        </section>
      </el-form>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="mq-footer">
        <el-button @click="handleClose">{{ t('metricQuery.cancel') }}</el-button>
        <el-button
          type="primary"
          :disabled="!canQuery"
          @click="handleQuery"
        >{{ t('metricQuery.query') }}</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Datasource, AloudataSyncedMetric, AloudataSyncedDimension } from '@/types'
import * as semanticModelApi from '@/api/semantic-model'
import * as datasourceApi from '@/api/datasource'

defineOptions({
  name: 'MetricQueryDrawer',
})

const props = defineProps<{
  /** 是否显示抽屉 */
  visible: boolean
  /** 可选数据源列表 */
  datasources: Datasource[]
  /** 初始数据源 ID（从当前对话上下文传入） */
  initialDatasourceId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'query', message: string): void
}>()

const { t } = useI18n()

/** 表单实例 */
const formRef = ref<FormInstance>()

/** 默认每页条数 */
const DEFAULT_PAGE_SIZE = 100

/** 本地状态 */
const localDatasourceId = ref('')
const localMetrics = ref<string[]>([])
const localDimensions = ref<string[]>([])
const localTimeRange = ref<[string, string] | null>(null)
/** 时间粒度，默认日粒度 */
const timeGranularity = ref<'minute' | 'hour' | 'day' | 'week' | 'month' | 'quarter' | 'year'>('day')
const pageSize = ref(DEFAULT_PAGE_SIZE)
const pageNum = ref(1)

/** 表单数据模型（用于 el-form :model 绑定） */
const formData = reactive({
  datasourceId: localDatasourceId,
  metrics: localMetrics,
})

/** 表单校验规则：数据源和指标为必填 */
const formRules = computed<FormRules>(() => ({
  datasourceId: [{ required: true, message: t('metricQuery.selectDatasource'), trigger: 'change' }],
  metrics: [{ required: true, type: 'array', min: 1, message: t('metricQuery.selectMetrics'), trigger: 'change' }],
}))

/** 筛选操作符定义 */
type FilterOp = 'in' | 'not in' | '=' | '!=' | '>' | '<' | '>=' | '<=' | 'like'

interface FilterItem {
  dim: string
  op: FilterOp
  value: string
}

interface OrderItem {
  col: string
  desc: boolean
}

/** 筛选与排序状态 */
const filterItems = ref<FilterItem[]>([])
const orderItems = ref<OrderItem[]>([])

/** 指标/维度选项与加载状态 */
const metricsOptions = ref<AloudataSyncedMetric[]>([])
const dimensionsOptions = ref<AloudataSyncedDimension[]>([])
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)

/** 是否可查询（至少选了数据源和一个指标） */
const canQuery = computed(() => {
  return !!localDatasourceId.value && localMetrics.value.length > 0
})

/** 日期快捷选项 */
const dateShortcuts = computed(() => [
  { text: t('metricQuery.last7d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 6); return [start, end] } },
  { text: t('metricQuery.last30d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 29); return [start, end] } },
  { text: t('metricQuery.last90d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 89); return [start, end] } },
])

/** 搜索防抖定时器 */
let metricsSearchTimer: ReturnType<typeof setTimeout> | null = null
let dimensionsSearchTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 排序字段 - 指标分组
 */
const orderByMetricOptions = computed(() => {
  return localMetrics.value.map((name) => {
    const m = metricsOptions.value.find((item) => item.metricName === name)
    return { name, displayName: m?.metricDisplayName || name }
  })
})

/**
 * 排序字段 - 维度分组
 * 仅展示当前已选维度
 */
const orderByDimOptions = computed(() => {
  const selectedSet = new Set(localDimensions.value)
  return dimensionsOptions.value
    .filter((d) => selectedSet.has(d.dimName))
    .map((d) => ({ dimName: d.dimName, dimDisplayName: d.dimDisplayName || d.dimName }))
})

/** 粒度值 → i18n 标签映射 */
const granularityLabels: Record<string, string> = {
  minute: 'chart.granularityMinute',
  hour: 'chart.granularityHour',
  day: 'chart.granularityDay',
  week: 'chart.granularityWeek',
  month: 'chart.granularityMonth',
  quarter: 'chart.granularityQuarter',
  year: 'chart.granularityYear',
}

/**
 * 排序字段 - 时间维度
 */
const orderByTimeDimOption = computed(() => {
  const g = timeGranularity.value
  const label = `${t('chart.timeGranularity')}-${t(granularityLabels[g] || 'chart.granularityDay')}`
  return { label, value: `metric_time__${g}` }
})

/** 判断维度是否为数值类型 */
function isNumericDim(dimName: string): boolean {
  const dim = dimensionsOptions.value.find((d) => d.dimName === dimName)
  if (!dim) return false
  const dt = (dim.originDataType || '').toLowerCase()
  return ['int', 'long', 'double', 'float', 'decimal', 'bigint', 'numeric'].some((k) => dt.includes(k))
}

/** 根据维度数据类型返回可选操作符列表 */
function getFilterOps(dimName: string): Array<{ value: FilterOp; label: string }> {
  if (!dimName) {
    return [
      { value: 'in', label: 'chart.filterOpIn' },
      { value: 'not in', label: 'chart.filterOpNotIn' },
    ]
  }
  if (isNumericDim(dimName)) {
    return [
      { value: '=', label: 'chart.filterOpEq' },
      { value: '!=', label: 'chart.filterOpNe' },
      { value: '>', label: 'chart.filterOpGt' },
      { value: '<', label: 'chart.filterOpLt' },
      { value: '>=', label: 'chart.filterOpGe' },
      { value: '<=', label: 'chart.filterOpLe' },
      { value: 'in', label: 'chart.filterOpIn' },
      { value: 'not in', label: 'chart.filterOpNotIn' },
    ]
  }
  return [
    { value: 'in', label: 'chart.filterOpIn' },
    { value: 'not in', label: 'chart.filterOpNotIn' },
    { value: '=', label: 'chart.filterOpEq' },
    { value: '!=', label: 'chart.filterOpNe' },
    { value: 'like', label: 'chart.filterOpLike' },
  ]
}

/** 当筛选维度变化时，重置操作符为该类型默认值 */
function onFilterDimChange(f: FilterItem): void {
  const ops = getFilterOps(f.dim)
  if (!ops.some((o) => o.value === f.op)) {
    f.op = ops[0].value
  }
  f.value = ''
}

/** 添加筛选行 */
function addFilterItem(): void {
  filterItems.value.push({ dim: '', op: 'in', value: '' })
}

/** 移除筛选行 */
function removeFilterItem(idx: number): void {
  filterItems.value.splice(idx, 1)
}

/** 添加排序行 */
function addOrderItem(): void {
  orderItems.value.push({ col: '', desc: false })
}

/** 移除排序行 */
function removeOrderItem(idx: number): void {
  orderItems.value.splice(idx, 1)
}

/** 监听 visible 变化，打开时初始化、关闭时重置 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.initialDatasourceId && !localDatasourceId.value) {
        localDatasourceId.value = props.initialDatasourceId
        loadMetrics()
        loadDimensions()
      }
    } else {
      // 关闭时重置状态
      localDatasourceId.value = ''
      localMetrics.value = []
      localDimensions.value = []
      localTimeRange.value = null
      timeGranularity.value = 'day'
      filterItems.value = []
      orderItems.value = []
      pageNum.value = 1
      pageSize.value = DEFAULT_PAGE_SIZE
      metricsOptions.value = []
      dimensionsOptions.value = []
    }
  },
)

/** 数据源变更 */
function handleDatasourceChange(): void {
  localMetrics.value = []
  localDimensions.value = []
  filterItems.value = []
  orderItems.value = []
  metricsOptions.value = []
  dimensionsOptions.value = []
  if (localDatasourceId.value) {
    loadMetrics()
    loadDimensions()
  }
}

/** 指标变更时重新加载关联维度 */
function handleMetricsChange(): void {
  if (localDatasourceId.value && localMetrics.value.length > 0) {
    loadDimensions()
  }
}

/** 加载指标选项 */
async function loadMetrics(keyword?: string): Promise<void> {
  if (!localDatasourceId.value) {
    return
  }
  metricsLoading.value = true
  try {
    const res = await semanticModelApi.pageAloudataMetrics(localDatasourceId.value, {
      pageNumber: 1,
      pageSize: 50,
      keyword: keyword?.trim() || undefined,
    })
    const data = (res as any) || { records: [] }
    const records: AloudataSyncedMetric[] = data.records || []
    // 合并已选指标，避免远程搜索后已选项 label 丢失
    const existingNames = new Set(records.map((m) => m.metricName))
    for (const name of localMetrics.value) {
      if (!existingNames.has(name)) {
        const cached = metricsOptions.value.find((m) => m.metricName === name)
        if (cached) {
          records.unshift(cached)
        }
      }
    }
    metricsOptions.value = records
  } catch (e) {
    console.error('[MetricQueryDrawer] load metrics error:', e)
    metricsOptions.value = []
  } finally {
    metricsLoading.value = false
  }
}

/** 加载维度选项（基于已选指标关联维度） */
async function loadDimensions(keyword?: string): Promise<void> {
  if (!localDatasourceId.value) {
    return
  }
  dimensionsLoading.value = true
  try {
    let records: AloudataSyncedDimension[] = []
    if (localMetrics.value.length > 0) {
      const res = await datasourceApi.listMetricsDimensionDetails(
        localDatasourceId.value,
        localMetrics.value,
        keyword?.trim() || undefined,
      )
      records = (res as unknown as AloudataSyncedDimension[]) || []
    } else {
      const res = await semanticModelApi.pageAloudataDimensions(localDatasourceId.value, {
        pageNumber: 1,
        pageSize: 50,
        keyword: keyword?.trim() || undefined,
      })
      const data = (res as any) || { records: [] }
      records = data.records || []
    }
    // 合并已选维度
    const existingNames = new Set(records.map((d) => d.dimName))
    for (const name of localDimensions.value) {
      if (!existingNames.has(name)) {
        const cached = dimensionsOptions.value.find((d) => d.dimName === name)
        if (cached) {
          records.unshift(cached)
        }
      }
    }
    dimensionsOptions.value = records
  } catch (e) {
    console.error('[MetricQueryDrawer] load dimensions error:', e)
    dimensionsOptions.value = []
  } finally {
    dimensionsLoading.value = false
  }
}

/** 远程搜索指标（防抖 300ms） */
function searchMetrics(query: string): void {
  if (metricsSearchTimer) {
    clearTimeout(metricsSearchTimer)
  }
  metricsSearchTimer = setTimeout(() => {
    loadMetrics(query.trim() || undefined)
  }, 300)
}

/** 远程搜索维度（防抖 300ms） */
function searchDimensions(query: string): void {
  if (dimensionsSearchTimer) {
    clearTimeout(dimensionsSearchTimer)
  }
  dimensionsSearchTimer = setTimeout(() => {
    loadDimensions(query.trim() || undefined)
  }, 300)
}

/**
 * 构建筛选条件字符串数组
 * 将每个 FilterItem 转换为 Aloudata API filters 格式
 */
function buildFilters(): string[] {
  const result: string[] = []
  for (const f of filterItems.value) {
    if (!f.dim || !f.value.trim()) continue
    const dim = f.dim
    const rawVal = f.value.trim()
    switch (f.op) {
      case 'in': {
        const vals = rawVal.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
        const formatted = vals.map((v) => `"${v}"`).join(',')
        result.push(`[${dim}] in (${formatted})`)
        break
      }
      case 'not in': {
        const vals = rawVal.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
        const formatted = vals.map((v) => `"${v}"`).join(',')
        result.push(`[${dim}] not in (${formatted})`)
        break
      }
      case '=':
        result.push(`[${dim}] = "${rawVal}"`)
        break
      case '!=':
        result.push(`[${dim}] != "${rawVal}"`)
        break
      case '>':
        result.push(`[${dim}] > ${rawVal}`)
        break
      case '<':
        result.push(`[${dim}] < ${rawVal}`)
        break
      case '>=':
        result.push(`[${dim}] >= ${rawVal}`)
        break
      case '<=':
        result.push(`[${dim}] <= ${rawVal}`)
        break
      case 'like':
        result.push(`[${dim}] like "%${rawVal}%"`)
        break
    }
  }
  return result
}

/** 构建查询消息文本 */
function buildQueryMessage(): string {
  const parts: string[] = []

  // 指标英文名
  parts.push(`${t('metricQuery.metricNames')}：${localMetrics.value.join('、')}`)

  // 指标中文名
  const metricDisplayNames = localMetrics.value.map((name) => {
    const m = metricsOptions.value.find((item) => item.metricName === name)
    return m?.metricDisplayName || name
  })
  parts.push(`${t('metricQuery.metricDisplayNames')}：${metricDisplayNames.join('、')}`)

  // 时间粒度
  const gLabel = t(granularityLabels[timeGranularity.value] || 'chart.granularityDay')
  parts.push(`${t('chart.timeGranularity')}：${gLabel}`)

  // 维度
  if (localDimensions.value.length > 0) {
    const dimLabels = localDimensions.value.map((name) => {
      const d = dimensionsOptions.value.find((item) => item.dimName === name)
      return d?.dimDisplayName || name
    })
    parts.push(`${t('metricQuery.queryDimensions')}：${dimLabels.join('、')}`)
  }

  // 时间范围
  if (localTimeRange.value && localTimeRange.value[0] && localTimeRange.value[1]) {
    parts.push(`${t('metricQuery.queryTimeRange')}：${localTimeRange.value[0]} ~ ${localTimeRange.value[1]}`)
  }

  // 筛选条件
  const filters = buildFilters()
  if (filters.length > 0) {
    parts.push(`${t('metricQuery.queryFilters')}：${filters.join('；')}`)
  }

  // 排序条件
  const orderParts: string[] = []
  for (const o of orderItems.value) {
    if (!o.col) continue
    const label = getOrderByLabel(o.col)
    orderParts.push(`${label} ${o.desc ? t('chart.desc') : t('chart.asc')}`)
  }
  if (orderParts.length > 0) {
    parts.push(`${t('chart.orderBy')}：${orderParts.join('、')}`)
  }

  // 分页
  parts.push(`${t('chart.pagination')}：${t('chart.pageSize')} ${pageSize.value}，${t('chart.pageNum')} ${pageNum.value}`)

  return parts.join('\n')
}

/** 获取排序字段的显示标签 */
function getOrderByLabel(col: string): string {
  // 检查是否为时间维度
  const timeDim = `metric_time__${timeGranularity.value}`
  if (col === timeDim) {
    return orderByTimeDimOption.value.label
  }
  // 检查是否为指标
  const metric = orderByMetricOptions.value.find((m) => m.name === col)
  if (metric) {
    return metric.displayName || metric.name
  }
  // 检查是否为维度
  const dim = orderByDimOptions.value.find((d) => d.dimName === col)
  if (dim) {
    return dim.dimDisplayName || dim.dimName
  }
  return col
}

/** 执行查询 */
function handleQuery(): void {
  if (!canQuery.value) {
    return
  }
  const message = buildQueryMessage()
  emit('query', message)
  handleClose()
}

/** 关闭抽屉 */
function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style scoped>
/* ── 头部自定义内容（插槽元素带 scoped 属性，可正常命中） ── */
.mq-header {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.mq-header-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--main-orange, #4176E6) 0%, color-mix(in srgb, var(--main-orange, #4176E6) 60%, #fff) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--main-orange, #4176E6) 30%, transparent);
}

.mq-header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mq-header-title {
  font-size: 15.5px;
  font-weight: 700;
  color: var(--theme-text, #1a2233);
  line-height: 1.3;
}

.mq-header-sub {
  font-size: 11.5px;
  color: var(--theme-text-muted, #8a94a6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 内容区 ── */
.mq-body {
  padding: 12px;
  overflow-y: auto;
}

/* ── 分区卡片 ── */
.mq-section {
  background: var(--theme-surface, #fff);
  border: 1px solid var(--theme-border, rgba(26, 34, 51, 0.08));
  border-radius: 10px;
  padding: 12px 14px 4px;
  margin-bottom: 10px;
}

.mq-section:last-child {
  margin-bottom: 0;
}

.mq-section-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-secondary, #46536b);
  margin-bottom: 12px;
  letter-spacing: 0.2px;
}

.mq-section-title::before {
  content: "";
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--main-orange, #4176E6);
}

/* ── 表单项 ── */
.mq-body :deep(.el-form-item) {
  margin-bottom: 12px;
}

.mq-body :deep(.el-form-item:last-child) {
  margin-bottom: 4px;
}

.mq-body :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-secondary, #606266);
  padding-right: 10px;
}

.mq-body :deep(.el-form-item.is-required .el-form-item__label::before) {
  color: var(--el-color-danger);
}

/* 字段下方提示文本（维度提示等） */
.mq-field-hint {
  width: 100%;
  font-size: 11.5px;
  color: var(--theme-text-muted, #8a94a6);
  margin-top: 5px;
  line-height: 1.4;
}

/* ── 下拉选项装饰 ── */
.mq-option-extra {
  float: right;
  color: var(--theme-text-muted, #999);
  font-size: 12px;
}

.mq-option-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-size: 10px;
  line-height: 16px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--main-orange, #4176E6) 14%, transparent);
  color: var(--main-orange, #4176E6);
}

/* ── 筛选 / 排序行 ── */
.mq-filter-list,
.mq-order-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-bottom: 6px;
}

.mq-filter-row,
.mq-orderby-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ── 分页行 ── */
.mq-pagination-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.mq-pagination-row :deep(.el-input-number) {
  width: 90px;
}

.mq-pagination-label {
  font-size: 13px;
  color: var(--theme-text-secondary, #909399);
  white-space: nowrap;
}

/* ── 底部操作栏内容 ── */
.mq-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
}
</style>

<style>
/* el-drawer teleport 到 body，scoped 选择器无法命中其内部节点，
   故用非 scoped 块 + 自定义类名限定作用域 */
.metric-query-drawer {
  --el-color-primary: var(--main-orange, #4176E6);
  --el-color-primary-light-3: color-mix(in srgb, var(--main-orange, #4176E6) 70%, var(--theme-surface, #fff));
  --el-color-primary-light-5: color-mix(in srgb, var(--main-orange, #4176E6) 50%, var(--theme-surface, #fff));
  --el-color-primary-light-7: color-mix(in srgb, var(--main-orange, #4176E6) 30%, var(--theme-surface, #fff));
  --el-color-primary-light-8: color-mix(in srgb, var(--main-orange, #4176E6) 20%, var(--theme-surface, #fff));
  --el-color-primary-light-9: color-mix(in srgb, var(--main-orange, #4176E6) 10%, var(--theme-surface, #fff));
  --el-color-primary-dark-2: color-mix(in srgb, var(--main-orange, #4176E6) 85%, #000);
  background: var(--theme-bg, #f6f8fb);
}

.metric-query-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 14px 16px;
  border-bottom: 1px solid var(--theme-border, #e5e7eb);
  background: var(--theme-surface, #fff);
}

.metric-query-drawer .el-drawer__body {
  padding: 0;
  background: var(--theme-bg, #f6f8fb);
}

.metric-query-drawer .el-drawer__footer {
  padding: 0;
  border-top: 1px solid var(--theme-border, #e5e7eb);
  background: var(--theme-surface, #fff);
}
</style>
