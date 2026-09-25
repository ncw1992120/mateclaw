<template>
  <section class="aloudata-field-selector" aria-label="Aloudata 指标与维度选择">
    <el-alert
      v-if="selectionConflict"
      class="selection-alert"
      type="error"
      :closable="false"
      title="已选指标与维度存在不兼容项，请调整选择后再确定"
    />
    <el-alert
      v-else-if="relationsUnverified"
      class="selection-alert"
      type="warning"
      :closable="false"
      :title="relationsLoading ? '正在校验已选指标的可用维度…' : '无法校验已选指标的可用维度，请重试或移除该指标'"
    />

    <div class="selection-row">
      <span class="selection-label">指标</span>
      <el-popover
        v-model:visible="metricPickerVisible"
        trigger="click"
        placement="bottom-start"
        :width="600"
        popper-class="aloudata-source-picker-popper"
        @show="openPicker('metrics')"
      >
        <template #reference>
          <div class="selection-box metric-selection-box" role="button" tabindex="0" aria-haspopup="dialog" @keydown.enter.prevent="metricPickerVisible = true">
            <el-tag v-for="name in metrics" :key="name" closable size="small" @close.stop="removeSelection('metrics', name)">{{ metricLabel(name) }}</el-tag>
            <span v-if="!metrics.length" class="selection-placeholder">点击选择指标</span>
          </div>
        </template>
        <div class="picker-panel metric-picker-popup" @click.stop>
          <div class="picker-heading"><strong>选择指标</strong><button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" @click="hideUnavailable = !hideUnavailable"><span>自动隐藏不可分析内容</span><i :class="{ 'is-on': hideUnavailable }" /></button></div>
          <div class="picker-toolbar"><el-input v-model="metricKeyword" placeholder="搜索指标展示名或字段名称" clearable @input="onKeywordInput" /><span>{{ metricPage.total }} 个指标</span></div>
          <div class="directory-layout">
            <aside class="directory-categories" aria-label="指标目录" v-loading="categoryLoading"><el-tree :data="filteredMetricCategories" :props="treeProps" node-key="categoryId" highlight-current :default-expanded-keys="expandedMetricKeys" :expand-on-click-node="false" :render-after-expand="false" @node-click="onMetricCategorySelect"><template #default="{ data }"><span class="category-node-label">{{ data.categoryName }}</span><span v-if="data.count !== undefined" class="category-node-count">{{ data.count }}</span></template></el-tree><div v-if="!metricCategories.length && !categoryLoading" class="directory-empty">暂无指标目录</div></aside>
            <section class="directory-results" v-loading="metricsLoading">
              <label v-for="item in visibleMetrics" :key="item.metricName" class="directory-item" :class="{ 'is-unavailable': isMetricUnavailable(item) && !metrics.includes(item.metricName) }">
                <el-tooltip v-if="isMetricUnavailable(item) && !metrics.includes(item.metricName)" :content="metricUnavailableReason(item)" placement="top"><span class="disabled-checkbox-target"><el-checkbox :model-value="false" :label="item.metricName" disabled /></span></el-tooltip>
                <el-checkbox v-else :model-value="metrics.includes(item.metricName)" :label="item.metricName" @change="(checked: boolean) => toggleSelection('metrics', item.metricName, checked, item)" />
                <el-popover trigger="hover" placement="right" :width="290" :show-after="250" popper-class="aloudata-source-detail-popper" @show="loadMetricDetail(item.metricName)">
                  <template #reference><span class="directory-item-info"><span class="directory-item-title">{{ item.metricDisplayName || item.metricName }}</span><span class="directory-item-code">{{ item.metricName }}</span></span></template>
                  <div class="detail-card"><div class="detail-card-title"><span class="detail-kind">123</span>{{ metricDetails[item.metricName]?.metricDisplayName || item.metricDisplayName || item.metricName }}</div><div v-if="metricDetailLoading[item.metricName]" class="detail-loading">加载详情中…</div><template v-else><div class="detail-row"><span>指标编码：</span>{{ item.metricName }}</div><div class="detail-row"><span>指标名称：</span>{{ metricDetails[item.metricName]?.metricDisplayName || item.metricDisplayName || '—' }}</div><div class="detail-row"><span>指标类型：</span>{{ metricDetails[item.metricName]?.type || item.type || '—' }}</div><div class="detail-row"><span>业务口径：</span>{{ metricDetails[item.metricName]?.businessCaliber || item.businessCaliber || '—' }}</div><div class="detail-row"><span>单位：</span>{{ metricDetails[item.metricName]?.unit || item.unit || '—' }}</div><div class="detail-row"><span>负责人：</span>{{ metricDetails[item.metricName]?.owner || item.owner || '—' }}</div></template></div>
                </el-popover>
              </label>
              <div v-if="!metricsLoading && !visibleMetrics.length" class="directory-empty">{{ hideUnavailable && metricPage.records.length ? '已隐藏不可分析的指标' : '没有匹配的指标' }}</div>
              <el-pagination v-if="metricPage.total > pageSize" class="pager" layout="prev, pager, next, total" :total="metricPage.total" :page-size="pageSize" :current-page="metricPage.current" small @current-change="loadMetrics" />
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
        :width="600"
        popper-class="aloudata-source-picker-popper"
        @show="openPicker('dimensions')"
      >
        <template #reference>
          <div class="selection-box dimension-selection-box" role="button" tabindex="0" aria-haspopup="dialog" @keydown.enter.prevent="dimensionPickerVisible = true">
            <el-tag v-for="name in dimensions" :key="name" closable size="small" type="info" @close.stop="removeSelection('dimensions', name)">{{ dimensionLabel(name) }}</el-tag>
            <span v-if="!dimensions.length" class="selection-placeholder">点击选择维度</span>
          </div>
        </template>
        <div class="picker-panel dimension-picker-popup" @click.stop>
          <div class="picker-heading"><strong>选择维度</strong><button class="hide-unavailable-toggle" type="button" role="switch" :aria-checked="hideUnavailable" @click="hideUnavailable = !hideUnavailable"><span>自动隐藏不可分析内容</span><i :class="{ 'is-on': hideUnavailable }" /></button></div>
          <div class="picker-toolbar"><el-input v-model="dimensionKeyword" placeholder="搜索维度展示名或字段名称" clearable @input="onKeywordInput" /><span>{{ dimensionPage.total }} 个维度</span></div>
          <div class="directory-layout">
            <aside class="directory-categories" aria-label="维度目录" v-loading="categoryLoading"><el-tree :data="filteredDimensionCategories" :props="treeProps" node-key="categoryId" highlight-current :default-expanded-keys="[allDimensionsId, ...expandedDimensionKeys]" :expand-on-click-node="false" :render-after-expand="false" @node-click="onDimensionCategorySelect"><template #default="{ data }"><span class="category-node-label">{{ data.categoryName }}</span><span v-if="data.count !== undefined" class="category-node-count">{{ data.count }}</span></template></el-tree><div v-if="!dimensionCategories.length && !categoryLoading" class="directory-empty">暂无维度目录</div></aside>
            <section class="directory-results" v-loading="dimensionsLoading">
              <label v-for="item in visibleDimensions" :key="item.dimName" class="directory-item" :class="{ 'is-unavailable': dimensionUnavailableReason(item.dimName) && !dimensions.includes(item.dimName) }">
                <el-tooltip v-if="dimensionUnavailableReason(item.dimName) && !dimensions.includes(item.dimName)" :content="dimensionUnavailableReason(item.dimName)" placement="top"><span class="disabled-checkbox-target"><el-checkbox :model-value="false" :label="item.dimName" disabled /></span></el-tooltip>
                <el-checkbox v-else :model-value="dimensions.includes(item.dimName)" :label="item.dimName" @change="(checked: boolean) => toggleSelection('dimensions', item.dimName, checked)" />
                <el-popover trigger="hover" placement="right" :width="290" :show-after="250" popper-class="aloudata-source-detail-popper" @show="loadDimensionDetail(item.dimName)">
                  <template #reference><span class="directory-item-info"><span class="directory-item-title">{{ item.dimDisplayName || item.dimName }}</span><span class="directory-item-code">{{ item.dimName }}</span></span></template>
                  <div class="detail-card"><div class="detail-card-title"><span class="detail-kind">abc</span>{{ dimensionDetails[item.dimName]?.dimDisplayName || item.dimDisplayName || item.dimName }}</div><div v-if="dimensionDetailLoading[item.dimName]" class="detail-loading">加载详情中…</div><template v-else><div class="detail-row"><span>维度编码：</span>{{ item.dimName }}</div><div class="detail-row"><span>维度名称：</span>{{ dimensionDetails[item.dimName]?.dimDisplayName || item.dimDisplayName || '—' }}</div><div class="detail-row"><span>数据类型：</span>{{ dimensionDetails[item.dimName]?.originDataType || item.originDataType || '—' }}</div><div class="detail-row"><span>维度描述：</span>{{ dimensionDetails[item.dimName]?.dimDescription || item.dimDescription || '—' }}</div></template></div>
                </el-popover>
              </label>
              <div v-if="!dimensionsLoading && !visibleDimensions.length" class="directory-empty">{{ hideUnavailable && dimensionPage.records.length ? '已隐藏不可分析的维度' : '没有匹配的维度' }}</div>
              <el-pagination v-if="dimensionPage.total > pageSize" class="pager" layout="prev, pager, next, total" :total="dimensionPage.total" :page-size="pageSize" :current-page="dimensionPage.current" small @current-change="loadDimensions" />
            </section>
          </div>
        </div>
      </el-popover>
    </div>
    <p class="selector-hint">指标与维度分别选择；鼠标悬浮字段可查看详情。已选 {{ metrics.length }} 个指标、{{ dimensions.length }} 个维度。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAloudataDimensionDetail, getAloudataMetricDetail, listAloudataCategoryCounts, pageAloudataDimensions, pageAloudataMetrics } from '@/api/semantic-model'
