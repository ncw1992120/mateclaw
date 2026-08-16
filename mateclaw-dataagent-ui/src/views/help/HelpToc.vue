<template>
  <aside class="help-toc" v-if="headings.length > 0">
    <div class="toc-title">
      <el-icon><Menu /></el-icon>
      <span>{{ t('helpCenter.toc') }}</span>
    </div>
    <el-scrollbar class="toc-scroll">
      <ul class="toc-list">
        <li
          v-for="heading in headings"
          :key="heading.id"
          :class="['toc-item', 'level-' + heading.level, { active: activeHeadingId === heading.id }]"
          @click="scrollToHeading(heading.id)"
        >
          {{ heading.text }}
        </li>
      </ul>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Menu } from '@element-plus/icons-vue'

const { t } = useI18n()

export interface Heading {
  id: string
  text: string
  level: number
}

defineProps<{
  headings: Heading[]
  activeHeadingId: string
}>()

const emit = defineEmits<{
  (e: 'scrollTo', id: string): void
}>()

function scrollToHeading(id: string): void {
  emit('scrollTo', id)
}
</script>

<style scoped>
.help-toc {
  width: 200px;
  flex-shrink: 0;
  border-left: 1px solid var(--theme-border);
  background: var(--theme-surface);
  display: flex;
  flex-direction: column;
  padding: 20px 0;
}

.toc-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
  margin-bottom: 8px;
}

.toc-scroll {
  flex: 1;
  min-height: 0;
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.toc-item {
  padding: 4px 16px;
  font-size: 12px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  line-height: 1.6;
  border-left: 2px solid transparent;
  padding-left: 14px;
  margin: 0;
}

.toc-item:hover {
  color: var(--main-orange);
}

.toc-item.active {
  color: var(--main-orange);
  border-left-color: var(--main-orange);
  font-weight: 500;
  background: rgba(65, 118, 230, 0.08);
}

.toc-item.level-1 { padding-left: 14px; }
.toc-item.level-2 { padding-left: 24px; }
.toc-item.level-3 { padding-left: 34px; font-size: 11px; }
.toc-item.level-4 { padding-left: 44px; font-size: 11px; }
</style>
