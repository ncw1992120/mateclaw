<template>
  <div class="chat-view">
    <!-- Chat Area -->
    <div ref="chatAreaRef" class="chat-area">
      <!-- Empty State -->
      <div v-if="chatStore.messages.length === 0" class="empty-state">
        <div class="empty-icon">💬</div>
        <p class="empty-text">{{ t('chat.welcome') }}</p>
        <div v-if="chatStore.conversations.length > 0" class="history-list">
          <p class="history-title">{{ t('conversation.history') }}</p>
          <div
            v-for="conv in chatStore.conversations"
            :key="conv.conversationId"
            class="history-item"
            @click="handleSwitchConversation(conv.conversationId)"
          >
            <span class="history-item-title">{{ conv.title || t('conversation.untitled') }}</span>
            <span class="history-item-meta">{{ conv.messageCount }} {{ t('conversation.messages') }}</span>
            <button class="history-item-delete" :title="t('conversation.delete')" @click.stop="handleDeleteConversation(conv.conversationId)">✕</button>
          </div>
        </div>
      </div>

      <!-- Messages -->
      <template v-for="(msg, index) in chatStore.messages" :key="index">
        <!-- User Message -->
        <div v-if="msg.role === 'user'" class="msg user">
          <div class="bubble user-bubble">{{ msg.content }}</div>
        </div>

        <!-- AI Message -->
        <div v-else class="msg ai">
          <div class="avatar">AI</div>
          <div class="bubble ai-bubble">
            <!-- Token & model info (右上角) -->
            <div v-if="getTokenInfo(msg)" class="meta-header">
              <span class="meta-token">{{ getTokenInfo(msg) }}</span>
            </div>

            <!-- Tool calls (seg-tool 卡片，参考 mateclaw-ui ToolCallSegment 样式) -->
            <template v-for="(tc, tcIdx) in getToolCalls(msg)" :key="`tool-${tcIdx}`">
              <div
                class="seg-tool"
                :class="{
                  'is-running': tc.status === 'running',
                  'is-success': tc.status === 'completed' && tc.success !== false,
                  'is-error': tc.status === 'error' || tc.success === false,
                }"
              >
                <div class="seg-tool__header" @click="toggleToolExpand(tcIdx)">
                  <span class="seg-tool__status">
                    <span v-if="tc.status === 'running'" class="spin-icon">⟳</span>
                    <span v-else-if="tc.status === 'completed' && tc.success !== false">✓</span>
                    <span v-else>✕</span>
                  </span>
                  <span class="seg-tool__type-icon">⚙</span>
                  <span class="seg-tool__name">{{ tc.name || tc.toolName }}</span>
                  <span v-if="truncateArgs(tc.arguments as string)" class="seg-tool__args">{{ truncateArgs(tc.arguments as string) }}</span>
                  <span
                    v-if="tc.result != null"
                    class="seg-tool__arrow"
                    :class="{ 'is-open': expandedTools.has(tcIdx) }"
                  >▾</span>
                </div>
                <Transition name="seg-slide">
                  <div v-if="expandedTools.has(tcIdx) && tc.result != null" class="seg-tool__body">
                    <pre>{{ formatResultPreview(tc.result as string) }}</pre>
                  </div>
                </Transition>
              </div>
            </template>

            <!-- Thinking (collapsible) -->
            <el-collapse v-if="msg.thinking" class="thinking-collapse">
              <el-collapse-item :title="t('chat.thinking')">
                <div class="thinking-content">{{ msg.thinking }}</div>
              </el-collapse-item>
            </el-collapse>

            <!-- Text content -->
            <div v-if="msg.content" class="msg-text" v-html="renderMarkdown(msg.content)" />

            <!-- Streaming cursor -->
            <span
              v-if="chatStore.isStreaming && index === chatStore.messages.length - 1 && !msg.content"
              class="streaming-cursor"
            />
          </div>
        </div>

        <!-- Rich Cards for this message -->
        <template v-if="msg.cards && msg.cards.length">
          <template v-for="(card, cardIdx) in msg.cards" :key="`${index}-${cardIdx}`">
            <!-- QueryPlan Card -->
            <div v-if="card.type === 'queryplan'" class="qp-box">
              <div class="qp-accent"></div>
              <div class="qp-title">{{ t('chat.queryPlanTitle') }}</div>
              <div
                v-for="(val, key) in (card.data as QueryPlanData)"
                :key="key"
                class="qp-row"
              >
                <span class="qp-label">{{ queryPlanLabels[key as string] || key }}</span>
                <span class="qp-val">{{ val }}</span>
                <button class="qp-modify" @click="handleModify(key as string)">{{ t('chat.modify') }}</button>
              </div>
              <button
                class="qp-confirm"
                :class="{ confirmed: queryPlanConfirmed[`${index}-${cardIdx}`] }"
                :disabled="queryPlanConfirmed[`${index}-${cardIdx}`]"
                @click="confirmQueryPlan(index, cardIdx)"
              >
                {{ queryPlanConfirmed[`${index}-${cardIdx}`] ? `${t('chat.confirmed')} ✓` : t('chat.confirm') }}
              </button>
              <div style="clear:both"></div>
            </div>

            <!-- Insight Bar -->
            <div v-else-if="card.type === 'insight'" class="insight-bar">
              💡 AI {{ t('chat.insight') }}：{{ card.data }}
            </div>

            <!-- Chart Card -->
            <div v-else-if="card.type === 'chart'" class="chart-box">
              <div class="chart-title">{{ (card.data as ChartCardData).title }}</div>
              <div :ref="(el) => setChartRef(el as HTMLElement, index, cardIdx)" class="mid-chart"></div>
            </div>

            <!-- Clarify Card -->
            <div v-else-if="card.type === 'clarify'" class="clarify-card">
              <div class="clarify-title">🔄 {{ (card.data as ClarifyData).title }}</div>
              <div class="clarify-desc">{{ (card.data as ClarifyData).desc }}</div>
              <div class="clarify-options">
                <label
                  v-for="(opt, optIdx) in (card.data as ClarifyData).options"
                  :key="optIdx"
                  class="clarify-opt"
                  :class="{ selected: clarifySelected[`${index}-${cardIdx}`] === optIdx }"
                  @click="clarifySelected[`${index}-${cardIdx}`] = optIdx"
                >
                  <input
                    type="radio"
                    :name="`clarify-${index}-${cardIdx}`"
                    :checked="clarifySelected[`${index}-${cardIdx}`] === optIdx"
                  />
                  {{ opt.label }}
                  <span v-if="opt.recommend" class="recommend">{{ t('chat.recommended') }}</span>
                </label>
              </div>
              <button
                class="clarify-confirm"
                :class="{ confirmed: clarifyConfirmed[`${index}-${cardIdx}`] }"
                :disabled="clarifyConfirmed[`${index}-${cardIdx}`]"
                @click="confirmClarify(index, cardIdx)"
              >
                {{ clarifyConfirmed[`${index}-${cardIdx}`] ? `${t('chat.confirmed')} ✓` : t('chat.confirmSelection') }}
              </button>
            </div>

            <!-- Dashboard Preview Card -->
            <div v-else-if="card.type === 'dashboard'" class="dash-card">
              <div class="dash-card-title">📊 {{ t('chat.dashboardGenerated') }}</div>
              <div class="dash-kpi-row">
                <div v-for="(kpi, kpiIdx) in (card.data as DashboardCardData).kpis" :key="kpiIdx" class="dash-kpi">
                  <div class="dash-kpi-val">{{ kpi.val }}</div>
                  <div class="dash-kpi-name">
                    {{ kpi.name }}
                    <span :style="{ color: kpi.up ? 'var(--main-orange)' : 'var(--mid-grey)' }">{{ kpi.chg }}</span>
                  </div>
                </div>
              </div>
              <div class="dash-link" @click="$emit('openDashboard')">{{ t('chat.viewDashboard') }} →</div>
            </div>

            <!-- Followup Chips -->
            <div v-else-if="card.type === 'followup'" class="followup-chips">
              <span
                v-for="(chip, chipIdx) in (card.data as FollowupData)"
                :key="chipIdx"
                class="followup-chip"
                @click="handleFollowup(chip)"
              >
                {{ chip }}
              </span>
            </div>

            <!-- Feedback -->
            <div v-else-if="card.type === 'feedback'" class="feedback">
              <span
                v-for="opt in feedbackOptions"
                :key="opt.key"
                :class="{ active: feedbackState[`${index}-${cardIdx}`] === opt.key }"
                @click="feedbackState[`${index}-${cardIdx}`] = opt.key"
              >
                {{ opt.label }}
              </span>
            </div>
          </template>
        </template>
      </template>

      <!-- Streaming cursor at end -->
      <div
        v-if="chatStore.isStreaming && chatStore.messages.length > 0 && chatStore.messages[chatStore.messages.length - 1]?.content"
        class="msg ai"
      >
        <span class="streaming-cursor-end" />
      </div>
    </div>

    <!-- Input Bar -->
    <div class="input-bar">
      <textarea
        v-model="inputMessage"
        class="chat-input"
        :placeholder="chatStore.isStreaming ? t('chat.generating') : t('chat.placeholder')"
        :disabled="chatStore.isStreaming"
        rows="1"
        @keydown="handleKeydown"
      />
      <button v-if="chatStore.isStreaming" class="btn-stop" @click="handleStop">{{ t('chat.stop') }}</button>
      <button v-else class="btn-send" :disabled="!canSend" @click="handleSend">{{ t('chat.send') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import * as echarts from 'echarts'
import type { QueryPlanData, ChartCardData, ClarifyData, DashboardCardData, FollowupData } from '@/types'

const { t } = useI18n()
const chatStore = useChatStore()
const modelStore = useModelStore()

defineEmits<{
  openDashboard: []
}>()

/** QueryPlan 字段标签映射 */
const queryPlanLabels: Record<string, string> = {
  indicator: '指标',
  dimension: '维度',
  time: '时间',
  compare: '比较',
  sort: '排序',
  limit: '条数',
}

/** 反馈选项 */
const feedbackOptions = [
  { key: 'helpful', label: '👍 有帮助' },
  { key: 'inaccurate', label: '👎 不准确' },
  { key: 'correct', label: '✏️ 给出正确答案' },
]

/** 输入消息 */
const inputMessage = ref('')

/** 聊天区域容器引用 */
const chatAreaRef = ref<HTMLElement | null>(null)

/** QueryPlan 确认状态 */
const queryPlanConfirmed = reactive<Record<string, boolean>>({})

/** 澄清卡片选中状态 */
const clarifySelected = reactive<Record<string, number>>({})

/** 澄清卡片确认状态 */
const clarifyConfirmed = reactive<Record<string, boolean>>({})

/** 反馈状态 */
const feedbackState = reactive<Record<string, string>>({})

/** 工具调用展开状态（按消息索引+工具索引） */
const expandedTools = reactive<Set<number>>(new Set())

/** 图表实例映射 */
const chartInstances = new Map<string, echarts.ECharts>()
const chartRefs = new Map<string, HTMLElement>()

/** 是否可以发送 */
const canSend = computed(() => inputMessage.value.trim() && !chatStore.isStreaming && chatStore.currentAgentId)

/** Marked 实例 */
const markedInstance = new Marked({
  gfm: true,
  breaks: true,
})
  .use(markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  }))

