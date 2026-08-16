<template>
  <div v-if="visible" class="import-overlay" @click.self="close">
    <div class="import-modal">
      <div class="import-header">
        <h2>{{ t('skillImport.title') }}</h2>
        <button class="modal-close" :disabled="installing" @click="close">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- 三个 Tab 切换 -->
      <div class="import-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="import-tab"
          :class="{ active: activeTab === tab.value }"
          :disabled="installing"
          @click="activeTab = tab.value"
        >
          <span class="import-tab-icon">{{ tab.icon }}</span>
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <div class="import-body">
        <!-- ========== URL Tab ========== -->
        <div v-if="activeTab === 'url'" class="import-section">
          <p class="import-hint">{{ t('skillImport.urlHint') }}</p>
          <div class="form-group">
            <label class="form-label">{{ t('skillImport.urlField') }} *</label>
            <input
              v-model="urlForm.bundleUrl"
              class="form-input"
              :placeholder="t('skillImport.urlPlaceholder')"
              :disabled="installing"
            />
            <p class="form-tip">{{ t('skillImport.urlTip') }}</p>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('skillImport.versionField') }}</label>
            <input
              v-model="urlForm.version"
              class="form-input"
              :placeholder="t('skillImport.versionPlaceholder')"
              :disabled="installing"
            />
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('skillImport.targetNameField') }}</label>
            <input
              v-model="urlForm.targetName"
              class="form-input"
              :placeholder="t('skillImport.targetNamePlaceholder')"
              :disabled="installing"
            />
          </div>
          <div class="form-row form-row-inline">
            <label class="form-checkbox">
              <input v-model="urlForm.enable" type="checkbox" :disabled="installing" />
              <span>{{ t('skillImport.enableAfterInstall') }}</span>
            </label>
            <label class="form-checkbox">
              <input v-model="urlForm.overwrite" type="checkbox" :disabled="installing" />
              <span>{{ t('skillImport.overwriteIfExists') }}</span>
            </label>
          </div>
        </div>

        <!-- ========== Market Tab ========== -->
        <div v-else-if="activeTab === 'market'" class="import-section">
          <p class="import-hint">{{ t('skillImport.marketHint') }}</p>
          <div class="market-search">
            <input
              v-model="marketQuery"
              class="form-input"
              :placeholder="t('skillImport.marketSearchPlaceholder')"
              :disabled="installing || marketLoading"
              @keyup.enter="runMarketSearch"
            />
            <button class="btn-secondary" :disabled="marketLoading || installing" @click="runMarketSearch">
              {{ t('skillImport.search') }}
            </button>
          </div>

          <div v-if="marketLoading" class="market-loading">{{ t('skillImport.searching') }}</div>
          <div v-else-if="marketError" class="market-error">{{ marketError }}</div>
          <div v-else-if="marketResults.length === 0" class="market-empty">
            {{ t('skillImport.marketEmpty') }}
          </div>
          <div v-else class="market-list">
            <div
              v-for="item in marketResults"
              :key="item.slug"
              class="market-item"
              :class="{ installing: installingSlug === item.slug }"
            >
              <div class="market-item-icon">
                {{ item.icon || '📦' }}
              </div>
              <div class="market-item-meta">
                <div class="market-item-name">
                  {{ item.name }}
                  <span v-if="item.version" class="market-item-version">v{{ item.version }}</span>
                </div>
                <div class="market-item-desc" :title="item.description">
                  {{ item.description || t('skillImport.noDescription') }}
                </div>
                <div class="market-item-extra">
                  <span v-if="item.author">{{ t('skillImport.author', { name: item.author }) }}</span>
                  <span v-if="item.downloads != null" class="market-item-downloads">
                    ⬇ {{ item.downloads }}
                  </span>
                </div>
              </div>
              <button
                class="btn-primary"
                :disabled="installing"
                @click="installFromMarket(item)"
              >
                {{ installingSlug === item.slug ? t('skillImport.installing') : t('skillImport.install') }}
              </button>
            </div>
          </div>
        </div>

        <!-- ========== ZIP Tab ========== -->
        <div v-else class="import-section">
          <p class="import-hint">{{ t('skillImport.zipHint') }}</p>
          <div
            class="dropzone"
            :class="{ active: zipDragOver, disabled: installing }"
            @click="triggerZipPicker"
            @dragover.prevent="zipDragOver = true"
            @dragleave="zipDragOver = false"
            @drop.prevent="onZipDropped"
          >
            <input
              ref="zipInputRef"
              type="file"
              accept=".zip,application/zip"
              style="display:none"
              :disabled="installing"
              @change="onZipPicked"
            />
            <div v-if="!zipFile" class="dropzone-empty">
              <div class="dropzone-icon">📦</div>
              <div class="dropzone-text">{{ t('skillImport.zipDrop') }}</div>
              <div class="dropzone-hint">{{ t('skillImport.zipDropHint') }}</div>
            </div>
            <div v-else class="dropzone-filled">
              <div class="dropzone-icon">📦</div>
              <div class="dropzone-filename">{{ zipFile.name }}</div>
              <div class="dropzone-filesize">{{ formatSize(zipFile.size) }}</div>
              <button class="btn-link" :disabled="installing" @click.stop="clearZip">
                {{ t('skillImport.zipReplace') }}
              </button>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ t('skillImport.targetNameField') }}</label>
            <input
              v-model="zipForm.targetName"
              class="form-input"
              :placeholder="t('skillImport.targetNamePlaceholder')"
              :disabled="installing"
            />
          </div>
          <div class="form-row form-row-inline">
            <label class="form-checkbox">
              <input v-model="zipForm.enable" type="checkbox" :disabled="installing" />
              <span>{{ t('skillImport.enableAfterInstall') }}</span>
            </label>
            <label class="form-checkbox">
              <input v-model="zipForm.overwrite" type="checkbox" :disabled="installing" />
              <span>{{ t('skillImport.overwriteIfExists') }}</span>
            </label>
          </div>
        </div>

        <!-- 安装进度（URL / Market 异步安装时显示） -->
        <div v-if="installProgress" class="install-progress">
          <div class="install-progress-text">
            <span class="install-progress-status">{{ statusLabel(installProgress.status) }}</span>
            <span v-if="installProgress.error" class="install-progress-error">
              {{ installProgress.error }}
            </span>
          </div>
          <el-progress
            v-if="installProgress.status === 'INSTALLING' || installProgress.status === 'PENDING'"
            :percentage="installProgressPercent"
            :indeterminate="installProgressPercent < 100"
            :show-text="false"
            :stroke-width="6"
          />
        </div>
      </div>

      <div class="import-footer">
        <button class="btn-secondary" :disabled="installing" @click="close">
          {{ t('common.cancel') }}
        </button>
        <button
          v-if="activeTab === 'url'"
          class="btn-primary"
          :disabled="!canSubmitUrl || installing"
          @click="installFromUrl"
        >
          {{ installing ? t('skillImport.installing') : t('skillImport.install') }}
        </button>
        <button
          v-else-if="activeTab === 'zip'"
          class="btn-primary"
          :disabled="!zipFile || installing"
          @click="installFromZip"
        >
          {{ installing ? t('skillImport.installing') : t('skillImport.install') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  cancelInstall,
  getInstallStatus,
  installFromZip as installFromZipApi,
  searchHub as searchHubApi,
  startInstall,
  uninstallByName,
} from '@/api/skill'
import type { HubSkillInfo, SkillInstallRequest, SkillInstallTask } from '@/types'

const { t } = useI18n()

interface Props {
  visible: boolean
  workspaceId: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'installed', skillName: string): void
  (e: 'removed', skillName: string): void
}>()

