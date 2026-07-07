<template>
  <div class="metric-platform-panel">
    <!-- 指标平台连接：use 权限隐藏连接配置，仅展示基础信息标题 -->
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
          <div v-if="currentDatasource?.permission === 'edit'" class="section-actions">
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

        <div v-if="currentDatasource?.permission !== 'use'" class="form-grid">
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
                :placeholder="isEditing ? '请输入新认证值，留空表示不修改' : ''"
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

          <!-- 共享元数据 -->
          <div class="form-field form-field-wide">
            <label class="checkbox-label">
              <label class="switch">
                <input v-model="form.metaShared" type="checkbox" :disabled="!isEditing" />
                <span class="slider"></span>
              </label>
              <span class="switch-text">共享元数据（同工作区所有用户可查看）</span>
            </label>
            <p class="field-desc" style="margin: 4px 0 0 40px; font-size: 12px; color: var(--theme-text-muted);">
              开启后，同工作区其他用户可查看该数据源的元数据（不包含连接配置）
            </p>
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
        <div v-if="metricPagination.loading" class="loading-container">
          <el-icon class="is-loading" style="font-size: 24px; color: var(--main-orange);">
            <Loading />
          </el-icon>
          <span class="loading-text">{{ t('common.loading') }}</span>
        </div>

        <!-- 指标管理主容器（左侧类目树 + 右侧表格） -->
        <div v-else-if="metricCategoryTree.length > 0" class="metric-management-container">
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
                <span class="tree-node-count">{{ metricTotalCount }}</span>
              </div>
              <category-tree-node
                v-for="group in metricCategoryTree"
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
              <div class="table-header-right">
                <div class="search-wrap">
                  <el-input
                    v-model="metricPagination.keyword"
                    size="small"
                    :placeholder="t('metricPlatform.searchMetrics')"
                    clearable
                    @input="handleMetricSearch"
                    @clear="handleMetricSearch"
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                </div>
                <span class="table-count">{{ metricPagination.total }} 个指标</span>
              </div>
            </div>
            <el-table :data="metricPagination.list" stripe size="small" style="width: 100%" height="550" @expand-change="handleMetricExpand">
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
              <el-table-column type="expand" width="40">
                <template #default="{ row }">
                  <div class="metric-dimensions-expand">
                    <div class="expand-label">{{ t('metricPlatform.availableDimensions') }}</div>
                    <div class="expand-content">
                      <!-- 加载中 -->
                      <div v-if="metricDimensionLoadingMap[row.metricName]" class="expand-loading">
                        <el-icon class="is-loading" style="font-size: 14px; color: var(--main-orange);"><Loading /></el-icon>
                        <span>{{ t('common.loading') }}</span>
                      </div>
                      <!-- 维度详情列表 -->
                      <template v-else-if="metricDimensionDetailMap[row.metricName]?.length">
                        <div
                          v-for="dim in metricDimensionDetailMap[row.metricName]"
                          :key="dim.dimName"
                          class="dimension-item"
                        >
                          <div class="dimension-item-header">
                            <span class="dimension-item-name">{{ dim.dimDisplayName || dim.dimName }}</span>
                            <el-tag v-if="dim.configType" size="small" type="info">{{ dim.configType }}</el-tag>
                          </div>
                          <div class="dimension-item-meta">
                            <span v-if="dim.dimName" class="meta-pair"><span class="meta-key">{{ t('metricPlatform.dimName') }}:</span> {{ dim.dimName }}</span>
                            <span v-if="dim.originDataType" class="meta-pair"><span class="meta-key">{{ t('metricPlatform.dataType') }}:</span> {{ dim.originDataType }}</span>
                          </div>
                          <div v-if="dim.dimDescription" class="dimension-item-desc">{{ dim.dimDescription }}</div>
                        </div>
                      </template>
                      <!-- 空状态 -->
                      <div v-else class="expand-empty">-</div>
                    </div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="metricPagination.page"
                v-model:page-size="metricPagination.size"
                :total="metricPagination.total"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                size="small"
                @current-change="handleMetricPageChange"
                @size-change="handleMetricSizeChange"
              />
            </div>
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
        <div v-if="dimensionPagination.loading" class="loading-container">
          <el-icon class="is-loading" style="font-size: 24px; color: var(--main-orange);">
            <Loading />
          </el-icon>
          <span class="loading-text">{{ t('common.loading') }}</span>
        </div>

        <!-- 维度管理主容器（左侧类目树 + 右侧表格） -->
        <div v-else-if="dimensionCategoryTree.length > 0" class="metric-management-container">
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
                <span class="tree-node-count">{{ dimensionTotalCount }}</span>
              </div>
              <category-tree-node
                v-for="group in dimensionCategoryTree"
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
              <div class="table-header-right">
                <div class="search-wrap">
                  <el-input
                    v-model="dimensionPagination.keyword"
                    size="small"
                    :placeholder="t('metricPlatform.searchDimensions')"
                    clearable
                    @input="handleDimensionSearch"
                    @clear="handleDimensionSearch"
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                </div>
                <span class="table-count">{{ dimensionPagination.total }} 个维度</span>
              </div>
            </div>
            <el-table :data="dimensionPagination.list" stripe size="small" style="width: 100%" height="550" @expand-change="handleDimensionExpand">
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
              <el-table-column prop="status" :label="t('metricPlatform.metricStatus')" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status === 'ONLINE' ? '已发布' : '未发布' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="categoryName" :label="t('metricPlatform.categoryName')" min-width="120" show-overflow-tooltip />
              <el-table-column prop="configType" :label="t('metricPlatform.dimConfigType')" width="110" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.configType === 'COLUMN_BIND' ? 'primary' : 'info'">{{ row.configType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="originDataType" :label="t('metricPlatform.dataType')" width="110" align="center" />
              <el-table-column type="expand" width="40">
                <template #default="{ row }">
                  <div class="metric-dimensions-expand">
                    <div class="expand-label">{{ t('metricPlatform.relatedMetrics') }}</div>
                    <div class="expand-content">
                      <!-- 加载中 -->
                      <div v-if="dimensionMetricLoadingMap[row.dimName]" class="expand-loading">
                        <el-icon class="is-loading" style="font-size: 14px; color: var(--main-orange);"><Loading /></el-icon>
                        <span>{{ t('common.loading') }}</span>
                      </div>
                      <!-- 指标详情列表 -->
                      <template v-else-if="dimensionMetricDetailMap[row.dimName]?.length">
                        <div
                          v-for="metric in dimensionMetricDetailMap[row.dimName]"
                          :key="metric.metricName"
                          class="dimension-item"
                        >
                          <div class="dimension-item-header">
                            <span class="dimension-item-name">{{ metric.metricDisplayName || metric.metricName }}</span>
                            <el-tag v-if="metric.status" size="small" :type="metric.status === 'ONLINE' ? 'success' : 'info'">
                              {{ metric.status === 'ONLINE' ? '已发布' : '未发布' }}
                            </el-tag>
                          </div>
                          <div class="dimension-item-meta">
                            <span v-if="metric.metricName" class="meta-pair"><span class="meta-key">{{ t('metricPlatform.metricName') }}:</span> {{ metric.metricName }}</span>
                            <span v-if="metric.owner" class="meta-pair"><span class="meta-key">{{ t('metricPlatform.owner') }}:</span> {{ metric.owner }}</span>
                          </div>
                          <div v-if="metric.businessCaliber" class="dimension-item-desc">{{ metric.businessCaliber }}</div>
                        </div>
                      </template>
                      <!-- 空状态 -->
                      <div v-else class="expand-empty">-</div>
                    </div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="dimensionPagination.page"
                v-model:page-size="dimensionPagination.size"
                :total="dimensionPagination.total"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                size="small"
                @current-change="handleDimensionPageChange"
                @size-change="handleDimensionSizeChange"
              />
            </div>
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
import { Loading, FolderOpened, DataLine, Search } from '@element-plus/icons-vue'
import * as datasourceApi from '@/api/datasource'
import {
  pageAloudataMetrics,
  pageAloudataDimensions,
  listAloudataCategoryCounts,
  listMetricDimensionDetails,
  listDimensionMetricDetails,
} from '@/api/semantic-model'
import CategoryTreeNode from './CategoryTreeNode.vue'
import type { CategoryTreeNodeGroup } from './CategoryTreeNode.vue'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import type { AloudataCategoryCount, Datasource } from '@/types'
import { storeToRefs } from 'pinia'

