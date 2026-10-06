import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DashboardCanvas from '../DashboardCanvas.vue'

/**
 * 预览态所见即所得等比缩放：
 * 舞台固定 1440px 逻辑宽（与编辑器一致，列宽不变 → 组合卡片内部 px 绝对定位不错位、图表不变形），
 * zoom = 视口可用宽 / 1440（上限 1 居中留白，下限 0.2），ResizeObserver 随视口重算。
 */
const stubs = {
  GridLayout: { name: 'GridLayout', props: ['colNum', 'layout'], template: '<div><slot /></div>' },
  GridItem: { name: 'GridItem', template: '<div class="vgl-item"><slot /></div>' },
  KpiCardWidget: { name: 'KpiCardWidget', props: ['component', 'componentData'], template: '<div />' },
  ChartWidget: { name: 'ChartWidget', props: ['componentData'], template: '<div />' },
  DataTableWidget: { name: 'DataTableWidget', props: ['component', 'componentData', 'showTitle', 'sampleMode'], template: '<div />' },
  FilterSelectWidget: { name: 'FilterSelectWidget', props: ['component', 'modelValue'], template: '<div />' },
  TimeFilterWidget: { name: 'TimeFilterWidget', props: ['component', 'modelValue', 'timeGranularity'], template: '<div />' },
  AiAnalysisWidget: { name: 'AiAnalysisWidget', props: ['componentData'], template: '<div />' },
  CombinationCardWidget: {
    name: 'CombinationCardWidget',
    props: ['component', 'dropHint'],
    template: '<div class="combination-card"><div class="cc-body" /></div>',
  },
}
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

const kpi = { id: 'kpi-1', type: 'kpi' as const, title: '订单数', position: { x: 12, y: 0, w: 4, h: 3 } }
const comboComponent = {
  id: 'combo-1',
  type: 'combination' as const,
  title: '组合卡片',
  position: { x: 0, y: 3, w: 12, h: 8 },
  children: [],
  containerConfig: {
    title: '',
    background: '#fff',
    radius: 12,
    padding: 16,
    layoutMode: 'free' as const,
    tabs: [],
    style: { border: { enabled: false, color: 'transparent' } },
  },
}

class ResizeObserverStub {
  static instances: ResizeObserverStub[] = []
  callback: ResizeObserverCallback
  observed: Element[] = []
  disconnected = false
  constructor(cb: ResizeObserverCallback) {
    this.callback = cb
    ResizeObserverStub.instances.push(this)
  }
  observe(el: Element): void {
    this.observed.push(el)
  }
  unobserve(): void {}
  disconnect(): void {
    this.disconnected = true
  }
  trigger(): void {
    this.callback([], this as unknown as ResizeObserver)
  }
}

let clientWidth = 0
beforeEach(() => {
  // jsdom 无 clientWidth / ResizeObserver，统一打桩
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get: () => clientWidth,
  })
  vi.stubGlobal('ResizeObserver', ResizeObserverStub as unknown as typeof ResizeObserver)
  ResizeObserverStub.instances = []
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  // @ts-expect-error 测试桩清理
  delete HTMLElement.prototype.clientWidth
})

function mountPreview(components: unknown[] = [comboComponent, kpi]) {
  return mount(DashboardCanvas, {
    props: { components: components as never, editable: false },
    global: { stubs, plugins: [i18n] },
    attachTo: document.body,
  })
}

function stageStyleAttr(wrapper: ReturnType<typeof mountPreview>): string {
  return wrapper.get('.canvas-grid-stage').attributes('style') ?? ''
}

