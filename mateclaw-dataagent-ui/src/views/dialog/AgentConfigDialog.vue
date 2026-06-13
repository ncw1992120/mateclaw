<template>
  <el-dialog
    :model-value="visible"
    :title="t('agentConfig.settings')"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <!-- 工具栏 -->
    <div class="toolbar">
      <span class="toolbar-count">
        {{ t('agentConfig.total') }} <b>{{ agentStore.agents.length }}</b>
      </span>
      <el-button type="primary" size="small" @click="openCreateDialog">
        + {{ t('agent.create') }}
      </el-button>
    </div>

    <!-- 智能体清单 -->
    <div class="agent-list-container">
      <!-- 空状态 -->
      <div v-if="agentStore.agents.length === 0" class="list-empty">
        <span class="empty-icon">📭</span>
        <p>{{ t('agentConfig.noAgents') }}</p>
        <el-button type="primary" size="small" @click="openCreateDialog">{{ t('agent.create') }}</el-button>
      </div>

      <!-- 列表 -->
      <template v-else>
        <!-- 表头 -->
        <div class="list-header-row">
          <span class="header-col">{{ t('agentConfig.colName') || '名称' }}</span>
          <span class="header-col center">{{ t('agentConfig.colRole') || '模式' }}</span>
          <span class="header-col center">{{ t('agentConfig.colMode') || '启停' }}</span>
          <span class="header-col"></span>
        </div>

        <div
          v-for="agent in agentStore.agents"
          :key="agent.id"
          class="agent-row"
        >
          <div class="row-cell name-cell">
            <div class="row-info">
              <span class="row-name">{{ agent.name }}</span>
              <span class="row-desc">{{ agent.description || '-' }}</span>
            </div>
          </div>
          <div class="row-cell mode-cell">
            <el-tag size="small" :type="agent.enabled ? 'success' : 'info'" effect="light" round>
              {{ agent.agentType }}
            </el-tag>
          </div>
          <div class="row-cell status-cell">
            <el-switch
              :model-value="agent.enabled"
              size="small"
              @change="(val) => handleToggleEnable(agent, val)"
            />
          </div>
          <div class="row-cell action-cell">
            <button class="action-btn edit-btn" @click="openEditDialog(agent)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              {{ t('agent.edit') }}
            </button>
            <button class="action-btn delete-btn" @click="handleDelete(agent)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
              {{ t('agent.delete') }}
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- 编辑 / 新建弹窗 -->
    <AgentDetailDialog
      v-model:visible="detailVisible"
      :mode="detailMode"
      :initial-data="editingAgentData"
      @saved="onDetailSaved"
    />

    <template #footer>
      <span></span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import type { Agent } from '@/types'
import AgentDetailDialog from './AgentDetailDialog.vue'

const { t } = useI18n()
const agentStore = useAgentStore()

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

/** 详情弹窗是否可见 */
const detailVisible = ref(false)
/** 详情弹窗模式 */
const detailMode = ref<'create' | 'edit'>('create')
/** 正在编辑的 Agent 原始数据 */
const editingAgentData = ref<Partial<Agent>>({})

/** 打开新建弹窗 */
function openCreateDialog(): void {
  detailMode.value = 'create'
  editingAgentData.value = {}
  detailVisible.value = true
}

/** 打开编辑弹窗 */
function openEditDialog(agent: Agent): void {
  detailMode.value = 'edit'
  editingAgentData.value = { ...agent }
  detailVisible.value = true
}

/** 详情弹窗保存成功回调 */
function onDetailSaved(): void {
  // 列表由 store 自动刷新，无需额外操作
}

/** 删除指定 Agent */
async function handleDelete(agent: Agent): Promise<void> {
  try {
    await ElMessageBox.confirm(t('agent.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await agentStore.deleteAgent(agent.id)
    ElMessage.success(t('agent.deleteSuccess'))
  } catch {
    ElMessage.error(t('common.error'))
  }
}

/** 切换启用/禁用 */
async function handleToggleEnable(agent: Agent, val: boolean): Promise<void> {
  try {
    await agentStore.updateAgent(agent.id, { ...agent, enabled: val })
  } catch {
    ElMessage.error(t('common.error'))
  }
}
</script>

<style scoped>
/* ====== 工具栏 ====== */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-count {
  font-size: 13px;
  color: #86909c;
}

.toolbar-count b {
  color: var(--main-orange);
  font-weight: 700;
}

/* ====== 列表容器 ====== */
.agent-list-container {
  max-height: 480px;
  overflow-y: auto;
}

/* 表头行 */
.list-header-row {
  display: grid;
  grid-template-columns: 1fr 100px 70px 130px;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid #e5e6eb;
  background: #f7f8fa;
  border-radius: 6px 6px 0 0;
  margin-bottom: 4px;
}

.header-col {
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.header-col.center {
  text-align: center;
}

/* 空状态 */
.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  color: #c9cdd4;
  gap: 10px;
}

.empty-icon {
  font-size: 36px;
  opacity: 0.5;
}

.list-empty p {
  font-size: 13px;
  margin: 0;
}

/* ====== 每一行 ====== */
.agent-row {
  display: grid;
  grid-template-columns: 1fr 100px 70px 130px;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  margin-bottom: 8px;
  background: #fff;
  transition: all 0.2s ease;
}

.agent-row:last-child {
  margin-bottom: 0;
}

.agent-row:hover {
  border-color: #d0d5dd;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}

/* 各单元格 */
.row-cell {
  min-height: 0;
}

.name-cell {
  min-width: 0;
}

.mode-cell {
  text-align: center;
}

.status-cell {
  text-align: center;
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.agent-row:hover .action-cell {
  opacity: 1;
}

.row-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.row-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-desc {
  font-size: 11px;
  color: #86909c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

/* 操作按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1.4;
}

.action-btn:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
}

.edit-btn:hover {
  background: rgba(240, 90, 35, 0.04);
}

.delete-btn:hover {
  border-color: #f53f3f;
  color: #f53f3f;
  background: rgba(245, 63, 63, 0.04);
}
</style>
