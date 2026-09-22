import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import FilterBindingDialog from '../FilterBindingDialog.vue'
import { useInsight } from '../useInsight'
import type { DatasetConfig } from '../useInsight'

const { state } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-select': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template:
      '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-button': { template: '<button type="button"><slot /></button>' },
}

function dataset(id: string, alias: string, fields: DatasetConfig['fields']): DatasetConfig {
  return { id, alias, sourceType: 'jdbc', sourceLabel: `JDBC · ${alias}`, fields, filters: [] }
}

async function openDialog(datasets: DatasetConfig[]) {
  state.datasets.splice(0, state.datasets.length, ...datasets)
  state.filterCatalog = [{ id: 'filter-1', title: '策略类型' }]
  state.filterBindings = []
  state.ui.filterBinding.visible = false
  const wrapper = mount(FilterBindingDialog, { global: { stubs } })
  state.ui.filterBinding.visible = true
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  state.datasets.splice(0, state.datasets.length)
  state.filterCatalog = []
  state.filterBindings = []
  state.ui.filterBinding.visible = false
})

describe('筛选器绑定 · 数据集字段联动', () => {
  it('切换数据集后字段映射只展示该数据集的维度字段', async () => {
    const wrapper = await openDialog([
      dataset('ds-1', 'table1', [
        { name: 'strategy_id', displayName: '策略 ID', role: 'dimension' },
        { name: 'amount', displayName: '金额', role: 'measure' },
      ]),
      dataset('ds-2', 'table2', [
        { name: 'strategy_code', displayName: '策略编码', role: 'dimension' },
        { name: 'total_amount', displayName: '总金额', role: 'measure' },
      ]),
    ])

    const selects = wrapper.findAll('select')
    expect(selects).toHaveLength(3)
    await selects[0].setValue('策略类型')
    await selects[1].setValue('ds-1')
    expect(selects[2].findAll('option').map((option) => option.element.value)).toEqual(['strategy_id'])

    await selects[1].setValue('ds-2')
    expect(selects[2].findAll('option').map((option) => option.element.value)).toEqual(['strategy_code'])
    expect(selects[2].element.value).toBe('')
    wrapper.unmount()
  })
})
