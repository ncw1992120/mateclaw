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
            @change="emitChange"
          >
            <el-option
              v-for="m in metricsOptions"
              :key="m.metricName"
              :label="m.metricDisplayName || m.metricName"
              :value="m.metricName"
            />
          </el-select>
        </div>

        <div v-if="localDataSource.datasourceId" class="form-group">
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

        <!-- 验证数据结果 -->
        <div v-if="previewResult" class="preview-result">
          <div v-if="previewResult.error" class="preview-error">
            {{ previewResult.error }}
          </div>
          <div v-else-if="previewResult.renderType === 'kpi'" class="preview-kpi">
            <span class="preview-kpi-name">{{ previewResult.kpi?.name }}</span>
            <span class="preview-kpi-value">{{ previewResult.kpi?.value }}</span>
            <span v-if="previewResult.kpi?.chg" class="preview-kpi-chg" :class="{ up: previewResult.kpi?.up }">
              {{ previewResult.kpi?.up ? '+' : '-' }}{{ previewResult.kpi?.chg }}
            </span>
          </div>
          <div v-else-if="previewResult.renderType === 'echarts'" class="preview-chart">
            <span class="preview-label">{{ t('insight.property.previewChartOk') }}</span>
          </div>
          <div v-else-if="previewResult.renderType === 'table'" class="preview-table">
            <span class="preview-label">{{ t('insight.property.previewTableOk', { rows: previewResult.table?.rows?.length ?? 0 }) }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { InsightComponent, ComponentDataSource, InsightComponentData } from '@/types'
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
}>()

const emit = defineEmits<{
  (e: 'change', component: InsightComponent): void
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
      loadMetricsAndDimensions(localDataSource.datasourceId)
    } else {
      localDataSource.datasourceId = ''
      localDataSource.metrics = []
      localDataSource.dimensions = []
      metricsOptions.value = []
      dimensionsOptions.value = []
    }
  },
  { immediate: true }
)

/** 加载指标和维度选项（初始加载，不带关键字） */
async function loadMetricsAndDimensions(datasourceId: string): Promise<void> {
  if (!datasourceId) {
    metricsOptions.value = []
    dimensionsOptions.value = []
    return
  }
  try {
    const [metrics, dimensions] = await Promise.all([
      datasourceApi.listSyncedMetrics(datasourceId, 1, 50),
      datasourceApi.listSyncedDimensions(datasourceId, 1, 50),
    ])
    metricsOptions.value = (metrics as unknown as Array<{ metricName: string; metricDisplayName: string }>) ?? []
    dimensionsOptions.value = (dimensions as unknown as Array<{ dimName: string; dimDisplayName: string }>) ?? []
  } catch (e) {
    console.error('[PropertyPanel] load metrics/dimensions error:', e)
    ElMessage.error(t('insight.property.loadFailed'))
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

/** 远程搜索维度（防抖 300ms） */
function searchDimensions(query: string): void {
  if (dimensionsSearchTimer) {
    clearTimeout(dimensionsSearchTimer)
  }
  if (!localDataSource.datasourceId) {
    return
  }
  dimensionsSearchTimer = setTimeout(async () => {
    dimensionsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      const result = await datasourceApi.listSyncedDimensions(localDataSource.datasourceId, 1, 50, keyword)
      dimensionsOptions.value = (result as unknown as Array<{ dimName: string; dimDisplayName: string }>) ?? []
    } catch (e) {
      console.error('[PropertyPanel] search dimensions error:', e)
    } finally {
      dimensionsLoading.value = false
    }
  }, 300)
}

/** 验证数据：调用 preview-component 端点 */
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
    if (result.error) {
      ElMessage.warning(result.error)
    }
  } catch (e: any) {
    previewResult.value = { componentId: localComponent.id, renderType: 'table', error: e.message ?? t('insight.previewDataFailed') }
  } finally {
    previewLoading.value = false
  }
}

/** 数据源变更时重新加载指标/维度 */
function handleDatasourceChange(): void {
  localDataSource.metrics = []
  localDataSource.dimensions = []
  previewResult.value = null
  loadMetricsAndDimensions(localDataSource.datasourceId)
  emitChange()
}

/** 触发变更事件 */
function emitChange(): void {
  previewResult.value = null
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    dataSource: localDataSource.datasourceId ? JSON.parse(JSON.stringify(localDataSource)) : undefined,
  }
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

.preview-kpi {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-kpi-name {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.preview-kpi-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text);
}

.preview-kpi-chg {
  font-size: 12px;
  color: var(--el-color-danger);
}

.preview-kpi-chg.up {
  color: var(--el-color-success);
}

.preview-label {
  font-size: 13px;
  color: var(--el-color-success);
}
</style>
