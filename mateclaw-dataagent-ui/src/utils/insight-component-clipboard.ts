import type { InsightCombinationChild, InsightComponent } from '@/types'

export type ClipboardIdFactory = (prefix: 'comp' | 'tab') => string

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
      tabs.forEach((tab: Record<string, any>) => {
        tab.id = createId('tab')
        if (Array.isArray(tab.children)) {
          tab.children.forEach((child: Record<string, any>) => cloneNode(child))
        }
      })
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
      tabs.forEach((tab: Record<string, any>) => {
        tab.id = createId('tab')
        if (Array.isArray(tab.children)) {
          tab.children.forEach((child: Record<string, any>) => cloneNode(child))
        }
      })
    }
  }
  cloneNode(clone as unknown as Record<string, any>)
  clone.layout = {
    ...clone.layout,
    x: Math.max(0, clone.layout.x + offset.x),
    y: Math.max(0, clone.layout.y + offset.y),
  }
  return clone
}
