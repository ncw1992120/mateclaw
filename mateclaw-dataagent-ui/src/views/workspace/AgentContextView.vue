<template>
  <div class="agent-context-page">
    <!-- 顶部标题栏 -->
    <header class="context-topbar">
      <div class="topbar-left">
        <h1 class="page-title">{{ t('workspaceMenu.agentContext') }}</h1>
        <p class="page-desc">{{ t('workspaceMenu.agentContextDesc') }}</p>
      </div>
      <div class="agent-selector">
        <el-select
          v-model="selectedAgentId"
          :placeholder="t('workspaceMenu.selectAgent')"
          size="default"
          filterable
          @change="handleAgentChange"
        >
          <el-option
            v-for="agent in agentStore.agents"
            :key="agent.id"
            :label="resolveAgentName(agent)"
            :value="agent.id"
          />
        </el-select>
      </div>
    </header>

    <!-- 内容区 -->
    <section class="context-content" v-loading="loading">
      <!-- 未选择 Agent -->
      <div v-if="!selectedAgentId" class="empty-state">
        <div class="empty-icon">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#c9cdd4" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
        </div>
        <p class="empty-text">{{ t('workspaceMenu.noAgentSelected') }}</p>
      </div>

      <template v-else>
        <!-- 已启用系统提示文件 -->
        <div class="context-card prompt-card">
          <div class="card-header">
            <div class="card-title-wrap">
              <h2 class="card-title">{{ t('workspaceMenu.enabledPromptFiles') }}</h2>
              <span class="card-subtitle">{{ t('workspaceMenu.enabledPromptFilesDesc') }}</span>
            </div>
            <el-tag v-if="enabledPromptFiles.length > 0" type="info" size="small">{{ enabledPromptFiles.length }}</el-tag>
          </div>

          <div v-if="enabledPromptFiles.length === 0" class="empty-inline">
            {{ t('workspaceMenu.noEnabledPromptFiles') }}
          </div>

          <div v-else class="prompt-list">
            <div
              v-for="(file, index) in enabledPromptFiles"
              :key="file.filename"
              class="prompt-item"
            >
              <span class="prompt-order">{{ index + 1 }}</span>
              <span class="file-icon" v-html="getFileIcon(file.filename)"></span>
              <span class="prompt-name" :title="file.filename">{{ file.filename }}</span>
              <span class="prompt-size">{{ formatFileSize(file.fileSize) }}</span>
              <div class="prompt-actions">
                <el-button
                  size="small"
                  link
                  :disabled="index === 0"
                  @click="movePrompt(index, -1)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15"/></svg>
                </el-button>
                <el-button
                  size="small"
                  link
                  :disabled="index === enabledPromptFiles.length - 1"
                  @click="movePrompt(index, 1)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
                </el-button>
                <el-button size="small" link type="danger" @click="disablePrompt(file.filename)">
                  {{ t('workspaceMenu.remove') }}
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 上下文文件列表 -->
        <div class="context-card files-card">
          <div class="card-header">
            <div class="card-title-wrap">
              <h2 class="card-title">{{ t('workspaceMenu.contextFiles') }}</h2>
              <span class="card-subtitle">{{ t('workspaceMenu.contextFilesDesc') }}</span>
            </div>
            <el-button type="primary" size="small" @click="openCreateDialog">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              {{ t('workspaceMenu.newFile') }}
            </el-button>
          </div>

          <div v-if="files.length === 0 && !loading" class="empty-inline">
            {{ t('workspaceMenu.noFiles') }}
          </div>

          <div v-else class="file-list">
            <div
              v-for="file in files"
              :key="file.filename"
              class="file-item"
              :class="{ 'is-prompt': isPromptEnabled(file.filename) }"
            >
              <div class="file-main">
                <span class="file-icon" v-html="getFileIcon(file.filename)"></span>
                <div class="file-info">
                  <div class="file-name-row">
                    <span class="file-name" :title="file.filename">{{ file.filename }}</span>
                    <el-tag v-if="isPromptEnabled(file.filename)" type="success" size="small" effect="light">
                      {{ t('workspaceMenu.enabledAsPrompt') }}
                    </el-tag>
                  </div>
                  <div class="file-meta">
                    <span>{{ formatFileSize(file.fileSize) }}</span>
                    <span class="meta-sep">·</span>
                    <span>{{ t('workspaceMenu.updateTime') }} {{ formatTime(file.updateTime) }}</span>
                  </div>
                </div>
              </div>
              <div class="file-actions">
                <el-tooltip :content="isPromptEnabled(file.filename) ? t('workspaceMenu.disablePrompt') : t('workspaceMenu.enableAsPrompt')" placement="top">
                  <el-button
                    size="small"
                    :type="isPromptEnabled(file.filename) ? 'success' : 'default'"
                    plain
                    @click="togglePrompt(file.filename)"
                  >
                    {{ isPromptEnabled(file.filename) ? t('workspaceMenu.promptOn') : t('workspaceMenu.promptOff') }}
                  </el-button>
                </el-tooltip>
                <el-button size="small" link @click="handleEdit(file)">{{ t('workspaceMenu.edit') }}</el-button>
                <el-button size="small" link type="danger" @click="handleDelete(file)">{{ t('workspaceMenu.delete') }}</el-button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>

    <!-- 编辑/新建文件弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="editingFile ? t('workspaceMenu.editFile') : t('workspaceMenu.newFile')"
      width="760px"
      :close-on-click-modal="false"
      class="file-edit-dialog"
    >
      <div class="edit-form">
        <div class="form-item">
          <label class="form-label">{{ t('workspaceMenu.fileName') }}</label>
          <el-input
            v-model="editingFilename"
            :placeholder="t('workspaceMenu.newFileName')"
            :disabled="!!editingFile"
            size="default"
          />
        </div>
        <div class="form-item">
          <label class="form-label">{{ t('workspaceMenu.fileContent') }}</label>
          <el-input
            v-model="editingContent"
            type="textarea"
            :rows="20"
            placeholder="Markdown content..."
            class="content-textarea"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('workspaceMenu.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('workspaceMenu.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import * as contextApi from '@/api/agent-context'
import type { Agent, WorkspaceFile } from '@/types'

const { t } = useI18n()
const agentStore = useAgentStore()

/** 当前选中的 Agent ID */
const selectedAgentId = ref<number | string>('')

/** 文件列表 */
const files = ref<WorkspaceFile[]>([])

/** 启用的系统提示文件名列表（有序） */
const promptFiles = ref<string[]>([])

/** 加载状态 */
const loading = ref(false)

/** 编辑弹窗 */
const editDialogVisible = ref(false)
const editingFile = ref<WorkspaceFile | null>(null)
const editingFilename = ref('')
const editingContent = ref('')
const saving = ref(false)

/** 已启用系统提示的完整文件对象（用于排序展示） */
const enabledPromptFiles = computed<WorkspaceFile[]>(() => {
  const fileMap = new Map(files.value.map((f) => [f.filename, f]))
  return promptFiles.value
    .map((name) => fileMap.get(name))
    .filter((f): f is WorkspaceFile => f !== undefined)
})

/** 智能体名（兼容 name 为空或中英文名） */
function resolveAgentName(agent: Agent): string {
  return (agent as any).nameZh || (agent as any).nameEn || agent.name || `#${agent.id}`
}

/** 判断是否已启用为系统提示 */
function isPromptEnabled(filename: string): boolean {
  return promptFiles.value.includes(filename)
}

/** 获取文件图标 */
function getFileIcon(filename: string): string {
  const lower = filename.toLowerCase()
  if (lower.endsWith('.md')) {
    return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#f05a23" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`
  }
  return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#86909c" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`
}

/** 格式化文件大小 */
function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 格式化时间 */
function formatTime(time: string): string {
  if (!time) return '—'
  return time.replace('T', ' ').substring(0, 19)
}

/** 加载智能体列表 */
async function loadAgents(): Promise<void> {
  try {
    await agentStore.fetchAgents(1)
  } catch (err) {
    console.error('[AgentContextView] loadAgents failed:', err)
  }
}

/** 加载文件列表和系统提示文件 */
async function loadFiles(): Promise<void> {
  if (!selectedAgentId.value) {
    files.value = []
    promptFiles.value = []
    return
  }
  loading.value = true
  try {
    const [fileList, promptList] = await Promise.all([
      contextApi.listFiles(selectedAgentId.value),
      contextApi.getPromptFiles(selectedAgentId.value),
    ])
    files.value = (fileList as unknown as WorkspaceFile[]) || []
    promptFiles.value = (promptList as unknown as string[]) || []
  } catch (err) {
    console.error('[AgentContextView] loadFiles failed:', err)
  } finally {
    loading.value = false
  }
}

/** Agent 切换 */
function handleAgentChange(): void {
  loadFiles()
}

/** 切换系统提示启用状态 */
async function togglePrompt(filename: string): Promise<void> {
  if (!selectedAgentId.value) return
  const next = isPromptEnabled(filename)
    ? promptFiles.value.filter((f) => f !== filename)
    : [...promptFiles.value, filename]
  promptFiles.value = next
  await persistPromptFiles()
}

/** 从系统提示区移除 */
async function disablePrompt(filename: string): Promise<void> {
  promptFiles.value = promptFiles.value.filter((f) => f !== filename)
  await persistPromptFiles()
}

/** 移动系统提示文件顺序 */
async function movePrompt(index: number, direction: number): Promise<void> {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= promptFiles.value.length) return
  const list = [...promptFiles.value]
  const [moved] = list.splice(index, 1)
  list.splice(newIndex, 0, moved)
  promptFiles.value = list
  await persistPromptFiles()
}

