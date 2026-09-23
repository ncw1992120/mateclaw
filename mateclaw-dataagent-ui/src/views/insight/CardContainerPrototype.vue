<script setup lang="ts">
/**
 * 洞察·仪表盘·组合卡片（卡片容器）前端交互原型
 * ----------------------------------------------------------------------------
 * 来源：docs/策略解读/组合卡片原型设计.md
 * 目的：在「洞察 → 仪表盘 → 组件」中验证组合卡片的完整前端交互（与真实工程同 token / 同 Element Plus）。
 * 数据：全部为前端假数据，见 FAKE_DATA 与各处 `// 数据对接点` 注释；
 *       接入后端时，只需把假数据读取替换为真实接口，组件状态模型可直接复用。
 * 运行：通过路由 /insight/card-container-prototype 访问（免登录，仅供交互体验验证）。
 * 注意：本文件为独立 prototype 视图，不改动线上编辑器，便于先验证交互、再决定如何并入 DashboardCanvas。
 * ============================================================================
 */
import { reactive, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import InsightColorField from './components/InsightColorField.vue'
import { CARD_BG_PRESETS } from '@/utils/color-presets'

/* ── 类型 ─────────────────────────────────────────────────────────────── */
type ChildType = 'kpi' | 'chart' | 'table' | 'filter' | 'timeFilter' | 'ai' | 'button' | 'container'
interface ChildWidget {
  id: string
  type: ChildType
  title: string
  col: number // 宽度占用的网格列数（1-12），自由布局下用于换算百分比宽度
  x: number | null // 自由布局：相对内容区左上角的横坐标(px)；null 表示未定位（落点定位）
  y: number | null // 自由布局：纵坐标(px)
  h: number | null // 自由布局：高度(px)；null 表示内容自适应
  data: any // 数据对接点：真实应来自数据集 / 筛选上下文
}
interface ContainerTab {
  id: string
  title: string
  children: string[]
}
interface ScopeParam {
  id: string
  name: string
  source: string
  scope: string
  overridable: boolean
}
interface ScopeEvent {
  id: string
  trigger: string
  action: string
  target: string
}

/* ── 假数据（数据对接点）─────────────────────────────────────────────── */
const FAKE_DATA = {
  kpi: [
    { label: '关联计划数', value: '12', trend: '+2', dir: 'up' },
    { label: '关联策略数', value: '38', trend: '+5', dir: 'up' },
    { label: '下发策略数', value: '24,680', trend: '+12%', dir: 'up' },
    { label: '触达策略数', value: '19,420', trend: '-3%', dir: 'down' },
  ],
  chart: { categories: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'], values: [120, 200, 150, 80, 170, 210, 140] },
  table: { head: ['策略', '触达', '转化'], rows: [['新客拉新', '8,420', '1,210'], ['召回', '5,300', '640'], ['沉睡唤醒', '3,120', '280']] },
  ai: { blocks: [
    { h: '整体结论', p: '本周策略整体触达 19,420 次，转化率较上周提升 1.8pct，新客拉新贡献最大。' },
    { h: '风险提示', p: '沉睡唤醒策略转化偏低，建议优化触达时段与文案。' },
  ] },
}

const uid = (p: string) => `${p}_${Math.random().toString(36).slice(2, 7)}`

/* ── 状态（对应设计文档 §十三 card-container）─────────────────────────── */
const container = reactive({
  id: 'cc-1',
  title: '策略执行情况',
  titleBarStyle: 'standard' as 'hidden' | 'standard',
  size: { width: 760, height: 420 },
  style: {
    background: '#FFFFFF',
    border: { enabled: true, color: '#E5EAF2', width: 1 },
    radius: 12,
    padding: 16,
    shadow: 'none' as 'none' | 'card',
  },
  layout: { mode: 'free' as 'grid' | 'vertical' | 'free', columns: 12, gap: 12, align: 'left' },
  activeTab: 'tab-summary',
  defaultTab: 'tab-summary',
  tabs: [
    { id: 'tab-summary', title: '策略概括', children: ['kpi-1', 'kpi-2', 'kpi-3', 'kpi-4'] },
    { id: 'tab-exec', title: '策略执行', children: ['chart-1', 'table-1'] },
    { id: 'tab-contrib', title: '策略贡献', children: ['ai-1'] },
  ] as ContainerTab[],
  scope: {
    parameters: [{ id: 'p1', name: '时间范围', source: '仪表盘时间筛选', scope: '全部子组件', overridable: true }] as ScopeParam[],
    events: [{ id: 'e1', trigger: '时间筛选', action: '刷新数据', target: '当前容器' }] as ScopeEvent[],
  },
})

// 初始坐标基于默认容器(宽760/内边距16/列12/间距12)的错落排布；自由布局下 x/y 为相对内容区左上角的像素。
const children = reactive<Record<string, ChildWidget>>({
  'kpi-1': { id: 'kpi-1', type: 'kpi', title: '关联计划数', col: 6, x: 0, y: 0, h: 96, data: FAKE_DATA.kpi[0] },
  'kpi-2': { id: 'kpi-2', type: 'kpi', title: '关联策略数', col: 6, x: 370, y: 0, h: 96, data: FAKE_DATA.kpi[1] },
  'kpi-3': { id: 'kpi-3', type: 'kpi', title: '下发策略数', col: 6, x: 0, y: 116, h: 96, data: FAKE_DATA.kpi[2] },
  'kpi-4': { id: 'kpi-4', type: 'kpi', title: '触达策略数', col: 6, x: 370, y: 116, h: 96, data: FAKE_DATA.kpi[3] },
  'chart-1': { id: 'chart-1', type: 'chart', title: '触达趋势', col: 7, x: 0, y: 232, h: 180, data: FAKE_DATA.chart },
  'table-1': { id: 'table-1', type: 'table', title: '策略明细', col: 5, x: 432, y: 232, h: 180, data: FAKE_DATA.table },
  'ai-1': { id: 'ai-1', type: 'ai', title: 'AI 分析', col: 12, x: 0, y: 436, h: 160, data: FAKE_DATA.ai },
})

/* ── UI 状态 ─────────────────────────────────────────────────────────── */
const mode = ref<'edit' | 'preview'>('edit')
const selection = ref<'container' | 'child' | 'none'>('container')
const selectedChildId = ref<string | null>(null)
const demoState = ref<'loaded' | 'loading' | 'empty' | 'error'>('loaded')
const collapsed = reactive<Record<string, boolean>>({})
const editingTab = ref<string | null>(null)
const hoverChildId = ref<string | null>(null)
const showAdd = ref(false)
const fitContent = ref(false)
const lockRatio = ref(false)
const ccBodyRef = ref<HTMLElement | null>(null) // 内容区 DOM，用于自由布局落点/拖动 clamp
const cardContainerRef = ref<HTMLElement | null>(null) // 卡片容器 DOM，用于判定拖拽是否仍在卡内
const backgroundPresets = ['#FFFFFF', 'var(--db-card)', 'var(--db-card-orange-bg)']
const isCustomBackground = ref(!backgroundPresets.includes(container.style.background))

watch(() => container.style.background, (background) => {
  isCustomBackground.value = !backgroundPresets.includes(background)
})

function changeBackground(value: string) {
  if (value === 'custom') {
    isCustomBackground.value = true
    if (container.style.background.startsWith('var(')) container.style.background = '#FFFFFF'
    return
  }
  container.style.background = value
  isCustomBackground.value = false
}

/* ── 计算 ───────────────────────────────────────────────────────────── */
const activeTabObj = computed<ContainerTab>(() => container.tabs.find((t) => t.id === container.activeTab) ?? container.tabs[0])
const activeChildren = computed<ChildWidget[]>(() => activeTabObj.value.children.map((id) => children[id]).filter(Boolean))
const isSelectedChild = (id: string) => selection.value === 'child' && selectedChildId.value === id

const dashboardTitle = ref('策略执行情况看板')

/* ── 选择 ───────────────────────────────────────────────────────────── */
function selectContainer() { selection.value = 'container'; selectedChildId.value = null }
function selectChild(id: string) { selection.value = 'child'; selectedChildId.value = id }
function selectTab(id: string) { container.activeTab = id; editingTab.value = null }
function selectChildFromState(): ChildWidget | null {
  return selectedChildId.value ? (children[selectedChildId.value] ?? null) : null
}

/* ── 页签 ───────────────────────────────────────────────────────────── */
function addTab() {
  if (container.tabs.length >= 8) { ElMessage.warning('已达页签数量上限'); return }
  const id = uid('tab')
  container.tabs.push({ id, title: `页签 ${container.tabs.length + 1}`, children: [] })
  container.activeTab = id
  ElMessage.success('已添加页签')
}
function deleteTab(id: string) {
  if (container.tabs.length <= 1) { ElMessage.warning('至少保留 1 个页签'); return }
  if (id === container.activeTab) { ElMessage.warning('请先切换到其他页签再删除当前页签'); return }
  container.tabs = container.tabs.filter((t) => t.id !== id)
}
function copyTab(id: string) {
  const t = container.tabs.find((x) => x.id === id)
  if (!t) return
  const nid = uid('tab')
  container.tabs.push({ id: nid, title: t.title + '-副本', children: [...t.children] })
  container.activeTab = nid
  ElMessage.success('已复制页签')
}
function setDefaultTab(id: string) { container.defaultTab = id }

/* ── 子组件 ─────────────────────────────────────────────────────────── */
function deleteChild(id: string) {
  container.tabs.forEach((t) => { t.children = t.children.filter((x) => x !== id) })
  delete children[id]
  if (selectedChildId.value === id) selectContainer()
  ElMessage.success('已删除子组件')
}
function addChild(type: ChildType, pos?: { x: number; y: number }) {
  const id = uid(type)
  // 数据对接点：接入后端后，pos 可来自接口返回；此处取鼠标落点（自由布局拖入）或错落默认位（弹窗/未知来源）。
  const base: Record<ChildType, { title: string; col: number; data: any }> = {
    kpi: { title: '新指标', col: 6, data: { value: '—', label: '新指标', trend: '', dir: 'up' } },
    chart: { title: '新图表', col: 7, data: FAKE_DATA.chart },
    table: { title: '新表格', col: 5, data: FAKE_DATA.table },
    ai: { title: 'AI 分析', col: 12, data: FAKE_DATA.ai },
    filter: { title: '筛选项', col: 6, data: null },
    timeFilter: { title: '时间筛选', col: 6, data: null },
    button: { title: '操作按钮', col: 4, data: null },
    container: { title: '嵌套组合卡片', col: 12, data: null },
  }
  const b = base[type]
  // 无明确落点时：自由布局给一个错落默认位（避免全堆左上角），网格布局时 x/y 不参与渲染。
  const n = activeTabObj.value.children.length
  const fallback = { x: 24 + (n % 4) * 36, y: 24 + Math.floor(n / 4) * 40 }
  children[id] = {
    id, type, title: b.title, col: b.col,
    x: pos ? pos.x : fallback.x,
    y: pos ? pos.y : fallback.y,
    data: b.data,
  }
  activeTabObj.value.children.push(id)
  selectChild(id)
  ElMessage.success(`已添加 1 个组件到「${activeTabObj.value.title}」`)
}
function handleChildAction(act: string) {
  if (act === 'retry') ElMessage.success('正在重试加载…')
  else if (act === 'view-cfg') ElMessage.info('打开组件配置（待对接）')
  else if (act === 'apply-time') ElMessage.success('时间筛选已应用，触发容器刷新（事件联动演示）')
  else if (act === 'op-click') ElMessage.success('操作按钮被点击（事件：可配置导出/下钻/跳转）')
}
function onFilterSeg(e: MouseEvent) {
  const btn = e.target as HTMLElement
  if (!btn.dataset.seg) return
  const row = btn.parentElement
  if (!row) return
  Array.from(row.querySelectorAll('[data-seg]')).forEach((b) => b.classList.remove('on'))
  btn.classList.add('on')
}

/* ── 预览 / 保存 ────────────────────────────────────────────────────── */
function togglePreview() {
  mode.value = mode.value === 'edit' ? 'preview' : 'edit'
  if (mode.value === 'preview') ElMessage.success('已进入预览态（隐藏编辑控件，保留真实交互）')
  else ElMessage.info('已返回编辑态')
}
function doSave() {
  const errs: string[] = []
  if (container.titleBarStyle !== 'hidden' && !container.title.trim()) errs.push('容器已开启标题栏，请补充标题')
  const names = container.tabs.map((t) => t.title.trim())
  if (names.some((n) => !n)) errs.push('存在空页签名称')
  const dup = names.filter((n, i) => names.indexOf(n) !== i)
  if (dup.length) errs.push('页签名称重复：' + Array.from(new Set(dup)).join('、'))
  if (container.tabs.some((t) => t.children.length === 0)) errs.push('存在空页签（非阻塞提醒，仍可保存）')

  // 数据对接点：保存时应把 container + children 配置 POST 给后端。
  const blocking = errs.filter((e) => e.includes('标题') || e.includes('重复') || e.includes('空页签名称'))
  if (blocking.length) {
    ElMessage.error('保存失败：' + blocking.join('；'))
    selection.value = 'container'
    return
  }
  if (errs.length) ElMessage.warning('已保存（提醒：' + errs.join('；') + '）')
  else ElMessage.success(`保存成功，已更新组合卡片「${container.title}」`)
}

/* ── 缩放（容器四角）────────────────────────────────────────────────── */
let rz: { corner: string; x: number; y: number; w: number; h: number } | null = null
function onResizeDown(e: PointerEvent, corner: string) {
  rz = { corner, x: e.clientX, y: e.clientY, w: container.size.width, h: container.size.height }
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeUp)
}
function onResizeMove(e: PointerEvent) {
  if (!rz) return
  const dx = e.clientX - rz.x
  const dy = e.clientY - rz.y
  let w = rz.w
  let h = rz.h
  if (rz.corner.includes('e')) w = Math.max(360, rz.w + dx)
  if (rz.corner.includes('w')) w = Math.max(360, rz.w - dx)
  if (rz.corner.includes('s')) h = Math.max(160, rz.h + dy)
  if (rz.corner.includes('n')) h = Math.max(160, rz.h - dy)
  container.size.width = Math.round(w)
  container.size.height = Math.round(h)
}
function onResizeUp() {
  rz = null
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeUp)
}

