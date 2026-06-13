<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑智能体' : '新建智能体'"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-position="top"
      v-loading="loading"
    >
      <!-- 基本信息 -->
      <div class="form-section">
        <div class="section-title-bar">
          <span class="section-dot"></span>
          <span class="section-title">基本信息</span>
        </div>

        <div class="form-row">
          <el-form-item :label="t('agent.name')" prop="name" class="flex-1">
            <el-input v-model="formData.name" placeholder="请输入名称" />
          </el-form-item>
          <el-form-item :label="t('agent.type')" class="w-140">
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
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="简要描述该智能体的用途" />
        </el-form-item>

        <div class="form-row">
          <div class="form-col-flex">
            <el-form-item label="使用模型" class="model-dropdown-wrapper">
              <!-- 自定义下拉：点击触发区打开面板 -->
              <div
                ref="modelTriggerRef"
                class="custom-select-trigger"
                :class="{ active: modelDropdownOpen }"
                @click.stop="toggleModelDropdown"
              >
                <span class="trigger-text" :class="{ placeholder: !selectedModelLabel }">
                  {{ selectedModelLabel || '请选择模型' }}
                </span>
                <span v-if="formData.modelName" class="trigger-clear" @click.stop="clearModel">✕</span>
                <span class="trigger-arrow">▾</span>
              </div>

              <!-- 下拉面板（Teleport 到 body，完全脱离 el-select） -->
              <Teleport to="body">
                <div
                  v-if="modelDropdownOpen"
                  ref="modelDropdownRef"
                  class="custom-model-dropdown"
                  @click.stop
                >
                  <!-- 搜索框 -->
                  <div class="dropdown-search">
                    <el-input
                      ref="modelSearchInputRef"
                      v-model="modelSearchKeyword"
                      placeholder="搜索模型名称"
                      clearable
                      size="small"
                    >
                      <template #prefix><span class="search-icon">🔍</span></template>
                    </el-input>
                  </div>

                  <!-- 选项列表（100% 由 filteredModelList 控制） -->
                  <div v-if="filteredModelList.length > 0" class="dropdown-options">
                    <div
                      v-for="model in filteredModelList"
                      :key="model.modelName"
                      class="dropdown-option"
                      :class="{ selected: formData.modelName === model.modelName }"
                      @click.stop="selectModel(model)"
                    >
                      <div class="option-avatar" :style="getProviderAvatarStyle(model.provider)">
                        {{ getProviderInitial(model.provider) }}
                      </div>
                      <div class="option-info">
                        <div class="option-name">{{ model.name }}</div>
                        <div class="option-meta">
                          <span class="option-provider">{{ model.provider || '未知供应商' }}</span>
                          <span class="option-divider">·</span>
                          <span class="option-model-name">{{ model.modelName }}</span>
                        </div>
                      </div>
                      <div v-if="formData.modelName === model.modelName" class="option-check">✓</div>
                    </div>
                  </div>

                  <!-- 空状态 -->
                  <div v-else class="dropdown-empty">
                    {{ modelSearchKeyword ? '未找到匹配模型' : '暂无可用模型' }}
                  </div>
                </div>
              </Teleport>
            </el-form-item>
            <div class="form-hint-inline">
              <span>💡</span>
              <span>留空则使用全局默认模型，指定后仅该智能体使用所选模型</span>
            </div>
          </div>
          <el-form-item :label="t('agent.maxIterations')" class="w-120">
            <el-input-number v-model="formData.maxIterations" :min="AGENT_MIN_ITERATIONS_LIMIT" :max="AGENT_MAX_ITERATIONS_LIMIT" controls-position="right" class="w-full" />
          </el-form-item>
        </div>
      </div>

      <!-- 高级配置 -->
      <div class="form-section">
        <div class="section-title-bar">
          <span class="section-dot"></span>
          <span class="section-title">高级设置</span>
        </div>

        <el-form-item :label="t('agent.prompt')">
          <el-input v-model="formData.systemPrompt" type="textarea" :rows="4" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="思考深度" class="flex-1">
            <el-select v-model="formData.defaultThinkingLevel" clearable class="w-full">
              <el-option
                v-for="tl in THINKING_LEVELS"
                :key="tl.value"
                :label="tl.label"
                :value="tl.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('agent.enabled')" class="w-120">
            <el-switch v-model="formData.enabled" />
          </el-form-item>
        </div>

        <el-form-item label="标签">
          <el-input v-model="formData.tags" placeholder="逗号分隔，如：数据分析,AUM" />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useModelStore } from '@/stores/useModelStore'
