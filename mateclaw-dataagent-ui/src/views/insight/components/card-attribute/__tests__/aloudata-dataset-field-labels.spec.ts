import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getInputDescriptor: vi.fn(),
  listDatasets: vi.fn(),
  confirmDatasetDraft: vi.fn(),
  listAloudataMetrics: vi.fn(),
  listAloudataDimensions: vi.fn(),
}))

vi.mock('@/api/dataset', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/dataset')>()),
  getInputDescriptor: mocks.getInputDescriptor,
  list: mocks.listDatasets,
}))

vi.mock('../useInsightBackend', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../useInsightBackend')>()),
  confirmDatasetDraft: mocks.confirmDatasetDraft,
}))

vi.mock('@/api/semantic-model', () => ({
  listAloudataMetrics: mocks.listAloudataMetrics,
  listAloudataDimensions: mocks.listAloudataDimensions,
}))

import { useInsight, type DatasetConfig } from '../useInsight'

const { state, openQueryConfig } = useInsight()

beforeEach(() => {
  state.datasets.splice(0, state.datasets.length)
  mocks.listDatasets.mockReset().mockResolvedValue([])
  mocks.confirmDatasetDraft.mockReset().mockResolvedValue({
    datasetId: '103',
    descriptor: {
      schema: [
        { name: 'dim_shop', title: '门店名称', dataType: 'STRING', nullable: true, semanticRole: 'dimension' },
        { name: 'met_sales', title: '销售额', dataType: 'DECIMAL', nullable: true, semanticRole: 'measure' },
      ],
    },
  })
  mocks.getInputDescriptor.mockReset().mockResolvedValue({
    schema: [
      { name: 'dim_shop', title: '门店名称' },
      { name: 'met_sales', title: '销售额' },
    ],
  })
  mocks.listAloudataMetrics.mockReset()
  mocks.listAloudataDimensions.mockReset()
})

