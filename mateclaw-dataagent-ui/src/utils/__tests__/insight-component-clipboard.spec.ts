import { describe, expect, it } from 'vitest'
import type { InsightComponent } from '@/types'
import {
  cloneCombinationChildForPaste,
  cloneCombinationTabForPaste,
  cloneInsightComponentForPaste,
  getCombinationChildPasteLayout,
} from '../insight-component-clipboard'

function createComponent(): InsightComponent {
  return {
    id: 'comp-source',
    type: 'combination',
    title: '策略执行情况',
    position: { x: 2, y: 3, w: 8, h: 6 },
    boundFilterIds: ['filter-source'],
    containerConfig: {
      title: '策略执行情况',
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
    // 画布整卡粘贴同样需同步重映射 activeTab 到新页签 id
    expect(clone.containerConfig?.activeTab).toBe('tab-new-2')
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
    // activeTab 必须同步重映射到新的页签 id，否则克隆出的组合卡片 activeTab 指向旧 id 会渲染为空
    expect(clone.containerConfig?.activeTab).toBe('tab-new-3')
  })

  it('keeps a pasted child just below and to the right of its copied position', () => {
    const source = {
      id: 'child-source',
      type: 'kpi' as const,
      title: '指标卡片',
      layout: { x: 120, y: 80, col: 6, h: 96 },
    }

    expect(getCombinationChildPasteLayout(source.layout)).toEqual({ x: 132, y: 92, col: 6, h: 96 })
  })

  it('cloneCombinationTabForPaste：页签与全部子孙 id 重生成，布局不位移', () => {
    const source = {
      id: 'tab-source',
      title: '策略视角',
      titleIconStyle: { iconKey: 'trend-charts', colorMode: 'theme' },
      children: [
        {
          id: 'child-1', type: 'kpi' as const, title: '下发次数',
          layout: { x: 24, y: 36, col: 6, h: 180 },
          children: [{ id: 'nested-child', type: 'kpi' as const, title: '深层', layout: { x: 8, y: 8, col: 6, h: 96 } }],
          containerConfig: { tabs: [{ id: 'tab-nested', title: '内层', children: [] }], activeTab: 'tab-nested' },
        },
      ],
    } as any
    let sequence = 0
    const clone = cloneCombinationTabForPaste(source, (prefix) => `${prefix}-new-${++sequence}`)

    expect(clone.id).toBe('tab-new-1')
    expect(clone.title).toBe('策略视角')
    // 布局保持原样（页签复制不错位）
    expect(clone.children[0].layout).toEqual(source.children[0].layout)
    expect(clone.children[0].id).toBe('comp-new-2')
    expect(clone.children[0].children[0].id).toBe('comp-new-3')
    expect(clone.children[0].containerConfig.tabs[0].id).toBe('tab-new-4')
  })

  it('cloneCombinationTabForPaste：嵌套组合卡片（containerConfig.tabs 含真实子组件）内容全部复制并重置 id', () => {
    // 精确复现用户场景：顶层页签 → 子组合卡片 → 子组合卡片内部页签含真实子组件（甚至再嵌套一层）
    const source = {
      id: 'tab-source',
      title: '策略视角',
      children: [
        {
          id: 'child-kpi', type: 'kpi' as const, title: '下发次数',
          layout: { x: 24, y: 36, col: 6, h: 180 },
        },
        {
          id: 'child-combo', type: 'combination' as const, title: '内层组合',
          layout: { x: 24, y: 240, col: 12, h: 320 },
          // 真实运行时：嵌套组合卡片内容在 containerConfig.tabs，children 为空数组
          children: [],
          containerConfig: {
            layoutMode: 'free' as const,
            activeTab: 'inner-tab-1',
            tabs: [
              {
                id: 'inner-tab-1', title: '内层页签1',
                children: [
                  { id: 'inner-kpi', type: 'kpi' as const, title: '内层指标', layout: { x: 8, y: 8, col: 6, h: 96 } },
                  {
                    id: 'inner-combo', type: 'combination' as const, title: '再嵌套组合',
                    layout: { x: 8, y: 120, col: 12, h: 160 },
                    children: [],
                    containerConfig: {
                      layoutMode: 'free' as const,
                      activeTab: 'deep-tab-1',
                      tabs: [
                        { id: 'deep-tab-1', title: '深层页签', children: [{ id: 'deep-chart', type: 'chart' as const, title: '深层图表', layout: { x: 8, y: 8, col: 7, h: 180 } }] },
                      ],
                    },
                  },
                ],
              },
              {
                id: 'inner-tab-2', title: '内层页签2',
                children: [{ id: 'inner-table', type: 'table' as const, title: '内层表格', layout: { x: 8, y: 8, col: 7, h: 180 } }],
              },
            ],
          },
        },
      ],
    } as any
    let sequence = 0
    const clone = cloneCombinationTabForPaste(source, (prefix) => `${prefix}-new-${++sequence}`)

    // 顶层页签与直接子组件
    expect(clone.id).toBe('tab-new-1')
    expect(clone.children[0].id).toBe('comp-new-2') // child-kpi
    expect(clone.children[1].id).toBe('comp-new-3') // child-combo（嵌套组合）

    // 嵌套组合卡片自身 id 换新，且其 inner-tab 与 inner 子组件都被复制并换新 id
    const nestedCombo = clone.children[1]
    expect(nestedCombo.containerConfig.tabs).toHaveLength(2)
    expect(nestedCombo.containerConfig.tabs[0].id).not.toBe('inner-tab-1')
    expect(nestedCombo.containerConfig.tabs[0].children).toHaveLength(2)
    expect(nestedCombo.containerConfig.tabs[0].children[0].id).not.toBe('inner-kpi') // inner-kpi 被复制
    expect(nestedCombo.containerConfig.tabs[0].children[0].type).toBe('kpi')
    expect(nestedCombo.containerConfig.tabs[1].children[0].id).not.toBe('inner-table') // inner-table 被复制
    expect(nestedCombo.containerConfig.tabs[1].children[0].type).toBe('table')

    // 再嵌套一层组合卡片的内容也被复制并重置 id
    const deepCombo = nestedCombo.containerConfig.tabs[0].children[1]
    expect(deepCombo.type).toBe('combination')
    expect(deepCombo.containerConfig.tabs).toHaveLength(1)
    expect(deepCombo.containerConfig.tabs[0].id).not.toBe('deep-tab-1')
    expect(deepCombo.containerConfig.tabs[0].children[0].id).not.toBe('deep-chart')
    expect(deepCombo.containerConfig.tabs[0].children[0].title).toBe('深层图表')

    // 内容确为深拷贝（与源互不影响）
    expect(nestedCombo.containerConfig.tabs[0].children[0].title).toBe('内层指标')

    // 关键回归：嵌套组合卡片的 activeTab 必须同步重映射到新的页签 id，否则渲染时
    // getActiveChildren 查不到该 id 会回退到空的 children，表现为「内容没复制」
    const newInnerTab1Id = nestedCombo.containerConfig.tabs[0].id
    expect(nestedCombo.containerConfig.activeTab).toBe(newInnerTab1Id)
    const newDeepTabId = deepCombo.containerConfig.tabs[0].id
    expect(deepCombo.containerConfig.activeTab).toBe(newDeepTabId)
  })
})
