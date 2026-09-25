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
              :width="350"
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
                  <button class="picker-close" type="button" aria-label="关闭选择指标" @click="metricPickerVisible = false">×</button>
                </div>
                <div class="picker-toolbar">
                  <el-input
                    v-model="metricKeyword"
                    placeholder="搜索"
                    clearable
                    @input="onMetricKeywordInput"
                  ><template #prefix><svg class="picker-search-icon" viewBox="0 0 20 20" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m12.5 12.5 4 4" /></svg></template></el-input>
                  <span>{{ metricPage.total }} 个指标</span>
                </div>
                <div class="picker-visibility"><span class="visibility-info">ⓘ</span><span>自动隐藏不可分析内容</span><button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" aria-label="自动隐藏不可分析内容" @click="hideUnavailable = !hideUnavailable"><i :class="{ 'is-on': hideUnavailable }" /></button></div>
                <div class="directory-layout" v-loading="metricsLoading">
                  <AloudataFieldDirectory :key="metricPickerRevision" :nodes="metricPickerTree" :auto-expand="Boolean(metricKeyword.trim())" @toggle="loadMetricCategory" @more="loadMoreMetricCategory">
                    <template #field="{ field }">
                      <AloudataPickerField
                        :kind="'metric'"
                        :field="field.source as AloudataSyncedMetric"
                        :selected="ui.aloudata.metrics.includes(field.code)"
                        :unavailable-reason="isMetricUnavailable(field.source as AloudataSyncedMetric) ? metricUnavailableReason(field.source as AloudataSyncedMetric) : ''"
                        :details="metricDetails[field.code]"
                        :loading="metricDetailLoading[field.code]"
                        @toggle="(checked) => toggleSelection('metrics', field.code, checked)"
                        @hover="loadMetricDetail(field.code)"
                      />
                    </template>
                  </AloudataFieldDirectory>
                  <div v-if="!metricsLoading && !metricPickerTree.length" class="directory-empty">
                    {{ hideUnavailable && metricPage.records.length ? '已隐藏不可分析的指标' : '没有匹配的指标' }}
                  </div>
                  <el-pagination
                    v-if="metricKeyword && metricPage.total > pageSize"
                    class="pager"
                    layout="prev, pager, next, total"
                    :total="metricPage.total"
                    :page-size="pageSize"
                    :current-page="metricPage.current"
                    @current-change="onMetricPageChange"
                    small
                  />
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
              :width="350"
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
                  <button class="picker-close" type="button" aria-label="关闭选择维度" @click="dimensionPickerVisible = false">×</button>
                </div>
                <div class="picker-toolbar">
                  <el-input
                    v-model="dimensionKeyword"
                    placeholder="搜索"
                    clearable
                    @input="onDimensionKeywordInput"
                  ><template #prefix><svg class="picker-search-icon" viewBox="0 0 20 20" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m12.5 12.5 4 4" /></svg></template></el-input>
                  <span>{{ dimensionPage.total }} 个维度</span>
                </div>
                <div class="picker-visibility"><span class="visibility-info">ⓘ</span><span>自动隐藏不可分析内容</span><button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" aria-label="自动隐藏不可分析内容" @click="hideUnavailable = !hideUnavailable"><i :class="{ 'is-on': hideUnavailable }" /></button></div>
                <div class="directory-layout" v-loading="dimensionsLoading">
                  <AloudataFieldDirectory :key="dimensionPickerRevision" :nodes="dimensionPickerTree" :auto-expand="Boolean(dimensionKeyword.trim())" @toggle="loadDimensionCategory" @more="loadMoreDimensionCategory">
                    <template #field="{ field }">
                      <AloudataPickerField
                        :kind="'dimension'"
                        :field="field.source as AloudataSyncedDimension"
                        :selected="ui.aloudata.dims.includes(field.code)"
                        :unavailable-reason="dimensionUnavailableReason(field.code)"
                        :details="dimensionDetails[field.code]"
                        :loading="dimensionDetailLoading[field.code]"
                        @toggle="(checked) => toggleSelection('dimensions', field.code, checked)"
                        @hover="loadDimensionDetail(field.code)"
                      />
                    </template>
                  </AloudataFieldDirectory>
                  <div v-if="!dimensionsLoading && !dimensionPickerTree.length" class="directory-empty">
                    {{ hideUnavailable && dimensionPage.records.length ? '已隐藏不可分析的维度' : '没有匹配的维度' }}
                  </div>
                  <el-pagination
                    v-if="dimensionKeyword && dimensionPage.total > pageSize"
                    class="pager"
                    layout="prev, pager, next, total"
                    :total="dimensionPage.total"
                    :page-size="pageSize"
                    :current-page="dimensionPage.current"
                    @current-change="onDimensionPageChange"
                    small
                  />
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
import { buildCategoryTree } from './aloudata-metric-directory'
import type { AloudataCategoryTreeNode } from './aloudata-metric-directory'
import AloudataFieldDirectory from './AloudataFieldDirectory.vue'
import AloudataPickerField from './AloudataPickerField.vue'
import type { DirectoryCategory, DirectoryField } from './AloudataFieldDirectory.vue'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const datasourceId = ref('')
const metricPickerVisible = ref(false)
const dimensionPickerVisible = ref(false)
const metricPickerRevision = ref(0)
const dimensionPickerRevision = ref(0)
const metricKeyword = ref('')
const dimensionKeyword = ref('')
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)
const hideUnavailable = ref(false)
const metricCategories = ref<AloudataCategoryTreeNode[]>([])
const dimensionCategories = ref<AloudataCategoryTreeNode[]>([])
const metricCategoryFields = reactive<Record<string, AloudataSyncedMetric[]>>({})
const dimensionCategoryFields = reactive<Record<string, AloudataSyncedDimension[]>>({})
const metricCategoryTotals = reactive<Record<string, number>>({})
const dimensionCategoryTotals = reactive<Record<string, number>>({})
const metricCategoryPages = reactive<Record<string, number>>({})
const dimensionCategoryPages = reactive<Record<string, number>>({})
const metricLabelMap = reactive<Record<string, string>>({})
const dimLabelMap = reactive<Record<string, string>>({})
const metricDimensions = reactive<Record<string, string[]>>({})
const metricDetails = reactive<Record<string, AloudataSyncedMetric>>({})
const dimensionDetails = reactive<Record<string, AloudataSyncedDimension>>({})
const metricDetailLoading = reactive<Record<string, boolean>>({})
const dimensionDetailLoading = reactive<Record<string, boolean>>({})
const selectedMetricRelationsLoading = ref(false)

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
const selectedMetricRelationsUnverified = computed(() => ui.aloudata.metrics.length > 0 && !selectedMetricRelationsReady.value)
const selectionConflict = computed(() => {
  if (!ui.aloudata.metrics.length || !selectedMetricRelationsReady.value) return false
  if (selectedMetricViewsConflict.value) return true
  if (!ui.aloudata.dims.length) return false
  return ui.aloudata.metrics.some((metricName) =>
    ui.aloudata.dims.some((dimName) => !metricDimensions[metricName].includes(dimName)),
  )
})

