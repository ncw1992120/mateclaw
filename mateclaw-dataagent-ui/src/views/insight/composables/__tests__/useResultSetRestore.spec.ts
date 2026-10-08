import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { InsightComponent } from '@/types'
import type { ComponentDatasetPipeline } from '@/types'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
import { collectResultSetComponents, fetchResultSetRows, reconcileKpiProjection, restoreResultSetData, toComponentData } from '../useResultSetRestore'

const getExecutionResult = vi.hoisted(() => vi.fn())
const fetchDatasetSampleRows = vi.hoisted(() => vi.fn())
vi.mock('@/api/insight-dashboard', () => ({ getExecutionResult }))
vi.mock('../../components/dataset-sample-query', () => ({ fetchDatasetSampleRows }))

beforeEach(() => {
  getExecutionResult.mockReset()
  fetchDatasetSampleRows.mockReset()
})

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

  it('恢复 Python KPI 时以 execution envelope 的实际列修正过期结果集列元数据', async () => {
    const component = {
      id: 'python-kpi-restore',
      type: 'kpi',
      config: {
        datasetPipeline: {
          datasetInputs: [],
          script: 'result = rows',
          finalResultQueryConfig: {
            displayFields: [
              { field: '转化规模', title: '转化规模', role: 'measure' },
              { field: '转化人数', title: '转化人数', role: 'measure' },
            ],
          },
          resultSet: {
            source: 'script', status: 'ready', executionId: 'exec-latest',
            // 页面旧版本遗留的列元数据，与 execution 的真实输出不一致。
            columns: [{ name: '旧字段一' }, { name: '旧字段二' }],
          },
        },
      },
      kpiMetrics: ['旧字段一', '旧字段二', '旧字段三'].map((fieldKey, index) => ({
        fieldKey, displayName: fieldKey, unit: '', helperText: '', visible: true,
        x: index * 160, y: 0, w: 160, h: 80, styles: defaultMetricStyles(),
      })),
    } as unknown as InsightComponent
    getExecutionResult.mockResolvedValue({
      envelope: {
        schemaVersion: '1.0', kind: 'table',
        data: {
          columns: [
            { name: '转化规模', title: '转化规模', dataType: 'number', nullable: false },
            { name: '转化人数', title: '转化人数', dataType: 'number', nullable: false },
          ],
          rows: [{ 转化规模: 27948000, 转化人数: 2964 }],
        },
        meta: { rowCount: 1, truncated: false },
      },
    })

    const data = await restoreResultSetData([component])

    expect(component.kpiMetrics?.map(({ fieldKey }) => fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(data[component.id].kpiList?.map(({ value }) => value)).toEqual(['27948000', '2964'])
  })

  it('恢复 Python 表格时遵循最终查询配置字段，并重放上次应用的筛选和排序', async () => {
    const component = {
      id: 'python-table',
      type: 'table',
      config: {
        pythonAppliedResultView: {
          filters: [{ field: 'region', op: '=', value: '华东' }],
          sort: { field: 'amount', direction: 'desc' },
        },
        datasetPipeline: {
          datasetInputs: [],
          script: 'result = rows',
          finalResultQueryConfig: {
            schemaFingerprint: 'python-result-v1',
            confirmed: true,
            displayFields: [{ field: 'amount', title: '金额', role: 'measure' }],
            filterFields: [],
            sortPolicy: { enabled: true, mode: 'single', allowedFields: ['amount'] },
            paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
          },
          resultSet: { source: 'script', status: 'ready', executionId: 'exec-table', columns: [] },
        },
      },
    } as unknown as InsightComponent
    getExecutionResult.mockResolvedValue({
      envelope: {
        schemaVersion: '1.0', kind: 'table',
        data: {
          columns: [
            { name: 'region', title: '地区', dataType: 'string', nullable: false },
            { name: 'amount', title: '金额', dataType: 'number', nullable: false },
            { name: 'legacy', title: '旧字段', dataType: 'string', nullable: false },
          ],
          rows: [
            { region: '华东', amount: 10, legacy: 'x' },
            { region: '华南', amount: 99, legacy: 'y' },
            { region: '华东', amount: 30, legacy: 'z' },
          ],
        },
        meta: { rowCount: 3, truncated: false },
      },
    })

    const data = await restoreResultSetData([component])

    expect(data[component.id].table).toEqual({ columns: ['amount'], rows: [['30'], ['10']] })
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

describe('fetchResultSetRows', () => {
  it('恢复数据集结果时重放保存的查询字段、运行筛选和限制行数', async () => {
    fetchDatasetSampleRows.mockResolvedValue({ rows: [{ amount: 12 }], columns: ['amount'] })
    const pipeline: ComponentDatasetPipeline = {
      datasetInputs: [{
        datasetId: 'dataset-restore',
        inputName: 'table_ab',
        sourceType: 'JDBC_SQL',
        sourceConfig: { datasourceId: 'db-1', sql: 'select amount from sales' },
        queryConfig: {
          displayFields: [{ field: 'amount', title: '金额', role: 'measure' }],
          parameterBindings: [],
          sortPolicy: { enabled: false, mode: 'single', allowedFields: [] },
          paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
        },
        lastQueryState: {
          filters: [
            { filterComponentId: 'date-filter', field: 'biz_date', parameterName: 'start', timeBoundary: 'start', value: '2026-10-01', enabled: true },
            { filterComponentId: 'date-filter', field: 'biz_date', parameterName: '', timeBoundary: 'end', value: '2026-10-08', enabled: true },
          ],
          parameters: {}, sort: null, page: 1, pageSize: 100, queryLimit: 700, timeGranularity: 'DAY',
        },
      }],
    }

    const rows = await fetchResultSetRows(pipeline, {
      source: 'dataset', status: 'ready', columns: [{ name: 'amount' }], rowCount: 1, generatedAt: '',
    })

    expect(rows).toEqual([{ amount: 12 }])
    expect(fetchDatasetSampleRows).toHaveBeenCalledWith(
      expect.objectContaining({ alias: 'table_ab', lastQueryState: pipeline.datasetInputs[0].lastQueryState }),
      [{ id: 'date-filter', type: 'timeFilter' }],
      { limit: 700 },
    )
  })
})
