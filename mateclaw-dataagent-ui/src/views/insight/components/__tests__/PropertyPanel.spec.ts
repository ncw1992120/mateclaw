import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import PropertyPanel from '../PropertyPanel.vue'

const listSyncedMetricsMock = vi.hoisted(() => vi.fn())
const listMetricsDimensionDetailsMock = vi.hoisted(() => vi.fn())
const listSyncedDimensionsMock = vi.hoisted(() => vi.fn().mockResolvedValue([]))
const listDimensionValuesMock = vi.hoisted(() => vi.fn())
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
  listSyncedDimensions: listSyncedDimensionsMock,
  listDimensionValues: listDimensionValuesMock,
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
  it('defaults new filter configuration to dynamic options before datasource setup', async () => {
    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          id: 'filter-new',
          type: 'filter',
          title: '区域',
          position: { x: 0, y: 0, w: 4, h: 2 },
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })

    await nextTick()

    expect((wrapper.vm as any).localFilterConfig.optionSource).toBe('dynamic')
    expect(wrapper.find('.static-options-list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('insight.property.filterOptionDynamicHint')
    const labels = wrapper.findAll('.form-label').map(label => label.text())
    expect(labels.indexOf('insight.property.filterOptions')).toBeLessThan(labels.indexOf('insight.property.datasource'))
  })

  it('shows static options only after switching to static source', async () => {
    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          id: 'filter-static',
          type: 'filter',
          title: '区域',
          position: { x: 0, y: 0, w: 4, h: 2 },
          config: { optionSource: 'static', staticOptions: [{ label: '华东', value: 'east' }] },
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })

    await nextTick()

    expect(wrapper.find('.static-options-list').exists()).toBe(true)
    expect(wrapper.findAll('.static-options-list input')).toHaveLength(2)
    expect(wrapper.text()).toContain('+ insight.property.addOption')
    expect(wrapper.text()).not.toContain('insight.property.filterOptionStaticHint')
    const labels = wrapper.findAll('.form-label').map(label => label.text())
    expect(labels.indexOf('insight.property.filterStaticOptions')).toBeLessThan(labels.indexOf('insight.property.filterSelectionMode'))
  })

  it('shows datasource and dimension controls only for dynamic filter options', async () => {
    datasourceListMock.splice(0, datasourceListMock.length,
      { id: 'aloudata-1', name: '指标平台', sourceType: 'aloudata' } as any,
      { id: 'jdbc-1', name: '业务 MySQL', sourceType: 'mysql' } as any,
      { id: 'api-1', name: '订单接口', sourceType: 'HTTP_API' } as any,
    )

    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          id: 'filter-1',
          type: 'filter',
          title: '区域',
          position: { x: 0, y: 0, w: 4, h: 2 },
          config: { optionSource: 'static', staticOptions: [] },
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })

    await nextTick()

    expect(wrapper.find('[aria-label="insight.property.datasource"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="insight.property.filterField"]').exists()).toBe(false)
    expect(wrapper.find('.static-options-list').exists()).toBe(true)

    await wrapper.setProps({
      component: {
        id: 'filter-1',
        type: 'filter',
        title: '区域',
        position: { x: 0, y: 0, w: 4, h: 2 },
        config: { optionSource: 'dynamic', datasourceId: 'aloudata-1', field: 'region' },
      },
    })
    await nextTick()

    expect(wrapper.find('[aria-label="insight.property.datasource"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="insight.property.filterField"]').exists()).toBe(true)
    expect(wrapper.find('.static-options-list').exists()).toBe(false)

    datasourceListMock.splice(0, datasourceListMock.length, { id: '7', name: 'Sales database', sourceType: 'aloudata' } as any)
  })

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

  it('loads dynamic default values from the configured dimension field', async () => {
    listDimensionValuesMock.mockResolvedValue(['华东', '华南'])
    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          id: 'filter-1',
          type: 'filter',
          title: '区域',
          position: { x: 0, y: 0, w: 4, h: 2 },
          config: {
            datasourceId: '7',
            field: 'region',
            optionSource: 'dynamic',
            defaultValue: null,
          },
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })

    await nextTick()
    await nextTick()

    expect(listDimensionValuesMock).toHaveBeenCalledWith('7', 'region', undefined, 200)
    expect(wrapper.findAll('option').map(option => option.text())).toContain('华东')
    expect(wrapper.findAll('option').map(option => option.text())).toContain('华南')
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
    await wrapper.findAll('.el-switch-stub')[1].trigger('click')

    const emitted = wrapper.emitted('change') ?? []
    expect(emitted.at(-1)?.[0]).toMatchObject({
      id: 'inner-combination',
      containerConfig: { showTitle: false },
    })
    expect((emitted.at(-1)?.[0] as any).children).toBeUndefined()
  })

  it('shows the full combination configuration for an inner combination without persisted container config', async () => {
    const wrapper = mount(PropertyPanel, {
      props: {
        component: {
          id: 'inner-combination',
          type: 'combination',
          title: '内层组合',
          position: { x: 0, y: 0, w: 6, h: 4 },
        },
        allComponents: [],
      },
      global: { stubs, plugins: [i18n] },
    })

    await nextTick()

    expect(wrapper.find('.el-switch-stub').exists()).toBe(true)
    expect(wrapper.find('.combination-tab-add').exists()).toBe(true)
  })
})
