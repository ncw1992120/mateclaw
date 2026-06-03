<template>
  <div class="datasource-page">
    <!-- 数据源选择/配置页面 -->
    <DatasourceSelect v-if="showSelectPage" :edit-id="editingDsId" @back="handleBackFromSelect" />

    <!-- 表详情页面 -->
    <TableDetail v-else-if="showTableDetail" :ds-id="detailDsId" :table-id="detailTableId" @back="handleBackFromDetail" />

    <template v-else>
      <!-- 加载中 -->
      <div v-if="loading && datasources.length === 0" class="page-loading">
        <span>{{ t('datasourcePage.loading') }}</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading && datasources.length === 0" class="empty-section">
        <div class="empty-icon-wrapper">
          <span class="empty-folder-icon">📁</span>
          <span class="empty-badge">📊</span>
        </div>
        <p class="empty-desc">{{ t('datasourcePage.emptyDesc') }}</p>
        <a class="recover-link" @click="handleRecover">
          {{ t('datasourcePage.recoverHint') }}
        </a>
      </div>

      <!-- 主从布局 -->
      <div v-else class="master-detail-layout">
        <!-- 左侧：数据源列表 -->
        <aside class="ds-sidebar">
          <div class="sidebar-header">
            <h1 class="sidebar-title">{{ t('datasourcePage.title') }}</h1>
            <button class="btn-create" @click="handleCreateDatasource">
              {{ t('datasourcePage.createDatasource') }}
            </button>
          </div>

          <div class="tab-bar">
            <span class="tab-item active">{{ t('datasourcePage.tabMyDs') }}</span>
            <span class="tab-item">{{ t('datasourcePage.tabTemplate') }}</span>
            <button class="search-icon-btn" title="">🔍</button>
          </div>

          <div class="ds-list-scroll">
            <div
              v-for="ds in datasources"
              :key="ds.id"
              class="ds-list-item"
              :class="{ active: selectedDsId === ds.id, disabled: !ds.enabled }"
              @click="handleSelectDs(ds)"
            >
              <span class="item-icon">{{ getDsIcon(ds.sourceType) }}</span>
              <div class="item-info">
                <span class="item-name">{{ ds.name }}</span>
                <span class="item-type">{{ ds.sourceType || t('datasourcePage.labelType') }}</span>
              </div>
              <div class="item-actions" @click.stop>
                <button class="item-action-btn" :title="t('datasourcePage.actionEdit')" @click="handleEdit(ds)">✏️</button>
                <button class="item-action-btn" :title="t('datasourcePage.actionToggle')" @click="handleToggle(ds)">{{ ds.enabled ? '⏸️' : '▶️' }}</button>
                <button class="item-action-btn" :disabled="testingId === ds.id" :title="t('datasourcePage.actionTest')" @click="handleTest(ds)">{{ testingId === ds.id ? '⏳' : '🔌' }}</button>
                <button class="item-action-btn" :disabled="syncingId === ds.id" :title="t('datasourcePage.actionSync')" @click="handleSync(ds)">{{ syncingId === ds.id ? '⏳' : '🔄' }}</button>
              </div>
            </div>
          </div>

          <div class="sidebar-footer">
            <span class="footer-hint">{{ t('datasourcePage.sidebarFooter') }}</span>
          </div>
        </aside>

        <!-- 右侧：数据表列表 -->
        <main class="tables-panel">
          <div class="panel-toolbar">
            <div class="toolbar-left">
              <span class="tool-tab active">{{ t('datasourcePage.tabTables') }}</span>
              <span class="tool-tab">{{ t('datasourcePage.tabUpload') }}</span>
            </div>
            <div class="toolbar-right">
              <input v-model="tableKeyword" class="table-search-input" :placeholder="t('datasourcePage.searchTable')" />
              <button class="tool-btn">{{ t('datasourcePage.btnSqlCreate') }}</button>
              <button class="tool-btn">{{ t('datasourcePage.btnUploadFile') }}</button>
            </div>
          </div>

          <div v-if="tablesLoading" class="panel-loading">
            <span>{{ t('datasourcePage.loading') }}</span>
          </div>

          <div v-else-if="filteredTables.length === 0" class="panel-empty">
            <p>{{ t('datasourcePage.noTables') }}</p>
          </div>

          <div v-else class="table-list-wrapper">
            <div class="table-grid-scroll">
              <table class="table-grid">
                <thead>
                  <tr>
                    <th class="col-expand"></th>
                    <th class="col-name">{{ t('datasourcePage.colName') }}<span class="required-mark">*</span></th>
                    <th class="col-comment">{{ t('datasourcePage.colComment') }}<span class="required-mark">*</span></th>
                    <th class="col-rows">{{ t('datasourcePage.colRows') }}</th>
                    <th class="col-action">{{ t('datasourcePage.colAction') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="table in filteredTables" :key="table.id">
                    <!-- 表行 -->
                    <tr class="table-row" :class="{ expanded: expandedTableId === table.id }">
                      <td class="col-expand">
                        <button class="expand-btn" :class="{ rotated: expandedTableId === table.id }" @click="handleToggleExpand(table)">
                          ▶
                        </button>
                      </td>
                      <td class="col-name">
                        <span class="tbl-name">{{ table.tableName }}</span>
                      </td>
                      <td class="col-comment">
                        <span class="tbl-comment">{{ table.tableComment || '-' }}</span>
                      </td>
                      <td class="col-rows">
                        <span class="tbl-rows">{{ table.rowCount ?? '-' }}</span>
                      </td>
                      <td class="col-action">
                        <div class="row-actions">
                          <button class="icon-btn" :title="t('datasourcePage.actionPreview')" @click="handlePreview(table)">👁️</button>
                          <button class="icon-btn" :title="t('datasourcePage.actionViewColumns')" @click="handleEditTable(table)">✏️</button>
                          <button class="icon-btn" :title="t('datasourcePage.actionDelete')" @click="handleDeleteTable(table)">🗑️</button>
                        </div>
                      </td>
                    </tr>
                    <!-- 展开行：字段信息 -->
                    <tr v-if="expandedTableId === table.id" class="expand-row">
                      <td colspan="5" class="expand-cell">
                        <div class="columns-panel">
                          <div v-if="columnsLoading" class="columns-loading">{{ t('datasourcePage.loading') }}</div>
                          <div v-else-if="expandedColumns.length === 0" class="columns-empty">{{ t('datasourcePage.noColumns') }}</div>
                          <table v-else class="columns-grid">
                            <thead>
                              <tr>
                                <th>{{ t('datasourcePage.colColName') }}</th>
                                <th>{{ t('datasourcePage.colColType') }}</th>
                                <th>{{ t('datasourcePage.colColComment') }}</th>
                                <th>{{ t('datasourcePage.colColPk') }}</th>
                                <th>{{ t('datasourcePage.colColNullable') }}</th>
                                <th>{{ t('datasourcePage.colColDefault') }}</th>
                              </tr>
                            </thead>
                            <tbody>
                              <tr v-for="col in expandedColumns" :key="col.id">
                                <td class="col-col-name">
                                  <span v-if="col.primaryKey" class="pk-badge">PK</span>
                                  {{ col.columnName }}
                                </td>
                                <td>{{ col.dataType }}<span v-if="col.columnSize">({{ col.columnSize }}<span v-if="col.decimalDigits">,{{ col.decimalDigits }}</span>)</span></td>
                                <td>{{ col.columnComment || '-' }}</td>
                                <td class="col-center">{{ col.primaryKey ? '✔️' : '' }}</td>
                                <td class="col-center">{{ col.nullable ? '✔️' : '✖️' }}</td>
                                <td>{{ col.defaultValue || '-' }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>

            <div class="pagination-bar">
              <div class="page-info">
                <span class="page-total">{{ filteredTables.length }}</span>
              </div>
              <div class="page-nav">
                <button class="page-btn active">1</button>
                <button class="page-btn next">&gt;</button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import * as datasourceApi from '@/api/datasource'
import type { Datasource, DatasourceTable, DatasourceColumn } from '@/types'
import DatasourceSelect from './datasource/DatasourceSelect.vue'
import TableDetail from './datasource/TableDetail.vue'

const { t } = useI18n()
const store = useDatasourceStore()
const { datasources, loading, currentTables } = storeToRefs(store)

const showSelectPage = ref(false)
const editingDsId = ref('')
const selectedDsId = ref('')
const tablesLoading = ref(false)
const tableKeyword = ref('')
const testingId = ref<string | null>(null)
const syncingId = ref<string | null>(null)

/** 表详情视图状态 */
const showTableDetail = ref(false)
const detailDsId = ref('')
const detailTableId = ref('')

/** 当前展开的表ID */
const expandedTableId = ref<string | null>(null)
/** 展开表的字段列表 */
const expandedColumns = ref<DatasourceColumn[]>([])
/** 字段加载中 */
const columnsLoading = ref(false)

/** 过滤后的表列表 */
const filteredTables = computed(() => {
  const keyword = tableKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return currentTables.value
  }
  return currentTables.value.filter(
    (tbl) =>
      tbl.tableName.toLowerCase().includes(keyword) ||
      (tbl.tableComment || '').toLowerCase().includes(keyword)
  )
})

onMounted(() => {
  store.fetchDatasources()
})

/** 选中数据源后自动加载表 */
watch(selectedDsId, async (newId) => {
  if (newId) {
    tablesLoading.value = true
    expandedTableId.value = null
    expandedColumns.value = []
    try {
      await store.fetchTables(newId)
    } finally {
      tablesLoading.value = false
    }
  }
})

/** 跳转到新建数据源 */
function handleCreateDatasource(): void {
  editingDsId.value = ''
  showSelectPage.value = true
}

/** 编辑数据源 */
function handleEdit(ds: Datasource): void {
  editingDsId.value = ds.id
  showSelectPage.value = true
}

/** 从选择页返回 */
function handleBackFromSelect(): void {
  showSelectPage.value = false
  editingDsId.value = ''
  store.fetchDatasources()
}

/** 恢复已删除数据源 */
function handleRecover(): void {
}

/** 选中左侧数据源项 */
function handleSelectDs(ds: Datasource): void {
  if (selectedDsId.value !== ds.id) {
    selectedDsId.value = ds.id
  }
}

/** 测试连接 */
async function handleTest(ds: Datasource): Promise<void> {
  testingId.value = ds.id
  try {
    const result = await datasourceApi.testConnection(ds.id)
    if (result) {
      ElMessage.success(t('datasourcePage.testSuccess'))
    } else {
      ElMessage.error(t('datasourcePage.testFail'))
    }
    store.fetchDatasources()
  } catch {
    ElMessage.error(t('datasourcePage.testFail'))
  } finally {
    testingId.value = null
  }
}

/** 启停切换 */
async function handleToggle(ds: Datasource): Promise<void> {
  try {
    await datasourceApi.toggle(ds.id, !ds.enabled)
    ElMessage.success(t('datasourcePage.toggleSuccess'))
    store.fetchDatasources()
  } catch {
    // error handled by interceptor
  }
}

/** 同步数据表（触发 Schema 发现） */
async function handleSync(ds: Datasource): Promise<void> {
  syncingId.value = ds.id
  try {
    await datasourceApi.triggerSchemaDiscovery(ds.id)
    ElMessage.success(t('datasourcePage.syncSuccess'))
    if (selectedDsId.value === ds.id) {
      await store.fetchTables(ds.id)
    }
    store.fetchDatasources()
  } catch {
    ElMessage.error(t('datasourcePage.syncFail'))
  } finally {
    syncingId.value = null
  }
}

/** 展开/收起表字段信息 */
async function handleToggleExpand(table: DatasourceTable): Promise<void> {
  if (expandedTableId.value === table.id) {
    expandedTableId.value = null
    expandedColumns.value = []
    return
  }
  expandedTableId.value = table.id
  if (!selectedDsId.value) {
    return
  }
  columnsLoading.value = true
  try {
    const cols = await datasourceApi.listColumns(selectedDsId.value, table.id)
    expandedColumns.value = (cols || []) as unknown as DatasourceColumn[]
  } catch {
    expandedColumns.value = []
  } finally {
    columnsLoading.value = false
  }
}

/** 根据类型获取图标 */
function getDsIcon(sourceType: string): string {
  const iconMap: Record<string, string> = {
    mysql: '🐬',
    postgresql: '🐘',
    oracle: '🔴',
    sqlserver: '🔵',
    clickhouse: '🔔',
    mongodb: '🍃',
    elasticsearch: '🔍',
  }
  return iconMap[sourceType?.toLowerCase()] || '💾'
}

/** 预览表（展开行） */
function handlePreview(table: DatasourceTable): void {
  handleToggleExpand(table)
}

/** 编辑表（切换到表详情视图） */
function handleEditTable(table: DatasourceTable): void {
  if (!selectedDsId.value) {
    return
  }
  detailDsId.value = selectedDsId.value
  detailTableId.value = table.id
  showTableDetail.value = true
}

/** 从表详情视图返回 */
function handleBackFromDetail(): void {
  showTableDetail.value = false
  detailDsId.value = ''
  detailTableId.value = ''
  if (selectedDsId.value) {
    store.fetchTables(selectedDsId.value)
  }
}

/** 删除表 */
async function handleDeleteTable(table: DatasourceTable): Promise<void> {
  if (!selectedDsId.value) {
    return
  }
  try {
    await ElMessageBox.confirm(t('datasourcePage.deleteTableConfirm'), '', {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await datasourceApi.deleteTable(selectedDsId.value, table.id)
    ElMessage.success(t('datasourcePage.deleteSuccess'))
    if (expandedTableId.value === table.id) {
      expandedTableId.value = null
      expandedColumns.value = []
    }
    store.fetchTables(selectedDsId.value)
  } catch {
    // cancel or error
  }
}
</script>

<style scoped>
.datasource-page {
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  overflow-y: auto;
}

.page-loading,
.panel-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 400px;
  color: #86909c;
  font-size: 14px;
}

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
}

.empty-icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.empty-folder-icon {
  font-size: 72px;
  opacity: 0.6;
}

.empty-badge {
  position: absolute;
  bottom: 4px;
  right: 0;
  font-size: 28px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.15));
}

