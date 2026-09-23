import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import DashboardCanvas from '../DashboardCanvas.vue'
import DashboardComponentIcon from '../DashboardComponentIcon.vue'
import DashboardTitleIconStyleDialog from '../DashboardTitleIconStyleDialog.vue'
import { DASHBOARD_CANVAS_MIN_HEIGHT, DASHBOARD_CANVAS_MIN_WIDTH } from '../dashboardCanvasConstants'

const stubs = {
  GridLayout: { template: '<div><slot /></div>' },
  GridItem: { template: '<div><slot /></div>' },
  KpiCardWidget: { name: 'KpiCardWidget', props: ['component', 'componentData', 'editable', 'dashboardTheme', 'titleIconStylePreview', 'tabTitleIconStylePreview'], template: '<div />' },
  ChartWidget: {
    name: 'ChartWidget',
    props: ['component', 'componentData', 'editable', 'dashboardTheme', 'titleIconStylePreview', 'tabTitleIconStylePreview'],
    emits: ['edit-tab-title-icon-style'],
    template: '<div />',
  },
  DataTableWidget: { name: 'DataTableWidget', props: ['component', 'componentData', 'editable', 'dashboardTheme', 'titleIconStylePreview', 'tabTitleIconStylePreview'], template: '<div />' },
  FilterSelectWidget: { name: 'FilterSelectWidget', props: ['component', 'editable', 'dashboardTheme', 'titleIconStylePreview'], template: '<div />' },
  TimeFilterWidget: { name: 'TimeFilterWidget', props: ['component', 'editable', 'dashboardTheme', 'titleIconStylePreview'], template: '<div />' },
  AiAnalysisWidget: { name: 'AiAnalysisWidget', props: ['component', 'componentData', 'editable', 'dashboardTheme', 'titleIconStylePreview'], template: '<div />' },
  CombinationCardWidget: {
    name: 'CombinationCardWidget',
    props: ['component', 'componentDataMap', 'editable', 'dashboardTheme', 'titleIconStylePreview', 'componentTitleIconStylePreview', 'tabTitleIconStylePreview'],
    emits: ['edit-tab-title-icon-style'],
    template: '<div />',
  },
}
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': { insight: { canvasEmpty: '暂无组件' } } }, missingWarn: false, fallbackWarn: false })

const component = {
  id: 'kpi-1',
  type: 'kpi' as const,
  title: '订单数',
  position: { x: 0, y: 0, w: 4, h: 3 },
}

