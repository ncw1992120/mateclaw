<template>
  <el-drawer
    :model-value="visible"
    title="主题外观"
    size="360px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="dashboard-theme-panel" :style="themeTokenStyle">
      <section class="theme-section">
        <div class="theme-section-title">预设主题</div>
        <p class="theme-hint">画布保持中性底色；主题色用于组件强调色、卡片形态与层次。</p>
        <div class="theme-presets" role="radiogroup" aria-label="仪表盘预设主题" @keydown="onPresetKeydown">
          <button
            data-preset-id="default"
            type="button"
            role="radio"
            class="theme-preset"
            :class="{ active: modelValue.presetId === 'default' }"
            :aria-checked="modelValue.presetId === 'default'"
            :tabindex="modelValue.presetId === 'default' ? 0 : -1"
            @click="requestPreset('default')"
          >
            <span class="theme-swatch theme-swatch-default">
              <i /><i /><i />
            </span>
            <span class="theme-preset-label">默认主题</span>
          </button>
          <button
            v-for="(preset, id) in DASHBOARD_THEME_PRESETS"
            :key="id"
            :data-preset-id="id"
            type="button"
            role="radio"
            class="theme-preset"
            :class="{ active: modelValue.presetId === id }"
            :aria-checked="modelValue.presetId === id"
            :tabindex="modelValue.presetId === id ? 0 : -1"
            @click="requestPreset(id)"
          >
            <span class="theme-swatch" :style="{ background: DASHBOARD_CANVAS_BACKGROUND, borderColor: '#E1E5EB' }">
              <i v-for="color in getPresetAccents(id)" :key="color" :style="{ background: color }" />
            </span>
            <span class="theme-preset-label">{{ preset.label }}</span>
          </button>
        </div>
      </section>

      <section class="theme-section">
        <div class="theme-section-title">标准组件样式</div>
        <p class="theme-hint">主色、辅助色与强调色按组件类型协调分配；主题圆角和阴影也会形成不同的卡片气质。组件上的自定义样式优先保留。</p>
        <div class="theme-option-group">
          <span class="theme-option-label">语义图标</span>
          <div class="theme-option-buttons" role="radiogroup" aria-label="语义图标">
            <button v-for="item in iconOptions" :key="item.value" type="button" role="radio" :aria-checked="resolved.iconMode === item.value" :class="{ active: resolved.iconMode === item.value }" @click="updateStandardOption('iconMode', item.value)">{{ item.label }}</button>
          </div>
        </div>
        <div class="theme-option-group">
          <span class="theme-option-label">层次强度</span>
          <div class="theme-option-buttons" role="radiogroup" aria-label="层次强度">
            <button v-for="item in hierarchyOptions" :key="item.value" type="button" role="radio" :aria-checked="resolved.hierarchy === item.value" :class="{ active: resolved.hierarchy === item.value }" @click="updateStandardOption('hierarchy', item.value)">{{ item.label }}</button>
          </div>
        </div>
        <div class="theme-option-group">
          <span class="theme-option-label">分区配色</span>
          <div class="theme-option-buttons" role="radiogroup" aria-label="同类组件配色">
            <button v-for="item in colorModeOptions" :key="item.value" type="button" role="radio" :aria-checked="resolved.componentColorMode === item.value" :class="{ active: resolved.componentColorMode === item.value }" @click="updateStandardOption('componentColorMode', item.value)">{{ item.label }}</button>
          </div>
        </div>
        <div class="theme-option-group">
          <InsightColorField
            :model-value="resolved.accentAlt"
            label="辅助色"
            :suggested-colors="accentAltChoices"
            @change="updateAccentAlt"
          />
          <button data-testid="reset-accent-alt" type="button" class="theme-reset-button" @click="resetAccentAlt">
            恢复预设默认色
          </button>
        </div>
        <p v-if="validationMessage" class="theme-error" role="alert">{{ validationMessage }}</p>
      </section>

      <section class="theme-section theme-preview-section" :style="previewStyle">
        <div class="theme-preview-heading">
          <div class="theme-section-title">组件效果预览</div>
          <span>画布底色固定</span>
        </div>
        <div class="theme-preview-grid">
          <div
            v-for="item in previewCards"
            :key="item.label"
            class="theme-preview-card"
            :style="componentThemeStyle(resolved, item.type, 0, item.group)"
          >
            <DashboardComponentIcon :type="item.type" :dashboard-theme="resolved" :theme-accent-group="item.group" />
            <div class="theme-preview-card-label">{{ item.label }}</div>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="theme-source">组件自定义配色与样式不会被主题覆盖。</div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ComponentThemeAccentGroup, DashboardThemeConfig, DashboardThemePresetId, ResolvedDashboardTheme, InsightComponentType } from '@/types'