.empty-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0 0 20px 0;
  text-align: center;
  max-width: 320px;
  line-height: 1.6;
}

.recover-link {
  font-size: 13px;
  color: #86909c;
  cursor: pointer;
  transition: color 0.2s;
}

.recover-link:hover {
  color: #165dff;
}

/* ========== 主从布局 ========== */
.master-detail-layout {
  display: flex;
  height: calc(100vh - 56px);
  gap: 0;
}

/* ========== 左侧：数据源侧边栏 ========== */
.ds-sidebar {
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-right: 1px solid #e5e6eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px 20px;
  border-bottom: 1px solid #f2f3f5;
}

.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.btn-create {
  height: 30px;
  padding: 0 14px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create:hover {
  background: #0e42d2;
}

.tab-bar {
  display: flex;
  align-items: center;
  padding: 10px 16px 0 16px;
  gap: 4px;
}

.tab-item {
  font-size: 13px;
  color: #86909c;
  padding: 4px 10px;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
}

.tab-item.active {
  color: #1d2129;
  font-weight: 500;
}

.tab-item:hover:not(.active) {
  color: #4e5969;
}

.search-icon-btn {
  margin-left: auto;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  opacity: 0.55;
  transition: opacity 0.15s;
}

.search-icon-btn:hover {
  opacity: 1;
}

.ds-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.ds-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}

