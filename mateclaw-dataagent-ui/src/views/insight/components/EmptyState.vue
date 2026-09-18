<template>
  <div class="insight-empty">
    <el-icon class="insight-empty-icon"><component :is="icon" /></el-icon>
    <div class="insight-empty-text">{{ text }}</div>
    <div v-if="$slots.default" class="insight-empty-action"><slot /></div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { Files } from '@element-plus/icons-vue'

defineOptions({
  name: 'InsightEmptyState',
})

withDefaults(
  defineProps<{
    /** 空态图标（线性风格，与界面内 Element Plus 图标保持一致） */
    icon?: Component
    /** 空态文案 */
    text?: string
  }>(),
  { icon: () => Files, text: '' },
)
</script>

<style scoped>
.insight-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--db-text-muted);
  text-align: center;
  width: 100%;
  height: 100%;
}

.insight-empty-icon {
  font-size: 32px;
  color: var(--db-text-quaternary);
}

.insight-empty-text {
  font-size: 13px;
}

.insight-empty-action {
  margin-top: 2px;
}
</style>
