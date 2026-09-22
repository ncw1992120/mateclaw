import { describe, expect, it } from 'vitest'
import { patchDashboardSchema } from '@/utils/insight-schema-patch'

describe('patchDashboardSchema', () => {
  it('只替换目标组件管道并保留其他页面和组件', () => {
    const original = {
      version: '1.1',
      pages: [
        {
          id: 'page-a',
          name: '页面 A',
          order: 0,
          components: [
            { id: 'card-1', type: 'kpi', title: '原卡片', position: { x: 2, y: 3, w: 4, h: 5 }, config: { color: 'red' } },
            { id: 'chart-1', type: 'chart', title: '图表', position: { x: 6, y: 0, w: 6, h: 5 } },
          ],
        },
        { id: 'page-b', name: '页面 B', order: 1, components: [{ id: 'table-1', type: 'table', title: '表格' }] },
      ],
      theme: { preset: 'dark' },
    }
    const result = patchDashboardSchema(original, {
      id: 'card-1',
      type: 'kpi',
      title: '新卡片',
      position: { x: 2, y: 3, w: 4, h: 5 },
      config: { color: 'blue', datasetPipeline: { script: 'result = []' } },
    })

    expect(result.pages).toHaveLength(2)
    expect(result.pages[0].components).toHaveLength(2)
    expect(result.pages[0].components[0]).toMatchObject({ id: 'card-1', title: '新卡片', config: { color: 'blue', datasetPipeline: { script: 'result = []' } } })
    expect(result.pages[0].components[1]).toMatchObject({ id: 'chart-1', title: '图表' })
    expect(result.pages[1].components[0]).toMatchObject({ id: 'table-1', title: '表格' })
    expect(result.theme).toEqual({ preset: 'dark' })
    expect(original.pages[0].components[0].title).toBe('原卡片')
  })

  it('目标组件不存在时只追加到第一个页面，不重建页面', () => {
    const original = { version: '1.1', pages: [{ id: 'page-a', name: '页面 A', order: 0, components: [] }] }
    const result = patchDashboardSchema(original, { id: 'card-new', type: 'kpi', title: '新卡片' })
    expect(result.pages).toHaveLength(1)
    expect(result.pages[0].components).toHaveLength(1)
    expect(result.pages[0].components[0]).toMatchObject({ id: 'card-new', title: '新卡片' })
  })
})
