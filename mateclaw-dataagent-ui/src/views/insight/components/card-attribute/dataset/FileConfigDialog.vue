<template>
  <el-dialog v-model="ui.file.visible" :title="`配置文件（${ui.file.fileType}）`" width="700px" :close-on-click-modal="false">
    <p class="hint">仅支持 Excel / CSV / TXT / JSON / Parquet。文件先上传到临时对象存储，再识别类型并预览内容（适配 macOS / Windows）。</p>

    <el-button size="small" :loading="uploading" @click="pickFile">选择文件</el-button>
    <input
      ref="fileInput"
      type="file"
      hidden
      accept=".xlsx,.xls,.csv,.txt,.json,.parquet"
      @change="onFile"
    />
    <div v-if="ui.file.fileName" class="file-name">已选择：{{ ui.file.fileName }}</div>

    <el-alert
      v-if="uploadError"
      type="warning"
      :closable="false"
      :title="uploadError"
      style="margin-top: 12px"
    />

    <el-table v-if="ui.file.columns.length" :data="pagedRows" border size="small" class="file-table">
      <el-table-column v-for="c in ui.file.columns" :key="c.name" :prop="c.name" :label="c.name" />
    </el-table>
    <el-pagination
      v-if="ui.file.rows.length"
      layout="prev,pager,next"
      :total="ui.file.rows.length"
      :page-size="pageSize"
      @current-change="onPage"
      class="file-page"
    />

    <template #footer>
      <el-button @click="ui.file.visible = false">取消</el-button>
      <el-button type="primary" :disabled="!ui.file.objectId" @click="confirmFile">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from '../useInsight'
import * as datasetApi from '@/api/dataset'

const { state, confirmFile } = useInsight()
const ui = state.ui
const fileInput = ref<HTMLInputElement>()
const pageSize = 3
const page = ref(1)
const uploading = ref(false)
const uploadError = ref('')

const pagedRows = computed(() =>
  (ui.file.rows || []).slice((page.value - 1) * pageSize, page.value * pageSize),
)

function pickFile() {
  fileInput.value?.click()
}

function inferType(v: unknown): string {
  if (v === null || v === undefined) return 'string'
  if (typeof v === 'number') return Number.isInteger(v) ? 'int' : 'double'
  if (typeof v === 'boolean') return 'boolean'
  return 'string'
}

async function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  uploading.value = true
  uploadError.value = ''
  ui.file.columns = []
  ui.file.rows = []
  try {
    // 1) 上传到临时对象存储，拿到受控 objectId（文件内容不经过页面 JSON）
    const up = await datasetApi.uploadFile(f)
    const objectId = up.objectId || (up as any).objectRef?.uri
    if (!objectId) throw new Error('上传未返回文件引用')
    const ext = (f.name.split('.').pop() || '').toUpperCase()
    const formatMap: Record<string, string> = {
      XLSX: 'Excel', XLS: 'Excel', CSV: 'CSV', TXT: 'TXT', JSON: 'JSON', PARQUET: 'Parquet',
    }
    const format = formatMap[ext] ?? ext
    ui.file.fileName = f.name
    ui.file.fileType = format
    ui.file.objectId = String(objectId)
    // 保留受控文件引用（StoredFileRef）原样回传：草稿预览与 schema 预取必须携带 fileRef
    ui.file.fileRef = up.objectId ? (up as unknown as Record<string, unknown>) : undefined

    // 2) 用真实后端识别并预览（受控行集，不把文件内容写入配置）
    const batch = await datasetApi.previewDraft({
      sourceType: 'FILE',
      sourceConfig: {
        objectId: String(objectId),
        fileName: f.name,
        format,
        ...(ui.file.fileRef ? { fileRef: ui.file.fileRef } : {}),
      },
    })
    const rows = (batch.rows as Record<string, unknown>[] | null) ?? []
    ui.file.rows = rows.map((r) =>
      Object.fromEntries(Object.entries(r).map(([k, v]) => [k, v == null ? '' : String(v)])),
    ) as Record<string, string>[]
    ui.file.columns = rows.length
      ? Object.keys(rows[0]).map((name) => ({ name, type: inferType(rows[0][name]) }))
      : []
    page.value = 1
    if (!rows.length) uploadError.value = '文件预览为空或暂不支持该格式预览'
  } catch (err) {
    uploadError.value = (err as Error)?.message || '文件上传/预览失败'
    ElMessage.error(uploadError.value)
  } finally {
    uploading.value = false
    // 允许重复选择同一文件
    ;(e.target as HTMLInputElement).value = ''
  }
}

function onPage(p: number) {
  page.value = p
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
}
.file-name {
  margin: 10px 0;
  font-size: 13px;
  color: var(--el-color-primary);
}
.file-table {
  margin-top: 8px;
}
.file-page {
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
