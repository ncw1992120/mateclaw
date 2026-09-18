<template>
  <el-dialog
    v-model="ui.metricStyle.visible"
    :title="`字段样式 · ${metric ? metric.displayName || metric.fieldKey : ''}`"
    width="760px"
    :close-on-click-modal="false"
  >
    <div v-if="metric" class="ms-body">
      <!-- 左侧：样式配置 -->
      <div class="ms-config">
        <div class="ms-field">
          <label class="ms-label">配置字段</label>
          <el-select v-model="ui.metricStyle.field" size="small">
            <el-option v-for="f in KPI_METRIC_FIELDS" :key="f" :value="f" :label="KPI_FIELD_LABELS[f]" />
          </el-select>
        </div>

        <template v-if="style">
          <div class="ms-field">
            <label class="ms-label">大小（px）</label>
            <el-input-number v-model="style.size" size="small" :min="8" :max="96" :step="1" controls-position="right" class="ms-size" />
          </div>

          <div class="ms-field">
            <label class="ms-label">字体</label>
            <el-select v-model="style.family" size="small">
              <el-option v-for="opt in KPI_FONT_OPTIONS" :key="opt.key" :value="opt.key" :label="opt.label" />
            </el-select>
          </div>

          <div class="ms-field ms-inline">
            <div class="ms-inline-item">
              <label class="ms-label">加粗</label>
              <el-switch
                :model-value="style.bold === 'bold'"
                size="small"
                @update:model-value="(v: boolean) => (style!.bold = v ? 'bold' : 'normal')"
              />
            </div>
            <div class="ms-inline-item">
              <label class="ms-label">颜色</label>
              <el-color-picker v-model="style.color" size="small" :predefine="PRESET_COLORS" />
            </div>
          </div>

          <div class="ms-field">
            <label class="ms-label">颜色 HEX</label>
            <el-input v-model="hexInput" size="small" placeholder="#1f2329" @change="applyHex">
              <template #prefix><span class="ms-dot" :style="{ background: style.color }" /></template>
            </el-input>
          </div>
        </template>
      </div>

      <!-- 右侧：展示区（按当前样式渲染四个字段，指标值只读） -->
      <div class="ms-preview">
        <div class="ms-preview-title">展示区</div>
        <div class="ms-preview-stage">
          <div class="ms-preview-name" :style="cssOf('name')">{{ metric.displayName || metric.fieldKey }}</div>
          <div class="ms-preview-value">
            <span class="ms-preview-num" :style="cssOf('value')">12,345.67</span>
            <span v-if="metric.unit" class="ms-preview-unit" :style="cssOf('unit')">{{ metric.unit }}</span>
          </div>
          <div v-if="metric.helperText" class="ms-preview-helper" :style="cssOf('helper')">{{ metric.helperText }}</div>
        </div>
        <p class="ms-preview-hint">
          展示区为实时样式预览；文本（展示名 / 单位 / 辅助说明）在「指标配置」弹窗中编辑，
          卡片画布上的指标会实时同步当前样式。
        </p>
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
import { KPI_FIELD_LABELS, KPI_FONT_OPTIONS, KPI_METRIC_FIELDS, normalizeHexColor, styleToCss } from '@/utils/kpi-metrics'
import type { KpiMetricField } from '@/utils/kpi-metrics'

const { state } = useInsight()
const ui = state.ui

const metric = computed(() => state.kpiMetrics.find((m) => m.fieldKey === ui.metricStyle.fieldKey))
const currentField = computed(() => (ui.metricStyle.field as KpiMetricField) || 'value')
const style = computed(() => metric.value?.styles?.[currentField.value])

const PRESET_COLORS = ['#1f2329', '#646a73', '#8f959e', '#6366f1', '#0f62fe', '#12b76a', '#f59e0b', '#e5484d']

/** HEX 输入框草稿（打开/切换字段时同步当前颜色） */
const hexInput = ref('')
watch(
  () => [ui.metricStyle.visible, ui.metricStyle.fieldKey, ui.metricStyle.field] as const,
  ([visible]) => {
    if (visible && style.value) hexInput.value = style.value.color
  },
  { immediate: true },
)

function applyHex(value: string) {
  const normalized = normalizeHexColor(value)
  if (!normalized) {
    hexInput.value = style.value?.color ?? ''
    ElMessage.warning('颜色格式不正确，请输入 #rgb 或 #rrggbb 格式的 HEX 值')
    return
  }
  if (style.value) style.value.color = normalized
  hexInput.value = normalized
}

function cssOf(field: KpiMetricField): Record<string, string> {
  const s = metric.value?.styles?.[field]
  if (!s) return {}
  const css = styleToCss(s)
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
</script>

<style scoped>
.ms-body {
  display: flex;
  gap: 20px;
  min-height: 220px;
}
.ms-config {
  flex: 1;
  min-width: 0;
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
