import type { DatasetSourceType } from '@/types'

export interface DatasetSourceFormState {
  sourceType: DatasetSourceType
  datasourceId: string
  tableIds: string[]
  sql: string
  analysisViewId?: string
  apiDefinitionId?: string
  objectId?: string
  format?: string
  schemaVersion?: number
}

export function isJdbcSource(sourceType: string): boolean {
  return sourceType === 'JDBC_TABLE' || sourceType === 'JDBC_SQL'
}

/** 将页面状态转换为后端 sealed DatasetSourceDefinition；不把 SQL 带到非 JDBC 来源。 */
export function buildDatasetSourceDefinition(state: DatasetSourceFormState): Record<string, unknown> {
  switch (state.sourceType) {
    case 'JDBC_TABLE':
      return { sourceType: state.sourceType, datasourceId: Number(state.datasourceId), tableIds: state.tableIds }
    case 'JDBC_SQL':
      return { sourceType: state.sourceType, datasourceId: Number(state.datasourceId), sql: state.sql.trim() }
    case 'ALOUDATA_ANALYSIS_VIEW':
      return { sourceType: state.sourceType, datasourceId: Number(state.datasourceId), analysisViewId: state.analysisViewId || '' }
    case 'HTTP_API':
      return { sourceType: state.sourceType, datasourceId: Number(state.datasourceId), apiDefinitionId: state.apiDefinitionId || '' }
    case 'FILE':
      return { sourceType: state.sourceType, objectId: state.objectId || '', format: state.format || 'file', schemaVersion: state.schemaVersion || 1 }
  }
}
