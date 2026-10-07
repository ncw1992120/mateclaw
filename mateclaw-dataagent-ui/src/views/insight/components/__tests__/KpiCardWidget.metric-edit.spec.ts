import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import type { InsightComponent, InsightComponentData, KpiMetricConfig } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { componentPreviewData } from '@/utils/component-preview-data'
import KpiCardWidget from '../KpiCardWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: {
    kpiMetricStyle: '配置字段样式',
    kpiMetricStyleFor: '配置指标“{name}”样式',
  } } },
  missingWarn: false,
  fallbackWarn: false,
})

function metric(fieldKey: string, x: number, y: number): KpiMetricConfig {
  return {
    fieldKey,
    displayName: fieldKey.toUpperCase(),
    visible: true,
    x,
    y,
    w: 160,
    h: 80,
    styles: defaultMetricStyles(),
    visual: { colorMode: 'theme' },
  }
}

function mountWidget(metrics: KpiMetricConfig[], componentData?: InsightComponentData) {
  const component = {
    id: 'kpi-card',
    type: 'kpi',
    title: '指标卡片',
    position: { x: 0, y: 0, w: 6, h: 4 },
    kpiMetrics: metrics,
  } as unknown as InsightComponent
  return { component, wrapper: mount(KpiCardWidget, {
    props: { component, componentData, editable: true },
    global: { plugins: [i18n], stubs: { 'el-icon': true, 'el-date-picker': true } },
  }) }
}

describe('KpiCardWidget · 画布指标编辑', () => {
  it('查询结果含两个已配置指标时，画布直接渲染两项展示名和值', () => {
    const metrics = [
      { ...metric('digo_distr_count_1', 0, 0), displayName: '下发次数' },
      { ...metric('digo_distr_user_cnt_a', 160, 0), displayName: '下发人数' },
    ]
    const initial = mountWidget(metrics)
    const { component } = initial
    const componentData = componentPreviewData(component, [{ digo_distr_count_1: 1470, digo_distr_user_cnt_a: 1215 }], [
      { name: 'digo_distr_count_1', title: '下发次数' },
      { name: 'digo_distr_user_cnt_a', title: '下发人数' },
    ])
    initial.wrapper.unmount()
    const { wrapper } = mountWidget(metrics, componentData)

    expect(wrapper.findAll('.kpi-metric')).toHaveLength(2)
    expect(wrapper.text()).toContain('下发次数')
    expect(wrapper.text()).toContain('1470')
    expect(wrapper.text()).toContain('下发人数')
    expect(wrapper.text()).toContain('1215')
    wrapper.unmount()
  })

  it('查询结果缺少已配置指标时，不将第一项指标值复用到缺失指标', () => {
    const metrics = [metric('old-field', 0, 0), metric('field-a', 160, 0), metric('field-b', 320, 0), metric('field-c', 480, 0)]
    const componentData = {
      componentId: 'kpi-card',
      renderType: 'kpi',
      kpi: { fieldKey: 'field-a', name: 'field-a', value: '100' },
      kpiList: [
        { fieldKey: 'field-a', name: 'field-a', value: '100' },
        { fieldKey: 'field-b', name: 'field-b', value: '200' },
      ],
    } as unknown as InsightComponentData
    const { wrapper } = mountWidget(metrics, componentData)

    expect(wrapper.get('[data-metric="old-field"] .kpi-metric-value').text()).toBe('--')
    expect(wrapper.get('[data-metric="field-a"] .kpi-metric-value').text()).toBe('100')
    expect(wrapper.get('[data-metric="field-b"] .kpi-metric-value').text()).toBe('200')
    expect(wrapper.get('[data-metric="field-c"] .kpi-metric-value').text()).toBe('--')
    wrapper.unmount()
  })

  it('编辑态可直接拖动指标并保存最终画布位置', () => {
    const source = metric('revenue', 10, 20)
    const { component, wrapper } = mountWidget([source])
    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperty(group, 'getBoundingClientRect', { value: () => ({ width: 500, height: 300 }) })
    const item = wrapper.get('[data-metric="revenue"]').element as HTMLElement
    Object.defineProperty(item, 'offsetWidth', { value: 160 })
    Object.defineProperty(item, 'offsetHeight', { value: 80 })

    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 10, clientY: 20 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 45, clientY: 58 }))
    window.dispatchEvent(new MouseEvent('mouseup'))

    expect(component.kpiMetrics?.[0]).toMatchObject({ x: 45, y: 58 })
    wrapper.unmount()
  })

  it('画布缩放时按逻辑像素提交拖动后的指标位置', async () => {
    const source = metric('revenue', 10, 20)
    const { component, wrapper } = mountWidget([source])
    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperties(group, {
      offsetWidth: { value: 500 },
      offsetHeight: { value: 300 },
      getBoundingClientRect: { value: () => ({ width: 200, height: 120 }) },
    })
    const item = wrapper.get('[data-metric="revenue"]').element as HTMLElement
    Object.defineProperties(item, {
      offsetWidth: { value: 160 },
      offsetHeight: { value: 80 },
    })

    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 10, clientY: 20 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 24, clientY: 32 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()

    expect(component.kpiMetrics?.[0]).toMatchObject({ x: 45, y: 50 })
    expect(wrapper.get('[data-metric="revenue"]').attributes('style')).toContain('left: 45px; top: 50px;')
    wrapper.unmount()
  })

  it('画布缩放时按逻辑像素提交缩放后的指标尺寸', async () => {
    const source = metric('revenue', 10, 20)
    const { component, wrapper } = mountWidget([source])
    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperties(group, {
      offsetWidth: { value: 500 },
      offsetHeight: { value: 300 },
      getBoundingClientRect: { value: () => ({ width: 200, height: 120 }) },
    })
    const item = wrapper.get('[data-metric="revenue"]').element as HTMLElement
    Object.defineProperties(item, {
      offsetWidth: { value: 160 },
      offsetHeight: { value: 80 },
    })

    await item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.get('.kpi-rs.e').trigger('mousedown', { clientX: 100, clientY: 100, button: 0 })
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 120, clientY: 100 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()

    expect(component.kpiMetrics?.[0]).toMatchObject({ w: 210, h: 80 })
    expect(wrapper.get('[data-metric="revenue"]').attributes('style')).toContain('width: 210px; height: 80px;')
    wrapper.unmount()
  })

  it('指标右上角提供可访问的铅笔样式编辑入口', async () => {
    const { wrapper } = mountWidget([metric('revenue', 0, 0)])
    const editButton = wrapper.get('[data-testid="kpi-metric-style"]')

    expect(editButton.attributes('aria-label')).toBe('配置指标“REVENUE”样式')
    await editButton.trigger('click')
    expect(wrapper.emitted('open-metric-style')?.[0]?.[0]).toMatchObject({
      componentId: 'kpi-card',
      fieldKey: 'revenue',
      field: 'value',
    })
  })
})
