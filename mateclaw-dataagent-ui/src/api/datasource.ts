import api from './index'
import type { Datasource, DatasourceTable, DatasourceColumn } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/datasources'

/** 查询数据源列表 */
export function list() {
  return api.get<Datasource[]>(BASE_URL)
}

/** 查询数据源详情 */
export function get(id: string) {
  return api.get<Datasource>(`${BASE_URL}/${id}`)
}

/** 创建数据源 */
export function create(data: Partial<Datasource>) {
  return api.post<Datasource>(BASE_URL, data)
}

/** 更新数据源 */
export function update(id: string, data: Partial<Datasource>) {
  return api.put<Datasource>(`${BASE_URL}/${id}`, data)
}

/** 删除数据源 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 测试数据源连接 */
export function testConnection(id: string) {
  return api.post(`${BASE_URL}/${id}/test`)
}

/** 测试数据源连接（不创建记录） */
export function testConnectionApi(data: Partial<Datasource>) {
  return api.post<boolean>(`${BASE_URL}/test`, data)
}

/** 切换数据源启用状态 */
export function toggle(id: string, enabled: boolean) {
  return api.put(`${BASE_URL}/${id}/toggle`, { enabled })
}

/** 触发 Schema 发现 */
export function triggerSchemaDiscovery(id: string) {
  return api.post<Datasource>(`${BASE_URL}/${id}/schema-discovery`)
}

/** 获取数据源下的表列表 */
export function listTables(datasourceId: string) {
  return api.get<DatasourceTable[]>(`${BASE_URL}/${datasourceId}/tables`)
}

/** 获取表详情（含字段列表） */
export function getTableDetail(datasourceId: string, tableId: string) {
  return api.get<DatasourceTable>(`${BASE_URL}/${datasourceId}/tables/${tableId}`)
}

/** 获取表字段列表 */
export function listColumns(datasourceId: string, tableId: string) {
  return api.get<DatasourceColumn[]>(`${BASE_URL}/${datasourceId}/tables/${tableId}/columns`)
}

/** 删除数据源下的表 */
export function deleteTable(datasourceId: string, tableId: string) {
  return api.delete(`${BASE_URL}/${datasourceId}/tables/${tableId}`)
}

/** 同步单张表元数据（支持追加/覆盖模式） */
export function syncTable(datasourceId: string, tableId: string, mode: string = 'append') {
  return api.post<DatasourceTable>(`${BASE_URL}/${datasourceId}/tables/${tableId}/sync`, { mode })
}

/** 预览表数据 */
export function previewTableData(datasourceId: string, tableId: string, limit: number = 100) {
  return api.get<{ columns: string[]; rows: Record<string, unknown>[]; total: number }>(
    `${BASE_URL}/${datasourceId}/tables/${tableId}/preview`,
    { params: { limit } }
  )
}

// ==================== Aloudata 语义层同步 ====================

/** 触发 Aloudata 语义层全量同步 */
export function syncAloudataSemantic(datasourceId: string | number) {
  return api.post<{
    metricCount: number
    dimensionCount: number
    metricDimensionCount: number
    categoryCount: number
    elapsedMs: number
    status: string
    message: string
  }>(`${BASE_URL}/${datasourceId}/aloudata/sync`)
}

/** 查询 Aloudata 同步状态 */
export function getAloudataSyncStatus(datasourceId: string | number) {
  return api.get<{
    metricCount: number
    dimensionCount: number
    metricDimensionCount: number
    categoryCount: number
    elapsedMs: number
    status: string
    message: string
  }>(`${BASE_URL}/${datasourceId}/aloudata/sync-status`)
}

/** 查询已同步的指标列表 */
export function listSyncedMetrics(datasourceId: string | number, pageNumber: number = 1, pageSize: number = 20, keyword?: string) {
  return api.get<{
    metricName: string
    metricDisplayName: string
    type: string
    businessCaliber: string
    synonyms: string[]
    metricCategoryName: string
    unit: string
    availableDimensions: string[]
  }[]>(`${BASE_URL}/${datasourceId}/aloudata/synced-metrics`, { params: { pageNumber, pageSize, keyword } })
}

/** 查询已同步的维度列表 */
export function listSyncedDimensions(datasourceId: string | number, pageNumber: number = 1, pageSize: number = 20, keyword?: string) {
  return api.get<{
    dimName: string
    dimDisplayName: string
    originDataType: string
    dimDescription: string
    synonyms: string[]
    configType: string
    isTimeDimension: boolean
    exampleValues: string
  }[]>(`${BASE_URL}/${datasourceId}/aloudata/synced-dimensions`, { params: { pageNumber, pageSize, keyword } })
}

/** 查询指标关联的维度列表 */
export function listMetricDimensions(datasourceId: string | number, metricName: string) {
  return api.get<string[]>(`${BASE_URL}/${datasourceId}/aloudata/metrics/${encodeURIComponent(metricName)}/dimensions`)
}

/** 批量查询多个指标关联的维度详情列表（去重合并，支持关键字过滤） */
export function listMetricsDimensionDetails(datasourceId: string | number, metricNames: string[], keyword?: string) {
  return api.get<{
    dimName: string
    dimDisplayName: string
    originDataType: string
    dimDescription: string
    synonyms: string[]
    configType: string
    isTimeDimension: boolean
    exampleValues: string
  }[]>(`${BASE_URL}/${datasourceId}/aloudata/metrics-dimension-details`, {
    params: { metricNames, keyword }
  })
}

/** 查询已同步的类目列表 */
export function listSyncedCategories(datasourceId: string | number, categoryType?: string) {
  return api.get<{
    id: number
    datasourceId: number
    categoryId: string
    categoryName: string
    categoryType: string
    parentId: string
    syncVersion: number
  }[]>(`${BASE_URL}/${datasourceId}/aloudata/synced-categories`, {
    params: categoryType ? { categoryType } : undefined,
  })
}

// ==================== 数据源用户查询账号 ====================

const ACCOUNT_BASE_URL = '/dataagent/api/v1/datasource-accounts'

/** 查询当前用户所有已绑定的查询账号 */
export function listDatasourceAccounts() {
  return api.get<DatasourceAccountVO[]>(ACCOUNT_BASE_URL)
}

/** 查询当前用户在指定数据源上绑定的查询账号 */
export function getDatasourceAccount(datasourceId: string | number) {
  return api.get<DatasourceAccountVO>(`${ACCOUNT_BASE_URL}/${datasourceId}`)
}

/** 创建或更新当前用户的查询账号绑定 */
export function upsertDatasourceAccount(data: { datasourceId: string | number; queryUsername: string; queryPassword: string }) {
  return api.post<DatasourceAccountVO>(ACCOUNT_BASE_URL, data)
}

/** 删除当前用户的查询账号绑定 */
export function deleteDatasourceAccount(datasourceId: string | number) {
  return api.delete(`${ACCOUNT_BASE_URL}/${datasourceId}`)
}

/** 测试当前用户的查询账号连接（支持传入临时账号参数进行预测试，不持久化） */
export function testDatasourceAccount(datasourceId: string | number, data?: { queryUsername: string; queryPassword: string }) {
  return api.post<boolean>(`${ACCOUNT_BASE_URL}/${datasourceId}/test`, data || {})
}

/** 数据源用户查询账号视图对象 */
export interface DatasourceAccountVO {
  id: number
  datasourceId: number
  datasourceName?: string
  datasourceType?: string
  queryUsername: string
  status: number
  lastTestTime?: string
  lastTestOk?: boolean
  createTime?: string
  updateTime?: string
}
