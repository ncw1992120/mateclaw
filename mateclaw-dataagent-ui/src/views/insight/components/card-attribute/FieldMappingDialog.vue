<template>
  <el-dialog v-model="ui.fieldMapping.visible" title="修改字段名称" width="600px" :close-on-click-modal="false">
    <div v-loading="loading" class="fm-body">
      <template v-if="list.length">
        <div class="fm-head">
          <span>字段名</span>
          <span>字段描述</span>
          <span>目标名称</span>
        </div>
        <div class="fm-row" v-for="(m, i) in list" :key="m.source || i">
          <el-input v-model="m.source" size="small" readonly class="fm-readonly" />
          <el-input v-model="m.desc" size="small" readonly class="fm-readonly" placeholder="—" />
          <el-input v-model="m.target" size="small" placeholder="目标名称" />
        </div>
        <p class="hint">
          字段来自数据集 schema（{{ list.length }} 个字段），不支持手工新增；只读列由数据源自动带出。
          最终数据集字段名称以「目标名称」为准，后续筛选器绑定应使用最终数据集字段名称。
        </p>
      </template>

      <!-- 空态：字段只能来自数据集 schema，不提供手工新增入口 -->
      <div v-else class="fm-empty">
        <p>暂无字段：字段由数据集 schema 自动生成，不支持手工新增。</p>
        <el-button v-if="canFetch" size="small" :loading="loading" @click="reload()">重新获取字段</el-button>
        <p v-else class="fm-empty-hint">
          当前数据集配置无法获取字段结构（接口类型需先在数据源中登记受控接口定义）。
        </p>
      </div>
    </div>

    <template #footer>
      <el-button @click="ui.fieldMapping.visible = false">取消</el-button>
      <el-button type="primary" :disabled="loading" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from './useInsight'
import type { FieldMapping } from './useInsight'
import { buildFieldMappingRows } from '@/utils/field-mapping'

const { state, saveFieldMapping, getDataset, canFetchDatasetSchema, refreshDatasetSchema } = useInsight()
const ui = state.ui
const list = ref<FieldMapping[]>([])
const loading = ref(false)

const canFetch = computed(() => canFetchDatasetSchema(getDataset(ui.fieldMapping.datasetId)))

/** 用数据集 schema 缓存 + 已保存映射重建表格（无缓存时列表为空 → 空态） */
function rebuild() {
  const ds = getDataset(ui.fieldMapping.datasetId)
  list.value = buildFieldMappingRows(ds?.schema ?? [], ds?.fieldMapping ?? [])
}

/**
 * 重新拉取字段结构：用于配置确定后预取失败、或缓存为空时的兜底。
 * 合并结果会写回数据集（保留用户已改过的目标名称）；silent=true 时不打扰用户。
 */
async function reload(silent = false) {
  loading.value = true
  try {
    const res = await refreshDatasetSchema(ui.fieldMapping.datasetId)
    rebuild()
    if (!silent) {
      if (res.ok) ElMessage.success(res.message)
      else ElMessage.warning(res.message)
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => ui.fieldMapping.visible,
  async (v) => {
    if (!v) return
    list.value = []
    rebuild()
    // 缓存为空（新增数据集预取未完成/失败、或历史数据无 schema）→ 兜底实时拉一次
    if (!list.value.length && canFetch.value) await reload(true)
  },
)

function save() {
  const error = saveFieldMapping(list.value)
  if (error) ElMessage.error(error)
}
</script>

<style scoped>
.fm-body {
  min-height: 80px;
}
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
.fm-readonly :deep(.el-input__wrapper) {
  background: var(--el-fill-color-light);
  box-shadow: none;
}
.fm-readonly :deep(.el-input__inner) {
  color: var(--el-text-color-regular);
  cursor: default;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
  line-height: 1.6;
}
.fm-empty {
  text-align: center;
  padding: 16px 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.fm-empty p {
  margin: 0 0 10px;
}
.fm-empty-hint {
  font-size: 12px;
  line-height: 1.6;
}
</style>
