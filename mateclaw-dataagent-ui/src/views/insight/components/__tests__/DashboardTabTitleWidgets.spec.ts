import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import KpiCardWidget from '../KpiCardWidget.vue'
import DataTableWidget from '../DataTableWidget.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { kpiTabsLabel: '指标分页' } } },
  missingWarn: false,
  fallbackWarn: false,
})

const tabs = [{ id: 'summary', title: '汇总', dataSource: {} as any }]

describe('数据组件页签标题图标配置入口', () => {
  it('KPI 卡片在编辑态为每个页签提供独立图标样式入口', async () => {
    const wrapper = mount(KpiCardWidget, {
      props: {
        editable: true,
        component: { id: 'kpi-owner', type: 'kpi', title: '指标', tabs, position: { x: 0, y: 0, w: 4, h: 3 } } as any,
      },
      global: { plugins: [i18n], stubs: { 'el-icon': true } },
    })

    await wrapper.get('[aria-label="编辑页签图标 汇总"]').trigger('click')
    expect(wrapper.emitted('edit-tab-title-icon-style')?.[0]?.[0]).toMatchObject({
      componentId: 'kpi-owner', tabId: 'summary', tabKind: 'component',
    })
  })

  it('数据表在查看态不显示图标样式编辑入口', () => {
    const wrapper = mount(DataTableWidget, {
      props: {
        editable: false,
        component: { id: 'table-owner', type: 'table', title: '明细', tabs, position: { x: 0, y: 0, w: 4, h: 3 } } as any,
      },
      global: { plugins: [i18n], stubs: { 'el-icon': true, 'el-table': true, 'el-pagination': true } },
    })

    expect(wrapper.find('[aria-label="编辑页签图标 汇总"]').exists()).toBe(false)
  })

  it('数据表在编辑态把每个页签的配置请求交给画布统一弹窗处理', async () => {
    const wrapper = mount(DataTableWidget, {
      props: {
        editable: true,
        component: { id: 'table-owner', type: 'table', title: '明细', tabs, position: { x: 0, y: 0, w: 4, h: 3 } } as any,
      },
      global: { plugins: [i18n], stubs: { 'el-icon': true, 'el-table': true, 'el-pagination': true } },
    })

    await wrapper.get('[aria-label="编辑页签图标 汇总"]').trigger('click')
    expect(wrapper.emitted('edit-tab-title-icon-style')?.[0]?.[0]).toMatchObject({
      componentId: 'table-owner', tabId: 'summary', tabKind: 'component',
    })
  })
})
