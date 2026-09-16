<template>
  <div class="dataset-source-picker">
    <div class="picker-search-row">
      <el-input v-model="keyword" clearable placeholder="搜索" aria-label="搜索数据集来源" />
    </div>
    <div class="source-tree" role="tree" aria-label="数据集来源类型">
      <div v-for="group in visibleGroups" :key="group.category" class="source-group" role="treeitem">
        <div class="source-group-label">{{ group.label }}</div>
        <button v-for="item in group.items" :key="item.id" type="button" class="source-item" @click="select(item)">
          <span>{{ item.name }}</span><small>{{ item.meta }}</small>
        </button>
      </div>
      <div v-if="!visibleGroups.length" class="source-empty">没有匹配的数据集来源</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { Dataset, Datasource, DatasetSourceType } from '@/types'
import * as datasetApi from '@/api/dataset'
import * as datasourceApi from '@/api/datasource'
import { classifyDatasourceType, datasetCategoryLabel } from '@/utils/data-binding'

export interface DatasetSourceSelection {
  sourceType: DatasetSourceType
  datasourceId?: string
  datasetId?: string
  fileFormat?: string
}

const props = defineProps<{ availableDatasets?: Dataset[]; availableDatasources?: Datasource[] }>()
const emit = defineEmits<{ (event: 'select', value: DatasetSourceSelection): void }>()
const keyword = ref('')
const datasets = ref<Dataset[]>(props.availableDatasets || [])
const datasources = ref<Datasource[]>(props.availableDatasources || [])

const sourceItems = computed(() => {
  const items = [
    ...datasets.value.map((item) => ({ id: `dataset-${item.id}`, name: item.name, meta: item.datasourceName || item.sourceType || '数据集', category: classifyDatasourceType(item.sourceType), selection: { sourceType: (item.sourceType || 'JDBC_TABLE') as DatasetSourceType, datasourceId: item.datasourceId, datasetId: item.id } as DatasetSourceSelection })),
    ...datasources.value.map((item) => ({ id: `datasource-${item.id}`, name: item.name, meta: item.sourceType || '数据源', category: classifyDatasourceType(item.sourceType), selection: { sourceType: 'JDBC_TABLE' as DatasetSourceType, datasourceId: item.id } })),
  ]
  // 原型中的固定节点不是“已配置列表”：它们直接进入对应配置弹窗。
  items.push(
    { id: 'aloudata-view', name: '指标视图', meta: '选择已有视图', category: 'aloudata', selection: { sourceType: 'ALOUDATA_ANALYSIS_VIEW' as DatasetSourceType } },
    { id: 'aloudata-metrics', name: '指标&维度', meta: '配置指标与维度', category: 'aloudata', selection: { sourceType: 'ALOUDATA_METRICS' as DatasetSourceType } },
    ...['Excel', 'CSV', 'TXT', 'JSON', 'Parquet'].map(format => ({ id: `file-${format}`, name: format, meta: '文件数据集', category: 'file', selection: { sourceType: 'FILE' as DatasetSourceType, fileFormat: format } })),
    { id: 'http-api', name: '接口', meta: '', category: 'api', selection: { sourceType: 'HTTP_API' as DatasetSourceType } },
  )
  return items
})

const visibleGroups = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const grouped = new Map<string, { category: string; label: string; items: Array<{ id: string; name: string; meta: string; selection: DatasetSourceSelection }> }>()
  sourceItems.value.filter(item => !query || `${item.name} ${item.meta}`.toLowerCase().includes(query)).forEach((item) => {
    const group = grouped.get(item.category) || { category: item.category, label: datasetCategoryLabel(item.category), items: [] }
    group.items.push(item)
    grouped.set(item.category, group)
  })
  const categories = ['aloudata', 'jdbc', 'api', 'file']
  return categories.map(category => grouped.get(category) || { category, label: datasetCategoryLabel(category as any), items: [] })
    .filter(group => !query || group.items.length > 0)
})

onMounted(async () => {
  if (props.availableDatasets || props.availableDatasources) return
  try { datasets.value = await datasetApi.list() as unknown as Dataset[] } catch { datasets.value = [] }
  try { datasources.value = await datasourceApi.list() as unknown as Datasource[] } catch { datasources.value = [] }
})

function select(item: { selection?: DatasetSourceSelection; type?: DatasetSourceType }): void {
  emit('select', item.selection || { sourceType: item.type as DatasetSourceType })
}
</script>

<style scoped>
.dataset-source-picker { padding: 4px 0; }
.picker-search-row { margin-bottom: 8px; }
.source-tree { max-height: 260px; overflow: auto; }
.source-group { margin-bottom: 8px; }
.source-group-label { color: var(--el-text-color-secondary); font-size: 12px; padding: 4px 8px; }
.source-item { width: 100%; display: flex; justify-content: space-between; border: 0; background: transparent; padding: 8px; color: var(--el-text-color-primary); cursor: pointer; text-align: left; border-radius: 4px; }
.source-item:hover { background: var(--el-fill-color-light); }
.source-item small { color: var(--el-text-color-secondary); }
.source-shortcuts { border-top: 1px solid var(--el-border-color-lighter); padding-top: 6px; }
.source-empty { color: var(--el-text-color-secondary); padding: 12px 8px; text-align: center; }
</style>
