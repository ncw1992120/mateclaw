import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const previewDatasetDraft = vi.fn(async () => ({
  rows: [{ trade_date: '2026-09-01' }] as Record<string, unknown>[],
  schema: ['trade_date'],
  rowCount: 1,
  last: false,
  totalCount: 120,
}))
const previewInput = vi.fn(async () => ({
  rows: [{ trade_date: '2026-09-01' }] as Record<string, unknown>[],
  schema: ['trade_date'],
  rowCount: 1,
  last: false,
  totalCount: 120,
}))

vi.mock('../card-attribute/useInsightBackend', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../card-attribute/useInsightBackend')>()
  return { ...actual, previewDatasetDraft: (...args: unknown[]) => previewDatasetDraft(...(args as [])) }
})

vi.mock('@/api/dataset', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dataset')>()
  return { ...actual, previewInput: (...args: unknown[]) => previewInput(...(args as [])) }
})

import DatasetDataDialog from '../DatasetDataDialog.vue'
import { clearAllCachedQueries } from '../dataset-data-cache'
import { useInsight } from '../card-attribute/useInsight'
import type { DatasetConfig } from '../card-attribute/useInsight'
import type { DatasetQueryConfig } from '@/types'

const { state } = useInsight()

function queryConfig(parameterBindings: DatasetQueryConfig['parameterBindings'] = []): DatasetQueryConfig {
  return {
    displayFields: [
      { field: 'trade_date', title: '交易日期', role: 'dimension' },
      { field: 'cust_type', title: '客户类型', role: 'dimension' },
      { field: 'amount', title: '金额', role: 'measure' },
    ],
    parameterBindings,
    sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
    paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
  }
}

const stubs = {
  'el-dialog': { template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>' },
  'el-input': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-input-number': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-date-picker': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-switch': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<input type="checkbox" :checked="modelValue" v-bind="$attrs" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />',
  },
  'el-button': { template: '<button type="button" :disabled="$attrs.disabled" v-bind="$attrs"><slot /></button>' },
  'el-table': {
    props: ['data'],
    emits: ['sort-change'],
    template: '<div class="stub-table"><button data-testid="sort-amount-desc" @click="$emit(\'sort-change\', { prop: \'amount\', order: \'descending\' })">模拟排序</button><slot /></div>',
  },
  'el-table-column': {
    props: ['prop', 'label', 'sortable'],
    template: '<div class="stub-table-column" :data-prop="prop" :data-label="label" :data-sortable="String(sortable)" />',
  },
  'el-empty': { props: ['description'], template: '<div class="stub-empty">{{ description }}</div>' },
}

