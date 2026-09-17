/**
 * useInsight —— 洞察·仪表盘·卡片属性配置 的「前端交互状态层」
 * =====================================================================
 * 本文件集中管理：
 *   1) 全部交互状态（当前选中卡片、已添加数据集、各弹窗开关、编辑目标）。
 *   2) 全部 action（增删数据集、打开/保存各类弹窗、后端对接等）。
 *   3) 派生计算：Python 系统生成区域、预览负载（均基于真实配置，无假数据）。
 *
 * 本项目已接入真实后端（mateclaw-dataagent）：数据源树、Aloudata 指标/维度、
 * 数据集草稿预览、组件级 Python 执行、Insight 仪表盘 CRUD 均走真实接口，
 * 不再保留任何假数据。
 *
 * 设计说明：
 *   - store 为模块级单例（reactive），所有组件共享同一份状态。
 *   - 组件只负责「渲染」与「派发 action」，不持有业务数据。
 * =====================================================================
 */
import { reactive, computed } from 'vue'
import * as backend from './useInsightBackend'
import * as datasetApi from '@/api/dataset'
import type { ComponentDatasetPipeline, DashboardDatasetInput, DashboardScriptFilterBinding, DatasetFilter, InsightComponent, InsightDashboardSchema } from '@/types'

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
  // 各类型专属配置（由对应配置弹窗填充，均来自真实后端）
  jdbc?: { db: string; sql: string }
  aloudata?: { mode: 'metric-dim' | 'metric-view'; datasourceId?: string; metricView?: string; metrics?: string[]; dims?: string[] }
  api?: { host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file?: { fileName: string; fileType: string; objectId?: string; columns: { name: string; type: string }[]; rows: Record<string, string>[] }
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
  aloudata: { visible: boolean; mode: 'metric-dim' | 'metric-view'; datasourceId: string; metricView: string; metrics: string[]; dims: string[] }
  api: { visible: boolean; host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file: { visible: boolean; fileType: string; fileName: string; objectId?: string; columns: { name: string; type: string }[]; rows: Record<string, string>[] }
  fieldMapping: { visible: boolean; datasetId: string }
  inputFilter: { visible: boolean; datasetId: string; previewKind?: 'dataset' | 'result' }
  filterBinding: { visible: boolean }
  python: { visible: boolean }
  preview: { visible: boolean; kind: 'dataset' | 'result' | 'component'; datasetId: string | null; tab?: string }
}

/* ============================ 数据来源说明（已接入真实后端） ============================ */

// 画布卡片由 useCardAttributeBridge.hydratePanel 从「当前选中组件」注入，不再使用假数据。

// 数据源选择树改由 DataSourceTreeDialog 通过 datasourceApi.list() / datasetApi.list() 动态构建，无假数据。

// Aloudata 指标视图 / 指标 / 维度改由 AloudataDialog 通过 datasource.listAnalysisViews / listSyncedMetrics / listSyncedDimensions 动态拉取，无假数据。

// JDBC SQL 不再预置假模板；默认空，由用户在 SQL 编辑器中输入（只读 SELECT / WITH，参数用绑定 :param）。

// 文件数据集改由 FileConfigDialog 通过 dataset.uploadFile + previewDraft 真实预览，无假数据。

// 字段映射默认值改为空；新增/预览数据集后由真实字段回填（源=目标）。

// 输入筛选默认空；由用户按原型 §4.2 运算符枚举添加（运算符为固定枚举，非假数据）。

// 筛选器绑定默认空；由用户添加，作用范围默认全选当前数据集，字段映射按真实字段名推断（见 FilterBindingDialog）。

// Python 系统生成区域由 buildPythonSystemRegion() 根据「真实」数据集与筛选器绑定确定性生成
// （见下方函数定义，位于 Python 预处理一节）；用户处理区域由用户在编辑器中自行编写，
// 不再预置任何假样例代码。

/**
 * Python 系统生成区域：根据当前「真实」数据集与筛选器绑定确定性生成（非假数据）。
 * 设计契约见 docs/策略解读/原型设计.md §6/§7：系统区域只读、可重新生成、不覆盖用户处理区域。
 */
export const PYTHON_USER_REGION_MARKER = '# ===== 用户处理区域 ====='

/** 把数据集别名转换为合法 Python 标识符（用于系统区域变量名） */
function toIdentifier(alias: string): string {
  const cleaned = (alias || '').replace(/[^0-9A-Za-z_]/g, '_')
  if (!cleaned) return 'dataset'
  return /^[0-9]/.test(cleaned) ? `ds_${cleaned}` : cleaned
}

export function buildPythonSystemRegion(): string {
  const lines = ['# ===== 系统生成区域：输入数据集和筛选绑定', '']
  state.datasets.forEach((ds) => {
    lines.push(`${toIdentifier(ds.alias)} = inputs["${ds.alias}"]`)
  })
  lines.push('')
  if (state.filterBindings.length) {
    state.filterBindings.forEach((b) => {
      const inScope = state.datasets.filter((ds) => b.scope[ds.id])
      inScope.forEach((ds) => {
        const hit = b.fieldMap.find((m) => m.datasetId === ds.id)
        const f = hit && hit.field ? hit.field : '<未映射字段>'
        lines.push(`# ${b.filterName} → ${ds.alias}.${f}`)
      })
    })
    lines.push('# 绑定筛选条件将在数据源查询阶段下推')
    lines.push('')
  }
  return lines.join('\n')
}

/* ============================ 状态单例 ============================ */

let aliasSeq = 0
function nextAlias() {
  aliasSeq += 1
  return `table${aliasSeq}`
}

const state = reactive({
  cards: [] as CardItem[],
  activeCardId: 'card-kpi-1',
  datasets: [] as DatasetConfig[],
  // Python 预处理
  hasPython: false,
  pythonSystem: '' as string,
  pythonUser: '' as string,
  // 筛选器绑定（支持同时绑定多个筛选器）
  filterBindings: [] as FilterBinding[],
  // 仪表盘可用筛选器组件（来自筛选器绑定弹窗的真实参数名来源；由 hydratePanel 注入）
  filterCatalog: [] as { id: string; title: string }[],
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
    jdbc: { visible: false, db: '', sql: '' },
    aloudata: { visible: false, mode: 'metric-dim', datasourceId: '', metricView: '', metrics: [], dims: [] },
    api: { visible: false, host: 'https://api.example.com', path: '/v1/strategies', method: 'POST', timeout: 5000, headers: '', params: '' },
    file: { visible: false, fileType: 'Excel', fileName: '', objectId: '', columns: [], rows: [] },
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
      sql: existing?.jdbc?.sql ?? '',
    }
  } else if (type === 'aloudata') {
    const existing = state.ui.editingDatasetId ? getDataset(state.ui.editingDatasetId) : undefined
    state.ui.aloudata = {
      visible: true,
      mode: node.mode,
      datasourceId: node.db ?? existing?.aloudata?.datasourceId ?? '',
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
      fieldMapping: [],
      filters: [],
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
    state.ui.jdbc = { visible: true, db: ds.jdbc?.db ?? '', sql: ds.jdbc?.sql ?? '' }
  } else if (ds.sourceType === 'aloudata') {
    state.ui.aloudata = {
      visible: true,
      mode: ds.aloudata?.mode ?? 'metric-dim',
      datasourceId: ds.aloudata?.datasourceId ?? '',
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
 * （真实后端以 datasetId 为稳定主键；本处按原型按别名引用同步，仅作回显兼容。）
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
  state.ui.jdbc = { visible: true, db, sql: '' }
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
  const { mode, datasourceId, metricView, metrics, dims } = state.ui.aloudata
  const label = mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度'
  commitDataset({
    sourceType: 'aloudata',
    sourceLabel: label,
    aloudata: { mode, datasourceId, metricView, metrics: [...metrics], dims: [...dims] },
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
    file: { fileName: f.fileName, fileType: f.fileType, objectId: f.objectId, columns: f.columns, rows: f.rows },
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
  // 首开不预置假数据；筛选器绑定弹窗按需创建空草稿（作用范围默认全选当前数据集）
  state.ui.filterBinding.visible = true
}
function saveFilterBindings(arr: FilterBinding[]) {
  state.filterBindings = arr
  state.ui.filterBinding.visible = false
}

/* ---- Python 预处理 ---- */
function openPython() {
  // 每次打开都根据最新数据集 + 筛选器绑定重新生成系统区域；用户处理区域保留
  state.pythonSystem = buildPythonSystemRegion()
  if (!state.hasPython) {
    state.pythonUser = ''
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
export function mapSourceTypeIn(s?: string | null): DataSourceLeafType {
  const v = (s || '').toUpperCase()
  if (v.includes('JDBC')) return 'jdbc'
  if (v.includes('ALOUDATA')) return 'aloudata'
  if (v.includes('HTTP') || v.includes('API')) return 'api'
  if (v.includes('FILE')) return 'file'
  return 'jdbc'
}

/** 组合最终脚本：系统生成区域（只读）+ 用户处理区域；用户区域为空视为未配置 Python */
function buildPipelineScript(): string | undefined {
  if (!state.hasPython) return undefined
  const system = buildPythonSystemRegion()
  const user = (state.pythonUser || '').trim()
  if (!user) return undefined
  return `${system}\n\n${user}`.trim()
}

/** 本地状态 → 组件级 datasetPipeline（后端契约） */
export function buildPipeline(): ComponentDatasetPipeline {
  const datasetInputs: DashboardDatasetInput[] = state.datasets.map((ds) => ({
    datasetId: ds.backendDatasetId ?? ds.id,
    inputName: ds.alias,
    displayName: ds.alias,
    sourceType: mapSourceTypeOut(ds),
    sourceConfig: {
      datasourceId: ds.aloudata?.datasourceId ?? ds.jdbc?.db,
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
    script: buildPipelineScript(),
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
  const script = (pipeline?.script ?? resp.script ?? '').toString().trim()
  if (script) {
    const idx = script.indexOf(PYTHON_USER_REGION_MARKER)
    if (idx >= 0) {
      state.pythonSystem = script.slice(0, idx).trim()
      state.pythonUser = script.slice(idx + PYTHON_USER_REGION_MARKER.length).trim()
    } else {
      // 兼容历史数据：无标记则整体视为用户处理区域
      state.pythonSystem = ''
      state.pythonUser = script
    }
    state.hasPython = true
  } else {
    state.pythonSystem = ''
    state.pythonUser = ''
    state.hasPython = false
  }

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

/**
 * 打开时对接后端：
 *   - 传入 dashboardId → 加载指定仪表盘并回显已保存配置（真实入口场景）
 *   - 不传 dashboardId → 回退到原型仪表盘（独立预览路由 / 首次联调）
 */
async function bootstrapDashboard(dashboardId?: string): Promise<boolean> {
  if (!backend.hasAuth()) {
    state.backend.lastError = '未登录：请先登录后再联调后端'
    return false
  }
  state.backend.loading = true
  try {
    const id = dashboardId ?? (await backend.ensurePrototypeDashboard())
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

/* ===================== 预览：对接真实后端 ===================== */
/**
 * 预览结果统一结构（四 Tab）：数据预览 / 字段结构 / 执行信息 / 处理日志。
 * 全部由后端返回，本文件不再构造任何假数据。
 */
export interface PreviewPayload {
  dataColumns: { name: string; type: string }[]
  dataRows: Record<string, unknown>[]
  fieldStruct: { name: string; type: string; desc: string; nullable: boolean }[]
  execInfo: {
    inputDatasets: string[]
    queryConditions: string
    pushedFilters: string[]
    scanned: string
    returned: string
    pythonTime: string
    inRows: number
    outRows: number
    error: string
  }
  logs: { time: string; level: string; msg: string }[]
}

/** 预览状态（供 PreviewDialog 绑定） */
const previewState = reactive<{ loading: boolean; error: string; payload: PreviewPayload | null }>({
  loading: false,
  error: '',
  payload: null,
})

/** 把本地数据集配置转换为后端 DatasetComposerDraftRequest（JDBC / Aloudata / File 支持预览） */
function draftRequestForDataset(ds: DatasetConfig): datasetApi.DatasetComposerDraftRequest | null {
  switch (ds.sourceType) {
    case 'jdbc':
      return {
        sourceType: 'JDBC_SQL',
        datasourceId: ds.jdbc?.db,
        sourceConfig: { sql: ds.jdbc?.sql },
        filters: (ds.filters as unknown as DatasetFilter[]) ?? [],
      }
    case 'aloudata':
      return ds.aloudata?.mode === 'metric-view'
        ? { sourceType: 'ALOUDATA_ANALYSIS_VIEW', datasourceId: ds.aloudata.datasourceId, sourceConfig: { analysisViewId: ds.aloudata.metricView } }
        : { sourceType: 'ALOUDATA_METRICS', datasourceId: ds.aloudata.datasourceId, sourceConfig: { metrics: ds.aloudata.metrics, dimensions: ds.aloudata.dims } }
    case 'file':
      return ds.file?.objectId
        ? { sourceType: 'FILE', sourceConfig: { objectId: ds.file.objectId, fileName: ds.file.fileName, format: ds.file.fileType } }
        : null
    default:
      // API 类型需先登记 API 定义，原型暂不在此预览
      return null
  }
}

function inferType(v: unknown): string {
  if (v === null || v === undefined) return 'string'
  if (typeof v === 'number') return Number.isInteger(v) ? 'int' : 'double'
  if (typeof v === 'boolean') return 'boolean'
  return 'string'
}
function rowsToColumns(rows: Record<string, unknown>[]): { name: string; type: string }[] {
  if (!rows.length) return []
  return Object.keys(rows[0]).map((k) => ({ name: k, type: inferType(rows[0][k]) }))
}
function nowHms(): string {
  return new Date().toTimeString().slice(0, 8)
}
function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

function normalizeResultRows(res: unknown): Record<string, unknown>[] {
  if (!res) return []
  const r = res as Record<string, unknown>
  if (Array.isArray(r.rows)) return r.rows as Record<string, unknown>[]
  if (Array.isArray((r as any).dataRows)) return (r as any).dataRows as Record<string, unknown>[]
  return []
}

/** 当前数据集预览：仅执行该输入数据集的查询（对应原型「当前数据集预览」） */
async function loadDatasetPreview(datasetId: string): Promise<void> {
  const ds = getDataset(datasetId)
  previewState.loading = true
  previewState.error = ''
  previewState.payload = null
  try {
    if (!ds) throw new Error('未找到数据集')
    const req = draftRequestForDataset(ds)
    if (!req) throw new Error('该类型数据集暂不支持预览（需登记数据源/接口定义）')
    const batch = await backend.previewDatasetDraft(req)
    const rows = (batch.rows as Record<string, unknown>[] | null) ?? []
    const columns = rowsToColumns(rows)
    previewState.payload = {
      dataColumns: columns,
      dataRows: rows,
      fieldStruct: columns.map((c) => ({ name: c.name, type: c.type, desc: c.name, nullable: true })),
      execInfo: {
        inputDatasets: [ds.sourceLabel],
        queryConditions: batch.pushdownReport?.sourceQueryDigest || '',
        pushedFilters: (batch.pushdownReport?.pushedFilters ?? []).map(
          (f) => `${f.field} ${f.operator}${f.value !== undefined ? ' ' + String(f.value) : ''}`,
        ),
        scanned: batch.rowCount != null ? `${batch.rowCount}` : '-',
        returned: `${rows.length}`,
        pythonTime: '-',
        inRows: batch.rowCount ?? rows.length,
        outRows: rows.length,
        error: '',
      },
      logs: [
        { time: nowHms(), level: 'INFO', msg: `查询数据源 ${ds.sourceLabel}` },
        { time: nowHms(), level: 'INFO', msg: batch.pushdownReport?.sourceQueryDigest ? `下推查询：${batch.pushdownReport.sourceQueryDigest}` : '已返回预览数据' },
      ],
    }
  } catch (e) {
    previewState.error = (e as Error)?.message || '预览失败'
    previewState.payload = null
  } finally {
    previewState.loading = false
  }
}

/** 预处理结果预览：执行组件级 Python 预处理并轮询结果（对应原型「预处理结果预览」） */
async function loadResultPreview(): Promise<void> {
  previewState.loading = true
  previewState.error = ''
  previewState.payload = null
  try {
    const { ok, message } = await runComponentPreview()
    if (!ok) throw new Error(message)
    const executionId = state.backend.executionId
    const res = await pollExecution(executionId)
    const rows = normalizeResultRows(res)
    const columns = rowsToColumns(rows)
    previewState.payload = {
      dataColumns: columns,
      dataRows: rows,
      fieldStruct: columns.map((c) => ({ name: c.name, type: c.type, desc: c.name, nullable: true })),
      execInfo: {
        inputDatasets: state.datasets.map((d) => d.sourceLabel),
        queryConditions: '',
        pushedFilters: [],
        scanned: '-',
        returned: `${rows.length}`,
        pythonTime: '-',
        inRows: rows.length,
        outRows: rows.length,
        error: '',
      },
      logs: [{ time: nowHms(), level: 'INFO', msg: `执行组件 Python 预处理（executionId=${executionId}）` }],
    }
  } catch (e) {
    previewState.error = (e as Error)?.message || '执行失败'
  } finally {
    previewState.loading = false
  }
}

/** 轮询执行状态（最多约 20 秒） */
async function pollExecution(executionId: string, timeoutMs = 20000): Promise<unknown> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const status = await backend.fetchExecutionStatus(executionId)
    const s = String(status.status || '').toUpperCase()
    if (s === 'SUCCEEDED' || s === 'COMPLETED' || s === 'FINISHED') {
      return await backend.fetchExecutionResult(executionId)
    }
    if (s === 'FAILED' || s === 'ERROR') {
      throw new Error((status.error as string) || '执行失败')
    }
    await sleep(1000)
  }
  throw new Error('执行超时，请稍后到执行记录查看结果')
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
    buildPythonSystemRegion,
    // 预览（对接真实后端）
    previewState,
    loadDatasetPreview,
    loadResultPreview,
    // preview
    openPreview,
    closePreview,
    // 后端对接（mateclaw-dataagent）
    bootstrapDashboard,
    saveDashboard,
    runComponentPreview,
  }
}
