import { describe, expect, it } from 'vitest'
import { defaultComponentVisualStyle, resolveComponentVisualStyle, normalizeComponentVisualStyle } from '../component-visual-style'

describe('component-visual-style', () => {
  it('为不同组件提供不会制造额外框线的默认层级', () => {
    expect(defaultComponentVisualStyle('combination').border?.mode).toBe('hidden')
    expect(defaultComponentVisualStyle('filter').background?.mode).toBe('transparent')
    expect(defaultComponentVisualStyle('kpi').border?.mode).toBe('theme')
  })

  it('支持跟随主题、显示和隐藏三态边框', () => {
    expect(resolveComponentVisualStyle({ border: { mode: 'theme' } }, 'kpi')['--component-border']).toBe('1px solid var(--db-border)')
    expect(resolveComponentVisualStyle({ border: { mode: 'visible', colorMode: 'custom', color: '#FF5500', width: 2 } }, 'kpi')['--component-border']).toBe('2px solid #FF5500')
    expect(resolveComponentVisualStyle({ border: { mode: 'hidden' } }, 'kpi')['--component-border']).toBe('1px solid transparent')
  })

  it('保留旧组合边框配置并归一化为通用样式', () => {
    const style = normalizeComponentVisualStyle({ border: { enabled: true, color: '#123456' } }, 'combination')
    expect(style.border).toMatchObject({ mode: 'visible', colorMode: 'custom', color: '#123456' })
  })

  it('migrates legacy combination container style into the shared visual style', () => {
    const style = normalizeComponentVisualStyle(undefined, 'combination', {
      background: '#FFE4D6',
      backgroundMode: 'custom',
      radius: 20,
      padding: 24,
      shadow: 'medium',
      style: { border: { enabled: true, color: '#123456', mode: 'visible', colorMode: 'custom', width: 2, style: 'dashed' } },
    })

    expect(style.background).toEqual({ mode: 'custom', color: '#FFE4D6' })
    expect(style.radius).toBe(20)
    expect(style.padding).toBe(24)
    expect(style.shadow).toBe('medium')
    expect(style.border).toMatchObject({ mode: 'visible', colorMode: 'custom', color: '#123456', width: 2, style: 'dashed' })
  })
})
