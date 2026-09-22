<template>
  <el-dialog
    :model-value="modelValue"
    class="insight-dialog--lg"
    title="查询配置"
    width="860px"
    destroy-on-close
    :close-on-click-modal="false"
    aria-label="查询配置"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="qc-body">
      <!-- ① 展示字段：字段属性固定首列，技术字段只读，展示名可编辑（唯一），支持拖动排序/删除 -->
      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">展示字段</span>
          <span class="qc-hint">字段属性固定；拖动行调整列顺序；删除同步解除筛选绑定与排序白名单</span>
        </div>
        <div class="qc-field-rows" data-testid="qc-field-rows">
          <div
            v-for="(row, index) in fieldRows"
            :key="row.field"
            class="qc-field-row"
            data-testid="qc-field-row"
            draggable="true"
            @dragstart="onDragStart(index)"
            @dragover.prevent
            @drop="onDrop(index)"
          >
            <el-tag :type="row.role === 'measure' ? 'warning' : 'info'" size="small" class="qc-role-tag">
              {{ row.role === 'measure' ? '指标' : '维度' }}
            </el-tag>
            <span class="qc-tech-field" :title="row.field">{{ row.field }}</span>
            <el-input
              v-model="row.title"
              class="qc-title-input"
              size="small"
              placeholder="展示名"
              data-testid="qc-title-input"
              @input="touchTitle(row)"
            />
            <span v-if="duplicateTitles.has(row.title.trim())" class="qc-error">展示名重复</span>
            <el-button class="qc-remove" size="small" text type="danger" data-testid="qc-field-remove" @click="removeField(index)">×</el-button>
          </div>
          <div v-if="!fieldRows.length" class="qc-empty" data-testid="qc-fields-empty">请从数据集字段中选择展示字段</div>
          <el-select
            :model-value="undefined"
            class="qc-add-field"
            size="small"
            placeholder="+ 添加字段"
            filterable
            @change="addField"
          >
            <el-option v-for="field in addableFields" :key="field.name" :label="fieldOptionLabel(field)" :value="field.name" />
          </el-select>
        </div>
      </div>

      <!-- ② 筛选器绑定 -->
      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">筛选器绑定</span>
          <el-button size="small" type="primary" plain data-testid="qc-add-binding" @click="addBinding">+ 添加绑定</el-button>
        </div>
        <div v-for="(binding, index) in bindingRows" :key="index" class="qc-binding-row" data-testid="qc-binding-row">
          <el-select v-model="binding.filterComponentId" size="small" placeholder="页面筛选器" filterable @change="onFilterChange(binding)">
            <el-option v-for="filter in filterOptions" :key="filter.id" :label="filter.title" :value="filter.id" />
          </el-select>
          <el-input v-model="binding.parameterName" size="small" placeholder="参数名" class="qc-param-input" />
          <el-select v-model="binding.field" size="small" placeholder="绑定字段（技术字段名）" filterable>
            <el-option v-for="row in fieldRows" :key="row.field" :label="`${row.field} · ${row.title}`" :value="row.field" />
          </el-select>
          <el-select v-model="binding.operator" size="small" class="qc-operator">
            <el-option v-for="op in OPERATORS" :key="op" :label="op" :value="op" />
          </el-select>
          <el-button size="small" text type="danger" data-testid="qc-binding-remove" @click="bindingRows.splice(index, 1)">删除</el-button>
        </div>
        <div v-if="!bindingRows.length" class="qc-empty">暂无筛选器绑定；「全部/清空」= 无过滤，显式空集合 = 空结果</div>
      </div>

      <!-- ③ 允许排序字段 -->
      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">允许排序</span>
          <el-switch v-model="sortEnabled" data-testid="qc-sort-enabled" />
        </div>
        <el-select v-if="sortEnabled" v-model="sortAllowed" multiple class="qc-sort-select" placeholder="选择允许排序的字段" data-testid="qc-sort-fields">
          <el-option v-for="row in fieldRows" :key="row.field" :label="`${row.field} · ${row.title}`" :value="row.field" />
        </el-select>
      </div>

      <!-- ④ 分页 -->
      <div class="qc-section">
        <div class="qc-section-head">
          <span class="qc-section-title">分页</span>
          <el-switch v-model="paginationEnabled" data-testid="qc-pagination-enabled" />
        </div>
        <div v-if="paginationEnabled" class="qc-pagination-row">
          <span class="qc-hint">默认每页</span>
          <el-input-number v-model="defaultPageSize" :min="1" :max="maxPageSize" size="small" controls-position="right" data-testid="qc-default-page-size" />
          <span class="qc-hint">最大每页</span>
          <el-input-number v-model="maxPageSize" :min="1" :max="500" size="small" controls-position="right" data-testid="qc-max-page-size" />
          <el-checkbox v-model="returnTotalCount" data-testid="qc-return-total">返回总数</el-checkbox>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" data-testid="qc-save" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { DatasetFieldMeta } from '@/utils/field-mapping'
