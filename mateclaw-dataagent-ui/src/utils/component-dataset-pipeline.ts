import type {
  ComponentDatasetPipeline,
  ComponentResultSet,
  DashboardScriptFilterBinding,
  DashboardSystemScriptState,
  InsightComponent,
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
    resultSet: readResultSet(value.resultSet),
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
        // 结果集元数据持久化：重开仪表盘时据此回显或刷新（决策 B）
        ...(pipeline.resultSet ? { resultSet: pipeline.resultSet } : {}),
      },
    },
  }
}
