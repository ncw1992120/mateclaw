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
    availableDimensions: ['dim_a'],
  })),
  getAloudataDimensionDetail: vi.fn(async (_datasourceId: string, dimName: string) => ({
    dimName,
    dimDisplayName: dimName === 'region' ? '所属大区' : '维度 A',
    originDataType: 'VARCHAR',
    dimDescription: '维度描述',
  })),
  pageAloudataMetrics: vi.fn(async () => ({
    records: [
      { metricName: 'metric_a', metricDisplayName: '指标 A', metricCategoryId: 'metric-child', metricCategoryName: '转化指标', availableDimensions: ['dim_a'] },
      { metricName: 'technical_rate', metricDisplayName: '转化率', metricCategoryId: 'metric-child', metricCategoryName: '转化指标', availableDimensions: ['dim_a'] },
    ],
    total: 2,
    current: 1,
    size: 20,
    pages: 1,
  })),
  pageAloudataDimensions: vi.fn(async () => ({
    records: [
      { dimName: 'dim_a', dimDisplayName: '维度 A', categoryId: 'dim-child', categoryName: '客户维度' },
      { dimName: 'region', dimDisplayName: '所属大区', categoryId: 'dim-child', categoryName: '客户维度' },
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

async function expandFieldCategory(wrapper: ReturnType<typeof mount>, kind: 'metric' | 'dimension') {
  const rootId = kind === 'metric' ? 'metric-root' : 'dim-root'
  const childId = kind === 'metric' ? 'metric-child' : 'dim-child'
  const popupClass = kind === 'metric' ? '.metric-picker-popup' : '.dimension-picker-popup'
  await wrapper.find(`${popupClass} [data-category-id="${rootId}"]`).trigger('click')
  await flushPromises()
  await wrapper.find(`${popupClass} [data-category-id="${childId}"]`).trigger('click')
  await flushPromises()
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
  it('renders the analysis builder and opens the matching live picker from each add button', async () => {
    state.ui.aloudata.metrics = []
    state.ui.aloudata.dims = []
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    expect(wrapper.find('.analysis-builder').exists()).toBe(true)
    expect(wrapper.find('.analysis-builder').text()).toContain('ANALYSIS BUILDER')
    expect(wrapper.find('.config-empty').text()).toContain('尚未添加维度')
    expect(wrapper.findAll('.config-empty')[1]?.text()).toContain('尚未添加指标')

    await wrapper.find('[data-testid="open-dimension-picker"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('.dimension-picker-popup').exists()).toBe(true)
    expect(wrapper.find('.metric-picker-popup').exists()).toBe(false)

    await wrapper.find('.dimension-picker-popup [data-category-id="dim-root"]').trigger('click')
    await flushPromises()
    await wrapper.find('.dimension-picker-popup [data-category-id="dim-child"]').trigger('click')
    await flushPromises()
    await wrapper.find('.dimension-picker-popup [data-field-code="dim_a"]').trigger('click')
    expect(wrapper.find('.configured-fields[data-testid="configured-dimensions"]').text()).toContain('维度 A')

    await wrapper.find('.configured-fields[data-testid="configured-dimensions"] button[aria-label*="移除维度"]').trigger('click')
    expect(state.ui.aloudata.dims).toEqual([])
  })

  it('uses existing live category and paginated list APIs instead of the unavailable directory route', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    expect(listAloudataCategoryCounts).toHaveBeenCalledWith('aloudata-1', 'CATEGORY_METRIC')
    expect(listAloudataCategoryCounts).toHaveBeenCalledWith('aloudata-1', 'CATEGORY_DIMENSION')
    expect(getAloudataMetricDirectory).not.toHaveBeenCalled()
  })

  it('renders live fields inline beneath expandable categories instead of a separate results pane', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()

    expect(wrapper.find('.metric-picker-popup .directory-results').exists()).toBe(false)
    await expandFieldCategory(wrapper, 'metric')
    expect(pageAloudataMetrics).toHaveBeenCalledWith('aloudata-1', expect.objectContaining({ categoryId: 'metric-child' }))
    expect(wrapper.find('.metric-picker-popup').text()).toContain('technical_rate')

    await wrapper.find('.dimension-selection-box').trigger('click')
    await flushPromises()
    expect(wrapper.find('.dimension-picker-popup .directory-results').exists()).toBe(false)
    await expandFieldCategory(wrapper, 'dimension')
    expect(pageAloudataDimensions).toHaveBeenCalledWith('aloudata-1', expect.objectContaining({ categoryId: 'dim-child' }))
    expect(wrapper.find('.dimension-picker-popup').text()).toContain('所属大区')
  })

  it('shows separate configured-field areas with removable selected chips', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    const metricFields = wrapper.find('[data-testid="configured-metrics"]')
    const dimensionFields = wrapper.find('[data-testid="configured-dimensions"]')
    expect(metricFields.exists()).toBe(true)
    expect(dimensionFields.exists()).toBe(true)
    expect(metricFields.text()).toContain('metric_a')
    expect(dimensionFields.text()).toContain('dim_a')

    await metricFields.find('button[aria-label*="移除指标"]').trigger('click')
    expect(state.ui.aloudata.metrics).toEqual([])
    expect(state.ui.aloudata.dims).toEqual(['dim_a'])
  })

  it('opens a floating tree picker and allows multiple metric selections without closing it', async () => {
    state.ui.aloudata.metrics = []
    state.ui.aloudata.dims = []
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    await expandFieldCategory(wrapper, 'metric')
    expect(wrapper.find('.metric-picker-popup').exists()).toBe(true)
    expect(wrapper.find('.metric-picker-popup').text()).toContain('业务指标')
    expect(wrapper.find('.metric-picker-popup').text()).toContain('technical_rate')

    const fieldRow = wrapper.find('.metric-picker-popup [data-field-code="technical_rate"]')
    expect(fieldRow.find('input').element.disabled).toBe(false)
    await fieldRow.trigger('click')
    expect(state.ui.aloudata.metrics).toContain('technical_rate')
    expect(wrapper.find('.metric-picker-popup').exists()).toBe(true)
  })

  it('filters paginated metrics when a category is selected in the tree', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    await expandFieldCategory(wrapper, 'metric')

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
    await expandFieldCategory(wrapper, 'dimension')

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
    await expandFieldCategory(wrapper, 'metric')

    const metricA = wrapper.find('.metric-picker-popup').findAll('.directory-item')
      .find((item) => item.text().includes('metric_a'))
    expect(metricA?.find('input').element.disabled).toBe(true)
  })

  it('can hide metrics that are incompatible with the selected dimensions', async () => {
    state.ui.aloudata.metrics = []
    state.ui.aloudata.dims = ['region']
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    const incompatibleMetricPage = {
      records: [
        { metricName: 'metric_a', metricDisplayName: '指标 A', metricCategoryId: 'metric-child', metricCategoryName: '转化指标', availableDimensions: ['dim_a'] },
        { metricName: 'technical_rate', metricDisplayName: '转化率', metricCategoryId: 'metric-child', metricCategoryName: '转化指标', availableDimensions: ['dim_a', 'region'] },
      ], total: 2, current: 1, size: 20, pages: 1,
    }
    pageAloudataMetrics.mockImplementationOnce(async () => incompatibleMetricPage)
    pageAloudataMetrics.mockImplementationOnce(async () => incompatibleMetricPage)
    await expandFieldCategory(wrapper, 'metric')

    expect(wrapper.findAll('.metric-picker-popup .directory-item')).toHaveLength(2)
    await wrapper.find('.metric-picker-popup [role="switch"]').trigger('click')
    expect(wrapper.findAll('.metric-picker-popup .directory-item')).toHaveLength(1)
    expect(wrapper.find('.metric-picker-popup').text()).not.toContain('指标 A')
  })

  it('loads and displays live metric details when hovering a metric', async () => {
    state.ui.aloudata.metrics = []
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()
    await wrapper.find('.metric-selection-box').trigger('click')
    await flushPromises()
    await expandFieldCategory(wrapper, 'metric')
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
    await expandFieldCategory(wrapper, 'dimension')
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

    expect(wrapper.find('.selection-conflict').text()).toContain('不兼容')
    expect(wrapper.find('[data-testid="configured-dimensions"]').text()).toContain('region')
    expect(wrapper.find('.stub-dialog').findAll('button').at(-1)?.element.disabled).toBe(true)

    await wrapper.find('[data-testid="configured-dimensions"] button[aria-label*="移除维度"]').trigger('click')
    expect(wrapper.find('.selection-conflict').exists()).toBe(false)
    expect(wrapper.find('.stub-dialog').findAll('button').at(-1)?.element.disabled).toBe(false)
  })
})
