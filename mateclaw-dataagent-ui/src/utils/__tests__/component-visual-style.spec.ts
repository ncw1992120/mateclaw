import { describe, expect, it } from 'vitest'
import { defaultComponentVisualStyle, resolveComponentVisualStyle, normalizeComponentVisualStyle } from '../component-visual-style'

describe('component-visual-style', () => {
  it('为不同组件提供不会制造额外框线的默认层级', () => {
    expect(defaultComponentVisualStyle('combination').border?.mode).toBe('hidden')
    expect(defaultComponentVisualStyle('filter').background?.mode).toBe('transparent')
    expect(defaultComponentVisualStyle('kpi').border?.mode).toBe('theme')
  })

  it('支持跟随主题、显示和隐藏三态边框，边框本体不再消费自定义色', () => {
    expect(resolveComponentVisualStyle({ border: { mode: 'theme' } }, 'kpi')['--component-border']).toBe('1px solid var(--db-border)')
    expect(resolveComponentVisualStyle({ border: { mode: 'visible', colorMode: 'custom', color: '#FF5500', width: 2 } }, 'kpi')['--component-border']).toBe('2px solid var(--db-border)')
    expect(resolveComponentVisualStyle({ border: { mode: 'hidden' } }, 'kpi')['--component-border']).toBe('1px solid transparent')
  })

  it('自定义颜色作为顶线颜色输出，与边框模式无关', () => {
    expect(resolveComponentVisualStyle({ border: { mode: 'visible', colorMode: 'custom', color: '#FF5500' } }, 'kpi')['--component-group-accent']).toBe('#FF5500')
    expect(resolveComponentVisualStyle({ border: { mode: 'hidden', colorMode: 'custom', color: '#FF5500' } }, 'kpi')['--component-group-accent']).toBe('#FF5500')
    expect(resolveComponentVisualStyle({ border: { mode: 'theme' } }, 'kpi')['--component-group-accent']).toBeUndefined()
    expect(resolveComponentVisualStyle({ border: { mode: 'theme', colorMode: 'custom', color: '#FF5500' } }, 'kpi')['--component-border']).toBe('1px solid var(--db-border)')
  })

})
