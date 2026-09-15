import { describe, expect, it } from 'vitest'
import {
  buildDatasetInput,
  isReadonlySql,
  normalizeDatasetInput,
} from '@/utils/dataset-composer'

describe('dataset composer', () => {
  it('normalizes legacy input without dropping existing fields', () => {
    const input = normalizeDatasetInput({ datasetId: 'd1', inputName: 'orders' })
    expect(input).toMatchObject({ datasetId: 'd1', inputName: 'orders', sourceType: 'JDBC_TABLE', filters: [], fieldMappings: [] })
  })

  it('builds source-aware inputs with SQL and Aloudata view mode', () => {
    expect(buildDatasetInput('d1', 'orders', 'JDBC_SQL', { sql: 'select * from orders' })).toMatchObject({
      sourceType: 'JDBC_SQL',
      sourceConfig: { sql: 'select * from orders' },
    })
    expect(buildDatasetInput('d2', 'metrics', 'ALOUDATA_ANALYSIS_VIEW', { analysisViewId: 'view-1' })).toMatchObject({
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      sourceConfig: { analysisViewId: 'view-1' },
    })
  })

  it('accepts only read-only SQL statements', () => {
    expect(isReadonlySql('select * from orders')).toBe(true)
    expect(isReadonlySql('with x as (select 1) select * from x')).toBe(true)
    expect(isReadonlySql('delete from orders')).toBe(false)
    expect(isReadonlySql('select 1; drop table orders')).toBe(false)
  })
})
