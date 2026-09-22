import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import CombinationCardWidget from '../CombinationCardWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      insight: {
        combination: {
          tabs: '页签',
          empty: '暂无组件',
          emptyEditable: '从组件库拖入组件',
          deleteChild: '删除子组件',
          deleteChildConfirm: '确认删除 {name}？',
          addTab: '添加页签',
          deleteTab: '删除页签',
        },
      },
      common: { confirm: '确认', cancel: '取消' },
    },
  },
  missingWarn: false,
  fallbackWarn: false,
})

const containerConfig = {
  title: '外层组合',
  showTitle: true,
  background: '#fff',
  radius: 12,
  padding: 16,
  layoutMode: 'free' as const,
  tabs: [],
  style: { border: { enabled: false, color: 'transparent' } },
}

const nestedCombination = {
  id: 'nested-combination',
  type: 'combination' as const,
  title: '内层组合',
  children: [],
  containerConfig: { ...containerConfig, title: '内层组合' },
  layout: { x: 12, y: 12, col: 6, h: 180 },
}

describe('CombinationCardWidget', () => {
  it('moves a selected child with keyboard arrows while staying inside the container', async () => {
    const child = {
      ...nestedCombination,
      id: 'keyboard-child',
      type: 'kpi' as const,
      title: '键盘子组件',
      layout: { x: 12, y: 12, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'outer-combination',
          type: 'combination',
          title: '外层组合',
          children: [child],
          containerConfig,
          position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true,
          ChartWidget: true,
          DataTableWidget: true,
          FilterSelectWidget: true,
          TimeFilterWidget: true,
          AiAnalysisWidget: true,
          EmptyState: { template: '<div />' },
          'el-icon': true,
        },
      },
    })

    const childEl = wrapper.get('[data-child="keyboard-child"]')
    expect(childEl.attributes('tabindex')).toBe('0')
    await childEl.trigger('mousedown', { clientX: 10, clientY: 10 })
    window.dispatchEvent(new MouseEvent('mouseup'))
    await childEl.trigger('keydown', { key: 'ArrowRight' })
    expect(child.layout.x).toBe(13)
    await childEl.trigger('keydown', { key: 'ArrowDown', shiftKey: true })
    expect(child.layout.y).toBe(22)
  })

  it('renders a combination child as a nested combination card', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'outer-combination',
          type: 'combination',
          title: '外层组合',
          children: [nestedCombination],
          containerConfig,
          position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true,
          ChartWidget: true,
          DataTableWidget: true,
          FilterSelectWidget: true,
          TimeFilterWidget: true,
          AiAnalysisWidget: true,
          EmptyState: { template: '<div />' },
          'el-icon': true,
        },
      },
    })

    expect(wrapper.findAll('.combination-card')).toHaveLength(2)
    expect(wrapper.findAll('.cc-child-title').map((item) => item.text())).toContain('内层组合')
  })

  it('switches an inner combination tab without starting the parent child drag', async () => {
    const innerConfig = {
      ...containerConfig,
      title: '内层组合',
      tabs: [
        { id: 'tab-one', title: '页签一', children: [] },
        { id: 'tab-two', title: '页签二', children: [] },
      ],
      activeTab: 'tab-one',
    }
    const inner = {
      ...nestedCombination,
      containerConfig: innerConfig,
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'outer-combination',
          type: 'combination',
          title: '外层组合',
          children: [inner],
          containerConfig,
          position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true,
          ChartWidget: true,
          DataTableWidget: true,
          FilterSelectWidget: true,
          TimeFilterWidget: true,
          AiAnalysisWidget: true,
          EmptyState: { template: '<div />' },
          'el-icon': true,
        },
      },
    })

    const innerCard = wrapper.findAll('.combination-card')[1]
    const secondTab = innerCard.findAll('.cc-tab')[1]
    await secondTab.trigger('mousedown', { clientX: 10, clientY: 10 })
    expect(wrapper.find('.cc-child.moving').exists()).toBe(false)

    await secondTab.trigger('click')
    expect(innerConfig.activeTab).toBe('tab-two')
  })
})
