import { describe, expect, it } from 'vitest'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'
import { resolveMetricVisual } from '@/utils/kpi-metrics'

describe('KpiCardWidget · 主题视觉意图', () => {
  it('四个指标按稳定序号获得不同默认图标和强调色', () => {
    const fields = ['a', 'b', 'c', 'd'].map((name) => ({ name, displayName: name }))
    const metrics = buildKpiMetrics(fields)
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light')
    const visuals = metrics.map((metric, index) => resolveMetricVisual(metric, undefined, theme, index))
    expect(new Set(visuals.map((item) => item.iconKey)).size).toBe(4)
    expect(new Set(visuals.map((item) => item.accentColor)).size).toBe(4)
  })

  it('关闭图标时只关闭图标，指标标题仍可读', () => {
    const metric = buildKpiMetrics([{ name: 'a', displayName: '关联计划数' }])[0]
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    const visual = resolveMetricVisual(metric, { iconKey: null }, theme, 0)
    expect(visual.iconKey).toBeNull()
    expect(metric.displayName).toBe('关联计划数')
  })
})
