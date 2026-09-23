/**
 * 组件输出规范：按 (组件类型, 图表子类型) 定义脚本「应当返回什么形状」。
 *
 * 这是脚本结果 → 组件渲染契约的单一事实来源，被三处消费：
 *  - 校验（validateComponentOutput）：执行后按规范 fail-fast，给出精确到列的报错；
 *  - 渲染（resultEnvelopeToComponentData）：按家族精确取列生成 option / 取值；
 *  - 编辑器引导（PythonScriptDialog）：展示「本组件期望输出」与返回示例。
 *
 * envelope 结构本身不变（仍是 table/scalar/message），本模块只约束「哪种组件要哪种 envelope」。
 */
import type { ChartType, InsightComponentType } from '@/types'
import type { ScriptResultColumn, ScriptResultEnvelope } from './script-result'

/** 输出形状家族：决定取列/生成 option 的方式 */
export type OutputShapeFamily = 'kpi' | 'table' | 'chartCategory' | 'chartNameValue'

export interface ComponentOutputSpec {
  componentType: InsightComponentType
  chartType?: ChartType
  /** 该组件可接受的 envelope kind */
  accepts: ScriptResultEnvelope['kind'][]
  family: OutputShapeFamily
  /** 编辑器展示的「期望输出」说明（中文） */
  description: string
  /** 编辑器展示的 Python 返回示例 */
  example: string
}

export interface OutputContractTemplate {
  kind: ScriptResultEnvelope['kind']
  example: string
  fieldRules: {
    minColumns: number
    minDimensionColumns: number
    minNumericColumns: number
  }
}

/** 名称+值 家族的图表：饼图/漏斗/仪表盘/旭日/矩形树。其余图表走 维度+指标 家族。 */
const NAME_VALUE_CHARTS = new Set<ChartType>(['pie', 'funnel', 'gauge', 'sunburst', 'treemap'])
const CHART_HINTS: Partial<Record<ChartType, string>> = {
  pie: '饼图需要恰好 1 个分类维度和 1 个数值指标',
  funnel: '漏斗图需要有序阶段字段和 1 个数值指标',
  gauge: '仪表盘需要恰好 1 个数值指标',
  radar: '雷达图需要至少 2 个数值指标轴；多系列需系列名称字段',
  treemap: '矩形树图需要至少 2 个层级维度和 1 个数值指标',
  sunburst: '旭日图需要至少 2 个层级维度和 1 个数值指标',
  scatter: '散点图需要 X、Y 两个数值字段',
  effectScatter: '涟漪特效散点图需要 X、Y 两个数值字段',
  candlestick: 'K 线图需要 open、close、low、high 四个数值字段',
  heatmap: '热力图需要 X 维度、Y 维度和 1 个数值指标',
  boxplot: '箱线图需要分组和样本数值，或 min、q1、median、q3、max 五数概括',
  map: '地图需要区域编码/名称或经纬度字段，以及数值指标',
  lines: '流向图需要 source、target 起终点字段',
  graph: '关系图需要唯一节点及 source、target 关系边字段',
  tree: '树图需要 name 和 parentId 层级字段',
  parallel: '平行坐标系至少需要 2 个数值轴字段',
  sankey: '桑基图需要 source、target 和非负数值 value 字段',
  themeRiver: '主题河流图需要时间、系列名称和数值指标',
}

const SPECIAL_CHARTS = new Set<ChartType>([
  'scatter', 'effectScatter', 'candlestick', 'heatmap', 'boxplot', 'map', 'lines',
  'graph', 'tree', 'parallel', 'sankey', 'themeRiver',
])

/** 图表子类型 → 输出家族；缺省（未知/非图表）按 维度+指标 处理。 */
export function chartOutputFamily(chartType?: ChartType): OutputShapeFamily {
  if (chartType && NAME_VALUE_CHARTS.has(chartType)) return 'chartNameValue'
  return 'chartCategory'
}

