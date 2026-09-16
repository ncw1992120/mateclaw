<template>
  <el-dialog v-model="ui.treeVisible" title="添加数据集" width="440px" :close-on-click-modal="false">
    <!-- 搜索框只存在于数据源选择树中（文档 10.3 / 11.3） -->
    <el-input
      v-model="search"
      placeholder="搜索数据源"
      :prefix-icon="Search"
      clearable
      style="margin-bottom: 12px"
      @input="applyFilter"
    />
    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="label"
      default-expand-all
      :filter-node-method="filterNode"
      @node-click="onNodeClick"
      class="src-tree"
    >
      <template #default="{ data }">
        <span class="tree-node">
          <el-icon v-if="data.type === 'category'"><Folder /></el-icon>
          <el-icon v-else><Coin /></el-icon>
          <span>{{ data.label }}</span>
        </span>
      </template>
    </el-tree>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search, Folder, Coin } from '@element-plus/icons-vue'
import { useInsight, MOCK_DATA_SOURCE_TREE } from '../useInsight'

const { state, onSelectLeaf } = useInsight()
const ui = state.ui
const treeData = MOCK_DATA_SOURCE_TREE as any[]
const treeProps = { label: 'label', children: 'children' }
const search = ref('')
const treeRef = ref()

// 搜索：过滤树节点
function filterNode(value: string, data: any) {
  if (!value) return true
  return data.label.includes(value)
}
// 监听搜索输入，调用 el-tree 的 filter
function applyFilter() {
  treeRef.value?.filter(search.value)
}
// 点击节点：分类节点仅展开/收起；叶子节点进入对应配置流程
function onNodeClick(data: any, node: any) {
  if (data.type === 'category') {
    treeRef.value?.setExpanded(node, !node.expanded)
    return
  }
  onSelectLeaf(data)
}
</script>

<style scoped>
.src-tree {
  max-height: 360px;
  overflow: auto;
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
:deep(.el-tree-node__content) {
  height: 34px;
}
</style>
