import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DatasetCard from '../DatasetCard.vue'

const actions = vi.hoisted(() => ({
  openFieldMapping: vi.fn(),
  openQueryConfig: vi.fn(),
  openDataDialog: vi.fn(),
  removeDataset: vi.fn(),
  reconfigureDataset: vi.fn(),
  renameDataset: vi.fn(),
}))

vi.mock('../useInsight', () => ({
  useInsight: () => actions,
}))

const dataset = {
  id: 'dataset-1',
  sourceType: 'jdbc' as const,
  sourceLabel: 'JDBC · table_zb',
  alias: 'table_zb',
  fields: [],
  filters: [],
  jdbc: { db: 'db-1', sql: 'select 1' },
}

const stubs = {
  'el-icon': { template: '<span><slot /></span>' },
  'el-input': {
    props: ['modelValue'],
    methods: { focus: vi.fn() },
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
}

describe('DatasetCard', () => {
  it('labels the shared dataset query entry as 查看数据', () => {
    const wrapper = mount(DatasetCard, {
      props: { dataset },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('查看数据')
    expect(wrapper.text()).not.toContain('筛选预览')
  })

  it('uses one dataset title and separates alias editing from card configuration', async () => {
    const wrapper = mount(DatasetCard, {
      props: { dataset },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('数据集')
    expect(wrapper.text()).toContain('table_zb')
    expect(wrapper.text()).not.toContain('数据源')
    expect(wrapper.text()).not.toContain('数据集别名')

    await wrapper.get('.dataset-card').trigger('click')
    expect(actions.reconfigureDataset).toHaveBeenCalledWith('dataset-1')

    actions.reconfigureDataset.mockClear()
    await wrapper.get('[data-dataset-alias]').trigger('click')
    expect(actions.reconfigureDataset).not.toHaveBeenCalled()
    expect(wrapper.find('.ds-alias-input').exists()).toBe(true)

    await wrapper.get('button').trigger('click')
    expect(actions.reconfigureDataset).not.toHaveBeenCalled()
  })
})
