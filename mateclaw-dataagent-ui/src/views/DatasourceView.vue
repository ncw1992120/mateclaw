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
      <!-- 身份页头：标题 + 启停统计 + 描述（左） + 主操作（右），与技能/智能体页同一版式语言 -->
      <div class="ds-content-header">
        <div class="ds-content-title">
          <div class="ds-content-name-row">
            <h3 class="ds-content-name">{{ t('configCenter.tabData') }}</h3>
            <!-- 启停数量标识：常驻标题旁，色彩编码与状态 chip 一致 -->
            <span v-if="statsReady" class="ds-title-stats">
              <span class="summary-item">
                <span class="summary-dot on" aria-hidden="true"></span>
                {{ t('skillManage.sectionEnabled') }}
                <b class="summary-num">{{ statusCounts.enabled }}</b>
              </span>
              <span class="summary-item off">
                <span class="summary-dot" aria-hidden="true"></span>
                {{ t('skillManage.sectionAvailable') }}
                <b class="summary-num">{{ statusCounts.disabled }}</b>
              </span>
            </span>
          </div>
          <p class="ds-content-desc">{{ t('configCenter.dataDesc') }}</p>
        </div>
        <div class="header-actions">
          <button v-if="hasPermission(PERMISSION.DATASOURCE_CREATE)" type="button" class="btn-create-pill" @click="handleCreateDatasource">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {{ t('datasourcePage.createDatasource') }}
          </button>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && datasources.length === 0" class="page-loading">
        <span>{{ t('datasourcePage.loading') }}</span>
      </div>

      <!-- 全局空态：线型 SVG chip + 文案 + 新建按钮，与技能/智能体页同形态 -->
      <div v-else-if="!loading && metricPlatformList.length === 0" class="global-empty surface-card">
        <div class="global-empty-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <ellipse cx="12" cy="5" rx="9" ry="3" />
            <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
            <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
          </svg>
        </div>
        <h3>{{ t('datasourcePage.emptyDesc') }}</h3>
        <button v-if="hasPermission(PERMISSION.DATASOURCE_CREATE)" type="button" class="btn-create-pill small" @click="handleCreateDatasource">
          {{ t('datasourcePage.createDatasource') }}
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
              <span class="item-icon">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <ellipse cx="12" cy="5" rx="9" ry="3" />
                  <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
                  <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
                </svg>
              </span>
              <!-- 单行紧凑布局：名称弹性占据剩余空间，共享标签/账号徽标/操作按钮依次右靠 -->
              <span class="item-name" :title="ds.name">{{ ds.name }}</span>
              <span v-if="ds.metaShared" class="item-shared-tag">共享</span>
              <div
                class="item-account-badge"
                :class="resolveAccountBadge(ds.id).dotClass"
                :title="`查询账号：${resolveAccountBadge(ds.id).text}`"
              >
                <span class="badge-dot"></span>
                <span class="badge-text">{{ resolveAccountBadge(ds.id).text }}</span>
                <span
                  v-if="resolveAccountBadge(ds.id).testOk !== null"
                  class="badge-test"
                  :class="resolveAccountBadge(ds.id).testOk ? 'test-ok' : 'test-fail'"
                  :title="resolveAccountBadge(ds.id).testOk ? '连接正常' : '连接失败'"
                >{{ resolveAccountBadge(ds.id).testOk ? '✓' : '✗' }}</span>
              </div>
              <!-- 「⋯」更多菜单：重命名/删除收进下拉，行面只留一个安静触发器 -->
              <el-dropdown
                v-if="ds.permission === 'edit'"
                class="item-more"
                trigger="click"
                popper-class="ds-more-popper"
                @command="(cmd: string | number | object) => handleItemCommand(cmd, ds)"
              >
                <button class="item-more-btn" :title="t('datasourcePage.moreActions')" @click.stop>
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
                    <circle cx="5" cy="12" r="1.7" />
                    <circle cx="12" cy="12" r="1.7" />
                    <circle cx="19" cy="12" r="1.7" />
                  </svg>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">
                      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                      {{ t('datasourcePage.actionRename') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" class="danger">
                      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                        <line x1="10" y1="11" x2="10" y2="17"/>
                        <line x1="14" y1="11" x2="14" y2="17"/>
                      </svg>
                      {{ t('datasourcePage.actionDelete') }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
              <span v-if="selectedDs.metaShared" class="ds-shared-tag">共享元数据</span>
            </div>
            <div class="toolbar-right">
              <button
                v-if="selectedDs.permission === 'edit'"
                class="toolbar-btn"
                :title="t('datasourcePage.actionToggle')"
                @click="handleToggle(selectedDs)"
              >
                <svg v-if="selectedDs.enabled" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                  <line x1="9" y1="5" x2="9" y2="19" />
                  <line x1="15" y1="5" x2="15" y2="19" />
                </svg>
                <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true">
                  <polygon points="6 4 20 12 6 20 6 4" />
                </svg>
                <span class="btn-text">{{ t('datasourcePage.actionToggle') }}</span>
              </button>
              <button
                class="toolbar-btn"
                :disabled="testingId === selectedDs.id"
                :title="selectedDs.permission === 'edit' ? t('datasourcePage.actionTest') : '测试你的查询账号连接（问数时使用此账号）'"
                @click="handleTest(selectedDs)"
              >
                <span v-if="testingId === selectedDs.id" class="btn-spinner" />
                <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
                <span class="btn-text">{{ t('datasourcePage.actionTest') }}</span>
              </button>
              <button
                v-if="canSyncMetadata"
                class="toolbar-btn"
                :disabled="syncing || debouncedSyncPending"
                :title="t('metricPlatform.syncTrigger')"
                @click="handleSyncMetadata"
              >
                <span v-if="syncing" class="btn-spinner" />
                <svg v-else viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="23 4 23 10 17 10" />
                  <polyline points="1 20 1 14 7 14" />
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
                </svg>
                <span class="btn-text">{{ syncing ? t('metricPlatform.syncing') : t('metricPlatform.syncTrigger') }}</span>
              </button>
              <button
                class="toolbar-btn"
                :title="'配置你的查询账号（问数时使用此账号连接数据库，确保数据权限隔离）'"
                @click="handleOpenAccountDialog"
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4" />
                </svg>
                <span class="btn-text">{{ t('datasourcePage.queryAccountConfig') }}</span>
              </button>
            </div>
          </div>

          <div v-if="selectedDsId" class="detail-body">
            <MetricPlatformPanel
              :datasource-id="selectedDsId"
              :refresh-key="panelRefreshKey"
            />
          </div>
          <div v-else class="detail-placeholder">
            <p>{{ t('datasourcePage.selectSource') }}</p>
          </div>
        </main>
      </div>
    </template>

    <!-- 查询账号配置对话框 -->
    <el-dialog
      v-model="accountDialogVisible"
      :title="t('datasourcePage.queryAccountDialogTitle')"
      width="480px"
      @close="handleAccountDialogClose"
    >
      <div v-if="selectedDs" class="account-dialog-body">
        <p class="account-hint">{{ t('datasourcePage.queryAccountHint') }}</p>
        <el-form label-width="100px" label-position="right">
          <el-form-item v-if="!isAloudataDatasource" :label="t('datasourcePage.queryUsername')">
            <el-input v-model="accountForm.queryUsername" :placeholder="t('datasourcePage.queryUsernamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="isAloudataDatasource ? t('datasourcePage.aloudataAuthValue') : t('datasourcePage.queryPassword')">
            <el-input v-model="accountForm.queryPassword" type="password" show-password :placeholder="isAloudataDatasource ? t('datasourcePage.aloudataAuthValuePlaceholder') : t('datasourcePage.queryPasswordPlaceholder')" />
          </el-form-item>
        </el-form>
        <div v-if="accountLastTestOk !== null" class="account-test-result">
          <span :class="accountLastTestOk ? 'test-ok' : 'test-fail'">
            {{ accountLastTestOk ? t('datasourcePage.accountTestOk') : t('datasourcePage.accountTestFail') }}
          </span>
        </div>
      </div>
      <template #footer>
        <el-button @click="handleTestAccountConnection" :loading="accountTesting">
          {{ t('datasourcePage.accountTestBtn') }}
        </el-button>
        <el-button @click="handleDeleteAccount" type="danger" plain :disabled="!accountHasExisting">
          {{ t('datasourcePage.accountDeleteBtn') }}
        </el-button>
        <el-button @click="handleSaveAccount" type="primary" :disabled="!canSaveAccount">
          {{ t('datasourcePage.accountSaveBtn') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import { useUserStore } from '@/stores/useUserStore'
import * as datasourceApi from '@/api/datasource'
import type { DatasourceAccountVO } from '@/api/datasource'
import { useDebouncedFn } from '@/composables/useDebouncedFn'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import type { Datasource } from '@/types'
import { encryptSensitiveField } from '@/utils/sensitiveCrypto'
import DatasourceForm from './datasource/DatasourceForm.vue'
import MetricPlatformPanel from './datasource/MetricPlatformPanel.vue'

const { t } = useI18n()
const store = useDatasourceStore()
const userStore = useUserStore()
const { hasPermission } = usePermission()
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
/** 同步中状态（语义层同步，工具栏同步按钮专用） */
const syncing = ref(false)

/** 当前用户在各数据源上的查询账号绑定状态映射（key=datasourceId, value=账号 VO） */
const accountStatusMap = ref<Map<string, DatasourceAccountVO>>(new Map())

/** 当前用户是否可同步元数据（复用权限体系） */
const canSyncMetadata = computed<boolean>(() => hasPermission(PERMISSION.DATASOURCE_SYNC))

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

/** 首次加载完成后才显示标题旁启停统计，避免首帧 0/0 闪烁 */
const statsReady = ref(false)

/** 启停统计：与侧栏列表同源 */
const statusCounts = computed(() => ({
  enabled: metricPlatformList.value.filter((d) => d.enabled).length,
  disabled: metricPlatformList.value.filter((d) => !d.enabled).length,
}))

onMounted(async () => {
  // 并行加载数据源列表与当前用户的查询账号绑定状态
  await Promise.allSettled([store.fetchDatasources(), loadAccountStatus()])
  statsReady.value = true
})

/** 加载当前用户所有已绑定的查询账号，构建 datasourceId → account VO 映射 */
async function loadAccountStatus(): Promise<void> {
  try {
    const accounts = await datasourceApi.listDatasourceAccounts()
    const map = new Map<string, DatasourceAccountVO>()
    if (Array.isArray(accounts)) {
      for (const acc of accounts) {
        map.set(String(acc.datasourceId), acc)
      }
    }
    accountStatusMap.value = map
  } catch {
    // 加载失败不阻塞主流程，徽标按"未绑定"显示
  }
}

/** 单条刷新指定数据源的账号绑定状态（测试/保存后调用） */
async function refreshAccountStatus(datasourceId: string | number): Promise<void> {
  try {
    const account = await datasourceApi.getDatasourceAccount(datasourceId)
    const map = new Map(accountStatusMap.value)
    if (account) {
      map.set(String(datasourceId), account)
    } else {
      map.delete(String(datasourceId))
    }
    accountStatusMap.value = map
  } catch {
    // 未绑定查询账号时 API 返回 404，从映射中移除
    const map = new Map(accountStatusMap.value)
    map.delete(String(datasourceId))
    accountStatusMap.value = map
  }
}

/** 移除指定数据源的账号绑定状态（删除账号后调用） */
function removeAccountStatus(datasourceId: string | number): void {
  const map = new Map(accountStatusMap.value)
  map.delete(String(datasourceId))
  accountStatusMap.value = map
}

/** 解析数据源对应的账号绑定状态文案与样式类 */
function resolveAccountBadge(dsId: string): { text: string; dotClass: string; testOk: boolean | null } {
  const acc = accountStatusMap.value.get(dsId)
  if (!acc) {
    return { text: '未绑定', dotClass: 'dot-unbound', testOk: null }
  }
  if (acc.status === 1) {
    return { text: '已绑定', dotClass: 'dot-bound', testOk: acc.lastTestOk ?? null }
  }
  return { text: '已停用', dotClass: 'dot-disabled', testOk: acc.lastTestOk ?? null }
}

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

/** 列表项「⋯」更多菜单命令分发 */
function handleItemCommand(command: string | number | object, ds: Datasource): void {
  if (command === 'rename') {
    handleRename(ds)
  } else if (command === 'delete') {
    handleDelete(ds)
  }
}

/**
 * 测试连接：按身份区分账号来源
 * - owner（permission=edit）：用数据源配置的管理员账号测试
 * - 非 owner（共享数据源查看者）：用当前用户的查询账号测试，未绑定时提示先配置
 */
async function handleTest(ds: Datasource): Promise<void> {
  testingId.value = ds.id
  try {
    const isOwner = ds.permission === 'edit'
    if (isOwner) {
      // owner 测试数据源本身的连接（管理员账号）
      const result = await datasourceApi.testConnection(ds.id)
      if (result) {
        ElMessage.success(t('datasourcePage.testSuccess'))
      } else {
        ElMessage.error(t('datasourcePage.testFail'))
      }
    } else {
      // 非 owner 测试当前用户的查询账号连接
      try {
        const result = await datasourceApi.testDatasourceAccount(ds.id)
        if (result) {
          ElMessage.success(t('datasourcePage.testSuccess'))
        } else {
          ElMessage.error(t('datasourcePage.testFail'))
        }
        // 刷新列表徽标状态（测试结果已持久化到后端）
        refreshAccountStatus(ds.id)
      } catch {
        // 未绑定查询账号或测试失败时，axios 拦截器已弹出后端错误消息，前端不再重复提示
      }
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

/** 真正执行同步的函数（被防抖包装） */
async function doSync(): Promise<void> {
  if (!selectedDs.value) return
  syncing.value = true
  try {
    const result = await datasourceApi.syncAloudataSemantic(selectedDs.value.id)
    if (result?.status === 'completed') {
      ElMessage.success(t('metricPlatform.syncSuccess'))
    } else {
      ElMessage.error(result?.message || t('metricPlatform.syncFailed'))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || t('metricPlatform.syncFailed'))
  } finally {
    syncing.value = false
  }
}

/**
 * 防抖包装：800ms 内的多次点击只触发最后一次。
 * 语义层同步会拉取全量元数据并写入语义层，非常消耗资源，必须避免重复触发。
 */
const { invoke: debouncedInvoke, pending: debouncedSyncPending } = useDebouncedFn(
  doSync,
  800,
)

/** 触发同步元数据（被防抖处理） */
function handleSyncMetadata(): void {
  if (syncing.value) return
  if (debouncedSyncPending.value) {
    ElMessage.warning(t('metricPlatform.syncDebounceHint'))
    return
  }
  debouncedInvoke()
}

// ==================== 查询账号配置 ====================

const accountDialogVisible = ref(false)
const accountForm = ref({ queryUsername: '', queryPassword: '' })
const accountHasExisting = ref(false)
const accountTesting = ref(false)
const accountLastTestOk = ref<boolean | null>(null)

/** 当前选中的数据源是否为 Aloudata 类型 */
const isAloudataDatasource = computed<boolean>(() => {
  return selectedDs.value?.sourceType?.toLowerCase() === 'aloudata'
})

/** 保存按钮是否可用：Aloudata 类型仅需认证值，JDBC 类型需要用户名和密码 */
const canSaveAccount = computed<boolean>(() => {
  if (isAloudataDatasource.value) {
    return !!accountForm.value.queryPassword
  }
  return !!accountForm.value.queryUsername && !!accountForm.value.queryPassword
})

/** 打开查询账号配置对话框 */
async function handleOpenAccountDialog(): Promise<void> {
  if (!selectedDs.value) return
  accountDialogVisible.value = true
  accountLastTestOk.value = null
  accountForm.value = { queryUsername: '', queryPassword: '' }
  accountHasExisting.value = false

  try {
    const account = await datasourceApi.getDatasourceAccount(selectedDs.value.id)
    if (account) {
      accountForm.value.queryUsername = account.queryUsername || ''
      accountForm.value.queryPassword = ''
      accountHasExisting.value = true
      accountLastTestOk.value = account.lastTestOk ?? null
    }
  } catch {
    // 未绑定查询账号，忽略
  }
}

/** 关闭对话框 */
function handleAccountDialogClose(): void {
  accountDialogVisible.value = false
  accountForm.value = { queryUsername: '', queryPassword: '' }
  accountHasExisting.value = false
  accountLastTestOk.value = null
}

/** 保存查询账号 */
async function handleSaveAccount(): Promise<void> {
  if (!selectedDs.value) return
  if (!canSaveAccount.value) {
    ElMessage.warning(t('datasourcePage.queryAccountRequired'))
    return
  }
  try {
    // 查询密码做 RSA-OAEP 传输加密，后端解密后 AES 存储加密落库
    const encryptedPwd = accountForm.value.queryPassword
      ? await encryptSensitiveField(accountForm.value.queryPassword)
      : accountForm.value.queryPassword
    await datasourceApi.upsertDatasourceAccount({
      datasourceId: selectedDs.value.id,
      queryUsername: isAloudataDatasource.value ? '' : accountForm.value.queryUsername,
      queryPassword: encryptedPwd,
    })
    accountHasExisting.value = true
    // 刷新列表徽标状态
    await refreshAccountStatus(selectedDs.value.id)
    ElMessage.success(t('datasourcePage.queryAccountSaveSuccess'))
    // 保存成功后关闭弹窗
    accountDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || t('datasourcePage.queryAccountSaveFail'))
  }
}

/** 删除查询账号 */
async function handleDeleteAccount(): Promise<void> {
  if (!selectedDs.value) return
  try {
    await ElMessageBox.confirm(
      t('datasourcePage.queryAccountDeleteConfirm'),
      t('datasourcePage.accountDeleteBtn'),
      { type: 'warning' },
    )
    await datasourceApi.deleteDatasourceAccount(selectedDs.value.id)
    accountForm.value = { queryUsername: '', queryPassword: '' }
    accountHasExisting.value = false
    accountLastTestOk.value = null
    // 移除列表徽标状态
    removeAccountStatus(selectedDs.value.id)
    ElMessage.success(t('datasourcePage.queryAccountDeleteSuccess'))
  } catch {
    // 用户取消或请求失败
  }
}

/** 测试查询账号连接（仅做连通性测试，不修改数据库） */
async function handleTestAccountConnection(): Promise<void> {
  if (!selectedDs.value) return
  accountTesting.value = true
  try {
    // 使用当前表单中的账号参数进行临时测试，不保存到数据库
    const encryptedPwd = accountForm.value.queryPassword
      ? await encryptSensitiveField(accountForm.value.queryPassword)
      : accountForm.value.queryPassword
    const testOk = await datasourceApi.testDatasourceAccount(selectedDs.value.id, {
      queryUsername: isAloudataDatasource.value ? '' : accountForm.value.queryUsername,
      queryPassword: encryptedPwd,
    })
    accountLastTestOk.value = !!testOk
    if (testOk) {
      ElMessage.success(t('datasourcePage.accountTestOk'))
    } else {
      ElMessage.error(t('datasourcePage.accountTestFail'))
    }
  } catch (e: any) {
    accountLastTestOk.value = false
    ElMessage.error(e?.message || t('datasourcePage.accountTestFail'))
  } finally {
    accountTesting.value = false
  }
}
</script>

<style scoped>
.datasource-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  /* 透明底：配置壳纸面卡片已提供工作区层级，子视图不再自带灰底 */
  background: transparent;
  overflow: hidden;
}

/* 身份页头：标题/描述靠左，主操作靠右，单行对齐 */
.ds-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 2px 2px 14px;
}

.ds-content-title {
  min-width: 0;
}

.ds-content-name-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.ds-content-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ds-content-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 启停数量标识：紧凑内联，色彩编码与状态 chip 一致 */
.ds-title-stats {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary);
}
.summary-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--db-text-muted);
}
.summary-dot.on {
  background: var(--el-color-success);
}
.summary-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--db-text);
  margin-left: 1px;
}
.summary-item.off .summary-num {
  color: var(--db-text-secondary);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-left: auto;
  flex-shrink: 0;
}

