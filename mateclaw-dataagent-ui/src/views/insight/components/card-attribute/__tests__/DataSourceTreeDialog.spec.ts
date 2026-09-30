import { computed, defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DataSourceTreeDialog from '../DataSourceTreeDialog.vue'

const fixture = vi.hoisted(() => ({
  state: { ui: { treeVisible: true } },
  onSelectLeaf: vi.fn(),
  datasets: [
    { id: 'api-dataset-1', name: '已配置接口', sourceType: 'api' },
    { id: 'jdbc-dataset-1', name: '已配置 JDBC', sourceType: 'jdbc' },
    { id: 'preview-dashboard-1_sales_hash', name: 'preview_dashboard-1_sales_hash', description: '看板预览自动确认的数据集：销售组件', sourceType: 'aloudata' },
    { id: 'preview-business-dataset', name: 'preview_业务数据集', description: '用户创建的真实数据集', sourceType: 'aloudata' },
  ],
  datasources: [
    { id: 'api-source-1', name: '接口连接', sourceType: 'api' },
    { id: 'jdbc-source-1', name: 'JDBC 连接', sourceType: 'jdbc' },
    { id: 'aloudata-1', name: '营销数据源', sourceType: 'aloudata' },
    { id: 'aloudata-2', name: '经营数据源', sourceType: 'aloudata' },
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
      leaves: computed(() => {
        const flatten = (nodes: any[]): any[] => nodes.flatMap((node) => [node, ...flatten(node.children ?? [])])
        return flatten(props.data).filter((node) => !node.children?.length)
      }),
    }
  },
  template: '<div class="tree-stub"><div v-for="node in leaves" :key="node.id"><slot :data="node" /></div></div>',
})
const DialogStub = defineComponent({
  name: 'ElDialog',
  template: '<div><slot /></div>',
})

afterEach(() => vi.clearAllMocks())

describe('添加数据集数据源树', () => {
  it('不在可复用数据集列表中展示仪表盘预览自动生成的数据集', async () => {
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

    const treeData = wrapper.findComponent(TreeStub).props('data') as Array<Record<string, any>>
    const flatten = (nodes: any[]): any[] => nodes.flatMap((node) => [node, ...flatten(node.children ?? [])])
    expect(flatten(treeData).some((node) => node.label === 'preview_dashboard-1_sales_hash')).toBe(false)
    expect(flatten(treeData).some((node) => node.label === 'preview_业务数据集')).toBe(true)
    wrapper.unmount()
  })

  it('将接口快捷项和已配置接口直接放在根层，其他类型仍按类别分组', async () => {
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

    const treeData = wrapper.findComponent(TreeStub).props('data') as Array<Record<string, any>>
    const apiNodes = treeData.filter(node => node.type === 'api')
    expect(apiNodes.map(node => node.id)).toEqual(['dataset-api-dataset-1', 'datasource-api-source-1', 'http-api'])
    expect(treeData.filter(node => node.type === 'category').map(node => node.id)).toEqual([
      'cat-aloudata', 'cat-jdbc', 'cat-file',
    ])
    expect(treeData.find(node => node.id === 'cat-jdbc')?.children.map((node: any) => node.id)).toEqual([
      'dataset-jdbc-dataset-1', 'datasource-jdbc-source-1',
    ])

    wrapper.findComponent(TreeStub).vm.$emit('node-click', apiNodes[0], {})
    expect(fixture.onSelectLeaf).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('仅允许选择 Aloudata 指标&维度，其他数据集入口置灰并拦截选择', async () => {
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
    const treeData = tree.props('data') as Array<Record<string, any>>
    const aloudata = treeData.find((node) => node.id === 'cat-aloudata')
    const connectionGroups = aloudata.children.filter((node: any) => node.type === 'category')
    const leaves = treeData.flatMap((node) => node.children ?? [node])
      .flatMap((node: any) => node.type === 'category' ? node.children ?? [node] : [node])
    const available = leaves.filter((node: any) => node.mode === 'metric-dim')
    const unavailable = leaves.filter((node: any) => node.mode !== 'metric-dim')

    expect(connectionGroups.map((node: any) => node.label)).toEqual(['营销数据源', '经营数据源'])
    expect(connectionGroups.map((node: any) => node.children.map((child: any) => child.db))).toEqual([
      ['aloudata-1', 'aloudata-1'], ['aloudata-2', 'aloudata-2'],
    ])

    expect(tree.props('props').disabled).toBe('disabled')
    expect(available.map((node: any) => [node.db, node.disabled])).toEqual([
      ['aloudata-1', false], ['aloudata-2', false],
    ])
    expect(unavailable.length).toBeGreaterThan(0)
    expect(unavailable.every((node) => node.disabled)).toBe(true)
    expect(wrapper.find('.tree-node--disabled').attributes('title')).toBe('开发中，还未上线，敬请期待')
    expect(wrapper.findAll('.tree-node--disabled')).toHaveLength(unavailable.length)

    tree.vm.$emit('node-click', unavailable.find((node) => node.id === 'http-api'), {})
    expect(fixture.onSelectLeaf).not.toHaveBeenCalled()

    tree.vm.$emit('node-click', available[1], {})
    expect(fixture.onSelectLeaf).toHaveBeenCalledWith(available[1])
    wrapper.unmount()
  })
})
