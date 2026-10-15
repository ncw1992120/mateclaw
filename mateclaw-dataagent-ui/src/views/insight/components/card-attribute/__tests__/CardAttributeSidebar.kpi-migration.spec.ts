import { nextTick, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { InsightComponent } from '@/types'
import { componentPreviewData } from '@/utils/component-preview-data'
import CardAttributeSidebar from '../CardAttributeSidebar.vue'

const fixture = vi.hoisted(() => ({
  state: {
    kpiMetrics: [] as Array<Record<string, unknown>>,
    datasets: [] as Array<Record<string, unknown>>,
    filterBindings: [] as Array<Record<string, unknown>>,
    pythonUser: '',
    hasPython: false,
    cards: [] as Array<Record<string, unknown>>,
    finalResultQueryConfig: {},
    resultSet: {
      status: 'empty', source: 'dataset', rows: [], columns: [], fieldLabels: {}, error: '',
      rowCount: 0, generatedAt: '', elapsedMs: 0, executionId: '',
    },
    projectedKpiMetrics: [] as Array<Record<string, unknown>>,
    ui: { preview: { kind: '' } },
    loadedComponent: null as InsightComponent | null,
  },
}))
fixture.state = reactive(fixture.state)

vi.mock('../useInsight', () => ({
  useInsight: () => ({ state: fixture.state, scheduleResultSet: vi.fn() }),
}))

vi.mock('../useCardAttributeBridge', () => ({
  hydratePanel: (component: InsightComponent) => {
    fixture.state.kpiMetrics = component.type === 'kpi'
      ? fixture.state.projectedKpiMetrics
      : []
  },
  panelToPipeline: () => ({
    datasetInputs: [],
    resultSet: fixture.state.resultSet.status === 'ready' ? {
      source: fixture.state.resultSet.source,
      status: 'ready',
      columns: fixture.state.resultSet.columns,
      rowCount: fixture.state.resultSet.rowCount,
      generatedAt: fixture.state.resultSet.generatedAt,
      elapsedMs: fixture.state.resultSet.elapsedMs,
      executionId: fixture.state.resultSet.executionId,
    } : undefined,
  }),
  buildComponentPatch: (component: InsightComponent) => ({
    ...component,
    kpiMetrics: fixture.state.kpiMetrics,
  }),
}))

vi.mock('../property/useComponentPropertyDraft', () => ({
  useComponentPropertyDraft: () => ({
    load: (component: InsightComponent) => { fixture.state.loadedComponent = component },
    commit: () => fixture.state.loadedComponent,
  }),
}))

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
  fixture.state.projectedKpiMetrics = []
  Object.assign(fixture.state.resultSet, {
    status: 'empty', source: 'dataset', rows: [], columns: [], fieldLabels: {}, error: '',
    rowCount: 0, generatedAt: '', elapsedMs: 0, executionId: '',
  })
})

