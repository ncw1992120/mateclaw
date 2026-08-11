<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="560px"
    :title="t('chart.customQuery')"
    :before-close="handleClose"
    class="chart-metric-query-drawer"
  >
    <div class="cmq-body">
      <!-- 指标（只读展示） -->
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.fieldMetric') }}</label>
        <div class="cmq-metrics-tags">
          <el-tag v-for="m in metrics" :key="m.name" size="small" type="info">
            {{ m.displayName || m.name }}
          </el-tag>
        </div>
      </div>

      <!-- 时间范围 -->
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.fieldTimeRange') }}</label>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          size="default"
          :start-placeholder="t('metricQuery.startDate')"
          :end-placeholder="t('metricQuery.endDate')"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </div>

      <!-- 时间粒度选择（默认包含 metric_time 维度） -->
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.timeGranularity') }}</label>
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

      <!-- 维度选择（支持自定义输入） -->
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.fieldDimensions') }}</label>
        <el-select
          v-model="selectedDims"
          multiple
          filterable
          allow-create
          default-first-option
          :loading="dimsLoading"
          :placeholder="t('chart.selectDimensions')"
          style="width: 100%"
        >
          <el-option
            v-for="d in dimOptions"
            :key="d.dimName"
            :label="d.dimDisplayName || d.dimName"
            :value="d.dimName"
          />
        </el-select>
        <div class="cmq-dim-tip">{{ t('chart.dimTip') }}</div>
      </div>

      <!-- 筛选条件（支持多维度筛选） -->
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.fieldFilters') }}</label>
        <div class="cmq-filter-list">
          <div
            v-for="(f, idx) in filterItems"
            :key="idx"
            class="cmq-filter-row"
          >
            <!-- 维度选择 -->
            <el-select
              v-model="f.dim"
              size="small"
              filterable
              allow-create
              default-first-option
              clearable
              :placeholder="t('chart.selectFilterDim')"
              style="width: 130px"
              @change="onFilterDimChange(f)"
            >
              <el-option
                v-for="d in dimOptions"
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
              v-if="isNumericFilter(f.dim)"
              v-model="f.value"
              size="small"
              :placeholder="t('chart.selectFilterValue')"
              style="flex: 1"
            />
            <el-input
              v-else
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
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.orderBy') }}</label>
        <div class="cmq-order-list">
          <div
            v-for="(o, idx) in orderItems"
            :key="idx"
            class="cmq-orderby-row"
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
      <div class="cmq-section">
        <label class="cmq-label">{{ t('chart.pagination') }}</label>
        <div class="cmq-pagination-row">
          <span class="cmq-pagination-label">{{ t('chart.pageSize') }}</span>
          <el-input-number
            v-model="pageSize"
            :min="10"
            :max="500"
            :step="10"
            size="default"
            controls-position="right"
          />
          <span class="cmq-pagination-label">{{ t('chart.pageNum') }}</span>
          <el-input-number
            v-model="pageNum"
            :min="1"
            size="default"
            controls-position="right"
          />
        </div>
      </div>

      <!-- 查询按钮 -->
      <div class="cmq-section">
        <el-button type="primary" :loading="loading" @click="executeQuery">
          {{ t('chart.executeQuery') }}
        </el-button>
      </div>

      <!-- 查询结果 -->
      <div v-if="resultColumns.length > 0" class="cmq-result">
        <div class="cmq-result-header">
          <span>{{ t('chart.queryResult') }}（{{ resultRows.length }}）</span>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="table">{{ t('chart.tableView') }}</el-radio-button>
            <el-radio-button value="chart">{{ t('chart.chartView') }}</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 表格视图 -->
        <div v-if="viewMode === 'table'" class="cmq-table-wrap">
          <el-table :data="resultRows" border size="small" max-height="400">
            <el-table-column
              v-for="col in resultColumns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
        </div>

        <!-- 图表视图 -->
        <div v-if="viewMode === 'chart'" class="cmq-chart-wrap">
          <div class="cmq-chart-controls">
            <el-select v-model="chartType" size="small" style="width: 140px">
              <el-option
                v-for="ct in CHART_TYPES"
                :key="ct.key"
                :label="ct.label"
                :value="ct.key"
              />
            </el-select>
            <el-select
              v-if="!noAxisChartTypes.has(chartType) && resultColumns.length > 1"
              v-model="xAxisCol"
              size="small"
              style="width: 160px"
              :placeholder="t('chart.selectXAxis')"
            >
              <el-option v-for="col in dimensionColumns" :key="col" :label="col" :value="col" />
            </el-select>
          </div>
          <div ref="chartRef" class="cmq-chart-container" />
        </div>
      </div>

      <!-- 空结果 / 错误提示 -->
      <div v-if="errorMsg" class="cmq-result-empty">{{ errorMsg }}</div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import {
  BarChart, LineChart, PieChart, ScatterChart, EffectScatterChart,
  RadarChart, HeatmapChart, BoxplotChart, CandlestickChart,
  FunnelChart, GaugeChart, SankeyChart, ThemeRiverChart,
  PictorialBarChart, GraphChart, TreemapChart, SunburstChart,
  ParallelChart, TreeChart, MapChart, LinesChart,
} from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  RadarComponent, VisualMapComponent, GeoComponent, PolarComponent,
  ParallelComponent, CalendarComponent, GraphicComponent, DatasetComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import * as datasourceApi from '@/api/datasource'
