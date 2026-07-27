<template>
  <div class="model-config-page">
    <el-tabs v-model="activeTab">
      <!-- Providers Tab -->
      <el-tab-pane :label="t('modelConfig.tabProviders')" name="providers">
        <div class="provider-list">
          <div
            v-for="provider in sortedProviders"
            :key="provider.providerId"
            class="provider-item"
            :class="{ 'provider-disabled': !provider.enabled, 'provider-unconfigured': provider.enabled && !isProviderConfigured(provider) }"
          >
            <!-- Provider Header -->
            <div class="provider-header">
              <div class="provider-title">
                <span class="provider-name">{{ provider.name }}</span>
                <span class="provider-id">{{ provider.providerId }}</span>
              </div>
              <div class="provider-status-actions">
                <el-tag
                  v-if="!provider.enabled"
                  type="info"
                  size="small"
                >
                  {{ t('modelConfig.statusDisabled') }}
                </el-tag>
                <el-tag
                  v-else-if="!isProviderConfigured(provider)"
                  type="warning"
                  size="small"
                >
                  {{ t('modelConfig.statusUnconfigured') }}
                </el-tag>
                <el-tag
                  v-else
                  type="success"
                  size="small"
                >
                  {{ t('modelConfig.statusReady') }}
                </el-tag>
                <!-- State-based actions -->
                <div class="provider-actions">
                  <!-- Disabled: only enable -->
                  <template v-if="!provider.enabled">
                    <el-button size="small" type="primary" @click="handleEnableProvider(provider.providerId)">
                      {{ t('modelConfig.enable') }}
                    </el-button>
                  </template>
                  <!-- Enabled but not configured: config form + test -->
                  <template v-else-if="!isProviderConfigured(provider)">
                    <el-button size="small" :loading="testingConnection === provider.providerId" @click="handleTestConnection(provider.providerId)">
                      {{ t('modelConfig.testConnection') }}
                    </el-button>
                    <el-button size="small" type="warning" @click="handleDisableProvider(provider.providerId)">
                      {{ t('modelConfig.disable') }}
                    </el-button>
                  </template>
                  <!-- Fully configured: full actions -->
                  <template v-else>
                    <el-button size="small" :loading="testingConnection === provider.providerId" @click="handleTestConnection(provider.providerId)">
                      {{ t('modelConfig.testConnection') }}
                    </el-button>
                    <el-button size="small" type="warning" @click="handleDisableProvider(provider.providerId)">
                      {{ t('modelConfig.disable') }}
                    </el-button>
                    <el-button
                      v-if="provider.supportModelDiscovery"
                      size="small"
                      :loading="discovering === provider.providerId"
                      @click="handleDiscoverModels(provider.providerId)"
                    >
                      {{ t('modelConfig.discover') }}
                    </el-button>
                    <el-button
                      size="small"
                      type="danger"
                      @click="handleDeleteProvider(provider)"
                    >
                      {{ t('modelConfig.delete') }}
                    </el-button>
                  </template>
                </div>
              </div>
            </div>

            <!-- Config Form: only when enabled -->
            <div v-if="provider.enabled" class="provider-config-section">
              <el-form label-width="100px" size="small">
                <el-form-item :label="t('modelConfig.baseUrl')">
                  <el-input
                    :model-value="getEditingBaseUrl(provider)"
                    :disabled="provider.freezeUrl"
                    @update:model-value="setEditingBaseUrl(provider.providerId, $event)"
                  />
                </el-form-item>
                <el-form-item :label="t('modelConfig.apiKey')">
                  <el-input
                    :model-value="getEditingApiKey(provider)"
                    type="password"
                    show-password
                    @update:model-value="setEditingApiKey(provider.providerId, $event)"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="savingConfig === provider.providerId"
                    @click="handleSaveProviderConfig(provider.providerId)"
                  >
                    {{ t('common.confirm') }}
                  </el-button>
                  <span v-if="!isProviderConfigured(provider)" class="config-hint">
                    {{ t('modelConfig.configHint') }}
                  </span>
                </el-form-item>
              </el-form>
            </div>

            <!-- Models under Provider: only when fully configured -->
            <div v-if="provider.enabled && isProviderConfigured(provider) && (provider.models?.length || provider.extraModels?.length)" class="provider-models">
              <div class="models-label">{{ t('modelConfig.providerModels') }}</div>
              <div class="model-tags">
                <div v-for="model in (provider.models || [])" :key="'m-' + model.id" class="model-mini">
                  <span class="model-mini-name">{{ model.name }}</span>
                  <el-tag v-if="model.isDefault" type="warning" size="small">{{ t('modelConfig.default') }}</el-tag>
                  <el-tag :type="model.probeOk === true ? 'success' : model.probeOk === false ? 'danger' : 'info'" size="small">
                    {{ model.probeOk === true ? t('modelConfig.probeOk') : model.probeOk === false ? t('modelConfig.probeFail') : t('modelConfig.untested') }}
                  </el-tag>
                </div>
                <div v-for="model in (provider.extraModels || [])" :key="'e-' + model.id" class="model-mini model-extra">
                  <span class="model-mini-name">{{ model.name }}</span>
                  <el-tag type="info" size="small">{{ t('modelConfig.extra') }}</el-tag>
                </div>
              </div>
            </div>

            <!-- Unconfigured hint -->
            <div v-if="provider.enabled && !isProviderConfigured(provider)" class="unconfigured-hint">
              <el-alert :title="t('modelConfig.configureFirst')" type="warning" :closable="false" show-icon />
            </div>
          </div>
        </div>

        <!-- Add Custom Provider -->
        <div class="add-provider-section">
          <el-button type="primary" size="small" @click="showAddProvider = true">
            {{ t('modelConfig.addCustomProvider') }}
          </el-button>
        </div>

        <el-dialog
          v-model="showAddProvider"
          :title="t('modelConfig.addCustomProvider')"
          width="480px"
          append-to-body
        >
          <el-form :model="newProvider" label-width="100px" size="small">
            <el-form-item :label="t('modelConfig.providerId')">
              <el-input v-model="newProvider.providerId" />
            </el-form-item>
            <el-form-item :label="t('modelConfig.providerName')">
              <el-input v-model="newProvider.name" />
            </el-form-item>
            <el-form-item :label="t('modelConfig.baseUrl')">
              <el-input v-model="newProvider.baseUrl" />
            </el-form-item>
            <el-form-item :label="t('modelConfig.apiKey')">
              <el-input v-model="newProvider.apiKey" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('modelConfig.chatModel')">
              <el-input v-model="newProvider.chatModel" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showAddProvider = false">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" @click="handleCreateProvider">{{ t('common.confirm') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Models Tab: only show models from configured providers -->
      <el-tab-pane :label="t('modelConfig.tabModels')" name="models">
        <div v-if="availableModels.length === 0" class="empty-state">
          <el-empty :description="t('modelConfig.noAvailableModels')" />
        </div>
        <div v-else class="model-list">
          <div v-for="model in availableModels" :key="model.id" class="model-item">
            <div class="model-info">
              <span class="model-name">{{ model.name }}</span>
              <span class="model-modelname">{{ model.modelName }}</span>
              <el-tag size="small">{{ getProviderName(model.provider) }}</el-tag>
              <el-tag v-if="model.isDefault" type="warning" size="small">{{ t('modelConfig.default') }}</el-tag>
              <el-tag v-if="model.modelType === 'embedding' && model.isDefault" type="success" size="small">{{ t('modelConfig.defaultEmbedding') }}</el-tag>
              <el-tag v-if="model.builtin" size="small" type="info">{{ t('modelConfig.builtin') }}</el-tag>
              <el-tag v-if="!model.enabled" type="danger" size="small">{{ t('modelConfig.statusDisabled') }}</el-tag>
            </div>
            <div class="model-actions">
              <!-- 连通性测试 -->
              <el-button
                size="small"
                :loading="testingModel === model.id"
                @click="handleTestModel(model)"
              >
                {{ t('modelConfig.testConnection') }}
              </el-button>
              <!-- 启用/禁用切换 -->
              <el-switch
                :model-value="model.enabled"
                :disabled="model.isDefault"
                size="small"
                @change="(val: boolean) => handleToggleModelEnabled(model, val)"
              />
              <!-- 对话模型：设为默认对话模型（互斥，is_default=1） -->
              <el-button
                v-if="model.modelType !== 'embedding' && !model.isDefault"
                size="small"
                type="primary"
                @click="handleSetDefault(model.id)"
              >
                {{ t('modelConfig.setDefaultChat') }}
              </el-button>
              <!-- 向量模型：设为默认向量模型（与对话模型共用 is_default 字段） -->
              <el-button
                v-if="model.modelType === 'embedding' && !model.isDefault"
                size="small"
                type="success"
                @click="handleSetDefaultEmbedding(model.id)"
              >
                {{ t('modelConfig.setDefaultEmbedding') }}
              </el-button>
              <el-button size="small" @click="openEditModel(model)">
                {{ t('modelConfig.edit') }}
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="handleDeleteModel(model.id)"
              >
                {{ t('modelConfig.delete') }}
              </el-button>
            </div>
          </div>
        </div>

        <div v-if="hasConfiguredProviders" class="add-model-section">
          <el-button type="primary" size="small" @click="openAddModel">
            {{ t('modelConfig.addModel') }}
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Add/Edit Model Sub-Dialog -->
    <el-dialog
      v-model="showEditModel"
      :title="editingModelId ? t('modelConfig.editModel') : t('modelConfig.addModel')"
      width="520px"
      append-to-body
    >
      <el-form :model="editModelData" label-width="120px" size="small">
        <el-form-item :label="t('modelConfig.modelName')">
          <el-input v-model="editModelData.name" />
        </el-form-item>
        <el-form-item :label="t('modelConfig.provider')">
          <el-select v-model="editModelData.provider" :placeholder="t('modelConfig.selectProvider')">
            <el-option
              v-for="p in configuredProviders"
              :key="p.providerId"
              :label="p.name"
              :value="p.providerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('modelConfig.realModelName')">
          <el-input v-model="editModelData.modelName" />
        </el-form-item>
        <el-form-item :label="t('agent.description')">
          <el-input v-model="editModelData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('modelConfig.temperature')">
          <el-slider v-model="editModelData.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
        <el-form-item :label="t('modelConfig.maxTokens')">
          <el-input-number v-model="editModelData.maxTokens" :min="1" :max="128000" />
        </el-form-item>
        <el-form-item :label="t('modelConfig.topP')">
          <el-slider v-model="editModelData.topP" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
        <el-form-item :label="t('modelConfig.modelType')">
          <el-select v-model="editModelData.modelType">
            <el-option label="对话 (chat)" value="chat" />
            <el-option label="向量 (embedding)" value="embedding" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('modelConfig.enableSearch')">
          <el-switch v-model="editModelData.enableSearch" />
        </el-form-item>
        <el-form-item :label="t('modelConfig.enabled')">
          <el-switch v-model="editModelData.enabled" />
        </el-form-item>
        <el-form-item :label="t('modelConfig.modalities')">
          <el-checkbox-group v-model="editModalities">
            <el-checkbox label="vision" value="vision">{{ t('modelConfig.modalityVision') }}</el-checkbox>
            <el-checkbox label="video" value="video">{{ t('modelConfig.modalityVideo') }}</el-checkbox>
            <el-checkbox label="audio" value="audio">{{ t('modelConfig.modalityAudio') }}</el-checkbox>
          </el-checkbox-group>
          <div class="form-tip">{{ t('modelConfig.modalitiesTip') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditModel = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveModel">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useModelStore } from '@/stores/useModelStore'
import * as modelApi from '@/api/model'
import type { ModelConfig, ModelProvider } from '@/types'

const { t } = useI18n()
const modelStore = useModelStore()

/** 模型配置页签本地缓存键 */
const MODEL_CONFIG_ACTIVE_TAB_KEY = 'mateclaw:model-config:active-tab'

/** 当前标签页 */
const activeTab = ref(localStorage.getItem(MODEL_CONFIG_ACTIVE_TAB_KEY) || 'providers')

watch(activeTab, tab => {
  localStorage.setItem(MODEL_CONFIG_ACTIVE_TAB_KEY, tab)
})

/** 编辑中的 Provider 配置（按 providerId 索引） */
const editingConfigs = reactive<Record<string, { baseUrl: string; apiKey: string }>>({})

/** 操作状态 */
const testingConnection = ref<string | null>(null)
const testingModel = ref<number | null>(null)
const savingConfig = ref<string | null>(null)
const discovering = ref<string | null>(null)

/** 新建自定义 Provider 表单 */
const showAddProvider = ref(false)
const newProvider = reactive({
  providerId: '',
  name: '',
  baseUrl: '',
  apiKey: '',
  chatModel: '',
})

/** 编辑模型弹窗 */
const showEditModel = ref(false)
const editingModelId = ref<number | null>(null)
const editModelData = reactive<Partial<ModelConfig>>({
  name: '',
  provider: '',
  modelName: '',
  description: '',
  temperature: 0.7,
  maxTokens: 4096,
  topP: 0.9,
  modelType: 'chat',
  enableSearch: false,
  enabled: true,
  modalities: '',
})

/** modalities 字符串与 checkbox-group 双向绑定 */
const editModalities = computed({
  get: () => {
    const raw = editModelData.modalities
    if (!raw) return []
    try {
      return JSON.parse(raw)
    } catch {
      return raw.split(',').map((s: string) => s.trim()).filter(Boolean)
    }
  },
  set: (val: string[]) => {
    editModelData.modalities = val.length > 0 ? JSON.stringify(val) : ''
  },
})

/**
 * 判断 Provider 是否已完成配置（有 apiKey 或 baseUrl）
 */
function isProviderConfigured(p: ModelProvider): boolean {
  return !!(p.apiKey || p.baseUrl)
}

/**
 * 排序后的 Provider：已启用优先，再按名称排序
 */
const sortedProviders = computed(() => {
  return [...modelStore.providers].sort((a, b) => {
    if (a.enabled !== b.enabled) return a.enabled ? -1 : 1
    return a.name.localeCompare(b.name)
  })
})

/**
 * 已配置且启用的 Provider 列表（用于模型创建时的选择）
 */
const configuredProviders = computed(() => {
  return modelStore.providers.filter(p => p.enabled && isProviderConfigured(p))
})

/**
 * 是否存在至少一个已配置的 Provider
 */
const hasConfiguredProviders = computed(() => configuredProviders.value.length > 0)

/**
 * 可用模型列表（来自已配置的 Provider，含启用和禁用）
 */
const availableModels = computed(() => {
  const configuredIds = new Set<string>(configuredProviders.value.map(p => String(p.providerId)))
  return modelStore.allModels.filter(m => configuredIds.has(String(m.provider)))
})

/**
 * 判断是否为默认向量模型
 */
function isDefaultEmbedding(modelId: number): boolean {
  return modelStore.defaultEmbeddingModel?.id === modelId
}

/**
 * 设置默认向量模型
 */
async function handleSetDefaultEmbedding(id: number): Promise<void> {
  await modelStore.setDefaultEmbeddingModelById(id)
  ElMessage.success(t('common.success'))
}

function getProviderName(providerId: string): string {
  const p = modelStore.providers.find(x => x.providerId === providerId)
  return p?.name || providerId
}

/** 获取编辑中的 baseUrl */
function getEditingBaseUrl(provider: ModelProvider): string {
  return editingConfigs[provider.providerId]?.baseUrl ?? provider.baseUrl ?? ''
}

/** 获取编辑中的 apiKey */
function getEditingApiKey(provider: ModelProvider): string {
  return editingConfigs[provider.providerId]?.apiKey ?? provider.apiKey ?? ''
}

/** 设置编辑中的 baseUrl */
function setEditingBaseUrl(providerId: string, val: string): void {
  if (!editingConfigs[providerId]) {
    editingConfigs[providerId] = { baseUrl: '', apiKey: '' }
  }
  editingConfigs[providerId].baseUrl = val
}

/** 设置编辑中的 apiKey */
function setEditingApiKey(providerId: string, val: string): void {
  if (!editingConfigs[providerId]) {
    editingConfigs[providerId] = { baseUrl: '', apiKey: '' }
  }
  editingConfigs[providerId].apiKey = val
}

/** 测试供应商连接 */
async function handleTestConnection(providerId: string): Promise<void> {
  testingConnection.value = providerId
  try {
    const result = await modelStore.testConnection(providerId)
    if (result.success) {
      const latency = result.latencyMs != null ? ` (${result.latencyMs}ms)` : ''
      ElMessage.success((result.message || t('modelConfig.connectionOk')) + latency)
    } else {
      ElMessage.error(result.message || t('modelConfig.connectionFail'))
    }
  } finally {
    testingConnection.value = null
  }
}

/** 测试单个模型连通性 */
async function handleTestModel(model: ModelConfig): Promise<void> {
  testingModel.value = model.id
  try {
    if (model.modelType === 'embedding') {
      const result = await modelStore.testEmbeddingModelAvailability(model.id)
      if (result.success) {
        const dimInfo = result.dimensions != null ? ` (${t('modelConfig.embeddingDimensions', { dim: result.dimensions })})` : ''
        ElMessage.success((result.message || t('modelConfig.connectionOk')) + dimInfo)
      } else {
        ElMessage.error(result.message || t('modelConfig.connectionFail'))
      }
    } else {
      const result = await modelStore.testModelAvailability(model.provider, model.modelName)
      if (result.success) {
        const latency = result.latencyMs != null ? ` (${result.latencyMs}ms)` : ''
        ElMessage.success((result.message || t('modelConfig.connectionOk')) + latency)
      } else {
        ElMessage.error(result.message || t('modelConfig.connectionFail'))
      }
    }
  } finally {
    testingModel.value = null
  }
}

/** 启用供应商 */
async function handleEnableProvider(providerId: string): Promise<void> {
  await modelStore.enableProvider(providerId)
  ElMessage.success(t('modelConfig.enabledSuccess'))
}

/** 禁用供应商 */
async function handleDisableProvider(providerId: string): Promise<void> {
  await ElMessageBox.confirm(t('modelConfig.disableConfirm'), t('common.confirm'))
  await modelStore.disableProvider(providerId)
  ElMessage.success(t('common.success'))
}

/** 删除供应商 */
async function handleDeleteProvider(provider: ModelProvider): Promise<void> {
  if (provider.isCustom) {
    await ElMessageBox.confirm(t('modelConfig.deleteProviderConfirm'), t('common.confirm'))
    await modelStore.deleteProvider(provider.providerId)
  } else {
    await ElMessageBox.confirm(t('modelConfig.deleteBuiltinProviderConfirm'), t('common.confirm'))
    await modelStore.disableProvider(provider.providerId)
  }
  ElMessage.success(t('common.success'))
}

/** 保存 Provider 配置 */
async function handleSaveProviderConfig(providerId: string): Promise<void> {
  const config = editingConfigs[providerId]
  if (!config) return
  savingConfig.value = providerId
  try {
    await modelStore.updateProvider(providerId, {
      baseUrl: config.baseUrl,
      apiKey: config.apiKey,
    } as Partial<ModelProvider>)
    ElMessage.success(t('modelConfig.configSaved'))
  } finally {
    savingConfig.value = null
  }
}

/** 发现远端模型 */
async function handleDiscoverModels(providerId: string): Promise<void> {
  discovering.value = providerId
  try {
    await modelStore.discoverModels(providerId)
    ElMessage.success(t('modelConfig.discoverComplete'))
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.warning(msg)
  } finally {
    discovering.value = null
  }
}

/** 创建自定义 Provider */
async function handleCreateProvider(): Promise<void> {
  if (!newProvider.providerId.trim()) {
    ElMessage.warning(t('modelConfig.providerIdRequired'))
    return
  }
  await modelStore.createCustomProvider({
    id: newProvider.providerId,
    name: newProvider.name || newProvider.providerId,
    defaultBaseUrl: newProvider.baseUrl,
    apiKeyPrefix: '',
    protocol: '',
    chatModel: newProvider.chatModel,
    requireApiKey: !!newProvider.apiKey,
    models: [],
  })
  Object.assign(newProvider, { providerId: '', name: '', baseUrl: '', apiKey: '', chatModel: '' })
  showAddProvider.value = false
  ElMessage.success(t('common.success'))
}

/** 设置默认模型 */
async function handleSetDefault(id: number): Promise<void> {
  await modelStore.setDefaultModel(id)
  ElMessage.success(t('common.success'))
}

/** 打开编辑模型 */
function openEditModel(model: ModelConfig): void {
  editingModelId.value = model.id
  Object.assign(editModelData, {
    name: model.name,
    provider: model.provider,
    modelName: model.modelName,
    description: model.description,
    temperature: model.temperature,
    maxTokens: model.maxTokens,
    topP: model.topP,
    modelType: model.modelType,
    enableSearch: model.enableSearch,
    enabled: model.enabled,
    modalities: model.modalities || '',
  })
  showEditModel.value = true
}

/** 打开新增模型 */
function openAddModel(): void {
  editingModelId.value = null
  const firstConfigured = configuredProviders.value[0]
  Object.assign(editModelData, {
    name: '',
    provider: firstConfigured?.providerId || '',
    modelName: '',
    description: '',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 0.9,
    modelType: 'chat',
    enableSearch: false,
    enabled: true,
    modalities: '',
  })
  showEditModel.value = true
}

/** 保存模型 */
async function handleSaveModel(): Promise<void> {
  if (!editModelData.provider) {
    ElMessage.warning(t('modelConfig.selectProviderRequired'))
    return
  }
  if (editingModelId.value) {
    await modelStore.updateModel(editingModelId.value, editModelData)
  } else {
    await modelStore.createModel(editModelData)
  }
  showEditModel.value = false
  ElMessage.success(t('common.success'))
}

/** 删除模型 */
async function handleDeleteModel(id: number): Promise<void> {
  await ElMessageBox.confirm(t('modelConfig.deleteModelConfirm'), t('common.confirm'))
  await modelStore.deleteModel(id)
}

/** 切换模型启用/禁用状态 */
async function handleToggleModelEnabled(model: ModelConfig, enabled: boolean): Promise<void> {
  if (!enabled && model.isDefault) {
    ElMessage.warning(t('modelConfig.cannotDisableDefault'))
    return
  }
  await modelStore.toggleModelEnabled(model.id, enabled)
  ElMessage.success(t('common.success'))
}

/** 组件挂载时加载模型配置数据 */
onMounted(async () => {
  await modelStore.fetchEnabledModels()
  await modelStore.fetchProviders()
  modelStore.fetchDefaultEmbeddingModel()
})
</script>

<style scoped>
.model-config-page {
  padding: 16px 24px 24px;
  background: var(--theme-bg);
  min-height: 100%;
}

/* Provider List */
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.provider-item {
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--theme-surface);
  transition: border-color 0.2s, background-color 0.2s;
}

.provider-item:hover {
  border-color: var(--primary);
}

.provider-item.provider-disabled {
  opacity: 0.7;
  background: var(--lighter-grey);
}

.provider-item.provider-unconfigured {
  border-left: 3px solid var(--warning);
}

/* Header */
.provider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.provider-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.provider-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--dark-text);
  white-space: nowrap;
}

.provider-id {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
}

.provider-status-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.provider-actions {
  display: flex;
  gap: 4px;
}

/* Config Section */
.provider-config-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--light-grey);
}

.config-hint {
  font-size: 11px;
  color: var(--warning);
  margin-left: 8px;
}

/* Models under Provider */
.provider-models {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--light-grey);
}

.models-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 6px;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-mini {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--lighter-grey);
  border: 1px solid var(--light-grey);
}

.model-mini.model-extra {
  opacity: 0.7;
  border-style: dashed;
}

.model-mini-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--dark-text);
}

/* Unconfigured hint */
.unconfigured-hint {
  margin-top: 8px;
}

/* Add section */
.add-provider-section {
  margin-top: 12px;
  text-align: center;
}

/* Models Tab */
.empty-state {
  padding: 20px 0;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-surface-elevated);
}

.model-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.model-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--dark-text);
}

.model-modelname {
  font-size: 11px;
  color: var(--muted);
}

.model-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.add-model-section {
  margin-top: 12px;
  text-align: center;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  line-height: 1.4;
  margin-top: 4px;
}
</style>
