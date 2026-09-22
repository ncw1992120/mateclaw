import type { ComponentVisualStyle, InsightComponentType } from '@/types'

type LegacyVisualStyle = {
  border?: {
    enabled?: boolean
    color?: string
    width?: number
    style?: 'solid' | 'dashed'
  }
}

type LegacyCombinationStyle = {
  background?: string
  backgroundMode?: 'theme' | 'custom'
  radius?: number
  padding?: number
  shadow?: 'none' | 'subtle' | 'medium'
  style?: {
    border?: LegacyVisualStyle['border'] & {
      mode?: 'theme' | 'visible' | 'hidden'
      colorMode?: 'theme' | 'custom'
    }
  }
}

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
  style: ComponentVisualStyle | LegacyVisualStyle | undefined,
  type: InsightComponentType,
  legacyCombinationStyle?: LegacyCombinationStyle,
): ComponentVisualStyle {
  const defaults = defaultComponentVisualStyle(type)
  const legacyBorder = style && 'border' in style ? style.border : legacyCombinationStyle?.style?.border
  const borderMode = 'mode' in (legacyBorder ?? {})
    ? (legacyBorder as ComponentVisualStyle['border'])?.mode
    : legacyBorder?.enabled == null
      ? defaults.border?.mode
      : legacyBorder.enabled ? 'visible' : 'hidden'
  const borderColor = legacyBorder?.color
  const legacyBackgroundMode = legacyCombinationStyle?.backgroundMode
    ?? (typeof legacyCombinationStyle?.background === 'string' && /^#fff(?:fff)?$/i.test(legacyCombinationStyle.background.trim()) ? 'theme' : 'custom')
  const legacyBackground = legacyCombinationStyle?.background && legacyBackgroundMode === 'custom'
    ? { mode: 'custom' as const, color: legacyCombinationStyle.background }
    : legacyBackgroundMode === 'theme'
      ? { mode: 'theme' as const }
      : undefined
  return {
    ...defaults,
    ...style,
    radius: style?.radius ?? legacyCombinationStyle?.radius ?? defaults.radius,
    padding: style?.padding ?? legacyCombinationStyle?.padding ?? defaults.padding,
    shadow: style?.shadow ?? legacyCombinationStyle?.shadow ?? defaults.shadow,
    border: {
      ...defaults.border,
      ...(legacyBorder as ComponentVisualStyle['border'] | undefined),
      ...(style?.border as ComponentVisualStyle['border'] | undefined),
      mode: borderMode ?? defaults.border?.mode ?? 'theme',
      colorMode: (style?.border as ComponentVisualStyle['border'] | undefined)?.colorMode
        ?? (legacyBorder as ComponentVisualStyle['border'] | undefined)?.colorMode
        ?? (borderColor ? 'custom' : 'theme'),
      ...(borderColor ? { color: borderColor } : {}),
    },
    background: { ...defaults.background, ...legacyBackground, ...style?.background },
  }
}

export function resolveComponentVisualStyle(
  style: ComponentVisualStyle | LegacyVisualStyle | undefined,
  type: InsightComponentType,
  legacyCombinationStyle?: LegacyCombinationStyle,
): Record<string, string> {
  const normalized = normalizeComponentVisualStyle(style, type, legacyCombinationStyle)
  const border = normalized.border ?? { mode: 'hidden' as const }
  const borderColor = border.colorMode === 'custom' && border.color ? border.color : 'var(--db-border)'
  const background = normalized.background?.mode === 'custom' && normalized.background.color
    ? normalized.background.color
    : normalized.background?.mode === 'transparent'
      ? 'transparent'
      : type === 'combination'
        ? 'var(--db-surface-container, var(--db-surface-card, var(--db-card)))'
        : 'var(--db-surface-card, var(--db-card))'

  return {
    '--component-border': border.mode === 'visible'
      ? `${border.width ?? 1}px ${border.style ?? 'solid'} ${borderColor}`
      : border.mode === 'theme'
        ? `${border.width ?? 1}px ${border.style ?? 'solid'} var(--db-border)`
        : '1px solid transparent',
    '--component-surface': background,
    '--component-radius': `${normalized.radius ?? 12}px`,
    '--component-shadow': SHADOW_TOKENS[normalized.shadow ?? 'subtle'],
    '--component-padding': `${normalized.padding ?? 0}px`,
  }
}
