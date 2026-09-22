import { describe, expect, it } from 'vitest'
import {
  chartOutputFamily,
  resolveOutputSpec,
  validateComponentOutput,
  type ComponentOutputSpec,
  type OutputValidationError,
} from '../component-output-spec'
import type { ScriptResultColumn, ScriptScalarEnvelope, ScriptTableEnvelope } from '../script-result'

function col(name: string, dataType: ScriptResultColumn['dataType'] = 'number'): ScriptResultColumn {
  return { name, title: name, dataType, nullable: true }
}

function tableEnv(columns: ScriptResultColumn[], rows: Record<string, unknown>[] = []): ScriptTableEnvelope {
  return { schemaVersion: '1.0', kind: 'table', data: { columns, rows }, meta: { rowCount: rows.length, truncated: false } }
}

function scalarEnv(value: string | number | boolean = 1): ScriptScalarEnvelope {
  return { schemaVersion: '1.0', kind: 'scalar', data: { value, dataType: 'number' }, meta: { rowCount: 1, truncated: false } }
}

function assertOk(spec: ComponentOutputSpec, env: Parameters<typeof validateComponentOutput>[1]): void {
  expect(validateComponentOutput(spec, env)).toBeNull()
}

function assertError(spec: ComponentOutputSpec, env: Parameters<typeof validateComponentOutput>[1]): OutputValidationError {
  const err = validateComponentOutput(spec, env)
  expect(err).not.toBeNull()
  expect(err?.status).toBe('OUTPUT_CONTRACT_ERROR')
  return err as OutputValidationError
}

describe('resolveOutputSpec', () => {
  it('kpi 接受 scalar 与 table', () => {
    const spec = resolveOutputSpec('kpi')
    expect(spec?.family).toBe('kpi')
    expect(spec?.accepts).toEqual(['scalar', 'table'])
  })

  it('table 仅接受 table', () => {
    const spec = resolveOutputSpec('table')
    expect(spec?.family).toBe('table')
    expect(spec?.accepts).toEqual(['table'])
  })

  it('图表缺省子类型按 维度+指标 家族', () => {
    expect(resolveOutputSpec('chart')?.family).toBe('chartCategory')
    expect(chartOutputFamily()).toBe('chartCategory')
  })

  it('饼图/漏斗/仪表盘按 名称+值 家族', () => {
    for (const ct of ['pie', 'funnel', 'gauge'] as const) {
      expect(resolveOutputSpec('chart', ct)?.family).toBe('chartNameValue')
    }
    expect(chartOutputFamily('pie')).toBe('chartNameValue')
  })

  it('筛选/AI/组合不消费脚本结果 → null', () => {
    expect(resolveOutputSpec('filter')).toBeNull()
    expect(resolveOutputSpec('aiAnalysis')).toBeNull()
    expect(resolveOutputSpec('combination')).toBeNull()
  })
})

describe('validateComponentOutput', () => {
  it('kpi 接受 scalar', () => {
    assertOk(resolveOutputSpec('kpi')!, scalarEnv(123))
  })

  it('kpi 接受单列 table', () => {
    assertOk(resolveOutputSpec('kpi')!, tableEnv([col('total')], [{ total: 10 }]))
  })

  it('kpi 设置 valueField 但该列缺失 → 精确报错', () => {
    const spec = resolveOutputSpec('kpi')!
    const err = validateComponentOutput(spec, tableEnv([col('other')], [{ other: 1 }]), { valueField: 'total' })
    expect(err).not.toBeNull()
    expect(err?.status).toBe('OUTPUT_CONTRACT_ERROR')
    expect(err?.path).toContain('result.data.columns')
  })

  it('kpi table 无任何列且未设 valueField → 报错', () => {
    assertError(resolveOutputSpec('kpi')!, tableEnv([]))
  })

  it('table 收到 scalar → kind 不匹配报错', () => {
    const err = assertError(resolveOutputSpec('table')!, scalarEnv(1))
    expect(err.path).toBe('result.kind')
    expect(err.expected).toContain('table')
  })

  it('图表 维度+指标 家族：缺数值列报错', () => {
    const err = assertError(resolveOutputSpec('chart')!, tableEnv([col('date', 'string')]))
    expect(err.suggestion).toContain('数值')
  })

  it('图表 维度+指标 家族：仅 1 列报错，2 列通过', () => {
    assertError(resolveOutputSpec('chart')!, tableEnv([col('date', 'string')]))
    assertOk(resolveOutputSpec('chart')!, tableEnv([col('date', 'string'), col('uv')]))
  })

  it('饼图 名称+值 家族：无数值列报错，名称+值通过', () => {
    assertError(resolveOutputSpec('chart', 'pie')!, tableEnv([col('channel', 'string')]))
    assertOk(resolveOutputSpec('chart', 'pie')!, tableEnv([col('channel', 'string'), col('amount')]))
  })

  it('图表收到 scalar → kind 不匹配报错', () => {
    assertError(resolveOutputSpec('chart')!, scalarEnv(1))
  })
})
