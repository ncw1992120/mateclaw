export type CombinationBackgroundMode = 'theme' | 'custom'

/**
 * 组合卡片背景兼容规则：新配置显式声明来源；旧配置的历史默认白色视为跟随主题，
 * 其他已保存颜色视为用户自定义，避免升级时覆盖已有视觉配置。
 */
export function normalizeCombinationBackgroundMode(mode: unknown, background: unknown): CombinationBackgroundMode {
  if (mode === 'theme' || mode === 'custom') return mode
  return typeof background === 'string' && /^#fff(?:fff)?$/i.test(background.trim()) ? 'theme' : 'custom'
}

export function resolveCombinationBackground(background: unknown, mode: unknown): string {
  if (normalizeCombinationBackgroundMode(mode, background) === 'theme') return 'var(--db-surface-container)'
  return typeof background === 'string' && background.trim() ? background : 'var(--db-surface-container)'
}
