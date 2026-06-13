import api from './index'
import type { SchemaSearchRequest, SchemaSearchResult } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/schema-search'

/** 为数据源生成 Schema 嵌入 */
export function embedSchema(datasourceId: string) {
  return api.post<number>(`${BASE_URL}/embed`, null, {
    params: { datasourceId },
  })
}

/** 为单张表生成 Schema 嵌入 */
export function embedTable(datasourceId: string, tableName: string) {
  return api.post<boolean>(`${BASE_URL}/embed-table`, null, {
    params: { datasourceId, tableName },
  })
}

/** 语义检索相关表 */
export function search(request: SchemaSearchRequest) {
  return api.post<SchemaSearchResult>(`${BASE_URL}/search`, request)
}

/** 删除数据源的所有 Schema 嵌入 */
export function deleteEmbed(datasourceId: string) {
  return api.delete(`${BASE_URL}/embed`, {
    params: { datasourceId },
  })
}

/** 预览表级嵌入文本 */
export function previewEmbeddingText(datasourceId: string, tableName: string) {
  return api.get<string>(`${BASE_URL}/embedding-text`, {
    params: { datasourceId, tableName },
  })
}
