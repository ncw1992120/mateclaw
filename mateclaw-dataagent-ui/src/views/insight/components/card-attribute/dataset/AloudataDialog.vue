<template>
  <el-dialog v-model="ui.aloudata.visible" :title="aloudataTitle" width="780px" :close-on-click-modal="false">
    <!-- 指标&维度：实时打 Aloudata，双框远程多选 + 可展开「按类目浏览」面板 -->
    <template v-if="ui.aloudata.mode === 'metric-dim'">
      <el-alert
        v-if="!datasourceId"
        type="warning"
        :closable="false"
        title="未关联到数据源，无法加载指标/维度"
        style="margin-bottom: 12px"
      />
      <template v-else>
        <!-- 双框：指标 / 维度，远程搜索多选（实时打 Aloudata，带分页限制） -->
        <div class="box-row">
          <span class="box-label">指标</span>
          <el-select
            v-model="ui.aloudata.metrics"
            multiple
            filterable
            remote
            :remote-method="onMetricRemote"
            :loading="metricSearchLoading"
            clearable
            class="box-select"
            placeholder="搜索并选择指标（实时）"
            @focus="preloadMetrics"
          >
            <el-option v-for="o in metricOptions" :key="o.value" :label="o.label" :value="o.value">
              <el-tooltip placement="right" :show-after="150" :hide-after="0" popper-class="metric-detail-popper">
                <template #content>
                  <div class="md">
                    <div class="md-row"><span class="md-k">业务口径</span><span class="md-v">{{ metricDetailMap[o.value]?.businessCaliber || '—' }}</span></div>
                    <div class="md-row"><span class="md-k">负责人</span><span class="md-v">{{ metricDetailMap[o.value]?.owner || '—' }}</span></div>
                    <div class="md-row"><span class="md-k">同义词</span><span class="md-v">{{ synonymText(o.value) }}</span></div>
                    <div class="md-row"><span class="md-k">关联维度</span><span class="md-v">{{ dimText(o.value) }}</span></div>
                  </div>
                </template>
                <span class="opt-main" @mouseenter="loadMetricDetail(o.value)">{{ o.label }}</span>
              </el-tooltip>
            </el-option>
            <template #tag="{ data, deleteTag }">
              <el-tag
                v-for="item in data"
                :key="item.value"
                class="selected-tag"
                size="small"
                closable
                @close="deleteTag($event, item)"
              >
                {{ metricLabel(item.value) }}
              </el-tag>
            </template>
          </el-select>
        </div>
        <div class="box-row">
          <span class="box-label">维度</span>
          <el-select
            v-model="ui.aloudata.dims"
            multiple
            filterable
            remote
            :remote-method="onDimRemote"
            :loading="dimSearchLoading"
            clearable
            class="box-select"
            placeholder="搜索并选择维度（实时）"
            @focus="preloadDims"
          >
            <el-option v-for="o in dimOptions" :key="o.value" :label="o.label" :value="o.value" />
            <template #tag="{ data, deleteTag }">
              <el-tag
                v-for="item in data"
                :key="item.value"
                class="selected-tag"
                size="small"
                closable
                @close="deleteTag($event, item)"
              >
                {{ dimLabel(item.value) }}
              </el-tag>
            </template>
          </el-select>
        </div>

        <!-- 按类目浏览（可展开） -->
        <el-button text type="primary" class="browse-toggle" @click="toggleBrowse">
          {{ browseVisible ? '收起浏览 ▴' : '按类目浏览 ▾' }}
        </el-button>
        <div v-show="browseVisible" class="browse-panel">
          <div class="toolbar">
            <el-input
              v-model="browseKeyword"
              placeholder="搜索指标/维度名称"
              clearable
              style="width: 200px"
              @input="onBrowseKeywordInput"
            />
            <el-select v-model="metricCategoryId" placeholder="指标类目" clearable style="width: 150px" @change="onMetricCatChange">
              <el-option v-for="c in metricCategoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
            <el-select v-model="dimCategoryId" placeholder="维度类目" clearable style="width: 150px" @change="onDimCatChange">
              <el-option v-for="c in dimCategoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
            <span class="count">已选 指标 {{ ui.aloudata.metrics.length }} · 维度 {{ ui.aloudata.dims.length }}</span>
          </div>

          <div class="grid">
            <div class="grid-col">
              <div class="grid-title">指标</div>
              <el-table
                ref="metricTableRef"
                :data="metricPage.records"
                v-loading="metricsLoading"
                height="320"
                row-key="metricName"
                size="small"
                @selection-change="onMetricSelectionChange"
                @expand-change="onMetricExpandChange"
              >
                <el-table-column type="expand" width="36">
                  <template #default="{ row }">
                    <div class="md">
                      <div class="md-row"><span class="md-k">业务口径</span><span class="md-v">{{ metricDetailMap[row.metricName]?.businessCaliber || row.businessCaliber || '—' }}</span></div>
                      <div class="md-row"><span class="md-k">负责人</span><span class="md-v">{{ metricDetailMap[row.metricName]?.owner || row.owner || '—' }}</span></div>
                      <div class="md-row"><span class="md-k">同义词</span><span class="md-v">{{ synonymText(row.metricName) }}</span></div>
                      <div class="md-row"><span class="md-k">关联维度</span><span class="md-v">{{ dimText(row.metricName) }}</span></div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column type="selection" width="42" :reserve-selection="true" />
                <el-table-column prop="metricName" label="名称" show-overflow-tooltip />
                <el-table-column prop="metricDisplayName" label="显示名" show-overflow-tooltip />
                <el-table-column prop="metricCategoryName" label="类目" width="110" />
              </el-table>
              <el-pagination
                class="pager"
                layout="prev, pager, next"
                :total="metricPage.total"
                :page-size="pageSize"
                :current-page="metricPage.current"
                @current-change="onMetricPageChange"
                small
              />
            </div>

            <div class="grid-col">
              <div class="grid-title">维度</div>
              <el-table
                ref="dimTableRef"
                :data="dimPage.records"
                v-loading="dimsLoading"
                height="320"
                row-key="dimName"
                size="small"
                @selection-change="onDimSelectionChange"
              >
                <el-table-column type="expand" width="36">
                  <template #default="{ row }">
                    <div class="md">
                      <div class="md-row"><span class="md-k">描述</span><span class="md-v">{{ row.dimDescription || '—' }}</span></div>
                      <div class="md-row"><span class="md-k">数据类型</span><span class="md-v">{{ row.originDataType || '—' }}</span></div>
                      <div class="md-row"><span class="md-k">所属数据集</span><span class="md-v">{{ row.datasetName || '—' }}</span></div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column type="selection" width="42" :reserve-selection="true" />
                <el-table-column prop="dimName" label="名称" show-overflow-tooltip />
                <el-table-column prop="dimDisplayName" label="显示名" show-overflow-tooltip />
                <el-table-column prop="configType" label="类型" width="100" />
              </el-table>
              <el-pagination
                class="pager"
                layout="prev, pager, next"
                :total="dimPage.total"
                :page-size="pageSize"
                :current-page="dimPage.current"
                @current-change="onDimPageChange"
                small
              />
            </div>
          </div>
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
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from '../useInsight'
import * as datasourceApi from '@/api/datasource'
import {
  pageAloudataMetrics,
  pageAloudataDimensions,
  listAloudataCategoryCounts,
  getAloudataMetricDetail,
} from '@/api/semantic-model'
import type {
  AloudataMetricPage,
  AloudataDimensionPage,
  AloudataCategoryCount,
  AloudataSyncedMetric,
} from '@/types'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const SEARCH_PAGE_SIZE = 50
const datasourceId = ref('')
const syncing = ref(false)
const metricTableRef = ref<any>()
const dimTableRef = ref<any>()