const metricPickerTree = computed(() => toDirectoryTree(
  metricCategories.value,
  metricKeyword.value ? metricPage.records : metricCategoryFields,
  'metric',
))
const dimensionPickerTree = computed(() => toDirectoryTree(
  dimensionCategories.value,
  dimensionKeyword.value ? dimensionPage.records : dimensionCategoryFields,
  'dimension',
))

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
  metricCategories.value = []
  dimensionCategories.value = []
  Object.keys(metricCategoryFields).forEach((key) => delete metricCategoryFields[key])
  Object.keys(dimensionCategoryFields).forEach((key) => delete dimensionCategoryFields[key])
  Object.keys(metricCategoryTotals).forEach((key) => delete metricCategoryTotals[key])
  Object.keys(dimensionCategoryTotals).forEach((key) => delete dimensionCategoryTotals[key])
  Object.keys(metricCategoryPages).forEach((key) => delete metricCategoryPages[key])
  Object.keys(dimensionCategoryPages).forEach((key) => delete dimensionCategoryPages[key])
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
      count: item.count,
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
      count: item.count,
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
function onMetricPickerShow() {
  dimensionPickerVisible.value = false
  metricPickerRevision.value += 1
  if (!metricPage.records.length) loadMetrics(1)
}
function onDimensionPickerShow() {
  metricPickerVisible.value = false
  dimensionPickerRevision.value += 1
  if (!dimensionPage.records.length) loadDimensions(1)
}

