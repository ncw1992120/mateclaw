import { describe, expect, it } from 'vitest'
import { classifyDatasourceType, datasetCategoryLabel, groupDatasets, groupDatasources } from '../data-binding'

describe('data binding source helpers', () => {
  it('classifies concrete JDBC dialects as jdbc', () => {
    expect(classifyDatasourceType('mysql')).toBe('jdbc')
    expect(classifyDatasourceType('postgresql')).toBe('jdbc')
    expect(classifyDatasourceType('JDBC')).toBe('jdbc')
  })

  it('keeps Aloudata, API and file categories distinct', () => {
    expect(classifyDatasourceType('aloudata')).toBe('aloudata')
    expect(classifyDatasourceType('http_api')).toBe('api')
    expect(classifyDatasourceType('file')).toBe('file')
  })

  it('groups datasets by source category while preserving order', () => {
    const groups = groupDatasets([
      { id: '1', name: 'Orders', sourceType: 'mysql' },
      { id: '2', name: 'Sales view', sourceType: 'ALOUDATA_ANALYSIS_VIEW' },
      { id: '3', name: 'Orders API', sourceType: 'HTTP_API' },
    ] as any)

    expect(groups.map(group => group.category)).toEqual(['aloudata', 'jdbc', 'api'])
    expect(groups.find(group => group.category === 'jdbc')?.datasets.map(dataset => dataset.id)).toEqual(['1'])
    expect(datasetCategoryLabel('file')).toBe('文件')
  })

  it('groups datasource connections with the same category rules', () => {
    const groups = groupDatasources([
      { id: 'a', name: '指标平台', sourceType: 'aloudata' },
      { id: 'j', name: '业务 MySQL', sourceType: 'mysql' },
    ] as any)
    expect(groups.map(group => group.label)).toEqual(['Aloudata', 'JDBC'])
    expect(groups[1].items[0].name).toBe('业务 MySQL')
  })
})
