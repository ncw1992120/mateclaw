import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import PreviewDialog from '../PreviewDialog.vue'
import { useInsight } from '../useInsight'

const { state, previewState } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-checkbox': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
  },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button" v-bind="$attrs"><slot /></button>' },
  'el-table': { props: ['data'], template: '<div class="stub-table">{{ JSON.stringify(data) }}</div>' },
  'el-table-column': { template: '<div />' },
  'el-tooltip': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div />' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' },
}

beforeEach(() => {
  state.ui.preview = { visible: true, kind: 'result', datasetId: '', tab: 'data', showFieldNames: false }
  previewState.loading = false
  previewState.error = ''
  previewState.payload = {
    dataColumns: [{ name: 'metric_id', type: 'string' }, { name: 'conversion', type: 'int' }],
    dataRows: [
      { metric_id: 'm1', conversion: 10 },
      { metric_id: 'm2', conversion: 20 },
    ],
    fieldStruct: [],
    execInfo: { inputDatasets: [], queryConditions: '', pushedFilters: [], scanned: '-', returned: '2', pythonTime: '0.1s', inRows: 2, outRows: 2, error: '' },
    logs: [],
  }
})

describe('Python 最终结果查看', () => {
  it('添加筛选条件并查询后，只展示 Python 输出中的匹配行', async () => {
    const wrapper = mount(PreviewDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="result-filter-panel"]').text()).toContain('Python 脚本处理后的最终结果')
    await wrapper.find('[data-testid="add-result-filter"]').trigger('click')
    await wrapper.find('select.result-filter-field').setValue('conversion')
    await wrapper.find('input.result-filter-value').setValue('20')
    await wrapper.find('[data-testid="query-result-filter"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('.stub-table').text()).toContain('"metric_id":"m2"')
    expect(wrapper.find('.stub-table').text()).not.toContain('"metric_id":"m1"')
    wrapper.unmount()
  })
})
