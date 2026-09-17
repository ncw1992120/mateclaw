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
  'el-option-group': { props: ['label'], template: '<optgroup :label="label"><slot /></optgroup>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-tooltip': { template: '<div><slot /><slot name="content" /></div>' },
  'el-alert': { props: ['title'], template: '<div><slot />{{ title }}</div>' },
}

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(DatasetInputPanel, {
    props: { inputs: [], parameters: [], ...props },
    global: { stubs },
  })
}

describe('DatasetInputPanel', () => {
  it('hides Python preprocessing until at least two datasets are selected', async () => {
    const wrapper = mountPanel({ inputs: [{ datasetId: '1', inputName: 'orders' }] })
    expect(wrapper.find('.script-draft').exists()).toBe(false)

    await wrapper.setProps({ inputs: [
      { datasetId: '1', inputName: 'orders' },
      { datasetId: '2', inputName: 'metrics' },
    ] })
    expect(wrapper.find('.script-draft').exists()).toBe(true)
    expect(wrapper.text()).toContain('已选择 2 个输入数据集')
  })

  it('groups dataset choices by Aloudata, JDBC, API and file categories', async () => {
    vi.mocked(datasetApi.list).mockResolvedValueOnce([
      { id: 'j1', name: '业务 MySQL', sourceType: 'mysql' },
      { id: 'a1', name: '销售指标视图', sourceType: 'ALOUDATA_ANALYSIS_VIEW' },
      { id: 'f1', name: '订单 Excel', sourceType: 'FILE' },
    ] as any)
    const wrapper = mountPanel({ inputs: [{ datasetId: '', inputName: 'input_1' }] })
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(wrapper.text()).toContain('Aloudata')
    expect(wrapper.text()).toContain('JDBC')
    expect(wrapper.text()).toContain('文件')
    expect(wrapper.text()).toContain('业务 MySQL')
  })

  it('opens the source picker before creating an input card', async () => {
    const wrapper = mountPanel()
    const add = wrapper.find('button[aria-label="添加数据集"]')
    expect(add).toBeDefined()
    await add!.trigger('click')
    await nextTick()
    expect(wrapper.find('.dataset-source-picker').exists()).toBe(true)
    expect(wrapper.emitted('update:inputs')).toBeUndefined()
  })

  it('does not expose field-binding controls in the parameter editor', () => {
    const wrapper = mountPanel({ parameters: [{ name: 'region', type: 'string', scope: 'dashboard' }] })
    expect(wrapper.text()).toContain('不绑定数据集字段')
    expect(wrapper.findAll('button').some((button) => button.text().includes('绑定字段'))).toBe(false)
  })

  it('renders the script template action only as a draft action', () => {
    const wrapper = mountPanel({ inputs: [
      { datasetId: '1', inputName: 'orders' },
      { datasetId: '2', inputName: 'metrics' },
    ] })
    expect(wrapper.text()).toContain('生成 Base Script')
    expect(wrapper.text()).not.toContain('自动执行脚本')
  })

  it('renders source-aware dataset cards with JDBC SQL and dataset actions', async () => {
    const wrapper = mountPanel({ inputs: [{ datasetId: 'j1', inputName: 'orders', sourceType: 'JDBC_SQL', sourceConfig: { sql: 'select 1' } }] })
    expect(wrapper.find('.dataset-source-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('JDBC SQL')
    expect(wrapper.text()).toContain('字段映射')
    expect(wrapper.text()).toContain('输入筛选')
    expect(wrapper.find('textarea[aria-label="数据集 SQL 查询"]').exists()).toBe(true)
  })

  it('keeps JDBC SQL configuration visible for a selected canvas component', () => {
    const wrapper = mountPanel({
      inputs: [{ datasetId: 'j1', inputName: 'orders', sourceType: 'JDBC_SQL', sourceConfig: { sql: 'select 1' } }],
      targetComponents: [{ id: 'kpi-1', type: 'kpi', title: '策略下发概览', position: { x: 0, y: 0, w: 4, h: 3 } }],
      targetComponentId: 'kpi-1',
    })
    expect(wrapper.find('textarea[aria-label="数据集 SQL 查询"]').exists()).toBe(true)
  })

  it('shows Aloudata mode and unified dataset guidance for API and file inputs', () => {
    const wrapper = mountPanel({ inputs: [
      { datasetId: 'a1', inputName: 'metric_view', sourceType: 'ALOUDATA_ANALYSIS_VIEW' },
      { datasetId: 'f1', inputName: 'file_rows', sourceType: 'FILE' },
    ] })
    expect(wrapper.text()).toContain('指标视图')
    expect(wrapper.text()).toContain('接口/文件数据集使用已创建的数据集')
  })

  it('separates generated system script from editable user script', () => {
    const wrapper = mountPanel({ inputs: [
      { datasetId: 'j1', inputName: 'orders' },
      { datasetId: 'a1', inputName: 'metrics' },
    ], script: '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） =====\norders = datasets.read()\n# ===== 系统生成区域结束 =====\n# ===== 用户处理区域：Join、合并、计算和业务规则 =====\nresult = orders' })
    expect(wrapper.find('.system-script-preview').exists()).toBe(true)
    expect(wrapper.find('textarea[aria-label="用户处理脚本"]').exists()).toBe(true)
  })

  it('generates a system script block without overwriting user code', async () => {
    const wrapper = mountPanel({
      inputs: [{ datasetId: '1', inputName: 'orders' }, { datasetId: '2', inputName: 'metrics' }],
      parameters: [{ name: 'status', type: 'string', scope: 'dashboard' }],
      script: 'result = orders.join(metrics)',
    })
    await wrapper.find('button[aria-label="生成 Python Base Script"]').trigger('click')
    const emitted = wrapper.emitted('update:script')?.at(-1)?.[0] as string
    expect(emitted).toContain('"operator": "eq"')
    expect(emitted).toContain('result = orders.join(metrics)')
    expect(emitted).toContain('系统生成区域')
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
      inputs: [
        { datasetId: '1', inputName: 'orders' },
        { datasetId: '2', inputName: 'metrics' },
      ],
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

  it('blocks two-input execution when only the generated system region exists', async () => {
    const wrapper = mountPanel({
      dashboardId: 'dashboard-1',
      inputs: [{ datasetId: '1', inputName: 'orders' }, { datasetId: '2', inputName: 'metrics' }],
      script: '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） =====\norders = datasets.read()\n# ===== 系统生成区域结束 =====',
    })
    await wrapper.findAll('button').find((button) => button.text().includes('最终结果预览'))!.trigger('click')
    await nextTick()
    expect(executeMock).not.toHaveBeenCalled()
    expect(wrapper.find('.execution-alert').text()).toContain('用户处理区域')
  })

  it('exposes cancel and retry paths for an asynchronous preview', async () => {
    executeMock.mockResolvedValue({ executionId: 'execution-1' })
    statusMock.mockResolvedValue({ status: 'RUNNING' })
    cancelMock.mockResolvedValue({ status: 'CANCELLED' })
    const wrapper = mountPanel({
      dashboardId: 'dashboard-1',
      script: 'result = []',
      inputs: [
        { datasetId: '1', inputName: 'orders' },
        { datasetId: '2', inputName: 'metrics' },
      ],
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
    const wrapper = mountPanel({ dashboardId: 'dashboard-1', script: 'result = []', inputs: [
      { datasetId: '1', inputName: 'orders' },
      { datasetId: '2', inputName: 'metrics' },
    ] })
    await wrapper.findAll('button').find((button) => button.text().includes('最终结果预览'))!.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(wrapper.text()).toContain('仅展示受限预览')
    expect(wrapper.text()).toContain('object://result')
    wrapper.unmount()
  })
})
