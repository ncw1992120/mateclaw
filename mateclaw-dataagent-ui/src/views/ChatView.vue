<template>
  <div class="chat-view">
    <!-- Chat Area -->
    <div ref="chatAreaRef" class="chat-area">
      <!-- Empty State -->
      <div v-if="chatStore.messages.length === 0" class="empty-state">
        <div class="empty-avatar">
          <span class="avatar-bot">🤖</span>
        </div>
        <p class="empty-greeting">{{ greetingText }}</p>
        <!-- 智能问数快捷菜单 -->
        <div class="smart-ask-menu">
          <span
            v-for="item in smartAskMenuItems"
            :key="item.key"
            class="smart-ask-chip"
            @click="handleSmartAskMenu(item)"
          >
            <span class="chip-icon">{{ item.icon }}</span>
            <span class="chip-label">{{ t(item.label) }}</span>
          </span>
        </div>
      </div>

      <!-- Messages -->
      <template v-for="(msg, index) in chatStore.messages" :key="index">
        <!-- User Message -->
        <div v-if="msg.role === 'user'" class="msg user">
          <div class="user-content-wrapper">
            <div class="bubble user-bubble">{{ msg.content }}</div>
            <!-- 用户消息操作栏（气泡外左下角） -->
            <div class="msg-actions msg-actions--user">
              <button
                class="action-btn"
                :class="{ copied: copyState[index] === 'copied' }"
                type="button"
                :title="copyState[index] === 'copied' ? t('chat.copied') : t('chat.copy')"
                @click="handleCopy(index, msg.content)"
              >
                <el-icon v-if="copyState[index] !== 'copied'"><CopyDocument /></el-icon>
                <el-icon v-else><Select /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- AI Message -->
        <div v-else class="msg ai">
          <div class="avatar">AI</div>
          <div class="ai-content-wrapper">
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
            <!-- AI 消息操作栏（气泡外右下角） -->
            <div class="msg-actions msg-actions--ai">
              <button
                class="action-btn"
                :class="{ copied: copyState[index] === 'copied' }"
                type="button"
                :title="copyState[index] === 'copied' ? t('chat.copied') : t('chat.copy')"
                @click="handleCopy(index, msg.content)"
              >
                <el-icon v-if="copyState[index] !== 'copied'"><CopyDocument /></el-icon>
                <el-icon v-else><Select /></el-icon>
              </button>
              <button
                v-if="!chatStore.isStreaming"
                class="action-btn"
                type="button"
                :title="t('chat.regenerate')"
                @click="handleRegenerate(index)"
              >
                <el-icon><RefreshRight /></el-icon>
              </button>
            </div>
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

            <!-- ECharts Option Card（后端返回标准 ECharts option 时直接渲染） -->
            <div v-else-if="card.type === 'echarts'" class="echarts-box">
              <div v-if="(card.data as EChartsOptionData).title" class="echarts-title">{{ (card.data as EChartsOptionData).title }}</div>
              <div :ref="(el) => setEChartsRef(el as HTMLElement, index, cardIdx)" class="echarts-chart"></div>
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

    <!-- Datasource Selector Toolbar -->
    <div v-if="enabledDatasources.length > 0" class="ds-toolbar">
      <el-popover :width="260" trigger="click" placement="top-start" :persistent="false">
        <template #reference>
          <button class="ds-trigger" :class="{ active: chatStore.selectedDatasourceIds.length > 0 }">
            <span class="ds-trigger-icon">⚙</span>
            <span class="ds-trigger-text">{{ dsTriggerLabel }}</span>
            <span class="ds-trigger-arrow">▾</span>
          </button>
        </template>
        <div class="ds-popover">
          <div class="ds-popover-header">
            <span>{{ t('chat.datasourceScope') }}</span>
            <span
              v-if="chatStore.selectedDatasourceIds.length > 0"
              class="ds-popover-clear"
              @click="chatStore.selectedDatasourceIds = []"
            >{{ t('chat.clearDatasourceScope') }}</span>
          </div>
          <div class="ds-popover-list">
            <label
              v-for="ds in enabledDatasources"
              :key="ds.id"
              class="ds-popover-item"
              :class="{ checked: chatStore.selectedDatasourceIds.includes(ds.id) }"
            >
              <input
                type="checkbox"
                :checked="chatStore.selectedDatasourceIds.includes(ds.id)"
                @change="toggleDatasource(ds.id)"
              />
              <span class="ds-item-name">{{ ds.name }}</span>
              <span v-if="ds.sourceType" class="ds-item-type">{{ ds.sourceType }}</span>
            </label>
          </div>
        </div>
      </el-popover>
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
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import * as echarts from 'echarts'
import { CopyDocument, Select, RefreshRight } from '@element-plus/icons-vue'
import { copyToClipboard } from '@/utils/clipboard'
import * as datasourceApi from '@/api/datasource'
import type { QueryPlanData, ChartCardData, EChartsOptionData, ClarifyData, DashboardCardData, FollowupData, Datasource } from '@/types'

const { t } = useI18n()
const chatStore = useChatStore()
const modelStore = useModelStore()

defineEmits<{
  openDashboard: []
}>()

/** 智能问数快捷菜单项配置 */
const smartAskMenuItems = [
  { key: 'interpret', label: 'smartAskMenu.interpret', icon: '🔍' },
  { key: 'report', label: 'smartAskMenu.report', icon: '📊' },
  { key: 'insight', label: 'smartAskMenu.insight', icon: '💡' },
  { key: 'compare', label: 'smartAskMenu.compare', icon: '📈' },
  { key: 'forecast', label: 'smartAskMenu.forecast', icon: '🔮' },
]

/** 已启用的数据源列表（用于输入框上方数据源选择器） */
const enabledDatasources = ref<Datasource[]>([])

/** 加载数据源列表 */
async function loadDatasources(): Promise<void> {
  try {
    const list = await datasourceApi.list() as unknown as Datasource[]
    enabledDatasources.value = list.filter(ds => ds.enabled)
  } catch {
    enabledDatasources.value = []
  }
}