import type { DashboardThemeComponentColorMode, DashboardThemeHierarchy, DashboardThemeIconMode } from '@/types'
import { componentAccentColor, componentThemeStyle, DASHBOARD_CANVAS_BACKGROUND, DASHBOARD_THEME_PRESETS, resolveDashboardTheme, validateDashboardTheme } from '@/utils/dashboard-theme'
import DashboardComponentIcon from './DashboardComponentIcon.vue'
import InsightColorField from './InsightColorField.vue'

const props = defineProps<{
  modelValue: DashboardThemeConfig
  visible: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: DashboardThemeConfig): void
  (event: 'update:visible', value: boolean): void
  (event: 'confirm-theme-switch', presetId: string): void
}>()

const iconOptions = [{ value: 'show' as const, label: '显示' }, { value: 'hide' as const, label: '隐藏' }]
const hierarchyOptions = [{ value: 'soft' as const, label: '柔和' }, { value: 'standard' as const, label: '标准' }, { value: 'strong' as const, label: '增强' }]
const colorModeOptions = [{ value: 'auto' as const, label: '按分区' }, { value: 'uniform' as const, label: '统一主色' }]
const accentAltChoices = ['#0F766E', '#0D9488', '#047857']
const previewCards: Array<{ label: string; type: InsightComponentType; group: ComponentThemeAccentGroup; value: string }> = [
  { label: '指标卡', type: 'kpi', group: 'primary', value: '12,345' },
  { label: '趋势图', type: 'chart', group: 'secondary', value: '趋势' },
  { label: '数据表', type: 'table', group: 'highlight', value: '明细' },
]

const resolved = computed<ResolvedDashboardTheme>(() => resolveDashboardTheme(props.modelValue, 'light'))
const validationMessage = computed(() => {
  const error = validateDashboardTheme(props.modelValue)[0]
  return error?.message ?? ''
})
const previewStyle = computed(() => ({ background: DASHBOARD_CANVAS_BACKGROUND, color: '#172B4D', borderColor: '#E1E5EB' }))
const themeTokenStyle = computed(() => ({
  '--theme-bg': resolved.value.pageBackground,
  '--theme-surface': resolved.value.cardBackground,
  '--theme-surface-hover': resolved.value.border,
  '--theme-border': resolved.value.border,
  '--theme-text': resolved.value.text,
  '--theme-text-secondary': resolved.value.textSecondary,
  '--theme-text-muted': resolved.value.textMuted,
  '--theme-primary': resolved.value.primary,
}))
const hasOverrides = computed(() => Object.keys(props.modelValue.overrides ?? {}).length > 0)

function requestPreset(presetId: string): void {
  if (presetId === props.modelValue.presetId) return
  if (hasOverrides.value) {
    emit('confirm-theme-switch', presetId)
    return
  }
  applyPreset(presetId)
}

function applyPreset(presetId: string): void {
  if (presetId !== 'default' && !DASHBOARD_THEME_PRESETS[presetId]) return
  emit('update:modelValue', {
    ...props.modelValue,
    mode: 'preset',
    presetId: presetId as DashboardThemePresetId,
    overrides: {},
  })
}

function updateStandardOption(key: 'iconMode' | 'hierarchy' | 'componentColorMode', value: DashboardThemeIconMode | DashboardThemeHierarchy | DashboardThemeComponentColorMode): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

/** 选择或输入均显式建立覆盖，包括与预设当前解析色相同的值。 */
function updateAccentAlt(color: string): void {
  const overrides = { ...(props.modelValue.overrides ?? {}) }
  overrides.accentAlt = color
  emit('update:modelValue', { ...props.modelValue, overrides })
}

/** 删除覆盖后由当前预设重新提供默认辅助色。 */
function resetAccentAlt(): void {
  const overrides = { ...(props.modelValue.overrides ?? {}) }
  delete overrides.accentAlt
  emit('update:modelValue', { ...props.modelValue, overrides })
}

