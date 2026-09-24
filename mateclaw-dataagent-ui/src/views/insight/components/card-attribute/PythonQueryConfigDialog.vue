<template>
  <el-dialog
    :model-value="modelValue"
    class="insight-dialog--lg"
    title="查询配置"
    width="860px"
    destroy-on-close
    :close-on-click-modal="false"
    aria-label="Python 最终结果查询配置"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="qc-body">
      <div class="qc-section">
        <div class="qc-section-head">
          <button type="button" class="qc-collapsible qc-expand-toggle" :aria-expanded="fieldsExpanded" aria-controls="python-qc-field-table" @click.stop="fieldsExpanded = !fieldsExpanded">
            <span class="qc-chevron" :class="{ 'is-collapsed': !fieldsExpanded }">›</span>
          </button>
          <span class="qc-section-title qc-section-title-grow">展示字段</span>
          <span class="qc-hint">角色可点击切换；拖动行调整列顺序；删除字段不会修改 Python 输出</span>
        </div>
        <div v-show="fieldsExpanded" id="python-qc-field-table" class="qc-field-wrap">
          <div class="qc-field-table" data-testid="python-qc-field-rows" role="table" aria-label="展示字段">
            <div class="qc-thead">
              <span class="qc-th qc-th-field">字段名</span>
              <span class="qc-th qc-th-title">展示名</span>
              <span class="qc-th qc-th-op" />
            </div>
            <div v-for="(row, index) in fieldRows" :key="displayFieldRowKey(row)" class="qc-field-row" data-testid="python-qc-field-row" draggable="true" @dragstart="onDragStart(index)" @dragover.prevent @drop="onDrop(index)">
              <div class="qc-field-cell qc-field-name-cell">
                <el-tag :type="row.role === 'measure' ? 'warning' : 'info'" size="small" class="qc-role-tag" data-testid="python-qc-role-toggle" title="点击切换维度 / 指标" @click.stop="toggleRole(row)">
                  {{ row.role === 'measure' ? '指标' : '维度' }}
                </el-tag>
                <div class="qc-input-field">
                  <el-input v-model="row.field" class="qc-tech-input" :class="{ 'qc-input-invalid': displayFieldNameError(row) }" :aria-invalid="Boolean(displayFieldNameError(row))" size="small" placeholder="技术字段名" data-testid="python-qc-field-tech-input" />
                  <span v-if="displayFieldNameError(row)" class="qc-error" data-testid="python-qc-field-tech-error">{{ displayFieldNameError(row) }}</span>
                </div>
              </div>
              <div class="qc-field-cell qc-field-title-cell">
                <el-input v-model="row.title" class="qc-title-input" size="small" placeholder="展示名" data-testid="python-qc-title-input" />
                <span v-if="duplicateTitles.has(row.title.trim())" class="qc-error">展示名重复</span>
              </div>
              <el-button class="qc-remove" size="small" text type="danger" data-testid="python-qc-field-remove" @click.stop="removeField(index)">×</el-button>
            </div>
          </div>
          <div class="qc-add-field-row">
            <el-button size="small" type="primary" plain data-testid="python-qc-add-new-field" @click="addNewField">添加字段</el-button>
          </div>
        </div>
      </div>

      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">筛选字段</span>
          <el-button size="small" type="primary" plain data-testid="python-qc-add-filter" @click="addFilterField">+ 添加筛选字段</el-button>
        </div>
        <div v-if="filterRows.length" class="qc-binding-table">
          <div class="qc-thead qc-binding-thead">
            <span class="qc-th qc-th-filter">筛选器名称</span>
            <span class="qc-th qc-th-target">绑定对象</span>
            <span class="qc-th qc-th-op" />
          </div>
          <div v-for="(row, index) in filterRows" :key="filterRowKey(row)" class="qc-binding-row" data-testid="python-qc-filter-row">
            <el-select v-model="row.filterComponentId" size="small" placeholder="筛选器名称" filterable data-testid="python-qc-filter-component">
              <el-option v-for="filter in filterOptions" :key="filter.id" :label="filter.title" :value="filter.id" />
            </el-select>
            <div class="qc-input-field">
              <el-input v-model="row.field" :class="{ 'qc-input-invalid': filterFieldNameError(row) }" :aria-invalid="Boolean(filterFieldNameError(row))" size="small" placeholder="字段名（英文/数字/下划线）" data-testid="python-qc-filter-field" />
              <span v-if="filterFieldNameError(row)" class="qc-error" data-testid="python-qc-filter-field-error">{{ filterFieldNameError(row) }}</span>
            </div>
            <el-button size="small" text type="danger" data-testid="python-qc-filter-remove" @click="filterRows.splice(index, 1)">删除</el-button>
          </div>
        </div>
        <div v-else class="qc-empty">暂无筛选字段；查看数据时不会产生筛选条件</div>
      </div>

      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">允许排序</span>
          <el-switch v-model="sortEnabled" data-testid="python-qc-sort-enabled" />
        </div>
        <el-select v-if="sortEnabled" v-model="sortAllowed" multiple class="qc-sort-select" placeholder="选择允许排序的字段" data-testid="python-qc-sort-fields">
          <el-option v-for="row in fieldRows" :key="displayFieldRowKey(row)" :label="`${row.field} · ${row.title}`" :value="row.field" />
        </el-select>
      </div>

      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">分页</span>
          <el-switch v-model="paginationEnabled" data-testid="python-qc-pagination-enabled" />
        </div>
        <div v-if="paginationEnabled" class="qc-pagination-row">
          <span class="qc-hint">默认每页</span>
          <el-input-number v-model="defaultPageSize" :min="1" :max="maxPageSize" size="small" controls-position="right" data-testid="python-qc-default-page-size" />
          <span class="qc-hint">最大每页</span>
          <el-input-number v-model="maxPageSize" :min="1" :max="500" size="small" controls-position="right" data-testid="python-qc-max-page-size" />
          <el-checkbox v-model="returnTotalCount" data-testid="python-qc-return-total">返回总数</el-checkbox>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" data-testid="python-qc-save" @click="save">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FinalResultFilterField, FinalResultQueryConfig, QueryDisplayField } from '@/types'

