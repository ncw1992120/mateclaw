import api from './index'
import type { Dataset, DatasetField, DatasetData, DatasetColumnDef } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/datasets'

/** 查询数据集列表 */
export function list() {
  return api.get<Dataset[]>(BASE_URL)
}

/** 查询数据集详情 */
export function get(id: string) {
  return api.get<Dataset>(`${BASE_URL}/${id}`)
}

/** 创建数据集 */
export function create(data: { name: string; description?: string; datasourceId: string; tableIds: string[] }) {
  return api.post<Dataset>(BASE_URL, data)
}

/** 更新数据集 */
export function update(id: string, data: Partial<Pick<Dataset, 'name' | 'description'>>) {
  return api.put<Dataset>(`${BASE_URL}/${id}`, data)
}

/** 删除数据集 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 获取数据集字段列表 */
export function listFields(datasetId: string) {
  return api.get<DatasetField[]>(`${BASE_URL}/${datasetId}/fields`)
}

/** 获取数据集数据（分页） */
export function getDatasetData(datasetId: string, page: number = 1, size: number = 50) {
  return api.get<DatasetData>(`${BASE_URL}/${datasetId}/data`, { params: { page, size } })
}

/** 更新数据集行数据 */
export function updateRow(datasetId: string, rowKey: Record<string, unknown>, values: Record<string, unknown>) {
  return api.put(`${BASE_URL}/${datasetId}/rows`, { rowKey, values })
}

/** 新增数据集行 */
export function addRow(datasetId: string, values: Record<string, unknown>) {
  return api.post(`${BASE_URL}/${datasetId}/rows`, { values })
}

/** 删除数据集行 */
export function deleteRow(datasetId: string, rowKey: Record<string, unknown>) {
  return api.delete(`${BASE_URL}/${datasetId}/rows`, { data: rowKey })
}

/** 更新字段分类 */
export function updateFieldCategory(fieldId: string, fieldCategory: string) {
  return api.put<DatasetField>(`${BASE_URL}/fields/${fieldId}/category`, { fieldCategory })
}

/** 同步数据集数据（从源表拉取数据并落库到本地业务数据表） */
export function syncData(datasetId: string) {
  return api.post<Dataset>(`${BASE_URL}/${datasetId}/sync`)
}