/** 渲染 Markdown */
function renderMarkdown(content: string): string {
  const html = markedInstance.parse(content) as string
  return DOMPurify.sanitize(html)
}

/** 判断消息是否有可展示的 metadata */
function hasMetadata(msg: typeof chatStore.messages.value[0]): boolean {
  if (!msg.metadata || typeof msg.metadata !== 'object') return false
  const meta = msg.metadata as Record<string, unknown>
  const toolCalls = meta.toolCalls as Array<Record<string, unknown>> | undefined
  if (toolCalls && toolCalls.length > 0) return true
  if (meta.runtimeModel || meta.promptTokens || meta.completionTokens) return true
  return false
}

/** 提取工具调用列表 */
function getToolCalls(msg: typeof chatStore.messages.value[0]): Array<Record<string, unknown>> {
  if (!msg.metadata || typeof msg.metadata !== 'object') return []
  const meta = msg.metadata as Record<string, unknown>
  return (meta.toolCalls as Array<Record<string, unknown>>) || []
}

/** 格式化 token 和模型信息 */
function getTokenInfo(msg: typeof chatStore.messages.value[0]): string | null {
  if (!msg.metadata || typeof msg.metadata !== 'object') return null
  const meta = msg.metadata as Record<string, unknown>
  const parts: string[] = []
  const model = (meta.runtimeModel as string | undefined)
  const provider = (meta.runtimeProviderId as string | undefined)
  const promptTok = (meta.promptTokens as number | undefined)
  const completionTok = (meta.completionTokens as number | undefined)
  if (model) parts.push(model)
  if (provider && provider !== model) parts.push(provider)
  if (promptTok != null || completionTok != null) {
    parts.push(`${promptTok ?? 0}+${completionTok ?? 0} tokens`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

/** 切换工具调用展开/收起 */
function toggleToolExpand(toolIdx: number): void {
  if (expandedTools.has(toolIdx)) {
    expandedTools.delete(toolIdx)
  } else {
    expandedTools.add(toolIdx)
  }
}

/** 截断工具参数（显示在 header 行） */
function truncateArgs(args: string | undefined): string {
  if (!args) return ''
  try {
    const parsed = JSON.parse(args)
    const str = JSON.stringify(parsed)
    return str.length > 60 ? str.slice(0, 60) + '…' : str
  } catch {
    return args.length > 60 ? args.slice(0, 60) + '…' : args
  }
}

/** 格式化工具返回结果预览（展开后显示） */
function formatResultPreview(result: string): string {
  if (!result) return ''
  try {
    const parsed = JSON.parse(result)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return result.length > 500 ? result.slice(0, 500) + '...' : result
  }
}

/** 设置图表容器引用 */
function setChartRef(el: HTMLElement | null, msgIndex: number, cardIndex: number): void {
  if (!el) return
  const key = `${msgIndex}-${cardIndex}`
  chartRefs.set(key, el)
  nextTick(() => initChart(key, msgIndex, cardIndex))
}

/** 初始化图表 */
function initChart(key: string, msgIndex: number, cardIndex: number): void {
  const el = chartRefs.get(key)
  if (!el) return

  const msg = chatStore.messages[msgIndex]
  if (!msg?.cards) return

  const card = msg.cards[cardIndex]
  if (card?.type !== 'chart') return

  const chartData = card.data as ChartCardData
  const instance = echarts.init(el)
  chartInstances.set(key, instance)

  const isBar = chartData.series[0]?.type === 'bar'
  instance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 20 },
    xAxis: { type: 'category', data: chartData.xData, axisLabel: { fontSize: 9, color: '#aaa' } },
    yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#aaa' }, splitLine: { lineStyle: { color: '#eee' } } },
    series: chartData.series.map(s => ({
      name: s.name,
      type: s.type || 'line',
      smooth: !isBar,
      data: s.data,
      lineStyle: { color: '#F05A23', width: 2 },
      itemStyle: { color: '#F05A23' },
      areaStyle: s.type !== 'bar' ? { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(240,90,35,0.2)' }, { offset: 1, color: 'rgba(240,90,35,0)' }] } } : undefined,
      barWidth: isBar ? 24 : undefined,
    }))
  })
}

/** 发送消息 */
function handleSend(): void {
  const message = inputMessage.value.trim()
  if (!message || chatStore.isStreaming || !chatStore.currentAgentId) return
  inputMessage.value = ''
  const modelName = modelStore.activeModel?.modelName
  chatStore.sendMessage(chatStore.currentAgentId, message, modelName)
}

/** 键盘事件处理：Enter发送，Ctrl+Enter换行 */
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    if (event.ctrlKey || event.metaKey) {
      // Ctrl+Enter 或 Cmd+Enter 换行
      event.preventDefault()
      const target = event.target as HTMLTextAreaElement
      const start = target.selectionStart
      const end = target.selectionEnd
      const value = inputMessage.value
      inputMessage.value = value.substring(0, start) + '\n' + value.substring(end)
      // 设置光标位置到新行的开头
      nextTick(() => {
        target.selectionStart = target.selectionEnd = start + 1
      })
    } else {
      // 单独 Enter 发送消息
      event.preventDefault()
      handleSend()
    }
  }
}

