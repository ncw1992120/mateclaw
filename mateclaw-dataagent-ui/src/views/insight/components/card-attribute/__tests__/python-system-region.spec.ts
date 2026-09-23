import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { buildPythonSystemRegion, useInsight } from '../useInsight'
import { buildComponentPatch, hydratePanel } from '../useCardAttributeBridge'
import { writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'

// 弹窗内的确认框在测试中直接通过
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn().mockResolvedValue(true) } }
})
const { ElMessage, ElMessageBox } = await import('element-plus')

const { state } = useInsight()

describe('buildPythonSystemRegion', () => {
  it('保留 Aloudata 维度筛选器配置的技术字段名，供查询配置自动匹配', () => {
    const component = {
      id: 'field-filter-card',
      type: 'kpi',
      title: '指标卡',
      position: { x: 0, y: 0, w: 6, h: 4 },
    }

    hydratePanel(component as never, '', [{
      id: 'strategy-filter',
      type: 'filter',
      title: '策略类型',
      field: 'strategy_id',
      selectionMode: 'single',
    } as never])

    expect(state.filterCatalog[0]).toMatchObject({ id: 'strategy-filter', field: 'strategy_id' })
  })

  it('round-trips a component accent group through card attribute-panel state', () => {
    const component = {
      id: 'accent-card',
      type: 'kpi',
      title: '指标卡',
      position: { x: 0, y: 0, w: 6, h: 4 },
      themeAccentGroup: 'highlight',
    }

    hydratePanel(component as never)
    const updated = buildComponentPatch(component as never)

    expect(state.cards[0]?.themeAccentGroup).toBe('highlight')
    expect(updated.themeAccentGroup).toBe('highlight')
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

    expect(state.pythonSystem).toContain('table2 = datasets.input(')
    expect(state.pythonSystemState?.generatedCode).toContain('datasets.input(')
    expect(state.pythonSystem).not.toContain('datasets.read(')
  })

  it('无绑定筛选时生成 datasets.input 标准读取（不生成 read/inputs[...]）', () => {
    state.datasets = [{ id: 'ds1', alias: 'table1' }] as never
    state.filterBindings = []
    const code = buildPythonSystemRegion()
    expect(code).toContain('table1 = datasets.input(')
    expect(code).toContain('input_name="table1"')
    expect(code).toContain('.to_polars()')
    expect(code).not.toContain('datasets.read(')
    expect(code).not.toContain('filters=')
    expect(code).not.toContain('inputs[')
  })

  it('绑定筛选器不再向系统区注入下推条件（条件由 queryConfig/Planner 处理）', () => {
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
    // 新契约：系统区逐输入标准读取，无 params/条件拼接
    expect(code.match(/datasets\.input\(/g)).toHaveLength(2)
    expect(code).not.toContain('_cond(')
    expect(code).not.toContain('datasets.params')
    expect(code).not.toContain('filters=')
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
    expect(code).toContain('table2 = datasets.input(')
    expect(code).not.toContain('_cond(')
    expect(code).not.toContain('def _cond(')
    expect(code).not.toContain('filters=')
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
    expect(code).not.toContain('_cond(')
    expect(code).toContain('table2 = datasets.input(')
    expect(code).toContain(').to_polars()')
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
    expect(code).toContain('input_name="table2"')
    expect(code).toContain('input_name="table3"')
    // 作用域不再影响系统区：两个输入都是统一标准读取
    expect(code).not.toContain('filters=')
    expect(code).not.toContain('_cond(')
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
  template: '<div v-bind="$attrs"><slot /><slot name="footer" /></div>',
}
const pythonQueryConfigDialogStub = {
  props: ['modelValue'],
  template: '<div v-if="modelValue" data-testid="query-config-editor" />',
}

async function mountDialog() {
  const wrapper = mount(PythonScriptDialog, {
    global: { stubs: { 'el-dialog': elDialogStub, 'el-input': elInputStub, 'el-button': elButtonStub, 'el-tag': elTagStub, PythonQueryConfigDialog: pythonQueryConfigDialogStub } },
  })
  state.ui.python.visible = true
  await nextTick()
  return wrapper
}

describe('PythonScriptDialog 系统区接管交互', () => {
  let warningSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.mocked(ElMessageBox.confirm).mockClear()
    warningSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    state.ui.python.visible = false
    state.ui.preview.visible = false
    state.finalResultQueryConfig = undefined
    state.datasets = [{ id: 'ds1', alias: 'dataset_a' }] as never
    state.filterBindings = []
    state.pythonUser = 'result = 1'
  })

  it('未确认查询配置时保存脚本会弹出提示且不关闭编辑弹窗', async () => {
    state.finalResultQueryConfig = {
      confirmed: false,
      schemaFingerprint: 'schema-1',
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    } as never
    state.pythonSystemState = { mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1', userCode: 'result = 1', hasGeneratedUpdate: false }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    await wrapper.findAll('button').find((button) => button.text() === '确定')!.trigger('click')

    expect(warningSpy).toHaveBeenCalledWith('请先编辑并确认查询配置，再保存 Python 脚本')
    expect(state.ui.python.visible).toBe(true)
    wrapper.unmount()
  })

  it('未确认查询配置时查看数据会弹出提示且不打开结果预览', async () => {
    state.finalResultQueryConfig = {
      confirmed: false,
      schemaFingerprint: 'schema-1',
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    } as never
    state.pythonSystemState = { mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1', userCode: 'result = 1', hasGeneratedUpdate: false }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    await wrapper.find('[data-testid="view-python-result"]').trigger('click')

    expect(warningSpy).toHaveBeenCalledWith('请先编辑并确认查询配置，再查看 Python 最终结果数据')
    expect(state.ui.preview.visible).toBe(false)
    wrapper.unmount()
  })

  it('没有最终结果 Schema 时点击查询配置不再弹锁定提示，并打开空配置编辑器', async () => {
    state.pythonSystemState = { mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1', userCode: 'result = 1', hasGeneratedUpdate: false }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    await wrapper.find('[data-testid="footer-query-config"]').trigger('click')

    expect(warningSpy).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="query-config-editor"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('底部操作顺序为查询配置、查看数据、执行记录、确定且不显示取消', async () => {
    state.finalResultQueryConfig = {
      confirmed: true,
      schemaFingerprint: 'schema-1',
      displayFields: [{ field: 'region', title: '区域', role: 'dimension' }],
      filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    } as never
    state.pythonSystemState = { mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1', userCode: 'result = 1', hasGeneratedUpdate: false }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    const buttons = wrapper.findAll('button').map((button) => button.text()).slice(-4)
    expect(buttons).toEqual(['查询配置', '查看数据', '执行记录', '确定'])
    expect(buttons).not.toContain('取消')
    wrapper.unmount()
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