/* ── 子组件调整大小（8 向手柄：四角改宽高、左右边改宽、上下边改高）──────── */
// 用 mouse 事件：mousedown 上 preventDefault 可可靠抑制原生 HTML5 拖拽与文字选中
type ResizeHandle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w'
let cz: { id: string; handle: ResizeHandle; sx: number; sy: number; oCol: number; oH: number; ox: number; oy: number; colW: number } | null = null
const resizingId = ref<string | null>(null)
function onChildResizeDown(e: MouseEvent, child: ChildWidget, handle: ResizeHandle) {
  const cols = container.layout.columns
  const gridW = container.size.width - container.style.padding * 2 - (cols - 1) * container.layout.gap
  const colW = gridW / cols
  const el = (e.currentTarget as HTMLElement).closest('[data-child]') as HTMLElement | null
  const oH = child.h ?? el?.offsetHeight ?? 90
  cz = { id: child.id, handle, sx: e.clientX, sy: e.clientY, oCol: child.col, oH, ox: child.x ?? 0, oy: child.y ?? 0, colW }
  resizingId.value = child.id
  window.addEventListener('mousemove', onChildResizeMove)
  window.addEventListener('mouseup', onChildResizeUp)
}
function onChildResizeMove(e: MouseEvent) {
  if (!cz) return
  const c = children[cz.id]
  if (!c) return
  const dx = e.clientX - cz.sx
  const dy = e.clientY - cz.sy
  const horiz = cz.handle.includes('e') ? 1 : cz.handle.includes('w') ? -1 : 0
  const vert = cz.handle.includes('s') ? 1 : cz.handle.includes('n') ? -1 : 0
  // 宽度：按水平位移换算列数（1-12）
  if (horiz === 1) c.col = Math.max(1, Math.min(12, Math.round(cz.oCol + dx / cz.colW)))
  else if (horiz === -1) {
    c.col = Math.max(1, Math.min(12, Math.round(cz.oCol - dx / cz.colW)))
    c.x = Math.max(0, cz.ox + dx) // 左边缘右移，右边缘保持不动
  }
  // 高度：按垂直位移连续改 px（下限 60）
  if (vert === 1) c.h = Math.max(60, cz.oH + dy)
  else if (vert === -1) {
    c.h = Math.max(60, cz.oH - dy)
    c.y = Math.max(0, cz.oy + dy) // 上边缘下移，下边缘保持不动
  }
}
function onChildResizeUp() {
  cz = null
  resizingId.value = null
  window.removeEventListener('mousemove', onChildResizeMove)
  window.removeEventListener('mouseup', onChildResizeUp)
}

