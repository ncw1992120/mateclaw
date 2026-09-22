import { describe, expect, it } from 'vitest'
import {
  densityScale,
  resolveDashboardTheme,
  themeCssVariables,
  themeSource,
  validateDashboardTheme,
} from '@/utils/dashboard-theme'

describe('dashboard theme · 旧配置兼容与预设', () => {
  it('没有 theme 时保留旧 --db-* 视觉，不注入局部变量', () => {
    const legacy = resolveDashboardTheme(undefined, 'light')
    expect(legacy.source).toBe('legacy')
    expect(themeCssVariables(legacy)).toEqual({})
  })

  it('新仪表盘默认使用 blue 预设并区分指标与图表色板', () => {
    const blue = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    expect(blue.source).toBe('configured')
    expect(blue.presetId).toBe('blue')
    expect(blue.metricPalette).toHaveLength(5)
    expect(blue.chartPalette).toHaveLength(5)
    expect(blue.metricPalette).not.toEqual(blue.chartPalette)
  })

  it('10 种预设都完整，5 种暖色预设的正文/卡片对比度合格', () => {
    const ids = ['blue', 'indigo', 'teal', 'amber', 'dark-data', 'rose', 'coral', 'orange', 'gold', 'burgundy'] as const
    for (const presetId of ids) {
      const theme = resolveDashboardTheme({ mode: 'preset', presetId }, 'light')
      expect(theme.chartPalette).toHaveLength(5)
      expect(theme.metricPalette).toHaveLength(5)
      expect(theme.pageBackground).toMatch(/^#|^rgb|^rgba/)
      expect(theme.cardBackground).toMatch(/^#|^rgb|^rgba/)
      expect(validateDashboardTheme({ mode: 'preset', presetId })).toEqual([])
    }
  })

  it('局部覆盖只改变点选字段，指标色板缺失时回退图表色板', () => {
    const theme = resolveDashboardTheme({
      mode: 'custom',
      presetId: 'teal',
      overrides: {
        text: '#102030',
        chartPalette: ['#111111', '#222222', '#333333', '#444444', '#555555'],
      },
    }, 'light')
    expect(theme.text).toBe('#102030')
    expect(theme.cardBackground).toBe('#FFFFFF')
    expect(theme.metricPalette).toEqual(theme.chartPalette)
    expect(themeSource(theme, 'text')).toBe('主题覆盖')
    expect(themeSource(theme, 'metricPalette')).toBe('回退')
  })

  it('非法 preset 与颜色会返回可诊断的校验错误并回退到 blue', () => {
    const fallback = resolveDashboardTheme({ mode: 'preset', presetId: 'missing' }, 'light')
    expect(fallback.presetId).toBe('blue')
    const errors = validateDashboardTheme({
      mode: 'custom',
      presetId: 'blue',
      overrides: { text: '#FFFFFF', cardBackground: '#FFFFFF', chartPalette: ['not-a-color'] },
    })
    expect(errors).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'contrast_too_low' }),
      expect.objectContaining({ code: 'invalid_color' }),
    ]))
  })
})

describe('dashboard theme · 变量和密度', () => {
  it('输出局部 insight 变量，同时允许组件继续使用 db token', () => {
    const vars = themeCssVariables(resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light'))
    expect(vars['--insight-page-bg']).toBe('#FFF7FA')
    expect(vars['--insight-chart-1']).toBeDefined()
    expect(vars['--db-bg']).toBe('var(--insight-page-bg)')
    expect(vars['--db-accent']).toBe('var(--insight-primary)')
  })

  it('深色预设完整覆盖交互背景、边框、状态背景和图表预览 Token', () => {
    const vars = themeCssVariables(resolveDashboardTheme({ mode: 'preset', presetId: 'dark-data' }, 'light'))

    expect(vars['--db-hover']).toContain('color-mix')
    expect(vars['--db-muted']).toContain('color-mix')
    expect(vars['--db-border-strong']).toContain('color-mix')
    expect(vars['--db-accent-light']).toContain('color-mix')
    expect(vars['--db-positive-bg']).toContain('color-mix')
    expect(vars['--db-danger-bg']).toContain('color-mix')
    expect(vars['--db-chart-preview-bg']).toContain('color-mix')
    expect(vars['--db-text-primary']).toBe('var(--insight-text)')
    expect(vars['--db-chart-1']).toBe('#60A5FA')
  })

  it('字号密度按 compact、standard、large 递增，未知值回退 standard', () => {
    expect(densityScale('compact').metricValue).toBeLessThan(densityScale('standard').metricValue)
    expect(densityScale('large').metricValue).toBeGreaterThan(densityScale('standard').metricValue)
    expect(densityScale(undefined)).toEqual(densityScale('standard'))
    expect(densityScale('unknown' as never)).toEqual(densityScale('standard'))
  })
})
