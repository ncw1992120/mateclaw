<template>
  <div class="metric-platform-panel">
    <!-- 指标平台连接 -->
    <section class="mp-section">
      <div class="mp-card">
        <div class="section-header">
          <div class="section-header-left">
            <span class="section-icon">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12.55a11 11 0 0 1 14.08 0" />
                <path d="M1.42 9a16 16 0 0 1 21.16 0" />
                <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
                <line x1="12" y1="20" x2="12.01" y2="20" />
              </svg>
            </span>
            <div class="title-block">
              <h2 class="section-title">{{ t('metricPlatform.sectionTitle') }}</h2>
              <p class="section-desc">{{ t('metricPlatform.sectionDesc') }}</p>
            </div>
          </div>
          <div class="section-actions">
            <template v-if="!isEditing">
              <button
                class="section-action-btn"
                :title="t('datasourcePage.actionEdit')"
                @click="handleEdit"
              >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 20h9" />
                  <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
                </svg>
                <span>{{ t('datasourcePage.actionEdit') }}</span>
              </button>
            </template>
            <template v-else>
              <button
                class="section-action-btn"
                :disabled="saving"
                @click="handleCancel"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                class="section-action-btn"
                :disabled="saving"
                @click="handleSave"
              >
                <span v-if="saving" class="btn-spinner" />
                <span>{{ saving ? t('common.loading') : t('common.save') }}</span>
              </button>
            </template>
          </div>
        </div>

        <div class="form-grid">
          <!-- 显示名称 -->
          <div class="form-field form-field-wide">
            <label class="form-label required">{{ t('metricPlatform.fieldDisplayName') }}</label>
            <input
              v-model="form.displayName"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderDisplayName')"
            />
          </div>

          <!-- 产品层服务地址 -->
          <div class="form-field">
            <label class="form-label required">{{ t('metricPlatform.fieldProductAddress') }}</label>
            <input
              v-model="form.productAddress"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderProductAddress')"
            />
          </div>

          <!-- 产品层端口 -->
          <div class="form-field">
            <label class="form-label required">{{ t('metricPlatform.fieldProductPort') }}</label>
            <input
              v-model="form.productPort"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderProductPort')"
            />
          </div>

          <!-- 语义层服务地址 -->
          <div class="form-field">
            <label class="form-label required">{{ t('metricPlatform.fieldSemanticAddress') }}</label>
            <input
              v-model="form.semanticAddress"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderSemanticAddress')"
            />
          </div>

          <!-- 语义层端口 -->
          <div class="form-field">
            <label class="form-label required">{{ t('metricPlatform.fieldSemanticPort') }}</label>
            <input
              v-model="form.semanticPort"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderSemanticPort')"
            />
          </div>

          <!-- 租户 ID -->
          <div class="form-field">
            <label class="form-label required">
              <span>{{ t('metricPlatform.fieldTenantId') }}</span>
              <el-tooltip :content="t('metricPlatform.tooltipTenantId')" placement="top">
                <span class="form-tip">?</span>
              </el-tooltip>
            </label>
            <input
              v-model="form.tenantId"
              class="form-input"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderTenantId')"
            />
          </div>

          <!-- 认证方式 -->
          <div class="form-field">
            <label class="form-label required">
              <span>{{ t('metricPlatform.fieldAuthMethod') }}</span>
              <el-tooltip :content="t('metricPlatform.tooltipAuthMethod')" placement="top">
                <span class="form-tip">?</span>
              </el-tooltip>
            </label>
            <select v-model="form.authMethod" class="form-select" :disabled="!isEditing">
              <option value="UID">UID</option>
              <option value="TOKEN">TOKEN</option>
              <option value="ACCOUNT">ACCOUNT</option>
              <option value="APIKEY">APIKEY</option>
            </select>
          </div>

          <!-- 认证值 -->
          <div class="form-field form-field-wide">
            <label class="form-label required">
              <span>{{ t('metricPlatform.fieldAuthValue') }}</span>
              <el-tooltip :content="t('metricPlatform.tooltipAuthValue')" placement="top">
                <span class="form-tip">?</span>
              </el-tooltip>
            </label>
            <div class="password-field">
              <input
                v-model="form.authValue"
                class="form-input"
                :type="showPassword ? 'text' : 'password'"
                :disabled="!isEditing"
                :placeholder="t('metricPlatform.placeholderAuthValue')"
              />
              <button
                type="button"
                class="eye-btn"
                :title="showPassword ? t('metricPlatform.hide') : t('metricPlatform.show')"
                @click="showPassword = !showPassword"
              >
                <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 指标管理 -->
    <section class="mp-section">
      <div class="mp-card">
        <div class="section-header">
          <div class="section-header-left">
            <span class="section-icon manage">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7" />
                <rect x="14" y="3" width="7" height="7" />
                <rect x="14" y="14" width="7" height="7" />
                <rect x="3" y="14" width="7" height="7" />
              </svg>
            </span>
            <div class="title-block">
              <h2 class="section-title">{{ t('metricPlatform.manageTitle') }}</h2>
              <p class="section-desc">{{ t('metricPlatform.manageDesc') }}</p>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loadingMetrics" class="loading-container">
          <el-icon class="is-loading" style="font-size: 24px; color: #165dff;">
            <Loading />
          </el-icon>
          <span class="loading-text">{{ t('common.loading') }}</span>
        </div>

        <!-- 指标管理主容器（左侧类目树 + 右侧表格） -->
        <div v-else-if="metricCategoryGroups.length > 0" class="metric-management-container">
          <!-- 左侧类目树 -->
          <div class="category-tree-panel">
            <div class="tree-header">
              <span class="tree-title">类目</span>
            </div>
            <div class="tree-content">
              <div
                class="tree-node tree-node-all"
                :class="{ 'is-active': selectedCategoryId === 'all' }"
                @click="selectCategory('all')"
              >
                <span class="tree-node-expand is-placeholder" />
                <span class="tree-node-icon">
                  <el-icon><FolderOpened /></el-icon>
                </span>
                <span class="tree-node-name">全部指标</span>
                <span class="tree-node-count">{{ metrics.length }}</span>
              </div>
              <category-tree-node
                v-for="group in metricCategoryGroups"
                :key="group.categoryId"
                :group="group"
                type="metric"
                :selected-id="selectedCategoryId"
                :expanded-set="expandedMetricCategories"
                @select="selectCategory"
                @toggle="toggleCategory"
              />
            </div>
          </div>

          <!-- 右侧指标表格 -->
          <div class="metric-table-panel">
            <div class="table-header">
              <span class="table-title">{{ currentMetricCategoryName }}</span>
              <span class="table-count">{{ currentMetrics.length }} 个指标</span>
            </div>
            <el-table :data="currentMetrics" stripe size="small" style="width: 100%" height="600" :virtual-scroll="true">
              <el-table-column type="selection" width="40" align="center" />
              <el-table-column prop="metricDisplayName" :label="t('metricPlatform.metricDisplayName')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="metric-name-cell">
                    <el-icon><DataLine /></el-icon>
                    <span>{{ row.metricDisplayName || row.metricName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="metricName" :label="t('metricPlatform.metricName')" min-width="140" show-overflow-tooltip />
              <el-table-column prop="status" :label="t('metricPlatform.metricStatus')" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status === 'ONLINE' ? '已发布' : '未发布' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="metricCategoryName" :label="t('metricPlatform.categoryName')" min-width="120" show-overflow-tooltip />
              <el-table-column prop="owner" :label="t('metricPlatform.owner')" min-width="100" show-overflow-tooltip />
              <el-table-column :label="t('metricPlatform.availableDimensions')" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.availableDimensions?.join('、') || '-' }}
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-container">
          <el-empty :description="t('metricPlatform.noMetrics')" :image-size="80" />
        </div>
      </div>
    </section>

    <!-- 维度管理 -->
    <section class="mp-section">
      <div class="mp-card">
        <div class="section-header">
          <div class="section-header-left">
            <span class="section-icon manage">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z" />
                <path d="M2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
            </span>
            <div class="title-block">
              <h2 class="section-title">{{ t('metricPlatform.dimensionTitle') }}</h2>
              <p class="section-desc">{{ t('metricPlatform.dimensionDesc') }}</p>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loadingDimensions" class="loading-container">
          <el-icon class="is-loading" style="font-size: 24px; color: #165dff;">
            <Loading />
          </el-icon>
          <span class="loading-text">{{ t('common.loading') }}</span>
        </div>

        <!-- 维度管理主容器（左侧类目树 + 右侧表格） -->
        <div v-else-if="dimensionCategoryGroups.length > 0" class="metric-management-container">
          <!-- 左侧类目树 -->
          <div class="category-tree-panel">
            <div class="tree-header">
              <span class="tree-title">类目</span>
            </div>
            <div class="tree-content">
              <div
                class="tree-node tree-node-all"
                :class="{ 'is-active': selectedDimensionCategoryId === 'all' }"
                @click="selectDimensionCategory('all')"
              >
                <span class="tree-node-expand is-placeholder" />
                <span class="tree-node-icon">
                  <el-icon><FolderOpened /></el-icon>
                </span>
                <span class="tree-node-name">全部维度</span>
                <span class="tree-node-count">{{ dimensions.length }}</span>
              </div>
              <category-tree-node
                v-for="group in dimensionCategoryGroups"
                :key="group.categoryId"
                :group="group"
                type="dimension"
                :selected-id="selectedDimensionCategoryId"
                :expanded-set="expandedDimensionCategories"
                @select="selectDimensionCategory"
                @toggle="toggleCategory"
              />
            </div>
          </div>

          <!-- 右侧维度表格 -->
          <div class="metric-table-panel">
            <div class="table-header">
              <span class="table-title">{{ currentDimensionCategoryName }}</span>
              <span class="table-count">{{ currentDimensions.length }} 个维度</span>
            </div>
            <el-table :data="currentDimensions" stripe size="small" style="width: 100%" height="600" :virtual-scroll="true">
              <el-table-column type="selection" width="40" align="center" />
              <el-table-column prop="dimDisplayName" :label="t('metricPlatform.dimDisplayName')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="metric-name-cell">
                    <el-icon><DataLine /></el-icon>
                    <span>{{ row.dimDisplayName || row.dimName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="dimName" :label="t('metricPlatform.dimName')" min-width="140" show-overflow-tooltip />
              <el-table-column prop="configType" :label="t('metricPlatform.dimConfigType')" width="110" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.configType === 'COLUMN_BIND' ? 'primary' : 'info'">{{ row.configType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="originDataType" :label="t('metricPlatform.dataType')" width="110" align="center" />
              <el-table-column prop="dimDescription" :label="t('metricPlatform.dimDesc')" min-width="180" show-overflow-tooltip />
            </el-table>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-container">
          <el-empty :description="t('metricPlatform.noDimensions')" :image-size="80" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Loading, FolderOpened, DataLine } from '@element-plus/icons-vue'
import * as datasourceApi from '@/api/datasource'
import { listMetricsGroupedByCategory, listDimensionsGroupedByCategory } from '@/api/semantic-model'
import CategoryTreeNode from './CategoryTreeNode.vue'
import type { CategoryTreeNodeGroup } from './CategoryTreeNode.vue'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import type { Datasource } from '@/types'

const { t } = useI18n()
const store = useDatasourceStore()

const props = defineProps<{
  datasourceId?: string
  /** 父级用于强制刷新面板的版本号，变更时重新拉取详情 */
  refreshKey?: number
}>()

/** 是否显示密码 */
const showPassword = ref(false)

/** 是否处于编辑模式 */
const isEditing = ref(false)

/** 是否正在保存 */
const saving = ref(false)

/** 连接信息表单初始值，加载后由详情接口回填 */
const form = reactive({
  displayName: '',
  productAddress: '',
  productPort: '',
  semanticAddress: '',
  semanticPort: '',
  tenantId: '',
  authMethod: 'UID',
  authValue: '',
})

/** 编辑前的表单快照（取消时恢复） */
const formBackup = reactive({ ...form })

/** connectionParams 解析后的结构（含 anymetricsHost / semanticHost / anymetricsPort / semanticPort / authType） */
interface ConnectionParams {
  anymetricsHost?: string
  semanticHost?: string
  anymetricsPort?: number
  semanticPort?: number
  authType?: string
}

/** 解析后端返回的 connectionParams 字符串 */
function parseConnectionParams(raw: string | undefined | null): ConnectionParams {
  if (!raw) {
    return {}
  }
  try {
    const obj = JSON.parse(raw) as ConnectionParams
    return obj || {}
  } catch {
    return {}
  }
}

/** 根据详情接口回填表单 */
function fillFormFromDatasource(ds: Datasource): void {
  const cp = parseConnectionParams(ds.connectionParams)
  // 产品层与语义层地址统一从 connection_params 读取（JSON 中 anymetricsHost / semanticHost）；
  // 未配置时回退到独立字段，再回退到通用 host 字段（兼容历史数据）
  form.displayName = ds.name || ''
  form.productAddress = cp.anymetricsHost || ds.productHost || ds.host || ''
  form.semanticAddress = cp.semanticHost || ds.semanticHost || ds.host || ''
  form.productPort = cp.anymetricsPort != null ? String(cp.anymetricsPort) : ''
  form.semanticPort = cp.semanticPort != null ? String(cp.semanticPort) : ''
  form.tenantId = ds.username || ''
  form.authMethod = cp.authType || 'UID'
  form.authValue = ds.password || ''
  Object.assign(formBackup, form)
}

/** 加载数据源详情并回填表单 */
async function loadDatasource(id: string): Promise<void> {
  try {
    const ds = await datasourceApi.get(id)
    fillFormFromDatasource(ds)
  } catch {
    // 错误由全局 axios 拦截器统一提示
  }
}

/** 指标列表数据结构（匹配后端 AloudataMetricSemanticDTO） */
interface MetricItem {
  metricId: string
  metricName: string
  metricDisplayName: string
  type: string
  businessCaliber: string
  owner: string
  metricCategoryId: string
  metricCategoryName: string
  status: string
  unit: string
  synonyms?: string[]
  availableDimensions?: string[]
}

/** 维度列表数据结构（匹配后端 AloudataDimensionSemanticDTO） */
interface DimensionItem {
  dimensionId: string
  dimName: string
  dimDisplayName: string
  originDataType: string
  dimDescription: string
  configType: string
  datasetName: string
  synonyms?: string[]
}

/** 指标类目分组（支持层级树） */
interface MetricCategoryGroup extends CategoryTreeNodeGroup {
  metricCount: number
  metrics: MetricItem[]
  children?: MetricCategoryGroup[]
}

/** 维度类目分组（支持层级树） */
interface DimensionCategoryGroup extends CategoryTreeNodeGroup {
  dimensionCount: number
  dimensions: DimensionItem[]
  children?: DimensionCategoryGroup[]
}

/** 指标列表 */
const metrics = ref<MetricItem[]>([])

/** 维度列表 */
const dimensions = ref<DimensionItem[]>([])

/** 指标类目分组列表（后端返回） */
const metricCategoryGroups = ref<MetricCategoryGroup[]>([])

/** 维度类目分组列表（后端返回） */
const dimensionCategoryGroups = ref<DimensionCategoryGroup[]>([])

/** 加载状态 */
const loadingMetrics = ref(false)
const loadingDimensions = ref(false)

/** 展开的指标类目 */
const expandedMetricCategories = ref<Set<string>>(new Set())

/** 展开的维度类目 */
const expandedDimensionCategories = ref<Set<string>>(new Set())

/** 当前选中的类目 ID */
const selectedCategoryId = ref<string>('all')

/** 当前选中的指标类目名称 */
const currentMetricCategoryName = computed<string>(() => {
  if (selectedCategoryId.value === 'all') {
    return '全部指标'
  }
  const group = findMetricGroupById(metricCategoryGroups.value, selectedCategoryId.value)
  return group?.categoryName || '全部指标'
})

/** 当前展示的指标列表（根据选中类目过滤） */
const currentMetrics = computed<MetricItem[]>(() => {
  if (selectedCategoryId.value === 'all') {
    return metrics.value
  }
  const group = findMetricGroupById(metricCategoryGroups.value, selectedCategoryId.value)
  return group?.metrics || []
})

/** 当前选中的维度类目 ID */
const selectedDimensionCategoryId = ref<string>('all')

/** 当前选中的维度类目名称 */
const currentDimensionCategoryName = computed<string>(() => {
  if (selectedDimensionCategoryId.value === 'all') {
    return '全部维度'
  }
  const group = findDimensionGroupById(dimensionCategoryGroups.value, selectedDimensionCategoryId.value)
  return group?.categoryName || '全部维度'
})

/** 当前展示的维度列表（根据选中类目过滤） */
const currentDimensions = computed<DimensionItem[]>(() => {
  if (selectedDimensionCategoryId.value === 'all') {
    return dimensions.value
  }
  const group = findDimensionGroupById(dimensionCategoryGroups.value, selectedDimensionCategoryId.value)
  return group?.dimensions || []
})

/** 选择维度类目 */
function selectDimensionCategory(categoryId: string): void {
  selectedDimensionCategoryId.value = categoryId
}

/**
 * 递归查找指标类目分组
 */
function findMetricGroupById(
  groups: MetricCategoryGroup[],
  categoryId: string,
): MetricCategoryGroup | undefined {
  for (const group of groups) {
    if (group.categoryId === categoryId) {
      return group
    }
    if (group.children) {
      const found = findMetricGroupById(group.children, categoryId)
      if (found) {
        return found
      }
    }
  }
  return undefined
}

/**
 * 递归查找维度类目分组
 */
function findDimensionGroupById(
  groups: DimensionCategoryGroup[],
  categoryId: string,
): DimensionCategoryGroup | undefined {
  for (const group of groups) {
    if (group.categoryId === categoryId) {
      return group
    }
    if (group.children) {
      const found = findDimensionGroupById(group.children, categoryId)
      if (found) {
        return found
      }
    }
  }
  return undefined
}

/** 切换类目展开/折叠 */
function toggleCategory(payload: { categoryId: string; type: 'metric' | 'dimension' }): void {
  if (payload.type === 'metric') {
    const set = new Set(expandedMetricCategories.value)
    if (set.has(payload.categoryId)) {
      set.delete(payload.categoryId)
    } else {
      set.add(payload.categoryId)
    }
    expandedMetricCategories.value = set
  } else {
    const set = new Set(expandedDimensionCategories.value)
    if (set.has(payload.categoryId)) {
      set.delete(payload.categoryId)
    } else {
      set.add(payload.categoryId)
    }
    expandedDimensionCategories.value = set
  }
}

/** 选择类目 */
function selectCategory(categoryId: string): void {
  selectedCategoryId.value = categoryId
}

/**
 * 递归收集类目树中所有节点的 categoryId
 */
function collectCategoryIds(groups: (MetricCategoryGroup | DimensionCategoryGroup)[]): Set<string> {
  const ids = new Set<string>()
  for (const group of groups) {
    ids.add(group.categoryId)
    if (group.children) {
      for (const id of collectCategoryIds(group.children)) {
        ids.add(id)
      }
    }
  }
  return ids
}

/** 加载指标列表（按类目分组，后端已分组） */
async function loadMetrics(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loadingMetrics.value = true
  try {
    const res = await listMetricsGroupedByCategory(props.datasourceId)
    const groups = (res as any) || []
    metricCategoryGroups.value = groups
    // 平铺所有指标（根节点已聚合子节点数据）
    metrics.value = groups.flatMap(g => g.metrics)
    // 默认展开所有类目
    expandedMetricCategories.value = collectCategoryIds(groups)
  } catch (error) {
    console.error('Failed to load metrics:', error)
    metricCategoryGroups.value = []
    metrics.value = []
  } finally {
    loadingMetrics.value = false
  }
}

/** 加载维度列表（按类目分组，后端已分组） */
async function loadDimensions(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loadingDimensions.value = true
  try {
    const res = await listDimensionsGroupedByCategory(props.datasourceId)
    const groups = (res as any) || []
    dimensionCategoryGroups.value = groups
    // 平铺所有维度（根节点已聚合子节点数据）
    dimensions.value = groups.flatMap(g => g.dimensions)
    // 默认展开所有类目
    expandedDimensionCategories.value = collectCategoryIds(groups)
  } catch (error) {
    console.error('Failed to load dimensions:', error)
    dimensionCategoryGroups.value = []
    dimensions.value = []
  } finally {
    loadingDimensions.value = false
  }
}

/** 切换选中数据源或父级强制刷新时重新加载表单数据 */
watch(
  () => [props.datasourceId, props.refreshKey],
  ([id]) => {
    isEditing.value = false
    if (id) {
      loadDatasource(id)
      // 加载指标、维度分组列表
      loadMetrics()
      loadDimensions()
    } else {
      // 清空指标和维度数据
      metrics.value = []
      dimensions.value = []
      metricCategoryGroups.value = []
      dimensionCategoryGroups.value = []
    }
  },
  { immediate: true },
)

/** 进入编辑模式 */
function handleEdit(): void {
  Object.assign(formBackup, form)
  isEditing.value = true
}

/** 取消编辑 */
function handleCancel(): void {
  Object.assign(form, formBackup)
  isEditing.value = false
}

/** 保存编辑 */
async function handleSave(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  try {
    if (props.datasourceId) {
      // 产品层与语义层是独立的进程服务，地址分别保存到 connection_params 中，
      // 不再使用 host 字段作为兜底，避免历史上 host 字段相同时两个地址被同步覆盖
      const params = {
        anymetricsHost: form.productAddress,
        semanticHost: form.semanticAddress,
        anymetricsPort: Number(form.productPort) || 8080,
        semanticPort: Number(form.semanticPort) || 8080,
        authType: form.authMethod,
      }
      const updated = await datasourceApi.update(props.datasourceId, {
        name: form.displayName,
        username: form.tenantId,
        password: form.authValue,
        connectionParams: JSON.stringify(params),
      } as never)
      // 用接口返回的最新数据回填表单，保证与服务端一致
      fillFormFromDatasource(updated)
      // 刷新列表与工具栏的展示名称
      await store.fetchDatasources()
    }
    isEditing.value = false
    ElMessage.success(t('common.success'))
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

/** 指标管理列表（演示数据） */
const indicators = reactive([
  {
    nameKey: 'metricPlatform.indicatorAumName',
    tagKey: 'metricPlatform.indicatorAumTag',
    descKey: 'metricPlatform.indicatorAumDesc',
    rangeKey: 'metricPlatform.indicatorAumRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorCrossName',
    tagKey: 'metricPlatform.indicatorCrossTag',
    descKey: 'metricPlatform.indicatorCrossDesc',
    rangeKey: 'metricPlatform.indicatorCrossRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorFundName',
    tagKey: 'metricPlatform.indicatorFundTag',
    descKey: 'metricPlatform.indicatorFundDesc',
    rangeKey: 'metricPlatform.indicatorFundRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorActiveName',
    tagKey: 'metricPlatform.indicatorActiveTag',
    descKey: 'metricPlatform.indicatorActiveDesc',
    rangeKey: 'metricPlatform.indicatorActiveRange',
    enabled: true,
  },
])
</script>

<style scoped>
.metric-platform-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 32px 40px;
  background: linear-gradient(180deg, #f7f8fa 0%, #f0f2f5 100%);
  min-height: 100%;
  box-sizing: border-box;
}

/* ========== Section 通用卡片 ========== */
.mp-section {
  display: flex;
  flex-direction: column;
}

.mp-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebedf0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  padding: 22px 24px 24px;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.2s;
}

.mp-card:hover {
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
  border-color: #e0e3e8;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.section-header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.section-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(22, 93, 255, 0.1) 0%, rgba(22, 93, 255, 0.04) 100%);
  color: #165dff;
  border-radius: 9px;
  box-shadow: inset 0 0 0 1px rgba(22, 93, 255, 0.06);
}

.section-icon.manage {
  background: linear-gradient(135deg, rgba(0, 180, 42, 0.1) 0%, rgba(0, 180, 42, 0.04) 100%);
  color: #00b42a;
  box-shadow: inset 0 0 0 1px rgba(0, 180, 42, 0.06);
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
  letter-spacing: 0.3px;
  line-height: 1.4;
}

.section-desc {
  font-size: 12.5px;
  color: #8c939d;
  margin: 0;
  line-height: 1.6;
}

/* ========== 操作按钮 ========== */
.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.section-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  font-family: inherit;
  white-space: nowrap;
}

.section-action-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
  background: #f5f8ff;
  box-shadow: 0 1px 3px rgba(22, 93, 255, 0.12);
}

