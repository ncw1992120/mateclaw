<template>
  <aside class="help-sidebar">
    <div class="sidebar-header">
      <div class="sidebar-title">{{ t('helpCenter.title') }}</div>
      <div class="sidebar-actions">
        <el-tooltip :content="t('helpCenter.expandAll')" placement="bottom">
          <el-button link size="small" @click="handleExpandAll">
            <el-icon><ArrowUpBold /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip :content="t('helpCenter.collapseAll')" placement="bottom">
          <el-button link size="small" @click="handleCollapseAll">
            <el-icon><ArrowDownBold /></el-icon>
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
        clearable
        @keyup.enter="handleSearch"
        @clear="handleClearSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
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
            <div class="tree-node" :class="{ 'is-doc': data.isDoc, 'is-empty': !data.isDoc && !hasChildren(data) }">
              <span class="tree-node-icon">
                <el-icon v-if="data.isDoc" size="14"><Document /></el-icon>
                <el-icon v-else size="14"><Folder /></el-icon>
              </span>
              <span class="tree-node-label" :title="data.name || data.title">
                {{ data.name || data.title }}
              </span>
              <span v-if="!data.isDoc && data.documentCount > 0" class="tree-node-count">
                {{ data.documentCount }}
              </span>
              <span v-if="data.isDoc && data.status === 'draft'" class="mc-tag pending">
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
  Plus, Search, Document, ArrowUpBold, ArrowDownBold, Edit, Delete,
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

/** 分类节点是否有子节点（子分类或文档），用于空分类的降级样式 */
function hasChildren(data: any): boolean {
  return (Array.isArray(data.children) && data.children.length > 0) || (data.documentCount ?? 0) > 0
}

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
/* 目录列：与配置中心导航同处一张纸面，不用硬边框切分，层级靠留白与激活淡底表达 */
.help-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 4px 10px 16px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 8px 10px;
}

.sidebar-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text);
}

.sidebar-actions {
  display: flex;
  gap: 2px;
}

/* 操作图标统一为 24px 幽灵按钮：抵消 EP 全局 .el-button+.el-button margin，间距只由 gap 承担 */
.sidebar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.sidebar-actions :deep(.el-button) {
  width: 24px;
  height: 24px;
  padding: 0;
  margin: 0;
  border-radius: 6px;
  color: var(--db-text-muted);
}

.sidebar-actions :deep(.el-button:hover),
.sidebar-actions :deep(.el-button:focus) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
}

.sidebar-search {
  padding: 0 6px 10px;
}

/* 胶囊搜索框：与技能/智能体页搜索框同族（白底描边 + 聚焦橙环） */
.sidebar-search :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--db-card);
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--db-border);
}

.sidebar-search :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-sm), inset 0 0 0 1px color-mix(in srgb, var(--main-orange) 45%, var(--db-border));
}

.sidebar-search :deep(.el-input__wrapper.is-focused) {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent), inset 0 0 0 1px var(--main-orange);
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
}

.sidebar-tree {
  padding: 4px;
}

.sidebar-tree :deep(.el-tree) {
  background: transparent;
}

.sidebar-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
  padding: 0 8px;
  font-size: 13px;
  margin-bottom: 2px;
}

.sidebar-tree :deep(.el-tree-node__content:hover) {
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  font-weight: 500;
}

/* 多级目录层级缩进优化 */
.sidebar-tree :deep(.el-tree-node) {
  position: relative;
}

/* .sidebar-tree :deep(.el-tree-node__children) {
  padding-left: 0;
} */

/* .sidebar-tree :deep(.el-tree-node__children .el-tree-node__content) {
  font-size: 13px;
} */

.sidebar-tree :deep(.el-tree-node__children .tree-node-icon) {
  font-size: 13px;
}

/* 空分类（无子分类且无文档）：整行弱化为次级灰，与有内容的分类拉开层级 */
.tree-node.is-empty .tree-node-icon {
  opacity: 0.65;
}

.tree-node.is-empty .tree-node-label {
  font-weight: 500;
  color: var(--db-text-muted);
}

.sidebar-tree :deep(.el-tree-node__expand-icon) {
  color: var(--db-text-muted);
  font-size: 12px;
  padding: 2px;
}

.sidebar-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  /* 叶子节点保留展开图标占位宽度，保证有/无子节点的行图标列对齐 */
  visibility: hidden;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  color: var(--db-text);
}

.tree-node.is-doc {
  font-size: 13px;
  color: var(--db-text-secondary);
}

.tree-node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  font-size: 14px;
  flex-shrink: 0;
  color: var(--db-text-muted);
}

.tree-node.is-doc .tree-node-icon {
  color: var(--db-text-muted);
}

/* 选中行图标跟随主题色高亮 */
.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .tree-node-icon {
  color: var(--main-orange);
}

.tree-node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分类节点标题加粗加深，与文档节点拉开层级 */
/* .tree-node:not(.is-doc) .tree-node-label {
  font-weight: 600;
  color: var(--db-text);
} */

.tree-node-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  border-radius: 999px;
  padding: 1px 7px;
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
  color: var(--db-text-muted);
  cursor: grab;
  border-radius: 3px;
  transition: all 0.15s;
}

.tree-node-drag-handle:active {
  cursor: grabbing;
}

.tree-node-drag-handle:hover {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

/* 悬停操作菜单 - 默认隐藏，悬停时显示 */
.tree-node-actions {
  display: none;
  margin-left: 4px;
  flex-shrink: 0;
}

/* .sidebar-tree :deep(.el-tree-node__content:hover) .tree-node-actions {
  display: inline-flex;
}

.sidebar-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .tree-node-actions {
  display: inline-flex;
} */

.action-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-muted);
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
  user-select: none;
}

.action-more:hover {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}
</style>
