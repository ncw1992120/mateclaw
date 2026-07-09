import { ref, reactive, computed } from 'vue'
import type {
  InsightComponent,
  DashboardFilterContext,
  TimeRangeValue,
  FilterScope,
  FilterComponentConfig,
  TimeFilterComponentConfig,
} from '@/types'

/**
 * 仪表盘筛选上下文管理（支持作用范围机制）
 *
 * 负责收集筛选值（时间范围 + 维度筛选），并根据筛选器的作用范围（全局/组件绑定）
 * 分发筛选事件到受影响的组件。
 *
 * 作用范围规则：
 * - 全局筛选器（scope=global 或未设置）：影响所有未绑定专属筛选器的数据组件
 * - 组件绑定筛选器（scope=scoped + targetComponentIds）：仅影响绑定的组件
 * - 图表组件绑定了专属筛选器（boundFilterIds 非空）后，不再受全局筛选器影响
 *
 * @param components 仪表盘组件列表的 getter
 * @param onFilterChange 筛选变化回调（传入筛选上下文，由调用方决定如何取数）
 */
export function useDashboardFilterContext(
  components: () => InsightComponent[],
  onFilterChange: (context: DashboardFilterContext) => void,
) {
  /** 全局时间范围 */
  const globalTimeRange = ref<TimeRangeValue | undefined>()
  /** 全局维度筛选值映射（field -> value） */
  const globalDimensionFilterMap = reactive<Record<string, string | string[]>>({})

  /** 组件绑定筛选器状态：filterId -> { timeRange?, dimensionFilters: { field -> value } } */
  const scopedFilterStates = reactive<Record<string, {
    timeRange?: TimeRangeValue
    dimensionFilters: Record<string, string | string[]>
  }>>({})

  /** 当前全局筛选上下文（计算属性，不含 sourceFilterId） */
  const filterContext = computed<DashboardFilterContext>(() => ({
    timeRange: globalTimeRange.value,
    dimensionFilters: Object.entries(globalDimensionFilterMap).map(([field, value]) => ({
      field,
      value,
    })),
  }))

  /** 判断是否有任何筛选条件（全局 + 组件绑定） */
  const hasFilters = computed(() => {
    const hasGlobal = globalTimeRange.value !== undefined || Object.keys(globalDimensionFilterMap).length > 0
    if (hasGlobal) return true
    return Object.values(scopedFilterStates).some(s =>
      s.timeRange !== undefined || Object.keys(s.dimensionFilters).length > 0
    )
  })

  /**
   * 获取筛选器组件的作用范围配置
   */
  function getFilterScope(filterComponentId: string): { scope: FilterScope; targetComponentIds: string[] } {
    const comp = components().find(c => c.id === filterComponentId)
    if (!comp) return { scope: 'global', targetComponentIds: [] }

    if (comp.type === 'filter') {
      const config = comp.config as FilterComponentConfig | undefined
      return {
        scope: config?.scope ?? 'global',
        targetComponentIds: config?.targetComponentIds ?? [],
      }
    }
    if (comp.type === 'timeFilter') {
      const config = comp.config as TimeFilterComponentConfig | undefined
      return {
        scope: config?.scope ?? 'global',
        targetComponentIds: config?.targetComponentIds ?? [],
      }
    }
    return { scope: 'global', targetComponentIds: [] }
  }

  /**
   * 判断一个数据组件是否受指定筛选器影响
   */
  function isComponentAffectedByFilter(dataComponent: InsightComponent, filterComponentId: string, filterScope: FilterScope): boolean {
    const boundFilterIds = dataComponent.boundFilterIds

    if (filterScope === 'scoped') {
      // 组件绑定筛选器：检查数据组件的 boundFilterIds 是否包含此筛选器
      return boundFilterIds?.includes(filterComponentId) ?? false
    }

    // 全局筛选器：影响所有未绑定任何专属筛选器的组件
    return !boundFilterIds || boundFilterIds.length === 0
  }

  /**
   * 设置时间范围（由筛选器组件触发，支持作用范围）
   */
  function setTimeRange(range: TimeRangeValue | undefined, sourceFilterId?: string): void {
    if (sourceFilterId) {
      const { scope } = getFilterScope(sourceFilterId)
      if (scope === 'scoped') {
        // 组件绑定筛选器的时间范围
        if (!scopedFilterStates[sourceFilterId]) {
          scopedFilterStates[sourceFilterId] = { dimensionFilters: {} }
        }
        scopedFilterStates[sourceFilterId].timeRange = range
        // 通知受影响的组件
        emitScopedFilterChange(sourceFilterId)
        return
      }
    }
    // 全局时间范围（不传 sourceFilterId，避免被误判为 scoped 筛选器）
    globalTimeRange.value = range
    onFilterChange({ ...filterContext.value })
  }

  /**
   * 设置维度筛选值（由筛选器组件触发，支持作用范围）
   */
  function setDimensionFilter(field: string, value: string | string[], sourceFilterId?: string): void {
    if (sourceFilterId) {
      const { scope } = getFilterScope(sourceFilterId)
      if (scope === 'scoped') {
        if (!scopedFilterStates[sourceFilterId]) {
          scopedFilterStates[sourceFilterId] = { dimensionFilters: {} }
        }
        scopedFilterStates[sourceFilterId].dimensionFilters[field] = value
        emitScopedFilterChange(sourceFilterId)
        return
      }
    }
    globalDimensionFilterMap[field] = value
    onFilterChange({ ...filterContext.value })
  }

  /**
   * 通知组件绑定筛选器变化
   * 构建包含 sourceFilterId 的筛选上下文，后端根据此 ID 判断哪些组件需要更新
   */
  function emitScopedFilterChange(filterId: string): void {
    const state = scopedFilterStates[filterId]
    if (!state) return

    const context: DashboardFilterContext = {
      timeRange: state.timeRange,
      dimensionFilters: Object.entries(state.dimensionFilters).map(([field, value]) => ({
        field,
        value,
      })),
      sourceFilterId: filterId,
    }
    onFilterChange(context)
  }

  /**
   * 清除所有筛选（全局 + 组件绑定）
   */
  function resetFilters(): void {
    globalTimeRange.value = undefined
    Object.keys(globalDimensionFilterMap).forEach((key) => {
      delete globalDimensionFilterMap[key]
    })
    Object.keys(scopedFilterStates).forEach((key) => {
      delete scopedFilterStates[key]
    })
    onFilterChange(filterContext.value)
  }

  /**
   * 获取指定数据组件应生效的完整筛选上下文
   * 合并全局筛选 + 该组件绑定的所有专属筛选器
   */
  function getEffectiveFilterContextForComponent(componentId: string): DashboardFilterContext {
    const comp = components().find(c => c.id === componentId)
    if (!comp) return filterContext.value

    const boundFilterIds = comp.boundFilterIds

    // 未绑定专属筛选器 → 仅使用全局筛选
    if (!boundFilterIds || boundFilterIds.length === 0) {
      return filterContext.value
    }

    // 绑定了专属筛选器 → 合并所有绑定的筛选器状态
    let timeRange: TimeRangeValue | undefined
    const dimensionFilterMap: Record<string, string | string[]> = {}

    for (const filterId of boundFilterIds) {
      const state = scopedFilterStates[filterId]
      if (!state) continue
      if (state.timeRange) {
        timeRange = state.timeRange // 后绑定的覆盖先绑定的
      }
      Object.entries(state.dimensionFilters).forEach(([field, value]) => {
        dimensionFilterMap[field] = value
      })
    }

    return {
      timeRange,
      dimensionFilters: Object.entries(dimensionFilterMap).map(([field, value]) => ({
        field,
        value,
      })),
    }
  }

  return {
    globalTimeRange,
    globalDimensionFilterMap,
    scopedFilterStates,
    filterContext,
    hasFilters,
    setTimeRange,
    setDimensionFilter,
    resetFilters,
    getFilterScope,
    isComponentAffectedByFilter,
    getEffectiveFilterContextForComponent,
  }
}
