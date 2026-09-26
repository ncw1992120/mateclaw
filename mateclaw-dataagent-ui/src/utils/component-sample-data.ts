import type { ChartType, InsightComponent, InsightComponentData } from '@/types'
import { resolveOutputSpec, validateComponentOutput, type OutputValidationError } from './component-output-spec'
import type { ScriptResultEnvelope, ScriptResultColumn } from './script-result'

export interface ComponentSample {
  isSample: true
  contract: string
  json: string
  renderData: InsightComponentData
}

type SampleComponent = Pick<InsightComponent, 'id' | 'type'> & Partial<Pick<InsightComponent, 'chartType' | 'title'>>
type SampleColumn = { name: string; dataType: 'string' | 'number' | 'date'; role: string }
type SampleEnvelope = {
  schemaVersion?: unknown
  component?: { type?: unknown; subtype?: unknown }
  data?: { kind?: unknown; schema?: SampleColumn[]; rows?: Record<string, unknown>[]; value?: unknown; pagination?: Record<string, unknown> }
  meta?: { isSample?: unknown; rowCount?: unknown }
}

function sampleError(path: string, expected: string, actual: string): OutputValidationError {
  return { status: 'OUTPUT_CONTRACT_ERROR', path, expected, actual, suggestion: '请修正组件样例数据定义' }
}

