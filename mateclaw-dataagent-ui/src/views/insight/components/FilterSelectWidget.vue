<template>
  <div class="filter-select-widget">
    <div v-if="showTitle" class="filter-label">{{ component.title }}</div>
    <el-select
      v-model="selectedValue"
      :placeholder="t('insight.filterPlaceholder')"
      clearable
      size="small"
      style="width: 100%"
      @change="handleChange"
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
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, FilterComponentConfig } from '@/types'

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

/** 从组件 config 中提取筛选配置 */
const filterConfig = computed<FilterComponentConfig | undefined>(() => {
  return props.component.config as FilterComponentConfig | undefined
})

/** 解析最终选项列表：优先使用 config.staticOptions，其次使用外部传入 options */
const resolvedOptions = computed<Array<{ label: string; value: string }>>(() => {
  if (filterConfig.value?.staticOptions && filterConfig.value.staticOptions.length > 0) {
    return filterConfig.value.staticOptions
  }
  return props.options ?? []
})

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
