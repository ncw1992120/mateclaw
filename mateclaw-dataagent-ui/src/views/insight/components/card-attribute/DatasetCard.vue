<template>
  <div class="dataset-card" @click="reconfigureDataset(dataset.id)">
    <!-- 数据集标题：卡片其他区域打开原有数据集配置弹窗；别名和编辑图标只编辑别名 -->
    <div class="ds-head" :title="`点击配置数据集（${dataset.sourceLabel}）`">
      <span class="ds-source-label">数据集</span>
      <span
        v-if="!editingAlias"
        class="ds-source-value"
        data-dataset-alias
        title="点击修改别名"
        @click.stop="startEditAlias"
      >
        {{ dataset.alias }}
        <el-icon class="ds-edit"><Edit /></el-icon>
      </span>
      <el-input
        v-else
        ref="aliasInputRef"
        v-model="aliasDraft"
        size="small"
        class="ds-alias-input"
        @click.stop
        @keyup.enter="commitAlias"
        @keyup.esc="cancelEditAlias"
        @blur="commitAlias"
      />
    </div>

    <!-- 操作行：查询配置 / 查看数据 / 移除。
         「查询配置」打开查询配置弹窗：展示字段、筛选器绑定、允许排序、分页。
         「查看数据」打开统一数据查看弹窗：按查询配置展示字段与筛选绑定，填写本次值后查询。 -->
    <div class="ds-actions" @click.stop>
      <el-button size="small" text bg @click="onOpenQueryConfig">查询配置</el-button>
      <el-button
        size="small"
        text bg
        title="查看该输入数据集：按查询配置启停筛选条件并查询"
        @click="openDataDialog(dataset.id)"
      >
        查看数据
      </el-button>
      <el-button size="small" text bg type="danger" @click="removeDataset(dataset.id)">移除</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useInsight } from './useInsight'
import type { DatasetConfig } from './useInsight'

const props = defineProps<{ dataset: DatasetConfig }>()
const { state, openQueryConfig, openDataDialog, removeDataset, reconfigureDataset, renameDataset } = useInsight()

function onOpenQueryConfig(): void {
  const hasPageFilter = state.filterCatalog.some((filter) => filter.type === 'filter' || filter.type === 'timeFilter')
  if (!hasPageFilter) {
    ElMessage.warning('请先添加筛选器或时间筛选组件，再配置数据集查询条件')
    return
  }
  openQueryConfig(props.dataset.id)
}

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

/* 数据集标题（点击卡片重配置，标题内的别名点击单独编辑） */
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: text;
}
.ds-edit {
  color: var(--db-text-muted);
  flex-shrink: 0;
}
.ds-source-value:hover .ds-edit {
  color: var(--db-accent);
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
