import { describe, expect, it } from 'vitest'
import { buildSystemScript, mergeBaseScript, SYSTEM_SCRIPT_START, USER_SCRIPT_START } from '../script-template'

/**
 * 系统区生成契约（实施计划任务 6 步骤 1/3）：
 * 唯一标准读取方式是 datasets.input(input_name="...").to_polars()；
 * 不再生成 datasets.read、columns=[]、datasets.params 或任何筛选/排序/分页拼接。
 */
describe('script template', () => {
  const inputs = [
    { datasetId: '1', inputName: 'orders' },
    { datasetId: '2', inputName: 'metrics' },
  ]

  it('generates datasets.input per input and never datasets.read / params / filters', () => {
    const script = buildSystemScript(inputs, [
      { name: 'status', type: 'string', scope: 'dashboard' },
      { name: 'regions', type: 'string[]', scope: 'dashboard' },
      { name: 'date_range', type: 'date_range', scope: 'dashboard' },
    ])
    expect(script.match(/datasets\.input\(/g)).toHaveLength(2)
    expect(script).toContain('input_name="orders"')
    expect(script).toContain(').to_polars()')
    // 旧入口与页面查询参数拼接全部移除
    expect(script).not.toContain('datasets.read(')
    expect(script).not.toContain('datasets.params')
    expect(script).not.toContain('columns=')
    expect(script).not.toContain('filters=')
    expect(script).not.toContain('"operator"')
  })

  it('replaces only the system block and preserves user code', () => {
    const old = `${SYSTEM_SCRIPT_START}\nold\n# ===== 系统生成区域结束 =====\n\n${USER_SCRIPT_START}\nresult = orders.join(metrics)`
    const merged = mergeBaseScript(old, buildSystemScript(inputs, []))
    expect(merged).toContain('input_name="orders"')
    expect(merged).toContain('result = orders.join(metrics)')
    expect(merged).not.toContain('\nold\n')
  })

  it('dataset-level filters no longer appear in the system region (they live in queryConfig)', () => {
    const script = buildSystemScript([
      { datasetId: '1', inputName: 'orders', filters: [{ field: 'status', role: 'dimension', operator: 'eq', value: 'PAID' }] },
      { datasetId: '2', inputName: 'metrics' },
    ], [])
    expect(script).not.toContain('"field": "status"')
    expect(script).not.toContain('filters=')
    expect(script.match(/datasets\.input\(/g)).toHaveLength(2)
  })

  it('bindings and parameters do not leak into the generated system region', () => {
    const script = buildSystemScript(inputs, [
      { name: 'region', type: 'string', scope: 'dashboard' },
      { name: 'status', type: 'string', scope: 'dashboard' },
    ], [{ filterComponentId: 'filter-1', inputNames: ['orders'], fieldMappings: { orders: 'region' } }])
    expect(script).not.toContain('"region"')
    expect(script).not.toContain('_optional_filter')
    // 绑定关系不影响逐输入读取形态
    expect(script.match(/datasets\.input\(/g)).toHaveLength(2)
  })

  it('explicit conditions do not produce runtime filters anymore', () => {
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
    expect(script).not.toContain('_optional_filter')
    expect(script).toContain('orders = datasets.input(')
    expect(script).toContain(').to_polars()')
  })

  it('empty inputs produce a placeholder comment', () => {
    const script = buildSystemScript([])
    expect(script).toContain('当前没有已绑定的数据集输入')
    expect(script).not.toContain('datasets.input(')
  })
})