import type { AloudataCategoryCount, AloudataDimensionPage, AloudataMetricPage, AloudataSyncedDimension, AloudataSyncedMetric } from '@/types'
import { buildCategoryTree, filterCategoryTree } from './card-attribute/dataset/aloudata-metric-directory'
import type { AloudataCategoryTreeNode } from './card-attribute/dataset/aloudata-metric-directory'

const props = defineProps<{ datasourceId: string; metrics: string[]; dimensions: string[] }>()
const emit = defineEmits<{
  (event: 'update:metrics', value: string[]): void
  (event: 'update:dimensions', value: string[]): void
  (event: 'update:valid', value: boolean): void
}>()

const pageSize = 20
const allDimensionsId = '__all_dimensions__'
const treeProps = { label: 'categoryName', children: 'children' }
const activePicker = ref<'metrics' | 'dimensions'>('metrics')
const metricPickerVisible = ref(false)
const dimensionPickerVisible = ref(false)
const hideUnavailable = ref(false)
const metricKeyword = ref('')
const dimensionKeyword = ref('')
const metricCategories = ref<AloudataCategoryTreeNode[]>([])
const dimensionCategories = ref<AloudataCategoryTreeNode[]>([])
const selectedMetricCategoryId = ref('')
const selectedDimensionCategoryId = ref(allDimensionsId)
const categoryLoading = ref(false)
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)
const relationsLoading = ref(false)
const metricLabelMap = reactive<Record<string, string>>({})
const dimensionLabelMap = reactive<Record<string, string>>({})
const metricDetails = reactive<Record<string, AloudataSyncedMetric>>({})
const dimensionDetails = reactive<Record<string, AloudataSyncedDimension>>({})
const metricDetailLoading = reactive<Record<string, boolean>>({})
const dimensionDetailLoading = reactive<Record<string, boolean>>({})
const metricDimensions = reactive<Record<string, string[]>>({})
const metricPage = reactive<AloudataMetricPage>({ records: [], total: 0, size: pageSize, current: 1, pages: 0 })
const dimensionPage = reactive<AloudataDimensionPage>({ records: [], total: 0, size: pageSize, current: 1, pages: 0 })
const selectedRelationsReady = computed(() => props.metrics.every(name => Array.isArray(metricDimensions[name])))
const relationsUnverified = computed(() => props.metrics.length > 0 && !selectedRelationsReady.value)
const selectionConflict = computed(() => props.metrics.length > 0 && props.dimensions.length > 0 && selectedRelationsReady.value
  && props.metrics.some(metric => props.dimensions.some(dimension => !metricDimensions[metric].includes(dimension))))
