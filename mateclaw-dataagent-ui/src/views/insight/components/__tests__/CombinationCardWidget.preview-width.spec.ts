import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CombinationCardWidget from '../CombinationCardWidget.vue'

let bodyWidth = 0

class ResizeObserverStub {
  static instances: ResizeObserverStub[] = []
  private readonly callback: ResizeObserverCallback

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
    ResizeObserverStub.instances.push(this)
  }

  observe(): void {}
  disconnect(): void {}

  trigger(): void {
    this.callback([], this as unknown as ResizeObserver)
  }
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { combination: { tabs: '页签', empty: '暂无组件', emptyEditable: '从组件库拖入组件' } } } },
  missingWarn: false,
  fallbackWarn: false,
})

beforeEach(() => {
  bodyWidth = 0
  ResizeObserverStub.instances = []
  Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
    configurable: true,
    get() { return this.classList?.contains('cc-body') ? bodyWidth : 0 },
  })
  vi.stubGlobal('ResizeObserver', ResizeObserverStub as unknown as typeof ResizeObserver)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  // @ts-expect-error 清理 jsdom 测试属性
  delete HTMLElement.prototype.offsetWidth
})

describe('CombinationCardWidget · preview width', () => {
  it('tracks the natural width before adaptive preview, then scales child positions only while adaptive', async () => {
    bodyWidth = 280
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        component: {
          id: 'combo', type: 'combination', title: '组合卡片',
          children: [{ id: 'child', type: 'kpi', title: '策略执行', layout: { x: 300, y: 0, col: 4, h: 3 } }],
          position: { x: 0, y: 0, w: 16, h: 8 },
          containerConfig: { layoutMode: 'free', tabs: [] },
        },
      },
      global: { plugins: [i18n], stubs: { KpiCardWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true } },
    })

    const observer = ResizeObserverStub.instances[0]
    bodyWidth = 940
    observer.trigger()
    await wrapper.setProps({ previewFillWidth: true })
    bodyWidth = 1880
    observer.trigger()
    await wrapper.vm.$nextTick()

    expect(wrapper.get('[data-child="child"]').attributes('style')).toContain('calc(300px * var(--cc-h-scale, 1))')
    expect(wrapper.get('.combination-card').attributes('style')).toContain('--cc-h-scale: 2')

    await wrapper.setProps({ previewFillWidth: false })
    bodyWidth = 940
    observer.trigger()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('.combination-card').attributes('style')).toContain('--cc-h-scale: 1')
    wrapper.unmount()
  })
})
