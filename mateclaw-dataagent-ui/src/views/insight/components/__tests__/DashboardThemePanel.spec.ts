import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DashboardThemePanel from '../DashboardThemePanel.vue'

const InsightColorFieldStub = {
  name: 'InsightColorField',
  props: ['modelValue', 'label', 'suggestedColors'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="insight-color-field-stub" :data-model-value="modelValue" :data-label="label" :data-suggested-colors="suggestedColors?.join(\',\')" />',
}

const globalStubs = {
  'el-drawer': { template: '<div><slot /></div>' },
  InsightColorField: InsightColorFieldStub,
  DashboardComponentIcon: { name: 'DashboardComponentIcon', template: '<span />' },
}

describe('DashboardThemePanel', () => {
  it('展示预设并通过键盘选择，修改只发出草稿而不请求数据', async () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'preset', presetId: 'blue' },
        visible: true,
      },
      global: { stubs: globalStubs },
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
      global: { stubs: globalStubs },
    })
    await wrapper.find('[data-preset-id="rose"]').trigger('click')
    expect(wrapper.emitted('confirm-theme-switch')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('主题面板的即时预览跟随所选深色预设，而不是沿用全站浅色 Token', () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'preset', presetId: 'dark-data' },
        visible: true,
      },
      global: { stubs: globalStubs },
    })

    const style = wrapper.find('.dashboard-theme-panel').attributes('style') ?? ''
    expect(style).toContain('--theme-surface: #161B26')
    expect(style).toContain('--theme-text: #EDF1F7')
  })

  it('将辅助色映射到兼容最近颜色历史的共享颜色字段', () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'preset', presetId: 'blue' },
        visible: true,
      },
      global: { stubs: globalStubs },
    })

    expect(wrapper.getComponent(InsightColorFieldStub).props()).toMatchObject({
      modelValue: '#0F766E',
      label: '辅助色',
      suggestedColors: ['#0F766E', '#0D9488', '#047857'],
    })
  })

  it('共享颜色字段选择或输入颜色时始终显式设置辅助色覆盖', async () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'preset', presetId: 'blue' },
        visible: true,
      },
      global: { stubs: globalStubs },
    })

    const colorField = wrapper.getComponent(InsightColorFieldStub)
    await colorField.vm.$emit('change', '#123456')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      overrides: { accentAlt: '#123456' },
    })

    await colorField.vm.$emit('change', '#0F766E')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      mode: 'preset',
      presetId: 'blue',
      overrides: { accentAlt: '#0F766E' },
    })
  })

  it('恢复预设默认辅助色时仅删除 accentAlt 覆盖', async () => {
    const wrapper = mount(DashboardThemePanel, {
      props: {
        modelValue: { mode: 'custom', presetId: 'teal', overrides: { accentAlt: '#123456', text: '#102030' } },
        visible: true,
      },
      global: { stubs: globalStubs },
    })

    await wrapper.get('[data-testid="reset-accent-alt"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      mode: 'custom',
      presetId: 'teal',
      overrides: { text: '#102030' },
    })
    expect((wrapper.emitted('update:modelValue')?.at(-1)?.[0] as { overrides: Record<string, string> }).overrides).not.toHaveProperty('accentAlt')
  })
})
