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
  { id: 'date_range', title: '日期范围', type: 'timeFilter', field: 'metric_date' },
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
  it('允许在没有筛选器绑定时保存数据集查询配置', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    await wrapper.find('[data-testid="qc-save"]').trigger('click')

    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.parameterBindings).toEqual([])
  })

  it('打开时按 descriptor 全量字段初始化展示字段（技术字段只读展示）', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const rows = findTest(wrapper, 'qc-field-row')
    expect(rows.length).toBe(3)
    expect(rows[0].text()).toContain('metric_date')
    expect(rows[0].text()).toContain('维度')
    expect(rows[2].text()).toContain('指标')
  })

  it('移除展示字段不影响筛选绑定，但会移出排序白名单', async () => {
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
    // 筛选字段与输出列独立，隐藏输出列后仍可用于过滤
    expect(config.parameterBindings).toHaveLength(1)
    expect(config.parameterBindings[0].field).toBe('strategy_id')
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

  it('优先用筛选器字段名匹配可查询字段，并在绑定对象选项中显示展示名', async () => {
    const wrapper = mountDialog({
      filterOptions: [{ id: 'strategy_filter', title: '策略筛选', type: 'filter', field: 'strategy_id' }],
    })
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const row = findTest(wrapper, 'qc-binding-row')[0]
    const selects = row.findAll('[data-testid="select"]')
    await selects[0].setValue('strategy_filter')

    expect((selects[1].element as HTMLSelectElement).value).toBe('strategy_id')
    expect(selects[1].find('option[value="strategy_id"]').text()).toBe('策略编码')
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.parameterBindings[0].field).toBe('strategy_id')
  })

  it('筛选器没有字段名时按筛选器名称匹配可查询字段展示名', async () => {
    const wrapper = mountDialog({
      filterOptions: [{ id: 'strategy_filter', title: '策略编码', type: 'filter' }],
    })
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const selects = findTest(wrapper, 'qc-binding-row')[0].findAll('[data-testid="select"]')
    await selects[0].setValue('strategy_filter')

    expect((selects[1].element as HTMLSelectElement).value).toBe('strategy_id')
  })

  it('筛选器名称匹配技术字段名时回填对应可查询字段', async () => {
    const wrapper = mountDialog({
      filterOptions: [{ id: 'metric-date-filter', title: 'metric_date', type: 'filter' }],
    })
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const selects = findTest(wrapper, 'qc-binding-row')[0].findAll('[data-testid="select"]')
    await selects[0].setValue('metric-date-filter')
    expect((selects[1].element as HTMLSelectElement).value).toBe('metric_date')
  })

  it('筛选器名称无法匹配可查询字段时保持未选择', async () => {
    const wrapper = mountDialog({
      filterOptions: [{ id: 'unknown-filter', title: '未匹配筛选器', type: 'filter' }],
    })
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const selects = findTest(wrapper, 'qc-binding-row')[0].findAll('[data-testid="select"]')
    await selects[0].setValue('unknown-filter')
    expect((selects[1].element as HTMLSelectElement).value).toBe('')
  })

  it('无法唯一匹配筛选器与可查询字段时不猜测默认字段', async () => {
    const wrapper = mountDialog({
      fields: [
        ...fields,
        { name: 'strategy_name', displayName: 'strategy_id', role: 'dimension' },
      ],
      filterOptions: [{ id: 'strategy_filter', title: 'strategy_id', type: 'filter' }],
    })
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const selects = findTest(wrapper, 'qc-binding-row')[0].findAll('[data-testid="select"]')
    await selects[0].setValue('strategy_filter')

    expect((selects[1].element as HTMLSelectElement).value).toBe('')
  })

  it('展示字段未包含筛选字段时仍可绑定，并保存完整可查询字段目录', async () => {
    const wrapper = mountDialog({
      fields: [
        ...fields,
        { name: 'created_by', displayName: '创建人', role: 'dimension' },
      ],
      initialConfig: {
        displayFields: [{ field: 'strategy_id', title: '策略编码', role: 'dimension' }],
        parameterBindings: [],
        sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
        paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
      },
      filterOptions: [{ id: 'creator', title: '创建人筛选', type: 'filter', field: 'created_by' }],
    })
    await flushPromises()
    await findTest(wrapper, 'qc-add-binding')[0].trigger('click')
    const selects = findTest(wrapper, 'qc-binding-row')[0].findAll('[data-testid="select"]')
    await selects[0].setValue('creator')

    expect((selects[1].element as HTMLSelectElement).value).toBe('created_by')
    expect(selects[1].find('option[value="created_by"]').text()).toBe('创建人')
    await wrapper.find('[data-testid="qc-save"]').trigger('click')
    const config = wrapper.emitted('save')![0][0] as DatasetQueryConfig
    expect(config.displayFields.map((field) => field.field)).toEqual(['strategy_id'])
    expect(config.parameterBindings[0].field).toBe('created_by')
    expect(config.queryableFields.map((field) => field.name)).toContain('created_by')
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
