<template>
  <div class="business-term-panel">
    <div class="panel-toolbar">
      <div class="toolbar-left">
        <input v-model="keyword" class="search-input" :placeholder="t('businessTerm.searchPlaceholder')" @keyup.enter="handleSearch" />
      </div>
      <div class="toolbar-right">
        <button class="tool-btn" :disabled="embedding" @click="handleEmbedAll">
          <span v-if="embedding">⏳</span>
          <span v-else>🔮</span>
          {{ embedding ? t('businessTerm.embedding') : t('businessTerm.embedAll') }}
        </button>
        <button class="tool-btn" :disabled="rebuilding" @click="handleRebuildEs">
          <span v-if="rebuilding">⏳</span>
          <span v-else>🔄</span>
          {{ rebuilding ? t('businessTerm.rebuilding') : t('businessTerm.rebuildEs') }}
        </button>
        <button class="tool-btn primary" @click="handleCreate">
          ＋ {{ t('businessTerm.create') }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="panel-loading">
      <span>{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="terms.length === 0" class="panel-empty">
      <p>{{ t('businessTerm.emptyDesc') }}</p>
    </div>

    <div v-else class="table-list-wrapper">
      <div class="table-grid-scroll">
        <table class="data-grid">
          <thead>
            <tr>
              <th class="col-term-name">{{ t('businessTerm.colTermName') }}</th>
              <th class="col-synonyms">{{ t('businessTerm.colSynonyms') }}</th>
              <th class="col-description">{{ t('businessTerm.colDescription') }}</th>
              <th class="col-calculation-formula">{{ t('businessTerm.colCalculationFormula') }}</th>
              <th class="col-data-caliber">{{ t('businessTerm.colDataCaliber') }}</th>
              <th class="col-owner">{{ t('businessTerm.colOwner') }}</th>
              <th class="col-category">{{ t('businessTerm.colCategory') }}</th>
              <th class="col-status">{{ t('businessTerm.colStatus') }}</th>
              <th class="col-action">{{ t('datasourcePage.colAction') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="term in terms" :key="term.id">
              <td class="col-term-name">
                <span class="term-name cell-text" :title="term.termName">{{ term.termName }}</span>
              </td>
              <td class="col-synonyms">
                <span class="cell-text" :title="term.synonyms">{{ term.synonyms || '-' }}</span>
              </td>
              <td class="col-description">
                <span class="cell-text" :title="term.description">{{ term.description || '-' }}</span>
              </td>
              <td class="col-calculation-formula">
                <span class="cell-text" :title="term.calculationFormula">{{ term.calculationFormula || '-' }}</span>
              </td>
              <td class="col-data-caliber">
                <span class="cell-text" :title="term.dataCaliber">{{ term.dataCaliber || '-' }}</span>
              </td>
              <td class="col-owner">
                <span class="cell-text" :title="term.owner">{{ term.owner || '-' }}</span>
              </td>
              <td class="col-category">
                <span v-if="term.category" class="category-tag">{{ term.category }}</span>
                <span v-else>-</span>
              </td>
              <td class="col-status">
                <span class="status-badge" :class="term.status === 1 ? 'enabled' : 'disabled'">
                  {{ term.status === 1 ? t('businessTerm.statusEnabled') : t('businessTerm.statusDisabled') }}
                </span>
              </td>
              <td class="col-action">
                <div class="row-actions">
                  <button class="icon-btn" :title="t('businessTerm.actionEdit')" @click="handleEdit(term)">✏️</button>
                  <button class="icon-btn" :title="term.status === 1 ? t('businessTerm.actionDisable') : t('businessTerm.actionEnable')" @click="handleToggle(term)">
                    {{ term.status === 1 ? '⏸️' : '▶️' }}
                  </button>
                  <button class="icon-btn" :title="t('businessTerm.actionDelete')" @click="handleDelete(term)">🗑️</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <div class="page-info">
          <span class="page-total">{{ terms.length }}</span> {{ t('businessTerm.totalUnit') }}
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog-card dialog-card-wide">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ isEditing ? t('businessTerm.editTitle') : t('businessTerm.createTitle') }}</h3>
          <button class="dialog-close" @click="showDialog = false">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldTenantCode') }}<span class="required">*</span></label>
            <input v-model="formData.tenantCode" :disabled="isEditing" class="form-input" :placeholder="t('businessTerm.fieldTenantCodePlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldTermName') }}<span class="required">*</span></label>
            <input v-model="formData.termName" class="form-input" :placeholder="t('businessTerm.fieldTermNamePlaceholder')" />
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldSynonyms') }}</label>
              <input v-model="formData.synonyms" class="form-input" :placeholder="t('businessTerm.fieldSynonymsPlaceholder')" />
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldCategory') }}</label>
              <input v-model="formData.category" class="form-input" :placeholder="t('businessTerm.fieldCategoryPlaceholder')" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldDescription') }}</label>
            <textarea v-model="formData.description" class="form-textarea" :placeholder="t('businessTerm.fieldDescriptionPlaceholder')" rows="3"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldCalculationFormula') }}</label>
            <textarea v-model="formData.calculationFormula" class="form-textarea" :placeholder="t('businessTerm.fieldCalculationFormulaPlaceholder')" rows="2"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldDataCaliber') }}</label>
            <textarea v-model="formData.dataCaliber" class="form-textarea" :placeholder="t('businessTerm.fieldDataCaliberPlaceholder')" rows="2"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldDataSource') }}</label>
              <input v-model="formData.dataSource" class="form-input" :placeholder="t('businessTerm.fieldDataSourcePlaceholder')" />
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldOwner') }}</label>
              <input v-model="formData.owner" class="form-input" :placeholder="t('businessTerm.fieldOwnerPlaceholder')" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldBusinessRule') }}</label>
            <textarea v-model="formData.businessRule" class="form-textarea" :placeholder="t('businessTerm.fieldBusinessRulePlaceholder')" rows="2"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldRelatedTerms') }}</label>
            <input v-model="formData.relatedTerms" class="form-input" :placeholder="t('businessTerm.fieldRelatedTermsPlaceholder')" />
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldRelatedMetrics') }}</label>
              <el-select
                v-model="selectedMetricKeys"
                multiple
                filterable
                remote
                :remote-method="handleMetricRemoteSearch"
                :loading="metricLoading"
                :placeholder="t('businessTerm.fieldRelatedMetricsPlaceholder')"
                class="ref-select"
                @change="handleMetricChange"
              >
                <el-option v-for="opt in metricOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
              </el-select>
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldRelatedDimensions') }}</label>
              <el-select
                v-model="selectedDimensionKeys"
                multiple
                filterable
                remote
                :remote-method="handleDimensionRemoteSearch"
                :loading="dimensionLoading"
                :placeholder="t('businessTerm.fieldRelatedDimensionsPlaceholder')"
                class="ref-select"
                @change="handleDimensionChange"
              >
                <el-option v-for="opt in dimensionOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
              </el-select>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('businessTerm.fieldExample') }}</label>
            <textarea v-model="formData.example" class="form-textarea" :placeholder="t('businessTerm.fieldExamplePlaceholder')" rows="2"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldSecurityLevel') }}</label>
              <select v-model="formData.securityLevel" class="form-select">
                <option value="">{{ t('businessTerm.fieldSecurityLevelPlaceholder') }}</option>
                <option value="public">{{ t('businessTerm.securityLevelPublic') }}</option>
                <option value="internal">{{ t('businessTerm.securityLevelInternal') }}</option>
                <option value="confidential">{{ t('businessTerm.securityLevelConfidential') }}</option>
              </select>
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('businessTerm.fieldParentId') }}</label>
              <select v-model="formData.parentId" class="form-select" @change="handleParentIdChange">
                <option :value="null">{{ t('businessTerm.fieldParentIdPlaceholder') }}</option>
                <option v-for="pt in parentOptions" :key="pt.id" :value="pt.id">
                  {{ pt.termName }}
                </option>
              </select>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showDialog = false">{{ t('common.cancel') }}</button>
          <button class="btn-confirm" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? t('common.loading') : t('common.confirm') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as businessTermApi from '@/api/business-term'
