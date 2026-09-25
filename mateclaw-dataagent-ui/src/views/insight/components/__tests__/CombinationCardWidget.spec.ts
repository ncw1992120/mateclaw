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
  containerConfig: { ...containerConfig },
  layout: { x: 12, y: 12, col: 6, h: 180 },
}

describe('CombinationCardWidget', () => {
  it('renames a child component from its canvas title pen', async () => {
    const child = {
      id: 'child-kpi',
      type: 'kpi' as const,
      title: '策略概括',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combination-child-title',
          type: 'combination',
          title: '组合卡片',
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

    await wrapper.get('[aria-label="编辑子组件标题 策略概括"]').trigger('click')
    const input = wrapper.get('input[aria-label="子组件标题"]')
    await input.setValue('策略概括（新）')
    await input.trigger('keyup', { key: 'Enter' })

    expect(child.title).toBe('策略概括（新）')
    expect(wrapper.find('input[aria-label="子组件标题"]').exists()).toBe(false)
  })

  it('renders default sample data and a title watermark for a newly dropped child', async () => {
    const component = {
      id: 'combination-sample-child',
      type: 'combination' as const,
      title: '组合卡片',
      children: [] as Array<{ id: string; type: 'kpi'; title: string; layout: { x: number; y: number; col: number; h: number } }>,
      containerConfig,
      position: { x: 0, y: 0, w: 12, h: 8 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component,
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

    const dataTransfer = {
      getData: (type: string) => type === 'application/json'
        ? JSON.stringify({ type: 'kpi' })
        : '',
    }
    await wrapper.get('.combination-card').trigger('drop', { clientX: 40, clientY: 50, dataTransfer })

    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toMatchObject({
      renderType: 'kpi',
      kpi: { value: '1,284' },
    })
    const watermark = wrapper.get('[data-testid="cc-sample-data-watermark"]')
    expect(watermark.text()).toBe('示例数据')
    expect(watermark.element.parentElement?.classList.contains('cc-child-head')).toBe(true)
    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
  })

  it('prefers actual result data and hides the child sample watermark', () => {
    const child = {
      id: 'actual-child-kpi',
      type: 'kpi' as const,
      title: '实际指标',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const actualData = { componentId: child.id, renderType: 'kpi' as const, kpi: { name: '实际', value: '42' } }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        componentDataMap: { [child.id]: actualData },
        component: {
          id: 'combination-actual-child',
          type: 'combination',
          title: '组合卡片',
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

    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toMatchObject(actualData)
    expect(wrapper.find('[data-testid="cc-sample-data-watermark"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
  })

  it('renders default chart and table samples inside a combination card', () => {
    const chart = {
      id: 'sample-child-chart',
      type: 'chart' as const,
      chartType: 'pie' as const,
      title: '样例饼图',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const table = {
      id: 'sample-child-table',
      type: 'table' as const,
      title: '样例表格',
      layout: { x: 0, y: 120, col: 6, h: 180 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'combination-chart-table-samples',
          type: 'combination',
          title: '组合卡片',
          children: [chart, table],
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

    expect(wrapper.findComponent({ name: 'ChartWidget' }).props('componentData')).toMatchObject({
      componentId: chart.id,
      renderType: 'echarts',
      option: { series: [{ type: 'pie' }] },
    })
    expect(wrapper.findComponent({ name: 'DataTableWidget' }).props('componentData').table.rows).toHaveLength(45)
    expect(wrapper.findComponent({ name: 'DataTableWidget' }).props('sampleMode')).toBe(true)
    expect(wrapper.findAll('[data-testid="cc-sample-data-watermark"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
  })

  it('does not show child sample data or watermarks for filter and time controls', () => {
    const filter = {
      id: 'sample-child-filter',
      type: 'filter' as const,
      title: '样例筛选器',
      layout: { x: 0, y: 0, col: 6, h: 60 },
    }
    const timeFilter = {
      id: 'sample-child-time-filter',
      type: 'timeFilter' as const,
      title: '样例时间筛选',
      layout: { x: 0, y: 60, col: 6, h: 60 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'combination-filter-sample',
          type: 'combination',
          title: '组合卡片',
          children: [filter, timeFilter],
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

    expect(wrapper.findComponent({ name: 'FilterSelectWidget' }).props('componentData')).toBeUndefined()
    expect(wrapper.findComponent({ name: 'TimeFilterWidget' }).exists()).toBe(true)
    expect(wrapper.find('[data-testid="cc-sample-data-watermark"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
  })

  it('emits copy, paste and context-menu actions for a child component', async () => {
    const child = {
      id: 'child-copy',
      type: 'kpi' as const,
      title: '策略概括',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combination-copy',
          type: 'combination',
          title: '组合卡片',
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

    const childEl = wrapper.get('[data-child="child-copy"]')
    await childEl.trigger('keydown', { key: 'c', ctrlKey: true })
    await childEl.trigger('keydown', { key: 'v', metaKey: true })
    await childEl.trigger('contextmenu', { clientX: 80, clientY: 120 })

    expect(wrapper.emitted('copy-child')).toEqual([[{ containerId: 'combination-copy', childId: 'child-copy' }]])
    expect(wrapper.emitted('paste-child')).toEqual([[{ containerId: 'combination-copy', childId: 'child-copy' }]])
    expect(wrapper.emitted('context-menu')).toEqual([[{ containerId: 'combination-copy', childId: 'child-copy', x: 80, y: 120 }]])
  })

  it('clicks the tab edit pen to rename a canvas tab and supports escape cancel', async () => {
    const tabs = [
      { id: 'tab-one', title: '页签一', children: [] },
      { id: 'tab-two', title: '页签二', children: [] },
    ]
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combination-tabs',
          type: 'combination',
          title: '组合卡片',
          children: [],
          containerConfig: { ...containerConfig, tabs, activeTab: 'tab-one' },
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

    const pen = wrapper.find('.cc-tab .tab-rename-hint')
    await pen.trigger('click')
    const input = wrapper.get('.cc-tab .tab-edit')
    expect((input.element as HTMLInputElement).value).toBe('页签一')

    await input.setValue('已保存页签')
    await input.trigger('keyup', { key: 'Enter' })
    expect(tabs[0].title).toBe('已保存页签')
    expect(wrapper.find('.tab-edit').exists()).toBe(false)

    await wrapper.find('.cc-tab .tab-rename-hint').trigger('click')
    const cancelInput = wrapper.get('.cc-tab .tab-edit')
    await cancelInput.setValue('不会保存')
    await cancelInput.trigger('keydown', { key: 'Escape' })
    expect(tabs[0].title).toBe('已保存页签')
    expect(wrapper.find('.tab-edit').exists()).toBe(false)
  })

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
    expect(wrapper.findAll('[data-testid="cc-sample-data-watermark"]')).toHaveLength(0)
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

  it('accepts an existing canvas component drop and emits a move request', async () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'outer-combination',
          type: 'combination',
          title: '外层组合',
          children: [],
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

    const dataTransfer = {
      getData: (type: string) => type === 'application/json'
        ? JSON.stringify({ kind: 'canvas-component', componentId: 'canvas-kpi-1' })
        : '',
    }
    await wrapper.get('.combination-card').trigger('drop', { clientX: 40, clientY: 50, dataTransfer })

    expect(wrapper.emitted('move-component-into')).toEqual([[{ containerId: 'outer-combination', componentId: 'canvas-kpi-1', x: 0, y: 0 }]])
  })
})
