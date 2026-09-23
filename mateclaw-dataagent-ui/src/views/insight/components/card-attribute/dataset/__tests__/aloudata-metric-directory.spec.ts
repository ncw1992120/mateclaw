import { describe, expect, it } from 'vitest'
import { buildCategoryTree, filterMetricCategoryTree } from '../aloudata-metric-directory'

const tree = [
  {
    categoryId: 'root',
    categoryName: '策略解读',
    metricList: [{ metricName: 'conversion_count', metricDisplayName: '转化次数' }],
    subCategory: [
      {
        categoryId: 'child',
        categoryName: '子策略',
        metricList: [{ metricName: 'strategy_cost', metricDisplayName: '策略成本' }],
        subCategory: [],
      },
    ],
  },
  {
    categoryId: 'other',
    categoryName: '其他',
    metricList: [{ metricName: 'visit_count', metricDisplayName: '访问次数' }],
    subCategory: [],
  },
]

describe('Aloudata metric directory filtering', () => {
  it('matches metric display names and keeps their ancestor categories', () => {
    const result = filterMetricCategoryTree(tree, '策略成本')

    expect(result).toHaveLength(1)
    expect(result[0].categoryId).toBe('root')
    expect(result[0].subCategory?.[0].metricList?.[0].metricName).toBe('strategy_cost')
  })

  it('matches the metric field identifier', () => {
    const result = filterMetricCategoryTree(tree, 'conversion_count')

    expect(result.flatMap((category) => category.metricList || []).map((metric) => metric.metricName))
      .toEqual(['conversion_count'])
  })

  it('returns the complete directory when the keyword is empty', () => {
    expect(filterMetricCategoryTree(tree, ' ')).toEqual(tree)
  })

  it('builds nested dimension categories from Aloudata parent identifiers', () => {
    const result = buildCategoryTree([
      { categoryId: 'child', categoryName: '子策略', parentId: 'root' },
      { categoryId: 'root', categoryName: '策略解读', parentId: null },
    ])

    expect(result).toHaveLength(1)
    expect(result[0].children[0].categoryId).toBe('child')
  })
})
