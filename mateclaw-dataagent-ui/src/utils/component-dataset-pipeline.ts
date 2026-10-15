import type {
  ComponentDatasetPipeline,
  ComponentResultSet,
  FinalResultQueryConfig,
  DashboardScriptFilterBinding,
  DashboardSystemScriptState,
  DatasetQueryConfig,
  InsightComponent,
  QueryDisplayField,
  QueryParameterBinding,
  QueryPaginationPolicy,
  QuerySortPolicy,
} from '@/types'

const PIPELINE_KEY = 'datasetPipeline'

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function readSystemScript(value: unknown): DashboardSystemScriptState | undefined {
  const raw = asRecord(value)
  if (raw.mode !== 'generated' && raw.mode !== 'managed') return undefined
  if (typeof raw.generatedCode !== 'string' || typeof raw.generatedFingerprint !== 'string' || typeof raw.userCode !== 'string') {
    return undefined
  }
  return {
    mode: raw.mode,
    generatedCode: raw.generatedCode,
    managedCode: typeof raw.managedCode === 'string' ? raw.managedCode : undefined,
    generatedFingerprint: raw.generatedFingerprint,
    userCode: raw.userCode,
  }
}

/** 展示名等表现层字段的宽松读取；field（技术字段名）缺失即丢弃。 */
function readDisplayField(value: unknown): QueryDisplayField | undefined {
  const raw = asRecord(value)
  if (typeof raw.field !== 'string' || !raw.field) return undefined
  const role = raw.role === 'measure' ? 'measure' : 'dimension'
  return {
    field: raw.field,
    title: typeof raw.title === 'string' && raw.title ? raw.title : raw.field,
    role,
    dataType: typeof raw.dataType === 'string' ? raw.dataType : undefined,
  }
}

function readParameterBinding(value: unknown): QueryParameterBinding | undefined {
  const raw = asRecord(value)
  if (typeof raw.filterComponentId !== 'string' || typeof raw.parameterName !== 'string' || typeof raw.field !== 'string' || typeof raw.operator !== 'string') {
    return undefined
  }
  return {
    filterComponentId: raw.filterComponentId,
    parameterName: raw.parameterName,
    field: raw.field,
    operator: raw.operator as QueryParameterBinding['operator'],
  }
}

function readQueryConfig(value: unknown): DatasetQueryConfig | undefined {
  const raw = asRecord(value)
  if (!Array.isArray(raw.displayFields) || !Array.isArray(raw.parameterBindings)) return undefined
  const displayFields = raw.displayFields.map(readDisplayField).filter((f): f is QueryDisplayField => Boolean(f))
  if (!displayFields.length) return undefined
  const queryableFields = Array.isArray(raw.queryableFields)
    ? raw.queryableFields.flatMap((value) => {
        const field = asRecord(value)
        if (typeof field.name !== 'string' || !field.name.trim()) return []
        const role = field.role === 'measure' ? 'measure' : 'dimension'
        return [{
          name: field.name,
          displayName: typeof field.displayName === 'string' ? field.displayName : undefined,
          role,
          dataType: typeof field.dataType === 'string' ? field.dataType : undefined,
        }]
      })
    : undefined
  const parameterBindings = raw.parameterBindings
    .map(readParameterBinding)
    .filter((b): b is QueryParameterBinding => Boolean(b))
  const sortRaw = asRecord(raw.sortPolicy)
  const sortPolicy: QuerySortPolicy = {
    enabled: sortRaw.enabled === true,
    mode: sortRaw.mode === 'multi' ? 'multi' : 'single',
    allowedFields: Array.isArray(sortRaw.allowedFields)
      ? sortRaw.allowedFields.filter((f): f is string => typeof f === 'string')
      : [],
    defaultSort: null,
  }
  const paginationRaw = asRecord(raw.paginationPolicy)
  const paginationPolicy: QueryPaginationPolicy = {
    enabled: paginationRaw.enabled === true,
    defaultPageSize: typeof paginationRaw.defaultPageSize === 'number' ? paginationRaw.defaultPageSize : 100,
    maxPageSize: typeof paginationRaw.maxPageSize === 'number' ? paginationRaw.maxPageSize : 500,
    returnTotalCount: paginationRaw.returnTotalCount === true,
  }
  return { displayFields, queryableFields, parameterBindings, sortPolicy, paginationPolicy }
}

