import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref, watch } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getAloudataMetricDirectory, getAloudataMetricDetail, getAloudataDimensionDetail, pageAloudataMetrics, pageAloudataDimensions, listAloudataCategoryCounts } = vi.hoisted(() => ({
  getAloudataMetricDirectory: vi.fn(async () => [{
    categoryId: 'metric-root',
    categoryName: '业务指标',
    metricList: [{ metricName: 'metric_a', metricDisplayName: '指标 A' }],
    subCategory: [],
  }]),
  getAloudataMetricDetail: vi.fn(async (_datasourceId: string, metricName: string) => ({
    metricName,
    metricDisplayName: metricName === 'metric_a' ? '指标 A' : '转化率',
    type: 'DERIVED',
    businessCaliber: '按用户统计转化率',
    unit: '%',
    owner: '数据团队',
    availableDimensions: metricName === 'metric_a' ? ['dim_a'] : ['dim_a', 'region'],
  })),
  getAloudataDimensionDetail: vi.fn(async (_datasourceId: string, dimName: string) => ({
    dimName,
    dimDisplayName: dimName === 'region' ? '所属大区' : '维度 A',
    originDataType: 'VARCHAR',
    dimDescription: '维度描述',
  })),
  pageAloudataMetrics: vi.fn(async () => ({
    records: [
      { metricName: 'metric_a', metricDisplayName: '指标 A', availableDimensions: ['dim_a'] },
      { metricName: 'technical_rate', metricDisplayName: '转化率', availableDimensions: ['dim_a', 'region'] },
    ],
    total: 2,
    current: 1,
    size: 20,
    pages: 1,
  })),
  pageAloudataDimensions: vi.fn(async () => ({
    records: [
      { dimName: 'dim_a', dimDisplayName: '维度 A' },
      { dimName: 'region', dimDisplayName: '所属大区' },
    ],
    total: 1,
    current: 1,
    size: 20,
    pages: 1,
  })),
  listAloudataCategoryCounts: vi.fn(async (_datasourceId: string, categoryType: string) => categoryType === 'CATEGORY_METRIC'
    ? [
      { categoryId: 'metric-root', categoryName: '业务指标', parentId: null },
      { categoryId: 'metric-child', categoryName: '转化指标', parentId: 'metric-root' },
    ]
    : [
      { categoryId: 'dim-root', categoryName: '业务维度', parentId: null },
      { categoryId: 'dim-child', categoryName: '客户维度', parentId: 'dim-root' },
    ]),
}))

vi.mock('@/api/semantic-model', () => ({
  getAloudataMetricDirectory,
  getAloudataMetricDetail,
  getAloudataDimensionDetail,
  pageAloudataMetrics,
  pageAloudataDimensions,
  listAloudataCategoryCounts,
}))

vi.mock('@/api/datasource', () => ({
  searchAnalysisViews: vi.fn(async () => []),
}))

import AloudataDialog from '../card-attribute/dataset/AloudataDialog.vue'
import { useInsight } from '../card-attribute/useInsight'

const { state } = useInsight()

const PopoverStub = defineComponent({
  name: 'ElPopover',
  props: { visible: Boolean },
  emits: ['update:visible', 'show'],
  setup(props, { slots, emit }) {
    const visible = ref(props.visible)
    watch(() => props.visible, (value) => { visible.value = value })
    const show = () => {
      visible.value = true
      emit('update:visible', true)
      emit('show')
    }
    return () => h('div', { class: 'picker-popover' }, [
      h('div', { class: 'picker-reference', onMouseover: () => {
        show()
      }, onClick: () => {
        if (visible.value) {
          visible.value = false
          emit('update:visible', false)
        } else show()
      } }, slots.reference?.()),
      visible.value ? h('div', { class: 'picker-popup' }, slots.default?.()) : null,
    ])
  },
})

const InputStub = defineComponent({
  name: 'ElInput',
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue', 'input'],
  setup(props, { emit }) {
    return () => h('input', {
      value: props.modelValue,
      onInput: (event: Event) => {
        const value = (event.target as HTMLInputElement).value
        emit('update:modelValue', value)
        emit('input', value)
      },
    })
  },
})