function toDirectoryTree<T extends AloudataSyncedMetric | AloudataSyncedDimension>(
  categories: AloudataCategoryTreeNode[],
  recordsByCategory: Record<string, T[]> | T[],
  kind: 'metric' | 'dimension',
): DirectoryCategory[] {
  const records = Array.isArray(recordsByCategory) ? recordsByCategory : undefined
  const categoryRecords = Array.isArray(recordsByCategory) ? undefined : recordsByCategory
  const keyword = (kind === 'metric' ? metricKeyword.value : dimensionKeyword.value).trim().toLocaleLowerCase()
  const nodes = categories.map((category): DirectoryCategory => {
    const source = records ?? categoryRecords?.[category.categoryId] ?? []
    const fields: DirectoryField[] = source
      .filter((item) => {
        const itemCategoryId = kind === 'metric'
          ? (item as AloudataSyncedMetric).metricCategoryId
          : (item as AloudataSyncedDimension).categoryId
        const itemCategoryName = kind === 'metric'
          ? (item as AloudataSyncedMetric).metricCategoryName
          : (item as AloudataSyncedDimension).categoryName
        if (itemCategoryId || itemCategoryName) {
          return itemCategoryId === category.categoryId || (!itemCategoryId && itemCategoryName === category.categoryName)
        }
        return !records
      })
      .filter((item) => {
        const code = kind === 'metric' ? (item as AloudataSyncedMetric).metricName : (item as AloudataSyncedDimension).dimName
        const label = kind === 'metric' ? (item as AloudataSyncedMetric).metricDisplayName : (item as AloudataSyncedDimension).dimDisplayName
        const unavailable = kind === 'metric'
          ? isMetricUnavailable(item as AloudataSyncedMetric)
          : Boolean(dimensionUnavailableReason(code))
        return (!keyword || `${code} ${label || ''}`.toLocaleLowerCase().includes(keyword)
          || category.categoryName.toLocaleLowerCase().includes(keyword))
          && (!hideUnavailable.value || !unavailable || (kind === 'metric'
            ? ui.aloudata.metrics.includes(code)
            : ui.aloudata.dims.includes(code)))
      })
      .map((item): DirectoryField => {
        const code = kind === 'metric' ? (item as AloudataSyncedMetric).metricName : (item as AloudataSyncedDimension).dimName
        const label = kind === 'metric'
          ? ((item as AloudataSyncedMetric).metricDisplayName || code)
          : ((item as AloudataSyncedDimension).dimDisplayName || code)
        return { nodeType: 'field', nodeKey: `${kind}:${category.categoryId}:${code}`, code, label, source: item }
      })
    const children = toDirectoryTree(category.children, recordsByCategory, kind)
    const categoryMatches = keyword && category.categoryName.toLocaleLowerCase().includes(keyword)
    return {
      ...category,
      nodeType: 'category',
      nodeKey: `${kind}:category:${category.categoryId}`,
      count: category.count ?? (kind === 'metric' ? metricCategoryTotals[category.categoryId] : dimensionCategoryTotals[category.categoryId]),
      hasMore: Boolean(categoryRecords?.[category.categoryId]
        && categoryRecords[category.categoryId].length < (kind === 'metric' ? metricCategoryTotals[category.categoryId] : dimensionCategoryTotals[category.categoryId])),
      fields: categoryMatches && !fields.length ? [] : fields,
      children,
    }
  })
  return keyword ? nodes.filter((node) => node.fields.length || node.children.length || node.categoryName.toLocaleLowerCase().includes(keyword)) : nodes
}