import type {
  DatasetQueryConfig,
  QueryDisplayField,
  QueryParameterBinding,
} from '@/types'

const props = defineProps<{
  modelValue: boolean
  /** 后端 descriptor 字段（技术字段名 + 角色），来源必须是真实 descriptor，不允许前端假数据 */
  fields: DatasetFieldMeta[]
  /** 页面筛选器组件（id + title） */
  filterOptions: Array<{ id: string; title: string }>
  initialConfig: DatasetQueryConfig | null
  /** 旧 scriptFilterBindings 归一化的展示草稿：仅在无已保存配置时预填 */
  legacyBindings?: QueryParameterBinding[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', config: DatasetQueryConfig): void
}>()

const OPERATORS = ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'in', 'not_in', 'contains', 'is_null', 'is_not_null'] as const

interface FieldRow extends QueryDisplayField {}
interface BindingRow extends QueryParameterBinding {}

const fieldRows = ref<FieldRow[]>([])
const bindingRows = ref<BindingRow[]>([])
const sortEnabled = ref(false)
const sortAllowed = ref<string[]>([])
const paginationEnabled = ref(false)
const defaultPageSize = ref(100)
const maxPageSize = ref(500)
const returnTotalCount = ref(false)
const dragIndex = ref<number | null>(null)

const duplicateTitles = computed(() => {
  const seen = new Set<string>()
  const duplicates = new Set<string>()
  for (const row of fieldRows.value) {
    const key = row.title.trim()
    if (!key) continue
    if (seen.has(key)) duplicates.add(key)
    seen.add(key)
  }
  return duplicates
})

const addableFields = computed(() =>
  props.fields.filter((field) => !fieldRows.value.some((row) => row.field === field.name)),
)

function fieldOptionLabel(field: DatasetFieldMeta): string {
  const display = (field.displayName ?? '').trim()
  return display && display !== field.name ? `${field.name} · ${display}` : field.name
}

function roleOf(field: DatasetFieldMeta): 'dimension' | 'measure' {
  const role = (field.role ?? '').trim().toLowerCase()
  return role === 'measure' || role === 'metric' ? 'measure' : 'dimension'
}

function addField(fieldName: string): void {
  const field = props.fields.find((item) => item.name === fieldName)
  if (!field || fieldRows.value.some((row) => row.field === fieldName)) return
  fieldRows.value.push({ field: field.name, title: field.displayName || field.name, role: roleOf(field), dataType: undefined })
}

function touchTitle(_row: FieldRow): void { /* 重复校验由 duplicateTitles 驱动 */ }

function removeField(index: number): void {
  const removed = fieldRows.value[index]
  fieldRows.value.splice(index, 1)
  // 删除字段联动：解除筛选绑定引用，并移出排序白名单（白名单为空时关闭排序）
  bindingRows.value = bindingRows.value.filter((binding) => binding.field !== removed.field)
  sortAllowed.value = sortAllowed.value.filter((field) => field !== removed.field)
  if (!sortAllowed.value.length) sortEnabled.value = false
}

/* ---- 拖动排序（HTML5 原生 drag，仅调整列顺序） ---- */
function onDragStart(index: number): void { dragIndex.value = index }
function onDrop(index: number): void {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === index) return
  const [moved] = fieldRows.value.splice(from, 1)
  fieldRows.value.splice(index, 0, moved)
}