/** 校验默认样例信封的字段、行数和分页元数据，避免样例定义与组件契约脱节。 */
export function validateComponentSample(component: SampleComponent, value: unknown): OutputValidationError | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return sampleError('result', '对象', Array.isArray(value) ? '数组' : typeof value)
  const envelope = value as SampleEnvelope
  if (envelope.schemaVersion !== '1.0') return sampleError('schemaVersion', '1.0', String(envelope.schemaVersion ?? '缺失'))
  if (envelope.component?.type !== component.type) return sampleError('component.type', component.type, String(envelope.component?.type ?? '缺失'))
  if (component.type === 'chart' && envelope.component?.subtype !== (component.chartType ?? 'line')) {
    return sampleError('component.subtype', component.chartType ?? 'line', String(envelope.component?.subtype ?? '缺失'))
  }
  if (envelope.meta?.isSample !== true) return sampleError('meta.isSample', 'true', String(envelope.meta?.isSample))
  const rowCount = envelope.meta.rowCount
  if (!Number.isInteger(rowCount) || Number(rowCount) < 0) return sampleError('meta.rowCount', '非负整数', String(rowCount))

  const data = envelope.data
  if (!data || typeof data !== 'object') return sampleError('data', '对象', typeof data)
  const isTabular = component.type === 'chart' || component.type === 'table'
    || (component.type === 'kpi' && data.kind === 'table')
  if (isTabular) {
    if (data.kind !== 'table') return sampleError('data.kind', 'table', String(data.kind))
    if (!Array.isArray(data.schema)) return sampleError('data.schema', '数组', typeof data.schema)
    if (!Array.isArray(data.rows)) return sampleError('data.rows', '数组', typeof data.rows)
    if (data.rows.length !== rowCount) return sampleError('meta.rowCount', `${data.rows.length}`, String(rowCount))
    const schemaNames = new Set<string>()
    for (const column of data.schema) {
      if (!column || typeof column.name !== 'string' || !column.name || schemaNames.has(column.name)) {
        return sampleError('data.schema.name', '非空且唯一的字段名', String(column?.name ?? '缺失'))
      }
      schemaNames.add(column.name)
    }
    for (const [rowIndex, row] of data.rows.entries()) {
      for (const column of data.schema) {
        if (!Object.prototype.hasOwnProperty.call(row, column.name)) {
          return sampleError(`data.rows[${rowIndex}].${column.name}`, '字段存在', '缺失')
        }
        const value = row[column.name]
        if (value == null) continue
        const validType = column.dataType === 'number'
          ? typeof value === 'number' && Number.isFinite(value)
          : typeof value === 'string'
        if (!validType) return sampleError(`data.rows[${rowIndex}].${column.name}`, column.dataType, typeof value)
      }
    }

    if (component.type === 'table') {
      const pagination = data.pagination
      if (!pagination || pagination.enabled !== true) return sampleError('data.pagination.enabled', 'true', String(pagination?.enabled))
      if (pagination.mode !== 'client' && pagination.mode !== 'server') return sampleError('data.pagination.mode', 'client | server', String(pagination.mode))
      if (!Number.isInteger(pagination.pageSize) || Number(pagination.pageSize) < 1) return sampleError('data.pagination.pageSize', '正整数', String(pagination.pageSize))
      if (!Number.isInteger(pagination.page) || Number(pagination.page) < 1) return sampleError('data.pagination.page', '正整数', String(pagination.page))
      if (pagination.mode === 'client') {
        if (pagination.total !== rowCount) return sampleError('data.pagination.total', String(rowCount), String(pagination.total))
        const pageCount = Math.max(1, Math.ceil(Number(rowCount) / Number(pagination.pageSize)))
        if (Number(pagination.page) > pageCount) return sampleError('data.pagination.page', `不大于 ${pageCount}`, String(pagination.page))
      } else {
        if (data.rows.length > Number(pagination.pageSize)) return sampleError('data.rows', `最多 ${pagination.pageSize} 行`, `${data.rows.length} 行`)
        if (!Number.isInteger(pagination.total) || Number(pagination.total) < rowCount) return sampleError('data.pagination.total', `不小于 ${rowCount}`, String(pagination.total))
        const expectedHasNext = Number(pagination.page) * Number(pagination.pageSize) < Number(pagination.total)
        if (pagination.hasNext !== expectedHasNext) return sampleError('data.pagination.hasNext', String(expectedHasNext), String(pagination.hasNext))
      }
    }

    if (component.type === 'kpi' && data.rows.length > 1) return sampleError('data.rows', '0..1 行', `${data.rows.length} 行`)
    if (component.type === 'chart') {
      if (component.chartType === 'pie') {
        const dimensions = data.schema.filter((column) => column.role === 'dimension').length
        const metrics = data.schema.filter((column) => column.role === 'metric').length
        if (dimensions !== 1 || metrics !== 1 || data.schema.length !== 2) {
          return sampleError('data.schema', '1 个维度 + 1 个指标', `${dimensions} 个维度 + ${metrics} 个指标`)
        }
      }
      const spec = resolveOutputSpec('chart', component.chartType ?? 'line')
      if (spec) {
        const columns = data.schema.map((column): ScriptResultColumn => ({
          name: column.name,
          title: column.name,
          dataType: column.dataType === 'date' ? 'date' : column.dataType,
          nullable: true,
        }))
        const output = validateComponentOutput(spec, {
          schemaVersion: '1.0',
          kind: 'table',
          data: { columns, rows: data.rows },
          meta: { rowCount: data.rows.length, truncated: false },
        } as ScriptResultEnvelope)
        if (output) return output
      }
    }
  } else if (component.type === 'kpi') {
    if (data.kind !== 'scalar') return sampleError('data.kind', 'scalar | table', String(data.kind))
    if (rowCount > 1) return sampleError('meta.rowCount', '0..1 行', `${rowCount} 行`)
    if (!Array.isArray(data.schema) || data.schema.length !== 1 || data.schema[0]?.dataType !== 'number' || data.schema[0]?.role !== 'metric') {
      return sampleError('data.schema', '1 个数值指标字段', `${Array.isArray(data.schema) ? data.schema.length : 0} 个符合字段`)
    }
    if (typeof data.value !== 'number' || !Number.isFinite(data.value)) return sampleError('data.value', '有限数值', typeof data.value)
  }
  return null
}

