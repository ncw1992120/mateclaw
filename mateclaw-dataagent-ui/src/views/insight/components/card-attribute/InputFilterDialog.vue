<template>
  <el-dialog v-model="ui.inputFilter.visible" title="输入筛选" width="640px" :close-on-click-modal="false">
    <div class="filter-list">
      <div class="filter-row" v-for="(f, i) in list" :key="i">
        <el-select v-model="f.field" placeholder="字段" filterable class="c-field">
          <el-option v-for="c in fieldOptions" :key="c.value" :label="c.label" :value="c.value" />
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
import { ref, watch, computed } from 'vue'
import { useInsight } from './useInsight'
import type { InputFilter } from './useInsight'
import { resolveFieldLabel, resolveFieldName, type DatasetFieldMeta } from '@/utils/field-mapping'

const { state, saveInputFilter, openPreview, getDataset } = useInsight()
const ui = state.ui
// 运算符为原型 §4.2 的固定枚举，非假数据
const OPS = ['=', '!=', 'contains', 'in', 'between', 'is null', 'is not null', '最近 N 天']
/**
 * 字段下拉：来自目标数据集的**字段注册表**。
 * value 恒为字段名（技术主键，下推唯一依据），label 为展示名（未设置时回退字段名）。
 * 无已知字段时列表为空，下拉框可输入（filterable），由用户按真实字段填写，不预置任何假字段。
 */
const fieldOptions = computed<{ value: string; label: string }[]>(() => {
  const { datasetId } = resolveTarget()
  const ds = getDataset(datasetId)
  if (!ds) return []
  const fields = ds.fields ?? []
  if (fields.length) {
    return fields.map((f) => ({ value: f.name, label: resolveFieldLabel(fields, f.name) }))
  }
  if (ds.file?.columns?.length) return ds.file.columns.map((c) => ({ value: c.name, label: c.name }))
  return []
})
const list = ref<InputFilter[]>([])

function isNoValueOp(op: string) {
  return op === 'is null' || op === 'is not null'
}

/**
 * 解析本次「输入筛选」作用的目标数据集与预览方式：
 *  - dataset 模式（卡片数据集 / SQL 配置）：编辑指定 datasetId 的筛选条件，预览该数据集。
 *  - result 模式（Python 脚本）：编辑「最后添加的数据集」的筛选条件，
 *    预览时打开「预处理结果预览」（真实后端执行，反映输入筛选下推效果）。
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
    if (!v) return
    const { datasetId } = resolveTarget()
    ui.inputFilter.datasetId = datasetId // 确保 saveInputFilter 写入正确数据集
    const ds = getDataset(datasetId)
    const saved = ds?.filters ?? []
    if (saved.length) {
      // 存量归一（决策 4）：老配置里存的是展示名 / 旧目标名 → 打开时就地反解为字段名
      list.value = JSON.parse(JSON.stringify(saved)).map((f: InputFilter) => ({
        ...f,
        field: resolveFieldName(ds?.fields, f.field),
      }))
      return
    }
    // 尚未配置过筛选条件时：默认用数据集的维度字段预置条件行
    // （字段名已填，运算符/取值待用户补全），无维度字段时不预置任何条件。
    list.value = defaultDimensionFilters(ds)
  },
)

/**
 * 用数据集字段注册表中的维度字段生成默认筛选条件行。
 * 字段名恒为技术主键（`f.name`），下拉展示的是展示名（见 fieldOptions）。
 */
function defaultDimensionFilters(ds: { fields?: DatasetFieldMeta[] } | undefined): InputFilter[] {
  const dims = (ds?.fields ?? []).filter((f) => f.role === 'dimension')
  if (!dims.length) return []
  return dims.map((f) => ({ field: f.name, op: '=', value: '' }))
}

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
