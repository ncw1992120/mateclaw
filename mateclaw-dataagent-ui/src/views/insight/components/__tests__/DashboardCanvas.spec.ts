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
  ElIcon: true,
  GridLayout: { template: '<div class="vgl-layout"><slot /></div>' },
  GridItem: { template: '<div><slot /></div>' },
  KpiCardWidget: { name: 'KpiCardWidget', props: ['component', 'componentData'], template: '<div />' },
  ChartWidget: { name: 'ChartWidget', props: ['componentData'], template: '<div />' },
  DataTableWidget: { name: 'DataTableWidget', props: ['component', 'componentData', 'showTitle', 'sampleMode'], template: '<div />' },
  FilterSelectWidget: { name: 'FilterSelectWidget', props: ['component', 'modelValue'], template: '<div />' },
  TimeFilterWidget: { name: 'TimeFilterWidget', props: ['component', 'modelValue', 'timeGranularity'], template: '<div />' },
  AiAnalysisWidget: { name: 'AiAnalysisWidget', props: ['componentData'], template: '<div />' },
  CombinationCardWidget: { name: 'CombinationCardWidget', props: ['componentDataMap', 'sampleMode'], template: '<div />' },
}
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': { insight: { canvasEmpty: '暂无组件' } } }, missingWarn: false, fallbackWarn: false })

/** jsdom 无 PointerEvent 构造器，用 Event + defineProperty 模拟 document 级 pointer 事件。 */
function pointerEvent(type: 'pointermove' | 'pointerup', clientX: number, clientY: number): Event {
  const event = new Event(type, { bubbles: true })
  Object.defineProperty(event, 'pointerId', { value: 1 })
  Object.defineProperty(event, 'clientX', { value: clientX })
  Object.defineProperty(event, 'clientY', { value: clientY })
  return event
}
const pointerMove = (x: number, y: number): Event => pointerEvent('pointermove', x, y)
const pointerUp = (x: number, y: number): Event => pointerEvent('pointerup', x, y)
/** 等待拖拽预览的 rAF 回调执行并完成 Vue 渲染。 */
async function afterFrame(): Promise<void> {
  await new Promise((resolve) => requestAnimationFrame(() => resolve()))
  await nextTick()
}

const component = {
  id: 'kpi-1',
  type: 'kpi' as const,
  title: '订单数',
  position: { x: 0, y: 0, w: 4, h: 3 },
}

