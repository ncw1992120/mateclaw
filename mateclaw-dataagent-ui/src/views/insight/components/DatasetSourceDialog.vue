<template>
  <el-dialog v-model="visible" :title="title" width="560px" aria-label="配置数据集来源" @close="cancel">
    <div class="source-dialog-body">
      <template v-if="draft.sourceType === 'JDBC_SQL'">
        <div class="source-caption">JDBC · {{ draft.datasourceName || draft.datasourceId }}</div>
        <el-button v-if="!sqlEditing" type="primary" plain @click="sqlEditing = true">输入 SQL</el-button>
        <el-input v-else v-model="draft.sql" type="textarea" :rows="8" aria-label="输入 SQL" placeholder="select ... from ... where ..." />
        <p class="form-hint">SQL 仅允许单条只读查询，参数使用绑定方式。</p>
      </template>
      <template v-else-if="draft.sourceType === 'ALOUDATA_ANALYSIS_VIEW'">
        <el-select v-model="draft.analysisViewId" placeholder="选择指标视图" aria-label="选择指标视图" style="width:100%">
          <el-option v-for="view in analysisViews" :key="view.id || view.viewName" :label="view.displayName || view.viewName" :value="view.id || view.viewName" />
        </el-select>
        <el-input v-model="draft.metricsText" aria-label="输出指标" placeholder="输出指标，多个用逗号分隔" />
        <el-input v-model="draft.dimensionsText" aria-label="输出维度" placeholder="输出维度，多个用逗号分隔" />
      </template>
      <template v-else-if="draft.sourceType === 'ALOUDATA_METRICS'">
        <el-input v-model="draft.metricsText" aria-label="配置指标" placeholder="指标，多个用逗号分隔" />
        <el-input v-model="draft.dimensionsText" aria-label="配置维度" placeholder="维度，多个用逗号分隔" />
        <el-input v-model="draft.filtersText" aria-label="指标筛选条件" placeholder="筛选条件（可选）" />
      </template>
      <template v-else-if="draft.sourceType === 'HTTP_API'">
        <el-input v-model="draft.host" aria-label="Host" placeholder="Host" />
        <el-input v-model="draft.path" aria-label="请求路径" placeholder="请求路径" />
        <el-select v-model="draft.method" aria-label="请求方式" style="width:100%"><el-option label="GET" value="GET" /><el-option label="POST" value="POST" /></el-select>
        <el-input-number v-model="draft.timeoutMs" aria-label="超时时长" :min="100" :max="120000" />
        <el-input v-model="draft.headersText" aria-label="请求头" placeholder="请求头（JSON）" />
        <el-input v-model="draft.parametersText" aria-label="请求参数" placeholder="请求参数（JSON）" />
      </template>
      <template v-else-if="draft.sourceType === 'FILE'">
        <el-select v-model="draft.fileFormat" aria-label="文件类型" placeholder="文件类型" style="width:100%">
          <el-option v-for="format in fileFormats" :key="format" :label="format" :value="format" />
        </el-select>
        <input type="file" aria-label="选择文件" @change="selectFile" />
        <p v-if="draft.fileName" class="form-hint">已选择：{{ draft.fileName }}</p>
      </template>
    </div>
    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button plain :loading="previewing" @click="preview">筛选预览</el-button>
      <el-button v-if="draft.sourceType === 'JDBC_SQL'" text @click="showExecution = true">执行记录</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="previewVisible" title="当前数据集预览" width="720px" aria-label="当前数据集预览">
    <div v-if="previewError" class="preview-error">{{ previewError }}</div>
    <table v-else-if="previewRows.length" class="preview-table"><tbody><tr v-for="(row, i) in previewRows" :key="i"><td v-for="(value, key) in row" :key="key">{{ value }}</td></tr></tbody></table>
    <el-empty v-else description="暂无预览数据" />
  </el-dialog>
  <el-dialog v-model="showExecution" title="执行记录" width="520px"><p>当前草稿执行记录将在确认后保留查询摘要、状态和耗时。</p></el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DatasetSourceType } from '@/types'
