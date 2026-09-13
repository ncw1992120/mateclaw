import type { InsightDashboardSchema } from '@/types'

type SchemaRecord = Record<string, unknown>

function asRecord(value: unknown): SchemaRecord {
  return value !== null && typeof value === 'object' ? value as SchemaRecord : {}
}

function generatePageId(): string {
  return `page_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

/**
 * 将旧版 components Schema 适配为当前 pages + datasetInputs Schema。
 * 该函数只负责读取兼容，不把旧仪表盘强行写回新格式。
 */
export function migrateInsightDashboardSchema(parsed: unknown, firstPageName: string): InsightDashboardSchema {
  const value = asRecord(parsed)
  const pages = value.pages
  if (Array.isArray(pages)) {
    return {
      version: typeof value.version === 'string' ? value.version : '1.0',
      pages: pages as InsightDashboardSchema['pages'],
      datasetInputs: Array.isArray(value.datasetInputs) ? value.datasetInputs as InsightDashboardSchema['datasetInputs'] : [],
      script: typeof value.script === 'string' ? value.script : undefined,
      parameters: Array.isArray(value.parameters) ? value.parameters as InsightDashboardSchema['parameters'] : [],
      executionPolicy: value.executionPolicy && typeof value.executionPolicy === 'object'
        ? value.executionPolicy as InsightDashboardSchema['executionPolicy']
        : {},
      scriptBindings: Array.isArray(value.scriptBindings)
        ? value.scriptBindings as InsightDashboardSchema['scriptBindings']
        : [],
    }
  }
  return {
    version: '1.1',
    pages: [{
      id: generatePageId(),
      name: firstPageName,
      components: Array.isArray(value.components) ? value.components as InsightDashboardSchema['pages'][number]['components'] : [],
    }],
    datasetInputs: [],
    parameters: [],
    executionPolicy: {},
    scriptBindings: [],
  }
}
