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
              <span class="item-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <ellipse cx="12" cy="5" rx="9" ry="3" />
                  <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
                  <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
                </svg>
              </span>
              <div class="item-info">
                <div class="item-main-row">
                  <span class="item-name">{{ ds.name }}</span>
                  <div v-if="ds.permission === 'edit'" class="item-actions" @click.stop>
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
                <div class="item-meta-row">
                  <span class="item-type">{{ t('datasourcePage.typeMetricPlatform') }}</span>
                  <span v-if="ds.metaShared" class="item-shared-tag">共享</span>
                  <div class="item-account-badge" :class="resolveAccountBadge(ds.id).dotClass">
                    <span class="badge-dot"></span>
                    <span class="badge-text">{{ resolveAccountBadge(ds.id).text }}</span>
                    <span
                      v-if="resolveAccountBadge(ds.id).testOk !== null"
                      class="badge-test"
                      :class="resolveAccountBadge(ds.id).testOk ? 'test-ok' : 'test-fail'"
                      :title="resolveAccountBadge(ds.id).testOk ? '连接正常' : '连接失败'"
                    >{{ resolveAccountBadge(ds.id).testOk ? '✓' : '✗' }}</span>
                  </div>
                </div>
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
              <span v-if="selectedDs.metaShared" class="ds-shared-tag">共享元数据</span>
            </div>
            <div class="toolbar-right">
              <button
                v-if="selectedDs.permission === 'edit'"
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
                :title="selectedDs.permission === 'edit' ? t('datasourcePage.actionTest') : '测试你的查询账号连接（问数时使用此账号）'"
                @click="handleTest(selectedDs)"
              >
                <span class="btn-icon">{{ testingId === selectedDs.id ? '⏳' : '🔌' }}</span>
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
                <span class="btn-icon">🔑</span>
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
import type { Datasource } from '@/types'
import DatasourceForm from './datasource/DatasourceForm.vue'
import MetricPlatformPanel from './datasource/MetricPlatformPanel.vue'

const { t } = useI18n()
const store = useDatasourceStore()
const userStore = useUserStore()
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

/** 当前用户是否为管理员（全局管理员或工作区 owner/admin），仅管理员可同步元数据 */
const canSyncMetadata = computed<boolean>(() => {
  if (userStore.isAdmin) return true
  const ws = userStore.currentWorkspace
  if (!ws) return false
  return ws.effectiveRole === 'owner' || ws.effectiveRole === 'admin'
})

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
  // 并行加载数据源列表与当前用户的查询账号绑定状态
  store.fetchDatasources()
  loadAccountStatus()
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
    await datasourceApi.upsertDatasourceAccount({
      datasourceId: selectedDs.value.id,
      queryUsername: isAloudataDatasource.value ? '' : accountForm.value.queryUsername,
      queryPassword: accountForm.value.queryPassword,
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
    const testOk = await datasourceApi.testDatasourceAccount(selectedDs.value.id, {
      queryUsername: isAloudataDatasource.value ? '' : accountForm.value.queryUsername,
      queryPassword: accountForm.value.queryPassword,
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
  background: var(--theme-bg);
  overflow: hidden;
}

.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.btn-create-top {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create-top:hover {
  background: var(--dark-orange);
}

.page-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--theme-text-muted);
  font-size: 14px;
}

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 80px 0;
  background: var(--theme-surface);
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
  color: var(--theme-text-muted);
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
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create-empty:hover {
  background: var(--dark-orange);
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
  width: 280px;
  min-width: 280px;
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
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
  background: var(--theme-surface-hover);
}

.ds-list-item.active {
  background: rgba(240, 90, 35, 0.1);
  border-left-color: var(--main-orange);
}

.ds-list-item.disabled {
  opacity: 0.55;
}

.item-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(240, 90, 35, 0.12) 0%, rgba(240, 90, 35, 0.04) 100%);
  color: var(--main-orange);
  box-shadow: inset 0 0 0 1px rgba(240, 90, 35, 0.08);
}

.item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-main-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-type {
  font-size: 12px;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.item-shared-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  background: rgba(240, 90, 35, 0.1);
  color: var(--main-orange);
  white-space: nowrap;
}

/* 账号绑定状态徽标 */
.item-account-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  flex-shrink: 0;
  background: var(--theme-surface-hover);
}

.item-account-badge .badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-account-badge.dot-bound .badge-dot {
  background: #00b42a;
}

.item-account-badge.dot-disabled .badge-dot {
  background: #ff7d00;
}

.item-account-badge.dot-unbound .badge-dot {
  background: var(--theme-text-muted);
}

.item-account-badge.dot-bound {
  color: #00b42a;
}

.item-account-badge.dot-disabled {
  color: #ff7d00;
}

.item-account-badge.dot-unbound {
  color: var(--theme-text-secondary);
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
  color: #00b42a;
}

.item-account-badge .badge-test.test-fail {
  color: #f53f3f;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
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
  color: var(--theme-text-secondary);
  transition: background 0.15s, color 0.15s;
  padding: 0;
}

.item-action-btn:hover {
  background: var(--main-orange);
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
  background: var(--theme-bg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 详情顶部操作栏 */
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
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
  color: var(--theme-text);
}

.ds-type-tag {
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--main-orange);
  background: rgba(240, 90, 35, 0.12);
}

.ds-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.ds-status.on {
  color: #00b42a;
  background: rgba(0, 180, 42, 0.12);
}

.ds-status.off {
  color: var(--theme-text-muted);
  background: var(--theme-surface-hover);
}

.ds-shared-tag {
  font-size: 11.5px;
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--main-orange);
  background: rgba(240, 90, 35, 0.12);
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
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-surface-elevated);
  color: var(--theme-text-secondary);
  font-size: 13px;
  line-height: 1;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
  user-select: none;
}

.toolbar-btn:hover:not(:disabled) {
  border-color: var(--main-orange);
  color: var(--main-orange);
  background: var(--theme-surface-hover);
  box-shadow: 0 1px 3px rgba(240, 90, 35, 0.1);
}

.toolbar-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(240, 90, 35, 0.2);
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
  background: var(--theme-surface);
}

.detail-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--theme-text-muted);
  font-size: 13px;
}

.account-dialog-body {
  padding: 0 8px;
}

.account-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--theme-text-muted);
  line-height: 1.6;
}

.account-test-result {
  margin-top: 12px;
  text-align: center;
}

.account-test-result .test-ok {
  color: #00b42a;
  font-weight: 500;
}

.account-test-result .test-fail {
  color: #f53f3f;
  font-weight: 500;
}
</style>
