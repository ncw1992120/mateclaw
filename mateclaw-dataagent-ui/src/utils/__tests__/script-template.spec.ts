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
})