/* 主操作：家族胶囊按钮（主题色实心 + 白字 + 阴影） */
.btn-create-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  box-shadow: var(--shadow-md);
  transition: filter var(--transition-fast, 0.15s);
}
.btn-create-pill:hover {
  filter: brightness(1.08);
}
.btn-create-pill.small {
  height: 30px;
  padding: 0 14px;
  font-size: 12px;
}

.page-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--db-text-muted);
  font-size: 13px;
}

/* 全局空态：一整块接管内容区，图标为线型 SVG chip */
.global-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 56px 20px;
  text-align: center;
}
.global-empty-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  color: var(--db-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}
.global-empty h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  margin: 0 0 14px;
  max-width: 360px;
  line-height: 1.6;
}

/* 卡片面：系统统一变量（白面 + 细边 + 圆角） */
.surface-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
}

/* ========== 主从布局 ========== */
.master-detail-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ========== 左侧：数据源侧边栏 ========== */
/* 侧栏：家族卡片面（白面 + 细边 + 圆角 + 弱阴影），与详情区以留白分界而非分线 */
.ds-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  margin-right: 14px;
  overflow: hidden;
}

.ds-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 列表项：单行紧凑布局（图标 + 名称 + 徽标 + 操作），项间由滚动区 gap 分隔 */
.ds-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 8px;
  transition: background var(--transition-fast, 0.15s);
}

