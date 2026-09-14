import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DelegationNodeView from '../DelegationNodeView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

describe('DelegationNodeView keyboard semantics', () => {
  it('expands a node with body content from Enter', async () => {
    const wrapper = mount(DelegationNodeView, {
      props: {
        node: {
          agentName: '分析 Agent',
          status: 'completed',
          plan: { steps: ['读取'], stepResults: [{ status: 'completed' }] },
        } as any,
      },
      global: { plugins: [i18n] },
    })

    const header = wrapper.find('.deleg-node__header')
    expect(header.attributes('role')).toBe('button')
    expect(header.attributes('tabindex')).toBe('0')
    await header.trigger('keydown', { key: 'Enter' })
    expect(wrapper.find('.deleg-node__body').exists()).toBe(true)
  })
})
