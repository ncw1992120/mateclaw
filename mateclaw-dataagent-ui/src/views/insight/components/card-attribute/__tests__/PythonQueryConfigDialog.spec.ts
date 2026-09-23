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

describe('PythonQueryConfigDialog', () => {
  it('使用查询配置弹窗结构展示字段、筛选字段、排序和分页并保存', async () => {
    const wrapper = mount(PythonQueryConfigDialog, {
      props: { modelValue: true, config, fieldCatalog: config.displayFields },
      global: { stubs },
    })

    expect(wrapper.findAll('[data-testid="python-qc-field-row"]').length).toBe(2)
    expect(wrapper.findAll('[data-testid="python-qc-filter-row"]').length).toBe(1)
    expect(wrapper.text()).toContain('允许排序')
    expect(wrapper.text()).toContain('分页')

    await wrapper.find('[data-testid="python-qc-save"]').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
    expect((wrapper.emitted('save')![0][0] as FinalResultQueryConfig).displayFields.map((field) => field.field)).toEqual(['region', 'amount'])
  })
})
