<template>
  <div class="semantic-model-panel">
    <div class="panel-toolbar">
      <div class="toolbar-left">
        <input v-model="keyword" class="search-input" :placeholder="t('semanticModel.searchPlaceholder')" @keyup.enter="handleSearch" />
      </div>
      <div class="toolbar-right">
        <button class="tool-btn" :disabled="autoIniting" @click="handleAutoInit">
          <span v-if="autoIniting">⏳</span>
          <span v-else>📋</span>
          {{ autoIniting ? t('semanticModel.autoIniting') : t('semanticModel.autoInit') }}
        </button>
        <button v-if="isAloudata" class="tool-btn" :disabled="syncingAloudata || !datasourceId" @click="handleSyncAloudata">
          <span v-if="syncingAloudata">⏳</span>
          <span v-else>🔄</span>
          {{ syncingAloudata ? t('semanticModel.syncing') : t('semanticModel.syncAloudata') }}
        </button>
        <button class="tool-btn primary" @click="handleCreate">
          ＋ {{ t('semanticModel.create') }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="panel-loading">
      <span>{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="models.length === 0" class="panel-empty">
      <p>{{ t('semanticModel.emptyDesc') }}</p>
      <button class="tool-btn primary" @click="handleAutoInit">{{ t('semanticModel.autoInit') }}</button>
    </div>

    <div v-else class="table-list-wrapper">
      <div class="table-grid-scroll">
        <table class="data-grid">
          <thead>
            <tr>
              <th class="col-table">{{ t('semanticModel.colTable') }}</th>
              <th class="col-column">{{ t('semanticModel.colColumn') }}</th>
              <th class="col-business-name">{{ t('semanticModel.colBusinessName') }}</th>
              <th class="col-description">{{ t('semanticModel.colDescription') }}</th>
              <th class="col-type">{{ t('semanticModel.colType') }}</th>
              <th class="col-status">{{ t('semanticModel.colStatus') }}</th>
              <th class="col-action">{{ t('datasourcePage.colAction') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="model in models" :key="model.id">
              <td class="col-table">
                <span class="tbl-name">{{ model.tableName }}</span>
              </td>
              <td class="col-column">
                <span class="col-name">{{ model.columnName }}</span>
              </td>
              <td class="col-business-name">
                {{ model.businessName || '-' }}
              </td>
              <td class="col-description">
                <span :title="model.businessDescription">{{ model.businessDescription || model.columnComment || '-' }}</span>
              </td>
              <td class="col-type">
                <span class="type-tag">{{ model.dataType || '-' }}</span>
              </td>
              <td class="col-status">
                <span class="status-badge" :class="model.status === 1 ? 'enabled' : 'disabled'">
                  {{ model.status === 1 ? t('semanticModel.statusEnabled') : t('semanticModel.statusDisabled') }}
                </span>
              </td>
              <td class="col-action">
                <div class="row-actions">
                  <button class="icon-btn" :title="t('semanticModel.actionEdit')" @click="handleEdit(model)">✏️</button>
                  <button class="icon-btn" :title="model.status === 1 ? t('semanticModel.actionDisable') : t('semanticModel.actionEnable')" @click="handleToggle(model)">
                    {{ model.status === 1 ? '⏸️' : '▶️' }}
                  </button>
                  <button class="icon-btn" :title="t('semanticModel.actionDelete')" @click="handleDelete(model)">🗑️</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <div class="page-info">
          <span class="page-total">{{ models.length }}</span> {{ t('semanticModel.totalUnit') }}
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog-card">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ isEditing ? t('semanticModel.editTitle') : t('semanticModel.createTitle') }}</h3>
          <button class="dialog-close" @click="showDialog = false">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">{{ t('semanticModel.colTable') }}<span class="required">*</span></label>
            <select v-model="formData.tableName" :disabled="isEditing" class="form-select" @change="handleTableChange">
              <option value="" disabled>{{ t('semanticModel.tablePlaceholder') }}</option>
              <option v-for="tbl in tableOptions" :key="tbl.id" :value="tbl.tableName">
                {{ tbl.tableName }}{{ tbl.tableComment ? ' (' + tbl.tableComment + ')' : '' }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('semanticModel.colColumn') }}<span class="required">*</span></label>
            <select v-model="formData.columnName" :disabled="isEditing || !formData.tableName" class="form-select">
              <option value="" disabled>{{ columnOptions.length === 0 ? t('semanticModel.selectTableFirst') : t('semanticModel.columnPlaceholder') }}</option>
              <option v-for="col in columnOptions" :key="col.id" :value="col.columnName">
                {{ col.columnName }}{{ col.columnComment ? ' (' + col.columnComment + ')' : '' }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('semanticModel.colBusinessName') }}</label>
            <input v-model="formData.businessName" class="form-input" :placeholder="t('semanticModel.businessNamePlaceholder')" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('semanticModel.colDescription') }}</label>
            <textarea v-model="formData.businessDescription" class="form-textarea" :placeholder="t('semanticModel.descriptionPlaceholder')" rows="3"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('semanticModel.fieldSynonyms') }}</label>
              <input v-model="formData.synonyms" class="form-input" :placeholder="t('semanticModel.synonymsPlaceholder')" />
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('semanticModel.fieldUnit') }}</label>
              <input v-model="formData.unit" class="form-input" :placeholder="t('semanticModel.unitPlaceholder')" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('semanticModel.fieldExampleValues') }}</label>
              <input v-model="formData.exampleValues" class="form-input" :placeholder="t('semanticModel.exampleValuesPlaceholder')" />
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('semanticModel.fieldValueRange') }}</label>
              <input v-model="formData.valueRange" class="form-input" :placeholder="t('semanticModel.valueRangePlaceholder')" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('semanticModel.fieldEnumValues') }}</label>
            <input v-model="formData.enumValues" class="form-input" :placeholder="t('semanticModel.enumValuesPlaceholder')" />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as semanticModelApi from '@/api/semantic-model'
