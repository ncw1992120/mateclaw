import { describe, expect, it } from 'vitest'
import { createPythonResultFilterRows, enabledPythonResultConditions } from './python-result-filter-conditions'
import { mergeSelectedFilterOptions } from './view-data-filter-options'

describe('Python 结果筛选条件', () => {
  it('根据绑定筛选器推断普通筛选操作符，忽略配置里的通用操作符列表', () => {
    const rows = createPythonResultFilterRows([
      { field: 'region', title: '地区', dataType: 'string', parameterName: 'region', operators: ['contains'], filterComponentId: 'region-filter' },
      { field: 'channel', title: '渠道', dataType: 'string', parameterName: 'channel', operators: ['eq'], filterComponentId: 'channel-filter' },
    ], [
      { id: 'region-filter', title: '地区筛选', type: 'filter', selectionMode: 'multiple' },
      { id: 'channel-filter', title: '渠道筛选', type: 'filter', selectionMode: 'single' },
    ])

    expect(rows.map(({ field, op }) => ({ field, op }))).toEqual([
      { field: 'region', op: 'in' },
      { field: 'channel', op: '=' },
    ])
  })

  it('初始化普通筛选条件时填入绑定筛选器的默认值并默认启用', () => {
    const rows = createPythonResultFilterRows([
      { field: 'region', title: '地区', dataType: 'string', parameterName: 'region', operators: ['eq'], filterComponentId: 'region-filter' },
    ], [{ id: 'region-filter', title: '地区筛选', type: 'filter', selectionMode: 'multiple', defaultValue: ['华东', '华南'] }])

    expect(rows[0]).toMatchObject({ field: 'region', op: 'in', value: ['华东', '华南'], enabled: true })
  })

  it('把时间筛选展开为包含开始、不包含结束的两个固定条件', () => {
    const rows = createPythonResultFilterRows([
      { field: 'event_date', title: '发生时间', dataType: 'date', parameterName: 'event_date', operators: ['between'], filterComponentId: 'date-filter' },
    ], [{ id: 'date-filter', title: '时间范围', type: 'timeFilter' }])

    expect(rows.map(({ field, op, timeBoundary }) => ({ field, op, timeBoundary }))).toEqual([
      { field: 'event_date', op: '>=', timeBoundary: 'start' },
      { field: 'event_date', op: '<', timeBoundary: 'end' },
    ])
    expect(rows[0].filterTitle).toBe('时间范围 · 开始时间')
    expect(rows[1].filterTitle).toBe('时间范围 · 结束时间')
  })

  it('把时间筛选器默认时间范围填入开始和结束日期条件并启用', () => {
    const rows = createPythonResultFilterRows([
      { field: 'event_date', title: '发生时间', dataType: 'date', parameterName: 'event_date', operators: ['between'], filterComponentId: 'date-filter' },
    ], [{ id: 'date-filter', title: '时间范围', type: 'timeFilter', defaultPreset: 'today' }])

    expect(rows.map(({ value, enabled }) => ({ value, enabled }))).toEqual([
      { value: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), enabled: true },
      { value: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), enabled: true },
    ])
    expect(rows[0].value).toBe(rows[1].value)
  })

  it('忽略未绑定的历史自动生成字段，筛选器绑定失效时仍明确标记错误', () => {
    const rows = createPythonResultFilterRows([
      { field: 'status', title: '状态', dataType: 'string', parameterName: 'status', operators: ['eq'], filterComponentId: 'deleted-filter' },
      { field: 'owner', title: '负责人', dataType: 'string', parameterName: 'owner', operators: ['eq'] },
    ], [])

    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({ field: 'status', bindingError: true, enabled: false })
    expect(enabledPythonResultConditions(rows)).toEqual([])
  })

  it('只提交启用且已填写的筛选条件', () => {
    const rows = createPythonResultFilterRows([
      { field: 'region', title: '地区', dataType: 'string', parameterName: 'region', operators: ['eq'], filterComponentId: 'region-filter' },
      { field: 'channel', title: '渠道', dataType: 'string', parameterName: 'channel', operators: ['eq'], filterComponentId: 'channel-filter' },
    ], [
      { id: 'region-filter', title: '地区', type: 'filter' },
      { id: 'channel-filter', title: '渠道', type: 'filter' },
    ])
    rows[0].enabled = true
    rows[0].value = '华东'
    rows[1].enabled = true

    expect(enabledPythonResultConditions(rows)).toEqual([
      { field: 'region', op: '=', value: '华东' },
    ])
  })
})

describe('查看数据筛选选项', () => {
  it('保留已选默认值，即使动态选项接口当前页没有返回该值', () => {
    expect(mergeSelectedFilterOptions(
      [{ label: '华南', value: 'south' }],
      ['east', 'south'],
    )).toEqual([
      { label: '华南', value: 'south' },
      { label: 'east', value: 'east' },
    ])
  })
})