const { t } = useI18n()
const store = useDatasourceStore()
const { currentDatasource } = storeToRefs(store)

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
  metaShared: false,
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
  // 认证值不再回显，编辑时留空表示不修改密码
  form.authValue = ''
  form.metaShared = ds.metaShared ?? false
  Object.assign(formBackup, form)
}

/** 加载数据源详情并回填表单 */
async function loadDatasource(id: string): Promise<void> {
  try {
    const ds = await datasourceApi.get(id)
    fillFormFromDatasource(ds)
    store.currentDatasource = ds as unknown as Datasource
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
  status: string
  categoryName: string
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

/** 指标分页状态 */
const metricPagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  list: [] as MetricItem[],
  keyword: '',
  categoryId: 'all',
  loading: false,
})

/** 维度分页状态 */
const dimensionPagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  list: [] as DimensionItem[],
  keyword: '',
  categoryId: 'all',
  loading: false,
})


/** 指标类目树 */
const metricCategoryTree = ref<MetricCategoryGroup[]>([])

/** 维度类目树 */
const dimensionCategoryTree = ref<DimensionCategoryGroup[]>([])

/** 指标总数量（用于“全部指标”节点固定展示，不随当前过滤条件变化） */
const metricTotalCount = ref(0)

/** 维度总数量（用于“全部维度”节点固定展示，不随当前过滤条件变化） */
const dimensionTotalCount = ref(0)