.section-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(22, 93, 255, 0.2);
  border-top-color: #165dff;
  border-radius: 50%;
  animation: panel-btn-spin 0.8s linear infinite;
}

@keyframes panel-btn-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ========== 表单网格 ========== */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 24px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.form-field-wide {
  grid-column: 1 / -1;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4e5969;
  font-weight: 500;
}

.form-label.required::before {
  content: '*';
  color: #f53f3f;
  font-weight: 600;
  margin-right: 2px;
}

.form-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #e5e6eb;
  color: #86909c;
  font-size: 10px;
  font-weight: bold;
  cursor: help;
  transition: all 0.15s;
}

.form-tip:hover {
  background: #165dff;
  color: #fff;
}

.form-input,
.form-select {
  height: 36px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 0 12px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  transition: all 0.15s;
  font-family: inherit;
  background: #fff;
  box-sizing: border-box;
  width: 100%;
}

.form-input:hover:not(:disabled),
.form-select:hover:not(:disabled) {
  border-color: #c9cdd4;
}

.form-input:focus,
.form-select:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.08);
}

.form-input:disabled,
.form-select:disabled {
  background: #f7f8fa;
  color: #1d2129;
  cursor: not-allowed;
  border-color: #e5e6eb;
}

.form-input::placeholder {
  color: #c9cdd4;
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath fill='%2386909c' d='M6 8.5L1.5 4h9z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 10px;
  padding-right: 32px;
}

