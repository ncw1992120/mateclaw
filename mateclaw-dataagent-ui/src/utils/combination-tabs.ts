import type { CombinationTab, InsightComponent } from '@/types'

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
