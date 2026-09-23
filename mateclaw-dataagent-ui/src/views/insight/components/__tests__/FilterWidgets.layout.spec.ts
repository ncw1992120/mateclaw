import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import FilterSelectWidget from '../FilterSelectWidget.vue'
import TimeFilterWidget from '../TimeFilterWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      insight: {
        filterPlaceholder: '请选择',
        timeRange: {
          startPlaceholder: '开始日期',
          endPlaceholder: '结束日期',
          today: '今天',
          '7d': '近7天',
          '30d': '近30天',
          '90d': '近90天',
        },
      },
    },
  },
  missingWarn: false,
  fallbackWarn: false,
})

const filterComponent = {
  id: 'filter-1',
  type: 'filter',
  title: '转化指标名称',
  config: {
    field: 'metric_name',
    optionSource: 'static',
    staticOptions: [{ label: '指标 A', value: 'a' }],
  },
} as any

const timeComponent = {
  id: 'time-filter-1',
  type: 'timeFilter',
  title: '指标日期',
  config: { field: 'metric_time' },
} as any

const global = {
  plugins: [i18n],
  stubs: {
    DashboardComponentIcon: { template: '<span class="stub-icon" />' },
    'el-select': { template: '<div class="stub-select"><slot /></div>' },
    'el-option': { template: '<div />' },
    'el-date-picker': { template: '<div class="stub-date-picker" />' },
  },
}

describe('筛选类组件的横向工具栏布局', () => {
  it('筛选器使用共享控制栏结构，并让标签和输入区保持同一行语义', () => {
    const wrapper = mount(FilterSelectWidget, { props: { component: filterComponent, showTitle: true }, global })

    expect(wrapper.find('.filter-control-widget').exists()).toBe(true)
    expect(wrapper.text()).toContain('转化指标名称')
    expect(wrapper.find('.filter-control-content').exists()).toBe(true)
    expect(wrapper.find('.filter-control-widget').classes()).toContain('filter-control-inline')
  })

  it('时间筛选器复用同一套横向控制栏结构，并保留可访问标签', () => {
    const wrapper = mount(TimeFilterWidget, { props: { component: timeComponent, showTitle: true }, global })

    expect(wrapper.find('.filter-control-widget').exists()).toBe(true)
    expect(wrapper.text()).toContain('指标日期')
    expect(wrapper.find('.filter-control-content').exists()).toBe(true)
    expect(wrapper.find('.filter-control-widget').classes()).toContain('filter-control-inline')
    expect(wrapper.find('.stub-date-picker').exists()).toBe(true)
  })
})
