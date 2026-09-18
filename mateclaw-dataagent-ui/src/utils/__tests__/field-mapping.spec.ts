import { describe, expect, it } from 'vitest'
import {
  buildFieldMappingRows,
  defaultTargetOf,
  resolveSourceFieldName,
  toSourceFilters,
  validateFieldMappingRows,
} from '@/utils/field-mapping'

describe('field mapping · 默认填充规则', () => {
  it('显示名优先作为目标名称，缺省回落到源字段名', () => {
    expect(defaultTargetOf({ name: 'strategy_id', displayName: '策略 ID' })).toBe('策略 ID')
    expect(defaultTargetOf({ name: 'strategy_id' })).toBe('strategy_id')
    expect(defaultTargetOf({ name: 'strategy_id', displayName: '   ' })).toBe('strategy_id')
  })

  it('按 schema 全量生成三列：字段名 / 字段描述 / 目标名称', () => {
    const rows = buildFieldMappingRows([
      { name: 'strategy_id', displayName: '策略 ID' },
      { name: 'event_date' },
      { name: 'delivery_count', displayName: '下发次数' },
    ])
    expect(rows).toEqual([
      { source: 'strategy_id', desc: '策略 ID', target: '策略 ID' },
      { source: 'event_date', desc: '', target: 'event_date' },
      { source: 'delivery_count', desc: '下发次数', target: '下发次数' },
    ])
  })

  it('schema 为空时保留已有映射，不把用户配置清空', () => {
    const existing = [{ source: 'a', desc: '', target: 'A' }]
    expect(buildFieldMappingRows([], existing)).toEqual(existing)
  })
})

describe('field mapping · 增量合并与用户改动保留', () => {
  it('用户改过的目标名称在重新拉取 schema 后保留', () => {
    const existing = [{ source: 'strategy_id', desc: '策略 ID', target: '策略编号' }]
    const rows = buildFieldMappingRows([{ name: 'strategy_id', displayName: '策略 ID' }], existing)
    expect(rows).toEqual([{ source: 'strategy_id', desc: '策略 ID', target: '策略编号' }])
  })

  it('未被用户改动的默认目标名称跟随最新显示名刷新', () => {
    const existing = [{ source: 'strategy_id', desc: '策略 ID', target: '策略 ID' }]
    const rows = buildFieldMappingRows([{ name: 'strategy_id', displayName: '策略名称' }], existing)
    expect(rows).toEqual([{ source: 'strategy_id', desc: '策略名称', target: '策略名称' }])
  })

  it('空目标名称视为默认值，按最新显示名补齐', () => {
    const existing = [{ source: 'strategy_id', desc: '策略 ID', target: '' }]
    const rows = buildFieldMappingRows([{ name: 'strategy_id', displayName: '策略 ID' }], existing)
    expect(rows).toEqual([{ source: 'strategy_id', desc: '策略 ID', target: '策略 ID' }])
  })

  it('新增字段追加、消失字段移除，顺序以 schema 为准', () => {
    const existing = [
      { source: 'gone', desc: '', target: 'Gone' },
      { source: 'keep', desc: '旧', target: 'Keep' },
    ]
    const rows = buildFieldMappingRows(
      [{ name: 'keep', displayName: '保留' }, { name: 'added' }],
      existing,
    )
    expect(rows).toEqual([
      { source: 'keep', desc: '保留', target: 'Keep' },
      { source: 'added', desc: '', target: 'added' },
    ])
  })

  it('schema 未提供显示名时描述列为空、目标名回落为源字段名', () => {
    const rows = buildFieldMappingRows([{ name: 'amount' }])
    expect(rows).toEqual([{ source: 'amount', desc: '', target: 'amount' }])
  })

  it('无显示名的 schema 刷新后，用户改过的目标名仍然保留', () => {
    const existing = [{ source: 'strategy_id', desc: 'strategy_id', target: '策略 ID' }]
    const rows = buildFieldMappingRows([{ name: 'strategy_id' }], existing)
    expect(rows).toEqual([{ source: 'strategy_id', desc: '', target: '策略 ID' }])
  })
})

describe('field mapping · 目标名称校验', () => {
  it('目标名称为空时报错', () => {
    expect(validateFieldMappingRows([{ source: 'a', desc: '', target: '  ' }])).toContain('目标名称')
  })

  it('目标名称重复时报错并指出重复项', () => {
    const err = validateFieldMappingRows([
      { source: 'a', desc: '', target: '同一名称' },
      { source: 'b', desc: '', target: '同一名称' },
    ])
    expect(err).toContain('同一名称')
  })

  it('合法时通过', () => {
    expect(validateFieldMappingRows([
      { source: 'a', desc: '', target: 'A' },
      { source: 'b', desc: '', target: 'B' },
    ])).toBeNull()
  })
})

describe('field mapping · 下推字段名反解', () => {
  const rows = [
    { source: 'strategy_id', desc: '策略 ID', target: '策略 ID' },
    { source: 'delivery_count', desc: '', target: '下发次数' },
  ]

  it('目标名称反解回源字段名，用于下推到数据源', () => {
    expect(resolveSourceFieldName(rows, '策略 ID')).toBe('strategy_id')
    expect(resolveSourceFieldName(rows, '下发次数')).toBe('delivery_count')
  })

  it('未命中映射时原样返回，未改名场景不受影响', () => {
    expect(resolveSourceFieldName(rows, 'unknown_field')).toBe('unknown_field')
    expect(resolveSourceFieldName(rows, '')).toBe('')
  })

  it('筛选条件下推时反解字段名并保留运算符与取值', () => {
    const filters = [
      { field: '策略 ID', op: '=', value: 'S1' },
      { field: 'unknown', op: 'contains', value: 'x' },
    ]
    expect(toSourceFilters(rows, filters)).toEqual([
      { field: 'strategy_id', op: '=', value: 'S1' },
      { field: 'unknown', op: 'contains', value: 'x' },
    ])
    // 不修改原数组，避免 UI 展示被改写
    expect(filters[0].field).toBe('策略 ID')
  })
})