/** 切换数据源选中状态 */
function toggleDatasource(dsId: string): void {
  const ids = chatStore.selectedDatasourceIds
  const idx = ids.indexOf(dsId)
  if (idx >= 0) {
    ids.splice(idx, 1)
  } else {
    ids.push(dsId)
  }
}

/** 数据源触发按钮文案 */
const dsTriggerLabel = computed(() => {
  const count = chatStore.selectedDatasourceIds.length
  if (count === 0) return t('chat.datasourceScope')
  return `${t('chat.datasourceScope')} (${count})`
})

/** 根据时间段生成问候语 */
const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) {
    return t('smartAskMenu.morningGreeting')
  } else if (hour < 18) {
    return t('smartAskMenu.afternoonGreeting')
  } else {
    return t('smartAskMenu.eveningGreeting')
  }
})

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

/** 复制状态：按消息索引记录 */
const copyState = reactive<Record<number, 'idle' | 'copied'>>({})

/** 复制消息内容到剪贴板 */
function handleCopy(msgIndex: number, content: string): void {
  if (!content) return
  copyToClipboard(content).then(() => {
    copyState[msgIndex] = 'copied'
    setTimeout(() => {
      copyState[msgIndex] = 'idle'
    }, 2000)
  }).catch(() => {})
}

/** 重新生成 AI 消息 */
function handleRegenerate(msgIndex: number): void {
  chatStore.regenerateMessage(msgIndex)
}

/** 工具调用展开状态（按消息索引+工具索引） */
const expandedTools = reactive<Set<number>>(new Set())

/** 图表实例映射 */
const chartInstances = new Map<string, echarts.ECharts>()
const chartRefs = new Map<string, HTMLElement>()

/** ECharts Option 模式图表实例映射（后端返回标准 option 时使用） */
const echartsInstances = new Map<string, echarts.ECharts>()
const echartsRefs = new Map<string, HTMLElement>()

/** 是否可以发送 */
const canSend = computed(() => inputMessage.value.trim() && !chatStore.isStreaming && chatStore.currentAgentId)

/** Marked 自定义 renderer：将 ```echarts 代码块转为占位 div，供后续 ECharts 初始化 */
const customRenderer = {
  code({ text, lang }: { text: string; lang?: string; escaped?: boolean }): string {
    const infoStr = (lang || '').split(/\s/)[0]
    if (infoStr === 'echarts') {
      // 转成占位 div，ECharts 实例由 scanAndMountEChartsBlocks 在 nextTick 后挂载
      return `<div class="echarts-block" data-echarts-option="${encodeURIComponent(text || '')}"></div>\n`
    }
    // 非 echarts 代码块：使用 highlight.js 高亮处理
    const detectedLang = infoStr
    const hasLanguage = !!detectedLang && !!hljs.getLanguage(detectedLang)
    let highlighted: string
    try {
      highlighted = hasLanguage
        ? hljs.highlight(text, { language: detectedLang }).value
        : hljs.highlightAuto(text).value
    } catch {
      highlighted = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    }
    const langClass = hasLanguage ? ` language-${detectedLang}` : ''
    return `<pre><code class="hljs${langClass}">${highlighted}</code></pre>\n`
  },
}

/** Marked 实例（不接入 markedHighlight，由自定义 renderer 自行处理高亮） */
const markedInstance = new Marked({
  gfm: true,
  breaks: true,
  renderer: customRenderer,
})

/** DOMPurify 配置：允许 echarts 占位 div 所需的属性 */
const purifyConfig = {
  ADD_ATTR: ['data-echarts-option', 'class', 'style'],
  ADD_TAGS: ['div', 'span', 'pre', 'code'],
}

