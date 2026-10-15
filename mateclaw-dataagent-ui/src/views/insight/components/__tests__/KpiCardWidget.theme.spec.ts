import { describe, expect, it } from 'vitest'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'
import { resolveMetricVisual } from '@/utils/kpi-metrics'

describe('KpiCardWidget · 主题视觉意图', () => {
  it('同一张卡内的指标共享一个强调色，只靠图标做形状区分', () => {
    const fields = ['a', 'b', 'c', 'd'].map((name) => ({ name, displayName: name }))
    const metrics = buildKpiMetrics(fields)
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light')
    const visuals = metrics.map((metric, index) => resolveMetricVisual(metric, undefined, theme, index))
    expect(new Set(visuals.map((item) => item.iconKey)).size).toBe(4)
    expect(new Set(visuals.map((item) => item.accentColor)).size).toBe(1)
    expect(visuals[0]?.accentColor).toBe(theme.primary)
  })

  it('只有显式 custom 才能引入第三个色号', () => {
    const metrics = buildKpiMetrics([{ name: 'a', displayName: 'a' }, { name: 'b', displayName: 'b' }])
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light')
    const visuals = metrics.map((metric, index) => resolveMetricVisual(metric, undefined, theme, index))
    const custom = resolveMetricVisual(metrics[1]!, { colorMode: 'custom', accentColor: '#0F766E' }, theme, 1)
    expect(custom.accentColor).toBe('#0F766E')
    expect(new Set([...visuals.map((item) => item.accentColor), custom.accentColor]).size).toBe(2)
  })

  it('关闭图标时只关闭图标，指标标题仍可读', () => {
    const metric = buildKpiMetrics([{ name: 'a', displayName: '关联计划数' }])[0]
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    const visual = resolveMetricVisual(metric, { iconKey: null }, theme, 0)
    expect(visual.iconKey).toBeNull()
    expect(metric.displayName).toBe('关联计划数')
  })
})
