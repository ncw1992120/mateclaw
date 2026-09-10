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
/* 大纲列：透明纸面内右栏，无硬边框（与配置中心导航分层一致） */
.help-toc {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 18px 10px 16px 6px;
  overflow: hidden;
}

.toc-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--db-text-muted);
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
  padding: 4px 10px;
  font-size: 12px;
  color: var(--db-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  line-height: 1.6;
  border-left: 2px solid transparent;
  padding-left: 8px;
  border-radius: 0 6px 6px 0;
  margin: 0;
}

.toc-item:hover {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--db-text-muted) 6%, transparent);
}

.toc-item.active {
  color: var(--main-orange);
  border-left-color: var(--main-orange);
  font-weight: 500;
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.toc-item.level-1 { padding-left: 8px; }
.toc-item.level-2 { padding-left: 18px; }
.toc-item.level-3 { padding-left: 28px; font-size: 11px; }
.toc-item.level-4 { padding-left: 38px; font-size: 11px; }
</style>
