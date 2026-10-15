import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import DashboardPanel from '../DashboardPanel.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

describe('DashboardPanel tab semantics', () => {
  it('exposes dashboard and raw-data tabs with selection state and arrow navigation', async () => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    })
    const wrapper = mount(DashboardPanel, { global: { plugins: [i18n] } })
    const tabs = wrapper.findAll('.tabs .tab')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].attributes('role')).toBe('tab')
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    await tabs[0].trigger('keydown', { key: 'ArrowRight' })
    expect(tabs[1].attributes('aria-selected')).toBe('true')
    expect(tabs[1].attributes('tabindex')).toBe('0')
    expect(tabs[0].attributes('tabindex')).toBe('-1')
  })
})
