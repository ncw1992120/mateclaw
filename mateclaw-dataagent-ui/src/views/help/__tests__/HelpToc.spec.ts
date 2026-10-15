import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import HelpToc from '../HelpToc.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

describe('HelpToc keyboard semantics', () => {
  it('makes entries focusable buttons and activates them with Enter and Space', async () => {
    const wrapper = mount(HelpToc, {
      props: { headings: [{ id: 'intro', text: '简介', level: 1 }], activeHeadingId: '' },
      global: { plugins: [i18n] },
    })
    const item = wrapper.find('.toc-item')
    expect(item.attributes('role')).toBe('button')
    expect(item.attributes('tabindex')).toBe('0')
    await item.trigger('keydown', { key: 'Enter' })
    await item.trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('scrollTo')).toEqual([['intro'], ['intro']])
  })
})
