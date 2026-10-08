import { describe, expect, it } from 'vitest'
import type { InsightComponent, InsightComponentData } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { persistAppliedKpiProjection } from '../appliedKpiProjection'

describe('persistAppliedKpiProjection', () => {
  it('keeps only the applied KPI fields and preserves matching metric presentation', () => {
    const component = {
      id: 'strategy-summary',
      type: 'kpi',
      kpiMetrics: [
        ...['touch_count', 'send_count', 'touch_users', 'send_users'].map((fieldKey, index) => ({
          fieldKey,
          displayName: fieldKey,
          unit: '',
          helperText: '',
          visible: true,
          x: index * 170,
          y: 0,
          w: 160,
          h: 72,
          styles: defaultMetricStyles(),
        })),
        ...['metric_date', 'strategy_name'].map((fieldKey, index) => ({
          fieldKey,
          displayName: fieldKey,
          unit: '',
          helperText: '',
          visible: true,
          x: index * 170,
          y: 80,
          w: 160,
          h: 72,
          styles: defaultMetricStyles(),
        })),
      ],
    } as unknown as InsightComponent
    component.kpiMetrics![0].helperText = '保留的说明'
    component.kpiMetrics![0].x = 37

    const data: InsightComponentData = {
      componentId: component.id,
      renderType: 'kpi',
      resultFieldProjection: true,
      kpiList: ['touch_count', 'send_count', 'touch_users', 'send_users'].map((fieldKey) => ({
        fieldKey,
        name: `${fieldKey} 展示名`,
        value: '1',
      })),
    }

    expect(persistAppliedKpiProjection(component, data)).toBe(true)
    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual([
      'touch_count', 'send_count', 'touch_users', 'send_users',
    ])
    expect(component.kpiMetrics?.[0]).toMatchObject({ helperText: '保留的说明', x: 37 })
    expect(component.kpiMetrics?.[0].displayName).toBe('touch_count 展示名')
  })

  it('does not persist ordinary previews that were not explicitly applied', () => {
    const component = {
      id: 'strategy-summary',
      type: 'kpi',
      kpiMetrics: [{ fieldKey: 'legacy_dimension' }],
    } as unknown as InsightComponent
    const data: InsightComponentData = {
      componentId: component.id,
      renderType: 'kpi',
      kpiList: [{ fieldKey: 'new_metric', name: '新指标', value: '1' }],
    }

    expect(persistAppliedKpiProjection(component, data)).toBe(false)
    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['legacy_dimension'])
  })
})