const CHART_CONTRACTS: Record<ChartType, string> = {
  line: '折线图：至少 1 个类别/时间维度和 1 个数值指标；可配置多个指标系列。',
  bar: '柱状图：至少 1 个类别维度和 1 个数值指标；可配置多个指标系列。',
  pie: '饼图：恰好 1 个分类维度和 1 个数值指标；分类值用于扇区，指标值必须是数值。',
  area: '面积图：至少 1 个类别/时间维度和 1 个数值指标；可配置多个指标系列。',
  scatter: '散点图：需要 X、Y 两个数值字段；可选分组和点大小字段。',
  radar: '雷达图：需要指标轴名称及每个系列在各指标轴上的数值。',
  effectScatter: '涟漪特效散点图：需要 X、Y 两个数值字段；可选分组字段。',
  candlestick: 'K 线图：需要时间/类别及 open、close、low、high 四个数值字段。',
  heatmap: '热力图：需要 X 类别、Y 类别和一个数值强度字段；每个坐标组合应唯一或明确聚合。',
  boxplot: '箱线图：需要分组和数值样本，或分组及 min、Q1、median、Q3、max 五数概括。',
  map: '地图：需要可识别的区域编码/名称或经纬度坐标，以及数值指标。',
  lines: '流向图：需要起点、终点及其地理坐标/路径，可选流量数值。',
  graph: '关系图：需要唯一节点 ID，以及引用有效节点的 source、target 边；可选节点/边权重。',
  tree: '树图：至少需要一个层级字段；可选数值指标用于节点大小，多层级时需明确父子关系。',
  treemap: '矩形树图：需要层级维度（至少一个）和数值指标；父子层级要明确。',
  sunburst: '旭日图：需要有序层级维度和数值指标；每行表示一个叶节点路径或递归 children。',
  parallel: '平行坐标系：需要至少两个数值轴字段；每行代表一条观测记录。',
  gauge: '仪表盘：需要一个数值指标；最小值、最大值和目标值为可选配置。',
  funnel: '漏斗图：需要有序阶段字段和一个非负数值指标；阶段顺序必须明确。',
  sankey: '桑基图：需要 source、target 和非负数值 value；边两端必须引用有效节点。',
  themeRiver: '主题河流图：需要时间/类别、系列名称和数值指标；同一时间与系列需明确聚合。',
  pictorialBar: '象形柱图：至少 1 个类别维度和 1 个数值指标；图形符号由样式配置提供。',
}