const COMPONENT_LABEL: Record<string, string> = {
  kpi: 'KPI',
  table: '表格',
  chart: '图表',
}

export function componentLabel(type: InsightComponentType): string {
  return COMPONENT_LABEL[type] ?? type
}

/**
 * 解析某组件的输出规范；筛选类/AI/组合不消费脚本结果，返回 null。
 * chartType 在编辑器上下文通常缺失（卡片只有类型），缺失时图表按 chartCategory 家族处理。
 */
export function resolveOutputSpec(
  componentType: InsightComponentType,
  chartType?: ChartType,
): ComponentOutputSpec | null {
  switch (componentType) {
    case 'kpi':
      return {
        componentType,
        accepts: ['scalar', 'table'],
        family: 'kpi',
        description: '返回单个标量（scalar），或一张表（取图表配置 valueField 列、否则首列作为指标值）。',
        example: 'result = 1234.5          # 或 result = df_pl.total.sum()',
      }
    case 'table':
      return {
        componentType,
        accepts: ['table'],
        family: 'table',
        description: '返回一张表：列即表格列，行即表格行，任意列结构均可。',
        example: 'result = df_order        # 任意列结构的 DataFrame',
      }
    case 'chart':
      if (chartType && SPECIAL_CHARTS.has(chartType)) {
        return {
          componentType,
          chartType,
          accepts: ['table'],
          family: 'chartCategory',
          description: CHART_HINTS[chartType] ?? '按图表子类型的字段角色返回数据表。',
          example: `result = df  # ${CHART_HINTS[chartType] ?? '返回符合字段要求的数据表'}`,
        }
      }
      if (chartOutputFamily(chartType) === 'chartNameValue') {
        return {
          componentType,
          chartType,
          accepts: ['table'],
          family: 'chartNameValue',
          description: '返回一张表：第一列作名称、第二列（数值型）作值；单列时该列直接作为值。',
          example: 'result = df.groupby("channel")["amount"].sum().reset_index()',
        }
      }
      return {
        componentType,
        chartType,
        accepts: ['table'],
        family: 'chartCategory',
        description: '返回一张表：含 1 个维度（类目）列 + 至少 1 个数值指标列。',
        example: 'result = df.groupby("date")[["uv", "pv"]].sum().reset_index()',
      }
    default:
      // filter / timeFilter / aiAnalysis / combination：不消费脚本结果
      return null
  }
}

/** 将组件输出规范转换为编辑器可展示的固定返回模板和字段下限。 */
export function outputContractTemplate(spec: ComponentOutputSpec): OutputContractTemplate {
  const table = spec.accepts.includes('table')
  const minDimensionColumns = spec.family === 'chartCategory' || spec.family === 'chartNameValue' ? 1 : 0
  const minNumericColumns = spec.family === 'chartCategory' || spec.family === 'chartNameValue' ? 1 : 0
  return {
    kind: table ? 'table' : spec.accepts[0],
    example: table
      ? '{"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}'
      : spec.accepts[0] === 'scalar'
        ? '{"schemaVersion":"1.0","kind":"scalar","data":{"value":0,"dataType":"number"},"meta":{"rowCount":1,"truncated":false}}'
        : '{"schemaVersion":"1.0","kind":"message","data":{"level":"info","message":"暂无数据"},"meta":{"rowCount":0,"truncated":false}}',
    fieldRules: {
      minColumns: table ? (spec.family === 'table' ? 0 : minDimensionColumns + minNumericColumns) : 0,
      minDimensionColumns,
      minNumericColumns,
    },
  }
}

export interface OutputValidationContext {
  valueField?: string
  metricFields?: string[]
  dimensionField?: string
}

export interface OutputValidationError {
  status?: string
  path?: string
  expected?: string
  actual?: string
  suggestion?: string
  message?: string
}

/**
 * 校验 envelope 是否满足组件输出规范；满足返回 null，否则返回精确到列的报错。
 * 调用方应把返回的 message 透出给用户（fail-fast，避免渲染错/静默空态）。
 */
