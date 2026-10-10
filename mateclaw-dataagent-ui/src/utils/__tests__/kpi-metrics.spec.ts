import { describe, it, expect } from 'vitest'
import {
  KPI_AUTO_MAX_CELL,
  KPI_AUTO_SCALE_MAX,
  KPI_AUTO_SCALE_MIN,
  autoFitFontSize,
  autoMetricLayout,
  buildKpiMetrics,
  cloneStyles,
  defaultMetricLayout,
  defaultMetricStyles,
  hasMetricLayoutConflict,
  isDefaultMetricFontSize,
  isMachineMetricLayout,
  metricBoxScale,
  normalizeHexColor,
  planKpiAutoLayout,
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

  it('主题模式使用区域主色且同一张卡内所有指标同色，旧固定 HEX 仍保持自定义色', () => {
    const theme = resolveDashboardTheme({ mode: 'preset', presetId: 'blue' }, 'light')
    const metric = buildKpiMetrics(schema)[0]
    expect(resolveMetricVisual(metric, undefined, theme, 0).textColors.value).toBe(theme.primary)
    // 卡内轮转只换图标，不换色相
    expect(resolveMetricVisual(metric, undefined, theme, 3).textColors.value).toBe(theme.primary)
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
    expect(metrics[0]).toMatchObject({ w: 160, h: 72 })
    expect(metrics[1]).toMatchObject({ x: 172, w: 160, h: 72 })
    expect(metrics[0].styles.value.bold).toBe('bold')
  })

  it('重新投影时将旧版自动默认尺寸收窄，但保留用户调整过的尺寸', () => {
    const existing = buildKpiMetrics(schema).map((metric, index) => ({
      ...metric,
      x: index * 296,
      y: 0,
      w: 284,
      h: 88,
    }))

    const projected = buildKpiMetrics(schema, existing)

    expect(projected[0]).toMatchObject({ x: 0, y: 0, w: 160, h: 72 })
    expect(projected[1]).toMatchObject({ x: 172, y: 0, w: 160, h: 72 })

    existing[2].w = 246
    const withManualSize = buildKpiMetrics(schema, existing)
    expect(withManualSize[2]).toMatchObject({ w: 246, h: 88 })
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
    metrics[0].visual = { iconKey: 'trend-up', colorMode: 'custom', accentColor: '#123456' }
    metrics[1].x = 66
    metrics[1].displayName = '独立展示名'
    metrics[1].helperText = '仅此指标的说明'

    const synced = syncMetricStylesToAll(metrics, 'plan_count')
    synced.forEach((m) => {
      expect(m.styles.value.size).toBe(40)
      expect(m.styles.value.color).toBe('#ff0000')
      expect(m.styles.name.color).toBe('#00ff00')
      expect(m.visual).toEqual({ iconKey: 'trend-up', colorMode: 'custom', accentColor: '#123456' })
    })
    // 独立副本
    synced[1].styles.value.color = '#000000'
    expect(synced[2].styles.value.color).toBe('#ff0000')
    synced[1].visual!.accentColor = '#000000'
    expect(synced[2].visual!.accentColor).toBe('#123456')
    expect(synced[1]).toMatchObject({
      fieldKey: 'sent_count',
      displayName: '独立展示名',
      helperText: '仅此指标的说明',
      x: 66,
    })
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

describe('kpi-metrics · 自动铺排（个数 + 卡片可用区尺寸）', () => {
  it('可用区尺寸未知或个数非法时不接管，返回 null 由调用方回落持久化坐标', () => {
    expect(planKpiAutoLayout(0, 300, 200)).toBeNull()
    expect(planKpiAutoLayout(-1, 300, 200)).toBeNull()
    expect(planKpiAutoLayout(4, 0, 200)).toBeNull()
    expect(planKpiAutoLayout(4, 300, 0)).toBeNull()
    expect(planKpiAutoLayout(4, Number.NaN, 200)).toBeNull()
    expect(planKpiAutoLayout(4, 300, Number.POSITIVE_INFINITY)).toBeNull()
  })

  it('单个指标：按上限封顶后整块居中', () => {
    const plan = planKpiAutoLayout(1, 900, 90)
    expect(plan).not.toBeNull()
    // 宽 900 会超过舒适上限，最多拉到 280；剩余 620 平分到两侧
    expect(plan).toMatchObject({ columns: 1, rows: 1, cellW: KPI_AUTO_MAX_CELL.w, cellH: 90, offsetX: 310, offsetY: 0 })
    expect(autoMetricLayout(0, plan!)).toEqual({ x: 310, y: 0, w: 280, h: 90 })
  })

  it('个数与可用区正好匹配时等分铺满，字号保持出厂值（系数 1）', () => {
    const plan = planKpiAutoLayout(4, 332, 170)
    expect(plan).toMatchObject({ columns: 2, rows: 2, cellW: 160, cellH: 79, offsetX: 0, offsetY: 0, scale: 1 })
    expect([0, 1, 2, 3].map((i) => autoMetricLayout(i, plan!))).toEqual([
      { x: 0, y: 0, w: 160, h: 79 },
      { x: 172, y: 0, w: 160, h: 79 },
      { x: 0, y: 91, w: 160, h: 79 },
      { x: 172, y: 91, w: 160, h: 79 },
    ])
    expect(autoFitFontSize(28, plan!.scale)).toBe(28)
  })

  it('宽阔卡片改用更多列并垂直居中，不再贴着左上角排', () => {
    const plan = planKpiAutoLayout(6, 800, 400)!
    expect(plan.rows).toBe(2)
    expect(plan.columns).toBeGreaterThan(2)
    expect(plan.cellH).toBeLessThanOrEqual(KPI_AUTO_MAX_CELL.h)
    // 整块居中：首行偏移大于 0，且末列不越过右边界
    expect(plan.offsetY).toBeGreaterThan(0)
    const last = autoMetricLayout(5, plan)
    expect(last.x + last.w).toBeLessThanOrEqual(800)
    expect(last.y + last.h).toBeLessThanOrEqual(400)
  })

  it.each([
    [1, 200, 130],
    [2, 280, 200],
    [3, 340, 170],
    [4, 460, 240],
    [6, 600, 200],
    [8, 800, 320],
    [10, 460, 170],
    [12, 800, 240],
  ])('%i 个指标在 %i×%i 可用区内：行数够、单元格不越界、系数在区间内', (count, w, h) => {
    const plan = planKpiAutoLayout(count, w, h)
    expect(plan).not.toBeNull()
    expect(plan!.columns * plan!.rows).toBeGreaterThanOrEqual(count)
    expect(plan!.scale).toBeGreaterThanOrEqual(KPI_AUTO_SCALE_MIN)
    expect(plan!.scale).toBeLessThanOrEqual(KPI_AUTO_SCALE_MAX)

    for (let index = 0; index < count; index++) {
      const box = autoMetricLayout(index, plan!)
      expect(box.x).toBeGreaterThanOrEqual(0)
      expect(box.y).toBeGreaterThanOrEqual(0)
      expect(box.x + box.w).toBeLessThanOrEqual(w)
      expect(box.y + box.h).toBeLessThanOrEqual(h)
      // 任意两个指标不重叠
      for (let other = index + 1; other < count; other++) {
        const peer = autoMetricLayout(other, plan!)
        const overlaps = box.x < peer.x + peer.w && peer.x < box.x + box.w
          && box.y < peer.y + peer.h && peer.y < box.y + box.h
        expect(overlaps).toBe(false)
      }
    }
  })

  it('连舒适尺寸都排不下时退到可用下限档（窄卡片单列铺开）', () => {
    // 宽 70 放不下两列，只能一列往下铺；单元格仍满足可用下限
    const plan = planKpiAutoLayout(4, 70, 400)
    expect(plan).toMatchObject({ columns: 1, rows: 4, cellW: 70, cellH: 91, scale: KPI_AUTO_SCALE_MIN })
    expect(autoMetricLayout(3, plan!)).toEqual({ x: 0, y: 309, w: 70, h: 91 })
  })

  it('面积再小也不再放弃：尽力把整块收进容器（小卡片收紧字号而不是溢出）', () => {
    // 这些组合过去会因「连可用下限都达不到」返回 null，导致持久化坐标原样溢出卡片
    for (const [count, w, h] of [[3, 200, 90], [5, 200, 130], [12, 120, 120]] as const) {
      const plan = planKpiAutoLayout(count, w, h)
      expect(plan).not.toBeNull()
      for (let index = 0; index < count; index++) {
        const box = autoMetricLayout(index, plan!)
        expect(box.x).toBeGreaterThanOrEqual(0)
        expect(box.y).toBeGreaterThanOrEqual(0)
        expect(box.x + box.w).toBeLessThanOrEqual(w)
        expect(box.y + box.h).toBeLessThanOrEqual(h)
      }
    }
  })

  it('hasMetricLayoutConflict：相交 / 越界 / 负坐标算破损，相接与正常网格不算', () => {
    // 边缘相接（共享边界）不算重叠
    expect(hasMetricLayoutConflict(
      [{ x: 0, y: 0, w: 120, h: 72 }, { x: 120, y: 0, w: 120, h: 72 }],
      300, 200,
    )).toBe(false)
    // 相交（模板遗留的典型症状：97 < 120）
    expect(hasMetricLayoutConflict(
      [{ x: 0, y: 0, w: 120, h: 72 }, { x: 97, y: 0, w: 120, h: 72 }],
      300, 200,
    )).toBe(true)
    // 越出右 / 下边界
    expect(hasMetricLayoutConflict([{ x: 0, y: 0, w: 160, h: 72 }], 100, 200)).toBe(true)
    expect(hasMetricLayoutConflict([{ x: 0, y: 0, w: 160, h: 72 }], 300, 50)).toBe(true)
    // 负坐标
    expect(hasMetricLayoutConflict([{ x: -4, y: 0, w: 160, h: 72 }], 300, 200)).toBe(true)
    // 非有限数坐标按破损处理
    expect(hasMetricLayoutConflict([{ x: Number.NaN, y: 0, w: 160, h: 72 }], 300, 200)).toBe(true)
    // 正常机器网格不误报
    expect(hasMetricLayoutConflict(
      [defaultMetricLayout(0), defaultMetricLayout(1), defaultMetricLayout(2)],
      800, 400,
    )).toBe(false)
  })

  it('识别「机器默认排布」：当前默认公式与旧版默认尺寸都算，手工坐标不算', () => {
    expect(isMachineMetricLayout(defaultMetricLayout(0), 0)).toBe(true)
    expect(isMachineMetricLayout(defaultMetricLayout(3), 3)).toBe(true)
    // 下标错位（例如隐藏了第一个指标后按新下标比对）不算机器布局
    expect(isMachineMetricLayout(defaultMetricLayout(1), 0)).toBe(false)
    // 旧版默认尺寸（284×88、步距 296×96）
    expect(isMachineMetricLayout({ x: 296, y: 96, w: 284, h: 88 }, 9)).toBe(true)
    // 用户拖动 / 缩放过的坐标
    expect(isMachineMetricLayout({ x: 10, y: 20, w: 160, h: 72 }, 0)).toBe(false)
    expect(isMachineMetricLayout({ x: 0, y: 0, w: 246, h: 72 }, 0)).toBe(false)
  })

  it('字号适配：按系数等比缩放并取整，夹住最小字号；非法输入原样返回', () => {
    expect(isDefaultMetricFontSize('value', 28)).toBe(true)
    expect(isDefaultMetricFontSize('value', 40)).toBe(false)
    expect(isDefaultMetricFontSize('helper', 12)).toBe(true)

    expect(autoFitFontSize(28, 1)).toBe(28)
    expect(autoFitFontSize(28, 1.3056)).toBe(37)
    expect(autoFitFontSize(14, 0.62)).toBe(9)
    // 小于最小字号时抬到 9，不出现糊成一片的字
    expect(autoFitFontSize(12, 0.3)).toBe(9)
    expect(autoFitFontSize(28, 0)).toBe(28)
    expect(autoFitFontSize(0, 1.3)).toBe(0)
  })

  it('metricBoxScale：按盒子与出厂基准 160×72 的比例给字号适配系数，老看板手工盒子也适用', () => {
    // 盒子等于基准 → 系数为 1（原样字号）
    expect(metricBoxScale(160, 72)).toBe(1)
    // 盒子放大 → 系数随之放大（上限 KPI_AUTO_SCALE_MAX）
    expect(metricBoxScale(280, 96)).toBeCloseTo(1.3333, 4)
    expect(metricBoxScale(1000, 1000)).toBe(1.5)
    // 盒子缩小 → 系数随之缩小（下限 KPI_AUTO_SCALE_MIN）
    expect(metricBoxScale(100, 45)).toBeCloseTo(0.625, 4)
    expect(metricBoxScale(40, 20)).toBe(0.62)
    // 尺寸未知 / 非有限数 / 非正数 → 回落 1（原样字号）
    expect(metricBoxScale(0, 72)).toBe(1)
    expect(metricBoxScale(160, 0)).toBe(1)
    expect(metricBoxScale(Number.NaN, 72)).toBe(1)
    expect(metricBoxScale(160, Number.POSITIVE_INFINITY)).toBe(1)
  })
})