const loadingMetricCategories = ref(false)

/** 维度类目树加载状态 */
const loadingDimensionCategories = ref(false)

/** 指标展开行：维度详情缓存（按 metricName 索引） */
const metricDimensionDetailMap = ref<Record<string, DimensionItem[]>>({})

/** 指标展开行：维度详情加载状态 */
const metricDimensionLoadingMap = ref<Record<string, boolean>>({})

/** 加载指标关联的维度详情（按需请求，带缓存） */
async function loadMetricDimensionDetails(metricName: string): Promise<void> {
  if (metricDimensionDetailMap.value[metricName] || metricDimensionLoadingMap.value[metricName]) {
    return
  }
  if (!props.datasourceId) {
    return
  }
  metricDimensionLoadingMap.value[metricName] = true
  try {
    const res = await listMetricDimensionDetails(props.datasourceId, metricName)
    metricDimensionDetailMap.value[metricName] = (res as any) || []
  } catch {
    metricDimensionDetailMap.value[metricName] = []
  } finally {
    metricDimensionLoadingMap.value[metricName] = false
  }
}

/** 维度展开行：指标详情缓存（按 dimName 索引） */
const dimensionMetricDetailMap = ref<Record<string, MetricItem[]>>({})

/** 维度展开行：指标详情加载状态 */
const dimensionMetricLoadingMap = ref<Record<string, boolean>>({})

/** 加载维度关联的指标详情（按需请求，带缓存） */
async function loadDimensionMetricDetails(dimName: string): Promise<void> {
  if (dimensionMetricDetailMap.value[dimName] || dimensionMetricLoadingMap.value[dimName]) {
    return
  }
  if (!props.datasourceId) {
    return
  }
  dimensionMetricLoadingMap.value[dimName] = true
  try {
    const res = await listDimensionMetricDetails(props.datasourceId, dimName)
    dimensionMetricDetailMap.value[dimName] = (res as any) || []
  } catch {
    dimensionMetricDetailMap.value[dimName] = []
  } finally {
    dimensionMetricLoadingMap.value[dimName] = false
  }
}

/** 指标行展开/收起事件，展开时按需加载维度详情 */
function handleMetricExpand(row: MetricItem, expandedRows: MetricItem[]): void {
  if (expandedRows.some(r => r.metricName === row.metricName)) {
    loadMetricDimensionDetails(row.metricName)
  }
}

/** 维度行展开/收起事件，展开时按需加载指标详情 */
function handleDimensionExpand(row: DimensionItem, expandedRows: DimensionItem[]): void {
  if (expandedRows.some(r => r.dimName === row.dimName)) {
    loadDimensionMetricDetails(row.dimName)
  }
}

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
  const group = findMetricGroupById(metricCategoryTree.value, selectedCategoryId.value)
  return group?.categoryName || '全部指标'
})

