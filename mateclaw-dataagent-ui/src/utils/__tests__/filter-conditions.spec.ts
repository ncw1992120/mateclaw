import { describe, expect, it } from 'vitest'
import {
  DIMENSION_OPERATORS,
  GENERIC_OPERATORS,
  completeConditions,
  emptyCondition,
  isComplete,
  needsValue,
  normalizeOperator,
  operatorsFor,
  partitionConditions,
  toCondition,
  valueHintOf,
  withoutField,
} from '../filter-conditions'

describe('filter-conditions · 操作符归一', () => {
  it('把后端枚举名收敛成界面写法', () => {
    expect(normalizeOperator('eq')).toBe('=')
    expect(normalizeOperator('neq')).toBe('!=')
    expect(normalizeOperator('gte')).toBe('>=')
    expect(normalizeOperator('is_null')).toBe('is null')
    expect(normalizeOperator('is_not_null')).toBe('is not null')
  })

  it('not_in 不等价于 !=，必须保留自己的语义', () => {
    expect(normalizeOperator('not_in')).toBe('not in')
    expect(normalizeOperator('nin')).toBe('not in')
    expect(normalizeOperator('not in')).toBe('not in')
  })

  it('未知或缺失操作符回退成等于', () => {
    expect(normalizeOperator('')).toBe('=')
    expect(normalizeOperator(undefined)).toBe('=')
    expect(normalizeOperator('莫名操作符')).toBe('=')
  })

  it('操作符是否需要取值', () => {
    expect(needsValue('is null')).toBe(false)
    expect(needsValue('is not null')).toBe(false)
    expect(needsValue('=')).toBe(true)
  })
})

describe('filter-conditions · 操作符可选范围', () => {
  it('按字段类型和来源适配操作符', () => {
    expect(operatorsFor('string', 'JDBC_SQL').map((item) => item.value)).toEqual([
      '=', '!=', 'contains', 'starts_with', 'ends_with', 'in', 'not in', 'is null', 'is not null',
    ])
    expect(operatorsFor('number', 'JDBC_SQL').map((item) => item.value)).toEqual([
      '=', '!=', '>', '>=', '<', '<=', 'between', 'in', 'not in', 'is null', 'is not null',
    ])
    expect(operatorsFor('date', 'ALOUDATA_ANALYSIS_VIEW').map((item) => item.value)).toEqual([
      '=', '>', '>=', '<', '<=', 'between', 'in', 'not in',
    ])
  })

  it('维度筛选不给 contains / 空值判断（Aloudata 语义层不接受）', () => {
    const values = DIMENSION_OPERATORS.map((item) => item.value)
    expect(values).not.toContain('contains')
    expect(values).not.toContain('is null')
    expect(values).not.toContain('is not null')
    expect(values).toContain('between')
    expect(values).toContain('not in')
  })

  it('通用筛选多出 contains 与空值判断', () => {
    const values = GENERIC_OPERATORS.map((item) => item.value)
    expect(values).toContain('contains')
    expect(values).toContain('is null')
    expect(values).toContain('is not null')
  })

  it('取值提示按操作符给出，未知回退', () => {
    expect(valueHintOf('in')).toContain('逗号')
    expect(valueHintOf('between')).toContain('结束值')
    expect(valueHintOf('未知')).toBe('值')
  })
})

describe('filter-conditions · 存量回填', () => {
  it('数组值用逗号连接，便于单行编辑', () => {
    expect(toCondition({ field: 'region', op: 'in', value: ['华东', '华南'] })).toEqual({
      field: 'region',
      op: 'in',
      value: '华东, 华南',
    })
  })

  it('null / undefined 取值回填空串，不出现 "null" 字样', () => {
    expect(toCondition({ field: 'a', op: 'is null', value: null }).value).toBe('')
    expect(toCondition({ field: 'a', op: '=', value: undefined }).value).toBe('')
  })
})

describe('filter-conditions · 完整性判断', () => {
  it('缺字段或需要取值却没值 → 不完整', () => {
    expect(isComplete(emptyCondition())).toBe(false)
    expect(isComplete({ field: 'region', op: '=', value: '' })).toBe(false)
    expect(isComplete({ field: 'region', op: '=', value: '华东' })).toBe(true)
  })

  it('不需要取值的操作符，填了字段就算完整', () => {
    expect(isComplete({ field: 'region', op: 'is null', value: '' })).toBe(true)
  })

  it('下推时只带走完整条件，并去掉首尾空白', () => {
    expect(
      completeConditions([
        { field: ' region ', op: '=', value: ' 华东 ' },
        { field: '', op: '=', value: '华东' },
        { field: 'amount', op: '=', value: '   ' },
      ]),
    ).toEqual([{ field: 'region', op: '=', value: '华东' }])
  })
})

describe('filter-conditions · 可编辑行与遗留条件分流', () => {
  const saved = [
    { field: 'region', op: '=', value: '华东' },
    { field: '已删除的维度', op: '=', value: 'x' },
    { field: '', op: '=', value: 'y' },
  ]

  it('字段在可选项内 → 可编辑行；不在 → 遗留条件', () => {
    const { rows, legacy } = partitionConditions(saved, new Set(['region']))
    expect(rows.map((item) => item.field)).toEqual(['region'])
    expect(legacy.map((item) => item.field)).toEqual(['已删除的维度'])
  })

  it('可选项尚未加载（集合为空）时不误判，全部保持可编辑', () => {
    const { rows, legacy } = partitionConditions(saved, new Set())
    expect(rows.map((item) => item.field)).toEqual(['region', '已删除的维度'])
    expect(legacy).toEqual([])
  })

  it('字段为空的条件直接丢弃，不占位', () => {
    const { rows } = partitionConditions([{ field: '', op: '=', value: 'y' }], new Set(['region']))
    expect(rows).toEqual([])
  })

  it('移除遗留条件按字段过滤', () => {
    expect(withoutField([{ field: 'a', op: '=', value: '1' }, { field: 'b', op: '=', value: '2' }], 'a')).toEqual([
      { field: 'b', op: '=', value: '2' },
    ])
  })
})
