<template>
  <div class="tree-node-wrapper">
    <div
      class="tree-node"
      :class="{ 'is-active': selectedId === group.categoryId }"
      :style="{ paddingLeft: `${16 + level * 18}px` }"
      @click="handleSelect"
    >
      <span
        v-if="hasChildren"
        class="tree-node-expand"
        @click.stop="handleToggle"
      >
        <el-icon :class="{ 'is-expanded': isExpanded }">
          <ArrowRight v-if="!isExpanded" />
          <ArrowDown v-else />
        </el-icon>
      </span>
      <span v-else class="tree-node-expand is-placeholder" />
      <span class="tree-node-icon">
        <el-icon>
          <Folder v-if="!isExpanded || !hasChildren" />
          <FolderOpened v-else />
        </el-icon>
      </span>
      <span class="tree-node-name">{{ group.categoryName }}</span>
      <span class="tree-node-count">{{ count }}</span>
    </div>
    <div
      v-if="hasChildren && isExpanded"
      class="tree-children"
    >
      <category-tree-node
        v-for="child in group.children"
        :key="child.categoryId"
        :group="child"
        :type="type"
        :selected-id="selectedId"
        :expanded-set="expandedSet"
        :level="level + 1"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, ArrowDown, Folder, FolderOpened } from '@element-plus/icons-vue'

defineOptions({
  name: 'CategoryTreeNode',
})

/** 类目树节点分组（指标/维度通用） */
export interface CategoryTreeNodeGroup {
  categoryId: string
  categoryName: string
  parentId?: string
  metricCount?: number
  dimensionCount?: number
  children?: CategoryTreeNodeGroup[]
}

const props = defineProps<{
  group: CategoryTreeNodeGroup
  type: 'metric' | 'dimension'
  selectedId: string
  expandedSet: Set<string>
  level?: number
}>()

const emit = defineEmits<{
  (e: 'select', categoryId: string): void
  (e: 'toggle', payload: { categoryId: string; type: 'metric' | 'dimension' }): void
}>()

const level = computed(() => props.level ?? 0)

const hasChildren = computed(() => {
  return Array.isArray(props.group.children) && props.group.children.length > 0
})

const isExpanded = computed(() => {
  return props.expandedSet.has(props.group.categoryId)
})

const count = computed(() => {
  return props.type === 'metric'
    ? props.group.metricCount ?? 0
    : props.group.dimensionCount ?? 0
})

function handleSelect(): void {
  emit('select', props.group.categoryId)
}

function handleToggle(): void {
  emit('toggle', { categoryId: props.group.categoryId, type: props.type })
}
</script>

<style scoped>
.tree-node-wrapper {
  display: flex;
  flex-direction: column;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.tree-node:hover {
  background: #eef0f5;
}

.tree-node.is-active {
  background: #e6f0ff;
}

.tree-node.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: #165dff;
  border-radius: 0 2px 2px 0;
}

.tree-node-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  cursor: pointer;
  flex-shrink: 0;
}

.tree-node-expand.is-placeholder {
  cursor: default;
}

.tree-node-expand .el-icon {
  font-size: 14px;
  color: #86909c;
  transition: transform 0.2s;
}

.tree-node-expand .el-icon.is-expanded {
  transform: rotate(90deg);
}

.tree-node-icon {
  display: flex;
  align-items: center;
  color: #165dff;
  flex-shrink: 0;
}

.tree-node-name {
  flex: 1;
  font-size: 13px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-count {
  font-size: 11px;
  color: #86909c;
  background: #e8e8e8;
  border-radius: 10px;
  padding: 1px 8px;
  flex-shrink: 0;
  min-width: 20px;
  text-align: center;
}

.tree-node.is-active .tree-node-count {
  background: #165dff;
  color: #fff;
}

.tree-children {
  display: flex;
  flex-direction: column;
}
</style>
