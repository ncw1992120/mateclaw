import { readonly, ref, toRaw } from 'vue'
import type { ComponentDatasetPipeline, InsightComponent } from '@/types'
import { readComponentDatasetPipeline, writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'

export interface ComponentPropertyDraft {
  componentId: string
  component: InsightComponent
  datasetPipeline: ComponentDatasetPipeline
}

export interface ComponentPropertyDraftController {
  draft: Readonly<{ value: ComponentPropertyDraft | null }>
  load: (component: InsightComponent | null) => void
  patch: (changes: Partial<InsightComponent>) => void
  patchPipeline: (changes: Partial<ComponentDatasetPipeline>) => void
  validate: () => boolean
  commit: () => InsightComponent | null
  reset: () => void
}

const emptyPipeline = (): ComponentDatasetPipeline => ({ datasetInputs: [] })

function clone<T>(value: T): T {
  const raw = toRaw(value) as T
  try {
    return structuredClone(raw)
  } catch {
    return JSON.parse(JSON.stringify(raw)) as T
  }
}

function createDraft(component: InsightComponent): ComponentPropertyDraft {
  return {
    componentId: component.id,
    component: clone(component),
    datasetPipeline: clone(readComponentDatasetPipeline(component) ?? emptyPipeline()),
  }
}

export function useComponentPropertyDraft(): ComponentPropertyDraftController {
  const draft = ref<ComponentPropertyDraft | null>(null)
  const original = ref<InsightComponent | null>(null)

  function load(component: InsightComponent | null): void {
    original.value = component ? clone(component) : null
    draft.value = component ? createDraft(component) : null
  }

  function patch(changes: Partial<InsightComponent>): void {
    if (!draft.value) return
    draft.value.component = { ...draft.value.component, ...clone(changes) }
  }

  function patchPipeline(changes: Partial<ComponentDatasetPipeline>): void {
    if (!draft.value) return
    draft.value.datasetPipeline = {
      ...draft.value.datasetPipeline,
      ...clone(changes),
    }
  }

  function validate(): boolean {
    return Boolean(draft.value?.componentId && draft.value.component.title.trim())
  }

  function commit(): InsightComponent | null {
    if (!draft.value || !validate()) return null
    return writeComponentDatasetPipeline(clone(draft.value.component), clone(draft.value.datasetPipeline))
  }

  function reset(): void {
    load(original.value)
  }

  return {
    draft: readonly(draft),
    load,
    patch,
    patchPipeline,
    validate,
    commit,
    reset,
  }
}