describe('CardAttributeSidebar · legacy KPI migration', () => {
  it('writes projected KPI metrics back to a legacy multi-metric component on selection', async () => {
    vi.useFakeTimers()
    fixture.state.projectedKpiMetrics = [{ fieldKey: 'revenue', displayName: '收入', x: 0, y: 0, w: 160, h: 80 }]
    const legacyKpi = {
      id: 'legacy-kpi',
      type: 'kpi',
      title: '策略概括',
      multiKpi: true,
      position: { x: 0, y: 0, w: 4, h: 3 },
    } as InsightComponent

    const wrapper = mount(CardAttributeSidebar, {
      props: { component: legacyKpi, dashboardId: '' },
      global: { stubs: { AttributePanel: true, DataSourceTreeDialog: true, JdbcSqlDialog: true,
        AloudataDialog: true, ApiConfigDialog: true, FileConfigDialog: true, FieldMappingDialog: true,
        PythonScriptDialog: true, PreviewDialog: true, PythonResultDataDialog: true,
        MetricConfigDialog: true, MetricStyleDialog: true } },
    })

    await nextTick()
    await vi.advanceTimersByTimeAsync(301)
    await nextTick()

    expect(wrapper.emitted('change')?.[0]?.[0]).toMatchObject({
      id: 'legacy-kpi',
      kpiMetrics: [{ fieldKey: 'revenue', displayName: '收入' }],
    })
    wrapper.unmount()
  })

  it('已有一个指标时也会把结果集中新增的第二个指标同步到画布组件', async () => {
    vi.useFakeTimers()
    fixture.state.projectedKpiMetrics = [
      { fieldKey: 'digo_distr_count_1', displayName: '下发次数', x: 0, y: 0, w: 160, h: 80 },
      { fieldKey: 'digo_distr_user_cnt_a', displayName: '下发人数', x: 160, y: 0, w: 160, h: 80 },
    ]
    const partialKpi = {
      id: 'canvas-kpi',
      type: 'kpi',
      title: '指标卡',
      kpiMetrics: [{ fieldKey: 'digo_distr_count_1', displayName: '下发次数', x: 0, y: 0, w: 160, h: 80 }],
      position: { x: 0, y: 0, w: 4, h: 3 },
    } as InsightComponent

    const wrapper = mount(CardAttributeSidebar, {
      props: { component: partialKpi, dashboardId: '' },
      global: { stubs: { AttributePanel: true, DataSourceTreeDialog: true, JdbcSqlDialog: true,
        AloudataDialog: true, ApiConfigDialog: true, FileConfigDialog: true, FieldMappingDialog: true,
        PythonScriptDialog: true, PreviewDialog: true, PythonResultDataDialog: true,
        MetricConfigDialog: true, MetricStyleDialog: true } },
    })

    await nextTick()
    await vi.advanceTimersByTimeAsync(301)
    await nextTick()

    const updatedComponent = wrapper.emitted('change')?.[0]?.[0] as InsightComponent
    expect(updatedComponent).toMatchObject({
      id: 'canvas-kpi',
      kpiMetrics: [
        { fieldKey: 'digo_distr_count_1', displayName: '下发次数' },
        { fieldKey: 'digo_distr_user_cnt_a', displayName: '下发人数' },
      ],
    })
    const canvasData = componentPreviewData(updatedComponent, [{ digo_distr_count_1: 1470, digo_distr_user_cnt_a: 1215 }], [
      { name: 'digo_distr_count_1', title: '下发次数' },
      { name: 'digo_distr_user_cnt_a', title: '下发人数' },
    ])
    expect(canvasData.kpiList?.map(({ fieldKey, value }) => [fieldKey, value])).toEqual([
      ['digo_distr_count_1', '1470'],
      ['digo_distr_user_cnt_a', '1215'],
    ])
    wrapper.unmount()
  })

  it('生成 Python 结果后把 executionId 和实际输出列持久化到组件 pipeline', async () => {
    vi.useFakeTimers()
    fixture.state.projectedKpiMetrics = []
    const component = {
      id: 'python-kpi',
      type: 'kpi',
      title: '策略贡献',
      kpiMetrics: [],
      position: { x: 0, y: 0, w: 4, h: 3 },
    } as InsightComponent
    const wrapper = mount(CardAttributeSidebar, {
      props: { component, dashboardId: '' },
      global: { stubs: { AttributePanel: true, DataSourceTreeDialog: true, JdbcSqlDialog: true,
        AloudataDialog: true, ApiConfigDialog: true, FileConfigDialog: true, FieldMappingDialog: true,
        PythonScriptDialog: true, PreviewDialog: true, PythonResultDataDialog: true,
        MetricConfigDialog: true, MetricStyleDialog: true } },
    })

    await nextTick()
    fixture.state.resultSet.source = 'script'
    fixture.state.resultSet.columns = [{ name: '转化规模' }, { name: '转化人数' }]
    fixture.state.resultSet.rows = [{ 转化规模: 27948000, 转化人数: 2964 }]
    fixture.state.resultSet.rowCount = 1
    fixture.state.resultSet.generatedAt = '2026-10-03T00:00:00.000Z'
    fixture.state.resultSet.elapsedMs = 120
    fixture.state.resultSet.executionId = 'execution-new'
    fixture.state.resultSet.status = 'ready'
    await nextTick()
    await vi.advanceTimersByTimeAsync(301)
    await nextTick()

    const updated = wrapper.emitted('change')?.at(-1)?.[0] as InsightComponent
    expect(updated.config?.datasetPipeline).toMatchObject({
      resultSet: {
        source: 'script', status: 'ready', executionId: 'execution-new',
        columns: [{ name: '转化规模' }, { name: '转化人数' }],
      },
    })
    wrapper.unmount()
  })

  it('Python 输出列元数据更新时，即使结果集状态仍为 ready 也回写 pipeline', async () => {
    vi.useFakeTimers()
    Object.assign(fixture.state.resultSet, {
      status: 'ready',
      columns: [{ name: '旧输入指标', type: 'number' }],
      executionId: 'execution-1',
    })
    const component = {
      id: 'nested-kpi', type: 'kpi', title: '策略贡献',
      position: { x: 0, y: 0, w: 4, h: 3 },
    } as InsightComponent
    const wrapper = mount(CardAttributeSidebar, {
      props: { component, dashboardId: '' },
      global: { stubs: { AttributePanel: true, DataSourceTreeDialog: true, JdbcSqlDialog: true,
        AloudataDialog: true, ApiConfigDialog: true, FileConfigDialog: true, FieldMappingDialog: true,
        PythonScriptDialog: true, PreviewDialog: true, PythonResultDataDialog: true,
        MetricConfigDialog: true, MetricStyleDialog: true } },
    })
    await nextTick()

    fixture.state.resultSet.columns = [
      { name: '转化规模', type: 'number' },
      { name: '转化人数', type: 'number' },
    ]
    await nextTick()
    await vi.advanceTimersByTimeAsync(301)
    await nextTick()

    expect(wrapper.emitted('change')?.at(-1)?.[0]).toMatchObject({ id: 'nested-kpi' })
    expect(wrapper.emitted('change')?.at(-1)?.[0]).toHaveProperty('config.datasetPipeline.resultSet.columns', [
      { name: '转化规模', type: 'number' },
      { name: '转化人数', type: 'number' },
    ])
    wrapper.unmount()
  })
})
