import { describe, expect, it } from 'vitest'
import type { DashboardDatasetInput } from '@/types'
import { findAloudataInputMissingMetricTime } from '../dashboard-metric-time-guard'

const input = (patch: Partial<DashboardDatasetInput> = {}): DashboardDatasetInput => ({
  datasetId: '1', inputName: 'sales', sourceType: 'ALOUDATA_METRICS',
  queryConfig: {
    displayFields: [], parameterBindings: [],
    sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
    paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
  },
  ...patch,
})

describe('findAloudataInputMissingMetricTime', () => {
  it('requires a non-empty metric_time binding value for Aloudata inputs', () => {
    const aloudata = input({ queryConfig: {
      ...input().queryConfig!,
      displayFields: [], parameterBindings: [{ filterComponentId: 'date', parameterName: 'start', field: 'metric_time', operator: 'gte' }],
    } })

    expect(findAloudataInputMissingMetricTime([aloudata], {})).toBe(aloudata)
    expect(findAloudataInputMissingMetricTime([aloudata], { start: '' })).toBe(aloudata)
    expect(findAloudataInputMissingMetricTime([aloudata], { start: '2026-09-01' })).toBeUndefined()
  })

  it('accepts a fixed metric_time filter and ignores non-Aloudata datasets', () => {
    const fixed = input({ filters: [{ field: 'metric_time__day', operator: 'gte', value: '2026-09-01' }] })
    const jdbc = input({ sourceType: 'JDBC_SQL' })

    expect(findAloudataInputMissingMetricTime([fixed], {})).toBeUndefined()
    expect(findAloudataInputMissingMetricTime([jdbc], {})).toBeUndefined()
  })

  it('does not treat metric_time as a projection field or an ordinary date field as the partition filter', () => {
    const aloudata = input({
      queryConfig: {
        ...input().queryConfig!,
        displayFields: [{ field: 'metric_time', title: '指标日期', role: 'dimension' }],
        parameterBindings: [{ filterComponentId: 'date', parameterName: 'date', field: 'trade_date', operator: 'gte' }],
      },
    })

    expect(findAloudataInputMissingMetricTime([aloudata], { date: '2026-09-01' })).toBe(aloudata)
  })
})