/**
 * 旧 Schema 归一化草稿：把 scriptFilterBindings 转成 queryConfig.parameterBindings 的**展示草稿**，
 * 仅在编辑器读取层使用；未经用户在查询配置中确认不得写回，更不得静默改变已发布仪表盘的执行结果。
 */
export function draftQueryConfigFromLegacyBindings(bindings: DashboardScriptFilterBinding[] | undefined): DatasetQueryConfig {
  const parameterBindings: QueryParameterBinding[] = []
  for (const binding of bindings || []) {
    const conditions = binding.conditions || []
    if (conditions.length) {
      for (const condition of conditions) {
        for (const parameterName of condition.parameterNames || []) {
          if (parameterName) {
            parameterBindings.push({
              filterComponentId: binding.filterComponentId,
              parameterName,
              field: condition.field,
              operator: condition.operator as QueryParameterBinding['operator'],
            })
          }
        }
      }
    } else {
      // 更旧形态：filterComponentId 即参数名，fieldMappings[alias] 是字段
      parameterBindings.push({
        filterComponentId: binding.filterComponentId,
        parameterName: binding.filterComponentId,
        field: Object.values(binding.fieldMappings || {})[0] || '',
        operator: 'eq',
      })
    }
  }
  return {
    displayFields: [],
    parameterBindings,
    sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
    paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
  }
}

function readFilterBinding(value: unknown): DashboardScriptFilterBinding | undefined {
  const raw = asRecord(value)
  if (typeof raw.filterComponentId !== 'string') return undefined
  return {
    filterComponentId: raw.filterComponentId,
    conditions: Array.isArray(raw.conditions) ? raw.conditions as DashboardScriptFilterBinding['conditions'] : [],
    inputNames: Array.isArray(raw.inputNames) ? raw.inputNames.filter((item): item is string => typeof item === 'string') : [],
    fieldMappings: raw.fieldMappings && typeof raw.fieldMappings === 'object'
      ? raw.fieldMappings as Record<string, string>
      : undefined,
  }
}
/** 只读取当前组件的数据集编排，不把旧根级输入猜测分配给组件。 */
export function readComponentDatasetPipeline(component: InsightComponent | null | undefined): ComponentDatasetPipeline | undefined {
  if (!component) return undefined
  const config = asRecord(component.config)
  const value = asRecord(config[PIPELINE_KEY])
  if (!Array.isArray(value.datasetInputs)) return undefined
  const bindings = Array.isArray(value.scriptFilterBindings)
    ? value.scriptFilterBindings.map(readFilterBinding).filter((binding): binding is DashboardScriptFilterBinding => Boolean(binding))
    : []
  return {
    datasetInputs: value.datasetInputs as ComponentDatasetPipeline['datasetInputs'],
    scriptFilterBindings: bindings,
    script: typeof value.script === 'string' ? value.script : undefined,
    systemScript: readSystemScript(value.systemScript),
    parameters: Array.isArray(value.parameters) ? value.parameters as ComponentDatasetPipeline['parameters'] : [],
    executionPolicy: value.executionPolicy && typeof value.executionPolicy === 'object' ? value.executionPolicy as ComponentDatasetPipeline['executionPolicy'] : {},
    boundFilterComponentIds: Array.isArray(value.boundFilterComponentIds)
      ? value.boundFilterComponentIds.filter((item): item is string => typeof item === 'string')
      : undefined,
    resultSet: readResultSet(value.resultSet),
    finalResultQueryConfig: readFinalResultQueryConfig(value.finalResultQueryConfig),
  }
}