import * as datasetApi from '@/api/dataset'
import * as datasourceApi from '@/api/datasource'
import type { DatasetSourceSelection } from './DatasetSourcePicker.vue'

const props = defineProps<{ modelValue: boolean; selection: DatasetSourceSelection }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'confirm', value: DatasetSourceSelection & { sourceConfig?: Record<string, unknown>; displayName?: string }): void }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const draft = ref<any>({ sourceType: props.selection.sourceType, datasourceId: props.selection.datasourceId, datasetId: props.selection.datasetId, method: 'GET', timeoutMs: 30000, fileFormat: 'CSV' })
const analysisViews = ref<any[]>([])
const sqlEditing = ref(false)
const previewing = ref(false)
const previewVisible = ref(false)
const previewRows = ref<Record<string, unknown>[]>([])
const previewError = ref('')
const showExecution = ref(false)
const fileFormats = ['Excel', 'CSV', 'TXT', 'JSON', 'Parquet']
const title = computed(() => ({ JDBC_SQL: '输入 SQL', ALOUDATA_ANALYSIS_VIEW: '配置指标视图', ALOUDATA_METRICS: '配置指标&维度', HTTP_API: '配置接口', FILE: '配置文件' } as Record<string, string>)[draft.value.sourceType] || '配置数据集')

watch(() => props.selection, async selection => {
  draft.value = { sourceType: selection.sourceType, datasourceId: selection.datasourceId, datasetId: selection.datasetId, method: 'GET', timeoutMs: 30000, fileFormat: 'CSV' }
  sqlEditing.value = false
  if (selection.sourceType === 'ALOUDATA_ANALYSIS_VIEW' && selection.datasourceId) {
    try { analysisViews.value = await datasourceApi.listAnalysisViews(selection.datasourceId) as any[] } catch { analysisViews.value = [] }
  }
}, { immediate: true, deep: true })

function selectFile(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) { draft.value.fileName = file.name; draft.value.file = file }
}

async function preview(): Promise<void> {
  previewing.value = true; previewError.value = ''; previewVisible.value = true
  try {
    if (draft.value.datasetId) {
      const result = await datasetApi.previewInput({ datasetId: draft.value.datasetId, inputName: 'draft', filters: [], limit: 20 }) as any
      previewRows.value = result.rows || []
    } else {
      const result = await datasetApi.previewDraft({
        sourceType: draft.value.sourceType,
        datasourceId: draft.value.datasourceId,
        sourceConfig: buildSourceConfig(),
        filters: [],
        limit: 20,
      }) as any
      previewRows.value = result.rows || result.data?.rows || []
      if (result.error || result.data?.error) previewError.value = result.error || result.data.error
    }
  } catch (error: any) { previewError.value = error?.message || '预览失败'; previewRows.value = [] }
  finally { previewing.value = false }
}