.password-field {
  position: relative;
  display: flex;
  align-items: center;
}

.password-field .form-input {
  padding-right: 40px;
}

.password-field .form-input::-ms-reveal,
.password-field .form-input::-webkit-credentials-auto-fill-button {
  display: none;
}

.eye-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c9cdd4;
  border-radius: 4px;
  transition: all 0.15s;
}

.eye-btn:hover {
  color: #165dff;
  background: #f2f3f5;
}

/* ========== 指标管理卡片网格 ========== */
.indicator-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.indicator-card {
  border: 1px solid #ebedf0;
  border-radius: 8px;
  background: #fff;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.18s ease;
  position: relative;
  overflow: hidden;
}

.indicator-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #e5e6eb;
  transition: background 0.2s;
}

.indicator-card.is-on::before {
  background: linear-gradient(180deg, #f05a23 0%, #e75c01 100%);
}

.indicator-card:hover {
  border-color: #165dff;
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.08);
  transform: translateY(-1px);
}

.indicator-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.indicator-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1d2129;
}

.indicator-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tql-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  font-size: 10.5px;
  font-weight: 700;
  color: #f05a23;
  background: rgba(240, 90, 35, 0.08);
  border-radius: 3px;
  letter-spacing: 0.3px;
}

.meta-text {
  font-size: 12px;
  color: #4e5969;
}

