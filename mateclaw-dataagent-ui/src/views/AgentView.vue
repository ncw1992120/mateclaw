<template>
  <div class="flex flex-col h-full p-6">
    <!-- 工具栏 -->
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-lg font-semibold text-gray-800">{{ t('agent.title') }}</h2>
      <div class="flex gap-2">
        <el-button @click="handleApplyTemplate">
          {{ t('agent.applyTemplate') }}
        </el-button>
        <el-button type="primary" @click="handleCreate">
          {{ t('agent.create') }}
        </el-button>
      </div>
    </div>

    <!-- Agent 卡片网格 -->
    <div v-loading="agentStore.loading" class="flex-1 overflow-y-auto">
      <el-row :gutter="16">
        <el-col
          v-for="agent in agentStore.agents"
          :key="agent.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          class="mb-4"
        >
          <el-card shadow="hover" class="cursor-pointer h-full" @click="handleSelectAgent(agent.id)">
            <template #header>
              <div class="flex items-center justify-between">
                <span class="font-medium truncate">{{ agent.name }}</span>
                <el-tag :type="agent.enabled ? 'success' : 'info'" size="small">
                  {{ agent.enabled ? t('agent.enabled') : '禁用' }}
                </el-tag>
              </div>
            </template>
            <div class="space-y-2">
              <div>
                <el-tag size="small" type="warning">{{ agent.agentType }}</el-tag>
              </div>
              <p class="text-sm text-gray-500 line-clamp-2 min-h-[2.5rem]">
                {{ agent.description || '-' }}
              </p>
              <div class="flex justify-end gap-2 pt-2">
                <el-button text type="primary" size="small" @click.stop="handleEdit(agent)">
                  {{ t('agent.edit') }}
                </el-button>
                <el-button text type="danger" size="small" @click.stop="handleDelete(agent.id)">
                  {{ t('agent.delete') }}
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 空状态 -->
      <div
        v-if="!agentStore.loading && agentStore.agents.length === 0"
        class="flex items-center justify-center h-64 text-gray-400"
      >
        暂无 Agent，请创建或应用模板
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('agent.edit') : t('agent.create')"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="110px"
        label-position="right"
      >
        <el-form-item :label="t('agent.name')" prop="name">
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item :label="t('agent.description')" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('agent.type')" prop="agentType">
          <el-select v-model="formData.agentType" class="w-full">
            <el-option label="React" value="react" />
            <el-option label="Plan & Execute" value="plan_execute" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.prompt')" prop="systemPrompt">
          <el-input v-model="formData.systemPrompt" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item :label="t('agent.model')" prop="modelName">
          <el-input v-model="formData.modelName" />
        </el-form-item>
        <el-form-item :label="t('agent.maxIterations')" prop="maxIterations">
          <el-input-number v-model="formData.maxIterations" :min="1" :max="50" class="w-full" />
        </el-form-item>
        <el-form-item :label="t('agent.enabled')" prop="enabled">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 应用模板对话框 -->
    <el-dialog
      v-model="templateDialogVisible"
      :title="t('agent.applyTemplate')"
      width="400px"
      destroy-on-close
    >
      <el-form
        ref="templateFormRef"
        :model="templateFormData"
        :rules="templateFormRules"
        label-width="100px"
      >
        <el-form-item label="模板 ID" prop="templateId">
          <el-input-number v-model="templateFormData.templateId" :min="1" class="w-full" />
        </el-form-item>
        <el-form-item label="工作空间 ID" prop="workspaceId">
          <el-input-number v-model="templateFormData.workspaceId" :min="1" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleApplySubmit" :loading="applyingTemplate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAgentStore } from '@/stores/useAgentStore'
import { useChatStore } from '@/stores/useChatStore'
import type { Agent } from '@/types'

/** 默认工作空间 ID */
const DEFAULT_WORKSPACE_ID = 1

