import { describe, expect, it } from 'vitest'
import {
  extractResultSchema,
  formatScriptResultError,
  fingerprintResultSchema,
  parseScriptResultEnvelope,
  resultEnvelopeToComponentData,
  tableEnvelopeFromRows,
  type ScriptResultColumn,
  type ScriptResultEnvelope,
} from '../script-result'

function column(name: string, dataType: ScriptResultColumn['dataType']): ScriptResultColumn {
  return { name, title: name, dataType, nullable: false }
}

function tableEnvelope(columns: ScriptResultColumn[], rows: Record<string, unknown>[]): ScriptResultEnvelope {
  return {
    schemaVersion: '1.0', kind: 'table',
    data: { columns, rows },
    meta: { rowCount: rows.length, truncated: false, sourceInputs: [] },
  }
}

function scalarEnvelope(value: number | string | boolean): ScriptResultEnvelope {
  const dataType = typeof value === 'number' ? 'number' : typeof value === 'boolean' ? 'boolean' : 'string'
  return { schemaVersion: '1.0', kind: 'scalar', data: { value, dataType }, meta: { rowCount: 0, truncated: false } }
}

function messageEnvelope(message: string): ScriptResultEnvelope {
  return { schemaVersion: '1.0', kind: 'message', data: { level: 'info', message }, meta: { rowCount: 0, truncated: false } }
}

function chartComponent(config: { dimensionField: string; metricFields: string[] }) {
  return { id: 'c1', type: 'chart', chartType: 'line', config }
}
function kpiComponent(config: { valueField?: string; aggregation?: 'sum' } = {}) {
  return { id: 'k1', type: 'kpi', config }
}
function tableComponent() {
  return { id: 't1', type: 'table' }
}

describe('parseScriptResultEnvelope', () => {
  it('接受合法 table/scalar/message', () => {
    expect(parseScriptResultEnvelope(tableEnvelope([column('id', 'number')], [{ id: 1 }])).kind).toBe('table')
    expect(parseScriptResultEnvelope(scalarEnvelope(42)).kind).toBe('scalar')
    expect(parseScriptResultEnvelope(messageEnvelope('ok')).kind).toBe('message')
  })

  it('拒绝未知版本/kind 与非法列类型', () => {
    expect(() => parseScriptResultEnvelope({ schemaVersion: '2.0', kind: 'table', data: {}, meta: { rowCount: 0 } })).toThrow(/schemaVersion/)
    expect(() => parseScriptResultEnvelope({ schemaVersion: '1.0', kind: 'chart', data: {}, meta: { rowCount: 0 } })).toThrow(/result\.kind/)
    expect(() => parseScriptResultEnvelope(tableEnvelope([{ name: 'x', title: 'x', dataType: 'object' as never, nullable: false }], []))).toThrow(/dataType/)
  })
})

describe('tableEnvelopeFromRows', () => {
  it('infers a typed output envelope for direct dataset preview rows', () => {
    const envelope = tableEnvelopeFromRows([{ region: '华东', amount: 12, active: true }])
    expect(envelope.data.columns.map(({ name, dataType }) => [name, dataType])).toEqual([
      ['region', 'string'], ['amount', 'number'], ['active', 'boolean'],
    ])
    expect(envelope.meta.rowCount).toBe(1)
  })
})

describe('result schema', () => {
  it('从 table 结果提取字段、类型、可空性和行数', () => {
    const envelope = tableEnvelope(
      [
        { name: 'region', title: 'region', dataType: 'string', nullable: false },
        { name: 'amount', title: 'amount', dataType: 'number', nullable: false },
      ],
      [{ region: '华东', amount: 10 }, { region: null, amount: 20 }],
    )
    const schema = extractResultSchema(envelope)
    expect(schema.kind).toBe('table')
    expect(schema.rowCount).toBe(2)
    expect(schema.columns).toEqual([
      { name: 'region', title: 'region', dataType: 'string', nullable: true },
      { name: 'amount', title: 'amount', dataType: 'number', nullable: false },
    ])
    expect(schema.fingerprint).toMatch(/^[0-9a-f]{8}$/)
  })

  it('空 table 仍保留声明的列并生成稳定指纹', () => {
    const a = extractResultSchema(tableEnvelope([{ name: 'amount', title: 'amount', dataType: 'number', nullable: false }], []))
    const b = extractResultSchema(tableEnvelope([{ name: 'amount', title: 'amount', dataType: 'number', nullable: false }], []))
    expect(a.rowCount).toBe(0)
    expect(a.columns).toEqual([{ name: 'amount', title: 'amount', dataType: 'number', nullable: true }])
    expect(fingerprintResultSchema(a)).toBe(fingerprintResultSchema(b))
  })
})

