import type { InsightComponent, InsightComponentData } from '@/types'

export interface AiAnalysisTableView {
  currentPage: number
  pageSize: number
  total: number
  columns: string[]
  rows: string[][]
  sort?: { field: string; order: 'ascending' | 'descending' } | null
}

export interface AiAnalysisSnapshot {
  pageId: string
  pageTitle: string
  filters: Record<string, unknown>
  components: Array<{
    componentId: string
    title: string
    type: 'kpi' | 'chart' | 'table'
    status: string
    error?: string
    fieldLabels?: Record<string, string>
    metrics?: string[]
    dimensions?: string[]
    kpi?: InsightComponentData['kpi']
    kpiList?: InsightComponentData['kpiList']
    chart?: Record<string, unknown>
    table?: {
      columns: string[]
      rows: string[][]
      pagination: { currentPage: number; pageSize: number; total: number }
      sort?: AiAnalysisTableView['sort']
    }
  }>
}

type VisibleComponent = InsightComponent & {
  layout?: unknown
  children?: VisibleComponent[]
}

/** 收集当前可见的数据组件；组合卡片只递归当前激活页签。 */
export function collectVisibleDashboardComponents(components: InsightComponent[]): VisibleComponent[] {
  const visible: VisibleComponent[] = []
  const visit = (items: Array<InsightComponent | VisibleComponent> | undefined): void => {
    for (const component of items ?? []) {
      if (component.type === 'combination') {
        const containerConfig = component.containerConfig
        const activeTab = containerConfig?.tabs.find((tab) => tab.id === containerConfig.activeTab)
        const activeChildren = containerConfig?.tabs.length
          ? activeTab?.children
          : component.children
        visit(activeChildren as VisibleComponent[] | undefined)
      } else {
        visible.push(component as VisibleComponent)
      }
    }
  }
  visit(components)
  return visible
}

export function collectVisibleAnalysisComponents(components: InsightComponent[]): VisibleComponent[] {
  return collectVisibleDashboardComponents(components)
    .filter((component) => component.type === 'kpi' || component.type === 'chart' || component.type === 'table')
}

export function collectVisibleAiAnalysisComponents(components: InsightComponent[]): VisibleComponent[] {
  return collectVisibleDashboardComponents(components)
    .filter((component) => component.type === 'aiAnalysis')
}

export function buildAiAnalysisSnapshot(input: {
  pageId: string
  pageTitle: string
  components: InsightComponent[]
  componentDataMap: Record<string, InsightComponentData>
  tableViews: Record<string, AiAnalysisTableView>
  filters: Record<string, unknown>
}): AiAnalysisSnapshot {
  const components = collectVisibleAnalysisComponents(input.components).map((component) => {
    const data = input.componentDataMap[component.id]
    const snapshot: AiAnalysisSnapshot['components'][number] = {
      componentId: component.id,
      title: component.title || component.id,
      type: component.type as 'kpi' | 'chart' | 'table',
      status: data?.queryStatus ?? (data ? 'success' : 'unavailable'),
      error: data?.error,
      fieldLabels: data?.fieldLabels,
      metrics: component.dataSource?.metrics,
      dimensions: component.dataSource?.dimensions,
    }

    if (component.type === 'kpi') {
      snapshot.kpi = data?.kpi
      snapshot.kpiList = data?.kpiList
    } else if (component.type === 'chart') {
      snapshot.chart = data?.option
    } else {
      const table = data?.table
      const visible = input.tableViews[component.id]
      if (table || visible) {
        const columns = visible?.columns ?? table?.columns ?? []
        const rows = visible?.rows ?? table?.rows ?? []
        snapshot.table = {
          columns,
          rows,
          pagination: {
            currentPage: visible?.currentPage ?? 1,
            pageSize: visible?.pageSize ?? Math.max(rows.length, 1),
            total: visible?.total ?? table?.totalCount ?? table?.rows.length ?? 0,
          },
          sort: visible?.sort,
        }
      }
    }
    return snapshot
  })

  return {
    pageId: input.pageId,
    pageTitle: input.pageTitle,
    filters: input.filters,
    components,
  }
}
