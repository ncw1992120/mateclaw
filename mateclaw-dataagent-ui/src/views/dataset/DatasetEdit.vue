<template>
  <div class="dataset-edit-page">
    <!-- 头部栏 -->
    <header class="edit-header">
      <div class="header-left">
        <button class="back-btn" @click="handleBack">
          <span class="back-arrow">&lt;</span>
        </button>
        <h1 class="dataset-title">{{ datasetName || t('datasetEdit.untitled') }}</h1>
      </div>
      <div class="header-right">
        <template v-if="currentMode === 'config'">
          <button class="action-btn cancel-btn" @click="handleBack">{{ t('datasetEdit.cancel') }}</button>
          <button
            class="action-btn finish-btn"
            :disabled="!canSave"
            @click="handleFinish"
          >
            {{ t('datasetEdit.finishAndProcess') }}
          </button>
        </template>
        <template v-else-if="currentMode === 'preview'">
          <button class="action-btn save-btn" @click="handleSaveAll">{{ t('datasetEdit.save') }}</button>
          <div class="search-box">
            <input
              v-model="searchKeyword"
              type="text"
              :placeholder="t('datasetEdit.searchPlaceholder')"
              class="search-input"
            />
          </div>
          <button class="icon-btn more-btn" @click="handleMore">⋯</button>
        </template>
      </div>
    </header>

    <!-- 模式一：模型配置 -->
    <div v-if="currentMode === 'config'" class="edit-body config-mode">
      <!-- 左侧面板 -->
      <aside class="left-panel config-panel">
        <div class="panel-section">
          <h2 class="panel-title">{{ t('datasetEdit.modelConfig') }}</h2>

          <!-- 数据集名称 -->
          <div class="section-block">
            <label class="section-label">{{ t('datasetEdit.datasetName') }}</label>
            <input
              v-model="datasetName"
              type="text"
              class="name-input"
              :placeholder="t('datasetEdit.datasetNamePlaceholder')"
            />
          </div>

          <!-- 数据源区域 -->
          <div class="section-block">
            <label class="section-label">{{ t('datasetEdit.datasource') }}</label>
            <select v-model="selectedDatasource" class="datasource-select">
              <option value="">{{ t('datasetEdit.selectDatasource') }}</option>
              <option v-for="ds in datasourceList" :key="ds.id" :value="ds.id">
                {{ ds.name }}
              </option>
            </select>
          </div>

          <!-- 上传文件区域 -->
          <div class="section-block">
            <label class="section-label">{{ t('datasetEdit.uploadFile') }}</label>
            <div class="file-actions">
              <button class="file-action-btn" :title="t('datasetEdit.upload')" @click="handleUpload">
                <span class="action-icon upload-icon">⬆️</span>
              </button>
              <button class="file-action-btn" :title="t('datasetEdit.download')" @click="handleDownload">
                <span class="action-icon download-icon">⬇️</span>
              </button>
              <button class="file-action-btn" :title="t('datasetEdit.refresh')" @click="handleRefresh">
                <span class="action-icon refresh-icon">🔄</span>
              </button>
            </div>
          </div>

          <!-- 表区域 -->
          <div class="section-block">
            <label class="section-label">{{ t('datasetEdit.table') }}</label>
            <div v-if="tablesLoading" class="table-loading-hint">
              <span>{{ t('datasetEdit.loadingTables') }}</span>
            </div>
            <ul v-else-if="datasourceTables.length > 0" class="table-list">
              <li
                v-for="table in datasourceTables"
                :key="table.id"
                class="table-item"
                :class="{ selected: isSelectedTable(table) }"
                @click="handleToggleTable(table)"
              >
                <span class="table-check" :class="{ checked: isSelectedTable(table) }">✓</span>
                <span class="table-name-text">{{ table.tableName }}</span>
              </li>
            </ul>
            <div v-else-if="selectedDatasource" class="empty-hint">{{ t('datasetEdit.noTablesInDs') }}</div>
            <div v-else class="empty-hint">{{ t('datasetEdit.selectDatasourceFirst') }}</div>
          </div>
        </div>
      </aside>

      <!-- 右侧主内容区 - 空状态 -->
      <main class="main-content empty-state">
        <div class="empty-illustration">
          <svg width="200" height="160" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="30" y="40" width="140" height="100" rx="8" fill="#f2f3f5" stroke="#e5e6eb" stroke-width="2"/>
            <rect x="50" y="60" width="100" height="12" rx="4" fill="#e5e6eb"/>
            <rect x="50" y="82" width="80" height="12" rx="4" fill="#e5e6eb"/>
            <rect x="50" y="104" width="60" height="12" rx="4" fill="#e5e6eb"/>
            <circle cx="150" cy="55" r="15" fill="#e8f3ff" stroke="#165dff" stroke-width="1.5"/>
            <path d="M145 55 L149 59 L156 51" stroke="#165dff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <p class="empty-title">{{ t('datasetEdit.selectTableHint') }}</p>
        <p class="empty-desc">{{ t('datasetEdit.dragHint') }}</p>
        <a href="#" class="learn-link" @click.prevent="handleLearnMore">{{ t('datasetEdit.learnConfig') }}</a>
      </main>
    </div>

    <!-- 模式二：数据预览与编辑 -->
    <div v-else-if="currentMode === 'preview'" class="edit-body preview-mode">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <button class="toolbar-btn" @click="handleAddRow">
            <span class="btn-icon">➕</span>
            <span>{{ t('datasetEdit.addRow') }}</span>
          </button>
          <button class="toolbar-btn" :disabled="selectedRowKeys.length === 0" @click="handleDeleteSelectedRows">
            <span class="btn-icon">🗑️</span>
            <span>{{ t('datasetEdit.deleteRows') }}</span>
          </button>
          <button class="toolbar-btn" @click="handleSourceTable">
            <span class="btn-icon">📋</span>
            <span>{{ t('datasetEdit.sourceTable') }}</span>
          </button>
          <button class="toolbar-btn" @click="handleAddCalcField">
            <span class="btn-icon">➕</span>
            <span>{{ t('datasetEdit.addCalcField') }}</span>
          </button>
          <button class="toolbar-btn" @click="handleAddGroupBy">
            <span class="btn-icon">📊</span>
            <span>{{ t('datasetEdit.addGroupBy') }}</span>
          </button>
          <button class="toolbar-btn" @click="handleAggregationEditor">
            <span class="btn-icon">∑</span>
            <span>{{ t('datasetEdit.aggregationEditor') }}</span>
          </button>
          <button class="toolbar-btn" @click="handleFieldSetting">
            <span class="btn-icon">⚙️</span>
            <span>{{ t('datasetEdit.fieldSetting') }}</span>
          </button>
        </div>
        <div class="toolbar-right">
          <label class="unlimit-row-check">
            <input v-model="unlimitRows" type="checkbox" />
            <span>{{ t('datasetEdit.unlimitRows') }}</span>
          </label>
          <button class="toolbar-btn refresh-btn" @click="handleRefreshData">
            <span class="btn-icon">🔄</span>
          </button>
        </div>
      </div>

      <!-- 主体区域：左侧字段大纲 + 右侧数据预览 -->
      <div class="preview-body">
        <!-- 左侧面板：字段大纲 -->
        <aside class="left-panel field-outline-panel">
          <div class="panel-section">
            <h2 class="panel-title">{{ t('datasetEdit.fieldOutline') }}</h2>
            <div class="field-search">
              <input
                v-model="fieldSearchKeyword"
                type="text"
                :placeholder="t('datasetEdit.searchFieldPlaceholder')"
                class="field-search-input"
              />
            </div>

            <!-- 可折叠分组列表 -->
            <div class="field-groups">
              <div
                v-for="group in filteredFieldGroups"
                :key="group.name"
                class="field-group"
              >
                <div class="group-header" @click="toggleGroup(group.name)">
                  <span class="group-toggle">{{ expandedGroups.has(group.name) ? '▼' : '▶' }}</span>
                  <span class="group-name">{{ group.label }}</span>
                  <span class="group-count">{{ group.fields.length }}</span>
                </div>
                <ul v-show="expandedGroups.has(group.name)" class="field-list">
                  <li
                    v-for="field in group.fields"
                    :key="field.id || field.name"
                    class="field-item"
                    @dblclick="handleToggleFieldCategory(field)"
                  >
                    <span class="field-name">{{ field.label || field.name }}</span>
                    <span v-if="field.type" class="field-type-tag">{{ field.type }}</span>
                    <span
                      class="field-eye-btn"
                      :class="{ hidden: isFieldHidden(field.name) }"
                      :title="isFieldHidden(field.name) ? t('datasetEdit.showColumn') : t('datasetEdit.hideColumn')"
                      @click.stop="toggleFieldVisibility(field.name)"
                    >
                      <svg viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M8 3C4.5 3 1.73 5.11 1 8c.73 2.89 3.5 5 7 5s6.27-2.11 7-5c-.73-2.89-3.5-5-7-5zm0 9a4 4 0 110-8 4 4 0 010 8z" fill="currentColor"/>
                        <circle cx="8" cy="8" r="2" fill="currentColor"/>
                      </svg>
                    </span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </aside>

        <!-- 右侧主内容区：数据预览表格 -->
        <main class="main-content data-preview">
          <div v-if="previewLoading" class="preview-loading">
            <span>{{ t('datasetEdit.loadingPreview') }}</span>
          </div>
          <div v-else-if="datasetColumns.length === 0" class="preview-empty">
            <span>{{ t('datasetEdit.noPreviewData') }}</span>
          </div>
          <template v-else>
            <div class="table-wrapper">
              <table class="data-table">
                <thead>
                  <tr>
                    <th class="data-th row-check-th">
                      <input
                        type="checkbox"
                        :checked="isAllRowsSelected"
                        :indeterminate="isPartialRowsSelected"
                        @change="handleSelectAllRows"
                      />
                    </th>
                    <th class="data-th row-num-th">{{ t('datasetEdit.rowNumber') }}</th>
                    <th
                      v-for="col in visibleColumns"
                      :key="col.name"
                      class="data-th sortable-th"
                      :style="{ width: getColumnWidth(col.name) }"
                      :title="col.title || col.name"
                      @click="handleSortColumn(col.name)"
                    >
                      <div class="th-content">
                        <span class="th-title">{{ col.title }}</span>
                        <span v-if="col.fieldCategory" class="th-category" :class="col.fieldCategory">
                          {{ col.fieldCategory === 'dimension' ? 'D' : 'M' }}
                        </span>
                        <span v-if="sortField === col.name" class="sort-indicator">
                          {{ sortOrder === 'asc' ? '↑' : '↓' }}
                        </span>
                        <span
                          class="th-eye-btn"
                          :class="{ hidden: isColumnHidden(col.name) }"
                          :title="isColumnHidden(col.name) ? t('datasetEdit.showColumn') : t('datasetEdit.hideColumn')"
                          @click.stop="toggleColumnVisibility(col.name)"
                        >
                          <svg viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M8 3C4.5 3 1.73 5.11 1 8c.73 2.89 3.5 5 7 5s6.27-2.11 7-5c-.73-2.89-3.5-5-7-5zm0 9a4 4 0 110-8 4 4 0 010 8z" fill="currentColor"/>
                            <circle cx="8" cy="8" r="2" fill="currentColor"/>
                          </svg>
                        </span>
                      </div>
                      <div
                        class="col-resize-handle"
                        @mousedown.stop.prevent="startResize($event, col.name)"
                      ></div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="displayRows.length === 0" class="no-data-row">
                    <td :colspan="visibleColumns.length + 2" class="no-data-cell">
                      {{ t('datasetEdit.noPreviewData') }}
                    </td>
                  </tr>
                  <tr
                    v-else
                    v-for="(row, idx) in displayRows"
                    :key="getRowKey(row) || idx"
                    class="data-row"
                    :class="{ selected: isRowSelected(row), editing: editingRowIdx === idx }"
                  >
                    <td class="data-td row-check-td">
                      <input
                        type="checkbox"
                        :checked="isRowSelected(row)"
                        @change="handleToggleRowSelect(row)"
                      />
                    </td>
                    <td class="data-td row-num-td">{{ (dataCurrentPage - 1) * dataPageSize + idx + 1 }}</td>
                    <td
                      v-for="col in visibleColumns"
                      :key="col.name"
                      class="data-td editable-td"
                      :style="{ width: getColumnWidth(col.name) }"
                      :class="{ 'cell-editing': editingRowIdx === idx && editingColName === col.name }"
                      @dblclick="handleStartEdit(idx, col.name, row)"
                    >
                      <template v-if="editingRowIdx === idx && editingColName === col.name">
                        <input
                          ref="editInputRef"
                          v-model="editCellValue"
                          class="cell-edit-input"
                          @blur="handleFinishEdit(row, col.name)"
                          @keydown.enter="handleFinishEdit(row, col.name)"
                          @keydown.escape="handleCancelEdit"
                        />
                      </template>
                      <template v-else>
                        {{ row[col.name] != null ? row[col.name] : '' }}
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 底部分页栏 -->
            <div class="bottom-bar">
              <div class="bottom-left">
                <span class="tab-item">{{ t('datasetEdit.totalFields', { count: totalFields }) }}</span>
                <span class="tab-item">{{ t('datasetEdit.dimensionCount', { count: dimensionCount }) }}</span>
                <span class="tab-item">{{ t('datasetEdit.measureCount', { count: measureCount }) }}</span>
              </div>
              <div class="bottom-right">
                <span class="page-info-text">
                  {{ t('datasetEdit.totalRows', { count: dataTotal }) }}
                </span>
                <button class="page-arrow" :disabled="dataCurrentPage <= 1" @click="dataCurrentPage--">&lt;</button>
                <span class="page-num">{{ dataCurrentPage }} / {{ dataTotalPages }}</span>
                <button class="page-arrow" :disabled="dataCurrentPage >= dataTotalPages" @click="dataCurrentPage++">&gt;</button>
              </div>
            </div>
          </template>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as datasourceApi from '@/api/datasource'
