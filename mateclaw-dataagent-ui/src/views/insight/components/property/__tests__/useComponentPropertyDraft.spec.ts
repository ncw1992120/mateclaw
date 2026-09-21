import { describe, expect, it } from 'vitest'
import { useComponentPropertyDraft } from '../useComponentPropertyDraft'
import type { InsightComponent } from '@/types'

const kpiWithJdbcPipeline: InsightComponent = {
  id: 'kpi-1',
  type: 'kpi',
  title: '订单数',
  position: { x: 0, y: 0, w: 3, h: 2 },
  config: {
    datasetPipeline: {
      datasetInputs: [{ datasetId: 'orders', alias: 'orders', sourceType: 'jdbc' }],
      script: '',
    },
  },
}

const tableWithoutPipeline: InsightComponent = {
  id: 'table-1',
  type: 'table',
  title: '明细',
  position: { x: 0, y: 2, w: 6, h: 4 },
}

describe('useComponentPropertyDraft', () => {
  it('does not leak dataset or dialog draft when switching components', () => {
    const controller = useComponentPropertyDraft()
    controller.load(kpiWithJdbcPipeline)
    controller.patchPipeline({ script: 'result = orders' })
    controller.load(tableWithoutPipeline)

    expect(controller.draft.value?.componentId).toBe('table-1')
    expect(controller.draft.value?.datasetPipeline.datasetInputs).toEqual([])
    expect(controller.draft.value?.datasetPipeline.script).toBeUndefined()
  })

  it('round-trips unknown component config fields while updating datasetPipeline', () => {
    const component: InsightComponent = {
      ...kpiWithJdbcPipeline,
      config: {
        ...kpiWithJdbcPipeline.config,
        extensionField: { keep: true },
      },
    }
    const controller = useComponentPropertyDraft()
    controller.load(component)
    controller.patch({ title: '新标题' })
    const result = controller.commit()!

    expect(result.title).toBe('新标题')
    expect(result.config?.extensionField).toEqual({ keep: true })
    expect(result.config?.datasetPipeline).toEqual(controller.draft.value?.datasetPipeline)
  })

  it('does not commit after reset and validates the current draft only', () => {
    const controller = useComponentPropertyDraft()
    controller.load(kpiWithJdbcPipeline)
    controller.patch({ title: '' })
    expect(controller.validate()).toBe(false)
    controller.reset()
    expect(controller.validate()).toBe(true)
    expect(controller.commit()?.title).toBe('订单数')
  })
})