.indicator-range {
  font-size: 12px;
  color: #86909c;
  line-height: 1.5;
}

:deep(.el-switch) {
  --el-switch-on-color: #f05a23;
}

/* ========== 维度管理卡片网格 ========== */
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dimension-card {
  border: 1px solid #ebedf0;
  border-radius: 8px;
  background: #fff;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.18s ease;
  position: relative;
  overflow: hidden;
}

.dimension-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, #165dff 0%, #0e42d2 100%);
  transition: background 0.2s;
}

.dimension-card:hover {
  border-color: #165dff;
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.08);
  transform: translateY(-1px);
}

.dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.dimension-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1d2129;
}

.dimension-type-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  font-size: 10.5px;
  font-weight: 700;
  color: #165dff;
  background: rgba(22, 93, 255, 0.08);
  border-radius: 3px;
  letter-spacing: 0.3px;
}

.dimension-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.meta-label {
  font-size: 12px;
  color: #86909c;
}

.meta-value {
  font-size: 12px;
  color: #4e5969;
  font-weight: 500;
}

.dimension-desc {
  font-size: 12px;
  color: #86909c;
  line-height: 1.5;
}

/* ========== 加载和空状态 ========== */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 0;
}

.loading-text {
  font-size: 13px;
  color: #86909c;
}

