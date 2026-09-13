import { describe, expect, it } from 'vitest'
import { migrateInsightDashboardSchema } from '@/utils/dashboard-schema'

describe('dashboard schema compatibility', () => {
  it('migrates the legacy components shape into one page without losing components', () => {
    const schema = migrateInsightDashboardSchema({
      version: '1.0',
      components: [{ id: 'legacy-table', type: 'table' }],
    }, '首页')

    expect(schema.version).toBe('1.1')
    expect(schema.pages).toHaveLength(1)
    expect(schema.pages[0].name).toBe('首页')
    expect(schema.pages[0].components[0].id).toBe('legacy-table')
    expect(schema.datasetInputs).toEqual([])
  })

  it('preserves dataset script fields when the schema already has pages', () => {
    const schema = migrateInsightDashboardSchema({
      version: '1.1',
      pages: [{ id: 'page-1', name: '销售', components: [] }],
      datasetInputs: [{ datasetId: '9', inputName: 'orders' }],
      script: 'result = []',
      parameters: [{ name: 'region', type: 'string', scope: 'dashboard' }],
      executionPolicy: { timeoutSeconds: 30 },
      scriptBindings: [{ componentId: 'table-1', renderType: 'table' }],
    }, '首页')

    expect(schema.version).toBe('1.1')
    expect(schema.datasetInputs?.[0].inputName).toBe('orders')
    expect(schema.script).toBe('result = []')
    expect(schema.executionPolicy?.timeoutSeconds).toBe(30)
    expect(schema.scriptBindings?.[0].componentId).toBe('table-1')
  })
})
