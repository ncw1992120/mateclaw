import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { reconcileKpiProjection } from '../useResultSetRestore'

describe('reconcileKpiProjection', () => {
  it('adds result-set KPI fields missing from an older persisted projection', () => {
    const component = {
      id: 'kpi-1',
      type: 'kpi',
      kpiMetrics: [{
        fieldKey: 'orders', displayName: '订单数', unit: '', helperText: '', visible: true,
        x: 0, y: 0, w: 160, h: 80, styles: defaultMetricStyles(),
      }],
    } as unknown as InsightComponent

    reconcileKpiProjection(component, [{ name: 'orders' }, { name: 'revenue' }])

    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['orders', 'revenue'])
    expect(component.kpiMetrics?.[0].displayName).toBe('订单数')
  })
})
