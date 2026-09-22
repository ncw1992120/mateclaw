import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import CombinationCardWidget from '../CombinationCardWidget.vue'
import { resolveDashboardTheme, themeCssVariables } from '@/utils/dashboard-theme'

const stubs = {
  KpiCardWidget: { template: '<div class="stub-kpi" />' },
  ChartWidget: { template: '<div class="stub-chart" />' },
  DataTableWidget: { template: '<div class="stub-table" />' },
  FilterSelectWidget: { template: '<div class="stub-filter" />' },
  TimeFilterWidget: { template: '<div class="stub-time-filter" />' },
  AiAnalysisWidget: { template: '<div class="stub-ai" />' },
  EmptyState: { template: '<div />' },
  'el-icon': true,
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { combination: { tabs: '页签', empty: '暂无组件', emptyEditable: '从组件库拖入组件', deleteChild: '删除子组件', deleteChildConfirm: '确认删除 {name}？', addTab: '添加页签', deleteTab: '删除页签' } }, common: { confirm: '确认', cancel: '取消' } } },
  missingWarn: false,
  fallbackWarn: false,
})

function component(containerConfig: Record<string, unknown>) {
  return {
    id: 'combination-1',
    type: 'combination' as const,
    title: '策略执行情况',
    children: [],
    containerConfig,
    position: { x: 0, y: 0, w: 12, h: 8 },
  }
}

describe('CombinationCardWidget · dashboard theme surfaces', () => {
  it('将子组件标题栏样式应用到编辑态子卡片标题栏', () => {
    const child = {
      id: 'child-kpi',
      type: 'kpi' as const,
      title: '策略概括',
      titleBarStyle: 'accent' as const,
      layout: { x: 0, y: 0, col: 6, h: 120 },
    }
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: true,
        component: {
          ...component({ layoutMode: 'free', tabs: [] }),
          children: [child],
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.cc-child-head').classes()).toContain('title-bar-accent')
  })

  it('解析主题时提供容器、卡片和控件三层背景 Token', () => {
    const vars = themeCssVariables(resolveDashboardTheme({ mode: 'preset', presetId: 'dark-data' }, 'light'))

    expect(vars['--db-surface-container']).toBeDefined()
    expect(vars['--db-surface-card']).toBe('var(--db-card)')
    expect(vars['--db-surface-control']).toBeDefined()
  })

  it('容器未指定自定义背景时跟随主题 Token，而不是固定白色', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        component: component({ layoutMode: 'free', tabs: [] }),
        dashboardTheme: resolveDashboardTheme({ mode: 'preset', presetId: 'dark-data' }, 'light'),
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.combination-card').attributes('style')).toContain('var(--db-surface-container')
  })

  it('容器明确选择自定义背景时保留用户颜色', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        component: {
          ...component({ layoutMode: 'free', tabs: [] }),
          visualStyle: { background: { mode: 'custom', color: '#FFE4D6' } },
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.combination-card').attributes('style')).toContain('--component-surface: #FFE4D6')
    expect(wrapper.get('.combination-card').attributes('style')).not.toContain('var(--db-surface-container)')
  })

  it('容器阴影使用所有组件共用的 visualStyle 配置', () => {
    const wrapper = mount(CombinationCardWidget, {
      props: {
        editable: false,
        component: {
          ...component({ layoutMode: 'free', tabs: [] }),
          visualStyle: { shadow: 'medium' },
        },
      },
      global: { stubs, plugins: [i18n] },
    })

    expect(wrapper.get('.combination-card').attributes('style')).toContain('--component-shadow: var(--shadow-card-hover')
  })
})
