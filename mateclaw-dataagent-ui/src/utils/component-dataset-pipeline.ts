import type { ComponentDatasetPipeline, InsightComponent } from '@/types'

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
      },
    },
  }
}
