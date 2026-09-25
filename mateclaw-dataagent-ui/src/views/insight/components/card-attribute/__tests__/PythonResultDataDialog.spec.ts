import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import PythonResultDataDialog from '../PythonResultDataDialog.vue'
import { useInsight } from '../useInsight'

const { state, previewState } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /></div>' },
  'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
  'el-table': { template: '<div class="result-table" />' },
  'el-table-column': { template: '<div />' },
}

beforeEach(() => {
  state.ui.preview = { visible: true, kind: 'result', datasetId: null, tab: 'data' }
  Object.assign(state.resultSet, {
    status: 'ready', source: 'script', columns: [{ name: 'result', type: 'string' }],
    rows: [{ result: 'rendered' }], rowCount: 1, elapsedMs: 1, executionId: 'execution-1', error: '',
  })
  previewState.loading = false
  previewState.error = ''
  previewState.payload = null
})

describe('PythonResultDataDialog', () => {
  it('没有筛选字段时允许直接查看全部 Python 输出', async () => {
    const wrapper = mount(PythonResultDataDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无筛选字段；本次将展示全部 Python 输出。')
    expect(wrapper.get('[data-testid="python-run-query"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="python-run-query"]').trigger('click')
    expect(wrapper.find('.result-table').exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads the existing result when mounted while already visible', async () => {
    const wrapper = mount(PythonResultDataDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('.result-table').exists()).toBe(true)
    expect(previewState.payload?.dataRows).toEqual([{ result: 'rendered' }])
    wrapper.unmount()
  })
})
