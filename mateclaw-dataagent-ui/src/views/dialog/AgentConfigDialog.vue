<template>
  <el-dialog
    :model-value="visible"
    :title="editingAgent ? t('agentConfig.editAgent') : t('agentConfig.createAgent')"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form :model="formData" label-width="120px" size="small">
      <el-form-item :label="t('agent.name')">
        <el-input v-model="formData.name" />
      </el-form-item>

      <el-form-item :label="t('agent.description')">
        <el-input v-model="formData.description" type="textarea" :rows="2" />
      </el-form-item>

      <el-form-item :label="t('agent.type')">
        <el-select v-model="formData.agentType">
          <el-option
            v-for="at in AGENT_TYPES"
            :key="at.value"
            :label="at.label"
            :value="at.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('agentConfig.modelSelect')">
        <el-select v-model="formData.modelName" clearable :placeholder="t('agentConfig.modelPlaceholder')">
          <el-option
            v-for="model in modelStore.enabledModels"
            :key="model.modelName"
            :label="`${model.name} (${model.modelName})`"
            :value="model.modelName"
          />
        </el-select>
        <div class="form-hint">{{ t('agentConfig.modelHint') }}</div>
      </el-form-item>

      <el-form-item :label="t('agent.prompt')">
        <el-input v-model="formData.systemPrompt" type="textarea" :rows="5" />
      </el-form-item>

      <el-form-item :label="t('agent.maxIterations')">
        <el-input-number v-model="formData.maxIterations" :min="1" :max="20" />
      </el-form-item>

      <el-form-item :label="t('agentConfig.thinkingLevel')">
        <el-select v-model="formData.defaultThinkingLevel" clearable>
          <el-option
            v-for="tl in THINKING_LEVELS"
            :key="tl.value"
            :label="tl.label"
            :value="tl.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('agentConfig.icon')">
        <el-input v-model="formData.icon" />
      </el-form-item>

      <el-form-item :label="t('agentConfig.tags')">
        <el-input v-model="formData.tags" :placeholder="t('agentConfig.tagsPlaceholder')" />
      </el-form-item>

      <el-form-item :label="t('agent.enabled')">
        <el-switch v-model="formData.enabled" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useModelStore } from '@/stores/useModelStore'
import { AGENT_TYPES, THINKING_LEVELS } from '@/types'
import type { Agent } from '@/types'

const { t } = useI18n()
const agentStore = useAgentStore()
const modelStore = useModelStore()

const props = defineProps<{
  visible: boolean
  editingAgent: Agent | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

/** 保存中状态 */
const saving = ref(false)

/** 表单数据 */
const formData = reactive<Partial<Agent>>({
  name: '',
  description: '',
  agentType: 'react',
  systemPrompt: '',
  modelName: '',
  maxIterations: 5,
  defaultThinkingLevel: 'medium',
  icon: '',
  tags: '',
  enabled: true,
  workspaceId: 1,
})

/** 监听编辑 Agent 变化，同步表单 */
watch(() => props.editingAgent, (agent) => {
  if (agent) {
    Object.assign(formData, {
      name: agent.name,
      description: agent.description,
      agentType: agent.agentType,
      systemPrompt: agent.systemPrompt,
      modelName: agent.modelName || '',
      maxIterations: agent.maxIterations,
      defaultThinkingLevel: agent.defaultThinkingLevel || 'medium',
      icon: agent.icon || '',
      tags: agent.tags || '',
      enabled: agent.enabled,
      workspaceId: agent.workspaceId,
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
      icon: '',
      tags: '',
      enabled: true,
      workspaceId: 1,
    })
  }
}, { immediate: true })

/** 保存 Agent */
async function handleSave(): Promise<void> {
  if (!formData.name?.trim()) {
    ElMessage.warning(t('agentConfig.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (props.editingAgent) {
      await agentStore.updateAgent(props.editingAgent.id, formData)
    } else {
      await agentStore.createAgent(formData)
    }
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
.form-hint {
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
  line-height: 1.4;
}
</style>