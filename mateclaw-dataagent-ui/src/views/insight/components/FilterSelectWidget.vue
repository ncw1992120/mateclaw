<template>
  <div class="filter-select-widget">
    <div class="filter-label">{{ component.title }}</div>
    <el-select
      v-model="selectedValue"
      :placeholder="t('insight.filterPlaceholder')"
      clearable
      size="small"
      style="width: 100%"
      @change="handleChange"
    >
      <el-option
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent } from '@/types'

defineOptions({
  name: 'FilterSelectWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 可选项（由外部联动注入） */
  options?: Array<{ label: string; value: string }>
}>()

const emit = defineEmits<{
  (e: 'change', value: string): void
}>()

const { t } = useI18n()

const selectedValue = ref<string>('')

const options = ref<Array<{ label: string; value: string }>>(props.options ?? [])

watch(
  () => props.options,
  (newOptions) => {
    options.value = newOptions ?? []
  }
)

function handleChange(value: string): void {
  emit('change', value)
}
</script>

<style scoped>
.filter-select-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  box-sizing: border-box;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
}

.filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}
</style>