const isValid = computed(() => props.metrics.length > 0 && !selectionConflict.value && !relationsUnverified.value)
const metricCategoryTree = computed(() => metricCategories.value)
const dimensionCategoryTree = computed(() => [{ categoryId: allDimensionsId, categoryName: '全部维度', children: dimensionCategories.value }])
const filteredMetricCategories = computed(() => filterCategoryTree(metricCategoryTree.value, metricKeyword.value))
const filteredDimensionCategories = computed(() => filterCategoryTree(dimensionCategoryTree.value, dimensionKeyword.value))
const expandedMetricKeys = computed(() => metricKeyword.value.trim() ? collectCategoryIds(filteredMetricCategories.value) : [])
const expandedDimensionKeys = computed(() => dimensionKeyword.value.trim() ? collectCategoryIds(filteredDimensionCategories.value) : [])
const visibleMetrics = computed(() => hideUnavailable.value ? metricPage.records.filter(item => !isMetricUnavailable(item)) : metricPage.records)
const visibleDimensions = computed(() => hideUnavailable.value ? dimensionPage.records.filter(item => !dimensionUnavailableReason(item.dimName)) : dimensionPage.records)

watch(isValid, value => emit('update:valid', value), { immediate: true })
watch(() => [...props.metrics], names => { void loadSelectedMetricRelations(names) }, { immediate: true })
watch(() => [...props.metrics], names => names.forEach(name => { if (!metricLabelMap[name]) void loadMetricDetail(name) }), { immediate: true })
watch(() => [...props.dimensions], names => names.forEach(name => { if (!dimensionLabelMap[name]) void loadDimensionDetail(name) }), { immediate: true })

