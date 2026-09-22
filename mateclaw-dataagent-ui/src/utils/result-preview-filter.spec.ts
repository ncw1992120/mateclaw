import { describe, expect, it } from 'vitest'
import { applyResultFilters } from './result-preview-filter'

describe('applyResultFilters', () => {
  const rows = [
    { metric_id: 'm1', attribution_strategy_id: 's1', conversion: 10, channel: '自然流量' },
    { metric_id: 'm2', attribution_strategy_id: 's2', conversion: 20, channel: '付费流量' },
    { metric_id: 'm3', attribution_strategy_id: 's1', conversion: 30, channel: '自然流量-其他' },
  ]

  it('支持等于、数值比较和包含文本', () => {
    expect(applyResultFilters(rows, [{ field: 'attribution_strategy_id', op: '=', value: 's1' }])).toHaveLength(2)
    expect(applyResultFilters(rows, [{ field: 'conversion', op: '>=', value: '20' }])).toHaveLength(2)
    expect(applyResultFilters(rows, [{ field: 'channel', op: 'contains', value: '自然' }])).toHaveLength(2)
  })

  it('多个条件按 AND 组合，并支持介于和包含于', () => {
    expect(applyResultFilters(rows, [
      { field: 'conversion', op: 'between', value: '10, 30' },
      { field: 'metric_id', op: 'in', value: 'm1, m3' },
    ])).toEqual([rows[0], rows[2]])
  })

  it('空条件不改变 Python 最终结果', () => {
    expect(applyResultFilters(rows, [])).toEqual(rows)
  })
})
