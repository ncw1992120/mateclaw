import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref, watch } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import DatasetSourceDialog from '../DatasetSourceDialog.vue'

const { confirmDraft, listAloudataCategoryCounts, pageAloudataMetrics, pageAloudataDimensions, getAloudataMetricDetail, getAloudataDimensionDetail } = vi.hoisted(() => ({
  confirmDraft: vi.fn().mockResolvedValue({ datasetId: 'draft-1' }),
  listAloudataCategoryCounts: vi.fn(async (_id: string, type: string) => type === 'CATEGORY_METRIC'
    ? [{ categoryId: 'metrics-root', categoryName: '业务指标', parentId: null, count: 1 }]
    : [{ categoryId: 'dimensions-root', categoryName: '业务维度', parentId: null, count: 2 }]),
  pageAloudataMetrics: vi.fn(async () => ({ records: [{ metricName: 'orders', metricDisplayName: '订单数', availableDimensions: ['region'] }], total: 1, current: 1, size: 20, pages: 1 })),
  pageAloudataDimensions: vi.fn(async () => ({ records: [{ dimName: 'region', dimDisplayName: '所属区域' }, { dimName: 'city', dimDisplayName: '城市' }], total: 2, current: 1, size: 20, pages: 1 })),
  getAloudataMetricDetail: vi.fn(async (datasourceId: string, metricName: string) => ({ metricName, metricDisplayName: '订单数', availableDimensions: ['region'], type: 'DERIVED', businessCaliber: '订单汇总', unit: '笔', owner: '数据团队' })),
  getAloudataDimensionDetail: vi.fn(async (datasourceId: string, dimName: string) => ({ dimName, dimDisplayName: '所属区域', originDataType: 'VARCHAR', dimDescription: '区域名称' })),
}))