import * as semanticModelApi from '@/api/semantic-model'
import type { ChartMetricItem } from '@/api/chat'
import type { AloudataSyncedDimension } from '@/types'

echarts.use([
  BarChart, LineChart, PieChart, ScatterChart, EffectScatterChart,
  RadarChart, HeatmapChart, BoxplotChart, CandlestickChart,
  FunnelChart, GaugeChart, SankeyChart, ThemeRiverChart,
  PictorialBarChart, GraphChart, TreemapChart, SunburstChart,
  ParallelChart, TreeChart, MapChart, LinesChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  RadarComponent, VisualMapComponent, GeoComponent, PolarComponent,
  ParallelComponent, CalendarComponent, GraphicComponent, DatasetComponent,
  CanvasRenderer,
])

/** 支持的图表类型列表（与问数展示一致） */
const CHART_TYPES = [
  { key: 'bar', label: '柱状图' },
  { key: 'line', label: '折线图' },
  { key: 'pie', label: '饼图' },
  { key: 'area', label: '面积图' },
  { key: 'scatter', label: '散点图' },
  { key: 'effectScatter', label: '涟漪特效散点图' },
  { key: 'candlestick', label: 'K线图' },
  { key: 'radar', label: '雷达图' },
  { key: 'heatmap', label: '热力图' },
  { key: 'boxplot', label: '箱线图' },
  { key: 'funnel', label: '漏斗图' },
  { key: 'gauge', label: '仪表盘' },
  { key: 'sankey', label: '桑基图' },
  { key: 'themeRiver', label: '主题河流图' },
  { key: 'pictorialBar', label: '象形柱图' },
  { key: 'graph', label: '关系图' },
  { key: 'treemap', label: '矩形树图' },
  { key: 'sunburst', label: '旭日图' },
  { key: 'parallel', label: '平行坐标系' },
  { key: 'tree', label: '树图' },
] as const

type ChartTypeKey = (typeof CHART_TYPES)[number]['key']

/** 默认每页条数 */
const DEFAULT_PAGE_SIZE = 100

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

interface Props {
  visible: boolean
  datasourceId: string | number | null
  metrics: ChartMetricItem[]
}

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const props = defineProps<Props>()
const { t } = useI18n()

const selectedDims = ref<string[]>([])
const dimOptions = ref<AloudataSyncedDimension[]>([])
const dimsLoading = ref(false)
const dateRange = ref<[string, string] | null>(null)
/** 时间粒度，默认日粒度 */
const timeGranularity = ref<'hour' | 'minute' | 'day' | 'week' | 'month' | 'quarter' | 'year'>('day')
const loading = ref(false)
const resultColumns = ref<string[]>([])
const resultRows = ref<Array<Record<string, unknown>>>([])
const errorMsg = ref('')
const viewMode = ref<'table' | 'chart'>('table')
const chartType = ref<ChartTypeKey>('bar')
const xAxisCol = ref('')
const chartRef = ref<HTMLElement>()

/** 无坐标轴的图表类型（不需要 X 轴选择） */
const noAxisChartTypes = new Set<ChartTypeKey>(['pie', 'funnel', 'gauge', 'treemap', 'sunburst', 'graph', 'tree', 'sankey', 'themeRiver'])
let chartInstance: echarts.ECharts | null = null

