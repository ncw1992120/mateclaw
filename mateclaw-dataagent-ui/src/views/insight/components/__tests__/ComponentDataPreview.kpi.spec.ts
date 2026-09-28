import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import { componentPreviewData } from '@/utils/component-preview-data'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import ComponentDataPreview from '../ComponentDataPreview.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { kpiNoData: '暂无数据' } } },
  missingWarn: false,
  fallbackWarn: false,
})

describe('ComponentDataPreview · KPI 输出字段投影', () => {
  it('只渲染 Python 实际输出的 KPI 字段，不用旧配置重复回退首个值', () => {
    const component = {
      id: 'strategy-contribution',
      type: 'kpi',
      title: '策略贡献',
      position: { x: 0, y: 0, w: 6, h: 4 },
      kpiMetrics: buildKpiMetrics([
        'metric_a', 'metric_b', 'metric_c', 'metric_d', 'metric_e', 'metric_f',
        'metric_g', 'metric_h', 'metric_i', 'metric_j', 'metric_k', 'metric_l', 'metric_m',
      ].map((name) => ({ name, displayName: name }))),
    } as unknown as InsightComponent
    const componentData = componentPreviewData(component, [{ 转化规模: 27948000, 转化人数: 2964 }], [
      { name: '转化规模', title: '转化规模' },
      { name: '转化人数', title: '转化人数' },
    ])
    const wrapper = mount(ComponentDataPreview, {
      props: { component, componentData },
      global: {
        plugins: [i18n],
        stubs: { 'el-icon': true, 'el-date-picker': true, 'el-empty': true },
      },
    })

    expect(wrapper.findAll('.kpi-metric')).toHaveLength(2)
    expect(wrapper.findAll('.kpi-metric-name').map((node) => node.text())).toEqual(['转化规模', '转化人数'])
    expect(wrapper.findAll('.kpi-metric-value').map((node) => node.text())).toEqual(['27948000', '2964'])
    expect(component.kpiMetrics).toHaveLength(13)
    wrapper.unmount()
  })

  it('结果为空时不显示配置中残留的 KPI 指标', () => {
    const component = {
      id: 'strategy-contribution',
      type: 'kpi',
      title: '策略贡献',
      position: { x: 0, y: 0, w: 6, h: 4 },
      kpiMetrics: buildKpiMetrics([{ name: 'old_metric', displayName: '旧指标' }]),
    } as unknown as InsightComponent
    const componentData = componentPreviewData(component, [], [])
    const wrapper = mount(ComponentDataPreview, {
      props: { component, componentData },
      global: {
        plugins: [i18n],
        stubs: { 'el-icon': true, 'el-date-picker': true, 'el-empty': true },
      },
    })

    expect(wrapper.findAll('.kpi-metric')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无数据')
    wrapper.unmount()
  })
})
