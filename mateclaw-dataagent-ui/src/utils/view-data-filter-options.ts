export interface ViewDataFilterOption {
  label: string
  value: string
}

/** 动态接口分页结果之外，默认已选值也必须保留为可显示选项。 */
export function mergeSelectedFilterOptions(
  options: readonly ViewDataFilterOption[],
  selected: string | readonly string[] | null | undefined,
): ViewDataFilterOption[] {
  const merged = new Map(options.map((option) => [option.value, option]))
  const selectedValues = Array.isArray(selected) ? selected : selected == null || selected === '' ? [] : [selected]
  selectedValues.forEach((value) => {
    if (!merged.has(value)) merged.set(value, { label: value, value })
  })
  return [...merged.values()]
}
