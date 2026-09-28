import { describe, expect, it } from 'vitest'
import type { DashboardRuntimeFilterState, InsightComponent } from '@/types'
import { buildComponentQueryParameters } from '../dashboard-preview-query'

function component(inputs: unknown[], extraPipeline: Record<string, unknown> = {}): InsightComponent {
  return {
    id: 'component-1',
    type: 'table',
    title: 'Table',
    position: { x: 0, y: 0, w: 6, h: 4 },
    config: { datasetPipeline: { datasetInputs: inputs, ...extraPipeline } },
  } as InsightComponent
}

function state(entries: DashboardRuntimeFilterState): DashboardRuntimeFilterState {
  return entries
}

describe('buildComponentQueryParameters', () => {
  it('maps one selected filter by its id to each input declared parameter name', () => {
    const card = component([
      { datasetId: '1', inputName: 'input_a', queryConfig: { parameterBindings: [
        { filterComponentId: 'filter-region', parameterName: 'regions_a', field: 'region_a', operator: 'in' },
      ] } },
      { datasetId: '2', inputName: 'input_b', queryConfig: { parameterBindings: [
        { filterComponentId: 'filter-region', parameterName: 'regions_b', field: 'region_b', operator: 'in' },
      ] } },
      { datasetId: '3', inputName: 'unbound', queryConfig: { parameterBindings: [] } },
    ])
    const current = state({
      'filter-region': { field: 'region', value: ['north', 'south'], scope: 'global', targetComponentIds: [] },
    })

    expect(buildComponentQueryParameters(card, current)).toEqual({
      regions_a: ['north', 'south'],
      regions_b: ['north', 'south'],
    })
  })

  it('omits missing, empty, and unbound filter values instead of inferring parameters', () => {
    const card = component([
      { datasetId: '1', inputName: 'input_a', queryConfig: { parameterBindings: [
        { filterComponentId: 'filter-cleared', parameterName: 'region', field: 'region', operator: 'eq' },
        { filterComponentId: 'filter-empty', parameterName: 'channel', field: 'channel', operator: 'in' },
      ] } },
      { datasetId: '2', inputName: 'legacy', parameterBindings: [{ filterComponentId: 'filter-cleared', parameterName: 'guessed' }] },
    ], { scriptFilterBindings: [{ filterComponentId: 'filter-cleared', inputNames: ['legacy'] }] })
    const current = state({
      'filter-cleared': { field: 'region', value: undefined, scope: 'global', targetComponentIds: [] },
      'filter-empty': { field: 'channel', value: [], scope: 'global', targetComponentIds: [] },
    })

    expect(buildComponentQueryParameters(card, current)).toEqual({})
  })

  it('expands the actual date range by each declared operator and omits a cleared range', () => {
    const card = component([{ datasetId: '1', inputName: 'metrics', queryConfig: { parameterBindings: [
      { filterComponentId: 'filter-date', parameterName: 'date_from', field: 'metric_time', operator: 'gte' },
      { filterComponentId: 'filter-date', parameterName: 'date_until', field: 'metric_time', operator: 'lt' },
      { filterComponentId: 'filter-date', parameterName: 'date_between', field: 'metric_time', operator: 'between' },
    ] } }])
    const current = state({
      'filter-date': {
        field: 'metric_time',
        value: { preset: 'custom', start: '2026-09-01', end: '2026-09-02' },
        scope: 'global',
        targetComponentIds: [],
      },
    })

    expect(buildComponentQueryParameters(card, current)).toEqual({
      date_from: '2026-09-01',
      date_until: '2026-09-02',
      date_between: ['2026-09-01', '2026-09-02'],
    })
    current['filter-date'].value = undefined
    expect(buildComponentQueryParameters(card, current)).toEqual({})
  })

  it('preserves both ends of a date range when the saved query has one time-filter binding', () => {
    const card = component([{ datasetId: '1', inputName: 'metrics', queryConfig: { parameterBindings: [
      { filterComponentId: 'filter-date', parameterName: 'date_range', field: 'metric_time', operator: 'gte' },
    ] } }])
    const current = state({
      'filter-date': {
        field: 'metric_time', value: { preset: 'custom', start: '2026-09-01', end: '2026-09-03' },
        scope: 'global', targetComponentIds: [],
      },
    })

    expect(buildComponentQueryParameters(card, current)).toEqual({
      date_range: ['2026-09-01', '2026-09-03'],
    })
  })

  it('rejects conflicting filter values mapped to the same parameter name', () => {
    const card = component([{ datasetId: '1', inputName: 'input_a', queryConfig: { parameterBindings: [
      { filterComponentId: 'filter-a', parameterName: 'region', field: 'region_a', operator: 'eq' },
      { filterComponentId: 'filter-b', parameterName: 'region', field: 'region_b', operator: 'eq' },
    ] } }])
    const current = state({
      'filter-a': { field: 'region_a', value: 'north', scope: 'global', targetComponentIds: [] },
      'filter-b': { field: 'region_b', value: 'south', scope: 'global', targetComponentIds: [] },
    })

    expect(() => buildComponentQueryParameters(card, current)).toThrow(/conflicting.*region/i)
  })

  it('does not apply a scoped filter outside its declared component targets', () => {
    const card = component([{ datasetId: '1', inputName: 'input_a', queryConfig: { parameterBindings: [
      { filterComponentId: 'filter-a', parameterName: 'region', field: 'region', operator: 'eq' },
    ] } }])
    const current = state({
      'filter-a': { field: 'region', value: 'north', scope: 'scoped', targetComponentIds: ['another-component'] },
    })

    expect(buildComponentQueryParameters(card, current)).toEqual({})
  })
})