/** 当前选中的维度类目 ID */
const selectedDimensionCategoryId = ref<string>('all')

/** 当前选中的维度类目名称 */
const currentDimensionCategoryName = computed<string>(() => {
  if (selectedDimensionCategoryId.value === 'all') {
    return '全部维度'
  }
  const group = findDimensionGroupById(dimensionCategoryTree.value, selectedDimensionCategoryId.value)
  return group?.categoryName || '全部维度'
})

/** 选择维度类目 */
function selectDimensionCategory(categoryId: string): void {
  selectedDimensionCategoryId.value = categoryId
  dimensionPagination.categoryId = categoryId
  dimensionPagination.page = 1
  loadDimensionPage()
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
  metricPagination.categoryId = categoryId
  metricPagination.page = 1
  loadMetricPage()
}

/**
 * 构建类目树（支持层级父子关系）
 */
function buildCategoryTree<T extends CategoryTreeNodeGroup>(
  counts: AloudataCategoryCount[],
): T[] {
  const nodeMap = new Map<string, T>()
  const roots: T[] = []
  for (const item of counts) {
    const countValue = Number(item.count) || 0
    const node = {
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      parentId: item.parentId,
      metricCount: countValue,
      dimensionCount: countValue,
      children: [],
      metrics: [],
      dimensions: [],
    } as unknown as T
    nodeMap.set(item.categoryId, node)
  }
  for (const node of nodeMap.values()) {
    const parentId = node.parentId
    if (parentId && nodeMap.has(parentId)) {
      const parent = nodeMap.get(parentId)
      if (parent && parent.children) {
        parent.children.push(node)
      }
    } else {
      roots.push(node)
    }
  }
  return roots
}

/**
 * 递归聚合子类目数量到父类目
 */
function aggregateCategoryCounts(groups: MetricCategoryGroup[] | DimensionCategoryGroup[]): number {
  let total = 0
  for (const group of groups) {
    let count = group.metricCount ?? group.dimensionCount ?? 0
    if (group.children && group.children.length > 0) {
      count += aggregateCategoryCounts(group.children)
    }
    if ('metricCount' in group) {
      group.metricCount = count
    } else {
      group.dimensionCount = count
    }
    total += count
  }
  return total
}

/**
 * 将类目树根节点加入展开集合（默认只展开顶级类目）
 */
function expandRootCategories(groups: CategoryTreeNodeGroup[], expandedSet: Set<string>): void {
  for (const group of groups) {
    if (group.children && group.children.length > 0) {
      expandedSet.add(group.categoryId)
    }
  }
}

/** 加载指标类目树 */
async function loadMetricCategories(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loadingMetricCategories.value = true
  try {
    const res = await listAloudataCategoryCounts(props.datasourceId, 'CATEGORY_METRIC')
    const counts = (res as any) || []
    metricCategoryTree.value = buildCategoryTree<MetricCategoryGroup>(counts)
    aggregateCategoryCounts(metricCategoryTree.value)
    expandedMetricCategories.value = new Set<string>()
    expandRootCategories(metricCategoryTree.value, expandedMetricCategories.value)
  } catch (error) {
    console.error('Failed to load metric categories:', error)
    metricCategoryTree.value = []
    expandedMetricCategories.value = new Set<string>()
  } finally {
    loadingMetricCategories.value = false
  }
}

/** 加载维度类目树 */
async function loadDimensionCategories(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  loadingDimensionCategories.value = true
  try {
    const res = await listAloudataCategoryCounts(props.datasourceId, 'CATEGORY_DIMENSION')
    const counts = (res as any) || []
    dimensionCategoryTree.value = buildCategoryTree<DimensionCategoryGroup>(counts)
    aggregateCategoryCounts(dimensionCategoryTree.value)
    expandedDimensionCategories.value = new Set<string>()
    expandRootCategories(dimensionCategoryTree.value, expandedDimensionCategories.value)
  } catch (error) {
    console.error('Failed to load dimension categories:', error)
    dimensionCategoryTree.value = []
    expandedDimensionCategories.value = new Set<string>()
  } finally {
    loadingDimensionCategories.value = false
  }
}

