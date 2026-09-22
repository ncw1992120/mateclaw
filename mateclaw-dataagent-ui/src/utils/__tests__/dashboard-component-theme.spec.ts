import { describe, expect, it } from 'vitest'
import { resolveDashboardTheme, componentThemeStyle, resolveDashboardIcon } from '@/utils/dashboard-theme'

describe('dashboard component theme standards', () => {
  it('uses standard icon, hierarchy and automatic group color defaults', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'amber' }, 'warm')

    expect(theme.iconMode).toBe('show')
    expect(theme.hierarchy).toBe('standard')
    expect(theme.componentColorMode).toBe('auto')
    expect(componentThemeStyle(theme, 'kpi')).toEqual(expect.objectContaining({
      '--component-group-accent': theme.metricPalette[0],
      '--component-group-border': expect.stringContaining('color-mix'),
    }))
  })

  it('allows only standard choices to change the rendering policy', () => {
    const theme = resolveDashboardTheme({
      mode: 'preset',
      presetId: 'amber',
      iconMode: 'hide',
      hierarchy: 'strong',
      componentColorMode: 'uniform',
    }, 'warm')

    expect(theme.iconMode).toBe('hide')
    expect(theme.hierarchy).toBe('strong')
    expect(theme.componentColorMode).toBe('uniform')
    expect(componentThemeStyle(theme, 'chart')['--component-group-accent']).toBe(theme.primary)
  })

  it('maps component and tab semantics to stable system icons', () => {
    expect(resolveDashboardIcon('kpi')).toBe('DataAnalysis')
    expect(resolveDashboardIcon('chart', 'line')).toBe('TrendCharts')
    expect(resolveDashboardIcon('filter')).toBe('Filter')
    expect(resolveDashboardIcon('tab', undefined, '策略视角')).toBe('Aim')
  })
})