const rowsFor = (type: ChartType): { columns: SampleColumn[]; rows: Record<string, unknown>[] } => {
  if (type === 'scatter' || type === 'effectScatter') return {
    columns: [{ name: 'x', dataType: 'number', role: 'x' }, { name: 'y', dataType: 'number', role: 'y' }, { name: 'group', dataType: 'string', role: 'series' }],
    rows: [{ x: 12, y: 28, group: 'A组' }, { x: 24, y: 18, group: 'B组' }, { x: 35, y: 42, group: 'A组' }],
  }
  if (type === 'candlestick') return {
    columns: [{ name: 'date', dataType: 'date', role: 'dimension' }, ...(['open', 'close', 'low', 'high'] as const).map((name) => ({ name, dataType: 'number' as const, role: name }))],
    rows: [{ date: '2026-09-21', open: 31, close: 38, low: 28, high: 42 }, { date: '2026-09-22', open: 38, close: 34, low: 32, high: 40 }],
  }
  if (type === 'heatmap') return {
    columns: [{ name: 'weekday', dataType: 'string', role: 'xDimension' }, { name: 'hour', dataType: 'string', role: 'yDimension' }, { name: 'count', dataType: 'number', role: 'metric' }],
    rows: [{ weekday: '周一', hour: '上午', count: 18 }, { weekday: '周一', hour: '下午', count: 28 }, { weekday: '周二', hour: '上午', count: 35 }],
  }
  if (type === 'graph' || type === 'sankey' || type === 'lines') return {
    columns: [{ name: 'source', dataType: 'string', role: 'source' }, { name: 'target', dataType: 'string', role: 'target' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ source: '节点A', target: '节点B', value: 12 }, { source: '节点A', target: '节点C', value: 8 }, { source: '节点B', target: '节点C', value: 5 }],
  }
  if (type === 'tree') return {
    columns: [{ name: 'id', dataType: 'string', role: 'id' }, { name: 'parentId', dataType: 'string', role: 'parentId' }, { name: 'name', dataType: 'string', role: 'label' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ id: 'root', parentId: '', name: '总览', value: 93 }, { id: 'east', parentId: 'root', name: '华东', value: 62 }, { id: 'south', parentId: 'root', name: '华南', value: 31 }],
  }
  if (type === 'treemap' || type === 'sunburst') return {
    columns: [{ name: 'region', dataType: 'string', role: 'level1' }, { name: 'team', dataType: 'string', role: 'level2' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ region: '华东', team: '一组', value: 38 }, { region: '华东', team: '二组', value: 24 }, { region: '华南', team: '三组', value: 31 }],
  }
  if (type === 'map') return {
    columns: [{ name: 'regionCode', dataType: 'string', role: 'geoId' }, { name: 'regionName', dataType: 'string', role: 'dimension' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ regionCode: '310000', regionName: '上海', value: 38 }, { regionCode: '330000', regionName: '浙江', value: 24 }, { regionCode: '440000', regionName: '广东', value: 31 }],
  }
  if (type === 'radar') return {
    columns: [{ name: 'series', dataType: 'string', role: 'series' }, { name: 'response', dataType: 'number', role: 'metric' }, { name: 'quality', dataType: 'number', role: 'metric' }, { name: 'efficiency', dataType: 'number', role: 'metric' }],
    rows: [{ series: '方案A', response: 82, quality: 74, efficiency: 91 }, { series: '方案B', response: 68, quality: 88, efficiency: 76 }],
  }
  if (type === 'parallel') return {
    columns: [{ name: 'response', dataType: 'number', role: 'axis' }, { name: 'quality', dataType: 'number', role: 'axis' }, { name: 'efficiency', dataType: 'number', role: 'axis' }],
    rows: [{ response: 82, quality: 74, efficiency: 91 }, { response: 68, quality: 88, efficiency: 76 }],
  }
  if (type === 'boxplot') return {
    columns: [{ name: 'group', dataType: 'string', role: 'dimension' }, { name: 'sample', dataType: 'number', role: 'sample' }],
    rows: [{ group: 'A组', sample: 12 }, { group: 'A组', sample: 18 }, { group: 'A组', sample: 24 }, { group: 'B组', sample: 15 }, { group: 'B组', sample: 31 }],
  }
  if (type === 'themeRiver') return {
    columns: [{ name: 'date', dataType: 'date', role: 'time' }, { name: 'series', dataType: 'string', role: 'series' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ date: '2026-09-21', series: 'A类', value: 12 }, { date: '2026-09-21', series: 'B类', value: 8 }, { date: '2026-09-22', series: 'A类', value: 16 }],
  }
  if (type === 'gauge') return {
    columns: [{ name: 'value', dataType: 'number', role: 'metric' }], rows: [{ value: 72 }],
  }
  if (type === 'funnel') return {
    columns: [{ name: 'stage', dataType: 'string', role: 'stage' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ stage: '访问', value: 100 }, { stage: '注册', value: 65 }, { stage: '转化', value: 28 }],
  }
  return {
    columns: [{ name: 'category', dataType: 'string', role: 'dimension' }, { name: 'value', dataType: 'number', role: 'metric' }],
    rows: [{ category: '策略A', value: 42 }, { category: '策略B', value: 35 }, { category: '策略C', value: 23 }],
  }
}

