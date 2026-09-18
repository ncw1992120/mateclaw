import { describe, expect, it } from 'vitest'
import {
  duplicateFieldNames,
  fieldMetasFromMappings,
  normalizeLegacyFieldRef,
  normalizeLegacyFieldRefs,
  reconcileFieldMetas,
  resolveFieldLabel,
  resolveFieldName,
  toFieldMappings,
  validateFieldMetas,
  type DatasetFieldMeta,
} from '@/utils/field-mapping'

const schema = [
  { name: 'strategy_id', displayName: '策略 ID' },
  { name: 'event_date' },
  { name: 'delivery_count', displayName: '下发次数' },
]

describe('字段注册表 · 解析（唯一取值口径）', () => {
  const fields: DatasetFieldMeta[] = [
    { name: 'strategy_id', displayName: '策略 ID' },
    { name: 'event_date' },
    { name: 'amount', displayName: '' },
  ]

  it('resolveFieldLabel：命中展示名用展示名，未设置回退字段名', () => {
    expect(resolveFieldLabel(fields, 'strategy_id')).toBe('策略 ID')
    expect(resolveFieldLabel(fields, 'event_date')).toBe('event_date')
    expect(resolveFieldLabel(fields, 'amount')).toBe('amount')
    expect(resolveFieldLabel(fields, 'unknown')).toBe('unknown')
  })

  it('resolveFieldLabel：空引用与空注册表不炸', () => {
    expect(resolveFieldLabel(fields, '')).toBe('')
    expect(resolveFieldLabel(undefined, 'strategy_id')).toBe('strategy_id')
    expect(resolveFieldLabel([], 'strategy_id')).toBe('strategy_id')
  })

  it('resolveFieldName：字段名原样返回，展示名反解为字段名', () => {
    expect(resolveFieldName(fields, 'strategy_id')).toBe('strategy_id')
    expect(resolveFieldName(fields, '策略 ID')).toBe('strategy_id')
    expect(resolveFieldName(fields, 'event_date')).toBe('event_date')
  })

  it('resolveFieldName：未命中原样返回（不静默改写用户手填值）', () => {
    expect(resolveFieldName(fields, 'unknown_field')).toBe('unknown_field')
    expect(resolveFieldName(fields, '')).toBe('')
    expect(resolveFieldName(undefined, '策略 ID')).toBe('策略 ID')
  })
})

describe('字段注册表 · 展示名唯一性校验', () => {
  it('展示名可空（回退字段名），不报错', () => {
    expect(
      validateFieldMetas([
        { name: 'a', displayName: '' },
        { name: 'b', displayName: '   ' },
        { name: 'c' },
      ]),
    ).toBeNull()
  })

  it('展示名重复时报错并指出重复项', () => {
    const err = validateFieldMetas([
      { name: 'a', displayName: '同一名称' },
      { name: 'b', displayName: '同一名称' },
    ])
    expect(err).toContain('同一名称')
    expect(err).toContain('重复')
  })

  it('展示名与其它字段的字段名冲突时报错（反解仍有歧义）', () => {
    const err = validateFieldMetas([
      { name: 'a', displayName: 'b' },
      { name: 'b' },
    ])
    expect(err).toContain('冲突')
  })

  it('展示名与自身字段名相同不算冲突', () => {
    expect(validateFieldMetas([{ name: 'a', displayName: 'a' }])).toBeNull()
  })

  it('duplicateFieldNames 标出所有冲突行', () => {
    const dups = duplicateFieldNames([
      { name: 'a', displayName: '同名' },
      { name: 'b', displayName: '同名' },
      { name: 'c', displayName: 'ok' },
    ])
    expect([...dups].sort()).toEqual(['a', 'b'])
  })
})