/** Aloudata 指标视图数据集：筛选字段来自视图维度 */
function metricViewDataset(partial: Partial<DatasetConfig> = {}): DatasetConfig {
  return {
    id: 'ds-1',
    sourceType: 'aloudata',
    sourceLabel: 'Aloudata',
    alias: 'cljd_zcl_zb_view',
    fields: [],
    filters: [],
    aloudata: {
      mode: 'metric-view',
      datasourceId: '2097582897597468673',
      metricView: 'cljd_zcl_zb_view',
      metrics: [],
      dims: [],
    },
    queryConfig: queryConfig([
      { filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' },
    ]),
    ...partial,
  } as DatasetConfig
}

/** 打开弹窗：visible 初值即 true，组件 mount 时就会走初始化 */
async function openWith(dataset: DatasetConfig) {
  state.ui.dataDialog = { visible: true, datasetId: dataset.id }
  const wrapper = mount(DatasetDataDialog, { props: { dataset }, global: { stubs }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  state.ui.dataDialog = { visible: false, datasetId: '' }
  state.filterBindings = []
  state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', selectionMode: 'single' }]
  clearAllCachedQueries()
  previewDatasetDraft.mockClear()
  previewInput.mockClear()
})

describe('查看数据弹窗 · 保留最近一次执行结果', () => {
  async function runQuery(wrapper: ReturnType<typeof mount>) {
    await wrapper.find('[data-testid="query-filter-row"] input.dd-value').setValue('2026-09-01')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
  }

  it('关掉再打开：条件和结果都还在，且不重新发请求', async () => {
    const dataset = metricViewDataset()
    const first = await openWith(dataset)
    await runQuery(first)
    const callsAfterQuery = previewDatasetDraft.mock.calls.length
    expect((first.find('.dd-result .dd-hint').text())).toContain('1 行')
    first.unmount()

    // 重新打开：不重新查询，直接恢复上次结果
    const again = await openWith(dataset)
    expect(previewDatasetDraft.mock.calls.length).toBe(callsAfterQuery)
    expect(again.find('.dd-result .dd-hint').text()).toContain('1 行')
    expect(again.find('.dd-result .dd-hint').text()).toContain('查询')
    // 会话缓存恢复运行时筛选值，且结果仍对应这份条件。
    expect(again.find('[data-testid="query-filter-row"] input.dd-value').element.value).toBe('2026-09-01')
    expect(again.find('.dd-result .dd-hint').text()).not.toContain('条件已变更')
    again.unmount()
  })

  it('条件改过之后：缓存结果照常展示，但标注「条件已变更」提醒重查', async () => {
    const dataset = metricViewDataset()
    const first = await openWith(dataset)
    await runQuery(first)
    first.unmount()

    // 用户在查询配置中调整绑定字段
    dataset.queryConfig!.parameterBindings[0].field = 'cust_type'
    const again = await openWith(dataset)
    expect(again.findAll('.dd-result-body .stub-table')).toHaveLength(1) // 结果仍展示，不清空
    expect(again.find('.dd-result .dd-hint').text()).toContain('条件已变更')
    // 条件区展示的是新绑定，而不是产生缓存结果的那份
    expect(again.find('[data-testid="query-filter-row"]').text()).toContain('cust_type')
    again.unmount()
  })

  it('本地 ds- 临时 ID 不调用 Long 主键接口，而回退草稿预览', async () => {
    const dataset = metricViewDataset({ backendDatasetId: 'ds-1789983261389' })
    const wrapper = await openWith(dataset)
    await runQuery(wrapper)

    expect(previewInput).not.toHaveBeenCalled()
    expect(previewDatasetDraft).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('没有缓存时维持空态，不假装查过', async () => {
    const wrapper = await openWith(metricViewDataset({ id: 'ds-fresh' }))
    expect(wrapper.find('.dd-result .dd-hint').text()).toBe('尚未查询')
    expect(wrapper.findAll('.dd-result-body .stub-table')).toHaveLength(0)
    wrapper.unmount()
  })
})

describe('查看数据弹窗 · 打开时的行为', () => {
  it('打开后不自动取数，先让用户配条件', async () => {
    const wrapper = await openWith(metricViewDataset())

    expect(previewDatasetDraft).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('点「查询」获取数据')
    expect(wrapper.find('.dd-result-body').exists()).toBe(true)
  })

  it('展示字段来自查询配置且只读，不再展示数据源定义字段', async () => {
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        displayFields: [
          { field: 'cust_type', title: '客户类型', role: 'dimension' },
          { field: 'amount', title: '金额', role: 'measure' },
        ],
        parameterBindings: [],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
    }))

    const table = wrapper.find('[data-testid="display-fields-table"]')
    expect(table.exists()).toBe(true)
    expect(table.find('thead').text()).toContain('字段类型')
    expect(table.find('thead').text()).toContain('字段名')
    expect(table.find('thead').text()).toContain('展示名')
    expect(table.findAll('tbody tr')).toHaveLength(2)
    expect(table.text()).toContain('cust_type')
    expect(table.text()).toContain('客户类型')
    expect(table.text()).toContain('金额')
    expect(wrapper.find('.dd-kv').exists()).toBe(false)
  })

  it('结果表头使用查询配置中的展示名，排序只开放已配置字段', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [{ trade_date: '2026-09-01', amount: 18 }],
      schema: ['trade_date', 'amount'],
      rowCount: 1,
      last: true,
    })
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        ...queryConfig([{ filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' }]),
        sortPolicy: { enabled: true, mode: 'single', allowedFields: ['amount'], defaultSort: null },
      },
    }))
    await wrapper.find('input.dd-value').setValue('2026-09-01')
    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()

    const columns = wrapper.findAll('.stub-table-column')
    expect(columns.find((column) => column.attributes('data-prop') === 'trade_date')?.attributes('data-label')).toBe('交易日期')
    expect(columns.find((column) => column.attributes('data-prop') === 'amount')?.attributes('data-label')).toBe('金额')
    expect(columns.find((column) => column.attributes('data-prop') === 'amount')?.attributes('data-sortable')).toBe('custom')
    expect(columns.find((column) => column.attributes('data-prop') === 'trade_date')?.attributes('data-sortable')).not.toBe('true')
    wrapper.unmount()
  })

  it('首次打开时直接展示已保存查询配置字段；未配置时显示前往配置的明确提示', async () => {
    const configured = await openWith(metricViewDataset())
    expect(configured.findAll('[data-testid="display-field-row"]')).toHaveLength(3)
    configured.unmount()

    const unconfigured = await openWith(metricViewDataset({
      id: 'ds-no-display-fields',
      queryConfig: { ...queryConfig([]), displayFields: [] },
    }))
    expect(unconfigured.find('.dd-display-empty').text()).toContain('请先在查询配置中选择字段')
    unconfigured.unmount()
  })

  it('没有筛选器绑定时仍可无条件查询', async () => {
    const wrapper = await openWith(metricViewDataset({
      queryConfig: queryConfig([]),
    }))

    expect(wrapper.find('.dd-empty').text()).toContain('未绑定筛选器，本次查询将不附加筛选条件')
    expect(wrapper.find('.dd-query-row button').attributes('disabled')).toBeUndefined()
    await wrapper.find('.dd-query-row button').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({ filters: [], parameters: {} })
    wrapper.unmount()
  })

  it('已绑定筛选条件但值为空或全部禁用时仍可无条件查询', async () => {
    const wrapper = await openWith(metricViewDataset())
    const queryButton = wrapper.find('.dd-query-row button')
    expect(queryButton.attributes('disabled')).toBeUndefined()

    await queryButton.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({ filters: [], parameters: {} })

    await wrapper.find('[data-testid="query-filter-enabled"]').setValue(false)
    expect(queryButton.attributes('disabled')).toBeUndefined()
    expect((wrapper.find('[data-testid="query-filter-row"] input.dd-value').element as HTMLInputElement).value).toBe('')
    wrapper.unmount()
  })

  it('查询配置开启分页后按当前页码、每页条数和允许排序字段发起服务端查询', async () => {
    const dataset = metricViewDataset({ backendDatasetId: '123' })
    dataset.queryConfig = {
      ...queryConfig([{ filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' }]),
      sortPolicy: { enabled: true, mode: 'single', allowedFields: ['amount'], defaultSort: null },
      paginationPolicy: { enabled: true, defaultPageSize: 2, maxPageSize: 5, returnTotalCount: true },
    }
    const wrapper = await openWith(dataset)
    await wrapper.find('input.dd-value').setValue('2026-09-01')
    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { limit?: number; offset?: number; requestTotalCount?: boolean }).limit).toBe(2)
    expect((previewInput.mock.calls.at(-1)?.[0] as { offset?: number }).offset).toBe(0)
    expect((previewInput.mock.calls.at(-1)?.[0] as { requestTotalCount?: boolean }).requestTotalCount).toBe(true)

    await wrapper.find('[data-testid="sort-amount-desc"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { orders?: unknown[] }).orders).toEqual([{ field: 'amount', direction: 'desc' }])
    expect((previewInput.mock.calls.at(-1)?.[0] as { offset?: number }).offset).toBe(0)

    await wrapper.find('[data-testid="page-next"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { limit?: number; offset?: number }).limit).toBe(2)
    expect((previewInput.mock.calls.at(-1)?.[0] as { offset?: number }).offset).toBe(2)
    expect(wrapper.find('[data-testid="pagination-status"]').text()).toContain('第 2 页')
    wrapper.unmount()

    const callsAfterPageChange = previewInput.mock.calls.length
    const reopened = await openWith(dataset)
    expect(previewInput.mock.calls.length).toBe(callsAfterPageChange)
    expect(reopened.find('[data-testid="pagination-status"]').text()).toContain('第 2 页')
    expect(reopened.find('.dd-result .dd-hint').text()).not.toContain('条件已变更')
    reopened.unmount()
  })

  it('查询配置筛选器绑定是唯一条件来源，操作符只读且默认启用', async () => {
    state.filterCatalog = [{ id: 'filter-strategy', title: '策略类型', type: 'filter', selectionMode: 'single' }]
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        displayFields: [{ field: 'strategy_id', title: '策略编号', role: 'dimension' }],
        parameterBindings: [{ filterComponentId: 'filter-strategy', parameterName: 'strategy', field: 'strategy_id', operator: 'eq' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
    }))

    expect(wrapper.findAll('[data-testid="query-filter-row"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="query-filter-row"]').text()).toContain('策略类型')
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).toContain('筛选器')
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).toContain('映射字段')
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).toContain('操作符')
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).toContain('本次查询值')
    expect(wrapper.find('[data-testid="query-filter-operator"]').text()).toContain('等于')
    expect(wrapper.find('[data-testid="query-filter-operator"] select').exists()).toBe(false)
    expect(wrapper.find('[data-testid="query-filter-enabled"]').element.checked).toBe(true)
    expect(wrapper.findAll('[data-testid="query-filter-row"] button')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('添加筛选条件')
    expect(wrapper.find('select.dd-field').exists()).toBe(false)
    expect(wrapper.find('.dd-note').exists()).toBe(false)
  })

  it('禁用唯一绑定筛选器时清空并锁定值，且可以无条件查询', async () => {
    state.filterCatalog = [{ id: 'filter-strategy', title: '策略类型', type: 'filter', selectionMode: 'single' }]
    const dataset = metricViewDataset({
      queryConfig: {
        displayFields: [{ field: 'strategy_id', title: '策略编号', role: 'dimension' }],
        parameterBindings: [{ filterComponentId: 'filter-strategy', parameterName: 'strategy', field: 'strategy_id', operator: 'eq' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
    })
    const wrapper = await openWith(dataset)
    const row = wrapper.find('[data-testid="query-filter-row"]')
    const value = row.find('input.dd-value')
    await value.setValue('strategy-a')
    await row.find('[data-testid="query-filter-enabled"]').setValue(false)

    expect(value.element.value).toBe('')
    expect(value.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.dd-query-row button').attributes('disabled')).toBeUndefined()
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({ filters: [] })
  })

  it('JDBC SQL 在查看数据时只读，SQL 参数仍可编辑并进入 parameters', async () => {
    const wrapper = await openWith(metricViewDataset({
      sourceType: 'jdbc',
      aloudata: undefined,
      jdbc: { db: '1', sql: 'select * from orders where region = :region' },
      fields: [],
    }))

    expect(wrapper.find('[data-testid="dataset-sql-readonly"]').text()).toContain('select * from orders')
    expect(wrapper.find('textarea.dd-code').exists()).toBe(false)
    await wrapper.find('.dd-param input').setValue('华东')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { parameters?: unknown }).parameters).toEqual({ region: '华东' })
  })
})