import type { BusinessTerm, BusinessTermCreateRequest, BusinessTermRef, BusinessTermUpdateRequest } from '@/types'

const props = defineProps<{
  tenantCode: string
}>()

const { t } = useI18n()

const loading = ref(false)
const embedding = ref(false)
const rebuilding = ref(false)
const terms = ref<BusinessTerm[]>([])
const keyword = ref('')

/** 弹窗状态 */
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const submitting = ref(false)

/** 父术语下拉选项 */
const parentOptions = ref<BusinessTerm[]>([])

/** 关联引用候选选项（key = datasourceId:name） */
interface RefOption {
  key: string
  label: string
  ref: BusinessTermRef
}

/** 关联指标候选 */
const metricOptions = ref<RefOption[]>([])
const metricLoading = ref(false)
/** 关联维度候选 */
const dimensionOptions = ref<RefOption[]>([])
const dimensionLoading = ref(false)
/** 已选关联指标 key 列表 */
const selectedMetricKeys = ref<string[]>([])
/** 已选关联维度 key 列表 */
const selectedDimensionKeys = ref<string[]>([])
/** key → 指标引用对象映射（回显与提交用） */
const metricKeyRefMap = ref<Record<string, BusinessTermRef>>({})
/** key → 维度引用对象映射（回显与提交用） */
const dimensionKeyRefMap = ref<Record<string, BusinessTermRef>>({})