/** 加载指标分页列表 */
async function loadMetricPage(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  metricPagination.loading = true
  try {
    const res = await pageAloudataMetrics(props.datasourceId, {
      pageNumber: metricPagination.page,
      pageSize: metricPagination.size,
      keyword: metricPagination.keyword,
      categoryId: metricPagination.categoryId === 'all' ? undefined : metricPagination.categoryId,
    })
    const data = (res as any) || { records: [], total: 0 }
    metricPagination.list = data.records || []
    metricPagination.total = Number(data.total) || 0
    if (metricPagination.categoryId === 'all') {
      metricTotalCount.value = metricPagination.total
    }
  } catch (error) {
    console.error('Failed to load metric page:', error)
    metricPagination.list = []
    metricPagination.total = 0
  } finally {
    metricPagination.loading = false
  }
}

/** 加载维度分页列表 */
async function loadDimensionPage(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  dimensionPagination.loading = true
  try {
    const res = await pageAloudataDimensions(props.datasourceId, {
      pageNumber: dimensionPagination.page,
      pageSize: dimensionPagination.size,
      keyword: dimensionPagination.keyword,
      categoryId: dimensionPagination.categoryId === 'all' ? undefined : dimensionPagination.categoryId,
    })
    const data = (res as any) || { records: [], total: 0 }
    dimensionPagination.list = data.records || []
    dimensionPagination.total = Number(data.total) || 0
    if (dimensionPagination.categoryId === 'all') {
      dimensionTotalCount.value = dimensionPagination.total
    }
  } catch (error) {
    console.error('Failed to load dimension page:', error)
    dimensionPagination.list = []
    dimensionPagination.total = 0
  } finally {
    dimensionPagination.loading = false
  }
}

/** 指标搜索防抖定时器 */
let metricSearchTimer: ReturnType<typeof setTimeout> | null = null

/** 指标搜索输入处理 */
function handleMetricSearch(): void {
  if (metricSearchTimer) {
    clearTimeout(metricSearchTimer)
  }
  metricSearchTimer = setTimeout(() => {
    metricPagination.page = 1
    loadMetricPage()
  }, 300)
}

/** 维度搜索防抖定时器 */
let dimensionSearchTimer: ReturnType<typeof setTimeout> | null = null

/** 维度搜索输入处理 */
function handleDimensionSearch(): void {
  if (dimensionSearchTimer) {
    clearTimeout(dimensionSearchTimer)
  }
  dimensionSearchTimer = setTimeout(() => {
    dimensionPagination.page = 1
    loadDimensionPage()
  }, 300)
}

/** 指标翻页 */
function handleMetricPageChange(page: number): void {
  metricPagination.page = page
  loadMetricPage()
}

/** 指标每页条数变化 */
function handleMetricSizeChange(size: number): void {
  metricPagination.size = size
  metricPagination.page = 1
  loadMetricPage()
}

/** 维度翻页 */
function handleDimensionPageChange(page: number): void {
  dimensionPagination.page = page
  loadDimensionPage()
}

/** 维度每页条数变化 */
function handleDimensionSizeChange(size: number): void {
  dimensionPagination.size = size
  dimensionPagination.page = 1
  loadDimensionPage()
}

/** 加载指标管理数据（类目树 + 分页列表） */
async function loadMetrics(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  await loadMetricCategories()
  await loadMetricPage()
}

/** 加载维度管理数据（类目树 + 分页列表） */
async function loadDimensions(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  await loadDimensionCategories()
  await loadDimensionPage()
}

