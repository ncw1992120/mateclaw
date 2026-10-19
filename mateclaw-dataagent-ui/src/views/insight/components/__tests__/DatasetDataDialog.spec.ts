import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
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
const listDatasets = vi.fn(async () => [{ id: '123' }])

vi.mock('../card-attribute/useInsightBackend', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../card-attribute/useInsightBackend')>()
  return { ...actual, previewDatasetDraft: (...args: unknown[]) => previewDatasetDraft(...(args as [])) }
})

vi.mock('@/api/dataset', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dataset')>()
  return {
    ...actual,
    list: (...args: unknown[]) => listDatasets(...(args as [])),
    previewInput: (...args: unknown[]) => previewInput(...(args as [])),
  }
})

import DatasetDataDialog from '../DatasetDataDialog.vue'
import * as componentPreviewUtils from '@/utils/component-preview-data'
import { clearAllCachedQueries, getCachedQuery, setCachedQuery } from '../dataset-data-cache'
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
  ViewDataFilterSelect: {
    props: ['modelValue'],
    template: '<input class="dd-value" :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
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
  'el-select': {
    props: ['modelValue'],
    template: '<select :value="modelValue" v-bind="$attrs" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-switch': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<input type="checkbox" :checked="modelValue" v-bind="$attrs" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />',
  },
  'el-button': { template: '<button type="button" :disabled="$attrs.disabled" v-bind="$attrs"><slot /></button>' },
  'el-auto-resizer': {
    template: '<div class="stub-auto-resizer"><slot :width="960" :height="300" /></div>',
  },
  'el-table-v2': {
    props: ['data', 'columns', 'width', 'height', 'sortBy', 'rowKey'],
    emits: ['column-sort', 'end-reached'],
    template: '<div class="stub-table" :data-row-key="rowKey"><button data-testid="sort-amount" @click="$emit(\'column-sort\', { key: \'amount\', order: sortBy.order === \'asc\' ? \'desc\' : \'asc\' })">模拟排序</button><button data-testid="end-reached" @click="$emit(\'end-reached\')">滚动到底</button><div v-for="column in columns" :key="column.key" class="stub-table-column" :data-prop="column.dataKey" :data-label="column.title" :data-sortable="String(column.sortable)" :data-flex-shrink="String(column.flexShrink)" /><div v-for="row in data" :key="row[rowKey]" class="stub-table-row" :data-row-id="row[rowKey]" /></div>',
  },
  'el-empty': { props: ['description'], template: '<div class="stub-empty">{{ description }}</div>' },
  'el-alert': { props: ['title'], template: '<div class="stub-alert">{{ title }}</div>' },
  ComponentDataPreview: { template: '<div data-testid="component-render-preview">组件预览 <slot /></div>' },
}

