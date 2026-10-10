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
  // 顶线独立于边框模式——设置了自定义色时即使边框隐藏也保留顶线强调色。
  const topline = border.colorMode === 'custom' && border.color ? border.color : undefined
  const background = normalized.background?.mode === 'custom' && normalized.background.color
    ? normalized.background.color
    : normalized.background?.mode === 'transparent'
      ? 'transparent'
      : 'var(--db-surface-card, var(--db-card))'

  // 顶线（= 边框颜色语义的载体）输出规则：
  // 1. 有自定义色时优先输出自定义色（与边框模式无关，隐藏边框也可保留顶线强调色）；
  // 2. 无自定义色且边框隐藏时，显式置 transparent，覆盖主题注入的强调色，
  //    否则主题强调色的顶线会被误认为「边框尚未隐藏」；
  // 3. 无自定义色且边框未隐藏时，不下发，回落主题强调色（zone 分区色）。
  const accent = topline ?? (border.mode === 'hidden' ? 'transparent' : undefined)

  const borderHidden = border.mode === 'hidden'
  const shadowNone = (normalized.shadow ?? 'subtle') === 'none'

  return {
    '--component-border': borderHidden
      ? '1px solid transparent'
      : `${border.width ?? 1}px ${border.style ?? 'solid'} var(--db-border)`,
    '--component-surface': background,
    ...(accent ? { '--component-group-accent': accent } : {}),
    '--component-radius': `${normalized.radius ?? 12}px`,
    '--component-shadow': SHADOW_TOKENS[normalized.shadow ?? 'subtle'],
    '--component-padding': `${normalized.padding ?? 0}px`,
    // 交互态（hover / 选中）边框与阴影：尊重组件的「隐藏边框 / 关闭阴影」设置。
    // 基础态的 --component-border / --component-shadow 在隐藏时已置 transparent / none，
    // 但 .grid-item-content:hover / .selected 与 .cc-child.selected 会硬编码重绘边框与阴影，
    // 无视隐藏设置（表现为编辑选中见蓝边、预览悬停见灰边）。
    // 此处仅在隐藏时下发守卫变量：CSS 交互态用 var(--xxx, 可视默认值) 消费，
    // 隐藏时下发给 transparent / none 把边框与阴影彻底抹掉；未隐藏时不下发，回落原可视默认值。
    ...(borderHidden ? {
      '--component-hover-border-color': 'transparent',
      '--component-selected-border-color': 'transparent',
    } : {}),
    ...(borderHidden || shadowNone ? {
      '--component-hover-shadow': 'none',
      '--component-selected-shadow': 'none',
    } : {}),
  }
}
