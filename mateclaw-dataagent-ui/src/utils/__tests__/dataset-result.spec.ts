import { describe, expect, it } from 'vitest'
import { rowsToComponentData } from '@/utils/dataset-result'

describe('dataset result mapping', () => {
  it('maps rows to table component data', () => {
    const result = rowsToComponentData('table-1', [{ name: 'east', count: 2 }, { name: 'west', count: 3 }])
    expect(result.table).toEqual({ columns: ['name', 'count'], rows: [['east', '2'], ['west', '3']] })
  })

  it('maps categorical numeric rows to an ECharts series', () => {
    const result = rowsToComponentData('chart-1', [{ day: 'Mon', amount: 2 }, { day: 'Tue', amount: 4 }], 'echarts')
    expect(result.option?.xAxis).toEqual({ type: 'category', data: ['Mon', 'Tue'] })
    expect(result.option?.series).toEqual([{ name: 'amount', type: 'line', data: [2, 4] }])
  })

  it('preserves the configured pie type instead of rendering it as a line chart', () => {
    const result = rowsToComponentData(
      'chart-pie',
      [{ channel: '自然流量', users: 8 }, { channel: '广告', users: 3 }],
      'echarts',
      [],
      undefined,
      { chartType: 'pie', config: { dimensionField: 'channel', metricFields: ['users'] } },
    )
    expect(result.option?.series).toEqual([{
      type: 'pie',
      data: [{ name: '自然流量', value: 8 }, { name: '广告', value: 3 }],
    }])
  })

  it('maps the last row to KPI metrics and computes change from the previous row', () => {
    const result = rowsToComponentData(
      'kpi-1',
      [{ amount: 100 }, { amount: 120 }],
      'kpi',
      [{ fieldKey: 'amount', displayName: '销售额', unit: '元' }],
    )
    expect(result.renderType).toBe('kpi')
    expect(result.kpiList).toEqual([
      { fieldKey: 'amount', name: '销售额', value: '120', unit: '元', chg: '20.00%', up: true },
    ])
    expect(result.kpi?.fieldKey).toBe('amount')
  })

  it('falls back to all result set columns when no KPI projection is configured', () => {
    const result = rowsToComponentData('kpi-1', [{ east: 1, west: 2 }], 'kpi')
    expect(result.kpiList?.map((item) => item.fieldKey)).toEqual(['east', 'west'])
    expect(result.kpiList?.[0]).toEqual({ fieldKey: 'east', name: 'east', value: '1', unit: undefined })
    expect(result.kpiList?.[0].chg).toBeUndefined()
  })

  it('drops the change when the previous value is zero or non-numeric', () => {
    const result = rowsToComponentData('kpi-1', [{ amount: 0 }, { amount: 5 }], 'kpi')
    expect(result.kpiList?.[0].chg).toBeUndefined()
  })
})
