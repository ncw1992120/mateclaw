<template>
  <el-dialog v-model="ui.python.visible" class="insight-dialog--preview python-script-dialog" title="编辑 Python 脚本" width="min(860px, calc(100vw - 32px))" destroy-on-close :close-on-click-modal="false" aria-label="编辑 Python 脚本">
    <!-- 系统生成区域：generated 只读 / managed 用户接管可编辑 -->
    <div class="py-block">
      <div class="py-title">
        <span class="py-title-left">
          系统生成区域
          <el-tag size="small" :type="mode === 'managed' ? 'warning' : 'info'" data-testid="system-mode-tag">
            {{ mode === 'managed' ? '用户接管' : '系统生成' }}
          </el-tag>
        </span>
        <span class="py-title-actions">
          <el-button
            v-if="hasUpdate"
            size="small"
            type="warning"
            plain
            data-testid="diff-toggle"
            @click="showDiff = !showDiff"
          >候选版本更新</el-button>
          <el-button v-if="mode === 'generated'" size="small" text type="primary" data-testid="regen-btn" @click="regenSystem">重新生成系统区域</el-button>
          <el-button v-if="mode === 'generated'" size="small" text type="warning" data-testid="unlock-btn" @click="unlock">解锁编辑系统区域</el-button>
        </span>
      </div>
      <pre v-if="mode === 'generated'" class="py-readonly py-highlighted" data-testid="system-code-readonly"><code v-html="highlightedSystemCode" /></pre>
      <el-input
        v-else
        v-model="managedCode"
        type="textarea"
        :rows="10"
        class="py-edit"
        data-testid="system-code-managed"
        aria-label="系统代码（用户接管）"
      />

      <!-- 候选版本差异：当前生效 vs 系统候选，并排展示 -->
      <div v-if="hasUpdate && showDiff" class="py-diff" data-testid="system-diff">
        <div class="py-diff-col">
          <div class="py-diff-title">当前生效版本</div>
          <pre class="py-readonly">{{ state.pythonSystem }}</pre>
        </div>
        <div class="py-diff-col">
          <div class="py-diff-title">候选系统版本</div>
          <pre class="py-readonly">{{ candidateCode }}</pre>
        </div>
      </div>
      <div v-if="hasUpdate" class="py-update-actions">
        <span class="py-update-hint">数据集或筛选绑定已变化，系统生成了新版本</span>
        <el-button size="small" data-testid="keep-current" @click="keepCurrent">保留当前版本</el-button>
        <el-button size="small" type="danger" plain data-testid="restore-generated" @click="restore">恢复系统生成</el-button>
      </div>
    </div>

    <!-- 本组件期望输出：脚本返回形状的规范引导 -->
    <div v-if="outputSpec" class="py-block py-output-spec">
      <div class="py-title">
        <span class="py-title-left">本组件期望输出（{{ componentLabel(outputSpec.componentType) }}）</span>
      </div>
      <p class="py-spec-desc">{{ outputSpec.description }}</p>
      <pre class="py-readonly py-spec-example" data-testid="output-spec-example"><code>{{ outputSpec.example }}</code></pre>
    </div>

    <div v-if="finalQueryConfig" class="py-block py-output-spec" data-testid="final-result-query-config">
      <div class="py-title">最终结果查询配置</div>
      <p class="py-spec-desc">作用于 Python 输出之后；字段候选来自最近一次通过组件契约校验的结果 Schema。</p>
      <div class="py-query-fields">
        <el-tag v-for="field in finalQueryConfig.displayFields" :key="field.field" size="small">{{ field.title }}（{{ field.field }}）</el-tag>
      </div>
      <p v-if="!finalQueryConfig.displayFields.length" class="py-spec-desc">尚未生成最终结果，执行一次脚本后系统会生成字段候选。</p>
    </div>

    <!-- 用户处理区域（可编辑；系统操作不影响此处） -->
    <div class="py-block">
      <div class="py-title">用户处理区域（可编辑）</div>
      <div class="py-editor-shell" data-testid="python-editor">
        <pre ref="userHighlightRef" class="py-highlight" aria-hidden="true"><code v-html="highlightedUserCode" /></pre>
        <el-input ref="userEditorRef" v-model="userCode" type="textarea" :rows="10" class="py-edit py-input-overlay" data-testid="user-code" aria-label="用户处理区域" />
      </div>
    </div>

    <div class="py-block py-query-config" data-testid="python-query-config">
      <div class="py-title">
        <span class="py-title-left">
          查询配置
          <el-tag size="small" :type="queryConfigured ? 'success' : 'warning'" data-testid="query-config-status">
            {{ queryConfigured ? '已配置' : '未配置' }}
          </el-tag>
        </span>
      </div>
    </div>

    <template #footer>
      <el-button type="primary" plain data-testid="footer-query-config" @click="openQueryConfig">查询配置</el-button>
      <el-button data-testid="view-python-result" @click="onPreview">查看数据</el-button>
      <el-button @click="onExec" :loading="executing">执行记录</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
  <PythonQueryConfigDialog
    v-model="showQueryConfig"
    :config="queryConfigDraft ?? createEmptyFinalResultQueryConfig()"
    :field-catalog="queryConfigFieldCatalog"
    @save="saveQueryConfig"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useInsight, currentPythonSource } from './useInsight'
