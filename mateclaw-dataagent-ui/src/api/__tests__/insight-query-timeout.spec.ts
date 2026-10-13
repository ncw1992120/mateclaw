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

  it('数据集草稿、数据源及计划预览默认不受全局 60 秒超时限制', async () => {
    captureRequests()

    await datasetApi.previewDraft({ sourceType: 'JDBC_SQL' })
    await datasetApi.previewInput({ datasetId: 'dataset-1' } as never)
    await datasetApi.previewQueryPlan({ datasetId: 'dataset-1', queryContext: {} } as never)

    expect(capturedTimeouts).toEqual([0, 0, 0])
  })

  it('Python 查看数据的执行、状态轮询和结果读取不受全局 60 秒超时限制', async () => {
    captureRequests()

    await dashboardApi.executeComponent('dashboard-1', 'component-1')
    await dashboardApi.getExecutionStatus('execution-1')
    await dashboardApi.getExecutionResult('execution-1')
    await dashboardApi.previewExecutionResult('execution-1', { finalResultQueryConfig: {} })
    await dashboardApi.executeComponent('dashboard-1', 'component-1', {}, undefined, undefined, { timeout: 5000 })

    expect(capturedTimeouts).toEqual([0, 0, 0, 0, 5000])
  })

  it('仪表盘与查询计划的长查询不受全局 60 秒超时限制，但普通详情请求仍保留默认超时', async () => {
    captureRequests()

    await dashboardApi.preview('dashboard-1')
    await dashboardApi.previewComponent({} as never)
    await dashboardApi.execute('dashboard-1')
    await dashboardApi.get('dashboard-1')

    expect(capturedTimeouts).toEqual([0, 0, 0, 60000])
  })
})