function chartOption(type: ChartType, columns: SampleColumn[], rows: Record<string, unknown>[]): Record<string, unknown> {
  const values = (field: string) => rows.map((row) => Number(row[field]) || 0)
  const category = (field: string) => rows.map((row) => String(row[field] ?? ''))
  const nums = columns.filter((column) => column.dataType === 'number').map((column) => column.name)
  const base = { tooltip: { trigger: 'item' }, legend: { bottom: 0 } }
  if (type === 'pie') return { ...base, series: [{ type: 'pie', data: rows.map((row) => ({ name: row.category, value: row.value })) }] }
  if (type === 'gauge') return { ...base, series: [{ type: 'gauge', data: [{ value: rows[0]?.value ?? 0, name: '完成率' }] }] }
  if (type === 'funnel') return { ...base, series: [{ type: 'funnel', data: rows.map((row) => ({ name: row.stage, value: row.value })) }] }
  if (type === 'sankey') return { ...base, series: [{ type: 'sankey', data: [...new Set(rows.flatMap((row) => [row.source, row.target]))].map((name) => ({ name })), links: rows.map((row) => ({ source: row.source, target: row.target, value: row.value })) }] }
  if (type === 'graph') return { ...base, series: [{ type: 'graph', layout: 'force', roam: true, data: [...new Set(rows.flatMap((row) => [row.source, row.target]))].map((name) => ({ name })), links: rows.map((row) => ({ source: row.source, target: row.target, value: row.value })) }] }
  if (type === 'tree' || type === 'treemap' || type === 'sunburst') {
    const tree = [{ name: '华东', children: [{ name: '一组', value: 38 }, { name: '二组', value: 24 }] }, { name: '华南', children: [{ name: '三组', value: 31 }] }]
    return { ...base, series: [{ type, data: tree }] }
  }
  if (type === 'radar') return { ...base, radar: { indicator: nums.map((name) => ({ name, max: 100 })) }, series: [{ type: 'radar', data: rows.map((row) => ({ name: row.series, value: nums.map((name) => row[name]) })) }] }
  if (type === 'parallel') return { ...base, parallelAxis: columns.map((column, dim) => ({ dim, name: column.name, type: 'value' })), series: [{ type: 'parallel', data: rows.map((row) => columns.map((column) => row[column.name])) }] }
  if (type === 'heatmap') return { ...base, xAxis: { type: 'category', data: [...new Set(category('weekday'))] }, yAxis: { type: 'category', data: [...new Set(category('hour'))] }, visualMap: { min: 0, max: 40 }, series: [{ type: 'heatmap', data: rows.map((row) => [category('weekday').indexOf(String(row.weekday)), category('hour').indexOf(String(row.hour)), row.count]) }] }
  if (type === 'candlestick') return { ...base, xAxis: { type: 'category', data: category('date') }, yAxis: { type: 'value' }, series: [{ type: 'candlestick', data: rows.map((row) => [row.open, row.close, row.low, row.high]) }] }
  if (type === 'boxplot') return { ...base, xAxis: { type: 'category', data: ['A组', 'B组'] }, yAxis: { type: 'value' }, series: [{ type: 'boxplot', data: [[12, 14, 18, 21, 24], [15, 17, 23, 28, 31]] }] }
  if (type === 'themeRiver') return { ...base, singleAxis: { type: 'category', data: [...new Set(category('date'))] }, series: [{ type: 'themeRiver', data: rows.map((row) => [row.date, row.value, row.series]) }] }
  if (type === 'map') return { ...base, title: { text: '区域指标样例', left: 'center' }, xAxis: { type: 'category', data: category('regionName') }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: values('value') }] }
  if (type === 'lines') return { ...base, title: { text: '流向关系样例', left: 'center' }, xAxis: { type: 'category', data: [...new Set(category('source').concat(category('target')))] }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: values('value') }] }
  if (type === 'scatter' || type === 'effectScatter') return { ...base, xAxis: { type: 'value' }, yAxis: { type: 'value' }, series: [{ type, data: rows.map((row) => [row.x, row.y]) }] }
  if (type === 'area') return { ...base, xAxis: { type: 'category', data: category('category') }, yAxis: { type: 'value' }, series: [{ type: 'line', areaStyle: {}, data: values('value') }] }
  if (type === 'pictorialBar') return { ...base, xAxis: { type: 'category', data: category('category') }, yAxis: { type: 'value' }, series: [{ type, symbol: 'rect', symbolRepeat: true, data: values('value') }] }
  return { ...base, xAxis: { type: 'category', data: category('category') }, yAxis: { type: 'value' }, series: [{ type, data: values('value') }] }
}

