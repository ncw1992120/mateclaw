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

    <!-- 消息展示 + 输入框整体容器 -->
    <div class="ai-chat-body">
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
                <div class="reasoning-header" @click="toggleReasoning(msg)">
                  <el-icon :class="['reasoning-arrow', { expanded: msg.reasoningExpanded }]"><ArrowRight /></el-icon>
                  <span class="reasoning-label">{{ t('insight.aiReasoning') }}</span>
                  <span class="reasoning-count">{{ (msg.reasoning || '').length }} 字</span>
                </div>
                <div v-show="msg.reasoningExpanded" class="reasoning-text" v-html="formatReasoningText(msg.reasoning)"></div>
                <div v-if="msg.reasoningExpanded && msg.streaming && !msg.content" class="reasoning-cursor"><span class="cursor-blink">|</span></div>
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

      <!-- 输入框（与消息列表同属一个整体容器） -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, nextTick, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Promotion, Close, ArrowRight } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { Datasource, InsightDashboard, ChatHistoryMessage } from '@/types'
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
  /** 思考过程内容（reasoning 事件累积） */
  reasoning?: string
  /** 思考过程是否展开（默认折叠） */
  reasoningExpanded?: boolean
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const hasStarted = ref(false)
const messageListRef = ref<HTMLElement | null>(null)

/** 当前 SSE 连接的关闭函数 */
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

/**
 * 思考过程文本节流更新器。
 * AgentScope 默认 incremental=true，推送的是增量片段且频率很高，
 * 直接每次拼接 + formatReasoningText 正则处理 + v-html DOM 重建在高频下产生闪现。
 * 通过节流将实际 DOM 更新限制在固定间隔，流结束后 flush 确保最终文本完整。
 */
let reasoningFlushTimer: ReturnType<typeof setTimeout> | null = null
let reasoningPendingText = ''
const REASONING_THROTTLE_MS = 150

/** 将缓存的增量文本拼接到消息并触发 DOM 更新 */
function flushReasoning(assistantIdx: number): void {
  reasoningFlushTimer = null
  if (reasoningPendingText) {
    const msg = messages.value[assistantIdx]
    if (msg) {
      msg.reasoning = (msg.reasoning || '') + reasoningPendingText
      reasoningPendingText = ''
      scrollToBottom()
    }
  }
}

/** 节流更新思考过程文本（增量拼接） */
function throttledUpdateReasoning(text: string, assistantIdx: number): void {
  reasoningPendingText += text
  if (reasoningFlushTimer === null) {
    reasoningFlushTimer = setTimeout(() => flushReasoning(assistantIdx), REASONING_THROTTLE_MS)
  }
}

/** 立即刷新思考过程文本（用于流结束等场景，确保最终文本完整写入） */
function flushReasoningNow(assistantIdx: number): void {
  if (reasoningFlushTimer !== null) {
    clearTimeout(reasoningFlushTimer)
    reasoningFlushTimer = null
  }
  if (reasoningPendingText) {
    const msg = messages.value[assistantIdx]
    if (msg) {
      msg.reasoning = (msg.reasoning || '') + reasoningPendingText
    }
    reasoningPendingText = ''
  }
}

