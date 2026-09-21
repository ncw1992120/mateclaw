<template>
  <el-dialog v-model="ui.python.visible" class="insight-dialog--preview" title="编辑 Python 脚本" width="860px" destroy-on-close :close-on-click-modal="false" aria-label="编辑 Python 脚本">
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
      <pre v-if="mode === 'generated'" class="py-readonly" data-testid="system-code-readonly">{{ state.pythonSystem }}</pre>
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

    <!-- 用户处理区域（可编辑；系统操作不影响此处） -->
    <div class="py-block">
      <div class="py-title">用户处理区域（可编辑）</div>
      <el-input v-model="userCode" type="textarea" :rows="10" class="py-edit" data-testid="user-code" aria-label="用户处理区域" />
    </div>

    <template #footer>
      <el-button @click="ui.python.visible = false">取消</el-button>
      <el-button @click="onPreview">查看数据</el-button>
      <el-button @click="onExec" :loading="executing">执行记录</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useInsight, currentPythonSource } from './useInsight'
import { effectiveSystemCode, fingerprintSystemSource, generateSystemScript, restoreGenerated } from '@/utils/python-script-template'

const { state, savePython, openPreview, runComponentPreview } = useInsight()
const ui = state.ui
const userCode = ref('')
const executing = ref(false)
const showDiff = ref(false)

const mode = computed(() => state.pythonSystemState?.mode ?? 'generated')
const hasUpdate = computed(() => state.pythonSystemState?.hasGeneratedUpdate ?? false)
const candidateCode = computed(() => state.pythonSystemState?.generatedCode ?? '')

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
    }
  },
)

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
  const system = state.pythonSystemState ? effectiveSystemCode(state.pythonSystemState) : state.pythonSystem
  savePython(system, userCode.value)
}
// 查看数据：先持久化 Python 脚本（关闭弹窗），再打开结果集预览。
// 脚本是**管道级**的（作用于多个数据集求最终输出），所以这里看的是结果集、不是某个输入数据集，
// 与数据集卡片上的「查看数据」（打开数据集工作台）层级不同
function onPreview() {
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
.py-edit :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
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
