<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="580px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form :model="formData" label-position="top" size="default" class="detail-form">
      <!-- 基本信息 -->
      <div class="form-section">
        <div class="section-title-bar">
          <span class="section-dot"></span>
          <span class="section-title">{{ t('agentConfig.basicInfo') || '基本信息' }}</span>
        </div>

        <div class="form-row">
          <el-form-item :label="t('agent.name')" class="flex-1">
            <el-input v-model="formData.name" :placeholder="t('agentConfig.namePlaceholder') || '请输入名称'" />
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
          <el-input v-model="formData.description" type="textarea" :rows="2" :placeholder="t('agentConfig.descPlaceholder') || '简要描述该智能体的用途'" />
        </el-form-item>

        <div class="form-row">
          <el-form-item :label="t('agentConfig.modelSelect')" class="flex-1">
            <el-select v-model="formData.modelName" clearable :placeholder="t('agentConfig.modelPlaceholder')" class="w-full">
              <el-option
                v-for="model in enabledModelList"
                :key="model.modelName"
                :label="`${model.name} (${model.modelName})`"
                :value="model.modelName"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('agent.maxIterations')" class="w-120">
            <el-input-number v-model="formData.maxIterations" :min="AGENT_MIN_ITERATIONS_LIMIT" :max="AGENT_MAX_ITERATIONS_LIMIT" controls-position="right" class="w-full" />
          </el-form-item>
        </div>
      </div>

      <!-- 高级配置 -->
      <div class="form-section">
        <div class="section-title-bar">
          <span class="section-dot"></span>
          <span class="section-title">{{ t('agentConfig.advancedSettings') || '高级设置' }}</span>
        </div>

        <el-form-item :label="t('agent.prompt')">
          <el-input v-model="formData.systemPrompt" type="textarea" :rows="4" />
        </el-form-item>

        <div class="form-row">
          <el-form-item :label="t('agent.enabled')" class="w-120">
            <el-switch v-model="formData.enabled" />
          </el-form-item>
        </div>

        <el-form-item :label="t('agentConfig.tags')">
          <el-input v-model="formData.tags" :placeholder="t('agentConfig.tagsPlaceholder')" />
        </el-form-item>

        <div class="form-hint-inline">
          <span>💡</span>
          <span>{{ t('agentConfig.modelHint') }}</span>
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ mode === 'create' ? t('common.create') : t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useModelStore } from '@/stores/useModelStore'
import { AGENT_TYPES, AGENT_MAX_ITERATIONS_LIMIT, AGENT_MIN_ITERATIONS_LIMIT } from '@/types'
import type { Agent } from '@/types'

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  initialData: Partial<Agent>
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { t } = useI18n()
const agentStore = useAgentStore()
const modelStore = useModelStore()

/** 弹窗标题 */
const dialogTitle = computed(() => {
  return props.mode === 'create'
    ? (t('agentConfig.createAgent') || '创建智能体')
    : (t('agentConfig.editAgent') || '编辑智能体')
})

/** 保存中状态 */
const saving = ref(false)

/** 仅展示已启用的模型列表 */
const enabledModelList = computed(() => {
  return modelStore.enabledModels
})

/** 表单数据 */
const formData = reactive<Partial<Agent>>({
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

/** 监听弹窗打开，用初始数据填充表单 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.mode === 'edit' && props.initialData) {
        Object.assign(formData, {
          name: props.initialData.name || '',
          description: props.initialData.description || '',
          agentType: props.initialData.agentType || 'react',
          systemPrompt: props.initialData.systemPrompt || '',
          modelName: props.initialData.modelName || '',
          maxIterations: props.initialData.maxIterations ?? 5,
          defaultThinkingLevel: props.initialData.defaultThinkingLevel || 'medium',
          tags: props.initialData.tags || '',
          enabled: props.initialData.enabled ?? true,
          workspaceId: props.initialData.workspaceId ?? 1,
        })
      } else {
        Object.assign(formData, {
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
    }
  }
)

/** 保存（新增或更新） */
async function handleSave(): Promise<void> {
  if (!formData.name?.trim()) {
    ElMessage.warning(t('agentConfig.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (props.mode === 'create') {
      await agentStore.createAgent(formData)
    } else if (props.initialData?.id) {
      await agentStore.updateAgent(props.initialData.id, formData)
    }
    emit('saved')
    emit('update:visible', false)
    ElMessage.success(t('common.success'))
  } catch {
    ElMessage.error(t('common.error'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.detail-form {
  padding-right: 4px;
}

.detail-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.detail-form :deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 12px;
  color: #4e5969;
  padding-bottom: 4px;
  line-height: 1.3;
}

.detail-form :deep(.el-input__wrapper),
.detail-form :deep(.el-textarea__inner),
.detail-form :deep(.el-select .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
  transition: box-shadow 0.2s;
}

.detail-form :deep(.el-input__wrapper:hover),
.detail-form :deep(.el-textarea__inner:hover),
.detail-form :deep(.el-select .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c9cdd4 inset;
}

.detail-form :deep(.el-input__wrapper.is-focus),
.detail-form :deep(.el-textarea__inner:focus),
.detail-form :deep(.el-select .el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--main-orange) inset;
}

/* 分组区块 */
.form-section {
  margin-bottom: 20px;
}

.section-title-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f2f3f5;
}

.section-dot {
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: var(--main-orange);
  flex-shrink: 0;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: #1d2129;
  letter-spacing: 0.3px;
}

/* 双列布局 */
.form-row {
  display: flex;
  gap: 16px;
}

.form-row .flex-1 {
  flex: 1;
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

/* 内联提示 */
.form-hint-inline {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 11px;
  color: #86909c;
  line-height: 1.5;
  margin-top: -8px;
  margin-bottom: 4px;
  padding-left: 2px;
}
</style>
