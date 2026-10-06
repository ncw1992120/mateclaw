import type { DashboardDatasetInput, DashboardPage, InsightCombinationChild, InsightComponent, InsightDashboardSchema } from '@/types'
import { collectDashboardComponents } from '@/composables/useDashboardFilterContext'

export interface FilterBindingDependent {
  componentId: string
  componentTitle: string
  pageName: string
}

export interface FilterComponentReference {
  id: string
  title: string
  type: 'filter' | 'timeFilter'
}

/** Collect filter widgets that will be removed together with a container or tab. */
export function collectFilterComponents(components: Array<InsightComponent | InsightCombinationChild>): FilterComponentReference[] {
  const filters: FilterComponentReference[] = []
  const visited = new Set<string>()
  const visit = (component: InsightComponent | InsightCombinationChild): void => {
    if (visited.has(component.id)) return
    visited.add(component.id)
    if (component.type === 'filter' || component.type === 'timeFilter') {
      filters.push({ id: component.id, title: component.title || component.id, type: component.type })
    }
    for (const child of component.children ?? []) visit(child)
    for (const tab of component.containerConfig?.tabs ?? []) {
      for (const child of tab.children ?? []) visit(child)
    }
  }
  components.forEach(visit)
  return filters
}

function componentReferencesFilter(component: InsightComponent, filterId: string): boolean {
  if (component.boundFilterIds?.includes(filterId)) return true

  const pipeline = component.config?.datasetPipeline as Record<string, any> | undefined
  if (!pipeline) return false
  if (pipeline.boundFilterComponentIds?.includes(filterId)) return true
  if (pipeline.scriptFilterBindings?.some((binding: any) => binding.filterComponentId === filterId)) return true
  if (pipeline.finalResultQueryConfig?.filterFields?.some((field: any) => field.filterComponentId === filterId)) return true
  return pipeline.datasetInputs?.some((input: DashboardDatasetInput) => (
    input.queryConfig?.parameterBindings?.some(binding => binding.filterComponentId === filterId)
    || input.lastQueryState?.filters?.some(row => row.filterComponentId === filterId)
  )) ?? false
}

function pageComponents(page: DashboardPage): InsightComponent[] {
  return collectDashboardComponents(page.components)
}

export function findFilterBindingDependents(schema: InsightDashboardSchema, filterId: string): FilterBindingDependent[] {
  const dependents: FilterBindingDependent[] = []
  for (const page of schema.pages) {
    for (const component of pageComponents(page)) {
      if (component.id !== filterId && componentReferencesFilter(component, filterId)) {
        dependents.push({
          componentId: component.id,
          componentTitle: component.title || component.id,
          pageName: page.name,
        })
      }
    }
  }
  const legacyScriptInputs = (schema.datasetInputs ?? []).filter(input => (
    input.queryConfig?.parameterBindings?.some(binding => binding.filterComponentId === filterId)
    || input.lastQueryState?.filters?.some(row => row.filterComponentId === filterId)
  ))
  const legacyScriptBindings = (schema.scriptFilterBindings ?? []).filter(binding => binding.filterComponentId === filterId)
  for (const input of legacyScriptInputs) {
    dependents.push({
      componentId: `dashboard-input:${input.inputName}`,
      componentTitle: `仪表盘脚本数据集「${input.displayName || input.inputName}」`,
      pageName: '仪表盘级脚本',
    })
  }
  for (const binding of legacyScriptBindings) {
    for (const inputName of binding.inputNames) {
      if (legacyScriptInputs.some(input => input.inputName === inputName)) continue
      dependents.push({
        componentId: `dashboard-script-binding:${inputName}`,
        componentTitle: `仪表盘脚本数据集「${inputName}」`,
        pageName: '仪表盘级脚本',
      })
    }
  }
  return dependents
}