describe('DashboardCanvas keyboard interaction', () => {
  it('uses the explicitly supplied background token when rendering a preview canvas', () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [component],
        editable: false,
        canvasBackground: 'var(--dashboard-preview-background)',
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.dashboard-canvas').attributes('style') ?? '')
      .toContain('--dashboard-canvas-background: var(--dashboard-preview-background)')
  })

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

  it('keeps combination content clipped to the resized outer card', () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [{
          ...component,
          id: 'combo-1',
          type: 'combination' as const,
          containerConfig: { layoutMode: 'free' as const, tabs: [] },
          children: [],
        }],
        editable: true,
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('[data-component-id="combo-1"]').classes()).not.toContain('has-combination-widget')
    expect(wrapper.get('[data-component-id="combo-1"] .grid-item-body').classes()).not.toContain('has-combination-widget')
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

  it('renders query states inside the affected component and exposes retry only for failures', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [component], editable: false,
        componentDataMap: { 'kpi-1': { componentId: 'kpi-1', renderType: 'kpi', queryStatus: 'timeout', error: '查询等待超时' } },
      },
      global: {
        stubs,
        plugins: [createI18n({
          legacy: false, locale: 'zh-CN', missingWarn: false, fallbackWarn: false,
          messages: { 'zh-CN': { insight: { componentQueryTimeout: '组件查询超时', componentQueryRetry: '重试' } } },
        })],
      },
    })

    expect(wrapper.findComponent({ name: 'ComponentQueryState' }).text()).toContain('组件查询超时')
    expect(wrapper.findComponent({ name: 'KpiCardWidget' }).exists()).toBe(false)
    await wrapper.get('.query-state-retry').trigger('click')
    expect(wrapper.emitted('retry-component-query')?.[0]).toEqual(['kpi-1'])

    await wrapper.setProps({ componentDataMap: { 'kpi-1': { componentId: 'kpi-1', renderType: 'kpi', queryStatus: 'empty' } } })
    expect(wrapper.findComponent({ name: 'ComponentQueryState' }).exists()).toBe(true)
    expect(wrapper.find('.query-state-retry').exists()).toBe(false)
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

  it('projects Python result fields onto the canvas KPI without mutating saved metrics', () => {
    const componentWithOldMetrics = {
      ...component,
      kpiMetrics: Array.from({ length: 13 }, (_, index) => ({
        fieldKey: `old_metric_${index}`,
        displayName: `旧指标${index}`,
        visible: true,
        x: index * 10,
        y: 0,
        w: 100,
        h: 60,
        styles: {
          name: { size: 14, family: 'system', color: '', colorMode: 'theme', bold: 'normal' },
          value: { size: 28, family: 'system', color: '', colorMode: 'theme', bold: 'bold' },
          unit: { size: 15, family: 'system', color: '', colorMode: 'theme', bold: 'normal' },
          helper: { size: 12, family: 'system', color: '', colorMode: 'theme', bold: 'normal' },
        },
      })),
    }
    const componentDataMap = {
      'kpi-1': {
        componentId: 'kpi-1',
        renderType: 'kpi' as const,
        pythonResultPreview: true,
        kpiList: [
          { fieldKey: '转化规模', name: '转化规模', value: '27948000' },
          { fieldKey: '转化人数', name: '转化人数', value: '2964' },
        ],
      },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [componentWithOldMetrics], editable: true, componentDataMap },
      global: { stubs, plugins: [i18n] },
    })

    const renderedComponent = wrapper.findComponent({ name: 'KpiCardWidget' }).props('component') as typeof componentWithOldMetrics
    expect(renderedComponent.kpiMetrics).toHaveLength(2)
    expect(renderedComponent.kpiMetrics.map((metric: { fieldKey: string }) => metric.fieldKey)).toEqual(['转化规模', '转化人数'])
    expect(componentWithOldMetrics.kpiMetrics).toHaveLength(13)
    wrapper.unmount()
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

  it('previews horizontal resizing continuously and commits the same fractional grid width', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: { stubs, plugins: [i18n] },
    })
    const grid = wrapper.get('.vgl-layout').element as HTMLElement
    grid.getBoundingClientRect = () => ({ width: 1440, height: 900, top: 0, right: 1440, bottom: 900, left: 0, x: 0, y: 0, toJSON() {} }) as DOMRect
    const card = wrapper.get('[data-component-id="kpi-1"]')
    const handle = wrapper.get('.resize-handle-right')

    await handle.trigger('pointerdown', { pointerId: 1, clientX: 500, clientY: 300, button: 0 })
    // pointerdown 同步阶段即进入自定义拉伸；真实列宽由舞台 1440px / 24 列计算
    expect(card.classes()).toContain('custom-resizing')

    // 60px 指针位移小于一个列宽，宽度也应按像素比例连续变化，不应整列跳宽
    document.dispatchEvent(pointerMove(560, 300))
    await afterFrame()
    const moveStyle = card.attributes('style') ?? ''
    expect(moveStyle).toContain('width: 286px')
    expect(moveStyle).toContain('margin-left: 0px')
    expect(wrapper.emitted('update-layout')).toBeUndefined()

    // 松手保留同一个小数列宽；不会回跳到整数列
    document.dispatchEvent(pointerUp(560, 300))
    await nextTick()
    expect(card.classes()).not.toContain('custom-resizing')
    const updated = wrapper.emitted('update-layout')?.at(-1)?.[0] as Array<{ w: number }>
    expect(updated[0].w).toBeGreaterThan(5)
    expect(updated[0].w).toBeLessThan(5.1)
  })

  it('previews top-edge resizing with a negative top margin from the snapped rows', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [{ ...component, position: { ...component.position, y: 1 } }],
        editable: true,
      },
      global: { stubs, plugins: [i18n] },
    })
    const card = wrapper.get('[data-component-id="kpi-1"]')
    const handle = wrapper.get('.resize-handle-top')

    await handle.trigger('pointerdown', { pointerId: 1, clientX: 300, clientY: 500, button: 0 })
    // jsdom rowStep=max(1,30+12)=42：-60px → 上移 1 行 → y:1→0、h:3→4 → margin-top=(0-1)*42=-42px
    document.dispatchEvent(pointerMove(300, 440))
    await afterFrame()
    const moveStyle = card.attributes('style') ?? ''
    expect(moveStyle).toContain('margin-top: -42px')
    expect(moveStyle).toContain('height: 156px')

    document.dispatchEvent(pointerUp(300, 440))
    await nextTick()
    expect(card.classes()).not.toContain('custom-resizing')
    expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toEqual([{ id: 'kpi-1', x: 0, y: 0, w: 4, h: 4 }])
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

  it('画布较窄时进入编辑页仍保持默认 100% 缩放', async () => {
    const clientWidth = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function (this: HTMLElement) {
      return this.classList.contains('dashboard-canvas') ? 600 : 0
    })
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      attachTo: document.body,
      global: { stubs, plugins: [i18n] },
    })
    await nextTick()
    await nextTick()

    expect((wrapper.get('input[aria-label="画布缩放百分比"]').element as HTMLInputElement).value).toBe('100')
    expect(wrapper.get('.canvas-grid-stage').attributes('style')).toContain('zoom: 1')
    wrapper.unmount()
    clientWidth.mockRestore()
  })

  it('将 KPI 指标布局更新事件转发给仪表盘编辑器', async () => {
    const wrapper = mount(DashboardCanvas, {
      props: { components: [component], editable: true },
      global: {
        stubs: {
          ...stubs,
          KpiCardWidget: {
            name: 'KpiCardWidget',
            emits: ['metric-layout-change'],
            template: '<button data-testid="emit-metric-layout" @click="$emit(\'metric-layout-change\', { componentId: \'kpi-1\', fieldKey: \'revenue\', x: 45, y: 58, w: 160, h: 80 })">move</button>',
          },
        },
        plugins: [i18n],
      },
    })

    await wrapper.get('[data-testid="emit-metric-layout"]').trigger('click')

    expect(wrapper.emitted('metric-layout-change')?.[0]?.[0]).toEqual({
      componentId: 'kpi-1', fieldKey: 'revenue', x: 45, y: 58, w: 160, h: 80,
    })
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

  it('隐藏标题栏的筛选器卡片仍保留右上角删除按钮', () => {
    const filter = {
      id: 'filter-1',
      type: 'filter' as const,
      title: '区域',
      titleBarStyle: 'hidden' as const,
      position: { x: 0, y: 0, w: 4, h: 2 },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [filter], editable: true },
      global: { stubs, plugins: [i18n] },
    })

    const toolbars = wrapper.findAll('.grid-item-toolbar')
    expect(toolbars.length).toBe(1)
    expect(toolbars[0].classes()).toContain('title-bar-hidden')
    // 隐藏整个标题栏后，删除组件用的 x 必须仍悬浮在右上角
    const deletes = wrapper.findAll('.grid-item-delete')
    expect(deletes.length).toBe(1)
    expect(deletes[0].attributes('aria-label')).toContain('删除组件')
  })

  it('restores runtime values into global filter controls after the preview canvas remounts', () => {
    const filter = {
      id: 'metric-filter',
      type: 'filter' as const,
      title: '转化指标名称',
      config: { field: 'metric_name', defaultValue: '默认指标', scope: 'global' },
      position: { x: 0, y: 0, w: 4, h: 2 },
    }
    const timeFilter = {
      id: 'date-filter',
      type: 'timeFilter' as const,
      title: '指标日期',
      config: { field: 'metric_time', scope: 'global' },
      position: { x: 4, y: 0, w: 4, h: 2 },
    }
    const selectedRange = { preset: 'custom' as const, start: '2026-09-01', end: '2026-09-03' }
    const wrapper = mount(DashboardCanvas, {
      props: {
        components: [filter, timeFilter, { ...component, config: { datasetPipeline: { datasetInputs: [{ datasetId: '101' }] } } }],
        editable: false,
        runtimeFilterState: {
          'metric-filter': { field: 'metric_name', scope: 'global', targetComponentIds: [], value: '用户选择的指标' },
          'date-filter': { field: 'metric_time', scope: 'global', targetComponentIds: [], value: selectedRange, timeGranularity: 'MONTH' },
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.findComponent({ name: 'FilterSelectWidget' }).props('modelValue')).toBe('用户选择的指标')
    expect(wrapper.findComponent({ name: 'TimeFilterWidget' }).props('modelValue')).toEqual(selectedRange)
    expect(wrapper.findComponent({ name: 'TimeFilterWidget' }).props('timeGranularity')).toBe('MONTH')
  })

  it('forwards time granularity changes from a top-level time filter', () => {
    const timeFilter = {
      id: 'date-filter', type: 'timeFilter' as const, title: '指标日期',
      config: { field: 'metric_time' }, position: { x: 0, y: 0, w: 4, h: 2 },
    }
    const wrapper = mount(DashboardCanvas, {
      props: { components: [timeFilter], editable: false },
      global: { stubs, plugins: [i18n] },
    })

    wrapper.findComponent({ name: 'TimeFilterWidget' }).vm.$emit('change', {
      field: 'metric_time', timeRange: undefined, timeGranularity: 'MONTH',
    })

    expect(wrapper.emitted('time-filter-change')?.[0]?.[0]).toEqual({
      componentId: 'date-filter', field: 'metric_time', timeRange: undefined, timeGranularity: 'MONTH',
    })
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

  it('keeps an expanded workspace for populated canvases and fits an empty canvas to the viewport', () => {
    expect(DASHBOARD_CANVAS_MIN_WIDTH).toBeGreaterThan(1024)
    expect(DASHBOARD_CANVAS_MIN_HEIGHT).toBeGreaterThan(768)

    // 空画布（默认状态）：不锁宽度，铺满浏览器宽度
    const empty = mount(DashboardCanvas, { props: { components: [], editable: true }, global: { stubs, plugins: [i18n] } })
    const emptyCanvas = empty.get('.dashboard-canvas')
    expect(emptyCanvas.attributes('data-canvas-workspace')).toBe('expanded')
    expect(emptyCanvas.attributes('style') ?? '').not.toContain('min-width:')
    const emptyStageStyle = empty.get('.canvas-grid-stage').attributes('style') ?? ''
    expect(emptyStageStyle).not.toContain('zoom:')
    expect(emptyStageStyle).not.toContain('width:')
    expect(emptyStageStyle).toContain(`min-height: ${DASHBOARD_CANVAS_MIN_HEIGHT}px`)

    // 已有组件的画布：保留固定最小宽度 + 缩放，保证双向滚动
    const populated = mount(DashboardCanvas, { props: { components: [component], editable: true }, global: { stubs, plugins: [i18n] } })
    const stageStyle = populated.get('.canvas-grid-stage').attributes('style') ?? ''
    expect(stageStyle).toContain('zoom:')
    expect(stageStyle).toContain(`width: ${DASHBOARD_CANVAS_MIN_WIDTH}px`)
    expect(stageStyle).toContain(`min-height: ${DASHBOARD_CANVAS_MIN_HEIGHT}px`)
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