import { effectiveSystemCode, fingerprintSystemSource, generateSystemScript, restoreGenerated } from '@/utils/python-script-template'
import type { FinalResultQueryConfig, QueryDisplayField } from '@/types'
import { isFinalResultQueryConfigured } from '@/utils/final-result-query'
import { resolveOutputSpec, componentLabel } from '@/utils/component-output-spec'
import { highlightPython } from '@/utils/python-syntax'
import PythonQueryConfigDialog from './PythonQueryConfigDialog.vue'

const { state, savePython, openPreview, runComponentPreview } = useInsight()
const ui = state.ui
const userCode = ref('')
const executing = ref(false)
const showDiff = ref(false)
const userEditorRef = ref<unknown>(null)
const userHighlightRef = ref<HTMLElement | null>(null)

const highlightedSystemCode = computed(() => highlightPython(state.pythonSystem || ''))
const highlightedUserCode = computed(() => highlightPython(userCode.value))

const mode = computed(() => state.pythonSystemState?.mode ?? 'generated')
const hasUpdate = computed(() => state.pythonSystemState?.hasGeneratedUpdate ?? false)
const candidateCode = computed(() => state.pythonSystemState?.generatedCode ?? '')

/** 当前编辑的卡片组件类型 → 期望输出规范（编辑器引导用；上下文仅含类型，不含图表子类型） */
const outputSpec = computed(() => {
  const card = state.cards.find((item) => item.id === state.activeCardId)
  if (!card) return null
  return resolveOutputSpec(card.type)
})
const finalQueryConfig = computed(() => state.finalResultQueryConfig)
const queryConfigured = computed(() => isFinalResultQueryConfigured(finalQueryConfig.value))
const showQueryConfig = ref(false)
const queryConfigDraft = ref<FinalResultQueryConfig | null>(null)

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

const queryConfigFieldCatalog = computed<QueryDisplayField[]>(() => {
  const fields = new Map<string, QueryDisplayField>()
  finalQueryConfig.value?.displayFields.forEach((field) => fields.set(field.field, { ...field }))
  finalQueryConfig.value?.filterFields.forEach((field) => {
    if (!fields.has(field.field)) fields.set(field.field, { field: field.field, title: field.title, role: field.dataType === 'number' ? 'measure' : 'dimension', dataType: field.dataType })
  })
  return [...fields.values()]
})


/** managed 模式下系统代码可编辑，直接绑定到接管副本 */
const managedCode = computed({
  get: () => (state.pythonSystemState?.mode === 'managed' ? state.pythonSystemState.managedCode ?? '' : ''),
  set: (value: string) => {
    if (state.pythonSystemState?.mode === 'managed') state.pythonSystemState.managedCode = value
  },
})

watch(
  () => ui.python.visible,
  (v) => {
    if (v) {
      userCode.value = state.pythonUser
      showDiff.value = false
      void nextTick(bindEditorScroll)
    }
  },
)

