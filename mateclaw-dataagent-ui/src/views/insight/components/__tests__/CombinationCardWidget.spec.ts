import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { defaultMetricStyles } from '@/utils/kpi-metrics'
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
  it('keeps child wrappers clipped by the combination card boundary', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'overflow-combo', type: 'combination', title: '组合卡片',
          children: [{ ...nestedCombination, layout: { x: 240, y: 180, col: 6, h: 120 } }],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: { plugins: [i18n], stubs: { CombinationCardWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true } },
    })

    expect(wrapper.get('.combination-card').classes()).not.toContain('free-layout')
    expect(wrapper.get('[data-child="nested-combination"] .cc-child-body').classes()).not.toContain('is-combination')
  })

  it('renders nested query failures locally and bubbles retry for that child', async () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'retry-combo', type: 'combination', title: '组合卡片',
          children: [{ id: 'retry-child', type: 'table', title: '明细', layout: { x: 0, y: 0, col: 6, h: 120 } }],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
        componentDataMap: {
          'retry-child': { componentId: 'retry-child', renderType: 'table', queryStatus: 'timeout', error: '执行超时' },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    expect(wrapper.findComponent({ name: 'ComponentQueryState' }).exists()).toBe(true)
    await wrapper.get('.query-state-retry').trigger('click')
    expect(wrapper.emitted('retry-component-query')?.[0]).toEqual(['retry-child'])
  })

  it('bubbles filter changes from components inside a combination card', async () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'filter-combo', type: 'combination', title: '组合卡片',
          children: [
            { id: 'nested-filter', type: 'filter', title: '指标名称', config: { field: 'metric_name' }, layout: { x: 0, y: 0, col: 6, h: 80 } },
            { id: 'nested-time-filter', type: 'timeFilter', title: '指标日期', config: { field: 'metric_time' }, layout: { x: 0, y: 90, col: 6, h: 80 } },
          ],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
        runtimeFilterState: {
          'nested-filter': { field: 'metric_name', value: '交易量', scope: 'global', targetComponentIds: [] },
          'nested-time-filter': { field: 'metric_time', value: { preset: 'custom', start: '2026-09-01', end: '2026-09-03' }, scope: 'global', targetComponentIds: [] },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: { name: 'FilterSelectWidget', props: ['component', 'modelValue'], emits: ['change'], template: '<button :data-testid="\'filter-\' + component.id" @click="$emit(\'change\', { field: \'metric_name\', value: \'交易量\' })" />' },
          TimeFilterWidget: { name: 'TimeFilterWidget', props: ['component', 'modelValue', 'timeGranularity'], emits: ['change'], template: '<button data-testid="time-filter" @click="$emit(\'change\', { field: \'metric_time\', timeRange: { preset: \'custom\', start: \'2026-09-01\', end: \'2026-09-03\' }, timeGranularity: \'MONTH\' })" />' },
          AiAnalysisWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    await wrapper.get('[data-testid="filter-nested-filter"]').trigger('click')
    await wrapper.get('[data-testid="time-filter"]').trigger('click')

    expect(wrapper.findComponent({ name: 'FilterSelectWidget' }).props('modelValue')).toBe('交易量')
    expect(wrapper.findComponent({ name: 'TimeFilterWidget' }).props('modelValue')).toEqual({ preset: 'custom', start: '2026-09-01', end: '2026-09-03' })
    expect(wrapper.emitted('filter-change')?.[0]?.[0]).toEqual({ componentId: 'nested-filter', field: 'metric_name', value: '交易量' })
    expect(wrapper.emitted('time-filter-change')?.[0]?.[0]).toEqual({
      componentId: 'nested-time-filter', field: 'metric_time',
      timeRange: { preset: 'custom', start: '2026-09-01', end: '2026-09-03' },
      timeGranularity: 'MONTH',
    })
  })

  it('passes nested KPI metrics to the canvas widget and bubbles metric style edits with child context', async () => {
    const metrics = [{
      fieldKey: 'revenue', displayName: '收入', unit: '', helperText: '', visible: true,
      x: 12, y: 18, w: 160, h: 80,
      styles: { name: {}, value: {}, unit: {}, helper: {} },
    }]
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combo', type: 'combination', title: '组合卡片',
          children: [{ id: 'nested-kpi', type: 'kpi', title: '指标', kpiMetrics: metrics, layout: { x: 0, y: 0, col: 6, h: 120 } }],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: { name: 'KpiCardWidget', props: ['component'], emits: ['open-metric-style'], template: '<button data-testid="nested-kpi-edit" @click="$emit(\'open-metric-style\', { componentId: component.id, fieldKey: \'revenue\', field: \'value\' })">edit</button>' },
          ChartWidget: true, DataTableWidget: true, FilterSelectWidget: true, TimeFilterWidget: true,
          AiAnalysisWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    const kpi = wrapper.findComponent({ name: 'KpiCardWidget' })
    expect(kpi.props('component').kpiMetrics).toEqual(metrics)
    await wrapper.get('[data-testid="nested-kpi-edit"]').trigger('click')
    expect(wrapper.emitted('open-metric-style')?.[0]?.[0]).toMatchObject({
      componentId: 'nested-kpi', containerId: 'combo', childId: 'nested-kpi', fieldKey: 'revenue', field: 'value',
    })
  })

  it('bubbles nested KPI metric layout changes so projected child configuration can be persisted', async () => {
    const metrics = [{
      fieldKey: 'revenue', displayName: '收入', unit: '', helperText: '', visible: true,
      x: 12, y: 18, w: 160, h: 80, styles: defaultMetricStyles(),
    }]
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combo', type: 'combination', title: '组合卡片',
          children: [{ id: 'nested-kpi', type: 'kpi', title: '指标', kpiMetrics: metrics, layout: { x: 0, y: 0, col: 6, h: 120 } }],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: {
            name: 'KpiCardWidget',
            emits: ['metric-layout-change'],
            template: '<button data-testid="move-nested-kpi" @click="$emit(\'metric-layout-change\', { componentId: \'nested-kpi\', fieldKey: \'revenue\', x: 45, y: 58, w: 160, h: 80 })">move</button>',
          },
          ChartWidget: true, DataTableWidget: true, FilterSelectWidget: true, TimeFilterWidget: true,
          AiAnalysisWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    await wrapper.get('[data-testid="move-nested-kpi"]').trigger('click')

    expect(wrapper.emitted('metric-layout-change')?.[0]?.[0]).toEqual({
      componentId: 'nested-kpi', fieldKey: 'revenue', x: 45, y: 58, w: 160, h: 80,
    })
  })

  it('projects Python result fields for a nested KPI while preserving its configured metrics', () => {
    const configuredMetrics = [{
      fieldKey: 'old_metric', displayName: '旧指标', unit: '', helperText: '', visible: true,
      x: 12, y: 18, w: 160, h: 80, styles: defaultMetricStyles(),
    }]
    const wrapper = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'combo', type: 'combination', title: '组合卡片',
          children: [{ id: 'nested-kpi', type: 'kpi', title: '指标', kpiMetrics: configuredMetrics, layout: { x: 0, y: 0, col: 6, h: 120 } }],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
        componentDataMap: {
          'nested-kpi': {
            componentId: 'nested-kpi', renderType: 'kpi', pythonResultPreview: true,
            kpiList: [
              { fieldKey: '转化规模', name: '转化规模', value: '27948000' },
              { fieldKey: '转化人数', name: '转化人数', value: '2964' },
            ],
          },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: { name: 'KpiCardWidget', props: ['component', 'componentData'], template: '<div />' },
          ChartWidget: true, DataTableWidget: true, FilterSelectWidget: true, TimeFilterWidget: true,
          AiAnalysisWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    const kpi = wrapper.findComponent({ name: 'KpiCardWidget' })
    expect(kpi.props('component').kpiMetrics.map((metric: { fieldKey: string }) => metric.fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(kpi.props('componentData').kpiList).toHaveLength(2)
    expect(configuredMetrics).toHaveLength(1)
    wrapper.unmount()
  })

  it('drags a nested KPI metric without moving its combination child card', () => {
    const metric = {
      fieldKey: 'revenue', displayName: '收入', unit: '', helperText: '', visible: true,
      x: 12, y: 18, w: 160, h: 80, styles: defaultMetricStyles(),
    }
    const child = {
      id: 'nested-kpi', type: 'kpi' as const, title: '指标', kpiMetrics: [metric],
      layout: { x: 36, y: 48, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'combo', type: 'combination', title: '组合卡片',
          children: [child], containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          ChartWidget: true, DataTableWidget: true, FilterSelectWidget: true, TimeFilterWidget: true,
          AiAnalysisWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })
    const group = wrapper.get('.kpi-metric-group').element as HTMLElement
    Object.defineProperty(group, 'getBoundingClientRect', { value: () => ({ width: 500, height: 300 }) })
    const item = wrapper.get('[data-metric="revenue"]').element as HTMLElement
    Object.defineProperty(item, 'offsetWidth', { value: 160 })
    Object.defineProperty(item, 'offsetHeight', { value: 80 })

    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 12, clientY: 18 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 42, clientY: 58 }))
    window.dispatchEvent(new MouseEvent('mouseup'))

    expect(metric).toMatchObject({ x: 42, y: 58 })
    expect(child.layout).toEqual({ x: 36, y: 48, col: 6, h: 120 })
    wrapper.unmount()
  })

  it('resizes a combination child continuously and keeps the same width after release', async () => {
    const child = {
      id: 'resizable-child', type: 'table' as const, title: '明细',
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'resize-combo', type: 'combination', title: '组合卡片',
          children: [child], containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })
    const body = wrapper.get('.cc-body').element as HTMLElement
    Object.defineProperties(body, {
      offsetWidth: { value: 600 },
      offsetHeight: { value: 300 },
      getBoundingClientRect: { value: () => ({ width: 600, height: 300, left: 0, top: 0, right: 600, bottom: 300 }) },
    })

    await wrapper.get('[data-child="resizable-child"]').trigger('mouseenter')
    await wrapper.get('.rs.e').trigger('mousedown', { clientX: 100, clientY: 100, button: 0 })
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 125, clientY: 100 }))
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    await nextTick()
    const previewStyle = wrapper.get('[data-child="resizable-child"]').attributes('style') ?? ''
    expect(previewStyle).toContain('width: calc(54.1667%)')

    window.dispatchEvent(new MouseEvent('mouseup'))
    await nextTick()
    expect(child.layout.col).toBe(6.5)
    expect(wrapper.get('[data-child="resizable-child"]').attributes('style')).toContain('width: calc(54.1667%)')
    wrapper.unmount()
  })

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

  it('renders default sample data and a title watermark for a newly added child', async () => {
    const component = {
      id: 'combination-sample-child',
      type: 'combination' as const,
      title: '组合卡片',
      children: [
        { id: 'cc-sample-kpi', type: 'kpi' as const, title: '订单数', layout: { x: 24, y: 24, col: 6, h: 90 } },
      ] as Array<{ id: string; type: 'kpi'; title: string; layout: { x: number; y: number; col: number; h: number } }>,
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

    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toMatchObject({
      renderType: 'kpi',
      kpi: { value: '1,284' },
    })
    const watermark = wrapper.get('[data-testid="cc-sample-data-watermark"]')
    expect(watermark.text()).toBe('示例数据')
    expect(watermark.element.parentElement?.classList.contains('cc-child-head')).toBe(true)
    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
  })

  it('captures a palette drop so it can be created inside the combination', async () => {
    const component = {
      id: 'combination-palette-drop',
      type: 'combination' as const,
      title: '组合卡片',
      children: [],
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

    const event = new Event('drop', { bubbles: true, cancelable: true })
    Object.defineProperty(event, 'dataTransfer', {
      value: { getData: (type: string) => (type === 'application/json' ? JSON.stringify({ type: 'kpi' }) : '') },
    })
    Object.defineProperty(event, 'clientX', { value: 40 })
    Object.defineProperty(event, 'clientY', { value: 50 })
    wrapper.get('.combination-card').element.dispatchEvent(event)
    await nextTick()

    expect(wrapper.emitted('add-component-into')?.[0]?.[0]).toMatchObject({
      containerId: component.id,
      type: 'kpi',
    })
    expect(component.children).toHaveLength(0)
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

  it('passes previewFillWidth down to nested combination cards so the whole canvas adapts', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        previewFillWidth: true,
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
    const outer = wrapper.findComponent({ name: 'CombinationCardWidget' })
    expect(outer.props('previewFillWidth')).toBe(true)
    // 修复前嵌套实例收不到该 prop → 内层基准宽度被持续校准、ccHScale 恒为 1，
    // 嵌套组合内部的组件在预览「自适应宽度」下不再横向自适应并可能出现交叉重叠
    const inner = outer.findComponent({ name: 'CombinationCardWidget' })
    expect(inner.props('previewFillWidth')).toBe(true)
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

  it('accepts a component-palette drop and emits an add request for the active combo position', async () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'outer-combination', type: 'combination', title: '外层组合', children: [],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })
    const body = wrapper.get('.cc-body').element as HTMLElement
    vi.spyOn(body, 'getBoundingClientRect').mockReturnValue({
      left: 100, top: 80, right: 600, bottom: 380, width: 500, height: 300,
      x: 100, y: 80, toJSON: () => ({}),
    } as DOMRect)
    const dataTransfer = {
      getData: (type: string) => type === 'application/json'
        ? JSON.stringify({ type: 'chart', chartType: 'line' })
        : '',
    }
    await wrapper.get('.combination-card').trigger('drop', {
      clientX: 220, clientY: 160, dataTransfer,
    })

    expect(wrapper.emitted('add-component-into')).toEqual([[
      { containerId: 'outer-combination', type: 'chart', chartType: 'line', x: 120, y: 80 },
    ]])
  })

  const mountEditableCombination = (children: Array<Record<string, unknown>>, extraProps: Record<string, unknown> = {}) =>
    mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'dragout-combo', type: 'combination', title: '组合卡片',
          children, containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
        ...extraProps,
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

  it('emits move-child-out when a child is dragged out of the combination body', () => {
    const child = { id: 'out-child', type: 'kpi' as const, title: '策略KPI', layout: { x: 36, y: 48, col: 6, h: 120 } }
    const wrapper = mountEditableCombination([child])
    const body = wrapper.get('.cc-body').element as HTMLElement
    Object.defineProperty(body, 'getBoundingClientRect', {
      value: () => ({ left: 100, top: 100, right: 600, bottom: 400, width: 500, height: 300 }),
    })
    const el = wrapper.get('[data-child="out-child"]').element as HTMLElement
    Object.defineProperty(el, 'offsetWidth', { value: 250 })
    Object.defineProperty(el, 'offsetHeight', { value: 120 })
    vi.spyOn(el, 'getBoundingClientRect').mockReturnValue({
      left: 150, top: 160, right: 400, bottom: 280, width: 250, height: 120,
      x: 150, y: 160, toJSON: () => ({}),
    } as DOMRect)

    // 按住子卡片（容器内）→ 拖出容器右边界 → 释放
    el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 200, clientY: 200 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 200, clientY: 200 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 700, clientY: 240 }))
    window.dispatchEvent(new MouseEvent('mouseup', { clientX: 700, clientY: 240 }))

    expect(wrapper.emitted('move-child-out')).toEqual([
      [{ containerId: 'dragout-combo', childId: 'out-child', clientX: 650, clientY: 200 }],
    ])
    // 拖出不改写容器内坐标
    expect(child.layout).toEqual({ x: 36, y: 48, col: 6, h: 120 })
    wrapper.unmount()
  })

  it('keeps normal in-container dragging when the pointer returns inside before release', () => {
    const child = { id: 'back-child', type: 'kpi' as const, title: '策略KPI', layout: { x: 36, y: 48, col: 6, h: 120 } }
    const wrapper = mountEditableCombination([child])
    const body = wrapper.get('.cc-body').element as HTMLElement
    Object.defineProperty(body, 'getBoundingClientRect', {
      value: () => ({ left: 100, top: 100, right: 600, bottom: 400, width: 500, height: 300 }),
    })
    const el = wrapper.get('[data-child="back-child"]').element as HTMLElement
    Object.defineProperty(el, 'offsetWidth', { value: 250 })
    Object.defineProperty(el, 'offsetHeight', { value: 120 })

    el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, clientX: 200, clientY: 200 }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 700, clientY: 240 })) // 出界
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 300, clientY: 220 })) // 拖回
    window.dispatchEvent(new MouseEvent('mouseup'))

    expect(wrapper.emitted('move-child-out')).toBeUndefined()
    // 拖回后按起点+位移计算：x = 36 + (300-200) = 136，y = 48 + (220-200) = 68，均在合法区间
    expect(child.layout).toMatchObject({ x: 136, y: 68 })
    wrapper.unmount()
  })

  it('shows a drop hint overlay while a canvas component is being dragged', () => {
    const wrapper = mountEditableCombination(
      [{ id: 'hint-child', type: 'kpi' as const, title: '策略KPI', layout: { x: 0, y: 0, col: 6, h: 120 } }],
      { dropHint: true },
    )
    expect(wrapper.find('[data-testid="combination-drop-hint"]').exists()).toBe(true)
    wrapper.unmount()
  })
})

