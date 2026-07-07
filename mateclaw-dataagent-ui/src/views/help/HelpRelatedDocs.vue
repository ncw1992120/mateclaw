<template>
  <div class="help-related" v-if="documents.length > 0">
    <div class="related-title">{{ t('helpCenter.relatedDocs') }}</div>
    <div class="related-list">
      <div
        v-for="doc in documents"
        :key="doc.id"
        class="related-item"
        @click="$emit('selectDoc', doc)"
      >
        <el-icon><Document /></el-icon>
        <span class="related-item-title">{{ doc.title }}</span>
        <span class="related-item-views" v-if="doc.viewCount > 0">
          <el-icon><View /></el-icon> {{ doc.viewCount }}
        </span>
      </div>
    </div>
  </div>
  <div class="help-related" v-else>
    <div class="related-title">{{ t('helpCenter.relatedDocs') }}</div>
    <div class="related-empty">{{ t('helpCenter.relatedDocsEmpty') }}</div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Document, View } from '@element-plus/icons-vue'
import type { HelpDocument } from '@/types'

const { t } = useI18n()

defineProps<{
  documents: HelpDocument[]
}>()

defineEmits<{
  (e: 'selectDoc', doc: HelpDocument): void
}>()
</script>

<style scoped>
.help-related {
  border-top: 1px solid var(--theme-border);
  padding: 20px 0 0;
  margin-top: 24px;
}

.related-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
  margin-bottom: 12px;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.related-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 14px;
  color: var(--theme-text-secondary);
}

.related-item:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.related-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-item-views {
  font-size: 12px;
  color: var(--theme-text-muted);
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.related-empty {
  font-size: 13px;
  color: var(--theme-text-muted);
}
</style>
