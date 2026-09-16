<template>
  <el-dialog v-model="ui.file.visible" :title="`配置文件（${ui.file.fileType}）`" width="700px" :close-on-click-modal="false">
    <p class="hint">仅支持 Excel / CSV / TXT / JSON / Parquet。程序将根据文件后缀自动识别类型并渲染内容（适配 macOS / Windows）。</p>

    <el-button size="small" @click="pickFile">选择文件</el-button>
    <!-- 原生文件选择器：accept 限定支持的类型；mac/win 均适用 -->
    <input
      ref="fileInput"
      type="file"
      hidden
      accept=".xlsx,.xls,.csv,.txt,.json,.parquet"
      @change="onFile"
    />
    <div v-if="ui.file.fileName" class="file-name">已选择：{{ ui.file.fileName }}</div>

    <!-- [MOCK] 选中文后以表格渲染内容并分页；真实场景此处解析文件二进制 -->
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
      <el-button type="primary" @click="confirmFile">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useInsight, MOCK_FILE_PREVIEW } from '../../useInsight'

const { state, confirmFile } = useInsight()
const ui = state.ui
const fileInput = ref<HTMLInputElement>()
const pageSize = 3
const page = ref(1)
const pagedRows = computed(() =>
  (ui.file.rows || []).slice((page.value - 1) * pageSize, page.value * pageSize),
)

function pickFile() {
  fileInput.value?.click()
}

function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  const name = f.name
  const ext = (name.split('.').pop() || '').toUpperCase()
  const map: Record<string, string> = {
    XLSX: 'Excel',
    XLS: 'Excel',
    CSV: 'CSV',
    TXT: 'TXT',
    JSON: 'JSON',
    PARQUET: 'Parquet',
  }
  ui.file.fileName = name
  ui.file.fileType = map[ext] ?? ext
  // [MOCK] 真实场景应解析文件内容；此处用假表格演示渲染与分页
  ui.file.columns = JSON.parse(JSON.stringify(MOCK_FILE_PREVIEW.columns))
  ui.file.rows = JSON.parse(JSON.stringify(MOCK_FILE_PREVIEW.rows))
  page.value = 1
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