describe('CombinationCardWidget · 页签移动与复制', () => {
  function mountWithTabs() {
    return mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'combo-tabs', type: 'combination', title: '组合卡片',
          children: [],
          containerConfig: {
            ...containerConfig,
            tabs: [
              { id: 't1', title: '页签 1', children: [] },
              { id: 't2', title: '页签 2', children: [] },
              { id: 't3', title: '页签 3', children: [] },
            ],
            activeTab: 't1',
          },
          position: { x: 0, y: 0, w: 12, h: 8 },
        },
        editable: true,
        selected: true,
      },
      global: { plugins: [i18n], stubs: { CombinationCardWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true, DashboardTabTitle: { template: '<span />' }, DashboardComponentIcon: { template: '<span />' } } },
    })
  }

  const dataTransfer = { setData: vi.fn(), getData: vi.fn(() => 't1'), effectAllowed: '', dropEffect: '' }

  it('拖拽页签 A 到页签 C 之后：emit move-tab（先删后插 toIndex）', async () => {
    const wrapper = mountWithTabs()
    const tabs = wrapper.findAll('.cc-tab')
    await tabs[0].trigger('dragstart', { dataTransfer })
    await tabs[2].trigger('dragover', { dataTransfer, clientX: 999 })
    expect(tabs[2].classes()).toContain('drop-after')
    await tabs[2].trigger('drop', { dataTransfer, clientX: 999 })
    await tabs[2].trigger('dragend', { dataTransfer })

    const move = wrapper.emitted('move-tab')?.at(-1)?.[0]
    expect(move).toEqual({ containerId: 'combo-tabs', tabId: 't1', toIndex: 3 })
    expect(tabs[2].classes()).not.toContain('drop-after')
    wrapper.unmount()
  })

  it('拖拽到目标前半段：emit 的 toIndex 指向插入其前', async () => {
    const wrapper = mountWithTabs()
    const tabs = wrapper.findAll('.cc-tab')
    await tabs[0].trigger('dragstart', { dataTransfer })
    await tabs[1].trigger('dragover', { dataTransfer, clientX: -1 })
    expect(tabs[1].classes()).toContain('drop-before')
    await tabs[1].trigger('drop', { dataTransfer, clientX: -1 })

    expect(wrapper.emitted('move-tab')?.at(-1)?.[0]).toEqual({ containerId: 'combo-tabs', tabId: 't1', toIndex: 1 })
    wrapper.unmount()
  })

  it('页签右键：emit tab-context-menu 携带坐标', async () => {
    const wrapper = mountWithTabs()
    await wrapper.findAll('.cc-tab')[1].trigger('contextmenu', { clientX: 120, clientY: 60 })

    expect(wrapper.emitted('tab-context-menu')?.[0]?.[0]).toMatchObject({ containerId: 'combo-tabs', tabId: 't2', x: 120, y: 60 })
    wrapper.unmount()
  })

  it('页签聚焦时 Ctrl+C：emit copy-tab；预览态（非编辑）不触发', async () => {
    const wrapper = mountWithTabs()
    const tab = wrapper.findAll('.cc-tab')[1]
    await tab.trigger('keydown', { ctrlKey: true, key: 'c' })
    expect(wrapper.emitted('copy-tab')?.[0]?.[0]).toEqual({ containerId: 'combo-tabs', tabId: 't2' })

    const preview = mount(CombinationCardWidget, {
      props: {
        component: {
          id: 'combo-preview', type: 'combination', title: '组合卡片', children: [],
          containerConfig: { ...containerConfig, tabs: [{ id: 't1', title: '页签 1', children: [] }], activeTab: 't1' },
          position: { x: 0, y: 0, w: 12, h: 8 },
        },
        editable: false,
      },
      global: { plugins: [i18n], stubs: { CombinationCardWidget: true, EmptyState: { template: '<div />' }, 'el-icon': true, DashboardTabTitle: { template: '<span />' }, DashboardComponentIcon: { template: '<span />' } } },
    })
    await preview.findAll('.cc-tab')[0].trigger('keydown', { ctrlKey: true, key: 'c' })
    expect(preview.emitted('copy-tab')).toBeUndefined()
    wrapper.unmount()
    preview.unmount()
  })

  it('头部不可见的子组件（内联筛选 / 隐藏标题栏）仍有右上角悬浮删除按钮，点击可删除', async () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          id: 'del-combo', type: 'combination', title: '组合卡片',
          children: [
            { id: 'child-filter', type: 'filter', title: '策略名称', config: { field: 'strategy_name' }, layout: { x: 0, y: 0, col: 6, h: 80 } },
            { id: 'child-time', type: 'timeFilter', title: '策略日期', config: { field: 'strategy_date' }, layout: { x: 0, y: 90, col: 6, h: 80 } },
            { id: 'child-hidden-table', type: 'table', title: '数据表格', titleBarStyle: 'hidden', layout: { x: 0, y: 180, col: 6, h: 120 } },
            { id: 'child-normal', type: 'table', title: '普通表格', layout: { x: 0, y: 310, col: 6, h: 120 } },
          ],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    // 内联筛选与隐藏标题栏的子组件：标题条不渲染，但悬浮删除按钮必须存在
    expect(wrapper.find('[data-child="child-filter"] .cc-child-head').exists()).toBe(false)
    expect(wrapper.find('[data-child="child-filter"] .cc-child-del--float').exists()).toBe(true)
    expect(wrapper.find('[data-child="child-time"] .cc-child-del--float').exists()).toBe(true)
    expect(wrapper.find('[data-child="child-hidden-table"] .cc-child-del--float').exists()).toBe(true)
    // 普通子组件：删除按钮仍在标题条内，不额外出现悬浮钮
    expect(wrapper.find('[data-child="child-normal"] .cc-child-head .cc-child-del').exists()).toBe(true)
    expect(wrapper.find('[data-child="child-normal"] .cc-child-del--float').exists()).toBe(false)

    // 点击悬浮删除：内联筛选子组件直接向外冒泡 delete-child
    await wrapper.get('[data-child="child-filter"] .cc-child-del--float').trigger('click')
    expect(wrapper.emitted('delete-child')?.[0]?.[0]).toEqual({ containerId: 'del-combo', childId: 'child-filter' })
  })

  it('预览态（非编辑）不渲染悬浮删除按钮', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        component: {
          id: 'del-combo-preview', type: 'combination', title: '组合卡片',
          children: [
            { id: 'child-filter', type: 'filter', title: '策略名称', config: { field: 'strategy_name' }, layout: { x: 0, y: 0, col: 6, h: 80 } },
          ],
          containerConfig, position: { x: 0, y: 0, w: 12, h: 8 },
        },
      },
      global: {
        plugins: [i18n],
        stubs: {
          KpiCardWidget: true, ChartWidget: true, DataTableWidget: true,
          FilterSelectWidget: true, TimeFilterWidget: true, AiAnalysisWidget: true,
          EmptyState: { template: '<div />' }, 'el-icon': true,
        },
      },
    })

    expect(wrapper.find('.cc-child-del--float').exists()).toBe(false)
  })
})
