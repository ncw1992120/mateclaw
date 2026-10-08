import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import PythonResultDataDialog from '../PythonResultDataDialog.vue'
import { useInsight } from '../useInsight'

const { state, previewState } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /></div>' },
  'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-table': { template: '<div class="result-table"><slot /></div>' },
  'el-table-column': { props: ['prop', 'label'], template: '<div class="result-column" :data-prop="prop" :data-label="label" />' },
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
  ComponentDataPreview: { template: '<div data-testid="component-render-preview">组件预览 <slot /></div>' },
}

beforeEach(() => {
  state.ui.preview = { visible: true, kind: 'result', datasetId: null, tab: 'data' }
  state.finalResultQueryConfig = undefined
  state.filterCatalog = []
  state.kpiMetrics = []
  Object.assign(state.resultSet, {
    status: 'ready', source: 'script', columns: [{ name: 'result', type: 'string' }],
    rows: [{ result: 'rendered' }], rowCount: 1, elapsedMs: 1, executionId: '', error: '',
  })
  previewState.loading = false
  previewState.error = ''
  previewState.payload = null
})

describe('PythonResultDataDialog', () => {
  it('查看数据展示全部输出列，并允许在结果区配置展示名', async () => {
    Object.assign(state.resultSet, {
      columns: [
        { name: 'amount', type: 'number' },
        { name: 'region', type: 'string' },
      ],
      rows: [{ amount: 10, region: '华东' }],
      rowCount: 1,
    })
  state.finalResultQueryConfig = {
      schemaFingerprint: 'test',
      confirmed: true,
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }

    const wrapper = mount(PythonResultDataDialog, { global: { stubs } })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="python-display-fields-table"] tbody tr')).toHaveLength(2)
    expect(wrapper.findAll('[data-testid="python-display-fields-table"] tbody tr').map((row) => row.findAll('td')[1].text()))
      .toEqual(['amount', 'region'])
    expect(wrapper.findAll('.result-table .result-column').map((column) => column.attributes('data-label')))
      .toEqual(['amount', '区域'])
    await wrapper.findAll('.dd-display-table input')[0].setValue('统计金额')
    expect(state.finalResultQueryConfig?.displayFields.find((field) => field.field === 'amount')?.title).toBe('统计金额')
    expect(wrapper.find('.result-column[data-prop="amount"]').attributes('data-label')).toBe('统计金额')
    wrapper.unmount()
  })

  it('Python 数值输出列应按指标保存，不能沿用旧的维度分类', async () => {
    Object.assign(state.resultSet, {
      columns: [{ name: '转化规模', type: 'number' }, { name: '转化人数', type: 'number' }],
      rows: [{ 转化规模: 27948000, 转化人数: 2964 }],
      rowCount: 1,
    })
    state.finalResultQueryConfig = {
      schemaFingerprint: 'old-input-schema',
      confirmed: true,
      displayFields: [
        { field: '转化规模', title: '转化规模', role: 'dimension' },
        { field: '转化人数', title: '转化人数', role: 'dimension' },
      ],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }
    state.cards = [{ id: 'strategy-kpi', type: 'kpi', title: '策略贡献' } as any]
    state.activeCardId = 'strategy-kpi'

    const wrapper = mount(PythonResultDataDialog, {
      props: { component: { id: 'strategy-kpi', type: 'kpi', title: '策略贡献' } as any },
      global: { stubs },
    })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="python-display-fields-table"] tbody tr').map((row) => row.find('td').text()))
      .toEqual(['指标', '指标'])
    expect(state.finalResultQueryConfig?.displayFields.map(({ role }) => role)).toEqual(['measure', 'measure'])
    expect(state.kpiMetrics.map(({ fieldKey }) => fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(state.resultSet.columns).toEqual([
      { name: '转化规模', type: 'number' },
      { name: '转化人数', type: 'number' },
    ])
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

  it('查询后点击组件渲染，将 Python 结果渲染到画布并展示预览', async () => {
    const component = { id: 'table-1', type: 'table', title: '结果表' } as any
    Object.assign(state.resultSet, {
      columns: [{ name: 'result', type: 'string' }, { name: 'internal_note', type: 'string' }],
      rows: [{ result: '保留行', internal_note: '不展示' }],
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
    await wrapper.find('.dd-filter-table input:not([type="checkbox"])').setValue('保留行')
    await wrapper.find('input[type="checkbox"]').setValue(true)

    await wrapper.get('[data-testid="python-run-query"]').trigger('click')

    expect(wrapper.emitted('resultset')).toBeUndefined()
    expect(wrapper.find('[data-testid="component-render-preview"]').exists()).toBe(false)
    await wrapper.get('[data-testid="python-component-render"]').trigger('click')

    expect(wrapper.find('[data-testid="component-render-preview"]').exists()).toBe(true)
    expect(wrapper.emitted('render')?.[0]?.[0]).toMatchObject({
      componentId: 'table-1',
      renderType: 'table',
      fieldLabels: { result: '结果' },
      table: { columns: ['result', 'internal_note'], rows: [['保留行', '不展示']] },
      pythonAppliedResultView: {
        filters: [{ field: 'result', op: '=', value: '保留行' }],
        sort: null,
      },
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