const props = defineProps<{
  modelValue: boolean
  config: FinalResultQueryConfig
  filterOptions?: Array<{ id: string; title: string; type?: string; field?: string; selectionMode?: 'single' | 'multiple' }>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', config: FinalResultQueryConfig): void
}>()

interface EditableField extends QueryDisplayField {
  originalField: string
}

interface EditableFilterField extends FinalResultFilterField {
  filterComponentId?: string
}

const fieldRows = ref<EditableField[]>([])
const filterRows = ref<EditableFilterField[]>([])
const displayFieldRowKeys = new WeakMap<object, number>()
const filterRowKeys = new WeakMap<object, number>()
let nextRowKey = 0
const sortEnabled = ref(false)
const sortAllowed = ref<string[]>([])
const paginationEnabled = ref(false)
const defaultPageSize = ref(100)
const maxPageSize = ref(500)
const returnTotalCount = ref(false)
const dragIndex = ref<number | null>(null)
const fieldsExpanded = ref(true)
const FIELD_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/

const duplicateTitles = computed(() => {
  const seen = new Set<string>()
  const duplicates = new Set<string>()
  fieldRows.value.forEach((row) => {
    const title = row.title.trim()
    if (!title) return
    if (seen.has(title)) duplicates.add(title)
    seen.add(title)
  })
  return duplicates
})

const filterOptions = computed(() => props.filterOptions ?? [])

function getFieldNameError(value: string, label: string): string {
  const fieldName = value.trim()
  if (!fieldName || FIELD_NAME_PATTERN.test(fieldName)) return ''
  return `${label}只能由英文、数字、下划线组成，且不能以数字开头`
}

