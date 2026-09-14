import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DatasetInputPanel from '../DatasetInputPanel.vue'
import * as datasetApi from '@/api/dataset'

vi.mock('@/api/dataset', () => ({
  list: vi.fn().mockResolvedValue([]),
  getInputDescriptor: vi.fn().mockResolvedValue({ schema: [] }),
  previewInput: vi.fn().mockResolvedValue({ rows: [{ id: 1, status: 'PAID' }], total: 1 }),
}))

const executeMock = vi.hoisted(() => vi.fn())
const statusMock = vi.hoisted(() => vi.fn())
const cancelMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/insight-dashboard', () => ({
  execute: executeMock,
  getExecutionStatus: statusMock,
  getExecutionResult: vi.fn(),
  cancelExecution: cancelMock,
}))

const stubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-tooltip': { template: '<div><slot /><slot name="content" /></div>' },
  'el-alert': { template: '<div><slot /></div>' },
}

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(DatasetInputPanel, {
    props: { inputs: [], parameters: [], ...props },
    global: { stubs },
  })
}

describe('DatasetInputPanel', () => {
  it('adds a stable input alias and emits the updated list', async () => {
    const wrapper = mountPanel()
    const add = wrapper.findAll('button').find((button) => button.text().includes('添加'))
    expect(add).toBeDefined()
    await add!.trigger('click')
    expect(wrapper.emitted('update:inputs')?.[0]).toEqual([[{ datasetId: '', inputName: 'dataset_1' }]])
  })

  it('does not expose field-binding controls in the parameter editor', () => {
    const wrapper = mountPanel({ parameters: [{ name: 'region', type: 'string', scope: 'dashboard' }] })
    expect(wrapper.text()).toContain('不绑定数据集字段')
    expect(wrapper.findAll('button').some((button) => button.text().includes('绑定字段'))).toBe(false)
  })

  it('renders the script template action only as a draft action', () => {
    const wrapper = mountPanel({ inputs: [{ datasetId: '1', inputName: 'orders' }] })
    expect(wrapper.text()).toContain('插入读取模板')
    expect(wrapper.text()).not.toContain('自动执行脚本')
  })

  it('exposes an explicit result binding target selector', () => {
    const wrapper = mountPanel({
      targetComponents: [{ id: 'table-1', type: 'table', title: '订单表', position: { x: 0, y: 0, w: 4, h: 3 } }],
      targetComponentId: 'table-1',
    })
    expect(wrapper.text()).toContain('结果绑定组件')
    expect(wrapper.text()).toContain('当前目标组件：table-1')
  })

  it('blocks execution when script parameter definitions are invalid', async () => {
    const wrapper = mountPanel({
      dashboardId: 'dashboard-1',
      script: 'result = []',
      inputs: [{ datasetId: '1', inputName: 'orders' }],
      parameters: [
        { name: 'region', type: 'string', scope: 'dashboard' },
        { name: 'region', type: 'string', scope: 'page' },
      ],
    })
    const preview = wrapper.findAll('button').find((button) => button.text().includes('最终结果预览'))
    expect(preview).toBeDefined()
    await preview!.trigger('click')
    await nextTick()
    expect(executeMock).not.toHaveBeenCalled()
  })

  it('exposes cancel and retry paths for an asynchronous preview', async () => {
    executeMock.mockResolvedValue({ executionId: 'execution-1' })
    statusMock.mockResolvedValue({ status: 'RUNNING' })
    cancelMock.mockResolvedValue({ status: 'CANCELLED' })
    const wrapper = mountPanel({
      dashboardId: 'dashboard-1',
      script: 'result = []',
      inputs: [{ datasetId: '1', inputName: 'orders' }],
    })
    await wrapper.findAll('button').find((button) => button.text().includes('最终结果预览'))!.trigger('click')
    await nextTick()
    await nextTick()
    const cancel = wrapper.findAll('button').find((button) => button.text().includes('取消执行'))
    expect(cancel).toBeDefined()
    await cancel!.trigger('click')
    expect(cancelMock).toHaveBeenCalledWith('execution-1')
    await nextTick()
    expect(wrapper.text()).toContain('重试')
    wrapper.unmount()
  })

  it('hydrates empty descriptors from the dataset preview so fields are visible', async () => {
    const wrapper = mountPanel({ inputs: [{ datasetId: '1', inputName: 'orders' }] })
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(wrapper.text()).toContain('2 个字段')
    expect(wrapper.text()).toContain('id')
    expect(wrapper.text()).toContain('status')
  })

  it('does not show a misleading zero-field state while descriptor fallback is pending', async () => {
    vi.mocked(datasetApi.previewInput).mockImplementationOnce(() => new Promise(() => {}))
    const wrapper = mountPanel({ inputs: [{ datasetId: '1', inputName: 'orders' }] })
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(wrapper.text()).toContain('字段探测中')
    expect(wrapper.text()).not.toContain('0 个字段')
    wrapper.unmount()
  })

  it('shows a bounded-result notice when execution uses an output reference', async () => {
    executeMock.mockResolvedValue({ executionId: 'execution-ref' })
    statusMock.mockResolvedValue({ status: 'RESULT_REF' })
    const getExecutionResult = (await import('@/api/insight-dashboard')).getExecutionResult as ReturnType<typeof vi.fn>
    getExecutionResult.mockResolvedValue({ rows: [{ id: 1 }], inline: false, outputRef: { uri: 'object://result' } })
    const wrapper = mountPanel({ dashboardId: 'dashboard-1', script: 'result = []', inputs: [{ datasetId: '1', inputName: 'orders' }] })
    await wrapper.findAll('button').find((button) => button.text().includes('最终结果预览'))!.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(wrapper.text()).toContain('仅展示受限预览')
    expect(wrapper.text()).toContain('object://result')
    wrapper.unmount()
  })
})
