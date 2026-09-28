import { computed, defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DataSourceTreeDialog from '../DataSourceTreeDialog.vue'

const fixture = vi.hoisted(() => ({
  state: { ui: { treeVisible: true } },
  onSelectLeaf: vi.fn(),
  datasets: [
    { id: 'dataset-aloudata', name: '已配置 Aloudata 数据集', sourceType: 'aloudata' },
    { id: 'dataset-jdbc', name: '已配置 JDBC 数据集', sourceType: 'jdbc' },
    { id: 'dataset-api', name: '已配置接口数据集', sourceType: 'api' },
  ],
  datasources: [
    { id: 'aloudata-1', name: 'Aloudata', sourceType: 'aloudata' },
    { id: 'jdbc-1', name: 'JDBC', sourceType: 'jdbc' },
  ],
}))

vi.mock('../useInsight', () => ({
  useInsight: () => ({ state: fixture.state, onSelectLeaf: fixture.onSelectLeaf }),
}))
vi.mock('@/api/dataset', () => ({ list: vi.fn(async () => fixture.datasets) }))
vi.mock('@/api/datasource', () => ({ list: vi.fn(async () => fixture.datasources) }))

const TreeStub = defineComponent({
  name: 'ElTree',
  props: {
    data: { type: Array, default: () => [] },
    props: { type: Object, default: () => ({}) },
  },
  emits: ['node-click'],
  setup(props) {
    return {
      leaves: computed(() => props.data.flatMap((node: any) => node.children ?? [node])),
    }
  },
  template: '<div><div v-for="node in leaves" :key="node.id"><slot :data="node" /></div></div>',
})
const DialogStub = defineComponent({
  name: 'ElDialog',
  template: '<div><slot /></div>',
})

afterEach(() => vi.clearAllMocks())

describe('添加数据集发布范围', () => {
  it('只允许 Aloudata 指标&维度，其他数据集入口置灰、提示并阻止选择', async () => {
    const wrapper = mount(DataSourceTreeDialog, {
      global: {
        stubs: {
          'el-dialog': DialogStub,
          'el-input': true,
          'el-icon': true,
          Search: true,
          Folder: true,
          Coin: true,
        },
        components: { 'el-tree': TreeStub },
      },
    })
    await flushPromises()
    await nextTick()

    const tree = wrapper.findComponent(TreeStub)
    const nodes = (tree.props('data') as Array<Record<string, any>>)
      .flatMap((node) => node.children ?? [node])
    const available = nodes.find((node) => node.id === 'aloudata-metrics')
    const unavailable = nodes.filter((node) => node.id !== 'aloudata-metrics')

    expect(tree.props('props').disabled).toBe('disabled')
    expect(available.disabled).toBe(false)
    expect(unavailable.length).toBeGreaterThan(0)
    expect(unavailable.every((node) => node.disabled)).toBe(true)
    expect(wrapper.findAll('.tree-node--disabled')).toHaveLength(unavailable.length)
    expect(wrapper.find('.tree-node--disabled').attributes('title')).toBe('开发中，还未上线，敬请期待')

    tree.vm.$emit('node-click', unavailable.find((node) => node.id === 'datasource-jdbc-1'), {})
    expect(fixture.onSelectLeaf).not.toHaveBeenCalled()

    tree.vm.$emit('node-click', available, {})
    expect(fixture.onSelectLeaf).toHaveBeenCalledWith(available)
    wrapper.unmount()
  })
})