.empty-container {
  padding: 20px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* ========== 类目分组样式 ========== */
.category-group {
  margin-bottom: 8px;
}

.category-group:last-child {
  margin-bottom: 0;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f7f8fa;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.category-header:hover {
  background: #eef0f5;
}

.category-arrow {
  font-size: 10px;
  color: #86909c;
  transition: transform 0.2s;
}

.category-arrow.is-expanded {
  transform: rotate(90deg);
}

.category-name {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
}

.category-count {
  font-size: 12px;
  color: #86909c;
  background: #e8e8e8;
  border-radius: 10px;
  padding: 1px 8px;
  margin-left: auto;
}

.category-content {
  padding: 8px 0 0 0;
}

/* ========== 指标管理布局（左侧类目树 + 右侧表格） ========== */
.metric-management-container {
  display: flex;
  gap: 16px;
  height: 650px;
}

/* 左侧类目树面板 */
.category-tree-panel {
  width: 240px;
  flex-shrink: 0;
  background: #f7f8fa;
  border-radius: 6px;
  border: 1px solid #ebedf0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-header {
  padding: 12px 16px;
  border-bottom: 1px solid #ebedf0;
  background: #f0f2f5;
}

.tree-title {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.tree-node:hover {
  background: #eef0f5;
}

.tree-node.is-active {
  background: #e6f0ff;
}

.tree-node.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: #165dff;
  border-radius: 0 2px 2px 0;
}

.tree-node-all .tree-node-icon {
  color: #165dff;
}

.tree-node-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  cursor: pointer;
  flex-shrink: 0;
}

.tree-node-expand.is-placeholder {
  cursor: default;
}

.tree-node-expand .el-icon {
  font-size: 14px;
  color: #86909c;
  transition: transform 0.2s;
}

.tree-node-expand .el-icon.is-expanded {
  transform: rotate(90deg);
}

.tree-node-icon {
  display: flex;
  align-items: center;
  color: #165dff;
  flex-shrink: 0;
}

.tree-node-name {
  flex: 1;
  font-size: 13px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-count {
  font-size: 11px;
  color: #86909c;
  background: #e8e8e8;
  border-radius: 10px;
  padding: 1px 8px;
  flex-shrink: 0;
  min-width: 20px;
  text-align: center;
}

.tree-node.is-active .tree-node-count {
  background: #165dff;
  color: #fff;
}

/* 右侧指标表格面板 */
.metric-table-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebedf0;
}

.table-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebedf0;
  background: #fafafa;
}

.table-title {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
}

.table-count {
  font-size: 12px;
  color: #86909c;
}

.metric-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.metric-name-cell .el-icon {
  color: #165dff;
}
</style>