const CheckboxStub = defineComponent({
  name: 'ElCheckbox',
  props: { modelValue: Boolean, label: { type: String, required: true } },
  emits: ['change'],
  setup(props, { emit }) {
    return () => h('input', {
      class: 'picker-checkbox',
      type: 'checkbox',
      checked: props.modelValue,
      onChange: (event: Event) => {
        const checked = (event.target as HTMLInputElement).checked
        emit('change', checked)
      },
    })
  },
})

const TreeStub = defineComponent({
  name: 'ElTree',
  props: { data: { type: Array, default: () => [] } },
  emits: ['node-click'],
  setup(props, { emit }) {
    const renderNodes = (nodes: any[]) => nodes.flatMap((node) => [
      h('button', {
        type: 'button',
        class: 'stub-tree-node',
        'data-category-id': node.categoryId,
        onClick: () => emit('node-click', node),
      }, node.categoryName),
      ...(node.children?.length ? renderNodes(node.children) : []),
    ])
    return () => h('div', { class: 'stub-tree' }, renderNodes(props.data as any[]))
  },
})

const stubs = {
  'el-dialog': { template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>' },
  'el-alert': {
    props: { title: { type: String, default: '' } },
    template: '<div>{{ title }}<slot /></div>',
  },
  'el-popover': PopoverStub,
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button type="button"><slot /></button>' },
  'el-option': { template: '<div />' },
  'el-tag': {
    props: { closable: Boolean },
    emits: ['close'],
    template: '<span class="selected-tag"><slot /><button v-if="closable" class="selected-tag-remove" type="button" @click="$emit(\'close\', $event)">×</button></span>',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-input': InputStub,
  'el-checkbox': CheckboxStub,
  'el-table': { template: '<div />' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-tree': TreeStub,
  'el-tooltip': {
    props: { content: { type: String, default: '' } },
    template: '<span :data-tooltip="content"><slot /><slot name="content" /></span>',
  },
}

beforeEach(() => {
  state.ui.aloudata = {
    visible: false,
    mode: 'metric-dim',
    datasourceId: 'aloudata-1',
    metricView: '',
    metrics: ['metric_a'],
    dims: ['dim_a'],
  }
  getAloudataMetricDirectory.mockClear()
  getAloudataMetricDetail.mockClear()
  getAloudataDimensionDetail.mockClear()
  pageAloudataMetrics.mockClear()
  pageAloudataDimensions.mockClear()
  listAloudataCategoryCounts.mockClear()
})

describe('Aloudata 指标&维度选择', () => {
  it('uses existing live category and paginated list APIs instead of the unavailable directory route', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    expect(listAloudataCategoryCounts).toHaveBeenCalledWith('aloudata-1', 'CATEGORY_METRIC')
    expect(listAloudataCategoryCounts).toHaveBeenCalledWith('aloudata-1', 'CATEGORY_DIMENSION')
    expect(getAloudataMetricDirectory).not.toHaveBeenCalled()
  })

  it('shows separate expanding metric and dimension boxes with removable selected chips', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    const metricBox = wrapper.find('.metric-selection-box')
    const dimensionBox = wrapper.find('.dimension-selection-box')
    expect(metricBox.exists()).toBe(true)
    expect(dimensionBox.exists()).toBe(true)
    expect(metricBox.text()).toContain('metric_a')
    expect(dimensionBox.text()).toContain('dim_a')

    await metricBox.find('.selected-tag-remove').trigger('click')
    expect(state.ui.aloudata.metrics).toEqual([])
    expect(state.ui.aloudata.dims).toEqual(['dim_a'])
  })

  it('opens a floating tree picker and allows multiple metric selections without closing it', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    expect(wrapper.find('.metric-picker-popup').exists()).toBe(true)
    expect(wrapper.find('.metric-picker-popup').text()).toContain('业务指标')
    expect(wrapper.find('.metric-picker-popup').text()).toContain('technical_rate')

    const checkbox = wrapper.findAll('.metric-picker-popup .picker-checkbox')[1]
    await checkbox.setValue(true)
    expect(state.ui.aloudata.metrics).toContain('technical_rate')
    expect(wrapper.find('.metric-picker-popup').exists()).toBe(true)
  })

  it('filters paginated metrics when a category is selected in the tree', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    await wrapper.find('.metric-picker-popup [data-category-id="metric-child"]').trigger('click')
    await flushPromises()

    expect(pageAloudataMetrics).toHaveBeenLastCalledWith('aloudata-1', expect.objectContaining({ categoryId: 'metric-child' }))
  })

  it('searches metrics by display name or technical name through the live list API', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    const search = wrapper.find('.metric-picker-popup input')
    await search.setValue('technical_rate')
    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()

    expect(pageAloudataMetrics).toHaveBeenLastCalledWith('aloudata-1', expect.objectContaining({ keyword: 'technical_rate' }))
  })

  it('disables dimensions that are not supported by every selected metric and explains why', async () => {
    state.ui.aloudata.metrics = ['metric_a', 'technical_rate']
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.dimension-selection-box').trigger('click')
    await flushPromises()

    const region = wrapper.find('.dimension-picker-popup').findAll('.directory-item')
      .find((item) => item.text().includes('region'))
    expect(region?.find('input').element.disabled).toBe(true)
    expect(region?.find('[data-tooltip]').attributes('data-tooltip')).toBe('该维度不是已选指标的可用维度')
    expect(getAloudataMetricDetail).toHaveBeenCalledWith('aloudata-1', 'technical_rate')
  })

  it('disables metrics that do not support every selected dimension', async () => {
    state.ui.aloudata.metrics = []
    state.ui.aloudata.dims = ['region']
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()

    const metricA = wrapper.find('.metric-picker-popup').findAll('.directory-item')
      .find((item) => item.text().includes('metric_a'))
    expect(metricA?.find('input').element.disabled).toBe(true)
  })

  it('loads and displays live metric details when hovering a metric', async () => {
    state.ui.aloudata.metrics = []
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    await wrapper.find('.metric-detail-trigger[data-metric-name="metric_a"]').trigger('mouseover')
    await flushPromises()
    expect(getAloudataMetricDetail).toHaveBeenCalledWith('aloudata-1', 'metric_a')
    const detail = wrapper.find('.metric-detail-card')
    expect(detail.text()).toContain('按用户统计转化率')
    expect(detail.text()).toContain('DERIVED')
    expect(detail.text()).toContain('%')
  })

  it('loads and displays live dimension details when hovering a dimension', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.dimension-selection-box').trigger('click')
    await flushPromises()
    await wrapper.find('.dimension-detail-trigger[data-dimension-name="region"]').trigger('mouseover')
    await flushPromises()
    expect(getAloudataDimensionDetail).toHaveBeenCalledWith('aloudata-1', 'region')
    const detail = wrapper.find('.dimension-detail-card')
    expect(detail.text()).toContain('region')
    expect(detail.text()).toContain('所属大区')
    expect(detail.text()).toContain('VARCHAR')
    expect(detail.text()).toContain('维度描述')
  })

  it('keeps incompatible existing selections, reports the conflict, and blocks confirmation', async () => {
    state.ui.aloudata.dims = ['region']
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    expect(wrapper.find('.selection-conflict').text()).toContain('已选指标与维度存在不兼容项')
    expect(wrapper.find('.dimension-selection-box').text()).toContain('region')
    expect(wrapper.find('.stub-dialog').findAll('button').at(-1)?.element.disabled).toBe(true)

    await wrapper.find('.dimension-selection-box .selected-tag-remove').trigger('click')
    expect(wrapper.find('.selection-conflict').exists()).toBe(false)
    expect(wrapper.find('.stub-dialog').findAll('button').at(-1)?.element.disabled).toBe(false)
  })
})
