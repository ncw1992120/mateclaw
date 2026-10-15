import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import HelpSearchPanel from '../HelpSearchPanel.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

describe('HelpSearchPanel keyboard semantics', () => {
  it('names the close action and makes results keyboard-operable', async () => {
    const item = { id: 2, title: '搜索结果', categoryName: '帮助', highlightContent: '', updateTime: '', viewCount: 0 } as any
    const wrapper = mount(HelpSearchPanel, {
      props: { visible: true, results: [item], loading: false },
      global: {
        plugins: [i18n],
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-empty': { template: '<div><slot /></div>' },
        },
      },
    })
    expect(wrapper.find('.search-header button').attributes('aria-label')).toBe('关闭搜索结果')
    const result = wrapper.find('.search-item')
    expect(result.attributes('role')).toBe('button')
    expect(result.attributes('tabindex')).toBe('0')
    await result.trigger('keydown', { key: 'Enter' })
    await result.trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('selectDoc')).toEqual([[item], [item]])
  })
})
