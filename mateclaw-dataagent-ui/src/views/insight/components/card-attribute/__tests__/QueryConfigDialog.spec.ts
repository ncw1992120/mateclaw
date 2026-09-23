import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import QueryConfigDialog from '../QueryConfigDialog.vue'
import type { DatasetQueryConfig } from '@/types'

const stubs = {
  'el-dialog': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
  },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<select data-testid="select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'input'],
    template: '<input data-testid="input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value); $emit(\'input\', $event.target.value)" />',
  },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input data-testid="number" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))" />',
  },
  'el-switch': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input data-testid="switch" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
  'el-checkbox': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input data-testid="checkbox" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
  'el-tag': { template: '<span><slot /></span>' },
}

const fields = [
  { name: 'metric_date', displayName: '指标日期', role: 'dimension' },
  { name: 'strategy_id', displayName: '策略编码', role: 'dimension' },
  { name: 'in_account', displayName: '入金客户数', role: 'measure' },
]

const filterOptions = [
  { id: 'strategy_type', title: '策略类型', type: 'filter', selectionMode: 'multiple' },
  { id: 'date_range', title: '日期范围', type: 'timeFilter' },
]

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(QueryConfigDialog, {
    props: {
      modelValue: true,
      fields,
      filterOptions,
      initialConfig: null,
      ...props,
    },
    global: { stubs },
  })
}

function findTest(wrapper: ReturnType<typeof mount>, id: string) {
  return wrapper.findAll(`[data-testid="${id}"]`)
}

beforeEach(() => {})

describe('QueryConfigDialog', () => {
  it('打开时按 descriptor 全量字段初始化展示字段（技术字段只读展示）', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const rows = findTest(wrapper, 'qc-field-row')
    expect(rows.length).toBe(3)
    expect(rows[0].text()).toContain('metric_date')
    expect(rows[0].text()).toContain('维度')
    expect(rows[2].text()).toContain('指标')
  })

  it('删除字段联动：同步解除筛选绑定引用并移出排序白名单', async () => {
    const saved: DatasetQueryConfig[] = []
    const wrapper = mountDialog({
      initialConfig: {
        displayFields: [
          { field: 'metric_date', title: '指标日期', role: 'dimension' },
          { field: 'strategy_id', title: '策略编码', role: 'dimension' },
        ],
        parameterBindings: [
          { filterComponentId: 'strategy_type', parameterName: 'strategy_ids', field: 'strategy_id', operator: 'in' },
        ],
        sortPolicy: { enabled: true, mode: 'single', allowedFields: ['strategy_id'], defaultSort: null },
        paginationPolicy: { enabled: true, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: true },
      },
    })
    wrapper.vm.$props // noop
    await flushPromises()
    // 删除 strategy_id（第 2 行）
    await findTest(wrapper, 'qc-field-remove')[1].trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    saved.push(wrapper.emitted('save')![0][0] as DatasetQueryConfig)
    const config = saved[0]
    expect(config.displayFields.map((f) => f.field)).toEqual(['metric_date'])
    // 绑定引用被解除
    expect(config.parameterBindings).toHaveLength(0)
    // 排序白名单同步移除
    expect(config.sortPolicy.allowedFields).toEqual([])
  })

  it('重复展示名校验：保存被拒绝且不发 save 事件', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    // 把两个字段展示名改成相同
    const inputs = findTest(wrapper, 'qc-title-input')
    await inputs[0].setValue('同名')
    await inputs[1].setValue('同名')
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('拖动行调整列顺序', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const rows = findTest(wrapper, 'qc-field-row')
    await rows[0].trigger('dragstart')
    await rows[2].trigger('drop')
    const saved = wrapper.emitted('save')
    // 直接保存检查顺序
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.displayFields.map((f) => f.field)).toEqual(['strategy_id', 'in_account', 'metric_date'])
  })

  it('旧绑定草稿预填：无已保存配置时 legacyBindings 生效', async () => {
    const wrapper = mountDialog({
      legacyBindings: [
        { filterComponentId: 'strategy_type', parameterName: 'strategy_ids', field: 'strategy_id', operator: 'in' },
      ],
    })
    await flushPromises()
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.parameterBindings).toEqual([
      { filterComponentId: 'strategy_type', parameterName: 'strategy_ids', field: 'strategy_id', operator: 'in' },
    ])
  })

  it('空映射绑定被拒绝提交', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    expect(wrapper.emitted('save')).toBeUndefined()
  })

  it('筛选器绑定只展示筛选器名称和绑定对象，运算符不在界面重复展示', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    await flushPromises()
    const row = findTest(wrapper, 'qc-binding-row')[0]
    const selects = row.findAll('[data-testid="select"]')
    // 仅「筛选器名称」「绑定对象」两个下拉；操作符由筛选器自身语义决定
    expect(selects.length).toBe(2)
    expect(row.text()).not.toContain('运算符')

    // 选多选筛选器后，保存时仍按筛选器类型写入兼容字段
    await selects[0].setValue('strategy_type')
    await flushPromises()
    await selects[0].setValue('date_range')
    await flushPromises()

    // 保存后运算符按筛选器类型固定写入，界面不暴露该实现字段
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    await flushPromises()
    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.parameterBindings[0].operator).toBe('gte')
  })
})
