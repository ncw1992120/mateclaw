<template>
  <div class="data-table-widget">
    <div class="table-header">
      <div v-if="showTitle" class="table-title">{{ component.title }}</div>
      <div v-if="showTimeFilter" class="table-time-filter">
        <el-date-picker
          v-model="localDateRange"
          type="daterange"
          size="small"
          style="width: 220px"
          value-format="YYYY-MM-DD"
          unlink-panels
          :shortcuts="dateShortcuts"
          :start-placeholder="t('insight.timeRange.startPlaceholder')"
          :end-placeholder="t('insight.timeRange.endPlaceholder')"
          @change="handleDateChange"
        />
      </div>
    </div>
    <!-- Tab 栏（多 Tab 模式） -->
    <div v-if="hasTabs" class="widget-tabs">
      <div
        v-for="tab in tabList"
        :key="tab.id"
        class="widget-tab"
        :class="{ active: activeTabId === tab.id }"
        @click="activeTabId = tab.id"
      >
        {{ tab.title }}
      </div>
    </div>
    <div class="table-wrapper">
      <el-table
        v-if="activeTableData && activeTableData.rows.length > 0"
        :data="activeTableRows"
        border
        size="small"
        height="100%"
        style="width: 100%"
      >
        <el-table-column
          v-for="(col, idx) in activeTableData.columns"
          :key="idx"
          :prop="`col_${idx}`"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <div v-else class="table-placeholder">
        {{ activeTabError || t('insight.tableNoData') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData, TimeRangeValue, ComponentTab } from '@/types'

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

const emit = defineEmits<{
  (e: 'component-time-range-change', payload: { componentId: string; timeRange: TimeRangeValue | undefined }): void
}>()

const tableData = computed(() => props.componentData?.table)
const showTimeFilter = computed(() => props.component.enableTimeFilter)

/** 是否有多 Tab 模式（基于组件配置判断，而非后端返回数据） */
const hasTabs = computed(() => {
  return !!(props.component.tabs && props.component.tabs.length > 0)
})

/** Tab 列表（从组件配置构建） */
const tabList = computed<ComponentTab[]>(() => props.component.tabs ?? [])

/** 当前激活的 Tab ID */
const activeTabId = ref('')

// 初始化 / 切换组件时自动选中第一个 Tab
watch(hasTabs, (val) => {
  if (val && !activeTabId.value) {
    activeTabId.value = tabList.value[0]?.id ?? ''
  }
  if (!val) {
    activeTabId.value = ''
  }
}, { immediate: true })

/** 当前 Tab 的渲染数据 */
const activeTabData = computed(() => {
  if (!hasTabs.value || !activeTabId.value) return null
  return props.componentData?.tabs?.[activeTabId.value] ?? null
})

/** 当前 Tab 的表格数据（Tab 模式下不 fallback 到主数据，避免未配置数据源的 Tab 显示其他 Tab 数据） */
const activeTableData = computed(() => {
  if (hasTabs.value) {
    return activeTabData.value?.table ?? null
  }
  return tableData.value
})
const activeTabError = computed(() => activeTabData.value?.error)

/** 组件级时间选择器绑定值 */
const localDateRange = ref<[string, string] | null>(null)

/** 快捷选项 */
const dateShortcuts = computed(() => [
  { text: t('insight.timeRange.today'), value: () => { const today = new Date(); return [today, today] } },
  { text: t('insight.timeRange.7d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 6); return [start, end] } },
  { text: t('insight.timeRange.30d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 29); return [start, end] } },
  { text: t('insight.timeRange.90d'), value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 89); return [start, end] } },
])

/** 日期选择变化 → 转换为 TimeRangeValue 并 emit */
function handleDateChange(val: [string, string] | null): void {
  if (!val) {
    emit('component-time-range-change', { componentId: props.component.id, timeRange: undefined })
    return
  }
  const [start, end] = val
  const timeRange: TimeRangeValue = { preset: 'custom', start, end }
  emit('component-time-range-change', { componentId: props.component.id, timeRange })
}

/** 将行列数据转为 el-table 需要的对象数组格式 */
const activeTableRows = computed(() => {
  if (!activeTableData.value) {
    return []
  }
  return activeTableData.value.rows.map((row) => {
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

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.table-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
}

.table-time-filter {
  padding: 4px 8px;
  border-bottom: 1px solid var(--theme-border);
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 8px;
}

.widget-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 8px;
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  overflow-x: auto;
}

.widget-tab {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  transition: all 0.15s ease;
}

.widget-tab:hover {
  color: var(--theme-text);
}

.widget-tab.active {
  color: var(--main-orange);
  border-bottom-color: var(--main-orange);
  font-weight: 600;
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
