<template>
  <div class="help-search-panel" v-if="visible">
    <div class="search-header">
      <h3>{{ t('helpCenter.searchResult') }}</h3>
      <span class="search-count" v-if="results.length > 0">
        {{ t('helpCenter.searchResultCount', { count: results.length }) }}
      </span>
      <el-button link size="small" @click="$emit('close')">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
    <div class="search-body" v-loading="loading">
      <div v-if="results.length > 0" class="search-results">
        <div
          v-for="item in results"
          :key="item.id"
          class="search-item"
          @click="$emit('selectDoc', item)"
        >
          <div class="search-item-title">
            <el-icon><Document /></el-icon>
            <span>{{ item.title }}</span>
            <span class="mc-tag text">{{ item.categoryName }}</span>
          </div>
          <div class="search-item-content" v-html="item.highlightContent" />
          <div class="search-item-meta">
            <span v-if="item.author"><el-icon><User /></el-icon> {{ item.author }}</span>
            <span><el-icon><Clock /></el-icon> {{ formatDateTime(item.updateTime, '') }}</span>
            <span><el-icon><View /></el-icon> {{ item.viewCount }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!loading" :description="t('helpCenter.searchEmpty')">
        <template #description>
          <p>{{ t('helpCenter.searchEmpty') }}</p>
          <p class="search-empty-hint">{{ t('helpCenter.searchEmptyHint') }}</p>
        </template>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Close, Document, User, Clock, View } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/time'
import type { HelpSearchResult } from '@/types'

const { t } = useI18n()

defineProps<{
  visible: boolean
  results: HelpSearchResult[]
  loading: boolean
}>()

defineEmits<{
  (e: 'close'): void
  (e: 'selectDoc', doc: HelpSearchResult): void
}>()
</script>

<style scoped>
.help-search-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.search-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 28px 16px;
  border-bottom: 1px solid var(--db-border);
}

.search-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
}

.search-count {
  font-size: 13px;
  color: var(--db-text-muted);
  flex: 1;
}

.search-body {
  flex: 1;
  overflow: auto;
  padding: 20px 28px;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-item {
  padding: 16px 20px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color var(--transition-fast, 0.15s), box-shadow var(--transition-fast, 0.15s);
  background: color-mix(in srgb, var(--db-text-muted) 4%, var(--db-card));
}

.search-item:hover {
  border-color: color-mix(in srgb, var(--main-orange) 50%, var(--db-border));
  box-shadow: 0 4px 12px color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.search-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text);
  margin-bottom: 8px;
}

.search-item-title > span:nth-child(2) {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-item-content {
  font-size: 13px;
  color: var(--db-text-secondary);
  line-height: 1.7;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-item-content :deep(mark) {
  background: color-mix(in srgb, var(--main-orange) 18%, transparent);
  color: var(--main-orange);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 500;
}

.search-item-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--db-text-muted);
}

.search-item-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.search-empty-hint {
  font-size: 12px;
  color: var(--db-text-muted);
  margin-top: 4px;
}
</style>
