import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DashboardCanvas from '../DashboardCanvas.vue'

const stubs = {
  GridLayout: { template: '<div><slot /></div>' },
  GridItem: { template: '<div><slot /></div>' },
  KpiCardWidget: { template: '<div />' },
  ChartWidget: { template: '<div />' },
  DataTableWidget: { template: '<div />' },
  FilterSelectWidget: { template: '<div />' },
  TimeFilterWidget: { template: '<div />' },
  AiAnalysisWidget: { template: '<div />' },
  CombinationCardWidget: { template: '<div />' },
}
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': { insight: { canvasEmpty: '暂无组件' } } }, missingWarn: false, fallbackWarn: false })

const component = {
  id: 'kpi-1',
  type: 'kpi' as const,
  title: '订单数',
  position: { x: 0, y: 0, w: 4, h: 3 },
}

describe('DashboardCanvas keyboard interaction', () => {
  it('moves and resizes the selected component with keyboard arrows', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true, selectedId: 'kpi-1' },
      global: { stubs, plugins: [i18n] },
    })
    const card = wrapper.get('[data-component-id="kpi-1"]')
    await card.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 1, y: 0, w: 4, h: 3 }])
    await card.trigger('keydown', { key: 'ArrowDown', shiftKey: true })
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 1, y: 0, w: 4, h: 4 }])
  })

  it('offers one add-component action in an empty canvas', () => {
    const wrapper = mount(DashboardCanvas, { props: { components: [], editable: true }, global: { stubs, plugins: [i18n] } })
    expect(wrapper.get('[aria-label="从组件库添加组件"]').exists()).toBe(true)
  })
})