import * as datasetApi from '@/api/dataset'
import type { DatasourceColumn, DatasetField, DatasetColumnDef, DatasetData } from '@/types'

/** 字段项结构 */
interface FieldItem {
  id?: string
  name: string
  label?: string
  type?: string
  fieldCategory?: string
}

/** 字段分组结构 */
interface FieldGroup {
  name: string
  label: string
  fields: FieldItem[]
}

const props = withDefaults(defineProps<{
  datasetId?: string
  mode?: 'config' | 'preview'
}>(), {
  datasetId: undefined,
  mode: 'config',
})

const emit = defineEmits<{
  back: []
  saved: [datasetId: string]
}>()

const { t } = useI18n()

const currentMode = ref<'config' | 'preview'>(props.mode)

/** 数据集名称 */
const datasetName = ref('')

/** 搜索关键词（预览模式头部） */
const searchKeyword = ref('')

/** 选中的数据源 ID */
const selectedDatasource = ref('')

/** 数据源列表 */
const datasourceList = ref<Array<{ id: string; name: string }>>([])

/** 选中数据源下的表列表 */
const datasourceTables = ref<Array<{ id: string; tableName: string; tableComment?: string }>>([])

/** 表加载中 */
const tablesLoading = ref(false)

/** 已选中的表对象列表 */
const selectedTableObjects = ref<Array<{ id: string; tableName: string }>>([])

