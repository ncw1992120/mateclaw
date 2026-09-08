<template>
  <div class="rec-section">
    <button
      class="rec-toggle"
      :class="{ expanded: isExpanded }"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
      {{ questions.length }} {{ t('chat.recommendedQuestions') }}
    </button>
    <div class="rec-pills" :class="{ open: isExpanded }">
      <button
        v-for="(question, qIdx) in questions"
        :key="qIdx"
        class="rec-pill"
        @click="$emit('select', question)"
      >
        <svg class="rec-pill__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/></svg>
        <span>{{ question }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'RecommendedQuestions' })

const props = defineProps<{
  /** 推荐问题列表 */
  questions: string[]
  /** 是否为最新一条消息（最新默认展开，历史默认折叠） */
  isLatest?: boolean
}>()

defineEmits<{
  (e: 'select', question: string): void
}>()

const { t } = useI18n()

/** 内部展开状态：isLatest 时默认展开，否则默认折叠 */
const isExpanded = ref(props.isLatest ?? false)

function toggle(): void {
  isExpanded.value = !isExpanded.value
}
</script>

<style scoped>
.rec-section {
  margin-top: 8px;
}

.rec-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: var(--theme-text-secondary);
  transition: all 0.15s;
  background: transparent;
  cursor: pointer;
  border: none;
  font-family: inherit;
}

.rec-toggle:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.rec-toggle svg {
  width: 13px;
  height: 13px;
  transition: transform 0.2s;
}

.rec-toggle.expanded svg {
  transform: rotate(90deg);
}

.rec-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 0.25s ease, opacity 0.2s ease, margin 0.2s ease;
}

.rec-pills.open {
  max-height: 500px;
  opacity: 1;
}

.rec-pill {
  display: inline-flex;
  align-items: flex-start;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  font-size: 13px;
  line-height: 1.45;
  color: var(--theme-text);
  cursor: pointer;
  transition: all 0.18s;
  max-width: 100%;
  font-family: inherit;
}

/* 长问题文本：允许换行，最多两行，超出省略 */
.rec-pill span {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  text-align: left;
}

.rec-pill:hover {
  border-color: color-mix(in srgb, var(--main-orange) 40%, transparent);
  color: var(--main-orange);
  background: var(--theme-surface-hover);
}

.rec-pill__icon {
  width: 13px;
  height: 13px;
  margin-top: 3px;
  color: var(--theme-text-secondary);
  flex-shrink: 0;
}

.rec-pill:hover .rec-pill__icon {
  color: var(--main-orange);
}
</style>
