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

const filter = {
  id: 'filter-region', type: 'filter', title: '区域', position: { x: 0, y: 0, w: 1, h: 1 },
  config: { field: 'region', scope: 'global' },
} as any

/** 组件级脚本执行管线：datasetId 已落库 + Python 脚本 */
const scriptPipelineComponent = {
  id: 'script-table', type: 'table', title: '脚本表格', position: { x: 1, y: 0, w: 2, h: 1 },
  config: { datasetPipeline: {
    datasetInputs: [{ datasetId: '101', inputName: 'sales', queryConfig: {
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      parameterBindings: [{ filterComponentId: 'filter-region', parameterName: 'regionParam', field: 'region', operator: 'eq' }],
    } }],
    script: 'print(1)',
  } },
} as any

function tableEnvelope(rows: Record<string, unknown>[]): string {
  return JSON.stringify({
    schemaVersion: '1.0', kind: 'table', meta: { rowCount: rows.length },
    data: { columns: [{ name: 'region', title: '区域', dataType: 'string' }], rows },
  })
}

async function mountPreview() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false })
  return mount(DashboardPreviewView, {
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
}

function canvasDataMap(wrapper: ReturnType<typeof mount>): Record<string, any> {
  return JSON.parse(wrapper.get('[data-test="canvas"]').text())
}

describe('DashboardPreviewView 脚本状态轮询退避', () => {
  let wrapper: ReturnType<typeof mount> | undefined
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.currentDashboard.value = {
      id: 'dashboard-1', name: '策略解读',
      schemaJson: JSON.stringify({ version: '1.0', pages: [{ id: 'page-1', name: '策略视角', components: [filter, scriptPipelineComponent] }] }),
    }
    mocks.selectDashboard.mockResolvedValue(undefined)
    mocks.preview.mockResolvedValue([])
    mocks.listDatasets.mockResolvedValue([])
    mocks.getReport.mockResolvedValue(null)
    mocks.executeComponent.mockResolvedValue({ executionId: 'exec-1' })
    mocks.getExecutionStatus.mockReset()
    mocks.getExecutionResult.mockReset()
    mocks.previewExecutionResult.mockReset()
    mocks.previewQueryPlan.mockReset()
  })
  afterEach(() => {
    wrapper?.unmount(); wrapper = undefined
    vi.useRealTimers(); vi.clearAllMocks()
  })

  it('RUNNING 时按 500ms→1s→2s→5s 退避时点轮询', async () => {
    mocks.getExecutionStatus.mockResolvedValue({ status: 'RUNNING' })
    wrapper = await mountPreview()
    await flushPromises()
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(499)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(999)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(1999)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(4)

    await vi.advanceTimersByTimeAsync(3999)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(4)
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(5)

    await vi.advanceTimersByTimeAsync(4999)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(5)
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(6)
  })

  it('SUCCEEDED 终态立即停止轮询并回填组件数据', async () => {
    mocks.getExecutionStatus
      .mockResolvedValueOnce({ status: 'RUNNING' })
      .mockResolvedValueOnce({ status: 'SUCCEEDED', result: tableEnvelope([{ region: '华东' }]) })
    wrapper = await mountPreview()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(2)
    const dataMap = canvasDataMap(wrapper)
    expect(dataMap['script-table'].queryStatus).toBe('success')
  })

  it('失败终态立即停止轮询并将组件置为 error', async () => {
    mocks.getExecutionStatus.mockResolvedValue({ status: 'FAILED', error: '脚本崩溃' })
    wrapper = await mountPreview()
    await flushPromises()

    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(1)
    const dataMap = canvasDataMap(wrapper)
    expect(dataMap['script-table'].queryStatus).toBe('error')
    expect(dataMap['script-table'].error).toContain('FAILED')
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(1)
  })

  it('超时后组件状态可见（error）且轮询停止', async () => {
    mocks.getExecutionStatus.mockResolvedValue({ status: 'RUNNING' })
    wrapper = await mountPreview()
    await flushPromises()
    // 120 次上限 + 退避总时长约 10 分钟，一次性推进到位
    await vi.advanceTimersByTimeAsync(700_000)
    await flushPromises()

    expect(mocks.getExecutionStatus).toHaveBeenCalledTimes(120)
    const dataMap = canvasDataMap(wrapper)
    // 「脚本执行等待超时」消息会被超时识别为 timeout 状态，对用户可见
    expect(dataMap['script-table'].queryStatus).toBe('timeout')
    expect(dataMap['script-table'].error).toContain('超时')
  })

  it('组件卸载后轮询停止且无残留定时器', async () => {
    mocks.getExecutionStatus.mockResolvedValue({ status: 'RUNNING' })
    wrapper = await mountPreview()
    await flushPromises()
    expect(vi.getTimerCount()).toBeGreaterThan(0)

    const callsAtUnmount = mocks.getExecutionStatus.mock.calls.length
    wrapper.unmount()
    wrapper = undefined
    await vi.advanceTimersByTimeAsync(60000)
    expect(mocks.getExecutionStatus.mock.calls.length).toBe(callsAtUnmount)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('筛选变化产生新执行时，旧轮询被替代且新结果正常回填', async () => {
    mocks.executeComponent.mockReset()
    mocks.executeComponent
      .mockResolvedValueOnce({ executionId: 'exec-1' })
      .mockResolvedValueOnce({ executionId: 'exec-2' })
    mocks.getExecutionStatus.mockImplementation(async (executionId: string) => {
      if (executionId === 'exec-2') {
        return { status: 'SUCCEEDED', result: tableEnvelope([{ region: '华南' }]) }
      }
      return { status: 'RUNNING' }
    })
    wrapper = await mountPreview()
    await flushPromises()
    expect(canvasDataMap(wrapper)['script-table'].queryStatus).toBe('loading')

    // 触发筛选变化：300ms 防抖后重新执行脚本
    await wrapper.get('[data-test="change"]').trigger('click')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()

    expect(mocks.executeComponent).toHaveBeenCalledTimes(2)
    const secondCall = mocks.executeComponent.mock.calls[1]
    expect(JSON.stringify(secondCall)).toContain('华南')

    await vi.advanceTimersByTimeAsync(60000)
    await flushPromises()
    const dataMap = canvasDataMap(wrapper)
    expect(dataMap['script-table'].queryStatus).toBe('success')
  })
})
