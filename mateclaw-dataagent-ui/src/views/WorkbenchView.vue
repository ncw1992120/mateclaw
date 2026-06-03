<template>
  <div class="workbench">
    <!-- Header -->
    <header class="header">
      <div class="header-right">
        <!-- Model Selector -->
        <div class="model-selector">
          <span class="selector-label">{{ t('modelConfig.model') }}</span>
          <el-select
            v-model="selectedModelId"
            size="small"
            :placeholder="availableModels.length ? t('modelConfig.selectModel') : t('modelConfig.configureFirst')"
            :loading="modelStore.loading"
            :no-data-text="t('modelConfig.noAvailableModels')"
            class="model-select"
            @change="handleModelChange"
          >
            <el-option
              v-for="model in availableModels"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            >
              <span class="model-option">
                <span class="model-option-name">{{ model.name }}</span>
                <span class="model-option-provider">{{ model.provider }}</span>
                <el-tag v-if="model.isDefault" type="warning" size="small">{{ t('modelConfig.default') }}</el-tag>
              </span>
            </el-option>
          </el-select>
          <button class="btn-config" :title="t('modelConfig.title')" @click="showModelConfig = true">⚙</button>
        </div>

        <div class="header-divider"></div>

        <button class="btn-outline">{{ t('workbench.exportReport') }}</button>

        <div class="header-divider"></div>

        <button class="btn-primary" @click="showAgentConfig = true">
          {{ t('agentConfig.manageAgents') }}
        </button>

        <button
          class="btn-immersion"
          :class="{ active: immersionMode }"
          @click="toggleImmersion"
        >
          {{ t('workbench.immersionMode') }}
        </button>
      </div>
    </header>

    <!-- Main Three Column Layout -->
    <div class="main">
      <div
        class="expand-tab left-tab"
        :class="{ hidden: leftOpen }"
        @click="toggleLeft"
        title="展开左栏"
      >◀</div>
      <div
        class="expand-tab right-tab"
        :class="{ hidden: rightOpen }"
        @click="toggleRight"
        title="展开右栏"
      >▶</div>

      <!-- Left: Skill Builder -->
      <div class="col-left" :class="{ collapsed: !leftOpen }">
        <SkillBuilder @collapse="toggleLeft" />
      </div>

      <!-- Middle: Chat -->
      <div class="col-mid">
        <div class="mid-header">
          <div class="agent-selector">
            <span class="selector-label">{{ t('agent.title') }}</span>
            <el-select
              v-model="chatStore.currentAgentId"
              size="small"
              :placeholder="t('agentConfig.selectAgent')"
              :loading="agentStore.loading"
              class="agent-select"
              @change="handleAgentChange"
            >
              <el-option
                v-for="agent in enabledAgents"
                :key="agent.id"
                :label="agent.name"
                :value="agent.id"
              >
                <span class="agent-option">
                  <span class="agent-option-icon">{{ agent.icon || '🤖' }}</span>
                  <span class="agent-option-name">{{ agent.name }}</span>
                  <span class="agent-option-type">{{ agent.agentType }}</span>
                </span>
              </el-option>
            </el-select>
          </div>
          <button class="mid-action-btn" :title="t('agentConfig.configAgent')" @click="handleConfigCurrentAgent">✏️</button>
          <button class="mid-action-btn" :title="t('agentConfig.createAgent')" @click="handleCreateAgent">➕</button>
          <div class="mid-spacer"></div>
          <button class="mid-action-btn" :title="t('conversation.newChat')" @click="handleNewChat">💬</button>
          <button class="mid-collapse-btn" @click="toggleLeft" title="折叠左栏">◀</button>
          <button class="mid-collapse-btn" style="margin-left:4px" @click="toggleRight" title="折叠右栏">▶</button>
        </div>
        <ChatView class="chat-container" @open-dashboard="rightOpen = true" />
      </div>

      <!-- Right: Dashboard -->
      <div class="col-right" :class="{ collapsed: !rightOpen }">
        <DashboardPanel @collapse="toggleRight" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import SkillBuilder from '@/views/skill/SkillBuilder.vue'
import ChatView from '@/views/ChatView.vue'
import DashboardPanel from '@/views/dashboard/DashboardPanel.vue'
import ModelConfigDialog from '@/views/dialog/ModelConfigDialog.vue'
import AgentConfigDialog from '@/views/dialog/AgentConfigDialog.vue'
import type { Agent } from '@/types'

const emit = defineEmits<{
  (e: 'show-model-config'): void
  (e: 'show-agent-config', agent: Agent | null): void
}>()

const { t } = useI18n()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const modelStore = useModelStore()

/** 左侧面板是否展开 */
const leftOpen = ref(true)
/** 右侧面板是否展开 */
const rightOpen = ref(true)
/** 沉浸模式 */
const immersionMode = ref(false)

/** 模型配置弹窗 */
const showModelConfig = ref(false)
/** 智能体配置弹窗 */
const showAgentConfig = ref(false)
/** 正在编辑的 Agent */
const editingAgent = ref<Agent | null>(null)

/** 当前选中的模型 ID */
const selectedModelId = ref<number | undefined>(undefined)

/** 已启用的 Agent 列表 */
const enabledAgents = computed(() => agentStore.agents.filter(a => a.enabled))

/**
 * 可用模型列表：仅来自已启用且已配置的 Provider
 * 未配置 Provider 的模型不展示在顶部选择器中
 */
const availableModels = computed(() => {
  const configuredProviderIds = new Set(
    modelStore.providers
      .filter(p => p.enabled && (p.apiKey || p.baseUrl))
      .map(p => p.providerId)
  )
  return modelStore.enabledModels.filter(m => configuredProviderIds.has(m.provider))
})

