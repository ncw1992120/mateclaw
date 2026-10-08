import { describe, expect, it } from 'vitest'
import type { InsightComponent, InsightComponentData } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { persistAppliedComponentResultSet, persistAppliedKpiProjection } from '../appliedKpiProjection'

describe('persistAppliedKpiProjection', () => {
  it('keeps only the applied KPI fields and preserves matching metric presentation', () => {
    const component = {
      id: 'strategy-summary',
      type: 'kpi',
      kpiMetrics: [
        ...['touch_count', 'send_count', 'touch_users', 'send_users'].map((fieldKey, index) => ({
          fieldKey,
          displayName: fieldKey,
          unit: '',
          helperText: '',
          visible: true,
          x: index * 170,
          y: 0,
          w: 160,
          h: 72,
          styles: defaultMetricStyles(),
        })),
        ...['metric_date', 'strategy_name'].map((fieldKey, index) => ({
          fieldKey,
          displayName: fieldKey,
          unit: '',
          helperText: '',
          visible: true,
          x: index * 170,
          y: 80,
          w: 160,
          h: 72,
          styles: defaultMetricStyles(),
        })),
      ],
    } as unknown as InsightComponent
    component.kpiMetrics![0].helperText = '保留的说明'
    component.kpiMetrics![0].x = 37

    const data: InsightComponentData = {
      componentId: component.id,
      renderType: 'kpi',
      resultFieldProjection: true,
      kpiList: ['touch_count', 'send_count', 'touch_users', 'send_users'].map((fieldKey) => ({
        fieldKey,
        name: `${fieldKey} 展示名`,
        value: '1',
      })),
    }

    expect(persistAppliedKpiProjection(component, data)).toBe(true)
    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual([
      'touch_count', 'send_count', 'touch_users', 'send_users',
    ])
    expect(component.kpiMetrics?.[0]).toMatchObject({ helperText: '保留的说明', x: 37 })
    expect(component.kpiMetrics?.[0].displayName).toBe('touch_count 展示名')
  })

  it('does not persist ordinary previews that were not explicitly applied', () => {
    const component = {
      id: 'strategy-summary',
      type: 'kpi',
      kpiMetrics: [{ fieldKey: 'legacy_dimension' }],
    } as unknown as InsightComponent
    const data: InsightComponentData = {
      componentId: component.id,
      renderType: 'kpi',
      kpiList: [{ fieldKey: 'new_metric', name: '新指标', value: '1' }],
    }

    expect(persistAppliedKpiProjection(component, data)).toBe(false)
    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['legacy_dimension'])
  })
})

describe('persistAppliedComponentResultSet', () => {
  it('explicitly applied dataset data becomes restorable dataset result metadata', () => {
    const component = {
      id: 'table-1',
      type: 'table',
      config: {
        datasetPipeline: {
          datasetInputs: [{ datasetId: 'dataset-1', inputName: 'table_ab', lastQueryState: { queryLimit: 100 } }],
          script: '',
        },
      },
    } as unknown as InsightComponent

    const changed = persistAppliedComponentResultSet(component, {
      componentId: 'table-1',
      renderType: 'table',
      resultFieldProjection: true,
      appliedResultSource: 'dataset',
      appliedResultRows: [{ amount: 12 }],
      fieldLabels: { amount: '金额' },
    })

    expect(changed).toBe(true)
    expect((component.config?.datasetPipeline as any).resultSet).toMatchObject({
      source: 'dataset', status: 'ready', rowCount: 1, columns: [{ name: 'amount' }],
    })
    expect((component.config?.datasetPipeline as any).datasetInputs[0].lastQueryState).toEqual({ queryLimit: 100 })
  })

  it('keeps Python execution reference when persisting an applied Python view', () => {
    const component = {
      id: 'python-table',
      type: 'table',
      config: {
        datasetPipeline: {
          datasetInputs: [], script: 'result = rows',
          resultSet: { source: 'script', status: 'ready', columns: [{ name: 'amount' }], rowCount: 2, generatedAt: 'now', executionId: 'exec-1' },
        },
      },
    } as unknown as InsightComponent

    const changed = persistAppliedComponentResultSet(component, {
      componentId: 'python-table',
      renderType: 'table',
      resultFieldProjection: true,
      appliedResultSource: 'script',
      appliedResultRows: [{ amount: 12 }],
    })

    expect(changed).toBe(true)
    expect((component.config?.datasetPipeline as any).resultSet).toMatchObject({
      source: 'script', status: 'ready', executionId: 'exec-1', rowCount: 1,
    })
  })
})