/** 筛选与排序状态 */
const filterItems = ref<FilterItem[]>([])
const orderItems = ref<OrderItem[]>([])
const pageNum = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

/** 维度列名（非数值列，用于 X 轴选择） */
const dimensionColumns = computed(() => {
  if (resultRows.value.length === 0) return []
  const firstRow = resultRows.value[0]
  return resultColumns.value.filter((col) => {
    const val = firstRow[col]
    return typeof val === 'string' || isNaN(Number(val))
  })
})

/** 指标列名（数值列，用于 Y 轴/系列） */
const metricColumns = computed(() => {
  return resultColumns.value.filter((c) => !dimensionColumns.value.includes(c))
})

/**
 * 排序字段 - 指标分组
 * 仅展示当前已选指标，对应 Aloudata API orders 参数中按指标排序的场景。
 */
const orderByMetricOptions = computed(() => {
  return props.metrics.map((m) => ({ name: m.name, displayName: m.displayName || m.name }))
})

/**
 * 排序字段 - 维度分组
 * 仅展示当前已选维度（dimOptions 中过滤出 selectedDims 选中的项），
 * 对应 Aloudata API orders 参数中按维度排序的场景。
 */
const orderByDimOptions = computed(() => {
  const selectedSet = new Set(selectedDims.value)
  return dimOptions.value
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
 * 根据当前选择的时间粒度，生成对应的 metric_time__{granularity} 排序选项。
 */
const orderByTimeDimOption = computed(() => {
  const g = timeGranularity.value
  const label = `${t('chart.timeGranularity')}-${t(granularityLabels[g] || 'chart.granularityDay')}`
  return { label, value: `metric_time__${g}` }
})

/** 判断维度是否为数值类型（根据 originDataType） */
function isNumericDim(dimName: string): boolean {
  const dim = dimOptions.value.find((d) => d.dimName === dimName)
  if (!dim) return false
  const dt = (dim.originDataType || '').toLowerCase()
  return ['int', 'long', 'double', 'float', 'decimal', 'bigint', 'numeric'].some((k) => dt.includes(k))
}

/** 判断筛选维度是否为数值类型 */
function isNumericFilter(dimName: string): boolean {
  return isNumericDim(dimName)
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

/** 初始化表单 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      // 重置字段
      selectedDims.value = []
      dateRange.value = null
      filterItems.value = []
      orderItems.value = []
      resultColumns.value = []
      resultRows.value = []
      errorMsg.value = ''
      viewMode.value = 'table'
      pageNum.value = 1
      pageSize.value = DEFAULT_PAGE_SIZE
      // 从后端加载维度
      void loadDimensions()
    } else {
      // 关闭时销毁图表
      if (chartInstance) {
        chartInstance.dispose()
        chartInstance = null
      }
    }
  },
)

/** 加载维度选项：已选指标时查关联维度，否则查全部维度 */
async function loadDimensions(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  dimsLoading.value = true
  try {
    const metricNames = props.metrics.map((m) => m.name)
    if (metricNames.length > 0) {
      const res = await datasourceApi.listMetricsDimensionDetails(props.datasourceId, metricNames)
      dimOptions.value = (res as unknown as AloudataSyncedDimension[]) || []
    } else {
      const res = await semanticModelApi.pageAloudataDimensions(props.datasourceId, {
        pageNumber: 1,
        pageSize: 200,
      })
      const data = (res as any) || { records: [] }
      dimOptions.value = data.records || []
    }
  } catch (err) {
    console.error('[ChartMetricQueryDrawer] 加载维度失败:', err)
    dimOptions.value = []
  } finally {
    dimsLoading.value = false
  }
}

/**
 * 构建筛选条件字符串数组
 * 将每个 FilterItem 转换为 Aloudata API filters 格式，如：
 * - in: [dim] in ("v1","v2")
 * - =: [dim] = "v1"
 * - like: [dim] like "%v1%"
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

/** 执行查询 */
async function executeQuery(): Promise<void> {
  if (!props.datasourceId || props.metrics.length === 0) {
    return
  }

  loading.value = true
  errorMsg.value = ''
  resultColumns.value = []
  resultRows.value = []

  try {
    const metricNames = props.metrics.map((m) => m.name)
    
    // 构建维度列表：默认包含 metric_time__{granularity}
    const timeDim = `metric_time__${timeGranularity.value}`
    const allDimensions = [timeDim, ...selectedDims.value]
    
    let timeConstraint: string | undefined
    if (dateRange.value && dateRange.value[0] && dateRange.value[1]) {
      timeConstraint = `[${timeDim}] BETWEEN ("${dateRange.value[0]}","${dateRange.value[1]}")`
    }

    // 构建筛选条件
    const filters = buildFilters()

    // 构建排序条件（字段名 orders 与 Aloudata API 5.7 节一致）
    const orders: Array<Record<string, 'asc' | 'desc'>> | undefined = orderItems.value
      .filter((o) => o.col)
      .map((o) => ({ [o.col]: o.desc ? 'desc' : 'asc' }))
    const ordersParam = orders && orders.length > 0 ? orders : undefined

    const res = await datasourceApi.queryMetricData(props.datasourceId, {
      metrics: metricNames,
      dimensions: allDimensions,
      filters: filters.length > 0 ? filters : undefined,
      timeConstraint,
      orders: ordersParam,
      limit: pageSize.value,
      offset: (pageNum.value - 1) * pageSize.value,
    })

    if (!res || !res.success || !res.data) {
      errorMsg.value = res?.errorMsg || t('chart.noChartData')
      return
    }

    // 解析数据：优先行式，回退列式转换
    const data = res.data

    // 构建期望的列顺序：时间维度 → 业务维度 → 指标
    const expectedOrder = [...allDimensions, ...metricNames]

    if (data.rows && data.rows.length > 0) {
      // 收集所有 key
      const keySet = new Set<string>()
      for (const row of data.rows) {
        for (const key of Object.keys(row)) {
          keySet.add(key)
        }
      }
      // 按期望顺序排列，未在期望列表中的列追加到末尾
      const ordered: string[] = []
      for (const col of expectedOrder) {
        if (keySet.has(col)) {
          ordered.push(col)
          keySet.delete(col)
        }
      }
      ordered.push(...Array.from(keySet))
      resultColumns.value = ordered
      resultRows.value = data.rows
    } else if (data.columns && Object.keys(data.columns).length > 0) {
      const allKeys = Object.keys(data.columns)
      const keySet = new Set(allKeys)
      // 按期望顺序排列
      const ordered: string[] = []
      for (const col of expectedOrder) {
        if (keySet.has(col)) {
          ordered.push(col)
          keySet.delete(col)
        }
      }
      ordered.push(...Array.from(keySet))
      const cols = ordered
      const maxLen = Math.max(0, ...cols.map((c) => data.columns[c]?.length || 0))
      const rows: Array<Record<string, unknown>> = []
      for (let i = 0; i < maxLen; i++) {
        const row: Record<string, unknown> = {}
        for (const col of cols) {
          const cv = data.columns[col]?.[i]
          row[col] = cv?.value ?? ''
        }
        rows.push(row)
      }
      resultColumns.value = cols
      resultRows.value = rows
    }

    if (resultRows.value.length === 0) {
      errorMsg.value = t('chart.noChartData')
    } else {
      // 默认选择第一个维度列作为 X 轴
      if (dimensionColumns.value.length > 0 && !xAxisCol.value) {
        xAxisCol.value = dimensionColumns.value[0]
      }
    }
  } catch (err) {
    console.error('[ChartMetricQueryDrawer] 查询失败:', err)
    errorMsg.value = t('chart.metricError')
  } finally {
    loading.value = false
  }
}

/** 切换到图表视图时渲染 */
watch([viewMode, chartType, xAxisCol, resultRows], () => {
  if (viewMode.value === 'chart' && resultRows.value.length > 0) {
    nextTick(() => {
      renderChart()
    })
  }
})

/** 渲染图表 */
function renderChart(): void {
  if (!chartRef.value) {
    return
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const xCol = xAxisCol.value || dimensionColumns.value[0] || resultColumns.value[0]
  const yCols = metricColumns.value
  const ct = chartType.value
  const xData = resultRows.value.map((row) => String(row[xCol] ?? ''))

  // 饼图
  if (ct === 'pie') {
    const pieData = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{ type: 'pie', radius: '60%', data: pieData }],
    }, true)
    return
  }

  // 漏斗图
  if (ct === 'funnel') {
    const funnelData = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{ type: 'funnel', data: funnelData }],
    }, true)
    return
  }

  // 仪表盘
  if (ct === 'gauge') {
    const val = resultRows.value.length > 0 ? Number(resultRows.value[0][yCols[0]] ?? 0) : 0
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'gauge', data: [{ value: val, name: yCols[0] || '' }] }],
    }, true)
    return
  }

  // 矩形树图
  if (ct === 'treemap') {
    const treeData = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'treemap', data: treeData }],
    }, true)
    return
  }

  // 旭日图
  if (ct === 'sunburst') {
    const sunData = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'sunburst', data: sunData }],
    }, true)
    return
  }

  // 桑基图
  if (ct === 'sankey') {
    const sankeyData = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    // 简化：source → target 的边
    const links = sankeyData.slice(1).map((d, i) => ({
      source: sankeyData[i].name,
      target: d.name,
      value: d.value,
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'sankey', data: sankeyData, links }],
    }, true)
    return
  }

  // 主题河流图
  if (ct === 'themeRiver') {
    const riverData: Array<[string, number, string]> = []
    for (const col of yCols) {
      for (const row of resultRows.value) {
        riverData.push([String(row[xCol] ?? ''), Number(row[col] ?? 0), col])
      }
    }
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      singleAxis: { type: 'category', data: xData },
      series: [{ type: 'themeRiver', data: riverData }],
    }, true)
    return
  }

  // 关系图（简化：节点 + 顺序边）
  if (ct === 'graph') {
    const nodes = resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    }))
    const links = nodes.slice(1).map((n, i) => ({
      source: nodes[i].name,
      target: n.name,
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'graph', layout: 'force', data: nodes, links, roam: true }],
    }, true)
    return
  }

  // 树图
  if (ct === 'tree') {
    const buildTree = (items: Array<{ name: string; value: number }>) => {
      if (items.length === 0) return null
      const [first, ...rest] = items
      return {
        name: first.name,
        value: first.value,
        children: rest.length > 0 ? [buildTree(rest)] : undefined,
      }
    }
    const treeData = buildTree(resultRows.value.map((row) => ({
      name: String(row[xCol] ?? ''),
      value: Number(row[yCols[0]] ?? 0),
    })))
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'tree',
        data: treeData ? [treeData] : [],
        layout: 'orthogonal',
        orient: 'LR',
      }],
    }, true)
    return
  }

  // 雷达图
  if (ct === 'radar') {
    const indicator = dimensionColumns.value.map((dim) => ({
      name: dim,
      max: Math.max(...resultRows.value.map((row) => Number(row[dim] ?? 0)), 0),
    }))
    const radarSeriesData = yCols.map((col) => ({
      name: col,
      value: dimensionColumns.value.map((dim) => {
        const row = resultRows.value.find((r) => String(r[xCol]) === String(dim))
        return Number(row?.[col] ?? 0)
      }),
    }))
    // 如果维度不足以做 indicator，用行数据
    const finalIndicator = indicator.length > 0 ? indicator : xData.map((d) => ({ name: d, max: 100 }))
    const finalData = radarSeriesData.length > 0 ? radarSeriesData : [{
      name: yCols[0] || '',
      value: resultRows.value.map((row) => Number(row[yCols[0]] ?? 0)),
    }]
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      radar: { indicator: finalIndicator },
      series: [{ type: 'radar', data: finalData }],
    }, true)
    return
  }

  // 热力图
  if (ct === 'heatmap') {
    const dims = dimensionColumns.value.length >= 2 ? dimensionColumns.value : [xCol, ...yCols.slice(0, 1)]
    const xCats = Array.from(new Set(resultRows.value.map((r) => String(r[dims[0]] ?? ''))))
    const yCats = Array.from(new Set(resultRows.value.map((r) => String(r[dims[1]] ?? ''))))
    const heatData: Array<[number, number, number]> = []
    for (const row of resultRows.value) {
      const xi = xCats.indexOf(String(row[dims[0]] ?? ''))
      const yi = yCats.indexOf(String(row[dims[1]] ?? ''))
      const val = Number(row[yCols[0]] ?? 0)
      heatData.push([xi, yi, val])
    }
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: xCats },
      yAxis: { type: 'category', data: yCats },
      visualMap: {
        min: 0,
        max: Math.max(...heatData.map((d) => d[2]), 1),
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
      },
      series: [{ type: 'heatmap', data: heatData, label: { show: true } }],
    }, true)
    return
  }

  // 箱线图（简化：用原始值作为数据点）
  if (ct === 'boxplot') {
    const boxData = yCols.map((col) => {
      const vals = resultRows.value.map((row) => Number(row[col] ?? 0)).sort((a, b) => a - b)
      if (vals.length === 0) return [0, 0, 0, 0, 0]
      const q = (p: number) => vals[Math.floor(vals.length * p)] ?? 0
      return [q(0), q(0.25), q(0.5), q(0.75), q(1)]
    })
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      xAxis: { type: 'category', data: yCols },
      yAxis: { type: 'value' },
      series: [{ type: 'boxplot', data: boxData }],
    }, true)
    return
  }

  // K线图（简化：需要 OHLC 数据，用前4个指标列）
  if (ct === 'candlestick') {
    const ohlcCols = yCols.slice(0, 4)
    if (ohlcCols.length < 4) {
      // 列不足时回退为柱状图
      const series = yCols.map((col) => ({
        name: col, type: 'bar',
        data: resultRows.value.map((row) => Number(row[col] ?? 0)),
      }))
      chartInstance.setOption({
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0 },
        grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
        xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
        yAxis: { type: 'value' },
        series,
      }, true)
      return
    }
    const candleData = resultRows.value.map((row) => [
      Number(row[ohlcCols[0]] ?? 0),
      Number(row[ohlcCols[1]] ?? 0),
      Number(row[ohlcCols[2]] ?? 0),
      Number(row[ohlcCols[3]] ?? 0),
    ])
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: xData },
      yAxis: { type: 'value' },
      series: [{ type: 'candlestick', data: candleData }],
    }, true)
    return
  }

  // 平行坐标系
  if (ct === 'parallel') {
    const parallelAxis = resultColumns.value.map((col, i) => ({
      dim: i,
      name: col,
      type: typeof resultRows.value[0]?.[col] === 'number' ? 'value' : 'category',
    }))
    const parallelData = resultRows.value.map((row) =>
      resultColumns.value.map((col) => row[col]),
    )
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      parallelAxis,
      series: [{ type: 'parallel', data: parallelData }],
    }, true)
    return
  }

  // 面积图（折线图 + areaStyle）
  if (ct === 'area') {
    const series = yCols.map((col) => ({
      name: col,
      type: 'line',
      areaStyle: {},
      data: resultRows.value.map((row) => Number(row[col] ?? 0)),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series,
    }, true)
    return
  }

  // 涟漪特效散点图
  if (ct === 'effectScatter') {
    const scatterData = resultRows.value.map((row) => [
      String(row[xCol] ?? ''),
      Number(row[yCols[0]] ?? 0),
    ])
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: [{ type: 'effectScatter', data: scatterData }],
    }, true)
    return
  }

  // 象形柱图
  if (ct === 'pictorialBar') {
    const series = yCols.map((col) => ({
      name: col,
      type: 'pictorialBar',
      data: resultRows.value.map((row) => Number(row[col] ?? 0)),
    }))
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
      xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series,
    }, true)
    return
  }

  // 默认：柱状图 / 折线图 / 散点图（坐标系类图表）
  const series = yCols.map((col) => ({
    name: col,
    type: ct as 'bar' | 'line' | 'scatter',
    data: resultRows.value.map((row) => Number(row[col] ?? 0)),
  }))
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 40, right: 24, top: 20, bottom: 48, containLabel: true },
    xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series,
  }, true)
}

function handleClose(): void {
  emit('update:visible', false)
}
</script>

<style scoped>
.cmq-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0 4px;
}

.cmq-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cmq-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-primary);
}

.cmq-metrics-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cmq-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cmq-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-primary);
}

.cmq-table-wrap {
  max-height: 400px;
  overflow: auto;
}

.cmq-chart-controls {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.cmq-chart-container {
  width: 100%;
  height: 320px;
}

.cmq-result-empty {
  font-size: 13px;
  color: var(--el-text-secondary);
  padding: 16px 0;
  text-align: center;
}

.cmq-filter-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmq-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cmq-order-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmq-orderby-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cmq-pagination-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cmq-pagination-label {
  font-size: 13px;
  color: var(--el-text-secondary);
  white-space: nowrap;
}

.cmq-dim-tip {
  font-size: 12px;
  color: var(--el-text-secondary);
  margin-top: 4px;
}
</style>