function findUserEditor(): HTMLTextAreaElement | null {
  const root = userEditorRef.value as { $el?: HTMLElement } | HTMLElement | null
  if (!root) return null
  if (root instanceof HTMLTextAreaElement) return root
  return root.$el?.querySelector('textarea') ?? root.querySelector?.('textarea') ?? null
}

function syncEditorScroll(): void {
  const editor = findUserEditor()
  const highlight = userHighlightRef.value
  if (!editor || !highlight) return
  highlight.scrollTop = editor.scrollTop
  highlight.scrollLeft = editor.scrollLeft
}

function bindEditorScroll(): void {
  const editor = findUserEditor()
  if (!editor) return
  editor.removeEventListener('scroll', syncEditorScroll)
  editor.addEventListener('scroll', syncEditorScroll)
  syncEditorScroll()
}

onBeforeUnmount(() => findUserEditor()?.removeEventListener('scroll', syncEditorScroll))

// 重新生成系统区域（generated 模式；基于真实数据集 + 筛选器绑定），不覆盖用户处理区域
function regenSystem() {
  if (!state.pythonSystemState) return
  const source = currentPythonSource()
  state.pythonSystemState = {
    ...state.pythonSystemState,
    generatedCode: generateSystemScript(source),
    generatedFingerprint: fingerprintSystemSource(source),
    hasGeneratedUpdate: false,
  }
  state.pythonSystem = effectiveSystemCode(state.pythonSystemState)
  ElMessage.success('已重新生成系统区域（用户处理区域保持不变）')
}

// 解锁编辑系统区域：进入 managed 模式，接管副本从当前生效代码复制而来
function unlock() {
  ElMessageBox.confirm(
    '解锁后系统区域进入用户接管模式：数据集或筛选绑定变化不再自动更新系统代码，仅提示候选版本。确定继续？',
    '解锁编辑系统区域',
    { type: 'warning', confirmButtonText: '解锁', cancelButtonText: '取消' },
  )
    .then(() => {
      if (!state.pythonSystemState) return
      state.pythonSystemState = {
        ...state.pythonSystemState,
        mode: 'managed',
        managedCode: effectiveSystemCode(state.pythonSystemState),
        hasGeneratedUpdate: false,
      }
      state.pythonSystem = effectiveSystemCode(state.pythonSystemState)
      ElMessage.success('已进入用户接管模式')
    })
    .catch(() => {})
}

// 保留当前版本：确认候选指纹（提示消失），接管代码保持不变
function keepCurrent() {
  if (!state.pythonSystemState) return
  state.pythonSystemState = {
    ...state.pythonSystemState,
    generatedFingerprint: fingerprintSystemSource(currentPythonSource()),
    hasGeneratedUpdate: false,
  }
  showDiff.value = false
  ElMessage.success('已保留当前版本（系统新版本不再提示）')
}

