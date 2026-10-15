import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PropertySection from '../PropertySection.vue'
import EditorEmptyState from '../EditorEmptyState.vue'

describe('insight property presentation primitives', () => {
  it('keeps help collapsed until hover focus or click and exposes an accessible name', async () => {
    const wrapper = mount(PropertySection, {
      props: { title: '数据集配置', help: '选择已有数据集或创建新数据集' },
      slots: { default: '<div>内容</div>' },
    })

    expect(wrapper.text()).not.toContain('选择已有数据集或创建新数据集')
    const help = wrapper.get('[aria-label="数据集配置说明"]')
    await help.trigger('focus')
    expect(wrapper.text()).toContain('选择已有数据集或创建新数据集')

    await help.trigger('keydown', { key: 'Escape' })
    expect(wrapper.text()).not.toContain('选择已有数据集或创建新数据集')
  })

  it('renders exactly one empty-state primary action when actionLabel is present', () => {
    const wrapper = mount(EditorEmptyState, {
      props: { description: '暂未配置数据集', actionLabel: '添加数据集' },
    })

    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.get('button').text()).toBe('添加数据集')
  })
})
