import { mount } from '@vue/test-utils'
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
