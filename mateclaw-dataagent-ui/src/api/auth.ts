import api from './index'
import type { CurrentUserInfo, LoginResponse } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/auth'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
}

/** 用户登录 */
export function login(params: LoginParams) {
  return api.post<LoginResponse>(`${BASE_URL}/login`, params)
}

/** 获取当前用户信息（刷新页面后恢复状态） */
export function getCurrentUser() {
  return api.get<CurrentUserInfo>(`${BASE_URL}/me`)
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return api.put<void>(`${BASE_URL}/password`, null, {
    params: { oldPassword, newPassword },
  })
}