import { AGENT_TYPES, THINKING_LEVELS, AGENT_MAX_ITERATIONS_LIMIT, AGENT_MIN_ITERATIONS_LIMIT } from '@/types'
import type { Agent } from '@/types'

const props = defineProps<{
  visible: boolean
  /** 编辑模式时传入 Agent id；新建模式不传或传 null */
  editId?: number | null
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

/** 加载中 */
const loading = ref(false)
/** 保存中 */
const saving = ref(false)

/** 仅展示已启用的模型列表 */
const enabledModelList = computed(() => modelStore.enabledModels)

/** 模型搜索关键字 */
const modelSearchKeyword = ref('')

/**
 * 计算模型与搜索关键字的匹配分（越大越靠前）。
 * 支持多关键词（空格分隔），每个关键词独立匹配各字段并累加得分。
 */
function calcMatchScore(model: { name?: string, modelName?: string, provider?: string, description?: string }, tokens: string[]): number {
  const name = (model.name || '').toLowerCase()
  const modelName = (model.modelName || '').toLowerCase()
  const provider = (model.provider || '').toLowerCase()
  const description = (model.description || '').toLowerCase()

  let totalScore = 0

  for (const token of tokens) {
    let tokenScore = 0
    // 对每个 token，在各字段中找最佳匹配
    if (name === token) tokenScore = Math.max(tokenScore, 100)
    else if (modelName === token) tokenScore = Math.max(tokenScore, 90)
    else if (name.startsWith(token)) tokenScore = Math.max(tokenScore, 80)
    else if (modelName.startsWith(token)) tokenScore = Math.max(tokenScore, 70)
    else if (provider.startsWith(token)) tokenScore = Math.max(tokenScore, 60)
    else if (name.includes(token)) tokenScore = Math.max(tokenScore, 50)
    else if (modelName.includes(token)) tokenScore = Math.max(tokenScore, 40)
    else if (provider.includes(token)) tokenScore = Math.max(tokenScore, 30)
    else if (description.includes(token)) tokenScore = Math.max(tokenScore, 20)

    // 任一 token 完全不匹配则整体不匹配（AND 语义）
    if (tokenScore === 0) return 0
    totalScore += tokenScore
  }
  return totalScore
}

/** 根据搜索关键字过滤并按相关度排序后的模型列表 */
const filteredModelList = computed(() => {
  const raw = modelSearchKeyword.value.trim().toLowerCase()
  if (!raw) return enabledModelList.value

  const tokens = raw.split(/\s+/).filter(Boolean)
  if (tokens.length === 0) return enabledModelList.value

  const scored = enabledModelList.value
    .map(m => ({ model: m, score: calcMatchScore(m, tokens) }))
    .filter(item => item.score > 0)

  // 分数降序，同分保持原顺序
  scored.sort((a, b) => b.score - a.score)
  return scored.map(item => item.model)
})

/* ========== 自定义模型下拉逻辑（完全脱离 el-select） ========== */

/** 下拉面板是否打开 */
const modelDropdownOpen = ref(false)
/** 下拉面板 DOM 引用 */
const modelDropdownRef = ref<HTMLElement | null>(null)
/** 搜索输入框 DOM 引用 */
const modelSearchInputRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)
/** 触发器 DOM 引用（用于精确定位下拉面板） */
const modelTriggerRef = ref<HTMLElement | null>(null)

/** 当前选中模型的显示文本 */
const selectedModelLabel = computed(() => {
  if (!formData.modelName) return ''
  const found = enabledModelList.value.find(m => m.modelName === formData.modelName)
  return found ? `${found.name} (${found.modelName})` : formData.modelName
})

/** 切换下拉面板 */
function toggleModelDropdown(): void {
  modelDropdownOpen.value = !modelDropdownOpen.value
  if (modelDropdownOpen.value) {
    nextTick(() => {
      modelSearchInputRef.value?.focus()
      positionDropdown()
    })
    document.addEventListener('click', closeModelDropdown)
    document.addEventListener('keydown', handleEscKey)
    window.addEventListener('resize', positionDropdown)
    window.addEventListener('scroll', positionDropdown, true)
  } else {
    cleanupDropdownListeners()
  }
}

/** 关闭下拉面板 */
function closeModelDropdown(): void {
  modelDropdownOpen.value = false
  cleanupDropdownListeners()
}

/** 清理下拉面板相关事件监听 */
function cleanupDropdownListeners(): void {
  document.removeEventListener('click', closeModelDropdown)
  document.removeEventListener('keydown', handleEscKey)
  window.removeEventListener('resize', positionDropdown)
  window.removeEventListener('scroll', positionDropdown, true)
}

