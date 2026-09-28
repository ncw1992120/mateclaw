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
          class="agent-select"
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
          <el-icon :size="56"><Document /></el-icon>
        </div>
        <p class="empty-text">{{ t('workspaceMenu.noAgentSelected') }}</p>
      </div>

      <template v-else>
        <!-- 已启用系统提示文件 -->
        <div class="context-card prompt-card">
          <div class="card-header">
            <div class="card-title-wrap">
              <span class="card-icon" aria-hidden="true"><el-icon :size="16"><Tickets /></el-icon></span>
              <div class="card-title-text">
                <h2 class="card-title">
                  {{ t('workspaceMenu.enabledPromptFiles') }}
                  <span v-if="promptFiles.length > 0" class="count-badge">{{ promptFiles.length }}</span>
                </h2>
                <span class="card-subtitle">{{ t('workspaceMenu.enabledPromptFilesDesc') }}</span>
              </div>
            </div>
          </div>

          <div v-if="promptFiles.length === 0" class="empty-inline">
            {{ t('workspaceMenu.noEnabledPromptFiles') }}
          </div>

          <div v-else>
            <!-- 拖拽排序：仅握柄可发起拖拽（handle），松手后持久化新顺序；flex/gap 挂在拖拽容器上保证 item 间距 -->
            <VueDraggable
              v-model="promptFiles"
              class="prompt-list"
              :animation="180"
              handle=".drag-grip"
              ghost-class="prompt-ghost"
              :disabled="!canManage"
              @end="handleDragEnd"
            >
              <div v-for="(name, index) in promptFiles" :key="name" class="prompt-item">
                <span v-if="canManage" class="drag-grip" :title="t('workspaceMenu.dragToReorder')">
                  <el-icon :size="14"><Rank /></el-icon>
                </span>
                <span class="prompt-order">{{ index + 1 }}</span>
                <span class="file-chip"><el-icon :size="16"><Document /></el-icon></span>
                <span class="prompt-name" :title="name">{{ name }}</span>
                <span class="prompt-size">{{ promptFileSize(name) }}</span>
                <div v-if="canManage" class="prompt-actions">
                  <el-button size="small" link type="danger" :title="t('workspaceMenu.remove')" @click="disablePrompt(name)">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </VueDraggable>
          </div>
        </div>

        <!-- 上下文文件列表 -->
        <div class="context-card files-card">
          <div class="card-header">
            <div class="card-title-wrap">
              <span class="card-icon" aria-hidden="true"><el-icon :size="16"><FolderOpened /></el-icon></span>
              <div class="card-title-text">
                <h2 class="card-title">{{ t('workspaceMenu.contextFiles') }}</h2>
                <span class="card-subtitle">{{ t('workspaceMenu.contextFilesDesc') }}</span>
              </div>
            </div>
            <el-button v-if="canManage" type="primary" size="small" class="new-file-btn" @click="openCreateDialog">
              <el-icon :size="14" class="btn-icon"><Plus /></el-icon>
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
            >
              <span class="file-chip"><el-icon :size="18"><Document /></el-icon></span>
              <div class="file-info">
                <span class="file-name" :title="file.filename">{{ file.filename }}</span>
                <span class="file-time">{{ t('workspaceMenu.updateTime') }} {{ formatTime(file.updateTime) }}</span>
              </div>
              <span class="file-size">{{ formatFileSize(file.fileSize) }}</span>
              <div class="file-actions">
                <el-tooltip
                  :content="isPromptEnabled(file.filename) ? t('workspaceMenu.disablePrompt') : t('workspaceMenu.enableAsPrompt')"
                  placement="top"
                  :disabled="!canManage"
                >
                  <el-switch
                    :model-value="isPromptEnabled(file.filename)"
                    size="small"
                    :disabled="!canManage"
                    @change="togglePrompt(file.filename)"
                  />
                </el-tooltip>
                <el-button v-if="!canManage" size="small" link :title="t('workspaceMenu.view')" @click="handleView(file)">
                  <el-icon :size="14"><View /></el-icon>
                </el-button>
                <template v-if="canManage">
                  <el-button size="small" link :title="t('workspaceMenu.edit')" @click="handleEdit(file)">
                    <el-icon :size="14"><Edit /></el-icon>
                  </el-button>
                  <el-button size="small" link type="danger" :title="t('workspaceMenu.delete')" @click="handleDelete(file)">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </template>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>

    <!-- 查看/编辑/新建文件弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="dialogReadonly ? t('workspaceMenu.viewFile') : editingFile ? t('workspaceMenu.editFile') : t('workspaceMenu.newFile')"
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
            :disabled="!!editingFile || dialogReadonly"
            size="default"
          />
        </div>
        <div class="form-item">
          <label class="form-label">{{ t('workspaceMenu.fileContent') }}</label>
          <el-input
            v-model="editingContent"
            type="textarea"
            :rows="20"
            :disabled="dialogReadonly"
            placeholder="Markdown content..."
            class="content-textarea"
          />
        </div>
      </div>
      <template #footer>
        <el-button v-if="dialogReadonly" @click="editDialogVisible = false">{{ t('common.close') }}</el-button>
        <template v-else>
          <el-button @click="editDialogVisible = false">{{ t('workspaceMenu.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">
            {{ t('workspaceMenu.save') }}
          </el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import * as contextApi from '@/api/agent-context'
import { VueDraggable } from 'vue-draggable-plus'
import { Document, FolderOpened, Tickets, Edit, Delete, View, Plus, Rank } from '@element-plus/icons-vue'
import type { Agent, WorkspaceFile } from '@/types'

const { t } = useI18n()
const agentStore = useAgentStore()
const { hasPermission } = usePermission()

/** 是否可编辑智能体上下文（管理员/工作区 admin+owner，全局管理员自动放行），否则只读 */
const canManage = computed(() => hasPermission(PERMISSION.AGENT_MANAGE))

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
/** 弹窗是否为只读（查看详情）模式 */
const dialogReadonly = ref(false)
const editingFile = ref<WorkspaceFile | null>(null)
const editingFilename = ref('')
const editingContent = ref('')
const saving = ref(false)

/** 文件名 → 文件对象映射（提示列表查尺寸用） */
const fileMap = computed(() => new Map(files.value.map((f) => [f.filename, f])))

/** 提示列表文件大小（文件缺失时显示占位符） */
function promptFileSize(name: string): string {
  const file = fileMap.value.get(name)
  return file ? formatFileSize(file.fileSize) : '—'
}

/** 智能体名（兼容 name 为空或中英文名） */
function resolveAgentName(agent: Agent): string {
  return (agent as any).nameZh || (agent as any).nameEn || agent.name || `#${agent.id}`
}

/** 判断是否已启用为系统提示 */
function isPromptEnabled(filename: string): boolean {
  return promptFiles.value.includes(filename)
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

/** 拖拽排序结束：vue-draggable-plus 已通过 v-model 更新 promptFiles，此处仅持久化 */
function handleDragEnd(): void {
  void persistPromptFiles()
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
  dialogReadonly.value = false
  editingFile.value = null
  editingFilename.value = ''
  editingContent.value = ''
  editDialogVisible.value = true
}

/** 打开文件内容弹窗（readonly=true 时为只读查看） */
async function openFileDialog(file: WorkspaceFile, readonly: boolean): Promise<void> {
  dialogReadonly.value = readonly
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

/** 查看文件详情（只读） */
async function handleView(file: WorkspaceFile): Promise<void> {
  await openFileDialog(file, true)
}

/** 编辑文件 */
async function handleEdit(file: WorkspaceFile): Promise<void> {
  await openFileDialog(file, false)
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
    ElMessage.success(t('workspaceMenu.saveSuccess'))
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
    await ElMessageBox.confirm(t('workspaceMenu.confirmDelete') + ` (${file.filename})`, t('workspaceMenu.deleteFileTitle'), {
      type: 'warning',
    })
    await contextApi.removeFile(selectedAgentId.value, file.filename)
    ElMessage.success(t('workspaceMenu.deleteSuccess'))
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
  gap: 16px;
  width: 100%;
  height: 100%;
  /* 透明底：配置壳纸面卡片已提供工作区层级，子视图不再自带灰底与白色顶栏 */
  background: transparent;
  box-sizing: border-box;
  overflow: hidden;
}

/* 身份页头：标题/描述靠左，智能体选择器靠右，与技能配置页头同规格（17px/700 + 12px muted） */
.context-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: transparent;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  margin: 0;
}

.page-desc {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  margin: 0;
}

/* 智能体选择器：白底描边胶囊皮肤，与技能/洞察页搜索框同族 */
.agent-selector {
  flex-shrink: 0;
}

.agent-selector :deep(.el-select) {
  width: 240px;
}

.agent-selector :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: 999px;
  background: var(--theme-surface-elevated);
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--theme-border-strong);
  padding: 4px 14px;
  font-size: 13px;
  transition: box-shadow var(--transition-fast, 0.15s);
}

.agent-selector :deep(.el-select__wrapper:hover) {
  box-shadow: var(--shadow-sm), inset 0 0 0 1px color-mix(in srgb, var(--main-orange) 45%, var(--theme-border-strong));
}

.agent-selector :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent), inset 0 0 0 1px var(--main-orange);
}

