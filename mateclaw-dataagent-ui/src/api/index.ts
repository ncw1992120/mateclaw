import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '@/types'

/**
 * 将 JSON 中超过 JS 安全整数范围的长整型数字转为字符串，避免精度丢失。
 * 匹配 "id":数字 或 "Id":数字 或 "_id":数字 等键值对中值大于 2^53-1 的数字。
 */
const LONG_ID_REGEX = /("(?:id|Id|ID|_id|datasourceId|datasetId|sourceTableId|tableId|columnId|conversationId|agentId|workspaceId)"\s*:\s*)(\d{16,})/g

function transformLongIds(data: string): string {
  return data.replace(LONG_ID_REGEX, '$1"$2"')
}

/** Axios 实例 */
const api = axios.create({
  baseURL: '',
  timeout: 60000,
  transformResponse: [
    (data) => {
      if (typeof data !== 'string') {
        return data
      }
      try {
        const transformed = transformLongIds(data)
        return JSON.parse(transformed)
      } catch {
        return data
      }
    },
  ],
})

/** 请求拦截器：添加 Authorization 头 + 工作区 ID 头 */
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 从 localStorage 读取当前工作区 ID，注入到请求头
    const workspaceIdRaw = localStorage.getItem('workspaceId')
    if (workspaceIdRaw) {
      try {
        const workspaceId = JSON.parse(workspaceIdRaw)
        config.headers['X-Workspace-Id'] = String(workspaceId)
      } catch {
        // workspaceId 格式异常，忽略
      }
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/** 响应拦截器：统一处理业务错误码 + 401 跳登录 + 滑动续期 */
api.interceptors.response.use(
  (response) => {
    // 滑动续期：后端在 Token 接近过期时通过 X-New-Token 头返回新 Token
    const newToken = response.headers['x-new-token']
    if (newToken) {
      localStorage.setItem('token', newToken)
    }

    const res = response.data as R<unknown>
    // 兼容非 R 格式响应（如 Spring 默认错误页）
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      console.error('[API] 非 R 格式响应:', response.status, response.data)
      const rawMsg = (response.data as any)?.message || (response.data as any)?.error || '服务器异常'
      ElMessage.error(rawMsg)
      return Promise.reject(new Error(rawMsg))
    }
    if (res.code !== 200) {
      console.error('[API] 业务错误:', res.code, res.msg, response.config?.url, response.data)
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res.data as any
  },
  (error) => {
    const status = error.response?.status

    // 401：Token 过期或无效，清除登录状态并跳转登录页
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('workspaceId')
      // 清除所有 mc- 前缀的业务状态，防止下次登录脏数据
      const keysToRemove: string[] = []
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key && key.startsWith('mc-')) {
          keysToRemove.push(key)
        }
      }
      keysToRemove.forEach((k) => localStorage.removeItem(k))
      // 避免在登录页重复跳转
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    const errData = error.response?.data
    const message = errData?.msg || errData?.message || error.message || '网络异常'
    console.error('[API] 网络/HTTP 错误:', status, message, error.config?.url)
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default api
