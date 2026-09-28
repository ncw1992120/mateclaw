import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { nextTick, ref } from 'vue'
import DashboardPreviewView from '../DashboardPreviewView.vue'

const mocks = vi.hoisted(() => ({
  currentDashboard: { value: null as any },
  selectDashboard: vi.fn(),
  preview: vi.fn(),
  previewQueryPlan: vi.fn(),
  listDatasets: vi.fn(),
  confirmDatasetDraft: vi.fn(),
  executeComponent: vi.fn(),
  getExecutionStatus: vi.fn(),
  getExecutionResult: vi.fn(),
  previewExecutionResult: vi.fn(),
}))

vi.mock('@/stores/useInsightDashboardStore', () => ({
  useInsightDashboardStore: () => ({
    get currentDashboard() { return mocks.currentDashboard.value },
    selectDashboard: mocks.selectDashboard,
  }),
}))

vi.mock('@/api/insight-dashboard', () => ({
  preview: mocks.preview,
  executeComponent: mocks.executeComponent,
  getExecutionStatus: mocks.getExecutionStatus,
  getExecutionResult: mocks.getExecutionResult,
  previewExecutionResult: mocks.previewExecutionResult,
}))

vi.mock('@/api/dataset', () => ({
  previewQueryPlan: mocks.previewQueryPlan,
  list: mocks.listDatasets,
  confirmDraft: mocks.confirmDatasetDraft,
}))
vi.mock('@/api/insight-report', () => ({ generateReport: vi.fn(), getReport: vi.fn().mockResolvedValue(null), publishReport: vi.fn() }))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => false }),
  PERMISSION: { INSIGHT_CREATE: 'insight:create' },
}))

const filter = {
  id: 'filter-region', type: 'filter', title: '区域', position: { x: 0, y: 0, w: 1, h: 1 },
  config: { field: 'region', defaultValue: '华东', scope: 'global' },
} as any
const datasetComponent = {
  id: 'dataset-table', type: 'table', title: '数据表', position: { x: 1, y: 0, w: 2, h: 1 },
  config: { datasetPipeline: { datasetInputs: [{ datasetId: 'ds-1', inputName: 'sales', sourceType: 'ALOUDATA_METRICS', sourceConfig: { datasourceId: '9', metrics: ['sales'], dimensions: ['region'] }, queryConfig: {
    displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
    parameterBindings: [{ filterComponentId: 'filter-region', parameterName: 'regionParam', field: 'region', operator: 'eq' }],
  } }] } },
} as any
const unboundDatasetComponent = {
  id: 'unbound-table', type: 'table', title: '未绑定表格', position: { x: 3, y: 0, w: 2, h: 1 },
  config: { datasetPipeline: { datasetInputs: [{ datasetId: 'ds-2', inputName: 'other', queryConfig: {
    displayFields: [{ field: 'channel', title: '渠道', role: 'dimension' }], parameterBindings: [],
  } }] } },
} as any
const scopedFilter = {
  ...filter,
  config: { ...filter.config, scope: 'scoped', targetComponentIds: ['legacy-bound-table'] },
} as any
const legacyBoundComponent = {
  id: 'legacy-bound-table', type: 'table', title: '绑定表格',
  position: { x: 1, y: 0, w: 2, h: 1 }, boundFilterIds: ['filter-region'], dataSource: { datasetId: 'legacy-dataset' },
} as any

