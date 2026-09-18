<script setup lang="ts">
/**
 * 洞察·仪表盘·KPI 指标分组卡片 前端交互原型
 * ----------------------------------------------------------------------------
 * 来源：docs/策略解读/指标分组卡片原型设计.md（「结果集优先」口径，已落地 R1–R10）
 * 目的：验证 KPI 指标分组卡片的完整前端交互（与真实工程同 Element Plus / 同 token）。
 * 数据：全部为前端假数据，见 FAKE_RESULTSET 与各处 `// 数据对接点` 注释；
 *       接入后端时，把假数据读取替换为真实「最终结果集」查询即可，状态模型可直接复用。
 * 运行：路由 /insight/kpi-metric-group-card-prototype（免登录，仅供交互体验验证）。
 * ============================================================================
 */
import { reactive, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'

/* ── 类型 ─────────────────────────────────────────────────────────────── */
type FieldType = 'count' | 'rate' | 'amount' | 'num'
interface ResultField {
  key: string // 结果集字段名，如 plan_count
  label: string // 字段元数据带出的中文名，如 关联计划数
  type: FieldType // 用于按类型默认单位 / 格式（§2.2 R9）
  value: number // 真实值（rate 以 0~1 比例存储）
  prev: number | null // 对比基准值（趋势自动计算用）
}
interface Metric {
  id: string
  fieldKey: string // 绑定的结果集字段；空串表示手动覆盖指标
  displayName: string // 展示列名（§5.2，已合并「指标名称」）
  unit: string
  format: 'original' | 'thousand' | 'percent' | 'amount' | 'decimal'
  decimals: number
  prefix: string
  suffix: string
  inherit: boolean // §5.6 是否继承卡片默认（覆盖开关）
  trendMode: 'auto' | 'bind' | 'none' // §5.7
  trendBindKey: string | null // 绑定字段模式选中的结果集字段
  trendColorInherit: boolean
  helperText: string // 辅助说明（支持 {{字段}} 插值，§5.8）
  visible: boolean
  manualValue?: number // 手动覆盖指标使用的固定值
}
interface Tab {
  id: string
  title: string
  metrics: Metric[]
}

/* ── 假数据：最终结果集（数据 + 字段元数据）───────────────────────────── */
// 数据对接点：真实应来自「数据源 + 筛选 + Python 预处理」产出的结果集查询。
const FAKE_RESULTSET: ResultField[] = [
  { key: 'plan_count', label: '关联计划数', type: 'count', value: 12, prev: 10 },
  { key: 'strategy_count', label: '关联策略数', type: 'count', value: 38, prev: 33 },
  { key: 'sent_count', label: '下发策略数', type: 'count', value: 24680, prev: 21900 },
  { key: 'touched_count', label: '触达策略数', type: 'count', value: 19420, prev: 20000 },
  { key: 'touch_rate', label: '触达率', type: 'rate', value: 0.623, prev: 0.645 },
  { key: 'conv_amount', label: '转化金额', type: 'amount', value: 128640, prev: 112300 },
]
// 可用作用域参数（辅助说明插值，§5.8）
const SCOPE_PARAMS: Record<string, string> = { 时间范围: '近 7 日', 渠道: '全渠道' }

const uid = (p: string) => `${p}_${Math.random().toString(36).slice(2, 7)}`

/* ── 按字段类型给默认单位 / 格式（§2.2 R9）─────────────────────────────── */
function defaultUnit(t: FieldType): string {
  if (t === 'rate') return '%'
  if (t === 'amount') return '元'
  if (t === 'count') return '个'
  return ''
}
function defaultFormat(t: FieldType): Metric['format'] {
  if (t === 'rate') return 'percent'
  if (t === 'amount') return 'amount'
  if (t === 'count') return 'thousand'
  return 'thousand'
}

/* ── 结果集投影出默认指标（核心：指标是结果集的投影，§二 / §2.2）─────────── */
function projectMetrics(fields: ResultField[]): Metric[] {
  return fields.map((f) => ({
    id: uid('m'),
    fieldKey: f.key,
    displayName: f.label,
    unit: defaultUnit(f.type),
    format: defaultFormat(f.type),
    decimals: f.type === 'rate' ? 1 : 0,
    prefix: '',
    suffix: '',
    inherit: true, // 默认继承卡片默认，覆盖开关置否才展开编辑（§5.6 R3）
    trendMode: f.prev != null ? 'auto' : 'none',
    trendBindKey: null,
    trendColorInherit: true,
    helperText: '',
    visible: true,
  }))
}

/* ── UI / 全局状态 ───────────────────────────────────────────────────── */
const componentTitle = ref('KPI 指标卡')
const multiMetric = ref(true) // 多指标模式（§2.2 默认开）
const multiTab = ref(false) // 多 Tab 模式
const mode = ref<'edit' | 'preview'>('edit') // 编辑态 / 组件预览(排版锁定态)，§二 R4
const emptySim = ref(false) // 模拟空结果集（§2.2 R8 空态）

const tabs = ref<Tab[]>([{ id: 'tab1', title: '默认集合', metrics: projectMetrics(FAKE_RESULTSET) }])
const activeTabId = ref('tab1')
const activeMetrics = ref<Metric[]>([]) // 当前 Tab 的可编辑指标数组（与 tab.metrics 引用同步）
const expandedMetricId = ref<string | null>(null)
const selectedIds = ref<string[]>([]) // 批量选择（§5.6 R9）
const showBaseAttr = ref(false) // 指标基础属性弹窗（§5.5，仅字体大小）
const cardFont = reactive({ fontValue: 28, fontLabel: 14 })
const showBatch = ref(false)
const batchUnit = ref('个')
const batchFormat = ref<Metric['format']>('thousand')
const insertFieldKey = ref('')

const fieldMap = computed<Record<string, ResultField>>(() => {
  const m: Record<string, ResultField> = {}
  for (const f of FAKE_RESULTSET) m[f.key] = f
  return m
})
const metricMap = computed<Record<string, Metric>>(() => {
  const m: Record<string, Metric> = {}
  for (const mt of activeMetrics.value) m[mt.id] = mt
  return m
})

function syncActive() {
  const t = tabs.value.find((x) => x.id === activeTabId.value)
  activeMetrics.value = t ? t.metrics : []
  expandedMetricId.value = null
  selectedIds.value = []
}
watch(activeTabId, syncActive, { immediate: true })

function onMetricsReorder(list: Metric[]) {
  const t = tabs.value.find((x) => x.id === activeTabId.value)
  if (t) t.metrics = list
}

/* ── 数值 / 趋势 / 辅助说明 渲染 ───────────────────────────────────────── */
function formatNumber(v: number, fmt: Metric['format'], decimals: number): string {
  if (fmt === 'percent') return (v * 100).toFixed(decimals)
  if (fmt === 'thousand') return Math.round(v).toLocaleString('zh-CN')
  if (fmt === 'amount') return Math.round(v).toLocaleString('zh-CN')
  if (fmt === 'decimal') return v.toFixed(decimals)
  return String(v)
}
function fieldDisplay(f: ResultField | null): string {
  if (!f) return ''
  const fmt = f.type === 'rate' ? 'percent' : f.type === 'amount' ? 'amount' : 'thousand'
  const dec = f.type === 'rate' ? 1 : 0
  return formatNumber(f.value, fmt, dec) + (f.type === 'rate' ? '%' : f.type === 'amount' ? '元' : '')
}
interface RenderedMetric {
  id: string
  name: string
  value: string
  unit: string
  trend: { dir: 'up' | 'down' | 'flat'; text: string } | null
  helper: string
}
function renderMetric(m: Metric): RenderedMetric {
  const f = m.fieldKey ? fieldMap.value[m.fieldKey] : null
  const raw = f ? f.value : m.manualValue ?? 0
  const val = formatNumber(raw, m.format, m.decimals) + (m.unit ? ' ' + m.unit : '')
  let trend: RenderedMetric['trend'] = null
  if (m.trendMode === 'auto' && f && f.prev != null) {
    const pct = ((f.value - f.prev) / f.prev) * 100
    trend = { dir: pct >= 0 ? 'up' : 'down', text: (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%' }
  } else if (m.trendMode === 'bind' && m.trendBindKey) {
    const bf = fieldMap.value[m.trendBindKey]
    trend = bf ? { dir: 'flat', text: fieldDisplay(bf) } : null
  }
  return { id: m.id, name: m.displayName, value: val, unit: m.unit, trend, helper: renderHelper(m) }
}
function renderHelper(m: Metric): string {
  let t = m.helperText
  for (const f of FAKE_RESULTSET) {
    t = t.split(`{{${f.key}}}`).join(fieldDisplay(f))
  }
  for (const [k, v] of Object.entries(SCOPE_PARAMS)) {
    t = t.split(`{{${k}}}`).join(v)
  }
  return t
}

/* ── 当前画布展示的指标（按列表顺序 + 可见性过滤）────────────────────────── */
const canvasMetrics = computed<Metric[]>(() => {
  const ordered = mode.value || true ? activeMetrics.value : activeMetrics.value
  const vis = ordered.filter((m) => m.visible)
  return multiMetric.value ? vis : vis.slice(0, 1)
})
const canvasRendered = computed<RenderedMetric[]>(() => canvasMetrics.value.map(renderMetric))

/* ── 指标操作：编辑展开 / 可见 / 复制 / 删除 / 新增 ─────────────────────── */
function toggleExpand(id: string) {
  expandedMetricId.value = expandedMetricId.value === id ? null : id
}
function toggleVisible(m: Metric) {
  m.visible = !m.visible
}
function copyMetric(m: Metric) {
  const idx = activeMetrics.value.findIndex((x) => x.id === m.id)
  const clone: Metric = { ...m, id: uid('m'), displayName: m.displayName + ' 副本' }
  activeMetrics.value.splice(idx + 1, 0, clone)
  syncActiveWriteback()
  ElMessage.success('已复制指标')
}
function deleteMetric(m: Metric) {
  const idx = activeMetrics.value.findIndex((x) => x.id === m.id)
  if (idx >= 0) activeMetrics.value.splice(idx, 1)
  syncActiveWriteback()
  ElMessage.success('已删除指标')
}
function addMetric() {
  // 覆盖 / 进阶操作（§5.1）：手动新增指标，值来源需手动配置
  const nm: Metric = {
    id: uid('m'),
    fieldKey: '',
    displayName: '新指标',
    unit: '个',
    format: 'thousand',
    decimals: 0,
    prefix: '',
    suffix: '',
    inherit: false,
    trendMode: 'none',
    trendBindKey: null,
    trendColorInherit: true,
    helperText: '',
    visible: true,
    manualValue: 0,
  }
  activeMetrics.value.push(nm)
  syncActiveWriteback()
  expandedMetricId.value = nm.id
  ElMessage.info('已新增手动指标（覆盖操作）：请配置值来源')
}
function syncActiveWriteback() {
  const t = tabs.value.find((x) => x.id === activeTabId.value)
  if (t) t.metrics = activeMetrics.value
}

/* ── 辅助说明：插入字段 token（§5.8 R6）────────────────────────────────── */
function insertField() {
  if (!insertFieldKey.value || !expandedMetricId.value) return
  const m = metricMap.value[expandedMetricId.value]
  if (m) m.helperText += `{{${insertFieldKey.value}}}`
  insertFieldKey.value = ''
}

/* ── 批量设置（§5.6 R9）────────────────────────────────────────────────── */
function openBatch() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先在指标列表勾选需要批量设置的指标')
    return
  }
  showBatch.value = true
}
function applyBatch() {
  let applied = 0
  for (const id of selectedIds.value) {
    const m = metricMap.value[id]
    if (m && m.inherit) {
      m.unit = batchUnit.value
      m.format = batchFormat.value
      applied++
    }
  }
  showBatch.value = false
  ElMessage.success(`已对 ${applied} 个「继承卡片默认」的指标应用单位 / 格式（手动覆盖项已跳过）`)
}

/* ── 多 Tab（指标集合切换，§十一 状态 10–13）────────────────────────────── */
function addTab() {
  const id = uid('tab')
  tabs.value.push({ id, title: `集合 ${tabs.value.length + 1}`, metrics: projectMetrics(FAKE_RESULTSET) })
  activeTabId.value = id
  ElMessage.success('已新增指标集合')
}
function renameTab(t: Tab) {
  const v = window.prompt('集合名称', t.title)
  if (v != null && v.trim()) t.title = v.trim()
}
function deleteTab(t: Tab) {
  if (tabs.value.length <= 1) {
    ElMessage.warning('至少保留一个指标集合')
    return
  }
  const idx = tabs.value.findIndex((x) => x.id === t.id)
  tabs.value.splice(idx, 1)
  if (activeTabId.value === t.id) activeTabId.value = tabs.value[0].id
}

/* ── 数据集配置区（假按钮，数据对接点）─────────────────────────────────── */
function fakeConfig(action: string) {
  // 数据对接点：真实应打开对应数据源 / 筛选器 / Python 预处理弹窗
  ElMessage.info(`数据对接点：${action}（原型中使用假结果集，接入后端时替换为真实配置弹窗）`)
}
function regenerateResultset() {
  // 模拟「重新生成最终结果集」：重新投影当前 Tab 指标（演示增量同步 / 空态切换）
  const t = tabs.value.find((x) => x.id === activeTabId.value)
  if (t) t.metrics = projectMetrics(FAKE_RESULTSET)
  syncActive()
  ElMessage.success('已按结果集字段重新投影默认指标')
}

/* ── 指标基础属性弹窗（§5.5，仅字体大小）────────────────────────────────── */
function saveBaseAttr() {
  showBaseAttr.value = false
  ElMessage.success('已保存卡片基础属性（字体大小）')
}

/* ── 组件预览（§二 / §3.1 R4：编辑态已出数，预览=排版锁定态）─────────────── */
function togglePreview() {
  mode.value = mode.value === 'edit' ? 'preview' : 'edit'
}
</script>

<template>
  <div class="proto-root">
    <!-- 顶部工具条 -->
    <div class="topbar">
      <div class="topbar-title">KPI 指标分组卡片 · 前端交互原型</div>
      <div class="topbar-actions">
        <el-switch v-model="emptySim" inline-prompt active-text="空态" inactive-text="正常" />
        <el-button size="small" @click="regenerateResultset">模拟生成结果集</el-button>
        <el-button size="small" :type="mode === 'preview' ? 'warning' : 'primary'" @click="togglePreview">
          {{ mode === 'preview' ? '退出预览（排版锁定态）' : '组件预览（排版 / 锁定态）' }}
        </el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 左侧：属性配置面板（§3.1） -->
      <aside class="panel">
        <div class="panel-section">
          <label class="field-label">组件标题</label>
          <el-input v-model="componentTitle" size="small" />
        </div>

        <div class="panel-section switches">
          <div class="switch-row">
            <span>多指标模式</span>
            <el-switch v-model="multiMetric" />
          </div>
          <div class="switch-row">
            <span>多 Tab 模式</span>
            <el-switch v-model="multiTab" />
          </div>
        </div>

        <el-divider>数据集配置（数据对接点）</el-divider>
        <div class="panel-section fake-config">
          <el-button size="small" plain @click="fakeConfig('数据集配置')">数据集配置</el-button>
          <el-button size="small" plain @click="fakeConfig('筛选器绑定')">筛选器绑定</el-button>
          <el-button size="small" plain @click="fakeConfig('Python 预处理')">Python 预处理</el-button>
        </div>

        <!-- 多 Tab 集合管理 -->
        <template v-if="multiTab">
          <el-divider>指标集合（多 Tab）</el-divider>
          <div class="tab-manager">
            <div
              v-for="t in tabs"
              :key="t.id"
              class="tab-pill"
              :class="{ active: t.id === activeTabId }"
              @click="activeTabId = t.id"
            >
              <span class="tab-name" @dblclick="renameTab(t)">{{ t.title }}</span>
              <span class="tab-del" @click.stop="deleteTab(t)">×</span>
            </div>
            <el-button size="small" class="tab-add" @click="addTab">+ 集合</el-button>
          </div>
        </template>

        <el-divider>指标列表（结果集投影生成）</el-divider>
        <div class="panel-section metric-toolbar">
          <el-button size="small" @click="openBatch">批量设置</el-button>
          <el-button size="small" type="primary" plain @click="addMetric">+ 新增指标</el-button>
          <span class="hint">勾选可批量改单位 / 格式</span>
        </div>

        <!-- 指标列表 + 拖拽排序（§4.1） -->
        <VueDraggable
          v-model="activeMetrics"
          class="metric-list"
          handle=".drag-handle"
          :animation="150"
          @update:model-value="onMetricsReorder"
        >
          <div v-for="m in activeMetrics" :key="m.id" class="metric-item" :class="{ expanded: expandedMetricId === m.id }">
            <div class="metric-head">
              <span class="drag-handle" title="拖拽排序">⠿</span>
              <input
                type="checkbox"
                v-model="selectedIds"
                :value="m.id"
                class="batch-cb"
                :title="'批量选择 ' + m.displayName"
              />
              <span class="m-name">{{ m.displayName }}</span>
              <el-tag v-if="m.fieldKey" size="small" type="info" effect="plain">结果集.{{ m.fieldKey }}</el-tag>
              <el-tag v-else size="small" type="warning" effect="plain">手动覆盖</el-tag>
              <span class="m-actions">
                <el-button link size="small" @click="toggleVisible(m)">{{ m.visible ? '👁' : '🚫' }}</el-button>
                <el-button link size="small" @click="toggleExpand(m.id)">{{ expandedMetricId === m.id ? '收起' : '编辑' }}</el-button>
                <el-button link size="small" @click="copyMetric(m)">复制</el-button>
                <el-button link size="small" type="danger" @click="deleteMetric(m)">删除</el-button>
              </span>
            </div>

            <!-- 编辑展开区（§5.1 展示配置） -->
            <div v-if="expandedMetricId === m.id" class="metric-edit">
              <div class="edit-row">
                <label>展示列名</label>
                <el-input v-model="m.displayName" size="small" />
              </div>
              <div class="edit-row">
                <label>值来源</label>
                <el-input
                  :model-value="m.fieldKey ? '结果集字段（已映射）' : '手动指标（覆盖）'"
                  size="small"
                  readonly
                />
              </div>
              <div v-if="m.fieldKey" class="edit-row">
                <label>字段</label>
                <el-input :model-value="m.fieldKey + '（只读）'" size="small" readonly />
              </div>

              <!-- 单位 / 格式：继承 or 覆盖（§5.6 R3） -->
              <div class="edit-row">
                <label>单位 / 格式</label>
                <el-radio-group v-model="m.inherit" size="small">
                  <el-radio :value="true">继承卡片默认</el-radio>
                  <el-radio :value="false">覆盖</el-radio>
                </el-radio-group>
              </div>
              <div v-if="!m.inherit" class="edit-row sub">
                <label>单位</label>
                <el-select v-model="m.unit" size="small" style="width: 110px">
                  <el-option label="个" value="个" />
                  <el-option label="%" value="%" />
                  <el-option label="元" value="元" />
                  <el-option label="次" value="次" />
                  <el-option label="无" value="" />
                </el-select>
                <label>格式</label>
                <el-select v-model="m.format" size="small" style="width: 120px">
                  <el-option label="原始数值" value="original" />
                  <el-option label="千分位" value="thousand" />
                  <el-option label="百分比" value="percent" />
                  <el-option label="金额" value="amount" />
                  <el-option label="小数位" value="decimal" />
                </el-select>
                <label>小数</label>
                <el-input-number v-model="m.decimals" :min="0" :max="4" size="small" style="width: 90px" />
              </div>

              <!-- 趋势配置（§5.7 R1） -->
              <div class="edit-row">
                <label>趋势</label>
                <el-select v-model="m.trendMode" size="small" style="width: 130px">
                  <el-option label="自动计算" value="auto" />
                  <el-option label="绑定字段" value="bind" />
                  <el-option label="不展示" value="none" />
                </el-select>
                <el-select
                  v-if="m.trendMode === 'bind'"
                  v-model="m.trendBindKey"
                  size="small"
                  style="width: 140px"
                  placeholder="选择趋势字段"
                >
                  <el-option v-for="f in FAKE_RESULTSET" :key="f.key" :label="f.label" :value="f.key" />
                </el-select>
              </div>

              <!-- 辅助说明：token 选择器 + 实时预览（§5.8 R6） -->
              <div class="edit-row col">
                <label>辅助说明</label>
                <el-input v-model="m.helperText" size="small" placeholder="支持 {{字段}} 插值，如 {{touch_rate}} 触达率" />
                <div class="helper-tools">
                  <el-select v-model="insertFieldKey" size="small" style="width: 160px" placeholder="插入字段" @change="insertField">
                    <el-option v-for="f in FAKE_RESULTSET" :key="f.key" :label="f.label" :value="f.key" />
                  </el-select>
                  <span class="helper-preview">预览：{{ renderHelper(m) || '（空）' }}</span>
                </div>
              </div>
            </div>
          </div>
        </VueDraggable>

        <!-- 底部：指标基础属性 / 组件预览（§3.1） -->
        <div class="panel-footer">
          <el-button size="small" @click="showBaseAttr = true">指标基础属性</el-button>
          <el-button size="small" :type="mode === 'preview' ? 'warning' : 'primary'" @click="togglePreview">
            {{ mode === 'preview' ? '退出预览' : '组件预览' }}
          </el-button>
        </div>
      </aside>

      <!-- 右侧：画布 KPI 卡片（§3.2 响应式网格，编辑态直接出数） -->
      <main class="canvas" :class="{ preview: mode === 'preview' }">
        <div v-if="mode === 'preview'" class="preview-banner">
          排版 / 锁定态：指标不可编辑，拖拽可调整顺序（与属性面板顺序一致）
        </div>

        <div class="kpi-card">
          <div class="kpi-card-head">
            <span class="kpi-title">{{ componentTitle }}</span>
            <span class="kpi-menu">⋮</span>
          </div>

          <!-- 多 Tab 画布栏 -->
          <div v-if="multiTab" class="kpi-tabs">
            <span
              v-for="t in tabs"
              :key="t.id"
              class="kpi-tab"
              :class="{ active: t.id === activeTabId }"
              @click="activeTabId = t.id"
            >{{ t.title }}</span>
          </div>

          <!-- 空态（§2.2 R8） -->
          <el-empty v-if="emptySim" description="暂无数据" :image-size="60" />

          <!-- 指标网格 -->
          <div v-else class="kpi-grid">
            <div v-for="rm in canvasRendered" :key="rm.id" class="kpi-metric">
              <div class="km-name" :style="{ fontSize: cardFont.fontLabel + 'px' }">{{ rm.name }}</div>
              <div class="km-value" :style="{ fontSize: cardFont.fontValue + 'px' }">
                {{ rm.value }}
                <span v-if="rm.trend" class="km-trend" :class="rm.trend.dir">
                  <template v-if="rm.trend.dir === 'up'">▲</template>
                  <template v-else-if="rm.trend.dir === 'down'">▼</template>
                  <template v-else>—</template>
                  {{ rm.trend.text }}
                </span>
              </div>
              <div v-if="rm.helper" class="km-helper">{{ rm.helper }}</div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 指标基础属性弹窗（§5.5 仅字体大小，R2） -->
    <el-dialog v-model="showBaseAttr" title="指标基础属性" width="360px">
      <div class="base-attr">
        <div class="edit-row">
          <label>指标值字号</label>
          <el-input-number v-model="cardFont.fontValue" :min="12" :max="48" size="small" />
          <span class="unit">px</span>
        </div>
        <div class="edit-row">
          <label>展示列名字号</label>
          <el-input-number v-model="cardFont.fontLabel" :min="10" :max="32" size="small" />
          <span class="unit">px</span>
        </div>
        <p class="base-note">
          单位 / 数值格式 / 辅助说明已下沉到单指标编辑（§5.6 / §5.8），本弹窗不再重复配置，避免「两头配」。
        </p>
      </div>
      <template #footer>
        <el-button size="small" @click="showBaseAttr = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveBaseAttr">保存属性</el-button>
      </template>
    </el-dialog>

    <!-- 批量设置弹窗（§5.6 R9） -->
    <el-dialog v-model="showBatch" title="批量设置单位 / 格式" width="360px">
      <div class="base-attr">
        <div class="edit-row">
          <label>单位</label>
          <el-select v-model="batchUnit" size="small" style="width: 120px">
            <el-option label="个" value="个" />
            <el-option label="%" value="%" />
            <el-option label="元" value="元" />
            <el-option label="次" value="次" />
            <el-option label="无" value="" />
          </el-select>
        </div>
        <div class="edit-row">
          <label>格式</label>
          <el-select v-model="batchFormat" size="small" style="width: 130px">
            <el-option label="原始数值" value="original" />
            <el-option label="千分位" value="thousand" />
            <el-option label="百分比" value="percent" />
            <el-option label="金额" value="amount" />
            <el-option label="小数位" value="decimal" />
          </el-select>
        </div>
        <p class="base-note">仅对勾选且「继承卡片默认」的指标生效；手动覆盖项会被跳过。</p>
      </div>
      <template #footer>
        <el-button size="small" @click="showBatch = false">取消</el-button>
        <el-button size="small" type="primary" @click="applyBatch">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.proto-root {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  color: #1f2329;
  font-family: -apple-system, 'Segoe UI', 'PingFang SC', sans-serif;
}
.topbar {
  height: 52px;
  flex: 0 0 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e5eaf2;
}
.topbar-title {
  font-weight: 600;
  font-size: 15px;
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.layout {
  flex: 1 1 auto;
  display: flex;
  min-height: 0;
}
.panel {
  width: 400px;
  flex: 0 0 400px;
  background: #fff;
  border-right: 1px solid #e5eaf2;
  padding: 14px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.panel-section {
  margin-bottom: 10px;
}
.field-label {
  display: block;
  font-size: 12px;
  color: #8a919f;
  margin-bottom: 4px;
}
.switches .switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: 13px;
}
.fake-config {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tab-manager {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.tab-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 14px;
  font-size: 12px;
  cursor: pointer;
  background: #fff;
}
.tab-pill.active {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}
.tab-del {
  color: #c0c4cc;
  font-size: 14px;
  line-height: 1;
}
.tab-del:hover {
  color: #f56c6c;
}
.tab-add {
  padding: 2px 8px;
}
.metric-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.metric-toolbar .hint {
  font-size: 11px;
  color: #a8abb2;
}
.metric-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1 1 auto;
}
.metric-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
  overflow: hidden;
}
.metric-item.expanded {
  border-color: #409eff;
  background: #fff;
}
.metric-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
}
.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  font-size: 16px;
}
.batch-cb {
  transform: scale(0.85);
}
.m-name {
  font-size: 13px;
  font-weight: 500;
  flex: 0 0 auto;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}
