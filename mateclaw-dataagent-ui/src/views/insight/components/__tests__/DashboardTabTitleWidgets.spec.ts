import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import KpiCardWidget from '../KpiCardWidget.vue'
import DataTableWidget from '../DataTableWidget.vue'
import FilterSelectWidget from '../FilterSelectWidget.vue'
import TimeFilterWidget from '../TimeFilterWidget.vue'
import DashboardComponentIcon from '../DashboardComponentIcon.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { kpiTabsLabel: '指标分页' } } },
  missingWarn: false,
  fallbackWarn: false,
})

const tabs = [{ id: 'summary', title: '汇总', dataSource: {} as any }]

describe('数据组件页签标题图标配置入口', () => {
  it.each([
    ['筛选器', FilterSelectWidget, 'filter'],
    ['时间筛选', TimeFilterWidget, 'timeFilter'],
  ] as const)('%s 标题即时采用弹窗正在编辑的图标样式', (_, Widget, type) => {
    const preview = { iconKey: 'calendar', colorMode: 'custom' as const, color: '#8c4a2f', strokeWidth: 3 as const }
    const wrapper = mount(Widget, {
      props: {
        component: { id: `owner-${type}`, type, title: '筛选条件', config: { field: 'created_at' }, position: { x: 0, y: 0, w: 4, h: 3 } } as any,
        titleIconStylePreview: preview,
        showTitle: true,
      },
      global: {
        plugins: [i18n],
        stubs: { 'el-icon': true, 'el-select': true, 'el-option': true, 'el-date-picker': true },
      },
    })

    expect(wrapper.vm.$props.titleIconStylePreview).toEqual(preview)
    expect(wrapper.find('.filter-label, .time-filter-label').exists()).toBe(true)
    expect(wrapper.findComponent(DashboardComponentIcon).props('titleIconStyle')).toEqual(preview)
  })

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
