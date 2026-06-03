import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Datasource, DatasourceTable, DatasourceColumn } from '@/types'
import * as datasourceApi from '@/api/datasource'

/** 数据源状态管理 */
export const useDatasourceStore = defineStore('datasource', () => {
  /** 数据源列表 */
  const datasources = ref<Datasource[]>([])
  /** 当前选中的数据源 */
  const currentDatasource = ref<Datasource | null>(null)
  /** 当前数据源的表列表 */
  const currentTables = ref<DatasourceTable[]>([])
  /** 当前选中表的详情 */
  const currentTableDetail = ref<DatasourceTable | null>(null)
  /** 当前表的字段列表 */
  const currentColumns = ref<DatasourceColumn[]>([])
  /** 加载状态 */
  const loading = ref(false)
  /** Schema 发现中 */
  const discovering = ref(false)

  /** 获取数据源列表 */
  async function fetchDatasources(): Promise<void> {
    loading.value = true
    try {
      const data = await datasourceApi.list()
      datasources.value = data as unknown as Datasource[]
    } finally {
      loading.value = false
    }
  }

  /** 选中数据源 */
  async function selectDatasource(id: string): Promise<void> {
    const data = await datasourceApi.get(id)
    currentDatasource.value = data as unknown as Datasource
  }

  /** 创建数据源 */
  async function createDatasource(data: Partial<Datasource>): Promise<void> {
    await datasourceApi.create(data)
    await fetchDatasources()
  }

  /** 更新数据源 */
  async function updateDatasource(id: string, data: Partial<Datasource>): Promise<void> {
    await datasourceApi.update(id, data)
    await fetchDatasources()
    if (currentDatasource.value?.id === id) {
      await selectDatasource(id)
    }
  }

  /** 删除数据源 */
  async function deleteDatasource(id: string): Promise<void> {
    await datasourceApi.remove(id)
    if (currentDatasource.value?.id === id) {
      currentDatasource.value = null
      currentTables.value = []
      currentTableDetail.value = null
      currentColumns.value = []
    }
    await fetchDatasources()
  }

  /** 测试数据源连接 */
  async function testConnection(id: string): Promise<void> {
    await datasourceApi.testConnection(id)
  }

  /** 切换数据源启用状态 */
  async function toggleDatasource(id: string, enabled: boolean): Promise<void> {
    await datasourceApi.toggle(id, enabled)
    await fetchDatasources()
    if (currentDatasource.value?.id === id) {
      await selectDatasource(id)
    }
  }

  /** 触发 Schema 发现 */
  async function triggerSchemaDiscovery(id: string): Promise<void> {
    discovering.value = true
    try {
      await datasourceApi.triggerSchemaDiscovery(id)
      await fetchDatasources()
      await selectDatasource(id)
      await fetchTables(id)
    } finally {
      discovering.value = false
    }
  }

  /** 获取数据源下的表列表 */
  async function fetchTables(datasourceId: string): Promise<void> {
    const data = await datasourceApi.listTables(datasourceId)
    currentTables.value = data as unknown as DatasourceTable[]
  }

  /** 获取表详情（含字段） */
  async function fetchTableDetail(datasourceId: string, tableId: string): Promise<void> {
    const data = await datasourceApi.getTableDetail(datasourceId, tableId)
    currentTableDetail.value = data as unknown as DatasourceTable
    currentColumns.value = (currentTableDetail.value?.columns || []) as unknown as DatasourceColumn[]
  }

  /** 获取表字段列表 */
  async function fetchColumns(datasourceId: string, tableId: string): Promise<void> {
    const data = await datasourceApi.listColumns(datasourceId, tableId)
    currentColumns.value = data as unknown as DatasourceColumn[]
  }

  /** 删除表 */
  async function deleteDatasourceTable(datasourceId: string, tableId: string): Promise<void> {
    await datasourceApi.deleteTable(datasourceId, tableId)
    await fetchTables(datasourceId)
    if (currentTableDetail.value?.id === tableId) {
      currentTableDetail.value = null
      currentColumns.value = []
    }
  }

  return {
    datasources,
    currentDatasource,
    currentTables,
    currentTableDetail,
    currentColumns,
    loading,
    discovering,
    fetchDatasources,
    selectDatasource,
    createDatasource,
    updateDatasource,
    deleteDatasource,
    testConnection,
    toggleDatasource,
    triggerSchemaDiscovery,
    fetchTables,
    fetchTableDetail,
    fetchColumns,
    deleteDatasourceTable,
  }
})
