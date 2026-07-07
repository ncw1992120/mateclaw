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
        <el-tooltip v-if="props.canManage" :content="t('helpCenter.newCategory')" placement="bottom">
          <el-button link size="small" @click="emit('newCategory')">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip v-if="props.canManage && !sortMode" :content="t('helpCenter.sort')" placement="bottom">
          <el-button link size="small" @click="sortMode = true">
            <el-icon><Rank /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip v-if="props.canManage && sortMode" :content="t('helpCenter.exitSort')" placement="bottom">
          <el-button link size="small" type="primary" @click="sortMode = false">
            <el-icon><CloseBold /></el-icon>
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
          :data="treeData"
          :props="treeProps"
          node-key="id"
          highlight-current
          :expand-on-click-node="!sortMode"
          :draggable="sortMode"
          :allow-drop="allowDrop"
          :default-expanded-keys="expandedKeys"
          :current-node-key="currentNodeKey"
          @node-click="handleNodeClick"
          @node-drop="handleDrop"
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
              <!-- 排序模式：拖拽手柄 -->
              <span v-if="sortMode" class="tree-node-drag-handle" :title="t('helpCenter.sortMode')">
                <el-icon><Rank /></el-icon>
              </span>
              <!-- 分类节点悬停操作菜单 -->
              <el-dropdown
                v-if="props.canManage && !data.isDoc && !sortMode"
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
        <el-empty v-if="treeData.length === 0" :description="t('helpCenter.emptyCategory')" :image-size="60" />
      </div>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Plus, Search, Document, CaretBottom, CaretTop, Edit, Delete,
  Folder, DocumentAdd, Rank, CloseBold
} from '@element-plus/icons-vue'
import type { HelpCategory, HelpDocument } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  categoryTree: HelpCategory[]
  currentCategoryId: string | null
  currentDocumentId: string | null
  canManage: boolean
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
  (e: 'reorderCategories', ids: string[]): void
  (e: 'reorderDocuments', categoryId: string, ids: string[]): void
}>()
/** 搜索关键字 */
const searchKeyword = ref('')
/** 树引用 */
const treeRef = ref()
/** 展开的节点 */
const expandedKeys = ref<string[]>([])
/** 是否处于排序模式 */
const sortMode = ref(false)

/** 树展示数据（排序模式下使用本地副本以支持拖拽） */
const treeData = ref<any[]>([])

/** 深度克隆树数据 */
function cloneTree(nodes: any[]): any[] {
  return nodes.map(node => {
    const cloned: any = { ...node }
    if (node.children && node.children.length > 0) {
      cloned.children = cloneTree(node.children)
    }
    return cloned
  })
}

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
  if (sortMode.value) {
    return
  }
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

/** 监听混合树与排序模式，保持展示数据同步 */
watch([mixedTree, sortMode], ([newTree, newSortMode], [, oldSortMode]) => {
  if (newSortMode) {
    if (!oldSortMode) {
      // 进入排序模式时克隆当前树，后续拖拽直接修改该副本
      treeData.value = cloneTree(newTree)
    }
  } else {
    // 非排序模式下始终与 prop 数据保持同步
    treeData.value = cloneTree(newTree)
  }
}, { immediate: true })

/** 获取节点的父级分类 ID，兼容 parent 为 null 的情况 */
function getNodeParentId(node: any): string | null {
  const parentRawId = node.parent?.data?.rawId
  if (parentRawId !== undefined && parentRawId !== null) {
    return parentRawId
  }
  // 部分场景下 node.parent 为 null，从 treeData 中反查
  const result = findNodeInTree(node.data?.id, treeData.value)
  return result?.parent ? result.parent.rawId : null
}

/** 拖拽放置校验：仅允许同类型同级节点之间移动 */
function allowDrop(draggingNode: any, dropNode: any, type: string): boolean {
  if (!sortMode.value || !draggingNode || !dropNode) {
    return false
  }
  if (type === 'inner') {
    return false
  }
  if (!draggingNode.data || !dropNode.data) {
    return false
  }
  if (draggingNode.data.isDoc !== dropNode.data.isDoc) {
    return false
  }
  const dragParentId = getNodeParentId(draggingNode)
  const dropParentId = getNodeParentId(dropNode)
  return dragParentId === dropParentId
}

/** 在树数据中查找节点及其父级、同级 */
function findNodeInTree(
  nodeId: string,
  nodes: any[],
  parent: any = null
): { node: any; parent: any; siblings: any[] } | null {
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].id === nodeId) {
      return { node: nodes[i], parent, siblings: nodes }
    }
    if (nodes[i].children && nodes[i].children.length > 0) {
      const found = findNodeInTree(nodeId, nodes[i].children, nodes[i])
      if (found) {
        return found
      }
    }
  }
  return null
}

/** 处理拖拽完成，触发重排序事件 */
function handleDrop(draggingNode: any, dropNode: any, dropType: string): void {
  if (!sortMode.value || dropType === 'inner' || !draggingNode || !draggingNode.data) {
    return
  }
  const data = draggingNode.data
  // 从本地 treeData 中查找节点位置，避免依赖 Element Plus 内部 parent 引用
  const result = findNodeInTree(data.id, treeData.value)
  if (!result) {
    return
  }
  const { parent, siblings } = result

  if (data.isDoc) {
    const categoryId = parent ? parent.rawId : null
    if (!categoryId) {
      return
    }
    const docIds = siblings
      .filter((sibling: any) => sibling.isDoc)
      .map((doc: any) => doc.rawId)
    emit('reorderDocuments', categoryId, docIds)
  } else {
    const categoryIds = siblings
      .filter((sibling: any) => !sibling.isDoc)
      .map((category: any) => category.rawId)
    emit('reorderCategories', categoryIds)
  }
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
  border-right: 1px solid var(--theme-border);
  background: var(--theme-surface);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--theme-border);
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
}

.sidebar-actions {
  display: flex;
  gap: 4px;
}

.sidebar-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border);
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
  background: var(--theme-surface-hover);
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(240, 90, 35, 0.12);
  color: var(--main-orange);
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
  color: var(--theme-text-muted);
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
  color: var(--theme-text);
}

.tree-node.is-doc {
  font-size: 13px;
  color: var(--theme-text-secondary);
  padding-left: 4px;
}

.tree-node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  font-size: 14px;
  flex-shrink: 0;
  color: var(--theme-text-muted);
}

.tree-node.is-doc .tree-node-icon {
  color: var(--main-orange);
}

.tree-node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-count {
  font-size: 11px;
  color: var(--theme-text-muted);
  background: var(--theme-surface-hover);
  border-radius: 10px;
  padding: 1px 6px;
  margin-left: 4px;
  flex-shrink: 0;
}

.tree-node-status {
  font-size: 11px;
  color: var(--main-orange);
  background: rgba(240, 90, 35, 0.12);
  border-radius: 10px;
  padding: 1px 6px;
  margin-left: 4px;
  flex-shrink: 0;
}

/* 排序模式拖拽手柄 */
.tree-node-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 4px;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  color: var(--theme-text-muted);
  cursor: grab;
  border-radius: 3px;
  transition: all 0.15s;
}

.tree-node-drag-handle:active {
  cursor: grabbing;
}

.tree-node-drag-handle:hover {
  color: var(--main-orange);
  background: var(--theme-surface-hover);
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
  color: var(--theme-text-muted);
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
  user-select: none;
}

.action-more:hover {
  color: var(--main-orange);
  background: var(--theme-surface-hover);
}
</style>
