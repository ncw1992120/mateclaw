<template>
  <div class="property-panel">
    <div class="panel-header">{{ t('insight.propertyTitle') }}</div>

    <div v-if="!component" class="panel-empty">
      <div class="empty-icon">⚙️</div>
      <div class="empty-text">{{ t('insight.propertyEmpty') }}</div>
    </div>

    <div v-else class="panel-body">
      <!-- 标题 -->
      <div class="form-group">
        <label class="form-label">{{ t('insight.property.componentTitle') }}</label>
        <el-input v-model="localComponent.title" size="small" @change="emitChange" />
      </div>

      <!-- 图表类型（仅 chart 组件） -->
      <div v-if="component.type === 'chart'" class="form-group">
        <label class="form-label">{{ t('insight.property.chartType') }}</label>
        <el-radio-group v-model="localComponent.chartType" size="small" @change="emitChange">
          <el-radio-button value="line">{{ t('insight.component.line') }}</el-radio-button>
          <el-radio-button value="bar">{{ t('insight.component.bar') }}</el-radio-button>
          <el-radio-button value="pie">{{ t('insight.component.pie') }}</el-radio-button>
          <el-radio-button value="area">{{ t('insight.component.area') }}</el-radio-button>
          <el-radio-button value="scatter">{{ t('insight.component.scatter') }}</el-radio-button>
          <el-radio-button value="radar">{{ t('insight.component.radar') }}</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 数据绑定（kpi/chart/table 组件） -->
      <template v-if="component.type !== 'filter'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.datasource') }}</label>
          <el-select
            v-model="localDataSource.datasourceId"
            :placeholder="t('insight.property.selectDatasource')"
            size="small"
            filterable
            style="width: 100%"
            @change="handleDatasourceChange"
          >
            <el-option
              v-for="ds in datasourceStore.datasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </div>

        <div v-if="localDataSource.datasourceId" class="form-group">
          <label class="form-label">{{ t('insight.property.metrics') }}</label>
          <el-select
            v-model="localDataSource.metrics"
            :placeholder="t('insight.property.selectMetrics')"
            size="small"
            multiple
            filterable
            remote
            :remote-method="searchMetrics"
            :loading="metricsLoading"
            style="width: 100%"
            @change="handleMetricsChange"
          >
            <el-option
              v-for="m in metricsOptions"
              :key="m.metricName"
              :label="m.metricDisplayName || m.metricName"
              :value="m.metricName"
            />
          </el-select>
        </div>

        <div v-if="localDataSource.datasourceId && localDataSource.metrics.length" class="form-group">
          <label class="form-label">{{ t('insight.property.dimensions') }}</label>
          <el-select
            v-model="localDataSource.dimensions"
            :placeholder="t('insight.property.selectDimensions')"
            size="small"
            multiple
            filterable
            remote
            :remote-method="searchDimensions"
            :loading="dimensionsLoading"
            style="width: 100%"
            @change="emitChange"
          >
            <el-option
              v-for="d in dimensionsOptions"
              :key="d.dimName"
              :label="d.dimDisplayName || d.dimName"
              :value="d.dimName"
            />
          </el-select>
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.limit') }}</label>
          <el-input-number
            v-model="localDataSource.limit"
            :min="1"
            :max="500"
            size="small"
            style="width: 100%"
            @change="emitChange"
          />
        </div>

        <!-- 验证数据按钮 -->
        <div v-if="canPreview" class="form-group preview-group">
          <el-button
            size="small"
            type="primary"
            :loading="previewLoading"
            @click="handlePreviewData"
          >
            {{ t('insight.property.previewData') }}
          </el-button>
        </div>

        <!-- 验证数据结果（简要状态） -->
        <div v-if="previewResult" class="preview-result">
          <div v-if="previewResult.error" class="preview-error">
            {{ previewResult.error }}
          </div>
          <div v-else class="preview-ok">
            {{ t('insight.property.previewChartOk') }}
          </div>
        </div>
      </template>

      <!-- 筛选组件配置（仅 filter 组件） -->
      <template v-if="component.type === 'filter'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterField') }}</label>
          <el-input
            v-model="localFilterConfig.field"
            size="small"
            :placeholder="t('insight.property.filterFieldPlaceholder')"
            @change="emitFilterConfigChange"
          />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterOptions') }}</label>
          <el-radio-group
            v-model="localFilterConfig.optionSource"
            size="small"
            @change="emitFilterConfigChange"
          >
            <el-radio-button value="static">{{ t('insight.property.filterOptionStatic') }}</el-radio-button>
            <el-radio-button value="dynamic">{{ t('insight.property.filterOptionDynamic') }}</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 静态选项编辑 -->
        <div v-if="localFilterConfig.optionSource === 'static'" class="form-group">
          <label class="form-label">{{ t('insight.property.filterStaticOptions') }}</label>
          <div
            v-for="(opt, idx) in localFilterConfig.staticOptions"
            :key="idx"
            class="static-option-row"
          >
            <el-input
              v-model="opt.label"
              size="small"
              :placeholder="t('insight.property.optionLabel')"
              style="flex: 1"
              @change="emitFilterConfigChange"
            />
            <el-input
              v-model="opt.value"
              size="small"
              :placeholder="t('insight.property.optionValue')"
              style="flex: 1"
              @change="emitFilterConfigChange"
            />
            <el-button
              text
              size="small"
              @click="removeStaticOption(idx)"
            >
              ✕
            </el-button>
          </div>
          <el-button
            text
            size="small"
            @click="addStaticOption"
          >
            + {{ t('insight.property.addOption') }}
          </el-button>
        </div>

        <!-- 筛选器作用范围 -->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterScope') }}</label>
          <el-radio-group
            v-model="localFilterScope"
            size="small"
            @change="emitFilterConfigChange"
          >
            <el-radio-button value="global">{{ t('insight.property.filterScopeGlobal') }}</el-radio-button>
            <el-radio-button value="scoped">{{ t('insight.property.filterScopeScoped') }}</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 作用范围=指定组件时，选择目标组件 -->
        <div v-if="localFilterScope === 'scoped'" class="form-group">
          <label class="form-label">{{ t('insight.property.filterTargetComponents') }}</label>
          <el-select
            v-model="localTargetComponentIds"
            :placeholder="t('insight.property.filterTargetComponentsPlaceholder')"
            size="small"
            multiple
            style="width: 100%"
            @change="emitFilterConfigChange"
          >
            <el-option
              v-for="c in selectableDataComponents"
              :key="c.id"
              :label="c.title || c.id"
              :value="c.id"
            />
          </el-select>
        </div>
      </template>

      <!-- 时间筛选组件配置（仅 timeFilter 组件） -->
      <template v-if="component.type === 'timeFilter'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.timeFilterField') }}</label>
          <el-input
            v-model="localTimeFilterConfig.field"
            size="small"
            :placeholder="t('insight.property.timeFilterFieldPlaceholder')"
            @change="emitTimeFilterConfigChange"
          />
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.timeFilterPresets') }}</label>
          <el-checkbox-group
            v-model="localTimeFilterPresets"
            size="small"
            @change="emitTimeFilterConfigChange"
          >
            <el-checkbox value="today">{{ t('insight.timeRange.today') }}</el-checkbox>
            <el-checkbox value="7d">{{ t('insight.timeRange.7d') }}</el-checkbox>
            <el-checkbox value="30d">{{ t('insight.timeRange.30d') }}</el-checkbox>
            <el-checkbox value="90d">{{ t('insight.timeRange.90d') }}</el-checkbox>
            <el-checkbox value="custom">{{ t('insight.timeRange.custom') }}</el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterScope') }}</label>
          <el-radio-group
            v-model="localFilterScope"
            size="small"
            @change="emitTimeFilterConfigChange"
          >
            <el-radio-button value="global">{{ t('insight.property.filterScopeGlobal') }}</el-radio-button>
            <el-radio-button value="scoped">{{ t('insight.property.filterScopeScoped') }}</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="localFilterScope === 'scoped'" class="form-group">
          <label class="form-label">{{ t('insight.property.filterTargetComponents') }}</label>
          <el-select
            v-model="localTargetComponentIds"
            :placeholder="t('insight.property.filterTargetComponentsPlaceholder')"
            size="small"
            multiple
            style="width: 100%"
            @change="emitTimeFilterConfigChange"
          >
            <el-option
              v-for="c in selectableDataComponents"
              :key="c.id"
              :label="c.title || c.id"
              :value="c.id"
            />
          </el-select>
        </div>
      </template>

      <!-- 数据组件绑定筛选器（kpi/chart/table 组件） -->
      <template v-if="component.type !== 'filter' && component.type !== 'timeFilter'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.boundFilters') }}</label>
          <el-select
            v-model="localBoundFilterIds"
            :placeholder="t('insight.property.boundFiltersPlaceholder')"
            size="small"
            multiple
            clearable
            style="width: 100%"
            @change="emitChange"
          >
            <el-option
              v-for="f in selectableFilterComponents"
              :key="f.id"
              :label="f.title || f.id"
              :value="f.id"
            />
          </el-select>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { InsightComponent, ComponentDataSource, InsightComponentData, FilterComponentConfig, TimeFilterComponentConfig, TimeRangePreset, FilterScope } from '@/types'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import * as datasourceApi from '@/api/datasource'
