import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CategoryTreeNode from '../CategoryTreeNode.vue'

describe('CategoryTreeNode', () => {
  it('supports keyboard selection and expansion with accessible tree semantics', async () => {
    const expandedSet = new Set<string>(['sales'])
    const wrapper = mount(CategoryTreeNode, {
      props: {
        group: {
          categoryId: 'sales',
          categoryName: '销售',
          metricCount: 3,
          children: [{ categoryId: 'regional', categoryName: '区域', metricCount: 1 }],
        },
        type: 'metric',
        selectedId: 'sales',
        expandedSet,
      },
      global: {
        stubs: {
          'el-icon': { template: '<span><slot /></span>' },
          ArrowRight: true,
          ArrowDown: true,
          Folder: true,
          FolderOpened: true,
        },
      },
    })

    const node = wrapper.find('[role="treeitem"]')
    expect(node.attributes('aria-label')).toBe('销售，3 个指标')
    expect(node.attributes('tabindex')).toBe('0')
    expect(wrapper.find('.tree-node-expand[role="button"]').attributes('aria-expanded')).toBe('true')

    await node.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('select')).toEqual([['sales']])

    await wrapper.find('.tree-node-expand[role="button"]').trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('toggle')).toEqual([[{ categoryId: 'sales', type: 'metric' }]])
  })
})
