import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { compileStyle, parse } from '@vue/compiler-sfc'
import { createI18n } from 'vue-i18n'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import DashboardCanvas from '../DashboardCanvas.vue'
import DashboardComponentIcon from '../DashboardComponentIcon.vue'
import DashboardTitleIconStyleDialog from '../DashboardTitleIconStyleDialog.vue'
import { DASHBOARD_CANVAS_MIN_HEIGHT, DASHBOARD_CANVAS_MIN_WIDTH } from '../dashboardCanvasConstants'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'

const stubs = {
  GridLayout: { template: '<div><slot /></div>' },
  GridItem: { template: '<div><slot /></div>' },
  KpiCardWidget: { name: 'KpiCardWidget', props: ['componentData'], template: '<div />' },
  ChartWidget: { name: 'ChartWidget', props: ['componentData'], template: '<div />' },
  DataTableWidget: { name: 'DataTableWidget', props: ['component', 'componentData', 'showTitle', 'sampleMode'], template: '<div />' },
  FilterSelectWidget: { name: 'FilterSelectWidget', props: ['component'], template: '<div />' },
  TimeFilterWidget: { name: 'TimeFilterWidget', props: ['component'], template: '<div />' },
  AiAnalysisWidget: { name: 'AiAnalysisWidget', props: ['componentData'], template: '<div />' },
  CombinationCardWidget: { name: 'CombinationCardWidget', props: ['componentDataMap', 'sampleMode'], template: '<div />' },
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

  it('renders default component sample data with a visible watermark until a dataset is configured', () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    const watermark = wrapper.get('[data-testid="sample-data-watermark"]')
    expect(watermark.text()).toBe('示例数据')
    expect(watermark.classes()).toContain('sample-data-watermark--titlebar')
    expect(watermark.element.parentElement?.classList.contains('grid-item-toolbar')).toBe(true)
    expect(wrapper.find('.grid-item-body [data-testid="sample-data-watermark"]').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toMatchObject({
      componentId: 'kpi-1', renderType: 'kpi', kpi: { value: '1,284' },
    })

    const configured = mount(DashboardCanvas, {
      props: {
        components: [{ ...component, config: { datasetPipeline: { datasetInputs: [{ alias: 'orders' }] } } }],
        editable: true,
      },
      global: { stubs, plugins: [i18n] },
    })
    expect(configured.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
    expect(configured.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toBeUndefined()
  })

  it('renders validated sample fixtures directly in KPI, chart, and table components', () => {
    const components = [
      component,
      { ...component, id: 'chart-sample', type: 'chart' as const, chartType: 'pie' as const },
      { ...component, id: 'table-sample', type: 'table' as const },
      { ...component, id: 'ai-sample', type: 'aiAnalysis' as const },
    ]
    const wrapper = mount(DashboardCanvas, {
      props: { components, editable: true },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData')).toMatchObject({ renderType: 'kpi', kpi: { value: '1,284' } })
    expect(wrapper.findComponent({ name: 'ChartWidget' }).props('componentData')).toMatchObject({ renderType: 'echarts', option: { series: [{ type: 'pie' }] } })
    expect(wrapper.findComponent({ name: 'DataTableWidget' }).props('componentData').table.rows).toHaveLength(45)
    expect(wrapper.findComponent({ name: 'AiAnalysisWidget' }).props('componentData')).toMatchObject({ renderType: 'aiAnalysis' })
  })

  it('prefers dataset display names over stale runtime labels for every data component', () => {
    const components = [
      { ...component, config: { datasetPipeline: { datasetInputs: [{ datasetId: 'orders', alias: 'orders', fieldMappings: [{ source: 'raw_amount', target: '销售额' }] }] } } },
      { ...component, id: 'chart-1', type: 'chart' as const, config: { datasetPipeline: { datasetInputs: [{ datasetId: 'orders', alias: 'orders', fieldMappings: [{ source: 'raw_amount', target: '销售额' }] }] } } },
      { ...component, id: 'table-1', type: 'table' as const, config: { datasetPipeline: { datasetInputs: [{ datasetId: 'orders', alias: 'orders', fieldMappings: [{ source: 'raw_amount', target: '销售额' }] }] } } },
    ]
    const staleData = {
      componentId: 'kpi-1', renderType: 'kpi' as const,
      fieldLabels: { raw_amount: 'raw_amount', derived_amount: '派生指标标题' },
      kpiList: [{ fieldKey: 'raw_amount', name: 'raw_amount', value: '120' }],
    }
    const wrapper = mount(DashboardCanvas, {
      props: {
        components,
        editable: true,
        componentDataMap: {
          'kpi-1': staleData,
          'chart-1': { ...staleData, componentId: 'chart-1', renderType: 'echarts', option: { series: [{ name: 'raw_amount', data: [120] }] } },
          'table-1': { ...staleData, componentId: 'table-1', renderType: 'table', table: { columns: ['raw_amount'], rows: [['120']] } },
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    const expectedLabels = { raw_amount: '销售额', derived_amount: '派生指标标题' }
    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).props('componentData').fieldLabels).toEqual(expectedLabels)
    expect(wrapper.findComponent({ name: 'ChartWidget' }).props('componentData').fieldLabels).toEqual(expectedLabels)
    expect(wrapper.findComponent({ name: 'DataTableWidget' }).props('componentData').fieldLabels).toEqual(expectedLabels)
  })

  it('passes resolved display names to components nested inside a combination card', () => {
    const child = {
      id: 'nested-kpi',
      type: 'kpi' as const,
      title: '销售额',
      layout: { x: 0, y: 0, col: 12, h: 120 },
      config: { datasetPipeline: { datasetInputs: [{ datasetId: 'orders', alias: 'orders', fieldMappings: [{ source: 'raw_amount', target: '销售额' }] }] } },
    }
    const combination = {
      ...component,
      id: 'combination-1',
      type: 'combination' as const,
      children: [child],
      containerConfig: { layoutMode: 'free' as const, tabs: [] },
    }
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [combination],
        editable: true,
        componentDataMap: {
          'nested-kpi': {
            componentId: 'nested-kpi',
            renderType: 'kpi',
            fieldLabels: { raw_amount: 'raw_amount' },
            kpiList: [{ fieldKey: 'raw_amount', name: 'raw_amount', value: '120' }],
          },
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.findComponent({ name: 'CombinationCardWidget' }).props('componentDataMap')['nested-kpi'].fieldLabels).toEqual({ raw_amount: '销售额' })
  })

  it('starts at 100 percent and applies manually entered zoom', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('[role="toolbar"][aria-label="画布缩放"]').exists()).toBe(true)
    const zoomInput = wrapper.get('input[aria-label="画布缩放百分比"]')
    expect((zoomInput.element as HTMLInputElement).value).toBe('100')
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain('zoom: 1')

    await wrapper.get('[aria-label="放大画布"]').trigger('click')
    expect((zoomInput.element as HTMLInputElement).value).toBe('110')

    await zoomInput.setValue('75')
    await zoomInput.trigger('keydown.enter')
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain('zoom: 0.75')
    expect((zoomInput.element as HTMLInputElement).value).toBe('75')
  })

  it('hides the canvas zoom toolbar while a modal overlay is open, then restores it', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
      attachTo: document.body,
    })
    const toolbar = wrapper.get('.canvas-zoom-toolbar').element as HTMLElement
    const source = readFileSync(resolve(process.cwd(), 'src/views/insight/components/DashboardCanvas.vue'), 'utf8')
    const { descriptor, errors } = parse(source, { filename: 'DashboardCanvas.vue' })
    expect(errors).toHaveLength(0)
    const scopeId = [...toolbar.attributes].find((attribute) => attribute.name.startsWith('data-v-'))?.name
    expect(scopeId).toBeTruthy()
    const compiledStyle = compileStyle({
      source: descriptor.styles[0].content,
      filename: 'DashboardCanvas.vue',
      id: scopeId!,
      scoped: descriptor.styles[0].scoped,
    })
    expect(compiledStyle.errors).toHaveLength(0)
    const style = document.createElement('style')
    style.textContent = compiledStyle.code
    document.head.append(style)

    const directOverlay = document.createElement('div')
    directOverlay.className = 'el-overlay'
    directOverlay.style.display = 'none'
    const sidebar = document.createElement('div')
    sidebar.className = 'card-attr-sidebar'
    const nestedOverlay = document.createElement('div')
    nestedOverlay.className = 'el-overlay'
    nestedOverlay.style.display = 'none'
    sidebar.append(nestedOverlay)

    try {
      expect(window.getComputedStyle(toolbar).visibility).toBe('visible')
      document.body.append(directOverlay, sidebar)
      await nextTick()
      expect(window.getComputedStyle(document.body).visibility).toBe('visible')
      expect(window.getComputedStyle(toolbar).visibility).toBe('visible')

      directOverlay.style.removeProperty('display')
      await nextTick()
      expect(window.getComputedStyle(document.body).visibility).toBe('visible')
      expect(window.getComputedStyle(toolbar).visibility).toBe('hidden')

      directOverlay.remove()
      await nextTick()
      expect(window.getComputedStyle(toolbar).visibility).toBe('visible')

      nestedOverlay.style.removeProperty('display')
      await nextTick()
      expect(window.getComputedStyle(toolbar).visibility).toBe('hidden')

      nestedOverlay.style.display = 'none'
      await nextTick()
      expect(window.getComputedStyle(toolbar).visibility).toBe('visible')
    } finally {
      directOverlay.remove()
      sidebar.remove()
      style.remove()
      wrapper.unmount()
    }
  })

  it('hides the duplicate table title in the editable canvas', () => {
    const table = { ...component, id: 'table-1', type: 'table' as const }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [table], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    const tableWidget = wrapper.findComponent({ name: 'DataTableWidget' })
    expect(tableWidget.props('showTitle')).toBe(false)
    expect(tableWidget.props('sampleMode')).toBe(true)
  })

  it('does not mark a combination card as sample data', () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [{
          id: 'combination-1',
          type: 'combination',
          title: '组合卡片',
          position: { x: 0, y: 0, w: 6, h: 4 },
          children: [],
          containerConfig: { layoutMode: 'free', tabs: [] },
        }],
        editable: true,
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'CombinationCardWidget' }).props('sampleMode')).toBe(false)
  })

  it('does not mark filter and time filter as sample data', () => {
    const filter = {
      id: 'filter-1',
      type: 'filter' as const,
      title: '区域',
      position: { x: 0, y: 0, w: 4, h: 2 },
    }
    const timeFilter = {
      id: 'time-filter-1',
      type: 'timeFilter' as const,
      title: '时间范围',
      position: { x: 4, y: 0, w: 4, h: 2 },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [filter, timeFilter], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.find('[data-testid="sample-data-watermark"]').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'FilterSelectWidget' }).props('component')).toEqual(filter)
    expect(wrapper.findComponent({ name: 'TimeFilterWidget' }).props('component')).toEqual(timeFilter)
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

  it('previews a custom title icon color on the matching canvas component', async () => {
    const dashboardTheme = resolveDashboardTheme({
      presetId: 'blue',
      componentColorMode: 'uniform',
      overrides: { primary: '#123456' },
    }, 'light')
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true, dashboardTheme },
      global: { stubs, plugins: [i18n] },
    })

    await wrapper.get('[aria-label="编辑标题图标 订单数"]').trigger('click')
    const dialog = wrapper.findComponent(DashboardTitleIconStyleDialog)
    expect(dialog.props('defaultColor')).toBe('#123456')

    const customStyle = { colorMode: 'custom' as const, color: '#e87431' }
    dialog.vm.$emit('preview', customStyle)
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(DashboardComponentIcon).props('titleIconStyle')).toEqual(customStyle)
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
    expect(canvas.attributes('style') ?? '').not.toContain('min-width:')
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain('zoom:')
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain(`width: ${DASHBOARD_CANVAS_MIN_WIDTH}px`)
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain(`min-height: ${DASHBOARD_CANVAS_MIN_HEIGHT}px`)
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
