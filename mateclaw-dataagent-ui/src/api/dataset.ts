import api from './index'
import type { Dataset, DatasetField, DatasetData, DatasetColumnDef, DatasetInputDescriptor, DatasetReadRequest, DatasetBatch, QueryContext, DatasetQueryConfig, DatasetQueryPlanSummary } from '@/types'
import type { DatasetSourceType, DatasetFilter } from '@/types'

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
export function create(data: {
  name: string
  description?: string
  datasourceId: string
  tableIds: string[]
  sourceDefinition?: Record<string, unknown>
}) {
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

/** 获取脚本/统一预览使用的数据集输入描述（不返回连接凭据） */
export function getInputDescriptor(datasetId: string, inputName: string = 'dataset') {
  return api.get<DatasetInputDescriptor>(`${BASE_URL}/${datasetId}/descriptor`, { params: { inputName } })
}

/** 使用统一 DatasetSourceAdapter 预览数据，并返回下推审计结果 */
export function previewInput(request: DatasetReadRequest) {
  return api.post<DatasetBatch>(`${BASE_URL}/preview`, request)
}

/** 查询计划预览请求：datasetId + 运行时 QueryContext +（可选）未保存的查询配置草稿 */
export interface QueryPlanPreviewRequest {
  datasetId: string
  inputName?: string
  queryContext: QueryContext
  queryConfig?: DatasetQueryConfig
}

/** 查询计划预览响应：plan / descriptor / rows / pushdownReport / totalCount?（预览不落库） */
export interface QueryPlanPreviewResponse {
  plan: DatasetQueryPlanSummary
  descriptor: DatasetInputDescriptor
  rows: Record<string, unknown>[]
  rowCount: number
  pushdownReport?: Record<string, unknown> | null
  totalCount?: number
}

/**
 * 查询计划预览：编辑器验证单个数据集计划并取回受控行集。
 * 显式空集合短路时 rows 为空且不访问数据源。
 */
export function previewQueryPlan(request: QueryPlanPreviewRequest) {
  return api.post<QueryPlanPreviewResponse>(`${BASE_URL}/query-plan/preview`, request)
}

export interface DatasetComposerDraftRequest {
  sourceType: DatasetSourceType | string
  datasourceId?: string
  sourceConfig?: Record<string, unknown>
  filters?: DatasetFilter[]
  limit?: number
  /** 分页偏移（配合 limit 做服务端滚动加载） */
  offset?: number
  /**
   * SQL 命名参数值（对应 baseSql 里的 `:name` 占位符）。
   * 与 filters 是两回事：filters 按列名追加外层谓词，parameters 绑进 SQL 内部的占位符，互不替代。
   */
  parameters?: Record<string, unknown>
}

export function previewDraft(request: DatasetComposerDraftRequest) {
  return api.post<DatasetBatch & { executionId?: string }>(
    '/dataagent/api/v1/dataset-composer/drafts/preview', request,
  )
}

export function confirmDraft(request: DatasetComposerDraftRequest & { name: string; description?: string }) {
  return api.post<{ datasetId: string; descriptor: DatasetInputDescriptor }>(
    '/dataagent/api/v1/dataset-composer/drafts/confirm', request,
  )
}

export function getComposerExecution(executionId: string) {
  return api.get<Record<string, unknown>>(`/dataagent/api/v1/dataset-composer/executions/${encodeURIComponent(executionId)}`)
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

/** 上传文件并返回受控对象引用；文件内容不经过页面 JSON。 */
export function uploadFile(file: File) {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ objectId: string; fileName?: string; format?: string }>(
    '/dataagent/api/v1/dataset-files',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}

/** 登记受控 HTTP 接口定义；连接凭据和 allowlist 仍由数据源配置持有。 */
export function registerApiDefinition(data: {
  datasourceId: string
  endpoint: string
  method: string
  allowedQueryParams?: string[]
  allowedBodyParams?: string[]
  resultPath?: string
  headers?: Record<string, string>
}) {
  return api.post<{ apiDefinitionId: string }>('/dataagent/api/v1/dataset-composer/api-definitions', data)
}
