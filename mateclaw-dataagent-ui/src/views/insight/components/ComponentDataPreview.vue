<template>
  <div class="component-data-preview" data-testid="component-render-preview">
    <KpiCardWidget
      v-if="component.type === 'kpi'"
      :component="previewComponent"
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
import { computed } from 'vue'
import type { InsightComponent, InsightComponentData } from '@/types'
import { projectPythonResultKpi } from '@/utils/kpi-result-projection'
import ChartWidget from './ChartWidget.vue'
import DataTableWidget from './DataTableWidget.vue'
import KpiCardWidget from './KpiCardWidget.vue'

defineOptions({ name: 'ComponentDataPreview' })

const props = defineProps<{
  component: InsightComponent
  componentData: InsightComponentData
}>()

/** KPI 预览以本次结果字段为准，使用临时组件配置，不污染画布中的持久化指标配置。 */
const previewComponent = computed(() => projectPythonResultKpi(props.component, props.componentData, true))
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