/* ── 子组件鼠标自由拖动（自由布局：拖到容器内任意位置）────────────────── */
// 用 mouse 事件而非 HTML5 DnD：自由定位需要实时跟随鼠标，DnD 的拖影不便精确控制位移。
let mv: { id: string; sx: number; sy: number; ox: number; oy: number } | null = null
const movingId = ref<string | null>(null)
function onChildMouseDown(e: MouseEvent, child: ChildWidget) {
  if (mode.value !== 'edit') return
  // 排除交互元素（删除 / 缩放 / 按钮 / 输入 / 分段控件等），避免点这些时误触发整体拖动
  const t = e.target as HTMLElement
  if (t.closest('.w-del, .rs, .resize-handle, button, input, select, textarea, .seg-btn, [data-no-drag]')) return
  selectChild(child.id)
  mv = { id: child.id, sx: e.clientX, sy: e.clientY, ox: child.x ?? 0, oy: child.y ?? 0 }
  movingId.value = child.id
  window.addEventListener('mousemove', onChildMouseMove)
  window.addEventListener('mouseup', onChildMouseUp)
}
function onChildMouseMove(e: MouseEvent) {
  if (!mv) return
  const dx = e.clientX - mv.sx
  const dy = e.clientY - mv.sy
  const c = children[mv.id]
  if (!c) return
  let nx = mv.ox + dx
  let ny = mv.oy + dy
  // clamp 到内容区内，避免拖出卡片
  const rect = ccBodyRef.value?.getBoundingClientRect()
  if (rect) {
    const el = document.querySelector(`[data-child="${mv.id}"]`) as HTMLElement | null
    const elW = el?.offsetWidth ?? 0
    const elH = el?.offsetHeight ?? 90
    nx = Math.max(0, Math.min(nx, Math.max(0, rect.width - elW)))
    ny = Math.max(0, Math.min(ny, Math.max(0, rect.height - elH)))
  }
  c.x = Math.round(nx)
  c.y = Math.round(ny)
}
function onChildMouseUp() {
  mv = null
  movingId.value = null
  window.removeEventListener('mousemove', onChildMouseMove)
  window.removeEventListener('mouseup', onChildMouseUp)
}
// 子组件定位样式：自由布局用绝对定位(left/top + 百分比宽)，网格/垂直布局用列跨度
function widgetStyle(child: ChildWidget): Record<string, string> {
  if (container.layout.mode === 'free') {
    return {
      position: 'absolute',
      left: (child.x ?? 0) + 'px',
      top: (child.y ?? 0) + 'px',
      width: `calc(${child.col} / 12 * 100%)`,
      ...(child.h != null ? { height: child.h + 'px' } : {}),
    }
  }
  return { gridColumn: `span ${child.col}` }
}

