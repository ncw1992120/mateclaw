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
      node-key="id"
      default-expand-all
      :filter-node-method="filterNode"
      :loading="loading"
      @node-click="onNodeClick"
      class="src-tree"
    >
      <template #default="{ data }">
        <span class="tree-node">
          <el-icon v-if="data.type === 'category'"><Folder /></el-icon>
          <el-icon v-else><Coin /></el-icon>
          <span>{{ data.label }}</span>
          <small v-if="data.meta" class="tree-meta">{{ data.meta }}</small>
        </span>
      </template>
    </el-tree>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { Search, Folder, Coin } from '@element-plus/icons-vue'
import { useInsight } from './useInsight'
import * as datasetApi from '@/api/dataset'
import * as datasourceApi from '@/api/datasource'
import { classifyDatasourceType, datasetCategoryLabel } from '@/utils/data-binding'
import type { Dataset, Datasource } from '@/types'

const { state, onSelectLeaf } = useInsight()
const ui = state.ui
const search = ref('')
const treeRef = ref()
const loading = ref(false)

// 真实数据源树：已配置数据集 / 数据源连接 + 固定快捷节点（直接进入对应配置弹窗）
const treeData = ref<any[]>([])

interface Leaf {
  id: string
  label: string
  type: 'jdbc' | 'aloudata' | 'api' | 'file'
  meta?: string
  db?: string
  mode?: string
  fileType?: string
  realId?: string
}

function buildTree(datasets: Dataset[], datasources: Datasource[]): any[] {
  const leaves: Leaf[] = []

  // 已配置数据集（按类型归类，作为可复用输入）
  datasets.forEach((d, i) => {
    const cat = classifyDatasourceType(d.sourceType)
    if (cat === 'unknown') return
    leaves.push({
      id: `dataset-${d.id ?? i}`,
      label: d.name,
      type: cat,
      meta: '已配置数据集',
      db: (d as any).datasourceId,
      mode: cat === 'aloudata' ? 'metric-view' : undefined,
      fileType: cat === 'file' ? (d as any).fileFormat : undefined,
      realId: String(d.id),
    })
  })

  // 数据源连接（JDBC 等；Aloudata 走固定快捷节点）
  const aloudataId = datasources.find((s) => classifyDatasourceType(s.sourceType) === 'aloudata')?.id
  datasources.forEach((s, i) => {
    const cat = classifyDatasourceType(s.sourceType)
    if (cat === 'aloudata' || cat === 'unknown') return
    leaves.push({
      id: `datasource-${s.id ?? i}`,
      label: s.name,
      type: cat,
      meta: '数据源连接',
      db: String(s.id),
    })
  })

  // 固定快捷节点：与原型一致，选完直接进入对应配置弹窗
  if (aloudataId !== undefined) {
    leaves.push({ id: 'aloudata-view', label: '指标视图', type: 'aloudata', meta: '选择已有视图', db: String(aloudataId), mode: 'metric-view' })
    leaves.push({ id: 'aloudata-metrics', label: '指标&维度', type: 'aloudata', meta: '配置指标与维度', db: String(aloudataId), mode: 'metric-dim' })
  }
  ;['Excel', 'CSV', 'TXT', 'JSON', 'Parquet'].forEach((fmt) =>
    leaves.push({ id: `file-${fmt}`, label: fmt, type: 'file', meta: '文件数据集', fileType: fmt }),
  )
  leaves.push({ id: 'http-api', label: '接口', type: 'api', meta: '' })

  // 按类别分组（顺序：Aloudata / JDBC / 接口 / 文件）
  const order = ['aloudata', 'jdbc', 'api', 'file'] as const
  const groups: Record<string, Leaf[]> = {}
  leaves.forEach((leaf) => {
    ;(groups[leaf.type] ||= []).push(leaf)
  })
  return order
    .map((cat) => ({
      id: `cat-${cat}`,
      label: datasetCategoryLabel(cat),
      type: 'category',
      children: groups[cat] ?? [],
    }))
    .filter((g) => g.children.length > 0)
}

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    const [dsList, srcList] = await Promise.all([
      datasetApi.list().catch(() => []) as Promise<unknown>,
      datasourceApi.list().catch(() => []) as Promise<unknown>,
    ])
    treeData.value = buildTree((dsList as Dataset[]) ?? [], (srcList as Datasource[]) ?? [])
  } finally {
    loading.value = false
  }
}

// 打开树时刷新（数据可能已变更）；首次挂载也加载一次
watch(
  () => ui.treeVisible,
  (visible) => {
    if (visible) loadTree()
  },
)
onMounted(loadTree)

const treeProps = { label: 'label', children: 'children' }

// 搜索：过滤树节点
function filterNode(value: string, data: any) {
  if (!value) return true
  return (data.label || '').includes(value)
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
.tree-meta {
  color: var(--db-text-muted, #999);
  font-size: 12px;
}
:deep(.el-tree-node__content) {
  height: 34px;
}
</style>
