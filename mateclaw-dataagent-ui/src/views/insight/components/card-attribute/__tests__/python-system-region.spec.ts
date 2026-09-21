import { describe, expect, it } from 'vitest'
import { buildPythonSystemRegion, useInsight } from '../useInsight'

const { state } = useInsight()

describe('buildPythonSystemRegion', () => {
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