function displayFieldNameError(row: EditableField): string {
  return getFieldNameError(row.field, '技术字段名')
}

function filterFieldNameError(row: EditableFilterField): string {
  return getFieldNameError(row.field, '绑定字段名')
}

function displayFieldRowKey(row: EditableField): number {
  let key = displayFieldRowKeys.get(row)
  if (key === undefined) {
    key = nextRowKey++
    displayFieldRowKeys.set(row, key)
  }
  return key
}

function filterRowKey(row: EditableFilterField): number {
  let key = filterRowKeys.get(row)
  if (key === undefined) {
    key = nextRowKey++
    filterRowKeys.set(row, key)
  }
  return key
}

function cloneConfig(config: FinalResultQueryConfig): void {
  fieldRows.value = config.displayFields.map((field) => ({ ...field, originalField: field.field }))
  filterRows.value = config.filterFields.map((field) => ({ ...field, filterComponentId: field.filterComponentId ?? '' }))
  sortEnabled.value = config.sortPolicy.enabled
  sortAllowed.value = [...config.sortPolicy.allowedFields]
  paginationEnabled.value = config.paginationPolicy.enabled
  defaultPageSize.value = config.paginationPolicy.defaultPageSize
  maxPageSize.value = config.paginationPolicy.maxPageSize
  returnTotalCount.value = config.paginationPolicy.returnTotalCount
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    fieldsExpanded.value = true
    cloneConfig(props.config)
  }
}, { immediate: true })

function addNewField(): void {
  fieldRows.value.push({ field: '', title: '', role: 'dimension', dataType: 'string', originalField: '' })
}

function addFilterField(): void {
  filterRows.value.push({ field: '', title: '', dataType: 'string', parameterName: '', operators: ['eq', 'neq', 'in', 'not_in', 'contains'], filterComponentId: filterOptions.value[0]?.id ?? '' })
}

function toggleRole(row: QueryDisplayField): void {
  row.role = row.role === 'measure' ? 'dimension' : 'measure'
}

function removeField(index: number): void {
  const field = fieldRows.value[index]?.field
  fieldRows.value.splice(index, 1)
  if (field) filterRows.value = filterRows.value.filter((row) => row.field !== field)
  sortAllowed.value = sortAllowed.value.filter((item) => item !== field)
}

function onDragStart(index: number): void { dragIndex.value = index }
function onDrop(index: number): void {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === index) return
  const [moved] = fieldRows.value.splice(from, 1)
  fieldRows.value.splice(index, 0, moved)
}

function save(): void {
  const fieldNames = new Set<string>()
  for (const field of fieldRows.value) {
    const fieldName = field.field.trim()
    if (!FIELD_NAME_PATTERN.test(fieldName)) {
      ElMessage.warning('技术字段名只能由英文、数字、下划线组成，且不能以数字开头')
      return
    }
    if (fieldNames.has(fieldName)) {
      ElMessage.warning('技术字段名不能重复')
      return
    }
    fieldNames.add(fieldName)
  }
  const filterNames = new Set<string>()
  for (const field of filterRows.value) {
    const fieldName = field.field.trim()
    if (!FIELD_NAME_PATTERN.test(fieldName)) {
      ElMessage.warning('绑定字段名只能由英文、数字、下划线组成，且不能以数字开头')
      return
    }
    if (filterNames.has(fieldName)) {
      ElMessage.warning('绑定字段名不能重复')
      return
    }
    filterNames.add(fieldName)
  }
  if (duplicateTitles.value.size) {
    ElMessage.warning('展示名必须唯一')
    return
  }
  if (sortEnabled.value && !sortAllowed.value.length) {
    ElMessage.warning('开启排序后至少选择一个允许排序的字段')
    return
  }
  const renamedFields = new Map(fieldRows.value.map((field) => [field.originalField, field.field.trim()]))
  const titleByField = new Map(fieldRows.value.map((field) => [field.field.trim(), field.title.trim() || field.field.trim()]))
  const displayFields = fieldRows.value.map((field) => ({ field: field.field.trim(), title: field.title.trim() || field.field.trim(), role: field.role, dataType: field.dataType }))
  const filterFields = filterRows.value.map((field) => {
    const nextField = renamedFields.get(field.field) ?? field.field
    return { ...field, field: nextField, title: titleByField.get(nextField) ?? field.title, parameterName: nextField, ...(field.filterComponentId ? { filterComponentId: field.filterComponentId } : {}) }
  })
  const allowedFields = sortAllowed.value.map((field) => renamedFields.get(field) ?? field)
  emit('save', {
    schemaFingerprint: props.config.schemaFingerprint,
    confirmed: false,
    displayFields,
    filterFields,
    sortPolicy: { ...props.config.sortPolicy, enabled: sortEnabled.value, allowedFields, defaultSort: allowedFields[0] ? { field: allowedFields[0], direction: 'asc' } : null },
    paginationPolicy: { ...props.config.paginationPolicy, enabled: paginationEnabled.value, defaultPageSize: defaultPageSize.value, maxPageSize: Math.min(maxPageSize.value, 500), returnTotalCount: returnTotalCount.value },
  })
  emit('update:modelValue', false)
}
</script>

