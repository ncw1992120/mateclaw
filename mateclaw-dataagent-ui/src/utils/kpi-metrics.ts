/**
 * kpi-metrics —— KPI 指标分组卡片的纯逻辑层
 * =====================================================================
 * 口径（见 docs/策略解读/指标分组卡片原型设计.md）：
 *   - 「结果集优先」：指标由「最终结果集」字段**逐列投影**生成；
 *     用户**不能**手动新增 / 删除指标，字段增删由 schema 驱动（增量同步）。
 *   - 每个指标可配置展示列名、单位（手动填写）、辅助说明、显示状态，
 *     以及 展示名 / 指标值 / 单位 / 辅助说明 四个字段各自的样式（大小 / 字体 / 加粗 / 颜色）。
 *   - 指标在卡片内为像素级自由布局（x/y/w/h）。
 *
 * 本文件只做确定性计算，不访问后端、不依赖 Vue，便于单测覆盖。
 */

import type { KpiMetricConfig, KpiMetricFieldStyle, KpiMetricStyles, KpiMetricVisualConfig, ResolvedDashboardTheme } from '@/types'
import type { DatasetFieldMeta } from './field-mapping'
import { DASHBOARD_ICON_REGISTRY } from './dashboard-icon-registry'

/** 指标可配置字段 */
export type KpiMetricField = 'name' | 'value' | 'unit' | 'helper'

/** 指标字段的固定顺序 */
export const KPI_METRIC_FIELDS: KpiMetricField[] = ['name', 'value', 'unit', 'helper']

/** 指标字段的中文名（样式弹窗下拉 / 表头使用） */
export const KPI_FIELD_LABELS: Record<KpiMetricField, string> = {
  name: '展示名',
  value: '指标值',
  unit: '单位',
  helper: '辅助说明',
}

/**
 * 字体键 → CSS font-family。
 * 注意：字体名统一使用**单引号**，避免被拼进 HTML style 属性时提前闭合（原型踩过的坑）。
 */
export const KPI_FONT_FAMILY: Record<string, string> = {
  system: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
  pingfang: "'PingFang SC', 'Source Han Sans SC', sans-serif",
  yahei: "'Microsoft YaHei', sans-serif",
  song: "'Songti SC', SimSun, serif",
  heiti: "'Heiti SC', 'SimHei', sans-serif",
  kaiti: "'Kaiti SC', 'KaiTi', STKaiti, serif",
  mono: 'ui-monospace, Menlo, Consolas, monospace',
  arial: 'Arial, Helvetica, sans-serif',
  helvetica: 'Helvetica, Arial, sans-serif',
  verdana: 'Verdana, Geneva, sans-serif',
  georgia: "Georgia, 'Times New Roman', serif",
  times: "'Times New Roman', Times, serif",
  courier: "'Courier New', Courier, monospace",
  roboto: "Roboto, 'PingFang SC', sans-serif",
}

/** 字体下拉项（样式弹窗渲染用，顺序即展示顺序） */
export const KPI_FONT_OPTIONS: Array<{ key: string; label: string }> = [
  { key: 'system', label: '系统默认' },
  { key: 'pingfang', label: '苹方 / 思源黑体' },
  { key: 'yahei', label: '微软雅黑' },
  { key: 'song', label: '宋体' },
  { key: 'heiti', label: '黑体' },
  { key: 'kaiti', label: '楷体' },
  { key: 'mono', label: '等宽' },
  { key: 'arial', label: 'Arial' },
  { key: 'helvetica', label: 'Helvetica' },
  { key: 'verdana', label: 'Verdana' },
  { key: 'georgia', label: 'Georgia' },
  { key: 'times', label: 'Times New Roman' },
  { key: 'courier', label: 'Courier New' },
  { key: 'roboto', label: 'Roboto' },
]

/** 四个字段的出厂字号。既是 `defaultMetricStyles()` 的唯一来源，也是「字号是否被用户显式改过」的判定基准
 *  （见 `isDefaultMetricFontSize` / 自动铺排的字号适配）。 */
export const KPI_DEFAULT_FONT_SIZE: Record<KpiMetricField, number> = {
  name: 14,
  value: 28,
  unit: 15,
  helper: 12,
}

/** 单个字段样式 */
export function fieldStyle(size: number, color: string, bold: 'bold' | 'normal' = 'normal'): KpiMetricFieldStyle {
  return { size, family: 'system', color, colorMode: color ? 'custom' : 'theme', bold }
}

