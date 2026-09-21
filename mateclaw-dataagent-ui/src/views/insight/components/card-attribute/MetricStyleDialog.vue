<template>
  <el-dialog
    v-model="ui.metricStyle.visible"
    class="insight-dialog--md"
    :title="`字段样式 · ${metric ? metric.displayName || metric.fieldKey : ''}`"
    width="420px"
    destroy-on-close
    :close-on-click-modal="false"
    aria-label="指标样式"
  >
    <div v-if="metric" class="ms-body">
      <div class="ms-config" v-if="metric">
        <div class="ms-icon-section">
          <div class="ms-section-title">图标与强调色</div>
          <p class="ms-hint">图标和强调色默认跟随仪表盘主题，可单独关闭或覆盖。</p>
          <div class="ms-icon-row">
            <el-switch v-model="iconEnabled" active-text="显示图标" />
            <el-color-picker v-model="metric.visual!.accentColor" size="small" :predefine="PRESET_COLORS" @change="setCustomAccent" />
          </div>
          <div class="ms-icon-grid" role="grid" aria-label="选择指标图标" @keydown.esc="ui.metricStyle.visible = false">
            <button
              v-for="item in DASHBOARD_ICON_REGISTRY"
              :key="item.key"
              type="button"
              class="ms-icon-option"
              :class="{ active: metric.visual?.iconKey === item.key }"
              :aria-label="item.label"
              :aria-pressed="metric.visual?.iconKey === item.key"
              @click="selectIcon(item.key)"
              @keydown="onIconKeydown($event, item.key)"
            >
              <el-icon aria-hidden="true"><component :is="item.component" /></el-icon>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>
        <div class="ms-section-title">字段样式</div>
        <div v-for="field in KPI_METRIC_FIELDS" :key="field" class="ms-style-row">
          <span class="ms-field-name">{{ KPI_FIELD_LABELS[field] }}</span>
          <el-input-number v-model="metric.styles[field].size" size="small" :min="10" :max="40" :step="1" controls-position="right" class="ms-size" />
          <el-switch
            :model-value="metric.styles[field].bold === 'bold'"
            size="small"
            :aria-label="`${KPI_FIELD_LABELS[field]}加粗`"
            @update:model-value="(v: boolean) => (metric!.styles[field].bold = v ? 'bold' : 'normal')"
          />
          <el-color-picker v-model="metric.styles[field].color" size="small" :predefine="PRESET_COLORS" @change="() => (metric!.styles[field].colorMode = 'custom')" />
        </div>
        <p class="ms-hint">颜色与字号修改会立即同步画布；双击颜色块或点击重置可恢复跟随主题。</p>
        <el-button text type="primary" @click="resetMetric">重置该指标</el-button>
      </div>
    </div>

    <div v-else class="ms-empty">未找到对应指标，请重新打开。</div>

    <template #footer>
      <el-button @click="ui.metricStyle.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from './useInsight'
import { KPI_FIELD_LABELS, KPI_METRIC_FIELDS, defaultMetricStyles, styleToCss } from '@/utils/kpi-metrics'
import { TEXT_COLOR_PRESETS } from '@/utils/color-presets'
import type { KpiMetricField } from '@/utils/kpi-metrics'
import { DASHBOARD_ICON_REGISTRY } from '@/utils/dashboard-icon-registry'

const { state } = useInsight()
const ui = state.ui

const metric = computed(() => state.kpiMetrics.find((m) => m.fieldKey === ui.metricStyle.fieldKey))
const currentField = computed(() => (ui.metricStyle.field as KpiMetricField) || 'value')

/** 取色器预设色板 · 统一收敛到 utils/color-presets.ts（与属性面板背景色板共用一处来源） */
const PRESET_COLORS = TEXT_COLOR_PRESETS

/** HEX 输入框草稿（打开/切换字段时同步当前颜色） */
const iconEnabled = ref(true)
watch(
  () => [ui.metricStyle.visible, ui.metricStyle.fieldKey, ui.metricStyle.field] as const,
  ([visible]) => {
    if (visible && metric.value) {
      metric.value.visual ??= { colorMode: 'theme' }
      iconEnabled.value = metric.value.visual.iconKey !== null
    }
  },
  { immediate: true },
)

watch(iconEnabled, (enabled) => {
  if (metric.value) {
    metric.value.visual ??= { colorMode: 'theme' }
    metric.value.visual.iconKey = enabled ? undefined : null
  }
})

function setCustomAccent() {
  if (metric.value?.visual) metric.value.visual.colorMode = 'custom'
}

function selectIcon(iconKey: string) {
  if (!metric.value) return
  metric.value.visual ??= { colorMode: 'theme' }
  metric.value.visual.iconKey = iconKey
  iconEnabled.value = true
}

function onIconKeydown(event: KeyboardEvent, iconKey: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    selectIcon(iconKey)
  }
  if (event.key.startsWith('Arrow')) {
    const buttons = Array.from((event.currentTarget as HTMLElement).parentElement?.querySelectorAll('button') ?? [])
    const current = buttons.indexOf(event.currentTarget as HTMLButtonElement)
    const columns = 6
    const next = event.key === 'ArrowRight' ? current + 1 : event.key === 'ArrowLeft' ? current - 1 : event.key === 'ArrowDown' ? current + columns : current - columns
    const target = buttons[next] as HTMLButtonElement | undefined
    if (target) { event.preventDefault(); target.focus() }
  }
}

function cssOf(field: KpiMetricField): Record<string, string> {
  const s = metric.value?.styles?.[field]
  if (!s) return {}
  const css = styleToCss(s, '#2563eb')
  return Object.fromEntries(
    css
      .split(';')
      .filter(Boolean)
      .map((line) => {
        const idx = line.indexOf(':')
        return [line.slice(0, idx), line.slice(idx + 1)]
      }),
  )
}

function resetMetric() {
  if (!metric.value) return
  metric.value.styles = defaultMetricStyles()
  metric.value.visual = { iconKey: undefined, colorMode: 'theme' }
  iconEnabled.value = true
  ElMessage.success('已恢复跟随主题')
}
</script>

<style scoped>
.ms-body {
  display: flex;
  min-height: 220px;
}
.ms-config {
  width: 100%;
  min-width: 0;
}
.ms-section-title {
  margin: 0 0 8px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.ms-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}
.ms-icon-section {
  margin-bottom: 18px;
}
.ms-icon-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.ms-icon-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}
.ms-icon-option {
  display: flex;
  min-height: 48px;
  padding: 5px 2px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-blank);
  cursor: pointer;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  font-size: 10px;
}
.ms-icon-option:hover,
.ms-icon-option:focus-visible,
.ms-icon-option.active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  outline: none;
}
.ms-style-row {
  display: grid;
  grid-template-columns: 1fr 92px 42px 32px;
  gap: 8px;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.ms-field-name {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.ms-field {
  margin-bottom: 14px;
}
.ms-label {
  display: block;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.ms-size {
  width: 140px;
}
.ms-inline {
  display: flex;
  gap: 28px;
}
.ms-inline-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ms-inline-item .ms-label {
  margin-bottom: 0;
}
.ms-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  border: 1px solid var(--el-border-color);
}
.ms-preview {
  width: 300px;
  flex-shrink: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  padding-left: 20px;
  display: flex;
  flex-direction: column;
}
.ms-preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}
.ms-preview-stage {
  flex: 1;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  padding: 16px;
  min-height: 120px;
}
.ms-preview-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.ms-preview-helper {
  margin-top: 4px;
}
.ms-preview-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin: 10px 0 0;
}
.ms-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
</style>
