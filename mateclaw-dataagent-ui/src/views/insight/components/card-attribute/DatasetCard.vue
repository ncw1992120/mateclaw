<template>
  <div class="dataset-card">
    <!-- 数据源：点击重开该数据集之前的配置弹窗（按 sourceType 路由，回填已有配置） -->
    <div class="ds-head" @click="reconfigureDataset(dataset.id)" title="点击重新配置该数据集">
      <span class="ds-source-label">数据源</span>
      <span class="ds-source-value">{{ dataset.sourceLabel }}</span>
      <el-icon class="ds-edit"><Edit /></el-icon>
    </div>

    <!-- 数据集别名：用于 Python/SQL 处理时引用，可点击编辑 -->
    <div class="ds-alias">
      <span class="ds-alias-label">数据集别名</span>
      <span v-if="!editingAlias" class="ds-alias-value" @click="startEditAlias" title="点击修改别名">
        <code>[{{ dataset.alias }}]</code>
        <el-icon class="ds-alias-edit"><Edit /></el-icon>
      </span>
      <el-input
        v-else
        ref="aliasInputRef"
        v-model="aliasDraft"
        size="small"
        class="ds-alias-input"
        @keyup.enter="commitAlias"
        @keyup.esc="cancelEditAlias"
        @blur="commitAlias"
      />
    </div>

    <!-- 操作行：字段名称 / 筛选预览 / 移除。
         「筛选预览」打开弹窗：填写本次条件后查询，并将条件下推到源查询。 -->
    <div class="ds-actions">
      <el-button size="small" text bg @click="openFieldMapping(dataset.id)">字段名称</el-button>
      <el-button
        size="small"
        text bg
        title="筛选预览该输入数据集：可添加条件下推后查询"
        @click="openDataDialog(dataset.id)"
      >
        筛选预览
      </el-button>
      <el-button size="small" text bg type="danger" @click="removeDataset(dataset.id)">移除</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { useInsight } from './useInsight'
import type { DatasetConfig } from './useInsight'

const props = defineProps<{ dataset: DatasetConfig }>()
const { openFieldMapping, openDataDialog, removeDataset, reconfigureDataset, renameDataset } = useInsight()

/* ---- 别名内联编辑 ---- */
const editingAlias = ref(false)
const aliasDraft = ref('')
const aliasInputRef = ref<any>(null)

function startEditAlias() {
  aliasDraft.value = props.dataset.alias
  editingAlias.value = true
  nextTick(() => aliasInputRef.value?.focus())
}

function commitAlias() {
  if (!editingAlias.value) return
  renameDataset(props.dataset.id, aliasDraft.value)
  editingAlias.value = false
}

function cancelEditAlias() {
  editingAlias.value = false
}
</script>

<style scoped>
.dataset-card {
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: var(--db-card);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.dataset-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card);
}

/* 数据源标题（点击重配置） */
.ds-head {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
  padding: 4px 6px;
  margin: -4px -6px 6px;
  border-radius: var(--radius-sm);
  transition: background 0.15s ease;
}
.ds-head:hover {
  background: var(--db-muted);
}
.ds-source-label {
  color: var(--db-text-muted);
  font-weight: 600;
}
.ds-source-value {
  color: var(--db-text);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ds-edit {
  color: var(--db-text-muted);
  flex-shrink: 0;
}
.ds-head:hover .ds-edit {
  color: var(--db-accent);
}

/* 别名（可编辑） */
.ds-alias {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary);
  margin-bottom: 12px;
  min-height: 26px;
}
.ds-alias-label {
  color: var(--db-text-muted);
}
.ds-alias-value {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: text;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  transition: background 0.15s ease;
}
.ds-alias-value:hover {
  background: var(--db-muted);
}
.ds-alias-value code {
  background: var(--db-accent-light);
  color: var(--db-accent);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.ds-alias-edit {
  color: var(--db-text-muted);
  font-size: 12px;
}
.ds-alias-input {
  flex: 1;
  max-width: 160px;
}

/* 操作行 */
.ds-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
</style>
