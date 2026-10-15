import type { FilterComponentConfig } from '@/types'

/** “全部”只是界面选项，不应作为真实字段值下发到数据查询。 */
export const FILTER_ALL_VALUE = '__MATECLAW_FILTER_ALL__'

export type FilterSelectionValue = string | string[] | undefined

/**
 * 将选择器值转换为运行时筛选值。
 * - “全部”表示不添加该字段的筛选条件；
 * - 清空仅在 allowNoFilter=true 时允许产生 undefined；
 * - 多选会移除“全部”哨兵，避免把虚拟值透传到底层数据源。
 */
export function normalizeFilterSelection(
  value: string | string[] | null | undefined,
  config: Pick<FilterComponentConfig, 'selectionMode' | 'allowSelectAll' | 'allowNoFilter'> = {},
): FilterSelectionValue {
  const selectionMode = config.selectionMode ?? 'single'
  const allowSelectAll = config.allowSelectAll ?? false
  const allowNoFilter = config.allowNoFilter ?? true

  if (selectionMode === 'multiple') {
    const values = Array.isArray(value) ? value.filter(Boolean) : value ? [value] : []
    if (allowSelectAll && values.includes(FILTER_ALL_VALUE)) {
      const concreteValues = values.filter((item) => item !== FILTER_ALL_VALUE)
      return concreteValues.length > 0 ? concreteValues : undefined
    }
    if (values.length === 0) return allowNoFilter ? undefined : []
    return values
  }

  if (value === FILTER_ALL_VALUE && allowSelectAll) return undefined
  if (!value) return allowNoFilter ? undefined : ''
  return Array.isArray(value) ? (value[0] ?? '') : value
}

/** 运行态配置，供组件决定 select 的交互模式。 */
export function getFilterSelectionBehavior(config?: Pick<FilterComponentConfig, 'selectionMode' | 'allowSelectAll' | 'allowNoFilter'>) {
  return {
    selectionMode: config?.selectionMode ?? 'single',
    allowSelectAll: config?.allowSelectAll ?? false,
    allowNoFilter: config?.allowNoFilter ?? true,
  } as const
}