/** 渲染 Markdown */
function renderMarkdown(content: string): string {
  const html = markedInstance.parse(content) as string
  return DOMPurify.sanitize(html, purifyConfig)
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

/** 设置 ECharts Option 模式图表容器引用 */
function setEChartsRef(el: HTMLElement | null, msgIndex: number, cardIndex: number): void {
  if (!el) return
  const key = `ec-${msgIndex}-${cardIndex}`
  echartsRefs.set(key, el)
  nextTick(() => initEChartsChart(key, msgIndex, cardIndex))
}

/**
 * 初始化 ECharts Option 模式图表
 * 后端返回标准 ECharts option 时，直接透传给 echarts.setOption() 渲染
 */
function initEChartsChart(key: string, msgIndex: number, cardIndex: number): void {
  const el = echartsRefs.get(key)
  if (!el) return

  const msg = chatStore.messages[msgIndex]
  if (!msg?.cards) return

  const card = msg.cards[cardIndex]
  if (card?.type !== 'echarts') return

  const echartsData = card.data as EChartsOptionData
  const instance = echarts.init(el)
  echartsInstances.set(key, instance)

  /** 合并默认配置与后端返回的 option，确保基础体验一致 */
  const mergedOption = {
    tooltip: {
      trigger: 'axis' as const,
      ...((echartsData.option.tooltip || {}) as Record<string, unknown>),
    },
    grid: {
      left: 40,
      right: 16,
      top: echartsData.title ? 40 : 24,
      bottom: 20,
      ...((echartsData.option.grid || {}) as Record<string, unknown>),
    },
    ...echartsData.option,
  }

  instance.setOption(mergedOption)
}

/** ECharts 代码块实例映射（Markdown 内嵌的 ```echarts 占位块） */
const echartsBlockInstances = new Map<HTMLElement, echarts.ECharts>()

/** ECharts 代码块原始 option 映射，用于图表类型切换时复用数据 */
const echartsBlockOptions = new Map<HTMLElement, Record<string, any>>()

/** ECharts 代码块当前选中类型映射 */
const echartsBlockTypes = new Map<HTMLElement, string>()

/** ECharts 代码块列表明细浮层可见状态映射 */
const echartsBlockVisibleTable = new Map<HTMLElement, boolean>()

/** 每个 ECharts 块关联的 ResizeObserver 映射，用于在容器尺寸变化时自动 resize */
const echartsBlockObservers = new Map<HTMLElement, ResizeObserver>()

/** 支持切换的图表类型（不含表格，表格由独立按钮触发的浮层展示） */
const ECHARTS_CHART_TYPES = [
  { key: 'bar', label: '柱状图' },
  { key: 'line', label: '折线图' },
  { key: 'pie', label: '饼图' },
  { key: 'scatter', label: '散点图' },
  { key: 'funnel', label: '漏斗图' },
  { key: 'gauge', label: '仪表盘/指标卡片' },
] as const

type ChartType = (typeof ECHARTS_CHART_TYPES)[number]['key']

/** ECharts option 顶层 key 白名单（安全过滤） */
const ECHARTS_ALLOWED_KEYS = new Set([
  'title', 'tooltip', 'legend', 'xAxis', 'yAxis', 'series',
  'grid', 'color', 'dataset', 'graphic', 'radar', 'polar',
  'angleAxis', 'radiusAxis', 'visualMap',
])

/** ECharts option 最大尺寸（100KB） */
const ECHARTS_MAX_OPTION_SIZE = 100 * 1024

/**
 * 递归剥离 ECharts option 中的函数值，防止 XSS
 */
function sanitizeEchartsOption(obj: Record<string, any>): void {
  for (const key of Object.keys(obj)) {
    const val = obj[key]
    if (typeof val === 'string' && val.trimStart().startsWith('function')) {
      delete obj[key]
    } else if (typeof val === 'function') {
      delete obj[key]
    } else if (val && typeof val === 'object') {
      if (Array.isArray(val)) {
        val.forEach((item: any) => {
          if (item && typeof item === 'object') {
            sanitizeEchartsOption(item)
          }
        })
      } else {
        sanitizeEchartsOption(val)
      }
    }
  }
}

/**
 * 过滤 ECharts option 顶层 key，仅保留白名单内的字段
 */
function filterEchartsTopLevelKeys(option: Record<string, any>): Record<string, any> {
  const filtered: Record<string, any> = {}
  for (const key of Object.keys(option)) {
    if (ECHARTS_ALLOWED_KEYS.has(key)) {
      filtered[key] = option[key]
    }
  }
  return filtered
}

/**
 * 提取 option 中的 xAxis/yAxis 指标名、xAxis 类别数据、series 名称与数值，
 * 供图表类型切换与列表明细复用
 */
function extractChartData(option: Record<string, any>): {
  xAxisName: string
  yAxisName: string
  categories: string[]
  seriesList: { name: string; data: number[] }[]
} {
  // 兼容 xAxis 是对象或数组（多 x 轴场景）：取第一个有数据的轴
  const xAxis: any = (() => {
    if (Array.isArray(option.xAxis)) return option.xAxis[0] || {}
    return option.xAxis || {}
  })()
  const yAxis: any = (() => {
    if (Array.isArray(option.yAxis)) return option.yAxis[0] || {}
    return option.yAxis || {}
  })()
  // 优先读取 xAxis.name，再回退到 series 第一个 name
  const xAxisName = (typeof xAxis?.name === 'string' && xAxis.name.trim())
    || (typeof xAxis?.nameTextStyle?.text === 'string' ? xAxis.nameTextStyle.text : '')
    || ''
  const yAxisName = (typeof yAxis?.name === 'string' && yAxis.name.trim())
    || (typeof yAxis?.nameTextStyle?.text === 'string' ? yAxis.nameTextStyle.text : '')
    || ''
  const categories: string[] = (xAxis && Array.isArray(xAxis.data)) ? [...xAxis.data] : []
  const seriesRaw: any[] = Array.isArray(option.series) ? option.series : []
  const seriesList = seriesRaw
    .map((s: any) => {
      // 兼容多种 series.data 形态：
      // 1. 纯数字数组 [1, 2, 3]
      // 2. 数值/字符串混合（折线图常见）
      // 3. 对象数组 [{name, value}]（饼图原始形态，不展开）
      let numericData: number[] = []
      if (Array.isArray(s?.data)) {
        numericData = s.data
          .map((v: any) => {
            if (typeof v === 'number') return v
            if (typeof v === 'string') {
              const n = Number(v)
              return Number.isFinite(n) ? n : null
            }
            if (v && typeof v === 'object' && typeof v.value === 'number') return v.value
            return null
          })
          .filter((v: number | null): v is number => v !== null && Number.isFinite(v))
      }
      return {
        name: typeof s?.name === 'string' ? s.name : '',
        data: numericData,
      }
    })
    .filter((s: { data: number[] }) => s.data.length > 0)
  return { xAxisName, yAxisName, categories, seriesList }
}

/**
 * 提取所有 series 数值（用于计算仪表盘/漏斗等汇总指标）
 */
function flattenValues(seriesList: { name: string; data: number[] }[]): number[] {
  return seriesList.flatMap((s) => s.data)
}

/**
 * 构造表格 HTML（替代 ECharts 的简单 table 渲染）
 * 表头优先展示 xAxis.name 等真实指标名，让用户能直接看懂列含义
 */
function buildTableHtml(original: Record<string, any>): string {
  const { xAxisName, yAxisName, categories, seriesList } = extractChartData(original)
  if (seriesList.length === 0) {
    return '<div class="echarts-table-empty">无数据</div>'
  }
  // 第一列是 X 轴（类别）维度：仅在有 xAxisName 时使用，否则用"日期"兜底；
  // 不要再回退到 yAxisName，否则会把"订单量"之类塞到日期列导致列含义错乱
  const firstColName = xAxisName || (categories.length > 0 ? '日期' : '序号')
  // 各 series 列名：优先 series.name，否则用 yAxis.name，再不行用"指标 N"
  const headerCells = [`<th>${escapeHtml(firstColName)}</th>`]
    .concat(seriesList.map((s, idx) => {
      const colName = s.name || yAxisName || `指标 ${idx + 1}`
      return `<th>${escapeHtml(colName)}</th>`
    }))
    .join('')
  const rows = categories.length > 0
    ? categories.map((cat, i) => {
        const cells = seriesList.map((s) => `<td>${escapeHtml(String(s.data[i] ?? '-'))}</td>`).join('')
        return `<tr><td>${escapeHtml(cat)}</td>${cells}</tr>`
      }).join('')
    : seriesList[0].data.map((v, i) => {
        const cells = seriesList.map((s) => `<td>${escapeHtml(String(s.data[i] ?? '-'))}</td>`).join('')
        return `<tr><td>第 ${i + 1} 行</td>${cells}</tr>`
      }).join('')
  return `<table class="echarts-table"><thead><tr>${headerCells}</tr></thead><tbody>${rows}</tbody></table>`
}

/** HTML 实体转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 数据量大时把最小的一批聚合成"其他"项，避免图表被挤成无法辨识的薄片
 * @param items 形如 [{name, value}, ...]
 * @param keepTop 保留前 N 个最大项，剩余聚合成"其他 (M 项)"
 */
function aggregateSmallItems<T extends { name: string; value: number }>(items: T[], keepTop: number): T[] {
  if (items.length <= keepTop) {
    return items
  }
  const sorted = [...items].sort((a, b) => b.value - a.value)
  const top = sorted.slice(0, keepTop)
  const rest = sorted.slice(keepTop)
  const otherValue = rest.reduce((sum, it) => sum + it.value, 0)
  return [
    ...top,
    { name: `其他 (${rest.length} 项)`, value: otherValue } as unknown as T,
  ]
}

/**
 * 通用 xAxis 配置：类别数 > 阈值时让标签自动间隔、旋转、截断，避免重叠
 */
function buildAdaptiveXAxis(categories: string[]): Record<string, any> {
  const cfg: Record<string, any> = { type: 'category', data: categories }
  if (categories.length > 8) {
    cfg.axisLabel = {
      interval: 'auto',
      rotate: categories.length > 15 ? 45 : 0,
      formatter: (val: string) => (val && val.length > 8 ? `${val.slice(0, 8)}…` : val),
    }
  }
  return cfg
}

/**
 * 根据目标图表类型重建 option（保留 title/legend/tooltip 等布局，替换 xAxis/yAxis/series）
 */
function buildEchartsOption(original: Record<string, any>, type: ChartType): Record<string, any> {
  const { categories, seriesList } = extractChartData(original)
  const base = {
    title: original.title,
    tooltip: original.tooltip,
    legend: original.legend,
    grid: original.grid,
  }

  if (type === 'pie') {
    // 饼图：只支持一个 series，使用 categories 作为外环标签
    const first = seriesList[0] || { name: '', data: [] }
    const rawData = first.data.map((v: number, i: number) => ({
      name: categories[i] ?? String(i),
      value: v,
    }))
    // 类别过多时把最小值聚合成"其他"，避免饼图被切成不可读的细条
    const pieData = aggregateSmallItems(rawData, 8)
    const dense = rawData.length > 8
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        name: first.name,
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['50%', '55%'],
        data: pieData,
        // 类别密集时关闭引导线与重叠标签，避免视觉混乱
        label: dense
          ? { formatter: '{b}\n{d}%', edgeDistance: 10, fontSize: 11 }
          : { formatter: '{b}\n{d}%' },
        labelLine: dense ? { length: 8, length2: 8 } : { show: true },
      }],
    }
  }

  if (type === 'scatter') {
    // 散点图：每个 series 转为 [xIndex, y] 二维数组
    const maxLen = Math.max(0, ...seriesList.map((s) => s.data.length))
    const xCats = categories.length > 0
      ? categories
      : Array.from({ length: maxLen }, (_, i) => String(i + 1))
    const scatterSeries = seriesList.map((s) => ({
      name: s.name,
      type: 'scatter',
      // 类别多时缩小点尺寸，避免一片糊
      symbolSize: xCats.length > 30 ? 6 : xCats.length > 15 ? 8 : 10,
      data: s.data.map((v, i) => [i, v]),
    }))
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: (p: any) => `${p.seriesName}<br/>${xCats[p.value[0]] ?? p.value[0]}: ${p.value[1]}` },
      xAxis: buildAdaptiveXAxis(xCats),
      yAxis: { type: 'value' },
      series: scatterSeries,
    }
  }

  if (type === 'funnel') {
    // 漏斗图：取首个 series，每个类别一个漏斗层
    const first = seriesList[0] || { name: '', data: [] }
    const rawData = first.data.map((v: number, i: number) => ({
      name: categories[i] ?? `第${i + 1}项`,
      value: v,
    }))
    // 漏斗图按层高切分，本质上不适合 N 较大的数据。
    // 阈值降到 6：超过 6 层就聚合成前 6 大 + "其他"，避免"11+ 层的薄片 + 重叠标签"。
    const dense = rawData.length > 6
    const funnelData = dense ? aggregateSmallItems(rawData, 6) : rawData
    const maxVal = Math.max(...funnelData.map((d) => d.value), 0)
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      series: [{
        name: first.name,
        type: 'funnel',
        left: '12%',
        right: '12%',
        top: '12%',
        bottom: '8%',
        width: '76%',
        min: 0,
        max: maxVal,
        // dense 时给最薄层留 8% 高度，避免接近 0 的层被压成不可见
        minSize: dense ? '8%' : '0%',
        maxSize: '100%',
        sort: 'descending',
        // dense 时加大层间距，让标签有展示空间
        gap: dense ? 6 : 2,
        // dense 场景统一改为外侧引导线 + 文字，并允许 hideOverlap 进一步去重
        label: dense
          ? { show: true, position: 'outside', formatter: '{b}: {c}', fontSize: 11 }
          : { show: true, position: 'inside' },
        labelLine: dense ? { show: true, length: 10, length2: 10 } : { show: false },
        labelLayout: dense ? { hideOverlap: true } : undefined,
        data: funnelData,
      }],
    }
  }

  if (type === 'gauge') {
    // 仪表盘/指标卡片：取首个 series 第一项作为指标值
    const allValues = flattenValues(seriesList)
    const value = allValues[0] ?? 0
    const max = Math.max(...allValues, value * 1.2, 100)
    const titleText = (() => {
      if (base.title && typeof base.title === 'object' && typeof base.title.text === 'string') {
        return base.title.text
      }
      if (typeof base.title === 'string') {
        return base.title
      }
      return '指标'
    })()
    return {
      ...base,
      tooltip: { trigger: 'item' },
      series: [{
        name: titleText,
        type: 'gauge',
        radius: '75%',
        center: ['50%', '60%'],
        min: 0,
        max,
        progress: { show: true, width: 12 },
        axisLine: { lineStyle: { width: 12 } },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: { valueAnimation: true, fontSize: 24, formatter: '{value}', offsetCenter: [0, '0%'] },
        title: { show: true, offsetCenter: [0, '70%'], fontSize: 13, color: '#666' },
        data: [{ value, name: titleText }],
      }],
    }
  }

  // 柱状图 / 折线图
  const series = seriesList.map((s: { name: string; data: number[] }) => ({
    name: s.name,
    type,
    data: s.data,
    smooth: type === 'line',
    // 数据点多时让柱变细、关掉 symbol 强调连线
    ...(type === 'bar' && categories.length > 20 ? { barMaxWidth: 12 } : {}),
    ...(type === 'line' && categories.length > 30 ? { showSymbol: false } : {}),
  }))

  return {
    ...base,
    tooltip: base.tooltip || { trigger: 'axis' },
    xAxis: buildAdaptiveXAxis(categories),
    yAxis: { type: 'value' },
    series,
  }
}