describe('字段注册表 · 按 schema 增量合并', () => {
  it('首次全量生成：展示名取后端显示名，无显示名时留空（回退字段名）', () => {
    expect(reconcileFieldMetas(schema)).toEqual([
      { name: 'strategy_id', displayName: '策略 ID', description: '策略 ID', role: undefined, unit: undefined, stale: false },
      { name: 'event_date', displayName: undefined, description: undefined, role: undefined, unit: undefined, stale: false },
      { name: 'delivery_count', displayName: '下发次数', description: '下发次数', role: undefined, unit: undefined, stale: false },
    ])
  })

  it('schema 为空时保留已有注册表，不把用户配置清空', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'a', displayName: 'A', unit: '个' }]
    expect(reconcileFieldMetas([], existing)).toEqual(existing)
  })

  it('用户改过的展示名在重新拉取 schema 后保留', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'strategy_id', displayName: '策略编号' }]
    const rows = reconcileFieldMetas([{ name: 'strategy_id', displayName: '策略 ID' }], existing, [
      { name: 'strategy_id', displayName: '策略 ID' },
    ])
    expect(rows[0].displayName).toBe('策略编号')
  })

  it('未被用户改动的展示名跟随最新显示名刷新', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'strategy_id', displayName: '策略 ID' }]
    const rows = reconcileFieldMetas([{ name: 'strategy_id', displayName: '策略编号' }], existing, [
      { name: 'strategy_id', displayName: '策略 ID' },
    ])
    expect(rows[0].displayName).toBe('策略编号')
  })

  it('无上一版 schema 可比对时，非空展示名一律保留（不误清用户编辑）', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'strategy_id', displayName: '策略编号' }]
    const rows = reconcileFieldMetas([{ name: 'strategy_id', displayName: '策略 ID' }], existing)
    expect(rows[0].displayName).toBe('策略编号')
  })

  it('描述 / 角色始终跟随 schema，单位保留用户配置', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'amount', displayName: '金额', unit: '万元' }]
    const rows = reconcileFieldMetas(
      [{ name: 'amount', displayName: '金额(元)', description: '口径说明', role: 'measure' }],
      existing,
      [{ name: 'amount', displayName: '金额' }],
    )
    // 展示名未被用户改动 → 跟随后端最新值；描述/角色为只读列 → 始终跟随
    expect(rows[0]).toMatchObject({ displayName: '金额(元)', description: '口径说明', role: 'measure', unit: '万元' })
  })

  it('新增字段追加、顺序以 schema 为准', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'keep', displayName: 'K' }]
    const rows = reconcileFieldMetas([{ name: 'added' }, { name: 'keep' }], existing, [{ name: 'keep' }])
    expect(rows.map((r) => r.name)).toEqual(['added', 'keep'])
  })

  it('schema 中消失的字段不清配置，打 stale 标记并排到末尾（决策 5）', () => {
    const existing: DatasetFieldMeta[] = [{ name: 'gone', displayName: '已下线' }]
    const rows = reconcileFieldMetas([{ name: 'keep' }], existing, [{ name: 'gone' }])
    expect(rows.map((r) => r.name)).toEqual(['keep', 'gone'])
    expect(rows[1].stale).toBe(true)
    expect(rows[1].displayName).toBe('已下线')
  })
})

describe('字段注册表 · 存量归一（决策 4）', () => {
  const fields: DatasetFieldMeta[] = [
    { name: 'strategy_id', displayName: '策略 ID' },
    { name: 'delivery_count', displayName: '下发次数' },
  ]

  it('① 精确等于字段名 → 保持', () => {
    expect(normalizeLegacyFieldRef(fields, undefined, 'strategy_id')).toEqual({
      name: 'strategy_id',
      resolved: true,
    })
  })

  it('② 等于展示名 → 反解为字段名', () => {
    expect(normalizeLegacyFieldRef(fields, undefined, '下发次数')).toEqual({
      name: 'delivery_count',
      resolved: true,
    })
  })

  it('③ 等于旧 fieldMapping.target → 反解为其 source', () => {
    const legacy = [{ source: 'event_date', target: '日期' }]
    expect(normalizeLegacyFieldRef(fields, legacy, '日期')).toEqual({
      name: 'event_date',
      resolved: true,
    })
  })

  it('④ 未命中保留原值并标记 resolved=false', () => {
    expect(normalizeLegacyFieldRef(fields, undefined, 'unknown')).toEqual({
      name: 'unknown',
      resolved: false,
    })
  })

  it('空引用不算未识别（无噪声告警）', () => {
    expect(normalizeLegacyFieldRef(fields, undefined, '')).toEqual({ name: '', resolved: true })
  })

  it('批量归一：返回归一后引用与去重的未识别清单', () => {
    const { names, unresolved } = normalizeLegacyFieldRefs(fields, undefined, [
      '策略 ID',
      'delivery_count',
      'unknown',
      'unknown',
      '',
    ])
    expect(names).toEqual(['strategy_id', 'delivery_count', 'unknown', 'unknown', ''])
    expect(unresolved).toEqual(['unknown'])
  })
})

describe('字段注册表 · 序列化（后端契约 §4.3）', () => {
  it('source=字段名（下推唯一依据），target=展示名（仅展示）', () => {
    const fields: DatasetFieldMeta[] = [
      { name: 'strategy_id', displayName: '策略 ID' },
      { name: 'event_date' },
    ]
    expect(toFieldMappings(fields)).toEqual([
      { source: 'strategy_id', target: '策略 ID' },
      { source: 'event_date', target: 'event_date' },
    ])
  })

  it('读入后端 fieldMappings：target 与 source 相同视为未设置展示名', () => {
    expect(
      fieldMetasFromMappings([
        { source: 'strategy_id', target: '策略 ID' },
        { source: 'event_date', target: 'event_date' },
      ]),
    ).toEqual([{ name: 'strategy_id', displayName: '策略 ID' }, { name: 'event_date', displayName: undefined }])
  })

  it('读入时按字段名去重，空 source 丢弃', () => {
    expect(
      fieldMetasFromMappings([
        { source: 'a', target: 'A' },
        { source: 'a', target: 'A2' },
        { source: '', target: 'x' },
      ]),
    ).toEqual([{ name: 'a', displayName: 'A' }])
  })
})
