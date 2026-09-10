<template>
  <div class="model-config-page">
    <!-- 身份页头：标题 + 可用统计 + 描述 + 主操作胶囊（随 Tab 切换创建入口） -->
    <div class="mc-header">
      <div class="mc-title-block">
        <h3 class="mc-name">{{ t('configCenter.tabModel') }}</h3>
        <p class="mc-desc">{{ t('modelConfig.desc') }}</p>
      </div>
      <div class="header-actions">
        <button
          type="button"
          class="btn-create-pill"
          :disabled="activeTab === 'models' && !hasConfiguredProviders"
          @click="handleHeaderCreate"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ activeTab === 'providers' ? t('modelConfig.addCustomProvider') : t('modelConfig.addModel') }}
        </button>
      </div>
    </div>

    <!-- 内容卡：Tab 头纳入卡头，控件与其控制的面板体共面 -->
    <div class="mc-panel">
      <div class="mc-tabs" role="tablist">
        <button type="button" class="tab-btn" :class="{ active: activeTab === 'providers' }" @click="activeTab = 'providers'">
          {{ t('modelConfig.tabProviders') }}
          <span class="tab-count">{{ modelStore.providers.length }}</span>
        </button>
        <button type="button" class="tab-btn" :class="{ active: activeTab === 'models' }" @click="activeTab = 'models'">
          {{ t('modelConfig.tabModels') }}
          <span class="tab-count">{{ availableModels.length }}</span>
        </button>
      </div>

      <!-- 供应商面板体 -->
      <div v-if="activeTab === 'providers'" class="mc-panel-body">
      <div v-if="sortedProviders.length === 0" class="mc-empty">
        <div class="mc-empty-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 3v4M12 17v4M3 12h4M17 12h4"/></svg>
        </div>
        <p>{{ t('modelConfig.noProviders') }}</p>
      </div>
      <template v-else>
        <template v-for="group in providerGroups" :key="group.key">
          <section class="mc-group">
            <div class="mc-section-head">
              <span class="section-dot" :class="{ on: group.enabled }" aria-hidden="true"></span>
              {{ group.enabled ? t('modelConfig.statusReady') : t('modelConfig.statusDisabled') }}
              <span class="section-count">{{ group.items.length }}</span>
            </div>
            <div :class="group.enabled ? 'provider-list' : 'provider-disabled-grid'">
          <div
            v-for="provider in group.items"
            :key="provider.providerId"
            class="provider-item"
            :class="{ 'provider-disabled': !provider.enabled }"
          >
            <!-- Provider Header -->
            <div class="provider-header">
              <div class="provider-title">
                <span v-if="provider.enabled" class="provider-icon" aria-hidden="true">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 2v6M15 2v6M6 8h12v3a6 6 0 0 1-6 6 6 6 0 0 1-6-6V8Z"/><path d="M12 17v5"/></svg>
                </span>
                <span class="provider-title-text">
                  <span class="provider-name" :title="provider.name">{{ provider.name }}</span>
                  <span class="provider-id" :title="provider.providerId">{{ provider.providerId }}</span>
                </span>
              </div>
              <div class="provider-status-actions">
                <el-tag
                  v-if="provider.enabled && !isProviderConfigured(provider)"
                  type="warning"
                  size="small"
                >
                  {{ t('modelConfig.statusUnconfigured') }}
                </el-tag>
                <el-tag
                  v-else-if="provider.enabled"
                  type="success"
                  size="small"
                >
                  {{ t('modelConfig.statusReady') }}
                </el-tag>
                <!-- State-based actions：家族轻量文字按钮，每个动作配对应线型图标；确认/取消由编辑态控制（确认已从内容区移出） -->
                <div class="provider-actions">
                  <!-- Editing state: confirm / cancel -->
                  <template v-if="editingProviderId === provider.providerId">
                    <el-button size="small" class="action-save" :icon="Check" :loading="savingConfig === provider.providerId" @click="handleSaveProviderConfig(provider.providerId)">
                      {{ t('common.confirm') }}
                    </el-button>
                    <el-button size="small" text :icon="Close" @click="cancelEditProvider(provider.providerId)">
                      {{ t('common.cancel') }}
                    </el-button>
                  </template>
                  <!-- 仅启用态动作（启用/禁用状态由卡头开关控制） -->
                  <template v-if="provider.enabled">
                    <el-button size="small" class="action-capsule" :icon="Lightning" :loading="testingConnection === provider.providerId" @click="handleTestConnection(provider.providerId)">
                      {{ t('modelConfig.testConnection') }}
                    </el-button>
                    <el-button
                      v-if="provider.supportModelDiscovery"
                      size="small"
                      class="action-capsule"
                      :icon="Search"
                      :loading="discovering === provider.providerId"
                      @click="handleDiscoverModels(provider.providerId)"
                    >
                      {{ t('modelConfig.discover') }}
                    </el-button>
                    <el-button v-if="editingProviderId !== provider.providerId" size="small" class="action-capsule" :icon="EditPen" @click="startEditProvider(provider)">
                      {{ t('modelConfig.edit') }}
                    </el-button>
                    <el-button
                      size="small"
                      text
                      type="danger"
                      :icon="Delete"
                      @click="handleDeleteProvider(provider)"
                    >
                      {{ t('modelConfig.delete') }}
                    </el-button>
                  </template>
                </div>
                <!-- 启用/禁用状态开关：卡头最右（技能卡家族先例）；禁用为破坏性操作，确认后受控翻转 -->
                <el-switch
                  :model-value="provider.enabled"
                  size="small"
                  :loading="togglingProvider === provider.providerId"
                  @change="(val: boolean) => handleToggleProviderEnabled(provider, val)"
                />
              </div>
            </div>

            <!-- Config Form: only when enabled；默认只读，点击卡头「编辑」后可写，确认/取消在卡头动作区 -->
            <div v-if="provider.enabled" class="provider-config-section">
              <el-form label-width="100px" size="small">
                <el-form-item :label="t('modelConfig.baseUrl')">
                  <el-input
                    :model-value="getEditingBaseUrl(provider)"
                    :disabled="provider.freezeUrl || editingProviderId !== provider.providerId"
                    @update:model-value="setEditingBaseUrl(provider.providerId, $event)"
                  />
                </el-form-item>
                <el-form-item :label="t('modelConfig.apiKey')">
                  <el-input
                    :model-value="getEditingApiKey(provider)"
                    type="password"
                    show-password
                    :disabled="editingProviderId !== provider.providerId"
                    @update:model-value="setEditingApiKey(provider.providerId, $event)"
                  />
                </el-form-item>
              </el-form>
              <div v-if="!isProviderConfigured(provider)" class="config-hint">
                {{ t('modelConfig.configHint') }}
              </div>
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
          </section>
        </template>
      </template>
      </div>

      <!-- 模型面板体：按类型分组（对话/向量/重排），分组头与供应商面板同族 -->
      <div v-else class="mc-panel-body">
        <div v-if="availableModels.length === 0" class="mc-empty">
          <div class="mc-empty-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 9h6M9 13h6M9 17h3"/></svg>
          </div>
          <p>{{ t('modelConfig.noAvailableModels') }}</p>
        </div>
        <template v-else>
          <section v-for="group in modelGroups" :key="group.key" class="mc-group">
            <div class="mc-section-head">
              <span class="section-dot" :class="group.dot" aria-hidden="true"></span>
              {{ t(group.labelKey) }}
              <span class="section-count">{{ group.items.length }}</span>
            </div>
            <div class="model-list">
              <div v-for="model in group.items" :key="model.id" class="model-item" :class="'model-dot-' + (model.modelType || 'chat')">
            <div class="model-top">
              <div class="model-id">
                <span class="model-icon" aria-hidden="true">
                  <svg v-if="model.modelType === 'chat'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                  <svg v-else-if="model.modelType === 'embedding'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 2v20M2 12h20"/></svg>
                  <svg v-else width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
                </span>
                <span class="model-title-text">
                  <span class="model-name">{{ model.name }}</span>
                  <span class="model-modelname" :title="model.modelName">{{ model.modelName }}</span>
                </span>
              </div>
              <div class="model-actions">
              <div class="model-action-btns">
                <!-- 连通性测试 -->
                <el-button
                  size="small"
                  class="action-capsule"
                  :icon="Lightning"
                  :loading="testingModel === model.id"
                  @click="handleTestModel(model)"
                >
                  {{ t('modelConfig.testConnection') }}
                </el-button>
                <el-button size="small" class="action-capsule" :icon="EditPen" @click="openEditModel(model)">
                  {{ t('modelConfig.edit') }}
                </el-button>
                <el-button
                  size="small"
                  class="action-capsule action-delete"
                  :icon="Delete"
                  @click="handleDeleteModel(model.id)"
                >
                  {{ t('modelConfig.delete') }}
                </el-button>
              </div>
              <!-- 启用/禁用切换，固定最右 -->
              <el-switch
                :model-value="model.enabled"
                :disabled="model.isDefault"
                size="small"
                @change="(val: boolean) => handleToggleModelEnabled(model, val)"
              />
              </div>
            </div>
            <div class="model-meta">
              <el-tag size="small">{{ getProviderName(model.provider) }}</el-tag>
              <!-- 默认标签按类型互斥展示一个，避免「默认 + 默认向量模型」双标签重复 -->
              <el-tag v-if="model.isDefault && model.modelType === 'embedding'" type="success" size="small">{{ t('modelConfig.defaultEmbedding') }}</el-tag>
              <el-tag v-else-if="model.isDefault && model.modelType === 'rerank'" type="success" size="small">{{ t('modelConfig.defaultRerank') }}</el-tag>
              <el-tag v-else-if="model.isDefault" type="warning" size="small">{{ t('modelConfig.default') }}</el-tag>
              <el-tag v-if="model.builtin" size="small" type="info">{{ t('modelConfig.builtin') }}</el-tag>
            </div>
            </div>
            </div>
          </section>
        </template>
      </div>
    </div>

      <!-- 添加自定义供应商弹窗 -->
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
            <el-option label="重排 (rerank)" value="rerank" />
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
import { Check, Close, Delete, EditPen, Lightning, Search } from '@element-plus/icons-vue'
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
const togglingProvider = ref<string | null>(null)

