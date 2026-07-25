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

      <!-- 数据绑定（kpi/chart/table 组件；筛选器与时间筛选无需数据源/指标） -->
      <template v-if="component.type !== 'filter' && component.type !== 'timeFilter'">
        <!-- 多指标模式开关（仅 kpi 组件） -->
        <div v-if="component.type === 'kpi'" class="form-group">
          <label class="form-label">{{ t('insight.property.multiKpi') }}</label>
          <el-switch
            v-model="localMultiKpi"
            size="small"
            @change="emitChange"
          />
          <span class="form-hint">{{ t('insight.property.multiKpiHint') }}</span>
        </div>

        <!-- 多 Tab 模式开关 -->
        <div v-if="component.type === 'table' || component.type === 'chart' || component.type === 'kpi'" class="form-group">
          <label class="form-label">多 Tab 模式</label>
          <el-switch
            v-model="tabModeEnabled"
            size="small"
            @change="handleTabModeToggle"
          />
          <span class="form-hint">开启后组件支持多个 Tab 切换不同数据源</span>
        </div>

        <!-- Tab 管理区域（多 Tab 模式开启时显示） -->
        <template v-if="tabModeEnabled">
          <div class="form-group">
            <label class="form-label">Tab 列表</label>
            <div class="tab-list-editor">
              <div
                v-for="(tab, idx) in localTabs"
                :key="tab.id"
                class="tab-item-row"
                :class="{ active: activeTabIndex === idx }"
                @click="activeTabIndex = idx"
              >
                <el-input
                  v-model="tab.title"
                  size="small"
                  placeholder="Tab 标题"
                  style="flex: 1"
                  @change="emitTabChange"
                />
                <el-button
                  text
                  size="small"
                  @click.stop="removeTab(idx)"
                >✕</el-button>
              </div>
              <el-button
                text
                size="small"
                @click="addTab"
              >+ 添加 Tab</el-button>
            </div>
          </div>

          <!-- 当前选中 Tab 的数据源配置 -->
          <div v-if="activeTab" class="tab-datasource-section">
            <div class="tab-datasource-title">Tab「{{ activeTab.title || '未命名' }}」数据源</div>
            <div class="form-group">
              <label class="form-label">{{ t('insight.property.datasource') }}</label>
              <el-select
                v-model="activeTab.dataSource.datasourceId"
                :placeholder="t('insight.property.selectDatasource')"
                size="small"
                filterable
                style="width: 100%"
                @change="handleTabDatasourceChange"
              >
                <el-option
                  v-for="ds in datasourceStore.datasources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                />
              </el-select>
            </div>

            <div v-if="activeTab.dataSource.datasourceId" class="form-group">
              <label class="form-label">{{ t('insight.property.metrics') }}</label>
              <el-select
                v-model="activeTab.dataSource.metrics"
                :placeholder="t('insight.property.selectMetrics')"
                size="small"
                multiple
                filterable
                remote
                :remote-method="searchMetrics"
                :loading="metricsLoading"
                style="width: 100%"
                @change="emitTabChange"
              >
                <el-option
                  v-for="m in metricsOptions"
                  :key="m.metricName"
                  :label="m.metricDisplayName || m.metricName"
                  :value="m.metricName"
                />
              </el-select>
            </div>

            <div v-if="activeTab.dataSource.datasourceId && activeTab.dataSource.metrics.length" class="form-group">
              <label class="form-label">{{ t('insight.property.dimensions') }}</label>
              <el-select
                v-model="activeTab.dataSource.dimensions"
                :placeholder="t('insight.property.selectDimensions')"
                size="small"
                multiple
                filterable
                remote
                :remote-method="searchDimensions"
                :loading="dimensionsLoading"
                style="width: 100%"
                @change="emitTabChange"
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
                v-model="activeTab.dataSource.limit"
                :min="1"
                :max="500"
                size="small"
                style="width: 100%"
                @change="emitTabChange"
              />
            </div>
          </div>
        </template>

        <!-- 单数据源模式（原有逻辑） -->
        <template v-else>
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
      </template>

      <!-- 筛选组件配置（仅 filter 组件） -->
      <template v-if="component.type === 'filter'">
        <!-- 数据源（用于加载筛选字段维度列表）-->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.datasource') }}</label>
          <el-select
            v-model="localFilterDatasourceId"
            :placeholder="t('insight.property.selectDatasource')"
            size="small"
            filterable
            style="width: 100%"
            @change="handleFilterDatasourceChange"
          >
            <el-option
              v-for="ds in datasourceStore.datasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </div>

        <!-- 筛选字段（从维度下拉选择）-->
        <div v-if="localFilterDatasourceId" class="form-group">
          <label class="form-label">{{ t('insight.property.filterField') }}</label>
          <el-select
            v-model="localFilterConfig.field"
            :placeholder="t('insight.property.selectDimensions')"
            size="small"
            filterable
            remote
            :remote-method="searchFilterDimensions"
            :loading="filterDimensionsLoading"
            style="width: 100%"
            @change="emitFilterConfigChange"
          >
            <el-option
              v-for="d in filterDimensionsOptions"
              :key="d.dimName"
              :label="d.dimDisplayName || d.dimName"
              :value="d.dimName"
            />
          </el-select>
        </div>

        <!-- 选项来源：静态手填 / 动态自动取值 -->
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
          <span class="form-hint">
            {{ localFilterConfig.optionSource === 'dynamic'
              ? t('insight.property.filterOptionDynamicHint')
              : t('insight.property.filterOptionStaticHint') }}
          </span>
        </div>

        <!-- 静态选项编辑（仅静态来源）-->
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

      <!-- 时间筛选组件配置（仅 timeFilter 组件；时间字段固定 metric_time，无需数据源/指标） -->
      <template v-if="component.type === 'timeFilter'">
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

      <!-- AI 分析组件配置（仅 aiAnalysis 组件） -->
      <template v-if="component.type === 'aiAnalysis'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.aiAnalysisPrompt') }}</label>
          <el-input
            v-model="localAiAnalysisPrompt"
            type="textarea"
            :rows="3"
            size="small"
            :placeholder="t('insight.property.aiAnalysisPromptPlaceholder')"
            @change="emitAiAnalysisConfigChange"
          />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.aiAnalysisAutoGenerate') }}</label>
          <el-switch
            v-model="localAiAnalysisAutoGenerate"
            size="small"
            @change="emitAiAnalysisConfigChange"
          />
        </div>
      </template>

      <!-- 数据组件绑定筛选器（kpi/chart/table 组件） -->
      <template v-if="component.type !== 'filter' && component.type !== 'timeFilter' && component.type !== 'aiAnalysis'">
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

        <!-- 组件级时间筛选开关 -->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.enableTimeFilter') }}</label>
          <el-switch
            v-model="localEnableTimeFilter"
            size="small"
            @change="emitChange"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { InsightComponent, ComponentDataSource, ComponentTab, InsightComponentData, FilterComponentConfig, TimeFilterComponentConfig, AIAnalysisComponentConfig, TimeRangePreset, FilterScope } from '@/types'
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
/** 筛选器数据源 ID */
const localFilterDatasourceId = ref<string>('')
/** 筛选器维度选项与加载状态 */
const filterDimensionsOptions = ref<Array<{ dimName: string; dimDisplayName: string }>>([])
const filterDimensionsLoading = ref(false)

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
/** 组件级时间筛选开关 */
const localEnableTimeFilter = ref(false)
/** 多指标模式开关（仅 kpi 组件） */
const localMultiKpi = ref(false)
/** AI 分析组件配置本地副本 */
const localAiAnalysisPrompt = ref('')
const localAiAnalysisAutoGenerate = ref(false)