export function validateComponentOutput(
  spec: ComponentOutputSpec,
  envelope: ScriptResultEnvelope,
  ctx: OutputValidationContext = {},
): OutputValidationError | null {
  if (!spec.accepts.includes(envelope.kind)) {
    return {
      status: 'OUTPUT_CONTRACT_ERROR',
      path: 'result.kind',
      expected: spec.accepts.join(' | '),
      actual: envelope.kind,
      suggestion: `${componentLabel(spec.componentType)}组件期望 ${spec.accepts.join(' 或 ')} 类型结果`,
    }
  }
  if (envelope.kind !== 'table') return null

  const columns = envelope.data.columns as ScriptResultColumn[]
  const numericCols = columns.filter((column) => column.dataType === 'number')
  const names = new Set(columns.map((column) => column.name.toLowerCase()))
  const hasAny = (...fields: string[]) => fields.some((field) => names.has(field))
  const failChart = (expected: string, actual: string) => ({
    status: 'OUTPUT_CONTRACT_ERROR',
    path: 'result.data.columns',
    expected,
    actual,
    suggestion: CHART_HINTS[spec.chartType ?? 'line'] ?? '图表数据结构不匹配',
  })

  if (spec.componentType === 'chart' && spec.chartType) {
    const stringCols = columns.filter((column) => column.dataType === 'string' || column.dataType === 'date' || column.dataType === 'datetime')
    const chartType = spec.chartType
    if (chartType === 'scatter' || chartType === 'effectScatter') {
      if (numericCols.length < 2) return failChart('至少 2 个数值字段（X、Y）', `${numericCols.length} 个数值字段`)
      return null
    }
    if (chartType === 'radar') {
      if (numericCols.length < 2) return failChart('至少 2 个数值指标轴字段', `${numericCols.length} 个数值字段`)
      return null
    }
    if (chartType === 'sankey' || chartType === 'graph' || chartType === 'lines') {
      if (!hasAny('source') || !hasAny('target')) return failChart('source、target 字段', columns.map((column) => column.name).join(', ') || '无字段')
      if (chartType === 'sankey' && (!hasAny('value') || numericCols.length === 0)) return failChart('source、target 及数值 value 字段', columns.map((column) => column.name).join(', '))
      return null
    }
    if (chartType === 'candlestick') {
      const ohlc = ['open', 'close', 'low', 'high']
      if (!ohlc.every((field) => names.has(field)) || ohlc.some((field) => !columns.find((column) => column.name.toLowerCase() === field)?.dataType.match(/^number$/))) {
        return failChart('open、close、low、high 四个数值字段', columns.map((column) => column.name).join(', ') || '无字段')
      }
      return null
    }
    if (chartType === 'heatmap') {
      if (stringCols.length < 2 || numericCols.length < 1) return failChart('2 个维度字段 + 1 个数值指标', `${stringCols.length} 个维度，${numericCols.length} 个数值字段`)
      return null
    }
    if (chartType === 'parallel') {
      if (numericCols.length < 2) return failChart('至少 2 个数值轴字段', `${numericCols.length} 个数值字段`)
      return null
    }
    if (chartType === 'themeRiver') {
      if (stringCols.length < 2 || numericCols.length < 1) return failChart('时间字段 + 系列字段 + 数值指标', `${stringCols.length} 个维度，${numericCols.length} 个数值字段`)
      return null
    }
    if (chartType === 'map') {
      if (!(hasAny('regioncode', 'regionname', 'geoid', 'name') || (hasAny('longitude', 'lon', 'lng') && hasAny('latitude', 'lat'))) || numericCols.length < 1) {
        return failChart('区域编码/名称或经纬度 + 数值指标', columns.map((column) => column.name).join(', ') || '无字段')
      }
      return null
    }
    if (chartType === 'tree') {
      if (!hasAny('name', 'label') || !hasAny('parentid', 'parent_id', 'parent')) return failChart('name/label + parentId 层级字段', columns.map((column) => column.name).join(', ') || '无字段')
      return null
    }
    if (chartType === 'boxplot') {
      const fiveNumbers = ['min', 'q1', 'median', 'q3', 'max']
      const hasFiveNumberSummary = fiveNumbers.every((field) => names.has(field) && columns.find((column) => column.name.toLowerCase() === field)?.dataType === 'number')
      if (!(hasFiveNumberSummary || (stringCols.length >= 1 && numericCols.length >= 1))) return failChart('分组 + 样本数值，或 min/q1/median/q3/max 五个数值字段', columns.map((column) => column.name).join(', ') || '无字段')
      return null
    }
    if ((chartType === 'treemap' || chartType === 'sunburst') && (stringCols.length < 2 || numericCols.length < 1)) {
      return failChart('至少 2 个层级维度 + 1 个数值指标', `${stringCols.length} 个维度，${numericCols.length} 个数值字段`)
    }
    if (chartType === 'pie' && (stringCols.length !== 1 || numericCols.length !== 1 || columns.length !== 2)) {
      return failChart('恰好 1 个分类维度 + 1 个数值指标', `${stringCols.length} 个维度，${numericCols.length} 个数值字段，共 ${columns.length} 列`)
    }
    if (chartType === 'funnel' && (stringCols.length < 1 || numericCols.length !== 1)) {
      return failChart('1 个阶段字段 + 1 个数值指标', `${stringCols.length} 个维度，${numericCols.length} 个数值字段`)
    }
  }

  if (spec.family === 'kpi') {
    if (ctx.valueField) {
      const hit = columns.find((column) => column.name === ctx.valueField)
      if (!hit) {
        return {
          status: 'OUTPUT_CONTRACT_ERROR',
          path: `result.data.columns.${ctx.valueField}`,
          expected: '存在该列',
          actual: '缺失',
          suggestion: `KPI 的 valueField=${ctx.valueField} 在结果中不存在`,
        }
      }
    } else if (columns.length === 0) {
      return {
        status: 'OUTPUT_CONTRACT_ERROR',
        path: 'result.data.columns',
        expected: '至少 1 列',
        actual: '0 列',
        suggestion: 'KPI 组件需要至少 1 列作为指标值',
      }
    }
    return null
  }

  if (spec.family === 'table') return null

  if (numericCols.length === 0) {
    return {
      status: 'OUTPUT_CONTRACT_ERROR',
      path: 'result.data.columns',
      expected: '至少 1 个数值列',
      actual: `${numericCols.length} 个数值列`,
      suggestion: `${componentLabel(spec.componentType)}组件需要至少 1 个数值指标列`,
    }
  }

  if (spec.componentType === 'chart' && spec.chartType && ['line', 'bar', 'area', 'pictorialBar'].includes(spec.chartType)) {
    const dimensionCols = columns.filter((column) => column.dataType === 'string' || column.dataType === 'date' || column.dataType === 'datetime')
    if (dimensionCols.length < 1) return failChart('至少 1 个类别/时间维度 + 1 个数值指标', columns.map((column) => column.name).join(', ') || '无字段')
  }

  if (spec.family === 'chartNameValue') {
    if (columns.length < 1) {
      return {
        status: 'OUTPUT_CONTRACT_ERROR',
        path: 'result.data.columns',
        expected: '至少 1 列',
        actual: `${columns.length} 列`,
        suggestion: '饼图/漏斗/仪表盘类需要 名称列 + 数值列（或单列数值）',
      }
    }
    if (spec.chartType === 'gauge' && numericCols.length !== 1) {
      return failChart('恰好 1 个数值指标', `${numericCols.length} 个数值字段`)
    }
    return null
  }

  if (columns.length < 2) {
    return {
      status: 'OUTPUT_CONTRACT_ERROR',
      path: 'result.data.columns',
      expected: '维度列 + 至少 1 指标列',
      actual: `${columns.length} 列`,
      suggestion: '折线/柱图类需要 1 个维度列 + 至少 1 个数值指标列',
    }
  }
  return null
}
