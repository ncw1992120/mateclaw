import type { InsightComponent, InsightComponentData } from '@/types'
import { rowsToComponentData } from './dataset-result'

export interface ComponentPreviewColumn {
  name: string
  title?: string
}

/** 将查看数据弹窗中的查询结果转换成画布现有组件可直接消费的数据结构。 */
export function componentPreviewData(
  component: InsightComponent,
  rows: Record<string, unknown>[],
  columns: ComponentPreviewColumn[],
): InsightComponentData {
  const renderType = component.type === 'kpi' ? 'kpi' : component.type === 'chart' ? 'echarts' : 'table'
  const fieldLabels = Object.fromEntries(columns.map(({ name, title }) => [name, title?.trim() || name]))
  const kpiFields = component.type === 'kpi'
    ? (component.kpiMetrics ?? []).map((metric) => ({
      fieldKey: metric.fieldKey,
      displayName: metric.displayName,
      unit: metric.unit,
    }))
    : []
  return rowsToComponentData(component.id, rows, renderType, kpiFields, fieldLabels)
}
