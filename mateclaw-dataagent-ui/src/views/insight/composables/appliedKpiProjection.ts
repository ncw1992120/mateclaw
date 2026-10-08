import type { InsightComponent, InsightComponentData } from '@/types'
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
