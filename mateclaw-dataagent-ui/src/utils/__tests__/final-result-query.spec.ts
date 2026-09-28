import { describe, expect, it } from 'vitest'
import { resolveOutputSpec } from '../component-output-spec'
import type { ResultSchema } from '../script-result'
import {
  buildFinalResultQueryConfig,
  finalResultQueryConfigStatus,
  isFinalResultQueryConfigured,
  normalizeFinalResultQueryContext,
  sortDisplayFieldsDimensionsFirst,
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
    expect(config.filterFields).toEqual([])
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

describe('sortDisplayFieldsDimensionsFirst', () => {
  it('默认维度在前、指标在后，并保持各自原有顺序', () => {
    const fields = [
      { field: 'amount_1', title: '金额1', role: 'measure' as const },
      { field: 'region', title: '区域', role: 'dimension' as const },
      { field: 'amount_2', title: '金额2', role: 'measure' as const },
      { field: 'date', title: '日期', role: 'dimension' as const },
    ]

    expect(sortDisplayFieldsDimensionsFirst(fields).map((field) => field.field))
      .toEqual(['region', 'date', 'amount_1', 'amount_2'])
    expect(fields.map((field) => field.field)).toEqual(['amount_1', 'region', 'amount_2', 'date'])
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

describe('isFinalResultQueryConfigured', () => {
  it('系统生成草稿不算用户已配置，保存确认后才算已配置', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, schema([{ name: 'region', title: '区域', dataType: 'string', nullable: false }]))
    expect(isFinalResultQueryConfigured(config)).toBe(false)
    expect(isFinalResultQueryConfigured({ ...config, confirmed: true })).toBe(true)
  })
})

describe('normalizeFinalResultQueryContext', () => {
  it('只保留结果配置允许的参数、排序和分页', () => {
    const config = buildFinalResultQueryConfig(resolveOutputSpec('table')!, schema([
      { name: 'region', title: '区域', dataType: 'string', nullable: false },
      { name: 'amount', title: '金额', dataType: 'number', nullable: false },
    ]))
    config.filterFields = [{
      field: 'region', title: '区域', dataType: 'string', parameterName: 'region', operators: ['eq'], filterComponentId: 'region-filter',
    }]
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