vi.mock('@/api/dataset', () => ({
  previewDraft: vi.fn().mockResolvedValue({ rows: [{ id: 1 }] }),
  confirmDraft,
}))
vi.mock('@/api/datasource', () => ({ listAnalysisViews: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/semantic-model', () => ({ listAloudataCategoryCounts, pageAloudataMetrics, pageAloudataDimensions, getAloudataMetricDetail, getAloudataDimensionDetail }))

const PopoverStub = defineComponent({
  props: { visible: Boolean },
  emits: ['update:visible', 'show'],
  setup(props, { slots, emit }) {
    const visible = ref(props.visible)
    watch(() => props.visible, value => { visible.value = value })
    function toggle() {
      visible.value = !visible.value
      emit('update:visible', visible.value)
      if (visible.value) emit('show')
    }
    return () => h('div', { class: 'popover-stub' }, [
      h('div', { class: 'popover-reference', onClick: toggle }, slots.reference?.()),
      visible.value ? h('div', { class: 'popover-content' }, slots.default?.()) : null,
    ])
  },
})

const TreeStub = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  emits: ['node-click'],
  setup(props, { emit }) {
    const render = (nodes: any[]) => nodes.flatMap(node => [
      h('button', { type: 'button', class: 'tree-node', 'data-category-id': node.categoryId, onClick: () => emit('node-click', node) }, node.categoryName),
      ...(node.children?.length ? render(node.children) : []),
    ])
    return () => h('div', { class: 'tree-stub' }, render(props.data as any[]))
  },
})

const stubs = {
  'el-dialog': {
    props: ['modelValue', 'title'],
    template: '<div class="el-dialog"><slot /><slot name="footer" /></div>',
  },
  'el-button': {
    props: ['type', 'plain', 'loading'],
    template: '<button :class="type === \'primary\' && !plain ? \'primary\' : \'secondary\'" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-input': { props: ['modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-input-number': { template: '<input />' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-popover': PopoverStub,
  'el-tree': TreeStub,
  'el-checkbox': {
    props: ['modelValue', 'label', 'disabled'],
    emits: ['change'],
    template: '<input class="picker-checkbox" type="checkbox" :checked="modelValue" :disabled="disabled" @change="$emit(\'change\', $event.target.checked)" />',
  },
  'el-tooltip': { props: ['content'], template: '<span :data-tooltip="content"><slot /></span>' },
  'el-tag': { props: { closable: Boolean }, emits: ['close'], template: '<span class="selected-tag"><slot /><button v-if="closable" class="selected-tag-remove" type="button" @click="$emit(\'close\', $event)">×</button></span>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-pagination': { template: '<div class="pagination-stub" />' },
}
const mountOptions = {
  global: {
    stubs,
    directives: { loading: {} },
  },
}

describe('DatasetSourceDialog', () => {
  it('renders separate Aloudata metric and dimension selection boxes instead of text inputs', async () => {
    const wrapper = mount(DatasetSourceDialog, {
      props: {
        modelValue: true,
        selection: { sourceType: 'ALOUDATA_METRICS', datasourceId: 'aloudata-1' },
      },
      ...mountOptions,
    })
    await nextTick()

    expect(wrapper.find('.aloudata-field-selector').exists()).toBe(true)
    expect(wrapper.find('.metric-selection-box').exists()).toBe(true)
    expect(wrapper.find('.dimension-selection-box').exists()).toBe(true)
    expect(wrapper.find('[aria-label="配置指标"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="配置维度"]').exists()).toBe(false)
  })

  it('loads live categories and allows selecting removable metric chips', async () => {
    listAloudataCategoryCounts.mockClear()
    pageAloudataMetrics.mockClear()
    const wrapper = mount(DatasetSourceDialog, {
      props: { modelValue: true, selection: { sourceType: 'ALOUDATA_METRICS', datasourceId: 'aloudata-1' } },
      ...mountOptions,
    })
    await nextTick()
    await wrapper.find('.metric-selection-box').trigger('click')
    await nextTick()
    await nextTick()

    expect(listAloudataCategoryCounts).toHaveBeenCalledWith('aloudata-1', 'CATEGORY_METRIC')
    expect(pageAloudataMetrics).toHaveBeenCalledWith('aloudata-1', expect.objectContaining({ pageNumber: 1 }))
    expect(wrapper.find('.metric-picker-popup').text()).toContain('订单数')

    await wrapper.find('.picker-checkbox').setValue(true)
    await nextTick()
    expect(wrapper.find('.metric-selection-box').text()).toContain('订单数')
    await wrapper.find('.metric-selection-box .selected-tag-remove').trigger('click')
    expect(wrapper.find('.metric-selection-box').text()).toContain('点击选择指标')
  })

  it('selects only compatible dimensions and submits the selected Aloudata fields', async () => {
    confirmDraft.mockClear()
    const wrapper = mount(DatasetSourceDialog, {
      props: {
        modelValue: true,
        selection: { sourceType: 'ALOUDATA_METRICS', datasourceId: 'aloudata-1' },
      },
      ...mountOptions,
    })
    await wrapper.find('.metric-selection-box').trigger('click')
    await nextTick()
    await wrapper.find('.metric-picker-popup .picker-checkbox').setValue(true)
    await nextTick()

    await wrapper.find('.dimension-selection-box').trigger('click')
    await nextTick()
    await nextTick()
    const dimensionCheckboxes = wrapper.findAll('.dimension-picker-popup .picker-checkbox')
    expect(dimensionCheckboxes).toHaveLength(2)
    expect((dimensionCheckboxes[0].element as HTMLInputElement).disabled).toBe(false)
    expect((dimensionCheckboxes[1].element as HTMLInputElement).disabled).toBe(true)
    await dimensionCheckboxes[0].setValue(true)
    await nextTick()

    await wrapper.findAll('button').find(button => button.text() === '确认添加')!.trigger('click')
    await nextTick()

    expect(confirmDraft).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'ALOUDATA_METRICS',
      datasourceId: 'aloudata-1',
      sourceConfig: { datasourceId: 'aloudata-1', metrics: ['orders'], dimensions: ['region'], filters: '' },
    }))
  })

  it('keeps preview in the same dialog and exposes one primary confirm action', async () => {
    const wrapper = mount(DatasetSourceDialog, {
      props: {
        modelValue: true,
        selection: { sourceType: 'JDBC_SQL', datasourceId: 'mysql-1' },
      },
      ...mountOptions,
    })

    expect(wrapper.findAll('.el-dialog')).toHaveLength(1)
    expect(wrapper.findAll('button').filter(button => button.text() === '确认添加')).toHaveLength(1)
    await wrapper.get('button[aria-label="筛选预览"]').trigger('click')
    expect(wrapper.find('[role="tabpanel"][aria-label="预览结果"]').exists()).toBe(true)
  })
})