/* ── 物料面板拖入组合卡片（整卡落区）────────────────────────────────── */
// paletteType 标记当前拖拽源为物料面板（区别于子组件自身的鼠标拖动）。
let paletteType: ChildType | null = null
// paletteOver：拖拽悬停在卡片内时高亮整卡落区（dragover 持续点亮，离开卡片才熄灭，避免进入子组件误熄灭）
const paletteOver = ref(false)
function onPaletteDragStart(e: DragEvent, type: ChildType) {
  paletteType = type
  if (e.dataTransfer) {
    e.dataTransfer.setData('text/cc-type', type)
    e.dataTransfer.effectAllowed = 'copy'
  }
}
function onBodyDragEnter() { if (paletteType) paletteOver.value = true }
function onBodyDragLeave(e: DragEvent) {
  // 仅当真正离开卡片（relatedTarget 不在卡片内）才熄灭高亮
  if (paletteType && e.relatedTarget && !cardContainerRef.value?.contains(e.relatedTarget as Node)) paletteOver.value = false
}
function onBodyDragOver(e: DragEvent) {
  if (!paletteType) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
  paletteOver.value = true
}
function onBodyDrop(e: DragEvent) {
  if (!paletteType) return
  e.preventDefault()
  // 数据对接点：addChild 内部创建假数据子组件，接入后端后此处可附带默认数据集/筛选上下文。
  // 按鼠标相对内容区坐标计算落点（自由布局直接定位；网格布局忽略 x/y）。
  addChild(paletteType, dropPosFromEvent(e))
  paletteType = null
  paletteOver.value = false
}
// 拖拽结束（无论是否落在卡片内）都复位，避免拖到卡片外时落区提示卡住
function onPaletteDragEnd() {
  paletteType = null
  paletteOver.value = false
}
// 计算拖入落点：鼠标相对 .cc-body 内容区左上角，并 clamp 在内容区内
function dropPosFromEvent(e: DragEvent): { x: number; y: number } {
  const rect = ccBodyRef.value?.getBoundingClientRect()
  if (!rect) return { x: 24, y: 24 }
  let x = e.clientX - rect.left
  let y = e.clientY - rect.top
  x = Math.max(0, Math.min(x, Math.max(0, rect.width - 200)))
  y = Math.max(0, Math.min(y, Math.max(0, rect.height - 90)))
  return { x: Math.round(x), y: Math.round(y) }
}

/* ── 图表 SVG（假数据）──────────────────────────────────────────────── */
function chartSvg(d: any): string {
  const max = Math.max(...d.values)
  const w = 240
  const h = 110
  const pad = 6
  const bw = (w - pad * 2) / d.values.length
  const bars = d.values.map((v: number, i: number) => {
    const bh = (v / max) * (h - 20)
    const x = pad + i * bw
    const y = h - bh - 4
    return `<rect x="${x + 2}" y="${y}" width="${bw - 4}" height="${bh}" rx="2" fill="var(--db-accent)" opacity="${0.55 + 0.45 * (v / max)}"></rect>`
  }).join('')
  return `<svg class="chart-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${bars}</svg>`
}
</script>