/** 多 Tab 模式状态 */
const tabModeEnabled = ref(false)
const localTabs = ref<ComponentTab[]>([])
const activeTabIndex = ref(0)
const activeTab = computed(() => localTabs.value[activeTabIndex.value] ?? null)

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
let filterDimensionsSearchTimer: ReturnType<typeof setTimeout> | null = null

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
      // 同步筛选器数据源 ID（优先使用 config.datasourceId，兼容旧数据）
      localFilterDatasourceId.value = config?.datasourceId ?? ''
      if (localFilterDatasourceId.value) {
        loadFilterDimensions(localFilterDatasourceId.value)
      } else {
        filterDimensionsOptions.value = []
      }
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
      localEnableTimeFilter.value = newComp.enableTimeFilter ?? false
    }
    // 同步多指标模式配置（仅 kpi 组件）
    if (newComp.type === 'kpi') {
      localMultiKpi.value = newComp.multiKpi ?? false
    }
    // 同步 AI 分析组件配置
    if (newComp.type === 'aiAnalysis') {
      const config = newComp.config as AIAnalysisComponentConfig | undefined
      localAiAnalysisPrompt.value = config?.promptTemplate ?? ''
      localAiAnalysisAutoGenerate.value = config?.autoGenerate ?? false
    }
    // 同步多 Tab 配置（仅在外部组件引用变化时同步，避免 emitTabChange 导致的循环重置）
    if (newComp.tabs && newComp.tabs.length > 0) {
      tabModeEnabled.value = true
      // 仅在 tabs 引用变化时才深拷贝覆盖，避免编辑中覆盖本地状态
      const newTabsJson = JSON.stringify(newComp.tabs)
      const localTabsJson = JSON.stringify(localTabs.value)
      if (newTabsJson !== localTabsJson) {
        localTabs.value = JSON.parse(JSON.stringify(newComp.tabs))
        // 保持当前选中 Tab 不变，仅在越界时修正
        if (activeTabIndex.value >= localTabs.value.length) {
          activeTabIndex.value = 0
        }
        // 加载当前 Tab 的指标/维度选项
        const currentTab = localTabs.value[activeTabIndex.value]
        if (currentTab?.dataSource?.datasourceId) {
          loadMetrics(currentTab.dataSource.datasourceId)
          if (currentTab.dataSource.metrics.length) {
            loadDimensions(currentTab.dataSource.datasourceId, currentTab.dataSource.metrics)
          }
        }
      }
    } else {
      tabModeEnabled.value = false
      localTabs.value = []
      activeTabIndex.value = 0
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

/** 获取当前生效的数据源 ID（Tab 模式取 activeTab，否则取 localDataSource） */
function getEffectiveDatasourceId(): string {
  return tabModeEnabled.value && activeTab.value
    ? activeTab.value.dataSource.datasourceId
    : localDataSource.datasourceId
}

/** 获取当前生效的指标列表 */
function getEffectiveMetrics(): string[] {
  return tabModeEnabled.value && activeTab.value
    ? activeTab.value.dataSource.metrics
    : localDataSource.metrics
}

/** 远程搜索指标（防抖 300ms） */
function searchMetrics(query: string): void {
  if (metricsSearchTimer) {
    clearTimeout(metricsSearchTimer)
  }
  const dsId = getEffectiveDatasourceId()
  if (!dsId) {
    return
  }
  metricsSearchTimer = setTimeout(async () => {
    metricsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      const result = await datasourceApi.listSyncedMetrics(dsId, 1, 50, keyword)
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
  const dsId = getEffectiveDatasourceId()
  const metrics = getEffectiveMetrics()
  if (!dsId || !metrics.length) {
    return
  }
  dimensionsSearchTimer = setTimeout(async () => {
    dimensionsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      await loadDimensions(dsId, metrics, keyword)
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
    dataSource: tabModeEnabled.value ? undefined : (localDataSource.datasourceId ? JSON.parse(JSON.stringify(localDataSource)) : undefined),
    tabs: tabModeEnabled.value && localTabs.value.length > 0
      ? JSON.parse(JSON.stringify(localTabs.value))
      : undefined,
    boundFilterIds: localBoundFilterIds.value.length > 0 ? [...localBoundFilterIds.value] : undefined,
    enableTimeFilter: localEnableTimeFilter.value || undefined,
    multiKpi: localComponent.type === 'kpi' ? (localMultiKpi.value || undefined) : undefined,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 筛选器数据源变更时清空已选字段并重新加载维度列表 */
function handleFilterDatasourceChange(): void {
  localFilterConfig.field = ''
  filterDimensionsOptions.value = []
  if (localFilterDatasourceId.value) {
    loadFilterDimensions(localFilterDatasourceId.value)
  }
  emitFilterConfigChange()
}

/** 加载筛选器维度选项（复用已同步维度列表接口，支持关键字服务端搜索）*/
async function loadFilterDimensions(datasourceId: string, keyword?: string): Promise<void> {
  if (!datasourceId) {
    filterDimensionsOptions.value = []
    return
  }
  filterDimensionsLoading.value = true
  try {
    const result = await datasourceApi.listSyncedDimensions(datasourceId, 1, 200, keyword)
    const list = (result as unknown as Array<{ dimName: string; dimDisplayName: string }>) ?? []
    // 按 dimName 去重（独立维度表可能存在同名多行，见 listSyncedDimensions 无 DISTINCT）
    const seen = new Set<string>()
    filterDimensionsOptions.value = list.filter((d) => {
      if (!d.dimName || seen.has(d.dimName)) return false
      seen.add(d.dimName)
      return true
    })
  } catch (e) {
    console.error('[PropertyPanel] load filter dimensions error:', e)
  } finally {
    filterDimensionsLoading.value = false
  }
}

/** 筛选字段维度远程搜索（服务端关键字，防抖 300ms，避免 >200 维度被前端截断）*/
function searchFilterDimensions(query: string): void {
  if (filterDimensionsSearchTimer) {
    clearTimeout(filterDimensionsSearchTimer)
  }
  if (!localFilterDatasourceId.value) {
    return
  }
  filterDimensionsSearchTimer = setTimeout(() => {
    loadFilterDimensions(localFilterDatasourceId.value, query.trim() || undefined)
  }, 300)
}

/** 筛选配置变更时 emit（将 config 写入组件） */
function emitFilterConfigChange(): void {
  const config: FilterComponentConfig = {
    field: localFilterConfig.field,
    optionSource: localFilterConfig.optionSource,
    // 两种模式都需要数据源来加载筛选字段维度列表
    datasourceId: localFilterDatasourceId.value || undefined,
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

/** AI 分析组件配置变更时 emit */
function emitAiAnalysisConfigChange(): void {
  const config: AIAnalysisComponentConfig = {
    promptTemplate: localAiAnalysisPrompt.value || undefined,
    autoGenerate: localAiAnalysisAutoGenerate.value || undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 多 Tab 模式开关切换 */
function handleTabModeToggle(): void {
  if (tabModeEnabled.value) {
    // 开启 Tab 模式：如果已有单数据源配置，迁移为第一个 Tab
    if (localTabs.value.length === 0) {
      const tabId = 'tab_' + Date.now()
      localTabs.value = [{
        id: tabId,
        title: localComponent.title || 'Tab 1',
        dataSource: JSON.parse(JSON.stringify(localDataSource)),
      }]
      activeTabIndex.value = 0
    }
  } else {
    // 关闭 Tab 模式：如果只有一个 Tab，迁移回单数据源
    if (localTabs.value.length === 1) {
      const tab = localTabs.value[0]
      Object.assign(localDataSource, JSON.parse(JSON.stringify(tab.dataSource)))
    }
    localTabs.value = []
  }
  emitTabChange()
}

/** 添加 Tab */
function addTab(): void {
  const tabId = 'tab_' + Date.now()
  localTabs.value.push({
    id: tabId,
    title: `Tab ${localTabs.value.length + 1}`,
    dataSource: {
      datasourceId: '',
      metrics: [],
      dimensions: [],
      filters: [],
      limit: 100,
    },
  })
  activeTabIndex.value = localTabs.value.length - 1
  // 切换到新 Tab 时加载对应指标选项
  metricsOptions.value = []
  dimensionsOptions.value = []
  emitTabChange()
}

/** 删除 Tab */
function removeTab(idx: number): void {
  localTabs.value.splice(idx, 1)
  if (activeTabIndex.value >= localTabs.value.length) {
    activeTabIndex.value = Math.max(0, localTabs.value.length - 1)
  }
  emitTabChange()
}

/** 切换 Tab 时重新加载指标/维度选项 */
watch(activeTabIndex, () => {
  metricsOptions.value = []
  dimensionsOptions.value = []
  if (activeTab.value?.dataSource?.datasourceId) {
    loadMetrics(activeTab.value.dataSource.datasourceId)
    if (activeTab.value.dataSource.metrics.length) {
      loadDimensions(activeTab.value.dataSource.datasourceId, activeTab.value.dataSource.metrics)
    }
  }
})

/** Tab 数据源变更时重新加载指标/维度 */
function handleTabDatasourceChange(): void {
  if (activeTab.value) {
    activeTab.value.dataSource.metrics = []
    activeTab.value.dataSource.dimensions = []
    dimensionsOptions.value = []
    loadMetrics(activeTab.value.dataSource.datasourceId)
  }
  emitTabChange()
}

/** Tab 配置变更时 emit */
function emitTabChange(): void {
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    tabs: tabModeEnabled.value && localTabs.value.length > 0
      ? JSON.parse(JSON.stringify(localTabs.value))
      : undefined,
    // Tab 模式下清除主 dataSource（后端以 tabs 为准）
    dataSource: tabModeEnabled.value ? undefined : (localDataSource.datasourceId ? JSON.parse(JSON.stringify(localDataSource)) : undefined),
    boundFilterIds: localBoundFilterIds.value.length > 0 ? [...localBoundFilterIds.value] : undefined,
    enableTimeFilter: localEnableTimeFilter.value || undefined,
    multiKpi: localComponent.type === 'kpi' ? (localMultiKpi.value || undefined) : undefined,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 添加静态选项 */
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
  background: var(--db-card);
  border-left: 1px solid var(--db-border);
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  border-bottom: 1px solid var(--db-border);
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
  color: var(--db-text-muted);
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
  color: var(--db-text-secondary);
}

.preview-group {
  margin-top: 4px;
}

.preview-result {
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--db-hover);
  border: 1px solid var(--db-border);
}

.preview-error {
  color: var(--el-color-danger);
  font-size: 13px;
}

.preview-ok {
  color: var(--el-color-success);
  font-size: 13px;
}

.form-hint {
  font-size: 11px;
  color: var(--db-text-muted);
  margin-top: 2px;
}

.tab-list-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tab-item-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: 4px;
  border: 1px solid transparent;
  cursor: pointer;
}

.tab-item-row.active {
  border-color: var(--db-accent);
  background: var(--db-accent-light);
}

.tab-datasource-section {
  border: 1px dashed var(--db-border);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tab-datasource-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-accent);
  margin-bottom: 4px;
}

.static-option-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
</style>
