<template>
  <el-dialog
    :model-value="modelValue"
    class="insight-dialog--lg"
    title="查询配置"
    width="860px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    aria-label="Python 最终结果查询配置"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="qc-body">
      <section class="qc-section">
        <div class="qc-section-head">
          <div>
            <div class="qc-section-title">筛选器绑定</div>
            <div class="qc-hint">将页面筛选器绑定到 Python 输出字段；未绑定的筛选器不会参与查询。</div>
          </div>
          <el-button size="small" type="primary" plain :disabled="availableFilterOptions.length === 0" data-testid="python-qc-add-filter" @click="addFilterField">+ 添加筛选器</el-button>
        </div>
        <div v-if="filterRows.length" class="qc-binding-table">
          <div class="qc-thead">
            <span>筛选器名称</span><span>结果字段</span><span>参数名</span><span />
          </div>
          <div v-for="(row, index) in filterRows" :key="filterRowKey(row)" class="qc-binding-row" data-testid="python-qc-filter-row">
            <el-select v-model="row.filterComponentId" size="small" placeholder="筛选器名称" filterable data-testid="python-qc-filter-component">
              <el-option v-for="filter in optionsForRow(row)" :key="filter.id" :label="filter.title" :value="filter.id" />
            </el-select>
            <el-select v-model="row.field" size="small" placeholder="选择 Python 输出字段" filterable data-testid="python-qc-filter-field">
              <el-option v-for="field in availableFields" :key="field.field" :label="`${field.title} · ${field.field}`" :value="field.field" />
            </el-select>
            <el-input v-model="row.parameterName" size="small" placeholder="参数名" data-testid="python-qc-filter-parameter" />
            <el-button size="small" text type="danger" data-testid="python-qc-filter-remove" @click="filterRows.splice(index, 1)">删除</el-button>
          </div>
        </div>
        <div v-else class="qc-empty">暂无筛选器绑定；查看数据时不会产生筛选条件。</div>
      </section>

      <section class="qc-section">
        <div class="qc-section-head">
          <div>
            <div class="qc-section-title">允许排序</div>
            <div class="qc-hint">可选字段来自脚本输出结果。</div>
          </div>
          <el-switch v-model="sortEnabled" data-testid="python-qc-sort-enabled" />
        </div>
        <el-select v-if="sortEnabled" v-model="sortAllowed" multiple class="qc-sort-select" placeholder="选择允许排序的字段" data-testid="python-qc-sort-fields">
          <el-option v-for="field in availableFields" :key="field.field" :label="`${field.field} · ${field.title}`" :value="field.field" />
        </el-select>
      </section>

      <section class="qc-section">
        <div class="qc-section-head">
          <div>
            <div class="qc-section-title">分页</div>
            <div class="qc-hint">设置查看数据时的默认分页行为。</div>
          </div>
          <el-switch v-model="paginationEnabled" data-testid="python-qc-pagination-enabled" />
        </div>
        <div v-if="paginationEnabled" class="qc-pagination-row">
          <span class="qc-hint">默认每页</span>
          <el-input-number v-model="defaultPageSize" :min="1" :max="maxPageSize" size="small" controls-position="right" data-testid="python-qc-default-page-size" />
          <span class="qc-hint">最大每页</span>
          <el-input-number v-model="maxPageSize" :min="1" :max="500" size="small" controls-position="right" data-testid="python-qc-max-page-size" />
          <el-checkbox v-model="returnTotalCount" data-testid="python-qc-return-total">返回总数</el-checkbox>
        </div>
      </section>
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

interface EditableFilterField extends FinalResultFilterField { filterComponentId?: string }
const filterRows = ref<EditableFilterField[]>([])
const rowKeys = new WeakMap<object, number>()
let nextRowKey = 0
const sortEnabled = ref(false)
const sortAllowed = ref<string[]>([])
const paginationEnabled = ref(false)
const defaultPageSize = ref(100)
const maxPageSize = ref(500)
const returnTotalCount = ref(false)
const availableFields = computed<QueryDisplayField[]>(() => props.config.displayFields ?? [])
const filterOptions = computed(() => props.filterOptions ?? [])
const availableFilterOptions = computed(() => {
  const boundIds = new Set(filterRows.value.map((row) => row.filterComponentId).filter(Boolean))
  return filterOptions.value.filter((filter) => !boundIds.has(filter.id))
})

function filterRowKey(row: EditableFilterField): number {
  let key = rowKeys.get(row)
  if (key === undefined) { key = nextRowKey++; rowKeys.set(row, key) }
  return key
}

