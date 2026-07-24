<template>
  <div class="filter-select-widget">
    <div v-if="showTitle" class="filter-label">{{ component.title }}</div>
    <el-select
      v-model="selectedValue"
      :placeholder="t('insight.filterPlaceholder')"
      clearable
      filterable
      :remote="isDynamic"
      :remote-method="handleRemoteSearch"
      :loading="dynamicLoading"
      size="small"
      style="width: 100%"
      @change="handleChange"
      @visible-change="handleVisibleChange"
    >
      <el-option
        v-for="opt in resolvedOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, FilterComponentConfig } from '@/types'
import * as datasourceApi from '@/api/datasource'

defineOptions({
  name: 'FilterSelectWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 可选项（外部注入，优先级低于 config.staticOptions） */
  options?: Array<{ label: string; value: string }>
  /** 是否显示标题 */
  showTitle?: boolean
}>()

const emit = defineEmits<{
  (e: 'change', payload: { field: string; value: string }): void
}>()

const { t } = useI18n()

const selectedValue = ref<string>('')
const dynamicLoading = ref(false)
const dynamicOptions = ref<Array<{ label: string; value: string }>>([])

/** 从组件 config 中提取筛选配置 */
const filterConfig = computed<FilterComponentConfig | undefined>(() => {
  return props.component.config as FilterComponentConfig | undefined
})

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
  () => filterConfig.value?.field,
  () => {
    selectedValue.value = ''
    dynamicOptions.value = []
  }
)

function handleChange(value: string): void {
  const field = filterConfig.value?.field ?? ''
  emit('change', { field, value })
}
</script>

<style scoped>
.filter-select-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-xs);
  padding: var(--space-md);
  box-sizing: border-box;
  background: var(--db-card);
  border-radius: var(--radius-lg);
}

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--db-text-secondary);
}

.filter-select-widget :deep(.el-select) {
  width: 100%;
}

.filter-select-widget :deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
}
</style>
