import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import AiChatPanel from '../AiChatPanel.vue'

vi.mock('@/api/insight-dashboard', () => ({ listDashboards: vi.fn(), createDashboard: vi.fn(), updateDashboard: vi.fn() }))
vi.mock('@/api/datasource', () => ({ list: vi.fn().mockResolvedValue([]), listDatasources: vi.fn().mockResolvedValue([]) }))

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

describe('AiChatPanel keyboard semantics', () => {
  it('expands reasoning with Enter and Space', async () => {
    const wrapper = mount(AiChatPanel, {
      global: {
        plugins: [i18n],
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<span><slot /></span>' },
          'el-input': { template: '<textarea />' },
        },
      },
    })
    ;(wrapper.vm as any).messages.push({ role: 'assistant', content: '', reasoning: '先分析', reasoningExpanded: false })
    await nextTick()
    const header = wrapper.find('.reasoning-header')
    expect(header.attributes('role')).toBe('button')
    expect(header.attributes('tabindex')).toBe('0')
    await header.trigger('keydown', { key: 'Enter' })
    await header.trigger('keydown', { key: ' ' })
    expect((wrapper.vm as any).messages[0].reasoningExpanded).toBe(false)
    await header.trigger('keydown', { key: 'Enter' })
    expect((wrapper.vm as any).messages[0].reasoningExpanded).toBe(true)
  })
})
