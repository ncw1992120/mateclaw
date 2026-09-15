import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import PropertyPanel from '../PropertyPanel.vue'

const listSyncedMetricsMock = vi.hoisted(() => vi.fn())
const listMetricsDimensionDetailsMock = vi.hoisted(() => vi.fn())
const previewComponentMock = vi.hoisted(() => vi.fn())

vi.mock('@/stores/useDatasourceStore', () => ({
  useDatasourceStore: () => ({
    datasources: [{ id: '7', name: 'Sales database' }],
    fetchDatasources: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/api/datasource', () => ({
  listSyncedMetrics: listSyncedMetricsMock,
  listMetricsDimensionDetails: listMetricsDimensionDetailsMock,
  listSyncedDimensions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/insight-dashboard', () => ({
  previewComponent: previewComponentMock,
}))

const stubs = {
  'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
  'el-input': { template: '<input v-bind="$attrs" />' },
  'el-input-number': { template: '<input v-bind="$attrs" />' },
  'el-select': { template: '<select v-bind="$attrs"><slot /></select>' },
  'el-option': {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  'el-switch': { template: '<input type="checkbox" v-bind="$attrs" />' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button><slot /></button>' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label><slot /></label>' },
}

const component = {
  id: 'chart-1',
  type: 'chart',
  title: 'Sales by region',
  chartType: 'line',
  position: { x: 0, y: 0, w: 6, h: 4 },
  dataSource: {
    datasourceId: '7',
    metrics: ['revenue'],
    dimensions: ['region'],
    filters: [],
    limit: 100,
  },
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

describe('PropertyPanel', () => {
  it('renders only descriptor fields returned for the selected datasource', async () => {
    listSyncedMetricsMock.mockResolvedValue([{ metricName: 'revenue', metricDisplayName: 'Revenue' }])
    listMetricsDimensionDetailsMock.mockResolvedValue([{ dimName: 'region', dimDisplayName: 'Region' }])

    const wrapper = mount(PropertyPanel, {
      props: { component, allComponents: [] },
      global: { stubs, plugins: [i18n] },
    })
    await nextTick()
    await nextTick()

    expect(listSyncedMetricsMock).toHaveBeenCalledWith('7', 1, 50)
    expect(listMetricsDimensionDetailsMock).toHaveBeenCalledWith('7', ['revenue'], undefined)
    expect(wrapper.text()).toContain('Revenue')
    expect(wrapper.text()).toContain('Region')
    expect(wrapper.text()).not.toContain('password')
  })

  it('uses the component preview API and emits preview data without creating a script execution', async () => {
    previewComponentMock.mockResolvedValue({
      componentId: 'chart-1',
      renderType: 'echarts',
      option: { series: [] },
    })
    const wrapper = mount(PropertyPanel, {
      props: { component, allComponents: [] },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.find('.preview-group button').trigger('click')
    await nextTick()

    expect(previewComponentMock).toHaveBeenCalledWith(expect.objectContaining({
      id: 'chart-1',
      dataSource: expect.objectContaining({ datasourceId: '7' }),
    }))
    expect(wrapper.emitted('preview')?.[0]).toEqual([expect.objectContaining({
      componentId: 'chart-1',
      renderType: 'echarts',
    })])
  })

  it('prevents page scrolling when Space activates an editable tab', async () => {
    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          ...component,
          tabs: [
            { id: 'overview', title: '概览', dataSource: component.dataSource },
            { id: 'detail', title: '明细', dataSource: component.dataSource },
          ],
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })
    await nextTick()
    const tab = wrapper.find('.tab-item-row')
    expect(tab.exists()).toBe(true)
    const event = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true })
    expect(tab.element.dispatchEvent(event)).toBe(false)
  })
})
