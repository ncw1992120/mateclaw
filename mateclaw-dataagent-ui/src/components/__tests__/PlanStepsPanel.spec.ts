import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import PlanStepsPanel from '../PlanStepsPanel.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const plan = {
  steps: ['读取数据', '生成结果'],
  currentStep: 0,
  stepResults: [{ status: 'completed', result: 'ok' }, { status: 'pending' }],
} as any

describe('PlanStepsPanel keyboard semantics', () => {
  it('expands the plan with Enter and toggles result steps with Space', async () => {
    const wrapper = mount(PlanStepsPanel, {
      props: { plan, isGenerating: false },
      global: { plugins: [i18n] },
    })

    const toggle = wrapper.find('.plan-panel__toggle')
    expect(toggle.attributes('role')).toBe('button')
    expect(toggle.attributes('tabindex')).toBe('0')
    await toggle.trigger('keydown', { key: 'Enter' })
    expect(wrapper.find('.plan-panel__body').exists()).toBe(true)

    const step = wrapper.find('.plan-step')
    expect(step.attributes('role')).toBe('button')
    await step.trigger('keydown', { key: ' ' })
    expect(wrapper.find('.plan-step__result').exists()).toBe(true)
  })
})
