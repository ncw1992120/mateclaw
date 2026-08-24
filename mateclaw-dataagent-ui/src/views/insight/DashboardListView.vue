<template>
  <div class="dashboard-list-view">
    <!-- 列表模式 -->
    <template v-if="mode === 'list'">
      <div class="list-mode" :class="{ 'with-ai-panel': showAiPanel }">
        <div class="list-header mc-toolbar">
        <h2 class="list-title mc-toolbar-title">{{ t('insight.title') }}</h2>
        <div class="list-header-actions mc-toolbar-right">
          <el-input
            v-model="searchKeyword"
            :placeholder="t('insight.searchPlaceholder')"
            :prefix-icon="Search"
            clearable
            size="default"
            class="search-input"
          />
          <el-button @click="toggleAiPanel">
            <template #icon>
              <RobotIcon style="width: 16px; height: 16px;" />
            </template>
            {{ t('insight.aiAssistant') }}
          </el-button>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="handleCreate">
            {{ t('insight.create') }}
          </el-button>
        </div>
      </div>

      <div class="list-body">
        <div v-loading="store.loading" class="list-content">
          <div v-if="filteredDashboards.length === 0 && !store.loading" class="empty-state">
            <div class="empty-icon">📊</div>
            <div class="empty-text">{{ searchKeyword ? t('insight.searchNoResult') : t('insight.listEmpty') }}</div>
            <div class="empty-actions">
              <el-button @click="toggleAiPanel">
              <template #icon>
                <RobotIcon style="width: 16px; height: 16px;" />
              </template>
              {{ t('insight.aiAssistant') }}
            </el-button>
              <el-button v-if="canCreate" type="primary" @click="handleCreate">{{ t('insight.create') }}</el-button>
            </div>
          </div>

          <div v-else class="card-grid">
            <div
              v-for="dashboard in filteredDashboards"
              :key="dashboard.id"
              class="dashboard-card"
            >
              <div class="card-header">
                <span class="card-name">{{ dashboard.name }}</span>
                <el-tag :type="dashboard.status === 'published' ? 'success' : 'info'" size="small">
                  {{ dashboard.status === 'published' ? t('insight.status.published') : t('insight.status.draft') }}
                </el-tag>
              </div>
              <div class="card-desc">{{ dashboard.description || t('insight.noDescription') }}</div>
              <div class="card-meta">
                <span class="card-owner">{{ dashboard.ownerName || '--' }}</span>
                <span class="card-time">{{ formatTime(dashboard.updateTime) }}</span>
              </div>
              <div class="card-actions">
                <el-tooltip
                  v-if="canModifyDashboard(dashboard)"
                  :content="t('insight.edit')"
                  placement="top"
                >
                  <el-button text size="small" :icon="Edit" @click="handleEdit(dashboard.id)" />
                </el-tooltip>
                <el-tooltip :content="t('insight.preview')" placement="top">
                  <el-button text size="small" :icon="View" @click="handlePreview(dashboard.id)" />
                </el-tooltip>
                <el-tooltip
                  v-if="canCreate && dashboard.status === 'draft'"
                  :content="t('insight.publish')"
                  placement="top"
                >
                  <el-button text size="small" type="success" :icon="VideoPlay" @click="handlePublish(dashboard)" />
                </el-tooltip>
                <el-tooltip v-else-if="canModifyDashboard(dashboard)" :content="t('insight.unpublish')" placement="top">
                  <el-button text size="small" :icon="VideoPause" @click="handleUnpublish(dashboard)" />
                </el-tooltip>
                <el-tooltip v-if="canCreate" :content="t('insight.copy')" placement="top">
                  <el-button text size="small" :icon="DocumentCopy" @click="handleCopy(dashboard)" />
                </el-tooltip>
                <el-tooltip v-if="canModifyDashboard(dashboard)" :content="t('insight.delete')" placement="top">
                  <el-button text size="small" type="danger" :icon="Delete" @click="handleDelete(dashboard)" />
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>

        <!-- AI助手面板 -->
        <div v-if="showAiPanel" class="list-ai-panel">
          <AiChatPanel
            @close="toggleAiPanel"
            @dashboard-updated="handleAiDashboardUpdated"
          />
        </div>
      </div>
      </div>
    </template>

    <!-- 编辑器模式 -->
    <InsightDashboardEditorView
      v-else-if="mode === 'editor'"
      :dashboard-id="currentDashboardId"
      @back="handleBackToList"
      @preview="handlePreviewFromEditor"
    />

    <!-- 预览模式 -->
    <DashboardPreviewView
      v-else-if="mode === 'preview'"
      :dashboard-id="currentDashboardId"
      @back="handleBackToList"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick, Edit, View, DocumentCopy, Delete, VideoPlay, VideoPause, Search } from '@element-plus/icons-vue'
import RobotIcon from './components/RobotIcon.vue'
import dayjs from 'dayjs'
import type { InsightDashboard } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { usePersistedState } from '@/composables/usePersistedRef'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import { useUserStore } from '@/stores/useUserStore'
import InsightDashboardEditorView from './InsightDashboardEditorView.vue'
import DashboardPreviewView from './DashboardPreviewView.vue'
import AiChatPanel from './components/AiChatPanel.vue'

defineOptions({
  name: 'DashboardListView',
})

const { t } = useI18n()
const store = useInsightDashboardStore()
const { hasPermission, canModifyResource } = usePermission()
const userStore = useUserStore()