describe('DashboardCanvas 预览态等比缩放', () => {
  it('有组件时舞台固定 1440px 逻辑宽、默认 100% 原始大小并居中', async () => {
    clientWidth = 720
    const wrapper = mountPreview()
    await nextTick()
    const style = stageStyleAttr(wrapper)
    expect(style).toContain('width: 1440px')
    expect(style).toContain('min-height: 1200px')
    expect(style).toContain('margin-inline: auto')
    // 默认 100% 原始大小，点「自适应宽度」后才等比缩放铺满
    expect(style).toContain('zoom: 1')
    wrapper.unmount()
  })

  it('自适应模式宽视口铺满（无缩小），极窄视口保持内容物理宽并 zoom 等比缩小', async () => {
    // 宽视口：stage 铺满 2880（收拢 16 列后内容物理宽 964 < 2880，无 zoom 缩小）
    clientWidth = 2880
    const wide = mountPreview()
    await nextTick()
    await wide.get('button.preview-fill-button').trigger('click')
    await nextTick()
    const wideStyle = stageStyleAttr(wide)
    expect(wideStyle).toContain('width: 2880px')
    expect(wideStyle).toContain('zoom: 1')
    wide.unmount()

    // 极窄视口：stage 保持内容物理宽 964（16×47.5 + 17×12），zoom = 100/964 等比缩小铺满
    clientWidth = 100
    const narrow = mountPreview()
    await nextTick()
    await narrow.get('button.preview-fill-button').trigger('click')
    await nextTick()
    const narrowStyle = stageStyleAttr(narrow)
    expect(narrowStyle).toContain('width: 964px')
    expect(narrowStyle).toContain('zoom: 0.1')
    narrow.unmount()
  })

  it('空画布预览不锁宽度，仅保留最小高度', () => {
    clientWidth = 720
    const wrapper = mountPreview([])
    const style = stageStyleAttr(wrapper)
    expect(style).not.toContain('width:')
    expect(style).not.toContain('zoom:')
    expect(style).toContain('min-height: 1200px')
    wrapper.unmount()
  })

  it('预览默认 24 列；自适应模式收拢到内容实际占宽列数', async () => {
    clientWidth = 720
    const wrapper = mountPreview()
    expect(wrapper.getComponent({ name: 'GridLayout' }).props('colNum')).toBe(24)

    // 自适应：收拢到内容实际占用的最大列数（mock 布局最大 x+w = 16）
    await wrapper.get('button.preview-fill-button').trigger('click')
    await nextTick()
    expect(wrapper.getComponent({ name: 'GridLayout' }).props('colNum')).toBe(16)
    wrapper.unmount()
  })

  it('观察画布根元素尺寸变化并实时重算缩放，卸载时断开观察', async () => {
    clientWidth = 720
    const wrapper = mountPreview()
    expect(ResizeObserverStub.instances).toHaveLength(1)
    const observer = ResizeObserverStub.instances[0]
    expect(observer.observed).toHaveLength(1)
    expect(observer.observed[0].classList.contains('dashboard-canvas')).toBe(true)

    clientWidth = 1440
    observer.trigger()
    await wrapper.vm.$nextTick()
    expect(stageStyleAttr(wrapper)).toContain('zoom: 1')

    wrapper.unmount()
    expect(observer.disconnected).toBe(true)
  })

  it('默认 100% 原始大小（1440 舞台）；点击「自适应宽度」后舞台铺满视口宽度，再点恢复', async () => {
    // 宽视口：默认 100%（zoom 1 + 1440 舞台），点击后舞台铺满 2880px
    clientWidth = 2880
    const wide = mountPreview()
    await nextTick()
    const button = wide.get('button.preview-fill-button')
    expect(stageStyleAttr(wide)).toContain('zoom: 1')
    expect(stageStyleAttr(wide)).toContain('width: 1440px')
    expect(button.attributes('aria-pressed')).toBe('false')

    await button.trigger('click')
    await nextTick()
    expect(stageStyleAttr(wide)).toContain('width: 2880px')
    expect(button.attributes('aria-pressed')).toBe('true')

    await button.trigger('click')
    await nextTick()
    expect(stageStyleAttr(wide)).toContain('width: 1440px')
    expect(button.attributes('aria-pressed')).toBe('false')
    wide.unmount()

    // 窄视口：默认 100% 超宽可横向滚动，点击后保持内容物理宽 964 并 zoom 等比缩小铺满
    clientWidth = 720
    const narrow = mountPreview()
    await nextTick()
    expect(stageStyleAttr(narrow)).toContain('width: 1440px')
    await narrow.get('button.preview-fill-button').trigger('click')
    await nextTick()
    expect(stageStyleAttr(narrow)).toContain('width: 964px')
    expect(stageStyleAttr(narrow)).toContain('zoom: 0.7')
    narrow.unmount()
  })

  it('自适应模式下视口尺寸变化实时重算舞台宽度，恢复默认后回到 1440 舞台', async () => {
    clientWidth = 1440
    const wrapper = mountPreview()
    await nextTick()
    await wrapper.get('button.preview-fill-button').trigger('click')
    await nextTick()
    expect(stageStyleAttr(wrapper)).toContain('width: 1440px')

    clientWidth = 720
    ResizeObserverStub.instances[0].trigger()
    await nextTick()
    expect(stageStyleAttr(wrapper)).toContain('width: 964px')

    await wrapper.get('button.preview-fill-button').trigger('click')
    await nextTick()
    expect(stageStyleAttr(wrapper)).toContain('width: 1440px')
    expect(stageStyleAttr(wrapper)).toContain('zoom: 1')
    wrapper.unmount()
  })
})
