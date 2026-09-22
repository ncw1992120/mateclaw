import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import { cloneCombinationChildForPaste, cloneInsightComponentForPaste } from '../insight-component-clipboard'

function createComponent(): InsightComponent {
  return {
    id: 'comp-source',
    type: 'combination',
    title: '策略执行情况',
    position: { x: 2, y: 3, w: 8, h: 6 },
    boundFilterIds: ['filter-source'],
    containerConfig: {
      title: '策略执行情况',
      showTitle: true,
      background: '#fff',
      radius: 12,
      padding: 16,
      layoutMode: 'free',
      tabs: [{
        id: 'tab-source',
        title: '概览',
        children: [{
          id: 'child-source',
          type: 'kpi',
          title: '关联策略数',
          layout: { x: 0, y: 0, col: 6 },
          boundFilterIds: ['filter-source'],
        }],
      }],
      activeTab: 'tab-source',
      style: { border: { enabled: false, color: 'transparent' } },
    },
  }
}

describe('Insight component clipboard', () => {
  it('deep clones a component and remaps nested component and tab IDs', () => {
    const source = createComponent()
    let sequence = 0
    const clone = cloneInsightComponentForPaste(source, (prefix) => `${prefix}-new-${++sequence}`)

    expect(clone).not.toBe(source)
    expect(clone.id).toBe('comp-new-1')
    expect(clone.position).toEqual({ x: 3, y: 4, w: 8, h: 6 })
    expect(clone.containerConfig?.tabs[0].id).toBe('tab-new-2')
    expect(clone.containerConfig?.tabs[0].children[0].id).toBe('comp-new-3')
    expect(clone.boundFilterIds).toEqual(['filter-source'])
    expect(clone.containerConfig?.tabs[0].children[0].boundFilterIds).toEqual(['filter-source'])
    expect(source.position).toEqual({ x: 2, y: 3, w: 8, h: 6 })
  })

  it('clones a combination child with new recursive IDs and an offset layout', () => {
    const source = {
      id: 'child-source',
      type: 'combination' as const,
      title: '策略执行',
      visualStyle: { border: { enabled: true, color: '#123456' } },
      dataSource: { datasourceId: 'source-1', metrics: ['sent_count'] },
      layout: { x: 24, y: 36, col: 6, h: 180 },
      children: [{ id: 'nested-child', type: 'kpi' as const, title: '下发次数', layout: { x: 8, y: 8, col: 6, h: 96 } }],
      containerConfig: { tabs: [{ id: 'tab-source', title: '指标视角', children: [] }], activeTab: 'tab-source' },
    }
    let sequence = 0
    const clone = cloneCombinationChildForPaste(source, (prefix) => `${prefix}-new-${++sequence}`)

    expect(clone.id).toBe('comp-new-1')
    expect(clone.title).toBe(source.title)
    expect(clone.visualStyle).toEqual(source.visualStyle)
    expect(clone.dataSource).toEqual(source.dataSource)
    expect(clone.layout).toMatchObject({ x: 36, y: 48, col: 6, h: 180 })
    expect(clone.children?.[0].id).toBe('comp-new-2')
    expect(clone.containerConfig?.tabs[0].id).toBe('tab-new-3')
    expect(clone.containerConfig?.activeTab).toBe('tab-source')
  })
})