<template>
  <div class="ccp" :class="mode === 'preview' ? 'is-preview' : ''">
    <!-- 顶栏 -->
    <div class="ccp-toolbar">
      <el-input v-model="dashboardTitle" class="ccp-title" size="default" />
      <span class="ccp-spacer" />
      <el-button @click="togglePreview">{{ mode === 'edit' ? '▶ 预览' : '✎ 返回编辑' }}</el-button>
      <el-button type="primary" @click="doSave">保存</el-button>
    </div>

    <!-- 三栏 -->
    <div class="ccp-body">
      <!-- 物料面板 -->
      <aside class="ccp-palette">
        <div class="ccp-panel-title">组件面板</div>
        <div class="ccp-group">
          <div class="ccp-group-h">基础组件</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'kpi')" @dragend="onPaletteDragEnd"><span class="pi">📊</span>KPI 卡片</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'chart')" @dragend="onPaletteDragEnd"><span class="pi">📈</span>图表</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'table')" @dragend="onPaletteDragEnd"><span class="pi">🗂</span>数据表格</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'filter')" @dragend="onPaletteDragEnd"><span class="pi">🔎</span>筛选项</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'timeFilter')" @dragend="onPaletteDragEnd"><span class="pi">🕑</span>时间筛选</div>
          <div class="ccp-item" draggable="true" @dragstart="onPaletteDragStart($event, 'ai')" @dragend="onPaletteDragEnd"><span class="pi">🤖</span>AI 分析</div>
        </div>
        <div class="ccp-group">
          <div class="ccp-group-h">布局组件</div>
          <div class="ccp-item layout" draggable="true" @dragstart="onPaletteDragStart($event, 'container')" @dragend="onPaletteDragEnd"><span class="pi">🧩</span>组合卡片 / 卡片容器</div>
        </div>
      </aside>

      <!-- 画布 -->
      <main class="ccp-canvas">
        <div class="ccp-stage">
          <div
            ref="cardContainerRef"
            class="card-container"
            :class="{ selected: selection === 'container', 'mode-grid': container.layout.mode === 'grid', 'mode-vertical': container.layout.mode === 'vertical', 'palette-over': paletteOver }"
            :style="{
              width: container.size.width + 'px',
              minHeight: container.size.height + 'px',
              background: container.style.background,
              borderRadius: container.style.radius + 'px',
              padding: container.style.padding + 'px',
              borderColor: container.style.border.enabled ? container.style.border.color : 'transparent',
              '--cc-cols': container.layout.columns,
              '--cc-gap': container.layout.gap + 'px',
            }"
            @click="selectContainer"
            @dragenter="onBodyDragEnter"
            @dragleave="onBodyDragLeave"
            @dragover="onBodyDragOver"
            @drop="onBodyDrop"
          >
            <!-- 头部（红框1：原工具栏按钮已删除，添加组件/页签/设置等入口保留在别处） -->
            <div class="cc-header">
              <span class="cc-title" v-if="container.titleBarStyle !== 'hidden'">{{ container.title }}</span>
              <span class="cc-title muted" v-else>（标题栏已隐藏）</span>
            </div>

            <!-- 页签 -->
            <div class="cc-tabs">
              <div
                v-for="tab in container.tabs"
                :key="tab.id"
                class="cc-tab"
                :class="{ active: tab.id === container.activeTab }"
                @click.stop="selectTab(tab.id)"
              >
                <input
                  v-if="mode === 'edit' && editingTab === tab.id"
                  class="tab-edit"
                  :value="tab.title"
                  @click.stop
                  @input="tab.title = ($event.target as HTMLInputElement).value"
                  @blur="editingTab = null"
                  @keyup.enter="editingTab = null"
                />
                <span v-else @dblclick.stop="mode === 'edit' && (editingTab = tab.id)">{{ tab.title }}</span>
                <!-- 红框2：页签右上角删除 x（编辑态可见；删除逻辑见 deleteTab 约束） -->
                <button v-if="mode === 'edit'" class="tab-x" @click.stop="deleteTab(tab.id)" title="删除页签">✕</button>
              </div>
              <button class="cc-tab-add" v-if="mode === 'edit'" @click.stop="addTab">+</button>
            </div>

            <!-- 主体：自由布局时作为绝对定位参考系；整卡拖入落区已上移到 .card-container -->
            <div
              ref="ccBodyRef"
              class="cc-body"
              :class="{ 'mode-free': container.layout.mode === 'free' }"
            >
              <div v-if="activeChildren.length" class="cc-grid" :class="{ 'mode-free': container.layout.mode === 'free' }">
                <div
                  v-for="child in activeChildren"
                  :key="child.id"
                  class="widget"
                  :class="{ selected: isSelectedChild(child.id), moving: movingId === child.id }"
                  :style="widgetStyle(child)"
                  :data-child="child.id"
                  @click.stop="selectChild(child.id)"
                  @mouseenter="hoverChildId = child.id"
                  @mouseleave="hoverChildId = null"
                  @mousedown="onChildMouseDown($event, child)"
                >
                  <div class="w-head">
                    <span class="w-title">{{ child.title }}</span>
                    <button v-if="mode === 'edit'" class="w-del" @click.stop="deleteChild(child.id)" title="删除">✕</button>
                  </div>

                  <!-- 演示状态：加载中 -->
                  <template v-if="demoState === 'loading'">
                    <div class="skeleton" style="height:14px;width:50%"></div>
                    <div class="skeleton" style="height:40px;margin-top:10px"></div>
                    <div class="skeleton" style="height:20px;margin-top:8px;width:70%"></div>
                  </template>

                  <div v-else-if="demoState === 'empty'" class="state-box">📭<div>当前组件暂无数据</div></div>

                  <div v-else-if="demoState === 'error'" class="state-box">
                    <span class="err-ic">⚠</span><div>数据加载失败</div>
                    <div style="font-size:12px">请检查数据集配置或筛选条件</div>
                    <div style="display:flex;gap:8px">
                      <el-button size="small" @click.stop="handleChildAction('retry')">重试</el-button>
                      <el-button size="small" text @click.stop="handleChildAction('view-cfg')">查看配置</el-button>
                    </div>
                  </div>

                  <!-- 正常渲染 -->
                  <template v-else>
                    <!-- KPI -->
                    <div v-if="child.type === 'kpi'">
                      <div class="kpi-val">{{ child.data.value }}</div>
                      <div class="kpi-label">{{ child.data.label }}</div>
                      <div class="kpi-trend" :class="child.data.dir" v-if="child.data.trend">
                        {{ child.data.dir === 'up' ? '▲' : '▼' }} {{ child.data.trend }}
                      </div>
                    </div>
                    <!-- 图表 -->
                    <div v-else-if="child.type === 'chart'" v-html="chartSvg(child.data)" />
                    <!-- 表格 -->
                    <div v-else-if="child.type === 'table'">
                      <table class="mini">
                        <thead><tr><th v-for="h in child.data.head" :key="h">{{ h }}</th></tr></thead>
                        <tbody>
                          <tr v-for="(row, ri) in child.data.rows" :key="ri">
                            <td v-for="(x, xi) in row" :key="xi">{{ x }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <!-- AI -->
                    <div v-else-if="child.type === 'ai'" class="ai-box">
                      <div v-for="b in child.data.blocks" :key="b.h">
                        <h4>{{ b.h }}</h4><p>{{ b.p }}</p>
                      </div>
                      <div class="ai-disclaimer">* 本分析由 AI 基于当前数据集生成，仅供参考。</div>
                    </div>
                    <!-- 筛选 -->
                    <div v-else-if="child.type === 'filter'" class="filter-row" @click.stop="onFilterSeg">
                      <button class="seg-btn on" data-seg>全部</button>
                      <button class="seg-btn" data-seg>新客</button>
                      <button class="seg-btn" data-seg>召回</button>
                      <button class="seg-btn" data-seg>沉睡</button>
                    </div>
                    <!-- 时间筛选 -->
                    <div v-else-if="child.type === 'timeFilter'" class="filter-row">
                      <el-date-picker type="date" placeholder="开始" size="small" />
                      <span>~</span>
                      <el-date-picker type="date" placeholder="结束" size="small" />
                      <el-button size="small" @click.stop="handleChildAction('apply-time')">应用</el-button>
                    </div>
                    <!-- 按钮 -->
                    <el-button v-else-if="child.type === 'button'" type="primary" size="small" @click.stop="handleChildAction('op-click')">{{ child.title }}</el-button>
                    <!-- 嵌套容器占位 -->
                    <div v-else-if="child.type === 'container'" class="state-box">🧩 嵌套组合卡片（演示占位）</div>
                  </template>

                  <template v-if="mode === 'edit' && (isSelectedChild(child.id) || hoverChildId === child.id || resizingId === child.id || movingId === child.id)">
                    <span
                      v-for="h in (['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const)"
                      :key="h"
                      class="rs"
                      :class="h"
                      @mousedown.stop.prevent="onChildResizeDown($event, child, h)"
                      @dragstart.stop.prevent
                    />
                  </template>
                </div>
              </div>

              <!-- 空页签 -->
              <div v-else class="cc-empty">
                <div>🧩 当前页签暂无组件</div>
                <div style="font-size:12px">拖入组件或点击下方按钮开始配置</div>
                <el-button v-if="mode === 'edit'" type="primary" size="small" @click="showAdd = true">添加组件</el-button>
              </div>

            </div>

            <!-- 拖入落区提示（整卡：物料拖入悬停时显示，覆盖全部内容区） -->
            <div v-if="paletteOver" class="drop-hint"><span class="drop-hint-pill">松开鼠标，添加到「{{ activeTabObj.title }}」</span></div>

            <!-- 缩放手柄 -->
            <template v-if="selection === 'container' && mode === 'edit'">
              <span class="resize-handle nw" @pointerdown="onResizeDown($event, 'nw')" />
              <span class="resize-handle ne" @pointerdown="onResizeDown($event, 'ne')" />
              <span class="resize-handle sw" @pointerdown="onResizeDown($event, 'sw')" />
              <span class="resize-handle se" @pointerdown="onResizeDown($event, 'se')" />
              <span class="size-tip">宽 {{ container.size.width }}px × 高 {{ container.size.height }}px</span>
            </template>
          </div>
        </div>

        <!-- 演示状态切换 -->
        <div v-if="mode === 'edit'" class="ccp-demo-bar">
          <span>演示子组件状态：</span>
          <el-select v-model="demoState" size="small" style="width:140px">
            <el-option label="正常加载" value="loaded" />
            <el-option label="加载中（骨架屏）" value="loading" />
            <el-option label="无数据" value="empty" />
            <el-option label="加载失败" value="error" />
          </el-select>
        </div>
      </main>

      <!-- 属性面板 -->
      <aside class="ccp-property">
        <!-- 容器属性 -->
        <template v-if="selection === 'container'">
          <div class="ccp-panel-title">组合容器属性</div>

          <section class="prop-section" :class="{ collapsed: collapsed.basic }">
            <button class="sh" @click="collapsed.basic = !collapsed.basic">基础信息 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.basic">
              <div class="field"><label>容器标题</label><el-input v-model="container.title" size="small" /></div>
              <div class="field"><label>容器描述</label><el-input placeholder="展示策略执行相关指标" size="small" /></div>
            </div>
          </section>

          <section class="prop-section" :class="{ collapsed: collapsed.size }">
            <button class="sh" @click="collapsed.size = !collapsed.size">尺寸与位置 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.size">
              <div class="field"><label>尺寸（px）</label>
                <div class="inline"><span>宽</span><el-input v-model.number="container.size.width" size="small" style="width:80px" /><span>高</span><el-input v-model.number="container.size.height" size="small" style="width:80px" /></div>
              </div>
              <div class="field row"><label>适应内容高度</label><el-switch v-model="fitContent" /></div>
              <div class="field row"><label>锁定宽高比</label><el-switch v-model="lockRatio" /></div>
            </div>
          </section>

          <section class="prop-section" :class="{ collapsed: collapsed.layout }">
            <button class="sh" @click="collapsed.layout = !collapsed.layout">内部布局 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.layout">
              <div class="field"><label>布局方式</label>
                <el-select v-model="container.layout.mode" size="small">
                  <el-option label="网格布局" value="grid" />
                  <el-option label="垂直流式" value="vertical" />
                  <el-option label="自由布局" value="free" />
                </el-select>
              </div>
              <div class="field row"><label>列数</label><el-input v-model.number="container.layout.columns" type="number" size="small" style="width:80px" /></div>
              <div class="field row"><label>间距(px)</label><el-input v-model.number="container.layout.gap" type="number" size="small" style="width:80px" /></div>
              <div class="field"><label>子组件对齐</label>
                <el-select v-model="container.layout.align" size="small">
                  <el-option label="左对齐" value="left" />
                  <el-option label="居中" value="center" />
                  <el-option label="右对齐" value="right" />
                </el-select>
              </div>
            </div>
          </section>

          <section class="prop-section" :class="{ collapsed: collapsed.appear }">
            <button class="sh" @click="collapsed.appear = !collapsed.appear">外观样式 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.appear">
              <div class="field"><label>背景</label>
                <el-select :model-value="isCustomBackground ? 'custom' : container.style.background" size="small" @change="changeBackground">
                  <el-option label="白色" value="#FFFFFF" /><el-option label="跟随主题" value="var(--db-card)" /><el-option label="浅橙" value="var(--db-card-orange-bg)" />
                  <el-option label="自定义" value="custom" />
                </el-select>
              </div>
              <div v-if="isCustomBackground" class="field">
                <InsightColorField v-model="container.style.background" label="自定义背景色" :suggested-colors="CARD_BG_PRESETS" />
              </div>
              <div class="field row"><label>显示边框</label><el-switch v-model="container.style.border.enabled" />
                <InsightColorField v-model="container.style.border.color" label="边框颜色" /><el-input v-model.number="container.style.border.width" type="number" size="small" style="width:54px" />
              </div>
              <div class="field row"><label>圆角(px)</label><el-input v-model.number="container.style.radius" type="number" size="small" style="width:80px" /></div>
              <div class="field row"><label>内边距(px)</label><el-input v-model.number="container.style.padding" type="number" size="small" style="width:80px" /></div>
              <div class="field"><label>阴影</label>
                <el-select v-model="container.style.shadow" size="small"><el-option label="无" value="none" /><el-option label="卡片阴影" value="card" /></el-select>
              </div>
            </div>
          </section>

          <section class="prop-section" :class="{ collapsed: collapsed.tabs }">
            <button class="sh" @click="collapsed.tabs = !collapsed.tabs">页签配置 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.tabs">
              <div v-for="tab in container.tabs" :key="tab.id" class="tab-row">
                <span class="grip">⠿</span>
                <el-input v-model="tab.title" size="small" class="nm" />
                <el-button v-if="tab.id !== container.defaultTab" size="small" text type="primary" @click="setDefaultTab(tab.id)">设默认</el-button>
                <span v-else style="font-size:11px;color:var(--db-accent)">默认</span>
                <el-button size="small" text @click="copyTab(tab.id)">复制</el-button>
                <el-button size="small" text type="danger" @click="deleteTab(tab.id)">删除</el-button>
              </div>
              <el-button size="small" text type="primary" @click="addTab">＋ 添加页签</el-button>
            </div>
          </section>

          <section class="prop-section" :class="{ collapsed: collapsed.scope }">
            <button class="sh" @click="collapsed.scope = !collapsed.scope">参数与事件作用域 <span class="ar">▾</span></button>
            <div class="prop-body" v-show="!collapsed.scope">
              <div class="field"><label>参数绑定</label></div>
              <div v-for="p in container.scope.parameters" :key="p.id" class="bind-row">
                <span class="bind-cell">{{ p.name }}</span>
                <span class="bind-cell">{{ p.source }}</span>
                <span class="bind-cell">{{ p.scope }}</span>
                <el-button size="small" text type="danger" @click="container.scope.parameters = container.scope.parameters.filter((x) => x.id !== p.id)">✕</el-button>
              </div>
              <el-button size="small" text type="primary" @click="container.scope.parameters.push({ id: uid('p'), name: '新参数', source: '固定值', scope: '全部子组件', overridable: true })">＋ 添加参数绑定</el-button>

              <div class="field" style="margin-top:8px"><label>事件绑定</label></div>
              <div v-for="e in container.scope.events" :key="e.id" class="bind-row">
                <span class="bind-cell">{{ e.trigger }}</span>
                <span class="bind-cell">{{ e.action }}</span>
                <span class="bind-cell">{{ e.target }}</span>
                <el-button size="small" text type="danger" @click="container.scope.events = container.scope.events.filter((x) => x.id !== e.id)">✕</el-button>
              </div>
              <el-button size="small" text type="primary" @click="container.scope.events.push({ id: uid('e'), trigger: '点击', action: '刷新当前容器', target: '当前容器' })">＋ 添加事件绑定</el-button>

              <div class="scope-tree">
                <div>［时间范围］</div>
                <div class="lvl1">│ 作用于</div>
                <div class="lvl1">└─ <span class="node">{{ container.title }}</span></div>
                <div class="lvl2">├─ 策略概括</div><div class="lvl2">├─ 策略执行</div><div class="lvl2">└─ 策略贡献</div>
              </div>
              <div class="conflict-tip">⚠ 当前参数同时绑定到容器和子组件，子组件配置将覆盖容器配置。<el-button text size="small">查看绑定关系</el-button></div>
            </div>
          </section>
        </template>

        <!-- 子组件属性 -->
        <template v-else-if="selection === 'child' && selectChildFromState()">
          <div class="ccp-panel-title">子组件属性 · {{ selectChildFromState()!.title }}</div>
          <section class="prop-section">
            <button class="sh">基础 <span class="ar">▾</span></button>
            <div class="prop-body">
              <div class="field"><label>标题</label><el-input v-model="selectChildFromState()!.title" size="small" /></div>
              <div class="field"><label>占用列数(1-12)</label><el-input v-model.number="selectChildFromState()!.col" type="number" min="1" max="12" size="small" style="width:80px" /></div>
            </div>
          </section>
          <section class="prop-section">
            <button class="sh">数据（假数据·待对接） <span class="ar">▾</span></button>
            <div class="prop-body">
              <div class="conflict-tip" style="border-color:var(--db-border);background:var(--db-hover);color:var(--db-text-secondary)">
                数据对接点：子组件数据来自 FAKE_DATA，接入后端后由数据集 / 筛选上下文提供。
              </div>
            </div>
          </section>
        </template>

        <!-- 画布属性 -->
        <template v-else>
          <div class="ccp-panel-title">属性配置</div>
          <div style="padding:16px;color:var(--db-text-muted)">点击画布中的组合卡片或子组件以编辑其属性。</div>
        </template>
      </aside>
    </div>

    <!-- 添加组件弹窗 -->
    <el-dialog v-model="showAdd" title="添加到：组合卡片 / 页签" width="440px">
      <div class="picker-group"><div class="pg-title">数据展示</div>
        <div class="picker-grid">
          <div class="picker-item" @click="addChild('kpi')"><span class="pi">📊</span>KPI 卡片</div>
          <div class="picker-item" @click="addChild('chart')"><span class="pi">📈</span>图表</div>
          <div class="picker-item" @click="addChild('table')"><span class="pi">🗂</span>数据表格</div>
        </div>
      </div>
      <div class="picker-group"><div class="pg-title">交互组件</div>
        <div class="picker-grid">
          <div class="picker-item" @click="addChild('filter')"><span class="pi">🔎</span>筛选项</div>
          <div class="picker-item" @click="addChild('timeFilter')"><span class="pi">🕑</span>时间筛选</div>
          <div class="picker-item" @click="addChild('button')"><span class="pi">⚙</span>操作按钮</div>
        </div>
      </div>
      <div class="picker-group"><div class="pg-title">智能分析</div>
        <div class="picker-grid"><div class="picker-item" @click="addChild('ai')"><span class="pi">🤖</span>AI 分析</div></div>
      </div>
      <div class="picker-group"><div class="pg-title">布局组件</div>
        <div class="picker-grid"><div class="picker-item" @click="addChild('container')"><span class="pi">🧩</span>组合卡片 / 卡片容器</div></div>
      </div>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ccp { height: 100%; display: flex; flex-direction: column; background: var(--db-bg); overflow: hidden; }
.ccp-toolbar { height: 56px; flex-shrink: 0; display: flex; align-items: center; gap: 10px; padding: 0 16px; background: var(--db-card); border-bottom: 1px solid var(--db-border); }
.ccp-title { width: 240px; }
.ccp-title :deep(.el-input__wrapper) { box-shadow: none; background: var(--db-hover); }
.ccp-spacer { flex: 1; }
.ccp-body { flex: 1; display: flex; gap: 12px; padding: 12px 16px; overflow: hidden; }

.ccp-panel-title { font-size: 12px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; color: var(--db-text-muted); padding: 12px 14px; border-bottom: 1px solid var(--db-border); }

/* 物料面板 */
.ccp-palette { width: 220px; flex-shrink: 0; background: var(--db-card); border: 1px solid var(--db-border); border-radius: 12px; overflow-y: auto; }
.ccp-group { border-bottom: 1px solid var(--db-border); }
.ccp-group-h { padding: 10px 14px; font-size: 12px; font-weight: 600; color: var(--db-text-secondary); }
.ccp-item { display: flex; align-items: center; gap: 10px; padding: 8px 14px; font-size: 13px; color: var(--db-text); border: 1px solid transparent; border-radius: 6px; margin: 2px 8px; cursor: grab; }
.ccp-item:hover { background: var(--db-hover); border-color: var(--db-border); }
.ccp-item.layout { border: 1px dashed var(--db-accent); background: var(--db-accent-light); }
.ccp-item .pi { width: 18px; text-align: center; }

/* 画布 */
.ccp-canvas { flex: 1; min-width: 0; overflow: auto; background: var(--db-bg); border: 1px solid var(--db-border); border-radius: 12px; position: relative; }
.ccp-stage { min-height: 100%; display: flex; align-items: flex-start; justify-content: center; padding: 24px; }

.card-container {
  position: relative; border: 1px solid var(--db-border); box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-base);
  /* flex 纵向布局：让 .cc-body 撑满容器剩余高度，子组件才能拖到容器底部 */
  display: flex; flex-direction: column;
  --cc-cols: 12; --cc-gap: 12px;
}
.card-container.selected { border-color: var(--db-accent); box-shadow: 0 0 0 3px var(--db-accent-light); }
.cc-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.cc-title { font-size: 15px; font-weight: 600; }
.cc-title.muted { color: var(--db-text-muted); }