/** 新建/复制权限：member 及以上（viewer 只读） */
const canCreate = computed(() => hasPermission(PERMISSION.INSIGHT_CREATE))

/**
 * 是否可管理该仪表盘（编辑/发布/取消发布/删除）：
 * 工作区 admin+owner 管理全部，普通成员仅限自己创建的
 */
function canModifyDashboard(dashboard: InsightDashboard): boolean {
  return canModifyResource((dashboard as InsightDashboard & { ownerId?: number | string | null }).ownerId)
}

type ViewMode = 'list' | 'editor' | 'preview'
const mode = usePersistedState<ViewMode>('mc-insight-view-mode', 'list')
const currentDashboardId = usePersistedState<string>('mc-insight-dashboard-id', '')

/** AI助手面板可见性 */
const showAiPanel = ref(false)

/** 搜索关键词 */
const searchKeyword = ref('')

/** 按关键词过滤仪表盘列表 */
const filteredDashboards = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return store.dashboards
  }
  return store.dashboards.filter((d) => {
    return d.name?.toLowerCase().includes(keyword)
      || d.description?.toLowerCase().includes(keyword)
      || d.ownerName?.toLowerCase().includes(keyword)
  })
})

onMounted(() => {
  store.fetchDashboards().catch(() => {
    ElMessage.error(t('insight.loadFailed'))
  })
  // 刷新后恢复编辑/预览模式时，需要加载对应仪表盘数据
  if (mode.value !== 'list' && currentDashboardId.value) {
    store.selectDashboard(currentDashboardId.value).catch(() => {
      // 仪表盘可能已被删除，回退到列表
      mode.value = 'list'
      currentDashboardId.value = ''
    })
  }
})

/** 格式化时间 */
function formatTime(time: string): string {
  if (!time) {
    return '--'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

/** 切换AI助手面板 */
function toggleAiPanel(): void {
  showAiPanel.value = !showAiPanel.value
}

/** AI助手生成仪表盘成功后 */
function handleAiDashboardUpdated(dashboardId: string): void {
  if (dashboardId) {
    currentDashboardId.value = dashboardId
    mode.value = 'editor'
  }
  showAiPanel.value = false
  store.fetchDashboards().catch(() => {
    // 静默失败
  })
}

/** 新建仪表盘 */
async function handleCreate(): Promise<void> {
  try {
    const created = await store.createDashboard({
      name: t('insight.defaultName'),
      description: '',
    })
    currentDashboardId.value = created.id
    mode.value = 'editor'
  } catch {
    ElMessage.error(t('insight.createFailed'))
  }
}

/** 编辑仪表盘 */
function handleEdit(id: string): void {
  currentDashboardId.value = id
  mode.value = 'editor'
}

/** 预览仪表盘 */
function handlePreview(id: string): void {
  currentDashboardId.value = id
  mode.value = 'preview'
}

/** 从编辑器进入预览 */
function handlePreviewFromEditor(dashboardId: string): void {
  currentDashboardId.value = dashboardId
  mode.value = 'preview'
}

/** 发布仪表盘 */
async function handlePublish(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.updateDashboard(dashboard.id, { status: 'published' })
    ElMessage.success(t('insight.publishSuccess'))
  } catch {
    ElMessage.error(t('insight.publishFailed'))
  }
}

/** 取消发布 */
async function handleUnpublish(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.updateDashboard(dashboard.id, { status: 'draft' })
    ElMessage.success(t('insight.unpublishSuccess'))
  } catch {
    ElMessage.error(t('insight.unpublishFailed'))
  }
}

/** 复制仪表盘 */
async function handleCopy(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.copyDashboard(dashboard.id)
    ElMessage.success(t('insight.copySuccess'))
  } catch {
    ElMessage.error(t('insight.copyFailed'))
  }
}

/** 删除仪表盘 */
async function handleDelete(dashboard: InsightDashboard): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('insight.deleteConfirm', { name: dashboard.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await store.deleteDashboard(dashboard.id)
    ElMessage.success(t('insight.deleteSuccess'))
  } catch (e) {
    // 用户取消删除时不报错
    if (e !== 'cancel') {
      ElMessage.error(t('insight.deleteFailed'))
    }
  }
}

/** 返回列表 */
function handleBackToList(): void {
  mode.value = 'list'
  currentDashboardId.value = ''
  store.fetchDashboards().catch(() => {
    // 静默失败
  })
}
</script>

<style scoped>
.dashboard-list-view {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.list-mode {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
}

.list-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  min-height: 56px;
  background: var(--db-card);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.list-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.search-input {
  width: 200px;
}

.list-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text);
}

.list-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  color: var(--db-text-muted);
}

.empty-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-lg);
}

.dashboard-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-base), border-color var(--transition-fast);
}

.dashboard-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.card-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: var(--db-text-secondary);
  min-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.card-owner {
  font-size: 12px;
  font-weight: 500;
  color: var(--db-accent);
  background: var(--db-accent-light);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.card-time {
  font-size: 12px;
  color: var(--db-text-muted);
}

.card-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-xs);
  border-top: 1px solid var(--db-border);
  padding-top: var(--space-sm);
  margin-top: var(--space-xs);
}

.list-ai-panel {
  width: 380px;
  flex-shrink: 0;
  overflow: hidden;
}
</style>
