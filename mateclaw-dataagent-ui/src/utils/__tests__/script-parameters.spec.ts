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

  it('maps bound single-sided time conditions when component parameters are implicit', () => {
    const result = buildScriptParameters([], {
      dimensionFilters: [],
      timeRange: { preset: 'custom', start: '2026-09-01' },
    }, [{
      filterComponentId: 'time_filter_1',
      inputNames: ['orders'],
      conditions: [
        { inputName: 'orders', field: 'metric_time', operator: 'gte', parameterNames: ['startDate'], required: false },
        { inputName: 'orders', field: 'metric_time', operator: 'lt', parameterNames: ['endDate'], required: false },
      ],
    }])

    expect(result).toEqual({ startDate: '2026-09-01' })
  })

  it('maps bound dimension conditions and both time boundaries', () => {
    const result = buildScriptParameters([], {
      timeRange: { preset: 'custom', start: '2026-09-01', end: '2026-10-01' },
      dimensionFilters: [{ field: 'status', value: ['PAID', 'PENDING'] }],
    }, [{
      filterComponentId: 'filter_1',
      inputNames: ['orders'],
      conditions: [
        { inputName: 'orders', field: 'status', operator: 'in', parameterNames: ['statuses'], required: false },
        { inputName: 'orders', field: 'metric_time', operator: 'gte', parameterNames: ['startDate'], required: false },
        { inputName: 'orders', field: 'metric_time', operator: 'lt', parameterNames: ['endDate'], required: false },
      ],
    }])

    expect(result).toEqual({
      statuses: ['PAID', 'PENDING'],
      startDate: '2026-09-01',
      endDate: '2026-10-01',
    })
  })
})
