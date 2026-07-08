<template>
  <div class="time-filter-widget">
    <div v-if="showTitle" class="time-filter-label">{{ component.title }}</div>
    <div class="time-filter-body">
      <el-select
        v-model="selectedPreset"
        :placeholder="t('insight.timeRange.placeholder')"
        size="small"
        clearable
        style="width: 100%"
        @change="handlePresetChange"
      >
        <el-option
          v-for="preset in availablePresets"
          :key="preset.value"
          :label="t(preset.label)"
          :value="preset.value"
        />
      </el-select>
      <el-date-picker
        v-if="selectedPreset === 'custom'"
        v-model="customDateRange"
        type="daterange"
        size="small"
        style="width: 100%; margin-top: 4px"
        value-format="YYYY-MM-DD"
        :start-placeholder="t('insight.timeRange.startPlaceholder')"
        :end-placeholder="t('insight.timeRange.endPlaceholder')"
        @change="handleDateChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, TimeRangePreset, TimeRangeValue, TimeFilterComponentConfig } from '@/types'
import { TIME_RANGE_PRESETS } from '@/types'

defineOptions({
  name: 'TimeFilterWidget',
})

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 是否显示标题 */
  showTitle?: boolean
}>()

const emit = defineEmits<{
  (e: 'change', payload: { field: string; timeRange: TimeRangeValue }): void
}>()

const { t } = useI18n()

const selectedPreset = ref<TimeRangePreset | ''>('')
const customDateRange = ref<[string, string] | null>(null)

/** 从组件 config 提取时间筛选配置 */
const timeFilterConfig = computed<TimeFilterComponentConfig>(() => {
  return (props.component.config as TimeFilterComponentConfig) ?? { field: 'metric_time' }
})

/** 根据配置过滤可用预设 */
const availablePresets = computed(() => {
  if (!timeFilterConfig.value.availablePresets) {
    return TIME_RANGE_PRESETS
  }
  const allowed = new Set(timeFilterConfig.value.availablePresets)
  return TIME_RANGE_PRESETS.filter((p) => allowed.has(p.value))
})

/** 预设变化 */
function handlePresetChange(preset: TimeRangePreset | ''): void {
  if (!preset) {
    emit('change', { field: timeFilterConfig.value.field, timeRange: undefined as unknown as TimeRangeValue })
    return
  }
  if (preset === 'custom') {
    // 等待用户选择日期范围
    return
  }
  const range: TimeRangeValue = { preset }
  emit('change', { field: timeFilterConfig.value.field, timeRange: range })
}

/** 自定义日期范围变化 */
function handleDateChange(dates: [string, string] | null): void {
  if (!dates || !dates[0] || !dates[1]) {
    emit('change', { field: timeFilterConfig.value.field, timeRange: undefined as unknown as TimeRangeValue })
    return
  }
  const range: TimeRangeValue = {
    preset: 'custom',
    start: dates[0],
    end: dates[1],
  }
  emit('change', { field: timeFilterConfig.value.field, timeRange: range })
}
</script>

<style scoped>
.time-filter-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  box-sizing: border-box;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
}

.time-filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}

.time-filter-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
