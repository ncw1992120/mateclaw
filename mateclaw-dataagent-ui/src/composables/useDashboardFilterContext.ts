import { ref, reactive, computed } from 'vue'
import type {
  DashboardFilterContext,
  TimeRangeValue,
} from '@/types'

/**
 * 仪表盘筛选上下文管理
 *
 * 负责收集筛选值（时间范围 + 维度筛选），并在筛选变化时触发受影响组件重新取数。
 * 筛选状态是运行时的，不持久化到 Schema。
 *
 * @param components 仪表盘组件列表
 * @param onFilterChange 筛选变化回调（触发重新取数）
 */
export function useDashboardFilterContext(
  components: () => InsightComponent[],
  onFilterChange: (context: DashboardFilterContext) => void,
) {
  /** 时间范围 */
  const timeRange = ref<TimeRangeValue | undefined>()
  /** 维度筛选值映射（field -> value） */
  const dimensionFilterMap = reactive<Record<string, string | string[]>>({})

  /** 当前筛选上下文（计算属性） */
  const filterContext = computed<DashboardFilterContext>(() => ({
    timeRange: timeRange.value,
    dimensionFilters: Object.entries(dimensionFilterMap).map(([field, value]) => ({
      field,
      value,
    })),
  }))

  /** 判断筛选上下文是否为空 */
  const hasFilters = computed(() => {
    return timeRange.value !== undefined || Object.keys(dimensionFilterMap).length > 0
  })

  /** 设置时间范围 */
  function setTimeRange(range: TimeRangeValue | undefined): void {
    timeRange.value = range
    onFilterChange(filterContext.value)
  }

  /** 设置维度筛选值 */
  function setDimensionFilter(field: string, value: string | string[]): void {
    dimensionFilterMap[field] = value
    onFilterChange(filterContext.value)
  }

  /** 清除所有筛选 */
  function resetFilters(): void {
    timeRange.value = undefined
    Object.keys(dimensionFilterMap).forEach((key) => {
      delete dimensionFilterMap[key]
    })
    onFilterChange(filterContext.value)
  }

  return {
    timeRange,
    dimensionFilterMap,
    filterContext,
    hasFilters,
    setTimeRange,
    setDimensionFilter,
    resetFilters,
  }
}
