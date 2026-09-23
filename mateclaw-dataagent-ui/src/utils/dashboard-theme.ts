import type {
  DashboardDensity,
  DashboardThemeConfig,
  DashboardThemeOverrides,
  DashboardThemeComponentColorMode,
  DashboardThemeHierarchy,
  DashboardThemeIconMode,
  ChartType,
  InsightComponentType,
  ComponentThemeAccentGroup,
  ResolvedDashboardTheme,
  ThemeValidationError,
} from '@/types'

type ThemeToken = Omit<ResolvedDashboardTheme, 'source' | 'presetId' | 'mode' | 'overrides'>

interface ThemePreset extends Omit<ThemeToken, 'iconPalette'> {
  label: string
}

const palette = (values: string[]): string[] => values.slice(0, 5)

export const DASHBOARD_THEME_PRESETS: Record<string, ThemePreset> = {
  blue: {
    label: '经典蓝', pageBackground: '#F7F8FA', cardBackground: '#FFFFFF', border: '#E1E5EB', text: '#172B4D', textSecondary: '#52627A', textMuted: '#7A889D', primary: '#1E40AF', accentAlt: '#0F766E', positive: '#047857', negative: '#B42318', warning: '#B45309', info: '#2563EB',
    metricPalette: palette(['#1E40AF', '#2563EB', '#0F766E', '#B45309', '#7C3AED']), chartPalette: palette(['#2563EB', '#14B8A6', '#F59E0B', '#8B5CF6', '#EC4899']), radius: 'medium', shadow: 'subtle',
  },
  indigo: {
    label: '靛青', pageBackground: '#F5F5FF', cardBackground: '#FFFFFF', border: '#DFDFF5', text: '#1E1B4B', textSecondary: '#57547A', textMuted: '#7B7895', primary: '#4F46E5', accentAlt: '#0F766E', positive: '#047857', negative: '#B42318', warning: '#B45309', info: '#6366F1',
    metricPalette: palette(['#4F46E5', '#6366F1', '#0F766E', '#C2410C', '#7C3AED']), chartPalette: palette(['#4F46E5', '#06B6D4', '#F97316', '#A855F7', '#10B981']), radius: 'medium', shadow: 'subtle',
  },
  teal: {
    label: '青碧', pageBackground: '#F2F9F8', cardBackground: '#FFFFFF', border: '#D3E9E5', text: '#123B3A', textSecondary: '#4B6865', textMuted: '#728B87', primary: '#0F766E', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#B45309', info: '#0E7490',
    metricPalette: palette(['#0F766E', '#0D9488', '#1D4ED8', '#B45309', '#7C3AED']), chartPalette: palette(['#0F766E', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899']), radius: 'medium', shadow: 'subtle',
  },
  amber: {
    label: '琥珀', pageBackground: '#FFFBF2', cardBackground: '#FFFFFF', border: '#F0E1C0', text: '#422006', textSecondary: '#6B4F2A', textMuted: '#8A7353', primary: '#B45309', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#A16207', info: '#2563EB',
    metricPalette: palette(['#B45309', '#D97706', '#1D4ED8', '#0F766E', '#9F1239']), chartPalette: palette(['#D97706', '#2563EB', '#0F766E', '#BE185D', '#7C3AED']), radius: 'medium', shadow: 'subtle',
  },
  'dark-data': {
    label: '深色数据', pageBackground: '#0D1117', cardBackground: '#161B26', border: '#303A4A', text: '#EDF1F7', textSecondary: '#C3CCD9', textMuted: '#9AA6B6', primary: '#60A5FA', accentAlt: '#34D399', positive: '#57D38C', negative: '#FF8A80', warning: '#FBBF24', info: '#93C5FD',
    metricPalette: palette(['#60A5FA', '#38BDF8', '#34D399', '#FBBF24', '#C084FC']), chartPalette: palette(['#60A5FA', '#2DD4BF', '#FBBF24', '#C084FC', '#FB7185']), radius: 'medium', shadow: 'elevated',
  },
  rose: {
    label: '玫瑰红', pageBackground: '#FFF7FA', cardBackground: '#FFFFFF', border: '#F3D8E3', text: '#4A1728', textSecondary: '#704356', textMuted: '#927184', primary: '#BE185D', accentAlt: '#0F766E', positive: '#047857', negative: '#B42318', warning: '#B45309', info: '#2563EB',
    metricPalette: palette(['#BE185D', '#DB2777', '#C2410C', '#0F766E', '#7C3AED']), chartPalette: palette(['#BE185D', '#F43F5E', '#F97316', '#14B8A6', '#8B5CF6']), radius: 'large', shadow: 'subtle',
  },
  coral: {
    label: '珊瑚橙', pageBackground: '#FFF8F5', cardBackground: '#FFFFFF', border: '#F4DDD5', text: '#431C12', textSecondary: '#704A3E', textMuted: '#94766A', primary: '#C2410C', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#A16207', info: '#2563EB',
    metricPalette: palette(['#C2410C', '#EA580C', '#BE185D', '#0F766E', '#7C3AED']), chartPalette: palette(['#F97316', '#FB7185', '#14B8A6', '#6366F1', '#EAB308']), radius: 'large', shadow: 'subtle',
  },
  orange: {
    label: '暖橙', pageBackground: '#FFF9F2', cardBackground: '#FFFFFF', border: '#F1DFC7', text: '#431F08', textSecondary: '#704D2F', textMuted: '#95785A', primary: '#EA580C', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#A16207', info: '#2563EB',
    metricPalette: palette(['#EA580C', '#F97316', '#BE185D', '#0F766E', '#7C3AED']), chartPalette: palette(['#EA580C', '#F59E0B', '#14B8A6', '#8B5CF6', '#F43F5E']), radius: 'medium', shadow: 'subtle',
  },
  gold: {
    label: '金黄', pageBackground: '#FFFCF2', cardBackground: '#FFFFFF', border: '#EFE2BA', text: '#3B2A05', textSecondary: '#67552A', textMuted: '#89774B', primary: '#A16207', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#A16207', info: '#2563EB',
    metricPalette: palette(['#A16207', '#CA8A04', '#C2410C', '#0F766E', '#BE185D']), chartPalette: palette(['#EAB308', '#F97316', '#14B8A6', '#6366F1', '#F43F5E']), radius: 'medium', shadow: 'subtle',
  },
  burgundy: {
    label: '酒红', pageBackground: '#FFF7F7', cardBackground: '#FFFFFF', border: '#EFD9DC', text: '#45151B', textSecondary: '#6F444A', textMuted: '#927277', primary: '#9F1239', accentAlt: '#1D4ED8', positive: '#047857', negative: '#B42318', warning: '#B45309', info: '#2563EB',
    metricPalette: palette(['#9F1239', '#BE123C', '#C2410C', '#0F766E', '#7C3AED']), chartPalette: palette(['#9F1239', '#F43F5E', '#F97316', '#14B8A6', '#8B5CF6']), radius: 'large', shadow: 'subtle',
  },
}

const LEGACY_THEME: ResolvedDashboardTheme = {
  source: 'legacy', mode: 'light', pageBackground: '', cardBackground: '', border: '', text: '', textSecondary: '', textMuted: '', primary: '', accentAlt: '', positive: '', negative: '', warning: '', info: '', metricPalette: [], chartPalette: [], iconPalette: [], radius: 'medium', shadow: 'subtle', iconMode: 'show', hierarchy: 'standard', componentColorMode: 'auto', overrides: {},
}

const STANDARD_THEME_OPTIONS: {
  iconMode: DashboardThemeIconMode
  hierarchy: DashboardThemeHierarchy
  componentColorMode: DashboardThemeComponentColorMode
} = { iconMode: 'show', hierarchy: 'standard', componentColorMode: 'auto' }

function isColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value.trim())
}