/** 切换选中数据源或父级强制刷新时重新加载表单数据 */
watch(
  () => [props.datasourceId, props.refreshKey],
  ([id]) => {
    isEditing.value = false
    // 重置分页、搜索、类目选中及展开状态
    selectedCategoryId.value = 'all'
    selectedDimensionCategoryId.value = 'all'
    metricPagination.page = 1
    metricPagination.keyword = ''
    metricPagination.categoryId = 'all'
    metricTotalCount.value = 0
    dimensionPagination.page = 1
    dimensionPagination.keyword = ''
    dimensionPagination.categoryId = 'all'
    dimensionTotalCount.value = 0
    expandedMetricCategories.value = new Set<string>()
    expandedDimensionCategories.value = new Set<string>()
    metricDimensionDetailMap.value = {}
    metricDimensionLoadingMap.value = {}
    dimensionMetricDetailMap.value = {}
    dimensionMetricLoadingMap.value = {}
    if (id) {
      loadDatasource(id)
      // 加载指标、维度类目树和分页列表
      loadMetrics()
      loadDimensions()
    } else {
      // 清空指标和维度数据
      metricPagination.list = []
      metricPagination.total = 0
      dimensionPagination.list = []
      dimensionPagination.total = 0
      metricCategoryTree.value = []
      dimensionCategoryTree.value = []
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
      const payload: Record<string, any> = {
        name: form.displayName,
        username: form.tenantId,
        connectionParams: JSON.stringify(params),
        metaShared: form.metaShared,
      }
      // 仅当用户填写了新认证值时才提交，留空表示不修改密码
      if (form.authValue && form.authValue.trim()) {
        payload.password = form.authValue
      }
      const updated = await datasourceApi.update(props.datasourceId, payload as never)
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
  background: var(--theme-bg);
  min-height: 100%;
  box-sizing: border-box;
}

/* ========== Section 通用卡片 ========== */
.mp-section {
  display: flex;
  flex-direction: column;
}

.mp-card {
  background: var(--theme-surface);
  border-radius: 12px;
  border: 1px solid var(--theme-border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  padding: 22px 24px 24px;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.2s;
}

.mp-card:hover {
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
  border-color: var(--theme-border-strong);
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
  color: var(--theme-text);
  margin: 0;
  letter-spacing: 0.3px;
  line-height: 1.4;
}

.section-desc {
  font-size: 12.5px;
  color: var(--theme-text-muted);
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
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  font-family: inherit;
  white-space: nowrap;
}

.section-action-btn:hover:not(:disabled) {
  border-color: var(--main-orange);
  color: var(--main-orange);
  background: var(--theme-surface-hover);
  box-shadow: 0 1px 3px rgba(240, 90, 35, 0.12);
}

.section-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(240, 90, 35, 0.2);
  border-top-color: var(--main-orange);
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
  color: var(--theme-text-secondary);
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
  background: var(--theme-border);
  color: var(--theme-text-muted);
  font-size: 10px;
  font-weight: bold;
  cursor: help;
  transition: all 0.15s;
}

.form-tip:hover {
  background: var(--main-orange);
  color: #fff;
}

.form-input,
.form-select {
  height: 36px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--theme-text);
  outline: none;
  transition: all 0.15s;
  font-family: inherit;
  background: var(--theme-surface);
  box-sizing: border-box;
  width: 100%;
}

.form-input:hover:not(:disabled),
.form-select:hover:not(:disabled) {
  border-color: var(--theme-border-strong);
}

.form-input:focus,
.form-select:focus {
  border-color: var(--main-orange);
  box-shadow: 0 0 0 3px rgba(240, 90, 35, 0.08);
}

.form-input:disabled,
.form-select:disabled {
  background: var(--theme-bg);
  color: var(--theme-text);
  cursor: not-allowed;
  border-color: var(--theme-border);
}

.form-input::placeholder {
  color: var(--theme-text-muted);
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
  color: var(--theme-text-muted);
  border-radius: 4px;
  transition: all 0.15s;
}

.eye-btn:hover {
  color: var(--main-orange);
  background: var(--theme-surface-hover);
}

/* ========== 指标管理卡片网格 ========== */
.indicator-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.indicator-card {
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  background: var(--theme-surface);
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
  background: var(--theme-border);
  transition: background 0.2s;
}

.indicator-card.is-on::before {
  background: linear-gradient(180deg, var(--main-orange) 0%, var(--dark-orange) 100%);
}

.indicator-card:hover {
  border-color: var(--main-orange);
  box-shadow: 0 4px 12px rgba(240, 90, 35, 0.08);
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
  color: var(--theme-text);
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
  color: var(--main-orange);
  background: rgba(240, 90, 35, 0.08);
  border-radius: 3px;
  letter-spacing: 0.3px;
}

.meta-text {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.indicator-range {
  font-size: 12px;
  color: var(--theme-text-muted);
  line-height: 1.5;
}

:deep(.el-switch) {
  --el-switch-on-color: var(--main-orange);
}

/* ========== 维度管理卡片网格 ========== */
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dimension-card {
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  background: var(--theme-surface);
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
  color: var(--theme-text);
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
  color: var(--theme-text-muted);
}

.meta-value {
  font-size: 12px;
  color: var(--theme-text-secondary);
  font-weight: 500;
}

.dimension-desc {
  font-size: 12px;
  color: var(--theme-text-muted);
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
  color: var(--theme-text-muted);
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
  background: var(--theme-bg);
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.category-header:hover {
  background: var(--theme-surface-hover);
}

.category-arrow {
  font-size: 10px;
  color: var(--theme-text-muted);
  transition: transform 0.2s;
}

.category-arrow.is-expanded {
  transform: rotate(90deg);
}

.category-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text);
}

.category-count {
  font-size: 12px;
  color: var(--theme-text-muted);
  background: var(--theme-surface-hover);
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
  background: var(--theme-bg);
  border-radius: 6px;
  border: 1px solid var(--theme-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border);
  background: var(--theme-surface);
}

.tree-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text);
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
  background: var(--theme-surface-hover);
}

