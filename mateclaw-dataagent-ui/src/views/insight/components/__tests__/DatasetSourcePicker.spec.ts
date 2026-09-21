import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DatasetSourcePicker from '../DatasetSourcePicker.vue'

vi.mock('@/api/dataset', () => ({ list: vi.fn().mockResolvedValue([{ id: 'd1', name: '订单数据集', sourceType: 'JDBC_TABLE', datasourceName: 'db1' }]) }))
vi.mock('@/api/datasource', () => ({ list: vi.fn().mockResolvedValue([{ id: 'j1', name: 'db2', sourceType: 'mysql' }]) }))

const stubs = {
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
}

describe('DatasetSourcePicker', () => {
  it('shows source categories and opens interface as a direct entry', async () => {
    const wrapper = mount(DatasetSourcePicker, {
      props: {
        availableDatasets: [{ id: 'd1', name: '订单数据集', sourceType: 'JDBC_TABLE', datasourceName: 'db1' }],
        availableDatasources: [{ id: 'j1', name: 'db2', sourceType: 'mysql' }],
      },
      global: { stubs },
    })
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('JDBC')
    expect(wrapper.text()).toContain('订单数据集')
    const jdbcSourceButton = wrapper.findAll('button').find(button => button.text().includes('db2'))
    expect(jdbcSourceButton).toBeDefined()
    await jdbcSourceButton!.trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([{ sourceType: 'JDBC_SQL', datasourceId: 'j1' }])
    const interfaceButton = wrapper.findAll('button').find(button => button.text() === '接口')
    expect(interfaceButton).toBeDefined()
    await interfaceButton!.trigger('click')
    expect(wrapper.emitted('select')?.at(-1)).toEqual([{ sourceType: 'HTTP_API' }])
  })

  it('separates reusable datasets from create-from-source actions', () => {
    const wrapper = mount(DatasetSourcePicker, {
      props: {
        availableDatasets: [{ id: 'orders', name: 'orders', sourceType: 'JDBC_TABLE', datasourceName: 'mysql-prod' }],
        availableDatasources: [{ id: 'j1', name: 'mysql-prod', sourceType: 'mysql' }],
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('已有数据集')
    expect(wrapper.text()).toContain('orders')
    expect(wrapper.text()).toContain('JDBC')
    expect(wrapper.text()).toContain('接口')
  })
})
