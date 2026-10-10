import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { InsightComponent, KpiMetricConfig } from '@/types'
import { defaultMetricLayout, defaultMetricStyles } from '@/utils/kpi-metrics'
import KpiCardWidget from '../KpiCardWidget.vue'

/**
 * ResizeObserver 替身。
 * jsdom 没有真实布局（offsetWidth 恒为 0），可用区尺寸只能由替身在 observe 时下发，
 * 用来模拟浏览器「首次 observe 立即回调一次」的行为。
 */
class FakeResizeObserver {
  static instances: FakeResizeObserver[] = []
  /** 下一次 observe() 时下发的可用区尺寸 */
  static next = { w: 0, h: 0 }

  private targets = new Set<Element>()

  constructor(private callback: ResizeObserverCallback) {
    FakeResizeObserver.instances.push(this)
  }

  observe(target: Element): void {
    this.targets.add(target)
    this.emit(FakeResizeObserver.next.w, FakeResizeObserver.next.h)
  }

  unobserve(target: Element): void {
    this.targets.delete(target)
  }

  disconnect(): void {
    this.targets.clear()
  }

  emit(width: number, height: number): void {
    const entries = [...this.targets].map((target) => ({ target, contentRect: { width, height } }))
    this.callback(entries as unknown as ResizeObserverEntry[], this as unknown as ResizeObserver)
  }
}

const originalResizeObserver = globalThis.ResizeObserver

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: {
    kpiMetricStyle: '配置字段样式',
    kpiMetricStyleFor: '配置指标“{name}”样式',
    kpiNoData: '暂无数据',
  } } },
  missingWarn: false,
  fallbackWarn: false,
})

/** 未经用户调整的指标：坐标命中机器默认排布公式，自动铺排才会接管。 */
function machineMetric(fieldKey: string, index: number): KpiMetricConfig {
  return {
    fieldKey,
    displayName: fieldKey,
    unit: '',
    helperText: '',
    visible: true,
    ...defaultMetricLayout(index),
    styles: defaultMetricStyles(),
  }
}

function mountWidget(metrics: KpiMetricConfig[]) {
  const component = {
    id: 'kpi-card',
    type: 'kpi',
    title: '指标卡片',
    position: { x: 0, y: 0, w: 6, h: 4 },
    kpiMetrics: metrics,
  } as unknown as InsightComponent
  const wrapper = mount(KpiCardWidget, {
    props: { component, editable: true },
    global: { plugins: [i18n], stubs: { 'el-icon': true, 'el-date-picker': true } },
  })
  return { component, wrapper }
}

/** 直接读内联样式，避开 style 属性序列化的空格差异。 */
function styleOf(wrapper: VueWrapper, selector: string): CSSStyleDeclaration {
  return (wrapper.get(selector).element as HTMLElement).style
}

beforeEach(() => {
  FakeResizeObserver.instances = []
  FakeResizeObserver.next = { w: 0, h: 0 }
  globalThis.ResizeObserver = FakeResizeObserver as unknown as typeof ResizeObserver
})

afterEach(() => {
  globalThis.ResizeObserver = originalResizeObserver
})

