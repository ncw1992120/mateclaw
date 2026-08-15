<template>
  <div class="context-usage-panel">
    <div v-if="!usage" class="context-usage-empty">
      暂无上下文使用数据
    </div>

    <template v-else>
      <!-- DSH ContextMeter 头部：headline + 大百分比 + figures（纯数字，无单位词） -->
      <div class="context-usage-headline">
        <span class="headline-first">上下文已使用</span>
        <span class="percent">{{ percentText }}</span>
        <span class="headline-last">，共</span>
        <span class="figures">~{{ formatTokens(usage.usedTokens) }} / {{ formatTokens(usage.contextWindow) }}</span>
      </div>

      <div class="context-usage-bar">
        <template v-for="(cat, idx) in usage.categories" :key="cat.name">
          <div
            v-if="cat.tokens > 0"
            class="context-usage-segment"
            :style="{ width: `${(cat.tokens / usage.contextWindow) * 100}%`, backgroundColor: categoryColor(cat) }"
            :title="`${categoryLabel(cat)}：${formatTokens(cat.tokens)} tokens`"
          />
        </template>
      </div>

      <div class="context-usage-categories">
        <div v-for="cat in usage.categories" :key="cat.name" class="context-usage-category">
          <span class="category-dot" :style="{ backgroundColor: categoryColor(cat) }" />
          <span class="category-label">{{ categoryLabel(cat) }}</span>
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
import type { ContextUsage, ContextUsageCategory } from '@/types'

const props = defineProps<{
  usage: ContextUsage | null
}>()

const percentText = computed(() => {
  if (!props.usage) return '0%'
  return `${Math.round(props.usage.usedPercent * 100)}%`
})

/**
 * DSH ContextMeter 分类配色（参考 packages/client/ui-conversation ——
 * ContextMeter.module.css：system=蓝灰 neutral-bluish-400、tools=紫 violet-400、
 * messages=蓝 blue-450）。键名与后端字段一致（ContextUsageServiceImpl：
 * system_prompt / tool_definitions / conversation）。
 */
const DSH_CATEGORY_TINTS: Record<string, string> = {
  system_prompt: '#5A6472',
  tool_definitions: 'rgb(167, 139, 250)',
  conversation: '#3B82F6',
}

/** 分类中文标签（对应 DSH 的 context.system / context.tools / context.messages），
    未命中则回退后端下发的 label。 */
const CATEGORY_LABELS_ZH: Record<string, string> = {
  system_prompt: '系统提示词',
  tool_definitions: '工具',
  conversation: '对话消息',
}

function categoryColor(cat: ContextUsageCategory): string {
  return DSH_CATEGORY_TINTS[cat.name] ?? (cat.color ?? 'var(--theme-text-muted)')
}

function categoryLabel(cat: ContextUsageCategory): string {
  return CATEGORY_LABELS_ZH[cat.name] ?? (cat.label || cat.name)
}

function formatTokens(tokens: number): string {
  if (tokens >= 1000) {
    return `${(tokens / 1000).toFixed(1)}K`
  }
  return String(tokens)
}
</script>

<style scoped>
/* 参考 DSH ContextMeter 面板（ContextMeter.module.css：锚定在触发按钮上方的菜单表面
   r12 + 反白描边 + shadow-lv3），适配 mateclaw 主题变量，随浅色/深色主题切换。 */
.context-usage-panel {
  position: absolute;
  bottom: calc(100% + 8px);
  right: 0;
  z-index: 100;
  box-sizing: border-box;
  width: 264px;
  background: var(--theme-surface-elevated, #fff);
  border: 1px solid var(--theme-border-strong, rgba(0, 0, 0, 0.12));
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.16);
  color: var(--theme-text-secondary, #555);
  font-size: 12px;
  line-height: 20px;
}

.context-usage-headline {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.headline-first,
.headline-last {
  color: var(--theme-text-muted, #999);
}

.percent {
  font-weight: 500;
  color: var(--theme-text, #333);
  font-variant-numeric: tabular-nums;
}

.figures {
  margin-left: auto;
  font-weight: 500;
  color: var(--theme-text, #333);
  font-variant-numeric: tabular-nums;
}

.context-usage-empty {
  text-align: center;
  color: var(--theme-text-muted, #999);
  padding: 16px 0;
}

.context-usage-bar {
  display: flex;
  gap: 1px;
  height: 4px;
  margin: 10px 0 12px;
  border-radius: 999px;
  background: var(--theme-surface-hover);
  overflow: hidden;
}

.context-usage-segment {
  height: 100%;
  border-radius: 1px;
  transition: width 0.3s ease;
}

.context-usage-categories {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.context-usage-category {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 2px 0;
  color: var(--theme-text-secondary, #555);
}

.category-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.category-label {
  flex: 1;
}

.category-tokens {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  color: var(--theme-text, #333);
}

.context-usage-compression {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--theme-border, rgba(0, 0, 0, 0.06));
}

.compression-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-text-muted, #999);
  margin-bottom: 6px;
}

.compression-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 2px;
  color: var(--theme-text-secondary, #555);
}
</style>