.cc-tabs { display: flex; align-items: center; gap: 4px; border-bottom: 1px solid var(--db-border); margin-bottom: 12px; flex-wrap: wrap; }
.cc-tab { display: inline-flex; align-items: center; padding: 7px 12px; border: none; background: transparent; color: var(--db-text-secondary); font-size: 13px; border-bottom: 2px solid transparent; cursor: pointer; }
.cc-tab:hover { color: var(--db-accent); }
.cc-tab.active { color: var(--db-accent); border-bottom-color: var(--db-accent); font-weight: 600; }
.tab-edit { width: 64px; border: 1px solid var(--db-accent); border-radius: 4px; padding: 2px 4px; font-size: 12px; }
.cc-tab-add { border: none; background: transparent; color: var(--db-text-muted); font-size: 18px; width: 28px; height: 28px; border-radius: 6px; cursor: pointer; }
.cc-tab-add:hover { background: var(--db-hover); color: var(--db-accent); }
.tab-x { margin-left: 6px; border: none; background: transparent; color: var(--db-text-muted); font-size: 12px; line-height: 1; cursor: pointer; opacity: 0; transition: opacity var(--transition-fast); }
.cc-tab:hover .tab-x, .cc-tab.active .tab-x { opacity: 1; }
.tab-x:hover { color: var(--danger-color); }

