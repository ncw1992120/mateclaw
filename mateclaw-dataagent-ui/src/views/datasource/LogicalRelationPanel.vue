<template>
  <div class="logical-relation-panel">
    <div class="panel-toolbar">
      <div class="toolbar-left">
        <input v-model="keyword" class="search-input" :placeholder="t('logicalRelation.searchPlaceholder')" @keyup.enter="handleSearch" />
      </div>
      <div class="toolbar-right">
        <button class="tool-btn" :disabled="autoIniting" @click="handleAutoInit">
          <span v-if="autoIniting">⏳</span>
          <span v-else>📋</span>
          {{ autoIniting ? t('logicalRelation.autoIniting') : t('logicalRelation.autoInit') }}
        </button>
        <button class="tool-btn primary" @click="handleCreate">
          ＋ {{ t('logicalRelation.create') }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="panel-loading">
      <span>{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="relations.length === 0" class="panel-empty">
      <p>{{ t('logicalRelation.emptyDesc') }}</p>
      <button class="tool-btn primary" @click="handleAutoInit">{{ t('logicalRelation.autoInit') }}</button>
    </div>

    <div v-else class="table-list-wrapper">
      <div class="table-grid-scroll">
        <table class="data-grid">
          <thead>
            <tr>
              <th class="col-source">{{ t('logicalRelation.colSource') }}</th>
              <th class="col-arrow"></th>
              <th class="col-target">{{ t('logicalRelation.colTarget') }}</th>
              <th class="col-type">{{ t('logicalRelation.colType') }}</th>
              <th class="col-description">{{ t('logicalRelation.colDescription') }}</th>
              <th class="col-action">{{ t('datasourcePage.colAction') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rel in filteredRelations" :key="rel.id">
              <td class="col-source">
                <span class="table-name">{{ rel.sourceTableName }}</span>
                <span class="dot">.</span>
                <span class="column-name">{{ rel.sourceColumnName }}</span>
              </td>
              <td class="col-arrow">
                <span class="arrow-icon">→</span>
              </td>
              <td class="col-target">
                <span class="table-name">{{ rel.targetTableName }}</span>
                <span class="dot">.</span>
                <span class="column-name">{{ rel.targetColumnName }}</span>
              </td>
              <td class="col-type">
                <span class="type-badge">{{ rel.relationType || '-' }}</span>
              </td>
              <td class="col-description">
                <span :title="rel.description">{{ rel.description || '-' }}</span>
              </td>
              <td class="col-action">
                <div class="row-actions">
                  <button class="icon-btn" :title="t('logicalRelation.actionEdit')" @click="handleEdit(rel)">✏️</button>
                  <button class="icon-btn" :title="t('logicalRelation.actionDelete')" @click="handleDelete(rel)">🗑️</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <div class="page-info">
          <span class="page-total">{{ filteredRelations.length }}</span> {{ t('logicalRelation.totalUnit') }}
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog-card">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ isEditing ? t('logicalRelation.editTitle') : t('logicalRelation.createTitle') }}</h3>
          <button class="dialog-close" @click="showDialog = false">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.fieldSourceTable') }}<span class="required">*</span></label>
              <select v-model="formData.sourceTableName" :disabled="isEditing" class="form-select" @change="handleSourceTableChange">
                <option value="" disabled>{{ t('logicalRelation.tablePlaceholder') }}</option>
                <option v-for="tbl in tableOptions" :key="tbl.id" :value="tbl.tableName">
                  {{ tbl.tableName }}
                </option>
              </select>
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.fieldSourceColumn') }}<span class="required">*</span></label>
              <select v-model="formData.sourceColumnName" :disabled="isEditing || !formData.sourceTableName" class="form-select">
                <option value="" disabled>{{ sourceColumnOptions.length === 0 ? t('logicalRelation.selectTableFirst') : t('logicalRelation.columnPlaceholder') }}</option>
                <option v-for="col in sourceColumnOptions" :key="col.id" :value="col.columnName">
                  {{ col.columnName }}
                </option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.fieldTargetTable') }}<span class="required">*</span></label>
              <select v-model="formData.targetTableName" :disabled="isEditing" class="form-select" @change="handleTargetTableChange">
                <option value="" disabled>{{ t('logicalRelation.tablePlaceholder') }}</option>
                <option v-for="tbl in tableOptions" :key="tbl.id" :value="tbl.tableName">
                  {{ tbl.tableName }}
                </option>
              </select>
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.fieldTargetColumn') }}<span class="required">*</span></label>
              <select v-model="formData.targetColumnName" :disabled="isEditing || !formData.targetTableName" class="form-select">
                <option value="" disabled>{{ targetColumnOptions.length === 0 ? t('logicalRelation.selectTableFirst') : t('logicalRelation.columnPlaceholder') }}</option>
                <option v-for="col in targetColumnOptions" :key="col.id" :value="col.columnName">
                  {{ col.columnName }}
                </option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.colType') }}</label>
              <select v-model="formData.relationType" class="form-select">
                <option value="1:1">1:1</option>
                <option value="1:N">1:N</option>
                <option value="N:1">N:1</option>
              </select>
            </div>
            <div class="form-group half">
              <label class="form-label">{{ t('logicalRelation.colDescription') }}</label>
              <input v-model="formData.description" class="form-input" :placeholder="t('logicalRelation.descriptionPlaceholder')" />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as logicalRelationApi from '@/api/logical-relation'
import * as datasourceApi from '@/api/datasource'
import type { LogicalRelation, LogicalRelationCreateRequest, LogicalRelationUpdateRequest, DatasourceTable, DatasourceColumn } from '@/types'

const props = defineProps<{
  datasourceId: string
  tables: DatasourceTable[]
}>()

const { t } = useI18n()

const loading = ref(false)
const autoIniting = ref(false)
const relations = ref<LogicalRelation[]>([])
const keyword = ref('')

/** 弹窗状态 */
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const submitting = ref(false)

/** 表/字段下拉选项 */
const tableOptions = ref<DatasourceTable[]>([])
const sourceColumnOptions = ref<DatasourceColumn[]>([])
const targetColumnOptions = ref<DatasourceColumn[]>([])
const loadingColumns = ref(false)

/** 表单数据 */
const formData = ref<LogicalRelationCreateRequest>({
  datasourceId: '',
  sourceTableName: '',
  sourceColumnName: '',
  targetTableName: '',
  targetColumnName: '',
  relationType: '1:N',
  description: '',
})

/** 关键词过滤 */
const filteredRelations = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return relations.value
  }
  return relations.value.filter((rel) => {
    return (
      rel.sourceTableName.toLowerCase().includes(kw) ||
      rel.sourceColumnName.toLowerCase().includes(kw) ||
      rel.targetTableName.toLowerCase().includes(kw) ||
      rel.targetColumnName.toLowerCase().includes(kw) ||
      (rel.description || '').toLowerCase().includes(kw)
    )
  })
})

onMounted(() => {
  loadRelations()
})

watch(() => props.datasourceId, () => {
  loadRelations()
  loadTableOptions()
})

/** 加载逻辑外键关系列表 */
async function loadRelations(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loading.value = true
  try {
    const data = await logicalRelationApi.list(props.datasourceId)
    relations.value = (data || []) as unknown as LogicalRelation[]
  } catch {
    relations.value = []
  } finally {
    loading.value = false
  }
}

/** 关键词搜索（前端过滤） */
function handleSearch(): void {
  // 前端过滤，无需额外 API 调用
}

/** 自动初始化 */
async function handleAutoInit(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  autoIniting.value = true
  try {
    const count = await logicalRelationApi.autoInit(props.datasourceId)
    ElMessage.success(t('logicalRelation.autoInitSuccess', { count }))
    loadRelations()
  } catch {
    ElMessage.error(t('logicalRelation.autoInitFail'))
  } finally {
    autoIniting.value = false
  }
}

/** 新建 */
function handleCreate(): void {
  isEditing.value = false
  editingId.value = ''
  formData.value = {
    datasourceId: props.datasourceId,
    sourceTableName: '',
    sourceColumnName: '',
    targetTableName: '',
    targetColumnName: '',
    relationType: '1:N',
    description: '',
  }
  sourceColumnOptions.value = []
  targetColumnOptions.value = []
  loadTableOptions()
  showDialog.value = true
}

/** 编辑 */
function handleEdit(rel: LogicalRelation): void {
  isEditing.value = true
  editingId.value = rel.id
  formData.value = {
    datasourceId: props.datasourceId,
    sourceTableName: rel.sourceTableName,
    sourceColumnName: rel.sourceColumnName,
    targetTableName: rel.targetTableName,
    targetColumnName: rel.targetColumnName,
    relationType: rel.relationType || '1:N',
    description: rel.description || '',
  }
  loadTableOptions()
  loadSourceColumns(rel.sourceTableName)
  loadTargetColumns(rel.targetTableName)
  showDialog.value = true
}

/** 提交表单 */
async function handleSubmit(): Promise<void> {
  if (!formData.value.sourceTableName || !formData.value.sourceColumnName ||
      !formData.value.targetTableName || !formData.value.targetColumnName) {
    ElMessage.warning(t('logicalRelation.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEditing.value) {
      const updateData: LogicalRelationUpdateRequest = {
        relationType: formData.value.relationType,
        description: formData.value.description,
      }
      await logicalRelationApi.update(editingId.value, updateData)
      ElMessage.success(t('logicalRelation.updateSuccess'))
    } else {
      formData.value.datasourceId = props.datasourceId
      await logicalRelationApi.create(formData.value)
      ElMessage.success(t('logicalRelation.createSuccess'))
    }
    showDialog.value = false
    loadRelations()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
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

/** 源表选择变化时，加载源字段列表 */
function handleSourceTableChange(): void {
  formData.value.sourceColumnName = ''
  sourceColumnOptions.value = []
  if (formData.value.sourceTableName) {
    loadSourceColumns(formData.value.sourceTableName)
  }
}

/** 目标表选择变化时，加载目标字段列表 */
function handleTargetTableChange(): void {
  formData.value.targetColumnName = ''
  targetColumnOptions.value = []
  if (formData.value.targetTableName) {
    loadTargetColumns(formData.value.targetTableName)
  }
}

/** 根据表名加载源字段列表 */
async function loadSourceColumns(tableName: string): Promise<void> {
  const selectedTable = tableOptions.value.find((tbl) => tbl.tableName === tableName)
  if (!selectedTable || !props.datasourceId) {
    return
  }
  loadingColumns.value = true
  try {
    const data = await datasourceApi.listColumns(props.datasourceId, selectedTable.id)
    sourceColumnOptions.value = (data || []) as unknown as DatasourceColumn[]
  } catch {
    sourceColumnOptions.value = []
  } finally {
    loadingColumns.value = false
  }
}

/** 根据表名加载目标字段列表 */
async function loadTargetColumns(tableName: string): Promise<void> {
  const selectedTable = tableOptions.value.find((tbl) => tbl.tableName === tableName)
  if (!selectedTable || !props.datasourceId) {
    return
  }
  loadingColumns.value = true
  try {
    const data = await datasourceApi.listColumns(props.datasourceId, selectedTable.id)
    targetColumnOptions.value = (data || []) as unknown as DatasourceColumn[]
  } catch {
    targetColumnOptions.value = []
  } finally {
    loadingColumns.value = false
  }
}

/** 删除 */
async function handleDelete(rel: LogicalRelation): Promise<void> {
  try {
    await ElMessageBox.confirm(t('logicalRelation.deleteConfirm'), '', {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await logicalRelationApi.remove(rel.id)
    ElMessage.success(t('logicalRelation.deleteSuccess'))
    loadRelations()
  } catch {
    // cancel or error
  }
}
</script>

<style scoped>
.logical-relation-panel {
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

.col-source {
  width: 25%;
}

.col-arrow {
  width: 40px;
  text-align: center;
}

.col-target {
  width: 25%;
}

.col-type {
  width: 10%;
}

.col-description {
  width: 22%;
  max-width: 200px;
}

.col-description span {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-action {
  width: 10%;
  text-align: right;
}

.table-name {
  color: #1d2129;
  font-weight: 500;
}

.dot {
  color: #c9cdd4;
  margin: 0 2px;
}

.column-name {
  color: #4e5969;
}

.arrow-icon {
  color: #165dff;
  font-weight: 600;
  font-size: 16px;
}

.type-badge {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #e8f3ff;
  color: #165dff;
  font-weight: 500;
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
  width: 520px;
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