/**
 * 根据图表类型与数据规模自适应调整容器高度，避免物理挤压
 * 漏斗图按层高切分，层数多时容器需要更高
 */
function adjustContainerHeightForType(htmlEl: HTMLElement, type: ChartType, option: Record<string, any>): void {
  if (type === 'funnel' && Array.isArray(option.series) && option.series[0]) {
    const funnelData: any[] = option.series[0].data || []
    // 每层至少 32px 高度 + 上下边距 100px，封顶 700px 防超长
    const minHeight = Math.min(Math.max(350, 100 + funnelData.length * 32), 700)
    const currentHeight = parseInt(htmlEl.style.height || '350', 10)
    if (currentHeight < minHeight) {
      htmlEl.style.height = `${minHeight}px`
    }
  } else {
    htmlEl.style.height = '350px'
  }
}

/**
 * 切换指定 ECharts 代码块的图表类型
 * 注意：表格不再作为图表类型，而是通过 toggleTableOverlay 触发的浮层单独展示
 */
function switchEchartsBlockType(htmlEl: HTMLElement, type: ChartType): void {
  const original = echartsBlockOptions.get(htmlEl)
  if (!original) {
    return
  }
  if (echartsBlockTypes.get(htmlEl) === type) {
    syncEchartsToolbarActive(htmlEl, type)
    return
  }
  echartsBlockTypes.set(htmlEl, type)

  let chart = echartsBlockInstances.get(htmlEl)
  if (!chart || chart.isDisposed()) {
    chart = echarts.init(htmlEl)
    echartsBlockInstances.set(htmlEl, chart)
  }
  const newOption = buildEchartsOption(original, type)
  chart.setOption(newOption, true)

  // 漏斗图按层高切分，层数多时容器高度需要扩展，避免物理挤压
  adjustContainerHeightForType(htmlEl, type, newOption)

  syncEchartsToolbarActive(htmlEl, type)
}