.cc-body { min-height: 120px; position: relative; flex: 1 1 auto; }
/* 整卡落区高亮（拖入物料悬停时，.card-container 加 palette-over） */
.card-container.palette-over { outline: 2px dashed var(--db-accent); outline-offset: -4px; border-radius: 12px; }
.drop-hint { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(99, 102, 241, .10); border-radius: 8px; pointer-events: none; z-index: 9; }
.drop-hint-pill { background: var(--db-accent); color: #fff; font-size: 13px; font-weight: 600; padding: 6px 14px; border-radius: 16px; box-shadow: var(--shadow-dropdown); }
.cc-grid { display: grid; gap: var(--cc-gap); grid-template-columns: repeat(var(--cc-cols), 1fr); }
.cc-grid.mode-vertical { display: flex; flex-direction: column; }
/* 自由布局：内容区作为绝对定位参考系，子组件用 left/top 摆放（不再顺序填充）；高度跟随 .cc-body 撑满 */
.cc-grid.mode-free { display: block; position: relative; min-height: 100%; }

.cc-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; min-height: 280px; color: var(--db-text-muted); text-align: center; border: 1px dashed var(--db-border); border-radius: 8px; padding: 24px; }

/* 子组件 */
.widget { grid-column: span var(--w-col, 6); box-sizing: border-box; border: 1px solid var(--db-border); border-radius: 8px; background: var(--db-card); padding: 12px; position: relative; min-height: 90px; cursor: pointer; transition: box-shadow var(--transition-base); }
.widget.moving { cursor: grabbing; z-index: 6; box-shadow: var(--shadow-card-hover); }
.widget:hover { box-shadow: var(--shadow-card-hover); }
.widget.selected { border-color: var(--db-accent); box-shadow: 0 0 0 2px var(--db-accent-light); z-index: 7; }
.w-head { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.w-title { font-size: 13px; font-weight: 600; }
.w-del { margin-left: auto; border: none; background: transparent; color: var(--db-text-muted); font-size: 14px; cursor: pointer; }
.w-del:hover { color: var(--danger-color); }
/* 子组件 8 向缩放手柄：四角改宽高、左右边改宽、上下边改高（红框4） */
.rs { position: absolute; width: 14px; height: 14px; background: var(--db-accent); border: 2px solid #fff; border-radius: 50%; z-index: 6; box-shadow: 0 1px 4px rgba(0, 0, 0, .25); }
.rs:hover { background: #4f46e5; }
.rs.nw { left: -7px; top: -7px; cursor: nwse-resize; }
.rs.n  { left: 50%; top: -7px; transform: translateX(-50%); cursor: ns-resize; }
.rs.ne { right: -7px; top: -7px; cursor: nesw-resize; }
.rs.e  { right: -7px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }
.rs.se { right: -7px; bottom: -7px; cursor: nwse-resize; }
.rs.s  { left: 50%; bottom: -7px; transform: translateX(-50%); cursor: ns-resize; }
.rs.sw { left: -7px; bottom: -7px; cursor: nesw-resize; }
.rs.w  { left: -7px; top: 50%; transform: translateY(-50%); cursor: ew-resize; }

.kpi-val { font-size: 28px; font-weight: 700; line-height: 1.1; }
.kpi-label { color: var(--db-text-secondary); font-size: 12px; margin-top: 2px; }
.kpi-trend { font-size: 12px; margin-top: 6px; display: inline-flex; align-items: center; gap: 4px; }
.kpi-trend.up { color: var(--db-card-green-fg); }
.kpi-trend.down { color: var(--danger-color); }
.chart-svg { width: 100%; height: 120px; display: block; }
table.mini { width: 100%; border-collapse: collapse; font-size: 12px; }
table.mini th, table.mini td { border: 1px solid var(--db-border); padding: 5px 8px; text-align: left; }
table.mini th { background: var(--db-hover); font-weight: 600; }
.ai-box { font-size: 12px; line-height: 1.7; color: var(--db-text-secondary); }
.ai-box h4 { margin: 8px 0 2px; font-size: 12px; color: var(--db-text); }
.ai-disclaimer { margin-top: 8px; font-size: 11px; color: var(--db-text-muted); border-top: 1px dashed var(--db-border); padding-top: 6px; }
.filter-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.seg-btn { border: 1px solid var(--db-border); background: var(--db-card); padding: 5px 10px; font-size: 12px; color: var(--db-text-secondary); border-radius: 6px; cursor: pointer; }
.seg-btn.on { background: var(--db-accent); color: #fff; border-color: var(--db-accent); }

.skeleton { background: linear-gradient(90deg, var(--db-hover) 25%, var(--db-card) 37%, var(--db-hover) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; border-radius: 6px; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
.state-box { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; min-height: 110px; color: var(--db-text-muted); text-align: center; }
.state-box .err-ic { color: var(--danger-color); font-size: 22px; }

/* 缩放手柄 */
.resize-handle { position: absolute; width: 12px; height: 12px; background: var(--db-accent); border: 2px solid #fff; border-radius: 50%; z-index: 5; cursor: nwse-resize; }
.resize-handle.nw { left: -6px; top: -6px; }
.resize-handle.ne { right: -6px; top: -6px; }
.resize-handle.sw { left: -6px; bottom: -6px; }
.resize-handle.se { right: -6px; bottom: -6px; }
.size-tip { position: absolute; bottom: -28px; left: 50%; transform: translateX(-50%); background: var(--db-accent); color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px; white-space: nowrap; pointer-events: none; }

/* 属性面板 */
.ccp-property { width: 300px; flex-shrink: 0; background: var(--db-card); border: 1px solid var(--db-border); border-radius: 12px; overflow-y: auto; }
.prop-section { border-bottom: 1px solid var(--db-border); }
.prop-section > .sh { display: flex; align-items: center; width: 100%; padding: 11px 14px; background: transparent; border: none; text-align: left; font-size: 13px; font-weight: 600; color: var(--db-text); cursor: pointer; }
.prop-section > .sh .ar { margin-left: auto; color: var(--db-text-muted); transition: transform var(--transition-fast); }
.prop-section.collapsed > .sh .ar { transform: rotate(-90deg); }
.prop-body { padding: 4px 14px 14px; display: flex; flex-direction: column; gap: 10px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field > label { font-size: 12px; color: var(--db-text-secondary); }
.field.row { flex-direction: row; align-items: center; }
.field.row > label { flex: 1; }
.field .inline { display: flex; align-items: center; gap: 6px; }
.tab-row { display: flex; align-items: center; gap: 6px; padding: 7px 8px; border: 1px solid var(--db-border); border-radius: 6px; }
.tab-row .grip { cursor: grab; color: var(--db-text-muted); }
.tab-row .nm { flex: 1; }
.bind-row { display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 6px; align-items: center; }
.bind-cell { font-size: 12px; padding: 4px 6px; border: 1px solid var(--db-border); border-radius: 6px; background: var(--db-hover); color: var(--db-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scope-tree { font-size: 12px; color: var(--db-text-secondary); line-height: 1.9; background: var(--db-hover); border-radius: 6px; padding: 10px 12px; }
.scope-tree .node { display: inline-block; padding: 1px 8px; border-radius: 10px; background: var(--db-accent-light); color: var(--db-accent); }
.scope-tree .lvl1 { padding-left: 16px; }
.scope-tree .lvl2 { padding-left: 32px; }
.conflict-tip { font-size: 12px; color: var(--warning-color); background: #FFFBE6; border: 1px solid #FFE58F; border-radius: 6px; padding: 8px 10px; }

/* 演示条 */
.ccp-demo-bar { position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 6px; background: var(--db-card); border: 1px solid var(--db-border); border-radius: 20px; padding: 6px 12px; box-shadow: var(--shadow-dropdown); font-size: 12px; color: var(--db-text-muted); }

/* 预览态：隐藏编辑控件（保留真实交互） */
.ccp.is-preview .ccp-palette,
.ccp.is-preview .ccp-property,
.ccp.is-preview .ccp-demo-bar { display: none; }
.ccp.is-preview .card-container { cursor: default; }
.ccp.is-preview .resize-handle,
.ccp.is-preview .rs,
.ccp.is-preview .w-del { display: none !important; }
</style>
