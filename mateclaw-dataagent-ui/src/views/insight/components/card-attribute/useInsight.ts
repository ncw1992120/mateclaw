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
import * as datasourceApi from '@/api/datasource'
import { listAloudataMetrics, listAloudataDimensions } from '@/api/semantic-model'
import {
  fieldMetasFromMappings,
  normalizeLegacyFieldRefs,
  reconcileFieldMetas,
  resolveFieldLabel,
  resolveFieldName,
  toFieldMappings,
  validateFieldMetas,
  type DatasetFieldMeta,
  type DatasetSchemaField,
} from '@/utils/field-mapping'
import type { ComponentDatasetPipeline, ComponentResultSet, DashboardDatasetInput, DashboardScriptFilterBinding, DashboardScriptFilterCondition, DatasetFilter, InsightComponent, InsightDashboardSchema, KpiMetricConfig } from '@/types'
import { buildKpiMetrics, syncMetricStylesToAll } from '@/utils/kpi-metrics'
import {
  composeExecutionScript,
  effectiveSystemCode,
  fingerprintSystemSource,
  generateSystemScript,
  reconcileSystemScript,
  restoreGenerated,
  type PythonSystemSource,
} from '@/utils/python-script-template'

/* ============================ 类型定义 ============================ */

export type DataSourceLeafType = 'jdbc' | 'aloudata' | 'api' | 'file'
export type CardType = 'kpi' | 'table' | 'chart'

/** 输入筛选条件（当前数据集查询条件，应下推到源查询） */
export interface InputFilter {
  /** 字段名（技术主键、下推唯一依据；**不存展示名**） */
  field: string
  op: string // 操作符：=、!=、contains、in、between、is null、is not null
  value: string // 条件值
}

