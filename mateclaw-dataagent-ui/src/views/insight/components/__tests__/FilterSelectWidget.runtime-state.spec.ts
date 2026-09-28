import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import FilterSelectWidget from '../FilterSelectWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': { insight: { filterPlaceholder: '请选择' } },
  },
  missingWarn: false,
  fallbackWarn: false,
})

const global = {
  plugins: [i18n],
  stubs: {
    DashboardComponentIcon: { template: '<span />' },
    FilterControlShell: { template: '<div><slot /></div>' },
    'el-select': { template: '<div><slot /></div>' },
    'el-option': { template: '<div />' },
  },
}

describe('FilterSelectWidget runtime state', () => {
  it('keeps an explicitly cleared preview value empty instead of restoring the configured default', () => {
    const wrapper = mount(FilterSelectWidget, {
      props: {
        component: {
          id: 'metric-filter',
          type: 'filter',
          title: '转化指标名称',
          config: {
            field: 'metric_name',
            defaultValue: 'metric-default',
            optionSource: 'static',
            staticOptions: [{ label: '默认指标', value: 'metric-default' }],
          },
        } as any,
        modelValue: null as any,
      },
      global,
    })

    expect((wrapper.vm as any).selectedValue).toBe('')
  })
})