function darkenHex(hex: string, amount: number): string {
  if (!isColor(hex)) return hex
  const channels = [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16))
  return `#${channels.map((channel) => Math.round(channel * (1 - amount)).toString(16).padStart(2, '0')).join('').toUpperCase()}`
}

function mergePreset(preset: ThemePreset, overrides: DashboardThemeOverrides): ThemeToken {
  const merged = { ...preset, ...overrides }
  const chart = Array.isArray(overrides.chartPalette) ? overrides.chartPalette : preset.chartPalette
  const metric = Array.isArray(overrides.metricPalette)
    ? overrides.metricPalette
    : Array.isArray(overrides.chartPalette) ? chart : preset.metricPalette
  const iconPalette = palette([0.16, 0.26, 0.36, 0.46, 0.56].map((amount) => darkenHex(preset.primary, amount)))
  return { ...merged, chartPalette: chart, metricPalette: metric, iconPalette } as ThemeToken
}

export function resolveDashboardTheme(config: DashboardThemeConfig | undefined, globalMode: ResolvedDashboardTheme['mode']): ResolvedDashboardTheme {
  if (!config) return { ...LEGACY_THEME, mode: globalMode }
  const presetId = typeof config.presetId === 'string' && DASHBOARD_THEME_PRESETS[config.presetId] ? config.presetId : 'blue'
  const preset = DASHBOARD_THEME_PRESETS[presetId]
  const overrides = config.overrides ?? {}
  return {
    ...mergePreset(preset, overrides),
    source: 'configured',
    presetId,
    mode: globalMode,
    iconMode: config.iconMode ?? STANDARD_THEME_OPTIONS.iconMode,
    hierarchy: config.hierarchy ?? STANDARD_THEME_OPTIONS.hierarchy,
    componentColorMode: config.componentColorMode ?? STANDARD_THEME_OPTIONS.componentColorMode,
    overrides: { ...overrides },
  }
}

