import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DataTableWidget from '../DataTableWidget.vue'
import { resolveComponentSample } from '@/utils/component-sample-data'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': { insight: { tableExportCsv: '导出 CSV', timeRange: {} } } },
  missingWarn: false,
  fallbackWarn: false,
})

const component = { id: 'table-1', type: 'table', title: '数据表格', enableTimeFilter: false } as any
const sampleData = resolveComponentSample(component).renderData

const stubs = {
  'el-table': {
    name: 'ElTable',
    props: ['data'],
    emits: ['sort-change'],
    template: `<div>
      <button data-testid="sort-ascending" @click="$emit('sort-change', { prop: 'col_1', order: 'ascending' })">升序</button>
      <button data-testid="sort-descending" @click="$emit('sort-change', { prop: 'col_1', order: 'descending' })">降序</button>
      <slot />
    </div>`,
  },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['prop', 'label', 'sortable'],
    template: '<div class="table-column" :data-prop="prop" :data-sortable="String(sortable)" />',
  },
  'el-pagination': { template: '<div data-testid="pagination" />' },
  'el-tooltip': { template: '<slot />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-date-picker': true,
  'el-icon': true,
}

describe('DataTableWidget sample interactions', () => {
  it('provides enough deterministic sample rows for scrolling and pagination', () => {
    expect(sampleData?.table?.rows.length).toBeGreaterThan(20)

    const wrapper = mount(DataTableWidget, {
      props: { component, componentData: sampleData, sampleMode: true },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('[data-testid="pagination"]').exists()).toBe(true)
    expect(wrapper.get('.table-wrapper').exists()).toBe(true)
  })

  it('sorts the sample numeric column in both directions before paging', async () => {
    const wrapper = mount(DataTableWidget, {
      props: { component, componentData: sampleData, sampleMode: true },
      global: { plugins: [i18n], stubs },
    })

    const columns = wrapper.findAll('.table-column')
    expect(columns[0]?.attributes('data-sortable')).toBe('false')
    expect(columns[1]?.attributes('data-sortable')).toBe('custom')

    await wrapper.get('[data-testid="sort-ascending"]').trigger('click')
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('data')[0]?.col_1).toBe('1')

    await wrapper.get('[data-testid="sort-descending"]').trigger('click')
    expect(table.props('data')[0]?.col_1).toBe('45')
  })

  it('does not show the inner title when the canvas owns the title bar', () => {
    const wrapper = mount(DataTableWidget, {
      props: { component, componentData: sampleData, showTitle: false, sampleMode: true },
      global: { plugins: [i18n], stubs },
    })

    expect(wrapper.find('.table-title').exists()).toBe(false)
    expect(wrapper.find('.table-header-right').exists()).toBe(true)
  })
})
