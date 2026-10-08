import type { InsightComponent, InsightComponentData } from '@/types'
import { readComponentDatasetPipeline, writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { buildKpiMetrics } from '@/utils/kpi-metrics'

/** Persist the KPI fields explicitly applied from a query-result preview. */
export function persistAppliedKpiProjection(component: InsightComponent, data: InsightComponentData): boolean {
  if (component.type !== 'kpi' || data.renderType !== 'kpi'
    || (!data.resultFieldProjection && !data.pythonResultPreview)) return false

  const appliedFields = (data.kpiList ?? (data.kpi ? [data.kpi] : []))
    .filter((item): item is typeof item & { fieldKey: string } => Boolean(item.fieldKey))
    .map((item) => ({ name: item.fieldKey, displayName: item.name, unit: item.unit, role: 'measure' as const }))
  if (!appliedFields.length) return false

  component.kpiMetrics = buildKpiMetrics(appliedFields, component.kpiMetrics ?? [])
  return true
}

/** Persist the metadata needed to regenerate an explicitly applied component result after reopening. */
export function persistAppliedComponentResultSet(component: InsightComponent, data: InsightComponentData): boolean {
  if (!data.resultFieldProjection || !Array.isArray(data.appliedResultRows)) return false
  const pipeline = readComponentDatasetPipeline(component)
  if (!pipeline) return false
  const source = data.appliedResultSource ?? 'dataset'
  const previousResultSet = pipeline.resultSet
  if (source === 'script' && !previousResultSet?.executionId) return false

  const rows = data.appliedResultRows
  const fieldNames = [...new Set([
    ...Object.keys(rows[0] ?? {}),
    ...Object.keys(data.fieldLabels ?? {}),
    ...(data.table?.columns ?? []),
    ...(data.kpiList ?? (data.kpi ? [data.kpi] : [])).flatMap((item) => item.fieldKey ? [item.fieldKey] : []),
  ])]
  const columns = fieldNames.map((name) => {
    const value = rows[0]?.[name]
    const type = typeof value === 'number' ? 'number' : typeof value === 'boolean' ? 'boolean' : 'string'
    return { name, type }
  })
  const updated = writeComponentDatasetPipeline(component, {
    ...pipeline,
    resultSet: {
      source,
      status: 'ready',
      columns,
      rowCount: rows.length,
      generatedAt: new Date().toISOString(),
      ...(source === 'script' && previousResultSet?.executionId ? { executionId: previousResultSet.executionId } : {}),
    },
  })
  component.config = updated.config
  return true
}
