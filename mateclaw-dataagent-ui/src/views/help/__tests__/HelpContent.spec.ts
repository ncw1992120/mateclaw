import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import HelpContent from '../HelpContent.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

describe('HelpContent breadcrumb semantics', () => {
  it('makes breadcrumb navigation keyboard-operable', async () => {
    const wrapper = mount(HelpContent, {
      props: {
        currentDocument: { id: 'doc-1', title: '入门', categoryId: 'cat-1', content: '# 入门', status: 'published' } as any,
        categoryTree: [{ id: 'cat-1', name: '指南', children: [] }] as any,
        searchVisible: false,
        searchResults: [],
        searchLoading: false,
        canManage: false,
      },
      global: {
        plugins: [i18n],
        stubs: {
          'el-icon': { template: '<span><slot /></span>' },
          'el-tag': { template: '<span><slot /></span>' },
          HelpRelatedDocs: { template: '<div />' },
          HelpFeedback: { template: '<div />' },
        },
      },
    })
    const breadcrumb = wrapper.find('.breadcrumb-link')
    expect(breadcrumb.attributes('role')).toBe('button')
    expect(breadcrumb.attributes('tabindex')).toBe('0')
    await breadcrumb.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('goHome')).toHaveLength(1)
  })
})
