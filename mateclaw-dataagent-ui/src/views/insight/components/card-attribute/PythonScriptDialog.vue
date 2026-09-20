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
      <el-input v-model="userCode" type="textarea" :rows="10" class="py-edit" />
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
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useInsight, buildPythonSystemRegion } from './useInsight'

const { state, savePython, openPreview, runComponentPreview } = useInsight()
const ui = state.ui
const userCode = ref('')
const executing = ref(false)

watch(
  () => ui.python.visible,
  (v) => {
    if (v) userCode.value = state.pythonUser
  },
)

// 重新生成系统区域（基于真实数据集 + 筛选器绑定），不覆盖用户处理区域
function regenSystem() {
  state.pythonSystem = buildPythonSystemRegion()
  ElMessage.success('已重新生成系统区域（用户处理区域保持不变）')
}
function save() {
  // 保存时以最新系统区域 + 用户代码组合为完整脚本
  savePython(buildPythonSystemRegion(), userCode.value)
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
