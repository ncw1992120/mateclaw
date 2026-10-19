import { afterEach, describe, expect, it, vi } from 'vitest'
import { CanceledError } from 'axios'
import api from '../index'

const showError = vi.hoisted(() => vi.fn())
vi.mock('element-plus', () => ({ ElMessage: { error: showError } }))

describe('主动取消请求', () => {
  const originalAdapter = api.defaults.adapter
  afterEach(() => { api.defaults.adapter = originalAdapter; vi.clearAllMocks() })

  it('保留取消信号供调用方识别，不弹出网络错误', async () => {
    api.defaults.adapter = async () => { throw new CanceledError('canceled') }
    await expect(api.get('/v1/insight/dashboards/7')).rejects.toBeInstanceOf(CanceledError)
    expect(showError).not.toHaveBeenCalled()
  })
})
