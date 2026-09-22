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
  /** 该组件可接受的 envelope kind */
  accepts: ScriptResultEnvelope['kind'][]
  family: OutputShapeFamily
  /** 编辑器展示的「期望输出」说明（中文） */
  description: string
  /** 编辑器展示的 Python 返回示例 */
  example: string
}

/** 名称+值 家族的图表：饼图/漏斗/仪表盘/旭日/矩形树。其余图表走 维度+指标 家族。 */
const NAME_VALUE_CHARTS = new Set<ChartType>(['pie', 'funnel', 'gauge', 'sunburst', 'treemap'])

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
      if (chartOutputFamily(chartType) === 'chartNameValue') {
        return {
          componentType,
          accepts: ['table'],
          family: 'chartNameValue',
          description: '返回一张表：第一列作名称、第二列（数值型）作值；单列时该列直接作为值。',
          example: 'result = df.groupby("channel")["amount"].sum().reset_index()',
        }
      }
      return {
        componentType,
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