function getPresetAccents(presetId: string): string[] {
  const theme = resolveDashboardTheme({ mode: 'preset', presetId }, 'light')
  return [
    componentAccentColor(theme, 'primary'),
    componentAccentColor(theme, 'secondary'),
    componentAccentColor(theme, 'highlight'),
  ]
}

function onPresetKeydown(event: KeyboardEvent): void {
  if (!['ArrowRight', 'ArrowLeft', 'ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) return
  const buttons = Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('[data-preset-id]'))
  const current = buttons.indexOf((event.target as HTMLButtonElement) ?? document.activeElement as HTMLButtonElement)
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (current >= 0) requestPreset(buttons[current].dataset.presetId ?? '')
    return
  }
  const next = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? current + 1 : current - 1
  const target = buttons[(next + buttons.length) % buttons.length]
  if (target) { event.preventDefault(); target.focus() }
}

defineExpose({ applyPreset })
</script>

<style scoped>
.dashboard-theme-panel { min-height: 100%; padding: 0 4px; box-sizing: border-box; background: var(--theme-bg); color: var(--theme-text); }
.theme-section { padding: 0 0 20px; margin-bottom: 20px; border-bottom: 1px solid var(--theme-border); }
.theme-section-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; }
.theme-hint, .theme-source { color: var(--theme-text-muted); font-size: 12px; line-height: 18px; margin: 0 0 12px; }
.theme-presets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.theme-preset { display: flex; align-items: center; gap: 8px; padding: 8px; border: 1px solid var(--theme-border); border-radius: 8px; background: var(--theme-surface); color: var(--theme-text); cursor: pointer; text-align: left; }
.theme-preset:hover, .theme-preset:focus-visible, .theme-preset.active { border-color: var(--theme-primary, #2563eb); outline: 2px solid color-mix(in srgb, var(--theme-primary, #2563eb) 20%, transparent); }
.theme-swatch { display: flex; align-items: end; width: 42px; height: 28px; padding: 4px; border: 1px solid; border-radius: 5px; gap: 2px; }
.theme-swatch i { width: 8px; height: 18px; border-radius: 2px; }
.theme-swatch-default { background: #FFFFFF; border-color: #E1E5EB; }
.theme-swatch-default i { background: #E1E5EB; }
.theme-preset-label { font-size: 12px; }
.theme-option-group { display: grid; gap: 7px; margin-top: 12px; }
.theme-option-label { color: var(--theme-text-secondary); font-size: 12px; }
.theme-option-buttons { display: flex; gap: 6px; }
.theme-option-buttons button { flex: 1; padding: 7px 8px; border: 1px solid var(--theme-border); border-radius: 6px; background: var(--theme-surface); color: var(--theme-text-secondary); cursor: pointer; font-size: 12px; }
.theme-option-buttons button.active, .theme-option-buttons button:hover, .theme-option-buttons button:focus-visible { border-color: var(--theme-primary); background: color-mix(in srgb, var(--theme-primary) 10%, var(--theme-surface)); color: var(--theme-primary); outline: none; }
.theme-error { color: var(--el-color-danger); font-size: 12px; margin: 10px 0 0; }
.theme-reset-button { justify-self: start; padding: 0; border: 0; background: transparent; color: var(--theme-primary); cursor: pointer; font: inherit; font-size: 12px; }
.theme-preview-section { padding: 12px; border: 1px solid; border-radius: 10px; }
.theme-preview-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.theme-preview-heading .theme-section-title { margin: 0; }
.theme-preview-heading > span { color: #64748B; font-size: 11px; }
.theme-preview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.theme-preview-card { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 5px; padding: 10px 9px; border: 1px solid var(--component-group-border, var(--theme-border)); border-top: 3px solid var(--component-group-accent); border-radius: var(--component-radius, 10px); background: var(--theme-surface); box-shadow: var(--component-shadow, none); }
.theme-preview-card-label { color: var(--theme-text-secondary); font-size: 11px; white-space: nowrap; }
.theme-preview-card strong { overflow: hidden; max-width: 100%; color: var(--theme-text); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.theme-preview-section .theme-source { margin: 10px 0 0; color: #64748B; }
</style>
