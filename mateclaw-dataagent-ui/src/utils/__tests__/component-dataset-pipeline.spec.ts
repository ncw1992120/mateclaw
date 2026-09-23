import { describe, expect, it } from 'vitest'
import {
  draftQueryConfigFromLegacyBindings,
  readComponentDatasetPipeline,
  writeComponentDatasetPipeline,
} from '../component-dataset-pipeline'

describe('component dataset pipeline', () => {
  it('reads managed system script state without inferring it from the legacy script', () => {
    const component = {
      id: 'c1',
      config: {
        datasetPipeline: {
          datasetInputs: [],
          script: 'result = []',
          scriptFilterBindings: [{ filterComponentId: 'f1', inputNames: ['orders'], fieldMappings: { orders: 'metric_time' } }],
          systemScript: {
            mode: 'managed',
            generatedCode: 'orders = datasets.read("orders")',
            managedCode: 'orders = custom_read()',
            generatedFingerprint: 'fp-1',
            userCode: 'result = []',
          },
        },
      },
    } as any

    const pipeline = readComponentDatasetPipeline(component)

    expect(pipeline?.systemScript?.mode).toBe('managed')
    expect(pipeline?.systemScript?.managedCode).toBe('orders = custom_read()')
    expect(pipeline?.scriptFilterBindings?.[0].conditions).toEqual([])
  })

  it('writes the new conditions and system script structures without dropping them', () => {
    const component = { id: 'c1', config: {} } as any
    const updated = writeComponentDatasetPipeline(component, {
      datasetInputs: [],
      scriptFilterBindings: [{
        filterComponentId: 'time_filter',
        conditions: [{
          inputName: 'orders',
          field: 'metric_time',
          operator: 'gte',
          parameterNames: ['startDate'],
          required: false,
        }],
      }],
      systemScript: {
        mode: 'generated',
        generatedCode: 'orders = datasets.read("orders")',
        generatedFingerprint: 'fp-2',
        userCode: 'result = orders',
      },
    })

    expect(readComponentDatasetPipeline(updated)?.scriptFilterBindings?.[0].conditions?.[0]).toMatchObject({
      field: 'metric_time',
      operator: 'gte',
      parameterNames: ['startDate'],
    })
    expect(readComponentDatasetPipeline(updated)?.systemScript?.mode).toBe('generated')
  })

  it('keeps pipelines scoped to their component', () => {
    const first = { id: 'c1', type: 'chart', title: '一', position: { x: 0, y: 0, w: 2, h: 2 }, config: {} } as any
    const second = { id: 'c2', type: 'chart', title: '二', position: { x: 2, y: 0, w: 2, h: 2 }, config: {} } as any
    const updated = writeComponentDatasetPipeline(first, { datasetInputs: [{ datasetId: '101', inputName: 'orders' }] })
    expect(readComponentDatasetPipeline(updated)?.datasetInputs[0].datasetId).toBe('101')
    expect(readComponentDatasetPipeline(second)).toBeUndefined()
  })

  it('does not infer legacy root inputs', () => {
    expect(readComponentDatasetPipeline({ id: 'legacy', config: { datasetInputs: [{ datasetId: '1' }] } } as any)).toBeUndefined()
  })

  it('persists result set metadata alongside the pipeline', () => {
    const component = { id: 'c1', type: 'kpi', title: '一', position: { x: 0, y: 0, w: 2, h: 2 }, config: {} } as any
    const updated = writeComponentDatasetPipeline(component, {
      datasetInputs: [{ datasetId: '101', inputName: 'orders' }],
      resultSet: {
        source: 'script',
        status: 'ready',
        columns: [{ name: 'amount', type: 'double' }],
        rowCount: 42,
        generatedAt: '2026-09-18T09:00:00.000Z',
        elapsedMs: 1200,
        executionId: 'dashboard-1-abc',
      },
    })
    const read = readComponentDatasetPipeline(updated)?.resultSet
    expect(read?.source).toBe('script')
    expect(read?.status).toBe('ready')
    expect(read?.executionId).toBe('dashboard-1-abc')
    expect(read?.columns[0].name).toBe('amount')
    expect(read?.rowCount).toBe(42)
  })

  it('drops malformed result set metadata instead of rendering it', () => {
    const component = {
      id: 'c1',
      config: { datasetPipeline: { datasetInputs: [], resultSet: { source: 'script', status: 'ready' } } },
    } as any
    expect(readComponentDatasetPipeline(component)?.resultSet).toBeUndefined()
  })

  it('omits the result set key when the pipeline carries none', () => {
    const component = { id: 'c1', config: {} } as any
    const updated = writeComponentDatasetPipeline(component, { datasetInputs: [] })
    expect((updated.config as any).datasetPipeline.resultSet).toBeUndefined()
  })

  it('reads and writes queryConfig on dataset inputs (任务 5 契约)', () => {
    const queryConfig = {
      displayFields: [{ field: 'metric_date', title: '指标日期', role: 'dimension' }],
      parameterBindings: [{ filterComponentId: 'date_range', parameterName: 'start_date', field: 'metric_date', operator: 'gte' }],
      sortPolicy: { enabled: true, mode: 'single', allowedFields: ['in_account'], defaultSort: null },
      paginationPolicy: { enabled: true, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: true },
    }
    const component = {
      id: 'c1',
      config: { datasetPipeline: { datasetInputs: [{ datasetId: '42', inputName: 'strategy_data', queryConfig }] } },
    } as any
    const read = readComponentDatasetPipeline(component)
    expect(read?.datasetInputs[0].queryConfig).toEqual(queryConfig)
    // 写回保留 queryConfig
    const updated = writeComponentDatasetPipeline(component, { datasetInputs: [{ datasetId: '42', inputName: 'strategy_data', queryConfig }] })
    expect((updated.config as any).datasetPipeline.datasetInputs[0].queryConfig).toEqual(queryConfig)
  })

  it('reads and writes component-level final result query config separately from input queryConfig', () => {
    const finalResultQueryConfig = {
      confirmed: false,
      schemaFingerprint: 'result-schema-1',
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      filterFields: [{ field: 'region', title: '区域', dataType: 'string', parameterName: 'region', operators: ['eq'] }],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: true, defaultPageSize: 20, maxPageSize: 100, returnTotalCount: false },
    }
    const component = { id: 'c1', config: {} } as any
    const updated = writeComponentDatasetPipeline(component, {
      datasetInputs: [],
      finalResultQueryConfig,
    })
    expect(readComponentDatasetPipeline(updated)?.finalResultQueryConfig).toEqual(finalResultQueryConfig)
    expect((updated.config as any).datasetPipeline.datasetInputs).toEqual([])
  })

  it('writes boundFilterComponentIds when present', () => {
    const component = { id: 'c1', config: {} } as any
    const updated = writeComponentDatasetPipeline(component, {
      datasetInputs: [],
      boundFilterComponentIds: ['strategy_type', 'date_range'],
    } as any)
    expect((updated.config as any).datasetPipeline.boundFilterComponentIds).toEqual(['strategy_type', 'date_range'])
  })

  it('draftQueryConfigFromLegacyBindings normalizes legacy scriptFilterBindings', () => {
    const draft = draftQueryConfigFromLegacyBindings([
      {
        filterComponentId: 'strategy_type',
        inputNames: ['strategy_data'],
        fieldMappings: { strategy_data: 'strategy_id' },
        conditions: [
          { inputName: 'strategy_data', field: 'strategy_id', operator: 'in', parameterNames: ['strategy_ids'] },
        ],
      },
    ])
    expect(draft.parameterBindings).toEqual([
      { filterComponentId: 'strategy_type', parameterName: 'strategy_ids', field: 'strategy_id', operator: 'in' },
    ])
    // 展示草稿不含字段/排序/分页配置：未经用户确认不得静默生效
    expect(draft.displayFields).toEqual([])
    expect(draft.sortPolicy.enabled).toBe(false)
    expect(draft.paginationPolicy.enabled).toBe(false)
  })
})
