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
        <div class="selection-fields">
          <el-alert
            v-if="selectionConflict"
            class="selection-conflict"
            type="error"
            :closable="false"
            title="已选指标或维度存在不兼容组合，请调整选择后再确定"
          />
          <el-alert
            v-else-if="selectedMetricRelationsUnverified"
            class="selection-validation-state"
            type="warning"
            :closable="false"
            :title="selectedMetricRelationsLoading ? '正在校验已选指标的可用维度…' : '无法校验已选指标的可用维度，请重试或移除该指标'"
          />
          <div class="selection-row">
            <span class="selection-label">指标</span>
            <el-popover
              v-model:visible="metricPickerVisible"
              trigger="click"
              placement="bottom-start"
              :width="430"
              popper-class="aloudata-picker-popper"
              @show="onMetricPickerShow"
            >
              <template #reference>
                <div class="selection-box metric-selection-box" role="button" tabindex="0" aria-haspopup="dialog">
                  <el-tag
                    v-for="name in ui.aloudata.metrics"
                    :key="name"
                    closable
                    size="small"
                    @close.stop="removeSelection('metrics', name)"
                  >{{ metricLabel(name) }}</el-tag>
                  <span v-if="!ui.aloudata.metrics.length" class="selection-placeholder">点击选择指标</span>
                </div>
              </template>
              <div class="picker-panel metric-picker-popup" @click.stop>
                <div class="picker-heading">
                  <strong>选择指标</strong>
                  <button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" @click="hideUnavailable = !hideUnavailable">
                    <span>自动隐藏不可分析内容</span><i :class="{ 'is-on': hideUnavailable }" />
                  </button>
                </div>
                <div class="picker-toolbar">
                  <el-input
                    v-model="metricKeyword"
                    placeholder="搜索指标展示名或字段名称"
                    clearable
                    @input="onMetricKeywordInput"
                  />
                  <span>{{ metricPage.total }} 个指标</span>
                </div>
                <div class="directory-layout">
                  <aside class="directory-categories" aria-label="指标目录">
                    <el-tree
                      :data="filteredMetricCategories"
                      :props="categoryTreeProps"
                      node-key="categoryId"
                      highlight-current
                      :default-expanded-keys="expandedMetricCategoryKeys"
                      :expand-on-click-node="false"
                      @node-click="onMetricCategorySelect"
                    />
                    <div v-if="!metricCategories.length" class="directory-empty">暂无指标目录</div>
                  </aside>
                  <section class="directory-results" v-loading="metricsLoading">
                    <label
                      v-for="item in visibleMetrics"
                      :key="item.metricName"
                      class="directory-item"
                      :class="{ 'is-unavailable': isMetricUnavailable(item) && !ui.aloudata.metrics.includes(item.metricName) }"
                    >
                      <el-tooltip
                        v-if="isMetricUnavailable(item) && !ui.aloudata.metrics.includes(item.metricName)"
                        :content="metricUnavailableReason(item)"
                        placement="top"
                      >
                        <span class="disabled-checkbox-target">
                          <el-checkbox
                            :model-value="ui.aloudata.metrics.includes(item.metricName)"
                            :label="item.metricName"
                            disabled
                            @change="(checked: boolean) => toggleSelection('metrics', item.metricName, checked)"
                          />
                        </span>
                      </el-tooltip>
                      <el-checkbox
                        v-else
                        :model-value="ui.aloudata.metrics.includes(item.metricName)"
                        :label="item.metricName"
                        @change="(checked: boolean) => toggleSelection('metrics', item.metricName, checked)"
                      />
                      <el-popover
                        trigger="hover"
                        placement="right"
                        :width="290"
                        :show-after="250"
                        popper-class="aloudata-detail-popper"
                        @show="loadMetricDetail(item.metricName)"
                      >
                        <template #reference>
                          <span class="directory-item-info metric-detail-trigger" :data-metric-name="item.metricName">
                            <span class="directory-item-title">{{ item.metricDisplayName || item.metricName }}</span>
                            <span class="directory-item-code">{{ item.metricName }}</span>
                          </span>
                        </template>
                        <div class="aloudata-detail-card metric-detail-card">
                          <div class="detail-card-title"><span class="detail-kind">123</span>{{ metricDetails[item.metricName]?.metricDisplayName || item.metricDisplayName || item.metricName }}</div>
                          <div v-if="metricDetailLoading[item.metricName]" class="detail-loading">加载详情中…</div>
                          <template v-else>
                            <div class="detail-row"><span>指标编码：</span>{{ item.metricName }}</div>
                            <div class="detail-row"><span>指标名称：</span>{{ metricDetails[item.metricName]?.metricDisplayName || item.metricDisplayName || '—' }}</div>
                            <div class="detail-row"><span>指标类型：</span>{{ metricDetails[item.metricName]?.type || item.type || '—' }}</div>
                            <div class="detail-row"><span>业务口径：</span>{{ metricDetails[item.metricName]?.businessCaliber || item.businessCaliber || '—' }}</div>
                            <div class="detail-row"><span>单位：</span>{{ metricDetails[item.metricName]?.unit || item.unit || '—' }}</div>
                            <div class="detail-row"><span>负责人：</span>{{ metricDetails[item.metricName]?.owner || item.owner || '—' }}</div>
                          </template>
                        </div>
                      </el-popover>
                    </label>
                    <div v-if="!metricsLoading && !visibleMetrics.length" class="directory-empty">
                      {{ hideUnavailable && metricPage.records.length ? '已隐藏不可分析的指标' : '没有匹配的指标' }}
                    </div>
                    <el-pagination
                      v-if="metricPage.total > pageSize"
                      class="pager"
                      layout="prev, pager, next, total"
                      :total="metricPage.total"
                      :page-size="pageSize"
                      :current-page="metricPage.current"
                      @current-change="onMetricPageChange"
                      small
                    />
                  </section>
                </div>
              </div>
            </el-popover>
          </div>

          <div class="selection-row">
            <span class="selection-label">维度</span>
            <el-popover
              v-model:visible="dimensionPickerVisible"
              trigger="click"
              placement="bottom-start"
              :width="430"
              popper-class="aloudata-picker-popper"
              @show="onDimensionPickerShow"
            >
              <template #reference>
                <div class="selection-box dimension-selection-box" role="button" tabindex="0" aria-haspopup="dialog">
                  <el-tag
                    v-for="name in ui.aloudata.dims"
                    :key="name"
                    closable
                    size="small"
                    type="info"
                    @close.stop="removeSelection('dimensions', name)"
                  >{{ dimLabel(name) }}</el-tag>
                  <span v-if="!ui.aloudata.dims.length" class="selection-placeholder">点击选择维度</span>
                </div>
              </template>
              <div class="picker-panel dimension-picker-popup" @click.stop>
                <div class="picker-heading">
                  <strong>选择维度</strong>
                  <button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" @click="hideUnavailable = !hideUnavailable">
                    <span>自动隐藏不可分析内容</span><i :class="{ 'is-on': hideUnavailable }" />
                  </button>
                </div>
                <div class="picker-toolbar">
                  <el-input
                    v-model="dimensionKeyword"
                    placeholder="搜索维度展示名或字段名称"
                    clearable
                    @input="onDimensionKeywordInput"
                  />
                  <span>{{ dimensionPage.total }} 个维度</span>
                </div>
                <div class="directory-layout">
                  <aside class="directory-categories" aria-label="维度目录">
                    <el-tree
                      :data="filteredDimensionCategories"
                      :props="categoryTreeProps"
                      node-key="categoryId"
                      highlight-current
                      :default-expanded-keys="['__all_dimensions__']"
                      :expand-on-click-node="false"
                      @node-click="onDimensionCategorySelect"
                    />
                    <div v-if="!dimensionCategories.length" class="directory-empty">暂无维度目录</div>
                  </aside>
                  <section class="directory-results" v-loading="dimensionsLoading">
                    <label
                      v-for="item in visibleDimensions"
                      :key="item.dimName"
                      class="directory-item"
                      :class="{ 'is-unavailable': dimensionUnavailableReason(item.dimName) && !ui.aloudata.dims.includes(item.dimName) }"
                    >
                      <el-tooltip
                        v-if="dimensionUnavailableReason(item.dimName) && !ui.aloudata.dims.includes(item.dimName)"
                        :content="dimensionUnavailableReason(item.dimName)"
                        placement="top"
                      >
                        <span class="disabled-checkbox-target">
                          <el-checkbox
                            :model-value="ui.aloudata.dims.includes(item.dimName)"
                            :label="item.dimName"
                            disabled
                            @change="(checked: boolean) => toggleSelection('dimensions', item.dimName, checked)"
                          />
                        </span>
                      </el-tooltip>
                      <el-checkbox
                        v-else
                        :model-value="ui.aloudata.dims.includes(item.dimName)"
                        :label="item.dimName"
                        @change="(checked: boolean) => toggleSelection('dimensions', item.dimName, checked)"
                      />
                      <el-popover
                        trigger="hover"
                        placement="right"
                        :width="290"
                        :show-after="250"
                        popper-class="aloudata-detail-popper"
                        @show="loadDimensionDetail(item.dimName)"
                      >
                        <template #reference>
                          <span class="directory-item-info dimension-detail-trigger" :data-dimension-name="item.dimName">
                            <span class="directory-item-title">{{ item.dimDisplayName || item.dimName }}</span>
                            <span class="directory-item-code">{{ item.dimName }}</span>
                          </span>
                        </template>
                        <div class="aloudata-detail-card dimension-detail-card">
                          <div class="detail-card-title"><span class="detail-kind">abc</span>{{ dimensionDetails[item.dimName]?.dimDisplayName || item.dimDisplayName || item.dimName }}</div>
                          <div v-if="dimensionDetailLoading[item.dimName]" class="detail-loading">加载详情中…</div>
                          <template v-else>
                            <div class="detail-row"><span>维度编码：</span>{{ item.dimName }}</div>
                            <div class="detail-row"><span>维度名称：</span>{{ dimensionDetails[item.dimName]?.dimDisplayName || item.dimDisplayName || '—' }}</div>
                            <div class="detail-row"><span>数据类型：</span>{{ dimensionDetails[item.dimName]?.originDataType || item.originDataType || '—' }}</div>
                            <div class="detail-row"><span>维度描述：</span>{{ dimensionDetails[item.dimName]?.dimDescription || item.dimDescription || '—' }}</div>
                          </template>
                        </div>
                      </el-popover>
                    </label>
                    <div v-if="!dimensionsLoading && !visibleDimensions.length" class="directory-empty">
                      {{ hideUnavailable && dimensionPage.records.length ? '已隐藏不可分析的维度' : '没有匹配的维度' }}
                    </div>
                    <el-pagination
                      v-if="dimensionPage.total > pageSize"
                      class="pager"
                      layout="prev, pager, next, total"
                      :total="dimensionPage.total"
                      :page-size="pageSize"
                      :current-page="dimensionPage.current"
                      @current-change="onDimensionPageChange"
                      small
                    />
                  </section>
                </div>
              </div>
            </el-popover>
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
      <el-button type="primary" :disabled="!datasourceId || selectionConflict || selectedMetricRelationsUnverified" @click="confirmAloudata">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from '../useInsight'
