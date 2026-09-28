import type { InsightComponent, InsightComponentData } from '@/types'
import { buildKpiMetrics } from './kpi-metrics'

/** Python 结果预览时，让画布 KPI 临时跟随本次结果字段；不修改原组件配置。 */
export function projectPythonResultKpi(
  component: InsightComponent,
  data: InsightComponentData | undefined,
  force = false,
): InsightComponent {
  if (component.type !== 'kpi' || (!force && !data?.pythonResultPreview) || !Array.isArray(data?.kpiList)) {
    return component
  }

  const fields = data.kpiList.map((item) => {
    const name = item.fieldKey || item.name
    return {
      name,
      displayName: data.fieldLabels?.[name] || item.name || name,
      role: 'measure',
      unit: item.unit,
    }
  })

  return {
    ...component,
    kpiMetrics: fields.length ? buildKpiMetrics(fields, component.kpiMetrics ?? []) : [],
  }
}