/** 已添加的数据集配置（每个数据集一张独立表单） */
export interface DatasetConfig {
  id: string
  sourceType: DataSourceLeafType
  sourceLabel: string // 例："JDBC · db1"
  alias: string // 数据集别名，例：table1
  /**
   * 字段注册表（唯一事实源）：字段名不可变，展示名 / 单位在此唯一登记。
   * 「字段名称」弹窗与「指标配置」弹窗读写的是同一份注册表 → 一处改、处处生效。
   * 契约见 docs/策略解读/字段名与展示名契约-实施计划.md。
   */
  fields: DatasetFieldMeta[]
  /**
   * 数据集 schema 缓存：后端**最近一次返回的原始清单**（不可变输入），
   * 仅用于 diff（新增 / 消失字段）与重绑提示，不承接用户编辑。
   */
  schema?: DatasetSchemaField[]
  filters: InputFilter[]
  /** 存量归一后仍未识别的字段引用（决策 4：面板顶部告警，引导手动修复） */
  unresolvedFields?: string[]
  // 各类型专属配置（由对应配置弹窗填充，均来自真实后端）
  jdbc?: { db: string; sql: string }
  aloudata?: { mode: 'metric-dim' | 'metric-view'; datasourceId?: string; metricView?: string; metrics?: string[]; dims?: string[] }
  api?: { host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file?: {
    fileName: string
    fileType: string
    objectId?: string
    /** 受控文件引用（StoredFileRef 原样回传）；草稿预览与 schema 预取需要 */
    fileRef?: Record<string, unknown>
    columns: { name: string; type: string }[]
    rows: Record<string, string>[]
  }
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
  /** 筛选器绑定到数据集后的运行时条件模板，值在查看数据时临时注入。 */
  conditions?: DashboardScriptFilterCondition[]
}

interface UiState {
  treeVisible: boolean // 数据源选择树弹窗
  editingDatasetId: string | null // 正在编辑（重新配置）的数据集 id；null=新增
  // 各配置弹窗状态
  jdbc: { visible: boolean; db: string; sql: string }
  aloudata: { visible: boolean; mode: 'metric-dim' | 'metric-view'; datasourceId: string; metricView: string; metrics: string[]; dims: string[] }
  api: { visible: boolean; host: string; path: string; method: string; timeout: number; headers: string; params: string }
  file: {
    visible: boolean
    fileType: string
    fileName: string
    objectId?: string
    fileRef?: Record<string, unknown>
    columns: { name: string; type: string }[]
    rows: Record<string, string>[]
  }
  fieldMapping: { visible: boolean; datasetId: string }
  filterBinding: { visible: boolean }
  python: { visible: boolean }
  preview: {
    visible: boolean
    kind: 'dataset' | 'result' | 'component'
    datasetId: string | null
    tab?: string
    /** 表头显示字段名（默认关：显示展示名，悬停 tooltip 显示字段名） */
    showFieldNames?: boolean
  }
  // KPI 指标分组弹窗
  metricConfig: { visible: boolean }
  metricStyle: { visible: boolean; fieldKey: string; field: string }
  /** 「查看数据」弹窗：定义 / 筛选条件 / 参数 / 结果 —— 条件由用户显式「添加」后点查询下推 */
  dataDialog: { visible: boolean; datasetId: string }
}

/* ============================ 结果集（卡片唯一数据来源） ============================ */

/**
 * 结果集状态机的五个状态（契约见 docs/策略解读/卡片数据链路与结果集交互方案-讨论稿.md §4）：
 *
 *   empty ──添加数据集──▶ stale ──生成──▶ running ──成功──▶ ready
 *                          ▲                │
 *                          │                └──失败──▶ failed
 *                          └──────配置变更─────┘
 *
 * 「改完配置结果集就旧了，刷新一下就新了」——无脚本时同步零成本，自动刷新；
 * 有脚本时是异步 Runner 执行，等用户点「生成结果集」。
 */
export type ResultSetStatus = 'empty' | 'stale' | 'running' | 'ready' | 'failed'

/** 结果集运行期状态：持久化只落元数据（无 rows），行数据靠回读或重算获得 */
export interface ResultSetState {
  status: ResultSetStatus
  source: 'dataset' | 'script'
  /** 结果集字段结构：指标配置的候选字段唯一来源 */
  columns: { name: string; type: string }[]
  /** 行数据（运行期内存态，不写入 Schema） */
  rows: Record<string, unknown>[]
  rowCount: number
  /** 生成时间（ISO 字符串） */
  generatedAt: string
  elapsedMs: number
  /** 脚本结果的执行 ID（source=script 时用于回读行数据） */
  executionId: string
  error: string
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

/**
 * 绑定筛选器的运行时下推辅助代码（随系统区域生成，供 datasets.read 使用）。
 *
 * 契约：页面筛选值经执行参数注入 `datasets.params`（key = 筛选器标题）。
 * operator="auto"（默认）按值形状自适应：
 *   - 字符串含 % 或 * → contains（模糊查询；后端编译为 LIKE '%value%'，* 归一为 %）
 *   - 列表 → in；{start,end} 字典 → between；其余标量 → eq
 * 未选值（None/空串/空列表）时不下推该条件，读全量。
 * 绑定若需强制某种匹配方式，可在生成处传显式 operator（如 "contains"）覆盖自适应。
 */
const PYTHON_FILTER_RUNTIME = `def _bind_param(name):
    # 页面筛选值由执行参数注入（datasets.params）；本地无 Runner 时 datasets 为 None
    return datasets.params.get(name) if datasets is not None else None

def _cond(field, name, operator="auto"):
    """绑定筛选器 → datasets.read 的 filters 条件（列表）；未选值时返回 []。"""
    v = _bind_param(name)
    if v is None or v == "" or (isinstance(v, (list, tuple)) and len(v) == 0):
        return []
    if operator == "auto":
        # 模糊查询：值自带 % 或 * 通配符时按 contains 下推（LIKE '%value%'）
        if isinstance(v, str) and ("%" in v or "*" in v):
            return [{"field": field, "operator": "contains", "value": v.replace("*", "%")}]
        operator = "in" if isinstance(v, (list, tuple)) else ("between" if isinstance(v, dict) else "eq")
    return [{"field": field, "operator": operator, "value": v}]`

export function buildPythonSystemRegion(): string {
  const lines = ['# ===== 系统生成区域：输入数据集和筛选绑定', '']
  const hasBoundFilters = state.filterBindings.some((b) =>
    state.datasets.some((ds) => b.scope[ds.id] && b.fieldMap.some((m) => m.datasetId === ds.id && m.field)),
  )
  if (hasBoundFilters) {
    lines.push(PYTHON_FILTER_RUNTIME, '')
  }
  state.datasets.forEach((ds) => {
    // 绑定到该数据集的筛选器：fieldMap 命中真实字段名的才生成下推条件
    const bound: { name: string; field: string; conditions: DashboardScriptFilterCondition[] }[] = []
    state.filterBindings.forEach((b) => {
      if (!b.scope[ds.id]) return
      const hit = b.fieldMap.find((m) => m.datasetId === ds.id)
      if (!hit?.field) return
      const explicitConditions = (b.conditions ?? []).filter((condition) =>
        (condition.inputName === ds.alias || b.inputNames?.includes(condition.inputName)) && condition.field,
      )
      bound.push({ name: b.filterName, field: hit.field, conditions: explicitConditions })
    })
    if (bound.length) {
      bound.forEach((b) => {
        lines.push(`# 筛选器「${b.name}」→ ${toIdentifier(ds.alias)}.${b.field}（经 datasets.params 注入，未选值时不下推）`)
      })
      const condArgs = bound.flatMap((b) => {
        if (b.conditions.length) {
          return b.conditions.map((condition) =>
            `_cond(${JSON.stringify(condition.field)}, ${JSON.stringify(condition.parameterNames[0])}, ${JSON.stringify(condition.operator)})`,
          )
        }
        return [`_cond(${JSON.stringify(b.field)}, ${JSON.stringify(b.name)})`]
      }).join(' + ')
      lines.push(
        `${toIdentifier(ds.alias)} = datasets.read(`,
        `    input_name=${JSON.stringify(ds.alias)},`,
        '    columns=[],',
        `    filters=${condArgs},`,
        ').to_polars()',
      )
    } else {
      lines.push(
        `${toIdentifier(ds.alias)} = datasets.read(`,
        `    input_name=${JSON.stringify(ds.alias)},`,
        '    columns=[],',
        '    filters=[],',
        ').to_polars()',
      )
    }
    lines.push('')
  })
  return lines.join('\n')
}

export function currentPythonSource(): PythonSystemSource {
  const inputs: DashboardDatasetInput[] = state.datasets.map((ds) => ({
    datasetId: ds.backendDatasetId ?? ds.id,
    inputName: ds.alias,
    displayName: ds.alias,
    sourceType: mapSourceTypeOut(ds),
    filters: ds.filters as unknown as DashboardDatasetInput['filters'],
  }))
  const bindings: DashboardScriptFilterBinding[] = state.filterBindings.map((binding) => {
    const scoped = state.datasets.filter((ds) => binding.scope[ds.id] ?? binding.scope[ds.alias])
    const fieldMappings: Record<string, string> = {}
    scoped.forEach((ds) => {
      const hit = binding.fieldMap.find((field) => field.datasetId === ds.id || field.datasetId === ds.alias)
      if (hit?.field) fieldMappings[ds.alias] = hit.field
    })
    return {
      filterComponentId: binding.filterName,
      inputNames: scoped.map((ds) => ds.alias),
      fieldMappings,
      conditions: binding.conditions ?? [],
    }
  })
  return { inputs, bindings }
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
  pythonSystemState: null as import('@/utils/python-script-template').ReconciledSystemScript | null,
  // 筛选器绑定（支持同时绑定多个筛选器）
  filterBindings: [] as FilterBinding[],
  // KPI 指标分组（由结果集字段逐列投影；由 hydratePanel 灌入、指标配置弹窗编辑）
  kpiMetrics: [] as KpiMetricConfig[],
  // 仪表盘可用筛选器组件（来自筛选器绑定弹窗的真实参数名来源；由 hydratePanel 注入）
  filterCatalog: [] as { id: string; title: string }[],
  // 结果集：卡片唯一数据来源（数据集 / 筛选 / 脚本都只是产出它的手段）
  resultSet: {
    status: 'empty',
    source: 'dataset',
    columns: [],
    rows: [],
    rowCount: 0,
    generatedAt: '',
    elapsedMs: 0,
    executionId: '',
    error: '',
  } as ResultSetState,
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
    file: { visible: false, fileType: 'Excel', fileName: '', objectId: '', fileRef: undefined, columns: [], rows: [] },
    fieldMapping: { visible: false, datasetId: '' },
    filterBinding: { visible: false },
    python: { visible: false },
    preview: {
      visible: false,
      kind: 'dataset' as 'dataset' | 'result' | 'component',
      datasetId: null as string | null,
      tab: 'data',
      showFieldNames: false,
    },
    metricConfig: { visible: false },
    metricStyle: { visible: false, fieldKey: '', field: 'value' },
    dataDialog: { visible: false, datasetId: '' },
  } as UiState,
})

/* ============================ 计算属性 ============================ */

const activeCard = computed(() => state.cards.find((c) => c.id === state.activeCardId)!)
const isKpiCard = computed(() => activeCard.value.type === 'kpi')
const datasetCount = computed(() => state.datasets.length)
// 2 个及以上数据集时 Python 用户处理区必填；1 个时可选
const pythonRequired = computed(() => state.datasets.length >= 2)