/** 同步工具栏当前选中状态 */
function syncEchartsToolbarActive(htmlEl: HTMLElement, type: ChartType): void {
  const toolbar = htmlEl.querySelector('.echarts-toolbar') as HTMLElement | null
  if (!toolbar) {
    return
  }
  const trigger = toolbar.querySelector<HTMLElement>('.echarts-toolbar-trigger-label')
  const item = ECHARTS_CHART_TYPES.find((t) => t.key === type)
  if (trigger && item) {
    trigger.textContent = item.label
  }
  toolbar.querySelectorAll<HTMLElement>('li[data-type]').forEach((li) => {
    const isActive = li.getAttribute('data-type') === type
    li.classList.toggle('is-active', isActive)
  })
}

/** 全局下拉点击外部关闭监听器引用 */
let dropdownOutsideHandler: ((e: MouseEvent) => void) | null = null

/**
 * 切换指定 ECharts 代码块的列表明细浮层
 * 浮层叠加在图表之上，不销毁 ECharts 实例，确保切换按钮始终可用
 */
function toggleTableOverlay(htmlEl: HTMLElement): void {
  const original = echartsBlockOptions.get(htmlEl)
  if (!original) {
    return
  }
  const isVisible = !!echartsBlockVisibleTable.get(htmlEl)
  if (isVisible) {
    const overlay = htmlEl.querySelector('.echarts-table-overlay')
    if (overlay) {
      overlay.remove()
    }
    echartsBlockVisibleTable.set(htmlEl, false)
  } else {
    const overlay = document.createElement('div')
    overlay.className = 'echarts-table-overlay'
    overlay.innerHTML = `
      <div class="echarts-table-overlay-header">
        <span class="echarts-table-overlay-title">列表明细</span>
        <button type="button" class="echarts-table-overlay-close" aria-label="关闭">×</button>
      </div>
      <div class="echarts-table-overlay-body">${buildTableHtml(original)}</div>
    `
    overlay.querySelector('.echarts-table-overlay-close')?.addEventListener('click', (e) => {
      e.stopPropagation()
      toggleTableOverlay(htmlEl)
    })
    htmlEl.appendChild(overlay)
    echartsBlockVisibleTable.set(htmlEl, true)
  }
  // 同步工具栏的"列表明细"按钮高亮
  const detailBtn = htmlEl.querySelector<HTMLElement>('.echarts-toolbar-detail-btn')
  detailBtn?.classList.toggle('is-active', !isVisible ? true : false)
}

/**
 * 渲染 ECharts 块顶部的下拉式类型选择器（并附加"列表明细"独立按钮）
 */
