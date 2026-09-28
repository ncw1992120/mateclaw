<template>
  <div class="component-data-preview" data-testid="component-render-preview">
    <KpiCardWidget
      v-if="component.type === 'kpi'"
      :component="component"
      :component-data="componentData"
      :show-title="true"
    />
    <ChartWidget
      v-else-if="component.type === 'chart'"
      :component="component"
      :component-data="componentData"
      :show-title="true"
    />
    <DataTableWidget
      v-else-if="component.type === 'table'"
      :component="component"
      :component-data="componentData"
      :show-title="true"
    />
    <el-empty v-else description="该组件类型暂不支持数据预览" />
  </div>
</template>

<script setup lang="ts">
import type { InsightComponent, InsightComponentData } from '@/types'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import KpiCardWidget from './KpiCardWidget.vue'

defineOptions({ name: 'ComponentDataPreview' })

defineProps<{
  component: InsightComponent
  componentData: InsightComponentData
}>()
</script>

<style scoped>
.component-data-preview {
  min-height: 220px;
  height: 300px;
  overflow: auto;
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  background: #fff;
}
</style>