import * as datasourceApi from '@/api/datasource'
import type { SemanticModel, SemanticModelCreateRequest, SemanticModelUpdateRequest, DatasourceTable, DatasourceColumn } from '@/types'

const props = defineProps<{
  datasourceId: string
  tables: DatasourceTable[]
  sourceType: string
}>()

const { t } = useI18n()

/** 是否为 Aloudata 类型数据源 */
const isAloudata = computed(() => props.sourceType?.toLowerCase() === 'aloudata')

const loading = ref(false)
const autoIniting = ref(false)
const syncingAloudata = ref(false)
const models = ref<SemanticModel[]>([])
const keyword = ref('')

/** 弹窗状态 */
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const submitting = ref(false)

/** 表/字段下拉选项 */
const tableOptions = ref<DatasourceTable[]>([])
const columnOptions = ref<DatasourceColumn[]>([])
const columnLoading = ref(false)

/** 表单数据 */
const formData = ref<SemanticModelCreateRequest>({
  datasourceId: '',
  tableName: '',
  columnName: '',
  businessName: '',
  businessDescription: '',
  synonyms: '',
  dataType: '',
  columnComment: '',
  exampleValues: '',
  enumValues: '',
  unit: '',
  valueRange: '',
})

onMounted(() => {
  loadModels()
})

watch(() => props.datasourceId, () => {
  loadModels()
  loadTableOptions()
})

