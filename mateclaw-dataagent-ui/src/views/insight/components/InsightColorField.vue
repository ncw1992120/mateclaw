<template>
  <div class="insight-color-field">
    <label class="insight-color-field__label" :for="inputId">{{ label }}</label>
    <div class="insight-color-field__controls">
      <span
        data-testid="color-preview"
        class="insight-color-field__preview"
        :style="{ backgroundColor: previewColor }"
        aria-hidden="true"
      />
      <el-color-picker
        :model-value="previewColor"
        :predefine="predefinedColors"
        :aria-label="`${label}取色器`"
        @change="onPickerChange"
      />
      <input
        v-model="draft"
        :id="inputId"
        type="text"
        inputmode="text"
        autocomplete="off"
        spellcheck="false"
        :aria-label="label"
        :aria-invalid="Boolean(validationMessage)"
        :aria-describedby="validationMessage ? errorId : undefined"
        @input="onInput"
        @keydown.enter.prevent="commitDraft"
        @blur="commitDraft"
      >
    </div>
    <p v-if="validationMessage" :id="errorId" class="insight-color-field__error" role="alert">
      {{ validationMessage }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useInsightColorHistoryStore } from '@/stores/useInsightColorHistoryStore'
import { TEXT_COLOR_PRESETS } from '@/utils/color-presets'

const props = withDefaults(defineProps<{
  modelValue: string
  label: string
  suggestedColors?: string[]
}>(), {
  suggestedColors: () => TEXT_COLOR_PRESETS,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'change', value: string): void
}>()

const history = useInsightColorHistoryStore()
const draft = ref(normalizeColor(props.modelValue) ?? props.modelValue)
const hasUncommittedEdit = ref(false)
const inputId = useId()
const errorId = `${inputId}-error`

function normalizeColor(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const color = value.trim()
  if (/^#[\da-f]{3}$/i.test(color)) {
    return `#${color.slice(1).split('').map((digit) => digit + digit).join('').toUpperCase()}`
  }
  return /^#[\da-f]{6}$/i.test(color) ? color.toUpperCase() : null
}

const normalizedDraft = computed(() => normalizeColor(draft.value))
const validationMessage = computed(() => normalizedDraft.value ? '' : '请输入有效的 HEX 颜色（#RGB 或 #RRGGBB）')
const previewColor = computed(() => normalizedDraft.value ?? normalizeColor(props.modelValue) ?? '#000000')
const predefinedColors = computed(() => {
  const colors = [...history.recentColors, ...props.suggestedColors]
    .map(normalizeColor)
    .filter((color): color is string => color !== null)
  return colors.filter((color, index) => colors.indexOf(color) === index)
})

watch(() => props.modelValue, (value) => {
  const isLocalEditEcho = hasUncommittedEdit.value && normalizeColor(value) === normalizedDraft.value
  draft.value = normalizeColor(value) ?? value
  if (!isLocalEditEcho) hasUncommittedEdit.value = false
})

function onInput(): void {
  hasUncommittedEdit.value = true
  const color = normalizedDraft.value
  if (!color) return
  emit('update:modelValue', color)
  emit('change', color)
}

function commitDraft(): void {
  if (!hasUncommittedEdit.value) return
  const color = normalizedDraft.value
  if (!color) return
  history.recordColor(color)
  hasUncommittedEdit.value = false
}

function onPickerChange(value: string | null): void {
  const color = normalizeColor(value)
  if (!color) return
  draft.value = color
  hasUncommittedEdit.value = false
  emit('update:modelValue', color)
  emit('change', color)
  history.recordColor(color)
}
</script>

<style scoped>
.insight-color-field { display: grid; gap: 6px; }
.insight-color-field__label { color: var(--el-text-color-regular); font-size: 13px; }
.insight-color-field__controls { display: flex; align-items: center; gap: 8px; }
.insight-color-field__preview { width: 24px; height: 24px; flex: 0 0 24px; border: 1px solid var(--el-border-color); border-radius: 4px; }
.insight-color-field__controls input { min-width: 0; width: 112px; padding: 6px 8px; border: 1px solid var(--el-border-color); border-radius: 4px; color: var(--el-text-color-primary); font: inherit; font-family: monospace; }
.insight-color-field__controls input[aria-invalid="true"] { border-color: var(--el-color-danger); }
.insight-color-field__error { margin: 0; color: var(--el-color-danger); font-size: 12px; }
</style>
