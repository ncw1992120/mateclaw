import { afterEach, describe, expect, it } from 'vitest'
import api from '../index'
import { listAloudataCategoryCounts, pageAloudataDimensions, pageAloudataMetrics } from '../semantic-model'

describe('Aloudata 指标维度选择器请求超时', () => {
  const originalAdapter = api.defaults.adapter
  const capturedTimeouts: Array<number | undefined> = []

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    capturedTimeouts.length = 0
  })

  it('目录、指标和维度搜索请求不使用全局请求超时', async () => {
    api.defaults.adapter = async (config) => {
      capturedTimeouts.push(config.timeout)
      return { data: { code: 200, data: { records: [], total: 0 } }, status: 200, statusText: 'OK', headers: {}, config }
    }

    await listAloudataCategoryCounts('source-1', 'CATEGORY_METRIC')
    await pageAloudataMetrics('source-1', { pageNumber: 1, pageSize: 20, keyword: '指标全名' })
    await pageAloudataDimensions('source-1', { pageNumber: 1, pageSize: 20, keyword: '维度全名' })

    expect(capturedTimeouts).toEqual([0, 0, 0])
  })
})
