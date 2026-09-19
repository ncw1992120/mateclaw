import { beforeEach, describe, expect, it } from 'vitest'
import { useInsight } from '../useInsight'
import type { DatasetConfig } from '../useInsight'

const {
  state,
  markResultSetStale,
  hydrateResultSet,
  resultSetMeta,
  kpiResultFields,
  resetResultSet,
  generateResultSet,
} = useInsight()

/** 基线：一个数据集 + 已就绪的结果集（模拟「刚生成完」） */
function seedReadyResultSet(): void {
  state.datasets = [{
    id: 'ds-1',
    sourceType: 'jdbc',
    sourceLabel: 'JDBC · db1',
    alias: 'orders',
    fields: [
      { name: 'amount', displayName: '销售额', unit: '元' },
      { name: 'region', displayName: '区域' },
    ],
    filters: [],
    jdbc: { db: '1', sql: 'select 1' },
  }] as DatasetConfig[]
  state.hasPython = false
  state.resultSet.source = 'dataset'
  state.resultSet.columns = [{ name: 'amount', type: 'double' }]
  state.resultSet.rows = [{ amount: 120 }]
  state.resultSet.rowCount = 1
  state.resultSet.generatedAt = '2026-09-18T09:00:00.000Z'
  state.resultSet.elapsedMs = 320
  state.resultSet.executionId = ''
  state.resultSet.error = ''
  state.resultSet.status = 'ready'
}

beforeEach(() => {
  state.datasets = []
  state.hasPython = false
  state.pythonUser = ''
  resetResultSet('empty')
})

describe('result set state machine', () => {
  it('marks a ready result set stale when inputs change, keeping rows for the card', () => {
    seedReadyResultSet()
    markResultSetStale()
    expect(state.resultSet.status).toBe('stale')
    // 旧行数据保留：卡片沿用上一次渲染，而不是被清空
    expect(state.resultSet.rows).toHaveLength(1)
  })

  it('resets a failed result set to empty instead of stale (nothing to reuse)', () => {
    seedReadyResultSet()
    state.resultSet.status = 'failed'
    markResultSetStale()
    expect(state.resultSet.status).toBe('empty')
  })

  it('keeps metadata out of persistence while empty or running', () => {
    expect(resultSetMeta()).toBeUndefined()
    state.resultSet.status = 'running'
    expect(resultSetMeta()).toBeUndefined()
  })

  it('persists stale as a successful result set so reopening can reuse it', () => {
    seedReadyResultSet()
    state.resultSet.source = 'script'
    state.resultSet.executionId = 'dashboard-1-abc'
    markResultSetStale()
    const meta = resultSetMeta()
    expect(meta?.status).toBe('ready')
    expect(meta?.executionId).toBe('dashboard-1-abc')
    expect(meta?.rowCount).toBe(1)
  })

  it('hydrates metadata without rows (rows are re-read on demand)', () => {
    hydrateResultSet({
      source: 'script',
      status: 'ready',
      columns: [{ name: 'amount', type: 'double' }],
      rowCount: 88,
      generatedAt: '2026-09-18T09:00:00.000Z',
      executionId: 'dashboard-1-abc',
    })
    expect(state.resultSet.status).toBe('ready')
    expect(state.resultSet.rowCount).toBe(88)
    expect(state.resultSet.rows).toEqual([])
  })

  it('projects KPI fields from the result set schema, not the dataset registry', () => {
    seedReadyResultSet()
    // 脚本把维度聚合掉了：结果集只剩 amount，指标候选就不能再出现 region
    expect(kpiResultFields().map((field) => field.name)).toEqual(['amount'])
    // 展示名 / 单位仍从字段注册表解析
    expect(kpiResultFields()[0].displayName).toBe('销售额')
    expect(kpiResultFields()[0].unit).toBe('元')
  })

  it('falls back to the dataset registry before any result set exists', () => {
    seedReadyResultSet()
    resetResultSet('empty')
    expect(kpiResultFields().map((field) => field.name)).toEqual(['amount', 'region'])
  })

  it('refuses to generate without datasets', async () => {
    const { ok, message } = await generateResultSet()
    expect(ok).toBe(false)
    expect(message).toBe('尚未配置数据集')
    expect(state.resultSet.status).toBe('empty')
  })

  it('rejects multi-dataset pipelines that have no Python script', async () => {
    state.datasets = [
      { id: 'a', sourceType: 'jdbc', sourceLabel: 'JDBC · a', alias: 'a', fields: [], filters: [] },
      { id: 'b', sourceType: 'jdbc', sourceLabel: 'JDBC · b', alias: 'b', fields: [], filters: [] },
    ] as DatasetConfig[]
    const { ok, message } = await generateResultSet()
    expect(ok).toBe(false)
    expect(message).toBe('多数据集时必须配置 Python 脚本')
    expect(state.resultSet.status).toBe('failed')
  })
})
