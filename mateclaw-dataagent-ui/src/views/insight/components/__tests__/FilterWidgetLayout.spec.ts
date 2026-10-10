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
  it('标签与下拉框通过共享壳组件内联展示，且标签在前', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: { component: filterComponent, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    const body = wrapper.find('.filter-control-widget')
    expect(body.exists()).toBe(true)

    const children = Array.from(body.element.children)
    expect(children.length).toBe(2)
    expect(children[0].classList.contains('filter-control-label')).toBe(true)
    expect(children[1].classList.contains('filter-control-content')).toBe(true)
    expect(children[1].querySelector('el-select-stub')).not.toBeNull()
  })

  it('标题栏隐藏时只摘标签，共享壳容器与下拉框保留', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: { component: { ...filterComponent, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.filter-label').exists()).toBe(false)
    const body = wrapper.find('.filter-control-widget')
    expect(body.exists()).toBe(true)
    const children = Array.from(body.element.children)
    expect(children.length).toBe(1)
    expect(children[0].classList.contains('filter-control-content')).toBe(true)
    expect(children[0].querySelector('el-select-stub')).not.toBeNull()
  })

  it('标签与日期范围控件通过共享壳组件内联展示，且标签在前', () => {
    const wrapper = mount(TimeFilterWidget, {
      props: { component: timeComponent, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    const body = wrapper.find('.filter-control-widget')
    expect(body.exists()).toBe(true)

    const children = Array.from(body.element.children)
    expect(children.length).toBe(2)
    expect(children[0].classList.contains('filter-control-label')).toBe(true)
    expect(children[1].classList.contains('filter-control-content')).toBe(true)
    expect(children[1].querySelector('el-date-picker-stub')).not.toBeNull()
  })

  it('标题栏隐藏时只摘标签，共享壳容器与日期控件保留', () => {
    const wrapper = mount(TimeFilterWidget, {
      props: { component: { ...timeComponent, titleBarStyle: 'hidden' } },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.time-filter-label').exists()).toBe(false)
    const body = wrapper.find('.filter-control-widget')
    expect(body.exists()).toBe(true)
    const children = Array.from(body.element.children)
    expect(children.length).toBe(1)
    expect(children[0].classList.contains('filter-control-content')).toBe(true)
    expect(children[0].querySelector('el-date-picker-stub')).not.toBeNull()
  })

  it('编辑态强制 showTitle:true 且标题栏隐藏时，筛选器内联标签仍随标题栏一并摘掉', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: { component: { ...filterComponent, titleBarStyle: 'hidden' }, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    // 画布此前强制 showTitle:true，导致 titleBarStyle:'hidden' 失效、内联标签残留。
    expect(wrapper.find('.filter-control-label').exists()).toBe(false)
    const body = wrapper.find('.filter-control-widget')
    expect(body.classes()).toContain('filter-control-label-hidden')
    expect(body.classes()).toContain('title-bar-hidden')
    const children = Array.from(body.element.children)
    expect(children.length).toBe(1)
    expect(children[0].classList.contains('filter-control-content')).toBe(true)
    expect(children[0].querySelector('el-select-stub')).not.toBeNull()
  })

  it('编辑态强制 showTitle:true 且标题栏隐藏时，时间筛选器内联标签仍随标题栏一并摘掉', () => {
    const wrapper = mount(TimeFilterWidget, {
      props: { component: { ...timeComponent, titleBarStyle: 'hidden' }, showTitle: true },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.filter-control-label').exists()).toBe(false)
    const body = wrapper.find('.filter-control-widget')
    expect(body.classes()).toContain('filter-control-label-hidden')
    expect(body.classes()).toContain('title-bar-hidden')
    const children = Array.from(body.element.children)
    expect(children.length).toBe(1)
    expect(children[0].classList.contains('filter-control-content')).toBe(true)
    expect(children[0].querySelector('el-date-picker-stub')).not.toBeNull()
  })
})
