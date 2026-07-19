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
      <div v-if="dataSection" class="analysis-section data-section" :data-label="t('insight.aiAnalysis.dataSectionLabel')">
        <div class="section-content markdown-body" v-html="renderedDataSection"></div>
      </div>

      <!-- AI 生成部分 -->
      <div v-if="analysisSection" class="analysis-section ai-section" :data-label="t('insight.aiAnalysis.aiSectionLabel')">
        <div class="section-content markdown-body" v-html="renderedAnalysisSection"></div>
      </div>

      <!-- 空状态 -->
      <div v-if="!dataSection && !analysisSection && !generating" class="analysis-placeholder">
        <div class="placeholder-icon">🤖</div>
        <div class="placeholder-text">{{ t('insight.aiAnalysis.placeholder') }}</div>
      </div>
    </div>

    <!-- 免责声明 -->
    <div class="disclaimer">{{ t('insight.aiDisclaimer') }}</div>
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
  background: var(--db-card);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.analysis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  min-height: 48px;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  gap: var(--space-sm);
}

.analysis-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 覆盖 Element Plus 主按钮为新的深色主按钮风格 */
.analysis-header :deep(.el-button--primary) {
  background: var(--db-text);
  border-color: var(--db-text);
  color: #fff;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.analysis-header :deep(.el-button--primary:hover) {
  background: var(--db-text-secondary);
  border-color: var(--db-text-secondary);
  color: #fff;
}

.generating-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--db-accent);
  padding: 4px 10px;
  border-radius: 12px;
  background: var(--db-accent-light);
}

.analysis-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.analysis-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--db-hover);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  animation: fadeIn var(--transition-base) both;
}

.analysis-section::before {
  content: attr(data-label);
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--db-text-muted);
}

.analysis-section.ai-section::before {
  color: var(--db-accent);
}

/* ===== Markdown 渲染样式 ===== */

.section-content {
  font-size: 13px;
  line-height: 1.75;
  color: var(--db-text);
  letter-spacing: 0.01em;
}

.section-content :deep(h1),
.section-content :deep(h2),
.section-content :deep(h3),
.section-content :deep(h4),
.section-content :deep(h5),
.section-content :deep(h6) {
  color: var(--db-text);
  font-weight: 600;
  line-height: 1.4;
  margin: 1.2em 0 0.5em;
}

.section-content :deep(h1) {
  font-size: 1.5em;
}

.section-content :deep(h2) {
  font-size: 1.3em;
}

.section-content :deep(h3) {
  font-size: 1.15em;
}

.section-content :deep(h4) {
  font-size: 1.05em;
}

.section-content :deep(h5),
.section-content :deep(h6) {
  font-size: 1em;
  color: var(--db-text-secondary);
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
  color: var(--db-accent);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color var(--transition-fast);
}

.section-content :deep(a:hover) {
  border-bottom-color: var(--db-accent-border);
}

.section-content :deep(strong) {
  color: var(--db-text);
  font-weight: 600;
}

.section-content :deep(ul),
.section-content :deep(ol) {
  padding-left: 1.5em;
  margin: 0.6em 0;
}

.section-content :deep(li) {
  margin: 0.3em 0;
}

.section-content :deep(pre) {
  background: var(--db-card);
  color: var(--db-text);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
  margin: 0.8em 0;
}

.section-content :deep(code) {
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 0.85em;
}

.section-content :deep(:not(pre) > code) {
  background: var(--db-muted);
  color: var(--db-text);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 0.82em;
}

.section-content :deep(blockquote) {
  border-left: 3px solid var(--db-border-strong);
  margin: 0.8em 0;
  padding: var(--space-sm) var(--space-md);
  color: var(--db-text-secondary);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--db-card);
}

.section-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--db-border);
  margin: 1.2em 0;
}

.section-content :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-md);
  margin: 0.4em 0;
}

.section-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.8em 0;
  font-size: 12px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.section-content :deep(th),
.section-content :deep(td) {
  border: 1px solid var(--db-border);
  padding: 6px 10px;
  text-align: left;
}

.section-content :deep(th) {
  background: var(--db-hover);
  font-weight: 600;
  color: var(--db-text-secondary);
}

.section-content :deep(tbody tr:nth-child(even)) {
  background: var(--db-card);
}

.analysis-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  color: var(--db-text-muted);
}

.placeholder-icon {
  font-size: 40px;
  line-height: 1;
  opacity: 0.8;
}

.placeholder-text {
  font-size: 13px;
}

.disclaimer {
  font-size: 12px;
  color: var(--db-text-muted);
  text-align: center;
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--db-border);
  background: var(--db-hover);
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .analysis-header {
    padding: var(--space-sm) var(--space-md);
  }

  .analysis-body {
    padding: var(--space-md);
  }

  .analysis-section {
    padding: var(--space-sm);
  }
}
</style>