describe('DashboardPreviewView runtime filter query flow', () => {
  let wrapper: ReturnType<typeof mount> | undefined
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读', schemaJson: JSON.stringify({ version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components: [filter, datasetComponent, unboundDatasetComponent] }] }),
    }
    mocks.selectDashboard.mockResolvedValue(undefined)
    mocks.preview.mockResolvedValue([])
    mocks.previewQueryPlan.mockImplementation((request: any) => Promise.resolve({
      rows: [{ region: request.queryContext.parameters.regionParam }], rowCount: 1,
    }))
    mocks.listDatasets.mockResolvedValue([])
    mocks.confirmDatasetDraft.mockResolvedValue({ datasetId: '101' })
    mocks.executeComponent.mockReset()
    mocks.getExecutionStatus.mockReset()
    mocks.getExecutionResult.mockReset()
    mocks.previewExecutionResult.mockReset()
  })
  afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.useRealTimers(); vi.clearAllMocks() })

  it('uses the latest user filter value in the controlled dataset query and renders returned rows', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: {
        plugins: [i18n],
        stubs: {
          DashboardCanvas: {
            props: ['components', 'componentDataMap'],
            emits: ['filter-change'],
            template: '<div data-test="canvas"><button data-test="change" @click="$emit(\'filter-change\', { componentId: \'filter-region\', field: \'region\', value: \'华南\' })" />{{ JSON.stringify(componentDataMap) }}</div>',
          },
          ElButton: true, ElIcon: true, ElDrawer: true,
        },
      },
    })
    await flushPromises()
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    mocks.previewQueryPlan.mockClear()
    await wrapper.get('[data-test="change"]').trigger('click')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    expect(mocks.previewQueryPlan).toHaveBeenCalledWith(expect.objectContaining({
      datasetId: '101', inputName: 'sales',
      queryContext: expect.objectContaining({ parameters: { regionParam: '华南' } }),
    }))
    expect(mocks.previewQueryPlan).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-test="canvas"]').text()).toContain('华南')
  })

  it('keeps the dashboard canvas mounted while a scoped filter refresh is pending', async () => {
    mocks.currentDashboard.value.schemaJson = JSON.stringify({
      version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components: [scopedFilter, legacyBoundComponent] }],
    })
    let finishPreview!: (value: unknown[]) => void
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: {
        plugins: [i18n],
        stubs: {
          DashboardCanvas: {
            emits: ['filter-change'],
            template: '<div data-test="canvas"><button data-test="change" @click="$emit(\'filter-change\', { componentId: \'filter-region\', field: \'region\', value: \'华南\' })" />画布内容</div>',
          },
          ElButton: true, ElIcon: true, ElDrawer: true,
        },
      },
    })
    await flushPromises()
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    mocks.preview.mockImplementationOnce(() => new Promise((resolve) => { finishPreview = resolve }))
    await wrapper.get('[data-test="change"]').trigger('click')
    await vi.advanceTimersByTimeAsync(350)
    await nextTick()

    expect(wrapper.find('[data-test="canvas"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('insight.loadingData')
    finishPreview([])
    await flushPromises()
  })

  it('materializes each temporary dataset only once when dashboard loads overlap', async () => {
    let releaseDatasetList!: (value: unknown[]) => void
    const datasetListGate = new Promise<unknown[]>((resolve) => { releaseDatasetList = resolve })
    mocks.listDatasets.mockReturnValue(datasetListGate)
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: {
        plugins: [i18n],
        stubs: { DashboardCanvas: true, ElButton: true, ElIcon: true, ElDrawer: true },
      },
    })
    await nextTick()
    await wrapper.setProps({ dashboardId: 'dashboard-2' })
    await nextTick()
    releaseDatasetList([])
    await flushPromises()

    expect(mocks.listDatasets).toHaveBeenCalledTimes(1)
    expect(mocks.confirmDatasetDraft).toHaveBeenCalledTimes(1)
  })

  it('reuses a previously confirmed preview dataset from the list response', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: { plugins: [i18n], stubs: { DashboardCanvas: true, ElButton: true, ElIcon: true, ElDrawer: true } },
    })
    await flushPromises()
    const request = mocks.confirmDatasetDraft.mock.calls[0][0]
    const created = await mocks.confirmDatasetDraft.mock.results[0].value
    const createdDatasetId = created.datasetId
    wrapper.unmount()
    wrapper = undefined

    mocks.listDatasets.mockResolvedValue([{
      id: createdDatasetId,
      name: request.name,
      sourceType: request.sourceType,
      datasourceId: request.datasourceId,
    }])
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: { plugins: [i18n], stubs: { DashboardCanvas: true, ElButton: true, ElIcon: true, ElDrawer: true } },
    })
    await flushPromises()

    expect(mocks.confirmDatasetDraft).toHaveBeenCalledTimes(1)
    expect(mocks.previewQueryPlan).toHaveBeenCalledWith(expect.objectContaining({ datasetId: createdDatasetId }))
  })

  it('runs Python only after component execution receives its bound input values, then filters output by its own binding', async () => {
    const pythonComponent = {
      ...datasetComponent,
      id: 'python-table',
      config: { datasetPipeline: {
        script: 'return sales',
        datasetInputs: [{ datasetId: 'ds-1', inputName: 'sales', sourceType: 'ALOUDATA_METRICS', sourceConfig: { datasourceId: '9', metrics: ['sales'], dimensions: ['region'] }, queryConfig: {
          displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
          parameterBindings: [{ filterComponentId: 'filter-region', parameterName: 'inputRegion', field: 'region', operator: 'eq' }],
        } }],
        finalResultQueryConfig: {
          confirmed: true, schemaFingerprint: 'schema-1',
          displayFields: [{ field: 'region', title: '结果区域', role: 'dimension' }],
          filterFields: [{ field: 'region', title: '区域', dataType: 'string', parameterName: 'outputRegion', operators: ['eq'], filterComponentId: 'filter-region' }],
          sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
          paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
        },
      } },
    } as any
    mocks.currentDashboard.value.schemaJson = JSON.stringify({ version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components: [filter, pythonComponent] }] })
    mocks.executeComponent.mockResolvedValue({ executionId: 'execution-1' })
    mocks.getExecutionStatus.mockResolvedValue({ status: 'SUCCEEDED', result: JSON.stringify({
      schemaVersion: '1.0', kind: 'table', data: { columns: [{ name: 'region', title: '区域', dataType: 'string', nullable: false }], rows: [{ region: '华南' }] }, meta: { rowCount: 1, truncated: false },
    }) })
    mocks.previewExecutionResult.mockResolvedValue({ rows: [{ region: '华南' }], columns: ['region'] })
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: {
        plugins: [i18n],
        stubs: {
          DashboardCanvas: { props: ['components', 'componentDataMap'], template: '<div data-test="canvas">{{ JSON.stringify(componentDataMap) }}</div>' },
          ElButton: true, ElIcon: true, ElDrawer: true,
        },
      },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    expect(mocks.executeComponent).toHaveBeenCalledWith('dashboard-1', 'python-table', {}, expect.any(String), expect.objectContaining({ parameters: { inputRegion: '华东' } }))
    expect(mocks.previewExecutionResult).toHaveBeenCalledWith('execution-1', expect.objectContaining({
      parameters: { outputRegion: '华东' },
      finalResultQueryConfig: expect.objectContaining({ parameterBindings: [{ parameterName: 'outputRegion', field: 'region', operator: 'eq' }] }),
    }))
    expect(mocks.executeComponent).toHaveBeenCalledWith('dashboard-1', 'python-table', {}, expect.stringContaining('"datasetId":"101"'), expect.anything())
    expect(wrapper.get('[data-test="canvas"]').text()).toContain('华南')
  })

  it('renders the Python result directly when its saved result-query fields are stale', async () => {
    const pythonComponent = {
      ...datasetComponent,
      id: 'python-table-stale-config',
      config: { datasetPipeline: {
        script: 'return sales',
        datasetInputs: [{ datasetId: 'ds-1', inputName: 'sales', sourceType: 'ALOUDATA_METRICS', sourceConfig: { datasourceId: '9', metrics: ['sales'], dimensions: ['region'] }, queryConfig: { displayFields: [], parameterBindings: [] } }],
        finalResultQueryConfig: {
          confirmed: true, schemaFingerprint: 'old-schema',
          displayFields: [{ field: 'obsolete_column', title: '旧字段', role: 'dimension', dataType: 'string' }],
          filterFields: [], sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
          paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
        },
      } },
    } as any
    mocks.currentDashboard.value.schemaJson = JSON.stringify({ version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components: [filter, pythonComponent] }] })
    mocks.executeComponent.mockResolvedValue({ executionId: 'execution-stale-config' })
    mocks.getExecutionStatus.mockResolvedValue({ status: 'SUCCEEDED', result: JSON.stringify({
      schemaVersion: '1.0', kind: 'table', data: { columns: [{ name: 'actual_column', title: '实际字段', dataType: 'string', nullable: false }], rows: [{ actual_column: '真实结果' }] },
      meta: { rowCount: 1, truncated: false },
    }) })
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: { plugins: [i18n], stubs: {
        DashboardCanvas: { props: ['components', 'componentDataMap'], template: '<div data-test="canvas">{{ JSON.stringify(componentDataMap) }}</div>' },
        ElButton: true, ElIcon: true, ElDrawer: true,
      } },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    expect(mocks.previewExecutionResult).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="canvas"]').text()).toContain('真实结果')
  })

  it('ignores an older component query response when a newer filter request has completed', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
    wrapper = mount(DashboardPreviewView, {
      props: { dashboardId: 'dashboard-1' },
      global: {
        plugins: [i18n],
        stubs: {
          DashboardCanvas: {
            props: ['components', 'componentDataMap'], emits: ['filter-change'],
            template: '<div data-test="canvas"><button data-test="change-south" @click="$emit(\'filter-change\', { componentId: \'filter-region\', field: \'region\', value: \'华南\' })" /><button data-test="change-north" @click="$emit(\'filter-change\', { componentId: \'filter-region\', field: \'region\', value: \'西北\' })" />{{ JSON.stringify(componentDataMap) }}</div>',
          },
          ElButton: true, ElIcon: true, ElDrawer: true,
        },
      },
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    let resolveOld!: (value: unknown) => void
    let resolveNew!: (value: unknown) => void
    mocks.previewQueryPlan.mockClear()
    mocks.previewQueryPlan
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveNew = resolve }))
    await wrapper.get('[data-test="change-south"]').trigger('click')
    await vi.advanceTimersByTimeAsync(350)
    await wrapper.get('[data-test="change-north"]').trigger('click')
    await vi.advanceTimersByTimeAsync(350)
    expect(mocks.previewQueryPlan).toHaveBeenCalledTimes(2)

    resolveNew({ rows: [{ region: '最新响应' }], rowCount: 1 })
    await flushPromises()
    resolveOld({ rows: [{ region: '过期响应' }], rowCount: 1 })
    await flushPromises()

    expect(wrapper.get('[data-test="canvas"]').text()).toContain('最新响应')
    expect(wrapper.get('[data-test="canvas"]').text()).not.toContain('过期响应')
  })
})
