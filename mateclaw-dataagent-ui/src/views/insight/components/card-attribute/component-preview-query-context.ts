import type { QueryContext } from '@/types'

/** 构造 Python 编辑器预览用的运行时查询上下文；无页面筛选选择时按空参数查询。 */
export function createComponentPreviewQueryContext(dashboardId: string, componentId: string): QueryContext {
  return {
    dashboardId,
    componentId,
    parameters: {},
    requestId: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  }
}
