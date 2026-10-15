/**
 * 对已加载的仪表盘 Schema 做局部组件替换。
 * 预览/属性面板只允许修改当前组件，不能用一个最小 Schema 覆盖整张仪表盘。
 */
export function patchDashboardSchema<T extends { pages?: Array<{ components?: unknown[] }> }>(
  original: T,
  component: Record<string, unknown>,
): T {
  const next = JSON.parse(JSON.stringify(original ?? {})) as T
  const pages = Array.isArray(next.pages) ? next.pages : []
  if (!pages.length) {
    next.pages = [{ components: [component] }]
    return next
  }
  const componentId = String(component.id ?? '')
  let replaced = false
  for (const page of pages) {
    const components = Array.isArray(page.components) ? page.components : []
    const index = components.findIndex((item) => String((item as Record<string, unknown>)?.id ?? '') === componentId)
    if (index >= 0) {
      components[index] = component
      page.components = components
      replaced = true
      break
    }
  }
  if (!replaced) {
    const first = pages[0]
    first.components = [...(Array.isArray(first.components) ? first.components : []), component]
  }
  next.pages = pages
  return next
}
