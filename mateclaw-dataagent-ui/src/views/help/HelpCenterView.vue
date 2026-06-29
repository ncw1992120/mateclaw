<template>
  <div class="help-center">
    <!-- 左侧：文档目录树 -->
    <HelpSidebar
      :categoryTree="categoryTree"
      :currentCategoryId="currentCategoryId"
      :currentDocumentId="currentDocument?.id || null"
      @selectCategory="handleCategoryClick"
      @selectDoc="handleDocClick"
      @search="handleSearch"
      @clearSearch="handleClearSearch"
      @newCategory="showCategoryDialog(null)"
      @newSubCategory="handleNewSubCategory"
      @newDoc="handleNewDoc"
      @editCategory="handleEditCategory"
      @deleteCategory="handleDeleteCategory"
    />

    <!-- 右侧：文档内容区 -->
    <HelpContent
      :currentDocument="currentDocument"
      :categoryTree="categoryTree"
      :searchVisible="searchVisible"
      :searchResults="searchResults"
      :searchLoading="searchLoading"
      @selectDoc="handleDocClick"
      @selectCategory="handleCategoryClick"
      @goHome="handleGoHome"
      @editDoc="handleEditDoc"
      @deleteDoc="handleDeleteDoc"
      @togglePublish="handleTogglePublish"
      @closeSearch="handleCloseSearch"
      @headingsChange="handleHeadingsChange"
    />

    <!-- 右侧：目录大纲 -->
    <HelpToc
      v-if="currentDocument && headings.length > 0"
      :headings="headings"
      :activeHeadingId="activeHeadingId"
      @scrollTo="scrollToHeading"
    />

    <!-- 分类编辑弹窗 -->
    <el-dialog v-model="categoryDialogVisible" :title="editingCategory ? t('helpCenter.editCategory') : t('helpCenter.newCategory')" width="480">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item :label="t('helpCenter.categoryName')">
          <el-input v-model="categoryForm.name" :placeholder="t('helpCenter.categoryNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.parentCategory')">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryTreeForSelect"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            :placeholder="t('helpCenter.rootCategory')"
            check-strictly
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('helpCenter.icon')">
          <el-input v-model="categoryForm.icon" :placeholder="t('helpCenter.iconPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.sortOrder')">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.description')">
          <el-input v-model="categoryForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveCategory">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 文档编辑弹窗 -->
    <el-dialog v-model="docDialogVisible" :title="editingDoc ? t('helpCenter.editDocument') : t('helpCenter.newDocument')" width="960" top="5vh">
      <el-form :model="docForm" label-width="80px">
        <el-form-item :label="t('helpCenter.documentTitle')">
          <el-input v-model="docForm.title" :placeholder="t('helpCenter.documentTitlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.category')">
          <el-tree-select
            v-model="docForm.categoryId"
            :data="categoryTreeForSelect"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            :placeholder="t('helpCenter.selectCategory')"
            check-strictly
          />
        </el-form-item>
        <el-form-item :label="t('helpCenter.author')">
          <el-input v-model="docForm.author" :placeholder="t('helpCenter.authorPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.tags')">
          <el-input v-model="docForm.tags" :placeholder="t('helpCenter.tagsPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.summary')">
          <el-input v-model="docForm.summary" :placeholder="t('helpCenter.summaryPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('helpCenter.content')">
          <HelpDocEditor v-model="docForm.content" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveDoc">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { HelpCategory, HelpCategoryRequest, HelpDocument, HelpDocumentRequest, HelpSearchResult } from '@/types'
import * as helpApi from '@/api/help-center'
import HelpSidebar from './HelpSidebar.vue'
import HelpContent from './HelpContent.vue'
import HelpToc from './HelpToc.vue'
import HelpDocEditor from './HelpDocEditor.vue'

const { t } = useI18n()

/** 分类树（含文档列表，方便一次性渲染） */
interface CategoryWithDocs extends HelpCategory {
  documents: HelpDocument[]
}
const categoryTree = ref<CategoryWithDocs[]>([])
/** 当前选中的分类 ID */
const currentCategoryId = ref<string | null>(null)
/** 当前查看的文档 */
const currentDocument = ref<HelpDocument | null>(null)
/** 当前文档的标题目录 */
interface Heading { id: string; text: string; level: number }
const headings = ref<Heading[]>([])
/** 当前激活的标题 */
const activeHeadingId = ref<string>('')

/** 搜索状态 */
const searchVisible = ref(false)
const searchResults = ref<HelpSearchResult[]>([])
const searchLoading = ref(false)

/** 分类弹窗 */
const categoryDialogVisible = ref(false)
const editingCategory = ref<HelpCategory | null>(null)
const categoryForm = ref<HelpCategoryRequest>({ name: '', parentId: '0', sortOrder: 0, icon: '', description: '' })