/** 字段搜索关键词 */
const fieldSearchKeyword = ref('')

/** 展开的分组名称集合 */
const expandedGroups = ref<Set<string>>(new Set(['dimension', 'measure']))

/** 不限显示行数 */
const unlimitRows = ref(false)

/** 已创建的数据集 ID（config→preview 切换后保存） */
const createdDatasetId = ref('')

/** 数据集字段列表（从后端加载） */
const datasetFields = ref<DatasetField[]>([])

/** 数据集列定义（从后端加载） */
const datasetColumns = ref<DatasetColumnDef[]>([])

/** 数据行 */
const datasetRows = ref<Record<string, unknown>[]>([])

/** 数据总行数 */
const dataTotal = ref(0)

/** 当前数据页码 */
const dataCurrentPage = ref(1)

/** 每页条数 */
const dataPageSize = ref(50)

/** 预览数据加载中 */
const previewLoading = ref(false)

/** 排序字段 */
const sortField = ref('')

/** 排序方向 */
const sortOrder = ref<'asc' | 'desc'>('asc')

/** 选中行主键值集合 */
const selectedRowKeys = ref<Set<string>>(new Set())

/** 编辑行索引 */
const editingRowIdx = ref(-1)

/** 编辑列名 */
const editingColName = ref('')

