import { describe, expect, it } from 'vitest'
import { resolveOutputSpec } from '../component-output-spec'
import type { ResultSchema } from '../script-result'
import {
  buildFinalResultQueryConfig,
  finalResultQueryConfigStatus,
  normalizeFinalResultQueryContext,
} from '../final-result-query'

function schema(columns: ResultSchema['columns'], fingerprint = 'schema-1'): ResultSchema {
  return { fingerprint, kind: 'table', columns, rowCount: 3 }
}

describe('buildFinalResultQueryConfig', () => {
  it('只从组件可消费的真实输出字段生成候选', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('chart', 'pie')!, schema([
      { name: 'region', title: '区域', dataType: 'string', nullable: false },
      { name: 'amount', title: '金额', dataType: 'number', nullable: false },
    ]))
    expect(config.schemaFingerprint).toBe('schema-1')
    expect(config.displayFields.map((field) => field.field)).toEqual(['region', 'amount'])
    expect(config.filterFields.map((field) => field.field)).toEqual(['region', 'amount'])
    expect(config.filterFields.find((field) => field.field === 'amount')?.operators).toContain('gte')
  })

  it('没有最终查询配置时生成默认草稿，不改变输入查询语义', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, schema([
      { name: 'id', title: 'ID', dataType: 'number', nullable: false },
    ]))
    expect(config.sortPolicy.enabled).toBe(false)
    expect(config.paginationPolicy.enabled).toBe(false)
    expect(config.displayFields[0].title).toBe('ID')
  })
})

describe('finalResultQueryConfigStatus', () => {
  const current = schema([{ name: 'region', title: '区域', dataType: 'string', nullable: false }], 'schema-2')

  it('相同 Schema 为 ready，字段仍存在但指纹变化为 stale', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, current)
    expect(finalResultQueryConfigStatus(config, current)).toBe('ready')
    expect(finalResultQueryConfigStatus({ ...config, schemaFingerprint: 'schema-old' }, current)).toBe('stale')
  })

  it('字段删除或类型变化为 conflict', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, current)
    expect(finalResultQueryConfigStatus(config, schema([], 'schema-3'))).toBe('conflict')
    expect(finalResultQueryConfigStatus(config, schema([{ name: 'region', title: '区域', dataType: 'number', nullable: false }], 'schema-4'))).toBe('conflict')
  })
})

describe('normalizeFinalResultQueryContext', () => {
  it('只保留结果配置允许的参数、排序和分页', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, schema([
      { name: 'region', title: '区域', dataType: 'string', nullable: false },
      { name: 'amount', title: '金额', dataType: 'number', nullable: false },
    ]))
    const normalized = normalizeFinalResultQueryContext(config, {
      parameters: { region: '华东', unknown: 'drop' },
      sort: { field: 'amount', direction: 'desc' },
      pagination: { page: 2, pageSize: 20 },
    })
    expect(normalized.parameters).toEqual({ region: '华东' })
    expect(normalized.sort).toBeNull()
    expect(normalized.pagination).toBeNull()
  })
})