import * as insightDashboardApi from '@/api/insight-dashboard'

defineOptions({
  name: 'PropertyPanel',
})

const { t } = useI18n()

const props = defineProps<{
  /** 当前选中的组件 */
  component: InsightComponent | null
  /** 仪表盘所有组件列表（用于筛选器绑定配置） */
  allComponents?: InsightComponent[]
}>()

const emit = defineEmits<{
  (e: 'change', component: InsightComponent): void
  (e: 'preview', data: InsightComponentData): void
}>()

const datasourceStore = useDatasourceStore()

/** 本地编辑副本（深拷贝） */
const localComponent = reactive<InsightComponent>({
  id: '',
  type: 'kpi',
  title: '',
  position: { x: 0, y: 0, w: 6, h: 4 },
})

const localDataSource = reactive<ComponentDataSource>({
  datasourceId: '',
  metrics: [],
  dimensions: [],
  filters: [],
  limit: 100,
})

/** 筛选组件配置本地副本 */
const localFilterConfig = reactive<FilterComponentConfig>({
  field: '',
  optionSource: 'static',
  staticOptions: [],
})

/** 时间筛选组件配置本地副本 */
const localTimeFilterConfig = reactive<TimeFilterComponentConfig>({
  field: 'metric_time',
})
const localTimeFilterPresets = ref<TimeRangePreset[]>(['today', '7d', '30d', '90d', 'custom'])