.ds-list-item:hover {
  background: #f2f3f5;
}

.ds-list-item.active {
  background: #e8f3ff;
  border-left-color: #165dff;
}

.ds-list-item.disabled {
  opacity: 0.55;
}

.item-icon {
  font-size: 22px;
  flex-shrink: 0;
  line-height: 1;
}

.item-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-type {
  font-size: 11.5px;
  color: #c9cdd4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-actions {
  display: none;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  flex-shrink: 0;
}

.ds-list-item:hover .item-actions {
  display: flex;
}

.item-action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  padding: 2px 4px;
  border-radius: 3px;
  opacity: 0.65;
  transition: all 0.15s;
  line-height: 1;
}

.item-action-btn:hover:not(:disabled) {
  opacity: 1;
  background: #e5e6eb;
}

.item-action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.sidebar-footer {
  padding: 10px 16px;
  border-top: 1px solid #f2f3f5;
}

.footer-hint {
  font-size: 11px;
  color: #c9cdd4;
}

/* ========== 右侧：数据表面板 ========== */
.tables-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
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
  gap: 4px;
}

.tool-tab {
  font-size: 13px;
  color: #86909c;
  padding: 4px 12px;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
}

.tool-tab.active {
  color: #165dff;
  font-weight: 500;
}

.tool-tab:hover:not(.active) {
  color: #4e5969;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-search-input {
  height: 30px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  width: 180px;
  font-family: inherit;
  transition: border-color 0.2s;
}

