import type { InsightCombinationChild, InsightComponent } from '@/types'

/**
 * Apply the editable component configuration to its combination-card child.
 * Layout and nested children remain owned by the canvas/container, while KPI
 * projection and dataset pipeline are owned by the attribute panel.
 */
export function mergeCombinationChildComponentUpdate(
  child: InsightCombinationChild,
  updated: InsightComponent,
): InsightCombinationChild {
  return {
    ...child,
    title: updated.title,
    titleBarStyle: updated.titleBarStyle,
    titleIconStyle: updated.titleIconStyle,
    themeAccentGroup: updated.themeAccentGroup,
    componentColor: updated.componentColor,
    visualStyle: updated.visualStyle,
    chartType: updated.chartType,
    config: updated.config,
    tabs: updated.tabs,
    dataSource: updated.dataSource,
    boundFilterIds: updated.boundFilterIds,
    enableTimeFilter: updated.enableTimeFilter,
    multiKpi: updated.multiKpi,
    kpiMetrics: updated.kpiMetrics,
    children: child.type === 'combination' ? child.children ?? updated.children ?? [] : child.children,
    containerConfig: child.type === 'combination'
      ? { ...child.containerConfig, ...updated.containerConfig }
      : child.containerConfig,
  }
}