/** 筛选器作用范围本地副本 */
const localFilterScope = ref<FilterScope>('global')
const localTargetComponentIds = ref<string[]>([])
/** 图表组件绑定的筛选器 ID 列表 */
const localBoundFilterIds = ref<string[]>([])

/** 指标/维度选项与加载状态 */
const metricsOptions = ref<Array<{ metricName: string; metricDisplayName: string }>>([])
const dimensionsOptions = ref<Array<{ dimName: string; dimDisplayName: string }>>([])
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)

/** 验证数据相关 */
const previewLoading = ref(false)
const previewResult = ref<InsightComponentData | null>(null)

/** 是否可以预览（数据源 + 至少一个指标已配置） */
const canPreview = computed(() => {
  return localDataSource.datasourceId
    && localDataSource.metrics.length > 0
})

/** 可选的目标组件列表（数据组件：kpi/chart/table，排除自身） */
const selectableDataComponents = computed(() => {
  return (props.allComponents ?? []).filter(c =>
    c.type !== 'filter' && c.type !== 'timeFilter' && c.id !== localComponent.id
  )
})

/** 可选的筛选器组件列表（filter/timeFilter，排除自身） */
const selectableFilterComponents = computed(() => {
  return (props.allComponents ?? []).filter(c =>
    (c.type === 'filter' || c.type === 'timeFilter') && c.id !== localComponent.id
  )
})

/** 搜索防抖定时器 */
let metricsSearchTimer: ReturnType<typeof setTimeout> | null = null
let dimensionsSearchTimer: ReturnType<typeof setTimeout> | null = null