import * as datasourceApi from '@/api/datasource'
import {
  getAloudataMetricDetail,
  getAloudataDimensionDetail,
  pageAloudataMetrics,
  pageAloudataDimensions,
  listAloudataCategoryCounts,
} from '@/api/semantic-model'
import type {
  AloudataMetricPage,
  AloudataDimensionPage,
  AloudataCategoryCount,
  AloudataSyncedMetric,
  AloudataSyncedDimension,
} from '@/types'
import { buildCategoryTree, filterCategoryTree } from './aloudata-metric-directory'
import type { AloudataCategoryTreeNode } from './aloudata-metric-directory'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const ALL_DIMENSIONS_ID = '__all_dimensions__'
const datasourceId = ref('')
const metricPickerVisible = ref(false)
const dimensionPickerVisible = ref(false)
const metricKeyword = ref('')
const dimensionKeyword = ref('')
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)
const hideUnavailable = ref(false)
const metricCategories = ref<AloudataCategoryTreeNode[]>([])
const dimensionCategories = ref<AloudataCategoryTreeNode[]>([])
const selectedMetricCategoryId = ref('')
const selectedDimensionCategoryId = ref(ALL_DIMENSIONS_ID)
const metricLabelMap = reactive<Record<string, string>>({})
const dimLabelMap = reactive<Record<string, string>>({})
const metricDimensions = reactive<Record<string, string[]>>({})
const metricDetails = reactive<Record<string, AloudataSyncedMetric>>({})
const dimensionDetails = reactive<Record<string, AloudataSyncedDimension>>({})
const metricDetailLoading = reactive<Record<string, boolean>>({})
const dimensionDetailLoading = reactive<Record<string, boolean>>({})
const selectedMetricRelationsLoading = ref(false)
const categoryTreeProps = { label: 'categoryName', children: 'children' }

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
const dimensionPage = reactive<AloudataDimensionPage>({
  records: [],
  total: 0,
  size: pageSize,
  current: 1,
  pages: 0,
})

