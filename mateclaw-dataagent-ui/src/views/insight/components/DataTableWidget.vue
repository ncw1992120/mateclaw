<template>
  <div class="data-table-widget">
    <div v-if="showTitle" class="table-title">{{ component.title }}</div>
    <div class="table-wrapper">
      <el-table
        v-if="tableData && tableData.rows.length > 0"
        :data="tableRows"
        border
        size="small"
        height="100%"
        style="width: 100%"
      >
        <el-table-column
          v-for="(col, idx) in tableData.columns"
          :key="idx"
          :prop="`col_${idx}`"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <div v-else class="table-placeholder">{{ t('insight.tableNoData') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData } from '@/types'

defineOptions({
  name: 'DataTableWidget',
})

const { t } = useI18n()

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
  /** 是否显示标题 */
  showTitle?: boolean
}>()

const tableData = computed(() => props.componentData?.table)

/** 将行列数据转为 el-table 需要的对象数组格式 */
const tableRows = computed(() => {
  if (!tableData.value) {
    return []
  }
  return tableData.value.rows.map((row) => {
    const obj: Record<string, string> = {}
    row.forEach((val, idx) => {
      obj[`col_${idx}`] = val
    })
    return obj
  })
})
</script>

<style scoped>
.data-table-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  overflow: hidden;
}

.table-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 8px;
}

.table-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 12px;
  color: var(--theme-text-muted);
}
</style>
