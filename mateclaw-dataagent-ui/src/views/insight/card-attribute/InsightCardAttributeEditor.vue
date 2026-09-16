<template>
  <div class="insight-root">
    <!-- 顶部工具栏 -->
    <div class="topbar">
      <div class="topbar-left">
        <button type="button" class="back-btn" title="返回" aria-label="返回" @click="onBack">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <el-icon class="logo"><DataAnalysis /></el-icon>
        <span class="tb-title">洞察 · 仪表盘</span>
        <span class="tb-crumb">/ 卡片属性配置</span>
      </div>
      <div class="topbar-right">
        <!-- 后端对接状态（mateclaw-dataagent） -->
        <span class="tb-status" :class="state.backend.online ? 'ok' : 'off'">
          <i class="dot" />
          {{ state.backend.online ? `已对接后端 #${state.backend.dashboardId}` : state.backend.loading ? '对接中…' : '未对接后端' }}
        </span>
        <!-- 组件展示预览 -->
        <el-button size="small" @click="openPreview('component')">组件展示预览</el-button>
        <!-- 整盘预览（仅对接后端后可跳转） -->
        <el-button size="small" :disabled="!state.backend.online" @click="onPreviewDashboard">预览仪表盘</el-button>
        <!-- 保存：写入后端仪表盘 Schema（真实持久化） -->
        <el-button size="small" type="primary" :loading="state.backend.loading" @click="onSave">保存</el-button>
      </div>
    </div>

    <!-- 左侧画布 + 右侧属性面板 -->
    <div class="insight-body">
      <CardCanvas />
      <AttributePanel />
    </div>

    <!-- 各类弹窗（内部各自绑定 store.ui.*.visible） -->
    <DataSourceTreeDialog />
    <JdbcSqlDialog />
    <AloudataDialog />
    <ApiConfigDialog />
    <FileConfigDialog />
    <FieldMappingDialog />
    <InputFilterDialog />
    <FilterBindingDialog />
    <PythonScriptDialog />
    <PreviewDialog />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import CardCanvas from './components/CardCanvas.vue'
import AttributePanel from './components/AttributePanel.vue'
import DataSourceTreeDialog from './components/DataSourceTreeDialog.vue'
import JdbcSqlDialog from './components/dataset/JdbcSqlDialog.vue'
import AloudataDialog from './components/dataset/AloudataDialog.vue'
import ApiConfigDialog from './components/dataset/ApiConfigDialog.vue'
import FileConfigDialog from './components/dataset/FileConfigDialog.vue'
import FieldMappingDialog from './components/FieldMappingDialog.vue'
import InputFilterDialog from './components/InputFilterDialog.vue'
import FilterBindingDialog from './components/FilterBindingDialog.vue'
import PythonScriptDialog from './components/PythonScriptDialog.vue'
import PreviewDialog from './components/PreviewDialog.vue'
import { useInsight } from './useInsight'

// 真实入口场景由 DashboardListView 传入 dashboardId；独立预览路由不传则回退原型仪表盘
const props = defineProps<{ dashboardId?: string }>()
const emit = defineEmits<{ back: []; preview: [dashboardId: string] }>()

const { state, openPreview, bootstrapDashboard, saveDashboard } = useInsight()

// 打开时自动对接后端：传入 dashboardId 加载指定仪表盘，不传回退原型仪表盘并回显已保存配置
onMounted(async () => {
  const ok = await bootstrapDashboard(props.dashboardId)
  if (ok) {
    ElMessage.success(`已对接后端仪表盘 #${state.backend.dashboardId}`)
  } else if (state.backend.lastError) {
    ElMessage.warning(state.backend.lastError)
  }
})

// 保存当前卡片配置到后端仪表盘 Schema（真实写入后端）
async function onSave() {
  const ok = await saveDashboard()
  if (ok) ElMessage.success('已保存到后端仪表盘 Schema')
  else ElMessage.error(state.backend.lastError || '保存失败')
}

// 返回仪表盘列表（由 DashboardListView 处理）
function onBack() {
  emit('back')
}

// 跳转到整盘预览（DashboardListView 的 preview 模式）
function onPreviewDashboard() {
  emit('preview', state.backend.dashboardId)
}
</script>

<style scoped>
.insight-root {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
}
.topbar {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--db-border);
  background: var(--db-card);
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--db-border);
  background: var(--db-card);
  color: var(--db-text-secondary);
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}
.back-btn:hover {
  color: var(--db-accent);
  border-color: var(--db-accent);
}
.logo {
  font-size: 20px;
  color: var(--db-accent);
}
.tb-title {
  font-weight: 700;
  font-size: 16px;
  color: var(--db-text);
  letter-spacing: -0.01em;
}
.tb-crumb {
  color: var(--db-text-muted);
  font-size: 13px;
}
.topbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.tb-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-muted);
}
.tb-status .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--db-text-muted);
}
.tb-status.ok {
  color: var(--db-success, #22c55e);
}
.tb-status.ok .dot {
  background: var(--db-success, #22c55e);
}
.tb-status.off .dot {
  background: var(--db-warning, #f59e0b);
}
.insight-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
</style>
