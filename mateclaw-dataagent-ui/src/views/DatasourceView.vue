<template>
  <div class="datasource-page">
    <!-- 数据源配置表单页（直接打开，不再走选择页） -->
    <DatasourceForm
      v-if="showFormPage"
      :source-id="60"
      :edit-id="editingDsId"
      @back="handleBackFromForm"
      @cancel="handleBackFromForm"
      @submit="handleFormSubmit"
    />

    <template v-else>
      <!-- 顶部标题栏 + 新建数据源 -->
      <div class="page-topbar">
        <h1 class="topbar-title">{{ t('datasourcePage.title') }}</h1>
        <button class="btn-create-top" @click="handleCreateDatasource">
          ＋ {{ t('datasourcePage.createDatasource') }}
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && datasources.length === 0" class="page-loading">
        <span>{{ t('datasourcePage.loading') }}</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading && metricPlatformList.length === 0" class="empty-section">
        <div class="empty-icon-wrapper">
          <span class="empty-folder-icon">📁</span>
          <span class="empty-badge">📊</span>
        </div>
        <p class="empty-desc">{{ t('datasourcePage.emptyDesc') }}</p>
        <button class="btn-create-empty" @click="handleCreateDatasource">
          ＋ {{ t('datasourcePage.createDatasource') }}
        </button>
      </div>

      <!-- 主从布局：左侧数据源列表 / 右侧数据源详情 -->
      <div v-else class="master-detail-layout">
        <!-- 左侧：数据源列表（仅指标平台） -->
        <aside class="ds-sidebar">
          <div class="ds-list-scroll">
            <div
              v-for="ds in metricPlatformList"
              :key="ds.id"
              class="ds-list-item"
              :class="{ active: selectedDsId === ds.id, disabled: !ds.enabled }"
              @click="handleSelectDs(ds)"
            >
              <span class="item-icon">📊</span>
              <div class="item-info">
                <span class="item-name">{{ ds.name }}</span>
                <span class="item-type">{{ t('datasourcePage.typeMetricPlatform') }}</span>
              </div>
              <div class="item-actions" @click.stop>
                <button
                  class="item-action-btn"
                  :title="t('datasourcePage.actionRename')"
                  @click="handleRename(ds)"
                >✏️</button>
                <button
                  class="item-action-btn danger"
                  :title="t('datasourcePage.actionDelete')"
                  @click="handleDelete(ds)"
                >🗑️</button>
              </div>
            </div>
          </div>
        </aside>

        <!-- 右侧：数据源详情（指标平台专属视图） -->
        <main class="ds-detail">
          <!-- 顶部操作工具栏：启停 / 测试 / 同步 -->
          <div v-if="selectedDs" class="detail-toolbar">
            <div class="toolbar-left">
              <span class="ds-name">{{ selectedDs.name }}</span>
              <span class="ds-type-tag">{{ t('datasourcePage.typeMetricPlatform') }}</span>
              <span class="ds-status" :class="selectedDs.enabled ? 'on' : 'off'">
                {{ selectedDs.enabled ? t('datasourcePage.statusEnabled') : t('datasourcePage.statusDisabled') }}
              </span>
            </div>
            <div class="toolbar-right">
              <button
                class="toolbar-btn"
                :title="t('datasourcePage.actionToggle')"
                @click="handleToggle(selectedDs)"
              >
                <span class="btn-icon">{{ selectedDs.enabled ? '⏸️' : '▶️' }}</span>
                <span class="btn-text">{{ t('datasourcePage.actionToggle') }}</span>
              </button>
              <button
                class="toolbar-btn"
                :disabled="testingId === selectedDs.id"
                :title="t('datasourcePage.actionTest')"
                @click="handleTest(selectedDs)"
              >
                <span class="btn-icon">{{ testingId === selectedDs.id ? '⏳' : '🔌' }}</span>
                <span class="btn-text">{{ t('datasourcePage.actionTest') }}</span>
              </button>
              <button
                class="toolbar-btn"
                :disabled="syncingId === selectedDs.id"
                :title="t('datasourcePage.actionSync')"
                @click="handleSync(selectedDs)"
              >
                <span class="btn-icon">{{ syncingId === selectedDs.id ? '⏳' : '🔄' }}</span>
                <span class="btn-text">{{ t('datasourcePage.actionSync') }}</span>
              </button>
            </div>
          </div>

          <div v-if="selectedDsId" class="detail-body">
            <MetricPlatformPanel :datasource-id="selectedDsId" :refresh-key="panelRefreshKey" />
          </div>
          <div v-else class="detail-placeholder">
            <p>{{ t('datasourcePage.selectSource') }}</p>
          </div>
        </main>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import * as datasourceApi from '@/api/datasource'
