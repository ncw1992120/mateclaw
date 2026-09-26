<template>
  <div class="component-render-preview" data-testid="component-render-preview">
    <div v-if="loading" class="preview-empty" role="status">正在准备组件预览…</div>
    <div v-else-if="error" class="preview-empty" role="status">{{ error }}</div>
    <el-empty v-else-if="!rows.length" :description="hasQueried ? '当前查询没有匹配到数据；可以调整筛选条件后重新查询。' : '请先查询数据，再查看组件预览。'" />
    <template v-else-if="previewComponent">
      <el-alert
        v-if="warning"
        class="preview-warning"
        type="info"
        :closable="false"
        show-icon
        data-testid="component-render-warning"
      >
        <template #title>当前数据暂时无法用于此组件预览</template>
        <div>{{ warning }}</div>
        <div class="preview-python-hint">你可以调整字段配置，或继续使用 Python 脚本整理数据后再预览。</div>
      </el-alert>
      <div v-else class="preview-widget" data-testid="component-render-content">
        <KpiCardWidget
          v-if="previewComponent.type === 'kpi'"
          :component="previewComponent"
          :component-data="componentData"
          :show-title="false"
          :editable="false"
        />
        <ChartWidget
          v-else-if="previewComponent.type === 'chart'"
          :component="previewComponent"
          :component-data="componentData"
          :show-title="false"
          :editable="false"
        />
        <DataTableWidget
          v-else-if="previewComponent.type === 'table'"
          :component="previewComponent"
          :component-data="componentData"
          :show-title="false"
          :sample-mode="false"
          :editable="false"
        />
      </div>
    </template>
    <div v-else class="preview-empty" role="status">请先选择要预览的数据组件。</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChartType, InsightComponent } from '@/types'
import { rowsToComponentData } from '@/utils/dataset-result'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import { resolveOutputSpec, validateComponentOutput } from '@/utils/component-output-spec'
import type { ScriptResultColumn, ScriptResultEnvelope } from '@/utils/script-result'
import KpiCardWidget from './KpiCardWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'

interface PreviewColumn {
  name: string
  title?: string
  dataType?: string
  nullable?: boolean
  role?: string
}

const props = defineProps<{
  component: InsightComponent | null
  rows: Record<string, unknown>[]
  columns: PreviewColumn[]
  fieldRoles?: Record<string, 'dimension' | 'measure'>
  loading?: boolean
  error?: string
  hasQueried?: boolean
}>()

const normalizedColumns = computed<ScriptResultColumn[]>(() => {
  const source = props.columns.length
    ? props.columns
    : [...new Set(props.rows.flatMap((row) => Object.keys(row)))].map((name) => ({ name }))
  return source.map((column) => ({
    name: column.name,
    title: column.title?.trim() || column.name,
    dataType: normalizeDataType(column.dataType, props.rows, column.name),
    nullable: column.nullable ?? true,
  }))
})

const labels = computed(() => Object.fromEntries(normalizedColumns.value.map((column) => [column.name, column.title])))
const previewComponent = computed<InsightComponent | null>(() => {
  const component = props.component
  if (!component || component.type !== 'kpi') return component
  const metrics = normalizedColumns.value
    .filter((column) => (props.fieldRoles?.[column.name] ?? columnRole(column)) === 'measure')
    .map((column) => ({ name: column.name, displayName: column.title, role: 'measure', dataType: column.dataType }))
  const kpiMetrics = buildKpiMetrics(metrics, component.kpiMetrics ?? [])
  return { ...component, kpiMetrics, multiKpi: kpiMetrics.length > 1 || component.multiKpi === true }
})
const componentData = computed(() => {
  const component = previewComponent.value
  if (!component) return undefined
  const kpiFields = (component.kpiMetrics ?? []).map((metric) => ({
    fieldKey: metric.fieldKey,
    displayName: metric.displayName ?? undefined,
    unit: metric.unit ?? undefined,
  }))
  const renderType = component.type === 'kpi' ? 'kpi' : component.type === 'chart' ? 'echarts' : 'table'
  return rowsToComponentData(component.id, props.rows, renderType, kpiFields, labels.value)
})

