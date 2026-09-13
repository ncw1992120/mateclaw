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
})
