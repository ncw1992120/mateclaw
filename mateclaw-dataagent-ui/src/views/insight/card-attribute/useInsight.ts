/**
 * useInsight —— 洞察·仪表盘·卡片属性配置 的「前端交互状态层」
 * =====================================================================
 * 本文件集中管理：
 *   1) 全部【假数据 MOCK】（卡片、数据源树、各类型数据集配置、字段映射、
 *      输入筛选、筛选器绑定、Python Base Script、预览四 Tab 数据）。
 *   2) 全部交互状态（当前选中卡片、已添加数据集、各弹窗开关、编辑目标）。
 *   3) 全部 action（增删数据集、打开/保存各类弹窗、自动 3 秒刷新等）。
 *
 * ⚠️ 对接后端时，只需把本文件里标注 `[MOCK]` 的假数据替换为真实接口调用，
 *    组件层（*.vue）无需改动。所有数据读写都经过这里，便于统一替换。
 *
 * 设计说明：
 *   - store 为模块级单例（reactive），所有组件共享同一份状态。
 *   - 组件只负责「渲染」与「派发 action」，不持有业务数据。
 * =====================================================================
 */
import { reactive, computed } from 'vue'
import * as backend from './useInsightBackend'
import type { ComponentDatasetPipeline, DashboardDatasetInput, DashboardScriptFilterBinding, InsightComponent, InsightDashboardSchema } from '@/types'

/* ============================ 类型定义 ============================ */

export type DataSourceLeafType = 'jdbc' | 'aloudata' | 'api' | 'file'
export type CardType = 'kpi' | 'table' | 'chart'

/** 字段映射：源字段名 → 目标字段名 */
export interface FieldMapping {
  source: string // 源字段名（数据库/接口原始字段）
  desc: string // 字段描述
  target: string // 目标字段名（最终数据集使用的名称）
}

/** 输入筛选条件（当前数据集查询条件，应下推到源查询） */
export interface InputFilter {
  field: string // 字段（使用最终数据集字段名）
  op: string // 操作符：=、!=、contains、in、between、is null、is not null、最近 N 天
  value: string // 条件值
}