export function resolveComponentSample(component: SampleComponent): ComponentSample {
  const chartType = component.type === 'chart' ? (component.chartType ?? 'line') : undefined
  let data: Record<string, unknown>
  let renderData: InsightComponentData
  let contract: string

  if (chartType) {
    const table = rowsFor(chartType)
    data = { kind: 'table', schema: table.columns, rows: table.rows }
    contract = CHART_CONTRACTS[chartType]
    renderData = { componentId: component.id, renderType: 'echarts', option: chartOption(chartType, table.columns, table.rows) }
  } else if (component.type === 'kpi') {
    const row = { value: 1284 }
    data = { kind: 'scalar', schema: [{ name: 'value', dataType: 'number', role: 'metric' }], value: row.value }
    contract = '指标卡：单指标模式接收一个数值；多指标模式接收具名数值项列表。'
    renderData = { componentId: component.id, renderType: 'kpi', kpi: { name: '总量', value: '1,284', fieldKey: 'value' }, kpiList: [{ name: '总量', value: '1,284', fieldKey: 'value' }] }
  } else if (component.type === 'table') {
    const columns = [{ name: 'name', dataType: 'string', role: 'dimension' }, { name: 'count', dataType: 'number', role: 'metric' }]
    const rows = Array.from({ length: 45 }, (_, index) => ({
      name: `策略${String.fromCharCode(65 + index % 26)}${Math.floor(index / 26) + 1}`,
      count: index + 1,
    }))
    data = { kind: 'table', schema: columns, rows, pagination: { page: 1, pageSize: 20, total: rows.length, hasNext: true } }
    contract = '表格：列定义 + 0..N 条明细行；样例需声明行数与分页模式、当前页、每页行数和总行数。'
    renderData = { componentId: component.id, renderType: 'table', table: { columns: ['name', 'count'], rows: rows.map((row) => [row.name, String(row.count)]) } }
  } else if (component.type === 'filter') {
    const options = [{ label: '策略A', value: 'strategy-a' }, { label: '策略B', value: 'strategy-b' }]
    data = { kind: 'options', schema: [{ name: 'label', dataType: 'string', role: 'label' }, { name: 'value', dataType: 'string', role: 'value' }], options }
    contract = '筛选器：选项包含 label（显示值）和 value（实际值）；选中值按单选或多选配置输出。'
    renderData = { componentId: component.id, renderType: 'table' }
  } else if (component.type === 'timeFilter') {
    data = { kind: 'timeRange', schema: [{ name: 'start', dataType: 'date', role: 'start' }, { name: 'end', dataType: 'date', role: 'end' }], value: { start: '2026-09-01', end: '2026-10-01', boundary: '[start,end)' } }
    contract = '时间筛选器：输出 start、end、时区和边界规则；推荐左闭右开 [start,end)。'
    renderData = { componentId: component.id, renderType: 'table' }
  } else if (component.type === 'aiAnalysis') {
    data = { kind: 'analysis', context: { title: '策略表现', metrics: [{ name: '总量', value: 1284 }] }, result: { markdown: '样例分析内容' } }
    contract = 'AI 分析：分析上下文与 Markdown/结构化分析结果分开承载。'
    renderData = { componentId: component.id, renderType: 'aiAnalysis', aiAnalysis: { dataSection: '总量：1,284', analysisSection: '样例分析内容' } }
  } else {
    data = { kind: 'composition', children: [{ componentId: 'sample-child-kpi', componentType: 'kpi', data: { kind: 'scalar', value: 1284 } }, { componentId: 'sample-child-chart', componentType: 'chart', data: { kind: 'table', schema: [{ name: 'category', role: 'dimension' }, { name: 'value', role: 'metric' }], rows: [{ category: '策略A', value: 42 }, { category: '策略B', value: 35 }] } }] }
    contract = '组合卡片：按子组件/页签 ID 分别提供结果，每个结果必须符合其子组件自己的契约。'
    renderData = { componentId: component.id, renderType: 'table' }
  }

  const envelope = {
    schemaVersion: '1.0',
    component: { type: component.type, ...(chartType ? { subtype: chartType } : {}) },
    data,
    meta: {
      isSample: true,
      rowCount: 'rows' in data ? (data.rows as unknown[]).length
        : 'options' in data ? (data.options as unknown[]).length
          : 'value' in data ? 1 : 1,
    },
  }
  if (component.type === 'table') {
    const tableData = data as { rows: unknown[]; pagination: { pageSize: number; total: number } }
    tableData.pagination = { mode: 'client', enabled: true, page: 1, pageSize: 20, total: tableData.rows.length }
  }
  const validationError = validateComponentSample(component, envelope)
  if (validationError) throw new Error(`组件样例数据不符合格式：${validationError.path} 期望 ${validationError.expected}，实际 ${validationError.actual}`)
  return { isSample: true, contract, json: JSON.stringify(envelope, null, 2), renderData }
}

export function hasConfiguredDataset(component: InsightComponent): boolean {
  const config = component.config as Record<string, unknown> | undefined
  const pipeline = config?.datasetPipeline as { datasetInputs?: unknown[] } | undefined
  if (pipeline?.datasetInputs?.length) return true
  if (component.dataSource?.datasourceId) return true
  return Boolean(component.children?.some((child) => hasConfiguredDataset(child as InsightComponent)))
}