/** 停止生成 */
function handleStop(): void {
  chatStore.stopChat()
}

/** 切换到历史会话 */
async function handleSwitchConversation(convId: string): Promise<void> {
  await chatStore.switchConversation(convId)
  if (chatStore.conversations.length === 0) {
    chatStore.fetchConversations()
  }
}

/** 删除历史会话 */
async function handleDeleteConversation(convId: string): Promise<void> {
  await chatStore.deleteConversation(convId)
}

/** 追问点击 */
function handleFollowup(text: string): void {
  if (chatStore.isStreaming || !chatStore.currentAgentId) return
  const modelName = modelStore.activeModel?.modelName
  chatStore.sendMessage(chatStore.currentAgentId, text, modelName)
}

/** 确认 QueryPlan */
function confirmQueryPlan(msgIndex: number, cardIndex: number): void {
  queryPlanConfirmed[`${msgIndex}-${cardIndex}`] = true
}

/** 确认澄清卡片 */
function confirmClarify(msgIndex: number, cardIndex: number): void {
  clarifyConfirmed[`${msgIndex}-${cardIndex}`] = true
}

/** 修改 QueryPlan 字段 */
function handleModify(field: string): void {
  console.log('Modify field:', field)
}

/** 滚动到底部 */
function scrollToBottom(): void {
  nextTick(() => {
    if (chatAreaRef.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
    }
  })
}

