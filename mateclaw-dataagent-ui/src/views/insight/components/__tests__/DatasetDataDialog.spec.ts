import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const previewDatasetDraft = vi.fn(async () => ({
  rows: [{ trade_date: '2026-09-01' }] as Record<string, unknown>[],
  schema: ['trade_date'],
}))
const previewInput = vi.fn(async () => ({
  rows: [{ trade_date: '2026-09-01' }] as Record<string, unknown>[],
  schema: ['trade_date'],
}))

vi.mock('../card-attribute/useInsightBackend', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../card-attribute/useInsightBackend')>()
  return { ...actual, previewDatasetDraft: (...args: unknown[]) => previewDatasetDraft(...(args as [])) }
})

vi.mock('@/api/dataset', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dataset')>()
  return { ...actual, previewInput: (...args: unknown[]) => previewInput(...(args as [])) }
})

/** 指标视图字段清单：两个维度 + 一个指标（指标不该出现在筛选项里） */
vi.mock('@/api/datasource', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/datasource')>()
  return {
    ...actual,
    listAnalysisViewFields: vi.fn(async () => [
      { name: 'trade_date', displayName: '交易日期', role: 'dimension' },
      { name: 'cust_type', displayName: '客户类型', role: 'dimension' },
      { name: 'amount', displayName: '金额', role: 'measure' },
    ]),
  }
})

import DatasetDataDialog from '../DatasetDataDialog.vue'
import { clearAllCachedQueries } from '../dataset-data-cache'
import { useInsight } from '../card-attribute/useInsight'
import type { DatasetConfig } from '../card-attribute/useInsight'

const { state } = useInsight()

const stubs = {
  'el-dialog': { template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>' },
  'el-alert': { props: ['title'], template: '<div class="stub-alert">{{ title }}</div>' },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<select :value="modelValue" v-bind="$attrs" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
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
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-table': { props: ['data'], template: '<div class="stub-table"><slot /></div>' },
  'el-table-column': { template: '<div />' },
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
  clearAllCachedQueries()
  previewDatasetDraft.mockClear()
  previewInput.mockClear()
})

describe('查看数据弹窗 · 保留最近一次执行结果', () => {
  async function runQuery(wrapper: ReturnType<typeof mount>) {
    await wrapper.find('select.dd-field').setValue('trade_date')
    await wrapper.find('.dd-condition').find('input.dd-value').setValue('2026-09-01')
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
    // 临时查询值不写回 Schema；重新打开只按持久化条件初始化
    expect(again.find('select.dd-field').element.value).toBe('')
    expect(again.find('.dd-condition').find('input.dd-value').element.value).toBe('')
    again.unmount()
  })

  it('条件改过之后：缓存结果照常展示，但标注「条件已变更」提醒重查', async () => {
    const dataset = metricViewDataset()
    const first = await openWith(dataset)
    await runQuery(first)
    first.unmount()

    // 用户在别处改了条件
    dataset.filters = [{ field: 'cust_type', op: '=', value: 'A' }] as never
    const again = await openWith(dataset)
    expect(again.findAll('.dd-result-body .stub-table')).toHaveLength(1) // 结果仍展示，不清空
    expect(again.find('.dd-result .dd-hint').text()).toContain('条件已变更')
    // 条件区展示的是新条件，不是产生缓存结果的那份
    expect(again.find('select.dd-field').element.value).toBe('cust_type')
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

  it('给出「添加筛选条件」入口与操作指引（此前缺失的地方）', async () => {
    const wrapper = await openWith(metricViewDataset())

    expect(wrapper.text()).toContain('添加筛选条件')
    expect(wrapper.text()).toContain('多个条件按 AND 组合')
  })

  it('指标视图的筛选项只取维度，指标不进筛选项', async () => {
    const wrapper = await openWith(metricViewDataset())

    const options = wrapper.findAll('select.dd-field option').map((o) => o.element.value)
    expect(options).toEqual(['trade_date', 'cust_type'])
  })

  it('没有可筛字段时提示可手输字段名，而不是静默给空下拉', async () => {
    const wrapper = await openWith(
      metricViewDataset({ sourceType: 'jdbc', jdbc: { db: '1', sql: 'select 1' }, aloudata: undefined, fields: [] }),
    )

    expect(wrapper.find('.stub-alert').text()).toContain('未取到可筛选字段')
    // 仍然给一条可编辑行，用户能直接敲字段名（下拉支持 allow-create）
    expect(wrapper.find('select.dd-field').attributes('allow-create')).toBeDefined()
  })
})

describe('查看数据弹窗 · 添加条件并查询', () => {
  it('点「添加筛选条件」新增一行条件', async () => {
    const wrapper = await openWith(metricViewDataset())

    // 初次打开没有存量条件时给一条空行，便于直接填
    expect(wrapper.findAll('.dd-condition')).toHaveLength(1)
    await wrapper.findAll('button').find((b) => b.text().includes('添加筛选条件'))!.trigger('click')
    expect(wrapper.findAll('.dd-condition')).toHaveLength(2)
  })

  it('查询时把条件下推到源查询（filters），但不写回数据集 Schema', async () => {
    const dataset = metricViewDataset()
    const wrapper = await openWith(dataset)

    await wrapper.find('select.dd-field').setValue('trade_date')
    await wrapper.find('.dd-condition').findAll('input')[0].setValue('2026-09-01')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()

    expect(previewDatasetDraft).toHaveBeenCalledTimes(1)
    const request = previewDatasetDraft.mock.calls[0][0] as { filters?: unknown[]; offset?: number }
    expect(request.filters).toEqual([{ field: 'trade_date', op: '=', value: '2026-09-01' }])
    expect(request.offset).toBe(0)
    expect(dataset.filters).toEqual([])
  })

  it('打开时默认带出绑定筛选模板，运行值只进入本次查询', async () => {
    state.filterBindings = [{
      filterName: '时间范围',
      scope: { 'ds-1': true },
      fieldMap: [{ datasetId: 'ds-1', field: 'metric_time', matched: true }],
      conditions: [
        { inputName: 'cljd_zcl_zb_view', field: 'metric_time', operator: 'gte', parameterNames: ['startDate'], required: false },
        { inputName: 'cljd_zcl_zb_view', field: 'metric_time', operator: 'lt', parameterNames: ['endDate'], required: false },
      ],
    }] as any
    const dataset = metricViewDataset()
    const wrapper = await openWith(dataset)

    expect(wrapper.findAll('[data-testid="bound-filter-row"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-testid="bound-filter-row"] input')[0].attributes('placeholder'))
      .toBe('可选，未填写不参与查询')
    await wrapper.findAll('[data-testid="bound-filter-row"] input')[0].setValue('2026-09-01')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()

    expect((previewDatasetDraft.mock.calls.at(-1)?.[0] as { filters?: unknown[] }).filters).toEqual([
      { field: 'metric_time', operator: 'gte', value: '2026-09-01', role: 'dimension' },
    ])
    expect(dataset.filters).toEqual([])
  })

  it('不完整的条件（缺字段或缺值）不参与下推', async () => {
    const dataset = metricViewDataset()
    const wrapper = await openWith(dataset)

    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()

    const request = previewDatasetDraft.mock.calls[0][0] as { filters?: unknown[] }
    expect(request.filters).toEqual([])
    expect(dataset.filters).toEqual([])
  })

  it('切到「为空」这类免值操作符时清掉残留取值，避免下推一个看不见的值', async () => {
    const dataset = metricViewDataset({
      sourceType: 'jdbc',
      aloudata: undefined,
      jdbc: { db: '1', sql: 'select 1' },
      fields: [{ name: 'region' }],
    })
    const wrapper = await openWith(dataset)

    await wrapper.find('select.dd-field').setValue('region')
    await wrapper.find('.dd-condition').find('input.dd-value').setValue('华东')
    expect(wrapper.find('.dd-condition').find('input.dd-value').element.value).toBe('华东')

    await wrapper.find('.dd-condition').findAll('select')[1].setValue('is null')

    const input = wrapper.find('.dd-condition').find('.dd-value')
    expect(input.element.value).toBe('')
    expect(input.attributes('disabled')).toBeDefined()

    // 空值操作符仍然可下推（不需要取值）
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewDatasetDraft.mock.calls[0][0] as { filters?: unknown }).filters).toEqual([
      { field: 'region', op: 'is null', value: '' },
    ])
  })
})