type TabKey = 'url' | 'market' | 'zip'
const tabs: { value: TabKey; label: string; icon: string }[] = [
  { value: 'url', label: t('skillImport.tabUrl'), icon: '🔗' },
  { value: 'market', label: t('skillImport.tabMarket'), icon: '🏬' },
  { value: 'zip', label: t('skillImport.tabZip'), icon: '📦' },
]

const activeTab = ref<TabKey>('url')

// ====== URL Tab ======
const urlForm = reactive<SkillInstallRequest>({
  bundleUrl: '',
  version: '',
  targetName: '',
  enable: true,
  overwrite: false,
})
const canSubmitUrl = computed(() => urlForm.bundleUrl.trim().length > 0)

// ====== Market Tab ======
const marketQuery = ref('')
const marketResults = ref<HubSkillInfo[]>([])
const marketLoading = ref(false)
const marketError = ref('')
const installingSlug = ref<string | null>(null)

async function runMarketSearch(): Promise<void> {
  marketLoading.value = true
  marketError.value = ''
  try {
    const data = await searchHubApi(marketQuery.value.trim(), 20)
    marketResults.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('[SkillImport] search hub failed:', err)
    marketResults.value = []
    marketError.value = t('skillImport.searchFail')
  } finally {
    marketLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'market' && marketResults.value.length === 0 && !marketLoading.value) {
    runMarketSearch()
  }
})