export function themeCssVariables(theme: ResolvedDashboardTheme): Record<string, string> {
  if (theme.source === 'legacy') return {}
  const mix = (foreground: string, background: string, foregroundAmount: number): string =>
    `color-mix(in srgb, ${foreground} ${foregroundAmount}%, ${background})`
  const vars: Record<string, string> = {
    '--insight-page-bg': theme.pageBackground, '--insight-card-bg': theme.cardBackground, '--insight-border': theme.border,
    '--insight-text': theme.text, '--insight-text-secondary': theme.textSecondary, '--insight-text-muted': theme.textMuted,
    '--insight-primary': theme.primary, '--insight-accent-alt': theme.accentAlt || theme.metricPalette[1] || theme.primary, '--insight-positive': theme.positive, '--insight-negative': theme.negative,
    '--insight-warning': theme.warning, '--insight-info': theme.info,
    '--db-bg': 'var(--insight-page-bg)', '--db-card': 'var(--insight-card-bg)', '--db-border': 'var(--insight-border)',
    '--db-hover': mix('var(--insight-text)', 'var(--insight-card-bg)', 10),
    '--db-muted': mix('var(--insight-text)', 'var(--insight-card-bg)', 16),
    '--db-strong': 'var(--insight-text)',
    '--db-text': 'var(--insight-text)', '--db-text-primary': 'var(--insight-text)',
    '--db-text-secondary': 'var(--insight-text-secondary)', '--db-text-muted': 'var(--insight-text-muted)',
    '--db-text-quaternary': 'var(--insight-text-muted)', '--db-accent': 'var(--insight-primary)',
    '--db-border-strong': mix('var(--insight-text)', 'var(--insight-border)', 34),
    // 页面级层次：外层画布 → 组合容器 → 子卡片/控件。组件只消费这些 Token，
    // 不再各自猜测主题背景，保证编辑器和正式预览使用同一层级。
    '--db-surface-container': mix('var(--insight-border)', 'var(--insight-page-bg)', 35),
    '--db-surface-card': 'var(--db-card)',
    '--db-surface-nested': mix('var(--insight-border)', 'var(--insight-card-bg)', 55),
    '--db-surface-control': mix('var(--insight-text)', 'var(--insight-card-bg)', 4),
    '--db-surface-control-hover': 'var(--db-hover)',
    '--db-focus': 'var(--db-accent)',
    // 向正文色混合：浅色主题下变深、深色主题下变亮，避免像 #4f46e5 那样硬编码死色。
    '--db-accent-strong': mix('var(--insight-primary)', 'var(--insight-text)', 18),
    '--db-accent-alt': 'var(--insight-accent-alt)',
    '--db-accent-light': mix('var(--insight-primary)', 'transparent', 18),
    '--db-accent-border': mix('var(--insight-primary)', 'transparent', 52),
    '--db-positive': 'var(--insight-positive)', '--db-danger': 'var(--insight-negative)',
    '--db-warning': 'var(--insight-warning)',
    '--db-positive-bg': mix('var(--insight-positive)', 'transparent', 16),
    '--db-danger-bg': mix('var(--insight-negative)', 'transparent', 16),
    '--db-up': 'var(--insight-negative)', '--db-up-bg': mix('var(--insight-negative)', 'transparent', 16),
    '--db-down': 'var(--insight-positive)', '--db-down-bg': mix('var(--insight-positive)', 'transparent', 16),
    '--db-status-success-bg': mix('var(--insight-positive)', 'transparent', 20),
    '--db-status-success-fg': 'var(--insight-positive)',
    '--db-status-warning-bg': mix('var(--insight-warning)', 'transparent', 20),
    '--db-status-warning-fg': 'var(--insight-warning)',
    '--db-action-warning-fg': 'var(--insight-warning)',
    '--db-action-danger-fg': 'var(--insight-negative)',
    '--db-chart-preview-bg': mix('var(--insight-text)', 'var(--insight-card-bg)', 5),
    '--db-chart-preview-track': mix('var(--insight-border)', 'var(--insight-card-bg)', 65),
    '--db-mask': 'rgba(0, 0, 0, 0.58)',
    '--db-theme-icons': theme.iconMode === 'show' ? '1' : '0',
    '--db-theme-hierarchy': theme.hierarchy,
    '--db-theme-component-colors': theme.componentColorMode,
  }
  // 分区色由 componentThemeStyle 单独下发，chartPalette 只在图表内部消费，不外溢到卡片。
  theme.chartPalette.forEach((color, index) => {
    vars[`--insight-chart-${index + 1}`] = color
    vars[`--db-chart-${index + 1}`] = color
  })
  theme.metricPalette.forEach((color, index) => { vars[`--insight-metric-${index + 1}`] = color })
  theme.iconPalette.forEach((color, index) => { vars[`--insight-icon-${index + 1}`] = color })
  return vars
}

