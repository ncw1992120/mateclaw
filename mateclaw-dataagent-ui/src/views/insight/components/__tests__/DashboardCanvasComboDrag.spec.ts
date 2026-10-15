import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DashboardCanvas from '../DashboardCanvas.vue'

/**
 * 自研指针拖动（GridItem 全部 static 后替代库拖动）：
 * pointerdown 候选 → 位移超阈值确认 → 吸附更新 + 悬停组合检测 → pointerup 移入或提交位置。
 * 另覆盖：组件库物料 drop 落在组合卡片上时由容器接管并向编辑器转发。
 * GridItem 用带 .vgl-item 根 class 的 stub 承载真实 DOM 结构，矩形与 elementFromPoint 均为 mock。
 */
const stubs = {
  GridLayout: { template: '<div><slot /></div>' },
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
    emits: ['add-component-into'],
    template: '<div class="combination-card" @drop.stop.prevent="handleDrop"><div class="cc-body" /></div>',
    setup(_props: unknown, { emit }: { emit: (event: string, payload: unknown) => void }) {
      return {
        handleDrop(event: DragEvent) {
          const raw = event.dataTransfer?.getData('application/json')
          if (!raw) return
          const payload = JSON.parse(raw) as { type: string; chartType?: string }
          emit('add-component-into', { containerId: 'combo-1', ...payload, x: 100, y: 80 })
        },
      }
    },
  },
}
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {} }, missingWarn: false, fallbackWarn: false })

const comboComponent = {
  id: 'combo-1',
  type: 'combination' as const,
  title: '组合卡片',
  position: { x: 0, y: 0, w: 12, h: 8 },
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
const kpi = { id: 'kpi-1', type: 'kpi' as const, title: '订单数', position: { x: 12, y: 0, w: 4, h: 3 } }

function domRect(left: number, top: number, width: number, height: number): DOMRect {
  return { left, top, width, height, right: left + width, bottom: top + height, x: left, y: top, toJSON: () => ({}) } as DOMRect
}

/** jsdom 无 PointerEvent 构造器，用 Event + defineProperty 模拟 pointer 事件 */
function pointerEvent(type: 'pointerdown' | 'pointermove' | 'pointerup', clientX: number, clientY: number): Event {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'pointerId', { value: 1 })
  Object.defineProperty(event, 'clientX', { value: clientX })
  Object.defineProperty(event, 'clientY', { value: clientY })
  Object.defineProperty(event, 'button', { value: 0 })
  return event
}
const dragDataTransfer = (raw: string) => ({ getData: (type: string) => (type === 'application/json' ? raw : '') })

let originalElementFromPoint: ((x: number, y: number) => Element | null) | null = null
let elementFromPointResult: Element | null = null
function mockHitElement(el: Element | null): void {
  if (!originalElementFromPoint) originalElementFromPoint = document.elementFromPoint
  elementFromPointResult = el
  document.elementFromPoint = () => elementFromPointResult
}

beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
    if (this.classList.contains('cc-body')) return domRect(50, 50, 800, 400)
    // 拖动位移换算的参照容器（stub 化的 GridLayout 没有 .vgl-layout，退化到 stage）
    if (this.classList.contains('canvas-grid-stage')) return domRect(0, 0, 1188, 1000)
    // 组件宿主元素：.grid-item-content 自身、其 .vgl-item 祖先容器（属性在后代）或 .cc-body 的祖先
    const content = this.classList.contains('grid-item-content')
      ? this
      : (this.querySelector?.('.grid-item-content[data-component-id]') ?? this.closest?.('[data-component-id]'))
    const id = content?.getAttribute('data-component-id')
    if (id === 'kpi-1') return domRect(100, 100, 200, 100)
    if (id === 'combo-1') return domRect(400, 100, 400, 300)
    return domRect(0, 0, 0, 0)
  })
})

afterEach(() => {
  if (originalElementFromPoint) {
    document.elementFromPoint = originalElementFromPoint
    originalElementFromPoint = null
  }
  elementFromPointResult = null
  vi.restoreAllMocks()
})

function mountCanvas() {
  // attachTo：pointer 拖动用 document.querySelector 定位组件，必须真实挂载到文档
  return mount(DashboardCanvas, {
    props: { components: [comboComponent, kpi], editable: true },
    global: { stubs, plugins: [i18n] },
    attachTo: document.body,
  })
}

function findKpiContent(wrapper: ReturnType<typeof mountCanvas>) {
  return wrapper.get('[data-component-id="kpi-1"]')
}

