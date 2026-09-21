/**
 * [正式位置适配] 原型卡片属性面板 ↔ 正式编辑器选中组件 的桥接层。
 *
 * 背景：面板组件（AttributePanel / DatasetCard / 各弹窗）来自原型页 views/insight/prototype
 * （现 card-attribute），内部用 useInsight() 的单例 state 承载「当前卡片 + 数据集 + Python + 筛选器绑定」。
 * 正式编辑器（InsightDashboardEditorView）里这些内容存在选中组件的 config.datasetPipeline（组件级）。
 *
 * 本层只做两件事：
 *  1. hydratePanel：选中组件的 datasetPipeline + 组件字段 → 灌入面板 state（切换卡片时回显）
 *  2. buildPipeline / buildComponentPatch：面板 state → 回写组件（沿用正式编辑器的持久化口）
 */
import { readComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { effectiveSystemCode } from '@/utils/python-script-template'
import type {
  ComponentDatasetPipeline,
  ComponentTab,
  DashboardDatasetInput,
  DashboardScriptFilterBinding,
  InsightComponent,
  InsightComponentType,
} from '@/types'
import {
  buildPipeline,
  datasetFromInput,
  hydrateResultSet,
  kpiResultFields,
  mapSourceTypeIn,
  materializedKpiMetrics,
  migrateKpiMetrics,
  normalizeFieldRef,
  useInsight,
} from './useInsight'
import type { CardType, DatasetConfig, FilterBinding, InputFilter } from './useInsight'
import { buildKpiMetrics } from '@/utils/kpi-metrics'

export type { ComponentDatasetPipeline }

/**
 * 「筛选器绑定」所需的筛选器最小信息。
 * 顶层筛选器与**组合卡片容器内的子筛选器**统一成这个形状：容器内子卡片是
 * `InsightCombinationChild`，没有 `position` 等 `InsightComponent` 的必填字段，
 * 而本场景只消费 `id` / `title` / `type`。
 */
export interface PanelFilterComponent {
  id: string
  type: InsightComponentType
  title: string
}

/** 正式组件类型 → 原型卡片类型 */
function toCardType(type: unknown): CardType {
  const value = String(type)
  if (value === 'kpi') return 'kpi'
  if (value === 'table') return 'table'
  return 'chart'
}

/** pipeline.datasetInputs → 面板数据集卡片（保留后端数据集 ID，保存时回写） */
export function inputToDatasetConfig(input: DashboardDatasetInput, index: number): DatasetConfig {
  // 字段注册表 + 存量引用归一统一走 datasetFromInput，避免两处各写一份
  const ds = datasetFromInput(input, index)
  const datasetId = input.datasetId ? String(input.datasetId) : ''
  return { ...ds, id: `${datasetId || 'ds'}-${index}` }
}

/** pipeline.scriptFilterBindings → 面板筛选器绑定（作用范围按数据集逐项展开） */
function filterBindingsFromPipeline(
  bindings: DashboardScriptFilterBinding[],
  datasets: DatasetConfig[],
  filterComponents: PanelFilterComponent[],
): FilterBinding[] {
  return bindings.map((binding, index) => {
    const scope: Record<string, boolean> = {}
    datasets.forEach((ds) => {
      scope[ds.id] = (binding.inputNames ?? []).includes(ds.alias)
    })
    const fieldMap = datasets.map((ds) => {
      // 老绑定里存的是展示名 → 归一为字段名（决策 4），改名后绑定关系依然有效
      const raw = binding.fieldMappings?.[ds.alias] ?? ''
      const field = raw ? normalizeFieldRef(raw) : ''
      return { datasetId: ds.id, field, matched: Boolean(field) }
    })
    // 绑定名回显：优先按组件 id 精确匹配；历史数据可能存的是筛选器名本身
    // （保存侧找不到组件 id 时回退存名），再按 title 兜底命中。
    // 旧版合成的 filter-N id 名字已丢失：画布仅一个筛选器候选时唯一可对，自动恢复。
    const matchedComponent = filterComponents.find((c) => c.id === binding.filterComponentId)
      ?? filterComponents.find((c) => c.title === binding.filterComponentId)
      ?? (filterComponents.length === 1 && /^filter-\d+$/.test(binding.filterComponentId) ? filterComponents[0] : undefined)
    return {
      filterName: matchedComponent?.title || binding.filterComponentId || `筛选器${index + 1}`,
      scope,
      fieldMap,
      conditions: binding.conditions ?? [],
    }
  })
}

/**
 * 选中组件 → 面板状态。切换卡片 / 首次打开属性配置时调用。
 * 回显内容：组件标题、多指标模式、多Tab模式、数据集卡片、筛选器绑定、Python 脚本。
 */
export function hydratePanel(
  component: InsightComponent,
  dashboardId = '',
  filterComponents: PanelFilterComponent[] = [],
): void {
  const { state } = useInsight()
  const pipeline = readComponentDatasetPipeline(component)
  const inputs = pipeline?.datasetInputs ?? []
  const script = pipeline?.script ?? ''

  state.cards = [{
    id: component.id,
    type: toCardType(component.type),
    title: component.title || '',
    showTitle: component.showTitle !== false,
    showHeader: component.showHeader !== false,
    multiMetric: Boolean(component.multiKpi),
    multiTab: Array.isArray(component.tabs) && component.tabs.length > 0,
  }]
  state.activeCardId = component.id
  state.datasets = inputs.map(inputToDatasetConfig)
  // 系统脚本状态：新 Schema 带systemScript（generated/managed + 候选指纹），原样恢复，
  // 展示区显示实际生效代码；旧 Schema 只有 script 时整体按历史用户代码读取，不猜测拆分。
  const systemState = pipeline?.systemScript
  if (systemState) {
    state.pythonSystemState = systemState
    state.pythonSystem = effectiveSystemCode(systemState)
    state.pythonUser = systemState.userCode
    state.hasPython = Boolean(systemState.userCode.trim() || state.pythonSystem.trim())
  } else {
    state.pythonSystemState = null
    state.pythonSystem = ''
    state.pythonUser = script
    state.hasPython = Boolean(script.trim())
  }
  state.filterBindings = filterBindingsFromPipeline(pipeline?.scriptFilterBindings ?? [], state.datasets, filterComponents)
  // 仪表盘可用筛选器组件：作为「筛选器绑定」弹窗的真实参数名来源（替代此前的固定词表）
  state.filterCatalog = filterComponents.map((c) => ({ id: String(c.id), title: c.title || String(c.id) }))

  // 结果集：回填持久化元数据（行数据留空，由画布侧回读或重算补齐）。
  // 必须在 kpiMetrics 投影之前 —— 指标候选字段以结果集 schema 为准。
  hydrateResultSet(pipeline?.resultSet)

  // KPI 指标分组：由结果集字段增量投影（保留组件已有配置；结果集为空时原样保留，不清空用户配置）
  // 迁移：fieldKey 归一为字段名、旧展示名/单位写入注册表，随后由注册表统一具化
  if (component.type === 'kpi') {
    state.kpiMetrics = buildKpiMetrics(kpiResultFields(), migrateKpiMetrics(component.kpiMetrics ?? []))
  } else {
    state.kpiMetrics = []
  }

  state.backend.dashboardId = dashboardId
  state.backend.componentId = component.id
  state.backend.online = Boolean(dashboardId)
  state.backend.loading = false
  state.backend.running = false
  state.backend.lastError = ''

  // 切换卡片时收起上一张卡片残留的弹窗与编辑态
  state.ui.treeVisible = false
  state.ui.editingDatasetId = null
  state.ui.jdbc.visible = false
  state.ui.aloudata.visible = false
  state.ui.api.visible = false
  state.ui.file.visible = false
  state.ui.fieldMapping.visible = false
  state.ui.filterBinding.visible = false
  state.ui.python.visible = false
  state.ui.preview.visible = false
  state.ui.metricConfig.visible = false
  state.ui.metricStyle.visible = false
}

/** 面板状态 → 组件级 pipeline（沿用原型 buildPipeline 的字段映射） */
export function panelToPipeline(): ComponentDatasetPipeline {
  return buildPipeline()
}

/**
 * 面板状态 → 组件补丁（组件标题 / 多指标模式 / 多Tab模式）。
 * 多Tab 开启时按单数据源迁移出第一个 Tab，关闭时清空（与正式属性面板既有行为一致）。
 */
export function buildComponentPatch(component: InsightComponent): InsightComponent {
  const { state } = useInsight()
  const card = state.cards[0]
  if (!card) return component

  const patch: InsightComponent = {
    ...component,
    title: card.title,
    showTitle: card.showTitle,
    showHeader: card.showHeader,
  }
  if (card.type === 'kpi') {
    patch.multiKpi = card.multiMetric
    // 指标分组配置随面板状态整体回写（hydrate 时已按结果集投影增量合并）。
    // 展示名 / 单位以字段注册表为准具化后再持久化，保证「一处改、处处生效」落到后端与画布渲染。
    patch.kpiMetrics = materializedKpiMetrics() as InsightComponent['kpiMetrics']
  }

  const hasTabs = Array.isArray(component.tabs) && component.tabs.length > 0
  if (card.multiTab && !hasTabs) {
    const tab: ComponentTab = {
      id: `tab_${Date.now()}`,
      title: card.title || 'Tab 1',
      dataSource: component.dataSource
        ? (JSON.parse(JSON.stringify(component.dataSource)) as ComponentTab['dataSource'])
        : { datasourceId: '', metrics: [], dimensions: [], filters: [], limit: 100 },
    }
    patch.tabs = [tab]
  } else if (!card.multiTab && hasTabs) {
    patch.tabs = []
  }
  return patch
}