/** 切换左侧面板 */
function toggleLeft(): void {
  leftOpen.value = !leftOpen.value
  immersionMode.value = !leftOpen.value && !rightOpen.value
}

/** 切换右侧面板 */
function toggleRight(): void {
  rightOpen.value = !rightOpen.value
  immersionMode.value = !leftOpen.value && !rightOpen.value
}

/** 切换沉浸模式 */
function toggleImmersion(): void {
  if (leftOpen.value || rightOpen.value) {
    leftOpen.value = false
    rightOpen.value = false
  } else {
    leftOpen.value = true
    rightOpen.value = true
  }
  immersionMode.value = !leftOpen.value && !rightOpen.value
}

/** 模型切换 */
async function handleModelChange(modelId: number): Promise<void> {
  await modelStore.setActiveModelById(modelId)
}

/** Agent 切换 */
async function handleAgentChange(agentId: number): Promise<void> {
  await agentStore.selectAgent(agentId)
}

/** 配置当前 Agent */
function handleConfigCurrentAgent(): void {
  if (chatStore.currentAgentId) {
    const agent = agentStore.agents.find(a => a.id === chatStore.currentAgentId)
    if (agent) {
      editingAgent.value = agent
      showAgentConfig.value = true
    }
  }
}

/** 创建新 Agent */
function handleCreateAgent(): void {
  editingAgent.value = null
  showAgentConfig.value = true
}

/** 新建对话 */
function handleNewChat(): void {
  chatStore.clearMessages()
}
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #fff;
  flex: 1;
}

.header {
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid var(--light-grey);
  background: var(--white);
  z-index: 10;
  position: relative;
  flex-shrink: 0;
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-divider {
  width: 1px;
  height: 24px;
  background: var(--light-grey);
}

.model-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.selector-label {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
}

.model-select {
  width: 180px;
}

.model-select :deep(.el-input__wrapper) {
  border-radius: 16px;
}

.model-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.model-option-name {
  font-size: 12px;
  font-weight: 600;
}

.model-option-provider {
  font-size: 10px;
  color: var(--muted);
}

.btn-config {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: var(--lighter-grey);
  font-size: 14px;
  color: var(--mid-grey);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-config:hover {
  background: var(--very-light-orange);
  color: var(--main-orange);
  border-color: var(--light-orange);
}

.btn-outline {
  height: 32px;
  border-radius: 16px;
  border: 1px solid var(--light-grey);
  background: var(--lighter-grey);
  padding: 0 14px;
  font-size: 12px;
  color: var(--body-text);
  cursor: pointer;
  font-family: inherit;
}

.btn-primary {
  height: 32px;
  border-radius: 16px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 0 14px;
  cursor: pointer;
  font-family: inherit;
}

.btn-primary:hover {
  background: var(--dark-orange);
}

.btn-immersion {
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid var(--light-grey);
  background: var(--lighter-grey);
  color: var(--mid-grey);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  font-family: inherit;
}

.btn-immersion:hover,
.btn-immersion.active {
  background: var(--main-orange);
  color: #fff;
  border-color: var(--main-orange);
}

.main {
  display: flex;
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.col-left {
  width: 330px;
  min-width: 330px;
  max-width: 330px;
  background: var(--very-light-orange);
  border-right: 1px solid var(--light-grey);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s ease, min-width 0.3s ease, max-width 0.3s ease, opacity 0.3s ease, padding 0.3s ease;
  padding: 16px;
}

.col-left.collapsed {
  width: 0;
  min-width: 0;
  max-width: 0;
  opacity: 0;
  padding: 0;
  overflow: hidden;
  border-right: none;
}

.col-mid {
  flex: 1;
  background: var(--near-white);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  position: relative;
}

.col-right {
  flex: 1;
  min-width: 400px;
  background: var(--white);
  border-left: 1px solid var(--light-grey);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s ease, min-width 0.3s ease, opacity 0.3s ease, padding 0.3s ease;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.col-right.collapsed {
  width: 0;
  min-width: 0;
  max-width: 0;
  opacity: 0;
  padding: 0;
  overflow: hidden;
  border-left: none;
}

.mid-header {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid var(--light-grey);
  flex-shrink: 0;
  gap: 6px;
}

.agent-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-select {
  width: 180px;
}

.agent-select :deep(.el-input__wrapper) {
  border-radius: 16px;
}

.agent-option {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-option-icon {
  font-size: 14px;
}

.agent-option-name {
  font-size: 12px;
  font-weight: 600;
}

.agent-option-type {
  font-size: 10px;
  color: var(--muted);
}

.mid-action-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  font-size: 14px;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.mid-action-btn:hover {
  background: var(--very-light-orange);
  color: var(--main-orange);
  border-color: var(--light-orange);
}

.mid-spacer {
  flex: 1;
}

.mid-collapse-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  font-size: 14px;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-family: inherit;
}

.chat-container {
  flex: 1;
  overflow: hidden;
}

.expand-tab {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 6;
  width: 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.3s;
  box-shadow: 2px 0 8px rgba(240, 90, 35, 0.3);
}

.expand-tab:hover {
  background: var(--dark-orange);
}

.expand-tab.left-tab {
  left: 0;
  border-radius: 0 6px 6px 0;
}

.expand-tab.right-tab {
  right: 0;
  border-radius: 6px 0 0 6px;
}

.expand-tab.hidden {
  opacity: 0;
  pointer-events: none;
}
</style>