.ds-list-item:hover {
  background: var(--db-hover);
}

/* 激活态：主题色 10% 淡底 + 名称主题色加粗，与配置侧栏同一语言 */
.ds-list-item.active {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.ds-list-item.active .item-name {
  color: var(--main-orange);
  font-weight: 600;
}

.ds-list-item.disabled {
  opacity: 0.62;
}

/* 图标：默认不给底色与字色（继承行文本色，安静线型），行 hover/激活时才点亮全局蓝色 tint 变量对（暗色自适配） */
.item-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: background-color 120ms ease, color 120ms ease;
}

.ds-list-item.active .item-icon {
  background: var(--db-card-blue-bg);
  color: var(--db-card-blue-fg);
}

.item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-shared-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  background: var(--db-card-blue-bg);
  color: var(--db-card-blue-fg);
  white-space: nowrap;
}

/* 账号绑定状态徽标：色彩改用家族语义令牌（success 绿 / 橙 / 安静灰 / danger 红） */
.item-account-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  flex-shrink: 0;
  background: var(--db-hover);
}

.item-account-badge .badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-account-badge.dot-bound .badge-dot {
  background: var(--el-color-success);
}

.item-account-badge.dot-disabled .badge-dot {
  background: var(--db-card-orange-fg);
}

.item-account-badge.dot-unbound .badge-dot {
  background: var(--db-text-muted);
}

