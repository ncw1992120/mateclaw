<template>
  <div class="attr-panel">
    <!-- 固定标题栏（不随内容滚动） -->
    <div class="panel-header">属性配置</div>

    <!-- 可滚动内容 -->
    <div class="panel-body">
      <!-- 字段契约告警（决策 4/5）：存量引用未识别 / 字段在数据源中已消失，均可关闭 -->
      <el-alert
        v-if="fieldWarnings.length && !warningsDismissed"
        class="field-alert"
        type="warning"
        :closable="true"
        title="字段引用需要确认"
        @close="warningsDismissed = true"
      >
        <ul class="field-alert-list">
          <li v-for="(w, i) in fieldWarnings" :key="i">{{ w }}</li>
        </ul>
      </el-alert>

      <!-- 1. 组件标题 -->
      <div class="field">
        <label class="field-label">组件标题</label>
        <el-input v-model="activeCard.title" placeholder="请输入组件标题" aria-label="组件标题" />
      </div>

      <div class="field">
        <label class="field-label">标题栏样式</label>
        <el-select v-model="activeCard.titleBarStyle" aria-label="标题栏样式" style="width: 100%">
          <el-option value="hidden" label="隐藏标题栏" />
          <el-option value="standard" label="标准卡片" />
          <el-option value="minimal" label="简洁文本" />
          <el-option value="accent" label="强调色" />
          <el-option value="section" label="分组标题" />
        </el-select>
      </div>
      <details class="style-settings">
        <summary class="section-title visual-style-title">样式设置</summary>
        <div class="style-field-grid">
          <div class="field">
            <label class="field-label">边框</label>
            <el-select v-model="activeCard.visualStyle.border!.mode" aria-label="组件边框" style="width: 100%">
          <el-option value="theme" label="跟随主题" />
          <el-option value="visible" label="显示" />
          <el-option value="hidden" label="隐藏" />
            </el-select>
          </div>
          <div class="field">
            <label class="field-label">背景</label>
            <el-select v-model="activeCard.visualStyle.background!.mode" aria-label="组件背景" style="width: 100%">
              <el-option value="theme" label="跟随主题" />
              <el-option value="transparent" label="透明" />
              <el-option value="custom" label="自定义颜色" />
            </el-select>
          </div>
          <template v-if="activeCard.visualStyle.border?.mode === 'visible'">
            <div class="field">
              <label class="field-label">边框颜色</label>
              <el-select v-model="activeCard.visualStyle.border.colorMode" aria-label="组件边框颜色" style="width: 100%">
            <el-option value="theme" label="跟随主题" />
            <el-option value="custom" label="自定义颜色" />
              </el-select>
            </div>
            <div class="field">
              <label class="field-label">边框粗细</label>
              <el-select v-model="activeCard.visualStyle.border.width" aria-label="组件边框粗细" style="width: 100%">
            <el-option :value="1" label="1px" />
            <el-option :value="2" label="2px" />
              </el-select>
            </div>
            <div class="field">
              <label class="field-label">边框样式</label>
              <el-select v-model="activeCard.visualStyle.border.style" aria-label="组件边框样式" style="width: 100%">
            <el-option value="solid" label="实线" />
            <el-option value="dashed" label="虚线" />
              </el-select>
            </div>
            <div v-if="activeCard.visualStyle.border.colorMode === 'custom'" class="field">
              <label class="field-label">自定义边框颜色</label>
              <el-color-picker v-model="activeCard.visualStyle.border.color" aria-label="自定义边框颜色" />
            </div>
          </template>
          <div v-if="activeCard.visualStyle.background?.mode === 'custom'" class="field">
            <label class="field-label">自定义背景色</label>
            <el-color-picker v-model="activeCard.visualStyle.background.color" aria-label="自定义背景色" />
          </div>
          <div class="field">
            <label class="field-label">圆角</label>
            <el-select v-model="activeCard.visualStyle.radius" aria-label="组件圆角" style="width: 100%">
          <el-option :value="0" label="无圆角" />
          <el-option :value="8" label="小圆角" />
          <el-option :value="12" label="标准圆角" />
          <el-option :value="16" label="大圆角" />
            </el-select>
          </div>
          <div class="field">
            <label class="field-label">阴影</label>
            <el-select v-model="activeCard.visualStyle.shadow" aria-label="组件阴影" style="width: 100%">
          <el-option value="none" label="无阴影" />
          <el-option value="subtle" label="轻微阴影" />
          <el-option value="medium" label="标准阴影" />
            </el-select>
          </div>
          <div class="field">
            <label class="field-label">内边距</label>
            <el-select v-model="activeCard.visualStyle.padding" aria-label="组件内边距" style="width: 100%">
          <el-option :value="0" label="紧凑" />
          <el-option :value="8" label="标准" />
          <el-option :value="16" label="宽松" />
            </el-select>
          </div>
        </div>
      </details>
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

      <!-- 5. Python 预处理（位于数据集之后、结果集之前：
           有脚本时结果集由用户处理区产出，面板顺序与数据流保持一致） -->
      <div class="section">
        <div class="section-head">
          <span class="section-title">Python 预处理</span>
          <InlineHelp label="Python 预处理" content="系统区域由平台生成，用户区域用于 Join、合并、计算和业务规则。" />
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

      <!-- 6. 结果集：管道唯一出口，也是卡片唯一的数据来源 —— 就绪后卡片才会显示数据 -->
      <div class="section">
        <div class="section-head">
          <span class="section-title">结果集</span>
          <InlineHelp label="结果集" content="结果集是卡片展示的唯一数据出口；配置变化后会标记为过期。" />
          <span class="rs-status" :class="resultSetStatusClass">
            <span class="rs-dot"></span>{{ resultSetStatusText }}
          </span>
        </div>

        <!-- 未配置数据集：结果集没有输入 -->
        <div v-if="state.resultSet.status === 'empty'" class="empty">添加数据集后自动生成</div>

        <template v-else>
          <div class="rs-box" :class="resultSetStatusClass">
            <div class="rs-line">
              <span class="rs-source">{{ resultSetSourceLabel }}</span>
              <span class="rs-meta">{{ state.resultSet.columns.length }} 字段 · {{ resultSetRowText }}</span>
            </div>
            <div v-if="resultSetTimeText" class="rs-time">{{ resultSetTimeText }}</div>
          </div>

          <el-alert
            v-if="state.resultSet.status === 'failed' && state.resultSet.error"
            class="rs-error"
            type="error"
            :closable="false"
            :title="state.resultSet.error"
          />

          <div class="rs-actions">
            <el-button size="small" :disabled="state.resultSet.status === 'running'" @click="openPreview('result')">
              预览结果集
            </el-button>
            <el-button
              size="small"
              type="primary"
              :loading="state.resultSet.status === 'running'"
              @click="generateResultSet()"
            >
              {{ resultSetActionText }}
            </el-button>
          </div>

          <div v-if="resultSetStale" class="rs-hint">输入已变更，卡片仍在用上一次的数据</div>
          <div v-else-if="!resultSetAuto" class="rs-hint">有 Python 脚本时需手动生成（执行有成本）</div>
        </template>
      </div>

      <!-- 7. 指标配置（仅 KPI 卡：结果集逐列投影的指标分组汇总表单）
           排在 Python 预处理之后：指标由「最终结果集」字段投影而来，而结果集可能由
           Python 用户处理区产生，放最后才符合「先出结果集、再配置指标」的使用顺序。 -->
      <div v-if="isKpiCard" class="section">
        <div class="section-head">
          <span class="section-title">指标配置</span>
          <span v-if="kpiMetricCount" class="metric-count">{{ kpiMetricCount }} 个指标</span>
        </div>
        <el-button size="small" type="primary" :disabled="!resultSetHasOutput" @click="openMetricConfig">配置指标</el-button>
        <div class="metric-hint">
          {{ resultSetHasOutput
            ? '指标由最终结果集字段自动投影生成，可配置展示列名、单位、辅助说明及各字段样式。'
            : '请先生成结果集：指标候选字段以结果集 schema 为准。' }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useInsight } from './useInsight'
