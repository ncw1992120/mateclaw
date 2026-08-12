/**
 * 图表类型公共定义（问数 / 指标查询共用）。
 * 全量列表为两份使用方的并集，避免同一份列表在多处重复定义导致漂移；
 * 各使用方按自身渲染能力引用对应子集。
 */

/** 图表类型项 */
export interface ChartTypeItem {
  key: string
  label: string
}

/** 全量图表类型（并集：问数 + 指标查询，含各自的独有类型） */
export const CHART_TYPES_ALL = [
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
  { key: 'map', label: '地图' },
  { key: 'lines', label: '线图（流向图）' },
  { key: 'graph', label: '关系图' },
  { key: 'tree', label: '树图' },
  { key: 'treemap', label: '矩形树图' },
  { key: 'sunburst', label: '旭日图' },
  { key: 'parallel', label: '平行坐标系' },
  { key: 'gauge', label: '仪表盘' },
  { key: 'funnel', label: '漏斗图' },
  { key: 'sankey', label: '桑基图' },
  { key: 'themeRiver', label: '主题河流图' },
  { key: 'pictorialBar', label: '象形柱图' },
] as const

/** 图表类型 key 联合类型 */
export type ChartTypeKey = (typeof CHART_TYPES_ALL)[number]['key']

/**
 * 问数图表类型：与全量一致。
 * 面积图（area）在问数中以折线图 + areaStyle 渲染（见 ChatView.buildEchartsOption）。
 */
export const CHART_TYPES_CHAT: typeof CHART_TYPES_ALL = CHART_TYPES_ALL

/**
 * 指标查询图表类型子集：不含 map / lines（指标查询基于查询结果行构建图表，
 * 无法生成地图 geoJSON 与流向图坐标序列）。
 * 顺序与 CHART_TYPES_ALL 保持一致。
 */
export const CHART_TYPES_METRIC_QUERY = CHART_TYPES_ALL.filter(
  (t) => t.key !== 'map' && t.key !== 'lines',
) as unknown as typeof CHART_TYPES_ALL
