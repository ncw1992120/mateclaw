<template>
  <aside class="help-sidebar">
    <div class="sidebar-header">
      <div class="sidebar-title">{{ t('helpCenter.title') }}</div>
      <div class="sidebar-actions">
        <el-tooltip :content="t('helpCenter.expandAll')" placement="bottom">
          <el-button link size="small" @click="handleExpandAll">
            <el-icon><ArrowDown /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip :content="t('helpCenter.collapseAll')" placement="bottom">
          <el-button link size="small" @click="handleCollapseAll">
            <el-icon><ArrowUp /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip :content="t('helpCenter.newCategory')" placement="bottom">
          <el-button link size="small" @click="emit('newCategory')">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>
    <div class="sidebar-search">
      <el-input
        v-model="searchKeyword"
        :placeholder="t('helpCenter.searchPlaceholder')"
        size="small"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleClearSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
    </div>
    <el-scrollbar class="sidebar-scroll">
      <div class="sidebar-tree">
        <el-tree
          ref="treeRef"
          :data="categoryTree"
          :props="treeProps"
          node-key="id"
          highlight-current
          :expand-on-click-node="false"
          :default-expanded-keys="expandedKeys"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <div class="tree-node" :class="{ 'is-doc': !!data.isDoc }">
              <span class="tree-node-icon">
                <template v-if="data.isDoc">
                  <el-icon size="14"><Document /></el-icon>
                </template>
                <template v-else>
                  {{ data.icon || defaultCategoryIcon }}
                </template>
              </span>
              <span class="tree-node-label" :title="data.name || data.title">
                {{ data.name || data.title }}
              </span>
              <span v-if="!data.isDoc && data.documentCount > 0" class="tree-node-count">
                {{ data.documentCount }}
              </span>
              <!-- 分类节点悬停操作按钮 -->
              <span v-if="!data.isDoc" class="tree-node-actions">
                <el-tooltip :content="t('helpCenter.newCategory')" placement="top">
                  <el-icon class="action-icon" @click.stop="handleAddSubCategory(data)"><Plus /></el-icon>
                </el-tooltip>
                <el-tooltip :content="t('helpCenter.edit')" placement="top">
                  <el-icon class="action-icon" @click.stop="handleEditCategory(data)"><Edit /></el-icon>
                </el-tooltip>
                <el-tooltip :content="t('helpCenter.delete')" placement="top">
                  <el-icon class="action-icon action-delete" @click.stop="handleDeleteCategory(data)"><Delete /></el-icon>
                </el-tooltip>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="categoryTree.length === 0" :description="t('helpCenter.emptyCategory')" :image-size="60" />
      </div>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Search, Document, ArrowDown, ArrowUp, Edit, Delete } from '@element-plus/icons-vue'
import type { HelpCategory, HelpDocument } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  categoryTree: HelpCategory[]
  currentCategoryId: string | null
  currentDocumentId: string | null
}>()

const emit = defineEmits<{
  (e: 'selectCategory', category: HelpCategory): void
  (e: 'selectDoc', doc: HelpDocument): void
  (e: 'search', keyword: string): void
  (e: 'clearSearch'): void
  (e: 'newCategory'): void
  (e: 'newSubCategory', parentId: string): void
  (e: 'editCategory', category: HelpCategory): void
  (e: 'deleteCategory', category: HelpCategory): void
}>()

/** 默认分类图标 */
const defaultCategoryIcon = '📘'
/** 搜索关键字 */
const searchKeyword = ref('')
/** 树引用 */
const treeRef = ref()
/** 展开的节点 */
const expandedKeys = ref<string[]>([])

/** 树属性配置 */
const treeProps = {
  children: 'children',
  label: 'name',
}

/** 构建分类树数据 */
const categoryTree = computed(() => {
  return buildMixedTree(props.categoryTree)
})

/** 将分类树转为分类+文档混合树 */
function buildMixedTree(categories: HelpCategory[]): any[] {
  return categories.map(cat => {
    const node: any = {
      id: `cat-${cat.id}`,
      rawId: cat.id,
      name: cat.name,
      icon: cat.icon,
      documentCount: cat.documentCount,
      parentId: cat.parentId,
    }
    if (cat.children && cat.children.length > 0) {
      node.children = buildMixedTree(cat.children)
    }
    return node
  })
}

/** 处理节点点击 */
function handleNodeClick(data: any): void {
  if (data.isDoc) {
    emit('selectDoc', data.rawDoc)
  } else {
    const category: HelpCategory = {
      id: data.rawId,
      name: data.name,
      icon: data.icon,
      documentCount: data.documentCount,
      parentId: data.parentId,
    } as HelpCategory
    emit('selectCategory', category)
  }
}

/** 添加子分类 */
function handleAddSubCategory(data: any): void {
  emit('newSubCategory', data.rawId)
}

