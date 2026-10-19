import { describe, expect, it, vi } from 'vitest'
import { generateComponentAnalysis } from '../insight-report'

const post = vi.hoisted(() => vi.fn())
vi.mock('../index', () => ({ default: { post } }))

describe('组件 AI 分析取消协议', () => {
  it('取消浏览器请求时同时取消相同请求标识的服务端分析', async () => {
    const controller = new AbortController()
    let finish!: (value: string) => void
    post.mockImplementationOnce(() => new Promise<string>((resolve) => { finish = resolve }))
      .mockResolvedValueOnce(undefined)
    const request = generateComponentAnalysis('7', 'ai-1', {
      snapshot: { pageId: 'p', pageTitle: '页面', filters: {}, components: [] },
    }, controller.signal)
    const requestId = post.mock.calls[0][1].requestId
    expect(requestId).toEqual(expect.any(String))
    controller.abort()
    expect(post).toHaveBeenNthCalledWith(2, `/v1/insight/dashboards/7/components/ai-1/ai-analysis/${requestId}/cancel`)
    finish('已完成')
    await request
  })

  it('分析完成后释放取消监听，后续 abort 不再取消已完成任务', async () => {
    post.mockReset().mockResolvedValue('分析结果')
    const controller = new AbortController()
    await generateComponentAnalysis('7', 'ai-1', {
      snapshot: { pageId: 'p', pageTitle: '页面', filters: {}, components: [] },
    }, controller.signal)
    controller.abort()
    expect(post).toHaveBeenCalledTimes(1)
  })

  it('旧任务的服务端取消尚未确认时，新一轮不得先开始生成', async () => {
    post.mockReset()
    let finishOld!: (value: string) => void
    let confirmCancel!: () => void
    post.mockImplementationOnce(() => new Promise<string>((resolve) => { finishOld = resolve }))
      .mockImplementationOnce(() => new Promise<void>((resolve) => { confirmCancel = resolve }))
      .mockResolvedValueOnce('最新结果')
    const snapshot = { pageId: 'p', pageTitle: '页面', filters: {}, components: [] }
    const controller = new AbortController()
    const old = generateComponentAnalysis('7', 'ai-1', { snapshot }, controller.signal)
    controller.abort()
    const latest = generateComponentAnalysis('7', 'ai-1', { snapshot }, new AbortController().signal)
    await Promise.resolve()
    expect(post).toHaveBeenCalledTimes(2)
    finishOld('过期结果')
    await old
    expect(post).toHaveBeenCalledTimes(2)
    confirmCancel()
    expect(await latest).toBe('最新结果')
    expect(post).toHaveBeenCalledTimes(3)
  })

  it('连续切换时，尚未发送的中间轮次不能绕过最早的取消确认', async () => {
    post.mockReset()
    let finishOld!: (value: string) => void
    let confirmCancel!: () => void
    post.mockImplementationOnce(() => new Promise<string>((resolve) => { finishOld = resolve }))
      .mockImplementationOnce(() => new Promise<void>((resolve) => { confirmCancel = resolve }))
      .mockResolvedValueOnce('C轮最新结果')
    const snapshot = { pageId: 'p', pageTitle: '页面', filters: {}, components: [] }
    const a = new AbortController()
    const b = new AbortController()
    const first = generateComponentAnalysis('7', 'ai-1', { snapshot }, a.signal)
    a.abort()
    const middle = generateComponentAnalysis('7', 'ai-1', { snapshot }, b.signal).catch((error) => error)
    b.abort()
    const latest = generateComponentAnalysis('7', 'ai-1', { snapshot }, new AbortController().signal)
    await Promise.resolve()
    expect(post).toHaveBeenCalledTimes(2)
    confirmCancel()
    expect((await middle).name).toBe('AbortError')
    expect(await latest).toBe('C轮最新结果')
    expect(post).toHaveBeenCalledTimes(3)
    finishOld('A轮结果')
    await first
  })

  it('取消失败时重试，确认前仍禁止发送新一轮生成请求', async () => {
    post.mockReset()
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {})
    let finishOld!: (value: string) => void
    post.mockImplementationOnce(() => new Promise<string>((resolve) => { finishOld = resolve }))
      .mockRejectedValueOnce(new Error('取消失败'))
      .mockRejectedValueOnce(new Error('取消仍失败'))
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce('重试后最新结果')
    const snapshot = { pageId: 'p', pageTitle: '页面', filters: {}, components: [] }
    const controller = new AbortController()
    const old = generateComponentAnalysis('7', 'ai-1', { snapshot }, controller.signal)
    controller.abort()
    await expect(generateComponentAnalysis('7', 'ai-1', { snapshot })).rejects.toThrow('取消仍失败')
    expect(post.mock.calls.slice(1).every(([url]) => url.endsWith('/cancel'))).toBe(true)
    expect(await generateComponentAnalysis('7', 'ai-1', { snapshot })).toBe('重试后最新结果')
    expect(post).toHaveBeenCalledTimes(5)
    finishOld('旧结果')
    await old
    warning.mockRestore()
  })

  it('生成请求断网失败后，也先确认后台取消才允许下一轮', async () => {
    post.mockReset()
    let confirmCancel!: () => void
    post.mockRejectedValueOnce(new Error('连接断开'))
      .mockImplementationOnce(() => new Promise<void>((resolve) => { confirmCancel = resolve }))
      .mockResolvedValueOnce('新结果')
    const snapshot = { pageId: 'p', pageTitle: '页面', filters: {}, components: [] }
    await expect(generateComponentAnalysis('7', 'ai-1', { snapshot })).rejects.toThrow('连接断开')
    const requestId = post.mock.calls[0][1].requestId
    expect(post).toHaveBeenNthCalledWith(2, `/v1/insight/dashboards/7/components/ai-1/ai-analysis/${requestId}/cancel`)
    const latest = generateComponentAnalysis('7', 'ai-1', { snapshot })
    await Promise.resolve()
    expect(post).toHaveBeenCalledTimes(2)
    confirmCancel()
    expect(await latest).toBe('新结果')
  })
})
