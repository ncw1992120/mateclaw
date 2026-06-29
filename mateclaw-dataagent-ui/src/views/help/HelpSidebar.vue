<template>
  <aside class="help-sidebar">
    <div class="sidebar-header">
      <div class="sidebar-title">{{ t('helpCenter.title') }}</div>
      <div class="sidebar-actions">
        <el-tooltip :content="t('helpCenter.expandAll')" placement="bottom">
          <el-button link size="small" @click="handleExpandAll">
            <el-icon><CaretBottom /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip :content="t('helpCenter.collapseAll')" placement="bottom">
          <el-button link size="small" @click="handleCollapseAll">
            <el-icon><CaretTop /></el-icon>
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
          :data="mixedTree"
          :props="treeProps"
          node-key="id"
          highlight-current
          :expand-on-click-node="true"
          :default-expanded-keys="expandedKeys"
          :current-node-key="currentNodeKey"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <div class="tree-node" :class="{ 'is-doc': data.isDoc }">
              <span class="tree-node-icon">
                <template v-if="data.isDoc">
                  <el-icon size="14"><Document /></el-icon>
                </template>
                <template v-else-if="data.icon">
                  {{ data.icon }}
                </template>
                <template v-else>
                  <el-icon size="14"><Folder /></el-icon>
                </template>
              </span>
              <span class="tree-node-label" :title="data.name || data.title">
                {{ data.name || data.title }}
              </span>
              <span v-if="!data.isDoc && data.documentCount > 0" class="tree-node-count">
                {{ data.documentCount }}
              </span>
              <span v-if="data.isDoc && data.status === 'draft'" class="tree-node-status">
                {{ t('helpCenter.draft') }}
              </span>
              <!-- 分类节点悬停操作菜单 -->
              <el-dropdown
                v-if="!data.isDoc"
                class="tree-node-actions"
                trigger="click"
                @command="(cmd: string) => handleCategoryCommand(cmd, data)"
              >
                <span class="action-more" @click.stop>...</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="newDoc">
                      <el-icon><DocumentAdd /></el-icon>
                      {{ t('helpCenter.newDocument') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="newSubCategory">
                      <el-icon><Plus /></el-icon>
                      {{ t('helpCenter.newCategory') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="edit">
                      <el-icon><Edit /></el-icon>
                      {{ t('helpCenter.edit') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>
                      {{ t('helpCenter.delete') }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="mixedTree.length === 0" :description="t('helpCenter.emptyCategory')" :image-size="60" />
      </div>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Plus, Search, Document, CaretBottom, CaretTop, Edit, Delete,
  Folder, DocumentAdd
} from '@element-plus/icons-vue'
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
  (e: 'newDoc', categoryId: string): void
  (e: 'editCategory', category: HelpCategory): void
  (e: 'deleteCategory', category: HelpCategory): void
}>()
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

/** 带文档的分类树 */
interface CategoryWithDocs extends HelpCategory {
  documents?: HelpDocument[]
}

/** 构建分类+文档混合树 */
const mixedTree = computed(() => {
  return buildMixedTree(props.categoryTree as CategoryWithDocs[])
})

/** 当前选中节点的 key */
const currentNodeKey = computed(() => {
  if (props.currentDocumentId) {
    return `doc-${props.currentDocumentId}`
  }
  if (props.currentCategoryId) {
    return `cat-${props.currentCategoryId}`
  }
  return null
})

/** 将分类树转为分类+文档混合树 */
function buildMixedTree(categories: CategoryWithDocs[]): any[] {
  return categories.map(cat => {
    const node: any = {
      id: `cat-${cat.id}`,
      rawId: cat.id,
      name: cat.name,
      icon: cat.icon,
      documentCount: cat.documentCount,
      parentId: cat.parentId,
      isDoc: false,
    }
    const children: any[] = []
    if (cat.children && cat.children.length > 0) {
      children.push(...buildMixedTree(cat.children as CategoryWithDocs[]))
    }
    if (cat.documents && cat.documents.length > 0) {
      for (const doc of cat.documents) {
        children.push({
          id: `doc-${doc.id}`,
          rawId: doc.id,
          name: doc.title,
          title: doc.title,
          isDoc: true,
          rawDoc: doc,
          status: doc.status,
        })
      }
    }
    if (children.length > 0) {
      node.children = children
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

/** 新建文档 */
function handleNewDoc(data: any): void {
  emit('newDoc', data.rawId)
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

/** 分类操作菜单命令分发 */
function handleCategoryCommand(command: string, data: any): void {
  switch (command) {
    case 'newDoc':
      handleNewDoc(data)
      break
    case 'newSubCategory':
      handleAddSubCategory(data)
      break
    case 'edit':
      handleEditCategory(data)
      break
    case 'delete':
      handleDeleteCategory(data)
      break
    default:
      break
  }
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
  if (!treeRef.value) {
    return
  }
  const nodes = treeRef.value.store?.nodesMap
  if (!nodes) {
    return
  }
  const keys: string[] = []
  for (const key in nodes) {
    if (Object.prototype.hasOwnProperty.call(nodes, key)) {
      const node = nodes[key]
      if (!node.data?.isDoc) {
        node.expand()
        keys.push(key)
      }
    }
  }
  expandedKeys.value = keys
}

/** 收起全部 */
function handleCollapseAll(): void {
  if (!treeRef.value) {
    return
  }
  const nodes = treeRef.value.store?.nodesMap
  if (!nodes) {
    return
  }
  for (const key in nodes) {
    if (Object.prototype.hasOwnProperty.call(nodes, key)) {
      nodes[key].collapse()
    }
  }
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

/** 监听当前选中节点，同步树高亮 */
watch(currentNodeKey, (key) => {
  if (key && treeRef.value) {
    nextTick(() => {
      treeRef.value.setCurrentKey(key)
    })
  }
})

/** 暴露方法供父组件调用 */
function setExpandedKeys(keys: string[]): void {
  expandedKeys.value = keys
}

defineExpose({ setExpandedKeys })
</script>

<style scoped>
.help-sidebar {
  width: 260px;
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
  height: 32px;
  border-radius: 4px;
  padding: 0 6px;
  font-size: 13px;
}

.sidebar-tree :deep(.el-tree-node__content:hover) {
  background: #f5f5f5;
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #fff2e8;
  color: #f05a23;
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
  color: #555;
  padding-left: 4px;
}

.tree-node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  font-size: 14px;
  flex-shrink: 0;
  color: #999;
}

.tree-node.is-doc .tree-node-icon {
  color: #1677ff;
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

.tree-node-status {
  font-size: 11px;
  color: #ff9c4d;
  background: #fff2e8;
  border-radius: 10px;
  padding: 1px 6px;
  margin-left: 4px;
  flex-shrink: 0;
}

/* 悬停操作菜单 - 默认隐藏，悬停时显示 */
.tree-node-actions {
  display: none;
  margin-left: 4px;
  flex-shrink: 0;
}

.sidebar-tree :deep(.el-tree-node__content:hover) .tree-node-actions {
  display: inline-flex;
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .tree-node-actions {
  display: inline-flex;
}

.action-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 13px;
  font-weight: 600;
  color: #999;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
  user-select: none;
}

.action-more:hover {
  color: #1677ff;
  background: #e6f7ff;
}
</style>
