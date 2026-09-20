<template>
  <el-dialog v-model="ui.api.visible" title="配置接口" width="560px" :close-on-click-modal="false">
    <el-form label-width="80px" class="api-form">
      <el-form-item label="Host">
        <el-input v-model="ui.api.host" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="请求路径">
        <el-input v-model="ui.api.path" placeholder="/v1/resource" />
      </el-form-item>
      <el-form-item label="请求方式">
        <el-select v-model="ui.api.method" style="width: 140px">
          <el-option label="POST" value="POST" />
          <el-option label="GET" value="GET" />
          <el-option label="PUT" value="PUT" />
        </el-select>
      </el-form-item>
      <el-form-item label="超时时长">
        <el-input-number v-model="ui.api.timeout" :min="100" :step="500" />
        <span class="unit">ms</span>
      </el-form-item>
      <el-form-item label="请求头">
        <el-input v-model="ui.api.headers" type="textarea" :rows="2" placeholder="JSON 或 key:value" />
      </el-form-item>
      <el-form-item label="请求参数">
        <el-input v-model="ui.api.params" type="textarea" :rows="2" placeholder="JSON 或 key=value" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="ui.api.visible = false">取消</el-button>
      <el-button @click="onPreview">查看数据</el-button>
      <el-button type="primary" @click="confirmApi">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useInsight } from '../useInsight'

const { state, confirmApi, openWorkbench, getDataset } = useInsight()
const ui = state.ui

// 确认后保存受控接口定义引用；敏感认证信息只引用凭据（通过数据源/凭据中心登记），不写入页面配置或日志。
// 随后打开全屏工作台 —— 参数从接口配置里的 {{name}} / :name 自动提取
function onPreview() {
  const editingId = state.ui.editingDatasetId
  confirmApi()
  const id = editingId && getDataset(editingId) ? editingId : state.datasets[state.datasets.length - 1]?.id ?? ''
  if (id) openWorkbench(id)
}
</script>

<style scoped>
.api-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
