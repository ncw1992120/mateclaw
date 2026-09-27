import { describe, expect, it } from 'vitest'
import { toFixedDatasetFilters } from '../fixed-dataset-filters'

describe('固定筛选条件序列化', () => {
  it('将操作符和值归一为数据集查询接口格式', () => {
    expect(toFixedDatasetFilters([
      { field: 'region', op: '=', value: '华东' },
      { field: 'status', op: 'in', value: '启用, 停用' },
      { field: 'amount', op: 'between', value: '10, 20' },
      { field: 'deleted_at', op: 'is null', value: '' },
    ], [
      { name: 'region', role: 'dimension' },
      { name: 'status', role: 'dimension' },
      { name: 'amount', role: 'measure' },
      { name: 'deleted_at', role: 'dimension' },
    ])).toEqual([
      { field: 'region', role: 'dimension', operator: 'eq', value: '华东' },
      { field: 'status', role: 'dimension', operator: 'in', value: ['启用', '停用'] },
      { field: 'amount', role: 'measure', operator: 'between', value: ['10', '20'] },
      { field: 'deleted_at', role: 'dimension', operator: 'is_null' },
    ])
  })
})
