import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PythonQueryConfigDialog from '../PythonQueryConfigDialog.vue'
import type { FinalResultQueryConfig } from '@/types'

const config: FinalResultQueryConfig = {
  schemaFingerprint: 'schema-1',
  confirmed: false,
  displayFields: [
    { field: 'region', title: '区域', role: 'dimension', dataType: 'string' },
    { field: 'amount', title: '金额', role: 'measure', dataType: 'number' },
  ],
  filterFields: [{ field: 'region', title: '区域', dataType: 'string', parameterName: 'region', operators: ['eq', 'in'], filterComponentId: 'strategy-filter' }],
  sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
  paginationPolicy: { enabled: true, defaultPageSize: 20, maxPageSize: 100, returnTotalCount: false },
}

const stubs = {
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { props: ['modelValue'], emits: ['update:modelValue', 'change'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-switch': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
  'el-input-number': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />' },
  'el-checkbox': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
}

const filterOptions = [
  { id: 'strategy-filter', title: '策略类型', type: 'filter', selectionMode: 'single' as const },
  { id: 'date-filter', title: '交易日期', type: 'timeFilter', selectionMode: 'single' as const },
]

describe('PythonQueryConfigDialog', () => {
  it('查询配置仅配置筛选器绑定、排序和分页，不编辑展示字段', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    expect(wrapper.text()).not.toContain('展示字段')
    expect(wrapper.find('[data-testid="python-qc-field-rows"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="python-qc-filter-row"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('允许排序')
    expect(wrapper.text()).toContain('分页')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.displayFields).toEqual(config.displayFields)
  })

  it('允许保存没有筛选字段绑定的查询配置', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [] }, fieldCatalog: config.displayFields, filterOptions: [] },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
    expect((wrapper.emitted('save')![0][0] as FinalResultQueryConfig).filterFields).toEqual([])
  })

  it('查询配置不展示或编辑 Python 输出字段，但保留原有字段元数据', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    expect(wrapper.findAll('[data-testid="python-qc-field-row"]')).toHaveLength(0)
    expect(wrapper.findAll('[data-testid="python-qc-filter-row"]').length).toBe(1)
    expect(wrapper.text()).toContain('允许排序')
    expect(wrapper.text()).toContain('分页')

    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.displayFields).toEqual(config.displayFields)
  })

  it('筛选绑定使用页面筛选器并将其映射到 Python 输出字段', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [] }, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-add-filter"]').trigger('click')
    expect(wrapper.find('[data-testid="python-qc-filter-component"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="python-qc-filter-field"]').exists()).toBe(true)
    await wrapper.find('[data-testid="python-qc-filter-field"]').setValue('region')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.filterFields[0]).toMatchObject({ field: 'region', filterComponentId: 'strategy-filter' })
  })

  it('没有页面筛选器时不能创建空绑定', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [] }, filterOptions: [] },
      global: { stubs },
    })

    expect(wrapper.get('[data-testid="python-qc-add-filter"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('[data-testid="python-qc-filter-row"]')).toHaveLength(0)
  })

  it('历史自动生成但未绑定筛选器的字段不再显示或保存', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [{ ...config.filterFields[0], filterComponentId: undefined }] }, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    expect(wrapper.findAll('[data-testid="python-qc-filter-row"]')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无筛选器绑定')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect((wrapper.emitted('save')![0][0] as FinalResultQueryConfig).filterFields).toEqual([])
  })
})