/* 可滚动内容区：横向负 margin + 等值内 padding，为卡片 hover 阴影留出余量并与页头对齐 */
.context-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 8px 24px;
  margin: 0 -8px;
  background: transparent;
}

/* 卡片面：系统统一规格（白面 + 细边 + 12px 圆角 + 轻阴影），与技能配置卡片同族 */
.context-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-card);
  padding: 16px 18px;
  margin-bottom: 14px;
}

.context-card:last-child {
  margin-bottom: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

/* 标题组：左侧图标 chip + 右侧纵向标题/描述 */
.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 卡片图标：28px 圆角 chip + 着色，与系统语义胶囊同族色彩语言 */
.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex-shrink: 0;
}

/* 系统提示卡：主题橙（与 prompt-order 序号徽标呼应） */
.prompt-card .card-icon {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
}

/* 上下文文件卡：蓝色 chip（mc-tag.text 同族） */
.files-card .card-icon {
  background: var(--db-card-blue-bg);
  color: var(--db-card-blue-fg);
}

.card-title-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

/* 计数徽标跟随标题同行 */
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text);
  margin: 0;
}

.card-subtitle {
  font-size: 12px;
  color: var(--db-text-muted);
}

/* 计数徽标：淡底胶囊，与技能页分类计数同规格 */
.count-badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  flex-shrink: 0;
}