const selectedMetricRelationsReady = computed(() => ui.aloudata.metrics.every((name) => Array.isArray(metricDimensions[name])))
const selectedMetricViewsConflict = computed(() => {
  if (ui.aloudata.metrics.length < 2 || !selectedMetricRelationsReady.value) return false
  return new Set(ui.aloudata.metrics.map((name) => dimensionRelationKey(metricDimensions[name]))).size > 1
})
const visibleMetrics = computed(() => hideUnavailable.value
  ? metricPage.records.filter((item) => !isMetricUnavailable(item))
  : metricPage.records)
const visibleDimensions = computed(() => hideUnavailable.value
  ? dimensionPage.records.filter((item) => !dimensionUnavailableReason(item.dimName))
  : dimensionPage.records)
const selectedMetricRelationsUnverified = computed(() => ui.aloudata.metrics.length > 0 && !selectedMetricRelationsReady.value)
const selectionConflict = computed(() => {
  if (!ui.aloudata.metrics.length || !selectedMetricRelationsReady.value) return false
  if (selectedMetricViewsConflict.value) return true
  if (!ui.aloudata.dims.length) return false
  return ui.aloudata.metrics.some((metricName) =>
    ui.aloudata.dims.some((dimName) => !metricDimensions[metricName].includes(dimName)),
  )
})