/** 编辑单元格值 */
const editCellValue = ref('')

/** 编辑输入框引用 */
const editInputRef = ref<HTMLInputElement[]>([])

/** 隐藏的列名集合 */
const hiddenColumnNames = ref<Set<string>>(new Set())

/** 列宽映射（colName -> px） */
const columnWidths = ref<Map<string, number>>(new Map())

/** 拖拽伸缩状态 */
const resizingCol = ref('')
const resizeStartX = ref(0)
const resizeStartWidth = ref(0)

/** 主键字段名列表 */
const primaryKeyFields = computed(() => {
  return datasetFields.value.filter(f => f.primaryKey).map(f => f.columnName)
})

/** 是否可以完成（已选择数据源和至少一张表且输入了名称） */
const canSave = computed(() => {
  return !!selectedDatasource.value && selectedTableObjects.value.length > 0 && !!datasetName.value.trim()
})

/** 过滤后的字段分组 */
const filteredFieldGroups = computed((): FieldGroup[] => {
  const keyword = fieldSearchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return buildFieldGroupsFromDatasetFields()
  }
  return buildFieldGroupsFromDatasetFields()
    .map(group => ({
      ...group,
      fields: group.fields.filter(
        field =>
          field.name.toLowerCase().includes(keyword) ||
          (field.label && field.label.toLowerCase().includes(keyword))
      ),
    }))
    .filter(group => group.fields.length > 0)
})

/** 总字段数 */
const totalFields = computed(() => {
  return datasetFields.value.length
})

/** 维度字段数 */
const dimensionCount = computed(() => {
  return datasetFields.value.filter(f => f.fieldCategory === 'dimension').length
})

/** 度量字段数 */
const measureCount = computed(() => {
  return datasetFields.value.filter(f => f.fieldCategory === 'measure').length
})

/** 数据总页数 */
const dataTotalPages = computed(() => {
  return Math.ceil(dataTotal.value / dataPageSize.value) || 1
})

/** 可见列（未隐藏的列） */
const visibleColumns = computed(() => {
  return datasetColumns.value.filter(col => !hiddenColumnNames.value.has(col.name))
})

/** 隐藏列（已隐藏的列） */
const hiddenColumns = computed(() => {
  return datasetColumns.value.filter(col => hiddenColumnNames.value.has(col.name))
})

/** 显示行（带排序和搜索过滤） */
const displayRows = computed(() => {
  let rows = [...datasetRows.value]
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    rows = rows.filter(row =>
      Object.values(row).some(val => String(val).toLowerCase().includes(keyword))
    )
  }
  if (sortField.value) {
    rows.sort((a, b) => {
      const va = a[sortField.value]
      const vb = b[sortField.value]
      if (va == null && vb == null) {
        return 0
      }
      if (va == null) {
        return 1
      }
      if (vb == null) {
        return -1
      }
      const cmp = String(va).localeCompare(String(vb), undefined, { numeric: true })
      return sortOrder.value === 'asc' ? cmp : -cmp
    })
  }
  return rows
})

/** 是否全选行 */
const isAllRowsSelected = computed(() => {
  return displayRows.value.length > 0 && displayRows.value.every(row => isRowSelected(row))
})

/** 是否部分选中行 */
const isPartialRowsSelected = computed(() => {
  const selected = displayRows.value.filter(row => isRowSelected(row)).length
  return selected > 0 && selected < displayRows.value.length
})

onMounted(async () => {
  await loadDatasources()
  if (props.datasetId && props.mode === 'preview') {
    createdDatasetId.value = props.datasetId
    await loadDatasetInfo(props.datasetId)
    await loadDatasetData(props.datasetId)
  }
})

watch(selectedDatasource, async (newDsId) => {
  if (newDsId) {
    await loadTables(newDsId)
  } else {
    datasourceTables.value = []
    selectedTableObjects.value = []
  }
})

watch(dataCurrentPage, async () => {
  if (createdDatasetId.value) {
    await loadDatasetData(createdDatasetId.value)
  }
})

/** 加载数据源列表 */
async function loadDatasources(): Promise<void> {
  try {
    const list = await datasourceApi.list()
    datasourceList.value = (list || []) as unknown as Array<{ id: string; name: string }>
  } catch {
    datasourceList.value = []
  }
}

/** 加载表列表 */
async function loadTables(dsId: string): Promise<void> {
  tablesLoading.value = true
  try {
    const tables = await datasourceApi.listTables(dsId)
    datasourceTables.value = (tables || []) as unknown as Array<{ id: string; tableName: string; tableComment?: string }>
    selectedTableObjects.value = []
  } catch {
    datasourceTables.value = []
  } finally {
    tablesLoading.value = false
  }
}

/** 加载数据集基本信息 */
async function loadDatasetInfo(dsId: string): Promise<void> {
  try {
    const ds = await datasetApi.get(dsId)
    if (ds) {
      datasetName.value = ds.name || ''
      selectedDatasource.value = ds.datasourceId || ''
    }
    const fields = await datasetApi.listFields(dsId)
    datasetFields.value = (fields || []) as unknown as DatasetField[]
  } catch {
    datasetFields.value = []
  }
}