onMounted(() => { if (props.datasourceId) void loadCategories() })
watch(() => props.datasourceId, () => {
  metricCategories.value = []
  dimensionCategories.value = []
  selectedMetricCategoryId.value = ''
  selectedDimensionCategoryId.value = allDimensionsId
  metricPage.records = []
  metricPage.total = 0
  dimensionPage.records = []
  dimensionPage.total = 0
  Object.keys(metricDimensions).forEach(name => delete metricDimensions[name])
  Object.keys(metricLabelMap).forEach(name => delete metricLabelMap[name])
  Object.keys(dimensionLabelMap).forEach(name => delete dimensionLabelMap[name])
  Object.keys(metricDetails).forEach(name => delete metricDetails[name])
  Object.keys(dimensionDetails).forEach(name => delete dimensionDetails[name])
  if (props.datasourceId) {
    void loadCategories()
    void loadSelectedMetricRelations(props.metrics)
    props.metrics.forEach(name => { void loadMetricDetail(name) })
    props.dimensions.forEach(name => { void loadDimensionDetail(name) })
  }
}, { flush: 'post' })

function collectCategoryIds(nodes: AloudataCategoryTreeNode[]): string[] {
  return nodes.flatMap(node => [node.categoryId, ...collectCategoryIds(node.children)])
}

function metricLabel(name: string): string { return metricLabelMap[name] || metricDetails[name]?.metricDisplayName || name }
function dimensionLabel(name: string): string { return dimensionLabelMap[name] || dimensionDetails[name]?.dimDisplayName || name }

function removeSelection(type: 'metrics' | 'dimensions', name: string): void {
  if (type === 'metrics') emit('update:metrics', props.metrics.filter(item => item !== name))
  else emit('update:dimensions', props.dimensions.filter(item => item !== name))
}

