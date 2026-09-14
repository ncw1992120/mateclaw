import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import DatasetEdit from '../DatasetEdit.vue'

const datasourceListMock = vi.hoisted(() => vi.fn())
const listTablesMock = vi.hoisted(() => vi.fn())
const listAnalysisViewsMock = vi.hoisted(() => vi.fn())
const createDatasetMock = vi.hoisted(() => vi.fn())
const syncDataMock = vi.hoisted(() => vi.fn())
const uploadFileMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/datasource', () => ({
  list: datasourceListMock,
  listTables: listTablesMock,
  listAnalysisViews: listAnalysisViewsMock,
}))

vi.mock('@/api/dataset', () => ({
  create: createDatasetMock,
  syncData: syncDataMock,
  get: vi.fn().mockResolvedValue({}),
  listFields: vi.fn().mockResolvedValue([]),
  getDatasetData: vi.fn().mockResolvedValue({ columns: [], rows: [], total: 0 }),
  update: vi.fn(),
  uploadFile: uploadFileMock,
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

async function mountEditor() {
  uploadFileMock.mockClear()
  datasourceListMock.mockResolvedValue([{ id: '1', name: 'Orders JDBC', connectionParams: JSON.stringify({ apiDefinitions: { orders: { name: '订单 API' } } }) }])
  listTablesMock.mockResolvedValue([])
  listAnalysisViewsMock.mockResolvedValue([])
  createDatasetMock.mockResolvedValue({ id: 'dataset-1' })
  syncDataMock.mockResolvedValue({ status: 'ok' })
  const wrapper = mount(DatasetEdit, {
    props: { mode: 'config' },
    global: { plugins: [i18n] },
  })
  await flushPromises()
  return wrapper
}

describe('DatasetEdit source configuration', () => {
  it('uploads a file and fills the controlled object reference', async () => {
    const wrapper = await mountEditor()
    uploadFileMock.mockResolvedValue({ objectId: 'datasets/1/orders.csv', format: 'csv' })
    const selects = wrapper.findAll('select')
    await selects[1].setValue('FILE')
    const input = wrapper.find('input[type="file"]')
    const file = new File(['id,amount\n1,2'], 'orders.csv', { type: 'text/csv' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(uploadFileMock).toHaveBeenCalled()
    expect(wrapper.find('input[placeholder="上传后自动回填对象引用"]').element.value).toBe('datasets/1/orders.csv')
  })
  it('rejects unsupported or oversized files before upload', async () => {
    const wrapper = await mountEditor()
    const selects = wrapper.findAll('select')
    await selects[1].setValue('FILE')
    const input = wrapper.find('input[type="file"]')
    const file = new File(['x'], 'orders.exe', { type: 'application/octet-stream' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(uploadFileMock).not.toHaveBeenCalled()
  })
  it('offers every supported dataset source type', async () => {
    const wrapper = await mountEditor()
    const sourceTypeValues = wrapper.findAll('select')[1].findAll('option').map(option => option.element.value)

    expect(sourceTypeValues).toEqual([
      'JDBC_TABLE',
      'JDBC_SQL',
      'ALOUDATA_ANALYSIS_VIEW',
      'HTTP_API',
      'FILE',
    ])
  })

  it('shows SQL only for JDBC_SQL and persists the SQL source definition', async () => {
    const wrapper = await mountEditor()
    const selects = wrapper.findAll('select')
    await wrapper.find('input.name-input').setValue('Orders dataset')
    await selects[0].setValue('1')
    await selects[1].setValue('JDBC_SQL')
    await wrapper.find('textarea.sql-input').setValue('select id, amount from orders')
    await wrapper.find('button.finish-btn').trigger('click')
    await flushPromises()

    expect(createDatasetMock).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Orders dataset',
      sourceDefinition: {
        sourceType: 'JDBC_SQL',
        datasourceId: 1,
        sql: 'select id, amount from orders',
      },
    }))
  })

  it('does not render or submit SQL for a FILE source', async () => {
    const wrapper = await mountEditor()
    const selects = wrapper.findAll('select')
    await wrapper.find('input.name-input').setValue('Orders file')
    await selects[1].setValue('FILE')
    await wrapper.find('input[placeholder="上传后自动回填对象引用"]').setValue('object-1')
    expect(wrapper.find('textarea.sql-input').exists()).toBe(false)

    await wrapper.find('button.finish-btn').trigger('click')
    await flushPromises()

    expect(createDatasetMock).toHaveBeenCalledWith(expect.objectContaining({
      sourceDefinition: {
        sourceType: 'FILE',
        objectId: 'object-1',
        format: 'csv',
        schemaVersion: 1,
      },
    }))
  })

  it('persists HTTP API sources by registered definition id only', async () => {
    const wrapper = await mountEditor()
    const selects = wrapper.findAll('select')
    await wrapper.find('input.name-input').setValue('Orders API')
    await selects[0].setValue('1')
    await selects[1].setValue('HTTP_API')
    const apiDefinitionSelect = wrapper.findAll('select')[2]
    await apiDefinitionSelect.setValue('orders')

    expect(wrapper.find('input[placeholder="输入 URL"]').exists()).toBe(false)
    expect(wrapper.find('input[placeholder="输入 Header"]').exists()).toBe(false)

    await wrapper.find('button.finish-btn').trigger('click')
    await flushPromises()

    expect(createDatasetMock).toHaveBeenCalledWith(expect.objectContaining({
      sourceDefinition: {
        sourceType: 'HTTP_API',
        datasourceId: 1,
        apiDefinitionId: 'orders',
      },
    }))
  })

  it('persists Aloudata sources by the selected read-only analysis view', async () => {
    const wrapper = await mountEditor()
    const selects = wrapper.findAll('select')
    listAnalysisViewsMock.mockResolvedValueOnce([
      { viewName: 'local_sales_view', displayName: '本地销售视图' },
    ])
    await selects[0].setValue('1')
    await selects[1].setValue('ALOUDATA_ANALYSIS_VIEW')
    await flushPromises()

    const viewSelect = wrapper.findAll('select')[2]
    await viewSelect.setValue('local_sales_view')
    expect(wrapper.find('textarea.sql-input').exists()).toBe(false)

    await wrapper.find('input.name-input').setValue('Sales view')
    await wrapper.find('button.finish-btn').trigger('click')
    await flushPromises()

    expect(createDatasetMock).toHaveBeenCalledWith(expect.objectContaining({
      sourceDefinition: {
        sourceType: 'ALOUDATA_ANALYSIS_VIEW',
        datasourceId: 1,
        analysisViewId: 'local_sales_view',
      },
    }))
  })
})
