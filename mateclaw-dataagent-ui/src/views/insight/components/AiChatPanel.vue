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
        <div class="message-content">
          <template v-if="msg.role === 'assistant' && msg.reasoning">
            <div class="reasoning-block">
              <div class="reasoning-label">思考过程</div>
              <div class="reasoning-text">{{ msg.reasoning }}<template v-if="msg.streaming && !msg.content"><span class="cursor-blink">|</span></template></div>
            </div>
          </template>
          <template v-if="msg.role === 'assistant' && msg.content">
            <template v-if="msg.streaming">
              <span class="streaming-text">{{ msg.content }}</span>
              <span class="cursor-blink">|</span>
            </template>
            <template v-else>{{ msg.content }}</template>
          </template>
          <template v-if="msg.role === 'assistant' && !msg.reasoning && !msg.content && msg.streaming">
            <span class="cursor-blink">|</span>
          </template>
          <template v-if="msg.role === 'user'">{{ msg.content }}</template>
        </div>
      </div>
      <div v-if="loading && !hasStreamingMessage" class="ai-chat-message assistant">
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
import { ref, computed, reactive, nextTick, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion, Close } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Datasource, InsightDashboard } from '@/types'
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
  /** 是否正在流式接收中 */
  streaming?: boolean
  /** 思考过程内容（reasoning事件累积） */
  reasoning?: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const hasStarted = ref(false)
const messageListRef = ref<HTMLElement | null>(null)

/** 当前SSE连接的关闭函数 */
let closeStream: (() => void) | null = null

/** 是否有正在流式接收的消息 */
const hasStreamingMessage = computed(() => messages.value.some((m) => m.streaming))

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

/** 组件卸载时关闭SSE连接 */
onUnmounted(() => {
  if (closeStream) {
    closeStream()
    closeStream = null
  }
})

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

  // 添加一条空的AI消息（流式填充）
  const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(assistantMsg)
  const assistantIdx = messages.value.length - 1

  // 调用流式API
  closeStream = insightDashboardApi.streamAiChat(
    {
      dashboardId: props.dashboardId || undefined,
      name: isGenerateMode.value && !hasStarted.value ? generateForm.name : undefined,
      datasourceId: isGenerateMode.value && !hasStarted.value ? generateForm.datasourceId : undefined,
      message,
    },
    {
      // onReasoning: AI思考过程增量
      onReasoning: (text: string) => {
        assistantMsg.reasoning = (assistantMsg.reasoning || '') + text
        scrollToBottom()
      },
      // onContent: AI最终结果文本增量
      onContent: (text: string) => {
        assistantMsg.content += text
        scrollToBottom()
      },
      // onResult: 最终仪表盘数据
      onResult: (dashboard: InsightDashboard) => {
        hasStarted.value = true
        assistantMsg.streaming = false

        // 追加成功提示
        const successMsg = isModifyMode.value
          ? t('insight.aiChatSuccess')
          : t('insight.generateSuccess')
        assistantMsg.content = assistantMsg.content
          ? `${assistantMsg.content}\n\n${successMsg}`
          : successMsg

        // 通知父组件刷新，传递仪表盘ID
        const resultId = dashboard?.id?.toString() || props.dashboardId || ''
        emit('dashboard-updated', resultId)

        ElMessage.success(successMsg)
        loading.value = false
        closeStream = null
        scrollToBottom()
      },
      // onError: 错误
      onError: (errorMsg: string) => {
        assistantMsg.streaming = false
        assistantMsg.content = errorMsg || (isModifyMode.value ? t('insight.aiChatFailed') : t('insight.generateFailed'))
        ElMessage.error(assistantMsg.content)
        loading.value = false
        closeStream = null
        scrollToBottom()
      },
    },
  )
}
</script>

<style scoped>
.ai-chat-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-card);
}

/* 头部 */
.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--db-card) 0%, rgba(99, 102, 241, 0.04) 100%);
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
  color: var(--db-text);
}

/* 生成模式表单 */
.ai-chat-generate-form {
  padding: 14px;
  border-bottom: 1px solid var(--db-border);
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
  color: var(--db-text-muted);
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
  white-space: pre-wrap;
}

.ai-chat-message.user .message-content {
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-chat-message.assistant .message-content {
  background: var(--db-hover);
  color: var(--db-text);
  border-bottom-left-radius: 4px;
}

/* 流式光标 */
.cursor-blink {
  animation: blink 1s step-end infinite;
  color: var(--db-accent);
  font-weight: bold;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 思考过程 */
.reasoning-block {
  margin-bottom: 8px;
  padding: 8px 10px;
  background: rgba(99, 102, 241, 0.06);
  border-left: 3px solid var(--db-accent);
  border-radius: 4px;
}

.reasoning-label {
  font-size: 11px;
  color: var(--db-accent);
  font-weight: 600;
  margin-bottom: 4px;
}

.reasoning-text {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
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
  background: var(--db-text-muted);
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
  padding: 10px 12px;
  border-top: 1px solid var(--db-border);
  flex-shrink: 0;
  background: var(--db-bg);
}

.ai-chat-input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  padding: 6px 8px 6px 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-chat-input-box:hover {
  border-color: var(--db-accent);
}

.ai-chat-input-box:focus-within {
  border-color: var(--db-accent);
  box-shadow: 0 0 0 3px var(--db-accent-light);
}

.ai-chat-input-box :deep(.el-textarea) {
  flex: 1;
}

.ai-chat-input-box :deep(.el-textarea__inner) {
  font-size: 13px;
  min-height: 32px !important;
  max-height: 120px;
  padding: 4px 0;
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
  color: var(--db-text-muted);
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