.item-account-badge.dot-bound {
  color: var(--el-color-success);
}

.item-account-badge.dot-disabled {
  color: var(--db-card-orange-fg);
}

.item-account-badge.dot-unbound {
  color: var(--db-text-secondary);
}

.item-account-badge .badge-text {
  white-space: nowrap;
}

.item-account-badge .badge-test {
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.item-account-badge .badge-test.test-ok {
  color: var(--el-color-success);
}

.item-account-badge .badge-test.test-fail {
  color: var(--db-danger);
}

/* 「⋯」更多菜单触发器：24px 幽灵图标按钮，安静灰常驻，行 hover/激活时提亮 */
.item-more {
  flex-shrink: 0;
  display: inline-flex;
}

.item-more-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--db-text-muted);
  transition: background-color 120ms ease, color 120ms ease;
  padding: 0;
}

.ds-list-item:hover .item-more-btn,
.ds-list-item.active .item-more-btn {
  color: var(--db-text-secondary);
}

.item-more-btn:hover {
  background: var(--db-hover);
  color: var(--db-text);
}

/* ========== 右侧：数据源详情 ========== */
.ds-detail {
  flex: 1;
  min-width: 0;
  background: transparent;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 详情顶部操作栏：与内容同处一张纸面，仅以细分隔线与面板分界 */
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px 12px;
  background: transparent;
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
  font-weight: 700;
  color: var(--db-text);
}