/** 窗口缩放处理 */
function handleResize(): void {
  chartInstances.forEach(instance => instance.resize())
}

watch(() => chatStore.messages.length, () => scrollToBottom())
watch(() => chatStore.messages[chatStore.messages.length - 1]?.content, () => scrollToBottom())

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(instance => instance.dispose())
  chartInstances.clear()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
  opacity: 0.3;
}

.empty-text {
  font-size: 16px;
  color: var(--muted);
}

.history-list {
  margin-top: 20px;
  width: 100%;
  max-width: 400px;
  text-align: left;
}

.history-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--body-text);
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--light-grey);
}

.history-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  gap: 8px;
}

.history-item:hover {
  background: var(--very-light-orange);
}

.history-item-title {
  flex: 1;
  font-size: 13px;
  color: var(--dark-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item-meta {
  font-size: 11px;
  color: var(--muted);
  flex-shrink: 0;
}

.history-item-delete {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-item:hover .history-item-delete {
  opacity: 1;
}

.history-item-delete:hover {
  background: #fee;
  color: #e53e3e;
}

.msg {
  display: flex;
  gap: 8px;
  max-width: 80%;
}

.msg.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg.ai {
  align-self: flex-start;
}

.avatar {
  width: 28px;
  height: 22px;
  border-radius: 6px;
  background: var(--main-orange);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bubble {
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 12px;
  line-height: 1.6;
}

.ai-bubble {
  background: #fff;
  border: 1px solid var(--light-grey);
  color: var(--body-text);
}

.user-bubble {
  background: var(--light-orange);
  border: 1px solid var(--main-orange);
  color: var(--dark-text);
}

.msg-text {
  color: var(--body-text);
}

.msg-text :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
}

.msg-text :deep(code) {
  font-size: 13px;
}

.msg-text :deep(:not(pre) > code) {
  background: #e5e7eb;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 12px;
}

.msg-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 12px;
}

.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid #ddd5cc;
  padding: 7px 12px;
  text-align: left;
}

.msg-text :deep(th) {
  background: #f5f0eb;
  font-weight: 600;
}

.msg-text :deep(blockquote) {
  border-left: 4px solid #e0b8a0;
  margin: 10px 0;
  padding: 6px 14px;
  color: #6b5344;
  background: rgba(217, 119, 87, 0.04);
}

.msg-text :deep(ul),
.msg-text :deep(ol) {
  padding-left: 1.4rem;
  margin: 6px 0;
}

.msg-text :deep(p) {
  margin: 6px 0;
}

.streaming-cursor {
  display: inline-block;
  width: 8px;
  height: 16px;
  background: var(--mid-grey);
  animation: pulse 1s ease-in-out infinite;
}

.streaming-cursor-end {
  display: inline-block;
  width: 8px;
  height: 16px;
  background: var(--mid-grey);
  animation: pulse 1s ease-in-out infinite;
  margin-left: 36px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* QueryPlan */
.qp-box {
  background: var(--light-orange);
  border: 2px solid var(--main-orange);
  border-radius: 8px;
  padding: 12px 16px;
  margin-left: 36px;
  position: relative;
  align-self: flex-start;
  max-width: 80%;
}

.qp-accent {
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background: var(--main-orange);
  border-radius: 8px 0 0 8px;
}

.qp-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--dark-orange);
  margin-bottom: 8px;
  padding-left: 8px;
}

