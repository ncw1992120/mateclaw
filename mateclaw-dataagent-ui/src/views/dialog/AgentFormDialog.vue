<template>
  <el-dialog
    :model-value="visible"
    :title="isReadonly ? t('agentForm.viewTitle') : isEdit ? t('agentForm.editTitle') : t('agentForm.createTitle')"
    width="720px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <div v-loading="loading" class="agent-form-dialog">
      <el-tabs v-model="activeTab" class="agent-tabs">
        <!-- 基本信息 -->
        <el-tab-pane :label="t('agentForm.basicInfo')" name="basic">
          <el-form
            ref="formRef"
            :model="formData"
            :rules="formRules"
            :disabled="isReadonly"
            label-position="top"
          >
            <div class="form-row">
              <el-form-item :label="t('agent.name')" prop="name" class="flex-1">
                <el-input v-model="formData.name" :placeholder="t('agentForm.namePlaceholder')" />
              </el-form-item>
              <el-form-item :label="t('agent.type')" class="w-160">
                <el-select v-model="formData.agentType" class="w-full">
                  <el-option
                    v-for="at in AGENT_TYPES"
                    :key="at.value"
                    :label="at.label"
                    :value="at.value"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-form-item :label="t('agent.description')">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="2"
                :placeholder="t('agentForm.descriptionPlaceholder')"
              />
            </el-form-item>

            <div class="form-row">
              <el-form-item :label="t('agentForm.modelLabel')" class="flex-1">
                <el-select
                  v-model="formData.modelName"
                  filterable
                  clearable
                  :placeholder="t('agentForm.modelPlaceholder')"
                  class="w-full"
                >
                  <el-option
                    v-for="model in enabledModelList"
                    :key="model.modelName"
                    :label="`${model.name} (${model.provider}/${model.modelName})`"
                    :value="model.modelName"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('agent.maxIterations')" class="w-140">
                <el-input-number
                  v-model="formData.maxIterations"
                  :min="AGENT_MIN_ITERATIONS_LIMIT"
                  :max="AGENT_MAX_ITERATIONS_LIMIT"
                  controls-position="right"
                  class="w-full"
                />
              </el-form-item>
            </div>

            <el-form-item :label="t('agent.prompt')">
              <el-input v-model="formData.systemPrompt" type="textarea" :rows="4" />
            </el-form-item>

            <div class="form-row">
              <el-form-item :label="t('agentForm.thinkingLevelLabel')" class="flex-1">
                <el-select v-model="formData.defaultThinkingLevel" clearable class="w-full">
                  <el-option
                    v-for="tl in THINKING_LEVELS"
                    :key="tl.value"
                    :label="tl.label"
                    :value="tl.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('agent.enabled')" class="w-140">
                <el-switch v-model="formData.enabled" />
              </el-form-item>
            </div>

            <el-form-item :label="t('agentForm.tagsLabel')">
              <el-input v-model="formData.tags" :placeholder="t('agentForm.tagsPlaceholder')" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 技能 -->
        <el-tab-pane name="skills" :disabled="!isEdit">
          <template #label>
            <span class="tab-label">
              {{ t('agentForm.skillsTab') }}
              <span v-if="formData.skillsDisabled" class="tab-badge tab-badge--off">{{ t('agentForm.disabledBadge') }}</span>
              <span v-else-if="selectedSkillIds.length" class="tab-badge">{{ selectedSkillIds.length }}</span>
            </span>
          </template>
          <div v-if="!isEdit" class="binding-disabled-tip">{{ t('agentForm.bindTip', { tab: t('agentForm.skillsTab') }) }}</div>
          <template v-else>
            <p class="binding-hint">{{ t('agentForm.skillHint') }}</p>
            <div class="binding-disable-row">
              <el-checkbox v-model="formData.skillsDisabled">
                <strong>{{ t('agentForm.disableAllSkills') }}</strong>
                <span class="binding-disable-hint">{{ t('agentForm.disableAllSkillsHint') }}</span>
              </el-checkbox>
            </div>
            <div class="binding-search">
              <el-input
                v-model="skillSearchKeyword"
                :placeholder="t('agentForm.skillSearchPlaceholder')"
                clearable
                :disabled="formData.skillsDisabled || isReadonly"
              >
                <template #prefix><span class="search-icon">🔍</span></template>
              </el-input>
            </div>
            <div v-if="filteredSkills.length === 0" class="binding-empty">
              {{ availableSkills.length === 0 ? t('agentForm.noSkills') : t('agentForm.noSkillMatch') }}
            </div>
            <div
              v-else
              class="binding-list"
              :class="{ 'binding-list--disabled': formData.skillsDisabled }"
            >
              <label
                v-for="skill in filteredSkills"
                :key="skill.id"
                class="binding-item"
                :class="{
                  selected: !formData.skillsDisabled && selectedSkillIds.includes(skill.id),
                  'binding-item--disabled': formData.skillsDisabled,
                }"
              >
                <el-checkbox
                  :model-value="!formData.skillsDisabled && selectedSkillIds.includes(skill.id)"
                  :disabled="formData.skillsDisabled || isReadonly"
                  @change="(checked: boolean | string | number) => onSkillToggle(skill.id, !!checked)"
                />
                <div class="binding-icon">{{ skill.icon || '🧩' }}</div>
                <div class="binding-info">
                  <div class="binding-name">{{ resolveSkillName(skill) }}</div>
                  <div v-if="skill.description" class="binding-desc">{{ skill.description }}</div>
                </div>
                <span v-if="skill.version" class="binding-meta">v{{ skill.version }}</span>
              </label>
            </div>
          </template>
        </el-tab-pane>

        <!-- 工具 -->
        <el-tab-pane name="tools" :disabled="!isEdit">
          <template #label>
            <span class="tab-label">
              {{ t('agentForm.toolsTab') }}
              <span v-if="formData.toolsDisabled" class="tab-badge tab-badge--off">{{ t('agentForm.disabledBadge') }}</span>
              <span v-else-if="selectedToolNames.length" class="tab-badge">{{ selectedToolNames.length }}</span>
            </span>
          </template>
          <div v-if="!isEdit" class="binding-disabled-tip">{{ t('agentForm.bindTip', { tab: t('agentForm.toolsTab') }) }}</div>
          <template v-else>
            <p class="binding-hint">{{ t('agentForm.toolHint') }}</p>
            <div class="binding-disable-row">
              <el-checkbox v-model="formData.toolsDisabled">
                <strong>{{ t('agentForm.disableAllTools') }}</strong>
                <span class="binding-disable-hint">{{ t('agentForm.disableAllToolsHint') }}</span>
              </el-checkbox>
            </div>
            <div class="binding-search">
              <el-input
                v-model="toolSearchKeyword"
                :placeholder="t('agentForm.toolSearchPlaceholder')"
                clearable
                :disabled="formData.toolsDisabled || isReadonly"
              >
                <template #prefix><span class="search-icon">🔍</span></template>
              </el-input>
            </div>
            <div v-if="filteredToolGroups.length === 0" class="binding-empty">
              {{ availableTools.length === 0 ? t('agentForm.noTools') : t('agentForm.noToolMatch') }}
            </div>
            <div
              v-else
              class="binding-list"
              :class="{ 'binding-list--disabled': formData.toolsDisabled }"
            >
              <template v-for="group in filteredToolGroups" :key="group.groupId">
                <div class="binding-group-header">{{ group.label }}</div>
                <label
                  v-for="tool in group.tools"
                  :key="tool.rowId || tool.name"
                  class="binding-item"
                  :class="{
                    selected: !formData.toolsDisabled && selectedToolNames.includes(tool.name),
                    'binding-item--disabled': formData.toolsDisabled || !tool.available,
                  }"
                >
                  <el-checkbox
                    :model-value="!formData.toolsDisabled && selectedToolNames.includes(tool.name)"
                    :disabled="formData.toolsDisabled || !tool.available || isReadonly"
                    @change="(checked: boolean | string | number) => onToolToggle(tool.name, !!checked)"
                  />
                  <div class="binding-icon">{{ tool.source === 'mcp' ? '🔌' : tool.source === 'plugin' ? '🧩' : '🔧' }}</div>
                  <div class="binding-info">
                    <div class="binding-name">{{ tool.rawName || tool.name }}</div>
                    <div v-if="tool.description" class="binding-desc">{{ tool.description }}</div>
                  </div>
                  <span v-if="tool.stale" class="binding-meta binding-meta--warn">{{ t('agentForm.toolStale') }}</span>
                  <span v-else-if="!tool.available" class="binding-meta binding-meta--warn">{{ t('agentForm.toolUnavailable') }}</span>
                  <span v-else class="binding-meta">{{ tool.source }}</span>
                </label>
              </template>
            </div>
          </template>
        </el-tab-pane>

        <!-- 偏好提供商 -->
        <el-tab-pane name="providers" :disabled="!isEdit">
          <template #label>
            <span class="tab-label">
              {{ t('agentForm.providersTab') }}
              <span v-if="selectedProviderIds.length" class="tab-badge">{{ selectedProviderIds.length }}</span>
            </span>
          </template>
          <div v-if="!isEdit" class="binding-disabled-tip">{{ t('agentForm.bindTip', { tab: t('agentForm.providersTab') }) }}</div>
          <template v-else>
            <p class="binding-hint">{{ t('agentForm.providerHint') }}</p>
            <div v-if="selectedProviderIds.length" class="provider-list">
              <div
                v-for="(pid, idx) in selectedProviderIds"
                :key="pid"
                class="provider-item"
              >
                <span class="provider-rank">{{ idx + 1 }}</span>
                <span class="provider-name">{{ providerNameById(pid) }}</span>
                <span class="provider-id">{{ pid }}</span>
                <el-button size="small" :disabled="idx === 0 || isReadonly" @click="moveProvider(idx, -1)">
                  ↑
                </el-button>
                <el-button size="small" :disabled="idx === selectedProviderIds.length - 1 || isReadonly" @click="moveProvider(idx, 1)">
                  ↓
                </el-button>
                <el-button size="small" type="danger" plain :disabled="isReadonly" @click="removeProvider(idx)">
                  ✕
                </el-button>
              </div>
            </div>
            <div v-else class="binding-empty">{{ t('agentForm.noProviderPreference') }}</div>

            <div v-if="unpickedProviders.length" class="provider-pool">
              <p class="binding-hint" style="margin-top: 12px">{{ t('agentForm.providerPoolHint') }}</p>
              <el-button
                v-for="p in unpickedProviders"
                :key="p.providerId"
                size="small"
                plain
                :disabled="isReadonly"
                @click="addProvider(p.providerId)"
              >
                + {{ p.name }}
              </el-button>
            </div>
          </template>
        </el-tab-pane>

        <!-- 知识库 -->
        <el-tab-pane name="knowledge" :disabled="!isEdit">
          <template #label>
            <span class="tab-label">
              {{ t('agentForm.knowledgeTab') }}
              <span v-if="selectedKbId" class="tab-badge">1</span>
            </span>
          </template>
          <div v-if="!isEdit" class="binding-disabled-tip">{{ t('agentForm.bindTip', { tab: t('agentForm.knowledgeTab') }) }}</div>
          <template v-else>
            <p class="binding-hint">{{ t('agentForm.knowledgeHint') }}</p>
            <div class="binding-list">
              <label class="binding-item" :class="{ selected: selectedKbId === null }">
                <el-radio v-model="selectedKbId" :value="null" :disabled="isReadonly" />
                <div class="binding-icon">🚫</div>
                <div class="binding-info">
                  <div class="binding-name">{{ t('agentForm.noPrimaryKb') }}</div>
                  <div class="binding-desc">{{ t('agentForm.noPrimaryKbDesc') }}</div>
                </div>
              </label>
              <label
                v-for="kb in availableKBs"
                :key="String(kb.id)"
                class="binding-item"
                :class="{ selected: selectedKbId === String(kb.id) }"
              >
                <el-radio v-model="selectedKbId" :value="String(kb.id)" :disabled="isReadonly" />
                <div class="binding-icon">📚</div>
                <div class="binding-info">
                  <div class="binding-name">{{ kb.name }}</div>
                  <div v-if="kb.description" class="binding-desc">{{ kb.description }}</div>
                </div>
                <span v-if="kb.pageCount != null" class="binding-meta">{{ kb.pageCount }} {{ t('agentForm.pagesUnit') }}</span>
              </label>
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <template v-if="isReadonly">
        <el-button @click="handleClose">{{ t('common.close') }}</el-button>
      </template>
      <template v-else>
        <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useModelStore } from '@/stores/useModelStore'
