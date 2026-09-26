import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { InsightComponent } from '@/types'
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
      ? [{ fieldKey: 'revenue', displayName: '收入', x: 0, y: 0, w: 160, h: 80 }]
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
    fixture.state.kpiMetrics = []
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
})
