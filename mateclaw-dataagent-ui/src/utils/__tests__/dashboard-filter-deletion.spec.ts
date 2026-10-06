import { describe, expect, it } from 'vitest'
import type { InsightDashboardSchema, InsightComponent } from '@/types'
import { findFilterBindingDependents, removeDanglingFilterBindings, removeFilterBindings } from '../dashboard-filter-deletion'

function component(overrides: Partial<InsightComponent> & Pick<InsightComponent, 'id' | 'title' | 'type'>): InsightComponent {
  return {
    id: overrides.id,
    title: overrides.title,
    type: overrides.type,
    position: { x: 0, y: 0, w: 4, h: 3 },
    ...overrides,
  } as InsightComponent
}

function schemaFixture(): InsightDashboardSchema {
  const boundChart = component({
    id: 'chart-1', title: '趋势图', type: 'chart', boundFilterIds: ['filter-1', 'filter-2'],
    config: {
      datasetPipeline: {
        datasetInputs: [{
          datasetId: 'ds-1', inputName: 'orders', queryConfig: {
            parameterBindings: [
              { filterComponentId: 'filter-1', parameterName: 'region', field: 'region', operator: 'eq' },
              { filterComponentId: 'filter-2', parameterName: 'date', field: 'date', operator: 'gte' },
            ],
          },
        }],
        boundFilterComponentIds: ['filter-1', 'filter-2'],
        scriptFilterBindings: [{ filterComponentId: 'filter-1', inputNames: ['orders'] }],
        finalResultQueryConfig: {
          filterFields: [
            { filterComponentId: 'filter-1', field: 'region' },
            { filterComponentId: 'filter-2', field: 'date' },
          ],
        },
      },
    },
  })
  const nestedTable = component({ id: 'table-1', title: '明细表', type: 'table', boundFilterIds: ['filter-1'] })
  const combination = component({
    id: 'combo-1', title: '分析容器', type: 'combination',
    containerConfig: { layoutMode: 'free', tabs: [{ id: 'tab-1', title: '明细', children: [nestedTable] }] },
  })
  return {
    version: '1',
    pages: [
      { id: 'page-1', name: '指标视角', components: [
        component({ id: 'filter-1', title: '区域筛选', type: 'filter' }),
        component({ id: 'filter-2', title: '日期筛选', type: 'timeFilter' }),
        boundChart,
        combination,
      ] },
      { id: 'page-2', name: '计划视角', components: [
        component({ id: 'filter-3', title: '另一个筛选器', type: 'filter' }),
      ] },
    ],
    scriptFilterBindings: [{ filterComponentId: 'filter-1', inputNames: ['legacy-input'] }],
    datasetInputs: [{
      datasetId: 'legacy-ds', inputName: 'legacy-input', displayName: '兼容输入',
      queryConfig: {
        displayFields: [],
        parameterBindings: [{ filterComponentId: 'filter-1', parameterName: 'region', field: 'region', operator: 'eq' }],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
    }],
  }
}

describe('dashboard filter deletion', () => {
  it('finds direct and nested components that reference the filter, including page names', () => {
    const schema = schemaFixture()

    expect(findFilterBindingDependents(schema, 'filter-1')).toEqual([
      { componentId: 'chart-1', componentTitle: '趋势图', pageName: '指标视角' },
      { componentId: 'table-1', componentTitle: '明细表', pageName: '指标视角' },
      { componentId: 'dashboard-input:legacy-input', componentTitle: '仪表盘脚本数据集「兼容输入」', pageName: '仪表盘级脚本' },
    ])
  })

  it('removes only the deleted filter references and preserves components and other filter bindings', () => {
    const schema = schemaFixture()

    removeFilterBindings(schema, 'filter-1')

    const chart = schema.pages[0].components.find(item => item.id === 'chart-1')!
    expect(chart.boundFilterIds).toEqual(['filter-2'])
    const pipeline = chart.config!.datasetPipeline as any
    expect(pipeline.boundFilterComponentIds).toEqual(['filter-2'])
    expect(pipeline.datasetInputs[0].queryConfig.parameterBindings).toEqual([
      { filterComponentId: 'filter-2', parameterName: 'date', field: 'date', operator: 'gte' },
    ])
    expect(pipeline.scriptFilterBindings).toEqual([])
    expect(pipeline.finalResultQueryConfig.filterFields).toEqual([{ filterComponentId: 'filter-2', field: 'date' }])
    const nestedTable = schema.pages[0].components.find(item => item.id === 'combo-1')!.containerConfig!.tabs[0].children[0]
    expect(nestedTable.boundFilterIds).toEqual([])
    expect(schema.scriptFilterBindings).toEqual([])
    expect(schema.datasetInputs?.[0].queryConfig?.parameterBindings).toEqual([])
    expect(schema.pages[0].components.some(item => item.id === 'chart-1')).toBe(true)
  })

  it('cleans historical references to missing filters across pages and nested dataset configs, preserving valid filters', () => {
    const schema = schemaFixture()
    const chart = schema.pages[0].components.find(item => item.id === 'chart-1')!
    chart.boundFilterIds = ['filter-1', 'deleted-filter']
    const pipeline = chart.config!.datasetPipeline as any
    pipeline.boundFilterComponentIds.push('deleted-filter')
    pipeline.scriptFilterBindings.push({ filterComponentId: 'deleted-filter', inputNames: ['orders'] })
    pipeline.finalResultQueryConfig.filterFields.push({ filterComponentId: 'deleted-filter', field: 'orphan' })
    pipeline.datasetInputs[0].queryConfig.parameterBindings.push({
      filterComponentId: 'deleted-filter', parameterName: 'orphan', field: 'orphan', operator: 'eq',
    })
    schema.scriptFilterBindings!.push({ filterComponentId: 'deleted-filter', inputNames: ['legacy-input'] })
    schema.datasetInputs![0].queryConfig!.parameterBindings.push({
      filterComponentId: 'deleted-filter', parameterName: 'orphan', field: 'orphan', operator: 'eq',
    })

    expect(removeDanglingFilterBindings(schema)).toEqual(['deleted-filter'])

    expect(chart.boundFilterIds).toEqual(['filter-1'])
    expect(pipeline.boundFilterComponentIds).toEqual(['filter-1', 'filter-2'])
    expect(pipeline.scriptFilterBindings).toEqual([{ filterComponentId: 'filter-1', inputNames: ['orders'] }])
    expect(pipeline.finalResultQueryConfig.filterFields).toEqual([
      { filterComponentId: 'filter-1', field: 'region' },
      { filterComponentId: 'filter-2', field: 'date' },
    ])
    expect(pipeline.datasetInputs[0].queryConfig.parameterBindings).toHaveLength(2)
    expect(schema.scriptFilterBindings).toEqual([{ filterComponentId: 'filter-1', inputNames: ['legacy-input'] }])
    expect(schema.datasetInputs![0].queryConfig!.parameterBindings).toEqual([
      { filterComponentId: 'filter-1', parameterName: 'region', field: 'region', operator: 'eq' },
    ])
    expect(schema.pages[0].components.some(item => item.id === 'chart-1')).toBe(true)
  })

  it('does not report changes when no dangling references exist', () => {
    expect(removeDanglingFilterBindings(schemaFixture())).toEqual([])
  })
})
