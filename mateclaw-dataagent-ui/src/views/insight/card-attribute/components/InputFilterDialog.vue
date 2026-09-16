<template>
  <el-dialog v-model="ui.inputFilter.visible" title="输入筛选" width="640px" :close-on-click-modal="false">
    <div class="filter-list">
      <div class="filter-row" v-for="(f, i) in list" :key="i">
        <el-select v-model="f.field" placeholder="字段" filterable class="c-field">
          <el-option v-for="c in fieldOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="f.op" class="c-op">
          <el-option v-for="o in OPS" :key="o" :label="o" :value="o" />
        </el-select>
        <el-input v-model="f.value" placeholder="值" class="c-val" :disabled="isNoValueOp(f.op)" />
        <el-button size="small" text type="danger" @click="removeRow(i)">×</el-button>
      </div>
    </div>
    <el-button size="small" @click="addRow">+ 添加筛选条件</el-button>
    <p class="hint">多个条件默认使用 AND 组合。支持 =、!=、contains、in、between、is null、is not null、最近 N 天。</p>
    <template #footer>
      <el-button @click="ui.inputFilter.visible = false">取消</el-button>
      <el-button type="primary" @click="save">确定（预览数据）</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useInsight } from '../useInsight'
import type { InputFilter } from '../useInsight'

const { state, saveInputFilter, openPreview, getDataset } = useInsight()
const ui = state.ui
const OPS = ['=', '!=', 'contains', 'in', 'between', 'is null', 'is not null', '最近 N 天']
const fieldOptions = ['event_date', 'status', 'strategy_id', 'delivery_count', 'strategy_type']
const list = ref<InputFilter[]>([])

function isNoValueOp(op: string) {
  return op === 'is null' || op === 'is not null'
}

/**
 * 解析本次「输入筛选」作用的目标数据集与预览方式：
 *  - dataset 模式（卡片数据集 / SQL 配置）：编辑指定 datasetId 的筛选条件，预览该数据集。
 *  - result 模式（Python 脚本）：编辑「最后添加的数据集」的筛选条件，
 *    与 getPreviewPayload 的读取口径一致，使结果预览能反映输入筛选；
 *    预览时打开「预处理结果预览」。
 */
function resolveTarget(): { datasetId: string; previewKind: 'dataset' | 'result' } {
  const previewKind = ui.inputFilter.previewKind ?? 'dataset'
  if (previewKind === 'result') {
    const last = state.datasets[state.datasets.length - 1]
    return { datasetId: last?.id ?? '', previewKind }
  }
  return { datasetId: ui.inputFilter.datasetId, previewKind }
}

watch(
  () => ui.inputFilter.visible,
  (v) => {
    if (v) {
      const { datasetId } = resolveTarget()
      ui.inputFilter.datasetId = datasetId // 确保 saveInputFilter 写入正确数据集
      const ds = getDataset(datasetId)
      list.value = JSON.parse(JSON.stringify(ds?.filters ?? []))
    }
  },
)

function addRow() {
  list.value.push({ field: '', op: '=', value: '' })
}
function removeRow(i: number) {
  list.value.splice(i, 1)
}
function save() {
  // 保存筛选条件到目标数据集，并关闭输入弹窗
  const { datasetId, previewKind } = resolveTarget()
  ui.inputFilter.datasetId = datasetId // 确保 saveInputFilter 写入正确数据集
  saveInputFilter(list.value)
  ui.inputFilter.visible = false
  // 配置完后弹出预览：SQL/卡片走数据集预览，Python 走结果预览
  if (previewKind === 'result') {
    openPreview('result', null)
  } else {
    openPreview('dataset', datasetId, 'data')
  }
}
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.c-field {
  flex: 1.2;
}
.c-op {
  width: 130px;
  flex-shrink: 0;
}
.c-val {
  flex: 1;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
}
</style>
