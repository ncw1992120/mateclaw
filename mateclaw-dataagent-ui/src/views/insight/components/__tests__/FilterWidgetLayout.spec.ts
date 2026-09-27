import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import FilterSelectWidget from '../FilterSelectWidget.vue'
import TimeFilterWidget from '../TimeFilterWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const stubs = {
  'el-select': true,
  'el-option': true,
  'el-date-picker': true,
  DashboardComponentIcon: true,
}

const filterComponent = {
  id: 'filter-1',
  type: 'filter',
  title: '转化指标名称',
  titleBarStyle: 'standard',
  config: {
    field: 'metric_name',
    optionSource: 'static',
    staticOptions: [{ label: 'A', value: 'a' }],
  },
} as any

const timeComponent = {
  id: 'time-1',
  type: 'timeFilter',
  title: '指标日期',
  titleBarStyle: 'standard',
  config: { field: 'metric_time' },
} as any

// 注意：showTitle 是 Boolean prop，未传时 Vue 会 cast 成 false，
// 组件内部标签不渲染（画布/全局筛选栏目前都靠外层渲染标题）。
// 所以要验证「标签 + 控件」的同容器结构，必须显式传 showTitle: true。
describe('筛选器组件的左右内联布局契约', () => {
  it('标签与下拉框同为 .filter-body 的直接子项，且标签在前', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: { component: filterComponent, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    const body = wrapper.find('.filter-body')
    expect(body.exists()).toBe(true)

    const children = Array.from(body.element.children)
    expect(children.length).toBe(2)
    expect(children[0].classList.contains('filter-label')).toBe(true)
    expect(children[1].tagName.toLowerCase()).toBe('el-select-stub')
  })

  it('标题栏隐藏时只摘标签，容器与下拉框保留', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: { component: { ...filterComponent, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.filter-label').exists()).toBe(false)
    const children = Array.from(wrapper.find('.filter-body').element.children)
    expect(children.length).toBe(1)
    expect(children[0].tagName.toLowerCase()).toBe('el-select-stub')
  })

  it('标签与日期范围控件同为 .time-filter-body 的直接子项，且标签在前', () => {
    const wrapper = mount(TimeFilterWidget, {
      props: { component: timeComponent, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    const body = wrapper.find('.time-filter-body')
    expect(body.exists()).toBe(true)

    const children = Array.from(body.element.children)
    expect(children.length).toBe(2)
    expect(children[0].classList.contains('time-filter-label')).toBe(true)
    expect(children[1].tagName.toLowerCase()).toBe('el-date-picker-stub')
  })

  it('标题栏隐藏时只摘标签，容器与日期控件保留', () => {
    const wrapper = mount(TimeFilterWidget, {
      props: { component: { ...timeComponent, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.time-filter-label').exists()).toBe(false)
    const children = Array.from(wrapper.find('.time-filter-body').element.children)
    expect(children.length).toBe(1)
    expect(children[0].tagName.toLowerCase()).toBe('el-date-picker-stub')
  })
})
