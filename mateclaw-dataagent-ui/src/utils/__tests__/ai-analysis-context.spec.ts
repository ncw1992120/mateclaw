import { describe, expect, it } from 'vitest'
import { buildAiAnalysisSnapshot, collectVisibleAnalysisComponents } from '../ai-analysis-context'

const position = { x: 0, y: 0, w: 4, h: 3 }

describe('AI analysis context', () => {
  it('collects the current page and active combination tabs only', () => {
    const components = [
      { id: 'page-kpi', type: 'kpi', title: '页面指标', position },
      {
        id: 'combo', type: 'combination', title: '组合卡片', position,
        containerConfig: {
          layoutMode: 'free', activeTab: 'tab-a',
          tabs: [
            { id: 'tab-a', title: '当前页签', children: [{ id: 'active-table', type: 'table', title: '当前表格', layout: { col: 4, h: 100 } }] },
            { id: 'tab-b', title: '其他页签', children: [{ id: 'inactive-chart', type: 'chart', title: '隐藏图表', layout: { col: 4, h: 100 } }] },
          ],
        },
      },
      { id: 'analysis', type: 'aiAnalysis', title: 'AI分析', position },
    ] as any

    expect(collectVisibleAnalysisComponents(components).map((component) => component.id))
      .toEqual(['page-kpi', 'active-table'])
  })

  it('includes current table page rows and pagination metadata in the snapshot', () => {
    const components = [
      { id: 'table', type: 'table', title: '策略明细', position },
    ] as any
    const snapshot = buildAiAnalysisSnapshot({
      pageId: 'page-1',
      pageTitle: '策略视角',
      components,
      componentDataMap: {
        table: { componentId: 'table', renderType: 'table', queryStatus: 'success', table: { columns: ['策略', '人数'], rows: [['A', '12'], ['B', '7']], totalCount: 18 } },
      } as any,
      tableViews: {
        table: { currentPage: 2, pageSize: 1, total: 18, columns: ['策略', '人数'], rows: [['B', '7']] },
      },
      filters: { channel: { value: 'APP' } },
    })

    expect(snapshot).toMatchObject({
      pageId: 'page-1',
      pageTitle: '策略视角',
      filters: { channel: { value: 'APP' } },
      components: [{
        componentId: 'table',
        title: '策略明细',
        status: 'success',
        table: {
          columns: ['策略', '人数'],
          rows: [['B', '7']],
          pagination: { currentPage: 2, pageSize: 1, total: 18 },
        },
      }],
    })
  })
})
