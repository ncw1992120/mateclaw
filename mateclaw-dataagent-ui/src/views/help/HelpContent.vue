<template>
  <main class="help-content">
    <!-- 搜索结果面板 -->
    <HelpSearchPanel
      v-if="searchVisible"
      :visible="searchVisible"
      :results="searchResults"
      :loading="searchLoading"
      @close="handleCloseSearch"
      @selectDoc="handleSearchSelectDoc"
    />

    <!-- 文档详情视图 -->
    <template v-else-if="currentDocument">
      <div class="content-header">
        <div class="content-header-info">
          <!-- 面包屑导航 -->
          <div class="content-breadcrumb">
            <span class="breadcrumb-link" @click="handleBreadcrumbHome">{{ t('helpCenter.breadcrumbHome') }}</span>
            <template v-for="(crumb, idx) in breadcrumbPath" :key="idx">
              <el-icon><ArrowRight /></el-icon>
              <span
                :class="['breadcrumb-item', { 'breadcrumb-current': idx === breadcrumbPath.length - 1 }]"
                @click="idx < breadcrumbPath.length - 1 && handleBreadcrumbClick(crumb)"
              >
                {{ crumb.name }}
              </span>
            </template>
          </div>
          <h1 class="content-title">{{ currentDocument.title }}</h1>
          <div class="content-meta">
            <span v-if="currentDocument.author">
              <el-icon><User /></el-icon> {{ currentDocument.author }}
            </span>
            <span>
              <el-icon><Clock /></el-icon> {{ currentDocument.updateTime }}
            </span>
            <span>
              <el-icon><View /></el-icon> {{ t('helpCenter.viewCount', { count: currentDocument.viewCount }) }}
            </span>
            <el-tag :type="currentDocument.status === 'published' ? 'success' : 'info'" size="small">
              {{ currentDocument.status === 'published' ? t('helpCenter.published') : t('helpCenter.draft') }}
            </el-tag>
            <template v-if="currentDocument.tags">
              <el-tag
                v-for="tag in currentDocument.tags.split(',')"
                :key="tag"
                size="small"
                type="warning"
                effect="plain"
              >
                {{ tag.trim() }}
              </el-tag>
            </template>
          </div>
        </div>
        <div class="content-header-actions">
          <el-button size="small" @click="handleEditDoc(currentDocument)">
            <el-icon><Edit /></el-icon> {{ t('helpCenter.edit') }}
          </el-button>
          <el-button
            size="small"
            :type="currentDocument.status === 'published' ? 'warning' : 'success'"
            @click="handleTogglePublish(currentDocument)"
          >
            {{ currentDocument.status === 'published' ? t('helpCenter.unpublish') : t('helpCenter.publish') }}
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDeleteDoc(currentDocument)">
            <el-icon><Delete /></el-icon> {{ t('helpCenter.delete') }}
          </el-button>
        </div>
      </div>
      <div class="content-body" ref="contentBodyRef">
        <article
          class="markdown-body"
          v-if="currentDocument.content"
          v-html="renderMarkdown(currentDocument.content)"
        />
        <el-empty v-else :description="t('helpCenter.emptyContent')" />

        <!-- 相关文档推荐 -->
        <HelpRelatedDocs
          :documents="relatedDocs"
          @selectDoc="handleRelatedDocClick"
        />

        <!-- 用户反馈 -->
        <HelpFeedback
          :documentId="currentDocument.id"
          :summary="feedbackSummary"
          @submitted="loadFeedbackSummary"
        />
      </div>
    </template>

    <!-- 未选中文档：空白占位 -->
    <div v-else class="content-blank" />
  </main>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, Delete, ArrowRight, User, Clock, View } from '@element-plus/icons-vue'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import type { HelpCategory, HelpDocument, HelpSearchResult, HelpFeedbackSummary } from '@/types'
import * as helpApi from '@/api/help-center'
import HelpSearchPanel from './HelpSearchPanel.vue'
import HelpRelatedDocs from './HelpRelatedDocs.vue'
import HelpFeedback from './HelpFeedback.vue'

const { t } = useI18n()

const props = defineProps<{
  currentDocument: HelpDocument | null
  categoryTree: HelpCategory[]
  searchVisible: boolean
  searchResults: HelpSearchResult[]
  searchLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'selectDoc', doc: HelpDocument | HelpSearchResult): void
  (e: 'selectCategory', category: HelpCategory): void
  (e: 'goHome'): void
  (e: 'editDoc', doc: HelpDocument): void
  (e: 'deleteDoc', doc: HelpDocument): void
  (e: 'togglePublish', doc: HelpDocument): void
  (e: 'closeSearch'): void
  (e: 'headingsChange', headings: { id: string; text: string; level: number }[]): void
}>()

/** 相关文档 */
const relatedDocs = ref<HelpDocument[]>([])
/** 反馈汇总 */
const feedbackSummary = ref<HelpFeedbackSummary | null>(null)
/** 内容区域引用 */
const contentBodyRef = ref<HTMLElement>()

/** 面包屑路径 */
const breadcrumbPath = computed(() => {
  if (!props.currentDocument) {
    return []
  }
  const path: { id: string; name: string; type: 'category' }[] = []
  // 查找分类路径
  const categoryId = props.currentDocument.categoryId
  findCategoryPath(props.categoryTree, categoryId, path)
  // 添加当前文档
  path.push({ id: props.currentDocument.id, name: props.currentDocument.title, type: 'category' })
  return path
})

