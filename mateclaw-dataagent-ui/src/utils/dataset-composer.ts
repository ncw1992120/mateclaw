import type { DashboardDatasetInput, DatasetFilter, DatasetSourceType } from '@/types'

export type DatasetComposerSource = DatasetSourceType | string

export function buildDatasetInput(
  datasetId: string,
  inputName: string,
  sourceType: DatasetComposerSource = 'JDBC_TABLE',
  sourceConfig: DashboardDatasetInput['sourceConfig'] = {},
): DashboardDatasetInput {
  return {
    datasetId,
    inputName,
    sourceType,
    sourceConfig: { ...sourceConfig },
    fieldMappings: [],
    filters: [],
  }
}

export function normalizeDatasetInput(input: DashboardDatasetInput): DashboardDatasetInput {
  return {
    ...input,
    sourceType: input.sourceType || 'JDBC_TABLE',
    sourceConfig: { ...(input.sourceConfig || {}) },
    fieldMappings: [...(input.fieldMappings || [])],
    filters: [...(input.filters || [])] as DatasetFilter[],
  }
}

/** 仅做前端快速校验，后端仍执行最终 AST 只读校验。 */
export function isReadonlySql(sql: string): boolean {
  const normalized = sql.trim().replace(/^\(+|\)+$/g, '')
  if (!normalized) return false
  if (!/^(select|with)\b/i.test(normalized)) return false
  const statements = normalized.split(';').map(statement => statement.trim()).filter(Boolean)
  return statements.length === 1 && !/\b(insert|update|delete|drop|alter|truncate|merge|grant|revoke|call)\b/i.test(normalized)
}
