<template>
  <div class="attr-panel">
    <!-- 固定标题栏（不随内容滚动） -->
    <div class="panel-header">属性配置</div>

    <!-- 可滚动内容 -->
    <div class="panel-body">
      <!-- 1. 组件标题 -->
      <div class="field">
        <label class="field-label">组件标题</label>
        <el-input v-model="activeCard.title" placeholder="请输入组件标题" />
      </div>

      <!-- 2/3. 多指标模式 / 多TAB模式（仅 KPI/指标卡显示，本轮只实现 KPI 卡） -->
      <template v-if="isKpiCard">
        <div class="field row">
          <span class="field-label">多指标模式</span>
          <el-switch v-model="activeCard.multiMetric" />
        </div>
        <div class="field row">
          <span class="field-label">多TAB模式</span>
          <el-switch v-model="activeCard.multiTab" />
        </div>
      </template>
      <div v-else class="field note">当前为「{{ typeLabel(activeCard.type) }}」，不显示「多指标模式 / 多TAB模式」。</div>

      <!-- 4. 数据集配置 -->
      <div class="section">
        <div class="section-head">
          <span class="section-title">数据集配置</span>
          <!-- 0 个数据集：添加入口在标题行右侧（文档 11.3） -->
          <el-button v-if="datasetCount === 0" size="small" type="primary" @click="openDataSourceTree">
            + 添加数据集
          </el-button>
        </div>

        <!-- 空态 -->
        <div v-if="datasetCount === 0" class="empty">暂未配置数据集输入</div>

        <!-- 数据集表单列表 -->
        <div v-else class="dataset-list">
          <DatasetCard v-for="ds in state.datasets" :key="ds.id" :dataset="ds" />
          <!-- 已有数据集：添加入口移动到列表底部（不重复显示） -->
          <el-button class="add-bottom" @click="openDataSourceTree">+ 添加数据集</el-button>
        </div>
      </div>

      <!-- 5. 筛选器绑定（绑定到当前选中的 KPI/指标卡，无独立组件级时间筛选） -->
      <div class="section">
        <div class="section-head">
          <span class="section-title">筛选器绑定</span>
        </div>
        <div class="fb-box">
          <template v-if="state.filterBindings && state.filterBindings.length">
            <div class="fb-tags">
              <el-tag v-for="(b, i) in state.filterBindings" :key="i" size="small" type="info" class="fb-tag">
                {{ b.filterName }}
              </el-tag>
            </div>
            <el-button size="small" text type="primary" @click="openFilterBinding">查看 / 修改</el-button>
          </template>
          <el-button v-else size="small" @click="openFilterBinding">绑定筛选器</el-button>
        </div>
      </div>

      <!-- 6. Python 预处理（位于所有数据集与筛选器绑定之后） -->
      <div class="section">
        <div class="section-head">
          <span class="section-title">Python 预处理</span>
          <el-button size="small" text type="primary" @click="openPreview('result')">筛选预览</el-button>
        </div>

        <!-- 未配置：提供入口（1 个数据集时可选；2+ 时用户处理区必填） -->
        <div v-if="!state.hasPython" class="python-empty">
          <el-button size="small" @click="openPython">编辑 Python 脚本</el-button>
          <span v-if="pythonRequired" class="req-tip">（多数据集时用户处理区域为必填）</span>
        </div>

        <!-- 已配置：系统生成区(只读) + 用户处理区(可编辑) -->
        <div v-else class="python-box">
          <div class="py-sub">系统生成区域（只读）</div>
          <pre class="py-system">{{ state.pythonSystem }}</pre>
          <div class="py-sub">用户处理区域（可编辑）</div>
          <el-input
            v-model="state.pythonUser"
            type="textarea"
            :rows="6"
            class="py-user"
            placeholder="编写 Join、合并、计算和业务规则"
          />
          <div class="py-actions">
            <el-button size="small" @click="openPython">展开编辑</el-button>
            <el-button size="small" type="danger" text @click="removePython">移除 Python 脚本</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useInsight } from '../useInsight'
import DatasetCard from './DatasetCard.vue'

const { state, activeCard, isKpiCard, datasetCount, pythonRequired, openDataSourceTree, openFilterBinding, openPython, openPreview, removePython } = useInsight()

function typeLabel(t: string) {
  return t === 'kpi' ? 'KPI/指标卡' : t === 'table' ? '表格卡' : '图表卡'
}
</script>

<style scoped>
.attr-panel {
  width: 420px;
  flex-shrink: 0;
  border-left: 1px solid var(--db-border);
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--db-card);
}
.panel-header {
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-weight: 700;
  color: var(--db-text);
  border-bottom: 1px solid var(--db-border);
}
.panel-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.field {
  margin-bottom: 14px;
}
.field-label {
  display: block;
  font-size: 13px;
  color: var(--db-text-secondary);
  margin-bottom: 6px;
}
.field.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.field.row .field-label {
  margin-bottom: 0;
}
.field.note {
  font-size: 12px;
  color: var(--db-text-muted);
}
.section {
  border-top: 1px solid var(--db-border);
  padding-top: 14px;
  margin-top: 4px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.section-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--db-text);
}
.empty {
  color: var(--db-text-muted);
  font-size: 13px;
  padding: 16px;
  text-align: center;
  border: 1px dashed var(--db-border);
  border-radius: var(--radius-md);
}
.dataset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.add-bottom {
  align-self: flex-start;
}
.fb-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 32px;
}
.fb-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}
.fb-tag {
  margin: 0;
}
.fb-name {
  font-size: 13px;
  color: var(--db-text);
}
.python-empty {
  display: flex;
  align-items: center;
  gap: 8px;
}
.req-tip {
  font-size: 12px;
  color: var(--db-warning);
}
.python-box {
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  padding: 10px;
  background: var(--db-muted);
}
.py-sub {
  font-size: 12px;
  color: var(--db-text-muted);
  margin: 6px 0 4px;
}
.py-system {
  background: var(--code-bg);
  border-radius: var(--radius-sm);
  padding: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  margin: 0;
  max-height: 160px;
  overflow: auto;
  color: var(--theme-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.py-user {
  margin-bottom: 8px;
}
.py-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
