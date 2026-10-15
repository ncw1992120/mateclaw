import type { CombinationChildLayout, CombinationTab, InsightCombinationChild, InsightComponent, InsightComponentType } from '@/types'
import { cloneCombinationTabForPaste, type ClipboardIdFactory } from '@/utils/insight-component-clipboard'

/**
 * 组合卡片页签增删的纯逻辑（画布与属性面板共用，保证两个入口行为一致）。
 *
 * 数据归属规则：
 * - 无页签态：子卡片存放在 `component.children`；
 * - 有页签态：子卡片存放在 `containerConfig.tabs[].children`，`activeTab` 指向当前页签；
 * - 首次添加页签：把 `component.children` 整体平移进第一个页签（已配置的组件不能丢）；
 * - 删除非最后页签：页签内组件随页签一并删除（调用方负责二次确认）；
 * - 删除最后一个页签：页签内组件平移回 `component.children`，回到无页签态。
 */

/** 生成页签 ID（与画布/面板其它临时 id 同风格） */
export function createCombinationTabId(): string {
  return `tab_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

/** 页签内子卡片数量（页签不存在返回 0） */
export function combinationTabChildCount(component: InsightComponent, tabId: string): number {
  const tab = component.containerConfig?.tabs.find((x) => x.id === tabId)
  return tab?.children.length ?? 0
}

/** 在组合卡片及其所有页签、嵌套组合卡片中查找指定子组件。 */
export function findCombinationChild(
  container: Pick<InsightComponent, 'children' | 'containerConfig'>,
  childId: string,
): InsightCombinationChild | null {
  const children = [
    ...(container.children ?? []),
    ...(container.containerConfig?.tabs.flatMap((tab) => tab.children) ?? []),
  ]
  for (const child of children) {
    if (child.id === childId) return child
    if (child.type === 'combination') {
      const nested = findCombinationChild(child, childId)
      if (nested) return nested
    }
  }
  return null
}

/** 组合子组件的默认布局，画布拖入和已有组件移入共用同一套尺寸规则。 */
export function defaultCombinationChildLayout(
  type: InsightComponentType,
  x = 24,
  y = 24,
): CombinationChildLayout {
  return {
    x,
    y,
    col: type === 'chart' || type === 'table' ? 7 : 6,
    h: type === 'kpi' ? 96 : type === 'aiAnalysis' ? 160 : 180,
  }
}

/** 将画布顶层组件转换为组合卡片子组件，保留数据配置和组件视觉配置。 */
export function componentToCombinationChild(
  component: InsightComponent,
  layout: CombinationChildLayout,
): InsightCombinationChild {
  const clone = JSON.parse(JSON.stringify(component)) as InsightComponent
  const { position: _position, ...child } = clone
  return { ...child, layout } as InsightCombinationChild
}

/**
 * 将组合卡片子组件拖出为画布顶层组件：去掉自由布局坐标，按比例换算画布栅格尺寸。
 * 组合内容区为 12 列、画布为 24 列，宽度等比 ×2；高度按 rowHeight=30 + margin=12 反推行数。
 */
export function combinationChildToComponent(
  child: InsightCombinationChild,
  position: { x: number; y: number },
): InsightComponent {
  const clone = JSON.parse(JSON.stringify(child)) as InsightCombinationChild
  const { layout } = clone
  const { layout: _layout, ...component } = clone
  const result = component as InsightComponent
  result.position = {
    x: Math.max(0, position.x),
    y: Math.max(0, position.y),
    w: Math.min(24, Math.max(2, Math.round((layout.col / 12) * 24))),
    h: layout.h && layout.h > 0 ? Math.max(3, Math.round((layout.h + 12) / 42)) : 4,
  }
  return result
}

export interface AddCombinationTabResult {
  /** 新建的页签（无 containerConfig 时返回 null） */
  tab: CombinationTab | null
  /** 是否把无页签态的已有子卡片平移进了这个页签 */
  migratedFromContainer: boolean
  /** 平移进页签的组件数 */
  migratedCount: number
}

/** 新增页签：首次添加时把容器内已有子卡片平移进第一个页签 */
export function addCombinationTab(component: InsightComponent): AddCombinationTabResult {
  const cfg = component.containerConfig
  if (!cfg) {
    return { tab: null, migratedFromContainer: false, migratedCount: 0 }
  }
  const isFirstTab = cfg.tabs.length === 0
  const existing = component.children ?? []
  const tab: CombinationTab = {
    id: createCombinationTabId(),
    title: `页签 ${cfg.tabs.length + 1}`,
    children: isFirstTab ? [...existing] : [],
  }
  if (isFirstTab && existing.length > 0) {
    component.children = []
  }
  cfg.tabs.push(tab)
  cfg.activeTab = tab.id
  return {
    tab,
    migratedFromContainer: isFirstTab && existing.length > 0,
    migratedCount: isFirstTab ? existing.length : 0,
  }
}

export interface RemoveCombinationTabResult {
  /** 页签是否存在并被移除 */
  removed: boolean
  /** 删除的是最后一个页签（组件已平移回容器，无信息丢失） */
  migrated: boolean
  /** 平移回容器的组件数 */
  migratedCount: number
  /** 随页签一并删除的组件数 */
  deletedCount: number
}

/** 删除页签：最后一个页签时把组件平移回容器，其余情况组件随页签删除 */
export function removeCombinationTab(component: InsightComponent, tabId: string): RemoveCombinationTabResult {
  const cfg = component.containerConfig
  if (!cfg) {
    return { removed: false, migrated: false, migratedCount: 0, deletedCount: 0 }
  }
  const idx = cfg.tabs.findIndex((x) => x.id === tabId)
  if (idx < 0) {
    return { removed: false, migrated: false, migratedCount: 0, deletedCount: 0 }
  }
  const [tab] = cfg.tabs.splice(idx, 1)
  const isLastTab = cfg.tabs.length === 0
  if (isLastTab) {
    component.children = [...(component.children ?? []), ...tab.children]
    cfg.activeTab = undefined
    return { removed: true, migrated: true, migratedCount: tab.children.length, deletedCount: 0 }
  }
  if (cfg.activeTab === tabId) {
    cfg.activeTab = cfg.tabs[0].id
  }
  return { removed: true, migrated: false, migratedCount: 0, deletedCount: tab.children.length }
}

export interface MoveCombinationTabResult {
  moved: boolean
  fromIndex: number
  toIndex: number
}

/**
 * 移动页签位置（拖拽排序/左右移动共用）。
 * `toIndex` 越界时钳制到有效范围；激活页签保持不变（页签随内容整体移动）。
 */
export function moveCombinationTab(component: InsightComponent, tabId: string, toIndex: number): MoveCombinationTabResult {
  const cfg = component.containerConfig
  if (!cfg) return { moved: false, fromIndex: -1, toIndex: -1 }
  const fromIndex = cfg.tabs.findIndex((x) => x.id === tabId)
  if (fromIndex < 0) return { moved: false, fromIndex: -1, toIndex: -1 }
  const target = Math.max(0, Math.min(cfg.tabs.length - 1, toIndex))
  if (target === fromIndex) return { moved: false, fromIndex, toIndex: target }
  const [tab] = cfg.tabs.splice(fromIndex, 1)
  cfg.tabs.splice(target, 0, tab)
  return { moved: true, fromIndex, toIndex: target }
}

export interface DuplicateCombinationTabResult {
  /** 新复制出的页签（源页签不存在返回 null） */
  created: CombinationTab | null
  sourceIndex: number
  insertedIndex: number
}

/**
 * 复制页签：内容（含嵌套组合卡片的子组件/页签）深拷贝并重生成全部 ID，
 * 插入到源页签之后并激活；标题追加「副本」以区分。
 */
export function duplicateCombinationTab(
  component: InsightComponent,
  tabId: string,
  createId: ClipboardIdFactory,
): DuplicateCombinationTabResult {
  const cfg = component.containerConfig
  if (!cfg) return { created: null, sourceIndex: -1, insertedIndex: -1 }
  const sourceIndex = cfg.tabs.findIndex((x) => x.id === tabId)
  if (sourceIndex < 0) return { created: null, sourceIndex: -1, insertedIndex: -1 }
  const source = cfg.tabs[sourceIndex]
  const clone = cloneCombinationTabForPaste(source, createId)
  clone.title = `${source.title} 副本`
  cfg.tabs.splice(sourceIndex + 1, 0, clone)
  cfg.activeTab = clone.id
  return { created: clone, sourceIndex, insertedIndex: sourceIndex + 1 }
}
