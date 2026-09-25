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
  'el-input': { inheritAttrs: false, template: '<input />' },
  'el-date-picker': { inheritAttrs: false, template: '<input data-testid="python-query-date-input" />' },
  'el-switch': { inheritAttrs: false, props: ['disabled'], template: '<input type="checkbox" :disabled="disabled" />' },
}

beforeEach(() => {
  state.ui.preview = { visible: true, kind: 'result', datasetId: null, tab: 'data' }
  state.finalResultQueryConfig = undefined
  state.filterCatalog = []
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

  it('按绑定筛选器固定操作符，并将时间范围呈现为开始和结束两个日期条件', async () => {
    state.finalResultQueryConfig = {
      schemaFingerprint: 'test',
      confirmed: true,
      displayFields: [],
      filterFields: [
        { field: 'event_date', title: '发生日期', dataType: 'date', parameterName: 'event_date', operators: ['between'], filterComponentId: 'date-filter' },
      ],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }
    state.filterCatalog = [{ id: 'date-filter', title: '日期范围', type: 'timeFilter' }]

    const wrapper = mount(PythonResultDataDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="python-query-filter-operator"]').map((cell) => cell.text())).toEqual(['>=', '<'])
    expect(wrapper.findAll('[data-time-boundary="start"], [data-time-boundary="end"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-testid="python-query-date-input"]')).toHaveLength(2)
    expect(wrapper.find('el-select').exists()).toBe(false)
    expect(wrapper.text()).toContain('不包含结束时间')
    wrapper.unmount()
  })

  it('页面筛选器绑定失效时明确提示且不允许启用条件', async () => {
    state.finalResultQueryConfig = {
      schemaFingerprint: 'test',
      confirmed: true,
      displayFields: [],
      filterFields: [
        { field: 'status', title: '状态', dataType: 'string', parameterName: 'status', operators: ['eq'], filterComponentId: 'deleted-filter' },
      ],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }

    const wrapper = mount(PythonResultDataDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('绑定的页面筛选器已失效，请先重新绑定')
    expect(wrapper.find('input[type="checkbox"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