.table-search-input:focus {
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

.tool-btn:hover {
  border-color: #165dff;
  color: #165dff;
}

.panel-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #c9cdd4;
  font-size: 13px;
}

/* 表格区域 */
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

.table-grid {
  width: 100%;
  border-collapse: collapse;
}

.table-grid thead tr {
  background: #fafafa;
}

.table-grid th {
  padding: 11px 16px;
  text-align: left;
  font-size: 12.5px;
  font-weight: 500;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  white-space: nowrap;
}

.col-expand {
  width: 36px;
  text-align: center;
}

.col-name {
  width: 35%;
}

.col-comment {
  width: 35%;
}

.col-rows {
  width: 10%;
  text-align: right;
}

.col-action {
  width: 15%;
  text-align: right;
}

.required-mark {
  color: #f53f3f;
  margin-left: 2px;
}

.table-grid tbody tr.table-row {
  transition: background 0.15s;
}

.table-grid tbody tr.table-row:hover {
  background: #fafbfc;
}

.table-grid tbody tr.table-row.expanded {
  background: #f0f7ff;
}

.table-grid td {
  padding: 10px 16px;
  font-size: 13px;
  color: #4e5969;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
}

/* 展开按钮 */
.expand-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 10px;
  color: #86909c;
  transition: transform 0.2s, color 0.15s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 3px;
}