const dimensionCategoryTree = computed(() => [{
  categoryId: ALL_DIMENSIONS_ID,
  categoryName: '全部维度',
  children: dimensionCategories.value,
}])
const filteredMetricCategories = computed(() => filterCategoryTree(metricCategories.value, metricKeyword.value))
const filteredDimensionCategories = computed(() => filterCategoryTree(dimensionCategoryTree.value, dimensionKeyword.value))
const expandedMetricCategoryKeys = computed(() => {
  const keys: string[] = []
  const visit = (nodes: AloudataCategoryTreeNode[]) => nodes.forEach((node) => {
    keys.push(node.categoryId)
    visit(node.children)
  })
  if (metricKeyword.value.trim()) visit(filteredMetricCategories.value)
  return keys
})

watch(
  () => ui.aloudata.visible,
  (v) => {
    if (v) open()
  },
)

function open() {
  datasourceId.value = ui.aloudata.datasourceId
  metricPickerVisible.value = false
  dimensionPickerVisible.value = false
  hideUnavailable.value = false
  metricKeyword.value = ''
  dimensionKeyword.value = ''
  metricPage.records = []
  metricPage.total = 0
  dimensionPage.records = []
  dimensionPage.total = 0
  selectedMetricCategoryId.value = ''
  selectedDimensionCategoryId.value = ALL_DIMENSIONS_ID
  metricCategories.value = []
  dimensionCategories.value = []
  viewKeyword.value = ''
  onlyMine.value = true
  Object.keys(metricLabelMap).forEach((k) => delete metricLabelMap[k])
  Object.keys(dimLabelMap).forEach((k) => delete dimLabelMap[k])
  Object.keys(metricDimensions).forEach((k) => delete metricDimensions[k])
  Object.keys(metricDetails).forEach((k) => delete metricDetails[k])
  Object.keys(dimensionDetails).forEach((k) => delete dimensionDetails[k])
  Object.keys(metricDetailLoading).forEach((k) => delete metricDetailLoading[k])
  Object.keys(dimensionDetailLoading).forEach((k) => delete dimensionDetailLoading[k])
  if (!datasourceId.value) {
    ElMessage.warning('未关联到数据源，无法加载指标/维度')
    return
  }
  if (ui.aloudata.mode === 'metric-view') {
    loadAnalysisViews()
    return
  }
  void loadSelectedMetricRelations(ui.aloudata.metrics)
  loadMetricCategories()
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

let viewKwTimer: ReturnType<typeof setTimeout> | undefined
function onViewKeywordInput() {
  if (viewKwTimer) clearTimeout(viewKwTimer)
  viewKwTimer = setTimeout(() => loadAnalysisViews(), 300)
}

async function loadMetricCategories() {
  try {
    const categories = await listAloudataCategoryCounts(datasourceId.value, 'CATEGORY_METRIC')
    metricCategories.value = buildCategoryTree((categories as AloudataCategoryCount[]).map((item) => ({
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      parentId: item.parentId,
    })))
  } catch {
    metricCategories.value = []
    ElMessage.error('加载 Aloudata 指标目录失败')
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

async function loadSelectedMetricRelations(metricNames: string[]) {
  const missing = [...new Set(metricNames)].filter((name) => !Array.isArray(metricDimensions[name]))
  if (!missing.length) return
  selectedMetricRelationsLoading.value = true
  try {
    const relations = await Promise.all(missing.map((name) => getAloudataMetricDetail(datasourceId.value, name)))
    relations.forEach((relation, index) => {
      metricDetails[missing[index]] = relation
      if (Array.isArray(relation.availableDimensions)) {
        metricDimensions[missing[index]] = relation.availableDimensions
      }
    })
  } catch {
    ElMessage.error('加载已选指标的可用维度失败，请重试')
  } finally {
    selectedMetricRelationsLoading.value = false
  }
}

async function loadMetricDetail(metricName: string) {
  if (metricDetails[metricName] || metricDetailLoading[metricName]) return
  metricDetailLoading[metricName] = true
  try {
    metricDetails[metricName] = await getAloudataMetricDetail(datasourceId.value, metricName)
  } catch {
    // Keep the live list fields visible if the optional hover-detail request fails.
  } finally {
    metricDetailLoading[metricName] = false
  }
}

async function loadDimensionDetail(dimName: string) {
  if (dimensionDetails[dimName] || dimensionDetailLoading[dimName]) return
  dimensionDetailLoading[dimName] = true
  try {
    dimensionDetails[dimName] = await getAloudataDimensionDetail(datasourceId.value, dimName)
  } catch {
    // Keep the live list fields visible if the optional hover-detail request fails.
  } finally {
    dimensionDetailLoading[dimName] = false
  }
}

watch(
  () => [...ui.aloudata.metrics],
  (metricNames) => { void loadSelectedMetricRelations(metricNames) },
)

function dimensionUnavailableReason(dimName: string) {
  if (!ui.aloudata.metrics.length) return ''
  if (!selectedMetricRelationsReady.value) {
    return selectedMetricRelationsLoading.value
      ? '正在校验已选指标的可用维度'
      : '无法校验已选指标的可用维度'
  }
  if (selectedMetricViewsConflict.value) return '已选指标来自不同视图，不能组合分析'
  return ui.aloudata.metrics.some((metricName) => !metricDimensions[metricName].includes(dimName))
    ? '该维度不是已选指标的可用维度'
    : ''
}

function dimensionRelationKey(dimensions: string[] | null | undefined) {
  return Array.isArray(dimensions) ? [...new Set(dimensions)].sort().join('\u0000') : null
}

function isMetricUnavailable(item: { availableDimensions?: string[] | null }) {
  if (ui.aloudata.metrics.length > 0 && selectedMetricRelationsReady.value) {
    const selectedRelation = metricDimensions[ui.aloudata.metrics[0] ?? '']
    if (dimensionRelationKey(selectedRelation) !== dimensionRelationKey(item.availableDimensions)) return true
  }
  return ui.aloudata.dims.length > 0 && (!Array.isArray(item.availableDimensions)
    || ui.aloudata.dims.some((dimName) => !item.availableDimensions!.includes(dimName)))
}

function metricUnavailableReason(item: { availableDimensions?: string[] | null }) {
  if (ui.aloudata.metrics.length > 0 && selectedMetricRelationsReady.value && Array.isArray(item.availableDimensions)
    && dimensionRelationKey(metricDimensions[ui.aloudata.metrics[0] ?? '']) !== dimensionRelationKey(item.availableDimensions)) {
    return '该指标来自其他视图，不能与已选指标组合分析'
  }
  return Array.isArray(item.availableDimensions)
    ? '该指标不支持已选维度'
    : '无法校验该指标对已选维度的支持情况'
}
function onMetricCategorySelect(data: AloudataCategoryTreeNode) {
  selectedMetricCategoryId.value = data.categoryId
  loadMetrics(1)
}
function onDimensionCategorySelect(data: { categoryId: string }) {
  selectedDimensionCategoryId.value = data.categoryId
  loadDimensions(1)
}
function onMetricPickerShow() {
  dimensionPickerVisible.value = false
  if (!metricPage.records.length) loadMetrics(1)
}
function onDimensionPickerShow() {
  metricPickerVisible.value = false
  if (!dimensionPage.records.length) loadDimensions(1)
}

async function loadMetrics(page: number) {
  if (!datasourceId.value) return
  metricsLoading.value = true
  try {
    const data = await pageAloudataMetrics(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: metricKeyword.value.trim() || undefined,
      categoryId: selectedMetricCategoryId.value || undefined,
    })
    Object.assign(metricPage, data)
    data.records.forEach((item) => { metricLabelMap[item.metricName] = item.metricDisplayName || item.metricName })
  } catch {
    metricPage.records = []
    metricPage.total = 0
    ElMessage.error('加载 Aloudata 指标失败')
  } finally {
    metricsLoading.value = false
  }
}

async function loadDimensions(page: number) {
  if (!datasourceId.value) return
  dimensionsLoading.value = true
  try {
    const data = await pageAloudataDimensions(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: dimensionKeyword.value.trim() || undefined,
      categoryId: selectedDimensionCategoryId.value === ALL_DIMENSIONS_ID ? undefined : selectedDimensionCategoryId.value,
    })
    Object.assign(dimensionPage, data)
    data.records.forEach((item) => { dimLabelMap[item.dimName] = item.dimDisplayName || item.dimName })
  } catch {
    dimensionPage.records = []
    dimensionPage.total = 0
    ElMessage.error('加载 Aloudata 维度失败')
  } finally {
    dimensionsLoading.value = false
  }
}

function toggleSelection(type: 'metrics' | 'dimensions', name: string, checked: boolean) {
  const selected = type === 'metrics' ? ui.aloudata.metrics : ui.aloudata.dims
  if (checked && !selected.includes(name)) selected.push(name)
  if (!checked) {
    const next = selected.filter((item) => item !== name)
    if (type === 'metrics') ui.aloudata.metrics = next
    else ui.aloudata.dims = next
  }
}

let metricKeywordTimer: ReturnType<typeof setTimeout> | undefined
let dimensionKeywordTimer: ReturnType<typeof setTimeout> | undefined
function onMetricKeywordInput() {
  if (metricKeywordTimer) clearTimeout(metricKeywordTimer)
  metricKeywordTimer = setTimeout(() => {
    selectedMetricCategoryId.value = ''
    loadMetrics(1)
  }, 300)
}
function onDimensionKeywordInput() {
  if (dimensionKeywordTimer) clearTimeout(dimensionKeywordTimer)
  dimensionKeywordTimer = setTimeout(() => {
    selectedDimensionCategoryId.value = ALL_DIMENSIONS_ID
    loadDimensions(1)
  }, 300)
}
function onMetricPageChange(page: number) { loadMetrics(page) }
function onDimensionPageChange(page: number) { loadDimensions(page) }

onBeforeUnmount(() => {
  if (viewKwTimer) clearTimeout(viewKwTimer)
  if (metricKeywordTimer) clearTimeout(metricKeywordTimer)
  if (dimensionKeywordTimer) clearTimeout(dimensionKeywordTimer)
})
</script>

<style scoped>
.selection-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.selection-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
}
.selection-label {
  padding-top: 9px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.selection-box {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  min-height: 40px;
  padding: 5px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.selection-box:hover,
.selection-box:focus-visible {
  border-color: var(--el-color-primary);
  outline: none;
}
.selection-placeholder {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.picker-panel {
  min-width: 0;
}
.picker-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 14px;
}
.hide-unavailable-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0 4px 8px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
}
.hide-unavailable-toggle i {
  position: relative;
  width: 30px;
  height: 16px;
  border-radius: 10px;
  background: var(--el-border-color);
  transition: background .15s;
}
.hide-unavailable-toggle i::after {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  content: '';
  transition: transform .15s;
}
.hide-unavailable-toggle i.is-on { background: var(--el-color-primary); }
.hide-unavailable-toggle i.is-on::after { transform: translateX(14px); }
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.picker-toolbar :deep(.el-input) {
  flex: 1;
}
.picker-toolbar > span {
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
  display: flex;
  flex-direction: column;
  height: 430px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}
.directory-categories {
  overflow: auto;
  flex: 0 0 142px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
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
  flex: 1 1 auto;
  min-width: 0;
  overflow: auto;
  padding: 8px 14px;
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
.disabled-checkbox-target {
  display: inline-flex;
  flex: none;
}
.directory-item.is-unavailable .directory-item-info {
  color: var(--el-text-color-placeholder);
}
.directory-item.is-unavailable {
  cursor: not-allowed;
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
.metric-detail-trigger,
.dimension-detail-trigger {
  flex: 1;
  cursor: help;
}
.aloudata-detail-card {
  color: var(--el-text-color-primary);
  font-size: 12px;
}
.detail-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
  font-weight: 600;
}
.detail-kind {
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 500;
}
.detail-row {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.detail-row > span {
  flex: none;
  color: var(--el-text-color-placeholder);
}
.detail-loading {
  padding-top: 12px;
  color: var(--el-text-color-secondary);
}
.directory-empty {
  display: grid;
  min-height: 180px;
  place-items: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
@media (max-width: 700px) {
  .selection-row {
    grid-template-columns: 42px minmax(0, 1fr);
  }
  .picker-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .directory-layout {
    grid-template-columns: 38% minmax(0, 1fr);
    height: 55vh;
  }
}
:global(.aloudata-picker-popper) {
  max-width: calc(100vw - 32px) !important;
}
:global(.aloudata-detail-popper) {
  padding: 14px !important;
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
