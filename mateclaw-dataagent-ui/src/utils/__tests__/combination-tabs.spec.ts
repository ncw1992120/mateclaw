import { describe, expect, it } from 'vitest'
import {
  addCombinationTab,
  combinationTabChildCount,
  componentToCombinationChild,
  defaultCombinationChildLayout,
  removeCombinationTab,
} from '@/utils/combination-tabs'
import type { InsightCombinationChild, InsightComponent } from '@/types'

function makeChild(id: string, title = id): InsightCombinationChild {
  return {
    id,
    type: 'kpi',
    title,
    layout: { x: 0, y: 0, col: 6, h: 96 },
  }
}

function makeContainer(children: InsightCombinationChild[] = [], tabs: Array<{ id: string; title: string; children: InsightCombinationChild[] }> = []): InsightComponent {
  return {
    id: 'comp_combo',
    type: 'combination',
    title: '组合卡片',
    position: { x: 0, y: 0, w: 6, h: 4 },
    children: [...children],
    containerConfig: {
      title: '',
      showTitle: true,
      background: '#ffffff',
      radius: 12,
      padding: 16,
      layoutMode: 'free',
      tabs: tabs.map((t) => ({ ...t, children: [...t.children] })),
      activeTab: tabs[0]?.id,
      style: { border: { enabled: false, color: 'transparent' } },
    },
  }
}

describe('组合卡片页签 · 首次添加页签', () => {
  it('无页签态已有子卡片时，全部平移进第一个页签并激活该页签', () => {
    const c = makeContainer([makeChild('c1'), makeChild('c2')])
    const res = addCombinationTab(c)

    expect(res.migratedFromContainer).toBe(true)
    expect(res.migratedCount).toBe(2)
    expect(c.children).toEqual([])
    expect(c.containerConfig!.tabs).toHaveLength(1)
    expect(c.containerConfig!.tabs[0].children.map((x) => x.id)).toEqual(['c1', 'c2'])
    expect(c.containerConfig!.activeTab).toBe(c.containerConfig!.tabs[0].id)
  })

  it('无子卡片时新增页签不产生平移', () => {
    const c = makeContainer()
    const res = addCombinationTab(c)

    expect(res.migratedFromContainer).toBe(false)
    expect(res.migratedCount).toBe(0)
    expect(c.containerConfig!.tabs[0].children).toEqual([])
  })

  it('已有页签时再新增，不会再动容器内子卡片', () => {
    const c = makeContainer([], [{ id: 'tab_a', title: '页签 1', children: [makeChild('c1')] }])
    const res = addCombinationTab(c)

    expect(res.migratedCount).toBe(0)
    expect(c.containerConfig!.tabs).toHaveLength(2)
    expect(c.containerConfig!.tabs[1].children).toEqual([])
    expect(c.containerConfig!.activeTab).toBe(c.containerConfig!.tabs[1].id)
  })
})

describe('组合卡片页签 · 删除页签', () => {
  it('删除非最后页签：页签内组件随之删除，数量如实返回', () => {
    const c = makeContainer([], [
      { id: 'tab_a', title: '页签 1', children: [makeChild('c1'), makeChild('c2')] },
      { id: 'tab_b', title: '页签 2', children: [] },
    ])
    const res = removeCombinationTab(c, 'tab_a')

    expect(res).toEqual({ removed: true, migrated: false, migratedCount: 0, deletedCount: 2 })
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_b'])
    expect(c.containerConfig!.activeTab).toBe('tab_b')
    expect(c.children).toEqual([])
  })

  it('删除被激活的页签后，激活态回落到第一个剩余页签', () => {
    const c = makeContainer([], [
      { id: 'tab_a', title: '页签 1', children: [] },
      { id: 'tab_b', title: '页签 2', children: [makeChild('c1')] },
    ])
    c.containerConfig!.activeTab = 'tab_b'
    removeCombinationTab(c, 'tab_b')

    expect(c.containerConfig!.activeTab).toBe('tab_a')
  })

  it('删除最后一个页签：组件平移回容器，回到无页签态', () => {
    const c = makeContainer([makeChild('c0')], [
      { id: 'tab_a', title: '页签 1', children: [makeChild('c1'), makeChild('c2')] },
    ])
    const res = removeCombinationTab(c, 'tab_a')

    expect(res.migrated).toBe(true)
    expect(res.migratedCount).toBe(2)
    expect(res.deletedCount).toBe(0)
    expect(c.containerConfig!.tabs).toEqual([])
    expect(c.containerConfig!.activeTab).toBeUndefined()
    expect(c.children.map((x) => x.id)).toEqual(['c0', 'c1', 'c2'])
  })

  it('页签不存在时不做任何改动', () => {
    const c = makeContainer([makeChild('c1')], [{ id: 'tab_a', title: '页签 1', children: [] }])
    const res = removeCombinationTab(c, 'tab_missing')

    expect(res.removed).toBe(false)
    expect(c.children.map((x) => x.id)).toEqual(['c1'])
    expect(c.containerConfig!.tabs).toHaveLength(1)
  })

  it('页签内组件数量可查询（用于删除前二次确认）', () => {
    const c = makeContainer([], [{ id: 'tab_a', title: '页签 1', children: [makeChild('c1')] }])

    expect(combinationTabChildCount(c, 'tab_a')).toBe(1)
    expect(combinationTabChildCount(c, 'tab_missing')).toBe(0)
  })
})

describe('组合卡片 · 顶层组件移入', () => {
  it('转换时保留组件配置、数据绑定和视觉样式，仅替换为相对布局', () => {
    const source: InsightComponent = {
      id: 'canvas-kpi-1',
      type: 'kpi',
      title: '策略概括',
      titleBarStyle: 'accent',
      visualStyle: { border: { mode: 'theme' }, shadow: 'medium' },
      dataSource: { datasourceId: 'ds-1', metrics: ['metric_a'], dimensions: ['channel'], filters: [], limit: 100 },
      multiKpi: true,
      position: { x: 3, y: 8, w: 6, h: 4 },
    }
    const child = componentToCombinationChild(source, defaultCombinationChildLayout('kpi', 40, 60))

    expect(child.id).toBe('canvas-kpi-1')
    expect(child.title).toBe('策略概括')
    expect(child.titleBarStyle).toBe('accent')
    expect(child.visualStyle?.shadow).toBe('medium')
    expect(child.dataSource?.datasourceId).toBe('ds-1')
    expect(child.multiKpi).toBe(true)
    expect(child.layout).toEqual({ x: 40, y: 60, col: 6, h: 96 })
    expect((child as unknown as { position?: unknown }).position).toBeUndefined()
  })
})
