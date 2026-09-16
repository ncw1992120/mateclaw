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
})