/** 加载数据集数据（分页） */
async function loadDatasetData(dsId: string): Promise<void> {
  previewLoading.value = true
  try {
    const data = await datasetApi.getDatasetData(dsId, dataCurrentPage.value, dataPageSize.value) as unknown as DatasetData
    if (data) {
      datasetColumns.value = data.columns || []
      datasetRows.value = data.rows || []
      dataTotal.value = data.total || 0
    } else {
      datasetColumns.value = []
      datasetRows.value = []
      dataTotal.value = 0
    }
  } catch {
    datasetColumns.value = []
    datasetRows.value = []
    dataTotal.value = 0
  } finally {
    previewLoading.value = false
  }
}

/** 根据 datasetFields 构建字段分组 */
function buildFieldGroupsFromDatasetFields(): FieldGroup[] {
  const dimensions: FieldItem[] = []
  const measures: FieldItem[] = []
  for (const f of datasetFields.value) {
    const typeStr = f.dataType + (f.columnSize ? `(${f.columnSize}${f.decimalDigits ? `,${f.decimalDigits}` : ''})` : '')
    const item: FieldItem = {
      id: f.id,
      name: f.columnName,
      label: f.columnAlias || f.columnComment || undefined,
      type: typeStr,
      fieldCategory: f.fieldCategory,
    }
    if (f.fieldCategory === 'measure') {
      measures.push(item)
    } else {
      dimensions.push(item)
    }
  }
  return [
    { name: 'dimension', label: t('datasetEdit.dimension'), fields: dimensions },
    { name: 'measure', label: t('datasetEdit.measure'), fields: measures },
  ]
}

/** 获取行唯一标识 */
function getRowKey(row: Record<string, unknown>): string {
  if (row._rowId) {
    return String(row._rowId)
  }
  if (primaryKeyFields.value.length > 0) {
    return primaryKeyFields.value.map(k => String(row[k])).join('|')
  }
  return String(Object.values(row).join('|'))
}

/** 判断行是否选中 */
function isRowSelected(row: Record<string, unknown>): boolean {
  return selectedRowKeys.value.has(getRowKey(row))
}

/** 判断表是否已选中 */
function isSelectedTable(table: { id: string; tableName: string }): boolean {
  return selectedTableObjects.value.some(t => t.id === table.id)
}

/** 切换表选中状态 */
function handleToggleTable(table: { id: string; tableName: string }): void {
  const idx = selectedTableObjects.value.findIndex(t => t.id === table.id)
  if (idx >= 0) {
    selectedTableObjects.value.splice(idx, 1)
  } else {
    selectedTableObjects.value.push({ id: table.id, tableName: table.tableName })
  }
}

/** 返回上一页 */
function handleBack(): void {
  if (currentMode.value === 'preview') {
    currentMode.value = 'config'
  } else {
    emit('back')
  }
}

/** 点击"完成，开始数据处理"：创建数据集并切换到预览模式 */
async function handleFinish(): Promise<void> {
  if (!canSave.value) {
    ElMessage.warning(t('datasetEdit.selectTableFirst'))
    return
  }
  try {
    const result = await datasetApi.create({
      name: datasetName.value.trim(),
      datasourceId: selectedDatasource.value,
      tableIds: selectedTableObjects.value.map(t => t.id),
    })
    if (result) {
      createdDatasetId.value = String(result.id)
      const syncResult = await datasetApi.syncData(createdDatasetId.value)
      if (syncResult && syncResult.status === 'error') {
        ElMessage.warning(t('datasetEdit.syncDataFail') || '数据同步失败，请稍后重试')
      }
      currentMode.value = 'preview'
      await loadDatasetInfo(createdDatasetId.value)
      await loadDatasetData(createdDatasetId.value)
      ElMessage.success(t('datasetEdit.createSuccess'))
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(t('datasetEdit.createFail') + ': ' + msg)
  }
}

/** 保存所有修改 */
async function handleSaveAll(): Promise<void> {
  if (!createdDatasetId.value) {
    return
  }
  try {
    await datasetApi.update(createdDatasetId.value, {
      name: datasetName.value.trim(),
    })
    emit('saved', createdDatasetId.value)
    ElMessage.success(t('datasetEdit.save') + ' ✓')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(t('datasetEdit.updateFail') + ': ' + msg)
  }
}

/** 排序列 */
function handleSortColumn(colName: string): void {
  if (sortField.value === colName) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = colName
    sortOrder.value = 'asc'
  }
}

/** 全选/取消全选行 */
function handleSelectAllRows(event: Event): void {
  const target = event.target as HTMLInputElement
  if (target.checked) {
    for (const row of displayRows.value) {
      selectedRowKeys.value.add(getRowKey(row))
    }
  } else {
    selectedRowKeys.value.clear()
  }
  selectedRowKeys.value = new Set(selectedRowKeys.value)
}

/** 切换行选中 */
function handleToggleRowSelect(row: Record<string, unknown>): void {
  const key = getRowKey(row)
  if (selectedRowKeys.value.has(key)) {
    selectedRowKeys.value.delete(key)
  } else {
    selectedRowKeys.value.add(key)
  }
  selectedRowKeys.value = new Set(selectedRowKeys.value)
}