import * as agentApi from '@/api/agent'
import {
  AGENT_TYPES,
  THINKING_LEVELS,
  AGENT_MAX_ITERATIONS_LIMIT,
  AGENT_MIN_ITERATIONS_LIMIT,
} from '@/types'
import type {
  Agent,
  AvailableKnowledgeBase,
  AvailableTool,
  ModelProvider,
  Skill,
} from '@/types'

const props = defineProps<{
  visible: boolean
  /** 编辑模式时传入 Agent id；新建模式不传或传 null */
  editId?: number | string | null
  /** 只读模式（仅查看详情，不可编辑） */
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { t } = useI18n()
const agentStore = useAgentStore()
const modelStore = useModelStore()

/** 是否编辑模式 */
const isEdit = computed(() => !!props.editId)

/** 是否只读模式（查看详情） */
const isReadonly = computed(() => props.readonly === true && isEdit.value)

/** 加载中 */
const loading = ref(false)
/** 保存中 */
const saving = ref(false)

/** 当前激活 Tab */
const activeTab = ref<'basic' | 'skills' | 'tools' | 'providers' | 'knowledge'>('basic')

/** 仅展示已启用的对话模型列表（智能体只能使用对话模型） */
const enabledModelList = computed(() => modelStore.enabledModels.filter(m => !m.modelType || m.modelType === 'chat'))

/** 表单引用 */
const formRef = ref<FormInstance | null>(null)

/** 默认表单值 */
function buildDefaultForm(): Partial<Agent> {
  return {
    id: undefined,
    name: '',
    description: '',
    agentType: 'react',
    systemPrompt: '',
    modelName: '',
    maxIterations: 5,
    defaultThinkingLevel: 'medium',
    tags: '',
    enabled: true,
    workspaceId: 1,
    primaryKbId: null,
    skillsDisabled: false,
    toolsDisabled: false,
  }
}

/** 表单数据 */
const formData = reactive<Partial<Agent>>(buildDefaultForm())

/** 表单校验规则 */
const formRules = reactive<FormRules>({
  name: [{ required: true, message: t('agentForm.nameRequired'), trigger: 'blur' }],
  agentType: [{ required: true, message: t('agentForm.typeRequired'), trigger: 'change' }],
})

/** ============= 绑定状态 ============= */
const availableSkills = ref<Skill[]>([])
const availableTools = ref<AvailableTool[]>([])
const availableProviders = ref<ModelProvider[]>([])
const availableKBs = ref<AvailableKnowledgeBase[]>([])

const selectedSkillIds = ref<number[]>([])
const selectedToolNames = ref<string[]>([])
const selectedProviderIds = ref<string[]>([])
const selectedKbId = ref<string | null>(null)

const skillSearchKeyword = ref('')
const toolSearchKeyword = ref('')

/** 重置表单 */
function resetForm(): void {
  Object.assign(formData, buildDefaultForm())
  selectedSkillIds.value = []
  selectedToolNames.value = []
  selectedProviderIds.value = []
  selectedKbId.value = null
  availableKBs.value = []
  skillSearchKeyword.value = ''
  toolSearchKeyword.value = ''
  activeTab.value = 'basic'
}

/** 加载 Agent 详情（编辑模式） */
async function loadAgent(id: number | string): Promise<void> {
  loading.value = true
  try {
    const agent = agentStore.agents.find(a => a.id === id)
    if (agent) {
      Object.assign(formData, {
        id: agent.id,
        name: agent.name || '',
        description: agent.description || '',
        agentType: agent.agentType || 'react',
        systemPrompt: agent.systemPrompt || '',
        modelName: agent.modelName || '',
        maxIterations: agent.maxIterations ?? 5,
        defaultThinkingLevel: agent.defaultThinkingLevel || 'medium',
        tags: agent.tags || '',
        enabled: agent.enabled ?? true,
        workspaceId: agent.workspaceId ?? 1,
        primaryKbId: agent.primaryKbId ?? null,
        skillsDisabled: agent.skillsDisabled === true,
        toolsDisabled: agent.toolsDisabled === true,
      })
      selectedKbId.value = agent.primaryKbId != null ? String(agent.primaryKbId) : null
    }

    await Promise.all([
      loadAvailableSkills(),
      loadAvailableTools(),
      loadAvailableProviders(),
      loadAvailableKBs(),
      loadAgentBindings(id),
    ])
  } finally {
    loading.value = false
  }
}

/** 加载可绑定技能 */
async function loadAvailableSkills(): Promise<void> {
  try {
    const data = (await agentApi.listAvailableSkills(formData.workspaceId ?? 1)) as unknown as Skill[]
    availableSkills.value = data || []
  } catch {
    availableSkills.value = []
  }
}

/** 加载可绑定工具 */
async function loadAvailableTools(): Promise<void> {
  try {
    const data = (await agentApi.listAvailableTools()) as unknown as AvailableTool[]
    availableTools.value = data || []
  } catch {
    availableTools.value = []
  }
}

/** 加载可用 Provider */
async function loadAvailableProviders(): Promise<void> {
  try {
    const data = (await agentApi.listAvailableProviders()) as unknown as ModelProvider[]
    availableProviders.value = data || []
  } catch {
    availableProviders.value = []
  }
}

/** 加载可绑定知识库 */
async function loadAvailableKBs(): Promise<void> {
  try {
    const data = (await agentApi.listAvailableKnowledgeBases(formData.workspaceId ?? 1)) as unknown as AvailableKnowledgeBase[]
    availableKBs.value = data || []
  } catch {
    availableKBs.value = []
  }
}

/** 加载 Agent 当前绑定 */
async function loadAgentBindings(agentId: number | string): Promise<void> {
  try {
    const [skillsRes, toolsRes, providerPrefsRes] = await Promise.all([
      agentApi.listAgentSkills(agentId),
      agentApi.listAgentTools(agentId),
      agentApi.listAgentProviderPreferences(agentId),
    ])
    selectedSkillIds.value = ((skillsRes as unknown as { skillId: number; enabled: boolean }[]) || [])
      .filter(b => b.enabled)
      .map(b => b.skillId)
    selectedToolNames.value = ((toolsRes as unknown as { toolName: string; enabled: boolean }[]) || [])
      .filter(b => b.enabled)
      .map(b => b.toolName)
    selectedProviderIds.value = ((providerPrefsRes as unknown as { providerId: string; enabled: boolean }[]) || [])
      .filter(b => b.enabled)
      .map(b => b.providerId)
  } catch {
    selectedSkillIds.value = []
    selectedToolNames.value = []
    selectedProviderIds.value = []
  }
}

/** ========== 技能选择 ========== */
const filteredSkills = computed(() => {
  const kw = skillSearchKeyword.value.trim().toLowerCase()
  if (!kw) return availableSkills.value
  return availableSkills.value.filter(s =>
    (s.name || '').toLowerCase().includes(kw)
    || (s.nameZh || '').toLowerCase().includes(kw)
    || (s.nameEn || '').toLowerCase().includes(kw)
    || (s.description || '').toLowerCase().includes(kw)
  )
})

function resolveSkillName(skill: Skill): string {
  return skill.nameZh || skill.nameEn || skill.name
}

function onSkillToggle(skillId: number, checked: boolean): void {
  if (checked) {
    if (!selectedSkillIds.value.includes(skillId)) {
      selectedSkillIds.value.push(skillId)
    }
  } else {
    selectedSkillIds.value = selectedSkillIds.value.filter(id => id !== skillId)
  }
}

/** ========== 工具选择 ========== */
const groupedTools = computed(() => {
  const groups = new Map<string, { groupId: string; label: string; tools: AvailableTool[] }>()
  for (const tool of availableTools.value) {
    const key = tool.groupId || (tool.source === 'mcp' ? `mcp:${tool.providerId}` : 'builtin')
    if (!groups.has(key)) {
      const label = tool.group
        || (tool.source === 'mcp' ? `MCP · ${tool.providerName ?? ''}` : tool.source || 'tools')
      groups.set(key, { groupId: key, label, tools: [] })
    }
    groups.get(key)!.tools.push(tool)
  }
  return Array.from(groups.values())
})

const filteredToolGroups = computed(() => {
  const kw = toolSearchKeyword.value.trim().toLowerCase()
  if (!kw) return groupedTools.value
  return groupedTools.value
    .map(group => {
      const matched = group.tools.filter(tool =>
        (tool.name || '').toLowerCase().includes(kw)
        || (tool.rawName || '').toLowerCase().includes(kw)
        || (tool.description || '').toLowerCase().includes(kw)
      )
      return matched.length ? { ...group, tools: matched } : null
    })
    .filter((g): g is { groupId: string; label: string; tools: AvailableTool[] } => g !== null)
})

function onToolToggle(toolName: string, checked: boolean): void {
  if (checked) {
    if (!selectedToolNames.value.includes(toolName)) {
      selectedToolNames.value.push(toolName)
    }
  } else {
    selectedToolNames.value = selectedToolNames.value.filter(n => n !== toolName)
  }
}

/** ========== 偏好供应商 ========== */
const unpickedProviders = computed(() =>
  availableProviders.value.filter(p => !selectedProviderIds.value.includes(p.providerId))
)

function providerNameById(id: string): string {
  return availableProviders.value.find(p => p.providerId === id)?.name || id
}

function addProvider(id: string): void {
  if (!selectedProviderIds.value.includes(id)) {
    selectedProviderIds.value.push(id)
  }
}

function removeProvider(idx: number): void {
  selectedProviderIds.value.splice(idx, 1)
}

function moveProvider(idx: number, dir: -1 | 1): void {
  const next = idx + dir
  if (next < 0 || next >= selectedProviderIds.value.length) return
  const arr = selectedProviderIds.value
  const tmp = arr[idx]
  arr[idx] = arr[next]
  arr[next] = tmp
}

/** 关闭弹窗 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 同步主知识库到 formData */
function syncPrimaryKb(): void {
  formData.primaryKbId = selectedKbId.value ?? null
}

/** 保存 */
async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    activeTab.value = 'basic'
    return
  }

  if (!formData.name?.trim()) {
    ElMessage.warning(t('agentForm.nameRequired'))
    activeTab.value = 'basic'
    return
  }

  syncPrimaryKb()

  saving.value = true
  try {
    let agentId: number | string | undefined
    if (isEdit.value && formData.id) {
      await agentStore.updateAgent(formData.id, formData)
      agentId = formData.id
    } else {
      const created = (await agentStore.createAgent(formData)) as unknown as Agent | undefined
      agentId = created?.id
    }

    if (agentId) {
      await persistBindings(agentId)
    }

    ElMessage.success(isEdit.value ? t('agentForm.saveSuccess') : t('agentForm.createSuccess'))
    emit('saved')
    emit('update:visible', false)
  } catch (err) {
    const message = err instanceof Error ? err.message : t('agentForm.opFailed')
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

/** 持久化绑定关系 */
async function persistBindings(agentId: number | string): Promise<void> {
  // 禁用全部时强制清空当前 picks，避免与禁用标志冲突
  const skillIds = formData.skillsDisabled ? [] : selectedSkillIds.value
  const toolNames = formData.toolsDisabled ? [] : selectedToolNames.value
  await agentApi.setAgentSkills(agentId, skillIds)
  await agentApi.setAgentTools(agentId, toolNames)
  await agentApi.setAgentProviderPreferences(agentId, selectedProviderIds.value)
}

/** 监听 dialog 打开状态 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (modelStore.enabledModels.length === 0) {
        modelStore.fetchEnabledModels()
      }
      resetForm()
      if (props.editId) {
        loadAgent(props.editId)
      }
    }
  },
)
</script>

<style scoped>
.agent-form-dialog {
  min-height: 360px;
}

.agent-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #f2f3f5;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9px;
  background: var(--main-orange, #4176E6);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.tab-badge--off {
  background: #e5e6eb;
  color: #4e5969;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .flex-1 {
  flex: 1;
  min-width: 0;
}

.form-row .w-140 {
  width: 140px;
  flex-shrink: 0;
}

.form-row .w-160 {
  width: 160px;
  flex-shrink: 0;
}

.w-full {
  width: 100%;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 12px;
  color: #4e5969;
  padding-bottom: 4px;
}

/* 绑定面板共用样式 */
.binding-hint {
  font-size: 12.5px;
  color: #86909c;
  margin: 0 0 12px;
  line-height: 1.6;
}

.binding-disabled-tip {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  color: #c9cdd4;
}

.binding-disable-row {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px dashed #e5e6eb;
  border-radius: 8px;
  background: #fafbfc;
}

.binding-disable-row :deep(.el-checkbox__label) {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  white-space: normal;
  line-height: 1.5;
}

.binding-disable-hint {
  font-size: 11.5px;
  color: #86909c;
  font-weight: normal;
}

.binding-search {
  margin-bottom: 10px;
}

.search-icon {
  font-size: 12px;
  opacity: 0.6;
}

.binding-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  color: #c9cdd4;
}

