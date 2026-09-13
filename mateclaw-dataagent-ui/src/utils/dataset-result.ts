import type { InsightComponentData } from '@/types'

/** 将脚本返回的行集映射为现有仪表盘组件数据，不改变数据源 Schema。 */
export function rowsToComponentData(
  componentId: string,
  rows: unknown[],
  renderType: 'table' | 'echarts' = 'table',
): InsightComponentData {
  const records = rows.filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object')
  const columns = [...new Set(records.flatMap((row) => Object.keys(row)))]
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

function formatCell(value: unknown): string {
  if (value == null) return ''
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