<style scoped>
.qc-body { display: flex; flex-direction: column; gap: 18px; max-height: 62vh; overflow: auto; padding-right: 4px; }
.qc-section { border: 1px solid var(--db-border); border-radius: var(--radius-md); padding: 12px; background: #fff; }
.qc-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.qc-section-title { font-weight: 600; font-size: 13px; color: var(--db-text); }
.qc-hint { font-size: 12px; color: var(--db-text-muted); }
.qc-expand-toggle { width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; padding: 0; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--db-text-muted); cursor: pointer; }
.qc-expand-toggle:hover { background: var(--db-muted); color: var(--db-text); }
.qc-chevron { display: inline-block; transition: transform 0.15s ease; font-size: 20px; line-height: 1; }
.qc-chevron.is-collapsed { transform: rotate(-90deg); }
.qc-section-title-grow { margin-right: auto; margin-left: 4px; }
.qc-field-wrap { margin-top: 2px; background: #fff; }
.qc-field-table { display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--db-border); border-radius: var(--radius-sm); background: #fff; }
.qc-thead, .qc-field-row { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr) 28px; align-items: center; column-gap: 12px; }
.qc-thead { min-height: 34px; padding: 0 10px; border-bottom: 1px solid var(--db-border); font-size: 12px; color: var(--db-text-muted); font-weight: 600; background: #fff; }
.qc-th-op { width: 28px; }
.qc-field-row { min-height: 46px; padding: 6px 10px; border-bottom: 1px solid var(--db-border); background: #fff; cursor: grab; }
.qc-field-row:last-of-type { border-bottom: 0; }
.qc-field-cell { display: flex; align-items: center; min-width: 0; }
.qc-field-name-cell { gap: 8px; }
.qc-field-title-cell { gap: 8px; }
.qc-input-field { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; }
.qc-input-invalid :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px var(--el-color-danger) inset; }
.qc-role-tag { flex-shrink: 0; cursor: pointer; user-select: none; }
.qc-tech-field { font-family: var(--font-mono, monospace); font-size: 12px; color: var(--db-text); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qc-tech-input { flex: 1; min-width: 0; }
.qc-title-input { flex: 1; min-width: 0; }
.qc-error { color: var(--el-color-danger); font-size: 12px; flex-shrink: 0; }
.qc-remove { flex-shrink: 0; }
.qc-add-field-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.qc-binding-table { display: flex; flex-direction: column; }
.qc-binding-thead { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 28px; padding: 0 2px 6px; }
.qc-binding-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 28px; align-items: center; gap: 8px; margin-bottom: 8px; }
.qc-sort-select { width: 100%; }
.qc-pagination-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.qc-empty { padding: 12px 0; color: var(--db-text-muted); font-size: 12px; text-align: center; }
</style>
