import { describe, expect, it } from 'vitest'
import type { InsightCombinationChild, InsightComponent } from '@/types'
import { mergeCombinationChildComponentUpdate } from '../combinationChildComponentUpdate'

describe('mergeCombinationChildComponentUpdate', () => {
  it('persists updated KPI projection and pipeline while preserving canvas-owned layout', () => {
    const child = {
      id: 'contribution-kpi',
      type: 'kpi',
      title: '策略贡献',
      layout: { x: 12, y: 24, col: 6, h: 180 },
      kpiMetrics: [{ fieldKey: 'legacy_metric', displayName: '旧指标' }],
      config: { datasetPipeline: { script: 'old script' } },
    } as InsightCombinationChild
    const updated = {
      ...child,
      kpiMetrics: [
        { fieldKey: '转化规模', displayName: '转化规模' },
        { fieldKey: '转化人数', displayName: '转化人数' },
      ],
      multiKpi: true,
      config: { datasetPipeline: { script: 'joined result script' } },
      layout: { x: 0, y: 0, col: 12, h: 100 },
    } as unknown as InsightComponent

    const merged = mergeCombinationChildComponentUpdate(child, updated)

    expect(merged.kpiMetrics?.map(metric => metric.fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(merged.multiKpi).toBe(true)
    expect(merged.config).toEqual({ datasetPipeline: { script: 'joined result script' } })
    expect(merged.layout).toEqual({ x: 12, y: 24, col: 6, h: 180 })
  })
})
