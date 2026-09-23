import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const colorHistory = vi.hoisted(() => ({ recentColors: [] as string[], recordColor: vi.fn() }))

vi.mock('@/stores/useInsightColorHistoryStore', () => ({
  useInsightColorHistoryStore: () => colorHistory,
}))

import router from '@/router'
import CardContainerPrototype from '../CardContainerPrototype.vue'

const ColorPickerStub = {
  props: ['modelValue', 'predefine'],
  emits: ['update:modelValue', 'change'],
  template: '<button type="button" aria-label="取色器" @click="$emit(\'change\', \'#abcdef\')">取色</button>',
}

const InputStub = {
  inheritAttrs: false,
  props: ['modelValue', 'type'],
  emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" :type="type || \'text\'" :value="modelValue" @input="$emit(\'update:modelValue\', type === \'number\' ? Number($event.target.value) : $event.target.value)" />',
}

const SwitchStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
}

function mountPrototype() {
  return mount(CardContainerPrototype, {
    global: {
      stubs: {
        ElColorPicker: ColorPickerStub,
        'el-color-picker': ColorPickerStub,
        'el-input': InputStub,
        'el-switch': SwitchStub,
        'el-select': { props: ['modelValue'], template: '<select><slot /></select>' },
        'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
        'el-button': { template: '<button><slot /></button>' },
      },
    },
  })
}

describe('CardContainerPrototype border color field', () => {
  beforeEach(() => {
    colorHistory.recentColors = []
    colorHistory.recordColor.mockClear()
  })

  it('updates the rendered border color while preserving border enabled and width', async () => {
    const wrapper = mountPrototype()
    const colorInput = wrapper.find('input[aria-label="边框颜色"]')
    const container = wrapper.get('.card-container')
    const borderControls = wrapper.findAll('.field.row').find((field) => field.text().includes('显示边框'))!
    const enabledInput = borderControls.get('input[type="checkbox"]')
    const widthInput = borderControls.get('input[type="number"]')

    expect(colorInput.exists()).toBe(true)
    expect((colorInput.element as HTMLInputElement).value).toBe('#E5EAF2')
    expect((container.element as HTMLElement).style.borderColor).toBe('rgb(229, 234, 242)')
    expect((enabledInput.element as HTMLInputElement).checked).toBe(true)
    expect((widthInput.element as HTMLInputElement).value).toBe('1')

    await colorInput.setValue('#123abc')

    expect((container.element as HTMLElement).style.borderColor).toBe('rgb(18, 58, 188)')
    expect((enabledInput.element as HTMLInputElement).checked).toBe(true)
    expect((widthInput.element as HTMLInputElement).value).toBe('1')
  })

  it('keeps the existing development route pointed at this prototype', () => {
    expect(router.resolve('/insight/card-container-prototype').name).toBe('insight-card-container-prototype')
    expect(router.hasRoute('insight-card-container-prototype')).toBe(import.meta.env.DEV)
  })
})