/** 双击开始编辑单元格 */
function handleStartEdit(idx: number, colName: string, row: Record<string, unknown>): void {
  editingRowIdx.value = idx
  editingColName.value = colName
  editCellValue.value = row[colName] != null ? String(row[colName]) : ''
  nextTick(() => {
    const inputs = editInputRef.value
    if (inputs && inputs.length > 0) {
      const input = inputs[0]
      if (input) {
        input.focus()
        input.select()
      }
    }
  })
}

/** 完成编辑单元格 */
async function handleFinishEdit(row: Record<string, unknown>, colName: string): Promise<void> {
  if (editingRowIdx.value < 0) {
    return
  }
  const oldValue = row[colName]
  const newValue = editCellValue.value
  editingRowIdx.value = -1
  editingColName.value = ''
  if (oldValue === newValue) {
    return
  }
  row[colName] = newValue
  if (createdDatasetId.value) {
    try {
      const rowKey: Record<string, unknown> = {}
      if (row._rowId) {
        rowKey['_rowId'] = row._rowId
      } else if (primaryKeyFields.value.length > 0) {
        for (const pk of primaryKeyFields.value) {
          rowKey[pk] = row[pk]
        }
      } else {
        for (const col of datasetColumns.value) {
          rowKey[col.name] = row[col.name]
        }
      }
      await datasetApi.updateRow(createdDatasetId.value, rowKey, { [colName]: newValue })
    } catch (e: unknown) {
      row[colName] = oldValue
      const msg = e instanceof Error ? e.message : String(e)
      ElMessage.error(t('datasetEdit.updateFail') + ': ' + msg)
    }
  }
}

/** 取消编辑 */
function handleCancelEdit(): void {
  editingRowIdx.value = -1
  editingColName.value = ''
}

/** 新增行 */
async function handleAddRow(): Promise<void> {
  if (!createdDatasetId.value) {
    return
  }
  const values: Record<string, unknown> = {}
  for (const col of datasetColumns.value) {
    values[col.name] = null
  }
  try {
    await datasetApi.addRow(createdDatasetId.value, values)
    await loadDatasetData(createdDatasetId.value)
    ElMessage.success(t('datasetEdit.addRowSuccess'))
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(t('datasetEdit.addRowFail') + ': ' + msg)
  }
}