// 恢复系统生成：丢弃接管代码，使用候选系统版本；用户处理区域保留
function restore() {
  ElMessageBox.confirm(
    '恢复后系统区域将被最新系统生成版本覆盖，用户处理区域保留。确定继续？',
    '恢复系统生成',
    { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' },
  )
    .then(() => {
      if (!state.pythonSystemState) return
      state.pythonSystemState = restoreGenerated(state.pythonSystemState)
      state.pythonSystem = effectiveSystemCode(state.pythonSystemState)
      ElMessage.success('已恢复系统生成版本')
    })
    .catch(() => {})
}

function save() {
  if (!queryConfigured.value) {
    ElMessage.warning('请先编辑并确认查询配置，再保存 Python 脚本')
    showQueryConfig.value = Boolean(finalQueryConfig.value)
    return
  }
  const system = state.pythonSystemState ? effectiveSystemCode(state.pythonSystemState) : state.pythonSystem
  savePython(system, userCode.value)
}

function openQueryConfig() {
  queryConfigDraft.value = JSON.parse(JSON.stringify(finalQueryConfig.value ?? createEmptyFinalResultQueryConfig())) as FinalResultQueryConfig
  showQueryConfig.value = true
}

function saveQueryConfig(config: FinalResultQueryConfig) {
  state.finalResultQueryConfig = { ...config, confirmed: true }
  showQueryConfig.value = false
  ElMessage.success('查询配置已保存')
}
// 查看数据：先持久化 Python 脚本（关闭弹窗），再打开最终结果集预览。
// 脚本是**管道级**的（作用于多个数据集求最终输出），所以这里看的是结果集、不是某个输入数据集，
// 与数据集卡片上的「查看数据」（打开单个输入数据集）层级不同。
function onPreview() {
  if (!queryConfigured.value) {
    ElMessage.warning('请先编辑并确认查询配置，再查看 Python 最终结果数据')
    showQueryConfig.value = Boolean(finalQueryConfig.value)
    return
  }
  save()
  openPreview('result')
}
async function onExec() {
  executing.value = true
  try {
    const { ok, message } = await runComponentPreview()
    if (ok) ElMessage.success(`已提交执行，executionId=${message}`)
    else ElMessage.error(message || '执行提交失败')
  } finally {
    executing.value = false
  }
}
</script>

<style scoped>
.py-block {
  margin-bottom: 14px;
}

.py-query-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.py-query-config {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}
.py-query-config-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.py-query-config-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.py-query-config-group :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.py-query-config-group :deep(.el-checkbox) {
  margin-right: 0;
}
.py-config-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.py-query-config-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.py-query-config-line :deep(.el-checkbox) {
  margin-right: 4px;
}
.py-query-config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:global(.python-script-dialog.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 40px);
}

:global(.python-script-dialog .el-dialog__header),
:global(.python-script-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.python-script-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

:global(.python-script-dialog .el-dialog__footer) {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  position: sticky;
  bottom: 0;
  z-index: 2;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

:global(.python-script-dialog .el-dialog__footer .el-button) {
  margin: 0;
}
.py-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.py-title-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.py-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.py-readonly {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  white-space: pre-wrap;
  margin: 0;
  max-height: 200px;
  overflow: auto;
  color: var(--el-text-color-secondary);
}
.py-readonly code,
.py-highlight code {
  font-family: inherit;
}
.py-editor-shell {
  position: relative;
  min-height: 214px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  background: var(--el-fill-color-blank);
}
.py-highlight {
  position: absolute;
  inset: 0;
  z-index: 0;
  box-sizing: border-box;
  margin: 0;
  padding: 8px 11px;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font: 13px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  overflow-wrap: break-word;
  pointer-events: none;
}
.py-input-overlay {
  position: relative;
  z-index: 1;
}
.py-input-overlay :deep(.el-textarea__inner) {
  box-sizing: border-box;
  min-height: 214px !important;
  padding: 8px 11px;
  border: 0;
  background: transparent;
  color: transparent;
  caret-color: var(--el-color-primary);
  -webkit-text-fill-color: transparent;
  font: 13px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  resize: vertical;
}
.py-input-overlay :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}
.py-highlight :deep(.hljs-keyword),
.py-highlight :deep(.hljs-built_in),
.py-highlight :deep(.hljs-title.function_),
.py-readonly :deep(.hljs-keyword),
.py-readonly :deep(.hljs-built_in),
.py-readonly :deep(.hljs-title.function_) {
  color: var(--el-color-primary);
  font-weight: 600;
}
.py-highlight :deep(.hljs-string),
.py-readonly :deep(.hljs-string) {
  color: var(--el-color-success);
}
.py-highlight :deep(.hljs-comment),
.py-readonly :deep(.hljs-comment) {
  color: var(--el-text-color-secondary);
}
.py-edit :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}
.py-output-spec {
  border: 1px dashed var(--el-color-success);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--el-color-success-light-9, rgba(34, 197, 94, 0.06));
}
.py-spec-desc {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
.py-spec-example {
  max-height: 120px;
  color: var(--el-text-color-primary);
}
.py-diff {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
.py-diff-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--el-text-color-regular);
}
.py-update-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.py-update-hint {
  font-size: 12px;
  color: var(--el-color-warning);
  flex: 1;
}
</style>
