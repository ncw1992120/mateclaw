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
              <el-icon><Clock /></el-icon> {{ formatDateTime(currentDocument.updateTime, '') }}
            </span>
            <span>
              <el-icon><View /></el-icon> {{ t('helpCenter.viewCount', { count: currentDocument.viewCount }) }}
            </span>
            <span class="mc-tag" :class="currentDocument.status === 'published' ? 'delivered' : 'pending'">
              {{ currentDocument.status === 'published' ? t('helpCenter.published') : t('helpCenter.draft') }}
            </span>
            <template v-if="currentDocument.tags">
              <span
                v-for="tag in currentDocument.tags.split(',')"
                :key="tag"
                class="mc-tag"
              >
                {{ tag.trim() }}
              </span>
            </template>
          </div>
        </div>
        <div v-if="props.canManage" class="content-header-actions">
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

    <!-- 分类视图：选中分类后展示其文档列表 -->
    <template v-else-if="currentCategory">
      <div class="content-header">
        <div class="content-header-info">
          <div class="content-breadcrumb">
            <span class="breadcrumb-link" @click="handleBreadcrumbHome">{{ t('helpCenter.breadcrumbHome') }}</span>
            <template v-for="(crumb, idx) in categoryBreadcrumb" :key="idx">
              <el-icon><ArrowRight /></el-icon>
              <span :class="['breadcrumb-item', { 'breadcrumb-current': idx === categoryBreadcrumb.length - 1 }]">
                {{ crumb.name }}
              </span>
            </template>
          </div>
          <h1 class="content-title">{{ currentCategory.name }}</h1>
          <div class="content-meta">
            <span v-if="currentCategory.description">{{ currentCategory.description }}</span>
            <span class="mc-tag text">{{ t('helpCenter.categoryDocCount', { count: currentCategory.documents?.length ?? 0 }) }}</span>
          </div>
        </div>
      </div>
      <div class="content-body">
        <div v-if="currentCategory.documents && currentCategory.documents.length > 0" class="category-docs">
          <div
            v-for="doc in currentCategory.documents"
            :key="doc.id"
            class="doc-row"
            @click="emit('selectDoc', doc)"
          >
            <el-icon class="doc-row-icon"><Document /></el-icon>
            <div class="doc-row-main">
              <div class="doc-row-title">{{ doc.title }}</div>
              <div v-if="doc.summary" class="doc-row-summary">{{ doc.summary }}</div>
            </div>
            <div class="doc-row-meta">
              <span v-if="doc.status === 'draft'" class="mc-tag pending">{{ t('helpCenter.draft') }}</span>
              <span class="doc-row-views"><el-icon><View /></el-icon>{{ doc.viewCount }}</span>
            </div>
            <el-icon class="doc-row-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <el-empty v-else :description="t('helpCenter.emptyDocDesc')" />
      </div>
    </template>

    <!-- 未选中文档/分类：欢迎占位 -->
    <div v-else class="content-welcome">
      <el-icon class="welcome-icon"><QuestionFilled /></el-icon>
      <div class="welcome-title">{{ t('helpCenter.welcomeTitle') }}</div>
      <div class="welcome-desc">{{ t('helpCenter.welcomeDesc') }}</div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, Delete, ArrowRight, User, Clock, View, Document, QuestionFilled } from '@element-plus/icons-vue'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import type { HelpCategory, HelpDocument, HelpSearchResult, HelpFeedbackSummary } from '@/types'
import * as helpApi from '@/api/help-center'
import { formatDateTime } from '@/utils/time'
import HelpSearchPanel from './HelpSearchPanel.vue'
import HelpRelatedDocs from './HelpRelatedDocs.vue'
import HelpFeedback from './HelpFeedback.vue'

const { t } = useI18n()

