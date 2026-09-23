import { describe, expect, it } from 'vitest'
import { CHART_TYPES_ALL } from '@/constants/chartTypes'
import { resolveComponentSample } from '../component-sample-data'
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
})