/** 格式化思考过程文本，增强可读性 */
function formatReasoningText(text: string): string {
  if (!text) return ""

  let formatted = text

  // 1. 移除 Markdown 粗体标记中的"思考："、"回答："等前缀
  formatted = formatted.replace(/\*\* 思考：\*\*/g, "")
  formatted = formatted.replace(/\*\* 回答：\*\*/g, "")
  formatted = formatted.replace(/\*\*Response:\*\*/g, "")
  formatted = formatted.replace(/\*\*Thinking:\*\*/g, "")

  // 2. 处理代码块 ```xxx``` -> <pre><code>xxx</code></pre>
  formatted = formatted.replace(/```([\s\S]*?)```/g, (match, code) => {
    return '<pre class="reasoning-code"><code>' + escapeHtml(code.trim()) + '</code></pre>'
  })

  // 3. 处理行内代码 `xxx` -> <code>xxx</code>
  formatted = formatted.replace(/`([^`]+)`/g, '<code class="reasoning-inline-code">$1</code>')

  // 4. 处理粗体 **xxx** -> <strong>xxx</strong>
  formatted = formatted.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")

  // 5. 处理有序列表 1. xxx 2. xxx -> <ol><li>xxx</li></ol>（先处理，此时\n还在）
  const lines = formatted.split('\n')
  const processedLines: string[] = []
  let inOrderedList = false
  let inUnorderedList = false

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const orderedMatch = line.match(/^\s*(\d+)\.\s+(.*)$/)
    const unorderedMatch = line.match(/^\s*[-*•]\s+(.*)$/)

    if (orderedMatch) {
      // 有序列表项
      if (!inOrderedList) {
        inOrderedList = true
        processedLines.push('<ol class="reasoning-list">')
      }
      processedLines.push(`<li>${orderedMatch[2]}</li>`)
    } else if (unorderedMatch) {
      // 无序列表项
      if (!inUnorderedList) {
        inUnorderedList = true
        processedLines.push('<ul class="reasoning-list">')
      }
      processedLines.push(`<li>${unorderedMatch[1]}</li>`)
    } else {
      // 普通文本行
      if (inOrderedList) {
        processedLines.push('</ol>')
        inOrderedList = false
      }
      if (inUnorderedList) {
        processedLines.push('</ul>')
        inUnorderedList = false
      }
      // 保留空行，用于段落分隔换行
      processedLines.push(line)
    }
  }

  // 关闭未结束的列表
  if (inOrderedList) processedLines.push('</ol>')
  if (inUnorderedList) processedLines.push('</ul>')

  formatted = processedLines.join('\n')

  // 6. 处理换行 -> <br>（列表处理完后）
  formatted = formatted.replace(/\n/g, "<br>")

  return formatted
}

/** 转义 HTML 特殊字符 */
function escapeHtml(text: string): string {
  const div = document.createElement("div")
  div.textContent = text
  return div.innerHTML
}

/** 切换思考过程展开/收起状态 */
function toggleReasoning(msg: ChatMessage): void {
  msg.reasoningExpanded = !msg.reasoningExpanded
}

/** 组件卸载时关闭 SSE 连接 */
onUnmounted(() => {
  if (closeStream) {
    closeStream()
    closeStream = null
  }
  // 清理思考过程节流定时器
  if (reasoningFlushTimer !== null) {
    clearTimeout(reasoningFlushTimer)
    reasoningFlushTimer = null
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

  // 收集已有对话作为多轮对话上下文（排除当前刚加入的用户消息和正在流式的助手消息）
  const historyMessages: ChatHistoryMessage[] = messages.value
    .slice(0, -1)
    .filter((m) => !m.streaming && m.content.trim())
    .map((m) => ({ role: m.role, content: m.content }))

  // 添加一条空的 AI 消息（流式填充）
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  const assistantIdx = messages.value.length - 1

  // 调用流式 API
  closeStream = insightDashboardApi.streamAiChat(
    {
      dashboardId: props.dashboardId || undefined,
      name: isGenerateMode.value && !hasStarted.value ? generateForm.name : undefined,
      datasourceId: isGenerateMode.value && !hasStarted.value ? generateForm.datasourceId : undefined,
      message,
      historyMessages,
    },
    {
      // onReasoning: AI 思考过程增量（AgentScope 默认 incremental=true，推送的是增量片段，需拼接）
      onReasoning: (text: string) => {
        // 通过代理对象修改，确保触发响应式更新
        const msg = messages.value[assistantIdx]
        // 流式输出思考过程时自动展开，让用户实时看到思考内容
        msg.reasoningExpanded = true
        // 节流更新：避免高频增量文本拼接 + formatReasoningText + v-html DOM 重建闪现
        throttledUpdateReasoning(text, assistantIdx)
      },
      // onContent: AI 最终结果文本增量
      onContent: (text: string) => {
        messages.value[assistantIdx].content += text
        scrollToBottom()
      },
      // onResult: 最终仪表盘数据
      onResult: (dashboard: InsightDashboard) => {
        hasStarted.value = true
        const msg = messages.value[assistantIdx]
        // 流式结束前 flush 节流缓存的最终思考文本
        flushReasoningNow(assistantIdx)
        msg.streaming = false
        // 流式结束，折叠思考过程面板
        msg.reasoningExpanded = false

        // 追加成功提示
        const successMsg = isModifyMode.value
          ? t('insight.aiChatSuccess')
          : t('insight.generateSuccess')
        msg.content = msg.content
          ? `${msg.content}\n\n${successMsg}`
          : successMsg

        // 通知父组件刷新，传递仪表盘 ID
        const resultId = dashboard?.id?.toString() || props.dashboardId || ''
        emit('dashboard-updated', resultId)

        ElMessage.success(successMsg)
        loading.value = false
        closeStream = null
        scrollToBottom()
      },
      // onError: 错误
      onError: (errorMsg: string) => {
        const msg = messages.value[assistantIdx]
        // 流式结束前 flush 节流缓存的最终思考文本
        flushReasoningNow(assistantIdx)
        msg.streaming = false
        // 流式结束，折叠思考过程面板
        msg.reasoningExpanded = false
        msg.content = errorMsg || (isModifyMode.value ? t('insight.aiChatFailed') : t('insight.generateFailed'))
        ElMessage.error(msg.content)
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

/* 消息展示 + 输入框整体容器 */
.ai-chat-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0 10px 10px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-card);
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
n.reasoning-cursor {
  display: inline;
}
  animation: blink 1s step-end infinite;
n.reasoning-cursor {
  display: inline;
}
  color: var(--db-accent);
n.reasoning-cursor {
  display: inline;
}
  font-weight: bold;
n.reasoning-cursor {
  display: inline;
}
}
n.reasoning-cursor {
  display: inline;
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

/* 思考过程折叠头部 */
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
  margin-bottom: 6px;
}

.reasoning-header:hover {
  opacity: 0.8;
}

.reasoning-arrow {
  width: 14px;
  height: 14px;
  transition: transform 0.2s;
  color: var(--db-accent);
}

.reasoning-arrow.expanded {
  transform: rotate(90deg);
}

.reasoning-label {
  font-size: 11px;
  color: var(--db-accent);
  font-weight: 600;
}

.reasoning-count {
  font-size: 10px;
  color: var(--db-text-muted);
  margin-left: auto;
}

/* 思考过程格式化样式 */
.reasoning-text {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.6;
}

.reasoning-text :deep(strong) {
  color: var(--db-text);
  font-weight: 600;
}

.reasoning-text :deep(code) {
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  color: #d946ef;
}

.reasoning-text :deep(pre) {
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 6px;
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.5;
  color: #e2e8f0;
}

.reasoning-text :deep(pre code) {
  background: transparent;
  padding: 0;
  border-radius: 0;
  color: inherit;
  font-size: inherit;
}

.reasoning-text :deep(ol),
.reasoning-text :deep(ul) {
  margin: 6px 0;
  padding-left: 20px;
}

.reasoning-text :deep(ol li),
.reasoning-text :deep(ul li) {
  margin: 4px 0;
  line-height: 1.5;
}

.reasoning-text :deep(ol) {
  list-style-type: decimal;
}

.reasoning-text :deep(ul) {
  list-style-type: disc;
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

/* 输入框（与消息列表同属整体容器，无分隔线） */
.ai-chat-input-wrapper {
  padding: 8px 12px 10px;
  border-top: 1px solid var(--db-border);
  flex-shrink: 0;
  background: transparent;
}

.ai-chat-input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--db-bg);
  border: 1px solid var(--db-border);
  border-radius: 10px;
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
