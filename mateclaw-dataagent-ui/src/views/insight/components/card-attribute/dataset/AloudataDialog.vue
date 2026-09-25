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
        <section class="analysis-builder" aria-label="分析配置">
          <header class="config-title">
            <div>
              <span class="config-eyebrow">ANALYSIS BUILDER</span>
              <h2>分析配置</h2>
            </div>
            <span class="config-step">字段配置 <b>01</b></span>
          </header>
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
          <section class="field-config-block" data-testid="config-dimension">
            <div class="field-config-heading">
              <div><strong>维度</strong><span>用于拆解和分组查看指标</span></div>
              <el-popover
                v-model:visible="dimensionPickerVisible"
                trigger="click"
                :placement="pickerLayout.placement"
                :width="350"
                popper-class="aloudata-picker-popper"
                @show="onDimensionPickerShow"
              >
                <template #reference>
                  <button ref="dimensionPickerTriggerEl" class="config-add-button dimension-selection-box" data-testid="open-dimension-picker" type="button" aria-haspopup="dialog">
                    <Plus aria-hidden="true" />添加维度
                  </button>
                </template>
                <div class="picker-panel dimension-picker-popup" :style="pickerPanelStyle" @click.stop>
                  <div class="picker-heading picker-heading-draggable" title="按住拖动弹窗" @pointerdown="startPickerDrag('dimension', $event)">
                    <strong>选择维度</strong>
                    <button class="picker-close" type="button" aria-label="关闭选择维度" @click="dimensionPickerVisible = false"><Close aria-hidden="true" /></button>
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
            <div class="configured-fields" :class="{ 'has-fields': ui.aloudata.dims.length }" data-testid="configured-dimensions">
              <template v-if="ui.aloudata.dims.length">
                <span v-for="name in ui.aloudata.dims" :key="name" class="selected-chip" :data-selected-code="name">
                  <span class="chip-type">abc</span><span class="chip-label">{{ dimLabel(name) }}</span><code>{{ name }}</code>
                  <button type="button" :aria-label="`移除维度 ${dimLabel(name)}`" :title="`移除维度 ${dimLabel(name)}`" @click="removeSelection('dimensions', name)"><Close aria-hidden="true" /></button>
                </span>
              </template>
              <span v-else class="config-empty">尚未添加维度，从目录中选择后会显示在这里</span>
            </div>
          </section>

          <section class="field-config-block" data-testid="config-metric">
            <div class="field-config-heading">
              <div><strong>指标</strong><span>选择需要分析的业务指标</span></div>
            <el-popover
              v-model:visible="metricPickerVisible"
              trigger="click"
              :placement="pickerLayout.placement"
              :width="350"
              popper-class="aloudata-picker-popper"
              @show="onMetricPickerShow"
            >
              <template #reference>
                <button ref="metricPickerTriggerEl" class="config-add-button metric-selection-box" data-testid="open-metric-picker" type="button" aria-haspopup="dialog">
                  <Plus aria-hidden="true" />添加指标
                </button>
              </template>
              <div class="picker-panel metric-picker-popup" :style="pickerPanelStyle" @click.stop>
                <div class="picker-heading picker-heading-draggable" title="按住拖动弹窗" @pointerdown="startPickerDrag('metric', $event)">
                  <strong>选择指标</strong>
                  <button class="picker-close" type="button" aria-label="关闭选择指标" @click="metricPickerVisible = false"><Close aria-hidden="true" /></button>
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
            <div class="configured-fields" :class="{ 'has-fields': ui.aloudata.metrics.length }" data-testid="configured-metrics">
              <template v-if="ui.aloudata.metrics.length">
                <span v-for="name in ui.aloudata.metrics" :key="name" class="selected-chip" :data-selected-code="name">
                  <span class="chip-type">123</span><span class="chip-label">{{ metricLabel(name) }}</span><code>{{ name }}</code>
                  <button type="button" :aria-label="`移除指标 ${metricLabel(name)}`" :title="`移除指标 ${metricLabel(name)}`" @click="removeSelection('metrics', name)"><Close aria-hidden="true" /></button>
                </span>
              </template>
              <span v-else class="config-empty">尚未添加指标，从目录中选择后会显示在这里</span>
            </div>
          </section>

          <div class="config-footnote"><InfoFilled aria-hidden="true" />指标与维度可以分别添加；移除字段后，目录选择状态会同步更新</div>
        </section>
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
import { ref, reactive, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { Close, InfoFilled, Plus } from '@element-plus/icons-vue'
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
import { clampPickerDragOffset, getPickerViewportLayout } from './aloudata-picker-layout'
import type { PickerOffset, PickerPanelBounds, PickerPlacement } from './aloudata-picker-layout'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

const pageSize = 20
const datasourceId = ref('')
const metricPickerVisible = ref(false)
const dimensionPickerVisible = ref(false)
const metricPickerTriggerEl = ref<HTMLButtonElement>()
const dimensionPickerTriggerEl = ref<HTMLButtonElement>()
const metricPickerRevision = ref(0)
const dimensionPickerRevision = ref(0)
const pickerLayout = reactive<{ placement: PickerPlacement; maxHeight: number }>({ placement: 'bottom-start', maxHeight: 520 })
const pickerOffsets = reactive<Record<'metric' | 'dimension', PickerOffset>>({
  metric: { x: 0, y: 0 },
  dimension: { x: 0, y: 0 },
})
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
  return metricLabelMap[name] || metricDetails[name]?.metricDisplayName || name
}
function dimLabel(name: string) {
  return dimLabelMap[name] || dimensionDetails[name]?.dimDisplayName || name
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
function updatePickerViewportLayout(kind: 'metric' | 'dimension') {
  const trigger = kind === 'metric' ? metricPickerTriggerEl.value : dimensionPickerTriggerEl.value
  if (!trigger) return
  const bounds = trigger.getBoundingClientRect()
  Object.assign(pickerLayout, getPickerViewportLayout(bounds, window.innerHeight, 520))
  pickerOffsets[kind] = { x: 0, y: 0 }
  const popupSelector = kind === 'metric' ? '.metric-picker-popup' : '.dimension-picker-popup'
  document.querySelectorAll<HTMLElement>('.aloudata-picker-popper').forEach((popper) => {
    if (!popper.querySelector(popupSelector)) return
    popper.style.maxHeight = `${pickerLayout.maxHeight}px`
    popper.style.overflow = 'visible'
    popper.style.setProperty('--picker-drag-offset', '0px 0px')
  })
}

const pickerPanelStyle = computed(() => ({
  maxHeight: `${Math.max(0, pickerLayout.maxHeight - 24)}px`,
}))

let pickerDrag: {
  kind: 'metric' | 'dimension'
  startX: number
  startY: number
  bounds: PickerPanelBounds
  origin: PickerOffset
  popper: HTMLElement
} | undefined

function movePickerDrag(event: PointerEvent) {
  if (!pickerDrag) return
  const nextOffset = clampPickerDragOffset(
    pickerDrag.bounds,
    pickerDrag.origin,
    { x: event.clientX - pickerDrag.startX, y: event.clientY - pickerDrag.startY },
    { width: window.innerWidth, height: window.innerHeight },
  )
  pickerOffsets[pickerDrag.kind] = nextOffset
  pickerDrag.popper.style.setProperty('--picker-drag-offset', `${nextOffset.x}px ${nextOffset.y}px`)
}

function stopPickerDrag() {
  pickerDrag = undefined
  window.removeEventListener('pointermove', movePickerDrag)
  window.removeEventListener('pointerup', stopPickerDrag)
  window.removeEventListener('pointercancel', stopPickerDrag)
}

function startPickerDrag(kind: 'metric' | 'dimension', event: PointerEvent) {
  if (event.button !== 0 || (event.target as HTMLElement).closest('button')) return
  const panel = (event.currentTarget as HTMLElement).parentElement
  const popper = panel?.closest<HTMLElement>('.el-popper')
  if (!popper) return
  event.preventDefault()
  const bounds = popper.getBoundingClientRect()
  const origin = { ...pickerOffsets[kind] }
  const baseBounds = {
    left: bounds.left - origin.x,
    right: bounds.right - origin.x,
    top: bounds.top - origin.y,
    bottom: bounds.bottom - origin.y,
  }
  pickerDrag = { kind, startX: event.clientX, startY: event.clientY, bounds: baseBounds, origin, popper }
  window.addEventListener('pointermove', movePickerDrag)
  window.addEventListener('pointerup', stopPickerDrag)
  window.addEventListener('pointercancel', stopPickerDrag)
}

function onMetricPickerShow() {
  dimensionPickerVisible.value = false
  metricPickerRevision.value += 1
  void nextTick(() => updatePickerViewportLayout('metric'))
  if (!metricPage.records.length) loadMetrics(1)
}
function onDimensionPickerShow() {
  metricPickerVisible.value = false
  dimensionPickerRevision.value += 1
  void nextTick(() => updatePickerViewportLayout('dimension'))
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
  stopPickerDrag()
  if (viewKwTimer) clearTimeout(viewKwTimer)
  if (metricKeywordTimer) clearTimeout(metricKeywordTimer)
  if (dimensionKeywordTimer) clearTimeout(dimensionKeywordTimer)
})
</script>

