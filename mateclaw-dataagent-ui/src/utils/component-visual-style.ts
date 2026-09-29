import type { ComponentVisualStyle, InsightComponentType } from '@/types'

const SHADOW_TOKENS: Record<NonNullable<ComponentVisualStyle['shadow']>, string> = {
  none: 'none',
  subtle: 'var(--shadow-card, 0 2px 10px rgba(15, 23, 42, 0.06))',
  medium: 'var(--shadow-card-hover, 0 8px 24px rgba(15, 23, 42, 0.12))',
}

export function defaultComponentVisualStyle(type: InsightComponentType): ComponentVisualStyle {
  const noFrame = type === 'combination' || type === 'filter' || type === 'timeFilter'
  return {
    border: { mode: noFrame ? 'hidden' : 'theme', colorMode: 'theme', width: 1, style: 'solid' },
    background: { mode: type === 'filter' || type === 'timeFilter' ? 'transparent' : 'theme' },
    radius: 12,
    shadow: noFrame ? 'none' : 'subtle',
    padding: 0,
  }
}

export function normalizeComponentVisualStyle(
  style: ComponentVisualStyle | undefined,
  type: InsightComponentType,
): ComponentVisualStyle {
  const defaults = defaultComponentVisualStyle(type)
  const borderMode = style?.border?.mode
  const borderColor = style?.border?.color
  return {
    ...defaults,
    ...style,
    radius: style?.radius ?? defaults.radius,
    padding: style?.padding ?? defaults.padding,
    shadow: style?.shadow ?? defaults.shadow,
    border: {
      ...defaults.border,
      ...(style?.border as ComponentVisualStyle['border'] | undefined),
      mode: borderMode ?? defaults.border?.mode ?? 'theme',
      colorMode: (style?.border as ComponentVisualStyle['border'] | undefined)?.colorMode
        ?? (borderColor ? 'custom' : 'theme'),
      ...(borderColor ? { color: borderColor } : {}),
    },
    background: { ...defaults.background, ...style?.background },
  }
}

export function resolveComponentVisualStyle(
  style: ComponentVisualStyle | undefined,
  type: InsightComponentType,
): Record<string, string> {
  const normalized = normalizeComponentVisualStyle(style, type)
  const border = normalized.border ?? { mode: 'hidden' as const }
  // 边框颜色语义已迁移为顶线颜色：自定义色不再画边框，而是覆盖卡片顶线（--component-group-accent），
  // 顶线独立于边框模式，即使边框隐藏/跟随主题也可生效
  const topline = border.colorMode === 'custom' && border.color ? border.color : undefined
  const background = normalized.background?.mode === 'custom' && normalized.background.color
    ? normalized.background.color
    : normalized.background?.mode === 'transparent'
      ? 'transparent'
      : 'var(--db-surface-card, var(--db-card))'

  return {
    '--component-border': border.mode === 'hidden'
      ? '1px solid transparent'
      : `${border.width ?? 1}px ${border.style ?? 'solid'} var(--db-border)`,
    '--component-surface': background,
    ...(topline ? { '--component-group-accent': topline } : {}),
    '--component-radius': `${normalized.radius ?? 12}px`,
    '--component-shadow': SHADOW_TOKENS[normalized.shadow ?? 'subtle'],
    '--component-padding': `${normalized.padding ?? 0}px`,
  }
}
