import { describe, it, expect } from 'vitest'
import {
  buildKpiMetrics,
  cloneStyles,
  defaultMetricStyles,
  normalizeHexColor,
  styleToCss,
  syncMetricStylesToAll,
  resolveMetricVisual,
} from '../kpi-metrics'
import { resolveDashboardTheme } from '../dashboard-theme'
import type { DatasetFieldMeta } from '../field-mapping'

const schema: DatasetFieldMeta[] = [
  { name: 'plan_count', displayName: '关联计划数' },
  { name: 'sent_count', displayName: '下发策略数' },
  { name: 'touch_rate', displayName: '触达率' },
]

describe('kpi-metrics · 默认样式', () => {
  it('指标值默认加粗，其余常规', () => {
    const styles = defaultMetricStyles()
    expect(styles.value.bold).toBe('bold')
    expect(styles.name.bold).toBe('normal')
    expect(styles.unit.bold).toBe('normal')
    expect(styles.helper.bold).toBe('normal')
    expect(styles.value.colorMode).toBe('theme')
    expect(styles.value.color).toBe('')
  })

  it('主题模式使用解析后的指标色，旧固定 HEX 仍保持自定义色', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    const metric = buildKpiMetrics(schema)[0]
    expect(resolveMetricVisual(metric, undefined, theme, 0).textColors.value).toBe(theme.metricPalette[0])
    metric.styles.value.color = '#1f2329'
    delete metric.styles.value.colorMode
    expect(resolveMetricVisual(metric, undefined, theme, 0).textColors.value).toBe('#1f2329')
  })

  it('styleToCss 输出四个属性且字体名用单引号', () => {
    const css = styleToCss({ size: 20, family: 'pingfang', color: '#ff0000', bold: 'bold' })
    expect(css).toContain('font-size:20px')
    expect(css).toContain('color:#ff0000')
    expect(css).toContain('font-weight:bold')
    expect(css).toContain("'PingFang SC'")
    expect(css).not.toContain('"')
  })

  it('未知字体键回落系统默认', () => {
    const css = styleToCss({ size: 12, family: 'nope', color: '#000', bold: 'normal' })
    expect(css).toContain('font-weight:normal')
    expect(css).toContain('-apple-system')
  })
})

describe('kpi-metrics · 结果集投影（增量合并）', () => {
  it('首次按 schema 逐列生成指标，带默认布局与样式', () => {
    const metrics = buildKpiMetrics(schema)
    expect(metrics.map((m) => m.fieldKey)).toEqual(['plan_count', 'sent_count', 'touch_rate'])
    expect(metrics[0].displayName).toBe('关联计划数')
    expect(metrics[0].visible).toBe(true)
    expect(metrics[0].x).toBe(0)
    expect(metrics[1].x).toBeGreaterThan(0)
    expect(metrics[0].styles.value.bold).toBe('bold')
  })

  it('再次投影保留用户配置（辅助说明/显示/布局/样式）', () => {
    const first = buildKpiMetrics(schema)
    first[0].helperText = '较上期'
    first[0].visible = false
    first[0].x = 42
    first[0].styles.value.size = 40

    const second = buildKpiMetrics(schema, first)
    expect(second[0].helperText).toBe('较上期')
    expect(second[0].visible).toBe(false)
    expect(second[0].x).toBe(42)
    expect(second[0].styles.value.size).toBe(40)
  })

  it('展示名与单位不属于投影配置，每次投影从字段注册表重新解析（一处改、处处生效）', () => {
    const first = buildKpiMetrics(schema)
    expect(first[0].displayName).toBe('关联计划数')
    const renamed: DatasetFieldMeta[] = [
      { name: 'plan_count', displayName: '计划数', unit: '个' },
      { name: 'sent_count', displayName: '下发策略数' },
      { name: 'touch_rate', displayName: '触达率' },
    ]
    const second = buildKpiMetrics(renamed, first)
    expect(second[0].displayName).toBe('计划数')
    expect(second[0].unit).toBe('个')
  })

  it('新增字段追加、消失字段移除、顺序以 schema 为准', () => {
    const first = buildKpiMetrics(schema)
    const changed: DatasetFieldMeta[] = [
      { name: 'touch_rate', displayName: '触达率' },
      { name: 'conv_amount', displayName: '转化金额' },
    ]
    const second = buildKpiMetrics(changed, first)
    expect(second.map((m) => m.fieldKey)).toEqual(['touch_rate', 'conv_amount'])
  })

  it('schema 为空时原样保留已有指标（不清空用户配置）', () => {
    const first = buildKpiMetrics(schema)
    const second = buildKpiMetrics([], first)
    expect(second).toHaveLength(first.length)
    expect(second[0].fieldKey).toBe('plan_count')
  })

  it('每个指标的样式是独立副本（不共享引用）', () => {
    const metrics = buildKpiMetrics(schema)
    metrics[0].styles.value.color = '#123456'
    expect(metrics[1].styles.value.color).not.toBe('#123456')
  })
})

describe('kpi-metrics · 样式一键同步', () => {
  it('把源指标的整套样式同步到全部指标，且各自独立', () => {
    const metrics = buildKpiMetrics(schema)
    metrics[0].styles.value.size = 40
    metrics[0].styles.value.color = '#ff0000'
    metrics[0].styles.name.color = '#00ff00'

    const synced = syncMetricStylesToAll(metrics, 'plan_count')
    synced.forEach((m) => {
      expect(m.styles.value.size).toBe(40)
      expect(m.styles.value.color).toBe('#ff0000')
      expect(m.styles.name.color).toBe('#00ff00')
    })
    // 独立副本
    synced[1].styles.value.color = '#000000'
    expect(synced[2].styles.value.color).toBe('#ff0000')
  })

  it('源指标不存在时原样返回（仅深拷贝样式）', () => {
    const metrics = buildKpiMetrics(schema)
    const synced = syncMetricStylesToAll(metrics, 'not-exist')
    expect(synced).toHaveLength(3)
  })
})

describe('kpi-metrics · 颜色规范化', () => {
  it('支持 #rgb 与 #rrggbb', () => {
    expect(normalizeHexColor('#abc')).toBe('#aabbcc')
    expect(normalizeHexColor('#AABBCC')).toBe('#aabbcc')
    expect(normalizeHexColor(' #1f2329 ')).toBe('#1f2329')
  })

  it('非法输入返回 null', () => {
    expect(normalizeHexColor('red')).toBeNull()
    expect(normalizeHexColor('#12')).toBeNull()
    expect(normalizeHexColor('')).toBeNull()
  })

  it('cloneStyles 是深拷贝', () => {
    const styles = defaultMetricStyles()
    const copy = cloneStyles(styles)
    copy.value.size = 99
    expect(styles.value.size).toBe(28)
  })
})
