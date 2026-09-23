import { describe, expect, it } from 'vitest'
import { resolveDashboardTheme, componentThemeStyle, componentIconStyle, resolveDashboardIcon } from '@/utils/dashboard-theme'

describe('dashboard component theme standards', () => {
  it('uses standard icon, hierarchy and automatic group color defaults', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'amber' }, 'warm')

    expect(theme.iconMode).toBe('show')
    expect(theme.hierarchy).toBe('standard')
    expect(theme.componentColorMode).toBe('auto')
    expect(theme.iconPalette).toHaveLength(5)
    expect(componentThemeStyle(theme, 'kpi')).toEqual(expect.objectContaining({
      '--component-group-accent': theme.primary,
      '--component-group-border': expect.stringContaining('color-mix'),
    }))
  })

  it('limits accent hues to primary plus one alternative across three semantic zones', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')

    expect(componentThemeStyle(theme, 'kpi')['--component-group-accent']).toBe(theme.primary)
    expect(componentThemeStyle(theme, 'chart')['--component-group-accent']).toBe(theme.primary)
    expect(componentThemeStyle(theme, 'table')['--component-group-accent']).toBe(theme.primary)
    expect(componentThemeStyle(theme, 'aiAnalysis')['--component-group-accent']).toBe(theme.accentAlt)
    // 控制区不吃色相：不下发 accent，下游回落中性 --db-*
    expect(componentThemeStyle(theme, 'filter')['--component-group-accent']).toBeUndefined()
    expect(componentThemeStyle(theme, 'timeFilter')['--component-group-accent']).toBeUndefined()
    expect(new Set([theme.primary, theme.accentAlt]).size).toBe(2)
  })

  it('collapses every zone to the primary color in uniform mode', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue', componentColorMode: 'uniform' }, 'light')

    expect(componentThemeStyle(theme, 'aiAnalysis')['--component-group-accent']).toBe(theme.primary)
    expect(componentThemeStyle(theme, 'filter')['--component-group-accent']).toBe(theme.primary)
  })

  it('uses a larger, bold warm semantic icon style instead of inheriting one text color', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    const metric = componentIconStyle(theme, 'kpi', undefined, 0)
    const metricNext = componentIconStyle(theme, 'kpi', undefined, 1)
    const ai = componentIconStyle(theme, 'aiAnalysis')

    expect(metric['--dashboard-icon-size']).toBe('18px')
    expect(metric['--dashboard-icon-weight']).toBe('800')
    expect(metric['--dashboard-icon-color']).not.toBe(ai['--dashboard-icon-color'])
    expect(metric['--dashboard-icon-color']).toBe(theme.iconPalette[0])
    expect(metricNext['--dashboard-icon-color']).toBe(theme.iconPalette[1])
    expect(theme.iconPalette[0]).not.toBe(theme.primary)
    expect(componentIconStyle(theme, 'tab', '策略视角')['--dashboard-icon-color']).not.toBe(metric['--dashboard-icon-color'])
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