/** 文档弹窗 */
const docDialogVisible = ref(false)
const editingDoc = ref<HelpDocument | null>(null)
const docForm = ref<HelpDocumentRequest>({ title: '', categoryId: '', content: '', author: '', sortOrder: 0, status: 'draft', tags: '', summary: '' })

/** 分类树选择器数据（添加根节点，递归构建） */
const categoryTreeForSelect = computed(() => {
  const tree = buildSelectTree(categoryTree.value)
  return [{ id: '0', name: t('helpCenter.rootCategory'), children: tree }]
})

/** 递归构建分类选择器数据 */
function buildSelectTree(categories: CategoryWithDocs[]): any[] {
  return categories.map(cat => {
    const node: any = {
      id: cat.id,
      name: cat.name,
    }
    if (cat.children && cat.children.length > 0) {
      node.children = buildSelectTree(cat.children as CategoryWithDocs[])
    }
    return node
  })
}

/** 加载分类树 */
async function fetchCategoryTree(): Promise<void> {
  try {
    const tree = await helpApi.listCategoryTree()
    const treeData = tree as unknown as HelpCategory[]
    // 递归为每个分类（含子分类）加载文档列表
    categoryTree.value = await enrichCategoryTree(treeData)
  } catch {
    // 错误已在拦截器处理
  }
}

/** 递归为分类树中的每个节点加载文档列表 */
async function enrichCategoryTree(categories: HelpCategory[]): Promise<CategoryWithDocs[]> {
  return Promise.all(
    categories.map(async (cat) => {
      let docs: HelpDocument[] = []
      try {
        const data = await helpApi.listDocuments(cat.id)
        docs = data as unknown as HelpDocument[]
      } catch {
        docs = []
      }
      const enriched: CategoryWithDocs = { ...cat, documents: docs }
      // 递归处理子分类
      if (cat.children && cat.children.length > 0) {
        enriched.children = await enrichCategoryTree(cat.children)
      }
      return enriched
    })
  )
}

/** 点击分类节点 */
function handleCategoryClick(category: HelpCategory): void {
  currentCategoryId.value = category.id
  currentDocument.value = null
  searchVisible.value = false
}

/** 点击文档查看详情 */
async function handleDocClick(doc: HelpDocument | HelpSearchResult): Promise<void> {
  try {
    const data = await helpApi.getDocument(doc.id)
    const detail = data as unknown as HelpDocument
    currentDocument.value = detail
    currentCategoryId.value = detail.categoryId
    searchVisible.value = false
    await nextTick()
  } catch {
    // 错误已在拦截器处理
  }
}

