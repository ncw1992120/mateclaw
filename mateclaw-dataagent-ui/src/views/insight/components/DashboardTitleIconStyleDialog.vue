<template>
  <el-dialog
    :model-value="modelValue"
    title="标题图标样式"
    :width="width"
    :top="top"
    :style="{ marginLeft: left }"
    :draggable="draggable"
    :close-on-click-modal="false"
    @update:model-value="handleVisibilityChange"
  >
    <section class="icon-style-section" aria-labelledby="title-icon-choice-label">
      <div id="title-icon-choice-label" class="section-label">图标</div>
      <div class="icon-options" role="radiogroup" aria-label="选择标题图标">
        <button
          type="button"
          class="icon-option icon-auto"
          :class="{ selected: !draft.iconKey }"
          role="radio"
          :aria-checked="!draft.iconKey"
          aria-label="使用自动匹配图标"
          @click="draft.iconKey = undefined"
        >
          自动
        </button>
        <button
          v-for="item in DASHBOARD_ICON_REGISTRY"
          :key="item.key"
          type="button"
          class="icon-option"
          :class="{ selected: draft.iconKey === item.key }"
          role="radio"
          :aria-checked="draft.iconKey === item.key"
          :aria-label="item.label"
          :title="item.label"
          @click="draft.iconKey = item.key"
        >
          <el-icon :size="18"><component :is="item.component" /></el-icon>
        </button>
      </div>
    </section>

    <div class="style-controls style-controls-inline">
      <div class="control-row">
        <label class="control-label" for="title-icon-stroke-width">粗细</label>
        <el-select id="title-icon-stroke-width" v-model="draft.strokeWidth" aria-label="粗细" style="width: 88px">
          <el-option :value="1.5" label="纤细" />
          <el-option :value="2" label="标准" />
          <el-option :value="2.5" label="加粗" />
          <el-option :value="3" label="粗体" />
        </el-select>
      </div>
      <div class="control-row color-control-row">
        <span class="control-label">颜色</span>
        <div class="color-mode-options" role="radiogroup" aria-label="图标颜色模式">
          <button
            type="button"
            class="color-mode-option"
            :class="{ selected: draft.colorMode === 'theme' }"
            role="radio"
            :aria-checked="draft.colorMode === 'theme'"
            aria-label="跟随主题颜色模式"
            @click="draft.colorMode = 'theme'"
          ><span class="color-mode-indicator" aria-hidden="true" />跟随主题</button>
          <button
            type="button"
            class="color-mode-option"
            :class="{ selected: draft.colorMode === 'custom' }"
            role="radio"
            :aria-checked="draft.colorMode === 'custom'"
            aria-label="自定义颜色模式"
            @click="draft.colorMode = 'custom'"
          ><span class="color-mode-indicator" aria-hidden="true" />自定义</button>
        </div>
        <el-color-picker
          v-if="draft.colorMode === 'custom'"
          v-model="draft.color"
          aria-label="自定义图标颜色"
          :show-alpha="false"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="reset">恢复默认</el-button>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="save">应用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { ComponentTitleIconStyle } from '@/types'
import { DASHBOARD_ICON_REGISTRY } from '@/utils/dashboard-icon-registry'

const props = withDefaults(defineProps<{
  modelValue: boolean
  titleIconStyle?: ComponentTitleIconStyle
  width?: string
  top?: string
  left?: string
  draggable?: boolean
}>(), { width: 'min(520px, calc(100vw - 32px))', top: '16px', left: '16px', draggable: true })

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'save', style: ComponentTitleIconStyle): void
  (event: 'preview', style: ComponentTitleIconStyle): void
}>()

const defaults = (): ComponentTitleIconStyle => ({ colorMode: 'theme', strokeWidth: 2 })
const draft = reactive<ComponentTitleIconStyle>(defaults())

watch(draft, (style) => emit('preview', { ...style }), { deep: true })

watch(() => props.modelValue, (visible) => {
  if (visible) Object.assign(draft, defaults(), props.titleIconStyle ?? {})
})

function reset(): void {
  Object.assign(draft, defaults())
}

function save(): void {
  emit('save', { ...draft })
  emit('update:modelValue', false)
}

function cancel(): void {
  emit('update:modelValue', false)
}

function handleVisibilityChange(visible: boolean): void {
  emit('update:modelValue', visible)
}
</script>

<style scoped>
.icon-style-section { margin-top: 0; }
.section-label, .control-label {
  display: block;
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}
.icon-options {
  display: grid;
  grid-template-columns: repeat(8, minmax(44px, 1fr));
  gap: 8px;
  max-height: 184px;
  overflow-y: auto;
  padding: 2px;
}
.icon-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: border-color 150ms ease, background-color 150ms ease, color 150ms ease;
}
.icon-auto { font-size: 12px; }
.icon-option:hover { border-color: var(--el-color-primary-light-5); color: var(--el-color-primary); }
.icon-option.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.icon-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.style-controls-inline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px 28px;
  margin-top: 18px;
}
.control-row {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}
.control-label {
  flex: 0 0 auto;
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}
.color-mode-options { display: inline-flex; align-items: center; gap: 12px; }
.color-mode-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
}
.color-mode-indicator {
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  background: var(--el-bg-color);
}
.color-mode-option.selected { color: var(--el-color-primary); }
.color-mode-option.selected .color-mode-indicator {
  border: 4px solid var(--el-color-primary);
}
.color-mode-option:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; border-radius: 4px; }
@media (max-width: 560px) {
  .style-controls-inline { align-items: flex-start; flex-direction: column; gap: 12px; }
  .icon-options { grid-template-columns: repeat(6, minmax(44px, 1fr)); }
}
</style>
