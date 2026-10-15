import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import ComponentPalette from '../ComponentPalette.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      insight: {
        paletteTitle: '组件库',
        component: { table: '数据表格' },
      },
    },
  },
  missingWarn: false,
  fallbackWarn: false,
})

describe('ComponentPalette', () => {
  it('supports keyboard activation for adding a component', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const table = wrapper.findAll('.palette-item').find((item) => item.text() === '数据表格')

    expect(table).toBeDefined()
    await table!.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('add-component')).toEqual([[{ type: 'table' }]])
  })

  it('supports Space activation with the existing component payload', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const table = wrapper.findAll('.palette-item').find((item) => item.text() === '数据表格')
    const event = { key: ' ', preventDefault: vi.fn() }

    await table!.trigger('keydown', event)

    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(wrapper.emitted('add-component')).toEqual([[{ type: 'table' }]])
  })

  it('keeps the chart type in keyboard-add payloads', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const lineChart = wrapper.findAll('.palette-group-body')[1].find('.palette-item')

    await lineChart.trigger('keydown', { key: 'Enter', preventDefault: vi.fn() })

    expect(wrapper.emitted('add-component')).toEqual([[{ type: 'chart', chartType: 'line' }]])
  })

  it('keeps the original drag payload and copy effect', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const lineChart = wrapper.findAll('.palette-group-body')[1].find('.palette-item')
    const setData = vi.fn()
    const dataTransfer = { effectAllowed: 'all', setData }

    await lineChart.trigger('dragstart', { dataTransfer })

    expect(dataTransfer.effectAllowed).toBe('copy')
    expect(setData).toHaveBeenCalledOnce()
    expect(setData).toHaveBeenCalledWith('application/json', expect.any(String))
    const payload = JSON.parse(setData.mock.calls[0][1])
    expect(payload).toMatchObject({ type: 'chart', chartType: 'line', labelKey: 'insight.component.line' })
    expect(payload.icon).toContain('<svg')
    expect(payload).not.toHaveProperty('visualStyle')
  })

  it('keeps the component inventory and group order unchanged', () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const groups = wrapper.findAll('.palette-group-body')

    expect(groups.map((group) => group.findAll('.palette-item').length)).toEqual([6, 5, 17])
    expect(wrapper.findAll('.palette-item')).toHaveLength(28)
    expect(groups[0].findAll('.palette-item').map((item) => item.attributes('aria-label'))).toEqual([
      'insight.component.kpi',
      '数据表格',
      'insight.component.filter',
      'insight.component.timeFilter',
      'insight.component.aiAnalysis',
      'insight.component.combination',
    ])
    expect(groups[1].findAll('.palette-item').map((item) => item.attributes('aria-label'))).toEqual([
      'insight.component.line',
      'insight.component.bar',
      'insight.component.pie',
      'insight.component.area',
      'insight.component.scatter',
    ])
  })

  it('toggles only the selected group', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const [basic, commonChart] = wrapper.findAll('.palette-group-header')

    await basic.trigger('click')

    expect(basic.attributes('aria-expanded')).toBe('false')
    expect(wrapper.findAll('.palette-group-body')[0].element.getAttribute('style')).toContain('display: none')
    expect(commonChart.attributes('aria-expanded')).toBe('true')
    expect(wrapper.findAll('.palette-group-body')[1].element.getAttribute('style')).toBeNull()
  })
})
