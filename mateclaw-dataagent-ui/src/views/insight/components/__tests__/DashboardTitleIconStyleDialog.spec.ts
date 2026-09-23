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
    template: '<select :value="modelValue" aria-label="图标粗细" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
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
  it('previews style changes immediately and allows switching custom color on and off', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数' },
      global: { stubs },
    })

    expect(wrapper.find('.title-icon-preview').exists()).toBe(false)
    expect(wrapper.get('[role="dialog"]').attributes('data-draggable')).toBe('true')
    expect(wrapper.get('[role="dialog"]').attributes('data-top')).toBeTruthy()
    expect(wrapper.get('[role="dialog"]').attributes('style')).toContain('margin-left')
    expect(wrapper.find('label[for="title-icon-stroke-width"]').text()).toBe('粗细')
    expect(wrapper.get('.color-control-row .control-label').text()).toBe('颜色')
    expect(wrapper.find('.style-controls').classes()).toContain('style-controls-inline')
    expect(wrapper.get('select[aria-label="粗细"]').attributes('style')).toContain('width: 88px')

    await wrapper.get('[aria-label="图表"]').trigger('click')
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ iconKey: 'chart-bar' })

    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')
    expect(wrapper.get('[aria-label="自定义颜色模式"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[aria-label="自定义图标颜色"]').exists()).toBe(true)
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom' })

    await wrapper.get('[aria-label="跟随主题颜色模式"]').trigger('click')
    expect(wrapper.get('[aria-label="跟随主题颜色模式"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[aria-label="自定义图标颜色"]').exists()).toBe(false)
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'theme' })
  })

  it('seeds custom color from the current theme color and previews it immediately', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数', defaultColor: '#b45309' },
      global: { stubs },
    })

    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')

    expect(wrapper.get('input[aria-label="自定义图标颜色"]').attributes('value')).toBe('#b45309')
    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#b45309' })
  })

  it('lets the user choose an icon, thickness, and custom color, then saves the complete style', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数' },
      global: { stubs },
    })

    await wrapper.get('[aria-label="图表"]').trigger('click')
    await wrapper.get('select[aria-label="粗细"]').setValue('2.5')
    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')
    const colorField = wrapper.get('[data-testid="insight-color-field"]')
    expect(colorField.attributes('aria-label')).toBe('自定义图标颜色')
    await colorField.setValue('#8c4a2f')
    expect(wrapper.emitted('save')).toBeUndefined()
    await wrapper.findAll('button').find((button) => button.text() === '应用')!.trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      iconKey: 'chart-bar',
      strokeWidth: 2.5,
      colorMode: 'custom',
      color: '#8c4a2f',
    })
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe(false)
  })

  it('keeps title icon color edits in the preview draft until applied', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, titleIconStyle: { colorMode: 'custom', color: '#123456', strokeWidth: 2 } },
      global: { stubs },
    })

    await wrapper.get('[aria-label="自定义颜色模式"]').trigger('click')
    await wrapper.get('[data-testid="insight-color-field"]').setValue('#AABBCC')

    expect(wrapper.emitted('preview')?.at(-1)?.[0]).toMatchObject({ colorMode: 'custom', color: '#AABBCC' })
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('resets to automatic theme icon styling', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: {
        modelValue: true,
        titleIconStyle: { iconKey: 'chart-bar', strokeWidth: 3, colorMode: 'custom', color: '#123456' },
      },
      global: { stubs },
    })

    await wrapper.findAll('button').find((button) => button.text() === '恢复默认')!.trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '应用')!.trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({ colorMode: 'theme', strokeWidth: 2 })
  })

  it('cancels live preview without committing the style', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: {
        modelValue: true,
        titleIconStyle: { iconKey: 'trend-charts', strokeWidth: 2, colorMode: 'theme' },
      },
      global: { stubs },
    })

    await wrapper.get('[aria-label="图表"]').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '取消')!.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe(false)
    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
