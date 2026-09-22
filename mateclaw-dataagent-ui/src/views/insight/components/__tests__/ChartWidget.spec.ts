import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import ChartWidget from '../ChartWidget.vue'

vi.mock('@/composables/useEChartsRenderer', () => ({
  useEChartsRenderer: () => ({
    renderECharts: vi.fn(),
    disposeChart: vi.fn(),
  }),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const component = {
  id: 'chart-1',
  type: 'chart',
  title: '销售趋势',
  enableTimeFilter: false,
  tabs: [
    { id: 'overview', title: '概览' },
    { id: 'detail', title: '明细' },
  ],
} as any

describe('ChartWidget', () => {
  it('hides the component title when the title bar is hidden', () => {
    const wrapper = mount(ChartWidget, {
      props: { component: { ...component, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })

    expect(wrapper.find('.chart-title').exists()).toBe(false)
  })

  it('removes the empty title bar when title and time filter are both hidden', () => {
    const wrapper = mount(ChartWidget, {
      props: { component: { ...component, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })

    expect(wrapper.find('.chart-header').exists()).toBe(false)
  })

  it('applies the configured title bar style', () => {
    const wrapper = mount(ChartWidget, {
      props: { component: { ...component, titleBarStyle: 'accent' } as any },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })

    expect(wrapper.find('.chart-header').classes()).toContain('title-bar-accent')
  })

  it('exposes widget tabs as keyboard-operable tabs', async () => {
    const wrapper = mount(ChartWidget, {
      props: { component },
      global: {
        plugins: [i18n],
        stubs: { 'el-date-picker': true },
      },
    })

    const tabs = wrapper.findAll('[role="tab"]')
    expect(wrapper.find('[role="tablist"]').attributes('aria-label')).toBe('图表分页')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].attributes('tabindex')).toBe('0')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(tabs[1].attributes('tabindex')).toBe('-1')

    await tabs[0].trigger('keydown', { key: 'ArrowRight' })
    expect(tabs[1].attributes('aria-selected')).toBe('true')
    expect(tabs[1].attributes('tabindex')).toBe('0')
  })

  it('resets the active tab when tab definitions are replaced', async () => {
    const wrapper = mount(ChartWidget, {
      props: { component },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })

    await wrapper.findAll('[role="tab"]')[0].trigger('keydown', { key: 'ArrowRight' })
    await wrapper.setProps({
      component: {
        ...component,
        tabs: [
          { id: 'summary', title: '汇总' },
          { id: 'trend', title: '趋势' },
        ],
      },
    })

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs[0].attributes('data-tab-id')).toBe('summary')
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].attributes('tabindex')).toBe('0')
  })

  it('prevents page scrolling when Space activates a tab', () => {
    const wrapper = mount(ChartWidget, {
      props: { component },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })
    const event = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true })
    const dispatched = wrapper.find('[role="tab"]').element.dispatchEvent(event)
    expect(dispatched).toBe(false)
  })
})
