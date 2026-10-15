import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useInsight } from '../useInsight'
import * as backend from '../useInsightBackend'
import * as datasetApi from '@/api/dataset'
import * as dashboardApi from '@/api/insight-dashboard'

const { state, runComponentPreview, resetResultSet } = useInsight()

describe('Python 查看数据查询等待时间', () => {
  beforeEach(() => {
    state.backend.dashboardId = 'dashboard-1'
    state.backend.componentId = 'component-1'
    state.cards = [{ id: 'card-1', type: 'table', title: '测试表', chartType: undefined, multiMetric: false }] as never
    state.activeCardId = 'card-1'
    state.datasets = []
    state.hasPython = true
    state.pythonUser = 'result = 1'
    resetResultSet('empty')
    vi.spyOn(datasetApi, 'list').mockResolvedValue([] as never)
    vi.spyOn(backend, 'submitComponentExecution').mockResolvedValue({ executionId: 'execution-1' })
    vi.spyOn(backend, 'fetchExecutionStatus').mockImplementation(async () => ({ status: 'RUNNING' }))
    vi.spyOn(dashboardApi, 'getExecutionResult').mockResolvedValue({
      envelope: {
        schemaVersion: '1.0',
        kind: 'table',
        data: { columns: [{ name: 'value', title: '值', dataType: 'number', nullable: false }], rows: [{ value: 1 }] },
        meta: { rowCount: 1, truncated: false },
      },
    } as never)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    state.datasets = []
    state.hasPython = false
    state.pythonUser = ''
    resetResultSet('empty')
    state.backend.dashboardId = ''
    state.backend.componentId = ''
    state.cards = []
  })

  it('执行超过 60 秒仍继续轮询，并在执行成功后返回结果', async () => {
    vi.useFakeTimers()
    let calls = 0
    vi.spyOn(backend, 'fetchExecutionStatus').mockImplementation(async () => {
      calls += 1
      return { status: calls <= 120 ? 'RUNNING' : 'SUCCEEDED' }
    })

    const pendingPreview = runComponentPreview()
    await vi.advanceTimersByTimeAsync(60_500)

    await expect(pendingPreview).resolves.toEqual({ ok: true, message: 'execution-1' })
    expect(calls).toBe(121)
  })

  it('后端返回字符串错误时展示原始错误并写入控制台', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    vi.spyOn(backend, 'fetchExecutionStatus').mockResolvedValue({
      status: 'FAILED',
      error: 'ValueError: 指标字段不存在',
    } as never)

    await expect(runComponentPreview()).resolves.toEqual({
      ok: false,
      message: 'ValueError: 指标字段不存在',
    })

    expect(state.resultSet.error).toBe('ValueError: 指标字段不存在')
    expect(consoleError).toHaveBeenCalledWith('[python-execution] failed', expect.objectContaining({
      executionId: 'execution-1',
      status: 'FAILED',
      error: 'ValueError: 指标字段不存在',
    }))
  })

  it('状态接口返回 HTTP 错误时提取服务端 message 而非只显示状态码', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const requestError = Object.assign(new Error('Request failed with status code 500'), {
      response: { status: 500, data: { msg: 'Python Worker 状态查询失败：进程不可用' } },
    })
    vi.spyOn(backend, 'fetchExecutionStatus').mockRejectedValue(requestError)

    await expect(runComponentPreview()).resolves.toEqual({
      ok: false,
      message: 'Python Worker 状态查询失败：进程不可用',
    })

    expect(state.backend.lastError).toBe('Python Worker 状态查询失败：进程不可用')
    expect(consoleError).toHaveBeenCalledWith('[python-execution] request failed', expect.objectContaining({
      executionId: 'execution-1',
      error: 'Python Worker 状态查询失败：进程不可用',
    }))
  })
})
