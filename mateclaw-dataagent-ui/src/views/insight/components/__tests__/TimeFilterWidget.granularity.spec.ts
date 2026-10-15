import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import TimeFilterWidget from '../TimeFilterWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { timeRange: { startPlaceholder: '开始日期', endPlaceholder: '结束日期' } } } },
  missingWarn: false,
  fallbackWarn: false,
})

const stubs = {
  FilterControlShell: { template: '<div><slot name="icon" /><slot /></div>' },
  DashboardComponentIcon: true,
  'el-date-picker': { name: 'ElDatePicker', template: '<div />' },
  'el-select': {
    name: 'ElSelect',
    props: ['modelValue'],
    template: '<select class="granularity-select" :value="modelValue" @change="$emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
}

function mountWidget(config: Record<string, unknown>) {
  return mount(TimeFilterWidget, {
    props: {
      component: {
        id: 'time-filter-1', type: 'timeFilter', title: '指标日期',
        position: { x: 0, y: 0, w: 4, h: 2 }, config,
      },
    },
    global: { stubs, plugins: [i18n] },
  })
}

describe('TimeFilterWidget 粒度', () => {
  it('未配置时展示粒度选择器并默认按 DAY 发出', async () => {
    const wrapper = mountWidget({ field: 'metric_time' })
    const selector = wrapper.find('.granularity-select')
    const granularitySelect = wrapper.findComponent({ name: 'ElSelect' })

    expect(selector.exists()).toBe(true)
    expect((selector.element as HTMLSelectElement).value).toBe('DAY')
    granularitySelect.vm.$emit('change', 'YEAR')
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('change')?.at(-1)?.[0]).toMatchObject({
      field: 'metric_time', timeGranularity: 'YEAR',
    })
  })

  it('隐藏选择器时不渲染控件，事件仍采用配置默认粒度', async () => {
    const wrapper = mountWidget({
      field: 'metric_time', showTimeGranularity: false, defaultTimeGranularity: 'QUARTER',
    })

    expect(wrapper.find('.granularity-select').exists()).toBe(false)
    await wrapper.findComponent({ name: 'ElDatePicker' }).vm.$emit('change', ['2026-01-01', '2026-03-31'])
    expect(wrapper.emitted('change')?.at(-1)?.[0]).toMatchObject({
      timeGranularity: 'QUARTER',
    })
  })
})
