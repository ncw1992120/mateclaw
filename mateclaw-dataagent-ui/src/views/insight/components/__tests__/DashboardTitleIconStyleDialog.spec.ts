import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DashboardTitleIconStyleDialog from '../DashboardTitleIconStyleDialog.vue'

const stubs = {
  'el-dialog': {
    props: ['modelValue', 'title'],
    template: '<div v-if="modelValue" role="dialog"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>',
  },
  'el-icon': { template: '<span><slot /></span>' },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<select :value="modelValue" aria-label="图标粗细" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>',
  },
  'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  'el-radio-group': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="color-modes"><button type="button" @click="$emit(\'update:modelValue\', \'theme\')">跟随主题</button><button type="button" @click="$emit(\'update:modelValue\', \'custom\')">自定义</button></div>',
  },
  'el-radio': { template: '<span><slot /></span>' },
  'el-color-picker': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input aria-label="自定义图标颜色" type="color" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
  DashboardComponentIcon: { props: ['titleIconStyle'], template: '<span class="preview-icon">{{ titleIconStyle?.iconKey || "automatic" }}</span>' },
}

describe('DashboardTitleIconStyleDialog', () => {
  it('lets the user choose an icon, thickness, and custom color, then saves the complete style', async () => {
    const wrapper = mount(DashboardTitleIconStyleDialog, {
      props: { modelValue: true, title: '订单数' },
      global: { stubs },
    })

    await wrapper.get('[aria-label="图表"]').trigger('click')
    await wrapper.get('select[aria-label="图标粗细"]').setValue('2.5')
    await wrapper.get('.color-modes button:nth-child(2)').trigger('click')
    await wrapper.get('input[aria-label="自定义图标颜色"]').setValue('#8c4a2f')
    await wrapper.findAll('button').find((button) => button.text() === '应用')!.trigger('click')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      iconKey: 'chart-bar',
      strokeWidth: 2.5,
      colorMode: 'custom',
      color: '#8c4a2f',
    })
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe(false)
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
})
