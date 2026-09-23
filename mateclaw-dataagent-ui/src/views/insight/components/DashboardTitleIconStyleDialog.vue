<template>
  <el-dialog
    :model-value="modelValue"
    title="标题图标样式"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="title-icon-preview">
      <DashboardComponentIcon
        :type="previewType"
        :chart-type="chartType"
        :title="title"
        :dashboard-theme="dashboardTheme"
        :title-icon-style="draft"
        :size="20"
      />
      <span>{{ title || '组件标题' }}</span>
      <span class="preview-caption">预览</span>
    </div>

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

    <div class="style-controls">
      <div class="control-field">
        <label for="title-icon-stroke-width">图标粗细</label>
        <el-select id="title-icon-stroke-width" v-model="draft.strokeWidth" aria-label="图标粗细" style="width: 100%">
          <el-option :value="1.5" label="纤细" />
          <el-option :value="2" label="标准" />
          <el-option :value="2.5" label="加粗" />
          <el-option :value="3" label="粗体" />
        </el-select>
      </div>
      <div class="control-field">
        <span class="control-label">图标颜色</span>
        <el-radio-group v-model="draft.colorMode" aria-label="图标颜色模式">
          <el-radio value="theme">跟随主题</el-radio>
          <el-radio value="custom">自定义</el-radio>
        </el-radio-group>
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
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="save">应用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { ChartType, ComponentTitleIconStyle, InsightComponentType, ResolvedDashboardTheme } from '@/types'
import { DASHBOARD_ICON_REGISTRY } from '@/utils/dashboard-icon-registry'
import DashboardComponentIcon from './DashboardComponentIcon.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  titleIconStyle?: ComponentTitleIconStyle
  title?: string
  previewType?: InsightComponentType
  chartType?: ChartType
  dashboardTheme?: ResolvedDashboardTheme
}>(), { title: '', previewType: 'kpi' })

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'save', style: ComponentTitleIconStyle): void
}>()

const defaults = (): ComponentTitleIconStyle => ({ colorMode: 'theme', strokeWidth: 2 })
const draft = reactive<ComponentTitleIconStyle>(defaults())

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
</script>

<style scoped>
.title-icon-preview {
  display: flex;
  align-items: center;
  min-height: 52px;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
}
.preview-caption {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.icon-style-section { margin-top: 20px; }
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
.style-controls {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 20px;
  margin-top: 20px;
}
.control-field { min-width: 0; }
.control-field > label {
  display: block;
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.control-field :deep(.el-radio-group) { display: flex; gap: 12px; margin-bottom: 10px; }
@media (max-width: 560px) {
  .style-controls { grid-template-columns: 1fr; gap: 14px; }
  .icon-options { grid-template-columns: repeat(6, minmax(44px, 1fr)); }
}
</style>
