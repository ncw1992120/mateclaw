import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import ChartWidget from '../ChartWidget.vue'

const renderEChartsMock = vi.hoisted(() => vi.fn())

vi.mock('@/composables/useEChartsRenderer', () => ({
  useEChartsRenderer: () => ({
    renderECharts: renderEChartsMock,
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

  it('passes the chart option through to the renderer unchanged (series data, encode and explicit colors intact)', async () => {
    const option = {
      color: ['#5470c6', '#91cc75'],
      xAxis: { type: 'category', data: ['一月', '二月'] },
      yAxis: { type: 'value' },
      dataset: { source: [['month', 'sales'], ['一月', 12], ['二月', 20]] },
      series: [
        {
          type: 'line',
          encode: { x: 0, y: 1 },
          data: [12, 20],
          itemStyle: { color: '#ff0000' },
          lineStyle: { width: 2 },
        },
      ],
      tooltip: { trigger: 'axis', formatter: '{b}: {c}' },
    }
    const wrapper = mount(ChartWidget, {
      props: {
        component: { ...component, titleBarStyle: 'hidden', tabs: [] } as any,
        componentData: { option, fieldLabels: {} } as any,
      },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })
    await wrapper.vm.$nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(renderEChartsMock).toHaveBeenCalled()
    const passedOption = renderEChartsMock.mock.calls.at(-1)![1]
    // 视觉改造不得改变数据：透传的 option 与输入深度一致（含 series.data、encode、显式色）。
    // 注：props 经 Vue 响应式代理，引用不相同但内容必须逐字段一致。
    expect(passedOption).toStrictEqual(option)
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

  it('offers a per-tab icon style action only in edit mode and emits the selected tab target', async () => {
    const wrapper = mount(ChartWidget, {
      props: { component, editable: true },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })

    const trigger = wrapper.get('[aria-label="编辑页签图标 概览"]')
    await trigger.trigger('click')
    expect(wrapper.emitted('edit-tab-title-icon-style')?.[0]?.[0]).toMatchObject({
      componentId: 'chart-1',
      tabId: 'overview',
      tabKind: 'component',
    })

    const viewWrapper = mount(ChartWidget, {
      props: { component, editable: false },
      global: { plugins: [i18n], stubs: { 'el-date-picker': true } },
    })
    expect(viewWrapper.find('[aria-label="编辑页签图标 概览"]').exists()).toBe(false)
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