/** 删除选中行 */
async function handleDeleteSelectedRows(): Promise<void> {
  if (selectedRowKeys.value.size === 0 || !createdDatasetId.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      t('datasetEdit.deleteRowsConfirm', { count: selectedRowKeys.value.size }),
      t('datasetEdit.confirmDelete'),
      { confirmButtonText: t('datasetEdit.confirm'), cancelButtonText: t('datasetEdit.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const rowsToDelete = datasetRows.value.filter(row => selectedRowKeys.value.has(getRowKey(row)))
    for (const row of rowsToDelete) {
      const rowKey: Record<string, unknown> = {}
      if (row._rowId) {
        rowKey['_rowId'] = row._rowId
      } else if (primaryKeyFields.value.length > 0) {
        for (const pk of primaryKeyFields.value) {
          rowKey[pk] = row[pk]
        }
      } else {
        for (const col of datasetColumns.value) {
          rowKey[col.name] = row[col.name]
        }
      }
      await datasetApi.deleteRow(createdDatasetId.value, rowKey)
    }
    selectedRowKeys.value.clear()
    await loadDatasetData(createdDatasetId.value)
    ElMessage.success(t('datasetEdit.deleteRowsSuccess'))
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(t('datasetEdit.deleteRowsFail') + ': ' + msg)
  }
}

/** 双击字段切换分类（维度↔度量） */
async function handleToggleFieldCategory(field: FieldItem): Promise<void> {
  if (!field.id || !createdDatasetId.value) {
    return
  }
  const newCategory = field.fieldCategory === 'dimension' ? 'measure' : 'dimension'
  try {
    await datasetApi.updateFieldCategory(field.id, newCategory)
    const target = datasetFields.value.find(f => f.id === field.id)
    if (target) {
      target.fieldCategory = newCategory
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(t('datasetEdit.updateCategoryFail') + ': ' + msg)
  }
}

/** 刷新数据 */
async function handleRefreshData(): Promise<void> {
  if (!createdDatasetId.value) {
    return
  }
  try {
    await datasetApi.syncData(createdDatasetId.value)
    await loadDatasetData(createdDatasetId.value)
    ElMessage.success(t('datasetEdit.refreshSuccess') || '刷新成功')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error((t('datasetEdit.syncDataFail') || '同步失败') + ': ' + msg)
  }
}

/** 刷新表列表 */
function handleRefresh(): void {
  if (selectedDatasource.value) {
    loadTables(selectedDatasource.value)
  }
}

/** 切换分组展开/收起 */
function toggleGroup(groupName: string): void {
  if (expandedGroups.value.has(groupName)) {
    expandedGroups.value.delete(groupName)
  } else {
    expandedGroups.value.add(groupName)
  }
  expandedGroups.value = new Set(expandedGroups.value)
}

/** 切换列显隐 */
function toggleColumnVisibility(colName: string): void {
  const next = new Set(hiddenColumnNames.value)
  if (next.has(colName)) {
    next.delete(colName)
  } else {
    next.add(colName)
  }
  hiddenColumnNames.value = next
}

/** 判断列是否隐藏 */
function isColumnHidden(colName: string): boolean {
  return hiddenColumnNames.value.has(colName)
}

/** 判断字段是否隐藏（按字段名匹配） */
function isFieldHidden(fieldName: string): boolean {
  return hiddenColumnNames.value.has(fieldName)
}

/** 切换字段显隐（供左侧面板使用） */
function toggleFieldVisibility(fieldName: string): void {
  toggleColumnVisibility(fieldName)
}

/** 获取列宽样式值 */
function getColumnWidth(colName: string): string | undefined {
  const w = columnWidths.value.get(colName)
  return w ? `${w}px` : undefined
}

/** 开始拖拽伸缩列宽 */
function startResize(event: MouseEvent, colName: string): void {
  const th = (event.target as HTMLElement).closest('th') as HTMLTableCellElement
  if (!th) { return }
  resizingCol.value = colName
  resizeStartX.value = event.clientX
  resizeStartWidth.value = th.offsetWidth
  document.addEventListener('mousemove', onResizing)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

/** 拖拽中 */
function onResizing(event: MouseEvent): void {
  if (!resizingCol.value) { return }
  const diff = event.clientX - resizeStartX.value
  const newWidth = Math.max(60, resizeStartWidth.value + diff)
  columnWidths.value.set(resizingCol.value, newWidth)
  columnWidths.value = new Map(columnWidths.value)
}

/** 停止拖拽 */
function stopResize(): void {
  resizingCol.value = ''
  document.removeEventListener('mousemove', onResizing)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

/** 上传文件 */
function handleUpload(): void {}

/** 下载文件 */
function handleDownload(): void {}

/** 了解如何配置 */
function handleLearnMore(): void {}

/** 来源表 */
function handleSourceTable(): void {}

/** 新建计算字段 */
function handleAddCalcField(): void {}

/** 新建分组依据 */
function handleAddGroupBy(): void {}

/** 聚合编辑器 */
function handleAggregationEditor(): void {}

/** 字段设置 */
function handleFieldSetting(): void {}

/** 更多操作 */
function handleMore(): void {}
</script>

<style scoped>
.dataset-edit-page {
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: none;
  border: none;
  color: #165dff;
  font-size: 16px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
  font-family: inherit;
}

.back-btn:hover {
  background: #e8f3ff;
}

.back-arrow {
  font-size: 18px;
  font-weight: 500;
}

.dataset-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #dcdfe6;
  font-family: inherit;
}

.cancel-btn {
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  border-color: var(--theme-border-strong);
}

.cancel-btn:hover {
  border-color: #165dff;
  color: #165dff;
}

.save-btn {
  background: #165dff;
  color: #fff;
  border-color: #165dff;
}

.save-btn:hover {
  background: #0e42d2;
  border-color: #0e42d2;
}

.finish-btn {
  background: #165dff;
  color: #fff;
  border-color: #165dff;
}

.finish-btn:hover {
  background: #0e42d2;
  border-color: #0e42d2;
}

.finish-btn:disabled,
.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.name-input {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
  box-sizing: border-box;
}

.name-input:focus {
  border-color: #165dff;
}

.search-box {
  position: relative;
}

.search-input {
  width: 200px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}

.search-input:focus {
  border-color: #165dff;
}

.search-input::placeholder {
  color: #c9cdd4;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
  font-family: inherit;
}

.icon-btn:hover {
  background: #f2f3f5;
}

.more-btn {
  font-size: 22px;
  font-weight: bold;
}

.edit-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.config-mode {
  gap: 16px;
  padding: 16px 24px;
}

.preview-mode {
  flex-direction: column;
}

.left-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  overflow-y: auto;
  flex-shrink: 0;
}

.config-panel {
  width: 300px;
  min-width: 300px;
}

.field-outline-panel {
  width: 250px;
  min-width: 250px;
}

.panel-section {
  padding: 16px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 16px 0;
}

.section-block {
  margin-bottom: 20px;
}

.section-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #4e5969;
  margin-bottom: 8px;
}

.datasource-select {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s;
  font-family: inherit;
}

.datasource-select:focus {
  border-color: #165dff;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.file-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: #f7f8fa;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.file-action-btn:hover {
  border-color: #165dff;
  background: #e8f3ff;
}

.action-icon {
  font-size: 16px;
}

.table-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.table-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #4e5969;
  background: #f7f8fa;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  border: 1px solid transparent;
}

.table-item:hover {
  background: #e8f3ff;
  color: #165dff;
}

.table-item.selected {
  background: #e8f3ff;
  border-color: #165dff;
}

.table-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1.5px solid #c9cdd4;
  font-size: 10px;
  color: #fff;
  flex-shrink: 0;
  transition: all 0.15s;
}

.table-check.checked {
  background: #165dff;
  border-color: #165dff;
}

.table-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-hint {
  font-size: 12px;
  color: #c9cdd4;
  text-align: center;
  padding: 12px 0;
}

.table-loading-hint {
  font-size: 12px;
  color: #86909c;
  text-align: center;
  padding: 12px 0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.empty-state {
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
}

.empty-illustration {
  margin-bottom: 20px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 13px;
  color: #86909c;
  margin: 0 0 16px 0;
  text-align: center;
  max-width: 360px;
  line-height: 1.6;
}

.learn-link {
  font-size: 13px;
  color: #165dff;
  text-decoration: none;
  transition: opacity 0.15s;
}

.learn-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
  gap: 12px;
  overflow-x: auto;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 5px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  font-family: inherit;
}

.toolbar-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.toolbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-icon {
  font-size: 13px;
  line-height: 1;
}

.unlimit-row-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  white-space: nowrap;
}