describe('查看数据弹窗 · 存量筛选不丢', () => {
  it('打开时回填已配好的条件', async () => {
    const wrapper = await openWith(
      metricViewDataset({ filters: [{ field: 'cust_type', op: 'in', value: 'A,B' }] as never }),
    )

    const row = wrapper.find('.dd-condition')
    expect(row.find('select').element.value).toBe('cust_type')
    expect(row.findAll('input')[0].element.value).toBe('A,B')
  })

  it('字段已不在可筛选项内 → 单独列为遗留条件，可移除但不静默丢弃', async () => {
    const dataset = metricViewDataset({ filters: [{ field: '已删除的维度', op: '=', value: 'x' }] as never })
    const wrapper = await openWith(dataset)

    expect(wrapper.text()).toContain('遗留条件')
    expect(wrapper.find('.dd-legacy').text()).toContain('已删除的维度')
    expect(wrapper.findAll('.dd-condition')).toHaveLength(1) // 遗留的不算可编辑行

    await wrapper.find('.dd-legacy').findAll('button').find((b) => b.text().includes('移除'))!.trigger('click')
    expect(wrapper.find('.dd-legacy').exists()).toBe(false)

    // 移除后查询：遗留条件不再写回数据集
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect(dataset.filters).toEqual([{ field: '已删除的维度', op: '=', value: 'x' }])
  })
})

describe('查看数据弹窗 · 按数据源类型给不同的筛选项', () => {
  it('文件类型不给筛选条件入口（上游不做下推，避免能配但不生效）', async () => {
    const wrapper = await openWith(
      metricViewDataset({
        sourceType: 'file',
        file: { fileName: 'a.csv', fileType: 'csv', columns: [{ name: 'c1', type: 'string' }], rows: [] },
        aloudata: undefined,
      }),
    )

    expect(wrapper.text()).not.toContain('添加筛选条件')
    expect(wrapper.text()).toContain('文件')
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

    // 占位符未填 → 不下发；填了 → 走 parameters（不是 filters）
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    expect((previewDatasetDraft.mock.calls[0][0] as { parameters?: unknown }).parameters).toEqual({})

    await wrapper.find('.dd-param input').setValue('华东')
    await wrapper.findAll('button').find((b) => b.text().includes('查询'))!.trigger('click')
    await flushPromises()
    const request = previewDatasetDraft.mock.calls[1][0] as { parameters?: unknown; filters?: unknown }
    expect(request.parameters).toEqual({ region: '华东' })
    expect(request.filters).toEqual([])
  })
})
