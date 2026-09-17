<template>
  <el-dialog v-model="ui.fieldMapping.visible" title="修改字段名称" width="600px" :close-on-click-modal="false">
    <div class="fm-head">
      <span>字段名</span>
      <span>字段描述</span>
      <span>目标名称</span>
    </div>
    <div class="fm-row" v-for="(m, i) in list" :key="i">
      <el-input v-model="m.source" size="small" placeholder="源字段名" />
      <el-input v-model="m.desc" size="small" placeholder="描述" />
      <el-input v-model="m.target" size="small" placeholder="目标名称" />
    </div>
    <el-button size="small" class="fm-add" @click="addRow">+ 添加字段映射</el-button>
    <p class="hint">最终数据集字段名称以「目标名称」为准；后续筛选器绑定应使用最终数据集字段名称。</p>
    <template #footer>
      <el-button @click="ui.fieldMapping.visible = false">取消</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useInsight } from './useInsight'
import type { FieldMapping } from './useInsight'

const { state, saveFieldMapping, getDataset } = useInsight()
const ui = state.ui
const list = ref<FieldMapping[]>([])

watch(
  () => ui.fieldMapping.visible,
  (v) => {
    if (v) {
      const ds = getDataset(ui.fieldMapping.datasetId)
      list.value = JSON.parse(JSON.stringify(ds?.fieldMapping ?? []))
    }
  },
)

function addRow() {
  list.value.push({ source: '', desc: '', target: '' })
}
function save() {
  saveFieldMapping(list.value)
}
</script>

<style scoped>
.fm-head,
.fm-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}
.fm-head {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.fm-add {
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
}
</style>
