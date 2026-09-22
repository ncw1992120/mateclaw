<template>
  <el-dialog v-model="ui.filterBinding.visible" class="insight-dialog--lg" title="绑定筛选器" width="780px" destroy-on-close :close-on-click-modal="false" aria-label="绑定筛选器">
    <div class="fb-toolbar">
      <span class="fb-count">已绑定 {{ drafts.length }} 条关系</span>
      <el-button size="small" type="primary" plain @click="addBinding">+ 绑定筛选器</el-button>
    </div>

    <div class="fb-list">
      <div v-for="(draft, index) in drafts" :key="draft.id" class="fb-row" data-testid="filter-binding-row">
        <div class="fb-row-index">{{ index + 1 }}</div>
        <el-select v-model="draft.filterName" class="fb-filter" placeholder="仪表盘筛选器" filterable @change="onFilterChange(draft)">
          <el-option v-for="filter in FILTERS" :key="filter" :label="filter" :value="filter" />
        </el-select>
        <el-select v-model="draft.datasetId" class="fb-dataset" placeholder="作用数据集" filterable @change="onDatasetChange(draft)">
          <el-option v-for="dataset in state.datasets" :key="dataset.id" :label="dataset.alias" :value="dataset.id" />
        </el-select>
        <el-select v-model="draft.field" class="fb-field" placeholder="字段映射（维度字段）" filterable :disabled="!draft.datasetId" @change="onFieldChange(draft)">
          <el-option v-for="field in dimensionFields(datasetFor(draft))" :key="field.name" :label="fieldOptionLabel(field)" :value="field.name" />
        </el-select>
        <span v-if="draft.field && draft.matched" class="match-tag ok">✓ 自动匹配</span>
        <span v-else-if="draft.field" class="match-tag warn">⚠ 手动映射</span>
        <span v-else class="match-tag empty">待选择</span>
        <el-button class="fb-remove" size="small" text type="danger" :disabled="drafts.length === 1" @click="removeBinding(index)">删除</el-button>
      </div>
      <div v-if="!drafts.length" class="fb-empty">暂无绑定关系，点击上方“+ 绑定筛选器”开始配置。</div>
    </div>

    <template #footer>
      <el-button @click="ui.filterBinding.visible = false">取消</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from './useInsight'
import type { DatasetConfig, FilterBinding } from './useInsight'
import { resolveFieldLabel, type DatasetFieldMeta } from '@/utils/field-mapping'

const { state, saveFilterBindings } = useInsight()
const ui = state.ui
const FILTERS = computed<string[]>(() => state.filterCatalog.map((filter) => filter.title))

interface FBDraft {
  id: string
  filterName: string
  datasetId: string
  field: string
  matched: boolean
}

const drafts = ref<FBDraft[]>([])
let draftSequence = 0

function newDraft(partial: Partial<FBDraft> = {}): FBDraft {
  draftSequence += 1
  return { id: `binding-${draftSequence}`, filterName: '', datasetId: '', field: '', matched: false, ...partial }
}

function datasetFor(draft: FBDraft): DatasetConfig | undefined {
  return state.datasets.find((dataset) => dataset.id === draft.datasetId)
}

/** 绑定筛选器只允许映射维度字段；缺失 role 的历史字段按维度兼容处理。 */
function dimensionFields(dataset: DatasetConfig | undefined): DatasetFieldMeta[] {
  return (dataset?.fields ?? []).filter((field) => {
    const role = (field.role ?? '').trim().toLowerCase()
    return !role || role === 'dimension' || role === 'dim'
  })
}

function fieldOptionLabel(field: DatasetFieldMeta): string {
  const displayName = resolveFieldLabel([field], field.name)
  return displayName === field.name ? field.name : `${field.name} · ${displayName}`
}

function toFieldName(dataset: DatasetConfig, value: string): string {
  const key = (value ?? '').trim()
  if (!key) return ''
  if ((dataset.fields ?? []).some((field) => field.name === key)) return key
  return (dataset.fields ?? []).find((field) => (field.displayName ?? '').trim() === key)?.name ?? key
}

function inferField(dataset: DatasetConfig | undefined, filterName: string): string {
  const fields = dimensionFields(dataset)
  const exact = fields.find((field) => field.name === filterName || field.displayName === filterName)
  if (exact) return exact.name
  const candidate = fields.find((field) => {
    const refs = [field.name, field.displayName].filter(Boolean) as string[]
    return refs.some((value) => value.includes(filterName) || filterName.includes(value))
  })
  return candidate?.name ?? ''
}

