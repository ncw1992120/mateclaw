import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DashboardThemePanel from '../DashboardThemePanel.vue'

describe('DashboardThemePanel', () => {
  it('展示预设并通过键盘选择，修改只发出草稿而不请求数据', async () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'preset', presetId: 'blue' },
        visible: true,
      },
      global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' } } },
    })
    const rose = wrapper.find('[data-preset-id="rose"]')
    expect(rose.exists()).toBe(true)
    await rose.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({ mode: 'preset', presetId: 'rose' })
  })

  it('存在主题覆盖时切换预设要求确认', async () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'custom', presetId: 'blue', overrides: { text: '#102030' } },
        visible: true,
      },
      global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' } } },
    })
    await wrapper.find('[data-preset-id="rose"]').trigger('click')
    expect(wrapper.emitted('confirm-theme-switch')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
