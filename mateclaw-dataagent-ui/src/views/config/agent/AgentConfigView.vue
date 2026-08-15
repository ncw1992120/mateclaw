<template>
  <div class="agent-config-page">
    <div class="agent-config-toolbar">
      <span class="agent-count">
        {{ t('agentConfig.total') }} <b>{{ agentStore.agents.length }}</b>
      </span>
      <el-button type="primary" size="small" @click="openCreateDialog">
        + {{ t('configCenter.agentNew') }}
      </el-button>
    </div>

    <div v-if="loading && agentStore.agents.length === 0" class="page-loading">
      <span>{{ t('common.loading') || '加载中…' }}</span>
    </div>

    <div v-else-if="agentStore.agents.length === 0" class="empty-state">
      <span class="empty-icon">📭</span>
      <p>{{ t('configCenter.agentEmpty') }}</p>
      <el-button type="primary" size="small" @click="openCreateDialog">
        + {{ t('configCenter.agentNew') }}
      </el-button>
    </div>

    <div v-else class="agent-grid">
      <div
        v-for="agent in agentStore.agents"
        :key="agent.id"
        class="agent-card"
        :class="{ disabled: !agent.enabled }"
      >
        <div class="card-header">
          <div class="card-icon">{{ agent.icon || '🤖' }}</div>
          <el-switch
            :model-value="agent.enabled"
            @update:model-value="(val: boolean | string | number) => handleToggle(agent, !!val)"
          />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ resolveAgentName(agent) }}</h3>
          <p class="card-desc">{{ agent.description || '—' }}</p>
        </div>
        <div class="card-footer">
          <el-button size="small" link @click="handleEdit(agent)">
            {{ t('configCenter.agentEdit') }}
          </el-button>
          <el-button size="small" link type="danger" @click="handleDelete(agent)">
            {{ t('configCenter.agentDelete') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 编辑 / 新建弹窗 -->
    <AgentFormDialog
      v-model:visible="dialogVisible"
      :edit-id="editingId"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import AgentFormDialog from '@/views/dialog/AgentFormDialog.vue'
import type { Agent } from '@/types'

const { t } = useI18n()
const agentStore = useAgentStore()

/** 当前工作区 ID：优先取已有 agent 的 workspaceId，否则用 1 */
const currentWorkspaceId = computed(() => {
  return agentStore.currentAgent?.workspaceId
    ?? agentStore.agents[0]?.workspaceId
    ?? 1
})

const loading = ref(false)

/** 新建/编辑弹窗状态 */
const dialogVisible = ref(false)
/** 正在编辑的 Agent ID（新建时为 null） */
const editingId = ref<number | string | null>(null)

/** 智能体名（兼容 name 为空或中英文名） */
function resolveAgentName(agent: Agent): string {
  return agent.nameZh || agent.nameEn || agent.name || `#${agent.id}`
}

/** 加载智能体列表 */
async function loadAgents(): Promise<void> {
  loading.value = true
  try {
    await agentStore.fetchAgents(currentWorkspaceId.value)
  } catch (err) {
    console.error('[AgentConfigView] loadAgents failed:', err)
  } finally {
    loading.value = false
  }
}

/** 启停切换 */
async function handleToggle(agent: Agent, enabled: boolean): Promise<void> {
  try {
    await agentStore.updateAgent(agent.id, { ...agent, enabled })
    ElMessage.success(enabled ? t('configCenter.agentEnable') + '成功' : t('configCenter.agentDisable') + '成功')
  } catch (err) {
    console.error('[AgentConfigView] handleToggle failed:', err)
  }
}

/** 编辑 - 弹出编辑智能体弹窗 */
function handleEdit(agent: Agent): void {
  editingId.value = agent.id
  dialogVisible.value = true
}

/** 新建 - 弹出新建智能体弹窗 */
function openCreateDialog(): void {
  editingId.value = null
  dialogVisible.value = true
}

/** 弹窗保存成功 */
function handleSaved(): void {
  // 列表由 store 自动刷新，无需额外操作
  editingId.value = null
}

/** 删除 */
async function handleDelete(agent: Agent): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('configCenter.agentDeleteConfirm', { name: resolveAgentName(agent) }),
      t('skillManage.deleteTitle'),
      { type: 'warning' },
    )
    await agentStore.deleteAgent(agent.id, agent.workspaceId)
    ElMessage.success(t('skillManage.deleteSuccess'))
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadAgents)
</script>

<style scoped>
.agent-config-page {
  padding: 16px 24px 24px;
  background: var(--theme-bg);
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* 表单全屏态：去掉 padding，让 AgentForm 自带布局占满 */
.agent-form-fullscreen {
  margin: -16px -24px -24px;
  flex: 1;
  min-height: 0;
}

.agent-config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--theme-surface);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--theme-border);
}

.agent-count {
  font-size: 13px;
  color: var(--theme-text-secondary);
}

.page-loading {
  text-align: center;
  padding: 60px 0;
  color: var(--theme-text-muted);
}

.empty-state {
  text-align: center;
  background: var(--theme-surface);
  border-radius: 8px;
  padding: 60px 16px;
  border: 1px solid var(--theme-border);
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
  color: var(--theme-text-muted);
  margin: 0 0 16px 0;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.agent-card {
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 14px;
  transition: all 0.2s;
}

.agent-card:hover {
  border-color: var(--main-orange);
  box-shadow: 0 2px 12px rgba(240, 90, 35, 0.08);
}

.agent-card.disabled {
  opacity: 0.6;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-icon {
  font-size: 28px;
}

.card-body {
  flex: 1;
  min-height: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0 0 6px 0;
}

.card-desc {
  font-size: 13px;
  color: var(--theme-text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--theme-border);
}
</style>
