<template>
  <div class="ai-chat-panel">
    <div class="ai-chat-header">
      <div class="ai-chat-header-left">
        <div class="ai-robot-icon">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="8" y="12" width="32" height="28" rx="8" fill="#6366F1" />
            <rect x="14" y="20" width="8" height="8" rx="4" fill="white" fill-opacity="0.9" />
            <rect x="26" y="20" width="8" height="8" rx="4" fill="white" fill-opacity="0.9" />
            <rect x="20" y="32" width="8" height="3" rx="1.5" fill="white" fill-opacity="0.6" />
            <rect x="18" y="6" width="12" height="4" rx="2" fill="#818CF8" />
            <circle cx="24" cy="4" r="2" fill="#818CF8" />
          </svg>
        </div>
        <span class="ai-chat-title">{{ t('insight.aiAssistantTitle') }}</span>
      </div>
      <el-button text size="small" @click="$emit('close')">
        <el-icon><Close /></el-icon>
      </el-button>
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
        <div class="ai-robot-large">
          <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="10" y="16" width="44" height="36" rx="10" fill="#6366F1" />
            <rect x="18" y="26" width="10" height="10" rx="5" fill="white" fill-opacity="0.9" />
            <rect x="36" y="26" width="10" height="10" rx="5" fill="white" fill-opacity="0.9" />
            <rect x="26" y="44" width="12" height="4" rx="2" fill="white" fill-opacity="0.6" />
            <rect x="24" y="8" width="16" height="5" rx="2.5" fill="#818CF8" />
            <circle cx="32" cy="5" r="3" fill="#818CF8" />
          </svg>
        </div>
        <div class="empty-hint">{{ t('insight.aiAssistantPlaceholder') }}</div>
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="ai-chat-message"
        :class="msg.role"
      >
        <div class="message-avatar">
          <template v-if="msg.role === 'user'">
            <div class="user-avatar">👤</div>
          </template>
          <template v-else>
            <div class="robot-avatar-small">
              <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="5" y="8" width="22" height="18" rx="5" fill="#6366F1" />
                <rect x="9" y="14" width="5" height="5" rx="2.5" fill="white" fill-opacity="0.9" />
                <rect x="18" y="14" width="5" height="5" rx="2.5" fill="white" fill-opacity="0.9" />
                <rect x="13" y="22" width="6" height="2" rx="1" fill="white" fill-opacity="0.6" />
              </svg>
            </div>
          </template>
        </div>
        <div class="message-content">{{ msg.content }}</div>
      </div>
      <div v-if="loading" class="ai-chat-message assistant">
        <div class="message-avatar">
          <div class="robot-avatar-small">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="5" y="8" width="22" height="18" rx="5" fill="#6366F1" />
              <rect x="9" y="14" width="5" height="5" rx="2.5" fill="white" fill-opacity="0.9" />
              <rect x="18" y="14" width="5" height="5" rx="2.5" fill="white" fill-opacity="0.9" />
              <rect x="13" y="22" width="6" height="2" rx="1" fill="white" fill-opacity="0.6" />
            </svg>
          </div>
        </div>
        <div class="message-content loading-dots">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <!-- 美化输入框 -->
    <div class="ai-chat-input-wrapper">
      <div class="ai-chat-input-box">
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
          class="send-btn"
          type="primary"
          circle
          :loading="loading"
          :disabled="!canSend"
          @click="handleSend"
        >
          <el-icon><Promotion /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion, Close } from '@element-plus/icons-vue'
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
  } catch (err: any) {
    const isTimeout = err?.code === 'ECONNABORTED' || err?.message?.includes('timeout')
    const errorContent = isTimeout
      ? t('insight.aiChatTimeout')
      : (isModifyMode.value ? t('insight.aiChatFailed') : t('insight.generateFailed'))
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

/* 头部 */
.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--theme-surface) 0%, rgba(99, 102, 241, 0.04) 100%);
}

.ai-chat-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-robot-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.ai-robot-icon svg {
  width: 100%;
  height: 100%;
}

.ai-chat-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text);
}

/* 生成模式表单 */
.ai-chat-generate-form {
  padding: 14px;
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

/* 消息列表 */
.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.ai-robot-large {
  width: 64px;
  height: 64px;
  opacity: 0.7;
}

.ai-robot-large svg {
  width: 100%;
  height: 100%;
}

.empty-hint {
  font-size: 12px;
  color: var(--theme-text-muted);
  text-align: center;
  line-height: 1.6;
  padding: 0 12px;
}

/* 消息气泡 */
.ai-chat-message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.ai-chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
  overflow: hidden;
}

.user-avatar {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  font-size: 13px;
}

.robot-avatar-small {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6366f1 0%, #818cf8 100%);
  border-radius: 50%;
  padding: 2px;
}

.robot-avatar-small svg {
  width: 100%;
  height: 100%;
}

.message-content {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.ai-chat-message.user .message-content {
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-chat-message.assistant .message-content {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
  border-bottom-left-radius: 4px;
}

/* 加载动画 */
.loading-dots {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 14px;
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

/* 美化输入框 */
.ai-chat-input-wrapper {
  padding: 12px 14px;
  border-top: 1px solid var(--theme-border);
  flex-shrink: 0;
  background: var(--theme-bg);
}

.ai-chat-input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  padding: 8px 8px 8px 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-chat-input-box:hover {
  border-color: var(--main-orange);
}

.ai-chat-input-box:focus-within {
  border-color: var(--main-orange);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.1);
}

.ai-chat-input-box :deep(.el-textarea) {
  flex: 1;
}

.ai-chat-input-box :deep(.el-textarea__inner) {
  font-size: 13px;
  min-height: 40px !important;
  max-height: 120px;
  padding: 6px 0;
  background: transparent;
  border: none;
  box-shadow: none;
  resize: none;
  line-height: 1.5;
}

.ai-chat-input-box :deep(.el-textarea__inner:focus) {
  outline: none;
}

.ai-chat-input-box :deep(.el-textarea__inner::placeholder) {
  color: var(--theme-text-muted);
}

.send-btn {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50% !important;
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn :deep(.el-icon) {
  font-size: 14px;
}
</style>
