<template>
  <div class="workbench">
    <!-- Header -->
    <header class="header">
      <div class="header-right">
        <!-- Agent Selector -->
        <div class="agent-selector-header">
          <span class="selector-label">{{ t('agent.title') }}</span>
          <el-select
            v-model="chatStore.currentAgentId"
            size="small"
            :placeholder="t('agentConfig.selectAgent')"
            :loading="agentStore.loading"
            class="agent-select-header"
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

        <div class="header-divider"></div>

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
        </div>
      </div>
    </header>

    <!-- Main: Chat Only -->
    <div class="main">
      <div class="col-mid chat-only">
        <ChatView class="chat-container" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import ChatView from '@/views/ChatView.vue'

const { t } = useI18n()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const modelStore = useModelStore()

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

/** 模型切换 */
async function handleModelChange(modelId: number): Promise<void> {
  await modelStore.setActiveModelById(modelId)
}

/** Agent 切换 */
async function handleAgentChange(agentId: number): Promise<void> {
  await agentStore.selectAgent(agentId)
}

/**
 * 初始化默认选择：
 * 1. 如果当前未选中 Agent，自动选中第一个启用的 Agent
 * 2. 如果当前未选中模型，自动选中标记为"默认"的模型
 */
function initDefaultSelection(): void {
  // Agent：自动选中第一个
  if (!chatStore.currentAgentId && enabledAgents.value.length > 0) {
    const firstAgent = enabledAgents.value[0]
    chatStore.setAgent(firstAgent.id)
    // 静默选中（不触发 API 调用）
    if (firstAgent.id) {
      chatStore.currentAgentId = firstAgent.id
    }
  }

  // Model：自动选中默认模型
  if (!selectedModelId.value && availableModels.value.length > 0) {
    const defaultModel = availableModels.value.find(m => m.isDefault)
      || availableModels.value[0]
    selectedModelId.value = defaultModel.id
  }
}

/** 监听 agents 加载完成，自动初始化选择 */
watch(() => enabledAgents.value.length, (len) => {
  if (len > 0) {
    initDefaultSelection()
  }
}, { immediate: true })

/** 监听 models 加载完成，自动初始化模型选择 */
watch(() => availableModels.value.length, (len) => {
  if (len > 0) {
    const defaultModel = availableModels.value.find(m => m.isDefault)
      || availableModels.value[0]
    if (!selectedModelId.value) {
      selectedModelId.value = defaultModel.id
    }
  }
}, { immediate: true })
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

.agent-selector-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-select-header {
  width: 160px;
}

.agent-select-header :deep(.el-input__wrapper) {
  border-radius: 16px;
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

.main {
  display: flex;
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
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

.chat-container {
  flex: 1;
  overflow: hidden;
}
</style>