/** 递归查找分类路径 */
function findCategoryPath(
  categories: HelpCategory[],
  targetId: string,
  path: { id: string; name: string; type: 'category' }[]
): boolean {
  for (const cat of categories) {
    if (cat.id === targetId) {
      path.push({ id: cat.id, name: cat.name, type: 'category' })
      return true
    }
    if (cat.children && cat.children.length > 0) {
      path.push({ id: cat.id, name: cat.name, type: 'category' })
      if (findCategoryPath(cat.children, targetId, path)) {
        return true
      }
      path.pop()
    }
  }
  return false
}

/** Markdown 渲染 */
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

/** 提取 Markdown 标题目录 */
function extractHeadings(markdown: string): { id: string; text: string; level: number }[] {
  const lines = markdown.split('\n')
  const result: { id: string; text: string; level: number }[] = []
  let inCodeBlock = false
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) {
      continue
    }
    const match = line.match(/^(#{1,6})\s+(.+)$/)
    if (match) {
      const level = match[1].length
      const text = match[2].trim()
      const id = text.replace(/[^\w\u4e00-\u9fa5]+/g, '-').toLowerCase()
      result.push({ id, text, level })
    }
  }
  return result
}

/** 加载相关文档 */
async function loadRelatedDocs(): Promise<void> {
  if (!props.currentDocument) {
    relatedDocs.value = []
    return
  }
  try {
    const data = await helpApi.getRelatedDocuments(props.currentDocument.id)
    relatedDocs.value = data as unknown as HelpDocument[]
  } catch {
    relatedDocs.value = []
  }
}

/** 加载反馈汇总 */
async function loadFeedbackSummary(): Promise<void> {
  if (!props.currentDocument) {
    feedbackSummary.value = null
    return
  }
  try {
    const data = await helpApi.getFeedbackSummary(props.currentDocument.id)
    feedbackSummary.value = data as unknown as HelpFeedbackSummary
  } catch {
    feedbackSummary.value = null
  }
}

/** 面包屑首页点击 */
function handleBreadcrumbHome(): void {
  emit('goHome')
}

/** 面包屑分类点击 */
function handleBreadcrumbClick(crumb: { id: string; name: string }): void {
  emit('selectCategory', { id: crumb.id, name: crumb.name } as HelpCategory)
}

/** 搜索结果选择文档 */
function handleSearchSelectDoc(doc: HelpSearchResult): void {
  emit('selectDoc', doc)
}

/** 关闭搜索 */
function handleCloseSearch(): void {
  emit('closeSearch')
}

/** 相关文档点击 */
function handleRelatedDocClick(doc: HelpDocument): void {
  emit('selectDoc', doc)
}

/** 编辑文档 */
function handleEditDoc(doc: HelpDocument): void {
  emit('editDoc', doc)
}

/** 切换发布状态 */
function handleTogglePublish(doc: HelpDocument): void {
  emit('togglePublish', doc)
}

/** 删除文档 */
async function handleDeleteDoc(doc: HelpDocument): Promise<void> {
  emit('deleteDoc', doc)
}

/** 监听文档变化，加载相关数据和提取标题 */
watch(() => props.currentDocument, (val) => {
  if (val) {
    const headings = extractHeadings(val.content || '')
    emit('headingsChange', headings)
    loadRelatedDocs()
    loadFeedbackSummary()
    nextTick(() => {
      if (contentBodyRef.value) {
        contentBodyRef.value.scrollTop = 0
      }
    })
  } else {
    emit('headingsChange', [])
    relatedDocs.value = []
    feedbackSummary.value = null
  }
}, { immediate: true })

defineExpose({ loadFeedbackSummary })
</script>

<style scoped>
.help-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  overflow: hidden;
}

.content-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 32px 16px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  gap: 16px;
}

.content-header-info {
  flex: 1;
  min-width: 0;
}

.content-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.breadcrumb-link {
  cursor: pointer;
  color: #1677ff;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: #4096ff;
}

.breadcrumb-item {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.content-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #111;
  line-height: 1.3;
}

.content-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #999;
  flex-wrap: wrap;
}

.content-meta > span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.content-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.content-body {
  flex: 1;
  overflow: auto;
  padding: 20px 32px 60px;
}

.content-blank {
  flex: 1;
  background: #fff;
}

.markdown-body {
  max-width: 880px;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.6em 0 0.6em;
  font-weight: 600;
  color: #111;
  scroll-margin-top: 16px;
}

.markdown-body :deep(h1) { font-size: 1.6em; }
.markdown-body :deep(h2) {
  font-size: 1.4em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #eee;
}
.markdown-body :deep(h3) { font-size: 1.2em; }
.markdown-body :deep(h4) { font-size: 1.1em; }

.markdown-body :deep(p) { margin: 0.8em 0; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.6em;
  margin: 0.8em 0;
}

.markdown-body :deep(li) { margin: 0.3em 0; }

.markdown-body :deep(pre) {
  background: #f5f5f5;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 14px;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-body :deep(code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.markdown-body :deep(p code),
.markdown-body :deep(li code) {
  background: #f0f0f0;
  color: #e51;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.85em;
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
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #eee;
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #fafafa;
  font-weight: 600;
}

.markdown-body :deep(tr:hover) { background: #fafafa; }

.markdown-body :deep(blockquote) {
  margin: 1em 0;
  padding: 8px 14px;
  border-left: 4px solid #1677ff;
  background: #f0f5ff;
  color: #555;
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(a) {
  color: #1677ff;
  text-decoration: none;
}

.markdown-body :deep(a:hover) { text-decoration: underline; }
</style>
