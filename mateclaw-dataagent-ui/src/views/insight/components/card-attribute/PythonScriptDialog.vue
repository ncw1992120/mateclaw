<template>
  <el-dialog
    v-if="!ui.python.queryConfigOnly"
    v-model="ui.python.visible"
    class="insight-dialog--preview python-script-dialog"
    title="展开编辑 · Python"
    width="min(1040px, calc(100vw - 32px))"
    top="4vh"
    destroy-on-close
    :close-on-click-modal="false"
    aria-label="展开编辑 Python 脚本"
  >
    <div class="editor-intro">
      <div>
        <div class="editor-kicker">PYTHON WORKSPACE</div>
        <h3>脚本编辑</h3>
      </div>
      <el-tag size="small" :type="queryConfigured ? 'success' : 'warning'" data-testid="query-config-status">
        {{ queryConfigured ? '查询配置已完成' : '请先完成查询配置' }}
      </el-tag>
    </div>

    <div class="py-editor-shell" data-testid="python-editor">
      <pre ref="highlightRef" class="py-highlight" aria-hidden="true"><code v-html="highlightedCode" /></pre>
      <el-input
        ref="editorRef"
        v-model="editorCode"
        type="textarea"
        :rows="24"
        class="py-edit py-input-overlay"
        data-testid="python-editor-code"
        aria-label="Python 脚本"
        spellcheck="false"
        :disabled="editorLoading"
      />
    </div>

    <p v-if="editorLoading" class="editor-hint" data-testid="python-sample-loading">正在获取上游数据集的查询结果，用于生成 JSON 造数样例…</p>
    <ul v-else-if="editorSampleWarnings.length" class="editor-warnings" data-testid="python-sample-warnings">
      <li v-for="warning in editorSampleWarnings" :key="warning">{{ warning }}</li>
    </ul>
    <p class="editor-hint">取消注释造数示例后，对应输入会跳过真实数据读取；查询参数说明仅供参考，查看数据时由查询配置应用。</p>

    <template #footer>
      <el-button data-testid="python-editor-cancel" @click="ui.python.visible = false">取消</el-button>
      <el-button type="primary" data-testid="python-editor-save" :disabled="editorLoading" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <PythonQueryConfigDialog
    v-model="showQueryConfig"
    :config="queryConfigDraft ?? createEmptyFinalResultQueryConfig()"
    :filter-options="state.filterCatalog"
    @save="saveQueryConfig"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight, currentPythonSource } from './useInsight'
import { buildPythonEditorDocument, parsePythonEditorDocument } from '@/utils/python-script-template'
import type { FinalResultQueryConfig, QueryDisplayField } from '@/types'
import { isFinalResultQueryConfigured, sortDisplayFieldsDimensionsFirst } from '@/utils/final-result-query'
import { highlightPython } from '@/utils/python-syntax'
import { fetchDatasetSampleRows } from '../dataset-sample-query'
import PythonQueryConfigDialog from './PythonQueryConfigDialog.vue'

const { state, savePython } = useInsight()
const ui = state.ui
const editorCode = ref('')
const editorLoading = ref(false)
const editorSampleWarnings = ref<string[]>([])
const editorRef = ref<unknown>(null)
const highlightRef = ref<HTMLElement | null>(null)
const showQueryConfig = ref(false)
const queryConfigDraft = ref<FinalResultQueryConfig | null>(null)
const finalQueryConfig = computed(() => state.finalResultQueryConfig)
const queryConfigured = computed(() => isFinalResultQueryConfigured(finalQueryConfig.value))
const highlightedCode = computed(() => highlightPython(editorCode.value))

const datasetQueryDisplayFields = computed<QueryDisplayField[]>(() => {
  const fields = new Map<string, QueryDisplayField>()
  state.datasets.forEach((dataset) => {
    dataset.queryConfig?.displayFields.forEach((field) => {
      const name = field.field.trim()
      if (name && !fields.has(name)) fields.set(name, { ...field, field: name })
    })
  })
  return sortDisplayFieldsDimensionsFirst([...fields.values()])
})

function createEmptyFinalResultQueryConfig(): FinalResultQueryConfig {
  return {
    schemaFingerprint: '',
    confirmed: false,
    displayFields: [],
    filterFields: [],
    sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
    paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
  }
}

let editorLoadId = 0

async function loadEditorDocument(): Promise<void> {
  const loadId = ++editorLoadId
  editorLoading.value = true
  editorSampleWarnings.value = []
  editorCode.value = '# 正在获取上游数据集的查询结果…'

  const samples: Record<string, Record<string, unknown>[]> = {}
  const warnings: string[] = []
  await Promise.all(state.datasets.map(async (dataset) => {
    try {
      const result = await fetchDatasetSampleRows(dataset, state.filterCatalog)
      samples[dataset.alias] = result.rows
      if (!result.rows.length) warnings.push(`上游数据集「${dataset.alias}」查询成功，但结果为 0 行；未生成 JSON 造数。`)
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      warnings.push(`上游数据集「${dataset.alias}」查询失败：${reason}。真实数据读取代码仍保留。`)
    }
  }))

  if (loadId !== editorLoadId || !ui.python.visible) return
  editorSampleWarnings.value = warnings
  editorCode.value = buildPythonEditorDocument(currentPythonSource(), state.pythonUser, samples)
  editorLoading.value = false
  void nextTick(bindEditorScroll)
}

