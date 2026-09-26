<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { AloudataCategoryTreeNode } from './aloudata-metric-directory'

export interface DirectoryField {
  nodeType: 'field'
  nodeKey: string
  code: string
  label: string
  source: unknown
}

export interface DirectoryCategory extends Omit<AloudataCategoryTreeNode, 'children'> {
  nodeType: 'category'
  nodeKey: string
  fields: DirectoryField[]
  children: DirectoryCategory[]
  hasMore?: boolean
}

defineOptions({ name: 'AloudataFieldDirectory' })

const props = defineProps<{
  nodes: DirectoryCategory[]
  autoExpand?: boolean
  defaultExpandedCategoryIds?: string[]
}>()
const emit = defineEmits<{
  toggle: [category: DirectoryCategory, expanded: boolean]
  more: [category: DirectoryCategory]
}>()
const expanded = ref(new Set(props.defaultExpandedCategoryIds ?? []))
const requestedDefaultCategories = new Set<string>()
const isExpanded = (node: DirectoryCategory) => props.autoExpand || expanded.value.has(node.categoryId)

function expandDefaultCategories() {
  for (const node of props.nodes) {
    if (!props.defaultExpandedCategoryIds?.includes(node.categoryId) || requestedDefaultCategories.has(node.categoryId)) continue
    requestedDefaultCategories.add(node.categoryId)
    emit('toggle', node, true)
  }
}

watch(() => props.defaultExpandedCategoryIds, (ids) => {
  const next = new Set(expanded.value)
  ids?.forEach((id) => next.add(id))
  expanded.value = next
  expandDefaultCategories()
}, { deep: true, flush: 'post' })
watch(() => props.nodes, expandDefaultCategories, { deep: true, flush: 'post' })
onMounted(expandDefaultCategories)

function toggle(node: DirectoryCategory) {
  const next = new Set(expanded.value)
  const open = !isExpanded(node)
  if (open) next.add(node.categoryId)
  else next.delete(node.categoryId)
  expanded.value = next
  if (open) emit('toggle', node, true)
}

const hasNodes = computed(() => props.nodes.length > 0)
</script>

<template>
  <div v-if="hasNodes" class="field-directory" role="tree">
    <div v-for="category in nodes" :key="category.nodeKey" class="category-group" role="treeitem" :aria-expanded="isExpanded(category)">
      <button
        type="button"
        class="category-row"
        :data-category-id="category.categoryId"
        :aria-expanded="isExpanded(category)"
        @click="toggle(category)"
      >
        <span class="category-chevron" aria-hidden="true">{{ isExpanded(category) ? '⌄' : '›' }}</span>
        <svg class="folder-icon" viewBox="0 0 20 16" aria-hidden="true"><path d="M1 2.5h6l2 2h10v10H1z" /></svg>
        <span class="category-name">{{ category.categoryName }}</span>
        <span v-if="category.count != null" class="category-count">{{ category.count }}</span>
      </button>
      <div v-if="isExpanded(category)" class="category-children" role="group">
        <slot v-for="field in category.fields" name="field" :key="field.nodeKey" :field="field" />
        <slot name="loading" :category="category" />
        <button v-if="category.hasMore" class="category-more" type="button" @click.stop="emit('more', category)">加载更多</button>
        <AloudataFieldDirectory
          v-if="category.children.length"
          :nodes="category.children"
          :auto-expand="autoExpand"
          :default-expanded-category-ids="defaultExpandedCategoryIds"
          @toggle="(child, expanded) => emit('toggle', child, expanded)"
          @more="(child) => emit('more', child)"
        >
          <template #field="{ field }"><slot name="field" :field="field" /></template>
          <template #loading="{ category: child }"><slot name="loading" :category="child" /></template>
        </AloudataFieldDirectory>
      </div>
    </div>
  </div>
</template>

<style scoped>
.category-group { min-width: 0; }
.category-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 5px 8px 5px 4px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
}
.category-row:hover { background: var(--el-fill-color-light); }
.category-chevron { width: 12px; color: var(--el-text-color-secondary); font-size: 18px; line-height: 1; }
.folder-icon { width: 18px; height: 16px; flex: none; fill: none; stroke: currentColor; stroke-width: 1.35; }
.category-name { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-count { color: var(--el-text-color-secondary); font-size: 12px; }
.category-children { padding-left: 26px; }
.category-more { width: 100%; padding: 8px; border: 0; background: transparent; color: var(--el-color-primary); text-align: left; cursor: pointer; }
</style>
