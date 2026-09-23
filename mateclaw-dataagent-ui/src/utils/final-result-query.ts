import type {
  FinalResultFilterField,
  FinalResultQueryConfig,
  FinalResultQueryContext,
  QueryDisplayField,
  QueryParameterBinding,
  QueryPaginationPolicy,
  QuerySortPolicy,
  QuerySortSpec,
} from '@/types'
import type { ComponentOutputSpec } from './component-output-spec'
import type { ResultSchema, ScriptDataType } from './script-result'

export type FinalResultQueryConfigStatus = 'missing' | 'ready' | 'stale' | 'conflict'

const STRING_OPERATORS: QueryParameterBinding['operator'][] = ['eq', 'neq', 'in', 'not_in', 'contains']
const NUMBER_OPERATORS: QueryParameterBinding['operator'][] = ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'between']
const BOOLEAN_OPERATORS: QueryParameterBinding['operator'][] = ['eq', 'neq']

function operatorsFor(dataType: ScriptDataType): QueryParameterBinding['operator'][] {
  if (dataType === 'number') return NUMBER_OPERATORS
  if (dataType === 'boolean') return BOOLEAN_OPERATORS
  return STRING_OPERATORS
}

function roleFor(dataType: ScriptDataType): QueryDisplayField['role'] {
  return dataType === 'number' ? 'measure' : 'dimension'
}

function defaultSortPolicy(): QuerySortPolicy {
  return { enabled: false, mode: 'single', allowedFields: [], defaultSort: null }
}

function defaultPaginationPolicy(): QueryPaginationPolicy {
  return { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false }
}

/** 从组件输出契约和真实结果 Schema 生成最终结果集查询配置草稿。 */
export function buildFinalResultQueryConfig(spec: ComponentOutputSpec, schema: ResultSchema): FinalResultQueryConfig {
  const displayFields = schema.columns.map((column): QueryDisplayField => ({
    field: column.name,
    title: column.title || column.name,
    role: roleFor(column.dataType),
    dataType: column.dataType,
  }))
  const filterFields = schema.columns.map((column): FinalResultFilterField => ({
    field: column.name,
    title: column.title || column.name,
    dataType: column.dataType,
    parameterName: column.name,
    operators: operatorsFor(column.dataType),
  }))
  // 组件契约已经在执行阶段校验；这里保留 spec 参数作为显式边界，避免未来调用方
  // 在没有可消费结果类型时误生成配置。
  const acceptedKind = spec.accepts.includes(schema.kind)
  if (!acceptedKind) {
    return {
      schemaFingerprint: schema.fingerprint,
      displayFields: [],
      filterFields: [],
      sortPolicy: defaultSortPolicy(),
      paginationPolicy: defaultPaginationPolicy(),
    }
  }
  return {
    schemaFingerprint: schema.fingerprint,
    displayFields,
    filterFields,
    sortPolicy: defaultSortPolicy(),
    paginationPolicy: defaultPaginationPolicy(),
  }
}

function configuredFields(config: FinalResultQueryConfig): string[] {
  return [
    ...config.displayFields.map((field) => field.field),
    ...config.filterFields.map((field) => field.field),
    ...config.sortPolicy.allowedFields,
  ]
}

/** 判断结果 Schema 是否仍可安全迁移当前最终结果查询配置。 */
export function finalResultQueryConfigStatus(
  config: FinalResultQueryConfig | undefined,
  schema: ResultSchema | undefined,
): FinalResultQueryConfigStatus {
  if (!config || !schema) return 'missing'
  const schemaByName = new Map(schema.columns.map((column) => [column.name, column]))
  for (const field of config.filterFields) {
    const current = schemaByName.get(field.field)
    if (!current || current.dataType !== field.dataType) return 'conflict'
  }
  for (const field of configuredFields(config)) {
    if (!schemaByName.has(field)) return 'conflict'
  }
  return config.schemaFingerprint === schema.fingerprint ? 'ready' : 'stale'
}

function allowedParameters(config: FinalResultQueryConfig): Set<string> {
  return new Set(config.filterFields.map((field) => field.parameterName))
}

function allowedSortFields(config: FinalResultQueryConfig): Set<string> {
  return new Set(config.sortPolicy.enabled ? config.sortPolicy.allowedFields : [])
}

/** 丢弃不在最终结果查询配置白名单中的运行时值。 */
export function normalizeFinalResultQueryContext(
  config: FinalResultQueryConfig,
  context: FinalResultQueryContext,
): FinalResultQueryContext {
  const parameters: Record<string, unknown> = {}
  const allowed = allowedParameters(config)
  Object.entries(context.parameters || {}).forEach(([name, value]) => {
    if (allowed.has(name)) parameters[name] = value
  })
  const sort: QuerySortSpec | null = context.sort && allowedSortFields(config).has(context.sort.field)
    ? context.sort
    : null
  const pagination = config.paginationPolicy.enabled && context.pagination
    && context.pagination.page >= 1
    && context.pagination.pageSize >= 1
    && context.pagination.pageSize <= config.paginationPolicy.maxPageSize
    ? context.pagination
    : null
  return { parameters, sort, pagination }
}
