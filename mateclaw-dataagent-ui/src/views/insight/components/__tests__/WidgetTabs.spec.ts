import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DataTableWidget from '../DataTableWidget.vue'
import KpiCardWidget from '../KpiCardWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
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
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<span />' },
  'el-pagination': { template: '<div />' },
  'el-tooltip': { template: '<slot />' },
  'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
  'el-date-picker': true,
  'el-icon': { template: '<span><slot /></span>' },
}

describe('widget tabs', () => {
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
})
