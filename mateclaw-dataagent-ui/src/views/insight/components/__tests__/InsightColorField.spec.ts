import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const store = vi.hoisted(() => ({ recentColors: [] as string[], recordColor: vi.fn() }))

vi.mock('@/stores/useInsightColorHistoryStore', () => ({
  useInsightColorHistoryStore: () => ({
    get recentColors() { return store.recentColors },
    recordColor: store.recordColor,
  }),
}))

import InsightColorField from '../InsightColorField.vue'

const ColorPickerStub = {
  props: ['modelValue', 'predefine'],
  emits: ['update:modelValue', 'change'],
  template: '<button class="picker" type="button" aria-label="取色器" @click="$emit(\'change\', \'#abcdef\')">{{ modelValue }}</button>',
}

function mountField(modelValue = '#123456', suggestedColors: string[] = ['#ABCDEF', '#112233']) {
  return mount(InsightColorField, {
    props: { modelValue, label: '主色', suggestedColors },
    global: { stubs: { ElColorPicker: ColorPickerStub, 'el-color-picker': ColorPickerStub } },
  })
}

describe('InsightColorField', () => {
  beforeEach(() => {
    store.recentColors = []
    store.recordColor.mockClear()
  })

  it('normalizes valid short and full HEX input to uppercase six-digit model values', async () => {
    const wrapper = mountField()
    const input = wrapper.get('input')

    await input.setValue('#abc')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe('#AABBCC')
    await input.setValue('#a1b2c3')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe('#A1B2C3')
    expect((wrapper.get('[data-testid="color-preview"]').element as HTMLElement).style.backgroundColor).toBe('rgb(161, 178, 195)')
  })

  it('keeps invalid intermediate text as a draft without updating the model', async () => {
    const wrapper = mountField('#123456')
    const input = wrapper.get('input')

    await input.setValue('#12')
    expect((input.element as HTMLInputElement).value).toBe('#12')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toBeTruthy()
  })

  it('records typed colors only when Enter or blur commits the draft', async () => {
    const wrapper = mountField()
    const input = wrapper.get('input')

    await input.setValue('#abc')
    expect(store.recordColor).not.toHaveBeenCalled()
    await input.trigger('keydown', { key: 'Enter' })
    expect(store.recordColor).toHaveBeenLastCalledWith('#AABBCC')
    expect(store.recordColor).toHaveBeenCalledTimes(1)
    await input.trigger('blur')
    expect(store.recordColor).toHaveBeenCalledTimes(1)

    await input.setValue('#123abc')
    await input.trigger('blur')
    expect(store.recordColor).toHaveBeenLastCalledWith('#123ABC')
    expect(store.recordColor).toHaveBeenCalledTimes(2)
  })

  it('does not record the initial color when Enter or blur occurs without an edit', async () => {
    const wrapper = mountField('#123456')
    const input = wrapper.get('input')

    await input.trigger('keydown', { key: 'Enter' })
    await input.trigger('blur')

    expect(store.recordColor).not.toHaveBeenCalled()
  })

  it('keeps a valid edit pending when the parent reflects its normalized model value', async () => {
    const wrapper = mountField()
    const input = wrapper.get('input')

    await input.setValue('#abc')
    await wrapper.setProps({ modelValue: '#AABBCC' })
    await input.trigger('blur')

    expect(store.recordColor).toHaveBeenCalledTimes(1)
    expect(store.recordColor).toHaveBeenLastCalledWith('#AABBCC')
  })

  it('commits and records a color picker selection immediately', async () => {
    const wrapper = mountField()

    await wrapper.get('.picker').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe('#ABCDEF')
    expect(store.recordColor).toHaveBeenLastCalledWith('#ABCDEF')
  })

  it('puts unique recent colors before suggested colors in the picker', async () => {
    store.recentColors = ['#ABCDEF', '#123456', '#abcdef']
    const wrapper = mountField('#123456', ['#ABCDEF', '#654321'])

    expect(wrapper.getComponent(ColorPickerStub).props('predefine')).toEqual(['#ABCDEF', '#123456', '#654321'])
  })

  it('exposes the field label as the accessible name of the HEX input', () => {
    const wrapper = mountField()
    expect(wrapper.get('input').attributes('aria-label')).toBe('主色')
    expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('input').attributes('id'))
  })
})
