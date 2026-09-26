import { nextTick } from 'vue'
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
    resultSet: { status: 'empty', source: 'dataset', rows: [], fieldLabels: {}, error: '' },
    projectedKpiMetrics: [] as Array<Record<string, unknown>>,
    ui: { preview: { kind: '' } },
    loadedComponent: null as InsightComponent | null,
  },
}))

vi.mock('../useInsight', () => ({
  useInsight: () => ({ state: fixture.state, scheduleResultSet: vi.fn() }),
}))

vi.mock('../useCardAttributeBridge', () => ({
  hydratePanel: (component: InsightComponent) => {
    fixture.state.kpiMetrics = component.type === 'kpi'
      ? fixture.state.projectedKpiMetrics
      : []
  },
  panelToPipeline: () => ({ datasetInputs: [] }),
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
})
