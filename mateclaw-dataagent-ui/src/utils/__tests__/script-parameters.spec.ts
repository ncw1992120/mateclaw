import { describe, expect, it } from 'vitest'
import { buildScriptParameters } from '@/utils/script-parameters'

describe('buildScriptParameters', () => {
  it('maps only declared dimension filters and date range parameters', () => {
    const result = buildScriptParameters([
      { name: 'region', type: 'string', scope: 'dashboard' },
      { name: 'window', type: 'date_range', scope: 'dashboard' },
    ], {
      dimensionFilters: [
        { field: 'region', value: ['east', 'west'] },
        { field: 'internal_only', value: 'secret' },
      ],
      timeRange: { preset: '7d' },
    })

    expect(result).toEqual({ region: ['east', 'west'], window: { preset: '7d' } })
  })

  it('does not guess a date range when multiple date range parameters exist', () => {
    expect(buildScriptParameters([
      { name: 'startWindow', type: 'date_range', scope: 'dashboard' },
      { name: 'compareWindow', type: 'date_range', scope: 'dashboard' },
    ], { dimensionFilters: [], timeRange: { preset: 'today' } })).toEqual({})
  })
})