describe('DashboardCanvas keyboard interaction', () => {
  it('moves and resizes the selected component with keyboard arrows', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true, selectedId: 'kpi-1' },
      global: { stubs, plugins: [i18n] },
    })
    const card = wrapper.get('[data-component-id="kpi-1"]')
    await card.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 1, y: 0, w: 4, h: 3 }])
    await card.trigger('keydown', { key: 'ArrowDown', shiftKey: true })
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 1, y: 0, w: 4, h: 4 }])
  })

  it('applies the selected title bar style to the edit toolbar', () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [{ ...component, titleBarStyle: 'accent' as const }],
        editable: true,
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.grid-item-toolbar').classes()).toContain('title-bar-accent')
  })

  it('shows a configured title icon for filters moved into the preview global toolbar', () => {
    const style = { iconKey: 'calendar', colorMode: 'custom' as const, color: '#8c4a2f', strokeWidth: 3 as const }
    const filter = {
      ...component,
      id: 'global-filter',
      type: 'filter' as const,
      title: '渠道',
      titleIconStyle: style,
      config: { scope: 'global', field: 'channel' },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [filter], editable: false },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.global-filter-label').text()).toContain('渠道')
    expect(wrapper.findComponent(DashboardComponentIcon).props('titleIconStyle')).toEqual(style)
  })

  it('renames a top-level component from the canvas title toolbar', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.get('[aria-label="编辑组件标题 订单数"]').trigger('click')
    const input = wrapper.get('input[aria-label="组件标题"]')
    await input.setValue('新标题')
    await input.trigger('keyup', { key: 'Enter' })

    expect(wrapper.emitted('rename-component')).toEqual([[{ componentId: 'kpi-1', title: '新标题' }]])
  })

  it('opens title icon styling separately from renaming the component', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.get('[aria-label="编辑标题图标 订单数"]').trigger('click')

    expect(wrapper.find('input[aria-label="组件标题"]').exists()).toBe(false)
    expect(wrapper.findComponent(DashboardTitleIconStyleDialog).props('modelValue')).toBe(true)
  })

  it('keeps icon tools grouped with the title and previews edits on the canvas until canceled', async () => {
    const initialStyle = { iconKey: 'trend-charts', colorMode: 'theme' as const, strokeWidth: 2 as const }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [{ ...component, titleIconStyle: initialStyle }], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    const titleGroup = wrapper.get('.grid-item-title-group')
    expect(titleGroup.findComponent(DashboardComponentIcon).exists()).toBe(true)
    expect(titleGroup.get('[aria-label="编辑标题图标 订单数"]').exists()).toBe(true)
    expect(titleGroup.get('[aria-label="编辑组件标题 订单数"]').text()).toContain('订单数')

    await titleGroup.get('[aria-label="编辑标题图标 订单数"]').trigger('click')
    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    const previewStyle = { iconKey: 'chart-bar', colorMode: 'custom' as const, color: '#8c4a2f', strokeWidth: 3 as const }
    dialog.vm.$emit('preview', previewStyle)
    await wrapper.vm.$nextTick()

    expect(titleGroup.findComponent(DashboardComponentIcon).props('titleIconStyle')).toEqual(previewStyle)
    dialog.vm.$emit('update:modelValue', false)
    await wrapper.vm.$nextTick()
    expect(titleGroup.findComponent(DashboardComponentIcon).props('titleIconStyle')).toEqual(initialStyle)
  })

  it.each([
    ['kpi', 'KpiCardWidget'],
    ['chart', 'ChartWidget'],
    ['table', 'DataTableWidget'],
    ['filter', 'FilterSelectWidget'],
    ['timeFilter', 'TimeFilterWidget'],
    ['aiAnalysis', 'AiAnalysisWidget'],
    ['combination', 'CombinationCardWidget'],
  ] as const)('实时预览应传递到 %s 组件本身，而不只更新画布工具栏', async (type, widgetName) => {
    const owner = { ...component, id: `owner-${type}`, type } as any
    const wrapper = mount(DashboardCanvas, {
      props: { components: [owner], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    await wrapper.get(`[aria-label="编辑标题图标 ${owner.title}"]`).trigger('click')
    const previewStyle = { iconKey: 'chart-bar', colorMode: 'custom' as const, color: '#8c4a2f', strokeWidth: 3 as const }
    wrapper.findComponent(DashboardTitleIconStyleDialog).vm.$emit('preview', previewStyle)
    await wrapper.vm.$nextTick()

    const widget = wrapper.findComponent({ name: widgetName })
    const previewProp = widgetName === 'CombinationCardWidget' ? 'componentTitleIconStylePreview' : 'titleIconStylePreview'
    expect(widget.props(previewProp)).toEqual(previewStyle)
  })

  it('positions the icon dialog below its trigger and enables dragging', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    const trigger = wrapper.get('[aria-label="编辑标题图标 订单数"]')
    Object.defineProperty(trigger.element, 'getBoundingClientRect', {
      value: () => ({ top: 50, right: 80, bottom: 76, left: 60, width: 20, height: 26, x: 60, y: 50, toJSON: () => ({}) }),
    })

    await trigger.trigger('click')

    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    expect(dialog.props('draggable')).toBe(true)
    expect(dialog.props('top')).toBe('88px')
    expect(dialog.props('left')).toBe('60px')
  })

  it('opens the shared icon dialog for a tab and previews only that tab before applying', async () => {
    const initialStyle = { iconKey: 'trend-charts', colorMode: 'theme' as const, strokeWidth: 2 as const }
    const chart = {
      ...component,
      id: 'chart-tab-owner',
      type: 'chart' as const,
      tabs: [{ id: 'summary-tab', title: '汇总', titleIconStyle: initialStyle, dataSource: {} }],
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [chart], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    const chartWidget = wrapper.findComponent({ name: 'ChartWidget' })
    const anchor = document.createElement('button')
    chartWidget.vm.$emit('edit-tab-title-icon-style', {
      componentId: chart.id,
      tabId: 'summary-tab',
      tabKind: 'component',
      anchor,
    })
    await wrapper.vm.$nextTick()

    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    expect(dialog.props('titleIconStyle')).toEqual(initialStyle)

    const previewStyle = { iconKey: 'chart-bar', colorMode: 'custom' as const, color: '#aa5522', strokeWidth: 3 as const }
    dialog.vm.$emit('preview', previewStyle)
    await wrapper.vm.$nextTick()
    expect(chartWidget.props('tabTitleIconStylePreview')).toEqual({
      componentId: chart.id,
      tabId: 'summary-tab',
      titleIconStyle: previewStyle,
    })

    dialog.vm.$emit('save', previewStyle)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update-tab-title-icon-style')?.[0]?.[0]).toEqual({
      componentId: chart.id,
      tabId: 'summary-tab',
      tabKind: 'component',
      titleIconStyle: previewStyle,
    })
  })

  it('resolves nested card tabs by component identity and routes saved styles to the schema update event', async () => {
    const initialStyle = { iconKey: 'calendar', colorMode: 'theme' as const, strokeWidth: 2.5 as const }
    const nestedChart = {
      id: 'nested-chart',
      type: 'chart' as const,
      title: '子趋势',
      tabs: [{ id: 'nested-week', title: '本周', titleIconStyle: initialStyle, dataSource: {} }],
      layout: { x: 0, y: 0, col: 6, h: 180 },
    }
    const combination = {
      ...component,
      id: 'combination-owner',
      type: 'combination' as const,
      title: '容器',
      children: [nestedChart],
      containerConfig: { layoutMode: 'free' as const, tabs: [], activeTab: undefined },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [combination], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    const combinationWidget = wrapper.findComponent({ name: 'CombinationCardWidget' })
    combinationWidget.vm.$emit('edit-tab-title-icon-style', {
      componentId: nestedChart.id,
      tabId: 'nested-week',
      tabKind: 'component',
      anchor: document.createElement('button'),
    })
    await wrapper.vm.$nextTick()

    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    expect(dialog.props('titleIconStyle')).toEqual(initialStyle)
    const updatedStyle = { iconKey: 'chart-bar', colorMode: 'custom' as const, color: '#aa5522', strokeWidth: 3 as const }
    dialog.vm.$emit('save', updatedStyle)
    expect(wrapper.emitted('update-tab-title-icon-style')?.[0]?.[0]).toEqual({
      componentId: nestedChart.id,
      tabId: 'nested-week',
      tabKind: 'component',
      titleIconStyle: updatedStyle,
    })
  })

  it('opens the same dialog for a combination container tab', async () => {
    const style = { iconKey: 'pie-chart', colorMode: 'theme' as const, strokeWidth: 1.5 as const }
    const combination = {
      ...component,
      id: 'combination-tab-owner',
      type: 'combination' as const,
      title: '组合卡片',
      children: [],
      containerConfig: {
        layoutMode: 'free' as const,
        tabs: [{ id: 'container-summary', title: '概览', titleIconStyle: style, children: [] }],
        activeTab: 'container-summary',
      },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [combination], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    wrapper.findComponent({ name: 'CombinationCardWidget' }).vm.$emit('edit-tab-title-icon-style', {
      componentId: combination.id,
      tabId: 'container-summary',
      tabKind: 'combination',
      anchor: document.createElement('button'),
    })
    await wrapper.vm.$nextTick()

    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    expect(dialog.props('titleIconStyle')).toEqual(style)
    const updatedStyle = { iconKey: 'trend-charts', colorMode: 'custom' as const, color: '#aa5522', strokeWidth: 2.5 as const }
    dialog.vm.$emit('save', updatedStyle)
    expect(wrapper.emitted('update-tab-title-icon-style')?.[0]?.[0]).toEqual({
      componentId: combination.id,
      tabId: 'container-summary',
      tabKind: 'combination',
      titleIconStyle: updatedStyle,
    })
  })

  it('uses the component title bar as a drag handle for moving into a combination', async () => {
    const setData = vi.fn()
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.get('.grid-item-toolbar').trigger('dragstart', { dataTransfer: { setData, effectAllowed: '' } })

    expect(setData).toHaveBeenCalledWith('application/json', JSON.stringify({ kind: 'canvas-component', componentId: 'kpi-1', componentType: 'kpi' }))
  })

  it('offers one add-component action in an empty canvas', () => {
    const wrapper = mount(DashboardCanvas, { props: { components: [], editable: true }, global: { stubs, plugins: [i18n] } })
    expect(wrapper.get('[aria-label="从组件库添加组件"]').exists()).toBe(true)
  })

  it('keeps an expanded workspace so the canvas can scroll in both directions', () => {
    expect(DASHBOARD_CANVAS_MIN_WIDTH).toBeGreaterThan(1024)
    expect(DASHBOARD_CANVAS_MIN_HEIGHT).toBeGreaterThan(768)

    const wrapper = mount(DashboardCanvas, { props: { components: [], editable: true }, global: { stubs, plugins: [i18n] } })
    const canvas = wrapper.get('.dashboard-canvas')
    expect(canvas.attributes('data-canvas-workspace')).toBe('expanded')
    expect(canvas.attributes('style')).toContain(`min-width: ${DASHBOARD_CANVAS_MIN_WIDTH}px`)
    expect(canvas.attributes('style')).toContain(`min-height: ${DASHBOARD_CANVAS_MIN_HEIGHT}px`)
  })

  it('emits copy and paste commands from canvas keyboard shortcuts', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true, selectedId: 'kpi-1' },
      global: { stubs, plugins: [i18n] },
    })
    const card = wrapper.get('[data-component-id="kpi-1"]')

    await card.trigger('keydown', { key: 'c', ctrlKey: true })
    await card.trigger('keydown', { key: 'v', metaKey: true })

    expect(wrapper.emitted('copy-component')).toEqual([['kpi-1']])
    expect(wrapper.emitted('paste-component')).toEqual([[]])
  })

  it('opens the component context menu at the browser pointer position', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    await wrapper.get('[data-component-id="kpi-1"]').trigger('contextmenu', { clientX: 120, clientY: 240 })

    expect(wrapper.emitted('context-menu')).toEqual([[{ componentId: 'kpi-1', x: 120, y: 240 }]])
  })
})
