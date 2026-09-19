import type { InsightComponentData, KpiItemData } from '@/types'

/** KPI 投影配置：结果集列 → 卡片指标的映射，与组件 kpiMetrics 的 fieldKey 对齐 */
export interface KpiProjectionField {
  fieldKey: string
  displayName?: string
  unit?: string
}

/** 将脚本返回的行集映射为现有仪表盘组件数据，不改变数据源 Schema。 */
export function rowsToComponentData(
  componentId: string,
  rows: unknown[],
  renderType: 'table' | 'echarts' | 'kpi' = 'table',
  kpiFields: KpiProjectionField[] = [],
): InsightComponentData {
  const records = rows.filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object')
  const columns = [...new Set(records.flatMap((row) => Object.keys(row)))]
  if (renderType === 'kpi') {
    return { componentId, renderType: 'kpi', ...buildKpiPayload(records, columns, kpiFields) }
  }
  if (renderType === 'table') {
    return {
      componentId,
      renderType: 'table',
      table: { columns, rows: records.map((row) => columns.map((column) => formatCell(row[column]))) },
    }
  }

  const category = columns[0]
  const valueColumns = columns.slice(1).filter((column) => records.some((row) => typeof row[column] === 'number'))
  return {
    componentId,
    renderType: 'echarts',
    option: {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: records.map((row) => formatCell(row[category])) },
      yAxis: { type: 'value' },
      series: valueColumns.map((column) => ({ name: column, type: 'line', data: records.map((row) => row[column]) })),
    },
  }
}

/**
 * 结果集 → KPI 指标项。
 * <p>
 * 口径与后端 buildKpiData 保持一致：指标值取结果集**最后一行**的对应列，
 * 环比取最后两行计算。配置了 kpiMetrics 时按其 fieldKey 顺序投影（含展示名 / 单位），
 * 否则回退结果集全部列 —— 这样脚本新增的列也能立刻在卡片上看到。
 */
function buildKpiPayload(
  records: Record<string, unknown>[],
  columns: string[],
  kpiFields: KpiProjectionField[],
): { kpi?: KpiItemData; kpiList: KpiItemData[] } {
  const keys = kpiFields.length ? kpiFields.map((field) => field.fieldKey) : columns
  const last = records[records.length - 1] ?? {}
  const prev = records.length > 1 ? records[records.length - 2] : undefined

  const kpiList: KpiItemData[] = keys.map((key) => {
    const meta = kpiFields.find((field) => field.fieldKey === key)
    const item: KpiItemData = {
      fieldKey: key,
      name: meta?.displayName || key,
      value: formatCell(last[key]),
      unit: meta?.unit,
    }
    if (prev) {
      const chg = computeChange(prev[key], last[key])
      if (chg !== undefined) {
        item.chg = chg
        item.up = isUp(prev[key], last[key])
      }
    }
    return item
  })
  return { kpi: kpiList[0], kpiList }
}

/** 环比百分比（与后端同口径：前值为 0 或非数值时不展示） */
function computeChange(previous: unknown, current: unknown): string | undefined {
  const prev = Number(previous)
  const curr = Number(current)
  if (!Number.isFinite(prev) || !Number.isFinite(curr) || prev === 0) return undefined
  return `${(((curr - prev) / prev) * 100).toFixed(2)}%`
}

/** 趋势方向（涨红跌绿的着色由卡片样式负责，这里只给方向） */
function isUp(previous: unknown, current: unknown): boolean {
  const prev = Number(previous)
  const curr = Number(current)
  if (!Number.isFinite(prev) || !Number.isFinite(curr)) return true
  return curr >= prev
}

function formatCell(value: unknown): string {
  if (value == null) return ''
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
