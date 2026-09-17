<template>
  <el-dialog v-model="ui.preview.visible" :title="title" width="780px" :close-on-click-modal="false">
    <div v-if="previewState.loading" v-loading="true" element-loading-text="正在拉取预览数据…" class="preview-loading" />

    <el-alert
      v-else-if="previewState.error"
      type="error"
      :closable="false"
      :title="previewState.error"
      style="margin-bottom: 12px"
    />

    <template v-else-if="payload">
      <el-tabs v-model="tab">
        <!-- 数据预览 -->
        <el-tab-pane label="数据预览" name="data">
          <el-table :data="pagedRows" border size="small" max-height="320">
            <el-table-column v-for="c in payload.dataColumns" :key="c.name" :prop="c.name" :label="c.name" />
          </el-table>
          <div class="preview-pager">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="payload.dataRows.length"
              layout="prev, pager, next, sizes, total"
              :page-sizes="[5, 10, 20, 50]"
              background
              size="small"
              @size-change="onSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- 字段结构 -->
        <el-tab-pane label="字段结构" name="struct">
          <el-table :data="payload.fieldStruct" border size="small" max-height="320">
            <el-table-column prop="name" label="字段名" />
            <el-table-column prop="type" label="类型" />
            <el-table-column prop="desc" label="描述" />
            <el-table-column label="可空">
              <template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 执行信息 -->
        <el-tab-pane label="执行信息" name="exec">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="输入数据集">{{ payload.execInfo.inputDatasets.join(', ') }}</el-descriptions-item>
            <el-descriptions-item label="实际查询条件">{{ payload.execInfo.queryConditions || '—' }}</el-descriptions-item>
            <el-descriptions-item label="已下推筛选条件">{{ payload.execInfo.pushedFilters.join('；') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="扫描量 / 返回量">{{ payload.execInfo.scanned }} / {{ payload.execInfo.returned }}</el-descriptions-item>
            <el-descriptions-item label="Python 处理耗时">{{ payload.execInfo.pythonTime }}</el-descriptions-item>
            <el-descriptions-item label="输入行数 / 输出行数">{{ payload.execInfo.inRows }} / {{ payload.execInfo.outRows }}</el-descriptions-item>
            <el-descriptions-item label="错误信息">{{ payload.execInfo.error || '无' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 处理日志 -->
        <el-tab-pane label="处理日志" name="log">
          <div v-for="l in payload.logs" :key="l.time + l.msg" class="log-line">
            <span class="log-time">{{ l.time }}</span>
            <span class="log-level" :class="l.level.toLowerCase()">[{{ l.level }}]</span>
            <span class="log-msg">{{ l.msg }}</span>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else description="暂无预览数据" />

    <template #footer>
      <el-button @click="ui.preview.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInsight } from './useInsight'

const { state, previewState, loadDatasetPreview, loadResultPreview } = useInsight()
const ui = state.ui
const tab = ref('data')
const page = ref(1)
const pageSize = ref(5)

const title = computed(() => {
  const map: Record<string, string> = {
    dataset: '当前数据集预览',
    result: '预处理结果预览',
    component: '组件展示预览',
  }
  return map[ui.preview.kind] ?? '预览'
})

const payload = computed(() => previewState.payload)

// 分页：按当前页切片
const pagedRows = computed(() => {
  const rows = payload.value?.dataRows ?? []
  const start = (page.value - 1) * pageSize.value
  return rows.slice(start, start + pageSize.value)
})

// 打开预览时，根据类型调用真实后端（数据集预览 / 组件执行预览）
watch(
  () => ui.preview.visible,
  (v) => {
    if (v) {
      tab.value = ui.preview.tab ?? 'data'
      page.value = 1
      const dsId = ui.preview.datasetId
      if (ui.preview.kind === 'dataset' && dsId) loadDatasetPreview(dsId)
      else loadResultPreview()
    }
  },
)
watch(
  () => payload.value?.dataRows,
  () => {
    page.value = 1
  },
)

function onSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}
</script>

<style scoped>
.preview-loading {
  min-height: 200px;
}
.preview-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.log-line {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px solid var(--db-border);
}
.log-time {
  color: var(--db-text-muted);
  margin-right: 8px;
}
.log-level {
  margin-right: 8px;
  font-weight: 600;
}
.log-level.info {
  color: var(--db-accent);
}
.log-level.warn {
  color: var(--db-warning);
}
.log-level.error {
  color: var(--db-danger);
}
</style>
