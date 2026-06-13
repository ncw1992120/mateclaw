<template>
  <div class="schema-embed-panel">
    <div class="panel-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-title">{{ t('schemaEmbed.title') }}</span>
        <span class="toolbar-desc">{{ t('schemaEmbed.desc') }}</span>
      </div>
      <div class="toolbar-right">
        <button class="tool-btn danger" :disabled="deleting" @click="handleDeleteEmbed">
          <span v-if="deleting">⏳</span>
          <span v-else>🗑️</span>
          {{ deleting ? t('schemaEmbed.deleting') : t('schemaEmbed.deleteEmbed') }}
        </button>
        <button class="tool-btn primary" :disabled="embedding" @click="handleEmbedAll">
          <span v-if="embedding">⏳</span>
          <span v-else>🧠</span>
          {{ embedding ? t('schemaEmbed.embedding') : t('schemaEmbed.embedAll') }}
        </button>
      </div>
    </div>

    <div class="embed-content">
      <!-- 语义检索测试区 -->
      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">{{ t('schemaEmbed.searchTitle') }}</h2>
        </div>
        <div class="search-box">
          <input v-model="searchQuery" class="search-input" :placeholder="t('schemaEmbed.searchPlaceholder')" @keyup.enter="handleSearch" />
          <button class="search-btn" :disabled="searching" @click="handleSearch">
            {{ searching ? t('schemaEmbed.searching') : t('schemaEmbed.searchBtn') }}
          </button>
        </div>

        <div v-if="searchResult" class="search-result">
          <div class="result-meta">
            <span class="result-info">{{ t('schemaEmbed.resultCount', { count: searchResult.tableHits?.length || 0 }) }}</span>
            <span class="result-time">{{ t('schemaEmbed.elapsedMs', { ms: searchResult.elapsedMs }) }}</span>
          </div>

          <div v-if="searchResult.tableHits && searchResult.tableHits.length > 0" class="result-list">
            <div v-for="hit in searchResult.tableHits" :key="hit.tableName" class="result-item">
              <div class="hit-header">
                <span class="hit-table">{{ hit.tableName }}</span>
                <span class="hit-comment">{{ hit.tableComment || '' }}</span>
                <span class="hit-score">{{ (hit.score * 100).toFixed(1) }}%</span>
                <span class="hit-source badge" :class="hit.matchSource">{{ hit.matchSource }}</span>
              </div>
              <div v-if="hit.semanticFields && hit.semanticFields.length > 0" class="hit-fields">
                <span v-for="field in hit.semanticFields.slice(0, 5)" :key="field.columnName" class="field-tag">
                  {{ field.columnName }}
                  <span v-if="field.businessName" class="field-alias">({{ field.businessName }})</span>
                </span>
                <span v-if="hit.semanticFields.length > 5" class="field-more">
                  +{{ hit.semanticFields.length - 5 }}
                </span>
              </div>
            </div>
          </div>

          <div v-else class="result-empty">
            {{ t('schemaEmbed.noResult') }}
          </div>

          <!-- 关联关系 -->
          <div v-if="searchResult.relations && searchResult.relations.length > 0" class="relation-section">
            <h3 class="relation-title">{{ t('schemaEmbed.relatedRelations') }}</h3>
            <div class="relation-list">
              <span v-for="rel in searchResult.relations" :key="rel.id" class="relation-tag">
                {{ rel.sourceTableName }}.{{ rel.sourceColumnName }} → {{ rel.targetTableName }}.{{ rel.targetColumnName }}
                <span class="relation-type">[{{ rel.relationType }}]</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 单表嵌入预览 -->
      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">{{ t('schemaEmbed.previewTitle') }}</h2>
        </div>
        <div class="preview-box">
          <div class="preview-input-row">
            <input v-model="previewTableName" class="form-input" :placeholder="t('schemaEmbed.tableNamePlaceholder')" />
            <button class="tool-btn" :disabled="previewing" @click="handlePreview">
              {{ previewing ? t('schemaEmbed.loading') : t('schemaEmbed.previewBtn') }}
            </button>
            <button class="tool-btn primary" :disabled="embedTableLoading" @click="handleEmbedTable">
              {{ embedTableLoading ? t('schemaEmbed.loading') : t('schemaEmbed.embedTable') }}
            </button>
          </div>
          <div v-if="previewText" class="preview-content">
            <pre class="preview-pre">{{ previewText }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as schemaSearchApi from '@/api/schema-search'
import type { SchemaSearchResult } from '@/types'

const props = defineProps<{
  datasourceId: string
}>()

const { t } = useI18n()

const embedding = ref(false)
const deleting = ref(false)
const searching = ref(false)
const previewing = ref(false)
const embedTableLoading = ref(false)

const searchQuery = ref('')
const searchResult = ref<SchemaSearchResult | null>(null)

const previewTableName = ref('')
const previewText = ref('')

/** 全量嵌入 */
async function handleEmbedAll(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  embedding.value = true
  try {
    const count = await schemaSearchApi.embedSchema(props.datasourceId)
    ElMessage.success(t('schemaEmbed.embedSuccess', { count }))
  } catch {
    ElMessage.error(t('schemaEmbed.embedFail'))
  } finally {
    embedding.value = false
  }
}

/** 删除嵌入 */
async function handleDeleteEmbed(): Promise<void> {
  if (!props.datasourceId) {
    return
  }
  try {
    await ElMessageBox.confirm(t('schemaEmbed.deleteConfirm'), '', {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    deleting.value = true
    await schemaSearchApi.deleteEmbed(props.datasourceId)
    ElMessage.success(t('schemaEmbed.deleteSuccess'))
    searchResult.value = null
  } catch {
    // cancel or error
  } finally {
    deleting.value = false
  }
}

/** 语义检索 */
async function handleSearch(): Promise<void> {
  if (!props.datasourceId || !searchQuery.value.trim()) {
    return
  }
  searching.value = true
  try {
    const result = await schemaSearchApi.search({
      datasourceId: props.datasourceId,
      query: searchQuery.value.trim(),
    })
    searchResult.value = result as unknown as SchemaSearchResult
  } catch {
    searchResult.value = null
  } finally {
    searching.value = false
  }
}

/** 预览嵌入文本 */
async function handlePreview(): Promise<void> {
  if (!props.datasourceId || !previewTableName.value.trim()) {
    return
  }
  previewing.value = true
  try {
    const text = await schemaSearchApi.previewEmbeddingText(props.datasourceId, previewTableName.value.trim())
    previewText.value = (text || '') as string
  } catch {
    previewText.value = ''
  } finally {
    previewing.value = false
  }
}

/** 单表嵌入 */
async function handleEmbedTable(): Promise<void> {
  if (!props.datasourceId || !previewTableName.value.trim()) {
    return
  }
  embedTableLoading.value = true
  try {
    await schemaSearchApi.embedTable(props.datasourceId, previewTableName.value.trim())
    ElMessage.success(t('schemaEmbed.embedTableSuccess'))
  } catch {
    ElMessage.error(t('schemaEmbed.embedFail'))
  } finally {
    embedTableLoading.value = false
  }
}
</script>

<style scoped>
.schema-embed-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid #e5e6eb;
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.toolbar-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}

.toolbar-desc {
  font-size: 12px;
  color: #86909c;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.tool-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.tool-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tool-btn.primary {
  background: #165dff;
  border-color: #165dff;
  color: #fff;
}

.tool-btn.primary:hover:not(:disabled) {
  background: #0e42d2;
}

.tool-btn.danger {
  color: #f53f3f;
  border-color: #f53f3f;
}

.tool-btn.danger:hover:not(:disabled) {
  background: #f53f3f;
  color: #fff;
}

.embed-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #f2f3f5;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.search-box {
  display: flex;
  gap: 8px;
  padding: 16px 20px;
}

.search-input {
  flex: 1;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #165dff;
}

.search-btn {
  height: 34px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
  white-space: nowrap;
}

.search-btn:hover:not(:disabled) {
  background: #0e42d2;
}

.search-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.search-result {
  padding: 0 20px 16px 20px;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #86909c;
}

.result-info {
  font-weight: 500;
}

.result-time {
  color: #c9cdd4;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  padding: 10px 14px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  transition: border-color 0.15s;
}

.result-item:hover {
  border-color: #bedaff;
}

.hit-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.hit-table {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
}

.hit-comment {
  font-size: 12px;
  color: #86909c;
}

.hit-score {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: #165dff;
}

.hit-source {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 500;
}

.hit-source.keyword {
  background: #fff7e8;
  color: #ff7d00;
}

.hit-source.semantic {
  background: #e8ffea;
  color: #00b42a;
}

.hit-source.hybrid {
  background: #e8f3ff;
  color: #165dff;
}

.hit-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.field-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  background: #f2f3f5;
  color: #4e5969;
}

.field-alias {
  color: #86909c;
}

.field-more {
  font-size: 11px;
  padding: 2px 6px;
  color: #c9cdd4;
}

.result-empty {
  text-align: center;
  padding: 24px 0;
  color: #c9cdd4;
  font-size: 13px;
}

.relation-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f2f3f5;
}

.relation-title {
  font-size: 12px;
  font-weight: 500;
  color: #86909c;
  margin: 0 0 8px 0;
}

.relation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.relation-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  background: #f7f8fa;
  color: #4e5969;
  border: 1px solid #e5e6eb;
}

.relation-type {
  color: #165dff;
  font-weight: 500;
}

/* 预览区 */
.preview-box {
  padding: 16px 20px;
}

.preview-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.form-input {
  flex: 1;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: #165dff;
}

.preview-content {
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fafafa;
  max-height: 300px;
  overflow-y: auto;
}

.preview-pre {
  padding: 12px 16px;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #4e5969;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}
</style>
