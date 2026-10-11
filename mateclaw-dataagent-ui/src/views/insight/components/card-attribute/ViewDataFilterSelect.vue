<template>
  <el-select
    :model-value="normalizedValue"
    :multiple="filter?.selectionMode === 'multiple'"
    :collapse-tags="filter?.selectionMode === 'multiple'"
    filterable
    :remote="filter?.optionSource === 'dynamic'"
    :remote-method="searchOptions"
    :loading="loading"
    :disabled="disabled"
    :clearable="filter?.allowNoFilter !== false"
    size="small"
    class="view-data-filter-select"
    :placeholder="placeholder"
    :aria-label="ariaLabel"
    @update:model-value="$emit('update:modelValue', $event)"
    @visible-change="onVisibleChange"
  >
    <el-option v-for="option in resolvedOptions" :key="option.value" :label="option.label" :value="option.value" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as datasourceApi from '@/api/datasource'
import { mergeSelectedFilterOptions, type ViewDataFilterOption } from '@/utils/view-data-filter-options'

interface FilterOptionConfig {
  optionSource?: 'static' | 'dynamic'
  staticOptions?: ViewDataFilterOption[]
  datasourceId?: string | number
  field?: string
  selectionMode?: 'single' | 'multiple'
  allowNoFilter?: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: string | string[]
  filter?: FilterOptionConfig
  disabled?: boolean
  placeholder?: string
  ariaLabel?: string
}>(), {
  placeholder: '请选择',
  ariaLabel: '筛选器本次查询值',
})

defineEmits<{ (event: 'update:modelValue', value: string | string[] | undefined): void }>()

const dynamicOptions = ref<ViewDataFilterOption[]>([])
const loading = ref(false)
const normalizedValue = computed(() => props.modelValue ?? (props.filter?.selectionMode === 'multiple' ? [] : ''))
const resolvedOptions = computed(() => mergeSelectedFilterOptions(
  props.filter?.optionSource === 'static' ? props.filter.staticOptions ?? [] : dynamicOptions.value,
  normalizedValue.value,
))

watch(() => [props.filter?.optionSource, props.filter?.datasourceId, props.filter?.field], () => {
  dynamicOptions.value = []
})

async function loadOptions(keyword?: string): Promise<void> {
  const filter = props.filter
  if (filter?.optionSource !== 'dynamic' || !filter.datasourceId || !filter.field) return
  loading.value = true
  try {
    const result = await datasourceApi.listDimensionValues(filter.datasourceId, filter.field, keyword, 200)
    dynamicOptions.value = ((result as unknown as string[]) ?? []).map((value) => ({ label: value, value }))
  } catch (error) {
    console.error('[ViewDataFilterSelect] 加载筛选器选项失败:', error)
  } finally {
    loading.value = false
  }
}

function searchOptions(keyword: string): void {
  if (props.filter?.optionSource === 'dynamic') void loadOptions(keyword || undefined)
}

function onVisibleChange(visible: boolean): void {
  if (visible && props.filter?.optionSource === 'dynamic' && dynamicOptions.value.length === 0) void loadOptions()
}
</script>

<style scoped>
.view-data-filter-select { width: 100%; }
</style>