.binding-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 360px;
  overflow-y: auto;
}

.binding-list--disabled {
  opacity: 0.55;
  pointer-events: none;
}

.binding-group-header {
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
  padding: 6px 4px 2px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.binding-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--theme-surface);
}

.binding-item:hover {
  border-color: var(--theme-border-strong);
  background: var(--theme-surface-hover);
}

.binding-item.selected {
  border-color: var(--main-orange, #4176E6);
  background: rgba(65, 118, 230, 0.05);
}

.binding-item--disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.binding-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.binding-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.binding-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #1d2129;
}

.binding-desc {
  font-size: 11.5px;
  color: #86909c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.binding-meta {
  font-size: 11px;
  color: #86909c;
  flex-shrink: 0;
  padding: 1px 6px;
  background: #f2f3f5;
  border-radius: 4px;
}

.binding-meta--warn {
  color: #f53f3f;
  background: rgba(245, 63, 63, 0.1);
}

/* 偏好供应商列表 */
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.provider-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid #f2f3f5;
  border-radius: 8px;
  background: #fff;
}

.provider-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--main-orange, #4176E6);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.provider-name {
  font-size: 13.5px;
  color: #1d2129;
  flex: 1;
}

.provider-id {
  font-size: 11.5px;
  color: #86909c;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}

.provider-pool {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.provider-pool > .binding-hint {
  width: 100%;
  margin-bottom: 4px;
}
</style>
