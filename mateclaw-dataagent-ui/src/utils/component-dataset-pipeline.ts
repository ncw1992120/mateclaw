import type { ComponentDatasetPipeline, ComponentResultSet, InsightComponent } from '@/types'

const PIPELINE_KEY = 'datasetPipeline'

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}
/** 只读取当前组件的数据集编排，不把旧根级输入猜测分配给组件。 */
export function readComponentDatasetPipeline(component: InsightComponent | null | undefined): ComponentDatasetPipeline | undefined {
  if (!component) return undefined
  const config = asRecord(component.config)
  const value = asRecord(config[PIPELINE_KEY])
  if (!Array.isArray(value.datasetInputs)) return undefined
  return {
    datasetInputs: value.datasetInputs as ComponentDatasetPipeline['datasetInputs'],
    scriptFilterBindings: Array.isArray(value.scriptFilterBindings) ? value.scriptFilterBindings as ComponentDatasetPipeline['scriptFilterBindings'] : [],
    script: typeof value.script === 'string' ? value.script : undefined,
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
        scriptFilterBindings: pipeline.scriptFilterBindings || [],
        script: pipeline.script,
        parameters: pipeline.parameters || [],
        executionPolicy: pipeline.executionPolicy || {},
        // 结果集元数据持久化：重开仪表盘时据此回显或刷新（决策 B）
        ...(pipeline.resultSet ? { resultSet: pipeline.resultSet } : {}),
      },
    },
  }
}
