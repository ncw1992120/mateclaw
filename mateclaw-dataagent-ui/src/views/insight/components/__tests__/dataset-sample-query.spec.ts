import { beforeEach, describe, expect, it, vi } from 'vitest'
import { datasetSampleSourceSignature, fetchDatasetSampleRows } from '../dataset-sample-query'

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
    expect(previewInput.mock.calls[0][1]).toEqual({ timeout: 0 })
    expect(result.rows).toEqual([
      { region: '华东', amount: 12 },
      { region: '华南', amount: 9 },
    ])
    expect(result.rows[0]).not.toEqual({ region: null, amount: null })
  })

  it('造数示例沿用数据集固定筛选和最近一次查询筛选，只展示第一页前两行', async () => {
    previewInput.mockResolvedValueOnce({
      rows: [
        { region: '华东', amount: 12 },
        { region: '华东', amount: 9 },
        { region: '华东', amount: 7 },
      ],
      schema: ['region', 'amount'],
    })
    const dataset = {
      id: 'local-sample', backendDatasetId: '21', sourceType: 'jdbc', sourceLabel: 'JDBC', alias: 'table_sales',
      jdbc: { db: 'db-1', sql: 'select region, amount from sales' },
      fields: [{ name: 'region', role: 'dimension' }, { name: 'amount', role: 'measure' }],
      filters: [{ field: 'tenant_id', op: '=', value: 'tenant-a' }],
      queryConfig: {
        displayFields: [
          { field: 'region', title: '区域', role: 'dimension' },
          { field: 'amount', title: '金额', role: 'measure' },
        ],
        parameterBindings: [{ filterComponentId: 'filter-region', field: 'region', operator: 'eq', parameterName: 'region_param' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
        paginationPolicy: { enabled: true, defaultPageSize: 20, maxPageSize: 100, returnTotalCount: false },
      },
      lastQueryState: {
        filters: [{ filterComponentId: 'filter-region', field: 'region', parameterName: 'region_param', value: '华东', enabled: true }],
        parameters: {}, sort: null, page: 3, pageSize: 50, queryLimit: 300,
      },
    } as any

    const result = await fetchDatasetSampleRows(dataset, [{ id: 'filter-region', type: 'filter' }], { sampleOnly: true })

    expect(previewInput.mock.calls[0][0]).toMatchObject({
      datasetId: '21', inputName: 'table_sales', limit: 2, offset: 0,
      filters: [
        { field: 'tenant_id', role: 'dimension', operator: 'eq', value: 'tenant-a' },
        { field: 'region', role: 'dimension', operator: 'eq', value: '华东' },
      ],
      parameters: { region_param: '华东' },
    })
    expect(result.rows).toEqual([
      { region: '华东', amount: 12 },
      { region: '华东', amount: 9 },
    ])
  })

  it('数据集字段、绑定和筛选发生变化时生成不同签名，使已缓存样例失效', () => {
    const dataset = {
      id: 'local-sample', backendDatasetId: '21', sourceType: 'jdbc', alias: 'table_sales',
      jdbc: { db: 'db-1', sql: 'select region, amount from sales' },
      fields: [{ name: 'region' }, { name: 'amount' }], filters: [],
      queryConfig: { displayFields: [{ field: 'region', title: '区域', role: 'dimension' }] },
      lastQueryState: { filters: [], parameters: {}, sort: null, page: 1, pageSize: 50 },
    } as any
    const before = datasetSampleSourceSignature([dataset], [])
    const afterFieldRemoval = datasetSampleSourceSignature([{
      ...dataset,
      fields: [{ name: 'region' }],
      queryConfig: { displayFields: [{ field: 'region', title: '区域', role: 'dimension' }] },
    }], [])

    expect(afterFieldRemoval).not.toBe(before)
    expect(datasetSampleSourceSignature([], [])).not.toBe(before)
  })

  it('样例请求持续等待真实接口结果，不用前端 15 秒计时器截断', async () => {
    let resolvePreview!: (value: { rows: { value: number }[]; schema: string[] }) => void
    previewInput.mockImplementationOnce(() => new Promise((resolve) => { resolvePreview = resolve }))
    const dataset = {
      id: 'local-4', backendDatasetId: '14', sourceType: 'jdbc', sourceLabel: 'JDBC', alias: 'table_slow',
      jdbc: { db: 'db-1', sql: 'select value from metrics' }, fields: [], filters: [],
      queryConfig: { displayFields: [], parameterBindings: [], sortPolicy: { enabled: false, mode: 'single', allowedFields: [] }, paginationPolicy: { enabled: false } },
    } as any

    let settled = false
    const request = fetchDatasetSampleRows(dataset, [], { sampleOnly: true }).finally(() => { settled = true })
    await new Promise((resolve) => setTimeout(resolve, 25))
    expect(settled).toBe(false)
    resolvePreview({ rows: [{ value: 7 }], schema: ['value'] })
    await expect(request).resolves.toMatchObject({ rows: [{ value: 7 }] })
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
    expect(previewDatasetDraft.mock.calls[0][1]).toEqual({ timeout: 0 })
    expect(result.rows).toEqual([{ amount: 12 }])
  })

  it('结果恢复可按上次查看数据的限制行数重放查询', async () => {
    const dataset = {
      id: 'local-3', backendDatasetId: '13', sourceType: 'jdbc', sourceLabel: 'JDBC', alias: 'table_ab',
      jdbc: { db: 'db-1', sql: 'select amount from sales' }, fields: [], filters: [],
      queryConfig: { displayFields: [{ field: 'amount', title: '金额', role: 'measure' }], parameterBindings: [], sortPolicy: { enabled: false, mode: 'single', allowedFields: [] }, paginationPolicy: { enabled: false, defaultPageSize: 50, maxPageSize: 500, returnTotalCount: false } },
      lastQueryState: { filters: [], parameters: {}, sort: null, page: 1, pageSize: 50, queryLimit: 320 },
    } as any

    await fetchDatasetSampleRows(dataset, [], { limit: dataset.lastQueryState.queryLimit })

    expect(previewInput.mock.calls[0][0]).toMatchObject({ columns: ['amount'], limit: 320, offset: 0 })
  })
})