function removeFilterFromDatasetInput(input: DashboardDatasetInput, filterId: string): void {
  if (Array.isArray(input.queryConfig?.parameterBindings)) {
    input.queryConfig.parameterBindings = input.queryConfig.parameterBindings.filter(binding => binding.filterComponentId !== filterId)
  }
  if (Array.isArray(input.lastQueryState?.filters)) {
    input.lastQueryState.filters = input.lastQueryState.filters.filter(row => row.filterComponentId !== filterId)
  }
}

function removeFilterFromComponent(component: InsightComponent, filterId: string): void {
  if (component.boundFilterIds) component.boundFilterIds = component.boundFilterIds.filter(id => id !== filterId)

  const pipeline = component.config?.datasetPipeline as Record<string, any> | undefined
  if (!pipeline) return
  if (Array.isArray(pipeline.boundFilterComponentIds)) {
    pipeline.boundFilterComponentIds = pipeline.boundFilterComponentIds.filter((id: string) => id !== filterId)
  }
  if (Array.isArray(pipeline.scriptFilterBindings)) {
    pipeline.scriptFilterBindings = pipeline.scriptFilterBindings.filter((binding: any) => binding.filterComponentId !== filterId)
  }
  if (pipeline.finalResultQueryConfig && Array.isArray(pipeline.finalResultQueryConfig.filterFields)) {
    pipeline.finalResultQueryConfig.filterFields = pipeline.finalResultQueryConfig.filterFields
      .filter((field: any) => field.filterComponentId !== filterId)
  }
  if (Array.isArray(pipeline.datasetInputs)) {
    for (const input of pipeline.datasetInputs as DashboardDatasetInput[]) removeFilterFromDatasetInput(input, filterId)
  }
}

/** Remove the deleted filter ID from every page/component binding while retaining the bound components. */
export function removeFilterBindings(schema: InsightDashboardSchema, filterId: string): void {
  for (const page of schema.pages) {
    for (const component of pageComponents(page)) removeFilterFromComponent(component, filterId)
  }
  if (schema.scriptFilterBindings) {
    schema.scriptFilterBindings = schema.scriptFilterBindings.filter(binding => binding.filterComponentId !== filterId)
  }
  for (const input of schema.datasetInputs ?? []) removeFilterFromDatasetInput(input, filterId)
}

/** Remove persisted references whose filter component was deleted in an older dashboard version. */
export function removeDanglingFilterBindings(schema: InsightDashboardSchema): string[] {
  const existingFilterIds = new Set<string>()
  const referencedFilterIds = new Set<string>()
  const addReferences = (ids: unknown): void => {
    if (Array.isArray(ids)) ids.forEach(id => typeof id === 'string' && referencedFilterIds.add(id))
  }
  const addBindings = (bindings: unknown): void => {
    if (Array.isArray(bindings)) {
      bindings.forEach(binding => {
        if (typeof binding?.filterComponentId === 'string') referencedFilterIds.add(binding.filterComponentId)
      })
    }
  }
  const inspectInput = (input: DashboardDatasetInput): void => {
    addBindings(input.queryConfig?.parameterBindings)
    addBindings(input.lastQueryState?.filters)
  }

  for (const page of schema.pages ?? []) {
    for (const component of pageComponents(page)) {
      if (component.type === 'filter' || component.type === 'timeFilter') existingFilterIds.add(component.id)
      addReferences(component.boundFilterIds)

      const pipeline = component.config?.datasetPipeline as Record<string, any> | undefined
      if (!pipeline) continue
      addReferences(pipeline.boundFilterComponentIds)
      addBindings(pipeline.scriptFilterBindings)
      addBindings(pipeline.finalResultQueryConfig?.filterFields)
      if (Array.isArray(pipeline.datasetInputs)) pipeline.datasetInputs.forEach(inspectInput)
    }
  }
  addBindings(schema.scriptFilterBindings)
  ;(schema.datasetInputs ?? []).forEach(inspectInput)

  const danglingIds = [...referencedFilterIds].filter(id => !existingFilterIds.has(id))
  danglingIds.forEach(id => removeFilterBindings(schema, id))
  return danglingIds
}
