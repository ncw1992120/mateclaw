<template>
  <el-dialog v-model="ui.jdbc.visible" title="输入 SQL" width="680px" :close-on-click-modal="false">
    <p class="hint">
      只允许 <code>SELECT</code> / <code>WITH</code> 只读查询；参数使用绑定方式（<code>:param</code>），不强制包含 WHERE；写入/多语句/危险函数由后端拒绝。
    </p>
    <!-- 基础 SQL 编辑器：textarea 占位，后续可替换为 Monaco 编辑器组件（不影响真实提交） -->
    <el-input
      v-model="ui.jdbc.sql"
      type="textarea"
      :rows="12"
      class="sql-editor"
      :disabled="refreshing"
      @input="onSqlInput"
    />
    <template #footer>
      <el-button @click="ui.jdbc.visible = false">取消</el-button>
      <el-button @click="onPreview">查看数据</el-button>
      <el-button type="primary" @click="confirmJdbc">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from '../useInsight'
import * as backend from '../useInsightBackend'

const { state, confirmJdbc, openDataDialog, getDataset } = useInsight()
const ui = state.ui

// 停止修改 SQL 800ms 后，真实向数据源预览一次：校验 SQL 并返回字段列（原型 3.1 规则 5）。
// 后端未配置对应 JDBC 数据源或 SQL 有误时调用会失败，此处如实提示错误，不伪造任何结果。
const refreshing = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null
function onSqlInput() {
  if (timer) clearTimeout(timer)
  const db = ui.jdbc.db
  const sql = ui.jdbc.sql.trim()
  if (!db || !sql) return
  timer = setTimeout(async () => {
    refreshing.value = true
    try {
      const batch = await backend.previewDatasetDraft({
        sourceType: 'JDBC_SQL',
        datasourceId: db,
        sourceConfig: { sql },
        filters: [],
      })
      const cols =
        batch.columns ??
        (batch.rows && batch.rows[0] ? Object.keys(batch.rows[0] as Record<string, unknown>) : [])
      ElMessage.success(`已根据 SQL 刷新字段（${cols.length} 列）`)
    } catch (e) {
      ElMessage.warning(`SQL 预览失败：${(e as Error)?.message || '未知错误'}`)
    } finally {
      refreshing.value = false
    }
  }, 800)
}

// 查看数据：先提交当前 SQL 配置（新增/更新数据集并关闭 SQL 弹窗），
// 再打开「查看数据」弹窗 —— 在那里添加筛选条件后点查询，条件下推到源查询
function onPreview() {
  const editingId = state.ui.editingDatasetId
  confirmJdbc()
  // 解析本次提交的数据集 id：编辑时沿用 editingId，新增时取最新提交的数据集
  const id = editingId && getDataset(editingId) ? editingId : state.datasets[state.datasets.length - 1]?.id ?? ''
  if (id) openDataDialog(id)
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 10px;
}
.hint code {
  background: var(--el-fill-color);
  padding: 1px 4px;
  border-radius: 3px;
}
.sql-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}
</style>