.expand-btn:hover {
  color: #165dff;
  background: #e8f3ff;
}

.expand-btn.rotated {
  transform: rotate(90deg);
  color: #165dff;
}

.tbl-name {
  color: #1d2129;
  font-weight: 400;
}

.tbl-comment {
  color: #86909c;
}

.tbl-rows {
  color: #86909c;
  font-size: 12px;
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

/* ========== 展开行：字段信息 ========== */
.expand-row td {
  padding: 0;
  border-bottom: 1px solid #e5e6eb;
  background: #fafbfc;
}

.expand-cell {
  padding: 0;
}

.columns-panel {
  padding: 12px 24px 16px 56px;
}

.columns-loading,
.columns-empty {
  padding: 16px 0;
  color: #86909c;
  font-size: 13px;
  text-align: center;
}

.columns-grid {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  overflow: hidden;
}

.columns-grid thead tr {
  background: #f7f8fa;
}

.columns-grid th {
  padding: 8px 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 500;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  white-space: nowrap;
}

.columns-grid td {
  padding: 7px 12px;
  font-size: 12.5px;
  color: #4e5969;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
}

.columns-grid tbody tr:hover {
  background: #f7f8fa;
}

.columns-grid tbody tr:last-child td {
  border-bottom: none;
}

.col-col-name {
  font-weight: 500;
  color: #1d2129;
}

.col-center {
  text-align: center;
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

/* 分页 */
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

.page-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.page-btn {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #4e5969;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.page-btn:hover {
  border-color: #165dff;
  color: #165dff;
}

.page-btn.active {
  background: #165dff;
  border-color: #165dff;
  color: #fff;
}

.page-dots {
  color: #c9cdd4;
  font-size: 12px;
  padding: 0 4px;
}
</style>
