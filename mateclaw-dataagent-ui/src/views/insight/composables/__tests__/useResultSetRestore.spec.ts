import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import type { ComponentDatasetPipeline } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { collectResultSetComponents, reconcileKpiProjection, toComponentData } from '../useResultSetRestore'

describe('reconcileKpiProjection', () => {
  it('adds result-set KPI fields missing from an older persisted projection', () => {
    const component = {
      id: 'kpi-1',
      type: 'kpi',
      kpiMetrics: [{
        fieldKey: 'orders', displayName: '订单数', unit: '', helperText: '', visible: true,
        x: 0, y: 0, w: 160, h: 80, styles: defaultMetricStyles(),
      }],
    } as unknown as InsightComponent

    reconcileKpiProjection(component, [{ name: 'orders' }, { name: 'revenue' }])

    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['orders', 'revenue'])
    expect(component.kpiMetrics?.[0].displayName).toBe('订单数')
  })

  it('restores all configured measure KPIs and excludes the date dimension when result metadata is incomplete', () => {
    const component = {
      id: 'kpi-2',
      type: 'kpi',
      kpiMetrics: [],
    } as unknown as InsightComponent
    const pipeline: ComponentDatasetPipeline = {
      datasetInputs: [{
        datasetId: 'dataset-1',
        inputName: 'table1',
        queryConfig: {
          displayFields: [
            { field: 'touch_count', title: '触达次数', role: 'measure' },
            { field: 'touch_users', title: '触达人数', role: 'measure' },
            { field: 'send_count', title: '下发次数', role: 'measure' },
            { field: 'send_users', title: '下发人数', role: 'measure' },
            { field: 'metric_time', title: '指标日期', role: 'dimension' },
          ],
          parameterBindings: [],
          sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
          paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 100, returnTotalCount: false },
        },
      }],
    }

    reconcileKpiProjection(component, [{ name: 'touch_count' }, { name: 'metric_time' }], pipeline)

    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual([
      'touch_count', 'touch_users', 'send_count', 'send_users',
    ])
    expect(component.kpiMetrics?.map(({ displayName }) => displayName)).toEqual([
      '触达次数', '触达人数', '下发次数', '下发人数',
    ])
  })

  it('reconciles a Python result schema before projecting values onto a KPI card', () => {
    const component = {
      id: 'python-kpi',
      type: 'kpi',
      config: {
        datasetPipeline: {
          datasetInputs: [],
          script: 'result = rows',
          finalResultQueryConfig: {
            schemaFingerprint: 'python-result-v1',
            displayFields: [
              { field: '转化规模', title: '转化规模', role: 'measure' },
              { field: '转化人数', title: '转化人数', role: 'measure' },
            ],
            filterFields: [],
            sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
            paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 100, returnTotalCount: false },
          },
        },
      },
      kpiMetrics: [{
        fieldKey: '下发次数', displayName: '下发次数', unit: '', helperText: '', visible: true,
        x: 0, y: 0, w: 160, h: 80, styles: defaultMetricStyles(),
      }],
    } as unknown as InsightComponent

    const data = toComponentData(component, [{ 转化规模: 1250000, 转化人数: 128 }])

    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(data.kpiList?.map(({ value }) => value)).toEqual(['1250000', '128'])
  })

  it('includes KPI children inside combination tabs in the same result-set restoration pass', () => {
    const child = {
      id: 'nested-kpi',
      type: 'kpi',
      title: '子指标卡',
      config: {},
      kpiMetrics: [],
      layout: { x: 0, y: 0, col: 6 },
    }
    const combination = {
      id: 'combination-1',
      type: 'combination',
      title: '组合卡片',
      position: { x: 0, y: 0, w: 6, h: 4 },
      containerConfig: {
        layoutMode: 'grid',
        tabs: [{ id: 'tab-1', title: '指标', children: [child] }],
      },
    } as unknown as InsightComponent

    const nested = collectResultSetComponents([combination]).find(({ id }) => id === 'nested-kpi')
    expect(nested).toBeDefined()
    reconcileKpiProjection(nested!, [{ name: 'metric_time' }, { name: 'orders' }], {
      datasetInputs: [{
        datasetId: 'dataset-1',
        inputName: 'table1',
        queryConfig: {
          displayFields: [
            { field: 'metric_time', title: '指标日期', role: 'dimension' },
            { field: 'orders', title: '订单数', role: 'measure' },
          ],
          parameterBindings: [],
          sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
          paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 100, returnTotalCount: false },
        },
      }],
    })

    expect(child.kpiMetrics.map(({ fieldKey }) => fieldKey)).toEqual(['orders'])
  })
})