/* 新建文件：主题色胶囊主按钮，与页头操作区同皮肤（卡片内 32px 略小一号） */
.new-file-btn {
  height: 32px;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  background: var(--main-orange);
  border-color: var(--main-orange);
}

.new-file-btn:hover,
.new-file-btn:focus {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

.new-file-btn .btn-icon {
  margin-right: 4px;
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
  padding: 9px 12px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  transition: border-color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.prompt-item:hover {
  box-shadow: 0 2px 8px color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 序号：主题色淡底 + 主题色文字，与分类计数徽标同一色彩语言 */
.prompt-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
  color: var(--main-orange);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.prompt-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-size {
  font-size: 12px;
  color: var(--db-text-muted);
  flex-shrink: 0;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

/* 抵消 Element 全局 .el-button+.el-button 外边距，间距只由 gap 承担 */
.prompt-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  background: var(--db-card);
  transition: border-color var(--transition-fast, 0.15s), box-shadow var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.file-item:hover {
  box-shadow: 0 2px 8px color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 文件图标：不加底色与着色，中性次级文字色 */
.file-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--db-text-secondary);
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 更新时间：文件名下方次级信息，不与右侧尺寸/操作争位 */
.file-time {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 16px;
}

/* 尺寸：保留在行右侧，便于扫读 */
.file-size {
  font-size: 12px;
  color: var(--db-text-muted);
  flex-shrink: 0;
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 抵消 Element 全局 .el-button+.el-button 外边距，间距只由 gap 承担 */
.file-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 图标化 link 按钮：收紧内边距、svg 居中 */
.prompt-actions :deep(.el-button.is-link),
.file-actions :deep(.el-button.is-link) {
  height: 26px;
  padding: 4px 6px;
}

.prompt-actions :deep(.el-button.is-link svg),
.file-actions :deep(.el-button.is-link svg) {
  display: block;
}

/* 拖拽握柄：grip 形态提示可排序，仅握柄发起拖拽（handle 限定） */
.drag-grip {
  display: flex;
  align-items: center;
  margin-left: -4px;
  color: var(--db-text-quaternary);
  cursor: grab;
  flex-shrink: 0;
  transition: color var(--transition-fast, 0.15s);
}

.drag-grip:hover {
  color: var(--db-text-secondary);
}

.drag-grip:active {
  cursor: grabbing;
}

/* 拖拽幽灵：主题色虚线描边标示落点 */
.prompt-ghost {
  opacity: 0.6;
  border: 1px dashed var(--main-orange) !important;
  background: color-mix(in srgb, var(--main-orange) 5%, var(--db-card)) !important;
}

/* 系统提示开关：二元状态用 switch 表达（开启态随主题主色），与技能启用开关同族 */
.file-actions :deep(.el-switch) {
  height: 20px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: var(--db-text-muted);
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: var(--db-text-quaternary);
}

.empty-text {
  font-size: 14px;
  margin: 0;
}

.empty-inline {
  text-align: center;
  padding: 28px 0;
  color: var(--db-text-muted);
  font-size: 13px;
  background: var(--db-bg);
  border-radius: 8px;
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
  color: var(--db-text-secondary);
}

.content-textarea :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--db-text);
  background: var(--theme-surface);
}

</style>