/** 持久化系统提示文件列表 */
async function persistPromptFiles(): Promise<void> {
  if (!selectedAgentId.value) return
  try {
    await contextApi.setPromptFiles(selectedAgentId.value, promptFiles.value)
  } catch (err) {
    console.error('[AgentContextView] setPromptFiles failed:', err)
  }
}

/** 打开新建弹窗 */
function openCreateDialog(): void {
  editingFile.value = null
  editingFilename.value = ''
  editingContent.value = ''
  editDialogVisible.value = true
}

/** 编辑文件 */
async function handleEdit(file: WorkspaceFile): Promise<void> {
  editingFile.value = file
  editingFilename.value = file.filename
  try {
    const result = await contextApi.getFile(selectedAgentId.value, file.filename)
    const fullFile = result as unknown as WorkspaceFile
    editingContent.value = fullFile.content || ''
  } catch (err) {
    console.error('[AgentContextView] getFile failed:', err)
    editingContent.value = ''
  }
  editDialogVisible.value = true
}

/** 保存文件 */
async function handleSave(): Promise<void> {
  if (!editingFilename.value.trim()) {
    ElMessage.warning(t('workspaceMenu.newFileName'))
    return
  }
  saving.value = true
  try {
    await contextApi.saveFile(selectedAgentId.value, editingFilename.value.trim(), editingContent.value)
    ElMessage.success(t('workspaceMenu.save') + ' OK')
    editDialogVisible.value = false
    await loadFiles()
  } catch (err) {
    console.error('[AgentContextView] saveFile failed:', err)
  } finally {
    saving.value = false
  }
}