<style scoped>
.analysis-builder {
  min-height: 390px;
  padding: 22px 24px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);
}
.config-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.config-eyebrow {
  color: var(--el-text-color-placeholder);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: .13em;
}
.config-title h2 {
  margin: 5px 0 0;
  color: var(--el-text-color-primary);
  font-size: 19px;
  font-weight: 620;
  letter-spacing: -.02em;
}
.config-step {
  padding: 6px 9px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 5px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}
.config-step b { margin-left: 7px; color: var(--el-color-primary); font-weight: 600; }
.field-config-block { margin-top: 20px; }
.field-config-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.field-config-heading > div { display: flex; align-items: baseline; gap: 10px; }
.field-config-heading strong { color: var(--el-text-color-primary); font-size: 14px; font-weight: 600; }
.field-config-heading > div span { color: var(--el-text-color-placeholder); font-size: 11px; }
.config-add-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 5px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}
.config-add-button:hover { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-8); }
.config-add-button > svg { width: 14px; height: 14px; }
.config-footnote > svg { width: 15px; height: 15px; flex: 0 0 auto; }
.configured-fields {
  min-height: 72px;
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 8px;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 7px;
  background: var(--el-fill-color-lighter);
}
.configured-fields.has-fields { border-style: solid; border-color: var(--el-border-color-lighter); background: var(--el-bg-color); }
.config-empty { align-self: center; padding: 14px 2px; color: var(--el-text-color-placeholder); font-size: 12px; }
.selected-chip {
  max-width: 100%;
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 6px 0 9px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 5px;
  background: var(--el-color-primary-light-9);
  color: var(--el-text-color-regular);
}
.selected-chip .chip-type { color: var(--el-color-primary); font-size: 10px; font-weight: 600; }
.selected-chip .chip-label { max-width: 150px; overflow: hidden; color: var(--el-text-color-regular); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.selected-chip code { max-width: 128px; overflow: hidden; color: var(--el-text-color-placeholder); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.selected-chip button {
  width: 20px;
  height: 20px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-placeholder);
  font-size: 16px;
  cursor: pointer;
}
.selected-chip button:hover { background: var(--el-color-primary-light-8); color: var(--el-color-primary); }
.selected-chip button svg { width: 12px; height: 12px; }
.config-footnote {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}
.picker-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
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
.picker-heading-draggable { cursor: grab; user-select: none; }
.picker-heading-draggable:active { cursor: grabbing; }
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
.picker-close svg { width: 15px; height: 15px; }
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
  flex: 1 1 auto;
  height: 390px;
  min-height: 0;
  max-height: 390px;
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
  .analysis-builder { min-height: 0; padding: 18px 16px; }
  .field-config-heading > div { display: block; }
  .field-config-heading > div span { display: block; margin-top: 3px; }
  .selected-chip code { display: none; }
  .config-footnote { align-items: flex-start; }
  .picker-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .directory-layout { height: 55vh; max-height: 55vh; }
}
:global(.aloudata-picker-popper) {
  max-width: calc(100vw - 32px) !important;
  translate: var(--picker-drag-offset, 0px 0px);
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
