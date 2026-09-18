<template>
  <el-dialog v-model="ui.fieldMapping.visible" title="修改字段名称" width="640px" :close-on-click-modal="false">
    <div v-loading="loading" class="fm-body">
      <template v-if="list.length">
        <div class="fm-head">
          <span>字段名</span>
          <span>字段描述</span>
          <span>展示名</span>
        </div>
        <div class="fm-row" v-for="(m, i) in list" :key="m.name || i">
          <el-tooltip :content="m.name" placement="top" :show-after="300">
            <el-input :model-value="m.name" size="small" readonly class="fm-readonly" />
          </el-tooltip>
          <el-input :model-value="descOf(m)" size="small" readonly class="fm-readonly" placeholder="—" />
          <el-input
            v-model="m.displayName"
            size="small"
            :class="{ 'fm-invalid': dupNames.has(m.name) }"
            :placeholder="m.name"
          />
        </div>
        <p v-if="error" class="hint hint-error">{{ error }}</p>
        <p class="hint">
          字段来自数据集 schema（{{ list.length }} 个字段），不支持手工新增；只读列由数据源自动带出。
          <b>字段名</b>为技术主键（下推 / 脚本 / Join 均按它），不可修改；<b>展示名</b>为表现层标签，
          数据集内需唯一，清空即回退字段名。展示名改动对筛选条件、绑定筛选器、指标配置、预览表头<b>立即生效</b>。
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
      <el-button type="primary" :disabled="loading || !!error" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight } from './useInsight'
import { duplicateFieldNames, validateFieldMetas, type DatasetFieldMeta } from '@/utils/field-mapping'

const { state, saveFieldMetas, getDataset, canFetchDatasetSchema, refreshDatasetSchema } = useInsight()
const ui = state.ui
/** 注册表草稿：三列读写同一份条目（字段名只读、描述只读、展示名可编辑） */
const list = ref<DatasetFieldMeta[]>([])
const loading = ref(false)

const canFetch = computed(() => canFetchDatasetSchema(getDataset(ui.fieldMapping.datasetId)))

/** 展示名重复 / 与字段名冲突的行（决策 1：重复即拦截） */
const dupNames = computed(() => duplicateFieldNames(list.value))
const error = computed(() => validateFieldMetas(list.value))

/** 字段描述列：真实描述优先，缺失时回退展示名（数据源只提供这些，只读） */
function descOf(m: DatasetFieldMeta): string {
  return (m.description ?? '').trim() || (m.displayName ?? '').trim()
}

/** 用数据集字段注册表重建表格（无注册表时列表为空 → 空态） */
function rebuild() {
  const ds = getDataset(ui.fieldMapping.datasetId)
  list.value = (ds?.fields ?? []).map((f) => ({ ...f }))
}

/**
 * 重新拉取字段结构：用于配置确定后预取失败、或缓存为空时的兜底。
 * 合并结果会写回注册表（保留用户已改过的展示名）；silent=true 时不打扰用户。
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
  const err = saveFieldMetas(list.value)
  if (err) {
    ElMessage.error(err)
    return
  }
  ElMessage.success(`已保存 ${list.value.length} 个字段的展示名`)
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
.fm-invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
  line-height: 1.6;
}
.hint-error {
  color: var(--el-color-danger);
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