const props = defineProps<{
  currentDocument: HelpDocument | null
  currentCategoryId: string | null
  categoryTree: HelpCategory[]
  searchVisible: boolean
  searchResults: HelpSearchResult[]
  searchLoading: boolean
  canManage: boolean
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

/** 带文档列表的分类节点 */
interface CategoryWithDocs extends HelpCategory {
  documents?: HelpDocument[]
}

/** 当前选中的分类（含其文档列表） */
const currentCategory = computed<CategoryWithDocs | null>(() => {
  if (!props.currentCategoryId) {
    return null
  }
  return findCategory(props.categoryTree as CategoryWithDocs[], props.currentCategoryId)
})

/** 递归查找分类节点 */
function findCategory(categories: CategoryWithDocs[], id: string): CategoryWithDocs | null {
  for (const cat of categories) {
    if (cat.id === id) {
      return cat
    }
    if (cat.children && cat.children.length > 0) {
      const found = findCategory(cat.children as CategoryWithDocs[], id)
      if (found) {
        return found
      }
    }
  }
  return null
}

/** 分类视图面包屑路径（祖先链 + 当前分类） */
const categoryBreadcrumb = computed(() => {
  if (!props.currentCategoryId) {
    return []
  }
  const path: { id: string; name: string; type: 'category' }[] = []
  findCategoryPath(props.categoryTree, props.currentCategoryId, path)
  return path
})

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
/* 内容画布：透明底，与配置中心 .config-content 同构，内部自行滚动 */
.help-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: var(--space-lg);
  gap: 16px;
}

.content-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  gap: 16px;
}

.content-header-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 4px;
}

.content-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--db-text-muted);
  flex-wrap: wrap;
}

.breadcrumb-link {
  cursor: pointer;
  color: var(--main-orange);
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: var(--dark-orange);
}

.breadcrumb-item {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breadcrumb-current {
  color: var(--db-text);
  font-weight: 500;
}

.content-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
}

.content-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--db-text-muted);
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

/* 抵消 Element Plus 全局 .el-button+.el-button 的 margin-left，间距只由 gap 承担 */
.content-header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 操作按钮胶囊化，与页头操作区同皮肤 */
.content-header-actions :deep(.el-button) {
  border-radius: 999px;
}

.content-body {
  flex: 1;
  overflow: auto;
}

/* 分类视图文档列表 */
.category-docs {
  max-width: 880px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.doc-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  background: var(--db-card);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.doc-row:hover {
  border-color: color-mix(in srgb, var(--main-orange) 45%, var(--db-border));
  box-shadow: var(--shadow-card);
}

.doc-row-icon {
  font-size: 18px;
  color: var(--main-orange);
  flex-shrink: 0;
}

.doc-row-main {
  flex: 1;
  min-width: 0;
}

.doc-row-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-summary {
  margin-top: 4px;
  font-size: 12px;
  color: var(--db-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.doc-row-views {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--db-text-muted);
}

.doc-row-arrow {
  color: var(--db-text-muted);
  flex-shrink: 0;
  transition: color 0.2s, transform 0.2s;
}

.doc-row:hover .doc-row-arrow {
  color: var(--main-orange);
  transform: translateX(2px);
}

/* 欢迎占位：未选中文档/分类时居中提示 */
.content-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.welcome-icon {
  font-size: 40px;
  color: color-mix(in srgb, var(--main-orange) 55%, var(--db-text-muted));
  margin-bottom: 4px;
}

.welcome-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-secondary);
}

.welcome-desc {
  font-size: 13px;
  color: var(--db-text-muted);
}

.markdown-body {
  max-width: 880px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--db-text-secondary);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.6em 0 0.6em;
  font-weight: 600;
  color: var(--db-text);
  scroll-margin-top: 16px;
}

.markdown-body :deep(h1) { font-size: 1.6em; }
.markdown-body :deep(h2) {
  font-size: 1.4em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--db-border);
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
  background: var(--db-bg);
  border: 1px solid var(--db-border);
  border-radius: 8px;
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
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  padding: 2px 5px;
  border-radius: 4px;
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
  border: 1px solid var(--db-border);
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--db-bg);
  font-weight: 600;
}

.markdown-body :deep(tr:hover) { background: color-mix(in srgb, var(--db-text-muted) 6%, transparent); }

.markdown-body :deep(blockquote) {
  margin: 1em 0;
  padding: 8px 14px;
  border-left: 4px solid var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, var(--db-card));
  color: var(--db-text-secondary);
  border-radius: 0 6px 6px 0;
}

.markdown-body :deep(a) {
  color: var(--main-orange);
  text-decoration: none;
}

.markdown-body :deep(a:hover) { text-decoration: underline; }
</style>
