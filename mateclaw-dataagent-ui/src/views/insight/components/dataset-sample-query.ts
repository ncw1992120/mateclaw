import { previewInput } from '@/api/dataset'
import type { DatasetFilter, QuerySortSpec } from '@/types'
import { extractApiParameters, extractSqlParameters } from '@/utils/parameter-extract'
import { buildExecutionParameters, toDatasetFilters, type RuntimeFilterRow } from '@/utils/runtime-filter-bindings'
import { getCachedQueryState } from './dataset-data-cache'
import { draftRequestForDataset, isPersistedBackendDatasetId, type DatasetConfig } from './card-attribute/useInsight'
import { previewDatasetDraft } from './card-attribute/useInsightBackend'

const DEFAULT_BATCH_SIZE = 50

function queryDisplayColumns(dataset: DatasetConfig): string[] {
  const fields = dataset.queryConfig?.displayFields ?? (dataset.fields ?? []).map((field) => ({
    field: field.name,
    title: field.displayName?.trim() || field.name,
    role: ['measure', 'metric'].includes((field.role ?? '').trim().toLowerCase()) ? 'measure' as const : 'dimension' as const,
  }))
  const state = dataset.lastQueryState ?? getCachedQueryState(dataset.id)
  const timeFields = new Set(state?.filters.filter((row) => row.timeBoundary).map((row) => row.field) ?? [])
  return fields
    .filter((field) => field.role !== 'dimension' || !timeFields.has(field.field))
    .map((field) => field.field)
}

function queryFilterRows(dataset: DatasetConfig, filterCatalog: Array<{ id: string; type?: string }>): RuntimeFilterRow[] {
  const bindings = dataset.queryConfig?.parameterBindings ?? []
  const queryState = dataset.lastQueryState ?? getCachedQueryState(dataset.id)
  const savedFilters = queryState?.filters ?? []

  return bindings.flatMap((binding) => {
    const filter = filterCatalog.find((item) => item.id === binding.filterComponentId)
    const saved = (boundary?: 'start' | 'end') => savedFilters.find((row) =>
      row.filterComponentId === binding.filterComponentId && row.field === binding.field && row.timeBoundary === boundary,
    )
    const toRow = (operator: RuntimeFilterRow['operator'], boundary?: 'start' | 'end'): RuntimeFilterRow => {
      const previous = saved(boundary)
      const parameterName = boundary === 'end' ? '' : previous?.parameterName || binding.parameterName
      return {
        inputName: dataset.alias,
        field: binding.field,
        operator,
        parameterNames: parameterName ? [parameterName] : [],
        parameterName,
        value: previous?.enabled === false ? undefined : previous?.value,
      }
    }
    return filter?.type === 'timeFilter'
      ? [toRow('gte', 'start'), toRow('lt', 'end')]
      : [toRow(binding.operator)]
  })
}

function queryParameters(dataset: DatasetConfig): Record<string, unknown> {
  const configured = dataset.sourceType === 'jdbc'
    ? extractSqlParameters(dataset.jdbc?.sql ?? '')
    : dataset.sourceType === 'api'
      ? extractApiParameters({ path: dataset.api?.path, headers: dataset.api?.headers, params: dataset.api?.params })
      : []
  const saved = dataset.lastQueryState?.parameters ?? getCachedQueryState(dataset.id)?.parameters ?? {}
  return Object.fromEntries(configured.flatMap((parameter) => {
    const value = Object.hasOwn(saved, parameter.name) ? saved[parameter.name] : parameter.defaultValue
    if (value === undefined || value === null || (typeof value === 'string' && !value.trim())) return []
    return [[parameter.name, value]]
  }))
}

function querySort(dataset: DatasetConfig): QuerySortSpec[] {
  const policy = dataset.queryConfig?.sortPolicy
  if (!policy?.enabled) return []
  const lastSort = dataset.lastQueryState?.sort ?? getCachedQueryState(dataset.id)?.sort
  const sort = lastSort && policy.allowedFields.includes(lastSort.field) ? lastSort : policy.defaultSort
  return sort && policy.allowedFields.includes(sort.field) ? [sort] : []
}

/** 通过与「查看数据 → 查询」相同的输入预览接口，取得真实结果行作为 Python 造数样例。 */
export async function fetchDatasetSampleRows(
  dataset: DatasetConfig,
  filterCatalog: Array<{ id: string; type?: string }>,
): Promise<{ rows: Record<string, unknown>[]; columns: string[] }> {
  const queryState = dataset.lastQueryState ?? getCachedQueryState(dataset.id)
  const policy = dataset.queryConfig?.paginationPolicy
  const paginationEnabled = policy?.enabled === true
  const pageSize = paginationEnabled
    ? Math.min(Math.max(1, queryState?.pageSize || policy.defaultPageSize || DEFAULT_BATCH_SIZE), Math.max(1, policy.maxPageSize || 500))
    : DEFAULT_BATCH_SIZE
  const page = paginationEnabled ? Math.max(1, queryState?.page || 1) : 1
  const runtimeRows = queryFilterRows(dataset, filterCatalog)
  const request = {
    columns: queryDisplayColumns(dataset),
    filters: toDatasetFilters(runtimeRows) as DatasetFilter[],
    orders: querySort(dataset),
    parameters: { ...queryParameters(dataset), ...buildExecutionParameters(runtimeRows) },
    limit: pageSize,
    offset: (page - 1) * pageSize,
  }

  let result
  if (isPersistedBackendDatasetId(dataset.backendDatasetId)) {
    result = await previewInput({
      datasetId: dataset.backendDatasetId,
      inputName: dataset.alias,
      ...request,
    })
  } else {
    const draft = draftRequestForDataset({ ...dataset, filters: request.filters as DatasetConfig['filters'] })
    if (!draft) throw new Error(`数据集「${dataset.alias}」暂不支持查询造数样例`)
    result = await previewDatasetDraft({ ...draft, ...request })
  }

  return {
    rows: (result.rows as Record<string, unknown>[] | null) ?? [],
    columns: result.schema ?? [],
  }
}
