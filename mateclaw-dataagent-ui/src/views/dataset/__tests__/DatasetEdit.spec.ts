import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import DatasetEdit from '../DatasetEdit.vue'

const datasourceListMock = vi.hoisted(() => vi.fn())
const listTablesMock = vi.hoisted(() => vi.fn())
const listAnalysisViewsMock = vi.hoisted(() => vi.fn())
const createDatasetMock = vi.hoisted(() => vi.fn())
const syncDataMock = vi.hoisted(() => vi.fn())

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
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': {} },
  missingWarn: false,
  fallbackWarn: false,
})

async function mountEditor() {
  datasourceListMock.mockResolvedValue([{ id: '1', name: 'Orders JDBC' }])
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
    await wrapper.find('input[placeholder="输入已登记的对象 ID"]').setValue('object-1')
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
})