describe('查看数据弹窗 · 查询配置筛选条件', () => {
  it('启用的绑定值转换为数据集过滤条件和参数，不修改保存的查询配置', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '交易日', type: 'filter', selectionMode: 'single' }]
    const dataset = metricViewDataset()
    const wrapper = await openWith(dataset)
    await wrapper.find('[data-testid="query-filter-row"] input.dd-value').setValue('2026-09-01')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()

    const request = previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[]; parameters?: unknown; offset?: number }
    expect(request.filters).toEqual([{ field: 'trade_date', operator: 'gte', value: '2026-09-01', role: 'dimension' }])
    expect(request.parameters).toEqual({ date: '2026-09-01' })
    expect(request.offset).toBe(0)
    expect(dataset.queryConfig?.parameterBindings[0].field).toBe('trade_date')
    expect(dataset.filters).toEqual([])
  })

  it('时间筛选器以开始和结束两行展示，并提交包前不包后的过滤条件', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', selectionMode: 'single' }]
    const wrapper = await openWith(metricViewDataset())

    const startRow = wrapper.find('[data-time-boundary="start"]')
    const endRow = wrapper.find('[data-time-boundary="end"]')
    expect(startRow.exists()).toBe(true)
    expect(endRow.exists()).toBe(true)
    expect(startRow.text()).toContain('开始时间')
    expect(startRow.find('[data-testid="query-filter-operator"]').text()).toContain('大于等于')
    expect(startRow.find('input.dd-value').attributes('aria-label')).toContain('开始时间')
    expect(endRow.text()).toContain('结束时间')
    expect(endRow.find('[data-testid="query-filter-operator"]').text()).toContain('小于')
    expect(endRow.find('input.dd-value').attributes('placeholder')).toContain('不包含')
    expect(wrapper.text()).toContain('包含开始时间，不包含结束时间')

    await startRow.find('input.dd-value').setValue('2026-09-01')
    await endRow.find('input.dd-value').setValue('2026-10-01')
    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()

    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[] }).filters).toEqual([
      { field: 'trade_date', operator: 'gte', value: '2026-09-01', role: 'dimension' },
      { field: 'trade_date', operator: 'lt', value: '2026-10-01', role: 'dimension' },
    ])
  })

  it('开始和结束条件独立启停；禁用结束时间只提交开始边界', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', selectionMode: 'single' }]
    const wrapper = await openWith(metricViewDataset())
    const startRow = wrapper.find('[data-time-boundary="start"]')
    const endRow = wrapper.find('[data-time-boundary="end"]')
    await startRow.find('input.dd-value').setValue('2026-09-01')
    await endRow.find('input.dd-value').setValue('2026-10-01')
    await endRow.find('[data-testid="query-filter-enabled"]').setValue(false)

    expect(startRow.find('[data-testid="query-filter-enabled"]').element.checked).toBe(true)
    expect(endRow.find('[data-testid="query-filter-enabled"]').element.checked).toBe(false)
    expect(endRow.find('input.dd-value').element.value).toBe('')
    expect(endRow.find('input.dd-value').attributes('disabled')).toBeDefined()

    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[] }).filters).toEqual([
      { field: 'trade_date', operator: 'gte', value: '2026-09-01', role: 'dimension' },
    ])
  })

  it('不再读取旧 dataset.filters 或全局 filterBindings，也不允许手工新增条件', async () => {
    state.filterBindings = [{
      filterName: '旧绑定', scope: { 'ds-1': true }, fieldMap: [], conditions: [],
    }] as never
    const dataset = metricViewDataset({
      filters: [{ field: 'cust_type', op: '=', value: '旧值' }] as never,
      queryConfig: queryConfig([]),
    })
    const wrapper = await openWith(dataset)

    expect(wrapper.findAll('[data-testid="query-filter-row"]')).toHaveLength(0)
    expect(wrapper.find('.dd-empty').text()).toContain('未绑定筛选器，本次查询将不附加筛选条件')
    expect(wrapper.text()).not.toContain('添加筛选条件')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({ filters: [] })
  })
})