/** 监听外部 component 变化，同步到本地（仅在引用变化时触发，避免 emitChange 导致的循环） */
watch(
  () => props.component,
  (newComp) => {
    if (!newComp) {
      return
    }
    previewResult.value = null
    Object.assign(localComponent, JSON.parse(JSON.stringify(newComp)))
    if (newComp.dataSource) {
      Object.assign(localDataSource, JSON.parse(JSON.stringify(newComp.dataSource)))
      loadMetrics(localDataSource.datasourceId)
      loadDimensions(localDataSource.datasourceId, localDataSource.metrics)
    } else {
      localDataSource.datasourceId = ''
      localDataSource.metrics = []
      localDataSource.dimensions = []
      metricsOptions.value = []
      dimensionsOptions.value = []
    }
    // 同步筛选组件配置
    if (newComp.type === 'filter') {
      const config = newComp.config as FilterComponentConfig | undefined
      localFilterConfig.field = config?.field ?? ''
      localFilterConfig.optionSource = config?.optionSource ?? 'static'
      localFilterConfig.staticOptions = config?.staticOptions ? JSON.parse(JSON.stringify(config.staticOptions)) : []
    }
    // 同步时间筛选组件配置
    if (newComp.type === 'timeFilter') {
      const config = newComp.config as TimeFilterComponentConfig | undefined
      localTimeFilterConfig.field = config?.field ?? 'metric_time'
      localTimeFilterPresets.value = config?.availablePresets ?? ['today', '7d', '30d', '90d', 'custom']
    }
    // 同步筛选器作用范围配置
    if (newComp.type === 'filter') {
      const config = newComp.config as FilterComponentConfig | undefined
      localFilterScope.value = config?.scope ?? 'global'
      localTargetComponentIds.value = config?.targetComponentIds ?? []
    }
    if (newComp.type === 'timeFilter') {
      const config = newComp.config as TimeFilterComponentConfig | undefined
      localFilterScope.value = config?.scope ?? 'global'
      localTargetComponentIds.value = config?.targetComponentIds ?? []
    }
    // 同步数据组件的绑定筛选器
    if (newComp.type !== 'filter' && newComp.type !== 'timeFilter') {
      localBoundFilterIds.value = newComp.boundFilterIds ?? []
    }
  },
  { immediate: true }
)

/** 加载指标选项（初始加载，不带关键字） */
async function loadMetrics(datasourceId: string): Promise<void> {
  if (!datasourceId) {
    metricsOptions.value = []
    return
  }
  try {
    const result = await datasourceApi.listSyncedMetrics(datasourceId, 1, 50)
    metricsOptions.value = (result as unknown as Array<{ metricName: string; metricDisplayName: string }>) ?? []
  } catch (e) {
    console.error('[PropertyPanel] load metrics error:', e)
  }
}

/** 加载维度选项（基于已选指标关联的维度） */
async function loadDimensions(datasourceId: string, metricNames: string[], keyword?: string): Promise<void> {
  if (!datasourceId || !metricNames.length) {
    dimensionsOptions.value = []
    return
  }
  try {
    const result = await datasourceApi.listMetricsDimensionDetails(datasourceId, metricNames, keyword)
    dimensionsOptions.value = (result as unknown as Array<{ dimName: string; dimDisplayName: string }>) ?? []
  } catch (e) {
    console.error('[PropertyPanel] load dimensions error:', e)
  }
}

/** 远程搜索指标（防抖 300ms） */
function searchMetrics(query: string): void {
  if (metricsSearchTimer) {
    clearTimeout(metricsSearchTimer)
  }
  if (!localDataSource.datasourceId) {
    return
  }
  metricsSearchTimer = setTimeout(async () => {
    metricsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      const result = await datasourceApi.listSyncedMetrics(localDataSource.datasourceId, 1, 50, keyword)
      metricsOptions.value = (result as unknown as Array<{ metricName: string; metricDisplayName: string }>) ?? []
    } catch (e) {
      console.error('[PropertyPanel] search metrics error:', e)
    } finally {
      metricsLoading.value = false
    }
  }, 300)
}

/** 远程搜索维度（基于已选指标关联维度，防抖 300ms） */
function searchDimensions(query: string): void {
  if (dimensionsSearchTimer) {
    clearTimeout(dimensionsSearchTimer)
  }
  if (!localDataSource.datasourceId || !localDataSource.metrics.length) {
    return
  }
  dimensionsSearchTimer = setTimeout(async () => {
    dimensionsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      await loadDimensions(localDataSource.datasourceId, localDataSource.metrics, keyword)
    } catch (e) {
      console.error('[PropertyPanel] search dimensions error:', e)
    } finally {
      dimensionsLoading.value = false
    }
  }, 300)
}