function optionsForRow(row: EditableFilterField) {
  return filterOptions.value.filter((filter) => filter.id === row.filterComponentId
    || !filterRows.value.some((other) => other !== row && other.filterComponentId === filter.id))
}

function cloneConfig(config: FinalResultQueryConfig): void {
  // 丢弃旧版本按结果 schema 自动生成的未绑定字段，避免它们继续变成运行时筛选项。
  filterRows.value = config.filterFields
    .filter((field) => Boolean(field.filterComponentId))
    .map((field) => ({ ...field, filterComponentId: field.filterComponentId }))
  sortEnabled.value = config.sortPolicy.enabled
  sortAllowed.value = [...config.sortPolicy.allowedFields]
  paginationEnabled.value = config.paginationPolicy.enabled
  defaultPageSize.value = config.paginationPolicy.defaultPageSize
  maxPageSize.value = config.paginationPolicy.maxPageSize
  returnTotalCount.value = config.paginationPolicy.returnTotalCount
}

watch(() => props.modelValue, (visible) => { if (visible) cloneConfig(props.config) }, { immediate: true })

function addFilterField(): void {
  const firstAvailable = availableFilterOptions.value[0]
  if (!firstAvailable) return
  filterRows.value.push({
    field: '', title: firstAvailable.title, dataType: 'string', parameterName: '',
    operators: ['eq', 'neq', 'in', 'not_in', 'contains'],
    filterComponentId: firstAvailable.id,
  })
}

function save(): void {
  const fields = new Set<string>()
  const filterIds = new Set<string>()
  for (const row of filterRows.value) {
    const field = row.field.trim()
    if (!row.filterComponentId || !filterOptions.value.some((filter) => filter.id === row.filterComponentId)) { ElMessage.warning('请选择有效的页面筛选器'); return }
    if (!availableFields.value.some((item) => item.field === field)) { ElMessage.warning('请选择有效的 Python 输出字段'); return }
    if (filterIds.has(row.filterComponentId)) { ElMessage.warning('同一个页面筛选器不能重复绑定'); return }
    if (fields.has(field)) { ElMessage.warning('同一个 Python 输出字段不能重复绑定'); return }
    filterIds.add(row.filterComponentId)
    fields.add(field)
  }
  if (sortEnabled.value && !sortAllowed.value.length) { ElMessage.warning('开启排序后至少选择一个允许排序的字段'); return }

  const titleByField = new Map(availableFields.value.map((field) => [field.field, field.title]))
  const filterFields = filterRows.value.map((row) => ({
    ...row,
    field: row.field.trim(),
    title: titleByField.get(row.field.trim()) ?? row.title ?? row.field.trim(),
    parameterName: row.parameterName.trim() || row.field.trim(),
  }))
  const allowedFields = sortAllowed.value.filter((field) => titleByField.has(field))
  emit('save', {
    ...props.config,
    confirmed: false,
    displayFields: [...(props.config.displayFields ?? [])],
    filterFields,
    sortPolicy: { ...props.config.sortPolicy, enabled: sortEnabled.value, allowedFields, defaultSort: allowedFields[0] ? { field: allowedFields[0], direction: 'asc' } : null },
    paginationPolicy: { ...props.config.paginationPolicy, enabled: paginationEnabled.value, defaultPageSize: defaultPageSize.value, maxPageSize: Math.min(maxPageSize.value, 500), returnTotalCount: returnTotalCount.value },
  })
  emit('update:modelValue', false)
}
</script>

<style scoped>
.qc-body { display: flex; flex-direction: column; gap: 16px; max-height: 62vh; overflow: auto; padding-right: 4px; }
.qc-section { border: 1px solid var(--db-border); border-radius: var(--radius-md); padding: 14px; background: var(--db-card); }
.qc-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.qc-section-title { color: var(--db-text); font-size: 13px; font-weight: 600; }
.qc-hint { color: var(--db-text-muted); font-size: 12px; line-height: 1.5; }
.qc-binding-table { display: flex; flex-direction: column; gap: 8px; }
.qc-thead, .qc-binding-row { display: grid; grid-template-columns: minmax(120px, .9fr) minmax(140px, 1fr) minmax(130px, 1fr) 46px; align-items: start; gap: 10px; }
.qc-thead { padding: 0 8px 8px; border-bottom: 1px solid var(--db-border); color: var(--db-text-muted); font-size: 12px; }
.qc-binding-row { align-items: center; }
.qc-empty { padding: 18px 8px; color: var(--db-text-muted); text-align: center; font-size: 12px; }
.qc-sort-select { width: 100%; }
.qc-pagination-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
@media (max-width: 640px) {
  .qc-thead { display: none; }
  .qc-binding-row { grid-template-columns: 1fr 1fr; }
}
</style>