/** 加载语义模型列表 */
async function loadModels(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loading.value = true
  try {
    const data = await semanticModelApi.list(props.datasourceId)
    models.value = (data || []) as unknown as SemanticModel[]
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

/** 关键词搜索 */
async function handleSearch(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  const kw = keyword.value.trim()
  if (!kw) {
    loadModels()
    return
  }
  loading.value = true
  try {
    const data = await semanticModelApi.search(props.datasourceId, kw)
    models.value = (data || []) as unknown as SemanticModel[]
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

/** 自动初始化 */
async function handleAutoInit(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  autoIniting.value = true
  try {
    const count = await semanticModelApi.autoInit(props.datasourceId)
    ElMessage.success(t('semanticModel.autoInitSuccess', { count }))
    loadModels()
  } catch {
    ElMessage.error(t('semanticModel.autoInitFail'))
  } finally {
    autoIniting.value = false
  }
}

/** 从 Aloudata 指标平台同步语义模型 */
async function handleSyncAloudata(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  syncingAloudata.value = true
  try {
    const result = await datasourceApi.syncAloudataSemantic(props.datasourceId)
    const summary = result
      ? `指标: ${result.metricCount}, 维度: ${result.dimensionCount}, 关联: ${result.metricDimensionCount}`
      : ''
    ElMessage.success(t('semanticModel.syncSuccess', { count: summary }))
    loadModels()
  } catch (e: any) {
    ElMessage.error(e.message || t('semanticModel.syncFailed'))
  } finally {
    syncingAloudata.value = false
  }
}

/** 新建 */
function handleCreate(): void {
  isEditing.value = false
  editingId.value = ''
  formData.value = {
    datasourceId: props.datasourceId,
    tableName: '',
    columnName: '',
    businessName: '',
    businessDescription: '',
    synonyms: '',
    dataType: '',
    columnComment: '',
    exampleValues: '',
    enumValues: '',
    unit: '',
    valueRange: '',
  }
  columnOptions.value = []
  loadTableOptions()
  showDialog.value = true
}

/** 编辑 */
function handleEdit(model: SemanticModel): void {
  isEditing.value = true
  editingId.value = model.id
  formData.value = {
    datasourceId: props.datasourceId,
    tableName: model.tableName,
    columnName: model.columnName,
    businessName: model.businessName || '',
    businessDescription: model.businessDescription || '',
    synonyms: model.synonyms || '',
    dataType: model.dataType || '',
    columnComment: model.columnComment || '',
    exampleValues: model.exampleValues || '',
    enumValues: model.enumValues || '',
    unit: model.unit || '',
    valueRange: model.valueRange || '',
  }
  loadTableOptions()
  loadColumns(model.tableName)
  showDialog.value = true
}

/** 提交表单 */
async function handleSubmit(): Promise<void> {
  if (!formData.value.tableName || !formData.value.columnName) {
    ElMessage.warning(t('semanticModel.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEditing.value) {
      const updateData: SemanticModelUpdateRequest = {
        businessName: formData.value.businessName,
        businessDescription: formData.value.businessDescription,
        synonyms: formData.value.synonyms,
        exampleValues: formData.value.exampleValues,
        enumValues: formData.value.enumValues,
        unit: formData.value.unit,
        valueRange: formData.value.valueRange,
      }
      await semanticModelApi.update(editingId.value, updateData)
      ElMessage.success(t('semanticModel.updateSuccess'))
    } else {
      formData.value.datasourceId = props.datasourceId
      await semanticModelApi.create(formData.value)
      ElMessage.success(t('semanticModel.createSuccess'))
    }
    showDialog.value = false
    loadModels()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

/** 启停切换 */
async function handleToggle(model: SemanticModel): Promise<void> {
  try {
    if (model.status === 1) {
      await semanticModelApi.disable(model.id)
    } else {
      await semanticModelApi.enable(model.id)
    }
    ElMessage.success(t('semanticModel.toggleSuccess'))
    loadModels()
  } catch {
    // error handled by interceptor
  }
}

/** 加载表下拉选项（从 props.tables 赋值） */
function loadTableOptions(): void {
  tableOptions.value = (props.tables || []).map((tbl) => ({
    id: tbl.id,
    tableName: tbl.tableName,
    tableComment: tbl.tableComment || '',
  }))
}

/** 表选择变化时，加载对应字段列表 */
function handleTableChange(): void {
  formData.value.columnName = ''
  columnOptions.value = []
  if (formData.value.tableName) {
    loadColumns(formData.value.tableName)
  }
}

/** 根据表名加载字段列表 */
async function loadColumns(tableName: string): Promise<void> {
  const selectedTable = tableOptions.value.find((tbl) => tbl.tableName === tableName)
  if (!selectedTable || !props.datasourceId) {
    return
  }
  columnLoading.value = true
  try {
    const data = await datasourceApi.listColumns(props.datasourceId, selectedTable.id)
    columnOptions.value = (data || []) as unknown as DatasourceColumn[]
  } catch {
    columnOptions.value = []
  } finally {
    columnLoading.value = false
  }
}

/** 删除 */
async function handleDelete(model: SemanticModel): Promise<void> {
  try {
    await ElMessageBox.confirm(t('semanticModel.deleteConfirm'), '', {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await semanticModelApi.remove(model.id)
    ElMessage.success(t('semanticModel.deleteSuccess'))
    loadModels()
  } catch {
    // cancel or error
  }
}
</script>

<style scoped>
.semantic-model-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid #e5e6eb;
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
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  width: 220px;
  font-family: inherit;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #165dff;
}

.tool-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.tool-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.tool-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tool-btn.primary {
  background: #165dff;
  border-color: #165dff;
  color: #fff;
}

.tool-btn.primary:hover:not(:disabled) {
  background: #0e42d2;
}

.panel-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #86909c;
  font-size: 14px;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #c9cdd4;
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
}

.data-grid {
  width: 100%;
  border-collapse: collapse;
}

.data-grid thead tr {
  background: #fafafa;
}

.data-grid th {
  padding: 11px 16px;
  text-align: left;
  font-size: 12.5px;
  font-weight: 500;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  white-space: nowrap;
}

.data-grid td {
  padding: 10px 16px;
  font-size: 13px;
  color: #4e5969;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
}

.data-grid tbody tr:hover {
  background: #fafbfc;
}

.col-table {
  width: 18%;
}

.col-column {
  width: 14%;
}

.col-business-name {
  width: 16%;
}

.col-description {
  width: 24%;
  max-width: 240px;
}

.col-description span {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-type {
  width: 10%;
}

.col-status {
  width: 8%;
}

.col-action {
  width: 10%;
  text-align: right;
}

.tbl-name {
  color: #1d2129;
  font-weight: 500;
}

.col-name {
  color: #1d2129;
  font-weight: 400;
}

.type-tag {
  color: #0fc6c2;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.status-badge {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-badge.enabled {
  background: #e8ffea;
  color: #00b42a;
}

.status-badge.disabled {
  background: #f2f3f5;
  color: #c9cdd4;
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
  background: #f2f3f5;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-top: 1px solid #e5e6eb;
}

.page-info {
  font-size: 12px;
  color: #86909c;
}

.page-total {
  font-weight: 600;
  color: #4e5969;
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
  background: #fff;
  border-radius: 8px;
  width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 6px 30px rgba(0, 0, 0, 0.12);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e6eb;
}

.dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 16px;
  color: #86909c;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
}

.dialog-close:hover {
  color: #1d2129;
  background: #f2f3f5;
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
  color: #4e5969;
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
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #165dff;
}

.form-input:disabled {
  background: #f7f8fa;
  color: #c9cdd4;
}

.form-select {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
  box-sizing: border-box;
  background: #fff;
  cursor: pointer;
}

.form-select:focus {
  border-color: #165dff;
}

.form-select:disabled {
  background: #f7f8fa;
  color: #c9cdd4;
}

.form-textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
  resize: vertical;
  box-sizing: border-box;
}

.form-textarea:focus {
  border-color: #165dff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e5e6eb;
}

.btn-cancel {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-cancel:hover {
  border-color: #165dff;
  color: #165dff;
}

.btn-confirm {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-confirm:hover:not(:disabled) {
  background: #0e42d2;
}

.btn-confirm:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
