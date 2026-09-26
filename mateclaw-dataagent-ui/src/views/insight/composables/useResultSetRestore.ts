/**
 * 结果集恢复：把组件已持久化的结果集，重新变成卡片可渲染的数据。
 *
 * 编辑器打开与展示态打开共用这一份逻辑，保证「配置态看到的」和「展示态看到的」是同一份数据：
 *  - 有脚本：按 executionId 回读后端执行结果（秒显，不重跑脚本）；
 *  - 无脚本：回源重算（同步毫秒级，等价于「数据集直通」）。
 * 取不到时返回空，由调用方决定是否保持空态（此时用户选中该卡片会自动重算）。
 */
import type {
  ComponentDatasetPipeline,
  ComponentResultSet,
  InsightComponent,
  InsightComponentData,
} from '@/types'
import * as insightDashboardApi from '@/api/insight-dashboard'
import { readComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { rowsToComponentData, type KpiProjectionField } from '@/utils/dataset-result'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import { parseScriptResultEnvelope, resultEnvelopeToComponentData } from '@/utils/script-result'
import { draftRequestForDataset } from '../components/card-attribute/useInsight'
import { inputToDatasetConfig } from '../components/card-attribute/useCardAttributeBridge'
import { previewDatasetDraft } from '../components/card-attribute/useInsightBackend'

/** 能承载结果集的数据组件类型（筛选类 / AI 分析走各自链路） */
const RESULT_SET_COMPONENT_TYPES = new Set(['kpi', 'chart', 'table'])

/** 组件渲染类型：KPI 卡走 kpi，图表走 echarts，其余走表格 */
export function renderTypeOf(component: InsightComponent): 'kpi' | 'echarts' | 'table' {
  if (component.type === 'kpi') return 'kpi'
  if (component.type === 'chart') return 'echarts'
  return 'table'
}

/** KPI 投影字段：组件上的 kpiMetrics 作为展示名 / 单位来源传给渲染器 */
export function kpiFieldsOf(component: InsightComponent): KpiProjectionField[] {
  return (component.kpiMetrics ?? []).map((metric) => ({
    fieldKey: metric.fieldKey,
    displayName: metric.displayName ?? undefined,
    unit: metric.unit ?? undefined,
  }))
}

/** 结果集行 → 组件渲染数据（卡片唯一的数据装配入口） */
export function toComponentData(
  component: InsightComponent,
  rows: Record<string, unknown>[],
  fieldLabels?: Record<string, string>,
): InsightComponentData {
  return rowsToComponentData(component.id, rows, renderTypeOf(component), kpiFieldsOf(component), fieldLabels, {
    chartType: component.chartType,
    config: component.config,
  })
}

/** 用持久化结果集字段修复旧 KPI 投影，保留已有指标的布局和样式。 */
export function reconcileKpiProjection(
  component: InsightComponent,
  columns: Array<{ name: string }>,
): void {
  if (component.type !== 'kpi' || !columns.length) return
  const existing = component.kpiMetrics ?? []
  component.kpiMetrics = buildKpiMetrics(
    columns.map(({ name }) => ({
      name,
      displayName: existing.find((metric) => metric.fieldKey === name)?.displayName,
    })),
    existing,
  )
}

/** 结果集行数据回读：脚本走执行结果，无脚本走数据集查询 */
export async function fetchResultSetRows(
  pipeline: ComponentDatasetPipeline,
  meta: ComponentResultSet,
): Promise<Record<string, unknown>[] | null> {
  if (meta.source === 'script' && meta.executionId) {
    const res = await insightDashboardApi.getExecutionResult(meta.executionId) as { envelope?: unknown }
    const envelope = parseScriptResultEnvelope(res?.envelope)
    return envelope.kind === 'table' ? envelope.data.rows : []
  }
  const input = pipeline.datasetInputs?.[0]
  if (!input) return null
  const req = draftRequestForDataset(inputToDatasetConfig(input, 0))
  if (!req) return null
  const batch = await previewDatasetDraft(req)
  return (batch.rows as Record<string, unknown>[] | null) ?? []
}

/** 统一执行结果 → 现有画布数据契约，避免展示态重新猜测行列。 */
function executionEnvelopeToComponentData(
  component: InsightComponent,
  envelopeValue: unknown,
): InsightComponentData {
  const envelope = parseScriptResultEnvelope(envelopeValue)
  const result = resultEnvelopeToComponentData({
    id: component.id,
    type: component.type,
    chartType: component.chartType,
    config: component.config,
    kpiMetrics: component.kpiMetrics,
  }, envelope)
  const renderType = renderTypeOf(component)
  if (result.state === 'message') {
    return { componentId: component.id, renderType, error: result.message }
  }
  if (result.state === 'error') {
    return { componentId: component.id, renderType, error: result.message ?? '输出不符合组件规范' }
  }
  if (result.state === 'empty') {
    return {
      componentId: component.id,
      renderType,
      fieldLabels: result.fieldLabels,
      ...(renderType === 'table' ? { table: { columns: result.columns.map((column) => column.name), rows: [] } } : {}),
    }
  }
  if (renderType === 'echarts') return { componentId: component.id, renderType, option: result.option, fieldLabels: result.fieldLabels }
  if (renderType === 'kpi') {
    const value = result.value
    const fieldKey = component.config?.valueField as string | undefined
    const kpiList = (result.kpiList ?? (value === undefined ? [] : [{ name: '值', value }])).map((item, index) => ({
      fieldKey: item.fieldKey ?? fieldKey ?? `value_${index}`,
      name: item.name,
      value: String(item.value),
      unit: component.kpiMetrics?.find((metric) => metric.fieldKey === (item.fieldKey ?? fieldKey))?.unit,
    }))
    return { componentId: component.id, renderType, kpi: kpiList[0], kpiList, fieldLabels: result.fieldLabels }
  }
  return { componentId: component.id, renderType, table: result.table, fieldLabels: result.fieldLabels }
}

/**
 * 批量恢复结果集渲染数据。
 * 只处理「有 pipeline 且成功产出过结果集」的组件；单个组件失败不影响其它组件，
 * 也不阻塞页面打开 —— 恢复不了的卡片保持空态，等用户选中时面板自动重算。
 */
export async function restoreResultSetData(
  components: InsightComponent[],
): Promise<Record<string, InsightComponentData>> {
  const out: Record<string, InsightComponentData> = {}
  await Promise.allSettled(
    components.map(async (component) => {
      if (!RESULT_SET_COMPONENT_TYPES.has(component.type)) return
      const pipeline = readComponentDatasetPipeline(component)
      const meta = pipeline?.resultSet
      if (!pipeline || !meta || meta.status !== 'ready') return
      reconcileKpiProjection(component, meta.columns)
      if (meta.source === 'script' && meta.executionId) {
        const result = await insightDashboardApi.getExecutionResult(meta.executionId) as { envelope?: unknown }
        out[component.id] = executionEnvelopeToComponentData(component, result.envelope)
        return
      }
      const rows = await fetchResultSetRows(pipeline, meta)
      if (!rows) return
      out[component.id] = toComponentData(component, rows)
    }),
  )
  return out
}
