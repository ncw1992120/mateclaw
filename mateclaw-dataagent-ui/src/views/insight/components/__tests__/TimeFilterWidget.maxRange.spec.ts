import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import TimeFilterWidget from '../TimeFilterWidget.vue'
import type { TimeFilterComponentConfig } from '@/types'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      insight: {
        timeRange: {
          today: '今天', '7d': '近7天', '30d': '近30天', '90d': '近90天', custom: '自定义',
          startPlaceholder: '开始日期', endPlaceholder: '结束日期',
        },
      },
    },
  },
  missingWarn: false,
  fallbackWarn: false,
})

const datePickerStub = {
  name: 'ElDatePicker',
  props: ['shortcuts', 'disabledDate'],
  emits: ['change', 'calendar-change', 'visible-change', 'update:modelValue'],
  template: '<div class="stub-date-picker"><span v-for="s in shortcuts" :key="s.key" class="shortcut">{{ s.text }}</span></div>',
}

const stubs = {
  FilterControlShell: { template: '<div class="stub-shell"><slot name="icon" /><slot /></div>' },
  DashboardComponentIcon: true,
  'el-date-picker': datePickerStub,
}

function mountWidget(config: TimeFilterComponentConfig) {
  return mount(TimeFilterWidget, {
    props: {
      component: {
        id: 'time-filter-1',
        type: 'timeFilter' as const,
        title: '时间筛选',
        position: { x: 0, y: 0, w: 4, h: 2 },
        config,
      },
    },
    global: { stubs, plugins: [i18n] },
  })
}

function picker(wrapper: ReturnType<typeof mountWidget>) {
  return wrapper.findComponent({ name: 'ElDatePicker' })
}

describe('TimeFilterWidget · 最大可选时间跨度', () => {
  it('maxRangeDays=30 时隐藏跨度超过 30 天的快捷选项', () => {
    const wrapper = mountWidget({ field: 'metric_time', maxRangeDays: 30 })

    const keys = picker(wrapper).props('shortcuts').map((s: { key: string }) => s.key)
    expect(keys).toEqual(['today', '7d', '30d'])
  })

  it('未配置 maxRangeDays 时保留全部快捷选项', () => {
    const wrapper = mountWidget({ field: 'metric_time' })

    const keys = picker(wrapper).props('shortcuts').map((s: { key: string }) => s.key)
    expect(keys).toEqual(['today', '7d', '30d', '90d'])
  })

  it('自定义选择超过最大跨度时将 end 收敛到 start+(N-1) 天', async () => {
    const wrapper = mountWidget({ field: 'metric_time', maxRangeDays: 30 })

    picker(wrapper).vm.$emit('change', ['2026-01-01', '2026-02-15'])
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('change')?.at(-1)?.[0]).toEqual({
      field: 'metric_time',
      timeRange: { preset: 'custom', start: '2026-01-01', end: '2026-01-30' },
    })
  })

  it('未配置 maxRangeDays 时自定义区间原样上报', async () => {
    const wrapper = mountWidget({ field: 'metric_time' })

    picker(wrapper).vm.$emit('change', ['2026-01-01', '2026-02-15'])
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('change')?.at(-1)?.[0]).toEqual({
      field: 'metric_time',
      timeRange: { preset: 'custom', start: '2026-01-01', end: '2026-02-15' },
    })
  })

  it('选中首日后把超窗日期置灰，关闭面板后解除', () => {
    const wrapper = mountWidget({ field: 'metric_time', maxRangeDays: 30 })
    const disabledDate = picker(wrapper).props('disabledDate') as (date: Date) => boolean

    expect(disabledDate(new Date(2026, 1, 10))).toBe(false)

    picker(wrapper).vm.$emit('calendar-change', [new Date(2026, 0, 1)])
    expect(disabledDate(new Date(2026, 0, 30))).toBe(false)
    expect(disabledDate(new Date(2026, 0, 31))).toBe(true)
    expect(disabledDate(new Date(2025, 11, 3))).toBe(false)
    expect(disabledDate(new Date(2025, 11, 2))).toBe(true)

    picker(wrapper).vm.$emit('visible-change', false)
    expect(disabledDate(new Date(2026, 1, 10))).toBe(false)
  })
})
