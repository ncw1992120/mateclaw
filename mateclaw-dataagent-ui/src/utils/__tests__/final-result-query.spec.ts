import { describe, expect, it } from 'vitest'
import { resolveOutputSpec } from '../component-output-spec'
import type { ResultSchema } from '../script-result'
import {
  buildFinalResultQueryConfig,
  reconcileFinalResultDisplayFields,
  preserveFinalResultQueryPreferences,
  resolveFinalResultQueryConfigAfterExecution,
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

describe('preserveFinalResultQueryPreferences', () => {
  it('发现新的结果 Schema 时保留用户已保存的筛选、排序、分页和展示名设置', () => {
    const previous = {
      schemaFingerprint: 'schema-old',
      confirmed: true,
      displayFields: [{ field: 'region', title: '业务区域', role: 'dimension' as const, dataType: 'string' }],
      filterFields: [{
        field: 'region', title: '业务区域', dataType: 'string', parameterName: 'regionParam',
        operators: ['eq'] as const, filterComponentId: 'region-filter',
      }],
      sortPolicy: { enabled: true, mode: 'single' as const, allowedFields: ['amount'], defaultSort: { field: 'amount', direction: 'desc' as const } },
      paginationPolicy: { enabled: true, defaultPageSize: 25, maxPageSize: 75, returnTotalCount: true },
    }
    const discovered = {
      schemaFingerprint: 'schema-new',
      confirmed: false,
      displayFields: [
        { field: 'region', title: 'region', role: 'dimension' as const, dataType: 'string' },
        { field: 'amount', title: 'Amount', role: 'measure' as const, dataType: 'number' },
      ],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single' as const, allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }

    expect(preserveFinalResultQueryPreferences(previous, discovered)).toEqual({
      schemaFingerprint: 'schema-new',
      confirmed: true,
      displayFields: [
        { field: 'region', title: '业务区域', role: 'dimension', dataType: 'string' },
        { field: 'amount', title: 'Amount', role: 'measure', dataType: 'number' },
      ],
      filterFields: previous.filterFields,
      sortPolicy: previous.sortPolicy,
      paginationPolicy: previous.paginationPolicy,
    })
  })

  it('首次发现结果 Schema 时使用默认查询配置', () => {
    const discovered = {
      schemaFingerprint: 'schema-new',
      confirmed: false,
      displayFields: [{ field: 'amount', title: 'Amount', role: 'measure' as const, dataType: 'number' }],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single' as const, allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }

    expect(preserveFinalResultQueryPreferences(undefined, discovered)).toEqual(discovered)
  })
})

describe('resolveFinalResultQueryConfigAfterExecution', () => {
  it('仅刷新 Python 结果时完整保留现有结果字段和筛选配置', () => {
    const previous = {
      schemaFingerprint: 'schema-old',
      confirmed: true,
      displayFields: [{ field: 'amount', title: '自定义金额', role: 'measure' as const, dataType: 'number' as const }],
      filterFields: [{ field: 'region', title: '区域条件', dataType: 'string' as const, parameterName: 'region', operators: ['eq'] as const, filterComponentId: 'region-filter' }],
      sortPolicy: { enabled: false, mode: 'single' as const, allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    }
    const discovered = buildFinalResultQueryConfig(resolveOutputSpec('table')!, schema([
      { name: 'amount', title: 'amount', dataType: 'number', nullable: false },
      { name: 'region', title: 'region', dataType: 'string', nullable: false },
    ], 'schema-new'))

    const result = resolveFinalResultQueryConfigAfterExecution(previous, discovered, false)

    expect(result).toBe(previous)
    expect(result?.displayFields).toBe(previous.displayFields)
    expect(result?.filterFields).toBe(previous.filterFields)
  })
})

describe('reconcileFinalResultDisplayFields', () => {
  it('查询刷新列元数据时保留已编辑或清空的展示名，并为新字段使用默认名', () => {
    const fields = reconcileFinalResultDisplayFields(
      [
        { name: 'amount', type: 'number', title: '默认金额' },
        { name: 'region', type: 'string', title: '默认区域' },
        { name: 'count', type: 'number', title: '默认数量' },
      ],
      [{ amount: 10, region: '华东', count: 2 }],
      [
        { field: 'amount', title: '自定义金额', role: 'measure' },
        { field: 'region', title: '', role: 'dimension' },
      ],
    )

    expect(fields).toEqual([
      { field: 'amount', title: '自定义金额', role: 'measure', dataType: 'number' },
      { field: 'region', title: '', role: 'dimension', dataType: 'string' },
      { field: 'count', title: '默认数量', role: 'measure', dataType: 'number' },
    ])
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
