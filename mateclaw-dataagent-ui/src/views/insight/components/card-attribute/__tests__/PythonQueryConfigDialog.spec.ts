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
  filterFields: [{ field: 'region', title: '区域', dataType: 'string', parameterName: 'region', operators: ['eq', 'in'] }],
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
  it('使用查询配置弹窗结构展示字段、筛选字段、排序和分页并保存', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    expect(wrapper.findAll('[data-testid="python-qc-field-row"]').length).toBe(2)
    expect(wrapper.findAll('[data-testid="python-qc-filter-row"]').length).toBe(1)
    expect(wrapper.text()).toContain('允许排序')
    expect(wrapper.text()).toContain('分页')

    await wrapper.find('[data-testid="python-qc-role-toggle"]').trigger('click')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.displayFields.map((field) => field.field)).toEqual(['region', 'amount'])
    expect(saved.displayFields[0].role).toBe('measure')
  })

  it('允许添加并编辑 Python 输出字段，同时将筛选字段保存为真实配置', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, displayFields: [], filterFields: [] }, fieldCatalog: [], filterOptions },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-new-field-name"]').setValue('customer_id')
    await wrapper.find('[data-testid="python-qc-new-field-title"]').setValue('客户ID')
    await wrapper.find('[data-testid="python-qc-add-new-field"]').trigger('click')

    expect(wrapper.findAll('[data-testid="python-qc-field-row"]')).toHaveLength(1)
    await wrapper.find('[data-testid="python-qc-field-tech-input"]').setValue('customer_code')
    await wrapper.find('[data-testid="python-qc-add-filter"]').trigger('click')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.displayFields[0]).toMatchObject({ field: 'customer_code', title: '客户ID' })
    expect(saved.filterFields[0]).toMatchObject({ field: 'customer_code', parameterName: 'customer_code' })
  })

  it('拒绝不符合英文数字下划线规则的技术字段名', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, displayFields: [], filterFields: [] }, fieldCatalog: [], filterOptions },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-new-field-name"]').setValue('客户名称')
    await wrapper.find('[data-testid="python-qc-add-new-field"]').trigger('click')

    expect(wrapper.findAll('[data-testid="python-qc-field-row"]')).toHaveLength(0)
  })

  it('筛选字段使用筛选器名称绑定结果字段', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [] }, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-add-filter"]').trigger('click')
    expect(wrapper.find('[data-testid="python-qc-filter-component"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="python-qc-filter-field"]').exists()).toBe(true)
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    const saved = wrapper.emitted('save')![0][0] as FinalResultQueryConfig
    expect(saved.filterFields[0]).toMatchObject({ field: 'region', filterComponentId: 'strategy-filter' })
  })

  it('筛选绑定对象是可编辑输入框并拒绝非法字段名', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config: { ...config, filterFields: [] }, fieldCatalog: config.displayFields, filterOptions },
      global: { stubs },
    })

    await wrapper.find('[data-testid="python-qc-add-filter"]').trigger('click')
    const target = wrapper.find('[data-testid="python-qc-filter-field"]')
    expect(target.element.tagName).toBe('INPUT')
    await target.setValue('字段-名称')
    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
