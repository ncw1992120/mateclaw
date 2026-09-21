import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DashboardCanvas from '../DashboardCanvas.vue'
import { DASHBOARD_CANVAS_MIN_HEIGHT, DASHBOARD_CANVAS_MIN_WIDTH } from '../dashboardCanvasConstants'

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

  it('keeps an expanded workspace so the canvas can scroll in both directions', () => {
    expect(DASHBOARD_CANVAS_MIN_WIDTH).toBeGreaterThan(1024)
    expect(DASHBOARD_CANVAS_MIN_HEIGHT).toBeGreaterThan(768)

    const wrapper = mount(DashboardCanvas, { props: { components: [], editable: true }, global: { stubs, plugins: [i18n] } })
    const canvas = wrapper.get('.dashboard-canvas')
    expect(canvas.attributes('data-canvas-workspace')).toBe('expanded')
    expect(canvas.attributes('style')).toContain(`min-width: ${DASHBOARD_CANVAS_MIN_WIDTH}px`)
    expect(canvas.attributes('style')).toContain(`min-height: ${DASHBOARD_CANVAS_MIN_HEIGHT}px`)
  })

  it('emits copy and paste commands from canvas keyboard shortcuts', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true, selectedId: 'kpi-1' },
      global: { stubs, plugins: [i18n] },
    })
    const card = wrapper.get('[data-component-id="kpi-1"]')

    await card.trigger('keydown', { key: 'c', ctrlKey: true })
    await card.trigger('keydown', { key: 'v', metaKey: true })

    expect(wrapper.emitted('copy-component')).toEqual([['kpi-1']])
    expect(wrapper.emitted('paste-component')).toEqual([[]])
  })

  it('opens the component context menu at the browser pointer position', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    await wrapper.get('[data-component-id="kpi-1"]').trigger('contextmenu', { clientX: 120, clientY: 240 })

    expect(wrapper.emitted('context-menu')).toEqual([[{ componentId: 'kpi-1', x: 120, y: 240 }]])
  })
})