function renderEchartsToolbar(htmlEl: HTMLElement, currentType: ChartType): void {
  // 工具栏已存在则仅更新高亮
  const existing = htmlEl.querySelector('.echarts-toolbar')
  if (existing) {
    syncEchartsToolbarActive(htmlEl, currentType)
    return
  }

  const toolbar = document.createElement('div')
  toolbar.className = 'echarts-toolbar'

  // "列表明细"独立按钮（在工具栏最左侧，与下拉同级）
  const detailBtn = document.createElement('button')
  detailBtn.type = 'button'
  detailBtn.className = 'echarts-toolbar-detail-btn'
  detailBtn.innerHTML = '<span class="echarts-toolbar-detail-icon">📋</span><span>列表明细</span>'
  detailBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    toggleTableOverlay(htmlEl)
  })
  toolbar.appendChild(detailBtn)

  // 下拉触发器
  const trigger = document.createElement('div')
  trigger.className = 'echarts-toolbar-trigger'
  const currentItem = ECHARTS_CHART_TYPES.find((t) => t.key === currentType)
  trigger.innerHTML = `
    <span class="echarts-toolbar-trigger-label">${currentItem ? currentItem.label : '选择图表类型'}</span>
    <span class="echarts-toolbar-trigger-arrow">▾</span>
  `
  // 下拉面板
  const panel = document.createElement('ul')
  panel.className = 'echarts-toolbar-panel'
  ECHARTS_CHART_TYPES.forEach(({ key, label }) => {
    const li = document.createElement('li')
    li.className = 'echarts-toolbar-panel-item' + (key === currentType ? ' is-active' : '')
    li.setAttribute('data-type', key)
    li.textContent = label
    li.addEventListener('click', (e) => {
      e.stopPropagation()
      switchEchartsBlockType(htmlEl, key as ChartType)
      panel.classList.remove('is-open')
      trigger.classList.remove('is-open')
    })
    panel.appendChild(li)
  })
  trigger.addEventListener('click', (e) => {
    e.stopPropagation()
    const isOpen = panel.classList.contains('is-open')
    // 关闭所有其他下拉
    document.querySelectorAll('.echarts-toolbar-panel.is-open').forEach((p) => p.classList.remove('is-open'))
    document.querySelectorAll('.echarts-toolbar-trigger.is-open').forEach((t) => t.classList.remove('is-open'))
    panel.classList.toggle('is-open', !isOpen)
    trigger.classList.toggle('is-open', !isOpen)
  })

  toolbar.appendChild(trigger)
  toolbar.appendChild(panel)
  htmlEl.appendChild(toolbar)

  // 注册全局点击关闭监听（仅注册一次）
  if (!dropdownOutsideHandler) {
    dropdownOutsideHandler = (e: MouseEvent): void => {
      const target = e.target as HTMLElement | null
      if (!target) {
        return
      }
      if (target.closest('.echarts-toolbar')) {
        return
      }
      document.querySelectorAll('.echarts-toolbar-panel.is-open').forEach((p) => p.classList.remove('is-open'))
      document.querySelectorAll('.echarts-toolbar-trigger.is-open').forEach((t) => t.classList.remove('is-open'))
    }
    document.addEventListener('click', dropdownOutsideHandler)
  }
}

/**
 * 扫描聊天区域中所有未初始化的 .echarts-block 占位元素并挂载 ECharts 实例
 * 参照 mateclaw-ui useEChartsRenderer 的实现模式
 */
