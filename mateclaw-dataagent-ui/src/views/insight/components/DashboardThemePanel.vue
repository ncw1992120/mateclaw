<template>
  <el-drawer
    :model-value="visible"
    title="主题外观"
    size="360px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="dashboard-theme-panel">
      <section class="theme-section">
        <div class="theme-section-title">预设主题</div>
        <p class="theme-hint">主题作用于整个仪表盘，切换页面和预览会保持一致。</p>
        <div class="theme-presets" role="radiogroup" aria-label="仪表盘预设主题" @keydown="onPresetKeydown">
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
            <span class="theme-swatch" :style="{ background: preset.pageBackground, borderColor: preset.border }">
              <i v-for="color in preset.chartPalette.slice(0, 3)" :key="color" :style="{ background: color }" />
            </span>
            <span class="theme-preset-label">{{ preset.label }}</span>
          </button>
        </div>
      </section>

      <section class="theme-section">
        <div class="theme-section-title">自定义颜色</div>
        <div class="theme-color-grid">
          <label v-for="item in editableColors" :key="item.key" class="theme-color-field">
            <span>{{ item.label }}</span>
            <input type="color" :value="colorValue(item.key)" :aria-label="item.label" @input="updateColor(item.key, ($event.target as HTMLInputElement).value)" />
          </label>
        </div>
        <p v-if="validationMessage" class="theme-error" role="alert">{{ validationMessage }}</p>
      </section>

      <section class="theme-section theme-preview-section" :style="previewStyle">
        <div class="theme-section-title">即时预览</div>
        <div class="theme-preview-card">
          <div class="theme-preview-icon" :style="{ background: `${resolved.primary}20`, color: resolved.primary }">↗</div>
          <div><strong>指标概览</strong><div class="theme-preview-value">12,345</div></div>
        </div>
        <div class="theme-source">当前主色：{{ themeSource(resolved, 'primary') }}</div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardThemeConfig, DashboardThemePresetId, ResolvedDashboardTheme } from '@/types'
import { DASHBOARD_THEME_PRESETS, resolveDashboardTheme, themeSource, validateDashboardTheme } from '@/utils/dashboard-theme'

const props = defineProps<{
  modelValue: DashboardThemeConfig
  visible: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: DashboardThemeConfig): void
  (event: 'update:visible', value: boolean): void
  (event: 'confirm-theme-switch', presetId: string): void
}>()

const editableColors = [
  { key: 'primary', label: '主色' },
  { key: 'pageBackground', label: '页面背景' },
  { key: 'cardBackground', label: '卡片背景' },
  { key: 'text', label: '正文文字' },
] as const

const resolved = computed<ResolvedDashboardTheme>(() => resolveDashboardTheme(props.modelValue, 'light'))
const validationMessage = computed(() => {
  const error = validateDashboardTheme(props.modelValue)[0]
  return error?.message ?? ''
})
const previewStyle = computed(() => ({ background: resolved.value.pageBackground, color: resolved.value.text, borderColor: resolved.value.border }))
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
  if (!DASHBOARD_THEME_PRESETS[presetId]) return
  emit('update:modelValue', { mode: 'preset', presetId: presetId as DashboardThemePresetId, overrides: {} })
}

function colorValue(key: keyof NonNullable<DashboardThemeConfig['overrides']>): string {
  const value = props.modelValue.overrides?.[key]
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value : resolved.value[key as keyof ResolvedDashboardTheme] as string
}

function updateColor(key: keyof NonNullable<DashboardThemeConfig['overrides']>, value: string): void {
  emit('update:modelValue', { ...props.modelValue, mode: 'custom', overrides: { ...props.modelValue.overrides, [key]: value } })
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
.dashboard-theme-panel { color: var(--theme-text); }
.theme-section { padding: 0 0 20px; margin-bottom: 20px; border-bottom: 1px solid var(--theme-border); }
.theme-section-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; }
.theme-hint, .theme-source { color: var(--theme-text-muted); font-size: 12px; line-height: 18px; margin: 0 0 12px; }
.theme-presets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.theme-preset { display: flex; align-items: center; gap: 8px; padding: 8px; border: 1px solid var(--theme-border); border-radius: 8px; background: var(--theme-surface); color: var(--theme-text); cursor: pointer; text-align: left; }
.theme-preset:hover, .theme-preset:focus-visible, .theme-preset.active { border-color: var(--theme-primary, #2563eb); outline: 2px solid color-mix(in srgb, var(--theme-primary, #2563eb) 20%, transparent); }
.theme-swatch { display: flex; align-items: end; width: 42px; height: 28px; padding: 4px; border: 1px solid; border-radius: 5px; gap: 2px; }
.theme-swatch i { width: 8px; height: 18px; border-radius: 2px; }
.theme-preset-label { font-size: 12px; }
.theme-color-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.theme-color-field { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 12px; }
.theme-color-field input { width: 32px; height: 24px; padding: 0; border: 0; cursor: pointer; }
.theme-error { color: var(--el-color-danger); font-size: 12px; margin: 10px 0 0; }
.theme-preview-section { padding: 12px; border: 1px solid; border-radius: 10px; }
.theme-preview-card { display: flex; align-items: center; gap: 10px; padding: 12px; border-radius: 8px; background: var(--theme-surface); }
.theme-preview-icon { display: grid; width: 30px; height: 30px; border-radius: 8px; place-items: center; }
.theme-preview-value { margin-top: 4px; font-size: 22px; font-weight: 700; }
</style>
