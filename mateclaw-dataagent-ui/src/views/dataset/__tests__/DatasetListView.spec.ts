import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DatasetListView from '../DatasetListView.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/api/dataset', () => ({
  list: vi.fn().mockResolvedValue([
    { id: 'ds-1', name: '订单数据集', datasourceName: '本地 MySQL', sourceType: 'JDBC_TABLE', rowCount: 5, columnCount: 3, status: 'READY' },
    { id: 'ds-2', name: '文件订单集', datasourceName: null, sourceType: 'FILE', rowCount: '0', columnCount: '0', status: 'DRAFT' },
  ]),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

describe('DatasetListView', () => {
  it('renders existing datasets and exposes create/edit navigation actions', async () => {
    push.mockClear()
    const wrapper = mount(DatasetListView, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { RouterLink: true },
      },
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('订单数据集')
    expect(wrapper.text()).toContain('文件数据源 · 待探测')
    expect(wrapper.text()).not.toContain('未命名数据源')
    await wrapper.get('[data-testid="dataset-create"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/datasets/new')
    await wrapper.get('[data-testid="dataset-edit-ds-1"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/datasets/ds-1/edit')
  })
})
