import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DataTableWidget from '../DataTableWidget.vue'
import KpiCardWidget from '../KpiCardWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { kpiTabsLabel: '指标卡分页' } } },
  missingWarn: false,
  fallbackWarn: false,
})

const tabs = [
  { id: 'overview', title: '概览' },
  { id: 'detail', title: '明细' },
]

const component = {
  id: 'widget-1',
  type: 'table',
  title: '销售明细',
  enableTimeFilter: false,
  tabs,
} as any

const componentData = {
  tabs: {
    overview: { table: { columns: ['订单'], rows: [['A']] }, kpi: { value: 10 } },
    detail: { table: { columns: ['订单'], rows: [['B']] }, kpi: { value: 20 } },
  },
} as any

const stubs = {
  'el-table': { template: '<div data-show-header="true"><slot /></div>' },
  'el-table-column': { template: '<span />' },
  'el-pagination': { template: '<div />' },
  'el-tooltip': { template: '<slot />' },
  'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
  'el-date-picker': true,
  'el-icon': { template: '<span><slot /></span>' },
}

describe('widget tabs', () => {
  it('always renders the table header', () => {
    const wrapper = mount(DataTableWidget, {
      props: {
        component: { ...component, tabs: [] },
        titleBarStyle: 'standard',
        componentData: { table: { columns: ['订单'], rows: [['A']] } },
      },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.table-title').exists()).toBe(true)
    expect(wrapper.find('[data-show-header="true"]').exists()).toBe(true)
  })

  it.each([
    ['table', DataTableWidget, '数据表分页'],
    ['kpi', KpiCardWidget, '指标卡分页'],
  ])('%s exposes keyboard-operable tabs', async (_name, Component, label) => {
    const wrapper = mount(Component, {
      props: { component, componentData },
      global: { plugins: [i18n], stubs },
    })

    const tabsInDom = wrapper.findAll('[role="tab"]')
    expect(wrapper.find('[role="tablist"]').attributes('aria-label')).toBe(label)
    expect(tabsInDom).toHaveLength(2)
    expect(tabsInDom[0].attributes('aria-selected')).toBe('true')
    expect(tabsInDom[0].attributes('tabindex')).toBe('0')

    await tabsInDom[0].trigger('keydown', { key: 'ArrowRight' })
    expect(tabsInDom[1].attributes('aria-selected')).toBe('true')
    expect(tabsInDom[1].attributes('tabindex')).toBe('0')
  })

  it.each([
    ['table', DataTableWidget],
    ['kpi', KpiCardWidget],
  ])('%s resets the active tab when its tab definitions are replaced', async (_name, Component) => {
    const wrapper = mount(Component, {
      props: { component, componentData },
      global: { plugins: [i18n], stubs },
    })

    const replacement = {
      ...component,
      tabs: [
        { id: 'summary', title: '汇总' },
        { id: 'trend', title: '趋势' },
      ],
    }
    await wrapper.setProps({ component: replacement })

    const replacementTabs = wrapper.findAll('[role="tab"]')
    expect(replacementTabs[0].attributes('data-tab-id')).toBe('summary')
    expect(replacementTabs[0].attributes('aria-selected')).toBe('true')
    expect(replacementTabs[0].attributes('tabindex')).toBe('0')
    expect(replacementTabs[1].attributes('aria-selected')).toBe('false')
  })

  it.each([
    ['table', DataTableWidget],
    ['kpi', KpiCardWidget],
  ])('%s prevents page scrolling when Space activates a tab', (_name, Component) => {
    const wrapper = mount(Component, {
      props: { component, componentData },
      global: { plugins: [i18n], stubs },
    })
    const event = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true })
    const dispatched = wrapper.find('[role="tab"]').element.dispatchEvent(event)
    expect(dispatched).toBe(false)
  })
})