/** 当前处于编辑态的供应商（默认不可编辑，点卡头「编辑」进入，确认/取消退出） */
const editingProviderId = ref<string | null>(null)

/** 进入编辑态：用当前值初始化缓存，避免读到陈旧缓存 */
function startEditProvider(provider: ModelProvider): void {
  if (!editingConfigs[provider.providerId]) {
    editingConfigs[provider.providerId] = {
      baseUrl: provider.baseUrl ?? '',
      apiKey: provider.apiKey ?? '',
    }
  }
  editingProviderId.value = provider.providerId
}

/** 取消编辑：丢弃未保存的缓存并退出编辑态 */
function cancelEditProvider(providerId: string): void {
  delete editingConfigs[providerId]
  editingProviderId.value = null
}

/** 页头主操作：按当前 Tab 打开对应创建入口 */
function handleHeaderCreate(): void {
  if (activeTab.value === 'providers') {
    showAddProvider.value = true
  } else {
    openAddModel()
  }
}

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
 * 分组后的 Provider：可用（已启用）整宽卡片组 + 未启用紧凑网格组，
 * 空组不产出，全空时由 mc-empty 兜底
 */
const providerGroups = computed(() => {
  const enabled = sortedProviders.value.filter(p => p.enabled)
  const disabled = sortedProviders.value.filter(p => !p.enabled)
  const groups: { key: string; enabled: boolean; items: ModelProvider[] }[] = []
  if (enabled.length) groups.push({ key: 'enabled', enabled: true, items: enabled })
  if (disabled.length) groups.push({ key: 'disabled', enabled: false, items: disabled })
  return groups
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
 * 模型类型分组（与供应商分组头同族语言）：固定顺序 chat → embedding → rerank，
 * 圆点用家族 tint 编码类型；未知类型（如 model3d）入「其他」兜底组，
 * 保证渲染总数与 Tab 计数一致，空组不产出
 */
interface ModelGroup {
  key: string
  labelKey: string
  dot: string
  items: ModelConfig[]
}

const modelGroups = computed<ModelGroup[]>(() => {
  const defs: { key: string; labelKey: string; dot: string }[] = [
    { key: 'chat', labelKey: 'modelConfig.groupChat', dot: 'blue' },
    { key: 'embedding', labelKey: 'modelConfig.groupEmbedding', dot: 'violet' },
    { key: 'rerank', labelKey: 'modelConfig.groupRerank', dot: 'orange' },
  ]
  const groups: ModelGroup[] = defs
    .map(d => ({ ...d, items: availableModels.value.filter(m => (m.modelType || 'chat') === d.key) }))
    .filter(g => g.items.length > 0)
  const known = new Set(defs.map(d => d.key))
  const rest = availableModels.value.filter(m => !known.has(m.modelType || 'chat'))
  if (rest.length) groups.push({ key: 'other', labelKey: 'modelConfig.groupOther', dot: '', items: rest })
  return groups
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

/**
 * 设置默认 Rerank 模型
 */
async function handleSetDefaultRerank(id: number): Promise<void> {
  await modelStore.setDefaultRerankModelById(id)
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
    } else if (model.modelType === 'rerank') {
      const result = await modelStore.testRerankModelAvailability(model.id)
      if (result.success) {
        ElMessage.success(result.message || t('modelConfig.connectionOk'))
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

/**
 * 切换供应商启用/禁用（卡头开关）：禁用为破坏性操作需确认，
 * 取消时受控 model-value 不翻转，开关视觉保持原状
 */
async function handleToggleProviderEnabled(provider: ModelProvider, enabled: boolean): Promise<void> {
  if (!enabled) {
    try {
      await ElMessageBox.confirm(t('modelConfig.disableConfirm'), t('common.confirm'))
    } catch {
      return
    }
  }
  togglingProvider.value = provider.providerId
  try {
    if (enabled) {
      await modelStore.enableProvider(provider.providerId)
      ElMessage.success(t('modelConfig.enabledSuccess'))
    } else {
      await modelStore.disableProvider(provider.providerId)
      ElMessage.success(t('common.success'))
    }
  } finally {
    togglingProvider.value = null
  }
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

/** 保存 Provider 配置（确认后退出编辑态并清理缓存） */
async function handleSaveProviderConfig(providerId: string): Promise<void> {
  const config = editingConfigs[providerId]
  if (!config) return
  savingConfig.value = providerId
  try {
    await modelStore.updateProvider(providerId, {
      baseUrl: config.baseUrl,
      apiKey: config.apiKey,
    } as Partial<ModelProvider>)
    delete editingConfigs[providerId]
    editingProviderId.value = null
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
  modelStore.fetchDefaultRerankModel()
})
</script>

<style scoped>
.model-config-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100%;
  /* 透明底：配置壳纸面卡片已提供层级，子视图不再自带灰底 */
  background: transparent;
}

/* ===== 身份页头（与数据配置/业务词典同族） ===== */
.mc-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 2px 2px 0;
}

.mc-title-block {
  min-width: 0;
}

.mc-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text);
}

.mc-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-left: auto;
  flex-shrink: 0;
}

/* 主操作：家族胶囊按钮（主题色实心 + 白字） */
.btn-create-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: filter 0.15s;
}

.btn-create-pill:hover {
  filter: brightness(1.06);
}

.btn-create-pill:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 纸面 Tab 条：下划线分段控件（与知识库工作区 Tab 同族） =====
   无卡片包裹：Tab 文字与内容卡左缘同为 x=0 对齐 */
.mc-tabs {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 10px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-muted);
  transition: color 0.15s;
  position: relative;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 下划线家族：hover 只提字色，不加底色填充（填充是 pill 分段的语言） */
.tab-btn:hover:not(.active) {
  color: var(--db-text-secondary);
}

.tab-btn.active {
  color: var(--main-orange);
  font-weight: 600;
}

/* Tab 计数徽标：默认半透明灰 chip，激活项上主题色淡底（对齐报告页 tab-count） */
.tab-count {
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  border-radius: 999px;
  padding: 0 7px;
  min-width: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  transition: color 0.15s, background 0.15s;
}

.tab-btn.active .tab-count {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 下划线与按钮内容同宽（padding 为 0，即全按钮宽） */
.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--main-orange);
  border-radius: 2px 2px 0 0;
}

