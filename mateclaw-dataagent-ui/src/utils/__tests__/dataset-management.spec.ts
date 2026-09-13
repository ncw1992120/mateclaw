import { describe, expect, it } from 'vitest'
import {
  buildDatasetSourceDefinition,
  isJdbcSource,
  type DatasetSourceFormState,
} from '@/utils/dataset-management'

describe('dataset management source definition', () => {
  it('shows SQL only for JDBC and builds a JDBC SQL definition', () => {
    expect(isJdbcSource('JDBC_SQL')).toBe(true)
    expect(isJdbcSource('HTTP_API')).toBe(false)

    const state: DatasetSourceFormState = {
      sourceType: 'JDBC_SQL',
      datasourceId: '7',
      tableIds: [],
      sql: 'select id from orders',
    }
    expect(buildDatasetSourceDefinition(state)).toEqual({
      sourceType: 'JDBC_SQL',
      datasourceId: 7,
      sql: 'select id from orders',
    })
  })

  it('does not send SQL for non-JDBC definitions', () => {
    const state: DatasetSourceFormState = {
      sourceType: 'FILE',
      datasourceId: '',
      tableIds: [],
      sql: 'select secret from users',
    }
    expect(buildDatasetSourceDefinition(state)).toEqual({
      sourceType: 'FILE',
      objectId: '',
      format: 'file',
      schemaVersion: 1,
    })
  })

  it('builds an Aloudata view reference without embedding view SQL', () => {
    const state: DatasetSourceFormState = {
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      datasourceId: '9',
      tableIds: [],
      sql: 'select should_not_be_sent',
      analysisViewId: 'sales_view',
    }
    expect(buildDatasetSourceDefinition(state)).toEqual({
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      datasourceId: 9,
      analysisViewId: 'sales_view',
    })
  })

  it('builds HTTP and file references from registered IDs only', () => {
    expect(buildDatasetSourceDefinition({
      sourceType: 'HTTP_API', datasourceId: '7', tableIds: [], sql: 'select x', apiDefinitionId: 'api-orders',
    })).toEqual({ sourceType: 'HTTP_API', datasourceId: 7, apiDefinitionId: 'api-orders' })
    expect(buildDatasetSourceDefinition({
      sourceType: 'FILE', datasourceId: '', tableIds: [], sql: 'select x', objectId: 'obj-1', format: 'csv', schemaVersion: 2,
    })).toEqual({ sourceType: 'FILE', objectId: 'obj-1', format: 'csv', schemaVersion: 2 })
  })
})