/** 表单数据 */
const formData = ref<BusinessTermCreateRequest>({
  tenantCode: '',
  termName: '',
  synonyms: '',
  description: '',
  calculationFormula: '',
  dataCaliber: '',
  dataSource: '',
  owner: '',
  businessRule: '',
  relatedTerms: '',
  example: '',
  securityLevel: '',
  category: '',
  parentId: null,
  relatedMetrics: [],
  relatedDimensions: [],
})

onMounted(() => {
  loadTerms()
})

/** 租户切换时重新加载术语列表 */
watch(() => props.tenantCode, () => {
  keyword.value = ''
  loadTerms()
})

/** 加载术语列表 */
async function loadTerms(): Promise<void> {
  if (!props.tenantCode) {
    return
  }
  loading.value = true
  try {
    const data = await businessTermApi.list(props.tenantCode)
    terms.value = (data || []) as unknown as BusinessTerm[]
  } catch {
    terms.value = []
  } finally {
    loading.value = false
  }
}

/** 关键词搜索 */
async function handleSearch(): Promise<void> {
  if (!props.tenantCode) {
    return
  }
  const kw = keyword.value.trim()
  if (!kw) {
    loadTerms()
    return
  }
  loading.value = true
  try {
    const data = await businessTermApi.search(props.tenantCode, kw)
    terms.value = (data || []) as unknown as BusinessTerm[]
  } catch {
    terms.value = []
  } finally {
    loading.value = false
  }
}

/** 加载父术语选项 */
async function loadParentOptions(): Promise<void> {
  if (!props.tenantCode) {
    return
  }
  try {
    const data = await businessTermApi.list(props.tenantCode)
    parentOptions.value = (data || []) as unknown as BusinessTerm[]
  } catch {
    parentOptions.value = []
  }
}

/** 新建 */
function handleCreate(): void {
  isEditing.value = false
  editingId.value = ''
  formData.value = {
    tenantCode: props.tenantCode,
    termName: '',
    synonyms: '',
    description: '',
    calculationFormula: '',
    dataCaliber: '',
    dataSource: '',
    owner: '',
    businessRule: '',
    relatedTerms: '',
    example: '',
    securityLevel: '',
    category: '',
    parentId: null,
    relatedMetrics: [],
    relatedDimensions: [],
  }
  resetRefSelects()
  loadParentOptions()
  showDialog.value = true
}