/* 内容列：透明布局列，回到页面纸面；条目卡为唯一卡片层，
   避免「页面纸面 > 包裹卡 > 条目卡」三层嵌套面的双边框与阴影叠阴影 */
.mc-panel {
  display: flex;
  flex-direction: column;
  margin-top: 14px;
  flex: 1;
  min-height: 0;
}

/* 面板体：条目卡直接落在页面纸面上；组间 24px，顶部 16px 与 Tab 下划线拉开 */
.mc-panel-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-top: 16px;
}

/* 分组：头 + 内容同组，头与内容间距 10px（头归属于下方内容） */
.mc-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 空态：家族线型 SVG chip + 安静文案 */
.mc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 44px 20px;
  color: var(--db-text-muted);
}

.mc-empty-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.mc-empty p {
  margin: 0;
  font-size: 13px;
}

/* Provider List */
/* 分组头：与页头统计同族（圆点 + 文字 + 计数 chip）；垂直节奏由 .mc-group/.mc-panel-body 承担 */
.mc-section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-secondary);
}

.section-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--db-text-muted);
}

.section-dot.on {
  background: var(--el-color-success);
}

/* 模型类型圆点：家族 tint 编码（与技能卡图标 tint 同源） */
.section-dot.blue {
  background: var(--db-card-blue-fg);
}