// 双框：远程搜索选项 + 显示名映射
const metricOptions = ref<{ value: string; label: string }[]>([])
const dimOptions = ref<{ value: string; label: string }[]>([])
const metricLabelMap = reactive<Record<string, string>>({})
const dimLabelMap = reactive<Record<string, string>>({})
const metricSearchLoading = ref(false)
const dimSearchLoading = ref(false)

// 指标详情（懒加载：悬停选项 / 展开行时才请求一次）
const metricDetailMap = reactive<Record<string, AloudataSyncedMetric>>({})
const metricDetailLoading = reactive<Record<string, boolean>>({})

// 浏览面板
const browseVisible = ref(false)
const browseKeyword = ref('')
const metricCategoryId = ref('')
const dimCategoryId = ref('')
const metricCategoryOptions = ref<{ label: string; value: string }[]>([])
const dimCategoryOptions = ref<{ label: string; value: string }[]>([])

const metricsLoading = ref(false)
const dimsLoading = ref(false)

/** 指标视图列表（来自后端 analysis-views/list，非假数据；带 owner/mine 归属） */
const analysisViews = ref<datasourceApi.AloudataAnalysisViewItem[]>([])
/** 指标视图搜索关键字 */
const viewKeyword = ref('')
/** 只看我创建的指标视图（按 owner 归属过滤） */
const onlyMine = ref(true)
const viewsLoading = ref(false)

const metricPage = reactive<AloudataMetricPage>({
  records: [],
  total: 0,
  size: pageSize,
  current: 1,
  pages: 0,
})
const dimPage = reactive<AloudataDimensionPage>({
  records: [],
  total: 0,
  size: pageSize,
  current: 1,
  pages: 0,
})

