import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MetricStyleDialog from '../MetricStyleDialog.vue'
import { defaultMetricStyles, KPI_FIELD_LABELS, KPI_METRIC_FIELDS } from '@/utils/kpi-metrics'
import { TEXT_COLOR_PRESETS } from '@/utils/color-presets'

const insightFixture = vi.hoisted(() => ({ value: null as any }))

vi.mock('../useInsight', () => ({
  useInsight: () => insightFixture.value,
}))

const colorFieldStub = {
  name: 'InsightColorField',
  props: ['modelValue', 'label', 'suggestedColors'],
  emits: ['update:modelValue', 'change'],
  template: '<input data-testid="insight-color-field" :aria-label="label" :data-suggested-colors="JSON.stringify(suggestedColors)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)" />',
}

function mountDialog() {
  return mount(MetricStyleDialog, {
    global: {
      stubs: {
        InsightColorField: colorFieldStub,
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
        'el-switch': { template: '<button type="button"><slot /></button>' },
        'el-icon': { template: '<span><slot /></span>' },
        'el-input-number': { template: '<span />' },
        'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
      },
    },
  })
}

beforeEach(() => {
  const metric = reactive({
    fieldKey: 'revenue',
    displayName: '收入',
    visual: { colorMode: 'theme' as const },
    styles: defaultMetricStyles(),
  })
  insightFixture.value = {
    state: {
      ui: { metricStyle: { visible: true, fieldKey: 'revenue', field: 'value' } },
      kpiMetrics: [metric],
    },
  }
})

describe('MetricStyleDialog shared color fields', () => {
  it('maps accent and each metric text color to their existing style fields', async () => {
    const wrapper = mountDialog()
    const metric = insightFixture.value.state.kpiMetrics[0]
    const fields = wrapper.findAll('[data-testid="insight-color-field"]')

    expect(fields).toHaveLength(KPI_METRIC_FIELDS.length + 1)
    expect(fields.map((field) => field.attributes('aria-label'))).toEqual([
      '强调色',
      ...KPI_METRIC_FIELDS.map((field) => `${KPI_FIELD_LABELS[field]}颜色`),
    ])
    for (const field of fields) {
      expect(field.attributes('data-suggested-colors')).toBe(JSON.stringify(TEXT_COLOR_PRESETS))
    }

    await fields[0].setValue('#123456')
    expect(metric.visual).toEqual({ colorMode: 'custom', accentColor: '#123456' })

    for (const [index, field] of KPI_METRIC_FIELDS.entries()) {
      await fields[index + 1].setValue(`#00000${index + 1}`)
      expect(metric.styles[field].color).toBe(`#00000${index + 1}`)
      expect(metric.styles[field].colorMode).toBe('custom')
    }
  })

  it('keeps theme defaults and reset behavior for accent and field colors', async () => {
    const wrapper = mountDialog()
    const metric = insightFixture.value.state.kpiMetrics[0]
    metric.visual = { colorMode: 'custom', accentColor: '#123456' }
    metric.styles.value.color = '#654321'
    metric.styles.value.colorMode = 'custom'

    await wrapper.findAll('button').find((button) => button.text() === '重置该指标')!.trigger('click')

    expect(metric.visual).toEqual({ iconKey: undefined, colorMode: 'theme' })
    expect(metric.styles).toEqual(defaultMetricStyles())
  })
})