.tree-node.is-active {
  background: rgba(240, 90, 35, 0.1);
}

.tree-node.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: var(--main-orange);
  border-radius: 0 2px 2px 0;
}

.tree-node-all .tree-node-icon {
  color: var(--main-orange);
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
  color: var(--theme-text-muted);
  transition: transform 0.2s;
}

.tree-node-expand .el-icon.is-expanded {
  transform: rotate(90deg);
}

.tree-node-icon {
  display: flex;
  align-items: center;
  color: var(--main-orange);
  flex-shrink: 0;
}

.tree-node-name {
  flex: 1;
  font-size: 13px;
  color: var(--theme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-count {
  font-size: 11px;
  color: var(--theme-text-muted);
  background: var(--theme-surface-hover);
  border-radius: 10px;
  padding: 1px 8px;
  flex-shrink: 0;
  min-width: 20px;
  text-align: center;
}

.tree-node.is-active .tree-node-count {
  background: var(--main-orange);
  color: #fff;
}

/* 右侧指标表格面板 */
.metric-table-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--theme-surface);
  border-radius: 6px;
  border: 1px solid var(--theme-border);
}

.table-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border);
  background: var(--theme-bg);
}

.table-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.search-wrap {
  width: 200px;
}

.search-wrap :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--theme-border) inset;
}

.search-wrap :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--main-orange) inset;
}

.table-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text);
}

.table-count {
  font-size: 12px;
  color: var(--theme-text-muted);
}

.metric-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.metric-name-cell .el-icon {
  color: var(--main-orange);
}

/* ========== 指标展开行：可用维度 ========== */
.metric-dimensions-expand {
  display: flex;
  gap: 16px;
  padding: 12px 16px;
  background: var(--theme-bg);
  border-radius: 6px;
  margin: 4px 0;
}

.expand-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  flex-shrink: 0;
  line-height: 24px;
}

.expand-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.expand-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--theme-text-muted);
}

.dimension-item {
  padding: 8px 12px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dimension-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dimension-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
}

.dimension-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.meta-pair {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.meta-key {
  color: var(--theme-text-muted);
}

.dimension-item-desc {
  font-size: 12px;
  color: var(--theme-text-muted);
  line-height: 1.5;
}

.expand-empty {
  font-size: 13px;
  color: var(--theme-text-muted);
}

/* ========== 共享元数据开关样式（与 DatasourceForm 保持一致） ========== */
.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.checkbox-label input[type='checkbox'] {
  display: none;
}

.switch-text {
  font-size: 13px;
  color: var(--theme-text-secondary);
}

.switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: var(--theme-text-muted);
  border-radius: 10px;
  transition: background 0.25s;
}

.slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background: var(--theme-surface);
  border-radius: 50%;
  transition: transform 0.25s;
}

.switch input:checked + .slider {
  background: var(--main-orange);
}

.switch input:checked + .slider::before {
  transform: translateX(16px);
}

.switch input:disabled + .slider {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
