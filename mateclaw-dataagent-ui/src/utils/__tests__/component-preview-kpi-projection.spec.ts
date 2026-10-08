import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { componentPreviewData } from '@/utils/component-preview-data'
import { projectPythonResultKpi } from '@/utils/kpi-result-projection'

describe('dataset query result KPI projection', () => {
  it.each([
    ['kpi', 'kpi'],
    ['chart', 'echarts'],
    ['table', 'table'],
  ] as const)('marks explicitly applied %s preview data for result restoration', (type, renderType) => {
    const component = { id: `${type}-1`, type, config: {} } as unknown as InsightComponent
    const rows = [{ amount: 12 }]
    const result = componentPreviewData(component, rows, [{ name: 'amount', title: '金额' }])

    expect(result).toMatchObject({
      componentId: component.id,
      renderType,
      resultFieldProjection: true,
      appliedResultRows: rows,
      appliedResultSource: 'dataset',
    })
  })

  it('renders only queried measures when an older KPI config still contains dimensions', () => {
    const component = {
      id: 'strategy-summary',
      type: 'kpi',
      kpiMetrics: ['metric_time', 'metric_name', 'measure_a', 'measure_b', 'measure_c', 'measure_d'].map((fieldKey, index) => ({
        fieldKey,
        displayName: fieldKey,
        unit: '',
        helperText: '',
        visible: true,
        x: index * 160,
        y: 0,
        w: 160,
        h: 80,
        styles: defaultMetricStyles(),
      })),
    } as unknown as InsightComponent
    const result = componentPreviewData(
      component,
      [{ measure_a: 1, measure_b: 2, measure_c: 3, measure_d: 4 }],
      [
        { name: 'measure_a', title: '指标一' },
        { name: 'measure_b', title: '指标二' },
        { name: 'measure_c', title: '指标三' },
        { name: 'measure_d', title: '指标四' },
      ],
    )

    const canvasComponent = projectPythonResultKpi(component, result)

    expect(canvasComponent.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual([
      'measure_a', 'measure_b', 'measure_c', 'measure_d',
    ])
    expect(result.fieldLabels).toEqual({
      measure_a: '指标一',
      measure_b: '指标二',
      measure_c: '指标三',
      measure_d: '指标四',
    })
  })
})