.metric-edit {
  border-top: 1px dashed #ebeef5;
  padding: 8px;
  background: #fff;
}
.edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.edit-row.col {
  flex-direction: column;
  align-items: stretch;
}
.edit-row label {
  font-size: 12px;
  color: #606266;
  flex: 0 0 64px;
}
.edit-row.sub {
  padding-left: 72px;
}
.helper-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.helper-preview {
  font-size: 12px;
  color: #409eff;
}
.panel-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #e5eaf2;
  display: flex;
  gap: 8px;
}
.canvas {
  flex: 1 1 auto;
  padding: 24px;
  overflow-y: auto;
}
.canvas.preview {
  background: #eef1f6;
}
.preview-banner {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  margin-bottom: 12px;
}
.kpi-card {
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 12px;
  padding: 16px;
  max-width: 920px;
  margin: 0 auto;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.kpi-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.kpi-title {
  font-size: 16px;
  font-weight: 600;
}
.kpi-menu {
  color: #c0c4cc;
  cursor: pointer;
}
.kpi-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
  padding-bottom: 6px;
}
.kpi-tab {
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 6px;
}
.kpi-tab.active {
  color: #409eff;
  background: #ecf5ff;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.kpi-metric {
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 14px;
  background: #fcfcfd;
}
.km-name {
  color: #8a919f;
  margin-bottom: 6px;
}
.km-value {
  font-weight: 700;
  color: #1f2329;
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}
.km-trend {
  font-size: 12px;
  font-weight: 600;
}
.km-trend.up {
  color: #f56c6c;
}
.km-trend.down {
  color: #67c23a;
}
.km-trend.flat {
  color: #909399;
}
.km-helper {
  margin-top: 6px;
  font-size: 12px;
  color: #a8abb2;
}
.base-attr .edit-row {
  margin-bottom: 12px;
}
.base-attr .unit {
  font-size: 12px;
  color: #909399;
}
.base-note {
  font-size: 12px;
  color: #a8abb2;
  margin: 6px 0 0;
  line-height: 1.5;
}
</style>
