import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { buildPythonSystemRegion, useInsight } from '../useInsight'
import { hydratePanel } from '../useCardAttributeBridge'
import { writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'

// 弹窗内的确认框在测试中直接通过
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn().mockResolvedValue(true) } }
})
const { ElMessageBox } = await import('element-plus')

const { state } = useInsight()

describe('buildPythonSystemRegion', () => {
  it('保存筛选器绑定后立即刷新系统生成区域的过滤条件', () => {
    const { saveFilterBindings } = useInsight()
    state.datasets = [{ id: 'ds1', alias: 'table2' }] as never
    state.filterBindings = []
    state.filterCatalog = [{ id: 'filter-metric-date', title: '指标日期' }]
    state.hasPython = true
    state.pythonUser = 'result = table2'
    state.pythonSystem = 'table2 = datasets.read(input_name="table2", filters=[]).to_polars()'
    state.pythonSystemState = {
      mode: 'generated',
      generatedCode: state.pythonSystem,
      generatedFingerprint: 'before-binding',
      userCode: state.pythonUser,
    }

    saveFilterBindings([{
      filterName: '指标日期',
      scope: { ds1: true },
      fieldMap: [{ datasetId: 'ds1', field: 'metric_time', matched: true }],
    }])

    expect(state.pythonSystem).toContain('_optional_filter("metric_time", "eq", "指标日期")')
    expect(state.pythonSystemState?.generatedCode).toContain('_optional_filter("metric_time", "eq", "指标日期")')
  })

  it('打开已绑定筛选器的组件时重算过期的系统生成区域', () => {
    const component = writeComponentDatasetPipeline(
      { id: 'nested-table', type: 'table', title: '子策略贡献表', position: { x: 0, y: 0, w: 6, h: 4 }, config: {} },
      {
        datasetInputs: [{
          datasetId: 'dataset-1',
          inputName: 'table2',
          displayName: 'table2',
          sourceType: 'JDBC_TABLE',
          fieldMappings: [{ source: 'metric_time', target: 'metric_time' }],
          filters: [],
        }],
        scriptFilterBindings: [{
          filterComponentId: '指标日期',
          inputNames: ['table2'],
          fieldMappings: { table2: 'metric_time' },
        }],
        parameters: [],
        executionPolicy: {},
        script: 'table2 = datasets.read(input_name="table2", filters=[]).to_polars()',
        systemScript: {
          mode: 'generated',
          generatedCode: 'table2 = datasets.read(input_name="table2", filters=[]).to_polars()',
          generatedFingerprint: 'stale',
          userCode: '',
        },
      },
    )

    hydratePanel(component as never)

    expect(state.pythonSystem).toContain('_optional_filter("metric_time", "eq", "指标日期")')
    expect(state.pythonSystemState?.generatedCode).toContain('_optional_filter("metric_time", "eq", "指标日期")')
  })

  it('无绑定筛选时生成 datasets.read 全量读取（可运行契约，不再生成 inputs[...]）', () => {
    state.datasets = [{ id: 'ds1', alias: 'table1' }] as never
    state.filterBindings = []
    const code = buildPythonSystemRegion()
    expect(code).toContain('table1 = datasets.read(')
    expect(code).toContain('input_name="table1"')
    expect(code).toContain('filters=[]')
    expect(code).toContain('.to_polars()')
    expect(code).not.toContain('inputs[')
  })

  it('绑定筛选器时生成真实下推条件（不再是注释）', () => {
    state.datasets = [
      { id: 'ds1', alias: 'table2' },
      { id: 'ds2', alias: 'table3' },
    ] as never
    state.filterBindings = [
      {
        filterName: '指标日期',
        scope: { ds1: true, ds2: true },
        fieldMap: [
          { datasetId: 'ds1', field: 'metric_time', matched: true },
          { datasetId: 'ds2', field: 'metric_time', matched: true },
        ],
      },
    ] as never
    const code = buildPythonSystemRegion()
    // 每个作用域数据集都有下推条件
    expect(code.match(/_cond\("metric_time", "指标日期"\)/g)).toHaveLength(2)
    expect(code).toContain('filters=_cond("metric_time", "指标日期")')
    // 运行时辅助：值经 datasets.params 注入，形状自适应
    expect(code).toContain('datasets.params.get(name)')
    expect(code).toContain('"between" if isinstance(v, dict) else "eq"')
    expect(code).toContain('"in" if isinstance(v, (list, tuple))')
    // 模糊查询：通配符值按 contains 下推（* 归一为 %），支持显式 operator 覆盖
    expect(code).toContain('"operator": "contains"')
    expect(code).toContain('v.replace("*", "%")')
    expect(code).toContain('operator="auto"')
    expect(code).not.toContain('未映射字段')
    expect(code).not.toContain('数据源查询阶段下推')
  })

  it('字段映射未命中时不生成下推条件（回落全量读取）', () => {
    state.datasets = [{ id: 'ds1', alias: 'table2' }] as never
    state.filterBindings = [
      {
        filterName: '指标日期',
        scope: { ds1: true },
        fieldMap: [{ datasetId: 'ds1', field: '', matched: false }],
      },
    ] as never
    const code = buildPythonSystemRegion()
    expect(code).toContain('filters=[]')
    expect(code).not.toContain('_cond(')
    // 未生成辅助函数定义（没有绑定条件时保持系统区域精简）
    expect(code).not.toContain('def _cond(')
  })

  it('有显式模板时按 operator 和 parameterNames 生成可选下推条件', () => {
    state.datasets = [{ id: 'ds1', alias: 'table2' }] as never
    state.filterBindings = [{
      filterName: '时间范围',
      scope: { ds1: true },
      fieldMap: [{ datasetId: 'ds1', field: 'metric_time', matched: true }],
      conditions: [
        { inputName: 'table2', field: 'metric_time', operator: 'gte', parameterNames: ['startDate'], required: false },
        { inputName: 'table2', field: 'metric_time', operator: 'lt', parameterNames: ['endDate'], required: false },
      ],
    }] as never
    const code = buildPythonSystemRegion()
    expect(code).toContain('_cond("metric_time", "startDate", "gte")')
    expect(code).toContain('_cond("metric_time", "endDate", "lt")')
    expect(code).not.toContain('_cond("metric_time", "时间范围")')
  })

  it('筛选器只作用于声明范围内数据集', () => {
    state.datasets = [
      { id: 'ds1', alias: 'table2' },
      { id: 'ds2', alias: 'table3' },
    ] as never
    state.filterBindings = [
      {
        filterName: '指标日期',
        scope: { ds1: true, ds2: false },
        fieldMap: [{ datasetId: 'ds1', field: 'metric_time', matched: true }],
      },
    ] as never
    const code = buildPythonSystemRegion()
    expect(code).toContain('filters=_cond("metric_time", "指标日期")')
    expect(code).toContain('input_name="table3"')
    // table3 不在作用域内 → 全量读取
    const table3Block = code.slice(code.indexOf('input_name="table3"'))
    expect(table3Block).toContain('filters=[]')
  })
})

