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

  it('隐藏边框且无自定义色时，顶线被显式置透明以覆盖主题强调色', () => {
    // 修复：边框隐藏后仍可见顶部强调色（被误认为边框未隐藏）
    expect(resolveComponentVisualStyle({ border: { mode: 'hidden' } }, 'kpi')['--component-group-accent']).toBe('transparent')
    // 控制区（filter/timeFilter）本就不下发强调色，隐藏边框同样回落透明
    expect(resolveComponentVisualStyle({ border: { mode: 'hidden' } }, 'filter')['--component-group-accent']).toBe('transparent')
  })

  it('隐藏边框时下发交互态守卫变量，让 hover/选中不再重绘边框与阴影', () => {
    // 修复：边框隐藏后，编辑选中见蓝边、预览悬停见灰边的问题
    const hidden = resolveComponentVisualStyle({ border: { mode: 'hidden' } }, 'kpi')
    expect(hidden['--component-hover-border-color']).toBe('transparent')
    expect(hidden['--component-selected-border-color']).toBe('transparent')
    expect(hidden['--component-hover-shadow']).toBe('none')
    expect(hidden['--component-selected-shadow']).toBe('none')

    // 关闭阴影同样抹掉交互态阴影
    const shadowNone = resolveComponentVisualStyle({ border: { mode: 'theme' }, shadow: 'none' }, 'kpi')
    expect(shadowNone['--component-hover-shadow']).toBe('none')
    expect(shadowNone['--component-selected-shadow']).toBe('none')
    // 边框可见时交互态边框色不下发，回落 CSS 可视默认值（--db-border-strong / --db-accent）
    expect(shadowNone['--component-hover-border-color']).toBeUndefined()
    expect(shadowNone['--component-selected-border-color']).toBeUndefined()
  })

  it('边框可见且阴影非 none 时不下发守卫变量，交互态回落原可视默认值', () => {
    const visible = resolveComponentVisualStyle({ border: { mode: 'theme' }, shadow: 'subtle' }, 'kpi')
    expect(visible['--component-hover-border-color']).toBeUndefined()
    expect(visible['--component-hover-shadow']).toBeUndefined()
    expect(visible['--component-selected-shadow']).toBeUndefined()
  })

})