.section-dot.violet {
  background: var(--db-card-violet-fg);
}

.section-dot.orange {
  background: var(--db-card-orange-fg);
}

.section-count {
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  padding: 0 7px;
  min-width: 16px;
  text-align: center;
  border-radius: 999px;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  font-variant-numeric: tabular-nums;
}

/* 可用供应商：整宽卡片纵列（表单内含两列字段） */
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 未启用供应商：紧凑网格，34 行压缩为约 12 行 */
.provider-disabled-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 8px;
}

/* 卡头标题截断：窄视口下名称不溢出覆盖右侧状态/操作（flex:1 让 title 吸收压缩，actions 保持不缩） */
.provider-item .provider-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.provider-item .provider-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.provider-item {
  border: 1px solid var(--db-border);
  border-radius: 12px;
  padding: 14px 16px;
  background: var(--db-card);
  box-shadow: var(--shadow-card);
  transition: opacity 0.15s;
}

/* 未启用行：压缩高度、去阴影安静沉底；暗化只作用于文本，
   开关（启用入口）保持全对比可交互（父级 opacity 会连同子控件一起暗掉，故不用整卡 opacity） */
.provider-item.provider-disabled {
  padding: 9px 16px;
  box-shadow: none;
}

.provider-disabled .provider-title {
  opacity: 0.72;
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
  gap: 10px;
  min-width: 0;
}