import type { Datasource } from '@/types'
import DatasourceForm from './datasource/DatasourceForm.vue'
import MetricPlatformPanel from './datasource/MetricPlatformPanel.vue'

const { t } = useI18n()
const store = useDatasourceStore()
const { datasources, loading } = storeToRefs(store)

/** 指标平台数据源 sourceType 标识集合 */
const METRIC_PLATFORM_TYPES = new Set(['aloudata', 'metric_platform', 'metricplatform'])

/** 是否显示数据源配置表单页 */
const showFormPage = ref(false)
/** 当前编辑的数据源ID（空字符串表示新建） */
const editingDsId = ref('')
const selectedDsId = ref('')
/** 右侧详情面板的强制刷新版本号（重命名等不切换选中时递增） */
const panelRefreshKey = ref(0)
const testingId = ref<string | null>(null)
const syncingId = ref<string | null>(null)

/** 仅展示指标平台数据源 */
const metricPlatformList = computed<Datasource[]>(() => {
  return datasources.value.filter((ds) => METRIC_PLATFORM_TYPES.has((ds.sourceType || '').toLowerCase()))
})

/** 当前选中的数据源对象 */
const selectedDs = computed<Datasource | null>(() => {
  return metricPlatformList.value.find((d) => d.id === selectedDsId.value) || null
})

/** 选中数据源后默认选中第一个 */
watch(metricPlatformList, (list) => {
  if (list.length > 0 && !list.some((d) => d.id === selectedDsId.value)) {
    selectedDsId.value = list[0].id
  }
  if (list.length === 0) {
    selectedDsId.value = ''
  }
}, { immediate: true })

onMounted(() => {
  store.fetchDatasources()
})

/** 跳转到新建数据源 */
function handleCreateDatasource(): void {
  editingDsId.value = ''
  showFormPage.value = true
}

/** 表单页返回列表 */
function handleBackFromForm(): void {
  showFormPage.value = false
  editingDsId.value = ''
  store.fetchDatasources()
}

/** 表单提交成功 */
function handleFormSubmit(): void {
  showFormPage.value = false
  editingDsId.value = ''
  store.fetchDatasources()
}

/** 重命名数据源 */
async function handleRename(ds: Datasource): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      t('datasourcePage.renamePromptMessage'),
      t('datasourcePage.actionRename'),
      {
        inputValue: ds.name,
        inputValidator: (val: string) => {
          const trimmed = val?.trim() || ''
          if (!trimmed) {
            return t('datasourcePage.renameRequired')
          }
          if (trimmed.length > 64) {
            return t('datasourcePage.renameTooLong')
          }
          return true
        },
        confirmButtonText: t('common.save'),
        cancelButtonText: t('common.cancel'),
      },
    )
    const newName = (value as string).trim()
    if (newName === ds.name) {
      return
    }
    await datasourceApi.update(ds.id, { name: newName })
    ElMessage.success(t('common.success'))
    await store.fetchDatasources()
    // 重命名不改变选中ID，递增版本号让右侧面板重新拉详情
    panelRefreshKey.value += 1
  } catch (e) {
    // 用户取消或接口异常（异常已由 axios 拦截器统一处理）
    if (e === 'cancel') {
      return
    }
  }
}

/** 删除数据源 */
async function handleDelete(ds: Datasource): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('datasourcePage.deleteConfirmMessage', { name: ds.name }),
      t('datasourcePage.actionDelete'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        confirmButtonClass: 'el-button--danger',
      },
    )
    await datasourceApi.remove(ds.id)
    ElMessage.success(t('common.success'))
    if (selectedDsId.value === ds.id) {
      selectedDsId.value = ''
    }
    await store.fetchDatasources()
  } catch (e) {
    // 用户取消或接口异常
    if (e === 'cancel') {
      return
    }
  }
}

