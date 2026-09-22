import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { pageAloudataMetrics, pageAloudataDimensions } = vi.hoisted(() => ({
  pageAloudataMetrics: vi.fn(async () => ({
    records: [{ metricName: 'metric_a', metricDisplayName: '指标 A' }],
    total: 1,
    current: 1,
    size: 50,
    pages: 1,
  })),
  pageAloudataDimensions: vi.fn(async () => ({
    records: [{ dimName: 'dim_a', dimDisplayName: '维度 A' }],
    total: 1,
    current: 1,
    size: 50,
    pages: 1,
  })),
}))

vi.mock('@/api/semantic-model', () => ({
  pageAloudataMetrics,
  pageAloudataDimensions,
  listAloudataCategoryCounts: vi.fn(async () => []),
  getAloudataMetricDetail: vi.fn(),
}))

vi.mock('@/api/datasource', () => ({
  searchAnalysisViews: vi.fn(async () => []),
}))

import AloudataDialog from '../card-attribute/dataset/AloudataDialog.vue'
import { useInsight } from '../card-attribute/useInsight'

const { state } = useInsight()

const SelectStub = defineComponent({
  name: 'ElSelect',
  props: { modelValue: { type: [Array, String], default: () => [] } },
  emits: ['update:modelValue'],
  setup(props, { slots, emit }) {
    return () =>
      h(
        'div',
        { class: 'stub-select' },
        slots.tag?.({
          data: (Array.isArray(props.modelValue) ? props.modelValue : []).map((value) => ({ value })),
          deleteTag: (_event: MouseEvent, removed: { value: string }) => {
            const next = (props.modelValue as string[]).filter((item) => item !== removed.value)
            // The real Element Plus select emits this when its tag close button is used.
            // The test only needs to model the component contract.
            emit('update:modelValue', next)
          },
        }),
      )
  },
})

const stubs = {
  'el-dialog': { template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>' },
  'el-alert': { template: '<div />' },
  'el-select': SelectStub,
  'el-option': { template: '<div />' },
  'el-tag': {
    props: { closable: Boolean },
    emits: ['close'],
    template: '<span class="selected-tag"><slot /><button v-if="closable" class="selected-tag-remove" type="button" @click="$emit(\'close\')">×</button></span>',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-input': { template: '<input />' },
  'el-checkbox': { template: '<input type="checkbox" />' },
  'el-table': { template: '<div />' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
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
  pageAloudataMetrics.mockClear()
  pageAloudataDimensions.mockClear()
})

describe('Aloudata 指标&维度选择', () => {
  it('回显已选指标和维度，并提供可点击的删除按钮', async () => {
    const wrapper = mount(AloudataDialog, { global: { stubs } })
    state.ui.aloudata.visible = true
    await flushPromises()

    expect(wrapper.findAll('.selected-tag').map((tag) => tag.text())).toEqual(['指标 A×', '维度 A×'])

    await wrapper.find('.selected-tag-remove').trigger('click')
    expect(state.ui.aloudata.metrics).toEqual([])
    expect(state.ui.aloudata.dims).toEqual(['dim_a'])
  })
})
