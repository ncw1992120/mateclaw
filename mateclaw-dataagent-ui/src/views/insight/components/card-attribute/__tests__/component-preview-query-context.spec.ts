import { describe, expect, it } from 'vitest'
import { createComponentPreviewQueryContext } from '../component-preview-query-context'

describe('createComponentPreviewQueryContext', () => {
  it('兼容历史时间绑定的开始和结束复用同一参数名，不将日期边界误判为冲突', () => {
    const input: import('@/types').DashboardDatasetInput = {
      datasetId: '1', inputName: 'table_ab',
      queryConfig: {
        displayFields: [],
        parameterBindings: [
          { filterComponentId: 'date', parameterName: 'comp_1792123987548_srqf', field: 'metric_time', operator: 'gte' },
          { filterComponentId: 'date', parameterName: 'comp_1792123987548_srqf', field: 'metric_time', operator: 'lt' },
        ],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
      lastQueryState: { filters: [
        { filterComponentId: 'date', field: 'metric_time', parameterName: 'comp_1792123987548_srqf', timeBoundary: 'start', value: '2026-10-09', enabled: true },
        { filterComponentId: 'date', field: 'metric_time', parameterName: 'comp_1792123987548_srqf', timeBoundary: 'end', value: '2026-10-10', enabled: true },
      ], parameters: {}, sort: null, page: 1, pageSize: 100 },
    }
    expect(createComponentPreviewQueryContext('1', 'card', [input]).datasetParameters).toEqual({
      table_ab: { comp_1792123987548_srqf: ['2026-10-09', '2026-10-10'] },
    })
    input.lastQueryState!.filters[0].enabled = false
    expect(createComponentPreviewQueryContext('1', 'card', [input]).datasetParameters).toEqual({
      table_ab: { comp_1792123987548_srqf: [null, '2026-10-10'] },
    })
  })

  it('includes the dashboard and component identity with empty runtime filters', () => {
    expect(createComponentPreviewQueryContext('dashboard-1', 'component-1')).toMatchObject({
      dashboardId: 'dashboard-1',
      componentId: 'component-1',
      parameters: {},
    })
  })

  it('把各上游输入最近一次查询的指标日期条件传给 Python 执行计划', () => {
    const queryConfig = () => ({
      displayFields: [],
      parameterBindings: [{
        filterComponentId: 'metric-date-filter',
        parameterName: 'comp-date-filter',
        field: 'metric_time',
        operator: 'gte' as const,
      }],
      sortPolicy: { enabled: false, mode: 'single' as const, allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    })
    const lastQueryState = {
      filters: [
        { filterComponentId: 'metric-date-filter', field: 'metric_time', parameterName: 'date_start', timeBoundary: 'start' as const, value: '2026-09-29', enabled: true },
        { filterComponentId: 'metric-date-filter', field: 'metric_time', parameterName: '', timeBoundary: 'end' as const, value: '2026-10-02', enabled: true },
      ],
      parameters: {}, sort: null, page: 1, pageSize: 100,
    }

    const context = createComponentPreviewQueryContext('dashboard-1', 'table-1', [
      { datasetId: '1', inputName: 'table_ab', queryConfig: queryConfig(), lastQueryState },
      {
        datasetId: '2', inputName: 'table_wd', queryConfig: queryConfig(),
        lastQueryState: {
          ...lastQueryState,
          filters: lastQueryState.filters.map((filter) => ({
            ...filter,
            value: filter.timeBoundary === 'start' ? '2026-09-17' : '2026-10-09',
          })),
        },
      },
    ])

    expect(context.parameters).toEqual({
    })
    expect(context.datasetParameters).toEqual({
      table_ab: { 'comp-date-filter': ['2026-09-29', '2026-10-01'] },
      table_wd: { 'comp-date-filter': ['2026-09-17', '2026-10-08'] },
    })
  })

  it('不把已关闭或空值的上游筛选条件传入执行计划', () => {
    const context = createComponentPreviewQueryContext('dashboard-1', 'table-1', [{
      datasetId: '1', inputName: 'table_ab',
      queryConfig: {
        displayFields: [],
        parameterBindings: [{ filterComponentId: 'metric-date-filter', parameterName: 'date', field: 'metric_time', operator: 'gte' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
      lastQueryState: {
        filters: [{ filterComponentId: 'metric-date-filter', field: 'metric_time', parameterName: 'date', value: '2026-09-29', enabled: false }],
        parameters: {}, sort: null, page: 1, pageSize: 100,
      },
    }])

    expect(context.parameters).toEqual({})
    expect(context.datasetParameters).toEqual({})
  })

  it('同一输入的多个筛选绑定复用参数名时不再因值不同而阻止查询', () => {
    const context = createComponentPreviewQueryContext('dashboard-1', 'python-1', [{
      datasetId: '1', inputName: 'table_ab',
      queryConfig: {
        displayFields: [],
        parameterBindings: [
          { filterComponentId: 'filter-a', parameterName: 'comp_1792123987548_srqf', field: 'metric_time', operator: 'eq' },
          { filterComponentId: 'filter-b', parameterName: 'comp_1792123987548_srqf', field: 'region', operator: 'eq' },
        ],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
      lastQueryState: {
        filters: [
          { filterComponentId: 'filter-a', field: 'metric_time', parameterName: 'comp_1792123987548_srqf', value: '2026-10-09', enabled: true },
          { filterComponentId: 'filter-b', field: 'region', parameterName: 'comp_1792123987548_srqf', value: '华东', enabled: true },
        ],
        parameters: {}, sort: null, page: 1, pageSize: 100,
      },
    }])

    expect(context.datasetParameters).toEqual({
      table_ab: { comp_1792123987548_srqf: '华东' },
    })
  })
})
