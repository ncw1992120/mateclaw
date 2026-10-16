import { afterEach, describe, expect, it } from 'vitest'
import api from '../index'
import { getAloudataDimensionDetail, getAloudataMetricDetail, getAloudataMetricDirectory } from '../semantic-model'
import { confirmDraft, getComposerExecution, previewDraft, registerApiDefinition, uploadFile } from '../dataset'

describe('Spring Boot 根路径静态部署 API 路径', () => {
  const originalAdapter = api.defaults.adapter

  afterEach(() => {
    api.defaults.adapter = originalAdapter
  })

  it('Aloudata 目录和详情直接请求后端路由，不依赖 Vite 前缀重写', async () => {
    const requests: string[] = []
    api.defaults.adapter = async (config) => {
      requests.push(config.url!)
      return { data: { code: 200, data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    await getAloudataMetricDirectory('source-1')
    await getAloudataMetricDetail('source-1', 'metric/name')
    await getAloudataDimensionDetail('source-1', 'dimension/name')

    expect(requests).toEqual([
      '/v1/datasources/source-1/aloudata/metrics/directory',
      '/v1/datasources/source-1/aloudata/metrics/metric%2Fname/detail',
      '/v1/datasources/source-1/aloudata/dimensions/dimension%2Fname/detail',
    ])
  })

  it('数据集操作直接请求后端路由，并保留请求方法和预览超时配置', async () => {
    const requests: Array<{ url: string; method?: string; timeout?: number }> = []
    api.defaults.adapter = async (config) => {
      requests.push({ url: config.url!, method: config.method, timeout: config.timeout })
      return { data: { code: 200, data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    await previewDraft({ sourceType: 'JDBC' })
    await confirmDraft({ sourceType: 'JDBC', name: '测试数据集' })
    await getComposerExecution('execution/id')
    await uploadFile(new File(['a,b\n1,2'], 'sample.csv', { type: 'text/csv' }))
    await registerApiDefinition({ datasourceId: 'source-1', endpoint: '/data', method: 'GET' })

    expect(requests.map(({ url, method }) => ({ url, method }))).toEqual([
      { url: '/v1/dataset-composer/drafts/preview', method: 'post' },
      { url: '/v1/dataset-composer/drafts/confirm', method: 'post' },
      { url: '/v1/dataset-composer/executions/execution%2Fid', method: 'get' },
      { url: '/v1/dataset-files', method: 'post' },
      { url: '/v1/dataset-composer/api-definitions', method: 'post' },
    ])
    expect(requests[0]?.timeout).toBe(0)
  })
})