/** 编辑 */
function handleEdit(term: BusinessTerm): void {
  isEditing.value = true
  editingId.value = term.id
  const relatedMetrics = term.relatedMetrics || []
  const relatedDimensions = term.relatedDimensions || []
  formData.value = {
    tenantCode: term.tenantCode,
    termName: term.termName || '',
    synonyms: term.synonyms || '',
    description: term.description || '',
    calculationFormula: term.calculationFormula || '',
    dataCaliber: term.dataCaliber || '',
    dataSource: term.dataSource || '',
    owner: term.owner || '',
    businessRule: term.businessRule || '',
    relatedTerms: term.relatedTerms || '',
    example: term.example || '',
    securityLevel: term.securityLevel || '',
    category: term.category || '',
    parentId: term.parentId || null,
    relatedMetrics,
    relatedDimensions,
  }
  initRefSelects(relatedMetrics, relatedDimensions)
  loadParentOptions()
  showDialog.value = true
}

/** 父术语选择变化时规范化 */
function handleParentIdChange(): void {
  if (formData.value.parentId === '' || formData.value.parentId === undefined) {
    formData.value.parentId = null
  }
}

/** 构造关联引用下拉 key（datasourceId:name，name 为稳定标识） */
function buildRefKey(ref: BusinessTermRef): string {
  return `${ref.datasourceId}:${ref.name}`
}

/** 构造关联引用下拉展示文案 */
function buildRefLabel(ref: BusinessTermRef): string {
  const dataSourceSuffix = ref.datasourceName ? ` [${ref.datasourceName}]` : ''
  if (ref.displayName && ref.displayName !== ref.name) {
    return `${ref.displayName}(${ref.name})${dataSourceSuffix}`
  }
  return `${ref.name}${dataSourceSuffix}`
}

/** 将引用对象转为下拉选项 */
function toRefOption(ref: BusinessTermRef): RefOption {
  return {
    key: buildRefKey(ref),
    label: buildRefLabel(ref),
    ref,
  }
}

/** 重置关联引用选择状态（新建时调用） */
function resetRefSelects(): void {
  selectedMetricKeys.value = []
  selectedDimensionKeys.value = []
  metricKeyRefMap.value = {}
  dimensionKeyRefMap.value = {}
  metricOptions.value = []
  dimensionOptions.value = []
}

/** 编辑回显：按已有关联引用初始化选择状态与选项 */
function initRefSelects(metrics: BusinessTermRef[], dimensions: BusinessTermRef[]): void {
  metricKeyRefMap.value = {}
  dimensionKeyRefMap.value = {}
  metricOptions.value = (metrics || []).map(toRefOption)
  dimensionOptions.value = (dimensions || []).map(toRefOption)
  metricOptions.value.forEach((opt) => {
    metricKeyRefMap.value[opt.key] = opt.ref
  })
  dimensionOptions.value.forEach((opt) => {
    dimensionKeyRefMap.value[opt.key] = opt.ref
  })
  selectedMetricKeys.value = (metrics || []).map(buildRefKey)
  selectedDimensionKeys.value = (dimensions || []).map(buildRefKey)
}

/** 远程搜索关联指标候选 */
async function handleMetricRemoteSearch(keyword: string): Promise<void> {
  metricLoading.value = true
  try {
    const data = await businessTermApi.referenceOptions(keyword || '', 20)
    metricOptions.value = (data?.metrics || []).map(toRefOption)
    metricOptions.value.forEach((opt) => {
      metricKeyRefMap.value[opt.key] = opt.ref
    })
  } catch {
    metricOptions.value = []
  } finally {
    metricLoading.value = false
  }
}

/** 远程搜索关联维度候选 */
async function handleDimensionRemoteSearch(keyword: string): Promise<void> {
  dimensionLoading.value = true
  try {
    const data = await businessTermApi.referenceOptions(keyword || '', 20)
    dimensionOptions.value = (data?.dimensions || []).map(toRefOption)
    dimensionOptions.value.forEach((opt) => {
      dimensionKeyRefMap.value[opt.key] = opt.ref
    })
  } catch {
    dimensionOptions.value = []
  } finally {
    dimensionLoading.value = false
  }
}

/** 指标选择变化：将已选 key 还原为引用对象写入表单 */
function handleMetricChange(): void {
  formData.value.relatedMetrics = selectedMetricKeys.value
    .map((key) => metricKeyRefMap.value[key])
    .filter((ref): ref is BusinessTermRef => !!ref)
}