describe('Aloudata 数据集展示名取数', () => {
  it('点击查询配置后先显示弹窗，再异步同步字段展示名', async () => {
    const dataset: DatasetConfig = {
      id: 'dataset-open-first',
      backendDatasetId: '100',
      sourceType: 'aloudata',
      sourceLabel: 'Aloudata 指标&维度',
      alias: 'table_open_first',
      fields: [],
      schema: [],
      filters: [],
      aloudata: { mode: 'metric-dim', datasourceId: '9', metrics: ['met_sales'], dims: ['dim_shop'] },
    }
    state.datasets.push(dataset)
    let resolveDescriptor!: (value: { schema: Array<{ name: string; title: string }> }) => void
    mocks.getInputDescriptor.mockReturnValueOnce(new Promise((resolve) => { resolveDescriptor = resolve }))

    const opening = openQueryConfig(dataset.id)

    expect(state.ui.queryConfigDialog).toEqual({ visible: true, datasetId: dataset.id })
    await Promise.resolve()
    await Promise.resolve()
    resolveDescriptor({ schema: [{ name: 'met_sales', title: '销售额' }] })
    await opening

    expect(dataset.fields).toContainEqual(expect.objectContaining({ name: 'met_sales', displayName: '销售额' }))
  })

  it('同一数据集字段同步期间的重复打开请求只执行一次同步', async () => {
    const dataset: DatasetConfig = {
      id: 'dataset-single-flight',
      backendDatasetId: '101',
      sourceType: 'aloudata',
      sourceLabel: 'Aloudata 指标&维度',
      alias: 'table_single_flight',
      fields: [],
      schema: [],
      filters: [],
      aloudata: { mode: 'metric-dim', datasourceId: '9', metrics: ['met_sales'], dims: ['dim_shop'] },
    }
    state.datasets.push(dataset)
    let resolveDescriptor!: (value: { schema: Array<{ name: string; title: string }> }) => void
    mocks.getInputDescriptor.mockReturnValueOnce(new Promise((resolve) => { resolveDescriptor = resolve }))

    const first = openQueryConfig(dataset.id)
    const second = openQueryConfig(dataset.id)
    await Promise.resolve()
    await Promise.resolve()
    expect(mocks.getInputDescriptor).toHaveBeenCalledTimes(1)
    resolveDescriptor({ schema: [{ name: 'met_sales', title: '销售额' }] })
    await Promise.all([first, second])
  })

  it('查询配置展示名只从数据集字段描述读取，不请求同步指标和维度列表', async () => {
    const dataset: DatasetConfig = {
      id: 'dataset-1',
      backendDatasetId: '101',
      sourceType: 'aloudata',
      sourceLabel: 'Aloudata 指标&维度',
      alias: 'table1',
      fields: [],
      schema: [],
      filters: [],
      aloudata: {
        mode: 'metric-dim',
        datasourceId: '9',
        metrics: ['met_sales'],
        dims: ['dim_shop'],
      },
    }
    state.datasets.push(dataset)

    await openQueryConfig(dataset.id)

    expect(dataset.fields.map(({ name, displayName }) => [name, displayName])).toEqual([
      ['dim_shop', '门店名称'],
      ['met_sales', '销售额'],
    ])
    expect(mocks.listAloudataMetrics).not.toHaveBeenCalled()
    expect(mocks.listAloudataDimensions).not.toHaveBeenCalled()
    expect(state.ui.queryConfigDialog).toEqual({ visible: true, datasetId: dataset.id })
  })

  it('数据集字段描述读取失败时拒绝查询配置回显，不以缓存名或同步列表冒充字段表数据', async () => {
    const dataset: DatasetConfig = {
      id: 'dataset-2',
      backendDatasetId: '102',
      sourceType: 'aloudata',
      sourceLabel: 'Aloudata 指标&维度',
      alias: 'table2',
      fields: [],
      schema: [],
      filters: [],
      aloudata: {
        mode: 'metric-dim',
        datasourceId: '9',
        metrics: ['met_sales'],
        dims: ['dim_shop'],
        displayNames: { dim_shop: '门店名称', met_sales: '销售额' },
      },
    }
    state.datasets.push(dataset)
    mocks.getInputDescriptor.mockRejectedValueOnce(new Error('descriptor unavailable'))

    await expect(openQueryConfig(dataset.id)).rejects.toThrow('descriptor unavailable')

    expect(mocks.getInputDescriptor).toHaveBeenCalledWith('102')
    expect(dataset.fields).toEqual([])
    expect(mocks.listAloudataMetrics).not.toHaveBeenCalled()
    expect(mocks.listAloudataDimensions).not.toHaveBeenCalled()
  })

  it('查询配置只读现有字段表，不在打开时新建数据集或读取缓存展示名', async () => {
    const dataset: DatasetConfig = {
      id: 'dataset-3',
      sourceType: 'aloudata',
      sourceLabel: 'Aloudata 指标&维度',
      alias: 'table3',
      fields: [],
      schema: [],
      filters: [],
      aloudata: {
        mode: 'metric-dim',
        datasourceId: '9',
        metrics: ['met_sales'],
        dims: ['dim_shop'],
        displayNames: { dim_shop: '门店名称', met_sales: '销售额' },
      },
    }
    state.datasets.push(dataset)

    await expect(openQueryConfig(dataset.id)).rejects.toThrow('数据集尚未写入展示字段表')

    expect(mocks.confirmDatasetDraft).not.toHaveBeenCalled()
    expect(mocks.getInputDescriptor).not.toHaveBeenCalled()
    expect(mocks.listDatasets).not.toHaveBeenCalled()
    expect(dataset.fields).toEqual([])
    expect(mocks.listAloudataMetrics).not.toHaveBeenCalled()
    expect(mocks.listAloudataDimensions).not.toHaveBeenCalled()
  })

  it('确认选择时等待展示名写入字段表后再完成，并以字段表 descriptor 回填', async () => {
    const { confirmAloudata } = useInsight()
    state.ui.aloudata = {
      visible: true,
      mode: 'metric-dim',
      datasourceId: '9',
      metricView: '',
      metrics: ['met_sales'],
      dims: ['dim_shop'],
      displayNames: { dim_shop: '门店名称', met_sales: '销售额' },
    }
    let resolveCreate!: (value: { datasetId: string; descriptor: { schema: Array<{ name: string; title: string; dataType: string; nullable: boolean; semanticRole: string }> } }) => void
    mocks.confirmDatasetDraft.mockReturnValueOnce(new Promise((resolve) => { resolveCreate = resolve }))

    const confirming = confirmAloudata()
    expect(state.ui.aloudata.visible).toBe(true)
    expect(state.datasets).toHaveLength(1)
    await Promise.resolve()
    await Promise.resolve()
    expect(mocks.confirmDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({
      sourceConfig: { metrics: ['met_sales'], dimensions: ['dim_shop'], displayNames: { dim_shop: '门店名称', met_sales: '销售额' } },
    }))

    resolveCreate({
      datasetId: '103',
      descriptor: {
        schema: [
          { name: 'dim_shop', title: '字段表门店名', dataType: 'STRING', nullable: true, semanticRole: 'dimension' },
          { name: 'met_sales', title: '字段表销售额', dataType: 'DECIMAL', nullable: true, semanticRole: 'measure' },
        ],
      },
    })
    await confirming

    expect(state.ui.aloudata.visible).toBe(false)
    expect(state.datasets).toHaveLength(1)
    expect(state.datasets[0].fields.map(({ name, displayName }) => [name, displayName])).toEqual([
      ['dim_shop', '字段表门店名'],
      ['met_sales', '字段表销售额'],
    ])
    expect(mocks.getInputDescriptor).not.toHaveBeenCalled()
    expect(mocks.listAloudataMetrics).not.toHaveBeenCalled()
    expect(mocks.listAloudataDimensions).not.toHaveBeenCalled()
  })

  it('字段表 descriptor 缺少已选字段时使用选择器字段信息兜底，不阻断添加数据集', async () => {
    const { confirmAloudata } = useInsight()
    state.ui.aloudata = {
      visible: true,
      mode: 'metric-dim',
      datasourceId: '9',
      metricView: '',
      metrics: ['digo_trd_fund_amt_inout_cy_jjgr'],
      dims: ['attribution_plan_id'],
      displayNames: {
        attribution_plan_id: '计划id',
        digo_trd_fund_amt_inout_cy_jjgr: '转化规模',
      },
    }
    mocks.confirmDatasetDraft.mockResolvedValueOnce({ datasetId: '104', descriptor: { schema: [] } })

    await expect(confirmAloudata()).resolves.toBeUndefined()

    expect(state.ui.aloudata.visible).toBe(false)
    expect(state.datasets[0].fields.map(({ name, displayName }) => [name, displayName])).toEqual([
      ['attribution_plan_id', '计划id'],
      ['digo_trd_fund_amt_inout_cy_jjgr', '转化规模'],
    ])
  })

  it('确认落库失败时移除未保存的数据集并保持配置弹窗打开', async () => {
    const { confirmAloudata } = useInsight()
    state.ui.aloudata = {
      visible: true,
      mode: 'metric-dim',
      datasourceId: '9',
      metricView: '',
      metrics: ['met_sales'],
      dims: ['dim_shop'],
      displayNames: { dim_shop: '门店名称', met_sales: '销售额' },
    }
    mocks.confirmDatasetDraft.mockRejectedValueOnce(new Error('字段表保存失败'))

    await expect(confirmAloudata()).rejects.toThrow('字段表保存失败')

    expect(state.datasets).toHaveLength(0)
    expect(state.ui.aloudata.visible).toBe(true)
  })
})
