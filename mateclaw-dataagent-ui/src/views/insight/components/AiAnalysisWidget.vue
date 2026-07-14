<template>
  <div class="ai-analysis-widget">
    <div class="analysis-header">
      <span v-if="showTitle" class="analysis-title">{{ component.title }}</span>
      <el-button
        v-if="!generating"
        type="primary"
        size="small"
        :icon="MagicStick"
        :loading="generating"
        @click="handleGenerate"
      >
        {{ t('insight.aiAnalysis.generate') }}
      </el-button>
      <div v-else class="generating-indicator">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('insight.aiAnalysis.generating') }}</span>
      </div>
    </div>

    <div class="analysis-body">
      <!-- 数据部分（模板填充） -->
      <div v-if="dataSection" class="analysis-section data-section">
        <div class="section-content markdown-body" v-html="renderedDataSection"></div>
      </div>

      <!-- AI 生成部分 -->
      <div v-if="analysisSection" class="analysis-section ai-section">
        <div class="section-content markdown-body" v-html="renderedAnalysisSection"></div>
      </div>

      <!-- 空状态 -->
      <div v-if="!dataSection && !analysisSection && !generating" class="analysis-placeholder">
        <div class="placeholder-icon">🤖</div>
        <div class="placeholder-text">{{ t('insight.aiAnalysis.placeholder') }}</div>
      </div>
    </div>

    <!-- 免责声明 -->
    <div class="disclaimer">AI 分析仅供参考</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MagicStick, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { InsightComponent, InsightComponentData } from '@/types'

defineOptions({
  name: 'AiAnalysisWidget',
})

const { t } = useI18n()

const props = defineProps<{
  component: InsightComponent
  componentData?: InsightComponentData
  showTitle?: boolean
  generating?: boolean
}>()

const emit = defineEmits<{
  (e: 'generate', componentId: string): void
}>()

const dataSection = computed(() => props.componentData?.aiAnalysis?.dataSection)
const analysisSection = computed(() => props.componentData?.aiAnalysis?.analysisSection)

/** 将 Markdown 渲染为安全 HTML */
function renderMarkdown(md: string): string {
  if (!md) return ''
  const html = marked.parse(md, { async: false, gfm: true, breaks: true }) as string
  return DOMPurify.sanitize(html, { ADD_ATTR: ['class', 'style'] })
}

/** 渲染数据部分 Markdown */
const renderedDataSection = computed(() => renderMarkdown(dataSection.value ?? ''))

/** 渲染 AI 分析部分 Markdown */
const renderedAnalysisSection = computed(() => renderMarkdown(analysisSection.value ?? ''))

function handleGenerate(): void {
  emit('generate', props.component.id)
}
</script>

<style scoped>
.ai-analysis-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  overflow: hidden;
}

.analysis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  gap: 8px;
}

.analysis-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generating-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--main-orange);
}

.analysis-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--theme-text-muted);
  padding-bottom: 4px;
  border-bottom: 1px solid var(--theme-border);
}

.section-label.ai-label {
  color: var(--main-orange);
}

/* ===== Markdown 渲染样式（对齐 ChatView .msg-text 效果） ===== */

.section-content {
  font-size: 13px;
  line-height: 1.75;
  color: var(--theme-text);
  letter-spacing: 0.01em;
}

.section-content :deep(h1),
.section-content :deep(h2),
.section-content :deep(h3),
.section-content :deep(h4),
.section-content :deep(h5),
.section-content :deep(h6) {
  color: var(--theme-text);
  font-weight: 600;
  line-height: 1.4;
  margin: 1.2em 0 0.5em;
}

.section-content :deep(h1) {
  font-size: 1.5em;
}

.section-content :deep(h2) {
  font-size: 1.35em;
}

.section-content :deep(h3) {
  font-size: 1.2em;
}

.section-content :deep(h4) {
  font-size: 1.1em;
}

.section-content :deep(h5) {
  font-size: 1em;
}

.section-content :deep(h6) {
  font-size: 0.95em;
  color: var(--theme-text-secondary);
}

.section-content :deep(h1:first-child),
.section-content :deep(h2:first-child),
.section-content :deep(h3:first-child),
.section-content :deep(h4:first-child),
.section-content :deep(h5:first-child),
.section-content :deep(h6:first-child) {
  margin-top: 0;
}

.section-content :deep(p) {
  margin: 0.6em 0;
}

.section-content :deep(p:first-child) {
  margin-top: 0;
}

.section-content :deep(p:last-child) {
  margin-bottom: 0;
}

.section-content :deep(a) {
  color: var(--main-orange);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s;
}

.section-content :deep(a:hover) {
  border-bottom-color: rgba(240, 90, 35, 0.4);
}

.section-content :deep(strong) {
  color: var(--theme-text);
  font-weight: 600;
}

.section-content :deep(em) {
  font-style: italic;
}

.section-content :deep(ul),
.section-content :deep(ol) {
  padding-left: 1.6em;
  margin: 0.6em 0;
}

.section-content :deep(li) {
  margin: 0.3em 0;
}

.section-content :deep(li > ul),
.section-content :deep(li > ol) {
  margin: 0.2em 0;
}

.section-content :deep(pre) {
  background: var(--near-white, #f7f7f7);
  color: var(--body-text, #333);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
  margin: 0.8em 0;
}

.section-content :deep(pre:first-child) {
  margin-top: 0;
}

.section-content :deep(pre:last-child) {
  margin-bottom: 0;
}

.section-content :deep(code) {
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 0.85em;
}

.section-content :deep(:not(pre) > code) {
  background: rgba(0, 0, 0, 0.05);
  color: var(--theme-text);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.82em;
}

.section-content :deep(blockquote) {
  border-left: 3px solid var(--theme-border-strong, var(--theme-border));
  margin: 0.8em 0;
  padding: 6px 12px;
  color: var(--theme-text-secondary);
  border-radius: 0 6px 6px 0;
}

.section-content :deep(blockquote p) {
  margin: 0.3em 0;
}

.section-content :deep(blockquote p:first-child) {
  margin-top: 0;
}

.section-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.section-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--theme-border);
  margin: 1.2em 0;
}

.section-content :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  margin: 0.4em 0;
}

.section-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.8em 0;
  font-size: 12px;
  border-radius: 6px;
  overflow: hidden;
}

.section-content :deep(th),
.section-content :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 6px 10px;
  text-align: left;
}

.section-content :deep(th) {
  background: var(--theme-surface-elevated, var(--theme-surface-hover));
  font-weight: 600;
  color: var(--theme-text-secondary, var(--theme-text-muted));
}

.section-content :deep(tbody tr:nth-child(even)) {
  background: var(--theme-surface-hover);
}

.analysis-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--theme-text-muted);
}

.placeholder-icon {
  font-size: 32px;
}

.placeholder-text {
  font-size: 13px;
}

.disclaimer {
  font-size: 12px;
  color: var(--theme-text-muted);
  text-align: center;
  padding: 8px 12px;
  border-top: 1px solid var(--theme-border);
  background: var(--theme-surface);
  flex-shrink: 0;
}
</style>
