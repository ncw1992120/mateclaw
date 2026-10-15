import { describe, expect, it } from 'vitest'
import {
  composeExecutionScript,
  buildPythonEditorDocument,
  refreshPythonEditorSamples,
  effectiveSystemCode,
  fingerprintSystemSource,
  generateSystemScript,
  parsePythonEditorDocument,
  reconcileSystemScript,
  restoreGenerated,
} from '../python-script-template'

const source = {
  inputs: [{ datasetId: 'a', inputName: 'dataset_a' }],
  bindings: [],
  outputContract: { kind: 'table', example: '{"kind":"table"}', fieldRules: { minColumns: 2, minDimensionColumns: 1, minNumericColumns: 1 } },
}

describe('python script system state', () => {
  it('generates deterministic system code and fingerprint', () => {
    const first = generateSystemScript(source)
    const second = generateSystemScript({ ...source, bindings: [] })
    expect(first).toContain('input_name="dataset_a"')
    expect(first).toContain('最终输出契约：kind=table')
    expect(first).toContain('# {"kind":"table"}')
    expect(fingerprintSystemSource(source)).toBe(fingerprintSystemSource({ ...source, bindings: [] }))
    expect(second).toBe(first)
  })

  it('managed mode keeps user takeover and exposes a generated candidate', () => {
    const current = {
      mode: 'managed' as const,
      generatedCode: generateSystemScript(source),
      managedCode: 'dataset_a = custom_read()',
      generatedFingerprint: fingerprintSystemSource(source),
      userCode: 'result = dataset_a',
    }
    const next = reconcileSystemScript(current, {
      inputs: [...source.inputs, { datasetId: 'b', inputName: 'dataset_b' }],
      bindings: [],
    })
    expect(next.mode).toBe('managed')
    expect(next.managedCode).toContain('dataset_a = custom_read()')
    expect(next.managedCode).toContain('if "dataset_b" not in locals():')
    expect(next.generatedCode).toContain('dataset_b')
    expect(next.hasGeneratedUpdate).toBe(true)
    expect(effectiveSystemCode(next)).toContain('dataset_a = custom_read()')
    expect(effectiveSystemCode(next)).toContain('input_name="dataset_b"')
  })

  it('managed mode adds newly declared dataset readers to the effective execution script', () => {
    const current = {
      mode: 'managed' as const,
      generatedCode: generateSystemScript(source),
      managedCode: [
        '# ===== 系统生成区域：输入数据集和筛选绑定（请勿手动修改） =====',
        'if "table_wd" not in locals():',
        '    table_wd = datasets.input(input_name="table_wd").to_polars()',
        'if "table_zb" not in locals():',
        '    table_zb = datasets.input(input_name="table_zb").to_polars()',
        '# ===== 系统生成区域结束 =====',
      ].join('\n'),
      generatedFingerprint: 'old',
      userCode: 'result = table_cnt',
    }

    const next = reconcileSystemScript(current, {
      inputs: [
        { datasetId: 'wd', inputName: 'table_wd' },
        { datasetId: 'zb', inputName: 'table_zb' },
        { datasetId: 'cnt', inputName: 'table_cnt' },
      ],
      bindings: [],
    })

    expect(effectiveSystemCode(next)).toContain('if "table_cnt" not in locals():')
    expect(effectiveSystemCode(next)).toContain('table_cnt = datasets.input(')
    expect(effectiveSystemCode(next)).toContain('input_name="table_cnt"')
    expect(effectiveSystemCode(next)).toContain('table_wd = datasets.input(input_name="table_wd").to_polars()')
    expect(next.userCode).toBe('result = table_cnt')
  })

  it('restoreGenerated explicitly returns generated mode and preserves user code', () => {
    const current = {
      mode: 'managed' as const,
      generatedCode: 'generated',
      managedCode: 'custom',
      generatedFingerprint: 'old',
      userCode: 'result = custom',
    }
    const restored = restoreGenerated(current)
    expect(restored.mode).toBe('generated')
    expect(effectiveSystemCode(restored)).toBe('generated')
    expect(restored.userCode).toBe('result = custom')
  })

  it('composes system and user regions without losing user code', () => {
    expect(composeExecutionScript({
      mode: 'generated', generatedCode: 'dataset_a = 1', generatedFingerprint: 'x', userCode: 'result = dataset_a',
    })).toContain('# ===== 用户处理区域 =====\nresult = dataset_a')
  })

  it('builds the four editor sections in order and lets uncommented samples bypass live reads', () => {
    const document = buildPythonEditorDocument({
      inputs: [{
        datasetId: 'a',
        inputName: 'table_zb',
        fieldMappings: [{ source: 'region', target: '区域' }, { source: 'amount', target: '金额' }],
      }],
      bindings: [],
      queryParameters: [{ field: 'region', title: '区域', parameterName: 'region_param', operators: ['eq'] }],
      outputContract: { kind: 'table', example: '{"kind":"table","data":[]}', fieldRules: { minColumns: 2, minDimensionColumns: 1, minNumericColumns: 1 } },
    }, 'result = table_zb', { table_zb: [{ region: '华东', amount: 12 }] })

    const sampleStart = document.indexOf('造数示例')
    const sourceStart = document.indexOf('datasets.input(')
    const userStart = document.indexOf('# ===== 自定义处理代码（可编辑） =====')
    const outputStart = document.indexOf('# ===== 输出结果示例（参考） =====')
    expect(sampleStart).toBeGreaterThanOrEqual(0)
    expect(sampleStart).toBeLessThan(sourceStart)
    expect(sourceStart).toBeLessThan(userStart)
    expect(userStart).toBeLessThan(outputStart)
    expect(document).toContain('# table_zb = pl.DataFrame(json.loads(')
    expect(document).toContain('华东')
    expect(document).toContain('if "table_zb" not in locals():')
    expect(document).toContain('# 参数 region_param：筛选字段「区域」(region)，可用操作符 eq')
    expect(document).toContain('不会作为 Python 变量注入')
    expect(document).toContain('# {"kind":"table","data":[]}')
    expect(parsePythonEditorDocument(document)).toMatchObject({ userCode: 'result = table_zb' })
  })

  it('builds the Python editor without generated data-sample comments when examples are shown separately', () => {
    const document = buildPythonEditorDocument({
      inputs: [{ datasetId: 'a', inputName: 'table_zb' }],
      bindings: [],
    }, 'result = table_zb', { table_zb: [{ amount: 12 }] }, false)

    expect(document).not.toContain('造数示例')
    expect(document).not.toContain('json.loads')
    expect(document).not.toContain('amount":12')
    expect(document).toContain('datasets.input(')
    expect(document).toContain('result = table_zb')
  })

  it('uses the saved editable dataset-read region instead of regenerating over user changes', () => {
    const savedSystem = [
      '# ===== 系统生成区域：上游输入数据读取 =====',
      'if "table_ab" not in locals():',
      '    table_ab = datasets.input(input_name="table_ab").to_pandas()',
      '# ===== 数据读取结束 =====',
    ].join('\n')
    const document = buildPythonEditorDocument(source, 'result = dataset_a', {}, false, savedSystem)

    expect(document).toContain('table_ab = datasets.input(input_name="table_ab").to_pandas()')
    expect(document).not.toContain('dataset_a = datasets.input(')
    expect(parsePythonEditorDocument(document).systemCode).toContain('.to_pandas()')
  })

  it('刷新注释示例时更新字段并移除已删除数据集的旧行，同时保留手写脚本', () => {
    const document = buildPythonEditorDocument({
      inputs: [
        { datasetId: 'a', inputName: 'table_ab' },
        { datasetId: 'b', inputName: 'table_wd' },
      ],
      bindings: [],
    }, 'result = custom_transform(table_ab)', {
      table_ab: [{ old_field: 1 }],
      table_wd: [{ legacy: 'old' }],
    })

    const refreshed = refreshPythonEditorSamples(document, [
      { datasetId: 'a', inputName: 'table_ab' },
    ], { table_ab: [{ new_field: 2 }, { new_field: 3 }] })

    expect(refreshed).toContain('new_field')
    expect(refreshed).not.toContain('old_field')
    expect(refreshed).not.toContain('table_wd = pl.DataFrame')
    expect(refreshed).toContain('result = custom_transform(table_ab)')
    expect(refreshed).toContain('if "table_ab" not in locals():')
    expect(refreshed).not.toContain('if "table_wd" not in locals():')
  })

  it('没有真实查询行时不再用字段映射伪造 schema 造数', () => {
    const document = buildPythonEditorDocument({
      inputs: [{
        datasetId: 'a', inputName: 'table_ab',
        fieldMappings: [{ source: 'region', target: '区域' }, { source: 'amount', target: '金额' }],
      }],
      bindings: [],
    })

    expect(document).not.toContain('pl.DataFrame({')
    expect(document).toContain('尚未获取真实查询结果行')
  })
})