/** 结果集是否可用于渲染卡片 */
const resultSetReady = computed(() => state.resultSet.status === 'ready')
/** 结果集是否已过期（输入配置变更后；卡片沿用旧渲染并提示刷新） */
const resultSetStale = computed(() => state.resultSet.status === 'stale')
/** 是否自动刷新结果集：无脚本时同步零成本自动跑；有脚本要起 Runner，等用户点按钮 */
const resultSetAuto = computed(() => !state.hasPython)
/** 结果集来源标签 */
const resultSetSourceLabel = computed(() => (state.resultSet.source === 'script' ? 'Python 输出' : '数据集直通'))
/** 结果集是否已有过一次产出（含已过期，用于决定是否展示旧数据行数） */
const resultSetHasOutput = computed(() => ['ready', 'stale', 'failed'].includes(state.resultSet.status))

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
  resetResultSet('empty')
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
      objectId: existing?.file?.objectId ?? '',
      fileRef: existing?.file?.fileRef,
      columns: existing?.file?.columns ?? [],
      rows: existing?.file?.rows ?? [],
    }
  }
  // 进入对应弹窗后，关闭选择树（文档：选完后关闭数据源选择树）
  state.ui.treeVisible = false
}

/** 从配置弹窗确认 → 新增或更新数据集，返回数据集 id（新增时立即异步预取 schema） */
function commitDataset(payload: Partial<DatasetConfig> & { sourceType: DataSourceLeafType; sourceLabel: string }): string {
  const editingId = state.ui.editingDatasetId
  let id: string
  if (editingId) {
    const ds = getDataset(editingId)
    if (ds) Object.assign(ds, payload)
    id = editingId
  } else {
    id = `ds-${Date.now()}`
    state.datasets.push({
      id,
      sourceType: payload.sourceType,
      sourceLabel: payload.sourceLabel,
      alias: nextAlias(),
      fields: [],
      filters: [],
      ...payload,
    } as DatasetConfig)
  }
  state.ui.editingDatasetId = null
  // 配置确定后异步预取数据集 schema，自动生成「字段名称」映射；
  // 不阻塞主流程，失败静默（打开弹窗时仍会兜底重试）。
  void refreshDatasetSchema(id, { silent: true })
  return id
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
      objectId: ds.file?.objectId ?? '',
      fileRef: ds.file?.fileRef,
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
    file: {
      fileName: f.fileName,
      fileType: f.fileType,
      objectId: f.objectId,
      fileRef: f.fileRef,
      columns: f.columns,
      rows: f.rows,
    },
  })
  state.ui.file.visible = false
}

/* ---- 数据集 schema（「字段名称」自动填充） ---- */
/**
 * 「字段名称」弹窗的字段不再手工新增，而是由数据集 schema 自动生成：
 *   配置确定 → 异步预取 schema 并生成映射行；打开弹窗时若缓存为空再兜底拉一次。
 * schema 全部来自后端草稿预览（JDBC / Aloudata 指标&维度 / 指标视图 / 文件均返回字段结构），
 * 显示名来自 Aloudata 语义层（指标/维度显示名、指标视图定义），无显示名时回落源字段名。
 * 契约见 docs/策略解读/原型设计.md §4.1。
 */

/** 从草稿预览响应取字段名列表（优先 schema，回落首行 key） */
function readSchemaNames(batch: { schema?: string[]; rows?: Record<string, unknown>[] } | null | undefined): string[] {
  if (!batch) return []
  if (batch.schema?.length) return batch.schema.map((n) => String(n)).filter(Boolean)
  const first = batch.rows?.[0]
  return first ? Object.keys(first) : []
}

/** 从候选键里取第一个非空值（Aloudata 视图定义为平台原始 Map，键名不稳定） */
function pickKey(obj: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = obj[key]
    if (value !== undefined && value !== null && String(value).trim()) return String(value).trim()
  }
  return ''
}

/** 指标视图定义 → 名称/显示名映射 */
function viewDisplayNameMap(detail: unknown): Record<string, string> {
  const map: Record<string, string> = {}
  const source = detail as { metrics?: unknown[]; dimensions?: unknown[] } | null
  const take = (list: unknown[] | undefined) => {
    ;(list ?? []).forEach((item) => {
      if (!item || typeof item !== 'object') return
      const row = item as Record<string, unknown>
      const name = pickKey(row, ['metricName', 'dimName', 'name', 'code', 'fieldName'])
      const display = pickKey(row, ['metricDisplayName', 'dimDisplayName', 'displayName', 'display_name', 'label', 'title', 'comment'])
      if (name && display) map[name] = display
    })
  }
  take(source?.metrics)
  take(source?.dimensions)
  return map
}

/**
 * 已同步语义层的名称 → 显示名映射（失败只影响描述列，不影响字段名）。
 * 后端 `aloudata/synced-metrics|dimensions` 返回列表（同时兼容 { records } 分页结构）；
 * 单次取 1000 条，超出部分回落源字段名作为目标名称。
 */
async function aloudataLabelMap(
  datasourceId: string,
  names: string[],
  kind: 'metric' | 'dimension',
): Promise<Record<string, string>> {
  if (!datasourceId || !names.length) return {}
  try {
    const res = (kind === 'metric'
      ? await listAloudataMetrics(datasourceId, 1, 1000)
      : await listAloudataDimensions(datasourceId, 1, 1000)) as unknown
    const records: Record<string, unknown>[] = Array.isArray(res)
      ? (res as Record<string, unknown>[])
      : ((res as { records?: Record<string, unknown>[] })?.records ?? [])
    const map: Record<string, string> = {}
    records.forEach((row) => {
      const name = kind === 'metric' ? row.metricName : row.dimName
      const display = kind === 'metric' ? row.metricDisplayName : row.dimDisplayName
      if (name && display) map[String(name)] = String(display)
    })
    return map
  } catch {
    return {}
  }
}

/** 文件草稿来源配置：objectId（后端校验用）+ fileRef（真实读取用的受控引用） */
function fileSourceConfig(file: NonNullable<DatasetConfig['file']>): Record<string, unknown> {
  return {
    objectId: file.objectId,
    fileName: file.fileName,
    format: file.fileType,
    ...(file.fileRef ? { fileRef: file.fileRef } : {}),
  }
}

/** 该数据集的配置是否足以拉取字段结构（接口类型需先登记受控接口定义，暂不支持） */
function canFetchDatasetSchema(ds: DatasetConfig | undefined): boolean {
  if (!ds) return false
  if (ds.sourceType === 'jdbc') return !!(ds.jdbc?.db && ds.jdbc?.sql)
  if (ds.sourceType === 'aloudata') {
    if (!ds.aloudata?.datasourceId) return false
    return ds.aloudata.mode === 'metric-view'
      ? !!ds.aloudata.metricView
      : !!(ds.aloudata.metrics?.length || ds.aloudata.dims?.length)
  }
  if (ds.sourceType === 'file') return !!ds.file?.objectId
  return false
}