/** 默认样式：指标值加粗，其余常规 */
export function defaultMetricStyles(): KpiMetricStyles {
  return {
    name: fieldStyle(KPI_DEFAULT_FONT_SIZE.name, '', 'normal'),
    value: fieldStyle(KPI_DEFAULT_FONT_SIZE.value, '', 'bold'),
    unit: fieldStyle(KPI_DEFAULT_FONT_SIZE.unit, '', 'normal'),
    helper: fieldStyle(KPI_DEFAULT_FONT_SIZE.helper, '', 'normal'),
  }
}

/** 深拷贝样式（避免多个指标共享同一对象引用） */
export function cloneStyles(styles: KpiMetricStyles): KpiMetricStyles {
  return {
    name: { ...styles.name },
    value: { ...styles.value },
    unit: { ...styles.unit },
    helper: { ...styles.helper },
  }
}

/** 字段样式 → 内联 CSS（供卡片渲染） */
export function styleToCss(style: KpiMetricFieldStyle, themeColor?: string): string {
  const family = KPI_FONT_FAMILY[style.family] ?? KPI_FONT_FAMILY.system
  const color = style.colorMode === 'theme' && themeColor ? themeColor : style.color
  return `font-size:${style.size}px;color:${color};font-family:${family};font-weight:${style.bold || 'normal'};`
}

/** 解析指标的图标、强调色和四个文字字段颜色；不访问数据源。
 * 强调色默认取区域主色（同一张卡内所有指标同色）：一卡跑五个色系是纯噪声，
 * 第三个色号只由用户在指标样式弹窗里显式 custom 产生。图标仍按序号轮转做形状区分。 */
export function resolveMetricVisual(
  metric: KpiMetricConfig,
  componentVisual: KpiMetricVisualConfig | undefined,
  theme: ResolvedDashboardTheme,
  index: number,
): { iconKey: string | null; accentColor: string; textColors: Record<KpiMetricField, string> } {
  const visual = componentVisual ?? metric.visual
  const accentColor = visual?.colorMode === 'custom' && visual.accentColor ? visual.accentColor : theme.primary
  const fieldColor = (field: KpiMetricField): string => {
    const style = metric.styles[field]
    return style.colorMode === 'theme' || !style.colorMode && !style.color ? accentColor : style.color
  }
  return {
    iconKey: visual?.iconKey === null ? null : visual?.iconKey ?? DASHBOARD_ICON_REGISTRY[index % DASHBOARD_ICON_REGISTRY.length]?.key ?? null,
    accentColor,
    textColors: { name: fieldColor('name'), value: fieldColor('value'), unit: fieldColor('unit'), helper: fieldColor('helper') },
  }
}

/** 单个指标的默认自由布局（两列错落，紧凑卡片：横向留 12px、纵向留 8px） */
export function defaultMetricLayout(index: number): Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'> {
  const col = index % 2
  const row = Math.floor(index / 2)
  return { x: col * 172, y: row * 80, w: 160, h: 72 }
}

/** 旧版默认布局尺寸（284×88、步距 296×96）。
 *  自动投影且从未被用户挪动/缩放过的指标，坐标必然精确命中这组值；
 *  用户拖动后坐标任意、缩放会改 w/h —— 都不会命中，保持用户布局不动。 */
function isLegacyDefaultLayout(m: Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'>): boolean {
  return m.w === 284 && m.h === 88 && m.x % 296 === 0 && m.y % 96 === 0
}

/** 该指标的位置/尺寸是否仍是「机器生成的默认排布」。
 *  命中当前或任一历史版本的默认公式即算 —— 用户拖动/缩放过的坐标不会命中，
 *  因此自动铺排只接管「从未被手工调整过」的卡片（见 planKpiAutoLayout）。 */
export function isMachineMetricLayout(
  metric: Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'>,
  index: number,
): boolean {
  if (isLegacyDefaultLayout(metric)) return true
  const def = defaultMetricLayout(index)
  return metric.x === def.x && metric.y === def.y && metric.w === def.w && metric.h === def.h
}

