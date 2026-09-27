import { describe, expect, it } from 'vitest'
import {
  composeExecutionScript,
  buildPythonEditorDocument,
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
    expect(next.managedCode).toBe('dataset_a = custom_read()')
    expect(next.generatedCode).toContain('dataset_b')
    expect(next.hasGeneratedUpdate).toBe(true)
    expect(effectiveSystemCode(next)).toBe('dataset_a = custom_read()')
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
})