/* ── PythonScriptDialog：解锁 / 接管 / 差异 / 恢复（点击真实按钮）───────── */

import PythonScriptDialog from '../PythonScriptDialog.vue'

const elInputStub = {
  name: 'el-input',
  props: ['modelValue', 'type', 'rows'],
  emits: ['update:modelValue'],
  template: `<textarea v-bind="$attrs" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`,
}
const elButtonStub = {
  name: 'el-button',
  props: ['size', 'type', 'plain', 'text', 'loading'],
  emits: ['click'],
  template: `<button v-bind="$attrs" @click="$emit('click')"><slot /></button>`,
}
const elTagStub = { name: 'el-tag', template: '<span v-bind="$attrs"><slot /></span>' }
const elDialogStub = {
  name: 'el-dialog',
  props: ['modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}

async function mountDialog() {
  const wrapper = mount(PythonScriptDialog, {
    global: { stubs: { 'el-dialog': elDialogStub, 'el-input': elInputStub, 'el-button': elButtonStub, 'el-tag': elTagStub } },
  })
  state.ui.python.visible = true
  await nextTick()
  return wrapper
}

describe('PythonScriptDialog 系统区接管交互', () => {
  beforeEach(() => {
    vi.mocked(ElMessageBox.confirm).mockClear()
    state.ui.python.visible = false
    state.datasets = [{ id: 'ds1', alias: 'dataset_a' }] as never
    state.filterBindings = []
    state.pythonUser = 'result = 1'
  })

  it('generated 模式：只读展示 + 解锁进入用户接管（用户区不受影响）', async () => {
    state.pythonSystemState = {
      mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1',
      userCode: 'result = 1', hasGeneratedUpdate: false,
    }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    expect(wrapper.find('[data-testid="system-code-readonly"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="system-mode-tag"]').text()).toContain('系统生成')

    await wrapper.find('[data-testid="unlock-btn"]').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="system-mode-tag"]').text()).toContain('用户接管')
    const managed = wrapper.find('[data-testid="system-code-managed"]')
    expect(managed.exists()).toBe(true)
    expect((managed.element as HTMLTextAreaElement).value).toBe('GEN_CODE')
    // 用户处理区域保持独立
    expect((wrapper.find('[data-testid="user-code"]').element as HTMLTextAreaElement).value).toBe('result = 1')
    wrapper.unmount()
  })

  it('用户处理区域显示 Python 关键字高亮，同时保留可编辑文本框', async () => {
    state.pythonSystemState = {
      mode: 'generated', generatedCode: 'table1 = datasets.read()', generatedFingerprint: 'fp1',
      userCode: 'def normalize(value):\n    return len(value)', hasGeneratedUpdate: false,
    }
    state.pythonSystem = 'table1 = datasets.read()'
    state.pythonUser = state.pythonSystemState.userCode
    const wrapper = await mountDialog()

    expect(wrapper.find('[data-testid="python-editor"] .hljs-keyword').text()).toBe('def')
    expect(wrapper.find('[data-testid="user-code"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('managed 模式：配置变化产生候选提示，保留当前版本不覆盖用户代码', async () => {
    state.pythonSystemState = {
      mode: 'managed', generatedCode: 'OLD', managedCode: 'dataset_a = custom_read()',
      generatedFingerprint: 'old-fp', userCode: 'result = 1', hasGeneratedUpdate: false,
    }
    state.pythonSystem = 'dataset_a = custom_read()'
    const wrapper = await mountDialog()

    // 数据集变化后 reconcile 产生候选版本（openPython 内部走 reconcileSystemScript）
    state.datasets = [{ id: 'ds1', alias: 'dataset_a' }, { id: 'ds2', alias: 'dataset_b' }] as never
    useInsight().openPython()
    await nextTick()

    expect(wrapper.find('[data-testid="diff-toggle"]').exists()).toBe(true)
    await wrapper.find('[data-testid="diff-toggle"]').trigger('click')
    const diff = wrapper.find('[data-testid="system-diff"]')
    expect(diff.exists()).toBe(true)
    expect(diff.text()).toContain('dataset_a = custom_read()')
    expect(diff.text()).toContain('dataset_b')

    await wrapper.find('[data-testid="keep-current"]').trigger('click')
    await nextTick()
    expect(wrapper.find('[data-testid="diff-toggle"]').exists()).toBe(false)
    // 用户接管代码未被覆盖
    expect(state.pythonSystemState?.managedCode).toBe('dataset_a = custom_read()')
    expect(wrapper.find('[data-testid="system-mode-tag"]').text()).toContain('用户接管')
    wrapper.unmount()
  })

  it('恢复系统生成：mode 回到 generated，用户处理区域保留', async () => {
    state.pythonSystemState = {
      mode: 'managed', generatedCode: 'GEN_NEW', managedCode: 'custom()',
      generatedFingerprint: 'fp', userCode: 'result = 2', hasGeneratedUpdate: true,
    }
    state.pythonSystem = 'custom()'
    state.pythonUser = 'result = 2'
    const wrapper = await mountDialog()

    await wrapper.find('[data-testid="restore-generated"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="system-mode-tag"]').text()).toContain('系统生成')
    expect(wrapper.find('[data-testid="system-code-readonly"]').text()).toContain('GEN_NEW')
    expect((wrapper.find('[data-testid="user-code"]').element as HTMLTextAreaElement).value).toBe('result = 2')
    wrapper.unmount()
  })

  it('卡片切换不串状态：A managed / B generated 互不影响', () => {
    const userMarker = '# ===== 用户处理区域 ====='
    const compA = writeComponentDatasetPipeline(
      { id: 'card-a', type: 'table', title: 'A', position: { x: 0, y: 0, w: 6, h: 4 }, config: {} },
      {
        datasetInputs: [], scriptFilterBindings: [], parameters: [], executionPolicy: {},
        script: `managed-a\n\n${userMarker}\nresult = 1`,
        systemScript: { mode: 'managed', generatedCode: 'gen-a', managedCode: 'managed-a', generatedFingerprint: 'fa', userCode: 'result = 1' },
      },
    )
    const compB = writeComponentDatasetPipeline(
      { id: 'card-b', type: 'table', title: 'B', position: { x: 0, y: 0, w: 6, h: 4 }, config: {} },
      {
        datasetInputs: [], scriptFilterBindings: [], parameters: [], executionPolicy: {},
        script: `gen-b\n\n${userMarker}\nresult = 2`,
        systemScript: { mode: 'generated', generatedCode: 'gen-b', generatedFingerprint: 'fb', userCode: 'result = 2' },
      },
    )
    hydratePanel(compA as never)
    expect(state.pythonSystemState?.mode).toBe('managed')
    expect(state.pythonSystem).toBe('managed-a')
    hydratePanel(compB as never)
    expect(state.pythonSystemState?.mode).toBe('generated')
    expect(state.pythonSystem).toBe('gen-b')
    expect(state.pythonSystem).not.toContain('managed-a')
    hydratePanel(compA as never)
    expect(state.pythonSystemState?.mode).toBe('managed')
    expect(state.pythonSystem).toBe('managed-a')
  })
})