/** ESC 键关闭下拉 */
function handleEscKey(e: KeyboardEvent): void {
  if (e.key === 'Escape') closeModelDropdown()
}

/** 定位下拉面板到触发器正下方 */
function positionDropdown(): void {
  nextTick(() => {
    const trigger = modelTriggerRef.value
    const panel = modelDropdownRef.value
    if (!trigger || !panel) return
    const rect = trigger.getBoundingClientRect()
    // 边界探测：避免下拉面板超出右边界
    const viewportWidth = window.innerWidth
    const minWidth = 360
    const maxWidth = 480
    const desiredWidth = Math.max(minWidth, Math.min(rect.width, maxWidth))
    // 如果右放不下，则向左对齐
    let left = rect.left
    if (left + desiredWidth > viewportWidth - 8) {
      left = Math.max(8, viewportWidth - desiredWidth - 8)
    }
    panel.style.top = `${rect.bottom + 6}px`
    panel.style.left = `${left}px`
    panel.style.width = `${desiredWidth}px`
  })
}

/** 供应商头像背景色调色板 */
const PROVIDER_COLORS = [
  '#165dff', // 蓝
  '#722ed1', // 紫
  '#0fc6c2', // 青
  '#f7ba1e', // 黄
  '#f53f3f', // 红
  '#37d67a', // 绿
  '#ff7d00', // 橙
  '#9b27b0', // 紫红
]

/** 根据供应商名生成稳定的颜色（hash 选色） */
function getProviderColor(provider: string | undefined): string {
  if (!provider) return PROVIDER_COLORS[0]
  let hash = 0
  for (let i = 0; i < provider.length; i++) {
    hash = provider.charCodeAt(i) + ((hash << 5) - hash)
  }
  return PROVIDER_COLORS[Math.abs(hash) % PROVIDER_COLORS.length]
}

/** 供应商头像 inline style */
function getProviderAvatarStyle(provider: string | undefined): Record<string, string> {
  return { background: getProviderColor(provider) }
}

/** 供应商首字母 */
function getProviderInitial(provider: string | undefined): string {
  if (!provider) return '?'
  return provider.charAt(0).toUpperCase()
}

/** 选择一个模型 */
function selectModel(model: { name: string, modelName: string }): void {
  formData.modelName = model.modelName
  closeModelDropdown()
}

/** 清除已选模型 */
function clearModel(): void {
  formData.modelName = ''
}

/** 组件卸载时清理事件监听 */
onBeforeUnmount(() => {
  cleanupDropdownListeners()
})

/** 表单引用 */
const formRef = ref<FormInstance | null>(null)

/** 表单数据 */
const formData = reactive<Partial<Agent>>({
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
})

/** 表单校验规则 */
const formRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  agentType: [{ required: true, message: '请选择类型', trigger: 'change' }],
})

/** 重置表单 */
function resetForm(): void {
  Object.assign(formData, {
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
  })
}

/** 加载 Agent 详情（编辑模式） */
async function loadAgent(id: number): Promise<void> {
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
      })
    }
  } finally {
    loading.value = false
  }
}

/** 关闭弹窗 */
function handleClose(): void {
  emit('update:visible', false)
}

/** 保存 */
async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!formData.name?.trim()) {
    ElMessage.warning('请输入名称')
    return
  }

  saving.value = true
  try {
    if (isEdit.value && formData.id) {
      await agentStore.updateAgent(formData.id, formData)
      ElMessage.success('保存成功')
    } else {
      await agentStore.createAgent(formData)
      ElMessage.success('创建成功')
    }
    emit('saved')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('common.error') || '操作失败')
  } finally {
    saving.value = false
  }
}

/** 监听 dialog 打开状态：每次打开时根据 editId 重置或加载数据 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      // 确保模型列表已加载
      if (modelStore.enabledModels.length === 0) {
        modelStore.fetchEnabledModels()
      }
      // 重置搜索关键字
      modelSearchKeyword.value = ''
      // 关闭可能残留的下拉面板
      closeModelDropdown()
      // 重置或加载
      if (props.editId) {
        loadAgent(props.editId)
      } else {
        resetForm()
      }
    } else {
      // 弹窗关闭时也清理下拉状态
      closeModelDropdown()
    }
  },
)
</script>

<style scoped>
/* 分组区块 */
.form-section {
  margin-bottom: 16px;
}

.form-section:last-child {
  margin-bottom: 0;
}

.section-title-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f2f3f5;
}

