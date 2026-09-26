import { computed, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
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
    template: '<input data-testid="insight-color-field" :aria-label="label" :data-show-picker="String(showPicker ?? true)" :data-suggested-colors="JSON.stringify(suggestedColors)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)" />',
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
      finalResultQueryConfig: { confirmed: true },
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
    openPythonQueryConfig: vi.fn(),
    openPythonResultPreview: vi.fn(),
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

describe('AttributePanel', () => {
  it('在 Python 区域直接提供查询配置和查看数据入口', async () => {
    insightFixture.value.state.hasPython = true
    const wrapper = mountPanel()

    const buttons = wrapper.findAll('button')
    const queryButton = buttons.find((button) => button.text() === '查询配置')
    const dataButton = buttons.find((button) => button.text() === '查看数据')

    expect(queryButton).toBeTruthy()
    expect(dataButton).toBeTruthy()
    await queryButton!.trigger('click')
    await dataButton!.trigger('click')
    expect(insightFixture.value.openPythonQueryConfig).toHaveBeenCalledOnce()
    expect(insightFixture.value.openPythonResultPreview).toHaveBeenCalledOnce()
  })

  it('尚未添加 Python 脚本时不显示 Python 查询配置和查看数据入口', () => {
    const wrapper = mountPanel()

    expect(wrapper.text()).not.toContain('查询配置')
    expect(wrapper.text()).not.toContain('查看数据')
  })

  it('查询配置未确认时点击查看数据会提示并直接打开查询配置', async () => {
    insightFixture.value.state.hasPython = true
    insightFixture.value.state.finalResultQueryConfig = { confirmed: false }
    const warning = vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    const wrapper = mountPanel()

    await wrapper.findAll('button').find((button) => button.text() === '查看数据')!.trigger('click')

    expect(warning).toHaveBeenCalledWith('请先完成 Python 查询配置，再查看最终结果数据')
    expect(insightFixture.value.openPythonQueryConfig).toHaveBeenCalledOnce()
    expect(insightFixture.value.openPythonResultPreview).not.toHaveBeenCalled()
    warning.mockRestore()
  })

  it('does not render the result-set section or its preview and generation actions', () => {
    const wrapper = mountPanel()

    expect(wrapper.text()).not.toContain('结果集')
    expect(wrapper.text()).not.toContain('预览结果集')
    expect(wrapper.text()).not.toContain('生成结果集')
    expect(wrapper.text()).not.toContain('重新生成')
  })

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
    expect(componentColorField.attributes('data-show-picker')).toBe('true')

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