.qp-row {
  display: flex;
  align-items: center;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--dark-text);
}

.qp-label {
  width: 36px;
  color: var(--mid-grey);
  font-weight: 600;
  flex-shrink: 0;
}

.qp-val {
  flex: 1;
  color: var(--dark-text);
}

.qp-modify {
  padding: 1px 8px;
  border-radius: 10px;
  border: 1px solid var(--main-orange);
  background: #fff;
  font-size: 10px;
  color: var(--main-orange);
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 4px;
  font-family: inherit;
}

.qp-modify:hover {
  background: var(--main-orange);
  color: #fff;
}

.qp-confirm {
  float: right;
  padding: 4px 16px;
  border-radius: 8px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  margin-top: 8px;
  cursor: pointer;
  font-family: inherit;
}

.qp-confirm.confirmed {
  background: #999;
  cursor: default;
}

/* Insight Bar */
.insight-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--very-light-orange);
  border: 1px solid var(--light-orange);
  border-radius: 8px;
  padding: 8px 12px;
  margin-left: 36px;
  font-size: 12px;
  color: var(--blue-grey);
  align-self: flex-start;
  max-width: 80%;
}

/* Chart Card */
.chart-box {
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  padding: 12px;
  margin-left: 36px;
  align-self: flex-start;
  max-width: 80%;
}