/** 编辑分类 */
function handleEditCategory(data: any): void {
  const category: HelpCategory = {
    id: data.rawId,
    name: data.name,
    icon: data.icon,
    documentCount: data.documentCount,
    parentId: data.parentId,
  } as HelpCategory
  emit('editCategory', category)
}

/** 删除分类 */
function handleDeleteCategory(data: any): void {
  const category: HelpCategory = {
    id: data.rawId,
    name: data.name,
    icon: data.icon,
    documentCount: data.documentCount,
    parentId: data.parentId,
  } as HelpCategory
  emit('deleteCategory', category)
}

/** 处理搜索 */
function handleSearch(): void {
  const keyword = searchKeyword.value.trim()
  if (keyword) {
    emit('search', keyword)
  } else {
    emit('clearSearch')
  }
}

/** 清除搜索 */
function handleClearSearch(): void {
  emit('clearSearch')
}

/** 展开全部 */
function handleExpandAll(): void {
  if (treeRef.value) {
    const allKeys = getAllNodeKeys(categoryTree.value)
    expandedKeys.value = allKeys
  }
}

/** 收起全部 */
function handleCollapseAll(): void {
  expandedKeys.value = []
}

/** 获取所有节点 key */
function getAllNodeKeys(nodes: any[]): string[] {
  const keys: string[] = []
  for (const node of nodes) {
    keys.push(node.id)
    if (node.children && node.children.length > 0) {
      keys.push(...getAllNodeKeys(node.children))
    }
  }
  return keys
}

/** 监听分类树数据变化，默认展开所有分类节点 */
watch(() => props.categoryTree, (newTree) => {
  if (newTree.length > 0) {
    const allKeys = getAllCategoryKeys(newTree)
    expandedKeys.value = allKeys
  }
}, { immediate: true })

/** 获取所有分类节点的 key（用于默认展开） */
function getAllCategoryKeys(categories: HelpCategory[]): string[] {
  const keys: string[] = []
  for (const cat of categories) {
    keys.push(`cat-${cat.id}`)
    if (cat.children && cat.children.length > 0) {
      keys.push(...getAllCategoryKeys(cat.children))
    }
  }
  return keys
}

/** 暴露方法供父组件调用 */
function setExpandedKeys(keys: string[]): void {
  expandedKeys.value = keys
}

defineExpose({ setExpandedKeys })
</script>

<style scoped>
.help-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
  background: #fff;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #eee;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.sidebar-actions {
  display: flex;
  gap: 4px;
}

.sidebar-search {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.sidebar-search :deep(.el-input-group__append) {
  padding: 0 8px;
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
}

.sidebar-tree {
  padding: 8px;
}

.sidebar-tree :deep(.el-tree) {
  background: transparent;
}

.sidebar-tree :deep(.el-tree-node__content) {
  height: 30px;
  border-radius: 4px;
  padding: 0 6px;
  font-size: 13px;
}

.sidebar-tree :deep(.el-tree-node__content:hover) {
  background: #f5f5f5;
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e6f7ff;
  color: #1677ff;
  font-weight: 500;
}

/* 多级目录层级缩进优化 */
.sidebar-tree :deep(.el-tree-node) {
  position: relative;
}

.sidebar-tree :deep(.el-tree-node__children) {
  padding-left: 0;
}

.sidebar-tree :deep(.el-tree-node__children .el-tree-node__content) {
  font-size: 13px;
}

.sidebar-tree :deep(.el-tree-node__children .tree-node-icon) {
  font-size: 12px;
  opacity: 0.6;
}

.sidebar-tree :deep(.el-tree-node__expand-icon) {
  color: #999;
  font-size: 12px;
  padding: 2px;
}

.sidebar-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  display: none;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  color: #333;
}

.tree-node.is-doc {
  font-size: 13px;
  color: #666;
  padding-left: 8px;
}

.tree-node-icon {
  margin-right: 6px;
  font-size: 14px;
  flex-shrink: 0;
}

.tree-node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-count {
  font-size: 11px;
  color: #999;
  background: #f0f0f0;
  border-radius: 10px;
  padding: 1px 6px;
  margin-left: 4px;
  flex-shrink: 0;
}

/* 悬停操作按钮 - 默认隐藏，悬停时显示 */
.tree-node-actions {
  display: none;
  align-items: center;
  gap: 2px;
  margin-left: 4px;
  flex-shrink: 0;
}

.sidebar-tree :deep(.el-tree-node__content:hover) .tree-node-actions {
  display: flex;
}

.action-icon {
  font-size: 13px;
  color: #999;
  cursor: pointer;
  padding: 2px;
  border-radius: 3px;
  transition: all 0.15s;
}

.action-icon:hover {
  color: #1677ff;
  background: #e6f7ff;
}

.action-delete:hover {
  color: #ff4d4f;
  background: #fff1f0;
}
</style>