/** 按数据集来源拉取真实字段结构（字段名 + 显示名） */
async function fetchDatasetSchema(ds: DatasetConfig): Promise<DatasetSchemaField[]> {
  if (ds.sourceType === 'jdbc') {
    if (!ds.jdbc?.db || !ds.jdbc?.sql) return []
    const batch = await backend.previewDatasetDraft({
      sourceType: 'JDBC_SQL',
      datasourceId: ds.jdbc.db,
      sourceConfig: { sql: ds.jdbc.sql },
      filters: [],
    })
    return readSchemaNames(batch).map((name) => ({ name }))
  }

  if (ds.sourceType === 'aloudata') {
    const datasourceId = ds.aloudata?.datasourceId
    if (!datasourceId) return []
    if (ds.aloudata?.mode === 'metric-view') {
      const view = ds.aloudata.metricView ?? ''
      if (!view) return []
      // 指标视图字段：一次拿全「字段名 / 展示名 / 描述 / 角色」
      // （后端聚合视图详情 + 指标批量详情 + 维度列表）
      const fields = await datasourceApi.listAnalysisViewFields(datasourceId, view)
      if (fields.length) {
        return fields.map((f) => ({
          name: f.name,
          displayName: f.displayName,
          description: f.description,
          role: f.role,
        }))
      }
      // 兜底：字段接口未返回时，退回「预览列名 + 视图展示名映射」
      const batch = await backend.previewDatasetDraft({
        sourceType: 'ALOUDATA_ANALYSIS_VIEW',
        datasourceId,
        sourceConfig: { analysisViewId: view },
        filters: [],
      })
      let labels: Record<string, string> = {}
      try {
        labels = viewDisplayNameMap(await datasourceApi.getAnalysisView(datasourceId, view))
      } catch {
        labels = {}
      }
      return readSchemaNames(batch).map((name) => ({ name, displayName: labels[name] }))
    }
    const metrics = ds.aloudata?.metrics ?? []
    const dims = ds.aloudata?.dims ?? []
    if (!metrics.length && !dims.length) return []
    const batch = await backend.previewDatasetDraft({
      sourceType: 'ALOUDATA_METRICS',
      datasourceId,
      sourceConfig: { metrics, dimensions: dims },
      filters: [],
    })
    const [metricLabels, dimLabels] = await Promise.all([
      aloudataLabelMap(datasourceId, metrics, 'metric'),
      aloudataLabelMap(datasourceId, dims, 'dimension'),
    ])
    return readSchemaNames(batch).map((name) => ({
      name,
      displayName: metricLabels[name] ?? dimLabels[name],
      // 命中维度映射的视为维度，供「筛选预览」预置维度筛选条件
      role: dimLabels[name] ? 'dimension' : 'measure',
    }))
  }

  if (ds.sourceType === 'file') {
    if (!ds.file?.objectId) return []
    const batch = await backend.previewDatasetDraft({ sourceType: 'FILE', sourceConfig: fileSourceConfig(ds.file) })
    return readSchemaNames(batch).map((name) => ({ name }))
  }

  // 接口类型：需先登记受控接口定义（含数据源与地址白名单），原型阶段不做草稿预览
  return []
}

/**
 * 刷新数据集 schema 并按增量合并规则重建字段映射：
 * 用户改过的目标名称保留，未改动的跟随最新显示名，新增字段追加、消失字段移除。
 * silent=true 时用于后台预取，不向外抛出错误（调用方自行决定提示）。
 */
async function refreshDatasetSchema(
  id: string,
  opts?: { silent?: boolean },
): Promise<{ ok: boolean; message: string }> {
  const ds = getDataset(id)
  if (!ds) return { ok: false, message: '未找到数据集' }
  if (!canFetchDatasetSchema(ds)) {
    return { ok: false, message: '当前配置无法获取字段结构，请先完成数据源配置' }
  }
  try {
    const schema = await fetchDatasetSchema(ds)
    if (!schema.length) return { ok: false, message: '未获取到字段结构，请检查数据源或查询条件' }
    // ds.schema 作为「上一版后端清单」参与 diff，用于判断用户是否手工改过展示名
    ds.fields = reconcileFieldMetas(schema, ds.fields, ds.schema)
    ds.schema = schema
    return { ok: true, message: `已获取 ${schema.length} 个字段` }
  } catch (e) {
    return { ok: false, message: (e as Error)?.message || '获取字段结构失败' }
  }
}

/* ---- 字段注册表（唯一事实源） ---- */
/** 在全部数据集中按字段名查找注册表条目 */
export function findFieldMeta(name: string): DatasetFieldMeta | undefined {
  const key = (name ?? '').trim()
  if (!key) return undefined
  for (const ds of state.datasets) {
    const hit = (ds.fields ?? []).find((f) => f.name === key)
    if (hit) return hit
  }
  return undefined
}

/** 某字段所属数据集的注册表（供展示名唯一性校验构造候选集） */
function findOwnerFields(name: string): DatasetFieldMeta[] | undefined {
  const key = (name ?? '').trim()
  for (const ds of state.datasets) {
    if ((ds.fields ?? []).some((f) => f.name === key)) return ds.fields
  }
  return undefined
}

/**
 * 取（必要时创建）某字段的注册表条目 —— 指标配置弹窗的写入口。
 * 字段不在任何数据集注册表中时（如 schema 尚未拉取），落到第一个数据集，保证双入口写同一份。
 */
export function ensureFieldMeta(name: string): DatasetFieldMeta | undefined {
  const key = (name ?? '').trim()
  if (!key) return undefined
  const found = findFieldMeta(key)
  if (found) return found
  const ds = state.datasets[0]
  if (!ds) return undefined
  if (!ds.fields) ds.fields = []
  const created: DatasetFieldMeta = { name: key }
  ds.fields.push(created)
  return created
}

/** 跨数据集把老引用（展示名 / 旧目标名）归一为字段名 */
export function normalizeFieldRef(ref: string): string {
  const key = (ref ?? '').trim()
  if (!key) return key
  for (const ds of state.datasets) {
    const resolved = resolveFieldName(ds.fields, key)
    if (resolved !== key) return resolved
  }
  return key
}

/** 字段引用 → 渲染标签（跨数据集解析，未命中回退字段名） */
export function labelOfField(name: string): string {
  const key = (name ?? '').trim()
  if (!key) return key
  for (const ds of state.datasets) {
    if ((ds.fields ?? []).some((f) => f.name === key)) return resolveFieldLabel(ds.fields, key)
  }
  return key
}

/**
 * 写展示名（决策 1 唯一性拦截）：先写入注册表，再具化到 kpiMetrics 镜像。
 * 返回错误文案（null 表示写入成功）。
 */
export function setFieldDisplayName(name: string, value: string): string | null {
  const meta = ensureFieldMeta(name)
  if (!meta) return null
  const next = (value ?? '').trim()
  // 用「同数据集内的整份注册表 + 本次改动」做唯一性预检，避免写出重名展示名
  const draft = findOwnerFields(name) ?? [meta]
  const candidate = draft.map((f) => (f.name === meta.name ? { ...f, displayName: next || undefined } : f))
  const error = validateFieldMetas(candidate)
  if (error) return error
  meta.displayName = next || undefined
  syncKpiMetricsFromFields()
  return null
}