/* ── 自动铺排（按「指标个数 + 卡片可用区尺寸」计算默认排布）──────────────
 *
 * 口径（需求：「配置数据并应用后，指标个数已确定，默认就按个数与卡片大小适配
 * 字号、行数并居中摆放」）：
 *   1. 卡片可用区先按「列 × 行」等分，枚举所有列数方案；
 *   2. 只让单元格达到「舒适最小尺寸」（88×48）的方案优先参与竞争；全都达不到时
 *      退而求其次取**全局最优**（尽力而为）——面积再小也把整块收进容器并居中，
 *      而不是放弃后让持久化坐标溢出卡片；
 *   3. 评分：优先让**字号适配系数**最大（指标值尽量大），同分时取单元格长宽比
 *      更接近基准比例（160×72）的方案 —— 避免出现又扁又长的指标位；
 *   4. 单元格尺寸设上限：超过上限的部分不拉伸，留给整块**居中**；
 *   5. 字号 = 用户字号 × 适配系数（用户显式改过字号的字段不参与，见 autoFitFontSize）。
 *
 * 该函数只做几何计算，不读 DOM；可用区尺寸未知（0 / undefined）时返回 null，
 * 由调用方回落到持久化坐标，保证首帧与无布局环境（如单测 jsdom）行为不变。
 */

/** 自动铺排的「基准单元格」：出厂字号（展示名 14 / 指标值 28 / 单位 15 / 辅助说明 12）在此尺寸下排版自然。 */
export const KPI_AUTO_REF_CELL = Object.freeze({ w: 160, h: 72 })

/** 自动铺排的单元格间距（与 defaultMetricLayout 的留白口径一致）。 */
export const KPI_AUTO_GAP = Object.freeze({ x: 12, y: 12 })

/** 单元格舒适尺寸上限：超过后不再拉伸，剩余空间用于整块居中。 */
export const KPI_AUTO_MAX_CELL = Object.freeze({ w: 280, h: 96 })

/** 字号适配系数区间（上限防「大卡片小指标被拉成大字号」，下限防字号缩到不可读）。 */
export const KPI_AUTO_SCALE_MIN = 0.62
export const KPI_AUTO_SCALE_MAX = 1.5

/** 适配后的最小字号（px）。 */
export const KPI_AUTO_MIN_FONT = 9

/** 自动铺排结果（相对卡片可用区左上角，单位 px）。 */
export interface KpiAutoLayoutPlan {
  /** 列数 */
  columns: number
  /** 行数（= ceil(个数 / 列数)） */
  rows: number
  /** 单元格宽（未取整，用于字号换算） */
  cellW: number
  /** 单元格高（未取整，用于字号换算） */
  cellH: number
  /** 横向间距 */
  gapX: number
  /** 纵向间距 */
  gapY: number
  /** 整块水平居中偏移 */
  offsetX: number
  /** 整块垂直居中偏移 */
  offsetY: number
  /** 字号适配系数 */
  scale: number
}

function clampAutoScale(value: number): number {
  return Math.min(KPI_AUTO_SCALE_MAX, Math.max(KPI_AUTO_SCALE_MIN, value))
}

/**
 * 按指标个数与卡片可用区尺寸计算默认排布。
 * 尺寸未知（<= 0 / 非有限数）时返回 `null`；面积有效时**总有解**（尽力而为：
 * 连舒适档都排不下也取全局最优方案收进容器，绝不放弃 —— 放弃只会让持久化坐标溢出卡片）。
 */