async function loadMetricCategory(category: DirectoryCategory) {
  if (metricCategoryFields[category.categoryId]) return
  try {
    const data = await pageAloudataMetrics(datasourceId.value, { pageNumber: 1, pageSize, categoryId: category.categoryId })
    metricCategoryFields[category.categoryId] = data.records
    metricCategoryTotals[category.categoryId] = data.total
    metricCategoryPages[category.categoryId] = 1
    data.records.forEach((item) => { metricLabelMap[item.metricName] = item.metricDisplayName || item.metricName })
  } catch {
    ElMessage.error(`加载“${category.categoryName}”指标失败`)
  }
}

async function loadDimensionCategory(category: DirectoryCategory) {
  if (dimensionCategoryFields[category.categoryId]) return
  try {
    const data = await pageAloudataDimensions(datasourceId.value, { pageNumber: 1, pageSize, categoryId: category.categoryId })
    dimensionCategoryFields[category.categoryId] = data.records
    dimensionCategoryTotals[category.categoryId] = data.total
    dimensionCategoryPages[category.categoryId] = 1
    data.records.forEach((item) => { dimLabelMap[item.dimName] = item.dimDisplayName || item.dimName })
  } catch {
    ElMessage.error(`加载“${category.categoryName}”维度失败`)
  }
}

async function loadMoreMetricCategory(category: DirectoryCategory) {
  const page = (metricCategoryPages[category.categoryId] || 1) + 1
  metricsLoading.value = true
  try {
    const data = await pageAloudataMetrics(datasourceId.value, { pageNumber: page, pageSize, categoryId: category.categoryId })
    metricCategoryFields[category.categoryId] = [...(metricCategoryFields[category.categoryId] || []), ...data.records]
    metricCategoryPages[category.categoryId] = page
    metricCategoryTotals[category.categoryId] = data.total
    data.records.forEach((item) => { metricLabelMap[item.metricName] = item.metricDisplayName || item.metricName })
  } catch {
    ElMessage.error(`加载“${category.categoryName}”更多指标失败`)
  } finally {
    metricsLoading.value = false
  }
}

async function loadMoreDimensionCategory(category: DirectoryCategory) {
  const page = (dimensionCategoryPages[category.categoryId] || 1) + 1
  dimensionsLoading.value = true
  try {
    const data = await pageAloudataDimensions(datasourceId.value, { pageNumber: page, pageSize, categoryId: category.categoryId })
    dimensionCategoryFields[category.categoryId] = [...(dimensionCategoryFields[category.categoryId] || []), ...data.records]
    dimensionCategoryPages[category.categoryId] = page
    dimensionCategoryTotals[category.categoryId] = data.total
    data.records.forEach((item) => { dimLabelMap[item.dimName] = item.dimDisplayName || item.dimName })
  } catch {
    ElMessage.error(`加载“${category.categoryName}”更多维度失败`)
  } finally {
    dimensionsLoading.value = false
  }
}

async function loadMetrics(page: number) {
  if (!datasourceId.value) return
  metricsLoading.value = true
  try {
    const data = await pageAloudataMetrics(datasourceId.value, {
      pageNumber: page,
      pageSize,
      keyword: metricKeyword.value.trim() || undefined,
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
    loadMetrics(1)
  }, 300)
}
function onDimensionKeywordInput() {
  if (dimensionKeywordTimer) clearTimeout(dimensionKeywordTimer)
  dimensionKeywordTimer = setTimeout(() => {
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
.picker-close {
  width: 26px;
  height: 26px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}
.picker-close:hover { color: var(--el-text-color-primary); }
.picker-visibility {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  margin: 0 -2px 4px;
  padding: 0 2px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.visibility-info { font-size: 15px; }
.picker-visibility .hide-unavailable-toggle { margin-left: auto; padding: 0; }
.hide-unavailable-toggle {
  display: inline-flex;
  align-items: center;
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
.picker-search-icon { width: 18px; height: 18px; fill: none; stroke: var(--el-text-color-secondary); stroke-width: 1.5; }
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
  display: block;
  height: 390px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: auto;
  padding: 8px 10px;
  background: var(--el-bg-color);
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
  .directory-layout { height: 55vh; }
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
