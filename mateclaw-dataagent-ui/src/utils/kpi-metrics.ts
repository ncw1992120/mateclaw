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

import type { KpiMetricConfig, KpiMetricFieldStyle, KpiMetricStyles } from '@/types'
import type { DatasetSchemaField } from './field-mapping'

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

/** 单个字段样式 */
export function fieldStyle(size: number, color: string, bold: 'bold' | 'normal' = 'normal'): KpiMetricFieldStyle {
  return { size, family: 'system', color, bold }
}

/** 默认样式：指标值加粗，其余常规 */
export function defaultMetricStyles(): KpiMetricStyles {
  return {
    name: fieldStyle(14, '#646a73', 'normal'),
    value: fieldStyle(28, '#1f2329', 'bold'),
    unit: fieldStyle(15, '#646a73', 'normal'),
    helper: fieldStyle(12, '#8f959e', 'normal'),
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
export function styleToCss(style: KpiMetricFieldStyle): string {
  const family = KPI_FONT_FAMILY[style.family] ?? KPI_FONT_FAMILY.system
  return `font-size:${style.size}px;color:${style.color};font-family:${family};font-weight:${style.bold || 'normal'};`
}

/** 单个指标的默认自由布局（两列错落） */
export function defaultMetricLayout(index: number): Pick<KpiMetricConfig, 'x' | 'y' | 'w' | 'h'> {
  const col = index % 2
  const row = Math.floor(index / 2)
  return { x: col * 296, y: row * 96, w: 284, h: 88 }
}

/** 结果集字段 → 指标默认展示列名（显示名优先，回落字段名） */
export function defaultDisplayName(field: DatasetSchemaField): string {
  const display = (field.displayName ?? '').trim()
  return display || field.name
}

/**
 * 按最新结果集 schema 投影指标（按 fieldKey 增量合并）：
 *   - 命中已有指标：**保留**用户的展示列名/单位/辅助说明/显示状态/布局/样式；
 *   - 新增字段：追加新指标并按默认布局与默认样式初始化；
 *   - 消失字段：移除对应指标；
 *   - 顺序以 schema 为准；
 *   - schema 为空（未取到字段）时原样保留已有指标，不清空用户配置。
 */
export function buildKpiMetrics(
  schema: DatasetSchemaField[],
  existing: KpiMetricConfig[] = [],
): KpiMetricConfig[] {
  if (!schema.length) return existing.map((m) => ({ ...m, styles: cloneStyles(m.styles) }))
  const byKey = new Map(existing.map((m) => [m.fieldKey, m]))
  return schema.map((field, index) => {
    const prev = byKey.get(field.name)
    if (prev) return { ...prev, styles: cloneStyles(prev.styles) }
    return {
      fieldKey: field.name,
      displayName: defaultDisplayName(field),
      unit: '',
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
  if (!source) return metrics.map((m) => ({ ...m, styles: cloneStyles(m.styles) }))
  const styles = cloneStyles(source.styles)
  return metrics.map((m) => ({ ...m, styles: cloneStyles(styles) }))
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