/** 维度选择变化：将已选 key 还原为引用对象写入表单 */
function handleDimensionChange(): void {
  formData.value.relatedDimensions = selectedDimensionKeys.value
    .map((key) => dimensionKeyRefMap.value[key])
    .filter((ref): ref is BusinessTermRef => !!ref)
}

/** 提交表单 */
async function handleSubmit(): Promise<void> {
  if (!formData.value.tenantCode || !formData.value.termName) {
    ElMessage.warning(t('businessTerm.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEditing.value) {
      const updateData: BusinessTermUpdateRequest = {
        termName: formData.value.termName,
        synonyms: formData.value.synonyms,
        description: formData.value.description,
        calculationFormula: formData.value.calculationFormula,
        dataCaliber: formData.value.dataCaliber,
        dataSource: formData.value.dataSource,
        owner: formData.value.owner,
        businessRule: formData.value.businessRule,
        relatedTerms: formData.value.relatedTerms,
        example: formData.value.example,
        securityLevel: formData.value.securityLevel,
        category: formData.value.category,
        parentId: formData.value.parentId || null,
        relatedMetrics: formData.value.relatedMetrics,
        relatedDimensions: formData.value.relatedDimensions,
      }
      await businessTermApi.update(editingId.value, updateData)
      ElMessage.success(t('businessTerm.updateSuccess'))
    } else {
      await businessTermApi.create(formData.value)
      ElMessage.success(t('businessTerm.createSuccess'))
    }
    showDialog.value = false
    loadTerms()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

/** 启停切换 */
async function handleToggle(term: BusinessTerm): Promise<void> {
  try {
    if (term.status === 1) {
      await businessTermApi.disable(term.id)
    } else {
      await businessTermApi.enable(term.id)
    }
    ElMessage.success(t('businessTerm.toggleSuccess'))
    loadTerms()
  } catch {
    // error handled by interceptor
  }
}

/** 删除 */
async function handleDelete(term: BusinessTerm): Promise<void> {
  try {
    await ElMessageBox.confirm(t('businessTerm.deleteConfirm'), '', {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await businessTermApi.remove(term.id)
    ElMessage.success(t('businessTerm.deleteSuccess'))
    loadTerms()
  } catch {
    // cancel or error
  }
}

/** 全量向量化并写入 ES */
async function handleEmbedAll(): Promise<void> {
  if (!props.tenantCode) {
    return
  }
  embedding.value = true
  try {
    const count = await businessTermApi.embedAndIndex(props.tenantCode)
    ElMessage.success(t('businessTerm.embedSuccess', { count }))
  } catch {
    ElMessage.error(t('businessTerm.embedFail'))
  } finally {
    embedding.value = false
  }
}

/** 重建 ES 索引 */
async function handleRebuildEs(): Promise<void> {
  if (!props.tenantCode) {
    return
  }
  rebuilding.value = true
  try {
    const count = await businessTermApi.rebuildEsIndex(props.tenantCode)
    ElMessage.success(t('businessTerm.rebuildSuccess', { count }))
  } catch {
    ElMessage.error(t('businessTerm.rebuildFail'))
  } finally {
    rebuilding.value = false
  }
}
</script>

<style scoped>
.business-term-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--theme-surface);
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid var(--theme-border);
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  width: 220px;
  font-family: inherit;
  color: var(--theme-text);
  background: var(--theme-surface);
  transition: border-color 0.2s;
}

.search-input::placeholder {
  color: var(--theme-text-muted);
}

.search-input:focus {
  border-color: var(--main-orange);
}

.tool-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 4px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.tool-btn:hover:not(:disabled) {
  border-color: var(--main-orange);
  color: var(--main-orange);
}

.tool-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tool-btn.primary {
  background: var(--main-orange);
  border-color: var(--main-orange);
  color: #fff;
}

.tool-btn.primary:hover:not(:disabled) {
  background: var(--dark-orange);
}

.panel-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--theme-text-muted);
  font-size: 14px;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--theme-text-muted);
  font-size: 13px;
  gap: 12px;
}

.table-list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-grid-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
}

.data-grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.data-grid thead tr {
  background: var(--theme-bg);
}