.chart-title {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 6px;
}

.mid-chart {
  width: 100%;
  height: 140px;
}

/* Clarify Card */
.clarify-card {
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  padding: 12px 16px;
  margin-left: 36px;
  position: relative;
  align-self: flex-start;
  max-width: 80%;
}

.clarify-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background: var(--blue-grey);
  border-radius: 8px 0 0 8px;
}

.clarify-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--blue-grey);
  margin-bottom: 4px;
  padding-left: 8px;
}

.clarify-desc {
  font-size: 12px;
  color: var(--body-text);
  margin-bottom: 10px;
  padding-left: 8px;
}

.clarify-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
  padding-left: 8px;
}

.clarify-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  cursor: pointer;
  font-size: 12px;
  color: var(--dark-text);
  transition: all 0.15s;
}

.clarify-opt:hover {
  border-color: var(--main-orange);
  background: var(--very-light-orange);
}

.clarify-opt.selected {
  border-color: var(--main-orange);
  background: var(--very-light-orange);
}

.clarify-opt input[type="radio"] {
  accent-color: var(--main-orange);
}

.clarify-opt .recommend {
  font-size: 10px;
  color: var(--main-orange);
  font-weight: 600;
  margin-left: 4px;
}

.clarify-confirm {
  display: block;
  margin: 0 auto;
  padding: 6px 24px;
  border-radius: 8px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
}

.clarify-confirm.confirmed {
  background: #999;
  cursor: default;
}

/* Dashboard Preview Card */
.dash-card {
  background: #fff;
  border: 1px solid var(--light-orange);
  border-radius: 8px;
  padding: 12px 16px;
  margin-left: 36px;
  align-self: flex-start;
  max-width: 80%;
}

.dash-card-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--dark-orange);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dash-kpi-row {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
}

.dash-kpi {
  text-align: center;
  flex: 1;
}

.dash-kpi-val {
  font-size: 16px;
  font-weight: 700;
  color: var(--dark-text);
}

.dash-kpi-name {
  font-size: 10px;
  color: var(--muted);
}

.dash-link {
  font-size: 12px;
  color: var(--main-orange);
  font-weight: 600;
  cursor: pointer;
  text-align: right;
}