/** 删除文件 */
async function handleDelete(file: WorkspaceFile): Promise<void> {
  try {
    await ElMessageBox.confirm(t('workspaceMenu.confirmDelete') + ` (${file.filename})`, 'Delete', {
      type: 'warning',
    })
    await contextApi.removeFile(selectedAgentId.value, file.filename)
    ElMessage.success('Deleted')
    await loadFiles()
  } catch (err) {
    if (err !== 'cancel') {
      console.error('[AgentContextView] deleteFile failed:', err)
    }
  }
}

onMounted(async () => {
  await loadAgents()
  if (agentStore.agents.length > 0) {
    selectedAgentId.value = agentStore.agents[0].id
    await loadFiles()
  }
})
</script>

<style scoped>
.agent-context-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f5f6f8;
  overflow: hidden;
}

.context-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
  margin: 0;
}

.page-desc {
  font-size: 13px;
  color: #86909c;
  margin: 0;
}

.agent-selector {
  min-width: 260px;
}

.context-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 24px;
  background: #f5f6f8;
}

.context-card {
  background: #fff;
  border-radius: 8px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid #e8ecf2;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.card-subtitle {
  font-size: 12px;
  color: #86909c;
}

/* 已启用系统提示文件 */
.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.prompt-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #f7f8fa;
  border: 1px solid #e8ecf2;
  border-radius: 6px;
  transition: background 0.2s;
}

.prompt-item:hover {
  background: #f2f3f5;
}

.prompt-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f05a23;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.prompt-name {
  flex: 1;
  font-size: 13px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-size {
  font-size: 12px;
  color: #86909c;
  flex-shrink: 0;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

/* 文件列表 */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #e8ecf2;
  border-radius: 8px;
  transition: all 0.2s;
}

.file-item:hover {
  border-color: #f05a23;
  box-shadow: 0 2px 8px rgba(240, 90, 35, 0.08);
}

.file-item.is-prompt {
  background: rgba(240, 90, 35, 0.04);
  border-color: rgba(240, 90, 35, 0.2);
}

.file-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.file-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #86909c;
}

.meta-sep {
  color: #c9cdd4;
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #86909c;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  margin: 0;
}

.empty-inline {
  text-align: center;
  padding: 30px 0;
  color: #86909c;
  font-size: 13px;
  background: #f7f8fa;
  border-radius: 6px;
}

/* 编辑弹窗 */
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
}

.content-textarea :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