describe('resultEnvelopeToComponentData', () => {
  it('chart 从 table columns/rows 和组件配置生成 option', () => {
    const envelope = tableEnvelope(
      [column('day', 'date'), column('amount', 'number')],
      [{ day: '2026-09-20', amount: 12 }, { day: '2026-09-21', amount: 18 }],
    )
    const result = resultEnvelopeToComponentData(
      chartComponent({ dimensionField: 'day', metricFields: ['amount'] }),
      envelope,
    )
    expect(result.state).toBe('data')
    expect((result.option as Record<string, unknown>).xAxis).toEqual({ type: 'category', data: ['2026-09-20', '2026-09-21'] })
    expect((result.option as { series: unknown[] }).series[0]).toEqual({ name: 'amount', type: 'line', data: [12, 18] })
  })

  it('kpi 接受 scalar 或按配置从 table 聚合', () => {
    expect(resultEnvelopeToComponentData(kpiComponent(), scalarEnvelope(42)).value).toBe(42)
    expect(resultEnvelopeToComponentData(
      kpiComponent({ valueField: 'amount', aggregation: 'sum' }),
      tableEnvelope([column('amount', 'number')], [{ amount: 12 }, { amount: 18 }]),
    ).value).toBe(30)
  })

  it('kpi 脚本表格结果按组件配置渲染全部指标', () => {
    const result = resultEnvelopeToComponentData({
      ...kpiComponent(),
      kpiMetrics: [
        { fieldKey: 'orders', displayName: '订单数' },
        { fieldKey: 'revenue', displayName: '销售额' },
      ],
    }, tableEnvelope(
      [column('orders', 'number'), column('revenue', 'number')],
      [{ orders: 12, revenue: 360 }],
    ))
    expect(result.kpiList).toEqual([
      { fieldKey: 'orders', name: '订单数', value: 12 },
      { fieldKey: 'revenue', name: '销售额', value: 360 },
    ])
  })

  it('message 显示说明，不伪造成表格行', () => {
    const result = resultEnvelopeToComponentData(kpiComponent(), messageEnvelope('没有满足条件的数据'))
    expect(result.state).toBe('message')
    expect(result.message).toBe('没有满足条件的数据')
  })

  it('空 table 显示暂无数据', () => {
    const result = resultEnvelopeToComponentData(tableComponent(), tableEnvelope([], []))
    expect(result.state).toBe('empty')
    expect(result.rows).toEqual([])
  })

  it('table 组件输出与 DataTableWidget 渲染契约一致的字符串表格', () => {
    const result = resultEnvelopeToComponentData(
      tableComponent(),
      tableEnvelope([column('id', 'number')], [{ id: 1 }, { id: 2 }]),
    )
    expect(result.state).toBe('data')
    expect(result.table).toEqual({ columns: ['id'], rows: [['1'], ['2']] })
  })
})

describe('formatScriptResultError', () => {
  it('OUTPUT_CONTRACT_ERROR 显示阶段、路径和建议', () => {
    const output = formatScriptResultError({
      status: 'OUTPUT_CONTRACT_ERROR', stage: 'output-validation',
      path: 'result.data.rows[2].amount', expected: 'number', actual: 'string',
      suggestion: '统一 amount 列类型',
    })
    expect(output).toContain('result.data.rows[2].amount')
    expect(output).toContain('统一 amount 列类型')
    expect(output).toContain('数据格式不匹配')
  })

  it('资源类错误映射为可读文案', () => {
    expect(formatScriptResultError({ status: 'TIMEOUT' })).toContain('超时')
    expect(formatScriptResultError({ status: 'RESULT_LIMIT' })).toContain('超过限制')
  })
})