describe('KpiCardWidget · 自动铺排', () => {
  it('未被动过手的卡片按可用区自动铺排：列行自适应、整块居中、字号随单元格适配', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const { wrapper } = mountWidget([0, 1, 2, 3].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()

    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('on')
    expect(wrapper.findAll('.kpi-metric')).toHaveLength(4)

    // 600×200 下 4 个指标排成 2 列 2 行：单元格 280×94，整块宽 572、水平居中偏 14px
    expect(styleOf(wrapper, '[data-metric="m0"]').left).toBe('14px')
    expect(styleOf(wrapper, '[data-metric="m0"]').top).toBe('0px')
    expect(styleOf(wrapper, '[data-metric="m0"]').width).toBe('280px')
    expect(styleOf(wrapper, '[data-metric="m0"]').height).toBe('94px')
    expect(styleOf(wrapper, '[data-metric="m1"]').left).toBe('306px')
    expect(styleOf(wrapper, '[data-metric="m3"]').top).toBe('106px')
    expect(styleOf(wrapper, '[data-metric="m3"]').left).toBe('306px')

    // 字号按系数 1.3056 适配：指标值 28 → 37，展示名 14 → 18
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize).toBe('37px')
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-name').fontSize).toBe('18px')
  })

  it('用户摆动过位置的卡片不接管：仍按持久化坐标与用户字号渲染', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const manual: KpiMetricConfig = { ...machineMetric('m0', 0), x: 10, y: 20, w: 160, h: 80 }
    const { wrapper } = mountWidget([manual, machineMetric('m1', 1)])
    await nextTick()

    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')
    expect(styleOf(wrapper, '[data-metric="m0"]').left).toBe('10px')
    expect(styleOf(wrapper, '[data-metric="m0"]').top).toBe('20px')
    expect(styleOf(wrapper, '[data-metric="m0"]').width).toBe('160px')
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize).toBe('28px')
  })

  it('可用区尺寸未知时不接管，回落持久化坐标', async () => {
    FakeResizeObserver.next = { w: 0, h: 0 }
    const { wrapper } = mountWidget([0, 1].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()

    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')
    expect(styleOf(wrapper, '[data-metric="m0"]').left).toBe('0px')
    expect(styleOf(wrapper, '[data-metric="m1"]').left).toBe('172px')
  })

  it('老看板里手工摆过的指标：保留用户盒子位置，但字号仍按盒子大小自适应（居中由 CSS 统一负责）', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    // 用户把 m0 摆成了一个更大的盒子（280×96，摆在 m1 下方不重叠），坐标仍按用户持久化值
    const manual: KpiMetricConfig = { ...machineMetric('m0', 0), x: 10, y: 100, w: 280, h: 96 }
    const { wrapper } = mountWidget([manual, machineMetric('m1', 1)])
    await nextTick()

    // 盒子坐标仍按用户持久化值（不自动重排：data-kpi-auto-layout=off）
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')
    expect(styleOf(wrapper, '[data-metric="m0"]').left).toBe('10px')
    expect(styleOf(wrapper, '[data-metric="m0"]').top).toBe('100px')
    expect(styleOf(wrapper, '[data-metric="m0"]').width).toBe('280px')
    expect(styleOf(wrapper, '[data-metric="m0"]').height).toBe('96px')

    // 字号按用户盒子 280×96 适配：scale = min(280/160, 96/72) 上限 1.5 → 1.333，值 28 → 37
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize).toBe('37px')
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-name').fontSize).toBe('19px')

    // 默认大小的机器盒子（160×72）系数 1，字号保持出厂值
    expect(styleOf(wrapper, '[data-metric="m1"] .kpi-metric-value').fontSize).toBe('28px')
  })

  it('旧看板里互相重叠的破损布局被自动铺排接管修复', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    // 模板 / 历史公式遗留的破损布局：两列盒子互相重叠（97 < 120）、行位参差（69 / 80）
    const broken: KpiMetricConfig[] = [
      { ...machineMetric('m0', 0), x: 0, y: 0, w: 120, h: 72 },
      { ...machineMetric('m1', 1), x: 97, y: 0, w: 120, h: 72 },
      { ...machineMetric('m2', 2), x: 0, y: 80, w: 120, h: 61 },
      { ...machineMetric('m3', 3), x: 97, y: 69, w: 120, h: 72 },
    ]
    const { wrapper } = mountWidget(broken)
    await nextTick()

    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('on')
    // 重排后所有盒子都在界内且互不重叠
    const boxes = ['m0', 'm1', 'm2', 'm3'].map((key) => {
      const s = styleOf(wrapper, `[data-metric="${key}"]`)
      return { x: parseInt(s.left), y: parseInt(s.top), w: parseInt(s.width), h: parseInt(s.height) }
    })
    for (const box of boxes) {
      expect(box.x).toBeGreaterThanOrEqual(0)
      expect(box.y).toBeGreaterThanOrEqual(0)
      expect(box.x + box.w).toBeLessThanOrEqual(600)
      expect(box.y + box.h).toBeLessThanOrEqual(200)
    }
    for (let i = 0; i < boxes.length; i++) {
      for (let j = i + 1; j < boxes.length; j++) {
        const a = boxes[i]!
        const b = boxes[j]!
        const overlaps = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
        expect(overlaps).toBe(false)
      }
    }
  })

  it('本会话拖拽过的卡片即使随后布局变破损也不再自动重排', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const { wrapper } = mountWidget([0, 1].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()

    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperties(group, {
      offsetWidth: { value: 600 },
      offsetHeight: { value: 200 },
      getBoundingClientRect: { value: () => ({ width: 600, height: 200 }) },
    })
    const item = wrapper.get('[data-metric="m0"]').element as HTMLElement
    Object.defineProperties(item, { offsetWidth: { value: 280 }, offsetHeight: { value: 94 } })
    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 100, clientY: 50 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()
    const leftBefore = styleOf(wrapper, '[data-metric="m1"]').left

    // 容器变小后持久化布局溢出（破损），但本会话调整过 → 仍按持久化坐标渲染
    FakeResizeObserver.instances[0]?.emit(200, 100)
    await nextTick()
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')
    expect(styleOf(wrapper, '[data-metric="m1"]').left).toBe(leftBefore)
  })

  it('隐藏指标不错位：机器布局按配置里的原始下标判定，可见指标照常自动铺排', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const metrics = [0, 1, 2].map((i) => machineMetric(`m${i}`, i))
    metrics[0].visible = false
    const { wrapper } = mountWidget(metrics)
    await nextTick()

    // 隐藏的是下标 0；剩下的 m1/m2 要按各自原始下标识别机器布局，否则会被误判成手工布局
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('on')
    expect(wrapper.findAll('.kpi-metric')).toHaveLength(2)
    expect(styleOf(wrapper, '[data-metric="m1"]').left).toBe('14px')
    expect(styleOf(wrapper, '[data-metric="m2"]').left).toBe('306px')
  })

  it('拖动指标时先固化自动铺排结果：其余指标不回退到旧默认坐标', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const { component, wrapper } = mountWidget([0, 1, 2, 3].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()

    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperties(group, {
      offsetWidth: { value: 600 },
      offsetHeight: { value: 200 },
      getBoundingClientRect: { value: () => ({ width: 600, height: 200 }) },
    })
    const item = wrapper.get('[data-metric="m0"]').element as HTMLElement
    Object.defineProperties(item, {
      offsetWidth: { value: 280 },
      offsetHeight: { value: 94 },
    })

    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 100, clientY: 50 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 110, clientY: 60 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()

    // 被拖的指标落在原自动位置 + 10px 位移
    expect(component.kpiMetrics?.[0]).toMatchObject({ x: 24, y: 10, w: 280, h: 94 })
    // 其余指标固化成自动铺排坐标，而不是旧的 172 / 80 默认值
    expect(component.kpiMetrics?.[1]).toMatchObject({ x: 306, y: 0, w: 280, h: 94 })
    expect(component.kpiMetrics?.[3]).toMatchObject({ x: 306, y: 106 })
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')
    expect(styleOf(wrapper, '[data-metric="m1"]').left).toBe('306px')

    // 固化必须走父级更新通道（画布交给卡片的是结果集投影后的新对象，只改本地副本会被覆盖）
    const emitted = wrapper.emitted('metric-layout-change') ?? []
    expect(emitted.slice(0, 4).map((call) => call[0])).toEqual([
      { componentId: 'kpi-card', fieldKey: 'm0', x: 14, y: 0, w: 280, h: 94 },
      { componentId: 'kpi-card', fieldKey: 'm1', x: 306, y: 0, w: 280, h: 94 },
      { componentId: 'kpi-card', fieldKey: 'm2', x: 14, y: 106, w: 280, h: 94 },
      { componentId: 'kpi-card', fieldKey: 'm3', x: 306, y: 106, w: 280, h: 94 },
    ])
    expect(emitted.at(-1)?.[0]).toMatchObject({ fieldKey: 'm0', x: 24, y: 10 })
  })

  it('手动拖动后重新应用不同指标集合时，整组恢复自动铺排', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const { component, wrapper } = mountWidget([0, 1].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()

    const item = wrapper.get('[data-metric="m0"]').element as HTMLElement
    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 100, clientY: 50 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')

    const reappliedMetrics = [0, 1, 2].map((index) => ({
      ...machineMetric(`new${index}`, index),
      x: index * 110,
      y: 0,
      w: 100,
      h: 50,
    }))
    await wrapper.setProps({
      component: { ...component, kpiMetrics: reappliedMetrics } as InsightComponent,
    })
    await nextTick()

    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('on')
    expect(styleOf(wrapper, '[data-metric="new0"]').left).toBe('14px')
    expect(styleOf(wrapper, '[data-metric="new1"]').left).toBe('306px')
    expect(styleOf(wrapper, '[data-metric="new2"]').top).toBe('106px')
  })

  it('未处于自动铺排时拖动不额外发出布局事件（只提交被拖的指标）', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const manual: KpiMetricConfig = { ...machineMetric('m0', 0), x: 10, y: 20, w: 160, h: 80 }
    const { wrapper } = mountWidget([manual, machineMetric('m1', 1)])
    await nextTick()
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('off')

    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperties(group, {
      offsetWidth: { value: 600 },
      offsetHeight: { value: 200 },
      getBoundingClientRect: { value: () => ({ width: 600, height: 200 }) },
    })
    const item = wrapper.get('[data-metric="m0"]').element as HTMLElement
    Object.defineProperties(item, { offsetWidth: { value: 160 }, offsetHeight: { value: 80 } })

    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 100, clientY: 50 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 130, clientY: 70 }))
    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()

    const emitted = wrapper.emitted('metric-layout-change') ?? []
    expect(emitted).toHaveLength(1)
    expect(emitted[0]?.[0]).toEqual({ componentId: 'kpi-card', fieldKey: 'm0', x: 40, y: 40, w: 160, h: 80 })
  })

  it('用户显式改过字号的字段不参与适配，其余字段照常适配', async () => {
    FakeResizeObserver.next = { w: 600, h: 200 }
    const metrics = [0, 1, 2, 3].map((i) => machineMetric(`m${i}`, i))
    metrics[0].styles.value.size = 40
    const { wrapper } = mountWidget(metrics)
    await nextTick()

    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize).toBe('40px')
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-name').fontSize).toBe('18px')
  })

  it('指标块内部行间距 / 内边距的缩放系数随盒子自适应（--kpi-metric-scale 注入）', async () => {
    const readVar = (w: VueWrapper, key: string): number => {
      const el = w.get(`[data-metric="${key}"]`).element as HTMLElement
      const matched = /--kpi-metric-scale:\s*([0-9.]+)/.exec(el.getAttribute('style') ?? '')
      return parseFloat(matched?.[1] ?? 'NaN')
    }

    // 自动铺排：600×200 放 4 个指标 → 单元格 280×94 → scale = min(280/160, 94/72) ≈ 1.3056
    FakeResizeObserver.next = { w: 600, h: 200 }
    const { wrapper } = mountWidget([0, 1, 2, 3].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()
    expect(readVar(wrapper, 'm0')).toBeCloseTo(1.3056, 3)

    // 手动大盒子（280×96，不与机器盒子重叠）按自身盒子算系数 1.3333
    FakeResizeObserver.next = { w: 600, h: 260 }
    const manual: KpiMetricConfig = { ...machineMetric('mm', 0), x: 0, y: 120, w: 280, h: 96 }
    const { wrapper: wrapper2 } = mountWidget([manual, machineMetric('m1', 1)])
    await nextTick()
    expect(readVar(wrapper2, 'mm')).toBeCloseTo(1.3333, 3)
  })

  it('可用区尺寸变化时重新铺排（画布缩放 / 卡片改大小）', async () => {
    FakeResizeObserver.next = { w: 332, h: 170 }
    const { wrapper } = mountWidget([0, 1, 2, 3].map((i) => machineMetric(`m${i}`, i)))
    await nextTick()
    expect(styleOf(wrapper, '[data-metric="m0"]').width).toBe('160px')
    expect(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize).toBe('28px')

    FakeResizeObserver.instances[0].emit(800, 400)
    await nextTick()
    expect(wrapper.get('.kpi-metric-group').attributes('data-kpi-auto-layout')).toBe('on')
    expect(styleOf(wrapper, '[data-metric="m0"]').width).not.toBe('160px')
    // 卡片变大后字号跟着变大
    expect(parseFloat(styleOf(wrapper, '[data-metric="m0"] .kpi-metric-value').fontSize)).toBeGreaterThan(28)
  })
})
