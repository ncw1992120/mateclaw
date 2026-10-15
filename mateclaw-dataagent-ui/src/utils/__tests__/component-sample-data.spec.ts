import { describe, expect, it } from 'vitest'
import { CHART_TYPES_ALL } from '@/constants/chartTypes'
import { resolveComponentSample, validateComponentSample } from '../component-sample-data'
import { resolveOutputSpec, validateComponentOutput } from '../component-output-spec'

describe('component sample data', () => {
  it('provides a renderable, explicitly marked sample for every top-level component', () => {
    for (const type of ['kpi', 'table', 'filter', 'timeFilter', 'aiAnalysis', 'combination'] as const) {
      const sample = resolveComponentSample({ id: `sample-${type}`, type, title: type })
      expect(sample.isSample).toBe(true)
      expect(sample.renderData.componentId).toBe(`sample-${type}`)
      expect(JSON.parse(sample.json)).toHaveProperty('schemaVersion', '1.0')
    }
  })

  it('provides a renderable fixture and a distinct format contract for every chart subtype', () => {
    for (const { key } of CHART_TYPES_ALL) {
      const sample = resolveComponentSample({ id: `sample-${key}`, type: 'chart', chartType: key, title: key })
      expect(sample.isSample).toBe(true)
      expect(sample.renderData.renderType).toBe('echarts')
      expect(sample.renderData.option).toBeTruthy()
      expect(sample.contract.length).toBeGreaterThan(10)
    }
  })

  it('returns copyable JSON which describes the render fixture and its semantic schema', () => {
    const sample = resolveComponentSample({ id: 'pie-1', type: 'chart', chartType: 'pie', title: '分布' })
    const parsed = JSON.parse(sample.json) as Record<string, unknown>
    expect(parsed).toMatchObject({
      schemaVersion: '1.0',
      component: { type: 'chart', subtype: 'pie' },
      meta: { isSample: true },
    })
    expect(sample.contract).toContain('维度')
    expect(sample.contract).toContain('指标')
  })

  it('ensures every chart sample schema itself satisfies that chart output contract', () => {
    for (const { key } of CHART_TYPES_ALL) {
      const sample = resolveComponentSample({ id: `sample-${key}`, type: 'chart', chartType: key, title: key })
      const parsed = JSON.parse(sample.json) as { data: { schema: Array<{ name: string; dataType: string }>; rows: Record<string, unknown>[] } }
      const envelope = {
        schemaVersion: '1.0' as const,
        kind: 'table' as const,
        data: {
          columns: parsed.data.schema.map((column) => ({ ...column, title: column.name, nullable: true })),
          rows: parsed.data.rows,
        },
        meta: { rowCount: parsed.data.rows.length, truncated: false },
      }
      expect(validateComponentOutput(resolveOutputSpec('chart', key)!, envelope), key).toBeNull()
    }
  })

  it('validates KPI sample cardinality as zero or one row', () => {
    const component = { id: 'kpi-sample', type: 'kpi' as const }
    const envelope = JSON.parse(resolveComponentSample(component).json) as Record<string, any>

    expect(validateComponentSample(component, envelope)).toBeNull()
    expect(validateComponentSample(component, {
      ...envelope,
      data: { kind: 'table', schema: [{ name: 'value', dataType: 'number', role: 'metric' }], rows: [{ value: 1 }, { value: 2 }] },
      meta: { ...envelope.meta, rowCount: 2 },
    })).toMatchObject({ path: 'data.rows', expected: '0..1 行', actual: '2 行' })
  })

  it('validates table sample row counts and client pagination metadata', () => {
    const component = { id: 'table-sample', type: 'table' as const }
    const envelope = JSON.parse(resolveComponentSample(component).json) as Record<string, any>

    expect(validateComponentSample(component, envelope)).toBeNull()
    expect(envelope.meta.rowCount).toBe(envelope.data.rows.length)
    expect(envelope.data.pagination).toMatchObject({ mode: 'client', enabled: true, pageSize: 20 })
    expect(validateComponentSample(component, {
      ...envelope,
      data: { ...envelope.data, pagination: { mode: 'client', enabled: true, pageSize: 0 } },
    })).toMatchObject({ path: 'data.pagination.pageSize' })
  })

  it('validates pie sample with exactly one dimension and one metric role', () => {
    const component = { id: 'pie-sample', type: 'chart' as const, chartType: 'pie' as const }
    const envelope = JSON.parse(resolveComponentSample(component).json) as Record<string, any>

    expect(validateComponentSample(component, envelope)).toBeNull()
    expect(validateComponentSample(component, {
      ...envelope,
      data: {
        ...envelope.data,
        schema: [...envelope.data.schema, { name: 'region2', dataType: 'string', role: 'dimension' }],
        rows: envelope.data.rows.map((row: Record<string, unknown>) => ({ ...row, region2: '样例' })),
      },
    })).toMatchObject({ path: 'data.schema', expected: '1 个维度 + 1 个指标', actual: '2 个维度 + 1 个指标' })
  })
})
