import { computed, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AttributePanel from '../AttributePanel.vue'
import { CARD_BG_PRESETS } from '@/utils/color-presets'

const insightFixture = vi.hoisted(() => ({ value: null as any }))

vi.mock('../useInsight', () => ({
  useInsight: () => insightFixture.value,
}))

const colorFieldStub = {
  name: 'InsightColorField',
  props: ['modelValue', 'label', 'suggestedColors', 'showPicker'],
  emits: ['update:modelValue', 'change'],
    template: '<input data-testid="insight-color-field" :aria-label="label" :data-show-picker="String(showPicker)" :data-suggested-colors="JSON.stringify(suggestedColors)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)" />',
}

function createCard() {
  return reactive({
    id: 'legacy-chart',
    type: 'chart',
    title: 'Legacy chart',
    chartType: 'line',
    titleBarStyle: 'standard',
    visualStyle: {
      border: { mode: 'visible', colorMode: 'custom', color: '#112233', width: 1, style: 'solid' },
      background: { mode: 'custom', color: '#445566' },
      radius: 8,
      shadow: 'none',
      padding: 8,
    },
  })
}

function mountPanel() {
  return mount(AttributePanel, {
    global: {
      stubs: {
        InsightColorField: colorFieldStub,
        'el-input': { template: '<input v-bind="$attrs" />' },
        'el-select': {
          props: ['modelValue'],
          template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
        },
        'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-switch': { template: '<input type="checkbox" />' },
        'el-alert': { template: '<div><slot /></div>' },
        'el-color-picker': { template: '<input type="color" />' },
        InlineHelp: true,
        DatasetCard: true,
        ComponentSampleDialog: true,
      },
    },
  })
}

beforeEach(() => {
  const card = createCard()
  insightFixture.value = {
    state: {
      cards: [card],
      datasets: [],
      kpiMetrics: [],
      hasPython: false,
      pythonSystem: '',
      pythonUser: '',
      resultSet: {
        status: 'empty', rowCount: 0, columns: [], rows: [], error: '',
        source: 'dataset', generatedAt: '', elapsedMs: 0,
      },
    },
    activeCard: computed(() => card),
    isKpiCard: false,
    datasetCount: 0,
    pythonRequired: false,
    openDataSourceTree: vi.fn(),
    openPython: vi.fn(),
    openPreview: vi.fn(),
    removePython: vi.fn(),
    openMetricConfig: vi.fn(),
    resultSetStale: false,
    resultSetAuto: true,
    resultSetSourceLabel: '数据集直通',
    resultSetHasOutput: false,
    generateResultSet: vi.fn(),
  }
})

describe('AttributePanel shared color field', () => {
  it('writes selected colors directly to the active card without changing mode or save flow', async () => {
    const wrapper = mountPanel()
    const fields = wrapper.findAll('[data-testid="insight-color-field"]')
    const card = insightFixture.value.activeCard.value

    expect(fields).toHaveLength(3)
    const borderField = fields.find(field => field.attributes('aria-label') === '自定义边框颜色')!
    const backgroundField = fields.find(field => field.attributes('aria-label') === '自定义背景色')!
    const componentColorField = fields.find(field => field.attributes('aria-label') === '组件配色')!
    expect(borderField.attributes('data-suggested-colors')).not.toBe(JSON.stringify(CARD_BG_PRESETS))
    expect(backgroundField.attributes('data-suggested-colors')).toBe(JSON.stringify(CARD_BG_PRESETS))
    expect(componentColorField.attributes('data-suggested-colors')).toBeTruthy()
    expect(componentColorField.attributes('data-show-picker')).toBe('false')

    await backgroundField.setValue('#AABBCC')
    expect(card.visualStyle.background).toEqual({ mode: 'custom', color: '#AABBCC' })
    expect(card.visualStyle.border).toEqual({ mode: 'visible', colorMode: 'custom', color: '#112233', width: 1, style: 'solid' })
  })

  it('retains theme and transparent mode choices on the active card', async () => {
    const wrapper = mountPanel()
    const card = insightFixture.value.activeCard.value

    await wrapper.get('select[aria-label="组件背景"]').setValue('transparent')
    expect(card.visualStyle.background.mode).toBe('transparent')
    await wrapper.get('select[aria-label="组件边框"]').setValue('theme')
    expect(card.visualStyle.border.mode).toBe('theme')
    expect(wrapper.find('[data-testid="insight-color-field"]').exists()).toBe(true)
  })
})
