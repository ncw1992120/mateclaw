import api from './index'
import type { SemanticModel, SemanticModelCreateRequest, SemanticModelUpdateRequest } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/semantic-models'

/** 查询语义模型列表 */
export function list(datasourceId: string, tableNames?: string) {
  return api.get<SemanticModel[]>(BASE_URL, {
    params: { datasourceId, tableNames },
  })
}

/** 获取语义模型详情 */
export function get(id: string) {
  return api.get<SemanticModel>(`${BASE_URL}/${id}`)
}

/** 创建语义模型 */
export function create(data: SemanticModelCreateRequest) {
  return api.post<SemanticModel>(BASE_URL, data)
}

/** 更新语义模型 */
export function update(id: string, data: SemanticModelUpdateRequest) {
  return api.put<SemanticModel>(`${BASE_URL}/${id}`, data)
}

/** 删除语义模型 */
export function remove(id: string) {
  return api.delete(`${BASE_URL}/${id}`)
}

/** 启用语义模型 */
export function enable(id: string) {
  return api.put(`${BASE_URL}/${id}/enable`)
}

/** 停用语义模型 */
export function disable(id: string) {
  return api.put(`${BASE_URL}/${id}/disable`)
}

/** 关键词搜索语义模型 */
export function search(datasourceId: string, keyword: string) {
  return api.get<SemanticModel[]>(`${BASE_URL}/search`, {
    params: { datasourceId, keyword },
  })
}

/** 从物理 Schema 自动初始化语义模型 */
export function autoInit(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/auto-init`, null, {
    params: { datasourceId },
  })
}

/** 从 Aloudata 指标平台同步语义模型 */
export function syncFromAloudata(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/sync-aloudata`, null, { params: { datasourceId } })
}

/** 查询已同步的 Aloudata 指标列表（从数据库查） */
export function listAloudataMetrics(datasourceId: string, pageNumber = 1, pageSize = 100) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/synced-metrics`, {
    params: { pageNumber, pageSize },
  })
}

/** 查询已同步的 Aloudata 维度列表（从数据库查） */
export function listAloudataDimensions(datasourceId: string, pageNumber = 1, pageSize = 100) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/synced-dimensions`, {
    params: { pageNumber, pageSize },
  })
}

/** 查询已同步的 Aloudata 类目列表 */
export function listAloudataCategories(datasourceId: string, categoryType?: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/synced-categories`, {
    params: categoryType ? { categoryType } : {},
  })
}

/** 查询指标关联的维度名称列表 */
export function listMetricDimensions(datasourceId: string, metricName: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/metrics/${encodeURIComponent(metricName)}/dimensions`)
}

/** 查询指标关联的维度详情列表（含展示名、描述等） */
export function listMetricDimensionDetails(datasourceId: string, metricName: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/metrics/${encodeURIComponent(metricName)}/dimension-details`)
}

/** 查询维度关联的指标详情列表（含展示名、业务口径等） */
export function listDimensionMetricDetails(datasourceId: string, dimName: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/dimensions/${encodeURIComponent(dimName)}/metric-details`)
}

/** 按类目分组查询指标列表（后端分组） */
export function listMetricsGroupedByCategory(datasourceId: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/metrics/grouped`)
}

/** 按类目分组查询维度列表（后端分组） */
export function listDimensionsGroupedByCategory(datasourceId: string) {
  return api.get(`/dataagent/api/v1/datasources/${datasourceId}/aloudata/dimensions/grouped`)
}
