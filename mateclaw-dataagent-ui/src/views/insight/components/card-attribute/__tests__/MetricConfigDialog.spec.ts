import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import MetricConfigDialog from '../MetricConfigDialog.vue'
import { useInsight } from '../useInsight'
import type { DatasetConfig } from '../useInsight'
import { buildKpiMetrics } from '@/utils/kpi-metrics'
import type { DatasetFieldMeta } from '@/utils/field-mapping'

const { state } = useInsight()

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-tooltip': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue'],
    template:
      '<input :value="modelValue" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-switch': {
    props: ['modelValue'],
    template: '<input type="checkbox" :checked="modelValue" />',
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
}

/** 弹窗内可编辑的文本框（排除只读列与 el-switch 的 checkbox） */
function editableInputs(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .find('.mc-row')
    .findAll('input')
    // 注意：readonly 属性的值是空字符串（falsy），必须用 undefined 判断
    .filter((i) => i.attributes('readonly') === undefined && i.attributes('type') !== 'checkbox')
}

function dataset(fields: DatasetFieldMeta[]): DatasetConfig {
  return {
    id: 'ds-1',
    sourceType: 'jdbc',
    sourceLabel: 'JDBC · db1',
    alias: 'table1',
    fields,
    filters: [],
  } as DatasetConfig
}

/** 落数据集 + 投影指标，再挂载弹窗 */
async function openWith(fields: DatasetFieldMeta[]) {
  const ds = dataset(fields)
  state.datasets.splice(0, state.datasets.length, ds)
  state.kpiMetrics = buildKpiMetrics(fields, [])
  state.ui.metricConfig.visible = false
  const wrapper = mount(MetricConfigDialog, { global: { stubs } })
  state.ui.metricConfig.visible = true
  await flushPromises()
  return { wrapper, ds }
}

beforeEach(() => {
  state.datasets.splice(0, state.datasets.length)
  state.kpiMetrics = []
  state.ui.metricConfig.visible = false
})

describe('指标配置弹窗 · 双入口写同一份注册表', () => {
  it('改展示名写入字段注册表（与「字段名称」弹窗同源）', async () => {
    const { wrapper, ds } = await openWith([
      { name: 'plan_count', displayName: '关联计划数' },
      { name: 'sent_count', displayName: '下发策略数' },
    ])

    const nameInput = editableInputs(wrapper)[0]
    await nameInput.setValue('计划数')
    await flushPromises()

    expect(ds.fields[0].displayName).toBe('计划数')
    // 镜像同步：画布渲染 / 后端持久化读的是它
    expect(state.kpiMetrics[0].displayName).toBe('计划数')
  })

  it('改单位同样写入注册表（字段级展示配置）', async () => {
    const { wrapper, ds } = await openWith([{ name: 'plan_count', displayName: '关联计划数' }])

    // 可编辑输入顺序：展示名(0) / 单位(1) / 辅助说明(2)；「指标值」为只读列
    const editable = editableInputs(wrapper)
    await editable[1].setValue('个')
    await flushPromises()

    expect(ds.fields[0].unit).toBe('个')
    expect(state.kpiMetrics[0].unit).toBe('个')
  })

  it('展示名重复时拦截（决策 1）：注册表与镜像都不写入', async () => {
    const { wrapper, ds } = await openWith([
      { name: 'plan_count', displayName: '关联计划数' },
      { name: 'sent_count', displayName: '下发策略数' },
    ])

    const editable = editableInputs(wrapper)
    await editable[0].setValue('下发策略数') // 与第二个字段的展示名重名
    await flushPromises()

    expect(ds.fields[0].displayName).toBe('关联计划数')
    expect(state.kpiMetrics[0].displayName).toBe('关联计划数')
  })

  it('展示名清空即回退字段名', async () => {
    const { wrapper, ds } = await openWith([{ name: 'plan_count', displayName: '关联计划数' }])

    const editable = editableInputs(wrapper)
    await editable[0].setValue('')
    await flushPromises()

    expect(ds.fields[0].displayName).toBeUndefined()
    expect(state.kpiMetrics[0].displayName).toBe('plan_count')
  })
})