/** 选中左侧数据源项 */
function handleSelectDs(ds: Datasource): void {
  if (selectedDsId.value !== ds.id) {
    selectedDsId.value = ds.id
  }
}

/** 测试连接 */
async function handleTest(ds: Datasource): Promise<void> {
  testingId.value = ds.id
  try {
    const result = await datasourceApi.testConnection(ds.id)
    if (result) {
      ElMessage.success(t('datasourcePage.testSuccess'))
    } else {
      ElMessage.error(t('datasourcePage.testFail'))
    }
    store.fetchDatasources()
  } catch {
    ElMessage.error(t('datasourcePage.testFail'))
  } finally {
    testingId.value = null
  }
}

/** 启停切换 */
async function handleToggle(ds: Datasource): Promise<void> {
  try {
    await datasourceApi.toggle(ds.id, !ds.enabled)
    ElMessage.success(t('datasourcePage.toggleSuccess'))
    store.fetchDatasources()
  } catch {
    // error handled by interceptor
  }
}

/** 同步数据表（触发 Schema 发现） */
async function handleSync(ds: Datasource): Promise<void> {
  syncingId.value = ds.id
  try {
    await datasourceApi.triggerSchemaDiscovery(ds.id)
    ElMessage.success(t('datasourcePage.syncSuccess'))
    store.fetchDatasources()
  } catch {
    ElMessage.error(t('datasourcePage.syncFail'))
  } finally {
    syncingId.value = null
  }
}
</script>

<style scoped>
.datasource-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  overflow: hidden;
}

.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.btn-create-top {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create-top:hover {
  background: #0e42d2;
}

.page-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #86909c;
  font-size: 14px;
}

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 80px 0;
  background: #fff;
}

.empty-icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.empty-folder-icon {
  font-size: 72px;
  opacity: 0.6;
}

.empty-badge {
  position: absolute;
  bottom: 4px;
  right: 0;
  font-size: 28px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.15));
}

.empty-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0 0 20px 0;
  text-align: center;
  max-width: 320px;
  line-height: 1.6;
}

.btn-create-empty {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create-empty:hover {
  background: #0e42d2;
}

/* ========== 主从布局 ========== */
.master-detail-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ========== 左侧：数据源侧边栏 ========== */
.ds-sidebar {
  width: 240px;
  min-width: 240px;
  background: #fff;
  border-right: 1px solid #e5e6eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.ds-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.ds-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}

.ds-list-item:hover {
  background: #f2f3f5;
}

.ds-list-item.active {
  background: #e8f3ff;
  border-left-color: #165dff;
}

.ds-list-item.disabled {
  opacity: 0.55;
}

.item-icon {
  font-size: 20px;
  flex-shrink: 0;
  line-height: 1;
}

.item-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-type {
  font-size: 11.5px;
  color: #c9cdd4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.ds-list-item:hover .item-actions,
.ds-list-item.active .item-actions {
  opacity: 1;
}

.item-action-btn {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  color: #4e5969;
  transition: background 0.15s, color 0.15s;
  padding: 0;
}

.item-action-btn:hover {
  background: #165dff;
  color: #fff;
}

.item-action-btn.danger:hover {
  background: #f53f3f;
  color: #fff;
}

/* ========== 右侧：数据源详情 ========== */
.ds-detail {
  flex: 1;
  min-width: 0;
  background: #f7f8fa;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 详情顶部操作栏 */
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ds-name {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
}

.ds-type-tag {
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #f05a23;
  background: #fff2e8;
}

.ds-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.ds-status.on {
  color: #00b42a;
  background: #e8ffea;
}

.ds-status.off {
  color: #c9cdd4;
  background: #f2f3f5;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #4e5969;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.toolbar-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.toolbar-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-icon {
  font-size: 13px;
  line-height: 1;
}

.btn-text {
  font-size: 12.5px;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
}

.detail-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #c9cdd4;
  font-size: 13px;
}
</style>