.data-grid th {
  padding: 11px 12px;
  text-align: left;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--theme-text-muted);
  border-bottom: 1px solid var(--theme-border);
  white-space: nowrap;
}

.data-grid td {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  border-bottom: 1px solid var(--theme-border);
  vertical-align: middle;
}

.data-grid tbody tr:hover {
  background: var(--theme-surface-hover);
}

.col-term-name {
  width: 13%;
}

.col-synonyms {
  width: 12%;
}

/* 单元格文本省略：超长内容以省略号截断，防止撑开列宽导致布局变形 */
.cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.col-description {
  width: 16%;
  max-width: 180px;
}

.col-calculation-formula {
  width: 14%;
  max-width: 150px;
}

.col-data-caliber {
  width: 14%;
  max-width: 150px;
}

.col-owner {
  width: 9%;
}

.col-category {
  width: 8%;
}

.col-status {
  width: 7%;
}

.col-action {
  width: 9%;
  text-align: right;
}

.term-name {
  color: var(--theme-text);
  font-weight: 500;
}

.category-tag {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.status-badge {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-badge.enabled {
  background: rgba(0, 180, 42, 0.12);
  color: #00b42a;
}

.status-badge.disabled {
  background: var(--theme-surface-hover);
  color: var(--theme-text-muted);
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  opacity: 0.65;
  padding: 2px 4px;
  border-radius: 3px;
  transition: all 0.15s;
  line-height: 1;
}

.icon-btn:hover {
  opacity: 1;
  background: var(--theme-surface-hover);
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-top: 1px solid var(--theme-border);
}

.page-info {
  font-size: 12px;
  color: var(--theme-text-muted);
}

.page-total {
  font-weight: 600;
  color: var(--theme-text-secondary);
}

/* ========== 弹窗 ========== */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.dialog-card {
  background: var(--theme-surface);
  border-radius: 8px;
  width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 6px 30px rgba(0, 0, 0, 0.12);
}

.dialog-card-wide {
  width: 720px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border);
}

.dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 16px;
  color: var(--theme-text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
}

.dialog-close:hover {
  color: var(--theme-text);
  background: var(--theme-surface-hover);
}

.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: 14px;
}

.form-group.half {
  flex: 1;
  min-width: 0;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-text-secondary);
  margin-bottom: 6px;
}

.required {
  color: #f53f3f;
  margin-left: 2px;
}

.form-input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  color: var(--theme-text);
  background: var(--theme-surface);
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-input::placeholder {
  color: var(--theme-text-muted);
}

.form-input:focus {
  border-color: var(--main-orange);
}

.form-input:disabled {
  background: var(--theme-bg);
  color: var(--theme-text-muted);
}

.form-select {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  color: var(--theme-text);
  transition: border-color 0.2s;
  box-sizing: border-box;
  background: var(--theme-surface);
  cursor: pointer;
}

.form-select:focus {
  border-color: var(--main-orange);
}

.form-select:disabled {
  background: var(--theme-bg);
  color: var(--theme-text-muted);
}

/* 关联指标/维度远程搜索多选 */
.ref-select {
  width: 100%;
}

.ref-select :deep(.el-select__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px var(--theme-border) inset;
  background: var(--theme-surface);
  min-height: 32px;
}

.ref-select :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--main-orange) inset;
}

.ref-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--main-orange) inset;
}

.ref-select :deep(.el-select__placeholder) {
  color: var(--theme-text-muted);
}

/* 关联指标/维度选中标签过长时省略号截断，防止选择器布局变形 */
.ref-select :deep(.el-tag) {
  max-width: 100%;
}

.ref-select :deep(.el-select__tags-text) {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  vertical-align: middle;
}

.form-textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  color: var(--theme-text);
  background: var(--theme-surface);
  transition: border-color 0.2s;
  resize: vertical;
  box-sizing: border-box;
}

.form-textarea::placeholder {
  color: var(--theme-text-muted);
}

.form-textarea:focus {
  border-color: var(--main-orange);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--theme-border);
}

.btn-cancel {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-cancel:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
}

.btn-confirm {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-confirm:hover:not(:disabled) {
  background: var(--dark-orange);
}

.btn-confirm:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
