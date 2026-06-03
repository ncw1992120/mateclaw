<template>
  <div class="table-detail-page">
    <div class="detail-header">
      <button class="back-btn" @click="handleBack">
        <span class="back-arrow">&larr;</span>
        {{ t('tableDetail.backToList') }}
      </button>
      <div class="header-info">
        <h1 class="table-title">{{ tableInfo?.tableName || '-' }}</h1>
        <span class="table-meta">{{ tableInfo?.tableComment || '' }}</span>
      </div>
    </div>

    <div v-if="pageLoading" class="page-loading">
      <span>{{ t('tableDetail.loading') }}</span>
    </div>

    <div v-else class="detail-body">
      <div class="detail-left">
        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">{{ t('tableDetail.fieldInfo') }}</h2>
            <span class="field-count">{{ columns.length }} {{ t('tableDetail.fieldUnit') }}</span>
          </div>
          <div v-if="columnsLoading" class="section-loading">{{ t('tableDetail.loading') }}</div>
          <div v-else-if="columns.length === 0" class="section-empty">{{ t('tableDetail.noColumns') }}</div>
          <table v-else class="columns-table">
            <thead>
              <tr>
                <th class="col-ordinal">#</th>
                <th class="col-name">{{ t('tableDetail.colName') }}</th>
                <th class="col-type">{{ t('tableDetail.colType') }}</th>
                <th class="col-comment">{{ t('tableDetail.colComment') }}</th>
                <th class="col-pk">{{ t('tableDetail.colPk') }}</th>
                <th class="col-nullable">{{ t('tableDetail.colNullable') }}</th>
                <th class="col-default">{{ t('tableDetail.colDefault') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="col in columns" :key="col.id">
                <td class="col-ordinal">{{ col.ordinalPosition }}</td>
                <td class="col-name">
                  <span v-if="col.primaryKey" class="pk-badge">PK</span>
                  {{ col.columnName }}
                </td>
                <td class="col-type">
                  {{ col.dataType }}<span v-if="col.columnSize">({{ col.columnSize }}<span v-if="col.decimalDigits">,{{ col.decimalDigits }}</span>)</span>
                </td>
                <td class="col-comment">{{ col.columnComment || '-' }}</td>
                <td class="col-center">{{ col.primaryKey ? '✔' : '' }}</td>
                <td class="col-center">{{ col.nullable ? '✔' : '✖' }}</td>
                <td class="col-default">{{ col.defaultValue || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="section-card">
          <div class="section-header">
            <h2 class="section-title">{{ t('tableDetail.dataPreview') }}</h2>
            <span v-if="previewData" class="field-count">{{ previewData.total }} {{ t('tableDetail.rowUnit') }}</span>
          </div>
          <div v-if="previewLoading" class="section-loading">{{ t('tableDetail.loading') }}</div>
          <div v-else-if="!previewData || previewData.rows.length === 0" class="section-empty">{{ t('tableDetail.noData') }}</div>
          <div v-else class="preview-wrapper">
            <div class="preview-scroll">
              <table class="preview-table">
                <thead>
                  <tr>
                    <th class="col-ordinal">#</th>
                    <th v-for="col in previewData.columns" :key="col">{{ col }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, idx) in previewData.rows" :key="idx">
                    <td class="col-ordinal">{{ idx + 1 }}</td>
                    <td v-for="col in previewData.columns" :key="col">{{ row[col] ?? '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <aside class="detail-right">
        <div class="sync-panel">
          <h2 class="sync-title">{{ t('tableDetail.syncTitle') }}</h2>
          <p class="sync-desc">{{ t('tableDetail.syncDesc') }}</p>

          <div class="sync-mode-group">
            <label class="sync-mode-item" :class="{ active: syncMode === 'append' }">
              <input type="radio" v-model="syncMode" value="append" class="sync-radio" />
              <div class="mode-card">
                <span class="mode-icon">📥</span>
                <div class="mode-info">
                  <span class="mode-label">{{ t('tableDetail.modeAppend') }}</span>
                  <span class="mode-hint">{{ t('tableDetail.modeAppendHint') }}</span>
                </div>
              </div>
            </label>
            <label class="sync-mode-item" :class="{ active: syncMode === 'overwrite' }">
              <input type="radio" v-model="syncMode" value="overwrite" class="sync-radio" />
              <div class="mode-card">
                <span class="mode-icon">🔄</span>
                <div class="mode-info">
                  <span class="mode-label">{{ t('tableDetail.modeOverwrite') }}</span>
                  <span class="mode-hint">{{ t('tableDetail.modeOverwriteHint') }}</span>
                </div>
              </div>
            </label>
          </div>

          <div class="sync-section">
            <h3 class="sync-section-title">{{ t('tableDetail.syncFieldLabel') }}</h3>
            <button class="sync-btn" :disabled="syncingField" @click="handleSyncField">
              <span v-if="syncingField" class="btn-loading">⏳</span>
              <span v-else>📋</span>
              {{ syncingField ? t('tableDetail.syncing') : t('tableDetail.syncFieldBtn') }}
            </button>
          </div>

          <div class="sync-section">
            <h3 class="sync-section-title">{{ t('tableDetail.syncDataLabel') }}</h3>
            <button class="sync-btn" :disabled="syncingData" @click="handleSyncData">
              <span v-if="syncingData" class="btn-loading">⏳</span>
              <span v-else>📊</span>
              {{ syncingData ? t('tableDetail.syncing') : t('tableDetail.syncDataBtn') }}
            </button>
          </div>

          <div class="sync-info">
            <div class="info-item">
              <span class="info-label">{{ t('tableDetail.lastSyncTime') }}</span>
              <span class="info-value">{{ tableInfo?.updateTime || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">{{ t('tableDetail.fieldCount') }}</span>
              <span class="info-value">{{ columns.length }}</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as datasourceApi from '@/api/datasource'
import type { DatasourceTable, DatasourceColumn } from '@/types'

const props = defineProps<{
  dsId: string
  tableId: string
}>()

const emit = defineEmits<{
  back: []
}>()

const { t } = useI18n()

const pageLoading = ref(true)
const columnsLoading = ref(false)
const previewLoading = ref(false)
const syncingField = ref(false)
const syncingData = ref(false)

const tableInfo = ref<DatasourceTable | null>(null)
const columns = ref<DatasourceColumn[]>([])
const previewData = ref<{ columns: string[]; rows: Record<string, unknown>[]; total: number } | null>(null)
const syncMode = ref('append')

onMounted(async () => {
  try {
    await loadTableDetail()
    await loadColumns()
    await loadPreview()
  } finally {
    pageLoading.value = false
  }
})

async function loadTableDetail(): Promise<void> {
  try {
    const detail = await datasourceApi.getTableDetail(props.dsId, props.tableId)
    tableInfo.value = detail as unknown as DatasourceTable
  } catch {
    tableInfo.value = null
  }
}

async function loadColumns(): Promise<void> {
  columnsLoading.value = true
  try {
    const cols = await datasourceApi.listColumns(props.dsId, props.tableId)
    columns.value = (cols || []) as unknown as DatasourceColumn[]
  } catch {
    columns.value = []
  } finally {
    columnsLoading.value = false
  }
}

async function loadPreview(): Promise<void> {
  previewLoading.value = true
  try {
    const data = await datasourceApi.previewTableData(props.dsId, props.tableId, 100)
    previewData.value = data as unknown as typeof previewData.value
  } catch {
    previewData.value = null
  } finally {
    previewLoading.value = false
  }
}

function handleBack(): void {
  emit('back')
}

async function handleSyncField(): Promise<void> {
  syncingField.value = true
  try {
    await datasourceApi.syncTable(props.dsId, props.tableId, syncMode.value)
    ElMessage.success(t('tableDetail.syncFieldSuccess'))
    await loadColumns()
    await loadTableDetail()
  } catch {
    ElMessage.error(t('tableDetail.syncFieldFail'))
  } finally {
    syncingField.value = false
  }
}

async function handleSyncData(): Promise<void> {
  syncingData.value = true
  try {
    await loadPreview()
    ElMessage.success(t('tableDetail.syncDataSuccess'))
  } catch {
    ElMessage.error(t('tableDetail.syncDataFail'))
  } finally {
    syncingData.value = false
  }
}
</script>

<style scoped>
.table-detail-page {
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: #165dff;
  font-size: 13px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 4px;
  transition: background 0.15s;
  font-family: inherit;
  white-space: nowrap;
}

.back-btn:hover {
  background: #e8f3ff;
}

.back-arrow {
  font-size: 16px;
  font-weight: 500;
}

.header-info {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
  white-space: nowrap;
}

.table-meta {
  font-size: 13px;
  color: #86909c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.page-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #86909c;
  font-size: 14px;
}

.detail-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  overflow: hidden;
}

.detail-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  overflow-y: auto;
}

.section-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #f2f3f5;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.field-count {
  font-size: 12px;
  color: #86909c;
}

.section-loading,
.section-empty {
  padding: 32px 0;
  text-align: center;
  color: #c9cdd4;
  font-size: 13px;
}

.columns-table,
.preview-table {
  width: 100%;
  border-collapse: collapse;
}

.columns-table thead tr,
.preview-table thead tr {
  background: #fafafa;
}

.columns-table th,
.preview-table th {
  padding: 10px 14px;
  text-align: left;
  font-size: 12px;
  font-weight: 500;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  white-space: nowrap;
}

.columns-table td,
.preview-table td {
  padding: 9px 14px;
  font-size: 12.5px;
  color: #4e5969;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
}

.columns-table tbody tr:hover,
.preview-table tbody tr:hover {
  background: #fafbfc;
}

.col-ordinal {
  width: 40px;
  text-align: center;
  color: #c9cdd4;
  font-size: 12px;
}

.col-name {
  font-weight: 500;
  color: #1d2129;
}

.col-type {
  white-space: nowrap;
  color: #0fc6c2;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.col-comment {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-pk,
.col-nullable {
  width: 50px;
  text-align: center;
}

.col-center {
  text-align: center;
}

.col-default {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pk-badge {
  display: inline-block;
  background: #e8f3ff;
  color: #165dff;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: middle;
}

.preview-wrapper {
  overflow: hidden;
}

.preview-scroll {
  overflow: auto;
  max-height: 400px;
}

.preview-table th,
.preview-table td {
  white-space: nowrap;
  min-width: 80px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-right {
  width: 300px;
  min-width: 300px;
  flex-shrink: 0;
}

.sync-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  padding: 20px;
  position: sticky;
  top: 0;
}

.sync-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 6px 0;
}

.sync-desc {
  font-size: 12px;
  color: #86909c;
  margin: 0 0 16px 0;
  line-height: 1.6;
}

.sync-mode-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.sync-mode-item {
  cursor: pointer;
}

.sync-radio {
  display: none;
}

.mode-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  transition: all 0.2s;
}

.sync-mode-item.active .mode-card {
  border-color: #165dff;
  background: #e8f3ff;
}

.mode-card:hover {
  border-color: #bedaff;
}

.mode-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.mode-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode-label {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
}

.mode-hint {
  font-size: 11px;
  color: #86909c;
}

.sync-section {
  margin-bottom: 16px;
}

.sync-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #4e5969;
  margin: 0 0 8px 0;
}

.sync-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  border-radius: 6px;
  border: 1px solid #165dff;
  background: #fff;
  color: #165dff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.sync-btn:hover:not(:disabled) {
  background: #165dff;
  color: #fff;
}

.sync-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-loading {
  display: inline-block;
}

.sync-info {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f2f3f5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-label {
  font-size: 12px;
  color: #86909c;
}

.info-value {
  font-size: 12px;
  color: #4e5969;
  font-weight: 500;
}
</style>
