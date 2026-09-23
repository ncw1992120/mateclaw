<template>
  <el-dialog v-model="ui.aloudata.visible" :title="aloudataTitle" width="920px" :close-on-click-modal="false">
    <template v-if="ui.aloudata.mode === 'metric-dim'">
      <el-alert
        v-if="!datasourceId"
        type="warning"
        :closable="false"
        title="未关联到数据源，无法加载指标/维度"
        style="margin-bottom: 12px"
      />
      <template v-else>
        <div class="directory-toolbar">
          <el-radio-group v-model="directoryMode" size="small" @change="onDirectoryModeChange">
            <el-radio-button label="metrics">指标</el-radio-button>
            <el-radio-button label="dimensions">维度</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="directoryKeyword"
            class="directory-search"
            :placeholder="directoryMode === 'metrics' ? '搜索指标展示名或指标字段名称' : '搜索维度展示名或维度字段名称'"
            clearable
            @input="onDirectoryKeywordInput"
          />
          <span class="count">已选 指标 {{ ui.aloudata.metrics.length }} · 维度 {{ ui.aloudata.dims.length }}</span>
        </div>

        <div class="directory-layout">
          <aside class="directory-categories" aria-label="Aloudata 指标目录">
            <el-tree
              v-if="directoryMode === 'metrics'"
              :data="filteredMetricTree"
              :props="metricTreeProps"
              node-key="categoryId"
              highlight-current
              :default-expanded-keys="expandedMetricCategoryKeys"
              :expand-on-click-node="false"
              @node-click="onMetricCategorySelect"
            >
              <template #default="{ data }">
                <span class="category-node"><span>{{ data.categoryName }}</span><span class="category-count">{{ data.metricList?.length || '' }}</span></span>
              </template>
            </el-tree>
            <el-tree
              v-else
              :data="dimensionCategoryTree"
              :props="dimensionTreeProps"
              node-key="categoryId"
              highlight-current
              :default-expanded-keys="['__all_dimensions__']"
              :expand-on-click-node="false"
              @node-click="onDimensionCategorySelect"
            >
              <template #default="{ data }">
                <span class="category-node"><span>{{ data.categoryName }}</span></span>
              </template>
            </el-tree>
            <div v-if="directoryMode === 'metrics' && !metricTreeLoading && !filteredMetricTree.length" class="directory-empty">没有匹配的指标目录</div>
          </aside>

          <section class="directory-results" v-loading="directoryMode === 'metrics' ? metricTreeLoading : dimsLoading">
            <template v-if="directoryMode === 'metrics'">
              <div v-if="visibleMetrics.length" class="directory-item-list">
                <label v-for="item in visibleMetrics" :key="item.metricName" class="directory-item">
                  <el-checkbox v-model="ui.aloudata.metrics" :label="item.metricName" />
                  <span class="directory-item-info">
                    <span class="directory-item-title">{{ item.metricDisplayName || item.metricName }}</span>
                    <span class="directory-item-code">{{ item.metricName }}<template v-if="item.categoryPath"> · {{ item.categoryPath }}</template></span>
                  </span>
                </label>
              </div>
              <div v-else-if="!metricTreeLoading" class="directory-empty">该目录下暂无指标</div>
            </template>
            <template v-else>
              <div v-if="dimPage.records.length" class="directory-item-list">
                <label v-for="item in dimPage.records" :key="item.dimName" class="directory-item">
                  <el-checkbox v-model="ui.aloudata.dims" :label="item.dimName" />
                  <span class="directory-item-info">
                    <span class="directory-item-title">{{ item.dimDisplayName || item.dimName }}</span>
                    <span class="directory-item-code">{{ item.dimName }}</span>
                  </span>
                </label>
              </div>
              <div v-else-if="!dimsLoading" class="directory-empty">该目录下没有匹配的维度</div>
              <el-pagination
                v-if="dimPage.total > pageSize"
                class="pager"
                layout="prev, pager, next, total"
                :total="dimPage.total"
                :page-size="pageSize"
                :current-page="dimPage.current"
                @current-change="onDimPageChange"
                small
              />
            </template>
          </section>
        </div>

        <div class="selected-summary">
          <span class="selected-summary-label">已选</span>
          <el-tag v-for="name in ui.aloudata.metrics" :key="`m-${name}`" closable size="small" @close="removeSelection('metrics', name)">
            {{ metricLabel(name) }}
          </el-tag>
          <el-tag v-for="name in ui.aloudata.dims" :key="`d-${name}`" closable size="small" type="info" @close="removeSelection('dimensions', name)">
            {{ dimLabel(name) }}
          </el-tag>
          <span v-if="!ui.aloudata.metrics.length && !ui.aloudata.dims.length" class="selected-placeholder">尚未选择</span>
        </div>
      </template>
    </template>

    <!-- 指标视图：平铺列表 + 关键字搜索 + 「只看我的」（来自后端 analysis-views/list） -->
    <template v-else>
      <p class="hint">
        默认只展示「我的」指标视图（按创建者归属过滤）；关闭开关可查看全部，他人的视图可能无取数权限。
      </p>
      <div class="toolbar">
        <el-input
          v-model="viewKeyword"
          placeholder="搜索指标视图名称"
          clearable
          style="width: 240px"
          @input="onViewKeywordInput"
        />
        <el-checkbox v-model="onlyMine" @change="loadAnalysisViews">只看我的</el-checkbox>
        <span class="count">{{ viewsLoading ? '加载中…' : `共 ${analysisViews.length} 个` }}</span>
      </div>
      <el-select
        v-model="ui.aloudata.metricView"
        placeholder="请选择指标视图"
        style="width: 100%"
        :loading="viewsLoading"
        filterable
      >
        <el-option v-for="v in analysisViews" :key="v.viewName" :label="v.displayName || v.viewName" :value="v.viewName">
          <span class="opt-name">{{ v.displayName || v.viewName }}</span>
          <span class="opt-tag" :class="v.mine ? 'is-mine' : 'is-other'">{{ v.mine ? '我的' : '他人' }}</span>
        </el-option>
      </el-select>
    </template>

    <template #footer>
      <el-button @click="ui.aloudata.visible = false">取消</el-button>
      <el-button type="primary" :disabled="!datasourceId" @click="confirmAloudata">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from '../useInsight'
