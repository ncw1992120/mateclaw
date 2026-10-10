import type { CombinationChildLayout, CombinationTab, InsightCombinationChild, InsightComponent } from '@/types'

export type ClipboardIdFactory = (prefix: 'comp' | 'tab') => string

/** 粘贴到组合卡片时沿用复制目标的位置，仅向右下轻微错开。 */
export function getCombinationChildPasteLayout(
  source: CombinationChildLayout,
  offset = { x: 12, y: 12 },
): CombinationChildLayout {
  return {
    ...source,
    x: Math.max(0, source.x + offset.x),
    y: Math.max(0, source.y + offset.y),
  }
}

/**
 * 为画布粘贴创建组件深拷贝。
 *
 * 复制的是配置快照，不复制外部筛选器本身；因此 boundFilterIds 继续指向当前页面已有筛选器。
 * 组合卡片中的子组件、页签和 KPI 组件内部 Tab 会生成新的 ID，避免复制后产生重复引用。
 */
export function cloneInsightComponentForPaste(
  source: InsightComponent,
  createId: ClipboardIdFactory,
  offset = { x: 1, y: 1 },
): InsightComponent {
  const clone = JSON.parse(JSON.stringify(source)) as InsightComponent
  const cloneNode = (node: Record<string, any>): void => {
    node.id = createId('comp')

    if (Array.isArray(node.tabs)) {
      node.tabs.forEach((tab: Record<string, any>) => {
        tab.id = createId('tab')
      })
    }

    if (Array.isArray(node.children)) {
      node.children.forEach((child: Record<string, any>) => cloneNode(child))
    }

    const tabs = node.containerConfig?.tabs
    if (Array.isArray(tabs)) {
      // 先收集旧 id → 新 id 映射，重生成页签 id 后把 activeTab 同步重映射，
      // 否则克隆出的嵌套组合卡片 activeTab 仍指向旧 id，渲染查不到该页签会回退到空 children。
      const tabIdMap = new Map<string, string>()
      tabs.forEach((tab: Record<string, any>) => {
        const newId = createId('tab')
        tabIdMap.set(tab.id, newId)
        tab.id = newId
        if (Array.isArray(tab.children)) {
          tab.children.forEach((child: Record<string, any>) => cloneNode(child))
        }
      })
      if (node.containerConfig?.activeTab != null && tabIdMap.has(node.containerConfig.activeTab)) {
        node.containerConfig.activeTab = tabIdMap.get(node.containerConfig.activeTab)
      }
    }
  }

  cloneNode(clone as unknown as Record<string, any>)
  clone.position = {
    ...clone.position,
    x: Math.max(0, clone.position.x + offset.x),
    y: Math.max(0, clone.position.y + offset.y),
  }
  return clone
}

/** 为组合卡片内部粘贴创建子组件深拷贝。 */
export function cloneCombinationChildForPaste(
  source: InsightCombinationChild,
  createId: ClipboardIdFactory,
  offset = { x: 12, y: 12 },
): InsightCombinationChild {
  const clone = JSON.parse(JSON.stringify(source)) as InsightCombinationChild
  const cloneNode = (node: Record<string, any>): void => {
    node.id = createId('comp')
    if (Array.isArray(node.tabs)) {
      node.tabs.forEach((tab: Record<string, any>) => { tab.id = createId('tab') })
    }
    if (Array.isArray(node.children)) {
      node.children.forEach((child: Record<string, any>) => cloneNode(child))
    }
    const tabs = node.containerConfig?.tabs
    if (Array.isArray(tabs)) {
      // 同步重映射 activeTab，避免克隆出的嵌套组合卡片 activeTab 指向旧 id 而渲染为空。
      const tabIdMap = new Map<string, string>()
      tabs.forEach((tab: Record<string, any>) => {
        const newId = createId('tab')
        tabIdMap.set(tab.id, newId)
        tab.id = newId
        if (Array.isArray(tab.children)) {
          tab.children.forEach((child: Record<string, any>) => cloneNode(child))
        }
      })
      if (node.containerConfig?.activeTab != null && tabIdMap.has(node.containerConfig.activeTab)) {
        node.containerConfig.activeTab = tabIdMap.get(node.containerConfig.activeTab)
      }
    }
  }
  cloneNode(clone as unknown as Record<string, any>)
  clone.layout = getCombinationChildPasteLayout(clone.layout, offset)
  return clone
}

/**
 * 复制组合卡片页签：页签 id、每个子组件及其嵌套页签/子组件的 id 全部重生成，
 * 布局保持原样（页签复制不产生位移错开），标题由调用方处理。
 */
export function cloneCombinationTabForPaste(
  source: CombinationTab,
  createId: ClipboardIdFactory,
): CombinationTab {
  const clone = JSON.parse(JSON.stringify(source)) as CombinationTab
  clone.id = createId('tab')
  clone.children = (clone.children ?? []).map((child) =>
    cloneCombinationChildForPaste(child, createId, { x: 0, y: 0 }))
  return clone
}
