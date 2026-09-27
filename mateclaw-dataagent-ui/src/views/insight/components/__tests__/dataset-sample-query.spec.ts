import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchDatasetSampleRows } from '../dataset-sample-query'

const { previewInput, previewDatasetDraft } = vi.hoisted(() => ({
  previewInput: vi.fn(),
  previewDatasetDraft: vi.fn(),
}))

vi.mock('@/api/dataset', () => ({ previewInput }))
vi.mock('../card-attribute/useInsightBackend', () => ({ previewDatasetDraft }))

describe('fetchDatasetSampleRows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    previewInput.mockResolvedValue({
      rows: [{ region: '华东', amount: 12 }, { region: '华南', amount: 9 }],
      schema: ['region', 'amount'],
      hasNext: false,
    })
  })

  it('从上游查询接口获取真实行数据，而非用字段映射构造空 schema', async () => {
    const dataset = {
      id: 'local-1', backendDatasetId: '11', sourceType: 'jdbc', sourceLabel: 'JDBC', alias: 'table_ab',
      jdbc: { db: 'db-1', sql: 'select region, amount from sales' }, fields: [], filters: [],
      queryConfig: {
        displayFields: [
          { field: 'region', title: '区域', role: 'dimension' },
          { field: 'amount', title: '金额', role: 'measure' },
        ],
        parameterBindings: [{ filterComponentId: 'filter-region', field: 'region', operator: 'eq', parameterName: 'region_param' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
        paginationPolicy: { enabled: false, defaultPageSize: 20, maxPageSize: 100, returnTotalCount: false },
      },
      lastQueryState: {
        filters: [{ filterComponentId: 'filter-region', field: 'region', parameterName: 'region_param', value: '华东', enabled: true }],
        parameters: {}, sort: null, page: 1, pageSize: 50,
      },
    } as any

    const result = await fetchDatasetSampleRows(dataset, [{ id: 'filter-region', type: 'filter' }])

    expect(previewInput).toHaveBeenCalledOnce()
    expect(previewInput.mock.calls[0][0]).toMatchObject({
      datasetId: '11', inputName: 'table_ab', columns: ['region', 'amount'], limit: 50, offset: 0,
      filters: [{ field: 'region', operator: 'eq', value: '华东', role: 'dimension' }],
      parameters: { region_param: '华东' },
    })
    expect(result.rows).toEqual([
      { region: '华东', amount: 12 },
      { region: '华南', amount: 9 },
    ])
    expect(result.rows[0]).not.toEqual({ region: null, amount: null })
  })

  it('未落库数据集通过草稿查询接口取得相同的结果行', async () => {
    previewInput.mockClear()
    previewDatasetDraft.mockResolvedValue({ rows: [{ amount: 12 }], schema: ['amount'] })
    const dataset = {
      id: 'local-2', sourceType: 'jdbc', sourceLabel: 'JDBC', alias: 'table_ab',
      jdbc: { db: 'db-1', sql: 'select amount from sales' }, fields: [], filters: [], queryConfig: { displayFields: [], parameterBindings: [], sortPolicy: { enabled: false, allowedFields: [] }, paginationPolicy: { enabled: false } },
    } as any

    const result = await fetchDatasetSampleRows(dataset, [])

    expect(previewDatasetDraft).toHaveBeenCalledOnce()
    expect(result.rows).toEqual([{ amount: 12 }])
  })
})
