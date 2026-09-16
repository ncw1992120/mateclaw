<template>
  <el-dialog v-model="ui.jdbc.visible" title="输入 SQL" width="680px" :close-on-click-modal="false">
    <p class="hint">
      只允许 <code>SELECT</code> / <code>WITH</code> 只读查询；参数使用绑定方式（<code>:param</code>），不强制包含 WHERE；写入/多语句/危险函数由后端拒绝。
    </p>
    <!-- [MOCK] SQL 编辑器：此处用 textarea 占位，后续可替换为 Monaco 编辑器组件 -->
    <el-input
      v-model="ui.jdbc.sql"
      type="textarea"
      :rows="12"
      class="sql-editor"
      @input="onSqlInput"
    />
    <template #footer>
      <el-button @click="ui.jdbc.visible = false">取消</el-button>
      <el-button @click="onPreview">筛选预览</el-button>
      <el-button @click="onExecLog">执行记录</el-button>
      <el-button type="primary" @click="confirmJdbc">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useInsight } from '../../useInsight'

const { state, confirmJdbc, openPreview } = useInsight()
const ui = state.ui

// [MOCK] 停止修改 SQL 3 秒后，自动生成/刷新数据集表单（文档 3.1 规则 5）
let timer: any
function onSqlInput() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    ElMessage.info('[MOCK] 已根据 SQL 自动刷新数据集字段与预览')
  }, 3000)
}

// 筛选预览：先保存当前定义，再预览当前输入数据集（文档 3.1 规则 4）
function onPreview() {
  confirmJdbc()
  openPreview('dataset', null) // datasetId=null 时预览最新数据集
}

// [MOCK] 执行记录占位
function onExecLog() {
  ElMessage.info('[MOCK] 执行记录：最近一次 SELECT 耗时 18ms，扫描 1.2M 行，返回 4 行')
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
