import { describe, expect, it } from 'vitest'
import {
  addCombinationTab,
  combinationChildToComponent,
  combinationTabChildCount,
  componentToCombinationChild,
  defaultCombinationChildLayout,
  duplicateCombinationTab,
  findCombinationChild,
  moveCombinationTab,
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
  it('可以从多层组合卡片和页签中找到嵌套组件', () => {
    const nested = makeContainer([], [{
      id: 'nested-tab', title: '子策略贡献表', children: [makeChild('table-target')],
    }])
    const parent = makeContainer([], [{
      id: 'parent-tab', title: '策略视角', children: [{
        ...componentToCombinationChild(nested, { x: 0, y: 0, col: 12 }),
      }],
    }])

    expect(findCombinationChild(parent, 'table-target')?.id).toBe('table-target')
  })

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

describe('组合卡片页签 · 移动位置', () => {
  it('页签后移：顺序更新，内容与激活态不变', () => {
    const c = makeContainer([], [
      { id: 'tab_a', title: '页签 1', children: [makeChild('c1')] },
      { id: 'tab_b', title: '页签 2', children: [] },
      { id: 'tab_c', title: '页签 3', children: [] },
    ])
    const res = moveCombinationTab(c, 'tab_a', 2)

    expect(res).toEqual({ moved: true, fromIndex: 0, toIndex: 2 })
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_b', 'tab_c', 'tab_a'])
    expect(c.containerConfig!.tabs[2].children.map((x) => x.id)).toEqual(['c1'])
    expect(c.containerConfig!.activeTab).toBe('tab_a') // 激活态保持
  })

  it('页签前移与边界钳制：toIndex 越界时收敛到有效范围', () => {
    const c = makeContainer([], [
      { id: 'tab_a', title: '页签 1', children: [] },
      { id: 'tab_b', title: '页签 2', children: [] },
    ])
    expect(moveCombinationTab(c, 'tab_b', 0).moved).toBe(true)
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_b', 'tab_a'])
    // 越界钳制：toIndex 99 钳到 1、-3 钳到 0，均按钳制结果移动
    expect(moveCombinationTab(c, 'tab_b', 99)).toEqual({ moved: true, fromIndex: 0, toIndex: 1 })
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_a', 'tab_b'])
    expect(moveCombinationTab(c, 'tab_b', -3)).toEqual({ moved: true, fromIndex: 1, toIndex: 0 })
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_b', 'tab_a'])
    // 原位不动：moved=false
    expect(moveCombinationTab(c, 'tab_b', 0)).toEqual({ moved: false, fromIndex: 0, toIndex: 0 })
  })

  it('页签不存在或无页签态时不做改动', () => {
    const noTabs = makeContainer([makeChild('c1')])
    expect(moveCombinationTab(noTabs, 'tab_x', 0).moved).toBe(false)
    const c = makeContainer([], [{ id: 'tab_a', title: '页签 1', children: [] }])
    expect(moveCombinationTab(c, 'tab_missing', 0).moved).toBe(false)
  })
})

describe('组合卡片页签 · 复制页签', () => {
  it('复制页签：插入源页签之后、激活新页签、标题追加副本、内容与配置完整保留', () => {
    const c = makeContainer([], [
      { id: 'tab_a', title: '策略视角', children: [makeChild('c1'), makeChild('c2')] },
      { id: 'tab_b', title: '页签 2', children: [] },
    ])
    let seq = 0
    const res = duplicateCombinationTab(c, 'tab_a', () => `new_${++seq}`)

    expect(res.insertedIndex).toBe(1)
    expect(c.containerConfig!.tabs.map((t) => t.id)).toEqual(['tab_a', 'new_1', 'tab_b'])
    expect(c.containerConfig!.tabs[1].title).toBe('策略视角 副本')
    expect(c.containerConfig!.tabs[1].children).toHaveLength(2)
    // 子组件 id 全部重生成，内容字段保留
    expect(c.containerConfig!.tabs[1].children.map((x) => x.id)).toEqual(['new_2', 'new_3'])
    expect(c.containerConfig!.tabs[1].children[0].title).toBe('c1')
    expect(c.containerConfig!.activeTab).toBe('new_1')
    // 源页签不受影响
    expect(c.containerConfig!.tabs[0].children.map((x) => x.id)).toEqual(['c1', 'c2'])
  })

  it('复制的嵌套组合卡片内部页签与子组件 id 全部重生成', () => {
    const nested = makeContainer([], [{
      id: 'nested-tab', title: '子页签', children: [makeChild('nested-child')],
    }])
    const c = makeContainer([], [{
      id: 'tab_a', title: '外层', children: [componentToCombinationChild(nested, { x: 0, y: 0, col: 12 })],
    }])
    let seq = 0
    const res = duplicateCombinationTab(c, 'tab_a', () => `id_${++seq}`)

    const copy = res.created!.children[0]
    expect(copy.id).not.toBe(c.containerConfig!.tabs[0].children[0].id)
    const copyNestedTabs = (copy as InsightCombinationChild).containerConfig!.tabs
    expect(copyNestedTabs[0].id).not.toBe('nested-tab')
    expect(copyNestedTabs[0].children[0].id).not.toBe('nested-child')
    expect(copyNestedTabs[0].children[0].title).toBe('nested-child')
  })

  it('源页签不存在时不做改动', () => {
    const c = makeContainer([], [{ id: 'tab_a', title: '页签 1', children: [] }])
    const res = duplicateCombinationTab(c, 'tab_missing', () => 'x')

    expect(res.created).toBeNull()
    expect(c.containerConfig!.tabs).toHaveLength(1)
    expect(c.containerConfig!.activeTab).toBe('tab_a')
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
      tabs: [{
        id: 'tab-summary',
        title: '概览',
        dataSource: { datasourceId: '', metrics: [], dimensions: [], filters: [], limit: 100 },
        titleIconStyle: { iconKey: 'trend-charts', colorMode: 'custom', color: '#aa5522', strokeWidth: 3 },
      }],
      position: { x: 3, y: 8, w: 6, h: 4 },
    }
    const child = componentToCombinationChild(source, defaultCombinationChildLayout('kpi', 40, 60))

    expect(child.id).toBe('canvas-kpi-1')
    expect(child.title).toBe('策略概括')
    expect(child.titleBarStyle).toBe('accent')
    expect(child.visualStyle?.shadow).toBe('medium')
    expect(child.dataSource?.datasourceId).toBe('ds-1')
    expect(child.multiKpi).toBe(true)
    expect(child.tabs?.[0]?.titleIconStyle).toEqual({
      iconKey: 'trend-charts', colorMode: 'custom', color: '#aa5522', strokeWidth: 3,
    })
    expect(child.layout).toEqual({ x: 40, y: 60, col: 6, h: 96 })
    expect((child as unknown as { position?: unknown }).position).toBeUndefined()
  })
})

describe('组合卡片 · 子卡片拖出为顶层组件', () => {
  it('按比例换算栅格尺寸并去掉自由布局坐标', () => {
    const child = makeChild('c1')
    child.layout = { x: 24, y: 36, col: 7, h: 180 }
    const component = combinationChildToComponent(child, { x: 2, y: 5 })

    expect(component.id).toBe('c1')
    expect(component.position).toEqual({ x: 2, y: 5, w: 14, h: Math.round((180 + 12) / 42) })
    expect((component as unknown as { layout?: unknown }).layout).toBeUndefined()
  })

  it('列宽与高度越界时钳制到合法范围', () => {
    const child = makeChild('c2')
    child.layout = { x: 0, y: 0, col: 12, h: 24 }
    const component = combinationChildToComponent(child, { x: -4, y: -1 })

    expect(component.position).toMatchObject({ x: 0, y: 0, w: 24, h: 3 })
  })

  it('高度缺省时回退默认 4 行', () => {
    const child = makeChild('c3')
    child.layout = { x: 0, y: 0, col: 6 }
    const component = combinationChildToComponent(child, { x: 0, y: 0 })

    expect(component.position).toMatchObject({ w: 12, h: 4 })
  })
})
