import { beforeEach, describe, expect, it } from 'vitest'
import {
  buildPipeline,
  datasetFromInput,
  migrateKpiMetrics,
  useInsight,
  type DatasetConfig,
} from '../useInsight'
import type { DashboardDatasetInput, KpiMetricConfig } from '@/types'
import { reconcileFieldMetas } from '@/utils/field-mapping'
import { buildKpiMetrics, defaultMetricLayout, defaultMetricStyles } from '@/utils/kpi-metrics'

const { state } = useInsight()

/** 老配置：fieldMappings.target 是展示名，filters/kpiMetrics 里存的也是展示名 */
function legacyInput(): DashboardDatasetInput {
  return {
    datasetId: '9',
    inputName: 'orders',
    displayName: '订单',
    sourceType: 'JDBC_SQL',
    sourceConfig: { datasourceId: '1', sql: 'select * from orders' },
    fieldMappings: [
      { source: 'strategy_id', target: '策略 ID' },
      { source: 'event_date', target: 'event_date' },
    ],
    filters: [{ field: '策略 ID', op: '=', value: 'S1' }] as unknown as DashboardDatasetInput['filters'],
  }
}

beforeEach(() => {
  state.datasets.splice(0, state.datasets.length)
  state.kpiMetrics = []
  state.filterBindings = []
})

describe('存量归一（决策 4）· 读入老配置', () => {
  it('fieldMappings → 字段注册表：source=字段名、target=展示名', () => {
    const ds = datasetFromInput(legacyInput(), 0)
    expect(ds.fields.map((f) => f.name)).toEqual(['strategy_id', 'event_date'])
    // target 与 source 相同视为「未设置展示名」，存 undefined 以便跟随后端最新展示名
    expect(ds.fields[0].displayName).toBe('策略 ID')
    expect(ds.fields[1].displayName).toBeUndefined()
  })

  it('filters 里的展示名反解为字段名并就地写回', () => {
    const ds = datasetFromInput(legacyInput(), 0)
    expect(ds.filters[0].field).toBe('strategy_id')
    expect(ds.unresolvedFields).toBeUndefined()
  })

  it('无法识别的引用原样保留并收集到 unresolvedFields（面板告警）', () => {
    const input = legacyInput()
    input.filters = [{ field: 'not_exist', op: '=', value: 'x' }] as unknown as DashboardDatasetInput['filters']
    const ds = datasetFromInput(input, 0)
    expect(ds.filters[0].field).toBe('not_exist')
    expect(ds.unresolvedFields).toEqual(['not_exist'])
  })

  it('kpiMetrics 迁移：fieldKey 归一为字段名，旧展示名/单位写入注册表', () => {
    state.datasets.splice(0, state.datasets.length, datasetFromInput(legacyInput(), 0))
    const legacy: KpiMetricConfig[] = [
      {
        fieldKey: '策略 ID', // 旧配置存的是展示名
        displayName: '策略编号', // 用户最后改过的名字 → 权威
        unit: '个',
        helperText: '较上期',
        visible: true,
        ...defaultMetricLayout(0),
        styles: defaultMetricStyles(),
      },
    ]
    const migrated = migrateKpiMetrics(legacy)
    expect(migrated[0].fieldKey).toBe('strategy_id')
    // 迁移以 kpiMetrics 现值为准写入注册表（用户最后编辑的即权威）
    expect(state.datasets[0].fields[0].displayName).toBe('策略编号')
    expect(state.datasets[0].fields[0].unit).toBe('个')
  })

  it('迁移后投影：展示名/单位由注册表统一具化（不再有两份副本）', () => {
    state.datasets.splice(0, state.datasets.length, datasetFromInput(legacyInput(), 0))
    const legacy: KpiMetricConfig[] = [
      {
        fieldKey: '策略 ID',
        displayName: '策略编号',
        unit: '个',
        helperText: '较上期',
        visible: true,
        ...defaultMetricLayout(0),
        styles: defaultMetricStyles(),
      },
    ]
    const metrics = buildKpiMetrics(
      state.datasets[0].fields,
      migrateKpiMetrics(legacy),
    )
    expect(metrics[0].fieldKey).toBe('strategy_id')
    expect(metrics[0].displayName).toBe('策略编号')
    expect(metrics[0].unit).toBe('个')
    expect(metrics[0].helperText).toBe('较上期')
  })
})

describe('存量归一（决策 4）· 保存写回（物理归一）', () => {
  it('buildPipeline 输出：fieldMappings.source=字段名、filters[].field=字段名', () => {
    const ds: DatasetConfig = datasetFromInput(legacyInput(), 0)
    state.datasets.splice(0, state.datasets.length, ds)
    const pipeline = buildPipeline()
    const input = pipeline.datasetInputs[0]
    expect(input.fieldMappings).toEqual([
      { source: 'strategy_id', target: '策略 ID' },
      { source: 'event_date', target: 'event_date' },
    ])
    expect((input.filters as unknown as { field: string }[])[0].field).toBe('strategy_id')
  })

  it('改名后下推引用不变：筛选条件存的是字段名，展示名改动不影响', () => {
    const ds: DatasetConfig = datasetFromInput(legacyInput(), 0)
    state.datasets.splice(0, state.datasets.length, ds)
    ds.fields[0].displayName = '策略编号（新）'
    const pipeline = buildPipeline()
    // 展示名改了，下推的字段名仍是 strategy_id
    expect((pipeline.datasetInputs[0].filters as unknown as { field: string }[])[0].field).toBe('strategy_id')
    expect(pipeline.datasetInputs[0].fieldMappings?.[0]).toEqual({
      source: 'strategy_id',
      target: '策略编号（新）',
    })
  })
})

describe('schema diff（决策 5）· 字段消失打失效标记', () => {
  it('消失的字段保留配置并标 stale（不清用户编辑）', () => {
    const ds = datasetFromInput(legacyInput(), 0)
    ds.fields.push({ name: 'gone_field', displayName: '已下线字段' })
    state.datasets.splice(0, state.datasets.length, ds)

    ds.fields = reconcileFieldMetas(
      [
        { name: 'strategy_id', displayName: '策略 ID' },
        { name: 'event_date' },
      ],
      ds.fields,
      ds.schema,
    )
    const gone = ds.fields.find((f) => f.name === 'gone_field')
    expect(gone).toBeDefined()
    expect(gone?.stale).toBe(true)
    expect(gone?.displayName).toBe('已下线字段')
  })
})