.section-dot {
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--main-orange, #f05a23);
  flex-shrink: 0;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: #1d2129;
  letter-spacing: 0.3px;
}

/* 弹性列容器（用于在 el-form-item 外挂额外内容） */
.form-col-flex {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* 双列布局 */
.form-row {
  display: flex;
  gap: 16px;
}

.form-row .flex-1 {
  flex: 1;
  min-width: 0;
}

.form-row .w-120 {
  width: 120px;
  flex-shrink: 0;
}

.form-row .w-140 {
  width: 140px;
  flex-shrink: 0;
}

.w-full {
  width: 100%;
}

/* 压缩 el-form-item 在弹窗中的间距 */
:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 12px;
  color: #4e5969;
  padding-bottom: 4px;
  line-height: 1.3;
}

/* 内联提示 */
.form-hint-inline {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 11px;
  color: #86909c;
  line-height: 1.5;
  margin-top: -10px;
  margin-bottom: 14px;
  padding-left: 2px;
}

/* ========== 自定义模型下拉样式 — 触发区（在组件内，scoped 生效） ========== */

.custom-select-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  user-select: none;
}

.custom-select-trigger:hover {
  border-color: #c9cdd4;
}

.custom-select-trigger.active,
.custom-select-trigger:focus-within {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.12);
}

.trigger-text {
  flex: 1;
  font-size: 14px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.trigger-text.placeholder {
  color: #c9cdd4;
}

.trigger-clear {
  font-size: 14px;
  color: #c9cdd4;
  cursor: pointer;
  line-height: 1;
  padding: 2px;
  border-radius: 3px;
  transition: background 0.15s;
}

.trigger-clear:hover {
  background: #f2f3f5;
  color: #4e5969;
}

.trigger-arrow {
  font-size: 11px;
  color: #86909c;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.custom-select-trigger.active .trigger-arrow {
  transform: rotate(180deg);
}
</style>

<!-- 下拉面板通过 Teleport 渲染到 body，scoped 样式无法穿透，必须使用非 scoped 样式 -->
<style>
/* 下拉面板：固定定位浮层 */
.custom-model-dropdown {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.06);
  min-width: 360px;
  max-width: 480px;
  max-height: 360px;
  display: flex;
  flex-direction: column;
  animation: dropdown-fade-in 0.15s ease-out;
  overflow: hidden;
}

@keyframes dropdown-fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 搜索框区域 */
.custom-model-dropdown .dropdown-search {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
  flex-shrink: 0;
  background: #fafbfc;
}

.custom-model-dropdown .dropdown-search .el-input__wrapper {
  border-radius: 6px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
  background: #fff;
}

.custom-model-dropdown .dropdown-search .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #c9cdd4 inset;
}

.custom-model-dropdown .dropdown-search .el-input.is-focus .el-input__wrapper {
  box-shadow: 0 0 0 1px #165dff inset !important;
  background: #fff;
}

.custom-model-dropdown .search-icon {
  font-size: 12px;
  opacity: 0.6;
  line-height: 1;
}

/* 选项列表 */
.custom-model-dropdown .dropdown-options {
  overflow-y: auto;
  flex: 1;
  padding: 6px;
}

.custom-model-dropdown .dropdown-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  cursor: pointer;
  transition: background 0.12s;
  border-radius: 8px;
}

.custom-model-dropdown .dropdown-option:hover {
  background: #f7f8fa;
}

.custom-model-dropdown .dropdown-option.selected {
  background: #e8f3ff;
}

.custom-model-dropdown .dropdown-option.selected:hover {
  background: #d9eaff;
}

/* 供应商头像 */
.custom-model-dropdown .option-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* 选项主体（名称 + 元信息） */
.custom-model-dropdown .option-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.custom-model-dropdown .option-name {
  font-size: 14px;
  color: #1d2129;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.custom-model-dropdown .dropdown-option.selected .option-name {
  color: #165dff;
  font-weight: 600;
}

.custom-model-dropdown .option-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #86909c;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
}

.custom-model-dropdown .option-provider {
  text-transform: capitalize;
  flex-shrink: 0;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.custom-model-dropdown .option-divider {
  color: #c9cdd4;
  flex-shrink: 0;
}

.custom-model-dropdown .option-model-name {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  color: #4e5969;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
  flex: 1;
}

/* 选中对勾 */
.custom-model-dropdown .option-check {
  color: #165dff;
  font-size: 16px;
  font-weight: bold;
  flex-shrink: 0;
  line-height: 1;
}

/* 空状态 */
.custom-model-dropdown .dropdown-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  color: #c9cdd4;
}
</style>
