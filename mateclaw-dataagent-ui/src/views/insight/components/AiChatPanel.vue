<template>
  <div class="ai-chat-panel">
    <div class="ai-chat-header">
      <span class="ai-chat-title">{{ t('insight.aiChatTitle') }}</span>
      <el-button text size="small" @click="$emit('close')">✕</el-button>
    </div>

    <div ref="messageListRef" class="ai-chat-messages">
      <div v-if="messages.length === 0" class="ai-chat-empty">
        <div class="empty-hint">{{ t('insight.aiChatPlaceholder') }}</div>
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
        :placeholder="t('insight.aiChatPlaceholder')"
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
        :disabled="!inputText.trim() || loading"
        @click="handleSend"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import * as insightDashboardApi from '@/api/insight-dashboard'

defineOptions({
  name: 'AiChatPanel',
})

const props = defineProps<{
  /** 仪表盘 ID */
  dashboardId: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'schema-updated'): void
}>()

const { t } = useI18n()

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const messageListRef = ref<HTMLElement | null>(null)

/** 滚动到底部 */
function scrollToBottom(): void {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

/** 发送修改指令 */
async function handleSend(): Promise<void> {
  const instruction = inputText.value.trim()
  if (!instruction || loading.value) {
    return
  }

  // 添加用户消息
  messages.value.push({ role: 'user', content: instruction })
  inputText.value = ''
  scrollToBottom()

  loading.value = true
  try {
    const result = await insightDashboardApi.modify({
      dashboardId: props.dashboardId,
      instruction,
    }) as unknown as any

    // 添加AI回复
    messages.value.push({
      role: 'assistant',
      content: t('insight.aiChatSuccess'),
    })
    scrollToBottom()

    // 通知父组件刷新Schema
    emit('schema-updated')

    ElMessage.success(t('insight.aiChatSuccess'))
  } catch {
    messages.value.push({
      role: 'assistant',
      content: t('insight.aiChatFailed'),
    })
    scrollToBottom()
    ElMessage.error(t('insight.aiChatFailed'))
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