export function planKpiAutoLayout(count: number, areaW: number, areaH: number): KpiAutoLayoutPlan | null {
  if (!Number.isFinite(count) || count < 1) return null
  if (!Number.isFinite(areaW) || !Number.isFinite(areaH) || areaW <= 0 || areaH <= 0) return null

  const gap = KPI_AUTO_GAP
  const refAspect = KPI_AUTO_REF_CELL.w / KPI_AUTO_REF_CELL.h

  interface Candidate {
    columns: number
    rows: number
    cellW: number
    cellH: number
    scale: number
  }

  /** 方案优劣：先比字号系数（越大越好）→ 再比单元格长宽比是否贴近基准 → 最后比行数（越少越好）。 */
  const isBetter = (next: Candidate, current: Candidate): boolean => {
    if (Math.abs(next.scale - current.scale) > 1e-6) return next.scale > current.scale
    const nextDeviation = Math.abs(Math.log(next.cellW / next.cellH / refAspect))
    const currentDeviation = Math.abs(Math.log(current.cellW / current.cellH / refAspect))
    if (Math.abs(nextDeviation - currentDeviation) > 1e-6) return nextDeviation < currentDeviation
    if (next.rows !== current.rows) return next.rows < current.rows
    return next.columns < current.columns
  }

  let chosen: Candidate | null = null

  for (let columns = 1; columns <= count; columns++) {
    const rows = Math.ceil(count / columns)
    const slotW = (areaW - (columns - 1) * gap.x) / columns
    const slotH = (areaH - (rows - 1) * gap.y) / rows
    if (!(slotW > 0) || !(slotH > 0)) continue

    // 上限制约的是「拉伸」，不限「收缩」：小卡片必须继续收缩才放得下。
    const cellW = Math.min(slotW, KPI_AUTO_MAX_CELL.w)
    const cellH = Math.min(slotH, KPI_AUTO_MAX_CELL.h)
    const candidate: Candidate = {
      columns,
      rows,
      cellW,
      cellH,
      scale: clampAutoScale(Math.min(cellW / KPI_AUTO_REF_CELL.w, cellH / KPI_AUTO_REF_CELL.h)),
    }
    if (!chosen || isBetter(candidate, chosen)) chosen = candidate
  }

  if (!chosen) return null

  const totalW = chosen.columns * chosen.cellW + (chosen.columns - 1) * gap.x
  const totalH = chosen.rows * chosen.cellH + (chosen.rows - 1) * gap.y

  return {
    columns: chosen.columns,
    rows: chosen.rows,
    cellW: chosen.cellW,
    cellH: chosen.cellH,
    gapX: gap.x,
    gapY: gap.y,
    offsetX: Math.max(0, (areaW - totalW) / 2),
    offsetY: Math.max(0, (areaH - totalH) / 2),
    scale: chosen.scale,
  }
}

/** 某指标在自动铺排下的盒子（列优先铺排，整体已带居中与间距）。 */
export function autoMetricLayout(
  index: number,
  plan: KpiAutoLayoutPlan,
): Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'> {
  const columns = Math.max(1, plan.columns)
  const column = index % columns
  const row = Math.floor(index / columns)
  return {
    x: Math.round(plan.offsetX + column * (plan.cellW + plan.gapX)),
    y: Math.round(plan.offsetY + row * (plan.cellH + plan.gapY)),
    // 向下取整：取整后不会因为浮点误差把单元格挤出可用区。
    w: Math.floor(plan.cellW),
    h: Math.floor(plan.cellH),
  }
}

/** 布局破损判定容差（px）：盒子边缘相接不算重叠，重叠超过该值才算。 */
export const KPI_LAYOUT_CONFLICT_EPSILON = 0.5

/**
 * 持久化布局是否「破损」：任两盒子相交、任一盒子越出可用区（含负坐标）、或坐标非有限数。
 *
 * 破损布局不可能是用户有意的排版（重叠 / 溢出卡片没有可解释的摆位意图），
 * 多半来自历史版本默认公式或模板遗留 —— 自动铺排应当接管修复；
 * 用户拖出的合法布局（盒子相接不重叠、都在界内）不受影响，仍按持久化坐标渲染。
 */
export function hasMetricLayoutConflict(
  boxes: Array<Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'>>,
  areaW: number,
  areaH: number,
): boolean {
  const eps = KPI_LAYOUT_CONFLICT_EPSILON
  for (const box of boxes) {
    if (![box.x, box.y, box.w, box.h].every((v) => Number.isFinite(v))) return true
    if (box.x < -eps || box.y < -eps) return true
    if (box.x + box.w > areaW + eps || box.y + box.h > areaH + eps) return true
  }
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i]!
      const b = boxes[j]!
      if (
        a.x < b.x + b.w - eps && b.x < a.x + a.w - eps &&
        a.y < b.y + b.h - eps && b.y < a.y + a.h - eps
      ) return true
    }
  }
  return false
}

/** 某字段的字号是否仍是出厂值。用户显式改过字号的字段不参与自动适配（尊重用户配置）。 */
export function isDefaultMetricFontSize(field: KpiMetricField, size: number): boolean {
  return size === KPI_DEFAULT_FONT_SIZE[field]
}

/** 自动铺排下的字号：字号 × 适配系数，取整并保证不小于最小字号。 */
export function autoFitFontSize(size: number, scale: number): number {
  if (!Number.isFinite(size) || size <= 0) return size
  if (!Number.isFinite(scale) || scale <= 0 || scale === 1) return size
  return Math.max(KPI_AUTO_MIN_FONT, Math.round(size * scale))
}