/** 已添加的数据集配置（每个数据集一张独立表单） */
export interface DatasetConfig {
  id: string
  sourceType: DataSourceLeafType
  sourceLabel: string // 例："JDBC · db1"
  alias: string // 数据集别名，例：table1
  fieldMapping: FieldMapping[]
  filters: InputFilter[]
  // 各类型专属配置（假数据占位）
  jdbc?: { db: string; sql: string }
  aloudata?: { mode: 'metric-dim' | 'metric-view'; metricView?: string; metrics?: string[]; dims?: string[] }
  api?: { host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file?: { fileName: string; fileType: string; columns: { name: string; type: string }[]; rows: Record<string, string>[] }
  /** [后端联调] 真实数据集 ID：数据集配置经后端 confirmDraft 落库后回填 */
  backendDatasetId?: string
}

/** 卡片（仪表盘画布上的组件） */
export interface CardItem {
  id: string
  type: CardType
  title: string
  multiMetric: boolean // 多指标模式（仅 KPI/指标卡）
  multiTab: boolean // 多 TAB 模式（仅 KPI/指标卡）
}

/** 筛选器绑定 */
export interface FilterBindingFieldMap {
  datasetId: string
  field: string // 该数据集上匹配到的字段
  matched: boolean // 是否自动匹配（false 表示需手动映射）
}
export interface FilterBinding {
  filterName: string // 仪表盘参数名，例：策略类型
  scope: Record<string, boolean> // 数据集 id -> 是否作用到该数据集
  fieldMap: FilterBindingFieldMap[]
}

interface UiState {
  treeVisible: boolean // 数据源选择树弹窗
  editingDatasetId: string | null // 正在编辑（重新配置）的数据集 id；null=新增
  // 各配置弹窗状态
  jdbc: { visible: boolean; db: string; sql: string }
  aloudata: { visible: boolean; mode: 'metric-dim' | 'metric-view'; metricView: string; metrics: string[]; dims: string[] }
  api: { visible: boolean; host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file: { visible: boolean; fileType: string; fileName: string; columns: { name: string; type: string }[]; rows: Record<string, string>[] }
  fieldMapping: { visible: boolean; datasetId: string }
  inputFilter: { visible: boolean; datasetId: string; previewKind?: 'dataset' | 'result' }
  filterBinding: { visible: boolean }
  python: { visible: boolean }
  preview: { visible: boolean; kind: 'dataset' | 'result' | 'component'; datasetId: string | null; tab?: string }
}

/* ============================ [MOCK] 假数据 ============================ */

// [MOCK] 仪表盘画布上的卡片。本轮只完整实现 KPI/指标卡，其余仅用于画布展示。
const MOCK_CARDS: CardItem[] = [
  { id: 'card-kpi-1', type: 'kpi', title: '策略下发概览', multiMetric: false, multiTab: false },
  { id: 'card-table-1', type: 'table', title: '策略明细表', multiMetric: false, multiTab: false },
  { id: 'card-chart-1', type: 'chart', title: '渠道趋势图', multiMetric: false, multiTab: false },
]

// [MOCK] 数据源选择树。分类节点（type=category）只展开收起，叶子节点进入配置。
export const MOCK_DATA_SOURCE_TREE = [
  {
    label: 'Aloudata',
    type: 'category',
    children: [
      { label: '指标视图', type: 'aloudata', mode: 'metric-view' },
      { label: '指标&维度', type: 'aloudata', mode: 'metric-dim' },
    ],
  },
  {
    label: 'JDBC',
    type: 'category',
    children: [
      { label: 'db1', type: 'jdbc', db: 'db1' },
      { label: 'db2', type: 'jdbc', db: 'db2' },
    ],
  },
  { label: '接口', type: 'api' },
  {
    label: '文件',
    type: 'category',
    children: [
      { label: 'Excel', type: 'file', fileType: 'Excel' },
      { label: 'CSV', type: 'file', fileType: 'CSV' },
      { label: 'TXT', type: 'file', fileType: 'TXT' },
      { label: 'JSON', type: 'file', fileType: 'JSON' },
      { label: 'Parquet', type: 'file', fileType: 'Parquet' },
    ],
  },
]

// [MOCK] Aloudata 可选指标视图（仅举例）
export const MOCK_ALOUDATA_METRIC_VIEWS = ['策略曝光视图', '转化漏斗视图', '留存矩阵视图']
// [MOCK] Aloudata 指标&维度可选项
export const MOCK_ALOUDATA_METRICS = ['曝光量', '点击量', '转化率', '下发次数']
export const MOCK_ALOUDATA_DIMS = ['策略类型', '渠道', '事件日期', '地域']

// [MOCK] JDBC 默认 SQL 模板（只读 SELECT / WITH，参数用绑定 :param）
export const MOCK_JDBC_SQL = `SELECT
  strategy_id,
  event_date,
  delivery_count,
  status
FROM strategy_table
WHERE status = :status`

// [MOCK] 文件数据集示例（上传后自动识别，这里直接给假结果）
export const MOCK_FILE_PREVIEW = {
  fileName: 'strategy_2024Q1.xlsx',
  fileType: 'Excel',
  columns: [
    { name: 'strategy_id', type: 'string' },
    { name: 'event_date', type: 'date' },
    { name: 'delivery_count', type: 'int' },
    { name: 'status', type: 'string' },
  ],
  rows: [
    { strategy_id: 'S1001', event_date: '2024-01-05', delivery_count: '1200', status: 'ACTIVE' },
    { strategy_id: 'S1002', event_date: '2024-01-08', delivery_count: '980', status: 'ACTIVE' },
    { strategy_id: 'S1003', event_date: '2024-01-12', delivery_count: '1540', status: 'PAUSED' },
    { strategy_id: 'S1004', event_date: '2024-01-15', delivery_count: '730', status: 'ACTIVE' },
    { strategy_id: 'S1005', event_date: '2024-01-20', delivery_count: '2100', status: 'ACTIVE' },
  ],
}

// [MOCK] 字段映射默认（文档示例：strategy_id→策略 I 等）
export const MOCK_FIELD_MAPPING: FieldMapping[] = [
  { source: 'strategy_id', desc: '策略 ID', target: '策略 I' },
  { source: 'event_date', desc: '事件日期', target: '事件日期' },
  { source: 'delivery_count', desc: '下发次数', target: '下发次数' },
]

// [MOCK] 输入筛选默认（文档示例）
export const MOCK_INPUT_FILTERS: InputFilter[] = [
  { field: 'event_date', op: '最近 30 天', value: '' },
  { field: 'status', op: '=', value: 'ACTIVE' },
]

// [MOCK] 筛选器绑定默认（数组：支持同时绑定多个筛选器）
// 文档示例：策略类型 -> table1.strategy_id 等；渠道 -> table1.channel 等
export const MOCK_FILTER_BINDINGS: FilterBinding[] = [
  {
    filterName: '策略类型',
    scope: { 'table1': true, 'table2': false, 'table3': true },
    fieldMap: [
      { datasetId: 'table1', field: 'strategy_id', matched: true },
      { datasetId: 'table2', field: 'strategy_code', matched: false }, // 需手动映射
      { datasetId: 'table3', field: '策略 ID', matched: true },
    ],
  },
  {
    filterName: '渠道',
    scope: { 'table1': true, 'table2': true, 'table3': false },
    fieldMap: [
      { datasetId: 'table1', field: 'channel', matched: false }, // 需手动映射
      { datasetId: 'table2', field: 'channel_code', matched: false }, // 需手动映射
      { datasetId: 'table3', field: '渠道', matched: true },
    ],
  },
]

// [MOCK] Python Base Script —— 系统生成区（只读）+ 用户处理区（可编辑）
export const MOCK_PYTHON_SYSTEM = `# ===== 系统生成区域：输入数据集和筛选绑定 =====
strategy = inputs["table1"]
users = inputs["table2"]

# strategy_id → table1.strategy_id
# strategy_id → table2.strategy_code
# 绑定筛选条件将在数据源查询阶段下推
`
export const MOCK_PYTHON_USER = `# ===== 用户处理区域 =====
result = strategy.join(users, on="user_id")
return result
`

// [MOCK] 预览弹窗假数据生成器：根据数据集/结果返回四 Tab 内容
function buildMockPreview(targetLabel: string, filters?: InputFilter[]) {
  // 原始 mock 行（输入筛选会在前端端按 filters 过滤，便于演示预览效果）
  const baseRows = [
    { strategy_id: 'S1001', event_date: '2024-01-05', delivery_count: '1200', status: 'ACTIVE' },
    { strategy_id: 'S1002', event_date: '2024-01-08', delivery_count: '980', status: 'ACTIVE' },
    { strategy_id: 'S1003', event_date: '2024-01-12', delivery_count: '1540', status: 'PAUSED' },
    { strategy_id: 'S1004', event_date: '2024-01-15', delivery_count: '730', status: 'ACTIVE' },
    { strategy_id: 'S1005', event_date: '2024-01-20', delivery_count: '2100', status: 'ACTIVE' },
    { strategy_id: 'S1006', event_date: '2024-02-02', delivery_count: '640', status: 'PAUSED' },
  ]
  const dataRows = filters && filters.length ? applyFilters(baseRows, filters) : baseRows
  return {
    // 数据预览 Tab：表格
    dataColumns: [
      { name: 'strategy_id', type: 'string' },
      { name: 'event_date', type: 'date' },
      { name: 'delivery_count', type: 'int' },
      { name: 'status', type: 'string' },
    ],
    dataRows,
    // 字段结构 Tab
    fieldStruct: [
      { name: 'strategy_id', type: 'string', desc: '策略 ID', nullable: false },
      { name: 'event_date', type: 'date', desc: '事件日期', nullable: false },
      { name: 'delivery_count', type: 'int', desc: '下发次数', nullable: true },
      { name: 'status', type: 'string', desc: '状态', nullable: false },
    ],
    // 执行信息 Tab
    execInfo: {
      inputDatasets: [targetLabel],
      queryConditions: 'status = :status AND event_date >= :start',
      pushedFilters: (filters && filters.length ? filters : [{ field: 'event_date', op: '最近 30 天', value: '' }, { field: 'status', op: '=', value: 'ACTIVE' }]).map(
        (f) => `${f.field} ${f.op}${f.value ? ' ' + f.value : ''}`,
      ),
      scanned: '1,204,330 行',
      returned: `${dataRows.length} 行`,
      pythonTime: '12 ms',
      inRows: baseRows.length,
      outRows: dataRows.length,
      error: '',
    },
    // 处理日志 Tab
    logs: [
      { time: '10:58:01.201', level: 'INFO', msg: `开始查询数据源 ${targetLabel}` },
      { time: '10:58:01.244', level: 'INFO', msg: '下推筛选条件：event_date 最近 30 天' },
      { time: '10:58:01.318', level: 'INFO', msg: `数据源返回 ${dataRows.length} 行` },
      { time: '10:58:01.330', level: 'INFO', msg: 'Python 处理耗时 12ms' },
    ],
  }
}

/* ============================ 状态单例 ============================ */

let aliasSeq = 0
function nextAlias() {
  aliasSeq += 1
  return `table${aliasSeq}`
}

const state = reactive({
  cards: MOCK_CARDS as CardItem[],
  activeCardId: 'card-kpi-1',
  datasets: [] as DatasetConfig[],
  // Python 预处理
  hasPython: false,
  pythonSystem: '' as string,
  pythonUser: '' as string,
  // 筛选器绑定（支持同时绑定多个筛选器）
  filterBindings: [] as FilterBinding[],
  // [后端联调] 与 mateclaw-dataagent 的联动状态
  backend: {
    dashboardId: '', // 后端仪表盘 ID
    componentId: '', // 当前卡片对应的后端组件 ID
    online: false, // 是否已成功对接后端
    loading: false, // 加载/保存中
    running: false, // Python 执行中
    lastError: '', // 最近一次错误
    executionId: '', // 最近一次执行 ID
  },
  ui: {
    treeVisible: false,
    editingDatasetId: null as string | null,
    jdbc: { visible: false, db: 'db1', sql: MOCK_JDBC_SQL },
    aloudata: { visible: false, mode: 'metric-dim', metricView: '', metrics: [], dims: [] },
    api: { visible: false, host: 'https://api.example.com', path: '/v1/strategies', method: 'POST', timeout: 5000, headers: '', params: '' },
    file: { visible: false, fileType: 'Excel', fileName: '', columns: [], rows: [] },
    fieldMapping: { visible: false, datasetId: '' },
    inputFilter: { visible: false, datasetId: '', previewKind: 'dataset' },
    filterBinding: { visible: false },
    python: { visible: false },
    preview: { visible: false, kind: 'dataset' as 'dataset' | 'result' | 'component', datasetId: null as string | null, tab: 'data' },
  } as UiState,
})

/* ============================ 计算属性 ============================ */

const activeCard = computed(() => state.cards.find((c) => c.id === state.activeCardId)!)
const isKpiCard = computed(() => activeCard.value.type === 'kpi')
const datasetCount = computed(() => state.datasets.length)
// 2 个及以上数据集时 Python 用户处理区必填；1 个时可选
const pythonRequired = computed(() => state.datasets.length >= 2)

function getDataset(id: string) {
  return state.datasets.find((d) => d.id === id)
}

/* ============================ Actions ============================ */

function selectCard(id: string) {
  state.activeCardId = id
  // 切换卡片时清空当前配置（原型：当前卡片即配置目标，切换=换目标）
  // 注：真实场景应按卡片分别存储；这里演示单卡片配置流。
  state.datasets = []
  state.hasPython = false
  state.pythonSystem = ''
  state.pythonUser = ''
  state.filterBindings = []
}

function openDataSourceTree() {
  state.ui.editingDatasetId = null
  state.ui.treeVisible = true
}

function closeDataSourceTree() {
  state.ui.treeVisible = false
}

/** 数据源树叶子被点击 → 打开对应配置弹窗，并回填（若是编辑已有数据集） */
function onSelectLeaf(node: any) {
  const type = node.type as DataSourceLeafType
  if (type === 'jdbc') {
    const existing = state.ui.editingDatasetId ? getDataset(state.ui.editingDatasetId) : undefined
    state.ui.jdbc = {
      visible: true,
      db: node.db,
      sql: existing?.jdbc?.sql ?? MOCK_JDBC_SQL,
    }
  } else if (type === 'aloudata') {
    const existing = state.ui.editingDatasetId ? getDataset(state.ui.editingDatasetId) : undefined
    state.ui.aloudata = {
      visible: true,
      mode: node.mode,
      metricView: existing?.aloudata?.metricView ?? '',
      metrics: existing?.aloudata?.metrics ?? [],
      dims: existing?.aloudata?.dims ?? [],
    }
  } else if (type === 'api') {
    const existing = state.ui.editingDatasetId ? getDataset(state.ui.editingDatasetId) : undefined
    state.ui.api = {
      visible: true,
      host: existing?.api?.host ?? 'https://api.example.com',
      path: existing?.api?.path ?? '/v1/strategies',
      method: existing?.api?.method ?? 'POST',
      timeout: existing?.api?.timeout ?? 5000,
      headers: existing?.api?.headers ?? '',
      params: existing?.api?.params ?? '',
    }
  } else if (type === 'file') {
    const existing = state.ui.editingDatasetId ? getDataset(state.ui.editingDatasetId) : undefined
    state.ui.file = {
      visible: true,
      fileType: node.fileType,
      fileName: existing?.file?.fileName ?? '',
      columns: existing?.file?.columns ?? [],
      rows: existing?.file?.rows ?? [],
    }
  }
  // 进入对应弹窗后，关闭选择树（文档：选完后关闭数据源选择树）
  state.ui.treeVisible = false
}

/** 从配置弹窗确认 → 新增或更新数据集 */
function commitDataset(payload: Partial<DatasetConfig> & { sourceType: DataSourceLeafType; sourceLabel: string }) {
  const editingId = state.ui.editingDatasetId
  if (editingId) {
    const ds = getDataset(editingId)
    if (ds) Object.assign(ds, payload)
  } else {
    const id = `ds-${Date.now()}`
    state.datasets.push({
      id,
      sourceType: payload.sourceType,
      sourceLabel: payload.sourceLabel,
      alias: nextAlias(),
      fieldMapping: JSON.parse(JSON.stringify(MOCK_FIELD_MAPPING)),
      filters: JSON.parse(JSON.stringify(MOCK_INPUT_FILTERS)),
      ...payload,
    } as DatasetConfig)
  }
  state.ui.editingDatasetId = null
}

function removeDataset(id: string) {
  const idx = state.datasets.findIndex((d) => d.id === id)
  if (idx >= 0) state.datasets.splice(idx, 1)
}

/**
 * 点击数据集卡片的「数据源」标题 → 重开该数据集【之前】的配置弹窗并回填，
 * 而非重新打开数据源选择树（用户反馈红框1）。
 * 根据已保存的 sourceType 路由到对应弹窗。
 */
function reconfigureDataset(id: string) {
  const ds = getDataset(id)
  if (!ds) return
  state.ui.editingDatasetId = id
  if (ds.sourceType === 'jdbc') {
    state.ui.jdbc = { visible: true, db: ds.jdbc?.db ?? 'db1', sql: ds.jdbc?.sql ?? MOCK_JDBC_SQL }
  } else if (ds.sourceType === 'aloudata') {
    state.ui.aloudata = {
      visible: true,
      mode: ds.aloudata?.mode ?? 'metric-dim',
      metricView: ds.aloudata?.metricView ?? '',
      metrics: ds.aloudata?.metrics ?? [],
      dims: ds.aloudata?.dims ?? [],
    }
  } else if (ds.sourceType === 'api') {
    state.ui.api = {
      visible: true,
      host: ds.api?.host ?? 'https://api.example.com',
      path: ds.api?.path ?? '/v1/strategies',
      method: ds.api?.method ?? 'POST',
      timeout: ds.api?.timeout ?? 5000,
      headers: ds.api?.headers ?? '',
      params: ds.api?.params ?? '',
    }
  } else if (ds.sourceType === 'file') {
    state.ui.file = {
      visible: true,
      fileType: ds.file?.fileType ?? 'Excel',
      fileName: ds.file?.fileName ?? '',
      columns: ds.file?.columns ?? [],
      rows: ds.file?.rows ?? [],
    }
  }
}

/** 转义正则特殊字符（用于按别名精确替换脚本引用） */
function escapeRegExp(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 修改数据集别名（用户反馈红框3）。写回 alias，并同步：
 *   - Python Base Script 中的 inputs["旧别名"] / inputs['旧别名'] 引用
 *   - 筛选器绑定里以别名为 key 的作用范围与字段映射
 * （真实后端应以 datasetId 为稳定主键，此处按原型 mock 同步别名引用。）
 */
function renameDataset(id: string, newAlias: string) {
  const ds = getDataset(id)
  if (!ds) return
  const oldAlias = ds.alias
  const clean = (newAlias ?? '').trim()
  if (!clean || clean === oldAlias) return

  // 同步 Python 脚本引用
  if (state.pythonSystem.includes(`inputs["${oldAlias}"]`) || state.pythonSystem.includes(`inputs['${oldAlias}']`)) {
    const re = new RegExp(`inputs\\[["']${escapeRegExp(oldAlias)}["']\\]`, 'g')
    state.pythonSystem = state.pythonSystem.replace(re, `inputs["${clean}"]`)
    state.pythonUser = state.pythonUser.replace(re, `inputs["${clean}"]`)
  }

  // 同步筛选器绑定（仅当绑定以别名为 key 时），遍历所有已绑定筛选器
  if (state.filterBindings && state.filterBindings.length) {
    state.filterBindings.forEach((b) => {
      if (b.scope[oldAlias] !== undefined) {
        b.scope[clean] = b.scope[oldAlias]
        delete b.scope[oldAlias]
      }
      b.fieldMap.forEach((m) => {
        if (m.datasetId === oldAlias) m.datasetId = clean
      })
    })
  }

  ds.alias = clean
}

/* ---- JDBC ---- */
function openJdbc(db: string) {
  state.ui.jdbc = { visible: true, db, sql: MOCK_JDBC_SQL }
}
function confirmJdbc() {
  const { db, sql } = state.ui.jdbc
  commitDataset({
    sourceType: 'jdbc',
    sourceLabel: `JDBC · ${db}`,
    jdbc: { db, sql },
  })
  state.ui.jdbc.visible = false
}

/* ---- Aloudata ---- */
function confirmAloudata() {
  const { mode, metricView, metrics, dims } = state.ui.aloudata
  const label = mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度'
  commitDataset({
    sourceType: 'aloudata',
    sourceLabel: label,
    aloudata: { mode, metricView, metrics: [...metrics], dims: [...dims] },
  })
  state.ui.aloudata.visible = false
}

/* ---- 接口 ---- */
function confirmApi() {
  const a = state.ui.api
  commitDataset({
    sourceType: 'api',
    sourceLabel: '接口',
    api: { host: a.host, path: a.path, method: a.method, timeout: a.timeout, headers: a.headers, params: a.params },
  })
  state.ui.api.visible = false
}

/* ---- 文件 ---- */
function confirmFile() {
  const f = state.ui.file
  commitDataset({
    sourceType: 'file',
    sourceLabel: '文件',
    file: { fileName: f.fileName || MOCK_FILE_PREVIEW.fileName, fileType: f.fileType, columns: f.columns.length ? f.columns : MOCK_FILE_PREVIEW.columns, rows: f.rows.length ? f.rows : MOCK_FILE_PREVIEW.rows },
  })
  state.ui.file.visible = false
}

/* ---- 字段映射 ---- */
function openFieldMapping(id: string) {
  state.ui.fieldMapping = { visible: true, datasetId: id }
}
function saveFieldMapping(list: FieldMapping[]) {
  const ds = getDataset(state.ui.fieldMapping.datasetId)
  if (ds) ds.fieldMapping = list
  state.ui.fieldMapping.visible = false
}

/* ---- 输入筛选 ---- */
function openInputFilter(id?: string, opts?: { previewKind?: 'dataset' | 'result' }) {
  state.ui.inputFilter = { visible: true, datasetId: id ?? '', previewKind: opts?.previewKind ?? 'dataset' }
}
function saveInputFilter(list: InputFilter[]) {
  const ds = getDataset(state.ui.inputFilter.datasetId)
  if (ds) ds.filters = list
  state.ui.inputFilter.visible = false
}

/* ---- 筛选器绑定（支持多个） ---- */
function openFilterBinding() {
  if (!state.filterBindings || state.filterBindings.length === 0) {
    // 用已有 MOCK 初始化（首开），真实场景应从服务端拉取作用范围候选
    state.filterBindings = JSON.parse(JSON.stringify(MOCK_FILTER_BINDINGS))
    // 作用范围候选基于当前数据集（原型：默认全选）
    state.filterBindings.forEach((b) => {
      const scope: Record<string, boolean> = {}
      state.datasets.forEach((d) => (scope[d.id] = true))
      b.scope = scope
    })
  }
  state.ui.filterBinding.visible = true
}
function saveFilterBindings(arr: FilterBinding[]) {
  state.filterBindings = arr
  state.ui.filterBinding.visible = false
}

/* ---- Python 预处理 ---- */
function openPython() {
  if (!state.hasPython) {
    state.pythonSystem = MOCK_PYTHON_SYSTEM
    state.pythonUser = MOCK_PYTHON_USER
    state.hasPython = true
  }
  state.ui.python.visible = true
}
function savePython(system: string, user: string) {
  state.pythonSystem = system
  state.pythonUser = user
  state.hasPython = true
  state.ui.python.visible = false
}
function removePython() {
  // 只移除用户处理脚本，不删除输入数据集
  state.pythonUser = ''
  state.pythonSystem = ''
  state.hasPython = false
}

/* ---- 预览 ---- */
function openPreview(kind: 'dataset' | 'result' | 'component', datasetId: string | null = null, tab = 'data') {
  state.ui.preview = { visible: true, kind, datasetId, tab }
}
function closePreview() {
  state.ui.preview.visible = false
}

/* ===================== 后端对接（mateclaw-dataagent） ===================== */
/**
 * 说明：本节把原型本地状态与后端「组件级 datasetPipeline」互相转换，
 * 并对接真实仪表盘 CRUD / 组件执行接口。组件层无需感知序列化细节。
 */

/** 原型数据源类型 → 后端 DatasetSourceType（JDBC_SQL / ALOUDATA_* / HTTP_API / FILE） */
function mapSourceTypeOut(ds: DatasetConfig): string {
  if (ds.sourceType === 'jdbc') return 'JDBC_SQL'
  if (ds.sourceType === 'aloudata') return ds.aloudata?.mode === 'metric-dim' ? 'ALOUDATA_METRICS' : 'ALOUDATA_ANALYSIS_VIEW'
  if (ds.sourceType === 'api') return 'HTTP_API'
  return 'FILE'
}
/** 后端 sourceType → 原型数据源类型 */
function mapSourceTypeIn(s?: string | null): DataSourceLeafType {
  const v = (s || '').toUpperCase()
  if (v.includes('JDBC')) return 'jdbc'
  if (v.includes('ALOUDATA')) return 'aloudata'
  if (v.includes('HTTP') || v.includes('API')) return 'api'
  if (v.includes('FILE')) return 'file'
  return 'jdbc'
}

/** 本地状态 → 组件级 datasetPipeline（后端契约） */
function buildPipeline(): ComponentDatasetPipeline {
  const datasetInputs: DashboardDatasetInput[] = state.datasets.map((ds) => ({
    datasetId: ds.backendDatasetId ?? ds.id,
    inputName: ds.alias,
    displayName: ds.alias,
    sourceType: mapSourceTypeOut(ds),
    sourceConfig: {
      datasourceId: ds.jdbc?.db,
      sql: ds.jdbc?.sql,
      analysisViewId: ds.aloudata?.metricView,
      apiDefinitionId: ds.api?.path,
      objectId: ds.file?.fileName,
    },
    fieldMappings: ds.fieldMapping.map((m) => ({ source: m.source, target: m.target })),
    filters: ds.filters as unknown as DashboardDatasetInput['filters'],
  }))

  const scriptFilterBindings: DashboardScriptFilterBinding[] = state.filterBindings.map((b, i) => {
    const inScope = state.datasets.filter((ds) => b.scope[ds.id] ?? b.scope[ds.alias])
    const fieldMappings: Record<string, string> = {}
    inScope.forEach((ds) => {
      const hit = b.fieldMap.find((m) => m.datasetId === ds.id || m.datasetId === ds.alias)
      if (hit?.field) fieldMappings[ds.alias] = hit.field
    })
    return { filterComponentId: `filter-${i}`, inputNames: inScope.map((ds) => ds.alias), fieldMappings }
  })

  return {
    datasetInputs,
    scriptFilterBindings,
    script: state.pythonUser || state.pythonSystem || undefined,
    parameters: [],
    executionPolicy: {},
  }
}

/** 本地状态 → 后端仪表盘 Schema（卡片组件 + 筛选器组件） */
function buildSchema(): InsightDashboardSchema {
  const pipeline = buildPipeline()
  const card = state.cards.find((c) => c.id === state.activeCardId) ?? state.cards[0]
  const componentId = state.backend.componentId || `card-${card.id}`
  const cardComponent: InsightComponent = {
    id: componentId,
    type: card.type === 'kpi' ? 'kpi' : card.type === 'table' ? 'table' : 'chart',
    title: card.title,
    position: { x: 0, y: 0, w: 12, h: 8 },
    multiKpi: card.multiMetric,
    renderType: card.type === 'kpi' ? 'kpi' : card.type === 'table' ? 'table' : 'echarts',
  }
  const filterComponents: InsightComponent[] = state.filterBindings.map((b, i) => ({
    id: `filter-${i}`,
    type: 'filter',
    title: b.filterName,
    position: { x: 0, y: 0, w: 6, h: 2 },
    config: { field: b.filterName, optionSource: 'static', scope: 'scoped' },
  }))
  return {
    version: '1.1',
    pages: [
      {
        id: 'page_0',
        name: '卡片配置',
        order: 0,
        components: [backend.withPipeline(cardComponent, pipeline), ...filterComponents],
      },
    ],
    datasetInputs: pipeline.datasetInputs,
    script: pipeline.script,
    scriptFilterBindings: pipeline.scriptFilterBindings,
    parameters: [],
    executionPolicy: {},
  }
}

/** 后端 Schema → 本地状态（打开时回显已保存配置） */
function applyPipeline(resp: InsightDashboardSchema): void {
  const components = resp.pages?.[0]?.components ?? []
  const cardComp = components.find((c) => c.type !== 'filter')
  const pipeline = backend.pipelineOf(cardComp)
  const inputs = pipeline?.datasetInputs ?? resp.datasetInputs ?? []
  state.datasets = inputs.map((inp, i) => ({
    id: inp.datasetId ? String(inp.datasetId) : `ds-${i}`,
    backendDatasetId: inp.datasetId ? String(inp.datasetId) : undefined,
    sourceType: mapSourceTypeIn(inp.sourceType),
    sourceLabel: String(inp.sourceType ?? '数据集'),
    alias: inp.inputName || `table${i + 1}`,
    fieldMapping: (inp.fieldMappings ?? []).map((m) => ({ source: m.source, desc: m.source, target: m.target })),
    filters: (inp.filters ?? []) as unknown as InputFilter[],
    jdbc: inp.sourceConfig?.sql
      ? { db: String(inp.sourceConfig.datasourceId ?? ''), sql: inp.sourceConfig.sql }
      : undefined,
  }))
  const script = pipeline?.script ?? resp.script ?? ''
  state.pythonUser = script
  state.pythonSystem = ''
  state.hasPython = !!script

  const filterComps = components.filter((c) => c.type === 'filter')
  const bindings = pipeline?.scriptFilterBindings ?? resp.scriptFilterBindings ?? []
  state.filterBindings = bindings.map((b, i) => {
    const scope: Record<string, boolean> = {}
    state.datasets.forEach((ds) => (scope[ds.id] = (b.inputNames ?? []).includes(ds.alias)))
    const fieldMap: FilterBindingFieldMap[] = state.datasets.map((ds) => ({
      datasetId: ds.id,
      field: b.fieldMappings?.[ds.alias] ?? '',
      matched: !!b.fieldMappings?.[ds.alias],
    }))
    return { filterName: filterComps[i]?.title || `筛选器${i + 1}`, scope, fieldMap }
  })
}

/** 打开原型时对接后端：找到/创建仪表盘并回显已保存配置 */
async function bootstrapDashboard(): Promise<boolean> {
  if (!backend.hasAuth()) {
    state.backend.lastError = '未登录：请先登录后再联调后端'
    return false
  }
  state.backend.loading = true
  try {
    const id = await backend.ensurePrototypeDashboard()
    state.backend.dashboardId = id
    const schema = await backend.loadDashboardSchema(id)
    const cardComp = schema.pages?.[0]?.components?.find((c) => c.type !== 'filter')
    state.backend.componentId = cardComp?.id ?? ''
    applyPipeline(schema)
    state.backend.online = true
    state.backend.lastError = ''
    return true
  } catch (e) {
    state.backend.online = false
    state.backend.lastError = (e as Error)?.message || '后端对接失败'
    return false
  } finally {
    state.backend.loading = false
  }
}

/** 保存当前卡片配置到后端 Schema */
async function saveDashboard(): Promise<boolean> {
  if (!state.backend.dashboardId && !(await bootstrapDashboard())) return false
  state.backend.loading = true
  try {
    const schema = buildSchema()
    if (!state.backend.componentId) state.backend.componentId = schema.pages[0].components[0].id
    await backend.saveDashboardSchema(state.backend.dashboardId, schema)
    state.backend.lastError = ''
    return true
  } catch (e) {
    state.backend.lastError = (e as Error)?.message || '保存失败'
    return false
  } finally {
    state.backend.loading = false
  }
}

/** 提交并执行该卡片的 Python 预处理（先保存 Schema，再执行） */
async function runComponentPreview(): Promise<{ ok: boolean; message: string }> {
  if (!(await saveDashboard())) return { ok: false, message: state.backend.lastError }
  state.backend.running = true
  try {
    const { executionId } = await backend.submitComponentExecution(
      state.backend.dashboardId,
      state.backend.componentId,
    )
    state.backend.executionId = executionId
    return { ok: true, message: executionId }
  } catch (e) {
    const msg = (e as Error)?.message || '执行提交失败'
    state.backend.lastError = msg
    return { ok: false, message: msg }
  } finally {
    state.backend.running = false
  }
}

/* ---- 输入筛选条件应用到预览数据（mock 端过滤，便于演示「筛选预览」效果） ---- */
function parseDate(s: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '')
  if (!m) return null
  const t = new Date(`${m[1]}-${m[2]}-${m[3]}T00:00:00`).getTime()
  return Number.isNaN(t) ? null : t
}

function matchOneFilter(
  row: Record<string, string>,
  f: InputFilter,
  rows: Record<string, string>[],
): boolean {
  const raw = row[f.field]
  const op = f.op
  if (op === 'is null') return raw === null || raw === undefined || raw === ''
  if (op === 'is not null') return !(raw === null || raw === undefined || raw === '')
  const v = (f.value ?? '').trim()
  const cell = raw === null || raw === undefined ? '' : String(raw)
  switch (op) {
    case '=':
      return cell === v
    case '!=':
      return cell !== v
    case 'contains':
      return v ? cell.includes(v) : true
    case 'in':
      return v
        .split(',')
        .map((x) => x.trim())
        .filter(Boolean)
        .includes(cell)
    case 'between': {
      const parts = v.split(',').map((x) => x.trim())
      const a = Number(parts[0])
      const b = Number(parts[1])
      const n = Number(cell)
      if (Number.isNaN(n)) return false
      return n >= a && n <= b
    }
    case '最近 N 天': {
      // [MOCK] 基准取数据集中该字段最大日期（真实场景应下推到源查询）
  const times = rows
    .map((r) => parseDate(String(r[f.field])))
    .filter((x): x is number => x !== null)
      if (!times.length) return true
      const max = Math.max(...times)
      const threshold = max - (Number(v) || 30) * 86400000
      const t = parseDate(String(raw))
      if (t === null) return false
      return t >= threshold
    }
    default:
      return true
  }
}

function applyFilters(
  rows: Record<string, string>[],
  filters: InputFilter[],
): Record<string, string>[] {
  if (!filters || !filters.length) return rows
  return rows.filter((row) => filters.every((f) => matchOneFilter(row, f, rows)))
}

function getPreviewPayload() {
  // datasetId 为空时（如各配置弹窗的「筛选预览」），回退到最新添加的数据集
  let ds = state.ui.preview.datasetId ? getDataset(state.ui.preview.datasetId) : null
  if (!ds && state.datasets.length) ds = state.datasets[state.datasets.length - 1]
  const label = ds ? ds.sourceLabel : '最终结果集'
  // 应用该数据集已配置的筛选条件（mock 端过滤），使「筛选预览」所见即所得
  const filters = ds ? ds.filters : undefined
  return buildMockPreview(label, filters)
}

export function useInsight() {
  return {
    state,
    // computed
    activeCard,
    isKpiCard,
    datasetCount,
    pythonRequired,
    getDataset,
    getPreviewPayload,
    // card
    selectCard,
    // tree
    openDataSourceTree,
    closeDataSourceTree,
    onSelectLeaf,
    // dataset
    commitDataset,
    removeDataset,
    reconfigureDataset,
    renameDataset,
    // jdbc
    openJdbc,
    confirmJdbc,
    // aloudata
    confirmAloudata,
    // api
    confirmApi,
    // file
    confirmFile,
    // field mapping
    openFieldMapping,
    saveFieldMapping,
    // input filter
    openInputFilter,
    saveInputFilter,
    // filter binding
    openFilterBinding,
    saveFilterBindings,
    // python
    openPython,
    savePython,
    removePython,
    // preview
    openPreview,
    closePreview,
    // 后端对接（mateclaw-dataagent）
    bootstrapDashboard,
    saveDashboard,
    runComponentPreview,
  }
}