async function loadCategories(): Promise<void> {
  const datasourceId = props.datasourceId
  categoryLoading.value = true
  try {
    const [metrics, dimensions] = await Promise.all([
      listAloudataCategoryCounts(datasourceId, 'CATEGORY_METRIC'),
      listAloudataCategoryCounts(datasourceId, 'CATEGORY_DIMENSION'),
    ])
    if (datasourceId !== props.datasourceId) return
    metricCategories.value = buildCategoryTree((metrics as AloudataCategoryCount[]).map(item => ({ categoryId: item.categoryId, categoryName: item.categoryName, parentId: item.parentId, count: item.count })))
    dimensionCategories.value = buildCategoryTree((dimensions as AloudataCategoryCount[]).map(item => ({ categoryId: item.categoryId, categoryName: item.categoryName, parentId: item.parentId, count: item.count })))
  } catch {
    if (datasourceId !== props.datasourceId) return
    ElMessage.error('加载 Aloudata 指标/维度目录失败')
    metricCategories.value = []
    dimensionCategories.value = []
  } finally { categoryLoading.value = false }
}

function openPicker(type: 'metrics' | 'dimensions'): void {
  activePicker.value = type
  hideUnavailable.value = false
  if (type === 'metrics') {
    dimensionPickerVisible.value = false
    if (!metricPage.records.length) void loadMetrics(1)
  } else {
    metricPickerVisible.value = false
    if (!dimensionPage.records.length) void loadDimensions(1)
    void loadSelectedMetricRelations(props.metrics)
  }
}

function onMetricCategorySelect(category: AloudataCategoryTreeNode): void {
  selectedMetricCategoryId.value = category.categoryId
  void loadMetrics(1)
}
function onDimensionCategorySelect(category: AloudataCategoryTreeNode): void {
  selectedDimensionCategoryId.value = category.categoryId
  void loadDimensions(1)
}

async function loadMetrics(page: number): Promise<void> {
  if (!props.datasourceId) return
  const datasourceId = props.datasourceId
  metricsLoading.value = true
  try {
    const result = await pageAloudataMetrics(datasourceId, { pageNumber: page, pageSize, keyword: metricKeyword.value.trim() || undefined, categoryId: selectedMetricCategoryId.value || undefined })
    if (datasourceId !== props.datasourceId) return
    Object.assign(metricPage, result)
    result.records.forEach(item => {
      metricLabelMap[item.metricName] = item.metricDisplayName || item.metricName
      if (Array.isArray(item.availableDimensions)) metricDimensions[item.metricName] = item.availableDimensions
    })
  } catch {
    if (datasourceId !== props.datasourceId) return
    metricPage.records = []
    metricPage.total = 0
    ElMessage.error('加载 Aloudata 指标失败')
  } finally { metricsLoading.value = false }
}

async function loadDimensions(page: number): Promise<void> {
  if (!props.datasourceId) return
  const datasourceId = props.datasourceId
  dimensionsLoading.value = true
  try {
    const result = await pageAloudataDimensions(datasourceId, { pageNumber: page, pageSize, keyword: dimensionKeyword.value.trim() || undefined, categoryId: selectedDimensionCategoryId.value === allDimensionsId ? undefined : selectedDimensionCategoryId.value })
    if (datasourceId !== props.datasourceId) return
    Object.assign(dimensionPage, result)
    result.records.forEach(item => { dimensionLabelMap[item.dimName] = item.dimDisplayName || item.dimName })
  } catch {
    if (datasourceId !== props.datasourceId) return
    dimensionPage.records = []
    dimensionPage.total = 0
    ElMessage.error('加载 Aloudata 维度失败')
  } finally { dimensionsLoading.value = false }
}

let keywordTimer: ReturnType<typeof setTimeout> | undefined
function onKeywordInput(): void {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    if (activePicker.value === 'metrics') {
      selectedMetricCategoryId.value = ''
      void loadMetrics(1)
    } else {
      selectedDimensionCategoryId.value = allDimensionsId
      void loadDimensions(1)
    }
  }, 300)
}

