import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import PythonResultDataDialog from '../PythonResultDataDialog.vue'
import { useInsight } from '../useInsight'

const { state, previewState } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /></div>' },
  'el-tabs': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div><button data-testid="show-component-result" @click="$emit(\'update:modelValue\', \'component\')">组件预览</button><slot /></div>',
  },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-table': { template: '<div class="result-table" />' },
  'el-table-column': { template: '<div />' },
  'el-input': {
    inheritAttrs: false,
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-date-picker': { inheritAttrs: false, template: '<input data-testid="python-query-date-input" />' },
  'el-switch': {
    inheritAttrs: false,
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue'],
    template: '<input type="checkbox" :checked="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
  },
}

beforeEach(() => {
  state.ui.preview = { visible: true, kind: 'result', datasetId: null, tab: 'data' }
  state.finalResultQueryConfig = undefined
  state.filterCatalog = []
  Object.assign(state.resultSet, {
    status: 'ready', source: 'script', columns: [{ name: 'result', type: 'string' }],
    rows: [{ result: 'rendered' }], rowCount: 1, elapsedMs: 1, executionId: '', error: '',
  })
  previewState.loading = false
  previewState.error = ''
  previewState.payload = null
})

describe('PythonResultDataDialog', () => {
  it('组件预览把当前 Python 输出交给画布中的组件，而不在弹窗内创建独立组件', async () => {
    const wrapper = mount(PythonResultDataDialog, {
      props: { component: { id: 'table-python', type: 'table', title: 'Python 结果' } as never },
      global: { stubs },
    })
    await flushPromises()

    await wrapper.get('[data-testid="show-component-result"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('render')).toHaveLength(1)
    expect(wrapper.emitted('render')?.[0]?.[0]).toMatchObject({
      componentId: 'table-python',
      renderType: 'table',
      table: { columns: ['result'], rows: [['rendered']] },
    })
    expect(wrapper.find('[data-testid="component-render-preview"]').exists()).toBe(false)
    wrapper.unmount()
  })

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

  it('点击查询后将 Python 查询结果推送到当前组件', async () => {
    const component = { id: 'table-1', type: 'table', title: '结果表' } as any
    Object.assign(state.resultSet, {
      columns: [{ name: 'result', type: 'string' }, { name: 'internal_note', type: 'string' }],
      rows: [
        { result: '保留行', internal_note: '不展示' },
        { result: '过滤行', internal_note: '不展示' },
      ],
      rowCount: 2,
    })
    state.finalResultQueryConfig = {
      schemaFingerprint: 'test',
      confirmed: true,
      displayFields: [{ field: 'result', title: '结果', role: 'dimension' }],
      filterFields: [
        { field: 'result', title: '结果', dataType: 'string', parameterName: 'result', operators: ['eq'], filterComponentId: 'result-filter' },
      ],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }
    state.filterCatalog = [{ id: 'result-filter', title: '结果筛选器', type: 'filter', selectionMode: 'single' }]
    const wrapper = mount(PythonResultDataDialog, { props: { component }, global: { stubs } })
    await flushPromises()
    await wrapper.find('input:not([type="checkbox"])').setValue('保留行')
    await wrapper.find('input[type="checkbox"]').setValue(true)

    await wrapper.get('[data-testid="python-run-query"]').trigger('click')

    expect(wrapper.emitted('resultset')?.[0]?.[0]).toMatchObject({
      componentId: 'table-1',
      status: 'ready',
      source: 'script',
      rows: [{ result: '保留行' }],
      fieldLabels: { result: '结果' },
    })
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
