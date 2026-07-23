<template>
  <div class="help-doc-editor">
    <div class="editor-toolbar">
      <el-radio-group v-model="editorMode" size="small">
        <el-radio-button value="edit">{{ t('helpCenter.editMode') }}</el-radio-button>
        <el-radio-button value="split">{{ t('helpCenter.splitMode') }}</el-radio-button>
        <el-radio-button value="preview">{{ t('helpCenter.previewMode') }}</el-radio-button>
      </el-radio-group>
    </div>
    <div class="editor-body" :class="editorMode">
      <div class="editor-pane" v-show="editorMode === 'edit' || editorMode === 'split'">
        <textarea
          v-model="localContent"
          :placeholder="t('helpCenter.contentPlaceholder')"
          class="editor-textarea"
          @input="handleInput"
        ></textarea>
      </div>
      <div class="editor-preview" v-show="editorMode === 'preview' || editorMode === 'split'">
        <article
          class="markdown-body"
          v-if="localContent"
          v-html="renderMarkdown(localContent)"
        />
        <el-empty v-else :description="t('helpCenter.emptyContent')" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

const { t } = useI18n()

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

/** 编辑器模式 */
const editorMode = ref<'edit' | 'split' | 'preview'>('split')
/** 本地内容 */
const localContent = ref(props.modelValue)

/** Markdown 渲染器 */
const customRenderer = {
  heading({ tokens, depth }: { tokens: { text: string; raw: string }[]; depth: number }): string {
    const text = tokens.map(t => t.text).join('')
    const id = text.replace(/[^\w\u4e00-\u9fa5]+/g, '-').toLowerCase()
    return `<h${depth} id="heading-${id}">${text}</h${depth}>\n`
  },
  code({ text, lang }: { text: string; lang?: string; escaped?: boolean }): string {
    const infoStr = (lang || '').split(/\s/)[0]
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

const markedInstance = new Marked({
  gfm: true,
  breaks: true,
  renderer: customRenderer as any,
})

const purifyConfig = {
  ADD_ATTR: ['class', 'style', 'id'],
  ADD_TAGS: ['div', 'span', 'pre', 'code'],
}

function renderMarkdown(content: string): string {
  const html = markedInstance.parse(content) as string
  return DOMPurify.sanitize(html, purifyConfig)
}

/** 处理输入 */
function handleInput(): void {
  emit('update:modelValue', localContent.value)
}

/** 同步外部值 */
watch(() => props.modelValue, (val) => {
  if (val !== localContent.value) {
    localContent.value = val
  }
})
</script>

<style scoped>
.help-doc-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 4px 0;
  border-bottom: 1px solid var(--theme-border);
  margin-bottom: 8px;
}

.editor-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
  align-items: stretch;
}

.editor-pane {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-body.edit .editor-pane {
  flex: 1;
}

.editor-body.split .editor-pane {
  flex: 1;
  min-width: 0;
}

.editor-body.split .editor-preview,
.editor-body.preview .editor-preview {
  flex: 1;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
}

.editor-textarea {
  flex: 1;
  width: 100%;
  min-height: 0;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  background: var(--theme-surface-elevated);
  color: var(--theme-text);
  resize: none;
  outline: none;
  box-sizing: border-box;
}

.editor-textarea:focus {
  border-color: var(--main-orange);
}

.editor-textarea::placeholder {
  color: var(--theme-text-secondary);
}

.editor-preview {
  background: var(--theme-surface-elevated);
}

.markdown-body {
  max-width: 880px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--theme-text);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.6em 0 0.6em;
  font-weight: 600;
  color: var(--theme-text);
  scroll-margin-top: 16px;
}

.markdown-body :deep(h1) { font-size: 1.8em; }
.markdown-body :deep(h2) {
  font-size: 1.5em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--theme-border);
}
.markdown-body :deep(h3) { font-size: 1.25em; }
.markdown-body :deep(h4) { font-size: 1.1em; }

.markdown-body :deep(p) { margin: 0.8em 0; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.6em;
  margin: 0.8em 0;
}

.markdown-body :deep(li) { margin: 0.3em 0; }

.markdown-body :deep(pre) {
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-body :deep(code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.markdown-body :deep(p code),
.markdown-body :deep(li code) {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1.2em 0;
  font-size: 14px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 10px 14px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--theme-surface-hover);
  font-weight: 600;
}

.markdown-body :deep(tr:hover) { background: var(--theme-surface-hover); }

.markdown-body :deep(blockquote) {
  margin: 1em 0;
  padding: 8px 16px;
  border-left: 4px solid var(--main-orange);
  background: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(a) {
  color: var(--main-orange);
  text-decoration: none;
}

.markdown-body :deep(a:hover) { text-decoration: underline; }
</style>
