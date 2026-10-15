export interface AloudataMetricDirectoryItem {
  metricName?: string
  metricDisplayName?: string
  [key: string]: unknown
}

export interface AloudataMetricDirectoryNode {
  categoryId: string
  categoryName: string
  metricList?: AloudataMetricDirectoryItem[]
  subCategory?: AloudataMetricDirectoryNode[]
  [key: string]: unknown
}

export interface AloudataCategoryItem {
  categoryId: string
  categoryName: string
  parentId?: string | null
  count?: number | null
}

export interface AloudataCategoryTreeNode extends AloudataCategoryItem {
  count?: number
  children: AloudataCategoryTreeNode[]
}

/** Build a browse tree from Aloudata category/list records. */
export function buildCategoryTree(categories: AloudataCategoryItem[]): AloudataCategoryTreeNode[] {
  const nodes = new Map<string, AloudataCategoryTreeNode>()
  for (const category of categories) {
    if (!category.categoryId || nodes.has(category.categoryId)) continue
    nodes.set(category.categoryId, { ...category, children: [] })
  }

  const roots: AloudataCategoryTreeNode[] = []
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined
    if (parent && parent.categoryId !== node.categoryId) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/** Filter categories by name while preserving matching ancestor paths. */
export function filterCategoryTree(
  roots: AloudataCategoryTreeNode[],
  keyword: string,
): AloudataCategoryTreeNode[] {
  const normalized = keyword.trim().toLocaleLowerCase()
  if (!normalized) return roots

  const filterNode = (node: AloudataCategoryTreeNode): AloudataCategoryTreeNode | undefined => {
    const children = node.children.map(filterNode).filter((child): child is AloudataCategoryTreeNode => Boolean(child))
    if (!node.categoryName.toLocaleLowerCase().includes(normalized) && !children.length) return undefined
    return { ...node, children }
  }

  return roots.map(filterNode).filter((node): node is AloudataCategoryTreeNode => Boolean(node))
}

/** Filter by the display name and technical field name while preserving matching category paths. */
export function filterMetricCategoryTree(
  roots: AloudataMetricDirectoryNode[],
  keyword: string,
): AloudataMetricDirectoryNode[] {
  const normalized = keyword.trim().toLocaleLowerCase()
  if (!normalized) return roots

  const filterNode = (node: AloudataMetricDirectoryNode): AloudataMetricDirectoryNode | undefined => {
    const metricList = (node.metricList || []).filter((metric) =>
      [metric.metricDisplayName, metric.metricName]
        .some((value) => String(value || '').toLocaleLowerCase().includes(normalized)),
    )
    const subCategory = (node.subCategory || [])
      .map(filterNode)
      .filter((child): child is AloudataMetricDirectoryNode => Boolean(child))
    if (!metricList.length && !subCategory.length) return undefined
    return { ...node, metricList, subCategory }
  }

  return roots.map(filterNode).filter((node): node is AloudataMetricDirectoryNode => Boolean(node))
}
