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
})
