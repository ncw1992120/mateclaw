<template>
  <div class="dashboard-list-view">
    <!-- 列表模式 -->
    <template v-if="mode === 'list'">
      <div class="list-mode" :class="{ 'with-ai-panel': showAiPanel }">
        <div class="list-header">
        <h2 class="list-title">{{ t('insight.title') }}</h2>
        <div class="list-header-actions">
          <el-button :icon="MagicStick" @click="toggleAiPanel">{{ t('insight.aiAssistant') }}</el-button>
          <el-button type="primary" :icon="Plus" @click="handleCreate">
            {{ t('insight.create') }}
          </el-button>
        </div>
      </div>

      <div class="list-body">
        <div v-loading="store.loading" class="list-content">
          <div v-if="store.dashboards.length === 0 && !store.loading" class="empty-state">
            <div class="empty-icon">📊</div>
            <div class="empty-text">{{ t('insight.listEmpty') }}</div>
            <div class="empty-actions">
              <el-button :icon="MagicStick" @click="toggleAiPanel">{{ t('insight.aiAssistant') }}</el-button>
              <el-button type="primary" @click="handleCreate">{{ t('insight.create') }}</el-button>
            </div>
          </div>

          <div v-else class="card-grid">
            <div
              v-for="dashboard in store.dashboards"
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
                <el-button text size="small" @click="handleEdit(dashboard.id)">{{ t('insight.edit') }}</el-button>
                <el-button text size="small" @click="handlePreview(dashboard.id)">{{ t('insight.preview') }}</el-button>
                <el-button
                  v-if="dashboard.status === 'draft'"
                  text
                  size="small"
                  type="success"
                  @click="handlePublish(dashboard)"
                >
                  {{ t('insight.publish') }}
                </el-button>
                <el-button
                  v-else
                  text
                  size="small"
                  @click="handleUnpublish(dashboard)"
                >
                  {{ t('insight.unpublish') }}
                </el-button>
                <el-button text size="small" type="danger" @click="handleDelete(dashboard)">{{ t('insight.delete') }}</el-button>
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
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import type { InsightDashboard } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { usePersistedState } from '@/composables/usePersistedRef'
import InsightDashboardEditorView from './InsightDashboardEditorView.vue'
import DashboardPreviewView from './DashboardPreviewView.vue'
import AiChatPanel from './components/AiChatPanel.vue'

defineOptions({
  name: 'DashboardListView',
})

const { t } = useI18n()
const store = useInsightDashboardStore()

type ViewMode = 'list' | 'editor' | 'preview'
const mode = usePersistedState<ViewMode>('mc-insight-view-mode', 'list')
const currentDashboardId = usePersistedState<string>('mc-insight-dashboard-id', '')

/** AI助手面板可见性 */
const showAiPanel = ref(false)

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
  background: var(--theme-bg);
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
  padding: 16px 24px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.list-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text);
}

.list-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--theme-text-muted);
}

.empty-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  gap: 16px;
}

.dashboard-card {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.15s ease;
}

.dashboard-card:hover {
  border-color: var(--main-orange);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: var(--theme-text-secondary);
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
  gap: 12px;
}

.card-owner {
  font-size: 12px;
  color: var(--main-orange);
  background: rgba(255, 152, 0, 0.08);
  padding: 1px 6px;
  border-radius: 3px;
}

.card-time {
  font-size: 12px;
  color: var(--theme-text-muted);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  border-top: 1px solid var(--theme-border);
  padding-top: 8px;
  margin-top: 4px;
}

.list-ai-panel {
  width: 380px;
  flex-shrink: 0;
  overflow: hidden;
}
</style>
