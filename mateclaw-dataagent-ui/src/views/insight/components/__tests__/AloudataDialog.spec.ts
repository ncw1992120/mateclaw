import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getAloudataMetricDirectory, pageAloudataMetrics, pageAloudataDimensions, listAloudataCategoryCounts } = vi.hoisted(() => ({
  getAloudataMetricDirectory: vi.fn(async () => [{
    categoryId: 'metric-root',
    categoryName: '业务指标',
    metricList: [{ metricName: 'metric_a', metricDisplayName: '指标 A' }],
    subCategory: [],
  }]),
  pageAloudataMetrics: vi.fn(async () => ({
    records: [
      { metricName: 'metric_a', metricDisplayName: '指标 A' },
      { metricName: 'technical_rate', metricDisplayName: '转化率' },
    ],
    total: 2,
    current: 1,
    size: 20,
    pages: 1,
  })),
  pageAloudataDimensions: vi.fn(async () => ({
    records: [{ dimName: 'dim_a', dimDisplayName: '维度 A' }],
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
    return () => h('div', { class: 'picker-popover' }, [
      h('div', { class: 'picker-reference', onClick: () => {
        emit('update:visible', !props.visible)
        if (!props.visible) emit('show')
      } }, slots.reference?.()),
      props.visible ? h('div', { class: 'picker-popup' }, slots.default?.()) : null,
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
  'el-alert': { template: '<div />' },
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
  'el-tooltip': { template: '<span><slot /><slot name="content" /></span>' },
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
})
