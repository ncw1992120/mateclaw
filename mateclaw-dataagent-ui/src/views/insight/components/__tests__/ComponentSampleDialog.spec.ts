import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ComponentSampleDialog from '../ComponentSampleDialog.vue'
import { resolveComponentSample } from '@/utils/component-sample-data'

describe('ComponentSampleDialog', () => {
  it('shows the component format rule and copyable sample JSON', () => {
    const sample = resolveComponentSample({ id: 'pie-1', type: 'chart', chartType: 'pie' })
    const wrapper = mount(ComponentSampleDialog, {
      props: { modelValue: true, title: '饼图', sample },
      global: { stubs: { 'el-dialog': { template: '<div><slot /></div>' }, 'el-button': { template: '<button><slot /></button>' } } },
    })
    expect(wrapper.text()).toContain(sample.contract)
    expect(wrapper.get('pre').text()).toContain('"subtype": "pie"')
    expect(wrapper.get('pre').text()).toContain('"isSample": true')
  })
})
