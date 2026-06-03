<template>
  <div class="dataset-page">
    <!-- 数据集编辑页面 -->
    <DatasetEdit
      v-if="showEditView"
      :dataset-id="editingDatasetId"
      :mode="editMode"
      @back="handleBackFromEdit"
      @saved="handleSaved"
    />

    <template v-else>
      <!-- 加载中 -->
      <div v-if="loading && datasets.length === 0" class="page-loading">
        <span>{{ t('datasetPage.loading') }}</span>
      </div>

      <!-- 主内容区（工具栏始终显示） -->
      <div v-else class="dataset-container">
        <!-- 顶部工具栏 -->
        <div class="toolbar-section">
          <div class="toolbar-left">
            <h1 class="page-title">{{ t('datasetPage.title') }}</h1>
          </div>
          <div class="toolbar-right">
            <label class="mine-checkbox">
              <input v-model="onlyMine" type="checkbox" />
              <span>{{ t('datasetPage.onlyMine') }}</span>
            </label>
            <input
              v-model="searchKeyword"
              class="search-input"
              :placeholder="t('datasetPage.searchPlaceholder')"
            />
            <button class="filter-btn" @click="handleFilter">
              {{ t('datasetPage.filter') }}
            </button>
            <select v-model="sortType" class="sort-select">
              <option value="default">{{ t('datasetPage.sortDefault') }}</option>
              <option value="nameAsc">{{ t('datasetPage.sortNameAsc') }}</option>
              <option value="nameDesc">{{ t('datasetPage.sortNameDesc') }}</option>
              <option value="timeDesc">{{ t('datasetPage.sortTimeDesc') }}</option>
              <option value="timeAsc">{{ t('datasetPage.sortTimeAsc') }}</option>
            </select>
            <button class="btn-secondary" @click="handleCreateFolder">
              {{ t('datasetPage.createFolder') }}
            </button>
            <button class="btn-primary" @click="handleCreateDataset">
              {{ t('datasetPage.createDataset') }}
            </button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="datasets.length === 0" class="empty-section-inner">
          <div class="empty-icon-wrapper">
            <span class="empty-folder-icon">📁</span>
            <span class="empty-badge">📊</span>
          </div>
          <p class="empty-desc">{{ t('datasetPage.emptyDesc') }}</p>
        </div>

        <!-- 表格区域 -->
        <template v-else>
          <div class="table-wrapper">
            <table class="data-table">
          <thead>
            <tr>
              <th class="col-checkbox">
                <input
                  type="checkbox"
                  :checked="isAllSelected"
                  :indeterminate="isPartialSelected"
                  @change="handleSelectAll"
                />
              </th>
              <th class="col-name">{{ t('datasetPage.colName') }}</th>
              <th class="col-owner">{{ t('datasetPage.colOwner') }}</th>
              <th class="col-modifier">{{ t('datasetPage.colModifier') }}</th>
              <th class="col-modify-time">{{ t('datasetPage.colModifyTime') }}</th>
              <th class="col-datasource">{{ t('datasetPage.colDatasource') }}</th>
              <th class="col-action">{{ t('datasetPage.colAction') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in paginatedData"
              :key="item.id"
              class="data-row"
              :class="{ selected: selectedIds.includes(item.id) }"
            >
              <td class="col-checkbox">
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(item.id)"
                  @change="handleSelectItem(item.id)"
                />
              </td>
              <td class="col-name">
                <div class="name-cell" @click="handleEdit(item.id)">
                  <span class="item-icon">📊</span>
                  <span class="item-name">{{ item.name }}</span>
                  <span v-if="item.isNew" class="new-badge">NEW</span>
                </div>
              </td>
              <td class="col-owner">{{ item.owner }}</td>
              <td class="col-modifier">{{ item.modifier }}</td>
              <td class="col-modify-time">{{ item.modifyTime }}</td>
              <td class="col-datasource">{{ item.datasourceName }}</td>
              <td class="col-action">
                <div class="action-btns">
                  <button
                    class="icon-btn"
                    :title="t('datasetPage.actionView')"
                    @click="handleView(item.id)"
                  >
                    👁️
                  </button>
                  <button
                    class="icon-btn"
                    :title="t('datasetPage.actionEdit')"
                    @click="handleEdit(item.id)"
                  >
                    ✏️
                  </button>
                  <button
                    class="icon-btn more-btn"
                    :title="t('datasetPage.actionMore')"
                    @click="handleMore(item.id)"
                  >
                    ⋯
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页区域 -->
      <div class="pagination-bar">
        <div class="page-info">
          <span>{{ t('datasetPage.total') }}：</span>
          <span class="page-total">{{ filteredData.length }}</span>
        </div>
        <div class="page-nav">
          <button
            class="page-btn"
            :disabled="currentPage === PAGE_FIRST"
            @click="handlePrevPage"
          >
            &lt;
          </button>
          <button
            v-for="page in visiblePages"
            :key="page"
            class="page-btn"
            :class="{ active: page === currentPage }"
            @click="handleGoPage(page)"
          >
            {{ page }}
          </button>
          <button
            class="page-btn"
            :disabled="currentPage === totalPages"
            @click="handleNextPage"
          >
            &gt;
          </button>
        </div>
      </div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import DatasetEdit from './dataset/DatasetEdit.vue'
import * as datasetApi from '@/api/dataset'
import type { Dataset } from '@/types'

const { t } = useI18n()

/** 数据集项接口定义 */
interface DatasetItem {
  id: string
  name: string
  owner: string
  modifier: string
  modifyTime: string
  datasourceName: string
  isNew: boolean
}

/** 首页页码 */
const PAGE_FIRST = 1

/** 默认每页条数 */
const DEFAULT_PAGE_SIZE = 10

/** 加载状态 */
const loading = ref(false)

/** 数据集列表数据 */
const datasets = ref<DatasetItem[]>([])

/** 仅显示我的 */
const onlyMine = ref(false)

/** 搜索关键词 */
const searchKeyword = ref('')

/** 排序类型 */
const sortType = ref('default')

/** 当前页码 */
const currentPage = ref(PAGE_FIRST)

/** 每页显示条数 */
const pageSize = ref(DEFAULT_PAGE_SIZE)

/** 已选中的ID列表 */
const selectedIds = ref<string[]>([])

/** 编辑视图状态 */
const showEditView = ref(false)
const editingDatasetId = ref('')
const editMode = ref<'config' | 'preview'>('config')

onMounted(async () => {
  await loadDatasets()
})

/** 从后端加载数据集列表 */
async function loadDatasets(): Promise<void> {
  loading.value = true
  try {
    const list = await datasetApi.list() as unknown as Dataset[]
    if (list && Array.isArray(list)) {
      datasets.value = list.map((ds: Dataset) => ({
        id: String(ds.id),
        name: ds.name || '',
        owner: ds.owner || '',
        modifier: ds.modifier || '',
        modifyTime: ds.updateTime || ds.createTime || '',
        datasourceName: ds.datasourceName || '',
        isNew: false,
      }))
    } else {
      datasets.value = []
    }
  } catch {
    datasets.value = []
  } finally {
    loading.value = false
  }
}

/** 过滤后的数据列表 */
const filteredData = computed((): DatasetItem[] => {
  let result = [...datasets.value]
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    result = result.filter(
      (item) =>
        item.name.toLowerCase().includes(keyword) ||
        item.owner.toLowerCase().includes(keyword)
    )
  }
  return result
})

/** 总页数 */
const totalPages = computed((): number => {
  return Math.ceil(filteredData.value.length / pageSize.value) || PAGE_FIRST
})

/** 当前页数据 */
const paginatedData = computed((): DatasetItem[] => {
  const start = (currentPage.value - PAGE_FIRST) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

/** 是否全选 */
const isAllSelected = computed((): boolean => {
  return (
    filteredData.value.length > 0 &&
    selectedIds.value.length === filteredData.value.length
  )
})

/** 是否部分选中 */
const isPartialSelected = computed((): boolean => {
  return (
    selectedIds.value.length > 0 &&
    selectedIds.value.length < filteredData.value.length
  )
})

/** 可见页码列表 */
const visiblePages = computed((): number[] => {
  const pages: number[] = []
  const total = totalPages.value
  const current = currentPage.value
  if (total <= MAX_VISIBLE_PAGES) {
    for (let i = PAGE_FIRST; i <= total; i++) {
      pages.push(i)
    }
  } else {
    pages.push(PAGE_FIRST)
    if (current > PAGE_FIRST + HALF_VISIBLE_PAGES) {
      pages.push(-1)
    }
    const start = Math.max(PAGE_FIRST + 1, current - HALF_VISIBLE_PAGES)
    const end = Math.min(total - 1, current + HALF_VISIBLE_PAGES)
    for (let i = start; i <= end; i++) {
      pages.push(i)
    }
    if (current < total - HALF_VISIBLE_PAGES) {
      pages.push(-1)
    }
    pages.push(total)
  }
  return pages
})

/** 最大可见页码数 */
const MAX_VISIBLE_PAGES = 7

/** 可见页码半区间 */
const HALF_VISIBLE_PAGES = 2

/** 新建数据集 */
function handleCreateDataset(): void {
  editingDatasetId.value = ''
  editMode.value = 'config'
  showEditView.value = true
}

/** 编辑数据集 */
function handleEdit(id: string): void {
  editingDatasetId.value = id
  editMode.value = 'preview'
  showEditView.value = true
}

/** 从编辑视图返回 */
function handleBackFromEdit(): void {
  showEditView.value = false
  editingDatasetId.value = ''
  loadDatasets()
}

/** 数据集保存成功 */
function handleSaved(_datasetId: string): void {
  ElMessage.success(t('datasetPage.saveSuccess'))
  loadDatasets()
}

/** 新建文件夹 */
function handleCreateFolder(): void {
  ElMessage.info(t('datasetPage.comingSoon'))
}

/** 筛选按钮点击 */
function handleFilter(): void {
  ElMessage.info(t('datasetPage.comingSoon'))
}

/** 查看数据集 */
function handleView(id: string): void {
  ElMessage.info(t('datasetPage.comingSoon'))
}

/** 更多操作 */
function handleMore(id: string): void {
  ElMessage.info(t('datasetPage.comingSoon'))
}

/** 全选/取消全选 */
function handleSelectAll(event: Event): void {
  const target = event.target as HTMLInputElement
  if (target.checked) {
    selectedIds.value = filteredData.value.map((item) => item.id)
  } else {
    selectedIds.value = []
  }
}

/** 选择单个项 */
function handleSelectItem(id: string): void {
  const index = selectedIds.value.indexOf(id)
  if (index > -1) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

/** 上一页 */
function handlePrevPage(): void {
  if (currentPage.value > PAGE_FIRST) {
    currentPage.value--
  }
}

/** 下一页 */
function handleNextPage(): void {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
  }
}

/** 跳转到指定页 */
function handleGoPage(page: number): void {
  if (page >= PAGE_FIRST && page <= totalPages.value) {
    currentPage.value = page
  }
}
</script>

<style scoped>
.dataset-page {
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  overflow-y: auto;
}

.page-loading {
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

.empty-section-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 60px 0;
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

/* 主内容区 */
.dataset-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  background: #fff;
  border-radius: 4px;
  margin: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

/* 工具栏 */
.toolbar-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #e5e6eb;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mine-checkbox {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  user-select: none;
}

.mine-checkbox input[type='checkbox'] {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: #165dff;
}

.search-input {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  width: 200px;
  font-family: inherit;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #165dff;
}

.filter-btn,
.btn-secondary {
  height: 32px;
  padding: 0 14px;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.filter-btn:hover,
.btn-secondary:hover {
  border-color: #165dff;
  color: #165dff;
}

.sort-select {
  height: 32px;
  padding: 0 28px 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  background: #fff;
  color: #4e5969;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.2s;
}

.sort-select:focus {
  border-color: #165dff;
}

.btn-primary {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.btn-primary:hover {
  background: #0e42d2;
}

/* 表格区域 */
.table-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead tr {
  background: #fafafa;
}

.data-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 13px;
  font-weight: 500;
  color: #86909c;
  border-bottom: 1px solid #e5e6eb;
  white-space: nowrap;
}

.col-checkbox {
  width: 40px;
  text-align: center;
}

.col-checkbox input[type='checkbox'] {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: #165dff;
}

.col-name {
  width: 30%;
}

.col-owner {
  width: 12%;
}

.col-modifier {
  width: 12%;
}

.col-modify-time {
  width: 15%;
}

.col-datasource {
  width: 15%;
}

.col-action {
  width: 16%;
  text-align: right;
}

.data-table tbody tr {
  transition: background 0.15s;
}

.data-table tbody tr:hover {
  background: #fafbfc;
}

.data-table tbody tr.selected {
  background: #f0f7ff;
}

.data-table td {
  padding: 12px 16px;
  font-size: 13px;
  color: #4e5969;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: middle;
}

.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: color 0.15s;
}

.name-cell:hover .item-name {
  color: #165dff;
}

.item-icon {
  font-size: 18px;
  flex-shrink: 0;
  line-height: 1;
}

.item-name {
  color: #1d2129;
  font-weight: 400;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-badge {
  display: inline-block;
  background: #f53f3f;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.action-btns {
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
  padding: 4px 6px;
  border-radius: 3px;
  transition: all 0.15s;
  line-height: 1;
}

.icon-btn:hover {
  opacity: 1;
  background: #f2f3f5;
}

.more-btn {
  font-size: 16px;
  font-weight: bold;
}

/* 分页 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-top: 1px solid #e5e6eb;
}

.page-info {
  font-size: 13px;
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
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.page-btn:hover:not(:disabled):not(.active) {
  border-color: #165dff;
  color: #165dff;
}

.page-btn.active {
  background: #165dff;
  border-color: #165dff;
  color: #fff;
}

.page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
