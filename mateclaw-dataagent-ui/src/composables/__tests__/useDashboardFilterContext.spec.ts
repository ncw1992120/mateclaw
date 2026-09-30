import { afterEach, describe, expect, it, vi } from 'vitest'
import type { InsightComponent, InsightCombinationChild } from '@/types'
import { useDashboardFilterContext } from '../useDashboardFilterContext'

function makeFilter(
  id: string,
  config: Record<string, unknown>,
  type: 'filter' | 'timeFilter' = 'filter',
): InsightComponent {
  return { id, type, title: id, config, position: { x: 0, y: 0, w: 4, h: 2 } } as InsightComponent
}

function runtimeState(context: ReturnType<typeof useDashboardFilterContext>) {
  return (context as unknown as {
    getRuntimeFilterState: () => Record<string, {
      field: string
      value: unknown
      scope: string
      targetComponentIds: string[]
    }>
  }).getRuntimeFilterState()
}

afterEach(() => vi.useRealTimers())

describe('useDashboardFilterContext runtime state', () => {
  it('keeps independent latest values by filter id and does not reapply defaults', () => {
    const filters = [
      makeFilter('filter-region-a', { field: 'region', defaultValue: 'east' }),
      makeFilter('filter-region-b', { field: 'region', defaultValue: ['north', 'south'], selectionMode: 'multiple' }),
    ]
    const context = useDashboardFilterContext(() => filters, () => undefined)

    context.initializeDefaults()
    expect(runtimeState(context)['filter-region-a'].value).toBe('east')
    expect(runtimeState(context)['filter-region-b'].value).toEqual(['north', 'south'])

    context.setDimensionFilter('region', 'west', 'filter-region-a')
    context.setDimensionFilter('region', ['central', 'coastal'], 'filter-region-b')
    context.initializeDefaults()

    expect(runtimeState(context)['filter-region-a'].value).toBe('west')
    expect(runtimeState(context)['filter-region-b'].value).toEqual(['central', 'coastal'])
  })

  it('clearing a filter removes its active value without falling back to its default', () => {
    const filters = [makeFilter('filter-channel', { field: 'channel', defaultValue: 'app' })]
    const context = useDashboardFilterContext(() => filters, () => undefined)
    context.initializeDefaults()

    context.setDimensionFilter('channel', undefined, 'filter-channel')

    expect(runtimeState(context)['filter-channel'].value).toBeUndefined()
    expect(context.filterContext.value.dimensionFilters).toEqual([])
  })

  it('normalizes empty string, empty array, and null to an unset runtime value', () => {
    const filters = [
      makeFilter('filter-single', { field: 'channel', defaultValue: 'app' }),
      makeFilter('filter-multiple', { field: 'region', defaultValue: ['east'], selectionMode: 'multiple' }),
    ]
    const context = useDashboardFilterContext(() => filters, () => undefined)
    context.initializeDefaults()

    context.setDimensionFilter('channel', '', 'filter-single')
    context.setDimensionFilter('region', [], 'filter-multiple')
    context.setDimensionFilter('channel', null, 'filter-single')

    expect(runtimeState(context)['filter-single'].value).toBeUndefined()
    expect(runtimeState(context)['filter-multiple'].value).toBeUndefined()
    expect(context.filterContext.value.dimensionFilters).toEqual([])
  })

  it('retains nested tab filter scope, target, and the user-selected date range', () => {
    const dateFilter = makeFilter('nested-date', {
      field: 'metric_time',
      scope: 'scoped',
      targetComponentIds: ['nested-table'],
    }, 'timeFilter')
    const nestedTable = {
      id: 'nested-table',
      type: 'table',
      title: 'Nested table',
      boundFilterIds: ['nested-date'],
      layout: { x: 0, y: 0, w: 6, h: 2 },
    } as unknown as InsightCombinationChild
    const combination = {
      id: 'combination',
      type: 'combination',
      title: 'Combination',
      position: { x: 0, y: 0, w: 12, h: 6 },
      containerConfig: {
        layoutMode: 'grid',
        activeTab: 'tab-1',
        tabs: [{ id: 'tab-1', title: 'Tab 1', children: [dateFilter as unknown as InsightCombinationChild, nestedTable] }],
      },
    } as unknown as InsightComponent
    const context = useDashboardFilterContext(() => [combination], () => undefined)

    expect(context.getFilterScope('nested-date')).toEqual({ scope: 'scoped', targetComponentIds: ['nested-table'] })
    context.setTimeRange({ preset: 'custom', start: '2026-09-01', end: '2026-09-02' }, 'nested-date')

    expect(runtimeState(context)['nested-date']).toEqual({
      field: 'metric_time',
      value: { preset: 'custom', start: '2026-09-01', end: '2026-09-02' },
      scope: 'scoped',
      targetComponentIds: ['nested-table'],
    })
  })

  it.each([
    ['yesterday', '2026-09-28', '2026-09-28'],
    ['today', '2026-09-29', '2026-09-29'],
    ['monthToDate', '2026-09-01', '2026-09-29'],
    ['yearToDate', '2026-01-01', '2026-09-29'],
  ] as const)('resolves the %s time-filter default from the current local date', (defaultPreset, start, end) => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 8, 29, 12, 0, 0))
    const dateFilter = makeFilter('date-filter', {
      field: 'metric_time',
      defaultPreset,
    }, 'timeFilter')
    const context = useDashboardFilterContext(() => [dateFilter], () => undefined)

    context.initializeDefaults()

    const expected = { preset: 'custom', start, end }
    expect(runtimeState(context)['date-filter'].value).toEqual(expected)
    expect(context.filterContext.value.timeRange).toEqual(expected)
  })

  it('clamps the resolved default range to the configured max span', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 8, 29, 12, 0, 0))
    const dateFilter = makeFilter('date-filter', {
      field: 'metric_time',
      defaultPreset: 'yearToDate',
      maxRangeDays: 30,
    }, 'timeFilter')
    const context = useDashboardFilterContext(() => [dateFilter], () => undefined)

    context.initializeDefaults()

    expect(context.filterContext.value.timeRange).toEqual({ preset: 'custom', start: '2026-08-31', end: '2026-09-29' })
  })

  it('recomputes relative defaults when a new preview session starts on a later date', () => {
    vi.useFakeTimers()
    const dateFilter = makeFilter('date-filter', {
      field: 'metric_time',
      defaultPreset: 'monthToDate',
    }, 'timeFilter')
    vi.setSystemTime(new Date(2026, 8, 30, 12, 0, 0))
    const firstSession = useDashboardFilterContext(() => [dateFilter], () => undefined)
    firstSession.initializeDefaults()

    vi.setSystemTime(new Date(2026, 9, 1, 12, 0, 0))
    const nextSession = useDashboardFilterContext(() => [dateFilter], () => undefined)
    nextSession.initializeDefaults()

    expect(firstSession.filterContext.value.timeRange).toEqual({ preset: 'custom', start: '2026-09-01', end: '2026-09-30' })
    expect(nextSession.filterContext.value.timeRange).toEqual({ preset: 'custom', start: '2026-10-01', end: '2026-10-01' })
  })
})