/** 语义分区：数据组件缺省主色，AI 洞察缺省辅色，筛选控件保持中性。 */
export function componentThemeZone(type: InsightComponentType): 'data' | 'insight' | 'control' {
  if (type === 'filter' || type === 'timeFilter') return 'control'
  if (type === 'aiAnalysis') return 'insight'
  return 'data'
}

/** 将组件强调色分组映射到有限的三色主题；琥珀使用当前主题的第四个指标色。 */
export function componentAccentColor(theme: ResolvedDashboardTheme, group: ComponentThemeAccentGroup): string {
  if (group === 'secondary') return theme.accentAlt || theme.metricPalette[2] || theme.primary
  if (group === 'highlight') return theme.metricPalette[3] || '#B45309'
  return theme.primary
}

/** 输出给画布/组合卡片的分区层次 Token；显式组件样式可在调用方覆盖。 */
export function componentThemeStyle(
  theme: ResolvedDashboardTheme | undefined,
  type: InsightComponentType,
  _depth = 0,
  accentGroup?: ComponentThemeAccentGroup,
  componentColor?: string,
): Record<string, string> {
  if (!theme || theme.source === 'legacy') return {}
  // 筛选控件始终中性，不随「统一主色」策略着色。
  if (componentThemeZone(type) === 'control') return {}
  const defaultGroup: ComponentThemeAccentGroup = theme.componentColorMode === 'uniform'
    ? 'primary'
    : componentThemeZone(type) === 'insight' ? 'secondary' : 'primary'
  const accent = isHexColor(componentColor) ? componentColor : componentAccentColor(theme, accentGroup ?? defaultGroup)
  const hierarchyAmount = theme.hierarchy === 'soft' ? 4 : theme.hierarchy === 'strong' ? 12 : 7
  const mix = (foreground: string, background: string, amount: number): string => `color-mix(in srgb, ${foreground} ${amount}%, ${background})`
  return {
    '--component-group-accent': accent,
    '--component-group-border': mix('var(--insight-text)', 'var(--insight-border)', hierarchyAmount),
    '--component-group-surface': 'var(--insight-card-bg)',
    '--component-group-header-surface': 'var(--insight-card-bg)',
  }
}