.ds-type-tag {
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--db-card-blue-fg);
  background: var(--db-card-blue-bg);
}

.ds-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.ds-status.on {
  color: var(--el-color-success);
  background: color-mix(in srgb, var(--el-color-success) 12%, transparent);
}

.ds-status.off {
  color: var(--db-text-muted);
  background: var(--db-hover);
}

.ds-shared-tag {
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--db-card-blue-fg);
  background: var(--db-card-blue-bg);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 工具栏按钮：家族描边胶囊（卡面 + 细边，hover 主题色描边淡底） */
.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  font-size: 12.5px;
  line-height: 1;
  font-weight: 500;
  cursor: pointer;
  transition: color var(--transition-fast, 0.15s), border-color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
  font-family: inherit;
  white-space: nowrap;
  user-select: none;
}

.toolbar-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--main-orange) 45%, transparent);
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
}

.toolbar-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid color-mix(in srgb, var(--main-orange) 25%, transparent);
  border-top-color: var(--main-orange);
  border-radius: 50%;
  animation: ds-btn-spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes ds-btn-spin {
  to {
    transform: rotate(360deg);
  }
}

.btn-text {
  font-size: 12.5px;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: transparent;
}

.detail-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--db-text-muted);
  font-size: 13px;
}

.account-dialog-body {
  padding: 0 8px;
}

