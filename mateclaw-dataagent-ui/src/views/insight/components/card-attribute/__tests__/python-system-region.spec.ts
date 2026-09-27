import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { buildPipeline, buildPythonSystemRegion, useInsight } from '../useInsight'
import { buildComponentPatch, hydratePanel } from '../useCardAttributeBridge'
import { writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'

// 弹窗内的确认框在测试中直接通过
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn().mockResolvedValue(true) } }
})
const { ElMessage, ElMessageBox } = await import('element-plus')

const { state } = useInsight()

describe('Python 查询 Pipeline 筛选组件声明', () => {
  it('声明当前页面可用的筛选组件，不把已删除的旧绑定 ID 作为有效 ID', () => {
    state.filterCatalog = [{ id: 'live-filter', title: '指标日期', type: 'filter' }]
    state.filterBindings = [{ filterName: 'deleted-filter', scope: {}, fieldMap: [] }] as never

    expect(buildPipeline().boundFilterComponentIds).toEqual(['live-filter'])
  })
})

describe('buildPythonSystemRegion', () => {
  it('保存统一编辑器中的数据接入代码时将其作为当前执行脚本持久化', () => {
    state.pythonSystemState = {
      mode: 'generated', generatedCode: 'old generated input', generatedFingerprint: 'old', userCode: '', hasGeneratedUpdate: false,
    }

    useInsight().savePython('new editor input', 'result = table_zb')

    expect(state.pythonSystemState?.mode).toBe('managed')
    expect(state.pythonSystemState?.managedCode).toBe('new editor input')
    expect(buildPipeline().script).toContain('new editor input')
    expect(buildPipeline().script).toContain('result = table_zb')
  })

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

const { fetchDatasetSampleRows } = vi.hoisted(() => ({ fetchDatasetSampleRows: vi.fn() }))
vi.mock('../../dataset-sample-query', () => ({ fetchDatasetSampleRows }))

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
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue" data-testid="query-config-editor"><button data-testid="query-config-close" @click="$emit(\'update:modelValue\', false)">关闭</button></div>',
}

async function mountDialog() {
  const wrapper = mount(PythonScriptDialog, {
    global: { stubs: { 'el-dialog': elDialogStub, 'el-input': elInputStub, 'el-button': elButtonStub, 'el-tag': elTagStub, PythonQueryConfigDialog: pythonQueryConfigDialogStub } },
  })
  state.ui.python.visible = true
  await nextTick()
  await flushPromises()
  return wrapper
}

describe('PythonScriptDialog 系统区接管交互', () => {
  let warningSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    fetchDatasetSampleRows.mockReset().mockResolvedValue({ rows: [{ region: '华东', amount: 12 }], columns: ['region', 'amount'] })
    vi.mocked(ElMessageBox.confirm).mockClear()
    warningSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    state.ui.python.visible = false
    state.ui.python.queryConfigOnly = false
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

    await wrapper.find('[data-testid="python-editor-save"]').trigger('click')

    expect(warningSpy).toHaveBeenCalledWith('请先完成查询配置，再展开编辑 Python 脚本')
    expect(state.ui.python.visible).toBe(true)
    expect(wrapper.find('[data-testid="query-config-editor"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('从属性配置直接打开查询配置时不显示脚本编辑弹窗，关闭配置后结束流程', async () => {
    const wrapper = mount(PythonScriptDialog, {
      global: { stubs: { 'el-dialog': elDialogStub, 'el-input': elInputStub, 'el-button': elButtonStub, 'el-tag': elTagStub, PythonQueryConfigDialog: pythonQueryConfigDialogStub } },
    })

    useInsight().openPythonQueryConfig()
    await nextTick()
    await nextTick()

    expect(wrapper.find('.python-script-dialog').exists()).toBe(false)
    expect(wrapper.find('[data-testid="query-config-editor"]').exists()).toBe(true)
    await wrapper.find('[data-testid="query-config-close"]').trigger('click')
    await nextTick()
    expect(state.ui.python.visible).toBe(false)
    expect(state.ui.python.queryConfigOnly).toBe(false)
    wrapper.unmount()
  })

  it('单一编辑区包含造数、数据接入、自定义代码和输出样例，不再呈现分区编辑器', async () => {
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

    const editor = wrapper.get('[data-testid="python-editor-code"]')
    expect((editor.element as HTMLTextAreaElement).value).toContain('造数示例与上游数据读取')
    expect((editor.element as HTMLTextAreaElement).value).toContain('自定义处理代码（可编辑）')
    expect((editor.element as HTMLTextAreaElement).value).toContain('输出结果示例（参考）')
    expect((editor.element as HTMLTextAreaElement).value).toContain('华东')
    expect((editor.element as HTMLTextAreaElement).value).toContain('12')
    expect(fetchDatasetSampleRows).toHaveBeenCalledWith(state.datasets[0], state.filterCatalog)
    expect(wrapper.find('[data-testid="system-code-readonly"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="user-code"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="footer-query-config"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('编辑统一脚本后保存时拆分接入脚本和用户处理代码', async () => {
    state.finalResultQueryConfig = {
      confirmed: true, schemaFingerprint: 'schema-1', displayFields: [], filterFields: [],
      sortPolicy: { enabled: false, mode: 'single', allowedFields: [], defaultSort: null },
      paginationPolicy: { enabled: false, defaultPageSize: 100, maxPageSize: 500, returnTotalCount: false },
    } as never
    state.pythonSystemState = {
      mode: 'generated', generatedCode: 'GEN_CODE', generatedFingerprint: 'fp1',
      userCode: 'result = 1', hasGeneratedUpdate: false,
    }
    state.pythonSystem = 'GEN_CODE'
    const wrapper = await mountDialog()

    const editor = wrapper.get('[data-testid="python-editor-code"]')
    await editor.setValue((editor.element as HTMLTextAreaElement).value.replace(
      '# ===== 输出结果示例（参考） =====',
      'value = 1\n\n# ===== 输出结果示例（参考） =====',
    ))
    await nextTick()
    await wrapper.find('[data-testid="python-editor-save"]').trigger('click')

    expect(state.pythonSystemState?.mode).toBe('managed')
    expect(state.pythonSystem).toContain('datasets.input(')
    expect(state.pythonUser).toContain('value = 1')
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

describe('属性配置中的 Python 查看数据入口', () => {
  it('保存共享 Python 草稿并直接打开最终结果预览', () => {
    state.ui.python.visible = true
    state.pythonSystem = 'generated code'
    state.pythonUser = 'result = 42'
    state.hasPython = true

    useInsight().openPythonResultPreview()

    expect(state.ui.python.visible).toBe(false)
    expect(state.ui.preview).toMatchObject({ visible: true, kind: 'result' })
    expect(state.pythonSystemState?.userCode).toBe('result = 42')
  })
})
