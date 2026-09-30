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
      :default-expanded-keys="defaultExpandedKeys"
      :filter-node-method="filterNode"
      :loading="loading"
      @node-click="onNodeClick"
      class="src-tree"
    >
      <template #default="{ data }">
        <span class="tree-node" :class="{ 'tree-node--disabled': isUnavailable(data) }" :title="isUnavailable(data) ? unavailableHint : undefined">
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
  disabled?: boolean
}

const unavailableHint = '开发中，还未上线，敬请期待'

function isUnavailable(data: any): boolean {
  return data.disabled === true
}

function buildTree(datasets: Dataset[], datasources: Datasource[]): any[] {
  const leaves: Leaf[] = []
  const aloudataSources: Datasource[] = []

  // 已配置数据集（按类型归类，作为可复用输入）
  datasets.forEach((d, i) => {
    // 仪表盘预览为执行临时草稿自动确认的数据集，不应出现在用户可复用的数据集列表中。
    if (String((d as any).description ?? '').startsWith('看板预览自动确认的数据集：')) return
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
      disabled: true,
    })
  })

  // 数据源连接（Aloudata 按连接分组；其他类型维持原有入口）
  datasources.forEach((s, i) => {
    const cat = classifyDatasourceType(s.sourceType)
    if (cat === 'aloudata') {
      aloudataSources.push(s)
      return
    }
    if (cat === 'unknown') return
    leaves.push({
      id: `datasource-${s.id ?? i}`,
      label: s.name,
      type: cat,
      meta: '数据源连接',
      db: String(s.id),
      disabled: true,
    })
  })

  // 每个 Aloudata 连接分别提供快捷配置入口，确保创建的数据集绑定到所选连接。
  const aloudataConnectionGroups = aloudataSources
    .filter((source) => source.id !== undefined && source.id !== null)
    .map((source) => ({
      id: `aloudata-source-${source.id}`,
      label: source.name,
      type: 'category',
      children: [
        { id: `aloudata-view-${source.id}`, label: '指标视图', type: 'aloudata', meta: '选择已有视图', db: String(source.id), mode: 'metric-view', disabled: true },
        { id: `aloudata-metrics-${source.id}`, label: '指标&维度', type: 'aloudata', meta: '配置指标与维度', db: String(source.id), mode: 'metric-dim', disabled: false },
      ],
    }))
  ;['Excel', 'CSV', 'TXT', 'JSON', 'Parquet'].forEach((fmt) =>
    leaves.push({ id: `file-${fmt}`, label: fmt, type: 'file', meta: '文件数据集', fileType: fmt, disabled: true }),
  )
  leaves.push({ id: 'http-api', label: '接口', type: 'api', meta: '', disabled: true })

  // 按类别分组（顺序：Aloudata / JDBC / 接口 / 文件）；接口项直接平铺到根层。
  const order = ['aloudata', 'jdbc', 'api', 'file'] as const
  const groups: Record<string, Leaf[]> = {}
  leaves.forEach((leaf) => {
    ;(groups[leaf.type] ||= []).push(leaf)
  })
  return order.flatMap((cat) => {
    const children = [...(groups[cat] ?? [])]
    if (cat === 'aloudata') children.push(...aloudataConnectionGroups as any[])
    if (children.length === 0) return []
    if (cat === 'api') return children
    return [{
      id: `cat-${cat}`,
      label: datasetCategoryLabel(cat),
      type: 'category',
      disabled: cat !== 'aloudata',
      children,
    }]
  })
}

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    const [dsList, srcList] = await Promise.all([
      datasetApi.list().catch(() => []) as Promise<unknown>,
      datasourceApi.list().catch(() => []) as Promise<unknown>,
    ])
    treeData.value = buildTree((dsList as Dataset[]) ?? [], (srcList as Datasource[]) ?? [])
    defaultExpandedKeys.value = [
      'cat-aloudata',
      ...((srcList as Datasource[]) ?? [])
        .filter((source) => classifyDatasourceType(source.sourceType) === 'aloudata' && source.id !== undefined && source.id !== null)
        .map((source) => `aloudata-source-${source.id}`),
    ]
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

const treeProps = { label: 'label', children: 'children', disabled: 'disabled' }
const defaultExpandedKeys = ref(['cat-aloudata'])

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
  if (data.disabled) return
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
.tree-node--disabled {
  color: var(--db-text-muted, #999);
  opacity: .55;
  cursor: not-allowed;
}
:deep(.el-tree-node__content) {
  height: 34px;
}
</style>
