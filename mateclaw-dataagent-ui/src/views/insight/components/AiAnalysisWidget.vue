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
        <div class="section-label">{{ t('insight.aiAnalysis.dataSection') }}</div>
        <div class="section-content markdown-body" v-html="renderedDataSection"></div>
      </div>

      <!-- AI 生成部分 -->
      <div v-if="analysisSection" class="analysis-section ai-section">
        <div class="section-label ai-label">{{ t('insight.aiAnalysis.aiSection') }}</div>
        <div class="section-content markdown-body" v-html="renderedAnalysisSection"></div>
      </div>

      <!-- 空状态 -->
      <div v-if="!dataSection && !analysisSection && !generating" class="analysis-placeholder">
        <div class="placeholder-icon">🤖</div>
        <div class="placeholder-text">{{ t('insight.aiAnalysis.placeholder') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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

/** 渲染数据部分 Markdown */
const renderedDataSection = computed(() => {
  if (!dataSection.value) return ''
  const raw = marked.parse(dataSection.value, { async: false }) as string
  return DOMPurify.sanitize(raw)
})

/** 渲染 AI 分析部分 Markdown */
const renderedAnalysisSection = computed(() => {
  if (!analysisSection.value) return ''
  const raw = marked.parse(analysisSection.value, { async: false }) as string
  return DOMPurify.sanitize(raw)
})

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

.section-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--theme-text);
}

.section-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}

.section-content :deep(th),
.section-content :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 4px 8px;
  text-align: left;
}

.section-content :deep(th) {
  background: var(--theme-surface-hover);
  font-weight: 600;
}

.section-content :deep(h1),
.section-content :deep(h2),
.section-content :deep(h3) {
  margin: 8px 0 4px;
  font-size: 14px;
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
</style>