import * as datasourceApi from '@/api/datasource'
import {
  getAloudataMetricDirectory,
  pageAloudataDimensions,
  listAloudataCategoryCounts,
} from '@/api/semantic-model'
import type { AloudataMetricDirectoryNode } from '@/api/semantic-model'
import type {
  AloudataDimensionPage,
  AloudataCategoryCount,
} from '@/types'
import { buildCategoryTree, filterMetricCategoryTree } from './aloudata-metric-directory'
import type { AloudataCategoryTreeNode, AloudataMetricDirectoryItem } from './aloudata-metric-directory'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const ALL_DIMENSIONS_ID = '__all_dimensions__'
const datasourceId = ref('')
const directoryMode = ref<'metrics' | 'dimensions'>('metrics')
const directoryKeyword = ref('')
const metricTreeLoading = ref(false)
const metricDirectory = ref<AloudataMetricDirectoryNode[]>([])
const selectedMetricCategoryId = ref('')
const dimensionCategories = ref<AloudataCategoryTreeNode[]>([])
const selectedDimensionCategoryId = ref(ALL_DIMENSIONS_ID)
const metricLabelMap = reactive<Record<string, string>>({})
const dimLabelMap = reactive<Record<string, string>>({})
const dimsLoading = ref(false)
const metricTreeProps = { label: 'categoryName', children: 'subCategory' }
const dimensionTreeProps = { label: 'categoryName', children: 'children' }

/** 指标视图列表（来自后端 analysis-views/list，非假数据；带 owner/mine 归属） */
const analysisViews = ref<datasourceApi.AloudataAnalysisViewItem[]>([])
/** 指标视图搜索关键字 */
const viewKeyword = ref('')
/** 只看我创建的指标视图（按 owner 归属过滤） */
const onlyMine = ref(true)
const viewsLoading = ref(false)