/* ---- 筛选器绑定 ---- */
function addBinding(): void {
  bindingRows.value.push({ filterComponentId: '', parameterName: '', field: '', operator: 'eq' })
}
function onFilterChange(binding: BindingRow): void {
  // 参数名缺省 = 筛选器组件 id（与后端 Planner 的声明式参数契约一致）
  if (!binding.parameterName) binding.parameterName = binding.filterComponentId
  if (!binding.field) {
    const firstDimension = fieldRows.value.find((row) => row.role === 'dimension')
    if (firstDimension) binding.field = firstDimension.field
  }
}

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  const initial = props.initialConfig
  if (initial && initial.displayFields.length) {
    fieldRows.value = initial.displayFields.map((row) => ({ ...row }))
    bindingRows.value = initial.parameterBindings.map((binding) => ({ ...binding }))
    sortEnabled.value = initial.sortPolicy.enabled
    sortAllowed.value = [...initial.sortPolicy.allowedFields]
    paginationEnabled.value = initial.paginationPolicy.enabled
    defaultPageSize.value = initial.paginationPolicy.defaultPageSize
    maxPageSize.value = initial.paginationPolicy.maxPageSize
    returnTotalCount.value = initial.paginationPolicy.returnTotalCount
  } else {
    // 无已保存配置：从 descriptor 全量字段起步，旧绑定草稿预填
    fieldRows.value = props.fields.map((field) => ({
      field: field.name, title: field.displayName || field.name, role: roleOf(field), dataType: undefined,
    }))
    bindingRows.value = (props.legacyBindings || []).map((binding) => ({ ...binding }))
    sortEnabled.value = false
    sortAllowed.value = []
    paginationEnabled.value = false
    defaultPageSize.value = 100
    maxPageSize.value = 500
    returnTotalCount.value = false
  }
}, { immediate: true })

function save(): void {
  if (!fieldRows.value.length) {
    ElMessage.warning('至少保留一个维度或指标字段')
    return
  }
  if (duplicateTitles.value.size) {
    ElMessage.warning('展示名必须唯一')
    return
  }
  const invalidBinding = bindingRows.value.find((binding) => !binding.filterComponentId || !binding.parameterName || !binding.field)
  if (invalidBinding) {
    ElMessage.warning('筛选器绑定必须完整：筛选器、参数名和字段不能为空（禁止空映射提交）')
    return
  }
  if (sortEnabled.value && !sortAllowed.value.length) {
    ElMessage.warning('开启排序后至少选择一个允许排序的字段')
    return
  }
  emit('save', {
    displayFields: fieldRows.value.map((row) => ({ ...row })),
    parameterBindings: bindingRows.value.map((binding) => ({ ...binding })),
    sortPolicy: {
      enabled: sortEnabled.value,
      mode: 'single',
      allowedFields: [...sortAllowed.value],
      defaultSort: null,
    },
    paginationPolicy: {
      enabled: paginationEnabled.value,
      defaultPageSize: defaultPageSize.value,
      maxPageSize: Math.min(maxPageSize.value, 500),
      returnTotalCount: returnTotalCount.value,
    },
  })
  emit('update:modelValue', false)
}
</script>

<style scoped>
.qc-body { display: flex; flex-direction: column; gap: 18px; max-height: 62vh; overflow: auto; padding-right: 4px; }
.qc-section { border: 1px solid var(--db-border); border-radius: var(--radius-md); padding: 12px; background: var(--db-card); }
.qc-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.qc-section-title { font-weight: 600; font-size: 13px; color: var(--db-text); }
.qc-hint { font-size: 12px; color: var(--db-text-muted); }
.qc-field-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; margin-bottom: 6px; border: 1px solid var(--db-border); border-radius: var(--radius-sm); background: var(--db-muted); cursor: grab; }
.qc-role-tag { flex-shrink: 0; }
.qc-tech-field { font-family: var(--font-mono, monospace); font-size: 12px; color: var(--db-text); min-width: 130px; flex-shrink: 0; }
.qc-title-input { flex: 1; }
.qc-error { color: var(--el-color-danger); font-size: 12px; flex-shrink: 0; }
.qc-remove { flex-shrink: 0; }
.qc-add-field { width: 220px; margin-top: 4px; }
.qc-binding-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.qc-binding-row .el-select { flex: 1; }
.qc-operator { width: 110px; flex: none !important; }
.qc-param-input { width: 140px; flex: none !important; }
.qc-sort-select { width: 100%; }
.qc-pagination-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.qc-empty { padding: 12px 0; color: var(--db-text-muted); font-size: 12px; text-align: center; }
</style>
