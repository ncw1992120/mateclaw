<template>
  <div class="filter-select-widget" :class="`title-bar-${props.component.titleBarStyle ?? 'standard'}`">
    <div class="filter-body">
      <div v-if="showTitle !== false && component.titleBarStyle !== 'hidden'" class="filter-label"><DashboardComponentIcon type="filter" :dashboard-theme="dashboardTheme" />{{ component.title }}</div>
      <el-select
        v-model="selectedValue"
        :placeholder="t('insight.filterPlaceholder')"
        :aria-label="component.title || t('insight.filterPlaceholder')"
        :clearable="selectionBehavior.allowNoFilter"
        filterable
        :multiple="selectionBehavior.selectionMode === 'multiple'"
        :collapse-tags="selectionBehavior.selectionMode === 'multiple'"
        :remote="isDynamic"
        :remote-method="handleRemoteSearch"
        :loading="dynamicLoading"
        size="small"
        @change="handleChange"
        @visible-change="handleVisibleChange"
      >
        <el-option
          v-if="selectionBehavior.allowSelectAll"
          :label="t('insight.filterAllOption')"
          :value="FILTER_ALL_VALUE"
        />
        <el-option
          v-for="opt in resolvedOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, FilterComponentConfig, ResolvedDashboardTheme } from '@/types'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import * as datasourceApi from '@/api/datasource'
import { FILTER_ALL_VALUE, getFilterSelectionBehavior, normalizeFilterSelection } from '@/utils/filter-selection'

defineOptions({
  name: 'FilterSelectWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 是否由组件内部显示标题；画布编辑态由统一标题栏显示 */
  showTitle?: boolean
  /** 可选项（外部注入，优先级低于 config.staticOptions） */
  options?: Array<{ label: string; value: string }>
  dashboardTheme?: ResolvedDashboardTheme
}>()

const emit = defineEmits<{
  (e: 'change', payload: { field: string; value: string | string[] | undefined }): void
}>()

const { t } = useI18n()

const selectedValue = ref<string | string[]>('')
const dynamicLoading = ref(false)
const dynamicOptions = ref<Array<{ label: string; value: string }>>([])

/** 从组件 config 中提取筛选配置 */
const filterConfig = computed<FilterComponentConfig | undefined>(() => {
  return props.component.config as FilterComponentConfig | undefined
})

const selectionBehavior = computed(() => getFilterSelectionBehavior(filterConfig.value))

/** 是否为动态选项模式 */
const isDynamic = computed<boolean>(() => {
  return filterConfig.value?.optionSource === 'dynamic'
})

/** 解析最终选项列表：静态优先，动态次之，外部注入兜底 */
const resolvedOptions = computed<Array<{ label: string; value: string }>>(() => {
  if (filterConfig.value?.optionSource === 'static' && filterConfig.value.staticOptions && filterConfig.value.staticOptions.length > 0) {
    return filterConfig.value.staticOptions
  }
  if (isDynamic.value && dynamicOptions.value.length > 0) {
    return dynamicOptions.value
  }
  return props.options ?? []
})

/** 动态加载维度值列表 */
async function loadDynamicOptions(keyword?: string): Promise<void> {
  const config = filterConfig.value
  if (!config?.datasourceId || !config?.field) {
    return
  }
  dynamicLoading.value = true
  try {
    const result = await datasourceApi.listDimensionValues(config.datasourceId, config.field, keyword, 200)
    const values = (result as unknown as string[]) ?? []
    dynamicOptions.value = values.map(v => ({ label: v, value: v }))
  } catch (e) {
    console.error('[FilterSelectWidget] load dynamic options error:', e)
  } finally {
    dynamicLoading.value = false
  }
}

/** 远程搜索（防抖由 el-select 内部处理） */
function handleRemoteSearch(query: string): void {
  if (!isDynamic.value) {
    return
  }
  loadDynamicOptions(query || undefined)
}

/** 下拉框展开时自动加载动态选项 */
function handleVisibleChange(visible: boolean): void {
  if (visible && isDynamic.value && dynamicOptions.value.length === 0) {
    loadDynamicOptions()
  }
}

/** 组件配置变化时重置动态选项和选中值 */
watch(
  () => [filterConfig.value?.field, filterConfig.value?.defaultValue, filterConfig.value?.selectionMode] as const,
  () => {
    const defaultValue = filterConfig.value?.defaultValue
    selectedValue.value = defaultValue == null
      ? (selectionBehavior.value.selectionMode === 'multiple' ? [] : '')
      : (Array.isArray(defaultValue) ? [...defaultValue] : defaultValue)
    dynamicOptions.value = []
  },
  { immediate: true },
)

function handleChange(value: string | string[]): void {
  const field = filterConfig.value?.field ?? ''
  let nextValue: string | string[] = value
  if (Array.isArray(value) && selectionBehavior.value.allowSelectAll && value.includes(FILTER_ALL_VALUE)) {
    nextValue = value.filter((item) => item !== FILTER_ALL_VALUE)
    selectedValue.value = nextValue
  }
  emit('change', { field, value: normalizeFilterSelection(nextValue, filterConfig.value) })
}
</script>

<style scoped>
.filter-select-widget {
  width: 100%;
  height: 100%;
  /* 容器查询锚点：让下面的 @container 按组件自身宽度决策，而不依赖视口 */
  container-type: inline-size;
  display: flex;
  align-items: center;
  padding: var(--space-md);
  box-sizing: border-box;
  background: var(--component-surface, var(--db-card));
  border-radius: var(--component-radius, var(--radius-lg));
}

/* 筛选器的「标题」语义是表单标签而非卡片标题，故标签与控件左右排列；
   宽度不足时（可由用户拖拽缩放产生）回退为上下，避免控件被压成一条缝。 */
.filter-body {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-width: 0;
}

.filter-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--db-text-secondary);
  white-space: nowrap;
}

/* min-width:0 必写：否则 el-select 作为 flex item 不会收缩，会把容器撑破 */
.filter-body :deep(.el-select) {
  flex: 1 1 0%;
  min-width: 0;
}

@container (max-width: 240px) {
  .filter-body {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-xs);
  }

  .filter-body :deep(.el-select) {
    flex: 0 0 auto;
    width: 100%;
  }
}

.filter-body :deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
  background: var(--db-hover);
  box-shadow: 0 0 0 1px var(--db-border) inset;
}

.filter-body :deep(.el-input__inner) {
  color: var(--db-text);
}

.filter-body :deep(.el-input__inner::placeholder) {
  color: var(--db-text-muted);
}

.filter-body :deep(.el-select__caret),
.filter-body :deep(.el-select__clear) {
  color: var(--db-text-secondary);
}
</style>