const { t } = useI18n()
const agentStore = useAgentStore()
const chatStore = useChatStore()

/** 对话框是否可见 */
const dialogVisible = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)
/** 编辑时的 Agent ID */
const editingId = ref<number | null>(null)
/** 提交中 */
const submitting = ref(false)

/** 模板对话框是否可见 */
const templateDialogVisible = ref(false)
/** 应用模板中 */
const applyingTemplate = ref(false)

/** 表单引用 */
const formRef = ref<FormInstance | null>(null)
const templateFormRef = ref<FormInstance | null>(null)

/** 表单数据 */
const formData = reactive<Partial<Agent>>({
  name: '',
  description: '',
  agentType: 'react',
  systemPrompt: '',
  modelName: '',
  maxIterations: 5,
  enabled: true,
  workspaceId: DEFAULT_WORKSPACE_ID,
})

/** 模板表单数据 */
const templateFormData = reactive({
  templateId: 1,
  workspaceId: DEFAULT_WORKSPACE_ID,
})

/** 表单校验规则 */
const formRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  agentType: [{ required: true, message: '请选择类型', trigger: 'change' }],
})

/** 模板表单校验规则 */
const templateFormRules = reactive<FormRules>({
  templateId: [{ required: true, message: '请输入模板 ID', trigger: 'blur' }],
  workspaceId: [{ required: true, message: '请输入工作空间 ID', trigger: 'blur' }],
})

/** 重置表单数据 */
function resetForm(): void {
  formData.name = ''
  formData.description = ''
  formData.agentType = 'react'
  formData.systemPrompt = ''
  formData.modelName = ''
  formData.maxIterations = 5
  formData.enabled = true
  formData.workspaceId = DEFAULT_WORKSPACE_ID
}

/** 新建 Agent */
function handleCreate(): void {
  isEdit.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

/** 编辑 Agent */
function handleEdit(agent: Agent): void {
  isEdit.value = true
  editingId.value = agent.id
  Object.assign(formData, {
    name: agent.name,
    description: agent.description,
    agentType: agent.agentType,
    systemPrompt: agent.systemPrompt,
    modelName: agent.modelName,
    maxIterations: agent.maxIterations,
    enabled: agent.enabled,
    workspaceId: agent.workspaceId,
  })
  dialogVisible.value = true
}

/** 提交表单 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await agentStore.updateAgent(editingId.value, { ...formData })
      ElMessage.success(t('agent.updateSuccess'))
    } else {
      await agentStore.createAgent({ ...formData })
      ElMessage.success(t('agent.createSuccess'))
    }
    dialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

/** 删除 Agent */
async function handleDelete(id: number): Promise<void> {
  await ElMessageBox.confirm(t('agent.deleteConfirm'), t('common.confirm'), {
    type: 'warning',
  })
  await agentStore.deleteAgent(id)
  ElMessage.success(t('agent.deleteSuccess'))
}

/** 选中 Agent（用于聊天和右侧面板） */
async function handleSelectAgent(id: number): Promise<void> {
  await agentStore.selectAgent(id)
  chatStore.setAgent(id)
}

/** 打开应用模板对话框 */
function handleApplyTemplate(): void {
  templateFormData.templateId = 1
  templateFormData.workspaceId = DEFAULT_WORKSPACE_ID
  templateDialogVisible.value = true
}

/** 提交应用模板 */
async function handleApplySubmit(): Promise<void> {
  if (!templateFormRef.value) return
  const valid = await templateFormRef.value.validate().catch(() => false)
  if (!valid) return

  applyingTemplate.value = true
  try {
    await agentStore.applyTemplate(templateFormData.templateId, templateFormData.workspaceId)
    ElMessage.success(t('agent.applySuccess'))
    templateDialogVisible.value = false
  } finally {
    applyingTemplate.value = false
  }
}

onMounted(() => {
  agentStore.fetchAgents(DEFAULT_WORKSPACE_ID)
})
</script>
