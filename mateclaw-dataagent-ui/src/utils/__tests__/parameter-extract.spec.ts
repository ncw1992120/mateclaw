import { describe, expect, it } from 'vitest'
import {
  extractApiParameters,
  extractDimensionParameters,
  extractFileParameters,
  extractScriptParameters,
  extractSqlParameters,
  fromDeclaredParameters,
  mergeParameters,
} from '@/utils/parameter-extract'

describe('SQL 参数提取', () => {
  it('提取 :param 绑定占位符', () => {
    const sql = `
      SELECT plan_id, amount FROM sales_detail
      WHERE metric_time >= :start_date
        AND region IN (:region)
      LIMIT :limit
    `
    const result = extractSqlParameters(sql)
    expect(result.map((p) => p.name)).toEqual(['start_date', 'region', 'limit'])
    expect(result.every((p) => p.source === 'sql')).toBe(true)
    expect(result.every((p) => p.required)).toBe(true)
  })

  it('按名称推断类型', () => {
    const result = extractSqlParameters('WHERE d >= :start_date AND r = :region AND n = :limit')
    const byName = Object.fromEntries(result.map((p) => [p.name, p.type]))
    expect(byName.start_date).toBe('date_range')
    expect(byName.region).toBe('string')
    expect(byName.limit).toBe('number')
  })

  it('不把 :: 类型转换当成参数', () => {
    expect(extractSqlParameters('SELECT created_at::text, amount::numeric FROM t')).toEqual([])
  })

  it('不把时间字面量里的冒号当成参数', () => {
    expect(extractSqlParameters("SELECT * FROM t WHERE created_at > '2026-01-01 10:30:00'")).toEqual([])
  })

  it('同名占位符只出现一次', () => {
    const result = extractSqlParameters('WHERE a >= :d AND b <= :d')
    expect(result.map((p) => p.name)).toEqual(['d'])
  })

  it('空 SQL 返回空', () => {
    expect(extractSqlParameters('')).toEqual([])
    expect(extractSqlParameters(undefined)).toEqual([])
  })
})

describe('接口参数提取', () => {
  it('同时支持 {{ name }} 与 :name', () => {
    const result = extractApiParameters({
      path: '/v1/orders?date={{date}}&region=:region',
      headers: 'X-Token: {{token}}',
      params: '',
    })
    expect(result.map((p) => p.name).sort()).toEqual(['date', 'region', 'token'])
    expect(result.every((p) => p.source === 'api')).toBe(true)
  })

  it('不把 URL 端口当成参数', () => {
    expect(extractApiParameters({ path: 'http://host:8080/v1/orders' })).toEqual([])
  })

  it('接口参数非必填', () => {
    const result = extractApiParameters({ path: '/v1?x={{x}}' })
    expect(result[0].required).toBe(false)
  })
})

describe('Python 脚本参数提取', () => {
  it('识别 parameters[...] / params[...] / .get(...)', () => {
    const script = `
      start = parameters['start_date']
      region = params["region"]
      keyword = parameters.get('keyword')
    `
    expect(extractScriptParameters(script).map((p) => p.name).sort()).toEqual(['keyword', 'region', 'start_date'])
  })

  it('不把系统生成区的 inputs 当成参数', () => {
    expect(extractScriptParameters('orders = inputs["orders"]')).toEqual([])
  })
})

describe('其它来源', () => {
  it('维度即筛选项，且为多选', () => {
    const result = extractDimensionParameters(['region', 'channel'])
    expect(result.map((p) => p.name)).toEqual(['region', 'channel'])
    expect(result.every((p) => p.type === 'string[]' && p.source === 'dimension')).toBe(true)
  })

  it('文件用固定的解析选项', () => {
    expect(extractFileParameters().map((p) => p.name)).toEqual(['sheet', 'encoding', 'delimiter', 'headerRow'])
  })

  it('人工声明的参数可转成统一形状', () => {
    const result = fromDeclaredParameters([
      { name: 'start_date', type: 'date', required: true, scope: 'component' },
    ])
    expect(result[0]).toMatchObject({ name: 'start_date', type: 'date', source: 'declared', required: true })
  })
})

describe('参数合并', () => {
  it('按名称去重', () => {
    const merged = mergeParameters(
      [{ name: 'a', type: 'string', source: 'sql', required: true }],
      [{ name: 'a', type: 'number', source: 'api', required: false }],
    )
    expect(merged).toHaveLength(1)
  })

  it('人工声明优先于自动推断', () => {
    const merged = mergeParameters(
      fromDeclaredParameters([{ name: 'start_date', type: 'date', required: false, scope: 'component' }]),
      extractSqlParameters('WHERE d >= :start_date'),
    )
    // 声明的类型不被推断覆盖，但必填会被"或"上去（SQL 占位符必须给值）
    expect(merged[0].type).toBe('date')
    expect(merged[0].required).toBe(true)
  })

  it('保留多来源并集与顺序', () => {
    const merged = mergeParameters(
      extractSqlParameters('WHERE d >= :d'),
      extractDimensionParameters(['region']),
    )
    expect(merged.map((p) => p.name)).toEqual(['d', 'region'])
  })
})
