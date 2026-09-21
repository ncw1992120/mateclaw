import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import PropertyPanel from '../PropertyPanel.vue'

const listSyncedMetricsMock = vi.hoisted(() => vi.fn())
const listMetricsDimensionDetailsMock = vi.hoisted(() => vi.fn())
const previewComponentMock = vi.hoisted(() => vi.fn())
const datasourceListMock = vi.hoisted(() => [{ id: '7', name: 'Sales database', sourceType: 'aloudata' }])

vi.mock('@/stores/useDatasourceStore', () => ({
  useDatasourceStore: () => ({
    datasources: datasourceListMock,
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
  'el-option-group': { props: ['label'], template: '<optgroup :label="label"><slot /></optgroup>' },
  'el-option': {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  'el-switch': {
    props: ['modelValue'],
    template: '<button class="el-switch-stub" @click="$emit(\'update:modelValue\', !modelValue); $emit(\'change\', !modelValue)">toggle</button>',
  },
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
  it('shows JDBC SQL controls instead of Aloudata metrics and dimensions', async () => {
    datasourceListMock.splice(0, datasourceListMock.length,
      { id: 'jdbc-1', name: '业务 MySQL', sourceType: 'mysql' } as any,
    )
    const wrapper = mount(PropertyPanel, {
      props: { component: { ...component, datasourceId: undefined, dataSource: { datasourceId: 'jdbc-1', metrics: [], dimensions: [], filters: [], limit: 100 } }, allComponents: [] },
      global: { stubs, plugins: [i18n] },
    })
    await nextTick()
    expect(wrapper.find('.jdbc-query-config').exists()).toBe(true)
    expect(wrapper.find('.jdbc-query-config').text()).toContain('SQL')
    expect(wrapper.find('[aria-label="insight.property.metrics"]').exists()).toBe(false)
    datasourceListMock.splice(0, datasourceListMock.length, { id: '7', name: 'Sales database', sourceType: 'aloudata' } as any)
  })

  it('shows a unified dataset hint for API and file sources', async () => {
    datasourceListMock.splice(0, datasourceListMock.length,
      { id: 'api-1', name: '订单接口', sourceType: 'HTTP_API' } as any,
    )
    const wrapper = mount(PropertyPanel, {
      props: { component: { ...component, dataSource: { datasourceId: 'api-1', metrics: [], dimensions: [], filters: [], limit: 100 } }, allComponents: [] },
      global: { stubs, plugins: [i18n] },
    })
    await nextTick()
    expect(wrapper.find('.source-binding-hint').text()).toContain('接口')
    expect(wrapper.find('.jdbc-query-config').exists()).toBe(false)
    expect(wrapper.find('[aria-label="insight.property.metrics"]').exists()).toBe(false)
    datasourceListMock.splice(0, datasourceListMock.length, { id: '7', name: 'Sales database', sourceType: 'aloudata' } as any)
  })

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

  it('does not carry the outer combination children into a newly selected inner combination', async () => {
    const outerChildren = [{
      id: 'outer-child',
      type: 'kpi' as const,
      title: '外层指标',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }]
    const outer = {
      id: 'outer-combination',
      type: 'combination' as const,
      title: '外层组合',
      children: outerChildren,
      containerConfig: {
        title: '外层组合',
        showTitle: true,
        background: '#fff',
        radius: 12,
        padding: 16,
        layoutMode: 'free' as const,
        tabs: [],
        style: { border: { enabled: false, color: 'transparent' } },
      },
      position: { x: 0, y: 0, w: 12, h: 8 },
    }
    const inner = {
      id: 'inner-combination',
      type: 'combination' as const,
      title: '内层组合',
      containerConfig: { ...outer.containerConfig, title: '内层组合' },
      position: { x: 0, y: 0, w: 6, h: 4 },
    }
    const wrapper = mount(PropertyPanel, {
      props: { component: outer, allComponents: [] },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.setProps({ component: inner })
    await nextTick()
    await wrapper.find('.el-switch-stub').trigger('click')

    const emitted = wrapper.emitted('change') ?? []
    expect(emitted.at(-1)?.[0]).toMatchObject({
      id: 'inner-combination',
      containerConfig: { showTitle: false },
    })
    expect((emitted.at(-1)?.[0] as any).children).toBeUndefined()
  })
})
