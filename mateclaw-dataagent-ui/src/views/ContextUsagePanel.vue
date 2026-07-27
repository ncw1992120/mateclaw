<template>
  <div class="context-usage-panel">
    <div class="context-usage-header">
      <span class="context-usage-title">Context Usage</span>
      <button class="context-usage-close" type="button" @click="emit('close')">×</button>
    </div>

    <div v-if="!usage" class="context-usage-empty">
      暂无上下文使用数据
    </div>

    <template v-else>
      <div class="context-usage-summary">
        <div class="context-usage-percent">
          <span class="percent-value">{{ percentText }}</span>
          <span class="percent-label">Full</span>
        </div>
        <div class="context-usage-total">
          ~{{ formatTokens(usage.usedTokens) }} / {{ formatTokens(usage.contextWindow) }} Tokens
        </div>
      </div>

      <div class="context-usage-bar">
        <template v-for="(cat, idx) in usage.categories" :key="cat.name">
          <div
            v-if="cat.tokens > 0"
            class="context-usage-segment"
            :style="{ width: `${(cat.tokens / usage.contextWindow) * 100}%`, backgroundColor: cat.color }"
            :title="`${cat.label}: ${formatTokens(cat.tokens)} tokens`"
          />
        </template>
      </div>

      <div class="context-usage-categories">
        <div v-for="cat in usage.categories" :key="cat.name" class="context-usage-category">
          <span class="category-dot" :style="{ backgroundColor: cat.color }" />
          <span class="category-label">{{ cat.label }}</span>
          <span class="category-tokens">{{ formatTokens(cat.tokens) }}</span>
        </div>
      </div>

      <div v-if="usage.compression && usage.compression.status !== 'none'" class="context-usage-compression">
        <div class="compression-title">压缩状态</div>
        <div class="compression-row">
          <span>压缩前</span>
          <span>{{ formatTokens(usage.compression.preTokens || 0) }}</span>
        </div>
        <div class="compression-row">
          <span>压缩后</span>
          <span>{{ formatTokens(usage.compression.postTokens || 0) }}</span>
        </div>
        <div class="compression-row">
          <span>摘要消息数</span>
          <span>{{ usage.compression.messagesSummarized || 0 }}</span>
        </div>
        <div class="compression-row">
          <span>保留消息数</span>
          <span>{{ usage.compression.tailKept || 0 }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ContextUsage } from '@/types'

const props = defineProps<{
  usage: ContextUsage | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const percentText = computed(() => {
  if (!props.usage) return '0%'
  return `${Math.round(props.usage.usedPercent * 100)}%`
})

function formatTokens(tokens: number): string {
  if (tokens >= 1000) {
    return `${(tokens / 1000).toFixed(1)}K`
  }
  return String(tokens)
}
</script>

<style scoped>
.context-usage-panel {
  position: fixed;
  right: 20px;
  bottom: 42px;
  width: 320px;
  background: var(--bg-color, #1f1f1f);
  border: 1px solid var(--border-color, #333);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  color: var(--text-color, #e5e5e5);
  z-index: 100;
}

.context-usage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.context-usage-title {
  font-size: 14px;
  font-weight: 600;
}

.context-usage-close {
  background: none;
  border: none;
  color: var(--text-color-secondary, #999);
  font-size: 18px;
  cursor: pointer;
}

.context-usage-empty {
  text-align: center;
  color: var(--text-color-secondary, #999);
  padding: 20px 0;
}

.context-usage-summary {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}

.context-usage-percent {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.percent-value {
  font-size: 20px;
  font-weight: 700;
}

.percent-label {
  font-size: 12px;
  color: var(--text-color-secondary, #999);
}

.context-usage-total {
  font-size: 12px;
  color: var(--text-color-secondary, #999);
}

.context-usage-bar {
  display: flex;
  height: 6px;
  background: var(--bar-bg, #333);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 16px;
}

.context-usage-segment {
  height: 100%;
  transition: width 0.3s ease;
}

.context-usage-categories {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.context-usage-category {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.category-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}

.category-label {
  flex: 1;
  color: var(--text-color-secondary, #bbb);
}

.category-tokens {
  font-weight: 500;
}

.context-usage-compression {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color, #333);
}

.compression-title {
  font-size: 12px;
  color: var(--text-color-secondary, #999);
  margin-bottom: 8px;
}

.compression-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
}
</style>