describe('查看数据弹窗 · 按数据源类型给不同的筛选项', () => {
  it('文件类型不显示筛选条件（上游不做下推，避免能配但不生效）', async () => {
    const wrapper = await openWith(
      metricViewDataset({
        sourceType: 'file',
        file: { objectId: 'object-1', fileName: 'a.csv', fileType: 'csv', columns: [{ name: 'c1', type: 'string' }], rows: [] },
        aloudata: undefined,
      }),
    )

    expect(wrapper.text()).not.toContain('添加筛选条件')
    expect(wrapper.find('.dd-conditions').exists()).toBe(false)
    expect(wrapper.find('[data-testid="run-query"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
  })

  it('SQL 的 :param 作为参数区自动提取，与筛选条件是两条通道', async () => {
    const wrapper = await openWith(
      metricViewDataset({
        sourceType: 'jdbc',
        aloudata: undefined,
        jdbc: { db: '1', sql: 'select * from t where region = :region' },
        fields: [],
      }),
    )

    expect(wrapper.text()).toContain(':region')
    expect(wrapper.text()).toContain('从定义自动提取')

    // 占位符未填时没有有效查询条件；填写后走 parameters（不是 filters）
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).not.toHaveBeenCalled()

    await wrapper.find('.dd-param input').setValue('华东')
    expect(wrapper.find('.dd-query-row button').attributes('disabled')).toBeUndefined()
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    const request = previewDatasetDraft.mock.calls[0][0] as { parameters?: unknown; filters?: unknown }
    expect(request.parameters).toEqual({ region: '华东' })
    expect(request.filters).toEqual([])
  })
})