/* 上下结构：名称主行、id 次行，各自截断 */
.provider-title-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

/* 家族 tint 图标 chip：供应商卡与技能/智能体/数据源卡同一语言；紧凑格不显示 */
.provider-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--db-card-blue-bg);
  color: var(--db-card-blue-fg);
}

.provider-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  white-space: nowrap;
}

.provider-id {
  font-size: 11px;
  color: var(--db-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.provider-status-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.provider-actions {
  display: flex;
  gap: 4px;
}

/* 抵消 EP 默认相邻按钮 12px 左 margin：按钮间距只由容器 gap 承担 */
.provider-actions :deep(.el-button + .el-button),
.model-action-btns :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 安静文字按钮（禁用/取消/删除等次级动作）：图标与文字间距由 EP 内置 margin 承担 */
.provider-actions :deep(.el-button.is-text) {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  font-size: 12px;
}

/* 主操作描边小胶囊（与数据配置 toolbar-btn 同族语言）：测试/发现/编辑/启用 */
.provider-actions :deep(.el-button.action-capsule) {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  font-size: 12px;
}

.provider-actions :deep(.el-button.action-capsule:hover),
.provider-actions :deep(.el-button.action-capsule:focus) {
  border-color: color-mix(in srgb, var(--main-orange) 45%, var(--db-border));
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

/* EP 图标统一 13px（对齐旧线型 SVG 尺寸） */
.provider-actions :deep(.el-button .el-icon) {
  font-size: 13px;
}

/* 确认=主题色实心小胶囊（编辑态主操作） */
.provider-actions :deep(.el-button.action-save),
.provider-actions :deep(.el-button.action-save:hover),
.provider-actions :deep(.el-button.action-save:focus) {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: var(--main-orange);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
}

.provider-actions :deep(.el-button.action-save:hover) {
  filter: brightness(1.06);
}

/* Config Section */
.provider-config-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--db-border);
}

/* 表单两列等宽：Base URL | API Key 输入框同宽；行距由 row-gap 承担，项无下边距 */
.provider-config-section :deep(.el-form) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
  row-gap: 12px;
}