function isMetricUnavailable(item: AloudataSyncedMetric): boolean {
  return props.dimensions.length > 0 && (!Array.isArray(item.availableDimensions) || props.dimensions.some(name => !item.availableDimensions!.includes(name)))
}
function metricUnavailableReason(item: AloudataSyncedMetric): string {
  return Array.isArray(item.availableDimensions) ? '该指标不支持已选维度' : '无法校验该指标对已选维度的支持情况'
}
function dimensionUnavailableReason(name: string): string {
  if (!props.metrics.length) return ''
  if (!selectedRelationsReady.value) return relationsLoading.value ? '正在校验已选指标的可用维度' : '无法校验已选指标的可用维度'
  return props.metrics.some(metric => !metricDimensions[metric].includes(name)) ? '该维度不是已选指标的可用维度' : ''
}

function toggleSelection(type: 'metrics' | 'dimensions', name: string, checked: boolean, item?: AloudataSyncedMetric): void {
  const selected = type === 'metrics' ? props.metrics : props.dimensions
  if (!checked) { removeSelection(type, name); return }
  if (selected.includes(name)) return
  if (type === 'metrics') {
    if (item && isMetricUnavailable(item)) return
    emit('update:metrics', [...props.metrics, name])
    if (item && Array.isArray(item.availableDimensions)) metricDimensions[name] = item.availableDimensions
    else void loadMetricDetail(name)
  } else {
    if (dimensionUnavailableReason(name)) return
    emit('update:dimensions', [...props.dimensions, name])
  }
}

async function loadSelectedMetricRelations(names: string[]): Promise<void> {
  const missing = [...new Set(names)].filter(name => !Array.isArray(metricDimensions[name]))
  if (!missing.length || !props.datasourceId) return
  const datasourceId = props.datasourceId
  relationsLoading.value = true
  try {
    const details = await Promise.all(missing.map(name => getAloudataMetricDetail(datasourceId, name)))
    if (datasourceId !== props.datasourceId) return
    details.forEach((detail, index) => {
      metricDetails[missing[index]] = detail
      if (Array.isArray(detail.availableDimensions)) metricDimensions[missing[index]] = detail.availableDimensions
    })
  } catch { /* Validation remains fail-closed and the user can reopen/retry the picker. */ }
  finally { relationsLoading.value = false }
}

async function loadMetricDetail(name: string): Promise<void> {
  if (!props.datasourceId || metricDetails[name] || metricDetailLoading[name]) return
  const datasourceId = props.datasourceId
  metricDetailLoading[name] = true
  try {
    const detail = await getAloudataMetricDetail(datasourceId, name)
    if (datasourceId !== props.datasourceId) return
    metricDetails[name] = detail
    metricLabelMap[name] = metricDetails[name].metricDisplayName || name
    if (Array.isArray(metricDetails[name].availableDimensions)) metricDimensions[name] = metricDetails[name].availableDimensions
  } catch { /* Keep the field identifier as its fallback label. */ }
  finally { metricDetailLoading[name] = false }
}

async function loadDimensionDetail(name: string): Promise<void> {
  if (!props.datasourceId || dimensionDetails[name] || dimensionDetailLoading[name]) return
  const datasourceId = props.datasourceId
  dimensionDetailLoading[name] = true
  try {
    const detail = await getAloudataDimensionDetail(datasourceId, name)
    if (datasourceId !== props.datasourceId) return
    dimensionDetails[name] = detail
    dimensionLabelMap[name] = dimensionDetails[name].dimDisplayName || name
  } catch { /* Keep the field identifier as its fallback label. */ }
  finally { dimensionDetailLoading[name] = false }
}

onBeforeUnmount(() => { if (keywordTimer) clearTimeout(keywordTimer) })
</script>

