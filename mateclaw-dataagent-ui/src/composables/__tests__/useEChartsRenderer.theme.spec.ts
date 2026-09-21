import { describe, expect, it } from 'vitest'
import { withDashboardChartTheme } from '@/composables/useEChartsRenderer'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'

describe('withDashboardChartTheme', () => {
  it('无显式色板时注入图表色板并返回新对象', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'teal' }, 'light')
    const raw = { series: [{ type: 'bar', data: [1, 2] }] }
    const themed = withDashboardChartTheme(raw, theme)
    expect(themed.color).toEqual(theme.chartPalette)
    expect(themed).not.toBe(raw)
    expect(raw).not.toHaveProperty('color')
  })

  it('显式 option.color 优先且轴/图例文字跟随主题', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light')
    const raw = { color: ['#123456'], xAxis: {}, legend: {}, series: [{ data: [1] }] }
    const themed = withDashboardChartTheme(raw, theme)
    expect(themed.color).toEqual(['#123456'])
    expect(themed.xAxis.axisLabel.color).toBe(theme.textSecondary)
    expect(themed.legend.textStyle.color).toBe(theme.textSecondary)
  })
})