async function confirm(): Promise<void> {
  if (draft.value.sourceType === 'FILE' && !draft.value.file && !draft.value.objectId) { previewError.value = '请选择文件'; return }
  if (draft.value.sourceType === 'HTTP_API' && (!String(draft.value.host || '').trim() || !String(draft.value.path || '').trim())) { previewError.value = 'Host 和请求路径不能为空'; return }
  if (draft.value.sourceType === 'HTTP_API' && !draft.value.apiDefinitionId) {
    try {
      const host = String(draft.value.host).trim().replace(/\/$/, '')
      const path = String(draft.value.path).startsWith('/') ? draft.value.path : `/${draft.value.path}`
      const endpoint = /^https?:\/\//i.test(host) ? `${host}${path}` : `https://${host}${path}`
      const params = parseJsonObject(draft.value.parametersText)
      const headers = parseJsonObject(draft.value.headersText) as Record<string, string>
      const registered = await datasetApi.registerApiDefinition({ datasourceId: draft.value.datasourceId, endpoint, method: draft.value.method, allowedQueryParams: Object.keys(params), headers }) as any
      draft.value.apiDefinitionId = registered?.apiDefinitionId || registered?.data?.apiDefinitionId
      if (!draft.value.apiDefinitionId) { previewError.value = '接口登记未返回定义 ID'; return }
    } catch (error: any) { previewError.value = error?.message || '接口定义登记失败'; return }
  }
  if (draft.value.sourceType === 'ALOUDATA_ANALYSIS_VIEW' && !draft.value.analysisViewId) { previewError.value = '请选择指标视图'; return }
  if (draft.value.sourceType === 'ALOUDATA_METRICS' && !split(draft.value.metricsText).length) { previewError.value = '至少配置一个指标'; return }
  if (draft.value.sourceType === 'FILE' && draft.value.file && typeof datasetApi.uploadFile === 'function') {
    try {
      const uploaded = await datasetApi.uploadFile(draft.value.file) as any
      draft.value.objectId = uploaded?.objectId || uploaded?.data?.objectId
      draft.value.fileRef = uploaded?.data || uploaded
      if (!draft.value.objectId) { previewError.value = '文件上传未返回 objectId'; return }
    } catch (error: any) { previewError.value = error?.message || '文件上传失败'; return }
  }
  const sourceConfig = buildSourceConfig()
  /* sourceConfig is built before confirmation so preview and confirm use one contract. */
  if (draft.value.sourceType === 'JDBC_SQL' && !String(sourceConfig.sql || '').trim()) { previewError.value = '请输入 SQL'; return }
  let datasetId = draft.value.datasetId
  if (!datasetId && typeof datasetApi.confirmDraft === 'function') {
    try {
      const result = await datasetApi.confirmDraft({ sourceType: draft.value.sourceType, datasourceId: draft.value.datasourceId, sourceConfig, name: `${title.value}-${Date.now()}` }) as any
      datasetId = result?.datasetId || result?.data?.datasetId
    } catch (error: any) {
      previewError.value = error?.message || '确认数据集失败'
      return
    }
  }
  emit('confirm', { ...props.selection, ...draft.value, datasetId, sourceConfig, displayName: title.value })
  visible.value = false
}

function buildSourceConfig(): Record<string, unknown> {
  const sourceConfig: Record<string, unknown> = { datasourceId: draft.value.datasourceId }
  if (draft.value.sourceType === 'JDBC_SQL') sourceConfig.sql = draft.value.sql
  if (draft.value.sourceType === 'ALOUDATA_ANALYSIS_VIEW') sourceConfig.analysisViewId = draft.value.analysisViewId
  if (draft.value.sourceType === 'ALOUDATA_METRICS') { sourceConfig.metrics = split(draft.value.metricsText); sourceConfig.dimensions = split(draft.value.dimensionsText); sourceConfig.filters = draft.value.filtersText }
  if (draft.value.sourceType === 'HTTP_API') Object.assign(sourceConfig, { apiDefinitionId: draft.value.apiDefinitionId, host: draft.value.host, path: draft.value.path, method: draft.value.method, timeoutMs: draft.value.timeoutMs, headers: draft.value.headersText, parameters: draft.value.parametersText })
  if (draft.value.sourceType === 'FILE') Object.assign(sourceConfig, { format: draft.value.fileFormat, fileName: draft.value.fileName, objectId: draft.value.objectId, fileRef: draft.value.fileRef })
  return sourceConfig
}
function cancel(): void { visible.value = false }
function split(value?: string): string[] { return String(value || '').split(',').map(item => item.trim()).filter(Boolean) }
function parseJsonObject(value?: string): Record<string, unknown> { if (!value?.trim()) return {}; try { const parsed = JSON.parse(value); return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {} } catch { return {} } }
</script>

<style scoped>
.source-dialog-body { display: grid; gap: 12px; }
.source-caption { color: var(--el-text-color-secondary); font-size: 13px; }
.form-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.preview-error { color: var(--el-color-danger); }
.preview-table { width: 100%; border-collapse: collapse; }
.preview-table td { border: 1px solid var(--el-border-color-lighter); padding: 6px; }
</style>