const dimPage = reactive<AloudataDimensionPage>({
  records: [],
  total: 0,
  size: pageSize,
  current: 1,
  pages: 0,
})

const dimensionCategoryTree = computed(() => [
  { categoryId: ALL_DIMENSIONS_ID, categoryName: '全部维度', children: dimensionCategories.value },
])
const filteredMetricTree = computed(() => filterMetricCategoryTree(metricDirectory.value, directoryKeyword.value))
const expandedMetricCategoryKeys = computed(() => {
  const keys: string[] = []
  const visit = (nodes: AloudataMetricDirectoryNode[]) => nodes.forEach((node) => {
    keys.push(node.categoryId)
    visit(node.subCategory || [])
  })
  if (directoryKeyword.value.trim()) visit(filteredMetricTree.value)
  return keys
})
const visibleMetrics = computed(() => {
  const result: Array<AloudataMetricDirectoryItem & { categoryPath: string }> = []
  const search = directoryKeyword.value.trim()
  const visit = (nodes: AloudataMetricDirectoryNode[], path: string[]) => {
    for (const node of nodes) {
      const nextPath = [...path, node.categoryName]
      const matches = search ? filterMetricCategoryTree([node], search)[0] : node
      if (!matches) continue
      const items = matches.metricList || []
      if (search || node.categoryId === selectedMetricCategoryId.value) {
        result.push(...items.map((item) => ({ ...item, categoryPath: nextPath.join(' / ') })))
      }
      if (search) visit(matches.subCategory || [], nextPath)
      else if (node.categoryId !== selectedMetricCategoryId.value) visit(node.subCategory || [], nextPath)
    }
  }
  visit(metricDirectory.value, [])
  return result
})

watch(
  () => ui.aloudata.visible,
  (v) => {
    if (v) open()
  },
)

function open() {
  datasourceId.value = ui.aloudata.datasourceId
  directoryMode.value = 'metrics'
  directoryKeyword.value = ''
  metricDirectory.value = []
  selectedMetricCategoryId.value = ''
  dimensionCategories.value = []
  selectedDimensionCategoryId.value = ALL_DIMENSIONS_ID
  viewKeyword.value = ''
  onlyMine.value = true
  Object.keys(metricLabelMap).forEach((k) => delete metricLabelMap[k])
  Object.keys(dimLabelMap).forEach((k) => delete dimLabelMap[k])
  if (!datasourceId.value) {
    ElMessage.warning('未关联到数据源，无法加载指标/维度')
    return
  }
  if (ui.aloudata.mode === 'metric-view') {
    loadAnalysisViews()
    return
  }
  loadMetricDirectory()
  loadDimensionCategories()
}

async function loadAnalysisViews() {
  if (!datasourceId.value) {
    analysisViews.value = []
    return
  }
  viewsLoading.value = true
  try {
    analysisViews.value = await datasourceApi.searchAnalysisViews(datasourceId.value, {
      keyword: viewKeyword.value || undefined,
      onlyMine: onlyMine.value,
    })
  } catch {
    analysisViews.value = []
    ElMessage.error('加载指标视图失败')
  } finally {
    viewsLoading.value = false
  }
}

let viewKwTimer: any
function onViewKeywordInput() {
  clearTimeout(viewKwTimer)
  viewKwTimer = setTimeout(() => loadAnalysisViews(), 300)
}

// ---- 实时目录接口与按字段标识搜索 ----
async function loadMetricDirectory() {
  if (!datasourceId.value) return
  metricTreeLoading.value = true
  try {
    metricDirectory.value = await getAloudataMetricDirectory(datasourceId.value)
    selectedMetricCategoryId.value = metricDirectory.value[0]?.categoryId || ''
    const collectLabels = (nodes: AloudataMetricDirectoryNode[]) => nodes.forEach((node) => {
      for (const metric of node.metricList || []) {
        metricLabelMap[metric.metricName] = metric.metricDisplayName || metric.metricName
      }
      collectLabels(node.subCategory || [])
    })
    collectLabels(metricDirectory.value)
  } catch {
    metricDirectory.value = []
    ElMessage.error('加载 Aloudata 指标目录失败，请检查数据源连接及认证配置')
  } finally {
    metricTreeLoading.value = false
  }
}

