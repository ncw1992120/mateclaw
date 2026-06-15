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
