import type {
  FinalResultQueryConfig,
  FinalResultQueryContext,
  QueryDisplayField,
  QueryPaginationPolicy,
  QuerySortPolicy,
  QuerySortSpec,
} from '@/types'
import type { ComponentOutputSpec } from './component-output-spec'
import type { ResultSchema, ScriptDataType } from './script-result'

export type FinalResultQueryConfigStatus = 'missing' | 'ready' | 'stale' | 'conflict'

/** 默认字段顺序：维度在前、指标在后；相同角色保持传入顺序。 */
export function sortDisplayFieldsDimensionsFirst<T extends Pick<QueryDisplayField, 'role'>>(fields: T[]): T[] {
  return [...fields].sort((a, b) => Number(a.role === 'measure') - Number(b.role === 'measure'))
}

export function isFinalResultQueryConfigured(config: FinalResultQueryConfig | undefined): boolean {
  return config?.confirmed === true
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
  // 组件契约已经在执行阶段校验；这里保留 spec 参数作为显式边界，避免未来调用方
  // 在没有可消费结果类型时误生成配置。
  const acceptedKind = spec.accepts.includes(schema.kind)
  if (!acceptedKind) {
    return {
      schemaFingerprint: schema.fingerprint,
      confirmed: false,
      displayFields: [],
      filterFields: [],
      sortPolicy: defaultSortPolicy(),
      paginationPolicy: defaultPaginationPolicy(),
    }
  }
  return {
    schemaFingerprint: schema.fingerprint,
    confirmed: false,
    displayFields,
    // 结果字段不应自动成为筛选条件；只有用户绑定页面筛选器后才生成 filterFields。
    filterFields: [],
    sortPolicy: defaultSortPolicy(),
    paginationPolicy: defaultPaginationPolicy(),
  }
}

/** 刷新输出 Schema 时更新字段清单，但保留用户已经配置的最终结果查询规则。 */
export function preserveFinalResultQueryPreferences(
  previous: FinalResultQueryConfig | undefined,
  discovered: FinalResultQueryConfig,
): FinalResultQueryConfig {
  if (!previous) return discovered

  const previousFields = new Map(previous.displayFields.map((field) => [field.field, field]))
  return {
    ...discovered,
    confirmed: previous.confirmed,
    displayFields: discovered.displayFields.map((field) => {
      const saved = previousFields.get(field.field)
      return saved ? { ...field, title: saved.title } : field
    }),
    filterFields: previous.filterFields,
    sortPolicy: previous.sortPolicy,
    paginationPolicy: previous.paginationPolicy,
  }
}

/** 执行 Python 后按调用场景决定是否用新 Schema 同步查询配置。 */
export function resolveFinalResultQueryConfigAfterExecution(
  previous: FinalResultQueryConfig | undefined,
  discovered: FinalResultQueryConfig,
  syncDiscoveredConfig: boolean,
): FinalResultQueryConfig | undefined {
  return syncDiscoveredConfig
    ? preserveFinalResultQueryPreferences(previous, discovered)
    : previous
}

/** 查询刷新列元数据时按技术字段名恢复展示字段，并保留用户的展示名（包括显式清空）。 */
export function reconcileFinalResultDisplayFields(
  columns: Array<{ name: string; type: string; title?: string }>,
  rows: Record<string, unknown>[],
  previousFields: QueryDisplayField[],
): QueryDisplayField[] {
  const previousByField = new Map(previousFields.map((field) => [field.field, field]))
  return columns.map((column) => {
    const configured = previousByField.get(column.name)
    const sampleValue = rows[0]?.[column.name]
    const role = column.type === 'number' || typeof sampleValue === 'number'
      ? 'measure'
      : configured?.role ?? 'dimension'
    return {
      field: column.name,
      title: configured ? configured.title : column.title || column.name,
      role,
      dataType: column.type,
    }
  })
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
  for (const field of config.displayFields) {
    const current = schemaByName.get(field.field)
    if (!current || (field.dataType && current.dataType !== field.dataType)) return 'conflict'
  }
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
