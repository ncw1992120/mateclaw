import { describe, expect, it } from 'vitest'
import type { DashboardScriptFilterBinding } from '@/types'
import {
  buildExecutionParameters,
  defaultRuntimeRows,
  toDatasetFilters,
  type RuntimeFilterRow,
} from '../runtime-filter-bindings'

const timeBinding: DashboardScriptFilterBinding = {
  filterComponentId: 'time_filter',
  inputNames: ['dataset_a'],
  conditions: [
    {
      inputName: 'dataset_a',
      field: 'metric_time',
      operator: 'gte',
      parameterNames: ['startDate'],
      required: false,
    },
    {
      inputName: 'dataset_a',
      field: 'metric_time',
      operator: 'lt',
      parameterNames: ['endDate'],
      required: false,
    },
  ],
}

describe('runtime-filter-bindings', () => {
  it('绑定模板默认带入，但空值不形成参数或过滤条件', () => {
    const rows = defaultRuntimeRows('dataset_a', [timeBinding])

    expect(rows.map((row) => row.parameterName)).toEqual(['startDate', 'endDate'])
    expect(buildExecutionParameters(rows)).toEqual({})
    expect(toDatasetFilters(rows)).toEqual([])
  })

  it('只填开始时间只生成 gte', () => {
    const rows = defaultRuntimeRows('dataset_a', [timeBinding])
    rows[0].value = '2026-09-01'

    expect(buildExecutionParameters(rows)).toEqual({ startDate: '2026-09-01' })
    expect(toDatasetFilters(rows)).toEqual([
      { field: 'metric_time', operator: 'gte', value: '2026-09-01', role: 'dimension' },
    ])
  })

  it('只填结束时间只生成 lt，两个值都填则生成左闭右开范围', () => {
    const endOnly = defaultRuntimeRows('dataset_a', [timeBinding])
    endOnly[1].value = '2026-10-01'
    expect(toDatasetFilters(endOnly)).toEqual([
      { field: 'metric_time', operator: 'lt', value: '2026-10-01', role: 'dimension' },
    ])

    const both = defaultRuntimeRows('dataset_a', [timeBinding])
    both[0].value = '2026-09-01'
    both[1].value = '2026-10-01'
    expect(toDatasetFilters(both)).toEqual([
      { field: 'metric_time', operator: 'gte', value: '2026-09-01', role: 'dimension' },
      { field: 'metric_time', operator: 'lt', value: '2026-10-01', role: 'dimension' },
    ])
  })

  it('支持集合条件并把空集合视为未填写', () => {
    const rows: RuntimeFilterRow[] = [{
      inputName: 'dataset_b',
      field: 'customer_id',
      operator: 'in',
      parameterNames: ['customerIds'],
      parameterName: 'customerIds',
      value: [7, 8],
      required: false,
    }]
    expect(buildExecutionParameters(rows)).toEqual({ customerIds: [7, 8] })
    expect(toDatasetFilters(rows)).toEqual([
      { field: 'customer_id', operator: 'in', value: [7, 8], role: 'dimension' },
    ])
    rows[0].value = []
    expect(buildExecutionParameters(rows)).toEqual({})
    expect(toDatasetFilters(rows)).toEqual([])
  })
})