// ====== ZIP Tab ======
const zipFile = ref<File | null>(null)
const zipDragOver = ref(false)
const zipInputRef = ref<HTMLInputElement | null>(null)
const zipForm = reactive({
  targetName: '',
  enable: true,
  overwrite: false,
})

function triggerZipPicker(): void {
  if (installing.value) return
  zipInputRef.value?.click()
}

function onZipPicked(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) acceptZip(file)
  target.value = ''
}

function onZipDropped(event: DragEvent): void {
  zipDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) acceptZip(file)
}

function acceptZip(file: File): void {
  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.warning(t('skillImport.zipInvalid'))
    return
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning(t('skillImport.zipTooLarge'))
    return
  }
  zipFile.value = file
}

function clearZip(): void {
  zipFile.value = null
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

// ====== 安装状态机 ======
const installing = ref(false)
const installProgress = ref<SkillInstallTask | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const installProgressPercent = computed(() => {
  const st = installProgress.value?.status
  if (st === 'COMPLETED') return 100
  if (st === 'FAILED' || st === 'CANCELLED') return 0
  return 60
})

function statusLabel(status: SkillInstallTask['status']): string {
  switch (status) {
    case 'PENDING': return t('skillImport.statusPending')
    case 'INSTALLING': return t('skillImport.statusInstalling')
    case 'COMPLETED': return t('skillImport.statusCompleted')
    case 'FAILED': return t('skillImport.statusFailed')
    case 'CANCELLED': return t('skillImport.statusCancelled')
    default: return status
  }
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollUntilDone(taskId: string): Promise<SkillInstallTask> {
  stopPolling()
  return new Promise((resolve) => {
    const tick = async (): Promise<void> => {
      try {
        const task = await getInstallStatus(taskId)
        installProgress.value = task
        const st = task.status
        if (st === 'COMPLETED' || st === 'FAILED' || st === 'CANCELLED') {
          stopPolling()
          resolve(task)
        }
      } catch (err) {
        console.error('[SkillImport] poll failed:', err)
        stopPolling()
        resolve(installProgress.value ?? { taskId, bundleUrl: '', status: 'FAILED', error: 'poll failed' } as SkillInstallTask)
      }
    }
    tick()
    pollTimer = setInterval(tick, 1500)
  })
}

async function installFromUrl(): Promise<void> {
  if (!canSubmitUrl.value) return
  installing.value = true
  installProgress.value = null
  try {
    const task = await startInstall({
      bundleUrl: urlForm.bundleUrl.trim(),
      version: urlForm.version?.trim() || undefined,
      targetName: urlForm.targetName?.trim() || undefined,
      enable: urlForm.enable,
      overwrite: urlForm.overwrite,
      workspaceId: props.workspaceId,
    })
    installProgress.value = task
    const final = await pollUntilDone(task.taskId)
    if (final.status === 'COMPLETED') {
      ElMessage.success(t('skillImport.installSuccess', { name: final.result?.name ?? '' }))
      emit('installed', final.result?.name ?? '')
      close()
    } else if (final.status === 'CANCELLED') {
      ElMessage.info(t('skillImport.cancelled'))
    } else {
      ElMessage.error(final.error || t('skillImport.installFailed'))
    }
  } catch (err) {
    console.error('[SkillImport] URL install failed:', err)
    // 拦截器已弹错误，这里只重置 installing
  } finally {
    installing.value = false
  }
}

async function installFromMarket(item: HubSkillInfo): Promise<void> {
  const url = item.bundleUrl
  if (!url) {
    ElMessage.warning(t('skillImport.noBundleUrl'))
    return
  }
  installingSlug.value = item.slug
  installing.value = true
  installProgress.value = null
  try {
    const task = await startInstall({
      bundleUrl: url,
      enable: true,
      overwrite: false,
      workspaceId: props.workspaceId,
    })
    installProgress.value = task
    const final = await pollUntilDone(task.taskId)
    if (final.status === 'COMPLETED') {
      ElMessage.success(t('skillImport.installSuccess', { name: final.result?.name ?? item.name }))
      emit('installed', final.result?.name ?? item.name)
      close()
    } else if (final.status === 'CANCELLED') {
      ElMessage.info(t('skillImport.cancelled'))
    } else {
      ElMessage.error(final.error || t('skillImport.installFailed'))
    }
  } catch (err) {
    console.error('[SkillImport] market install failed:', err)
  } finally {
    installingSlug.value = null
    installing.value = false
  }
}

async function installFromZip(): Promise<void> {
  if (!zipFile.value) return
  installing.value = true
  installProgress.value = null
  try {
    const result = await installFromZipApi(zipFile.value, {
      enable: zipForm.enable,
      overwrite: zipForm.overwrite,
      targetName: zipForm.targetName?.trim() || undefined,
      workspaceId: props.workspaceId,
    })
    const name = (result?.name as string) || zipForm.targetName || ''
    ElMessage.success(t('skillImport.installSuccess', { name }))
    emit('installed', name)
    close()
  } catch (err) {
    console.error('[SkillImport] zip install failed:', err)
  } finally {
    installing.value = false
  }
}

/** 取消当前安装任务（暴露给父组件的卸载按钮也可复用） */
async function cancelCurrent(): Promise<void> {
  if (installProgress.value && (installProgress.value.status === 'INSTALLING' || installProgress.value.status === 'PENDING')) {
    try {
      await cancelInstall(installProgress.value.taskId)
    } catch (err) {
      console.error('[SkillImport] cancel failed:', err)
    }
  }
}

/** 暴露卸载方法给父组件 */
async function removeByName(name: string): Promise<void> {
  try {
    await uninstallByName(name, props.workspaceId)
    ElMessage.success(t('skillImport.uninstallSuccess', { name }))
    emit('removed', name)
  } catch (err) {
    console.error('[SkillImport] uninstall failed:', err)
  }
}

defineExpose({ removeByName, cancelCurrent })

function resetForms(): void {
  urlForm.bundleUrl = ''
  urlForm.version = ''
  urlForm.targetName = ''
  urlForm.enable = true
  urlForm.overwrite = false
  marketQuery.value = ''
  marketResults.value = []
  marketError.value = ''
  zipFile.value = null
  zipForm.targetName = ''
  zipForm.enable = true
  zipForm.overwrite = false
  installProgress.value = null
  installingSlug.value = null
}

function close(): void {
  if (installing.value) return
  stopPolling()
  emit('update:visible', false)
  // 关闭后清空状态，方便下次打开
  setTimeout(resetForms, 200)
}

watch(() => props.visible, (v) => {
  if (v) {
    activeTab.value = 'url'
    resetForms()
  }
})
</script>

<style scoped>
.import-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 220;
  padding: 20px;
}

.import-modal {
  background: var(--white, #fff);
  border: 1px solid var(--light-grey, #ebeef0);
  border-radius: 14px;
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.import-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--light-grey, #ebeef0);
}

.import-header h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--dark-text, #1f2933);
  margin: 0;
}

.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--muted, #8a94a6);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.modal-close:hover:not(:disabled) {
  background: var(--lighter-grey, #f5f6f8);
  color: var(--dark-text, #1f2933);
}

.import-tabs {
  display: flex;
  gap: 6px;
  padding: 10px 20px 0;
  border-bottom: 1px solid var(--light-grey, #ebeef0);
}

.import-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border: none;
  background: transparent;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  color: var(--muted, #8a94a6);
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
  margin-bottom: -1px;
  transition: all 0.15s;
}

.import-tab:hover:not(:disabled) {
  color: var(--main-orange, #4176E6);
}

.import-tab.active {
  color: var(--main-orange, #4176E6);
  border-bottom-color: var(--main-orange, #4176E6);
}

.import-tab:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.import-tab-icon {
  font-size: 13px;
}

.import-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.import-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.import-hint {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--muted, #8a94a6);
  line-height: 1.5;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--body-text, #3e4756);
}

.form-input {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--light-grey, #ebeef0);
  border-radius: 8px;
  font-size: 13px;
  color: var(--dark-text, #1f2933);
  outline: none;
  font-family: inherit;
  background: var(--white, #fff);
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--main-orange, #4176E6);
  box-shadow: 0 0 0 3px rgba(65, 118, 230, 0.1);
}

.form-input:disabled {
  background: var(--lighter-grey, #f5f6f8);
  color: var(--muted, #8a94a6);
  cursor: not-allowed;
}

.form-tip {
  margin: 0;
  font-size: 11px;
  color: var(--muted, #8a94a6);
}

.form-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.form-row-inline {
  flex-wrap: wrap;
  padding-top: 2px;
}

.form-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--body-text, #3e4756);
  cursor: pointer;
  user-select: none;
}

.form-checkbox input {
  margin: 0;
  cursor: pointer;
}

/* Market tab */
.market-search {
  display: flex;
  gap: 8px;
}

.market-search .form-input {
  flex: 1;
}

.market-loading,
.market-empty,
.market-error {
  padding: 24px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--muted, #8a94a6);
}

.market-error {
  color: #f53f3f;
}

.market-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.market-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--light-grey, #ebeef0);
  border-radius: 10px;
  background: var(--white, #fff);
  transition: all 0.15s;
}

.market-item:hover {
  border-color: var(--main-orange, #4176E6);
}

.market-item.installing {
  opacity: 0.6;
}

.market-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--lighter-grey, #f5f6f8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.market-item-meta {
  flex: 1;
  min-width: 0;
}

.market-item-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--dark-text, #1f2933);
}

.market-item-version {
  margin-left: 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted, #8a94a6);
}

.market-item-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--body-text, #3e4756);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.market-item-extra {
  margin-top: 2px;
  font-size: 11px;
  color: var(--muted, #8a94a6);
  display: flex;
  gap: 10px;
}

/* Dropzone */
.dropzone {
  border: 1px dashed var(--light-grey, #ebeef0);
  border-radius: 10px;
  padding: 24px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--white, #fff);
}

.dropzone:hover,
.dropzone.active {
  border-color: var(--main-orange, #4176E6);
  background: rgba(65, 118, 230, 0.04);
}

.dropzone.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.dropzone-icon {
  font-size: 32px;
  margin-bottom: 6px;
}

.dropzone-text {
  font-size: 13px;
  color: var(--dark-text, #1f2933);
  font-weight: 600;
}

.dropzone-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted, #8a94a6);
}

.dropzone-filled .dropzone-filename {
  font-size: 13px;
  font-weight: 600;
  color: var(--dark-text, #1f2933);
  word-break: break-all;
}

.dropzone-filled .dropzone-filesize {
  margin-top: 2px;
  font-size: 11px;
  color: var(--muted, #8a94a6);
}

.btn-link {
  margin-top: 6px;
  background: transparent;
  border: none;
  color: var(--main-orange, #4176E6);
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.btn-link:hover:not(:disabled) {
  text-decoration: underline;
}

/* Install progress */
.install-progress {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--light-grey, #ebeef0);
  border-radius: 8px;
  background: var(--lighter-grey, #f5f6f8);
}

.install-progress-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 6px;
  color: var(--body-text, #3e4756);
}

.install-progress-status {
  font-weight: 600;
  color: var(--dark-text, #1f2933);
}

.install-progress-error {
  color: #f53f3f;
  font-size: 11px;
}

/* Footer */
.import-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--light-grey, #ebeef0);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: var(--main-orange, #4176E6);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}

.btn-primary:hover:not(:disabled) {
  background: var(--dark-orange, #d24a1c);
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--white, #fff);
  color: var(--body-text, #3e4756);
  border: 1px solid var(--light-grey, #ebeef0);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--main-orange, #4176E6);
  color: var(--main-orange, #4176E6);
}

.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