.provider-config-section :deep(.el-form-item) {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .provider-config-section :deep(.el-form) {
    grid-template-columns: 1fr;
  }
}

.config-hint {
  font-size: 11px;
  color: var(--el-color-warning);
  margin-top: 2px;
}

/* Models under Provider */
.provider-models {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--db-border);
}

.models-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-muted);
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
  background: var(--db-bg);
  border: 1px solid var(--db-border);
}

.model-mini.model-extra {
  opacity: 0.7;
  border-style: dashed;
}

.model-mini-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--db-text);
}

/* Unconfigured hint */
.unconfigured-hint {
  margin-top: 8px;
}

/* 模型列表：两列响应式网格，消除整宽行的中段空白 */
.model-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(460px, 1fr));
  gap: 10px;
}

/* 窄视口：内容区窄于网格最小列宽时回退单列，避免卡片被裁切 */
@media (max-width: 768px) {
  .model-list {
    grid-template-columns: 1fr;
  }
}

/* 模型卡：与 provider-item 同族语言，去阴影纯 border，紧凑行高 */
.model-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-card);
  transition: border-color 0.15s;
}

.model-item:hover {
  border-color: var(--db-border-strong);
}

/* 类型 tint 图标 chip：chat=blue / embedding=violet / rerank=orange */
.model-item.model-dot-chat    .model-icon { background: var(--db-card-blue-bg);    color: var(--db-card-blue-fg); }
.model-item.model-dot-embedding .model-icon { background: var(--db-card-violet-bg); color: var(--db-card-violet-fg); }
.model-item.model-dot-rerank   .model-icon { background: var(--db-card-orange-bg);  color: var(--db-card-orange-fg); }

.model-icon {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  /* 未知类型（如 model3d）的中性兜底底色；chat/embedding/rerank 由 model-dot-* 覆盖为家族 tint */
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-secondary);
}

.model-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  width: 100%;
}

.model-id {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

/* 上下结构：名称主行、真实模型名次行，各自截断（与 provider-title-text 同族） */
.model-title-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.model-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.model-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.model-modelname {
  font-size: 11px;
  color: var(--db-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.model-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-left: auto;
}

.model-action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 1;
  min-width: 0;
  max-width: 100%;
}

/* 描边胶囊按钮：与 provider-item .action-capsule 同族 */
.model-actions :deep(.el-button.action-capsule) {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.model-actions :deep(.el-button.action-capsule:hover),
.model-actions :deep(.el-button.action-capsule:focus) {
  border-color: color-mix(in srgb, var(--main-orange) 45%, var(--db-border));
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

/* 设为默认=主题色描边（主轻量动作） */
.model-actions :deep(.el-button.action-set-default) {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 35%, var(--db-border));
}
.model-actions :deep(.el-button.action-set-default:hover) {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 向量/重排默认=绿色描边 */
.model-actions :deep(.el-button.action-set-embedding),
.model-actions :deep(.el-button.action-set-rerank) {
  color: var(--el-color-success);
  border-color: color-mix(in srgb, var(--el-color-success) 35%, var(--db-border));
}
.model-actions :deep(.el-button.action-set-embedding:hover),
.model-actions :deep(.el-button.action-set-rerank:hover) {
  background: rgba(34, 197, 94, 0.06);
}

/* 删除=红色描边 */
.model-actions :deep(.el-button.action-delete) {
  color: var(--db-danger);
  border-color: color-mix(in srgb, var(--db-danger) 30%, var(--db-border));
}
.model-actions :deep(.el-button.action-delete:hover) {
  color: #dc2626;
  background: var(--db-danger-bg);
}

.form-tip {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.4;
  margin-top: 4px;
}

/* 窄视口：页头纵向堆叠，胶囊撑满便于点按 */
@media (max-width: 768px) {
  .mc-header {
    flex-direction: column;
    align-items: stretch;
  }

  .mc-desc {
    white-space: normal;
    overflow: visible;
  }

  .btn-create-pill {
    width: 100%;
    justify-content: center;
  }
}
</style>
