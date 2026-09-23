import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DatasetFieldMeta } from '@/utils/field-mapping'
import type { DatasetQueryConfig, QueryDisplayField, QueryParameterBinding } from '@/types'

// ElMessage 在 jsdom 下无需真实渲染，置为无副作用桩
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }
})

const stubs = {
  'el-dialog': { template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>' },
  'el-tag': { props: ['type'], template: '<span class="stub-tag"><slot /></span>' },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<select :value="modelValue" v-bind="$attrs" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-input-number': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-switch': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />',
  },
  'el-checkbox': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
}

import QueryConfigDialog from '../card-attribute/QueryConfigDialog.vue'

// 故意打乱顺序：指标在前、维度在后，验证「维度默认置顶」排序
const fields = [
  { name: 'amount', displayName: '金额', role: 'measure' },
  { name: 'trade_date', displayName: '交易日期', role: 'dimension' },
  { name: 'cust_type', displayName: '客户类型', role: 'dimension' },
] as DatasetFieldMeta[]

function mountDialog(propsPartial: Record<string, unknown> = {}) {
  return mount(QueryConfigDialog, {
    props: {
      modelValue: true,
      fields,
      filterOptions: [{ id: 'f1', title: '时间筛选' }],
      initialConfig: null,
      ...propsPartial,
    },
    global: { stubs },
    attachTo: document.body,
  })
}

function rowFieldNames(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('.qc-field-row').map((row) => row.find('.qc-tech-field').text())
}
function rowRoleText(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('.qc-field-row').map((row) => row.find('.stub-tag').text())
}

describe('查询配置弹窗 · 展示字段', () => {
  it('默认展开，且表头为「字段名 / 展示名」', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(wrapper.find('.qc-field-wrap').isVisible()).toBe(true)
    const head = wrapper.find('.qc-thead').text()
    expect(head).toContain('字段名')
    expect(head).toContain('展示名')
  })

  it('维度字段默认置顶（指标排在最后）', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const names = rowFieldNames(wrapper)
    expect(names).toEqual(['trade_date', 'cust_type', 'amount'])
    const roles = rowRoleText(wrapper)
    expect(roles[0]).toBe('维度')
    expect(roles[2]).toBe('指标')
  })

  it('点击区头可折叠 / 展开展示字段', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(wrapper.find('.qc-field-wrap').isVisible()).toBe(true)
    await wrapper.find('.qc-collapsible').trigger('click')
    await flushPromises()
    expect(wrapper.find('.qc-field-wrap').isVisible()).toBe(false)
    expect(wrapper.find('.qc-chevron').classes()).toContain('is-collapsed')
    await wrapper.find('.qc-collapsible').trigger('click')
    await flushPromises()
    expect(wrapper.find('.qc-field-wrap').isVisible()).toBe(true)
  })

  it('编辑展示名后重复会标红', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const inputs = wrapper.findAll('.qc-title-input')
    await inputs[0].setValue('客户类型') // 与 cust_type 的展示名冲突
    await flushPromises()
    expect(wrapper.find('.qc-error').exists()).toBe(true)
  })

  it('点击角色徽标可在 维度/指标 间切换（数据源未返回 role 时手动标记指标）', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    // 第一行是维度（trade_date），点击其角色徽标 → 变为指标
    const firstTag = wrapper.find('.qc-field-row .qc-role-tag')
    expect(firstTag.text()).toBe('维度')
    await firstTag.trigger('click')
    await flushPromises()
    expect(wrapper.find('.qc-field-row .qc-role-tag').text()).toBe('指标')
  })
})

describe('查询配置弹窗 · 筛选器绑定', () => {
  const initialConfig: DatasetQueryConfig = {
    displayFields: [
      { field: 'amount', title: '金额', role: 'measure', dataType: undefined },
      { field: 'trade_date', title: '交易日期', role: 'dimension', dataType: undefined },
      { field: 'cust_type', title: '客户类型', role: 'dimension', dataType: undefined },
    ] as QueryDisplayField[],
    parameterBindings: [
      { filterComponentId: 'f1', parameterName: '', field: 'amount', operator: 'eq' },
    ] as QueryParameterBinding[],
    sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
    paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
  }

  it('表头为「筛选器名称 / 绑定对象」且不展示运算符', async () => {
    const wrapper = mountDialog({ initialConfig })
    await flushPromises()
    const head = wrapper.find('.qc-binding-thead').text()
    expect(head).toContain('筛选器名称')
    expect(head).toContain('绑定对象')
    expect(head).not.toContain('运算符')
  })

  it('绑定对象只显示展示名（不显示 字段名 · 展示名）', async () => {
    const wrapper = mountDialog({ initialConfig })
    await flushPromises()
    const bindingRow = wrapper.find('.qc-binding-row')
    // 第二个 select 为「绑定对象」字段选择
    const fieldSelect = bindingRow.findAll('select')[1]
    const labels = fieldSelect.findAll('option').map((o) => o.text())
    expect(labels).toEqual(['交易日期', '客户类型', '金额'])
    // 不应出现 "字段名 · 展示名" 的拼接
    expect(labels.some((l) => l.includes(' · '))).toBe(false)
  })

  it('保存时参数名由筛选器组件 id 兜底填充', async () => {
    const wrapper = mountDialog({ initialConfig })
    await flushPromises()
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    await flushPromises()
    const emitted = wrapper.emitted('save') as unknown[][]
    expect(emitted).toBeTruthy()
    const payload = emitted[0][0] as DatasetQueryConfig
    expect(payload.parameterBindings[0].parameterName).toBe('f1')
    expect(payload.parameterBindings[0].field).toBe('amount')
  })
})
