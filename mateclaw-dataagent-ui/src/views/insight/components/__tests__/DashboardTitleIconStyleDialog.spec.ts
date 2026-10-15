import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DashboardTitleIconStyleDialog from '../DashboardTitleIconStyleDialog.vue'

const stubs = {
  'el-dialog': {
    props: ['modelValue', 'title', 'draggable', 'top', 'left'],
    template: '<div v-if="modelValue" role="dialog" :data-draggable="draggable" :data-top="top" :style="$attrs.style"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>',
  },
  'el-icon': { template: '<span><slot /></span>' },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
  },
  'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  InsightColorField: {
    name: 'InsightColorField',
    props: ['modelValue', 'label', 'suggestedColors'],
    emits: ['update:modelValue', 'change'],
    template: '<input data-testid="insight-color-field" :aria-label="label" :data-suggested-colors="JSON.stringify(suggestedColors)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
}

describe('DashboardTitleIconStyleDialog', () => {
  it('previews and saves style changes immediately, allowing custom color switching', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数' },
      global: { stubs },
    })

    expect(wrapper.get('[role="dialog"]').attributes('data-draggable')).toBe('true')
    expect(wrapper.get('[role="dialog"]').attributes('data-top')).toBeTruthy()
    expect(wrapper.get('[role="dialog"]').attributes('style')).toContain('margin-left')
    expect(wrapper.find('label[for="title-icon-stroke-width"]').text()).toBe('粗细')
    expect(wrapper.find('label[for="title-icon-font-size"]').text()).toBe('大小')
    expect(wrapper.get('.color-control-row .control-label').text()).toBe('颜色')
    expect(wrapper.find('.style-controls').classes()).toContain('style-controls-inline')

    await wrapper.get('[aria-label="图表"]').trigger('click')
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ iconKey: 'chart-bar' })
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({ iconKey: 'chart-bar' })

    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')
    expect(wrapper.get('[aria-label="自定义颜色模式"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[aria-label="自定义图标颜色"]').exists()).toBe(true)
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom' })

    await wrapper.get('[aria-label="跟随主题颜色模式"]').trigger('click')
    expect(wrapper.get('[aria-label="跟随主题颜色模式"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[aria-label="自定义图标颜色"]').exists()).toBe(false)
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({ colorMode: 'theme' })
  })

  it('seeds custom color from the current theme color and commits it immediately', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数', defaultColor: '#b45309' },
      global: { stubs },
    })

    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')

    expect(wrapper.get('input[aria-label="自定义图标颜色"]').attributes('value')).toBe('#b45309')
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#b45309' })
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#b45309' })
  })

  it('commits the complete style on each change without an apply button', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数' },
      global: { stubs },
    })

    await wrapper.get('[aria-label="图表"]').trigger('click')
    await wrapper.findAll('select')[0].setValue('2.5')
    await wrapper.findAll('select')[1].setValue('20')
    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')
    const colorField = wrapper.get('[data-testid="insight-color-field"]')
    expect(colorField.attributes('aria-label')).toBe('自定义图标颜色')
    await colorField.setValue('#8c4a2f')

    const lastSave = wrapper.emitted('save')?.at(-1)?.[0]
    expect(lastSave).toMatchObject({
      iconKey: 'chart-bar',
      strokeWidth: 2.5,
      fontSize: 20,
      colorMode: 'custom',
      color: '#8c4a2f',
    })
    // 实时生效：save 不关闭弹窗
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.findAll('button').some((button) => button.text() === '应用')).toBe(false)
  })

  it('commits color edits immediately instead of keeping them as an unapplied draft', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, titleIconStyle: { colorMode: 'custom', color: '#123456', strokeWidth: 2 } },
      global: { stubs },
    })

    await wrapper.get('[data-testid="insight-color-field"]').setValue('#AABBCC')

    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#AABBCC' })
    expect(wrapper.emitted('save')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#AABBCC' })
  })

  it('resets to automatic theme icon styling immediately', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: {
        modelValue: true,
        titleIconStyle: { iconKey: 'chart-bar', strokeWidth: 3, colorMode: 'custom', color: '#123456' },
      },
      global: { stubs },
    })

    await wrapper.findAll('button').find((button) => button.text() === '恢复默认')!.trigger('click')

    expect(wrapper.emitted('save')?.at(-1)?.[0]).toEqual({ colorMode: 'theme', strokeWidth: 2 })
  })

  it('keeps already committed styles when closing the dialog', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: {
        modelValue: true,
        titleIconStyle: { iconKey: 'trend-charts', strokeWidth: 2, colorMode: 'theme' },
      },
      global: { stubs },
    })

    await wrapper.get('[aria-label="图表"]').trigger('click')
    expect(wrapper.emitted('save')).toHaveLength(1)

    await wrapper.findAll('button').find((button) => button.text() === '关闭')!.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe(false)
    // 关闭不回滚：save 不会被撤销或重复提交
    expect(wrapper.emitted('save')).toHaveLength(1)
  })

  it('does not save while hydrating the draft on open', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: false, titleIconStyle: { iconKey: 'chart-bar', strokeWidth: 3, colorMode: 'custom', color: '#123456' } },
      global: { stubs },
    })

    await wrapper.setProps({ modelValue: true })
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.emitted('preview')).toBeUndefined()
  })
})