/** 写单位（字段级展示配置），并具化到 kpiMetrics 镜像 */
export function setFieldUnit(name: string, value: string): void {
  const meta = ensureFieldMeta(name)
  if (!meta) return
  meta.unit = (value ?? '').trim() || undefined
  syncKpiMetricsFromFields()
}

/* ---- 字段名称弹窗（注册表编辑器） ---- */
function openFieldMapping(id: string) {
  state.ui.fieldMapping = { visible: true, datasetId: id }
}

/** 保存字段注册表；返回可展示的错误文案，null 表示保存成功并已关闭弹窗 */
function saveFieldMetas(list: DatasetFieldMeta[]): string | null {
  const normalized = list.map((f) => ({
    ...f,
    name: (f.name ?? '').trim(),
    displayName: (f.displayName ?? '').trim() || undefined,
    unit: (f.unit ?? '').trim() || undefined,
  }))
  const error = validateFieldMetas(normalized)
  if (error) return error
  const ds = getDataset(state.ui.fieldMapping.datasetId)
  if (ds) {
    ds.fields = normalized
    // 展示名可能刚变 → 立即具化指标镜像，保证「指标配置」里同步生效
    syncKpiMetricsFromFields()
  }
  state.ui.fieldMapping.visible = false
  return null
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
  // generated 模式可自动刷新；managed 模式只更新候选版本，不覆盖用户接管代码。
  const source = currentPythonSource()
  if (!state.pythonSystemState) {
    const generatedCode = generateSystemScript(source)
    state.pythonSystemState = { mode: 'generated', generatedCode, generatedFingerprint: fingerprintSystemSource(source), userCode: state.pythonUser }
  } else {
    state.pythonSystemState = reconcileSystemScript(state.pythonSystemState, source)
  }
  state.pythonSystem = effectiveSystemCode(state.pythonSystemState)
  if (!state.hasPython) {
    state.pythonUser = ''
    state.hasPython = true
  }
  state.ui.python.visible = true
}
function savePython(system: string, user: string) {
  state.pythonSystem = system
  state.pythonUser = user
  const source = currentPythonSource()
  const current = state.pythonSystemState ?? {
    mode: 'generated' as const,
    generatedCode: system,
    generatedFingerprint: fingerprintSystemSource(source),
    userCode: user,
  }
  state.pythonSystemState = { ...current, userCode: user }
  state.hasPython = true
  state.ui.python.visible = false
}
function removePython() {
  // 只移除用户处理脚本，不删除输入数据集
  state.pythonUser = ''
  state.pythonSystem = ''
  state.hasPython = false
  state.pythonSystemState = null
}

/* ---- 预览 ---- */
function openPreview(kind: 'dataset' | 'result' | 'component', datasetId: string | null = null, tab = 'data') {
  state.ui.preview = { visible: true, kind, datasetId, tab }
}
function closePreview() {
  state.ui.preview.visible = false
}

/** 打开「查看数据」弹窗（定义 / 筛选条件 / 结果；点查询才取数） */
function openDataDialog(datasetId: string): void {
  state.ui.dataDialog = { visible: true, datasetId }
}
function closeDataDialog(): void {
  state.ui.dataDialog.visible = false
}

/* ---- KPI 指标分组（结果集优先：指标由最终结果集字段逐列投影） ---- */

/**
 * 「最终结果集」字段（指标投影来源）。
 *
 * 结果集优先（契约见 docs/策略解读/卡片数据链路与结果集交互方案-讨论稿.md §3.1）：
 * 有结果集 schema 时，指标只能来源于结果集真实存在的列，避免「结果长什么样还不知道就配指标」；
 * 结果集尚未产出时回退到数据集字段注册表并集，作为首次配置的候选来源。
 * 展示名 / 单位始终从字段注册表解析（注册表是全站唯一登记处）。
 */
export function kpiResultFields(): DatasetFieldMeta[] {
  const rs = state.resultSet
  if ((rs.status === 'ready' || rs.status === 'stale') && rs.columns.length) {
    const registry = datasetRegistryFields()
    const byName = new Map(registry.map((f) => [f.name, f]))
    return rs.columns.map((c) => {
      const hit = byName.get(c.name)
      // 命中注册表 → 保留展示名 / 单位；脚本新产出的列尚未登记 → 用字段名兜底
      return hit ? { ...hit } : ({ name: c.name } as DatasetFieldMeta)
    })
  }
  return datasetRegistryFields()
}

/** 数据集字段注册表并集（结果集尚未产出时的回退候选来源） */
function datasetRegistryFields(): DatasetFieldMeta[] {
  const fields: DatasetFieldMeta[] = []
  state.datasets.forEach((ds) => {
    if (ds.fields?.length) {
      ds.fields.forEach((f) => fields.push({ ...f }))
      return
    }
    // 注册表尚未建立（schema 未拉取）时回落后端最近一次原始清单
    ;(ds.schema ?? []).forEach((f) => fields.push({ ...f }))
  })
  return fields
}

/** 按最新结果集字段增量重建指标（命中保留用户配置、新增追加、消失移除；结果集为空不清空） */
function rebuildKpiMetrics() {
  state.kpiMetrics = buildKpiMetrics(kpiResultFields(), state.kpiMetrics)
}

/**
 * 把注册表的展示名 / 单位具化到 kpiMetrics 镜像。
 * kpiMetrics 保留的是「指标在卡片上的投影配置」（辅助说明 / 显示 / 布局 / 样式）；
 * 展示名与单位只存在注册表一份，镜像用于画布渲染与后端持久化。
 */
function syncKpiMetricsFromFields(): void {
  if (!state.kpiMetrics.length) return
  const fields = kpiResultFields()
  const byName = new Map(fields.map((f) => [f.name, f]))
  state.kpiMetrics.forEach((m) => {
    const hit = byName.get(m.fieldKey)
    if (!hit) return
    m.displayName = resolveFieldLabel(fields, m.fieldKey)
    m.unit = (hit.unit ?? '').trim()
  })
}

/** 序列化用：返回具化后的指标副本（不改 state，避免与自动保存互相触发） */
export function materializedKpiMetrics(): KpiMetricConfig[] {
  const fields = kpiResultFields()
  const byName = new Map(fields.map((f) => [f.name, f]))
  return JSON.parse(
    JSON.stringify(
      state.kpiMetrics.map((m) => {
        const hit = byName.get(m.fieldKey)
        if (!hit) return m
        return { ...m, displayName: resolveFieldLabel(fields, m.fieldKey), unit: (hit.unit ?? '').trim() }
      }),
    ),
  ) as KpiMetricConfig[]
}

