<template>
  <el-dialog v-model="ui.filterBinding.visible" title="绑定筛选器" width="720px" :close-on-click-modal="false">
    <!-- 顶部工具条：统计 + 继续添加 -->
    <div class="fb-toolbar">
      <span class="fb-count">已绑定 {{ drafts.length }} 个筛选器</span>
      <el-button size="small" type="primary" plain @click="addBinding">+ 添加筛选器</el-button>
    </div>

    <!-- 多条绑定条目 -->
    <div class="fb-list">
      <div class="fb-item" v-for="(d, i) in drafts" :key="i">
        <div class="fb-item-head">
          <div class="fb-item-title">筛选器 {{ i + 1 }}</div>
          <el-button size="small" text type="danger" :disabled="drafts.length === 1" @click="removeBinding(i)">
            删除
          </el-button>
        </div>

        <!-- 筛选器（仪表盘参数） -->
        <div class="fb-section">
          <div class="fb-label">筛选器（仪表盘参数）</div>
          <el-select v-model="d.filterName" placeholder="选择筛选器" style="width: 100%" filterable @change="onFilterNameChange(d)">
            <el-option v-for="f in FILTERS" :key="f" :label="f" :value="f" />
          </el-select>
        </div>

        <!-- 作用范围 -->
        <div class="fb-section">
          <div class="fb-label">作用范围（勾选该筛选器作用到的数据集）</div>
          <el-checkbox-group v-model="d.scopeKeys">
            <el-checkbox v-for="ds in state.datasets" :key="ds.id" :value="ds.id" :label="ds.alias" />
          </el-checkbox-group>
        </div>

        <!-- 字段映射：自动匹配 + 手动映射 -->
        <div class="fb-section">
          <div class="fb-label">字段映射（按数据集字段名自动推断，需手动映射的可编辑目标字段）</div>
          <div class="fm-row" v-for="ds in state.datasets" :key="ds.id">
            <span class="ds-alias">数据集 {{ ds.alias }}</span>
            <el-input v-model="d.fieldMap[ds.id]" size="small" placeholder="匹配到的字段名" />
            <span class="fb-label-hint">{{ labelOf(ds, d.fieldMap[ds.id]) }}</span>
            <span class="match-tag" :class="matched(ds, d.filterName) ? 'ok' : 'warn'">
              {{ matched(ds, d.filterName) ? '✓ 自动匹配' : '⚠ 需手动映射' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="ui.filterBinding.visible = false">取消</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useInsight } from './useInsight'
import type { DatasetConfig } from './useInsight'
import { resolveFieldLabel, type DatasetFieldMeta } from '@/utils/field-mapping'

const { state, saveFilterBindings } = useInsight()
const ui = state.ui

/** 仪表盘参数名候选：来自当前仪表盘真实的筛选器组件（由 hydratePanel 注入 state.filterCatalog）。
 *  无可用筛选器时列表为空，下拉框可输入（filterable），由用户按真实参数名填写，不预置任何假词表。 */
const FILTERS = computed<string[]>(() => state.filterCatalog.map((f) => f.title))

/** 回填时把老引用（展示名 / 旧目标名）归一为字段名（决策 4） */
function toFieldName(ds: DatasetConfig, ref: string): string {
  const key = (ref ?? '').trim()
  if (!key) return ''
  const fields: DatasetFieldMeta[] = ds.fields ?? []
  // 已经是字段名 → 原样；否则按展示名反解（展示名唯一，反解无歧义）
  if (fields.some((f) => f.name === key)) return key
  const hit = fields.find((f) => (f.displayName ?? '').trim() === key)
  return hit ? hit.name : key
}

/** 映射框旁的可读名（展示名；未设置时回退字段名） */
function labelOf(ds: DatasetConfig, field: string): string {
  const key = (field ?? '').trim()
  if (!key) return ''
  const label = resolveFieldLabel(ds.fields, key)
  return label === key ? '' : label
}

/**
 * 真实字段名推断：候选为数据集字段注册表的「字段名 + 展示名」，
 * 与筛选器名做精确 / 包含匹配；**返回值恒为字段名**（改名不影响已保存的绑定）。
 */
function inferField(ds: DatasetConfig, filterName: string): string {
  const fields = (ds.fields || []).flatMap((f) => [f.name, f.displayName]).filter((f): f is string => !!f)
  const exact = fields.find((f) => f === filterName)
  const candidate = exact ?? fields.find((f) => f && (f.includes(filterName) || filterName.includes(f)))
  // 命中的可能是展示名 → 统一反解成字段名
  return candidate ? toFieldName(ds, candidate) : ''
}

/** 是否可自动匹配（基于真实字段推断） */
function matched(ds: DatasetConfig, filterName: string): boolean {
  return !!inferField(ds, filterName)
}

/** 单条筛选器绑定草稿（对话框内部维护） */
interface FBDraft {
  filterName: string
  scopeKeys: string[]
  fieldMap: Record<string, string> // datasetId -> 映射字段
}

/** 按当前数据集构造一份「全选作用范围 + 自动匹配字段」的空草稿 */
function emptyFieldMap(filterName: string): Record<string, string> {
  const fm: Record<string, string> = {}
  state.datasets.forEach((ds) => (fm[ds.id] = inferField(ds, filterName)))
  return fm
}
function emptyDraft(): FBDraft {
  return {
    filterName: '',
    scopeKeys: state.datasets.map((d) => d.id),
    fieldMap: emptyFieldMap(''),
  }
}

const drafts = ref<FBDraft[]>([])

watch(
  () => ui.filterBinding.visible,
  (v) => {
    if (!v) return
    if (state.filterBindings && state.filterBindings.length) {
      // 回填：已有绑定 -> 草稿列表
      drafts.value = state.filterBindings.map((b) => {
        const fm: Record<string, string> = {}
        state.datasets.forEach((ds) => {
          const found = b.fieldMap.find((m) => m.datasetId === ds.id)
          const raw = found?.field ?? inferField(ds, b.filterName)
          fm[ds.id] = toFieldName(ds, raw)
        })
        return {
          filterName: b.filterName,
          scopeKeys: Object.keys(b.scope).filter((k) => b.scope[k]),
          fieldMap: fm,
        }
      })
    } else {
      // 首开：至少一条空草稿
      drafts.value = [emptyDraft()]
    }
  },
)

/** 筛选器变更时，基于真实字段重新推断各数据集的映射字段 */
function onFilterNameChange(d: FBDraft) {
  d.fieldMap = emptyFieldMap(d.filterName)
}

/** 继续绑定一个筛选器 */
function addBinding() {
  drafts.value.push(emptyDraft())
}
/** 删除某条绑定（至少保留一条） */
function removeBinding(i: number) {
  if (drafts.value.length > 1) drafts.value.splice(i, 1)
}

function save() {
  // 仅保存已选筛选器的条目（过滤掉未选择的空草稿）
  const arr = drafts.value
    .filter((d) => d.filterName)
    .map((d) => {
      const scope: Record<string, boolean> = {}
      state.datasets.forEach((ds) => (scope[ds.id] = d.scopeKeys.includes(ds.id)))
      const fm = state.datasets.map((ds) => ({
        datasetId: ds.id,
        // 存字段名（技术主键）：展示名改了绑定关系依然有效
        field: toFieldName(ds, d.fieldMap[ds.id] ?? ''),
        matched: matched(ds, d.filterName),
      }))
      return { filterName: d.filterName, scope, fieldMap: fm }
    })
  saveFilterBindings(arr)
}
</script>

<style scoped>
.fb-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.fb-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.fb-list {
  max-height: 54vh;
  overflow: auto;
  padding-right: 4px;
}
.fb-item {
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  padding: 12px;
  margin-bottom: 12px;
  background: var(--db-muted);
}
.fb-item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.fb-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text);
}
.fb-section {
  margin-bottom: 14px;
}
.fb-section:last-child {
  margin-bottom: 0;
}
.fb-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.fm-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.ds-alias {
  width: 110px;
  font-size: 13px;
  flex-shrink: 0;
}
.fb-label-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.match-tag {
  font-size: 12px;
  flex-shrink: 0;
  width: 96px;
  text-align: right;
}
.match-tag.ok {
  color: var(--el-color-success);
}
.match-tag.warn {
  color: var(--el-color-warning);
}
</style>