/** 图标只使用主题三色；筛选器与页签图标走中性色/主色，不再按序号生成额外色相。 */
export function componentIconStyle(
  theme: ResolvedDashboardTheme | undefined,
  type: InsightComponentType | 'tab',
  title?: string,
  _variant = 0,
  accentGroup?: ComponentThemeAccentGroup,
  componentColor?: string,
): Record<string, string> {
  if (!theme || theme.source === 'legacy') return {}
  const color = type === 'tab'
    ? theme.primary
    : componentThemeZone(type) === 'control'
      ? theme.textSecondary
      : isHexColor(componentColor)
        ? componentColor
        : componentAccentColor(theme, accentGroup ?? (componentThemeZone(type) === 'insight' ? 'secondary' : 'primary'))
  return {
    '--dashboard-icon-size': type === 'tab' ? '16px' : '18px',
    '--dashboard-icon-weight': '800',
    '--dashboard-icon-color': color,
  }
}

function isHexColor(value: string | undefined): value is string {
  return typeof value === 'string' && /^#[\da-f]{6}$/i.test(value)
}

/** 标准语义图标注册表。返回 Element Plus 图标组件名，业务不配置图标名称。 */
export function resolveDashboardIcon(type: InsightComponentType | 'tab', chartType?: ChartType, title?: string): string {
  if (type === 'tab') {
    if (title?.includes('指标')) return 'DataAnalysis'
    if (title?.includes('计划')) return 'Calendar'
    if (title?.includes('策略')) return 'Aim'
    return 'Collection'
  }
  if (type === 'kpi') return 'DataAnalysis'
  if (type === 'chart') {
    if (chartType === 'line' || chartType === 'area') return 'TrendCharts'
    if (chartType === 'bar' || chartType === 'pictorialBar') return 'Histogram'
    if (chartType === 'pie' || chartType === 'funnel') return 'PieChart'
    return 'DataAnalysis'
  }
  if (type === 'table') return 'Grid'
  if (type === 'filter') return 'Filter'
  if (type === 'timeFilter') return 'Calendar'
  if (type === 'aiAnalysis') return 'MagicStick'
  return 'Collection'
}

export function densityScale(density: DashboardDensity | string | undefined): { metricName: number; metricValue: number; icon: number } {
  const scales = {
    compact: { metricName: 12, metricValue: 24, icon: 28 },
    standard: { metricName: 14, metricValue: 28, icon: 32 },
    large: { metricName: 16, metricValue: 36, icon: 40 },
  }
  return scales[density as keyof typeof scales] ?? scales.standard
}

export function themeSource(theme: ResolvedDashboardTheme, token: keyof ThemeToken): string {
  if (theme.source === 'legacy') return '旧配色'
  if (Object.prototype.hasOwnProperty.call(theme.overrides, token)) return '主题覆盖'
  if (token === 'metricPalette' && !theme.overrides.metricPalette) return '回退'
  return '预设'
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16) / 255).map((channel) => channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
}

function contrastRatio(foreground: string, background: string): number {
  const light = Math.max(luminance(foreground), luminance(background))
  const dark = Math.min(luminance(foreground), luminance(background))
  return (light + 0.05) / (dark + 0.05)
}

export function validateDashboardTheme(config: DashboardThemeConfig): ThemeValidationError[] {
  const errors: ThemeValidationError[] = []
  if (!config.presetId || !DASHBOARD_THEME_PRESETS[config.presetId]) errors.push({ code: 'invalid_preset', field: 'presetId', message: '预设主题不存在，将回退到经典蓝' })
  const overrides = config.overrides ?? {}
  for (const [field, value] of Object.entries(overrides)) {
    if (field.endsWith('Palette')) {
      if (!Array.isArray(value) || value.length < 5 || value.some((color) => !isColor(color))) errors.push({ code: Array.isArray(value) && value.some((color) => !isColor(color)) ? 'invalid_color' : 'invalid_palette', field, message: '色板需要至少 5 个合法十六进制颜色' })
    } else if (['pageBackground', 'cardBackground', 'border', 'text', 'textSecondary', 'textMuted', 'primary', 'accentAlt', 'positive', 'negative', 'warning', 'info'].includes(field) && !isColor(value)) {
      errors.push({ code: 'invalid_color', field, message: '颜色必须是 6 位十六进制值' })
    }
  }
  const resolved = resolveDashboardTheme(config, 'light')
  if (isColor(resolved.text) && isColor(resolved.cardBackground) && contrastRatio(resolved.text, resolved.cardBackground) < 4.5) errors.push({ code: 'contrast_too_low', field: 'text', message: '正文与卡片背景对比度低于 4.5:1' })
  return errors
}