import InlineHelp from '../property/InlineHelp.vue'
import DatasetCard from './DatasetCard.vue'
import { resolveFieldLabel } from '@/utils/field-mapping'

const { state, activeCard, isKpiCard, datasetCount, pythonRequired, openDataSourceTree, openPython, openPreview, removePython, openMetricConfig, resultSetStale, resultSetAuto, resultSetSourceLabel, resultSetHasOutput, generateResultSet } = useInsight()

const kpiMetricCount = computed(() => state.kpiMetrics.length)

/* ── 结果集节点：管道唯一出口，也是卡片唯一的数据来源 ── */

const RESULT_SET_STATUS_LABEL: Record<string, string> = {
  empty: '未配置',
  stale: '已过期',
  running: '生成中',
  ready: '就绪',
  failed: '失败',
}

const resultSetStatusText = computed(() => RESULT_SET_STATUS_LABEL[state.resultSet.status] ?? state.resultSet.status)
const resultSetStatusClass = computed(() => `rs-${state.resultSet.status}`)
const resultSetRowText = computed(() =>
  state.resultSet.status === 'running' ? '生成中…' : `${state.resultSet.rowCount} 行`,
)
/** 生成时间 + 耗时；过期态额外标注输入已变更，避免被误认为当前配置的结果 */
const resultSetTimeText = computed(() => {
  const rs = state.resultSet
  if (rs.status === 'running') return '正在执行…'
  if (!rs.generatedAt) return ''
  const time = new Date(rs.generatedAt).toLocaleTimeString('zh-CN', { hour12: false })
  const cost = rs.elapsedMs ? ` · 耗时 ${(rs.elapsedMs / 1000).toFixed(1)}s` : ''
  const stale = rs.status === 'stale' ? '（输入已变更）' : ''
  return `${time} 生成${cost}${stale}`
})
const resultSetActionText = computed(() => (state.resultSet.status === 'ready' ? '重新生成' : '生成结果集'))

