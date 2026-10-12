import { afterEach, describe, expect, it } from 'vitest'
import api from '../index'
import * as datasetApi from '../dataset'
import * as dashboardApi from '../insight-dashboard'

describe('查看数据查询请求超时', () => {
  const originalAdapter = api.defaults.adapter
  const capturedTimeouts: Array<number | undefined> = []

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    capturedTimeouts.length = 0
  })

  function captureRequests(): void {
    api.defaults.adapter = async (config) => {
      capturedTimeouts.push(config.timeout)
      return {
        data: { code: 200, data: {} },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
  }

  it('数据集查看数据的草稿和已保存数据预览不受全局 60 秒超时限制', async () => {
    captureRequests()

    await datasetApi.previewDraft({ sourceType: 'JDBC_SQL' }, { timeout: 0 })
    await datasetApi.previewInput({ datasetId: 'dataset-1' } as never, { timeout: 0 })

    expect(capturedTimeouts).toEqual([0, 0])
  })

  it('Python 查看数据的执行、状态轮询和结果读取不受全局 60 秒超时限制', async () => {
    captureRequests()

    await dashboardApi.executeComponent('dashboard-1', 'component-1', {}, undefined, undefined, { timeout: 0 })
    await dashboardApi.getExecutionStatus('execution-1', { timeout: 0 })
    await dashboardApi.getExecutionResult('execution-1', { timeout: 0 })

    expect(capturedTimeouts).toEqual([0, 0, 0])
  })
})