/** 搜索文档 */
async function handleSearch(keyword: string): Promise<void> {
  if (keyword.length < 2) {
    ElMessage.warning(t('helpCenter.searchMinLength'))
    return
  }
  searchVisible.value = true
  searchLoading.value = true
  try {
    const data = await helpApi.searchDocuments(keyword)
    searchResults.value = data as unknown as HelpSearchResult[]
  } catch {
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

/** 清除搜索 */
function handleClearSearch(): void {
  searchVisible.value = false
  searchResults.value = []
}

/** 关闭搜索 */
function handleCloseSearch(): void {
  searchVisible.value = false
  searchResults.value = []
}

/** 返回首页 */
function handleGoHome(): void {
  currentCategoryId.value = null
  currentDocument.value = null
  searchVisible.value = false
}

/** 标题目录变化 */
function handleHeadingsChange(newHeadings: Heading[]): void {
  headings.value = newHeadings
}

/** 滚动到指定标题 */
function scrollToHeading(id: string): void {
  const el = document.getElementById(`heading-${id}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/** 显示分类编辑弹窗 */
function showCategoryDialog(category: HelpCategory | null): void {
  editingCategory.value = category
  if (category) {
    categoryForm.value = {
      name: category.name,
      parentId: category.parentId,
      sortOrder: category.sortOrder,
      icon: category.icon || '',
      description: category.description || '',
    }
  } else {
    categoryForm.value = { name: '', parentId: '0', sortOrder: 0, icon: '', description: '' }
  }
  categoryDialogVisible.value = true
}

/** 新建子分类 */
function handleNewSubCategory(parentId: string): void {
  editingCategory.value = null
  categoryForm.value = { name: '', parentId: parentId, sortOrder: 0, icon: '', description: '' }
  categoryDialogVisible.value = true
}

/** 新建文档（从侧边栏分类触发） */
function handleNewDoc(categoryId: string): void {
  currentCategoryId.value = categoryId
  showDocDialog(null)
}

/** 编辑分类（从侧边栏触发） */
function handleEditCategory(category: HelpCategory): void {
  showCategoryDialog(category)
}

/** 删除分类（从侧边栏触发） */
async function handleDeleteCategory(category: HelpCategory): Promise<void> {
  try {
    await ElMessageBox.confirm(t('helpCenter.deleteCategoryConfirm'), t('common.confirm'), { type: 'warning' })
    await helpApi.deleteCategory(category.id)
    ElMessage.success(t('helpCenter.deleteSuccess'))
    if (currentCategoryId.value === category.id) {
      currentCategoryId.value = null
    }
    await fetchCategoryTree()
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}

/** 保存分类 */
async function handleSaveCategory(): Promise<void> {
  if (!categoryForm.value.name) {
    ElMessage.warning(t('helpCenter.categoryNameRequired'))
    return
  }
  try {
    if (editingCategory.value) {
      await helpApi.updateCategory(editingCategory.value.id, categoryForm.value)
      ElMessage.success(t('helpCenter.updateSuccess'))
    } else {
      await helpApi.createCategory(categoryForm.value)
      ElMessage.success(t('helpCenter.createSuccess'))
    }
    categoryDialogVisible.value = false
    await fetchCategoryTree()
  } catch {
    // 错误已在拦截器处理
  }
}

/** 显示文档编辑弹窗 */
function showDocDialog(doc: HelpDocument | null): void {
  editingDoc.value = doc
  if (doc) {
    docForm.value = {
      categoryId: doc.categoryId,
      title: doc.title,
      content: doc.content,
      sortOrder: doc.sortOrder,
      status: doc.status,
      author: doc.author,
      tags: doc.tags || '',
      summary: doc.summary || '',
    }
  } else {
    docForm.value = {
      categoryId: currentCategoryId.value || '',
      title: '',
      content: '',
      sortOrder: 0,
      status: 'draft',
      author: '',
      tags: '',
      summary: '',
    }
  }
  docDialogVisible.value = true
}

/** 编辑文档 */
function handleEditDoc(doc: HelpDocument): void {
  showDocDialog(doc)
}

/** 保存文档 */
async function handleSaveDoc(): Promise<void> {
  if (!docForm.value.title) {
    ElMessage.warning(t('helpCenter.documentTitleRequired'))
    return
  }
  if (!docForm.value.categoryId) {
    ElMessage.warning(t('helpCenter.selectCategoryRequired'))
    return
  }
  try {
    if (editingDoc.value) {
      await helpApi.updateDocument(editingDoc.value.id, docForm.value)
      ElMessage.success(t('helpCenter.updateSuccess'))
    } else {
      await helpApi.createDocument(docForm.value)
      ElMessage.success(t('helpCenter.createSuccess'))
    }
    docDialogVisible.value = false
    await fetchCategoryTree()
    if (currentDocument.value && editingDoc.value) {
      const data = await helpApi.getDocument(editingDoc.value.id)
      currentDocument.value = data as unknown as HelpDocument
    }
  } catch {
    // 错误已在拦截器处理
  }
}

/** 删除文档 */
async function handleDeleteDoc(doc: HelpDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(t('helpCenter.deleteDocumentConfirm'), t('common.confirm'), { type: 'warning' })
    await helpApi.deleteDocument(doc.id)
    ElMessage.success(t('helpCenter.deleteSuccess'))
    if (currentDocument.value?.id === doc.id) {
      currentDocument.value = null
    }
    await fetchCategoryTree()
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}

/** 切换发布状态 */
async function handleTogglePublish(doc: HelpDocument): Promise<void> {
  try {
    if (doc.status === 'published') {
      await helpApi.unpublishDocument(doc.id)
      ElMessage.success(t('helpCenter.unpublishSuccess'))
    } else {
      await helpApi.publishDocument(doc.id)
      ElMessage.success(t('helpCenter.publishSuccess'))
    }
    if (currentDocument.value?.id === doc.id) {
      const data = await helpApi.getDocument(doc.id)
      currentDocument.value = data as unknown as HelpDocument
    }
    await fetchCategoryTree()
  } catch {
    // 错误已在拦截器处理
  }
}

/** 监听文档变化，更新标题目录 */
watch(currentDocument, (val) => {
  if (!val) {
    headings.value = []
  }
})

onMounted(() => {
  fetchCategoryTree()
})
</script>

<style scoped>
.help-center {
  display: flex !important;
  flex-direction: row !important;
  flex-wrap: nowrap !important;
  flex: 1 !important;
  width: 100% !important;
  height: 100% !important;
  min-height: 0 !important;
  background: #fff;
  overflow: hidden;
}

.help-center :deep(.help-sidebar) {
  flex: 0 0 260px !important;
  min-width: 260px !important;
  max-width: 260px !important;
  height: 100% !important;
}

.help-center :deep(.help-content) {
  flex: 1 1 0 !important;
  min-width: 0 !important;
  height: 100% !important;
  overflow: hidden !important;
}

.help-center :deep(.help-toc) {
  flex: 0 0 200px !important;
  min-width: 200px !important;
  max-width: 200px !important;
  height: 100% !important;
}
</style>
