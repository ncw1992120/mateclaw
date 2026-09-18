import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import FieldMappingDialog from '../FieldMappingDialog.vue'
import { useInsight } from '../useInsight'
import type { DatasetConfig } from '../useInsight'
import type { DatasetFieldMeta } from '@/utils/field-mapping'

const { state } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-tooltip': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue'],
    // 支持 v-model：input 事件回抛 update:modelValue（原 stub 只渲染 value，无法断言编辑行为）
    template:
      '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'v-loading': true,
}

function dataset(partial: Partial<DatasetConfig>): DatasetConfig {
  return {
    id: 'ds-1',
    sourceType: 'jdbc',
    sourceLabel: 'JDBC · db1',
    alias: 'table1',
    fields: [],
    filters: [],
    ...partial,
  } as DatasetConfig
}

/** 打开弹窗：先落数据集，再翻转 visible 触发 watch 加载 */
async function openWith(ds: DatasetConfig) {
  state.datasets.splice(0, state.datasets.length, ds)
  state.ui.fieldMapping = { visible: false, datasetId: ds.id }
  const wrapper = mount(FieldMappingDialog, { global: { stubs } })
  state.ui.fieldMapping.visible = true
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  state.datasets.splice(0, state.datasets.length)
  state.ui.fieldMapping = { visible: false, datasetId: '' }
})

describe('字段名称弹窗 · 注册表驱动', () => {
  it('按数据集字段注册表铺出字段名/描述/展示名三列', async () => {
    const wrapper = await openWith(
      dataset({
        fields: [
          { name: 'strategy_id', displayName: '策略 ID', description: '策略 ID' },
          { name: 'event_date' },
        ],
      }),
    )

    const rows = wrapper.findAll('.fm-row')
    expect(rows).toHaveLength(2)
    const inputs = rows[0].findAll('input')
    expect(inputs.map((i) => i.element.value)).toEqual(['strategy_id', '策略 ID', '策略 ID'])
    // 展示名为空时留空（placeholder 提示回退字段名），不再把字段名当展示名存副本
    expect(rows[1].findAll('input').map((i) => i.element.value)).toEqual(['event_date', '', ''])
  })

  it('字段名与字段描述只读，只有展示名可编辑', async () => {
    const wrapper = await openWith(dataset({ fields: [{ name: 'amount', displayName: '金额' }] }))
    const inputs = wrapper.find('.fm-row').findAll('input')

    expect(inputs[0].attributes('readonly')).toBeDefined()
    expect(inputs[1].attributes('readonly')).toBeDefined()
    expect(inputs[2].attributes('readonly')).toBeUndefined()
  })

  it('不再提供手工新增入口', async () => {
    const wrapper = await openWith(dataset({ fields: [{ name: 'amount' }] }))
    expect(wrapper.text()).not.toContain('添加字段映射')
  })

  it('无字段且无法拉取时给空态提示，不出现手工新增入口', async () => {
    // 接口类型尚未登记受控接口定义 → 无法获取字段结构
    const wrapper = await openWith(dataset({ sourceType: 'api', fields: [] }))

    expect(wrapper.find('.fm-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('不支持手工新增')
    expect(wrapper.text()).toContain('登记受控接口定义')
  })

  it('无字段但配置可预览时，空态提供「重新获取字段」', async () => {
    const wrapper = await openWith(dataset({ jdbc: { db: '1', sql: 'select 1' }, fields: [] }))

    expect(wrapper.find('.fm-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('重新获取字段')
  })

  it('已保存的注册表在 schema 缺失时仍然展示，不清空用户配置', async () => {
    const wrapper = await openWith(
      dataset({
        sourceType: 'api',
        fields: [{ name: 'strategy_id', displayName: '策略编号', description: '策略唯一标识' }],
      }),
    )

    const inputs = wrapper.find('.fm-row').findAll('input')
    expect(inputs.map((i) => i.element.value)).toEqual(['strategy_id', '策略唯一标识', '策略编号'])
  })
})

describe('字段名称弹窗 · 展示名唯一性（决策 1）', () => {
  it('展示名重复时标红该行并禁用保存', async () => {
    const wrapper = await openWith(
      dataset({
        fields: [
          { name: 'a', displayName: '同名' },
          { name: 'b', displayName: '同名' },
        ],
      }),
    )

    expect(wrapper.findAll('.fm-invalid')).toHaveLength(2)
    expect(wrapper.text()).toContain('重复')
    const buttons = wrapper.findAll('button')
    const saveBtn = buttons[buttons.length - 1]
    expect(saveBtn.attributes('disabled')).toBeDefined()
  })

  it('展示名与另一字段的字段名冲突时同样拦截', async () => {
    const wrapper = await openWith(
      dataset({
        fields: [{ name: 'a', displayName: 'b' }, { name: 'b' }],
      }),
    )

    expect(wrapper.text()).toContain('冲突')
    const buttons = wrapper.findAll('button')
    expect(buttons[buttons.length - 1].attributes('disabled')).toBeDefined()
  })

  it('展示名可留空（回退字段名），不拦截', async () => {
    const wrapper = await openWith(
      dataset({ fields: [{ name: 'a', displayName: '' }, { name: 'b', displayName: 'B' }] }),
    )

    expect(wrapper.findAll('.fm-invalid')).toHaveLength(0)
    const buttons = wrapper.findAll('button')
    expect(buttons[buttons.length - 1].attributes('disabled')).toBeUndefined()
  })
})

describe('字段名称弹窗 · 双入口写同一份注册表', () => {
  it('保存后写回数据集注册表（指标配置等其它入口读的是同一份）', async () => {
    const ds = dataset({ fields: [{ name: 'a', displayName: 'A' }] })
    const wrapper = await openWith(ds)

    const input = wrapper.find('.fm-row').findAll('input')[2]
    await input.setValue('甲指标')
    wrapper.findAll('button').at(-1)!.trigger('click')
    await flushPromises()

    const saved: DatasetFieldMeta[] = ds.fields
    expect(saved[0].displayName).toBe('甲指标')
    expect(state.ui.fieldMapping.visible).toBe(false)
  })
})
