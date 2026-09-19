import { describe, expect, it } from 'vitest'
import { readComponentDatasetPipeline, writeComponentDatasetPipeline } from '../component-dataset-pipeline'

describe('component dataset pipeline', () => {
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
})