/** 验证数据：调用 preview-component 端点，结果 emit 到画布渲染 */
async function handlePreviewData(): Promise<void> {
  if (!canPreview.value) {
    return
  }
  previewLoading.value = true
  previewResult.value = null
  try {
    const comp: InsightComponent = {
      ...JSON.parse(JSON.stringify(localComponent)),
      dataSource: JSON.parse(JSON.stringify(localDataSource)),
    }
    const result = await insightDashboardApi.previewComponent(comp) as unknown as InsightComponentData
    previewResult.value = result
    // 将渲染数据 emit 给 Editor，写入 componentDataMap，画布组件自动渲染
    emit('preview', result)
    if (result.error) {
      ElMessage.warning(result.error)
    } else {
      ElMessage.success(t('insight.property.previewChartOk'))
    }
  } catch (e: any) {
    const errorData: InsightComponentData = { componentId: localComponent.id, renderType: 'table', error: e.message ?? t('insight.previewDataFailed') }
    previewResult.value = errorData
    emit('preview', errorData)
  } finally {
    previewLoading.value = false
  }
}

/** 数据源变更时重新加载指标/维度 */
function handleDatasourceChange(): void {
  localDataSource.metrics = []
  localDataSource.dimensions = []
  previewResult.value = null
  dimensionsOptions.value = []
  loadMetrics(localDataSource.datasourceId)
  emitChange()
}

/** 指标变更时重新加载关联维度，并清除不在关联范围内的已选维度 */
function handleMetricsChange(): void {
  previewResult.value = null
  if (localDataSource.datasourceId && localDataSource.metrics.length) {
    loadDimensions(localDataSource.datasourceId, localDataSource.metrics).then(() => {
      // 清除不在关联维度选项中的已选维度
      const validDimNames = new Set(dimensionsOptions.value.map(d => d.dimName))
      const filtered = localDataSource.dimensions.filter(d => validDimNames.has(d))
      if (filtered.length !== localDataSource.dimensions.length) {
        localDataSource.dimensions = filtered
      }
    })
  } else {
    localDataSource.dimensions = []
    dimensionsOptions.value = []
  }
  emitChange()
}

/** 触发变更事件（不包含 position，position 由画布拖拽管理） */
function emitChange(): void {
  previewResult.value = null
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    // position 不 emit，由画布拖拽/缩放管理
    dataSource: localDataSource.datasourceId ? JSON.parse(JSON.stringify(localDataSource)) : undefined,
    boundFilterIds: localBoundFilterIds.value.length > 0 ? [...localBoundFilterIds.value] : undefined,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 筛选配置变更时 emit（将 config 写入组件） */
function emitFilterConfigChange(): void {
  const config: FilterComponentConfig = {
    field: localFilterConfig.field,
    optionSource: localFilterConfig.optionSource,
    staticOptions: localFilterConfig.optionSource === 'static'
      ? JSON.parse(JSON.stringify(localFilterConfig.staticOptions))
      : undefined,
    scope: localFilterScope.value,
    targetComponentIds: localFilterScope.value === 'scoped' ? [...localTargetComponentIds.value] : undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 添加静态选项 */
function addStaticOption(): void {
  if (!localFilterConfig.staticOptions) {
    localFilterConfig.staticOptions = []
  }
  localFilterConfig.staticOptions.push({ label: '', value: '' })
}

/** 移除静态选项 */
function removeStaticOption(idx: number): void {
  if (localFilterConfig.staticOptions) {
    localFilterConfig.staticOptions.splice(idx, 1)
    emitFilterConfigChange()
  }
}

/** 时间筛选配置变更时 emit（将 config 写入组件） */
function emitTimeFilterConfigChange(): void {
  const config: TimeFilterComponentConfig = {
    field: localTimeFilterConfig.field || 'metric_time',
    availablePresets: localTimeFilterPresets.value.length > 0
      ? [...localTimeFilterPresets.value]
      : undefined,
    scope: localFilterScope.value,
    targetComponentIds: localFilterScope.value === 'scoped' ? [...localTargetComponentIds.value] : undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 初始化：加载数据源列表 */
datasourceStore.fetchDatasources().catch(() => {
  // 静默失败，列表可能在其他页面已加载
})
</script>

<style scoped>
.property-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-left: 1px solid var(--theme-border);
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--theme-text-muted);
}

.empty-icon {
  font-size: 36px;
}

.empty-text {
  font-size: 13px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-text-secondary);
}

.preview-group {
  margin-top: 4px;
}

.preview-result {
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--theme-surface-elevated);
  border: 1px solid var(--theme-border);
}

.preview-error {
  color: var(--el-color-danger);
  font-size: 13px;
}

.preview-ok {
  color: var(--el-color-success);
  font-size: 13px;
}

.static-option-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
</style>