watch(
  () => ui.aloudata.visible,
  (v) => {
    if (v) open()
  },
)

function open() {
  datasourceId.value = ui.aloudata.datasourceId
  browseKeyword.value = ''
  metricCategoryId.value = ''
  dimCategoryId.value = ''
  viewKeyword.value = ''
  onlyMine.value = true
  metricOptions.value = []
  dimOptions.value = []
  Object.keys(metricLabelMap).forEach((k) => delete metricLabelMap[k])
  Object.keys(dimLabelMap).forEach((k) => delete dimLabelMap[k])
  Object.keys(metricDetailMap).forEach((k) => delete metricDetailMap[k])
  Object.keys(metricDetailLoading).forEach((k) => delete metricDetailLoading[k])
  browseVisible.value = false
  if (!datasourceId.value) {
    ElMessage.warning('未关联到数据源，无法加载指标/维度')
    return
  }
  if (ui.aloudata.mode === 'metric-view') {
    loadAnalysisViews()
    return
  }
  loadMetricCategories()
  loadDimCategories()
  preloadMetrics()
  preloadDims()
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

// ---- 类目（实时，按类型拆分）----
async function loadMetricCategories() {
  try {
    const list = await listAloudataCategoryCounts(datasourceId.value, 'metric')
    metricCategoryOptions.value = (list as AloudataCategoryCount[]).map((c) => ({
      label: c.categoryName,
      value: c.categoryId,
    }))
  } catch {
    metricCategoryOptions.value = []
  }
}
async function loadDimCategories() {
  try {
    const list = await listAloudataCategoryCounts(datasourceId.value, 'dimension')
    dimCategoryOptions.value = (list as AloudataCategoryCount[]).map((c) => ({
      label: c.categoryName,
      value: c.categoryId,
    }))
  } catch {
    dimCategoryOptions.value = []
  }
}

// ---- 双框：远程搜索多选（实时打 Aloudata，带分页限制，不依赖本地同步）----
async function onMetricRemote(query: string) {
  metricSearchLoading.value = true
  try {
    const data = await pageAloudataMetrics(datasourceId.value, {
      pageNumber: 1,
      pageSize: SEARCH_PAGE_SIZE,
      keyword: query || undefined,
    })
    data.records.forEach((r) => {
      if (r.metricDisplayName) metricLabelMap[r.metricName] = r.metricDisplayName
    })
    metricOptions.value = mergeSelectedOptions(
      data.records.map((r) => ({ value: r.metricName, label: r.metricDisplayName || r.metricName })),
      ui.aloudata.metrics,
      metricLabelMap,
    )
  } catch {
    ElMessage.error('指标搜索失败')
  } finally {
    metricSearchLoading.value = false
  }
}
async function onDimRemote(query: string) {
  dimSearchLoading.value = true
  try {
    const data = await pageAloudataDimensions(datasourceId.value, {
      pageNumber: 1,
      pageSize: SEARCH_PAGE_SIZE,
      keyword: query || undefined,
    })
    data.records.forEach((r) => {
      if (r.dimDisplayName) dimLabelMap[r.dimName] = r.dimDisplayName
    })
    dimOptions.value = mergeSelectedOptions(
      data.records.map((r) => ({ value: r.dimName, label: r.dimDisplayName || r.dimName })),
      ui.aloudata.dims,
      dimLabelMap,
    )
  } catch {
    ElMessage.error('维度搜索失败')
  } finally {
    dimSearchLoading.value = false
  }
}
async function preloadMetrics() {
  if (metricOptions.value.length) return
  await onMetricRemote('')
}
async function preloadDims() {
  if (dimOptions.value.length) return
  await onDimRemote('')
}
function metricLabel(v: string) {
  return metricLabelMap[v] || v
}
function dimLabel(v: string) {
  return dimLabelMap[v] || v
}
function mergeSelectedOptions(
  options: { value: string; label: string }[],
  selected: string[],
  labels: Record<string, string>,
) {
  const result = [...options]
  selected.forEach((value) => {
    if (!result.some((option) => option.value === value)) {
      result.push({ value, label: labels[value] || value })
    }
  })
  return result
}

// ---- 指标详情（懒加载：悬停选项 / 展开行时才请求，结果缓存）----
async function loadMetricDetail(name: string) {
  if (!name || metricDetailMap[name] || metricDetailLoading[name] || !datasourceId.value) return
  metricDetailLoading[name] = true
  try {
    metricDetailMap[name] = await getAloudataMetricDetail(datasourceId.value, name)
  } catch {
    // 详情为非关键路径，静默失败
  } finally {
    metricDetailLoading[name] = false
  }
}
function synonymText(name: string) {
  const d = metricDetailMap[name]
  if (!d) return metricDetailLoading[name] ? '加载中…' : '—'
  const s = (d.synonyms || []).filter(Boolean)
  return s.length ? s.join('、') : '—'
}
function dimText(name: string) {
  const d = metricDetailMap[name]
  if (!d) return metricDetailLoading[name] ? '加载中…' : '—'
  const s = (d.availableDimensions || []).filter(Boolean)
  return s.length ? s.join('、') : '—'
}
function onMetricExpandChange(row: any) {
  if (row?.metricName) loadMetricDetail(row.metricName)
}

// ---- 浏览面板（实时分页，类目按指标/维度拆分）----
function toggleBrowse() {
  browseVisible.value = !browseVisible.value
  if (browseVisible.value && metricPage.records.length === 0 && metricPage.total === 0) {
    loadMetrics(1)
    loadDims(1)
  }
}

async function loadMetrics(page: number) {
  if (!datasourceId.value) return
  metricsLoading.value = true
  syncing.value = true
  try {
    metricTableRef.value?.clearSelection()
    const data = await pageAloudataMetrics(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: browseKeyword.value || undefined,
      categoryId: metricCategoryId.value || undefined,
    })
    metricPage.records = data.records
    metricPage.total = data.total
    metricPage.current = data.current
    metricPage.size = data.size
    metricPage.pages = data.pages
    await nextTick()
    metricPage.records.forEach((r) => {
      if (ui.aloudata.metrics.includes(r.metricName)) metricTableRef.value?.toggleRowSelection(r, true)
    })
  } catch {
    ElMessage.error('加载指标失败')
  } finally {
    syncing.value = false
    metricsLoading.value = false
  }
}

