import { ref, reactive, computed } from 'vue'
import type {
  InsightComponent,
  InsightCombinationChild,
  DashboardRuntimeFilterState,
  DashboardFilterContext,
  TimeRangeValue,
  FilterScope,
  FilterComponentConfig,
  TimeFilterComponentConfig,
  TimeFilterDefaultPreset,
} from '@/types'

/** Flatten container children and tab children while retaining their component IDs. */
export function collectDashboardComponents(components: InsightComponent[]): InsightComponent[] {
  const result: InsightComponent[] = []
  const visited = new Set<string>()
  const visit = (component: InsightComponent | InsightCombinationChild): void => {
    if (visited.has(component.id)) return
    visited.add(component.id)
    result.push(component as InsightComponent)
    for (const child of component.children ?? []) visit(child)
    for (const tab of component.containerConfig?.tabs ?? []) {
      for (const child of tab.children ?? []) visit(child)
    }
  }
  components.forEach(visit)
  return result
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function resolveTimeFilterDefault(preset: TimeFilterDefaultPreset | undefined): TimeRangeValue | undefined {
  if (!preset) return undefined

  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  let start = today
  let end = today

  if (preset === 'yesterday') {
    start = new Date(today)
    start.setDate(start.getDate() - 1)
    end = start
  } else if (preset === 'monthToDate') {
    start = new Date(today.getFullYear(), today.getMonth(), 1)
  } else if (preset === 'yearToDate') {
    start = new Date(today.getFullYear(), 0, 1)
  }

  return { preset: 'custom', start: formatLocalDate(start), end: formatLocalDate(end) }
}

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
  /** 当前运行态按 filter component id 保存，避免同字段筛选器互相覆盖。 */
  const runtimeFilterState = reactive<DashboardRuntimeFilterState>({})

  const allComponents = () => collectDashboardComponents(components())

  function filterMetadata(filterComponentId: string): Pick<DashboardRuntimeFilterState[string], 'field' | 'scope' | 'targetComponentIds'> | undefined {
    const comp = allComponents().find(c => c.id === filterComponentId)
    if (!comp) return undefined
    if (comp.type === 'filter') {
      const config = comp.config as FilterComponentConfig | undefined
      if (!config?.field) return undefined
      return { field: config.field, scope: config.scope ?? 'global', targetComponentIds: [...(config.targetComponentIds ?? [])] }
    }
    if (comp.type === 'timeFilter') {
      const config = comp.config as TimeFilterComponentConfig | undefined
      if (!config?.field) return undefined
      return { field: config.field, scope: config.scope ?? 'global', targetComponentIds: [...(config.targetComponentIds ?? [])] }
    }
    return undefined
  }

  function setRuntimeFilterValue(filterComponentId: string, value: DashboardRuntimeFilterState[string]['value']): void {
    const metadata = filterMetadata(filterComponentId)
    if (!metadata) return
    runtimeFilterState[filterComponentId] = { ...metadata, value: Array.isArray(value) ? [...value] : value }
  }

  /** Snapshot the latest values so async preview refreshes never consult configuration defaults. */
  function getRuntimeFilterState(): DashboardRuntimeFilterState {
    return Object.fromEntries(Object.entries(runtimeFilterState).map(([id, entry]) => [id, {
      ...entry,
      targetComponentIds: [...entry.targetComponentIds],
      value: Array.isArray(entry.value) ? [...entry.value] : entry.value,
    }]))
  }

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
    const metadata = filterMetadata(filterComponentId)
    return metadata
      ? { scope: metadata.scope, targetComponentIds: metadata.targetComponentIds }
      : { scope: 'global', targetComponentIds: [] }
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
  function setTimeRange(range: TimeRangeValue | undefined | null, sourceFilterId?: string): void {
    const activeRange = range == null ? undefined : range
    if (sourceFilterId) setRuntimeFilterValue(sourceFilterId, activeRange)
    if (sourceFilterId) {
      const { scope } = getFilterScope(sourceFilterId)
      if (scope === 'scoped') {
        // 组件绑定筛选器的时间范围
        if (!scopedFilterStates[sourceFilterId]) {
          scopedFilterStates[sourceFilterId] = { dimensionFilters: {} }
        }
        scopedFilterStates[sourceFilterId].timeRange = activeRange
        // 通知受影响的组件
        emitScopedFilterChange(sourceFilterId)
        return
      }
    }
    // 全局时间范围（不传 sourceFilterId，避免被误判为 scoped 筛选器）
    globalTimeRange.value = activeRange
    onFilterChange({ ...filterContext.value })
  }

  /**
   * 设置维度筛选值（由筛选器组件触发，支持作用范围）
   */
  function setDimensionFilter(field: string, value: string | string[] | undefined | null, sourceFilterId?: string): void {
    const activeValue = value == null || value === '' || (Array.isArray(value) && value.length === 0)
      ? undefined
      : value
    if (sourceFilterId) setRuntimeFilterValue(sourceFilterId, activeValue)
    if (sourceFilterId) {
      const { scope } = getFilterScope(sourceFilterId)
      if (scope === 'scoped') {
        if (!scopedFilterStates[sourceFilterId]) {
          scopedFilterStates[sourceFilterId] = { dimensionFilters: {} }
        }
        if (activeValue === undefined) {
          delete scopedFilterStates[sourceFilterId].dimensionFilters[field]
        } else {
          scopedFilterStates[sourceFilterId].dimensionFilters[field] = activeValue
        }
        emitScopedFilterChange(sourceFilterId)
        return
      }
    }
    if (activeValue === undefined) {
      delete globalDimensionFilterMap[field]
    } else {
      globalDimensionFilterMap[field] = activeValue
    }
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
    for (const filterId of Object.keys(runtimeFilterState)) {
      runtimeFilterState[filterId].value = undefined
    }
    onFilterChange(filterContext.value)
  }

  /** 将筛选器配置中的默认值装载为初始运行态；不触发多次网络请求。 */
  function initializeDefaults(): void {
    const currentFilters = allComponents().filter(comp => comp.type === 'filter' || comp.type === 'timeFilter')
    const currentIds = new Set(currentFilters.map(comp => comp.id))
    for (const filterId of Object.keys(runtimeFilterState)) {
      if (!currentIds.has(filterId)) delete runtimeFilterState[filterId]
    }

    globalTimeRange.value = undefined
    Object.keys(globalDimensionFilterMap).forEach(key => delete globalDimensionFilterMap[key])
    Object.keys(scopedFilterStates).forEach(key => delete scopedFilterStates[key])

    for (const comp of currentFilters) {
      const metadata = filterMetadata(comp.id)
      if (!metadata) continue
      if (runtimeFilterState[comp.id]) {
        // Rebuild the compatibility contexts from the live value, never from defaultValue.
        const value = runtimeFilterState[comp.id].value
        if (metadata.scope === 'scoped') {
          const state = scopedFilterStates[comp.id] ??= { dimensionFilters: {} }
          if (comp.type === 'timeFilter') state.timeRange = value as TimeRangeValue | undefined
          else if (typeof value === 'string' || Array.isArray(value)) state.dimensionFilters[metadata.field] = value
        } else if (comp.type === 'timeFilter') {
          globalTimeRange.value = value as TimeRangeValue | undefined
        } else if (typeof value === 'string' || Array.isArray(value)) {
          globalDimensionFilterMap[metadata.field] = value
        }
        continue
      }

      const config = comp.config as FilterComponentConfig | TimeFilterComponentConfig | undefined
      const value = comp.type === 'filter'
        ? (config as FilterComponentConfig | undefined)?.defaultValue
        : resolveTimeFilterDefault((config as TimeFilterComponentConfig | undefined)?.defaultPreset)
      const activeValue = value === null || value === '' || (Array.isArray(value) && value.length === 0) ? undefined : value
      runtimeFilterState[comp.id] = { ...metadata, value: Array.isArray(activeValue) ? [...activeValue] : activeValue }
      if (metadata.scope === 'scoped') {
        const state = scopedFilterStates[comp.id] ??= { dimensionFilters: {} }
        if (comp.type === 'timeFilter') state.timeRange = activeValue as TimeRangeValue | undefined
        else if (typeof activeValue === 'string' || Array.isArray(activeValue)) state.dimensionFilters[metadata.field] = activeValue
      } else if (comp.type === 'timeFilter') {
        globalTimeRange.value = activeValue as TimeRangeValue | undefined
      } else if (typeof activeValue === 'string' || Array.isArray(activeValue)) {
        globalDimensionFilterMap[metadata.field] = activeValue
      }
    }
  }

  /**
   * 获取指定数据组件应生效的完整筛选上下文
   * 合并全局筛选 + 该组件绑定的所有专属筛选器
   */
  function getEffectiveFilterContextForComponent(componentId: string): DashboardFilterContext {
    const comp = allComponents().find(c => c.id === componentId)
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
    getRuntimeFilterState,
    filterContext,
    hasFilters,
    setTimeRange,
    setDimensionFilter,
    resetFilters,
    initializeDefaults,
    getFilterScope,
    isComponentAffectedByFilter,
    getEffectiveFilterContextForComponent,
  }
}
