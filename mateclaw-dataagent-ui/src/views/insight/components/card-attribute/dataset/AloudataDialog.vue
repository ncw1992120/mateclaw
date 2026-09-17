<template>
  <el-dialog v-model="ui.aloudata.visible" :title="aloudataTitle" width="780px" :close-on-click-modal="false">
    <!-- 指标&维度：从 Aloudata 已同步数据真实取数 + 勾选 -->
    <template v-if="ui.aloudata.mode === 'metric-dim'">
      <el-alert
        v-if="!datasourceId"
        type="warning"
        :closable="false"
        title="未关联到数据源，无法加载指标/维度"
        style="margin-bottom: 12px"
      />
      <template v-else>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索指标/维度名称"
            clearable
            style="width: 220px"
            @input="onKeywordInput"
          />
          <el-select
            v-model="categoryId"
            placeholder="全部类目"
            clearable
            style="width: 200px"
            @change="onCategoryChange"
          >
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
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
            >
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
} from '@/api/semantic-model'
import type { AloudataMetricPage, AloudataDimensionPage, AloudataCategoryCount } from '@/types'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const datasourceId = ref('')
const keyword = ref('')
const categoryId = ref('')
const categoryOptions = ref<{ label: string; value: string }[]>([])
const metricsLoading = ref(false)
const dimsLoading = ref(false)
const syncing = ref(false)
const metricTableRef = ref<any>()
const dimTableRef = ref<any>()

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
  keyword.value = ''
  categoryId.value = ''
  viewKeyword.value = ''
  onlyMine.value = true
  if (!datasourceId.value) {
    ElMessage.warning('未关联到数据源，无法加载指标/维度')
    return
  }
  if (ui.aloudata.mode === 'metric-view') {
    loadAnalysisViews()
    return
  }
  loadCategories()
  loadMetrics(1)
  loadDims(1)
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

async function loadCategories() {
  try {
    const [m, d] = await Promise.all([
      listAloudataCategoryCounts(datasourceId.value, 'metric'),
      listAloudataCategoryCounts(datasourceId.value, 'dimension'),
    ])
    const opts: { label: string; value: string }[] = []
    ;(m as AloudataCategoryCount[]).forEach((c) =>
      opts.push({ label: `指标·${c.categoryName} (${c.count})`, value: c.categoryId }),
    )
    ;(d as AloudataCategoryCount[]).forEach((c) =>
      opts.push({ label: `维度·${c.categoryName} (${c.count})`, value: c.categoryId }),
    )
    categoryOptions.value = opts
  } catch {
    categoryOptions.value = []
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
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
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
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
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
  ui.aloudata.metrics = rows.map((r) => r.metricName)
}
function onDimSelectionChange(rows: any[]) {
  if (syncing.value) return
  ui.aloudata.dims = rows.map((r) => r.dimName)
}

let kwTimer: any
function onKeywordInput() {
  clearTimeout(kwTimer)
  kwTimer = setTimeout(() => {
    loadMetrics(1)
    loadDims(1)
  }, 300)
}
function onCategoryChange() {
  loadMetrics(1)
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