.dash-link:hover {
  text-decoration: underline;
}

/* Followup Chips */
.followup-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0 0 36px;
  align-self: flex-start;
}

.followup-chip {
  padding: 6px 14px;
  border-radius: 16px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--main-orange);
  background: var(--very-light-orange);
  color: var(--dark-orange);
  transition: all 0.15s;
}

.followup-chip:hover {
  background: var(--main-orange);
  color: #fff;
}

/* Feedback */
.feedback {
  display: flex;
  gap: 16px;
  padding: 8px 0 0 36px;
  font-size: 11px;
  color: var(--muted);
  align-self: flex-start;
}

.feedback span {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.15s;
}

.feedback span:hover {
  background: var(--lighter-grey);
}

.feedback span.active {
  background: var(--very-light-orange);
  color: var(--dark-orange);
}

/* Input Bar */
.input-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--light-grey);
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  min-height: 40px;
  max-height: 120px;
  border-radius: 16px;
  border: 1px solid var(--light-grey);
  padding: 10px 16px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  resize: none;
  line-height: 1.5;
}

.chat-input:focus {
  border-color: var(--main-orange);
}

.btn-send {
  height: 40px;
  border-radius: 16px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  padding: 0 20px;
  cursor: pointer;
  font-family: inherit;
}

.btn-send:disabled {
  background: var(--light-grey);
  color: var(--muted);
  cursor: default;
}

.btn-stop {
  height: 40px;
  border-radius: 16px;
  border: 2px solid #e53e3e;
  background: #fff;
  color: #e53e3e;
  font-size: 13px;
  font-weight: 700;
  padding: 0 20px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.btn-stop:hover {
  background: #e53e3e;
  color: #fff;
}

/* Thinking Collapse */
:deep(.thinking-collapse) {
  border: none;
  margin-bottom: 8px;
}

:deep(.el-collapse-item__header) {
  font-size: 12px;
  color: #9ca3af;
  height: 28px;
  line-height: 28px;
  border: none;
  background: transparent;
}

:deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

:deep(.el-collapse-item__content) {
  padding-bottom: 4px;
}

.thinking-content {
  font-size: 12px;
  color: var(--muted);
  white-space: pre-wrap;
}

/* Metadata */
.meta-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 6px;
}

.meta-token {
  font-size: 10px;
  color: var(--muted);
  background: #f7f8fa;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid #eee;
}

/* seg-tool (参考 mateclaw-ui ToolCallSegment) */
.seg-tool {
  border-left: 3px solid #b0c4de;
  background: #fafbfd;
  border-radius: 6px;
  margin-bottom: 6px;
  overflow: hidden;
  transition: transform 0.15s, box-shadow 0.15s;
}

.seg-tool:hover {
  transform: translateX(2px);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.seg-tool.is-running { border-left-color: #409eff; }
.seg-tool.is-success { border-left-color: #67c23a; }
.seg-tool.is-error   { border-left-color: #f56c6c; }

.seg-tool__header {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 10px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  line-height: 1.3;
}

.seg-tool__status {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #909399;
}

.is-running .seg-tool__status { background: #409eff; }
.is-success .seg-tool__status { background: #67c23a; }
.is-error .seg-tool__status   { background: #f56c6c; }

.spin-icon {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.seg-tool__type-icon {
  font-size: 11px;
  opacity: 0.6;
}

.seg-tool__name {
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.seg-tool__args {
  color: #909399;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
  flex: 1;
  min-width: 0;
}

.seg-tool__arrow {
  flex-shrink: 0;
  font-size: 10px;
  color: #c0c4cc;
  transition: transform 0.2s;
  margin-left: auto;
}

.seg-tool__arrow.is-open {
  transform: rotate(180deg);
}

.seg-tool__body {
  padding: 0 10px 6px 22px;
}

.seg-tool__body pre {
  margin: 0;
  padding: 8px 10px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--body-text);
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* seg-slide transition (Vue Transition) */
.seg-slide-enter-active,
.seg-slide-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.seg-slide-enter-from,
.seg-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.seg-slide-enter-to,
.seg-slide-leave-from {
  opacity: 1;
  max-height: 240px;
}
</style>
