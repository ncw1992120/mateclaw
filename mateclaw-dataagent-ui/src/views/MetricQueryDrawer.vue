<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="560px"
    :title="t('metricQuery.title')"
    :before-close="handleClose"
    class="metric-query-drawer"
  >
    <div class="mq-body">
      <!-- 数据源 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('metricQuery.datasource') }}</label>
        <el-select
          v-model="localDatasourceId"
          :placeholder="t('metricQuery.selectDatasource')"
          size="default"
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
      </div>

      <!-- 指标 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('metricQuery.metrics') }}</label>
        <el-select
          v-model="localMetrics"
          :placeholder="t('metricQuery.selectMetrics')"
          size="default"
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
      </div>

      <!-- 时间范围 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('metricQuery.timeRange') }}</label>
        <el-date-picker
          v-model="localTimeRange"
          type="daterange"
          :start-placeholder="t('metricQuery.startDate')"
          :end-placeholder="t('metricQuery.endDate')"
          size="default"
          style="width: 100%"
          value-format="YYYY-MM-DD"
          :shortcuts="dateShortcuts"
        />
      </div>

      <!-- 时间粒度选择 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('chart.timeGranularity') }}</label>
        <el-radio-group v-model="timeGranularity" size="default">
          <el-radio-button value="minute">{{ t('chart.granularityMinute') }}</el-radio-button>
          <el-radio-button value="hour">{{ t('chart.granularityHour') }}</el-radio-button>
          <el-radio-button value="day">{{ t('chart.granularityDay') }}</el-radio-button>
          <el-radio-button value="week">{{ t('chart.granularityWeek') }}</el-radio-button>
          <el-radio-button value="month">{{ t('chart.granularityMonth') }}</el-radio-button>
          <el-radio-button value="quarter">{{ t('chart.granularityQuarter') }}</el-radio-button>
          <el-radio-button value="year">{{ t('chart.granularityYear') }}</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 维度 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('metricQuery.dimensions') }}</label>
        <el-select
          v-model="localDimensions"
          :placeholder="t('metricQuery.selectDimensions')"
          size="default"
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
        <div class="mq-dim-tip">{{ t('chart.dimTip') }}</div>
      </div>

      <!-- 筛选条件（支持多维度筛选，按数据类型区分操作符） -->
      <div class="mq-section">
        <label class="mq-label">{{ t('chart.fieldFilters') }}</label>
        <div class="mq-filter-list">
          <div
            v-for="(f, idx) in filterItems"
            :key="idx"
            class="mq-filter-row"
          >
            <!-- 维度选择 -->
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

            <!-- 操作符选择（根据数据类型） -->
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

            <!-- 筛选值输入 -->
            <el-input
              v-model="f.value"
              size="small"
              :placeholder="t('chart.selectFilterValue')"
              style="flex: 1"
            />

            <!-- 删除按钮 -->
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
      </div>

      <!-- 排序条件（支持多字段排序） -->
      <div class="mq-section">
        <label class="mq-label">{{ t('chart.orderBy') }}</label>
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
      </div>

      <!-- 分页设置 -->
      <div class="mq-section">
        <label class="mq-label">{{ t('chart.pagination') }}</label>
        <div class="mq-pagination-row">
          <span class="mq-pagination-label">{{ t('chart.pageSize') }}</span>
          <el-input-number
            v-model="pageSize"
            :min="10"
            :max="500"
            :step="10"
            size="default"
            controls-position="right"
          />
          <span class="mq-pagination-label">{{ t('chart.pageNum') }}</span>
          <el-input-number
            v-model="pageNum"
            :min="1"
            size="default"
            controls-position="right"
          />
        </div>
      </div>
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
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
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
.metric-query-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border, #e5e7eb);
}

.metric-query-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.mq-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.mq-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mq-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-primary);
}

.mq-option-extra {
  float: right;
  color: var(--theme-text-muted, #999);
  font-size: 12px;
}

.mq-option-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  border-radius: 3px;
  background: var(--main-orange, #4176E6);
  color: #fff;
  opacity: 0.8;
}

.mq-filter-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mq-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mq-order-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mq-orderby-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mq-pagination-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mq-pagination-label {
  font-size: 13px;
  color: var(--el-text-secondary);
  white-space: nowrap;
}

.mq-dim-tip {
  font-size: 12px;
  color: var(--el-text-secondary);
  margin-top: 4px;
}

.mq-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--theme-border, #e5e7eb);
}
</style>