.unlimit-row-check input[type='checkbox'] {
  cursor: pointer;
}

.refresh-btn {
  padding: 0 8px;
}

.preview-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  overflow: hidden;
}

.field-search {
  margin-bottom: 12px;
}

.field-search-input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  font-size: 12px;
  color: #1d2129;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
  font-family: inherit;
}

.field-search-input:focus {
  border-color: #165dff;
}

.field-search-input::placeholder {
  color: #c9cdd4;
}

.field-groups {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-group {
  border-radius: 6px;
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  border-radius: 4px;
}

.group-header:hover {
  background: #f7f8fa;
}

.group-toggle {
  font-size: 10px;
  color: #86909c;
  width: 12px;
  text-align: center;
  flex-shrink: 0;
}

.group-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
  flex: 1;
}

.group-count {
  font-size: 11px;
  color: #86909c;
  background: #f2f3f5;
  padding: 1px 6px;
  border-radius: 10px;
  flex-shrink: 0;
}

.field-list {
  list-style: none;
  margin: 0;
  padding: 0 0 0 26px;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 12px;
  color: #4e5969;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.field-item:hover {
  background: #f7f8fa;
}

.field-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-type-tag {
  font-size: 10px;
  color: #86909c;
  background: #f2f3f5;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
  margin-left: 8px;
}

.field-eye-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  color: #86909c;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
  opacity: 0.5;
}

.field-eye-btn:hover {
  color: #165dff;
  background: #e8f3ff;
  opacity: 1;
}

.field-eye-btn.hidden {
  color: #c9cdd4;
  opacity: 0.35;
}

.field-eye-btn.hidden:hover {
  color: #0fc6c2;
  background: #e8fffb;
  opacity: 1;
}

.field-eye-btn svg {
  width: 13px;
  height: 13px;
}

.data-preview {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-loading,
.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #c9cdd4;
  font-size: 13px;
}

.table-wrapper {
  flex: 1;
  overflow: auto;
  overscroll-behavior: contain;
}

.data-table {
  width: 100%;
  min-width: max-content;
  border-collapse: collapse;
  table-layout: auto;
}

.data-table thead tr {
  background: #fafafa;
}

.data-th {
  padding: 10px 16px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #4e5969;
  border-bottom: 2px solid #e5e6eb;
  white-space: nowrap;
  position: sticky;
  top: 0;
  background: #fafafa;
  z-index: 1;
  min-width: 80px;
}

.row-check-th {
  width: 40px;
  text-align: center;
}

.row-num-th {
  width: 50px;
  text-align: center;
}

.sortable-th {
  position: relative;
  cursor: pointer;
  user-select: none;
}

.sortable-th:hover {
  background: #f2f3f5;
}

.th-content {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.th-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.th-category {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 2px;
  flex-shrink: 0;
}

.th-category.dimension {
  color: #165dff;
  background: #e8f3ff;
}

.th-category.measure {
  color: #0fc6c2;
  background: #e8fffb;
}

.sort-indicator {
  font-size: 12px;
  color: #165dff;
  flex-shrink: 0;
}

.th-eye-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-left: 2px;
  color: #86909c;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
  opacity: 0.6;
}

.th-eye-btn:hover {
  color: #165dff;
  background: #e8f3ff;
  opacity: 1;
}

.th-eye-btn.hidden {
  color: #c9cdd4;
  opacity: 0.4;
}

.th-eye-btn.hidden:hover {
  color: #0fc6c2;
  background: #e8fffb;
  opacity: 1;
}

.th-eye-btn svg {
  width: 14px;
  height: 14px;
}

.data-td {
  padding: 10px 16px;
  font-size: 12.5px;
  color: #1d2129;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 80px;
}

.row-check-td {
  width: 40px;
  text-align: center;
}

.row-num-td {
  width: 50px;
  text-align: center;
  color: #c9cdd4;
  font-size: 12px;
}

.editable-td {
  cursor: default;
}

.editable-td:hover {
  background: #f7f8fa;
}

.cell-editing {
  padding: 0 !important;
}

.cell-edit-input {
  width: 100%;
  height: 100%;
  min-height: 36px;
  padding: 8px 14px;
  border: 2px solid #165dff;
  border-radius: 0;
  font-size: 12.5px;
  color: #1d2129;
  outline: none;
  background: #fff;
  box-sizing: border-box;
  font-family: inherit;
}

.data-row:hover {
  background: #fafbfc;
}

.data-row.selected {
  background: #f0f7ff;
}

.data-row.editing {
  background: #fff;
}

.no-data-row .no-data-cell {
  text-align: center;
  padding: 40px 0;
  color: #c9cdd4;
  font-size: 13px;
}

.bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid #f2f3f5;
  background: #fafafa;
  flex-shrink: 0;
}

.bottom-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.bottom-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-item {
  font-size: 12px;
  color: #86909c;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.15s;
}

.page-info-text {
  font-size: 12px;
  color: #86909c;
}

.page-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.page-arrow:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.page-arrow:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.page-num {
  font-size: 12px;
  color: #4e5969;
  min-width: 60px;
  text-align: center;
}

/* 列宽拖拽伸缩 - 已合并到 sortable-th 中 */

.col-resize-handle {
  position: absolute;
  right: 0;
  top: 10%;
  bottom: 10%;
  width: 5px;
  cursor: col-resize;
  z-index: 2;
  border-right: 2px solid transparent;
  transition: border-color 0.15s;
}

.col-resize-handle:hover {
  border-right-color: #165dff;
}
</style>