function updateAutomaticField(draft: FBDraft): void {
  const inferred = inferField(datasetFor(draft), draft.filterName)
  draft.field = inferred
  draft.matched = !!inferred
}

function onFilterChange(draft: FBDraft): void { updateAutomaticField(draft) }
function onDatasetChange(draft: FBDraft): void { updateAutomaticField(draft) }

function onFieldChange(draft: FBDraft): void {
  const dataset = datasetFor(draft)
  draft.field = dataset ? toFieldName(dataset, draft.field) : ''
  draft.matched = !!draft.field && draft.field === inferField(dataset, draft.filterName)
}

function addBinding(): void { drafts.value.push(newDraft()) }
function removeBinding(index: number): void { drafts.value.splice(index, 1) }

function restoreDrafts(bindings: FilterBinding[]): void {
  const rows: FBDraft[] = []
  bindings.forEach((binding) => {
    Object.keys(binding.scope ?? {}).filter((id) => binding.scope[id]).forEach((datasetId) => {
      const dataset = state.datasets.find((item) => item.id === datasetId)
      const mapped = binding.fieldMap?.find((item) => item.datasetId === datasetId)
      rows.push(newDraft({
        filterName: binding.filterName,
        datasetId,
        field: dataset ? toFieldName(dataset, mapped?.field ?? '') : mapped?.field ?? '',
        matched: Boolean(mapped?.matched),
      }))
    })
  })
  drafts.value = rows.length ? rows : [newDraft()]
}

watch(() => ui.filterBinding.visible, (visible) => {
  if (visible) restoreDrafts(state.filterBindings ?? [])
})

function save(): void {
  const validRows = drafts.value.filter((draft) => draft.filterName || draft.datasetId || draft.field)
  if (!validRows.length) {
    saveFilterBindings([])
    return
  }
  if (validRows.some((draft) => !draft.filterName || !draft.datasetId || !draft.field)) {
    ElMessage.warning('请完整配置每条绑定关系：筛选器、数据集和维度字段不能为空')
    return
  }
  const duplicate = validRows.find((draft, index) => validRows.findIndex((item) => item.filterName === draft.filterName && item.datasetId === draft.datasetId) !== index)
  if (duplicate) {
    ElMessage.warning('同一个筛选器不能重复绑定同一个数据集')
    return
  }

  const grouped = new Map<string, FilterBinding>()
  validRows.forEach((draft) => {
    const binding = grouped.get(draft.filterName) ?? { filterName: draft.filterName, scope: {}, fieldMap: [] }
    binding.scope[draft.datasetId] = true
    binding.fieldMap.push({ datasetId: draft.datasetId, field: draft.field, matched: draft.matched })
    grouped.set(draft.filterName, binding)
  })
  saveFilterBindings(Array.from(grouped.values()))
}
</script>

<style scoped>
.fb-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.fb-count { font-size: 13px; color: var(--el-text-color-secondary); }
.fb-list { max-height: 54vh; overflow: auto; padding: 2px 4px 2px 0; }
.fb-row { display: flex; align-items: center; gap: 8px; min-height: 56px; padding: 10px 12px; margin-bottom: 8px; border: 1px solid var(--db-border); border-radius: var(--radius-md); background: var(--db-muted); }
.fb-row-index { width: 20px; color: var(--el-text-color-secondary); font-size: 12px; text-align: center; flex-shrink: 0; }
.fb-filter { width: 190px; }
.fb-dataset { width: 150px; }
.fb-field { min-width: 190px; flex: 1; }
.match-tag { width: 76px; font-size: 12px; white-space: nowrap; flex-shrink: 0; }
.match-tag.ok { color: var(--el-color-success); }
.match-tag.warn { color: var(--el-color-warning); }
.match-tag.empty { color: var(--el-text-color-placeholder); }
.fb-remove { flex-shrink: 0; }
.fb-empty { padding: 28px 0; color: var(--el-text-color-secondary); text-align: center; }
@media (max-width: 760px) {
  .fb-row { align-items: stretch; flex-wrap: wrap; }
  .fb-filter, .fb-dataset, .fb-field { width: calc(50% - 14px); min-width: 0; }
  .match-tag { width: auto; }
}
</style>
