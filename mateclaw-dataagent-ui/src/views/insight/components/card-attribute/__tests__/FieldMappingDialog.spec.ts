import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import FieldMappingDialog from '../FieldMappingDialog.vue'
import { useInsight } from '../useInsight'
import type { DatasetConfig } from '../useInsight'

const { state } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-input': {
    props: ['modelValue'],
    template: '<input :value="modelValue" v-bind="$attrs" />',
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
    fieldMapping: [],
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

describe('字段名称弹窗 · schema 驱动', () => {
  it('按数据集 schema 铺出字段名/显示名/目标名称三列', async () => {
    const wrapper = await openWith(
      dataset({
        schema: [
          { name: 'strategy_id', displayName: '策略 ID' },
          { name: 'event_date' },
        ],
      }),
    )

    const rows = wrapper.findAll('.fm-row')
    expect(rows).toHaveLength(2)
    const inputs = rows[0].findAll('input')
    expect(inputs.map((i) => i.element.value)).toEqual(['strategy_id', '策略 ID', '策略 ID'])
    expect(rows[1].findAll('input').map((i) => i.element.value)).toEqual(['event_date', '', 'event_date'])
  })

  it('字段名与字段描述只读，只有目标名称可编辑', async () => {
    const wrapper = await openWith(dataset({ schema: [{ name: 'amount', displayName: '金额' }] }))
    const inputs = wrapper.find('.fm-row').findAll('input')

    expect(inputs[0].attributes('readonly')).toBeDefined()
    expect(inputs[1].attributes('readonly')).toBeDefined()
    expect(inputs[2].attributes('readonly')).toBeUndefined()
  })

  it('不再提供手工新增入口', async () => {
    const wrapper = await openWith(dataset({ schema: [{ name: 'amount' }] }))
    expect(wrapper.text()).not.toContain('添加字段映射')
  })

  it('无 schema 且无法拉取时给空态提示，不出现手工新增入口', async () => {
    // 接口类型尚未登记受控接口定义 → 无法获取字段结构
    const wrapper = await openWith(dataset({ sourceType: 'api', schema: [] }))

    expect(wrapper.find('.fm-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('不支持手工新增')
    expect(wrapper.text()).toContain('登记受控接口定义')
  })

  it('无 schema 但配置可预览时，空态提供「重新获取字段」', async () => {
    const wrapper = await openWith(
      dataset({ jdbc: { db: '1', sql: 'select 1' }, schema: [] }),
    )

    expect(wrapper.find('.fm-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('重新获取字段')
  })

  it('已保存的字段映射在缓存缺失时仍然展示，不清空用户配置', async () => {
    const wrapper = await openWith(
      dataset({
        sourceType: 'api',
        fieldMapping: [{ source: 'strategy_id', desc: '', target: '策略编号' }],
      }),
    )

    const inputs = wrapper.find('.fm-row').findAll('input')
    expect(inputs.map((i) => i.element.value)).toEqual(['strategy_id', '', '策略编号'])
  })
})