function openMetricConfig() {
  rebuildKpiMetrics()
  state.ui.metricConfig.visible = true
}

function openMetricStyle(fieldKey: string, field: string = 'value') {
  state.ui.metricStyle = { visible: true, fieldKey, field }
}

/** 把某指标的整套样式同步到所有指标（只同步样式，不同步文本/映射） */
function syncKpiMetricStyles(fieldKey: string) {
  state.kpiMetrics = syncMetricStylesToAll(state.kpiMetrics, fieldKey)
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

/** 数据源类型的展示名（卡片标题行「数据源」右侧显示） */
export const SOURCE_LABEL: Record<string, string> = {
  jdbc: 'JDBC',
  aloudata: 'Aloudata',
  api: '接口',
  file: '文件',
}

/**
 * 后端 datasetInput → 面板数据集配置。
 * 字段注册表由后端 fieldMappings 恢复（source=字段名、target=展示名），
 * 再对引用处的老值做惰性归一（决策 4）。
 */
export function datasetFromInput(input: DashboardDatasetInput, index = 0): DatasetConfig {
  const sourceType = mapSourceTypeIn(input.sourceType)
  const rawType = String(input.sourceType ?? '').toUpperCase()
  const cfg = input.sourceConfig ?? {}
  const ds: DatasetConfig = {
    id: input.datasetId ? String(input.datasetId) : `ds-${index}`,
    backendDatasetId: input.datasetId ? String(input.datasetId) : undefined,
    sourceType,
    sourceLabel: input.displayName || SOURCE_LABEL[sourceType] || String(input.sourceType ?? ''),
    alias: input.inputName || `table${index + 1}`,
    fields: fieldMetasFromMappings(input.fieldMappings),
    filters: (input.filters ?? []) as unknown as InputFilter[],
    jdbc: cfg.sql
      ? { db: String(cfg.datasourceId ?? ''), sql: cfg.sql }
      : undefined,
  }
  // 按来源类型恢复各自配置：重开仪表盘要据 sourceConfig 原样重建查询，
  // 缺指标/维度清单等关键项时会取不出数（返回「暂不支持取数」而不是静默给空）。
  if (sourceType === 'aloudata') {
    ds.aloudata = {
      mode: rawType.includes('ANALYSIS_VIEW') ? 'metric-view' : 'metric-dim',
      datasourceId: cfg.datasourceId != null ? String(cfg.datasourceId) : undefined,
      metricView: cfg.analysisViewId != null ? String(cfg.analysisViewId) : undefined,
      metrics: cfg.metrics,
      dims: cfg.dimensions,
    }
  } else if (sourceType === 'api') {
    ds.api = {
      host: '',
      path: String(cfg.apiDefinitionId ?? ''),
      method: 'POST',
      timeout: 5000,
      headers: '',
      params: '',
    }
  } else if (sourceType === 'file') {
    ds.file = {
      fileName: String(cfg.objectId ?? ''),
      fileType: 'Excel',
      objectId: cfg.objectId != null ? String(cfg.objectId) : undefined,
      columns: [],
      rows: [],
    }
  }
  normalizeDatasetFields(ds)
  return ds
}

/**
 * 存量归一（决策 4）：把 filters 等处存歪的「展示名 / 旧目标名」反解回字段名并就地写回；
 * 未识别的引用收集到 ds.unresolvedFields，由属性面板顶部告警引导手动修复。
 */
export function normalizeDatasetFields(ds: DatasetConfig): void {
  const refs = (ds.filters ?? []).map((f) => f.field ?? '')
  const { names, unresolved } = normalizeLegacyFieldRefs(ds.fields, undefined, refs)
  ds.filters = (ds.filters ?? []).map((f, i) => ({ ...f, field: names[i] ?? f.field }))
  ds.unresolvedFields = unresolved.length ? unresolved : undefined
}

/**
 * kpiMetrics 迁移（决策 4）：fieldKey 归一为字段名；
 * 旧的展示名 / 单位写入注册表（以 kpiMetrics 现值为准，用户最后编辑的即权威），
 * 随后统一由注册表具化回镜像，完成物理归一。
 */
export function migrateKpiMetrics(metrics: KpiMetricConfig[]): KpiMetricConfig[] {
  return metrics.map((m) => {
    const fieldKey = normalizeFieldRef(m.fieldKey)
    const meta = findFieldMeta(fieldKey)
    if (meta) {
      // 冲突时以 kpiMetrics 现值为准（用户最后编辑的即权威，见契约 §7）
      const display = (m.displayName ?? '').trim()
      if (display && display !== fieldKey && display !== (meta.displayName ?? '').trim()) {
        meta.displayName = display
      }
      const unit = (m.unit ?? '').trim()
      if (unit && unit !== (meta.unit ?? '').trim()) meta.unit = unit
    }
    return { ...m, fieldKey }
  })
}

/** 组合最终脚本：系统生成区域（只读）+ 用户处理区域；用户区域为空视为未配置 Python */
function buildPipelineScript(): string | undefined {
  if (!state.hasPython) return undefined
  const scriptState = state.pythonSystemState ?? {
    mode: 'generated' as const,
    generatedCode: state.pythonSystem || buildPythonSystemRegion(),
    generatedFingerprint: fingerprintSystemSource(currentPythonSource()),
    userCode: state.pythonUser,
  }
  state.pythonSystemState = scriptState
  const script = composeExecutionScript(scriptState)
  return script || undefined
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
      // 指标 & 维度模式必须带上指标/维度清单：重开仪表盘要据此重建结果集（否则无从查询）
      metrics: ds.aloudata?.metrics,
      dimensions: ds.aloudata?.dims,
    },
    // 契约定版（§4.3）：source=字段名（后端下推唯一依据），target=展示名（仅导出表头等展示场景）
    fieldMappings: toFieldMappings(ds.fields ?? []),
    // filters[].field 自本版本起恒为字段名（存展示名的老配置在读入时已惰性归一，见 normalizeDatasetFields）
    filters: ds.filters as unknown as DashboardDatasetInput['filters'],
  }))

  const scriptFilterBindings: DashboardScriptFilterBinding[] = state.filterBindings.map((b) => {
    const inScope = state.datasets.filter((ds) => b.scope[ds.id] ?? b.scope[ds.alias])
    const fieldMappings: Record<string, string> = {}
    inScope.forEach((ds) => {
      const hit = b.fieldMap.find((m) => m.datasetId === ds.id || m.datasetId === ds.alias)
      if (hit?.field) fieldMappings[ds.alias] = hit.field
    })
    // filterComponentId 必须存画布真实筛选器组件 id（此前合成 filter-N，回显时按 id 找
    // 不到组件 → 绑定名丢失成 "filter-0"，连带 params 取值 key 失效）。
    // 找不到对应组件时回退存绑定名本身：回显侧按 title 兜底命中。
    const catalogHit = state.filterCatalog.find((f) => f.title === b.filterName)
    return {
      filterComponentId: catalogHit?.id ?? b.filterName,
      inputNames: inScope.map((ds) => ds.alias),
      fieldMappings,
      conditions: b.conditions ?? [],
    }
  })

  return {
    datasetInputs,
    scriptFilterBindings,
    script: buildPipelineScript(),
    systemScript: state.pythonSystemState ?? undefined,
    parameters: [],
    executionPolicy: {},
    // 结果集元数据随 pipeline 持久化：重开仪表盘时据此回读（有脚本）或重算（无脚本）
    resultSet: resultSetMeta(),
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
  state.datasets = inputs.map((inp, i) => datasetFromInput(inp, i))
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
    const fieldMap: FilterBindingFieldMap[] = state.datasets.map((ds) => {
      // 老绑定里存的是展示名 → 归一为字段名（决策 4），保证改名后绑定关系仍有效
      const raw = b.fieldMappings?.[ds.alias] ?? ''
      const field = raw ? normalizeFieldRef(raw) : ''
      return { datasetId: ds.id, field, matched: !!field }
    })
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
  fieldStruct: { name: string; type: string; displayName: string; desc: string; nullable: boolean }[]
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
export function draftRequestForDataset(ds: DatasetConfig): datasetApi.DatasetComposerDraftRequest | null {
  // 筛选条件存的已经是字段名（技术主键），可直接下推，无需反解展示名。
  const sourceFilters = (ds.filters ?? []) as unknown as DatasetFilter[]
  switch (ds.sourceType) {
    case 'jdbc':
      return {
        sourceType: 'JDBC_SQL',
        datasourceId: ds.jdbc?.db,
        sourceConfig: { sql: ds.jdbc?.sql },
        filters: sourceFilters,
      }
    case 'aloudata':
      return ds.aloudata?.mode === 'metric-view'
        ? { sourceType: 'ALOUDATA_ANALYSIS_VIEW', datasourceId: ds.aloudata.datasourceId, sourceConfig: { analysisViewId: ds.aloudata.metricView }, filters: sourceFilters }
        : { sourceType: 'ALOUDATA_METRICS', datasourceId: ds.aloudata.datasourceId, sourceConfig: { metrics: ds.aloudata.metrics, dimensions: ds.aloudata.dims }, filters: sourceFilters }
    case 'file':
      return ds.file?.objectId
        ? { sourceType: 'FILE', sourceConfig: fileSourceConfig(ds.file), filters: sourceFilters }
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

/**
 * 预览「字段结构」：展示名来自字段注册表（未命中回退字段名）。
 * 指定数据集时只查该数据集的注册表；结果集预览则跨数据集解析。
 */
function previewFieldStruct(
  columns: { name: string; type: string }[],
  ds?: DatasetConfig,
): PreviewPayload['fieldStruct'] {
  const fields = ds?.fields ?? state.datasets.flatMap((d) => d.fields ?? [])
  return columns.map((c) => ({
    name: c.name,
    type: c.type,
    displayName: resolveFieldLabel(fields, c.name),
    desc: (fields.find((f) => f.name === c.name)?.description ?? '').trim() || c.name,
    nullable: true,
  }))
}

/** 预览表头解析用的字段注册表：指定数据集优先，否则取全部数据集并集 */
export function previewFieldMetas(datasetId?: string | null): DatasetFieldMeta[] {
  if (datasetId) {
    const ds = getDataset(datasetId)
    if (ds?.fields?.length) return ds.fields
  }
  return state.datasets.flatMap((d) => d.fields ?? [])
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
      fieldStruct: previewFieldStruct(columns, ds),
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

/**
 * 结果集预览（弹窗的「数据预览 / 字段结构 / 执行信息 / 处理日志」四页签）。
 *
 * 与卡片共用同一个结果集：未就绪或已过期时先生成，再展示。
 * 这样预览所见即卡片所见，不再出现「弹窗里有数据、卡片却是空的」这种错位。
 */
async function loadResultPreview(): Promise<void> {
  previewState.loading = true
  previewState.error = ''
  previewState.payload = null
  try {
    // 已就绪且未过期时直接复用，避免重复执行有成本的脚本
    if (state.resultSet.status !== 'ready') {
      const { ok, message } = await generateResultSet()
      if (!ok) throw new Error(message)
    }
    const rows = state.resultSet.rows
    const columns = state.resultSet.columns
    previewState.payload = {
      dataColumns: columns,
      dataRows: rows,
      fieldStruct: previewFieldStruct(columns),
      execInfo: {
        inputDatasets: state.datasets.map((d) => d.sourceLabel),
        queryConditions: '',
        pushedFilters: [],
        scanned: '-',
        returned: `${state.resultSet.rowCount}`,
        pythonTime: state.resultSet.source === 'script'
          ? `${(state.resultSet.elapsedMs / 1000).toFixed(1)}s`
          : '-',
        inRows: state.resultSet.rowCount,
        outRows: state.resultSet.rowCount,
        error: state.resultSet.error,
      },
      logs: [
        {
          time: nowHms(),
          level: 'INFO',
          msg: `结果集来源：${state.resultSet.source === 'script' ? 'Python 输出' : '数据集直通'}（${state.resultSet.rowCount} 行）`,
        },
        ...(state.resultSet.executionId
          ? [{ time: nowHms(), level: 'INFO', msg: `executionId=${state.resultSet.executionId}` }]
          : []),
      ],
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

/* ===================== 结果集：生成 / 过期 / 持久化 ===================== */

/** 生成中的重入保护（自动刷新与手动点击可能并发） */
let resultSetRunning = false
let autoResultSetTimer: ReturnType<typeof setTimeout> | null = null

/** 清空结果集 */
function resetResultSet(status: ResultSetStatus = 'empty'): void {
  state.resultSet.status = status
  state.resultSet.columns = []
  state.resultSet.rows = []
  state.resultSet.rowCount = 0
  state.resultSet.generatedAt = ''
  state.resultSet.elapsedMs = 0
  state.resultSet.executionId = ''
  state.resultSet.error = ''
}

/**
 * 输入配置变更：已有结果集标记过期（保留旧行数据供卡片沿用），失败态回到空态。
 * 本函数不取数——取数由 scheduleResultSet 或用户点按钮触发。
 */
export function markResultSetStale(): void {
  const s = state.resultSet.status
  if (s === 'ready') {
    state.resultSet.status = 'stale'
  } else if (s === 'failed') {
    // 上次失败后又改了配置：没有可沿用的数据，回到空态
    state.resultSet.status = 'empty'
  }
}

/**
 * 配置变更入口：标记过期，并在「无脚本」时防抖自动生成。
 * 有脚本时不自动跑（Runner 执行有真实成本），等用户点「生成结果集」。
 */
export function scheduleResultSet(): void {
  // 数据集被清空：没有输入就没有结果集，回到空态（而不是「已过期」）
  if (!state.datasets.length) {
    resetResultSet('empty')
    return
  }
  markResultSetStale()
  if (!resultSetAuto.value) return
  if (autoResultSetTimer) clearTimeout(autoResultSetTimer)
  autoResultSetTimer = setTimeout(() => {
    autoResultSetTimer = null
    void generateResultSet()
  }, 800)
}

/** 产物提交：写入结果集并置为就绪（status 最后置位，保证观察者看到 ready 时其余字段已就绪） */
function commitResultSet(payload: {
  source: 'dataset' | 'script'
  rows: Record<string, unknown>[]
  executionId?: string
  elapsedMs: number
}): void {
  state.resultSet.source = payload.source
  state.resultSet.columns = rowsToColumns(payload.rows)
  state.resultSet.rows = payload.rows
  state.resultSet.rowCount = payload.rows.length
  state.resultSet.generatedAt = new Date().toISOString()
  state.resultSet.elapsedMs = payload.elapsedMs
  state.resultSet.executionId = payload.executionId ?? ''
  state.resultSet.error = ''
  state.resultSet.status = 'ready'
}

/** 生成失败：记录失败态与原因（是否沿用旧渲染由画布侧决定） */
function failResultSet(source: 'dataset' | 'script', message: string): void {
  state.resultSet.source = source
  state.resultSet.error = message
  state.resultSet.columns = []
  state.resultSet.rows = []
  state.resultSet.rowCount = 0
  state.resultSet.status = 'failed'
}

/**
 * 生成结果集（统一入口）。
 * 无脚本：结果集 = 单个数据集的查询结果（直通）；
 * 有脚本：结果集 = 组件级 Python 预处理的输出。
 */
async function generateResultSet(): Promise<{ ok: boolean; message: string }> {
  if (resultSetRunning) return { ok: false, message: '结果集正在生成中' }
  if (!state.datasets.length) {
    resetResultSet('empty')
    return { ok: false, message: '尚未配置数据集' }
  }
  resultSetRunning = true
  try {
    return state.hasPython ? await generateResultSetByScript() : await generateResultSetByDataset()
  } finally {
    resultSetRunning = false
  }
}

/** 无脚本：数据集直通（同步、毫秒级） */
async function generateResultSetByDataset(): Promise<{ ok: boolean; message: string }> {
  if (state.datasets.length > 1) {
    const msg = '多数据集时必须配置 Python 脚本'
    failResultSet('dataset', msg)
    return { ok: false, message: msg }
  }
  const ds = state.datasets[0]
  const started = Date.now()
  state.resultSet.status = 'running'
  state.resultSet.error = ''
  try {
    const req = draftRequestForDataset(ds)
    if (!req) throw new Error('该类型数据集暂不支持取数（需登记数据源 / 接口定义）')
    const batch = await backend.previewDatasetDraft(req)
    const rows = (batch.rows as Record<string, unknown>[] | null) ?? []
    commitResultSet({ source: 'dataset', rows, elapsedMs: Date.now() - started })
    return { ok: true, message: `${rows.length} 行` }
  } catch (e) {
    const msg = (e as Error)?.message || '生成结果集失败'
    failResultSet('dataset', msg)
    return { ok: false, message: msg }
  }
}

/** 有脚本：先保存 Schema 再提交执行，轮询取回输出 */
async function generateResultSetByScript(): Promise<{ ok: boolean; message: string }> {
  const started = Date.now()
  state.resultSet.status = 'running'
  state.resultSet.error = ''
  try {
    const { ok, message } = await runComponentPreview()
    if (!ok) throw new Error(message)
    const executionId = state.backend.executionId
    const res = await pollExecution(executionId)
    const rows = normalizeResultRows(res)
    commitResultSet({ source: 'script', rows, executionId, elapsedMs: Date.now() - started })
    return { ok: true, message: `${rows.length} 行` }
  } catch (e) {
    const msg = (e as Error)?.message || '生成结果集失败'
    failResultSet('script', msg)
    return { ok: false, message: msg }
  }
}

/** 结果集元数据 → 持久化进组件 pipeline（不含行数据；行数据靠回读或重算） */
export function resultSetMeta(): ComponentResultSet | undefined {
  const s = state.resultSet
  // running / empty 不落盘；stale 落的是「上一次成功」的元数据（保留 executionId 供重开回读）
  if (s.status === 'empty' || s.status === 'running') return undefined
  return {
    source: s.source,
    status: s.status === 'failed' ? 'failed' : 'ready',
    columns: s.columns,
    rowCount: s.rowCount,
    generatedAt: s.generatedAt,
    elapsedMs: s.elapsedMs || undefined,
    executionId: s.executionId || undefined,
    error: s.error || undefined,
  }
}

/** 从持久化元数据回填结果集（切换组件 / 重开仪表盘）；行数据留空，由回读或重算补齐 */
export function hydrateResultSet(meta?: ComponentResultSet): void {
  if (!meta) {
    resetResultSet('empty')
    return
  }
  state.resultSet.status = meta.status === 'failed' ? 'failed' : 'ready'
  state.resultSet.source = meta.source
  state.resultSet.columns = meta.columns ?? []
  state.resultSet.rows = []
  state.resultSet.rowCount = meta.rowCount ?? 0
  state.resultSet.generatedAt = meta.generatedAt ?? ''
  state.resultSet.elapsedMs = meta.elapsedMs ?? 0
  state.resultSet.executionId = meta.executionId ?? ''
  state.resultSet.error = meta.error ?? ''
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
    // 字段注册表（唯一事实源）
    openFieldMapping,
    saveFieldMetas,
    resolveFieldLabel,
    resolveFieldName,
    validateFieldMetas,
    findFieldMeta,
    ensureFieldMeta,
    normalizeFieldRef,
    labelOfField,
    setFieldDisplayName,
    setFieldUnit,
    // dataset schema（「字段名称」自动填充 + diff）
    canFetchDatasetSchema,
    refreshDatasetSchema,
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
    previewFieldMetas,
    // 全屏「查看数据」工作台
    openDataDialog,
    closeDataDialog,
    // 结果集（卡片唯一数据来源）
    resultSetReady,
    resultSetStale,
    resultSetAuto,
    resultSetSourceLabel,
    resultSetHasOutput,
    generateResultSet,
    markResultSetStale,
    scheduleResultSet,
    resetResultSet,
    hydrateResultSet,
    resultSetMeta,
    // KPI 指标分组（结果集优先投影）
    kpiResultFields,
    rebuildKpiMetrics,
    syncKpiMetricsFromFields,
    openMetricConfig,
    openMetricStyle,
    syncKpiMetricStyles,
    // 后端对接（mateclaw-dataagent）
    bootstrapDashboard,
    saveDashboard,
    runComponentPreview,
  }
}