const warning = computed(() => {
  const component = props.component
  if (!component || !props.rows.length) return ''

  const roles = new Map(normalizedColumns.value.map((column) => [
    column.name,
    props.fieldRoles?.[column.name]
      ?? (columnRole(column) === 'measure' ? 'measure' : 'dimension'),
  ]))
  const metrics = normalizedColumns.value.filter((column) => roles.get(column.name) === 'measure')
  const dimensions = normalizedColumns.value.filter((column) => roles.get(column.name) === 'dimension')

  if (component.type === 'table' && !normalizedColumns.value.length) return '当前结果没有返回可展示字段。请检查查询配置，或使用 Python 脚本生成表格字段。'

  if (component.type === 'kpi') {
    if (props.rows.length > 1) return `指标卡需要 Aloudata 返回一行汇总结果；当前有 ${props.rows.length} 行。请检查查询是否已按指标汇总，也可以使用 Python 脚本整理数据。`
    if (dimensions.length) return `指标卡展示结果不能包含维度字段（当前有：${dimensions.map((item) => item.title).join('、')}）。请只选择指标，也可以使用 Python 脚本整理数据。`
    if (!metrics.length || metrics.some((column) => column.dataType !== 'number')) return '指标卡需要至少一个数值指标字段。请检查字段类型，或使用 Python 脚本整理为指标值。'
  }

  const spec = resolveOutputSpec(component.type, component.chartType)
  if (!spec) return ''
  const envelope: ScriptResultEnvelope = {
    schemaVersion: '1.0',
    kind: 'table',
    data: { columns: normalizedColumns.value, rows: props.rows },
    meta: { rowCount: props.rows.length, truncated: false },
  }
  const violation = validateComponentOutput(spec, envelope, {
    valueField: typeof component.config?.valueField === 'string' ? component.config.valueField : undefined,
    metricFields: Array.isArray(component.config?.metricFields) ? component.config.metricFields as string[] : undefined,
    dimensionField: typeof component.config?.dimensionField === 'string' ? component.config.dimensionField : undefined,
  })
  if (!violation) return ''

  const label = component.type === 'table' ? '表格' : chartLabel(component.chartType)
  const shape = component.type === 'chart'
    ? chartRequirement(component.chartType)
    : spec.description
  const current = `${dimensions.length} 个维度、${metrics.length} 个指标`
  return `${label}需要${shape}；当前查询结果有${current}。请调整展示字段，或用 Python 脚本预处理后再试。`
})

function columnRole(column: ScriptResultColumn): 'dimension' | 'measure' {
  const configuredRole = props.fieldRoles?.[column.name]
  if (configuredRole) return configuredRole
  const sourceRole = props.columns.find((item) => item.name === column.name)?.role?.toLowerCase()
  if (sourceRole === 'measure' || sourceRole === 'metric') return 'measure'
  if (sourceRole === 'dimension') return 'dimension'
  return column.dataType === 'number' ? 'measure' : 'dimension'
}

function normalizeDataType(type: string | undefined, rows: Record<string, unknown>[], field: string): ScriptResultColumn['dataType'] {
  const value = String(type ?? '').trim().toLowerCase()
  if (/(int|long|float|double|decimal|numeric|number|real)/.test(value)) return 'number'
  if (value.includes('datetime') || value.includes('timestamp')) return 'datetime'
  if (value === 'date' || value.includes('date')) return 'date'
  if (value.includes('bool')) return 'boolean'
  const sample = rows.find((row) => row[field] != null)?.[field]
  if (typeof sample === 'number') return 'number'
  if (typeof sample === 'boolean') return 'boolean'
  if (typeof sample === 'string' && /^\d{4}-\d{2}-\d{2}(?:[T ]|$)/.test(sample)) return sample.length > 10 ? 'datetime' : 'date'
  return 'string'
}

function chartLabel(type?: ChartType): string {
  const labels: Partial<Record<ChartType, string>> = {
    line: '折线图', bar: '柱状图', pie: '饼图', area: '面积图', scatter: '散点图', radar: '雷达图',
    effectScatter: '涟漪散点图', candlestick: 'K 线图', heatmap: '热力图', boxplot: '箱线图', map: '地图',
    lines: '流向图', graph: '关系图', tree: '树图', treemap: '矩形树图', sunburst: '旭日图', parallel: '平行坐标图',
    gauge: '仪表盘', funnel: '漏斗图', sankey: '桑基图', themeRiver: '主题河流图', pictorialBar: '象形柱图',
  }
  return labels[type ?? 'line'] ?? '图表'
}

function chartRequirement(type?: ChartType): string {
  const requirements: Partial<Record<ChartType, string>> = {
    line: '一个分类或时间字段和至少一个数值指标',
    bar: '一个分类字段和至少一个数值指标',
    area: '一个分类或时间字段和至少一个数值指标',
    pictorialBar: '一个分类字段和至少一个数值指标',
    pie: '一个分类字段和一个数值指标',
    funnel: '一个有顺序的阶段字段和一个数值指标',
    gauge: '一个数值指标',
    scatter: '两个数值字段作为横轴和纵轴',
    effectScatter: '两个数值字段作为横轴和纵轴',
    radar: '至少两个数值指标轴',
    heatmap: '两个分类字段和一个数值指标',
    tree: '至少一个层级字段；数值指标可用于节点大小',
    treemap: '至少一个层级字段和一个数值指标',
    sunburst: '至少一个层级字段和一个数值指标',
    candlestick: '时间字段及开盘、收盘、最低、最高四个数值字段',
    boxplot: '分组字段和数值样本，或五数概括字段',
    map: '区域或经纬度字段和一个数值指标',
    lines: '起点、终点及地理坐标或路径字段',
    graph: '节点关系字段（起点和终点）',
    parallel: '至少两个数值轴字段',
    sankey: '起点、终点和一个数值指标',
    themeRiver: '时间字段、系列名称和数值指标',
  }
  return requirements[type ?? 'line'] ?? '符合图表类型的字段'
}
</script>

<style scoped>
.component-render-preview { min-height: 250px; }
.preview-warning { align-items: flex-start; }
.preview-python-hint { margin-top: 6px; }
.preview-widget { height: 320px; min-height: 260px; overflow: hidden; background: var(--el-bg-color); }
.preview-widget > * { height: 100%; }
.preview-empty { min-height: 180px; display: grid; place-items: center; color: var(--db-text-muted); font-size: 13px; }
</style>