watch(() => ui.python.visible, (visible) => {
  if (visible && !ui.python.queryConfigOnly) {
    void loadEditorDocument()
  } else {
    editorLoadId += 1
    editorLoading.value = false
  }
})

watch(() => ui.python.queryConfigOnly, (queryConfigOnly) => {
  if (queryConfigOnly && ui.python.visible) openQueryConfig()
}, { flush: 'post' })

watch(showQueryConfig, (visible) => {
  if (!visible && ui.python.queryConfigOnly) {
    ui.python.queryConfigOnly = false
    ui.python.visible = false
  }
})

function findEditor(): HTMLTextAreaElement | null {
  const root = editorRef.value as { $el?: HTMLElement } | HTMLElement | null
  if (!root) return null
  if (root instanceof HTMLTextAreaElement) return root
  return root.$el?.querySelector('textarea') ?? root.querySelector?.('textarea') ?? null
}

function syncEditorScroll(): void {
  const editor = findEditor()
  if (!editor || !highlightRef.value) return
  highlightRef.value.scrollTop = editor.scrollTop
  highlightRef.value.scrollLeft = editor.scrollLeft
}

function bindEditorScroll(): void {
  const editor = findEditor()
  if (!editor) return
  editor.removeEventListener('scroll', syncEditorScroll)
  editor.addEventListener('scroll', syncEditorScroll)
  syncEditorScroll()
}

onBeforeUnmount(() => findEditor()?.removeEventListener('scroll', syncEditorScroll))

function save(): void {
  if (!queryConfigured.value) {
    ElMessage.warning('请先完成查询配置，再展开编辑 Python 脚本')
    openQueryConfig()
    return
  }
  const { systemCode, userCode } = parsePythonEditorDocument(editorCode.value)
  savePython(systemCode, userCode)
}

function openQueryConfig(): void {
  const config = JSON.parse(JSON.stringify(finalQueryConfig.value ?? createEmptyFinalResultQueryConfig())) as FinalResultQueryConfig
  if (!finalQueryConfig.value?.confirmed && datasetQueryDisplayFields.value.length) {
    config.displayFields = datasetQueryDisplayFields.value.map((field) => ({ ...field }))
  }
  queryConfigDraft.value = config
  showQueryConfig.value = true
}

function saveQueryConfig(config: FinalResultQueryConfig): void {
  state.finalResultQueryConfig = { ...config, confirmed: true }
  showQueryConfig.value = false
  ElMessage.success('查询配置已保存')
}
</script>

<style scoped>
.editor-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 0 2px 12px;
  border-bottom: 1px solid var(--db-border);
}
.editor-kicker {
  margin-bottom: 4px;
  color: var(--db-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .14em;
}
.editor-intro h3 {
  margin: 0;
  color: var(--db-text);
  font-size: 18px;
  line-height: 1.35;
}
.py-editor-shell {
  position: relative;
  min-height: min(68vh, 640px);
  overflow: hidden;
  border: 1px solid var(--db-border-strong);
  border-radius: var(--radius-md);
  background: #fbfcfe;
}
.py-highlight,
.py-input-overlay :deep(textarea) {
  box-sizing: border-box;
  width: 100%;
  min-height: min(68vh, 640px);
  margin: 0;
  padding: 18px 20px;
  border: 0;
  font: 13px/1.7 var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  tab-size: 4;
  white-space: pre;
  overflow: auto;
}
.py-highlight {
  position: absolute;
  inset: 0;
  color: var(--db-text);
  pointer-events: none;
}
.py-input-overlay {
  position: relative;
  z-index: 1;
  height: 100%;
  opacity: 1;
}
.py-input-overlay :deep(textarea) {
  resize: vertical;
  color: transparent;
  caret-color: var(--db-text);
  background: transparent;
  -webkit-text-fill-color: transparent;
}
.editor-hint {
  margin: 10px 2px 0;
  color: var(--db-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
.editor-warnings {
  margin: 10px 2px 0;
  padding-left: 20px;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.6;
}
:global(.python-script-dialog.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 32px);
}
:global(.python-script-dialog .el-dialog__header),
:global(.python-script-dialog .el-dialog__footer) { flex: 0 0 auto; }
:global(.python-script-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}
:global(.python-script-dialog .el-dialog__footer) {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  position: sticky;
  bottom: 0;
  z-index: 2;
  border-top: 1px solid var(--db-border);
  background: var(--db-card);
}
:global(.python-script-dialog .el-dialog__footer .el-button) { margin: 0; }
</style>