describe('DashboardCanvas 指针拖动与组合卡片移入', () => {
  it('拖动中悬停组合卡片时显示放置提示，松手按当前位置换算落点并移入', async () => {
    const wrapper = mountCanvas()
    // elementFromPoint 模拟真实命中：返回组合卡片内部的元素，处理链路向上 closest('.combination-card')
    mockHitElement(wrapper.get('[data-component-id="combo-1"] .combination-card .cc-body').element)
    const kpiContent = findKpiContent(wrapper)

    // 按住卡片主体开始拖动（位移 49px ≈ 1 列，stage 宽 1188 → 列步长 49px）
    kpiContent.element.dispatchEvent(pointerEvent('pointerdown', 100, 100))
    window.dispatchEvent(pointerEvent('pointermove', 149, 100))
    await nextTick()

    const comboWidget = wrapper.findComponent({ name: 'CombinationCardWidget' })
    expect(comboWidget.props('dropHint')).toBe(true)

    window.dispatchEvent(pointerEvent('pointerup', 149, 100))
    await nextTick()

    expect(wrapper.emitted('move-component-into')?.at(-1)?.[0]).toEqual({
      containerId: 'combo-1',
      componentId: 'kpi-1',
      x: 50,
      y: 50,
    })
    // 移入触发后悬停状态复位：提示关闭
    expect(comboWidget.props('dropHint')).toBe(false)
    wrapper.unmount()
  })

  it('拖动未悬停组合时松手按吸附位置提交 update-layout（不阻塞任意位置摆放）', async () => {
    const wrapper = mountCanvas()
    mockHitElement(wrapper.get('[data-component-id="kpi-1"]').element)
    const kpiContent = findKpiContent(wrapper)

    kpiContent.element.dispatchEvent(pointerEvent('pointerdown', 100, 100))
    window.dispatchEvent(pointerEvent('pointermove', 100, 149))
    window.dispatchEvent(pointerEvent('pointerup', 100, 149))
    await nextTick()

    expect(wrapper.emitted('move-component-into')).toBeUndefined()
    // 纵向 49px ≈ 1 行（行步长 42px，49/42 = 1.17 → 1 行）
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 12, y: 1, w: 4, h: 3 }])
    wrapper.unmount()
  })

  it('未超过位移阈值的按下松手视为点击，不触发拖动提交', async () => {
    const wrapper = mountCanvas()
    mockHitElement(wrapper.get('[data-component-id="combo-1"] .combination-card .cc-body').element)
    const kpiContent = findKpiContent(wrapper)

    kpiContent.element.dispatchEvent(pointerEvent('pointerdown', 100, 100))
    window.dispatchEvent(pointerEvent('pointermove', 102, 102))
    window.dispatchEvent(pointerEvent('pointerup', 102, 102))
    await nextTick()

    expect(wrapper.emitted('move-component-into')).toBeUndefined()
    expect(wrapper.emitted('update-layout')).toBeUndefined()
    expect(wrapper.findComponent({ name: 'CombinationCardWidget' }).props('dropHint')).toBe(false)
    wrapper.unmount()
  })

  it('组件库物料 drop 落在组合卡片上时转发为容器内新增请求', async () => {
    const wrapper = mountCanvas()
    const dropTarget = wrapper.get('[data-component-id="combo-1"] .combination-card').element
    const event = new Event('drop', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'dataTransfer', { value: dragDataTransfer(JSON.stringify({ type: 'kpi' })) })
    Object.defineProperty(event, 'clientX', { value: 300 })
    Object.defineProperty(event, 'clientY', { value: 200 })
    dropTarget.dispatchEvent(event)
    await nextTick()

    expect(wrapper.emitted('add-component-into')?.at(-1)?.[0]).toEqual({
      containerId: 'combo-1', type: 'kpi', x: 100, y: 80,
    })
    expect(wrapper.emitted('add-component')).toBeUndefined()
    wrapper.unmount()
  })

  it('画布顶层组件的标题栏 drop 落在组合卡片上时仍按移入处理', async () => {
    const wrapper = mountCanvas()
    const dropTarget = wrapper.get('[data-component-id="combo-1"] .combination-card').element
    const event = new Event('drop', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'dataTransfer', {
      value: dragDataTransfer(JSON.stringify({ kind: 'canvas-component', componentId: 'kpi-1', componentType: 'kpi' })),
    })
    Object.defineProperty(event, 'clientX', { value: 120 })
    Object.defineProperty(event, 'clientY', { value: 90 })
    dropTarget.dispatchEvent(event)
    await nextTick()

    expect(wrapper.emitted('add-component')).toBeUndefined()
    wrapper.unmount()
  })
})
