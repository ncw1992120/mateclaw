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
  InsightColorField: {
    name: 'InsightColorField',
    props: ['modelValue', 'label', 'suggestedColors'],
    emits: ['update:modelValue', 'change'],
    template: '<input data-testid="insight-color-field" :aria-label="label" :data-suggested-colors="JSON.stringify(suggestedColors)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
  DashboardComponentIcon: { name: 'DashboardComponentIcon', props: ['titleIconStyle'], template: '<span class="preview-icon">{{ titleIconStyle?.iconKey || "automatic" }}</span>' },
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

    await wrapper.get('.color-modes button:nth-child(2)').trigger('click')
    await wrapper.get('[data-testid="insight-color-field"]').setValue('#AABBCC')

    expect(wrapper.findComponent({ name: 'DashboardComponentIcon' }).props('titleIconStyle')).toMatchObject({
      colorMode: 'custom',
      color: '#AABBCC',
    })
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
})
