import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import DashboardPreviewView from '../DashboardPreviewView.vue'

const mocks = vi.hoisted(() => ({
  currentDashboard: { value: null as any },
  selectDashboard: vi.fn(),
  preview: vi.fn(),
  previewQueryPlan: vi.fn(),
  listDatasets: vi.fn(),
  confirmDatasetDraft: vi.fn(),
  execute: vi.fn(),
  executeComponent: vi.fn(),
  getExecutionStatus: vi.fn(),
  getExecutionResult: vi.fn(),
  previewExecutionResult: vi.fn(),
  getReport: vi.fn(),
}))

vi.mock('@/stores/useInsightDashboardStore', () => ({
  useInsightDashboardStore: () => ({
    get currentDashboard() { return mocks.currentDashboard.value },
    selectDashboard: mocks.selectDashboard,
  }),
}))

vi.mock('@/api/insight-dashboard', () => ({
  preview: mocks.preview,
  execute: mocks.execute,
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
vi.mock('@/api/insight-report', () => ({
  generateReport: vi.fn(),
  getReport: mocks.getReport,
  publishReport: vi.fn(),
}))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => false }),
  PERMISSION: { INSIGHT_CREATE: 'insight:create' },
}))

/** 旧版直连组件：有 dataSource、无管线，走整页 preview 接口 */
const directComponent = {
  id: 'legacy-bound-table', type: 'table', title: '直连表格',
  position: { x: 0, y: 0, w: 2, h: 1 }, dataSource: { datasetId: 'legacy-dataset' },
} as any

/** 数据集管线组件：已落库 datasetId，无需物化，走 previewQueryPlan */
const pipelineComponent = {
  id: 'dataset-table', type: 'table', title: '管线表格', position: { x: 2, y: 0, w: 2, h: 1 },
  config: { datasetPipeline: { datasetInputs: [{ datasetId: '101', inputName: 'sales', queryConfig: {
    displayFields: [{ field: 'region', title: '区域', role: 'dimension' }], parameterBindings: [],
  } }] } },
} as any

function schemaFor(components: unknown[]): string {
  return JSON.stringify({ version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components }] })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

async function mountPreview() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
  const wrapper = mount(DashboardPreviewView, {
    props: { dashboardId: 'dashboard-1' },
    global: {
      plugins: [i18n],
      stubs: {
        DashboardCanvas: {
          props: ['components', 'componentDataMap'],
          emits: ['filter-change'],
          template: '<div data-test="canvas">{{ JSON.stringify(componentDataMap) }}</div>',
        },
        ElButton: true, ElIcon: true, ElDrawer: true,
      },
    },
  })
  return wrapper
}

function canvasDataMap(wrapper: ReturnType<typeof mount>): Record<string, any> {
  return JSON.parse(wrapper.get('[data-test="canvas"]').text())
}

describe('DashboardPreviewView 阶段式并行加载', () => {
  let wrapper: ReturnType<typeof mount> | undefined
  beforeEach(() => {
    mocks.currentDashboard.value = null
    mocks.selectDashboard.mockResolvedValue(undefined)
    mocks.preview.mockReset()
    mocks.previewQueryPlan.mockReset()
    mocks.listDatasets.mockReset()
    mocks.confirmDatasetDraft.mockReset()
    mocks.execute.mockReset()
    mocks.getReport.mockResolvedValue(null)
    mocks.previewQueryPlan.mockResolvedValue({ rows: [{ region: '华东' }], rowCount: 1 })
  })
  afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.clearAllMocks() })

  it('直连通道延迟时，管线组件独立先出数，不被整页响应阻塞', async () => {
    const pendingPreview = deferred<any[]>()
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读', schemaJson: schemaFor([directComponent, pipelineComponent]),
    }
    mocks.preview.mockReturnValue(pendingPreview.promise)
    wrapper = await mountPreview()
    await flushPromises()

    const dataMap = canvasDataMap(wrapper)
    expect(dataMap['dataset-table'].queryStatus).toBe('success')
    expect(dataMap['legacy-bound-table'].queryStatus).toBe('loading')

    pendingPreview.resolve([{ componentId: 'legacy-bound-table', renderType: 'table', table: { columns: ['city'], rows: [{ city: '杭州' }] } }])
    await flushPromises()
    expect(canvasDataMap(wrapper)['legacy-bound-table'].queryStatus).toBe('success')
  })

  it('直连通道失败只将直连组件置为 error，管线组件保留成功结果', async () => {
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读', schemaJson: schemaFor([directComponent, pipelineComponent]),
    }
    mocks.preview.mockRejectedValue(new Error('直连数据源不可用'))
    wrapper = await mountPreview()
    await flushPromises()

    const dataMap = canvasDataMap(wrapper)
    expect(dataMap['legacy-bound-table'].queryStatus).toBe('error')
    expect(dataMap['legacy-bound-table'].error).toContain('直连数据源不可用')
    expect(dataMap['dataset-table'].queryStatus).toBe('success')
  })

  it('报告读取延迟不阻塞组件数据渲染路径', async () => {
    const pendingReport = deferred<string | null>()
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读', schemaJson: schemaFor([pipelineComponent]),
    }
    mocks.getReport.mockReturnValue(pendingReport.promise)
    wrapper = await mountPreview()
    await flushPromises()

    // 报告未返回时，管线查询已完成（loadDashboard 不再等待报告）
    expect(mocks.previewQueryPlan).toHaveBeenCalledTimes(1)
    expect(canvasDataMap(wrapper)['dataset-table'].queryStatus).toBe('success')

    pendingReport.resolve('<p>报告</p>')
    await flushPromises()
    expect(mocks.getReport).toHaveBeenCalledWith('dashboard-1')
  })

  it('无数据集管线时不请求数据集列表（不做物化探测）', async () => {
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读', schemaJson: schemaFor([directComponent]),
    }
    wrapper = await mountPreview()
    await flushPromises()

    expect(mocks.listDatasets).not.toHaveBeenCalled()
    expect(mocks.confirmDatasetDraft).not.toHaveBeenCalled()
    expect(mocks.preview).toHaveBeenCalledTimes(1)
  })

  it('组件同时被管线与脚本绑定时，脚本绑定让位（唯一数据源仲裁）', async () => {
    const overlapped = {
      ...pipelineComponent,
      dataSource: { datasetId: 'legacy-dataset' },
    } as any
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读',
      schemaJson: JSON.stringify({
        version: '1.0', script: 'print(1)',
        scriptBindings: [{ componentId: 'dataset-table', renderType: 'table' }],
        pages: [{ id: 'page-1', name: '策略视角', components: [overlapped] }],
      }),
    }
    wrapper = await mountPreview()
    await flushPromises()

    // 脚本通道让位：整页脚本执行不发起；数据由管线通道提供
    expect(mocks.execute).not.toHaveBeenCalled()
    expect(mocks.previewQueryPlan).toHaveBeenCalledTimes(1)
    expect(canvasDataMap(wrapper)['dataset-table'].queryStatus).toBe('success')
  })

  it('预览页面向外层内容区应用与画布相同的仪表盘背景主题', async () => {
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '主题预览',
      schemaJson: JSON.stringify({
        version: '1.0',
        theme: { mode: 'preset', presetId: 'rose' },
        pages: [{ id: 'page-1', name: '页面', components: [pipelineComponent] }],
      }),
    }
    wrapper = await mountPreview()
    await flushPromises()

    expect(wrapper.get('.dashboard-preview-view').attributes('style') ?? '').toContain('--insight-page-bg: #FFF7FA')
  })
})