function readFinalResultQueryConfig(value: unknown): FinalResultQueryConfig | undefined {
  const raw = asRecord(value)
  if (typeof raw.schemaFingerprint !== 'string' || !Array.isArray(raw.displayFields) || !Array.isArray(raw.filterFields)) return undefined
  const sort = asRecord(raw.sortPolicy)
  const pagination = asRecord(raw.paginationPolicy)
  return {
    schemaFingerprint: raw.schemaFingerprint,
    confirmed: raw.confirmed === true,
    displayFields: raw.displayFields as FinalResultQueryConfig['displayFields'],
    filterFields: raw.filterFields as FinalResultQueryConfig['filterFields'],
    sortPolicy: {
      enabled: sort.enabled === true,
      mode: sort.mode === 'multi' ? 'multi' : 'single',
      allowedFields: Array.isArray(sort.allowedFields) ? sort.allowedFields.filter((field): field is string => typeof field === 'string') : [],
      defaultSort: sort.defaultSort && typeof sort.defaultSort === 'object' ? sort.defaultSort as FinalResultQueryConfig['sortPolicy']['defaultSort'] : null,
    },
    paginationPolicy: {
      enabled: pagination.enabled === true,
      defaultPageSize: typeof pagination.defaultPageSize === 'number' ? pagination.defaultPageSize : 100,
      maxPageSize: typeof pagination.maxPageSize === 'number' ? pagination.maxPageSize : 500,
      returnTotalCount: pagination.returnTotalCount === true,
    },
  }
}

/**
 * 读取持久化的结果集元数据；结构不合法（缺 columns / rowCount）时视为没有结果集，
 * 避免历史 schema 的半成品字段被当成可用结果集渲染到卡片。
 */
function readResultSet(value: unknown): ComponentResultSet | undefined {
  const raw = asRecord(value)
  if (!Array.isArray(raw.columns)) return undefined
  if (raw.source !== 'dataset' && raw.source !== 'script') return undefined
  if (raw.status !== 'ready' && raw.status !== 'failed') return undefined
  return {
    source: raw.source,
    status: raw.status,
    columns: raw.columns as ComponentResultSet['columns'],
    rowCount: typeof raw.rowCount === 'number' ? raw.rowCount : 0,
    generatedAt: typeof raw.generatedAt === 'string' ? raw.generatedAt : '',
    elapsedMs: typeof raw.elapsedMs === 'number' ? raw.elapsedMs : undefined,
    executionId: typeof raw.executionId === 'string' ? raw.executionId : undefined,
    error: typeof raw.error === 'string' ? raw.error : undefined,
  }
}

/** 返回新对象，避免在编辑器状态中共享配置引用。 */
export function writeComponentDatasetPipeline(component: InsightComponent, pipeline: ComponentDatasetPipeline): InsightComponent {
  return {
    ...component,
    config: {
      ...(component.config || {}),
      [PIPELINE_KEY]: {
        datasetInputs: pipeline.datasetInputs,
        scriptFilterBindings: (pipeline.scriptFilterBindings || []).map(binding => ({
          ...binding,
          conditions: binding.conditions || [],
        })),
        script: pipeline.script,
        ...(pipeline.systemScript ? { systemScript: pipeline.systemScript } : {}),
        parameters: pipeline.parameters || [],
        executionPolicy: pipeline.executionPolicy || {},
        // 绑定的筛选器组件 ID：Planner 据此校验绑定归属
        ...(pipeline.boundFilterComponentIds && pipeline.boundFilterComponentIds.length
          ? { boundFilterComponentIds: pipeline.boundFilterComponentIds }
          : {}),
        // 结果集元数据持久化：重开仪表盘时据此回显或刷新（决策 B）
        ...(pipeline.resultSet ? { resultSet: pipeline.resultSet } : {}),
        ...(pipeline.finalResultQueryConfig ? { finalResultQueryConfig: pipeline.finalResultQueryConfig } : {}),
      },
    },
  }
}