async function loadDimensionCategories() {
  try {
    const categories = await listAloudataCategoryCounts(datasourceId.value, 'CATEGORY_DIMENSION')
    dimensionCategories.value = buildCategoryTree((categories as AloudataCategoryCount[]).map((item) => ({
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      parentId: item.parentId,
    })))
  } catch {
    dimensionCategories.value = []
    ElMessage.error('加载 Aloudata 维度目录失败')
  }
}

function metricLabel(name: string) {
  return metricLabelMap[name] || name
}
function dimLabel(name: string) {
  return dimLabelMap[name] || name
}
function removeSelection(type: 'metrics' | 'dimensions', name: string) {
  if (type === 'metrics') ui.aloudata.metrics = ui.aloudata.metrics.filter((item) => item !== name)
  else ui.aloudata.dims = ui.aloudata.dims.filter((item) => item !== name)
}
function onMetricCategorySelect(data: AloudataMetricDirectoryNode) {
  selectedMetricCategoryId.value = data.categoryId
}
function onDimensionCategorySelect(data: { categoryId: string }) {
  selectedDimensionCategoryId.value = data.categoryId
  loadDimensions(1)
}
function onDirectoryModeChange(mode: 'metrics' | 'dimensions') {
  if (mode === 'dimensions') loadDimensions(1)
}

async function loadDimensions(page: number) {
  if (!datasourceId.value) return
  dimsLoading.value = true
  try {
    const data = await pageAloudataDimensions(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: directoryKeyword.value.trim() || undefined,
      categoryId: selectedDimensionCategoryId.value === ALL_DIMENSIONS_ID
        ? undefined
        : selectedDimensionCategoryId.value,
    })
    dimPage.records = data.records
    dimPage.total = data.total
    dimPage.current = data.current
    dimPage.size = data.size
    dimPage.pages = data.pages
    data.records.forEach((item) => {
      dimLabelMap[item.dimName] = item.dimDisplayName || item.dimName
    })
  } catch {
    dimPage.records = []
    dimPage.total = 0
    ElMessage.error('加载 Aloudata 维度失败')
  } finally {
    dimsLoading.value = false
  }
}

let directoryKeywordTimer: ReturnType<typeof setTimeout> | undefined
function onDirectoryKeywordInput() {
  if (directoryMode.value !== 'dimensions') return
  if (directoryKeywordTimer) clearTimeout(directoryKeywordTimer)
  directoryKeywordTimer = setTimeout(() => loadDimensions(1), 300)
}
function onDimPageChange(page: number) {
  loadDimensions(page)
}
</script>

<style scoped>
.directory-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.directory-search {
  width: min(390px, 44%);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.directory-layout {
  display: grid;
  grid-template-columns: minmax(190px, 28%) minmax(0, 1fr);
  height: 390px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}
.directory-categories {
  overflow: auto;
  padding: 8px 4px;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}
.directory-categories :deep(.el-tree) {
  background: transparent;
}
.category-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.category-count {
  flex: none;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}
.directory-results {
  min-width: 0;
  overflow: auto;
  padding: 8px 14px;
}
.directory-item-list {
  display: flex;
  flex-direction: column;
}
.directory-item {
  display: flex;
  align-items: center;
  min-height: 44px;
  gap: 8px;
  padding: 5px 4px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer;
}
.directory-item-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.directory-item-title,
.directory-item-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.directory-item-title {
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.directory-item-code {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.directory-empty {
  display: grid;
  min-height: 180px;
  place-items: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.selected-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 88px;
  overflow: auto;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.selected-summary-label {
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.selected-placeholder {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
@media (max-width: 700px) {
  .directory-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .directory-search {
    width: 100%;
  }
  .directory-layout {
    grid-template-columns: 38% minmax(0, 1fr);
    height: 55vh;
  }
}
.pager {
  margin-top: 8px;
  justify-content: flex-end;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
}
.opt-name {
  margin-right: 8px;
}
.opt-tag {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 4px;
  line-height: 18px;
}
.opt-tag.is-mine {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.opt-tag.is-other {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}
</style>
