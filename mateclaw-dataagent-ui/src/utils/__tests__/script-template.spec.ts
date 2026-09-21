import { describe, expect, it } from 'vitest'
import { buildSystemScript, mergeBaseScript, SYSTEM_SCRIPT_START, USER_SCRIPT_START } from '../script-template'

describe('script template', () => {
  const inputs = [
    { datasetId: '1', inputName: 'orders' },
    { datasetId: '2', inputName: 'metrics' },
  ]

  it('generates eq, in and between filters for declared parameter types', () => {
    const script = buildSystemScript(inputs, [
      { name: 'status', type: 'string', scope: 'dashboard' },
      { name: 'regions', type: 'string[]', scope: 'dashboard' },
      { name: 'date_range', type: 'date_range', scope: 'dashboard' },
    ])
    expect(script).toContain('"operator": "eq"')
    expect(script).toContain('"operator": "in"')
    expect(script).toContain('"operator": "between"')
    expect(script).toContain('value.get("start")')
    expect(script.match(/datasets\.read\(/g)).toHaveLength(2)
  })

  it('replaces only the system block and preserves user code', () => {
    const old = `${SYSTEM_SCRIPT_START}\nold\n# ===== 系统生成区域结束 =====\n\n${USER_SCRIPT_START}\nresult = orders.join(metrics)`
    const merged = mergeBaseScript(old, buildSystemScript(inputs, []))
    expect(merged).toContain('input_name="orders"')
    expect(merged).toContain('result = orders.join(metrics)')
    expect(merged).not.toContain('\nold\n')
  })

  it('includes dataset-level source filters in the generated read call', () => {
    const script = buildSystemScript([
      { datasetId: '1', inputName: 'orders', filters: [{ field: 'status', role: 'dimension', operator: 'eq', value: 'PAID' }] },
      { datasetId: '2', inputName: 'metrics' },
    ], [])
    expect(script).toContain('"field": "status"')
    expect(script).toContain('"value": "PAID"')
  })

  it('scopes page parameters to bound inputs instead of broadcasting them', () => {
    const script = buildSystemScript(inputs, [
      { name: 'region', type: 'string', scope: 'dashboard' },
      { name: 'status', type: 'string', scope: 'dashboard' },
    ], [{ filterComponentId: 'filter-1', inputNames: ['orders'], fieldMappings: { orders: 'region' } }])
    const reads = script.split('datasets.read(').slice(1)
    expect(reads[0]).toContain('"region"')
    expect(reads[0]).not.toContain('"status"')
    expect(reads[1]).not.toContain('"region"')
    expect(reads[1]).not.toContain('"status"')
  })

  it('uses explicit bound operators and parameter names for optional runtime filters', () => {
    const script = buildSystemScript(
      [{ datasetId: '1', inputName: 'orders' }],
      [],
      [{
        filterComponentId: 'filter-1',
        inputNames: ['orders'],
        fieldMappings: { orders: 'metric_time' },
        conditions: [
          { inputName: 'orders', field: 'metric_time', operator: 'gte', parameterNames: ['startDate'] },
          { inputName: 'orders', field: 'metric_time', operator: 'lt', parameterNames: ['endDate'] },
        ],
      }],
    )
    expect(script).toContain('_optional_filter("metric_time", "gte", "startDate")')
    expect(script).toContain('_optional_filter("metric_time", "lt", "endDate")')
    expect(script).toContain('未填写时不下推')
  })
})