<style scoped>
.aloudata-field-selector { display: grid; gap: 14px; }
.selection-alert { margin-bottom: 0; }
.selection-row { display: grid; grid-template-columns: 48px minmax(0, 1fr); align-items: start; gap: 10px; }
.selection-label { padding-top: 9px; color: var(--el-text-color-regular); font-size: 13px; }
.selection-box { display: flex; min-height: 40px; flex-wrap: wrap; align-items: center; gap: 5px; padding: 5px 8px; border: 1px solid var(--el-border-color); border-radius: 5px; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.selection-box:hover, .selection-box:focus-visible { border-color: var(--el-color-primary); outline: none; box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 10%, transparent); }
.selection-placeholder { color: var(--el-text-color-placeholder); font-size: 13px; }
.picker-panel { min-width: 0; }
.picker-heading { display: flex; min-height: 36px; align-items: center; justify-content: space-between; margin-bottom: 8px; color: var(--el-text-color-primary); font-size: 14px; }
.hide-unavailable-toggle { display: inline-flex; align-items: center; gap: 8px; padding: 4px 0 4px 8px; border: 0; background: transparent; color: var(--el-text-color-secondary); font-size: 12px; cursor: pointer; }
.hide-unavailable-toggle i { position: relative; width: 30px; height: 16px; border-radius: 10px; background: var(--el-border-color); transition: background .15s; }
.hide-unavailable-toggle i::after { position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%; background: white; content: ''; transition: transform .15s; }
.hide-unavailable-toggle i.is-on { background: var(--el-color-primary); }
.hide-unavailable-toggle i.is-on::after { transform: translateX(14px); }
.picker-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.picker-toolbar :deep(.el-input) { flex: 1; }
.picker-toolbar > span { flex: none; color: var(--el-text-color-secondary); font-size: 12px; }
.directory-layout { display: grid; height: 390px; grid-template-columns: 210px minmax(0, 1fr); overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.directory-categories { overflow: auto; padding: 6px 8px; border-right: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-lighter); }
.directory-categories :deep(.el-tree) { background: transparent; }
.category-node-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-node-count { flex: none; margin-left: auto; color: var(--el-text-color-placeholder); font-size: 12px; }
.directory-categories :deep(.el-tree-node__content) { display: flex; gap: 8px; }
.directory-results { min-width: 0; overflow: auto; padding: 8px 12px; }
.directory-item { display: flex; min-height: 44px; align-items: center; gap: 8px; padding: 5px 4px; border-bottom: 1px solid var(--el-border-color-extra-light); cursor: pointer; }
.directory-item-info { display: flex; min-width: 0; flex-direction: column; gap: 2px; cursor: help; }
.directory-item-title, .directory-item-code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.directory-item-title { color: var(--el-text-color-primary); font-size: 13px; }
.directory-item-code { color: var(--el-text-color-secondary); font-size: 11px; }
.directory-item.is-unavailable, .directory-item.is-unavailable .directory-item-info { color: var(--el-text-color-placeholder); cursor: not-allowed; }
.disabled-checkbox-target { display: inline-flex; flex: none; }
.directory-empty { padding: 28px 10px; color: var(--el-text-color-placeholder); font-size: 12px; text-align: center; }
.pager { justify-content: center; margin-top: 10px; }
.selector-hint { margin: 0 0 0 58px; color: var(--el-text-color-secondary); font-size: 12px; }
.detail-card { color: var(--el-text-color-primary); }
.detail-card-title { display: flex; min-height: 42px; align-items: center; gap: 8px; margin: -4px -4px 10px; padding: 0 10px; border-bottom: 1px solid var(--el-border-color-lighter); font-size: 14px; }
.detail-kind { color: var(--el-color-primary); font-size: 12px; }
.detail-row { display: flex; min-height: 24px; align-items: baseline; gap: 4px; color: var(--el-text-color-regular); font-size: 12px; line-height: 20px; }
.detail-row span { flex: none; color: var(--el-text-color-placeholder); }
.detail-loading { padding: 12px 0; color: var(--el-text-color-secondary); font-size: 12px; }
:global(.aloudata-source-picker-popper) { padding: 12px !important; }
:global(.aloudata-source-detail-popper) { padding: 12px !important; }
@media (max-width: 720px) {
  .selection-row { grid-template-columns: 42px minmax(0, 1fr); gap: 7px; }
  .directory-layout { height: min(390px, 55vh); grid-template-columns: minmax(120px, 36%) minmax(0, 1fr); }
  .selector-hint { margin-left: 49px; }
}
</style>