async function loadDims(page: number) {
  if (!datasourceId.value) return
  dimsLoading.value = true
  syncing.value = true
  try {
    dimTableRef.value?.clearSelection()
    const data = await pageAloudataDimensions(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: browseKeyword.value || undefined,
      categoryId: dimCategoryId.value || undefined,
    })
    dimPage.records = data.records
    dimPage.total = data.total
    dimPage.current = data.current
    dimPage.size = data.size
    dimPage.pages = data.pages
    await nextTick()
    dimPage.records.forEach((r) => {
      if (ui.aloudata.dims.includes(r.dimName)) dimTableRef.value?.toggleRowSelection(r, true)
    })
  } catch {
    ElMessage.error('加载维度失败')
  } finally {
    syncing.value = false
    dimsLoading.value = false
  }
}

function onMetricSelectionChange(rows: any[]) {
  if (syncing.value) return
  rows.forEach((r) => {
    if (r.metricDisplayName) metricLabelMap[r.metricName] = r.metricDisplayName
  })
  ui.aloudata.metrics = rows.map((r) => r.metricName)
}
function onDimSelectionChange(rows: any[]) {
  if (syncing.value) return
  rows.forEach((r) => {
    if (r.dimDisplayName) dimLabelMap[r.dimName] = r.dimDisplayName
  })
  ui.aloudata.dims = rows.map((r) => r.dimName)
}

let browseKwTimer: any
function onBrowseKeywordInput() {
  clearTimeout(browseKwTimer)
  browseKwTimer = setTimeout(() => {
    loadMetrics(1)
    loadDims(1)
  }, 300)
}
function onMetricCatChange() {
  loadMetrics(1)
}
function onDimCatChange() {
  loadDims(1)
}
function onMetricPageChange(p: number) {
  loadMetrics(p)
}
function onDimPageChange(p: number) {
  loadDims(p)
}
</script>

<style scoped>
.box-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.box-label {
  width: 40px;
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.box-select {
  flex: 1;
}
.box-select :deep(.el-select__wrapper) {
  min-height: 32px;
  height: auto;
  align-items: flex-start;
  padding-top: 4px;
  padding-bottom: 4px;
}
.box-select :deep(.el-select__selection),
.box-select :deep(.el-select__tags) {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 24px;
  height: auto;
  max-width: calc(100% - 24px);
}
.box-select :deep(.el-select__selected-item) {
  max-width: 100%;
}
.box-select :deep(.selected-tag) {
  margin: 0;
  max-width: 100%;
}
.browse-toggle {
  padding-left: 0;
  margin-bottom: 8px;
}
.browse-panel {
  border-top: 1px dashed var(--el-border-color-light);
  padding-top: 12px;
}
.opt-main {
  display: block;
  width: 100%;
}
.md {
  max-width: 420px;
  font-size: 12px;
  line-height: 1.8;
}
.md-row {
  display: flex;
  gap: 8px;
}
.md-k {
  flex: none;
  width: 56px;
  color: var(--el-text-color-secondary);
}
.md-v {
  flex: 1;
  word-break: break-all;
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
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.grid-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
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
