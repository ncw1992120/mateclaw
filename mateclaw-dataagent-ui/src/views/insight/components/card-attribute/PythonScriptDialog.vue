<template>
  <el-dialog v-model="ui.python.visible" title="编辑 Python 脚本" width="760px" :close-on-click-modal="false">
    <!-- 系统生成区域（只读） -->
    <div class="py-block">
      <div class="py-title">
        系统生成区域（只读）
        <el-button size="small" text type="primary" @click="regenSystem">重新生成系统区域</el-button>
      </div>
      <pre class="py-readonly">{{ state.pythonSystem }}</pre>
    </div>

    <!-- 用户处理区域（可编辑） -->
    <div class="py-block">
      <div class="py-title">用户处理区域（可编辑）</div>
      <!-- [MOCK] 此处用 textarea 占位，后续可替换为 Monaco 编辑器 -->
      <el-input v-model="userCode" type="textarea" :rows="10" class="py-edit" />
    </div>

    <template #footer>
      <el-button @click="ui.python.visible = false">取消</el-button>
      <el-button @click="onPreview">筛选预览</el-button>
      <el-button @click="onExec">执行记录</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight, MOCK_PYTHON_SYSTEM } from './useInsight'

const { state, savePython, openInputFilter } = useInsight()
const ui = state.ui
const userCode = ref('')

watch(
  () => ui.python.visible,
  (v) => {
    if (v) userCode.value = state.pythonUser
  },
)

// [MOCK] 修改筛选器绑定后重新生成系统区域，且不覆盖用户处理区域
function regenSystem() {
  state.pythonSystem = MOCK_PYTHON_SYSTEM
  ElMessage.success('已重新生成系统区域（用户处理区域保持不变）')
}
function save() {
  savePython(state.pythonSystem, userCode.value)
}
// 筛选预览：先持久化 Python 脚本（关闭 Python 弹窗），再弹出「输入筛选」弹窗，
// 保存后自动打开「预处理结果预览」
function onPreview() {
  save()
  openInputFilter(undefined, { previewKind: 'result' })
}
function onExec() {
  ElMessage.info('[MOCK] Python 执行记录：Join 耗时 12ms，输入 4 行，输出 4 行')
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
</style>