/** Aloudata 指标视图数据集：筛选字段来自视图维度 */
function metricViewDataset(partial: Partial<DatasetConfig> = {}): DatasetConfig {
  return {
    id: 'ds-1',
    sourceType: 'aloudata',
    sourceLabel: 'Aloudata',
    alias: 'cljd_zcl_zb_view',
    fields: [],
    filters: [{ field: 'metric_time', op: '>=', value: '2026-09-01' }],
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
async function openWith(dataset: DatasetConfig, component?: Record<string, unknown>) {
  state.ui.dataDialog = { visible: true, datasetId: dataset.id }
  const wrapper = mount(DatasetDataDialog, { props: { dataset, component }, global: { stubs }, attachTo: document.body })
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
  listDatasets.mockReset()
  listDatasets.mockResolvedValue([{ id: '123' }])
})

describe('查看数据弹窗 · 保留最近一次执行结果', () => {
  it('107 列结果的虚拟滚动内容宽度覆盖全部列，而非仅可视区宽度', async () => {
    const fields = Array.from({ length: 107 }, (_, index) => `metric_${index}`)
    const dataset = metricViewDataset({
      queryConfig: {
        ...queryConfig(),
        displayFields: fields.map((field) => ({ field, title: field, role: 'measure' as const })),
      },
    })
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [Object.fromEntries(fields.map((field, index) => [field, index]))],
      schema: fields, rowCount: 1, last: true, totalCount: 1,
    })
    state.ui.dataDialog = { visible: true, datasetId: dataset.id }
    const wrapper = mount(DatasetDataDialog, {
      props: { dataset },
      global: { stubs: { ...stubs, 'el-table-v2': false } },
      attachTo: document.body,
    })
    try {
      await wrapper.get('[data-testid="run-query"]').trigger('click')
      await flushPromises()

      const row = wrapper.get('.el-table-v2__row')
      expect((row.element as HTMLElement).style.width).toBe(`${fields.length * 160}px`)
    } finally {
      wrapper.unmount()
    }
  })

  it('筛选器默认值预填到本次查询条件，时间筛选器拆成日期输入并填入默认区间', async () => {
    state.filterCatalog = [
      { id: 'filter-region', title: '地区', type: 'filter', selectionMode: 'multiple', defaultValue: ['华东', '华南'] },
      { id: 'filter-date', title: '日期范围', type: 'timeFilter', defaultPreset: 'today' },
    ]
    const wrapper = await openWith(metricViewDataset({
      queryConfig: queryConfig([
        { filterComponentId: 'filter-region', parameterName: 'region', field: 'region', operator: 'in' },
        { filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' },
      ]),
    }))

    const rows = wrapper.findAll('[data-testid="query-filter-row"]')
    expect(rows).toHaveLength(2)
    expect((rows[0].get('input').element as HTMLInputElement).value).toBe('华东,华南')
    const dateInputs = rows[1].findAll('input.dd-value')
    expect(dateInputs).toHaveLength(2)
    expect(dateInputs[0].element.value).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(dateInputs[1].element.value).toBe(dateInputs[0].element.value)
    expect(rows[1].text()).toContain('开始时间')
    expect(rows[1].text()).toContain('结束时间')
    wrapper.unmount()
  })

  async function runQuery(wrapper: ReturnType<typeof mount>) {
    await wrapper.find('[data-testid="query-filter-row"] input.dd-value').setValue('2026-09-01')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
  }

  it('固定筛选只读展示且始终与本次筛选器绑定条件一起下推', async () => {
    const wrapper = await openWith(metricViewDataset({
      filters: [
        { field: 'metric_time', op: '>=', value: '2026-09-01' },
        { field: 'status', op: '=', value: 'ACTIVE' },
      ],
      queryConfig: queryConfig([
        { filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' },
      ]),
      fields: [{ name: 'status', displayName: '状态', role: 'dimension' }],
    }))
    await runQuery(wrapper)

    expect(wrapper.get('[data-testid="fixed-filter-section"]').text()).toContain('始终生效 · 只读')
    expect(wrapper.findAll('[data-testid="fixed-filter-row"]').some((row) => row.text().includes('ACTIVE'))).toBe(true)
    expect(wrapper.find('[data-testid="fixed-filter-table"] thead').text()).not.toContain('操作符')
    expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({
      filters: [
        { field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' },
        { field: 'status', role: 'dimension', operator: 'eq', value: 'ACTIVE' },
        { field: 'trade_date', role: 'dimension', operator: 'gte', value: '2026-09-01' },
      ],
    }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    wrapper.unmount()
  })

  it('查询后等待手动点击组件渲染，再更新画布并展示组件预览', async () => {
    const component = { id: 'table-1', type: 'table', title: '订单明细' }
    const buildPreviewData = vi.spyOn(componentPreviewUtils, 'componentPreviewData')
    const wrapper = await openWith(metricViewDataset({ queryConfig: queryConfig() }), component)
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('render')).toBeUndefined()
    expect(wrapper.find('[data-testid="component-render-preview"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="component-render"]').attributes('disabled')).toBeUndefined()
    expect(buildPreviewData).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="component-render"]').trigger('click')

    expect(buildPreviewData).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('render')).toHaveLength(1)
    expect(wrapper.emitted('render')?.[0]?.[0]).toMatchObject({
      componentId: 'table-1',
      renderType: 'table',
      table: { columns: ['trade_date', 'cust_type', 'amount'], rows: [['2026-09-01', '', '']] },
    })
    expect((wrapper.emitted('render')?.[0]?.[0] as { fieldLabels?: Record<string, string> }).fieldLabels?.trade_date).toBe('交易日期')
    expect(wrapper.find('[data-testid="component-render-preview"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('从过期缓存重新查询成功后应立即允许组件渲染', async () => {
    const dataset = metricViewDataset({ queryConfig: queryConfig() })
    setCachedQuery(dataset.id, {
      rows: [{ trade_date: '2026-08-01' }],
      columns: ['trade_date', 'cust_type', 'amount'],
      hasMore: false,
      elapsed: '0.1s',
      queriedAt: Date.now(),
      signature: 'stale-signature',
    })
    const wrapper = await openWith(dataset, { id: 'table-1', type: 'table', title: '订单明细' })

    expect(wrapper.get('[data-testid="component-render"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="component-render"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="component-render"]').trigger('click')
    expect(wrapper.emitted('render')).toHaveLength(1)
    wrapper.unmount()
  })

  it('相同条件下每次点击查询都会重新请求数据源，不使用已缓存行代替查询', async () => {
    const wrapper = await openWith(metricViewDataset())

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(previewDatasetDraft).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('查询完成后记录同一请求的接口与表格绘制耗时', async () => {
    const log = vi.spyOn(console, 'info').mockImplementation(() => {})
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(performance.now())
      return 1
    })
    const wrapper = await openWith(metricViewDataset())

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    const requestOptions = previewDatasetDraft.mock.calls.at(-1)?.[1] as { headers?: Record<string, string> }
    const event = log.mock.calls.filter(([name]) => name === '[dataset-query-perf]').at(-1)?.[1] as Record<string, unknown> | undefined
    expect(requestOptions.headers?.['X-Perf-Id']).toMatch(/^[\w-]{8,64}$/)
    expect(event).toMatchObject({
      requestId: requestOptions.headers?.['X-Perf-Id'],
      rows: 1,
    })
    expect(event?.columns).toEqual(expect.any(Number))
    expect(event?.cells).toBe(event?.columns)
    expect(event?.requestMs).toEqual(expect.any(Number))
    expect(event?.renderMs).toEqual(expect.any(Number))
    expect(event?.totalMs).toEqual(expect.any(Number))

    wrapper.unmount()
    log.mockRestore()
    vi.unstubAllGlobals()
  })

  it('已落库的 Aloudata 指标维度配置按当前选择发起草稿查询并遵从展示字段', async () => {
    const dataset = metricViewDataset({
      backendDatasetId: '123',
      aloudata: {
        mode: 'metric-dim',
        datasourceId: '2097582897597468673',
        metricView: '',
        metrics: ['new_metric', 'hidden_metric'],
        dims: ['new_dimension'],
      },
      queryConfig: {
        ...queryConfig(),
        displayFields: [
          { field: 'new_dimension', title: '新增维度', role: 'dimension' },
          { field: 'new_metric', title: '新增指标', role: 'measure' },
        ],
      },
    })
    const wrapper = await openWith(dataset)

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(previewInput).not.toHaveBeenCalled()
    expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'ALOUDATA_METRICS',
      sourceConfig: expect.objectContaining({ metrics: ['new_metric', 'hidden_metric'], dimensions: ['new_dimension'] }),
      columns: ['new_dimension', 'new_metric'],
    }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    wrapper.unmount()
  })

  it('指标卡查询返回多行时提示校验错误、保留原始结果并阻止画布渲染', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [
        { amount: 1215, count: 1470 },
        { amount: 1215, count: 1470 },
      ],
      schema: ['amount', 'count'],
      rowCount: 2,
      last: true,
    })
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        ...queryConfig(),
        displayFields: [
          { field: 'amount', title: '下发人数', role: 'measure' },
          { field: 'count', title: '下发次数', role: 'measure' },
        ],
      },
    }), {
      id: 'kpi-multi-row',
      type: 'kpi',
      title: '指标卡',
      kpiMetrics: [
        { fieldKey: 'amount', displayName: '下发人数' },
        { fieldKey: 'count', displayName: '下发次数' },
      ],
    })

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="component-preview-validation"]').text()).toContain('返回 2 行')
    expect(wrapper.findAll('.dd-result-body .stub-table')).toHaveLength(1)
    expect(wrapper.emitted('render')).toBeUndefined()
    expect(wrapper.get('[data-testid="component-render"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('分页查询的指标卡按总行数校验，即使当前页只有一行也不渲染', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [{ amount: 1215 }],
      schema: ['amount'],
      rowCount: 1,
      totalCount: 2,
      last: false,
    })
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        ...queryConfig(),
        displayFields: [{ field: 'amount', title: '下发人数', role: 'measure' }],
        paginationPolicy: { enabled: true, defaultPageSize: 1, maxPageSize: 10, returnTotalCount: false },
      },
    }), {
      id: 'kpi-paginated-multi-row',
      type: 'kpi',
      title: '指标卡',
      kpiMetrics: [{ fieldKey: 'amount', displayName: '下发人数' }],
    })

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({ requestTotalCount: true }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    expect(wrapper.get('[data-testid="component-preview-validation"]').text()).toContain('返回 2 行')
    expect(wrapper.emitted('render')).toBeUndefined()
    wrapper.unmount()
  })

  it('组件预览不会把响应中未配置展示的日期维度带到画布', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [{ trade_date: '2026-09-01', amount: 18, hidden_metric: 999 }],
      schema: ['trade_date', 'amount', 'hidden_metric'],
      rowCount: 1,
      last: true,
    })
    const dataset = metricViewDataset({
      queryConfig: { ...queryConfig(), displayFields: [{ field: 'amount', title: '金额', role: 'measure' }] },
    })
    const wrapper = await openWith(dataset, { id: 'table-1', type: 'table', title: '指标表' })
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="component-render"]').trigger('click')
    const rendered = wrapper.emitted('render')?.[0]?.[0] as { table?: { columns: string[]; rows: unknown[][] } }
    expect(rendered.table).toEqual({ columns: ['amount'], rows: [['18']] })
    wrapper.unmount()
  })

  it('KPI 组件预览忽略组件历史配置中未选择展示的日期指标', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [{ trade_date: '2026-09-01', amount: 18 }],
      schema: ['trade_date', 'amount'],
      rowCount: 1,
      last: true,
    })
    const dataset = metricViewDataset({
      queryConfig: { ...queryConfig(), displayFields: [{ field: 'amount', title: '金额', role: 'measure' }] },
    })
    const component = {
      id: 'kpi-1',
      type: 'kpi',
      title: '指标卡',
      kpiMetrics: [
        { fieldKey: 'trade_date', displayName: '指标日期' },
        { fieldKey: 'amount', displayName: '金额' },
      ],
    }
    const wrapper = await openWith(dataset, component)
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="component-render"]').trigger('click')
    const rendered = wrapper.emitted('render')?.[0]?.[0] as { kpiList?: Array<{ fieldKey: string }> }
    expect(rendered.kpiList?.map(({ fieldKey }) => fieldKey)).toEqual(['amount'])
    wrapper.unmount()
  })

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

  it('数字数据集 ID 已失效时回退来源草稿预览，不请求不存在的记录', async () => {
    const dataset = metricViewDataset({ backendDatasetId: '999' })
    listDatasets.mockResolvedValue([{ id: '101' }])
    const wrapper = await openWith(dataset)
    await runQuery(wrapper)

    expect(previewInput).not.toHaveBeenCalled()
    expect(previewDatasetDraft).toHaveBeenCalled()
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      datasourceId: '2097582897597468673',
      sourceConfig: { analysisViewId: 'cljd_zcl_zb_view' },
    })
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

  it('无筛选器绑定时，保存查询配置会同步展示字段，并可查询渲染到画布', async () => {
    const originalDatasets = state.datasets
    const dataset = reactive(metricViewDataset({ queryConfig: undefined }))
    state.datasets = [dataset]
    const wrapper = await openWith(state.datasets[0], { id: 'table-no-bindings', type: 'table', title: '策略表' })

    try {
      expect(wrapper.find('[data-testid="display-fields-empty"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="query-filter-row"]').exists()).toBe(false)

      const fixedFilters = [
        { field: 'metric_time', op: '>=', value: '2026-09-01' },
        { field: 'cust_type', op: '=', value: '机构' },
      ]
      const saveError = useInsight().saveQueryConfig(dataset.id, queryConfig([]), fixedFilters)
      await flushPromises()

      expect(saveError).toBeNull()
      expect(dataset.filters).toEqual(fixedFilters)
      expect(wrapper.findAll('[data-testid="display-field-row"]')).toHaveLength(3)
      expect(wrapper.text()).toContain('交易日期')
      expect(wrapper.text()).toContain('客户类型')
      expect(wrapper.text()).toContain('金额')
      expect(wrapper.findAll('[data-testid="fixed-filter-row"]').some((row) => row.text().includes('机构'))).toBe(true)

      await wrapper.get('[data-testid="run-query"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="component-render"]').trigger('click')

      expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({
        columns: ['trade_date', 'cust_type', 'amount'],
        filters: [
          { field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' },
          { field: 'cust_type', role: 'dimension', operator: 'eq', value: '机构' },
        ],
      }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
      expect(wrapper.emitted('render')?.[0]?.[0]).toMatchObject({
        componentId: 'table-no-bindings',
        table: { columns: ['trade_date', 'cust_type', 'amount'] },
      })
    } finally {
      wrapper.unmount()
      state.datasets = originalDatasets
    }
  })

  it('首次未保存查询配置时跟随数据集字段目录，并按默认展示字段查询和渲染', async () => {
    const dataset = reactive(metricViewDataset({ queryConfig: undefined }))
    const wrapper = await openWith(dataset, { id: 'table-first-open', type: 'table', title: '策略表' })

    expect(wrapper.find('[data-testid="display-fields-empty"]').exists()).toBe(true)

    // 模拟首次添加数据集后，异步 schema 预取完成；没有打开过「查询配置」。
    dataset.fields = [
      { name: 'amount', displayName: '下发人数', role: 'measure' },
      { name: 'trade_date', displayName: '指标日期', role: 'dimension' },
      { name: 'cust_type', displayName: '客户类型', role: 'dimension' },
    ]
    await flushPromises()

    const displayRows = wrapper.findAll('[data-testid="display-field-row"]')
    expect(displayRows).toHaveLength(3)
    expect(displayRows.map((row) => row.text())).toEqual([
      expect.stringContaining('指标日期'),
      expect.stringContaining('客户类型'),
      expect.stringContaining('下发人数'),
    ])

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="component-render"]').trigger('click')
    expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({ columns: ['trade_date', 'cust_type', 'amount'] }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    expect(wrapper.emitted('render')?.[0]?.[0]).toMatchObject({
      componentId: 'table-first-open',
      table: { columns: ['trade_date', 'cust_type', 'amount'] },
    })
    wrapper.unmount()
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
    expect(columns.map((column) => column.attributes('data-prop'))).toEqual(['cust_type', 'amount'])
    expect(columns[0].attributes('data-label')).toBe('客户类型')
    expect(columns[1].attributes('data-label')).toBe('金额')
    expect(columns[1].attributes('data-sortable')).toBe('true')
    expect(columns[1].attributes('data-flex-shrink')).toBe('0')
    expect(wrapper.get('.stub-table').attributes('data-row-key')).toBe('__ddRowKey')
    expect(wrapper.get('.stub-table-row').attributes('data-row-id')).toBe('0')
    wrapper.unmount()
  })

  it('时间维度只参与筛选，不参与展示分组；结果仅保留配置字段并映射中文名', async () => {
    previewDatasetDraft.mockResolvedValueOnce({
      rows: [{ trade_date: '2026-09-01', cust_type: '个人', amount: 18, extra: '不要展示' }],
      schema: ['trade_date', 'cust_type', 'amount', 'extra'],
      rowCount: 1,
      last: true,
    })
    const wrapper = await openWith(metricViewDataset())
    const filterRows = wrapper.findAll('[data-testid="query-filter-row"]')
    await filterRows[0].findAll('input.dd-value')[0].setValue('2026-09-01')
    await filterRows[0].findAll('input.dd-value')[1].setValue('2026-09-02')
    await wrapper.find('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    const request = previewDatasetDraft.mock.calls.at(-1)?.[0] as {
      columns?: string[]
      filters?: Array<{ field: string; operator: string; value: unknown }>
    }
    expect(request.columns).toEqual(['cust_type', 'amount'])
    expect(request.filters).toEqual([
      { field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' },
      { field: 'trade_date', role: 'dimension', operator: 'gte', value: '2026-09-01' },
      { field: 'trade_date', role: 'dimension', operator: 'lt', value: '2026-09-02' },
    ])
    const columns = wrapper.findAll('.stub-table-column')
    expect(columns.map((column) => column.attributes('data-prop'))).toEqual(['cust_type', 'amount'])
    expect(columns.map((column) => column.attributes('data-label'))).toEqual(['客户类型', '金额'])
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

  it('没有筛选器绑定时仍可按固定指标日期条件查询', async () => {
    const wrapper = await openWith(metricViewDataset({
      queryConfig: queryConfig([]),
    }))

    expect(wrapper.find('.dd-empty').text()).toContain('固定筛选条件仍会生效')
    expect(wrapper.find('.dd-query-row button').attributes('disabled')).toBeUndefined()
    await wrapper.find('.dd-query-row button').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({
      filters: [{ field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' }],
      parameters: {},
    })
    wrapper.unmount()
  })

  it('Aloudata 查看数据缺少指标日期时提示并阻止查询', async () => {
    const wrapper = await openWith(metricViewDataset({
      filters: [],
      queryConfig: queryConfig([]),
    }))

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(previewDatasetDraft).not.toHaveBeenCalled()
    expect(previewInput).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="dataset-query-performance"]').text()).toContain(
      '请配置指标日期（metric_time）筛选条件后再查询',
    )
    wrapper.unmount()
  })

  it('已绑定筛选条件为空或禁用时仍按固定指标日期条件查询', async () => {
    const wrapper = await openWith(metricViewDataset())
    const queryButton = wrapper.find('.dd-query-row button')
    expect(queryButton.attributes('disabled')).toBeUndefined()

    await queryButton.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({
      filters: [{ field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' }],
      parameters: {},
    })

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

    await wrapper.find('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { orders?: unknown[] }).orders).toEqual([{ field: 'amount', direction: 'asc' }])
    expect((previewInput.mock.calls.at(-1)?.[0] as { offset?: number }).offset).toBe(0)

    await wrapper.find('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { orders?: unknown[] }).orders).toEqual([{ field: 'amount', direction: 'desc' }])
    await wrapper.find('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { orders?: unknown[] }).orders).toEqual([])

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

  it('配置了默认降序时首次点击应切换为升序，而不是清除排序', async () => {
    const dataset = metricViewDataset({ backendDatasetId: '123' })
    dataset.queryConfig = {
      ...queryConfig([]),
      sortPolicy: { enabled: true, mode: 'single', allowedFields: ['amount'], defaultSort: { field: 'amount', direction: 'desc' } },
      paginationPolicy: { enabled: true, defaultPageSize: 2, maxPageSize: 5, returnTotalCount: true },
    }
    const wrapper = await openWith(dataset)
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect((previewInput.mock.calls.at(-1)?.[0] as { orders?: unknown[] }).orders).toEqual([{ field: 'amount', direction: 'asc' }])
    wrapper.unmount()
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
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).not.toContain('操作符')
    expect(wrapper.find('[data-testid="query-filter-table"] thead').text()).toContain('本次查询值')
    expect(wrapper.find('[data-testid="query-filter-operator"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="query-filter-enabled"]').element.checked).toBe(true)
    expect(wrapper.findAll('[data-testid="query-filter-row"] button')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('添加筛选条件')
    expect(wrapper.find('select.dd-field').exists()).toBe(false)
    expect(wrapper.find('.dd-note').exists()).toBe(false)
  })

  it('禁用唯一绑定筛选器时清空并锁定值，仍按固定指标日期条件查询', async () => {
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
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({
      filters: [{ field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' }],
    })
  })

  it('JDBC SQL 在查看数据时只读，SQL 参数仍可编辑并进入 parameters', async () => {
    const wrapper = await openWith(metricViewDataset({
      sourceType: 'jdbc',
      aloudata: undefined,
      filters: [],
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
    expect(request.filters).toEqual([
      { field: 'metric_time', operator: 'gte', value: '2026-09-01', role: 'dimension' },
      { field: 'trade_date', operator: 'gte', value: '2026-09-01', role: 'dimension' },
    ])
    expect(request.parameters).toEqual({ date: '2026-09-01' })
    expect(request.offset).toBe(0)
    expect(dataset.queryConfig?.parameterBindings[0].field).toBe('trade_date')
    expect(dataset.filters).toEqual([{ field: 'metric_time', op: '>=', value: '2026-09-01' }])
  })

  it('时间筛选器合并为一行并提交包前不包后的过滤条件', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', selectionMode: 'single', defaultTimeGranularity: 'MONTH' }]
    const wrapper = await openWith(metricViewDataset())

    const timeRow = wrapper.find('[data-testid="query-filter-time-range"]')
    expect(wrapper.findAll('[data-testid="query-filter-row"]')).toHaveLength(1)
    expect(timeRow.text()).toContain('开始时间')
    expect(timeRow.text()).toContain('结束时间')
    expect(timeRow.find('[data-testid="query-filter-operator"]').exists()).toBe(false)
    expect(timeRow.findAll('input.dd-value')).toHaveLength(2)
    expect(wrapper.get('[data-testid="time-granularity"]').element.value).toBe('MONTH')
    await wrapper.get('[data-testid="time-granularity"]').setValue('WEEK')

    await timeRow.findAll('input.dd-value')[0].setValue('2026-09-01')
    await timeRow.findAll('input.dd-value')[1].setValue('2026-10-01')
    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()

    expect(previewDatasetDraft.mock.calls.at(-1)?.[0]).toMatchObject({ timeGranularity: 'WEEK' })
    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[] }).filters).toEqual([
      { field: 'metric_time', operator: 'gte', value: '2026-09-01', role: 'dimension' },
      { field: 'trade_date', operator: 'gte', value: '2026-09-01', role: 'dimension' },
      { field: 'trade_date', operator: 'lt', value: '2026-10-01', role: 'dimension' },
    ])
  })

  it('未绑定时间筛选器时不展示或发送时间粒度', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'filter', selectionMode: 'single' }]
    const wrapper = await openWith(metricViewDataset({ queryConfig: queryConfig([
      { filterComponentId: 'filter-date', parameterName: 'date', field: 'trade_date', operator: 'gte' },
    ]) }))

    expect(wrapper.find('[data-testid="time-granularity"]').exists()).toBe(false)
    await wrapper.get('[data-testid="query-filter-row"] input.dd-value').setValue('2026-09-01')
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft.mock.calls.at(-1)?.[0]).not.toHaveProperty('timeGranularity')
  })

  it('已保存数据集也将所选时间粒度传入统一读取接口', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', defaultTimeGranularity: 'YEAR' }]
    const wrapper = await openWith(metricViewDataset({ backendDatasetId: '123' }))

    expect(wrapper.get('[data-testid="time-granularity"]').element.value).toBe('YEAR')
    await wrapper.get('[data-testid="time-granularity"]').setValue('QUARTER')
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()

    expect(previewInput.mock.calls.at(-1)?.[0]).toMatchObject({ timeGranularity: 'QUARTER' })
    wrapper.unmount()
  })

  it('单个启用开关同时控制开始和结束时间，禁用时两侧边界都不提交', async () => {
    state.filterCatalog = [{ id: 'filter-date', title: '日期范围', type: 'timeFilter', selectionMode: 'single' }]
    const wrapper = await openWith(metricViewDataset())
    const timeRow = wrapper.find('[data-testid="query-filter-time-range"]')
    const dates = timeRow.findAll('input.dd-value')
    await dates[0].setValue('2026-09-01')
    await dates[1].setValue('2026-10-01')
    await wrapper.find('[data-testid="query-filter-enabled"]').setValue(false)

    expect(wrapper.findAll('[data-testid="query-filter-enabled"]')).toHaveLength(1)
    expect(timeRow.findAll('input.dd-value').every((input) => input.attributes('disabled') !== undefined)).toBe(true)

    await wrapper.findAll('button').find((button) => button.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[] }).filters).toEqual([
      { field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' },
    ])
  })

  it('固定筛选不受旧全局 filterBindings 影响，并始终下推', async () => {
    state.filterBindings = [{
      filterName: '旧绑定', scope: { 'ds-1': true }, fieldMap: [], conditions: [],
    }] as never
    const dataset = metricViewDataset({
      filters: [
        { field: 'metric_time', op: '>=', value: '2026-09-01' },
        { field: 'cust_type', op: '=', value: '旧值' },
      ] as never,
      queryConfig: queryConfig([]),
    })
    const wrapper = await openWith(dataset)

    expect(wrapper.findAll('[data-testid="query-filter-row"]')).toHaveLength(0)
    expect(wrapper.findAll('[data-testid="fixed-filter-row"]').some((row) => row.text().includes('旧值'))).toBe(true)
    expect(wrapper.find('.dd-empty').text()).toContain('固定筛选条件仍会生效')
    expect(wrapper.text()).not.toContain('添加筛选条件')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    expect(previewDatasetDraft.mock.calls[0][0]).toMatchObject({
      filters: [
        { field: 'metric_time', role: 'dimension', operator: 'gte', value: '2026-09-01' },
        { field: 'cust_type', role: 'dimension', operator: 'eq', value: '旧值' },
      ],
    })
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
        filters: [],
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

describe('查看数据弹窗 · 限制条数', () => {
  it('未分页时按小批次渲染并在滚动时继续取数，累计不超过查询上限', async () => {
    const wrapper = await openWith(metricViewDataset({ queryConfig: queryConfig() }))

    const limitInput = wrapper.get('[data-testid="query-limit"]')
    expect((limitInput.element as HTMLInputElement).value).toBe('10000')

    await limitInput.setValue('80')
    previewDatasetDraft
      .mockResolvedValueOnce({
        rows: Array.from({ length: 50 }, (_, index) => ({ trade_date: `2026-09-${String(index + 1).padStart(2, '0')}` })),
        schema: ['trade_date'], rowCount: 50, last: false, totalCount: 120,
      })
      .mockResolvedValueOnce({
        rows: Array.from({ length: 30 }, (_, index) => ({ trade_date: `2026-10-${String(index + 1).padStart(2, '0')}` })),
        schema: ['trade_date'], rowCount: 30, last: false, totalCount: 120,
      })

    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenCalledWith(expect.objectContaining({ limit: 50, offset: 0 }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))

    await wrapper.get('[data-testid="end-reached"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenLastCalledWith(expect.objectContaining({ limit: 30, offset: 50 }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    expect(wrapper.get('.dd-result-actions').text()).not.toContain('可滚动加载更多')
    expect(getCachedQuery('ds-1')).toMatchObject({ rows: expect.arrayContaining([{ trade_date: '2026-09-01' }]), hasMore: true })
    expect(getCachedQuery('ds-1')?.rows).toHaveLength(50)

    await limitInput.setValue('200000')
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenLastCalledWith(expect.objectContaining({ limit: 50, offset: 0 }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    wrapper.unmount()
  })

  it('分页模式下限制条数禁用，返回量由每页条数接管', async () => {
    const wrapper = await openWith(metricViewDataset({
      queryConfig: {
        ...queryConfig(),
        paginationPolicy: { enabled: true, defaultPageSize: 5, maxPageSize: 50, returnTotalCount: false },
      },
    }))

    expect(wrapper.get('[data-testid="query-limit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="run-query"]').trigger('click')
    await flushPromises()
    expect(previewDatasetDraft).toHaveBeenLastCalledWith(expect.objectContaining({ limit: 5, offset: 0 }), expect.objectContaining({ timeout: 0, headers: expect.objectContaining({ 'X-Perf-Id': expect.any(String) }) }))
    wrapper.unmount()
  })
})