.account-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--db-text-muted);
  line-height: 1.6;
}

.account-test-result {
  margin-top: 12px;
  text-align: center;
}

.account-test-result .test-ok {
  color: var(--el-color-success);
  font-weight: 500;
}

.account-test-result .test-fail {
  color: var(--db-danger);
  font-weight: 500;
}

/* ===== 响应式适配 ===== */

/* 中等屏幕：标题行允许换行，避免标题被统计+按钮挤成零宽 */
@media (max-width: 1024px) {
  .ds-content-header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .ds-content-name-row {
    flex-wrap: wrap;
    gap: 6px 14px;
  }
  .header-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

/* 小屏幕：主从改纵向堆叠，侧栏横向铺满并限高 */
@media (max-width: 720px) {
  .master-detail-layout {
    flex-direction: column;
  }
  .ds-sidebar {
    width: 100%;
    min-width: 0;
    margin-right: 0;
    margin-bottom: 14px;
    max-height: 200px;
  }
  .ds-content-desc {
    white-space: normal;
    overflow: visible;
  }
}
</style>

<style>
/* 「⋯」更多菜单：el-dropdown 菜单 teleport 到 body，scoped 选择器够不到，故用非 scoped 块 + popper-class 限定作用域 */
.ds-more-popper .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
}

.ds-more-popper .el-dropdown-menu__item svg {
  flex-shrink: 0;
}

.ds-more-popper .el-dropdown-menu__item.danger,
.ds-more-popper .el-dropdown-menu__item.danger:hover,
.ds-more-popper .el-dropdown-menu__item.danger:focus {
  color: var(--el-color-danger);
}
</style>
