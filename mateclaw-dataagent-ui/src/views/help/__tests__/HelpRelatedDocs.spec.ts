import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import HelpRelatedDocs from '../HelpRelatedDocs.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

describe('HelpRelatedDocs keyboard semantics', () => {
  it('makes related documents keyboard-operable', async () => {
    const doc = { id: 1, title: '指南', viewCount: 0 } as any
    const wrapper = mount(HelpRelatedDocs, { props: { documents: [doc] }, global: { plugins: [i18n] } })
    const item = wrapper.find('.related-item')
    expect(item.attributes('role')).toBe('button')
    expect(item.attributes('tabindex')).toBe('0')
    await item.trigger('keydown', { key: 'Enter' })
    await item.trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('selectDoc')).toEqual([[doc], [doc]])
  })
})
