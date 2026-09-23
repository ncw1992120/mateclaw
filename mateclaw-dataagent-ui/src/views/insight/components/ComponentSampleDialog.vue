<template>
  <el-dialog
    :model-value="modelValue"
    :title="`样例数据格式 · ${title || '组件'}`"
    width="min(720px, calc(100vw - 32px))"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <section class="sample-contract">
      <strong>格式要求</strong>
      <p>{{ sample.contract }}</p>
    </section>
    <div class="sample-code-head">
      <strong>样例 JSON</strong>
      <el-button size="small" @click="copySample">复制 JSON</el-button>
    </div>
    <pre class="sample-json">{{ sample.json }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { ComponentSample } from '@/utils/component-sample-data'

const props = defineProps<{ modelValue: boolean; title?: string; sample: ComponentSample }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()

async function copySample(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.sample.json)
    ElMessage.success('已复制样例 JSON')
  } catch {
    ElMessage.warning('复制失败，请手动选择并复制样例 JSON')
  }
}
</script>

<style scoped>
.sample-contract {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}
.sample-contract p { margin: 6px 0 0; line-height: 1.55; color: var(--el-text-color-regular); }
.sample-code-head { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 8px; }
.sample-json { max-height: 48vh; overflow: auto; margin: 0; padding: 14px; border-radius: 6px; background: #f6f8fa; color: #24292f; font: 12px/1.6 var(--font-mono, monospace); white-space: pre; }
</style>