function scanAndMountEChartsBlocks(): void {
  const container = chatAreaRef.value
  if (!container) {
    return
  }
  const blocks = container.querySelectorAll('.echarts-block:not(.echarts-error)')
  blocks.forEach((el) => {
    const htmlEl = el as HTMLElement
    if (echartsBlockInstances.has(htmlEl)) {
      return
    }
    // 已有 toolbar 但未初始化（重复扫描安全兜底）

    const encoded = htmlEl.getAttribute('data-echarts-option')
    if (!encoded) {
      return
    }

    if (encoded.length > ECHARTS_MAX_OPTION_SIZE) {
      htmlEl.textContent = 'Chart option too large'
      htmlEl.classList.add('echarts-error')
      return
    }

    try {
      const raw = decodeURIComponent(encoded)
      let option = JSON.parse(raw)

      if (!option || typeof option !== 'object' || !option.series) {
        htmlEl.textContent = 'Invalid chart option'
        htmlEl.classList.add('echarts-error')
        return
      }

      option = filterEchartsTopLevelKeys(option)
      sanitizeEchartsOption(option)

      // 修正布局：后端生成的 legend/grid 间距过紧容易重叠，
      // 在前端统一给标题/图例留出合理空间，避免元素互相挤压
      const hasTitle = !!option.title
      const hasLegend = !!option.legend
      option.title = {
        left: 'center',
        top: 8,
        textStyle: { fontSize: 13, fontWeight: 600 },
        ...(typeof option.title === 'object' ? option.title : {}),
      }
      option.legend = {
        bottom: 8,
        top: hasTitle ? 32 : 8,
        type: 'scroll',
        textStyle: { fontSize: 11 },
        ...(typeof option.legend === 'object' ? option.legend : {}),
      }
      option.grid = {
        left: 40,
        right: 24,
        top: hasTitle ? (hasLegend ? 72 : 56) : (hasLegend ? 56 : 28),
        bottom: hasLegend ? 48 : 24,
        containLabel: true,
        ...(typeof option.grid === 'object' ? option.grid : {}),
      }
      // 给工具栏留 36px 空间
      if (typeof option.grid === 'object' && option.grid) {
        option.grid.top = (typeof option.grid.top === 'number' ? option.grid.top : 56) + 36
      }

      if (!htmlEl.style.height) {
        htmlEl.style.height = '350px'
      }
      if (!htmlEl.style.width) {
        htmlEl.style.width = '100%'
      }

      // 推断初始图表类型：根据 series.type 决定工具栏默认高亮项
      const initialType: ChartType = (() => {
        const firstSeries: any = Array.isArray(option.series) ? option.series[0] : null
        const t = firstSeries?.type
        if (t === 'bar' || t === 'line' || t === 'pie' || t === 'scatter' || t === 'funnel' || t === 'gauge') {
          return t
        }
        return 'bar'
      })()

      // 后端直接返回 funnel 类型时，初始挂载阶段也按层数扩展容器高度
      adjustContainerHeightForType(htmlEl, initialType, option)

      // 保存原始 option（剔除前端布局覆盖字段）以便切换时复用数据
      echartsBlockOptions.set(htmlEl, {
        title: option.title,
        tooltip: option.tooltip,
        legend: option.legend,
        xAxis: option.xAxis,
        yAxis: option.yAxis,
        series: option.series,
      })
      echartsBlockTypes.set(htmlEl, initialType)

      const chart = echarts.init(htmlEl)
      chart.setOption(option)
      echartsBlockInstances.set(htmlEl, chart)

      // 监听容器尺寸变化：解决列表明细浮层开关、窗口缩放、侧边栏折叠等场景下
      // 图表不跟着容器伸缩的问题；debounce 50ms 避免动画过程高频触发
      let resizeRafId: number | null = null
      const observer = new ResizeObserver(() => {
        if (resizeRafId !== null) {
          cancelAnimationFrame(resizeRafId)
        }
        resizeRafId = requestAnimationFrame(() => {
          resizeRafId = null
          if (!chart.isDisposed()) {
            chart.resize()
          }
        })
      })
      observer.observe(htmlEl)
      echartsBlockObservers.set(htmlEl, observer)

      // 渲染图表类型切换工具栏
      renderEchartsToolbar(htmlEl, initialType)
    } catch (e) {
      console.error('[ChatView] ECharts block mount error:', e)
      htmlEl.textContent = 'Chart render error'
      htmlEl.classList.add('echarts-error')
    }
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

/** 追问点击 */
function handleFollowup(text: string): void {
  if (chatStore.isStreaming || !chatStore.currentAgentId) return
  const modelName = modelStore.activeModel?.modelName
  chatStore.sendMessage(chatStore.currentAgentId, text, modelName)
}

/** 智能问数快捷菜单点击 */
function handleSmartAskMenu(item: { key: string; label: string }): void {
  if (chatStore.isStreaming || !chatStore.currentAgentId) return
  const promptMap: Record<string, string> = {
    interpret: t('smartAskMenu.interpretPrompt'),
    report: t('smartAskMenu.reportPrompt'),
    insight: t('smartAskMenu.insightPrompt'),
    compare: t('smartAskMenu.comparePrompt'),
    forecast: t('smartAskMenu.forecastPrompt'),
  }
  const prompt = promptMap[item.key] || t('smartAskMenu.defaultPrompt')
  const modelName = modelStore.activeModel?.modelName
  chatStore.sendMessage(chatStore.currentAgentId, prompt, modelName)
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
  echartsInstances.forEach(instance => instance.resize())
  echartsBlockInstances.forEach(instance => {
    if (!instance.isDisposed()) {
      instance.resize()
    }
  })
}

watch(() => chatStore.messages.length, () => {
  scrollToBottom()
  nextTick(scanAndMountEChartsBlocks)
})
watch(() => chatStore.messages[chatStore.messages.length - 1]?.content, () => {
  scrollToBottom()
  nextTick(scanAndMountEChartsBlocks)
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  // 加载数据源列表（用于输入框上方数据源选择器）
  loadDatasources()
  // 切回对话菜单时，强制重拉当前会话历史消息以触发 ECharts 重新挂载
  if (chatStore.conversationId && !chatStore.isStreaming) {
    chatStore.switchConversation(chatStore.conversationId, true).finally(() => {
      nextTick(scanAndMountEChartsBlocks)
    })
  } else {
    nextTick(scanAndMountEChartsBlocks)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(instance => instance.dispose())
  chartInstances.clear()
  echartsInstances.forEach(instance => instance.dispose())
  echartsInstances.clear()
  echartsBlockInstances.forEach(instance => {
    if (!instance.isDisposed()) {
      instance.dispose()
    }
  })
  echartsBlockInstances.clear()
  echartsBlockOptions.clear()
  echartsBlockTypes.clear()
  echartsBlockVisibleTable.clear()
  echartsBlockObservers.forEach(observer => observer.disconnect())
  echartsBlockObservers.clear()
  if (dropdownOutsideHandler) {
    document.removeEventListener('click', dropdownOutsideHandler)
    dropdownOutsideHandler = null
  }
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
  padding-top: 40px;
}

.empty-avatar {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fff7f2 0%, #f5f6f8 100%);
  box-shadow: 0 4px 12px rgba(240, 90, 35, 0.12);
}

.avatar-bot {
  font-size: 30px;
  line-height: 1;
}

.empty-greeting {
  font-size: 18px;
  color: var(--body-text);
  margin-bottom: 24px;
  font-weight: 500;
}

/* 智能问数快捷菜单 */
.smart-ask-menu {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 560px;
  margin-bottom: 28px;
}

.smart-ask-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid var(--main-orange);
  background: var(--white);
  color: var(--dark-orange);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.smart-ask-chip:hover {
  background: var(--main-orange);
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(240, 90, 35, 0.25);
}

.smart-ask-chip:active {
  transform: translateY(0);
}

.chip-icon {
  font-size: 14px;
  line-height: 1;
}

.chip-label {
  white-space: nowrap;
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

.ai-content-wrapper {
  display: flex;
  flex-direction: column;
}

.user-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
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

/* ECharts Markdown 内嵌代码块占位元素 */
:deep(.echarts-block) {
  width: 100%;
  min-height: 350px;
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  margin: 8px 0;
  position: relative;
  padding-top: 36px;
}

:deep(.echarts-block.echarts-error) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  color: var(--muted);
  font-size: 12px;
  padding-top: 0;
}

/* ECharts 图表类型下拉选择器 */
:deep(.echarts-toolbar) {
  position: absolute;
  top: 0;
  right: 8px;
  left: 8px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  /* 提高 z-index 到 20，确保盖在 ECharts 内部 canvas/zr 之上，
     避免 ECharts 的 zr handler 拦截 toolbar 按钮的 click */
  z-index: 20;
}

:deep(.echarts-toolbar > *) {
  pointer-events: auto;
}

/* 列表明细独立按钮 */
:deep(.echarts-toolbar-detail-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--light-grey);
  border-radius: 4px;
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

:deep(.echarts-toolbar-detail-btn:hover) {
  color: var(--primary, #409eff);
  border-color: var(--primary, #409eff);
}

:deep(.echarts-toolbar-detail-btn.is-active) {
  color: #fff;
  background: var(--primary, #409eff);
  border-color: var(--primary, #409eff);
}

:deep(.echarts-toolbar-detail-icon) {
  font-size: 12px;
  line-height: 1;
}

:deep(.echarts-toolbar-trigger) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--light-grey);
  border-radius: 4px;
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

:deep(.echarts-toolbar-trigger:hover),
:deep(.echarts-toolbar-trigger.is-open) {
  color: var(--primary, #409eff);
  border-color: var(--primary, #409eff);
}

:deep(.echarts-toolbar-trigger-arrow) {
  font-size: 10px;
  line-height: 1;
}

:deep(.echarts-toolbar-panel) {
  position: absolute;
  top: 28px;
  right: 0;
  min-width: 130px;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  display: none;
  z-index: 10;
}

:deep(.echarts-toolbar-panel.is-open) {
  display: block;
}

:deep(.echarts-toolbar-panel-item) {
  height: 28px;
  padding: 0 12px;
  line-height: 28px;
  font-size: 12px;
  color: var(--text, #333);
  cursor: pointer;
  transition: background 0.15s ease;
}

:deep(.echarts-toolbar-panel-item:hover) {
  background: rgba(64, 158, 255, 0.08);
  color: var(--primary, #409eff);
}

:deep(.echarts-toolbar-panel-item.is-active) {
  color: var(--primary, #409eff);
  font-weight: 600;
  background: rgba(64, 158, 255, 0.06);
}

/* 列表明细浮层（叠加在图表上，不销毁 ECharts 实例） */
:deep(.echarts-table-overlay) {
  position: absolute;
  top: 36px;
  left: 4px;
  right: 4px;
  bottom: 4px;
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 5;
  /* 不设置 overflow，避免破坏 thead sticky 定位上下文 */
  isolation: isolate;
}

:deep(.echarts-table-overlay-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 12px;
  border-bottom: 1px solid var(--light-grey);
  background: #fafbfc;
  flex-shrink: 0;
  border-top-left-radius: 6px;
  border-top-right-radius: 6px;
}

:deep(.echarts-table-overlay-title) {
  font-size: 13px;
  font-weight: 600;
  color: var(--text, #333);
}

:deep(.echarts-table-overlay-close) {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s ease;
}

:deep(.echarts-table-overlay-close:hover) {
  background: rgba(0, 0, 0, 0.06);
  color: var(--text, #333);
}

:deep(.echarts-table-overlay-body) {
  flex: 1;
  overflow: auto;
  border-bottom-left-radius: 6px;
  border-bottom-right-radius: 6px;
}

:deep(.echarts-table-overlay-body > .echarts-table-empty) {
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

:deep(.echarts-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  color: var(--text, #333);
}

:deep(.echarts-table th),
:deep(.echarts-table td) {
  border: 1px solid var(--light-grey);
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
}

/* 表头吸顶：相对 body 滚动容器（外层 overlay 不再设 overflow） */
:deep(.echarts-table thead th) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fafbfc;
  box-shadow: 0 1px 0 var(--light-grey);
}

:deep(.echarts-table tbody tr:hover) {
  background: rgba(64, 158, 255, 0.04);
}

/* ECharts Option Card（后端返回标准 ECharts option 时直接渲染） */
.echarts-box {
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  padding: 12px;
  margin-left: 36px;
  align-self: flex-start;
  max-width: 80%;
}

.echarts-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--body-text);
  margin-bottom: 8px;
}

.echarts-chart {
  width: 100%;
  min-height: 280px;
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

/* 消息操作栏（位于气泡外部） */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-top: 4px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.msg:hover .msg-actions {
  opacity: 1;
  pointer-events: auto;
}

.msg-actions--ai {
  justify-content: flex-start;
}

.msg-actions--user {
  justify-content: flex-start;
  padding-right: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--muted, #94a3b8);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: var(--lighter-grey, rgba(0, 0, 0, 0.05));
  color: var(--mid-grey, #64748b);
}

.action-btn.copied {
  color: #10b981;
}

/* Datasource Selector Toolbar */
.ds-toolbar {
  display: flex;
  align-items: center;
  padding: 4px 16px;
  border-top: 1px solid var(--light-grey);
  background: var(--white);
  flex-shrink: 0;
}

.ds-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 14px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
  user-select: none;
  line-height: 1.6;
}

.ds-trigger:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
  background: var(--very-light-orange);
}

.ds-trigger.active {
  border-color: var(--main-orange);
  color: var(--dark-orange);
  background: var(--light-orange);
  font-weight: 600;
}

.ds-trigger-icon {
  font-size: 12px;
  line-height: 1;
}

.ds-trigger-text {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ds-trigger-arrow {
  font-size: 10px;
  margin-left: 2px;
  opacity: 0.6;
}

/* Popover */
.ds-popover {
  padding: 0;
}

.ds-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--body-text);
  border-bottom: 1px solid var(--light-grey, #eee);
}

.ds-popover-clear {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}

.ds-popover-clear:hover {
  color: var(--main-orange);
}

.ds-popover-list {
  max-height: 220px;
  overflow-y: auto;
  padding: 4px 0;
}

.ds-popover-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin: 0 4px;
  transition: background 0.12s;
  font-size: 12px;
  color: var(--body-text);
}

.ds-popover-item:hover {
  background: var(--very-light-orange);
}

.ds-popover-item.checked {
  background: var(--light-orange);
  color: var(--dark-orange);
}

.ds-popover-item input[type="checkbox"] {
  accent-color: var(--main-orange);
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  cursor: pointer;
}

.ds-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-item-type {
  font-size: 9px;
  color: var(--muted);
  background: var(--near-white);
  padding: 1px 5px;
  border-radius: 4px;
  flex-shrink: 0;
}

.ds-popover-item.checked .ds-item-type {
  color: var(--dark-orange);
  background: rgba(240, 90, 35, 0.08);
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
