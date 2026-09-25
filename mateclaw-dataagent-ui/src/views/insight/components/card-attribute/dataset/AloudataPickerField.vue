<script setup lang="ts">
import type { AloudataSyncedDimension, AloudataSyncedMetric } from '@/types'

const props = defineProps<{
  kind: 'metric' | 'dimension'
  field: AloudataSyncedMetric | AloudataSyncedDimension
  selected: boolean
  unavailableReason?: string
  details?: AloudataSyncedMetric | AloudataSyncedDimension
  loading?: boolean
}>()
const emit = defineEmits<{ toggle: [checked: boolean]; hover: [] }>()

const code = () => props.kind === 'metric'
  ? (props.field as AloudataSyncedMetric).metricName
  : (props.field as AloudataSyncedDimension).dimName
const label = () => props.kind === 'metric'
  ? ((props.field as AloudataSyncedMetric).metricDisplayName || code())
  : ((props.field as AloudataSyncedDimension).dimDisplayName || code())
const title = () => props.kind === 'metric'
  ? ((props.details as AloudataSyncedMetric | undefined)?.metricDisplayName || (props.field as AloudataSyncedMetric).metricDisplayName || '—')
  : ((props.details as AloudataSyncedDimension | undefined)?.dimDisplayName || (props.field as AloudataSyncedDimension).dimDisplayName || '—')
function toggleFromRow() {
  if (props.unavailableReason && !props.selected) return
  emit('toggle', !props.selected)
}
</script>

<template>
  <div
    class="directory-item"
    :class="{ 'is-unavailable': unavailableReason && !selected, 'is-selected': selected }"
    :data-field-code="code()"
    @click="toggleFromRow"
  >
    <el-tooltip v-if="unavailableReason && !selected" :content="unavailableReason" placement="top">
      <span class="disabled-checkbox-target">
        <el-checkbox :model-value="selected" :label="code()" disabled @click.stop @change="(checked: boolean) => emit('toggle', checked)" />
      </span>
    </el-tooltip>
    <el-checkbox v-else :model-value="selected" :label="code()" @click.stop @change="(checked: boolean) => emit('toggle', checked)" />
    <span class="field-type-icon">{{ kind === 'metric' ? '123' : 'abc' }}</span>
    <el-popover
      trigger="hover"
      placement="right"
      :width="290"
      :show-after="250"
      popper-class="aloudata-detail-popper"
      @show="emit('hover')"
    >
      <template #reference>
        <span
          class="directory-item-info"
          :class="kind === 'metric' ? 'metric-detail-trigger' : 'dimension-detail-trigger'"
          :data-metric-name="kind === 'metric' ? code() : undefined"
          :data-dimension-name="kind === 'dimension' ? code() : undefined"
        >
          <span class="directory-item-title">{{ label() }}</span>
          <span class="directory-item-code">{{ code() }}</span>
        </span>
      </template>
      <div class="aloudata-detail-card" :class="kind === 'metric' ? 'metric-detail-card' : 'dimension-detail-card'">
        <div class="detail-card-title"><span class="detail-kind">{{ kind === 'metric' ? '123' : 'abc' }}</span>{{ title() }}</div>
        <div v-if="loading" class="detail-loading">加载详情中…</div>
        <template v-else-if="kind === 'metric'">
          <div class="detail-row"><span>指标编码：</span>{{ code() }}</div>
          <div class="detail-row"><span>指标名称：</span>{{ title() }}</div>
          <div class="detail-row"><span>指标描述：</span>{{ (details as AloudataSyncedMetric | undefined)?.businessCaliber || (field as AloudataSyncedMetric).businessCaliber || '—' }}</div>
          <div class="detail-row"><span>指标类型：</span>{{ (details as AloudataSyncedMetric | undefined)?.type || (field as AloudataSyncedMetric).type || '—' }}</div>
          <div class="detail-row"><span>单位：</span>{{ (details as AloudataSyncedMetric | undefined)?.unit || (field as AloudataSyncedMetric).unit || '—' }}</div>
        </template>
        <template v-else>
          <div class="detail-row"><span>维度编码：</span>{{ code() }}</div>
          <div class="detail-row"><span>维度名称：</span>{{ title() }}</div>
          <div class="detail-row"><span>数据类型：</span>{{ (details as AloudataSyncedDimension | undefined)?.originDataType || (field as AloudataSyncedDimension).originDataType || '—' }}</div>
          <div class="detail-row"><span>维度描述：</span>{{ (details as AloudataSyncedDimension | undefined)?.dimDescription || (field as AloudataSyncedDimension).dimDescription || '—' }}</div>
        </template>
      </div>
    </el-popover>
  </div>
</template>

<style scoped>
.directory-item { display: flex; align-items: center; min-height: 43px; gap: 8px; padding: 5px 12px; border-bottom: 1px solid var(--el-border-color-extra-light); cursor: pointer; }
.directory-item:hover { background: var(--el-fill-color-light); }
.directory-item.is-selected { background: var(--el-color-primary-light-9); }
.directory-item :deep(.el-checkbox) { flex: none; margin-right: 0; }
.field-type-icon { flex: none; margin-right: 2px; color: var(--el-color-primary); font-size: 11px; }
.directory-item-info { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 2px; cursor: help; }
.directory-item-title, .directory-item-code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.directory-item-title { color: var(--el-text-color-primary); font-size: 15px; }
.directory-item-code { color: var(--el-text-color-secondary); font-size: 12px; }
.directory-item-code { display: none; }
.disabled-checkbox-target { display: inline-flex; flex: none; }
.directory-item.is-unavailable { opacity: .55; }
.aloudata-detail-card { color: var(--el-text-color-primary); font-size: 12px; }
.detail-card-title { display: flex; align-items: center; gap: 8px; padding-bottom: 10px; border-bottom: 1px solid var(--el-border-color-lighter); font-size: 16px; font-weight: 600; }
.detail-kind { color: var(--el-color-primary); font-size: 12px; font-weight: 500; }
.detail-row { display: flex; gap: 4px; margin-top: 10px; line-height: 1.5; overflow-wrap: anywhere; }
.detail-row > span { flex: none; color: var(--el-text-color-placeholder); }
.detail-loading { padding-top: 12px; color: var(--el-text-color-secondary); }
</style>
