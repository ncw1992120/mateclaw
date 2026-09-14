import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import ComponentPalette from '../ComponentPalette.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      insight: {
        paletteTitle: '组件库',
        component: { table: '数据表格' },
      },
    },
  },
  missingWarn: false,
  fallbackWarn: false,
})

describe('ComponentPalette', () => {
  it('supports keyboard activation for adding a component', async () => {
    const wrapper = mount(ComponentPalette, { global: { plugins: [i18n] } })
    const table = wrapper.findAll('.palette-item').find((item) => item.text() === '数据表格')

    expect(table).toBeDefined()
    await table!.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('add-component')).toEqual([[{ type: 'table' }]])
  })
})
