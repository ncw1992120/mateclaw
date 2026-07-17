<template>
  <div class="ai-chat-panel">
    <div class="ai-chat-header">
      <span class="ai-chat-title">{{ t('insight.aiAssistantTitle') }}</span>
      <el-button text size="small" @click="$emit('close')">✕</el-button>
    </div>

    <!-- 生成模式：首次发送前显示配置表单 -->
    <div v-if="isGenerateMode && !hasStarted" class="ai-chat-generate-form">
      <el-form
        ref="generateFormRef"
        :model="generateForm"
        :rules="generateRules"
        label-width="80px"
        label-position="top"
        @submit.prevent
      >
        <el-form-item :label="t('insight.generateName')" prop="name">
          <el-input
            v-model="generateForm.name"
            :placeholder="t('insight.generateNamePlaceholder')"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item :label="t('insight.generateDatasource')" prop="datasourceId">
          <el-select
            v-model="generateForm.datasourceId"
            :placeholder="t('insight.generateDatasourcePlaceholder')"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="ds in datasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <div ref="messageListRef" class="ai-chat-messages">
      <div v-if="messages.length === 0 && (isModifyMode || hasStarted)" class="ai-chat-empty">
        <div class="empty-hint">{{ t('insight.aiAssistantPlaceholder') }}</div>
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="ai-chat-message"
        :class="msg.role"
      >
        <div class="message-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="message-content">{{ msg.content }}</div>
      </div>
      <div v-if="loading" class="ai-chat-message assistant">
        <div class="message-avatar">🤖</div>
        <div class="message-content loading-dots">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <div class="ai-chat-input">
      <el-input
        v-model="inputText"
        :placeholder="isGenerateMode && !hasStarted ? t('insight.generateDescriptionPlaceholder') : t('insight.aiAssistantPlaceholder')"
        :disabled="loading"
        type="textarea"
        :rows="2"
        resize="none"
        @keydown.enter.exact.prevent="handleSend"
      />
      <el-button
        type="primary"
        :icon="Promotion"
        :loading="loading"
        :disabled="!canSend"
        @click="handleSend"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Datasource } from '@/types'
import * as insightDashboardApi from '@/api/insight-dashboard'
import * as datasourceApi from '@/api/datasource'

defineOptions({
  name: 'AiChatPanel',
})

const props = defineProps<{
  /** 仪表盘 ID，为空时为生成模式，不为空时为修改模式 */
  dashboardId?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  /** 生成或修改成功后触发，payload 为仪表盘 ID */
  (e: 'dashboard-updated', dashboardId: string): void
}>()

const { t } = useI18n()

/** 是否为修改模式 */
const isModifyMode = computed(() => !!props.dashboardId)

/** 是否为生成模式 */
const isGenerateMode = computed(() => !props.dashboardId)

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const hasStarted = ref(false)
const messageListRef = ref<HTMLElement | null>(null)

/** 生成模式表单 */
const generateFormRef = ref<FormInstance | null>(null)
const generateForm = reactive({
  name: '',
  datasourceId: '',
})
const generateRules = reactive<FormRules>({
  name: [{ required: true, message: t('insight.generateNamePlaceholder'), trigger: 'blur' }],
  datasourceId: [{ required: true, message: t('insight.generateDatasourcePlaceholder'), trigger: 'change' }],
})

/** 数据源列表（生成模式使用） */
const datasources = ref<Datasource[]>([])

/** 是否可以发送 */
const canSend = computed(() => {
  if (loading.value) {
    return false
  }
  if (isGenerateMode.value && !hasStarted.value) {
    // 生成模式首次发送：需要表单验证通过且有输入
    return inputText.value.trim() !== '' && generateForm.name !== '' && generateForm.datasourceId !== ''
  }
  return inputText.value.trim() !== ''
})

/** 加载数据源列表（生成模式） */
function loadDatasources(): void {
  datasourceApi.list().then((data) => {
    datasources.value = (data as unknown as Datasource[]).filter((ds) => ds.enabled)
  }).catch(() => {
    // 静默失败
  })
}

// 生成模式时自动加载数据源
if (isGenerateMode.value) {
  loadDatasources()
}

/** 滚动到底部 */
function scrollToBottom(): void {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

/** 发送消息 */
async function handleSend(): Promise<void> {
  const message = inputText.value.trim()
  if (!message || loading.value) {
    return
  }

  // 生成模式首次发送：先校验表单
  if (isGenerateMode.value && !hasStarted.value) {
    if (!generateFormRef.value) {
      return
    }
    try {
      await generateFormRef.value.validate()
    } catch {
      return
    }
  }

  // 添加用户消息
  messages.value.push({ role: 'user', content: message })
  inputText.value = ''
  scrollToBottom()

  loading.value = true
  try {
    const result = await insightDashboardApi.aiChat({
      dashboardId: props.dashboardId || undefined,
      name: isGenerateMode.value && !hasStarted.value ? generateForm.name : undefined,
      datasourceId: isGenerateMode.value && !hasStarted.value ? generateForm.datasourceId : undefined,
      message,
    }) as unknown as any

    // 标记已开始对话
    hasStarted.value = true

    // 添加AI回复
    const replyContent = isModifyMode.value
      ? t('insight.aiChatSuccess')
      : t('insight.generateSuccess')
    messages.value.push({
      role: 'assistant',
      content: replyContent,
    })
    scrollToBottom()

    // 通知父组件刷新，传递仪表盘ID
    const dashboardId = result?.id?.toString() || props.dashboardId || ''
    emit('dashboard-updated', dashboardId)

    ElMessage.success(replyContent)
  } catch {
    const errorContent = isModifyMode.value
      ? t('insight.aiChatFailed')
      : t('insight.generateFailed')
    messages.value.push({
      role: 'assistant',
      content: errorContent,
    })
    scrollToBottom()
    ElMessage.error(errorContent)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ai-chat-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-left: 1px solid var(--theme-border);
}

.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.ai-chat-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}

.ai-chat-generate-form {
  padding: 12px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  overflow-y: auto;
}

.ai-chat-generate-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.ai-chat-generate-form :deep(.el-form-item__label) {
  font-size: 12px;
  padding-bottom: 4px;
}

.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-hint {
  font-size: 12px;
  color: var(--theme-text-muted);
  text-align: center;
  line-height: 1.6;
  padding: 0 12px;
}

.ai-chat-message {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.ai-chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
  background: var(--theme-surface-hover);
}

.message-content {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.ai-chat-message.user .message-content {
  background: var(--main-orange);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.ai-chat-message.assistant .message-content {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
  border-bottom-left-radius: 2px;
}

.loading-dots {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--theme-text-muted);
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dot-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.ai-chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.ai-chat-input :deep(.el-textarea) {
  flex: 1;
}

.ai-chat-input :deep(.el-textarea__inner) {
  font-size: 13px;
  min-height: 56px !important;
  padding: 8px 12px;
}

.ai-chat-input :deep(.el-button) {
  height: 32px;
  flex-shrink: 0;
}
</style>