/** 告警被用户关闭后不再重复打扰（切换卡片时应重新提示） */
const warningsDismissed = ref(false)
watch(() => state.cards[0]?.id, () => {
  warningsDismissed.value = false
})

/**
 * 字段契约告警（决策 4 / 决策 5）：
 *   - 存量归一后仍未识别的引用：老配置里的名字在字段注册表中找不到，需手动修复；
 *   - 字段在数据源中已消失：注册表保留配置并打 stale 标记，提示重绑（不静默删除用户编辑）。
 */
const fieldWarnings = computed<string[]>(() => {
  const out: string[] = []
  state.datasets.forEach((ds) => {
    ;(ds.unresolvedFields ?? []).forEach((name) => {
      out.push(`数据集 ${ds.alias}：字段引用「${name}」无法识别，请在筛选条件 / 绑定筛选器中重新选择字段`)
    })
    ;(ds.fields ?? []).forEach((f) => {
      if (!f.stale) return
      const label = resolveFieldLabel(ds.fields, f.name)
      out.push(`数据集 ${ds.alias}：字段「${label}」（${f.name}）已不存在，请重新绑定或移除相关配置`)
    })
  })
  return out
})

function typeLabel(t: string) {
  return t === 'kpi' ? 'KPI/指标卡' : t === 'table' ? '表格卡' : '图表卡'
}
</script>

<style scoped>
.attr-panel {
  width: 100%;
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
.field-alert {
  margin-bottom: 14px;
}
.field-alert-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.7;
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
.style-settings {
  margin: 4px 0 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--db-bg) 50%, transparent);
}
.style-settings > .style-section-title {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  cursor: pointer;
  list-style: none;
}
.style-settings > .style-section-title::-webkit-details-marker { display: none; }
.style-settings > .style-section-title::after { content: '⌄'; color: var(--db-text-muted); }
.style-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
  padding: 0 12px 2px;
}
.style-field-grid .field { min-width: 0; }
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
.metric-count {
  font-size: 12px;
  color: var(--db-text-muted);
}
.metric-hint {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.6;
  margin-top: 8px;
}

/* ── 结果集节点（管道唯一出口，卡片唯一数据来源） ── */
.rs-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--db-text-muted);
}
.rs-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.rs-status.rs-ready { color: var(--db-positive); }
.rs-status.rs-stale { color: var(--db-warning); }
.rs-status.rs-running { color: var(--db-accent); }
.rs-status.rs-failed { color: var(--db-danger); }
.rs-box {
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  background: var(--db-muted);
}
.rs-box.rs-failed {
  border-color: var(--db-danger);
}
.rs-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: var(--db-text);
}
.rs-source {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rs-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--db-text-muted);
}
.rs-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--db-text-muted);
}
.rs-error {
  margin-top: 8px;
}
.rs-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.rs-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--db-warning);
  line-height: 1.6;
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