/**
 * 某盒子尺寸对应的字号适配系数（与 planKpiAutoLayout 同口径）。
 *
 * 用于「手动布局」指标：盒子是用户在老看板里摆好的固定尺寸，字号仍按盒子大小自适应
 * （盒子越接近出厂基准 160×72，字号越接近原值；放大盒子字号跟着大，缩小盒子字号跟着小）。
 * 这样「居中摆放 + 自适应字体大小」对**所有**指标生效，与卡片是否处于自动排布无关。
 * 盒子尺寸未知 / 非有限数时回落 1（原样使用用户配置的字号）。
 */
export function metricBoxScale(w: number, h: number): number {
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return 1
  return clampAutoScale(Math.min(w / KPI_AUTO_REF_CELL.w, h / KPI_AUTO_REF_CELL.h))
}

/** 结果集字段 → 指标默认展示列名（显示名优先，回落字段名） */
export function defaultDisplayName(field: DatasetFieldMeta): string {
  const display = (field.displayName ?? '').trim()
  return display || field.name
}

/**
 * 按最新结果集字段投影指标（按 fieldKey 增量合并）：
 *   - 命中已有指标：**保留**用户的辅助说明/显示状态/布局/样式；
 *   - 展示名 / 单位**不属于投影配置**：它们只存在「字段注册表」一份，
 *     每次投影都从结果集字段重新解析（保证「一处改、处处生效」，见字段名与展示名契约）；
 *   - 新增字段：追加新指标并按默认布局与默认样式初始化；
 *   - 消失字段：移除对应指标；
 *   - 顺序以结果集为准；
 *   - 结果集为空（未取到字段）时原样保留已有指标，不清空用户配置。
 */
export function buildKpiMetrics(
  fields: DatasetFieldMeta[],
  existing: KpiMetricConfig[] = [],
): KpiMetricConfig[] {
  if (!fields.length) return existing.map((m) => ({ ...m, styles: cloneStyles(m.styles) }))
  const byKey = new Map(existing.map((m) => [m.fieldKey, m]))
  return fields.map((field, index) => {
    const displayName = defaultDisplayName(field)
    const unit = (field.unit ?? '').trim()
    const prev = byKey.get(field.name)
    if (prev) {
      // 旧默认布局吸附迁移：只搬「机器生成且从未被用户摆放过」的坐标（见 isLegacyDefaultLayout）
      const layout = isLegacyDefaultLayout(prev) ? defaultMetricLayout(index) : prev
      return { ...prev, ...layout, displayName, unit, styles: cloneStyles(prev.styles) }
    }
    return {
      fieldKey: field.name,
      displayName,
      unit,
      helperText: '',
      visible: true,
      ...defaultMetricLayout(index),
      styles: defaultMetricStyles(),
    }
  })
}

/** 把某个指标的整套样式同步到全部指标（返回新数组，不改动入参） */
export function syncMetricStylesToAll(metrics: KpiMetricConfig[], sourceFieldKey: string): KpiMetricConfig[] {
  const source = metrics.find((m) => m.fieldKey === sourceFieldKey)
  if (!source) return metrics.map((m) => ({
    ...m,
    styles: cloneStyles(m.styles),
    visual: m.visual ? { ...m.visual } : undefined,
  }))
  const styles = cloneStyles(source.styles)
  const visual = source.visual ? { ...source.visual } : undefined
  return metrics.map((m) => ({
    ...m,
    styles: cloneStyles(styles),
    visual: visual ? { ...visual } : undefined,
  }))
}

/** 后续渲染用：按结果集字段名索引指标 */
export function indexMetricsByField(metrics: KpiMetricConfig[]): Record<string, KpiMetricConfig> {
  const map: Record<string, KpiMetricConfig> = {}
  metrics.forEach((m) => { map[m.fieldKey] = m })
  return map
}

/** 16 进制颜色规范化：支持 #rgb / #rrggbb，非法返回 null */
export function normalizeHexColor(input: string): string | null {
  const value = (input ?? '').trim()
  const matched = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$/.exec(value)
  if (!matched) return null
  const hex = matched[1]
  if (hex.length === 3) return '#' + hex.split('').map((c) => c + c).join('').toLowerCase()
  return '#' + hex.toLowerCase()
}
