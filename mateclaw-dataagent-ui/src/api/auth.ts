import api from './index'
import type { CurrentUserInfo, LoginResponse } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/auth'

/** 领航认证类型：UM=域账号口令 / AD=用户主机账号口令 */
export type PilotAuthType = 'UM' | 'AD'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
  /** 企业认证图形验证码请求 ID（触发风控后的重试登录必传） */
  requestId?: string
  /** 用户输入的图形验证码（触发风控后的重试登录必传） */
  validCode?: string
  /** 认证类型，缺省由后端配置默认值决定 */
  authnType?: PilotAuthType
  /** 登录通道：local=强制本地账密校验（"本地账号登录"表单）；缺省自动路由 */
  channel?: 'local'
}

/** 企业认证图形验证码 */
export interface CaptchaInfo {
  requestId: string
  /** Base64 PNG，直接用于 <img src="data:image/png;base64,..."> */
  captchaImage: string
}

/** 认证模式：local=本地账密（隐藏企业认证选择器）；pilot=领航代验 */
export interface AuthMode {
  provider: 'local' | 'pilot'
  authTypes: string[]
  /** 领航 SSO Cookie 名（共享域）；非空时登录页尝试静默免登 */
  ssoCookieName?: string | null
}

/**
 * 判断错误是否为"需要图形验证码"（后端 HTTP 429）。
 * axios 错误对象携带 response，供登录页据此切换验证码 UI。
 */
export function isNeedCaptchaError(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 429
}

/** 用户登录 */
export function login(params: LoginParams) {
  return api.post<LoginResponse>(`${BASE_URL}/login`, params)
}

/** 查询认证模式（登录页初始化：决定是否展示企业认证选择器） */
export function getAuthMode() {
  return api.get<AuthMode>(`${BASE_URL}/mode`)
}

/** 企业 SSO 免登：用领航共享域 Cookie 换取本地会话（失败静默处理） */
export function ssoLogin(params: { ssoCookie: string; authnType?: PilotAuthType }) {
  return api.post<LoginResponse>(`${BASE_URL}/sso/login`, params)
}

/** 领航 SSO 会话续期透传（失败不影响本地会话） */
export function renewSsoSession(params: { ssoCookie: string; authnType?: PilotAuthType }) {
  return api.post<void>(`${BASE_URL}/sso/renewal`, params)
}

/** 获取企业认证图形验证码（登录返回 429 后调用），authMechanism 与认证类型传同一值 */
export function getCaptcha(params?: { authnType?: PilotAuthType }) {
  return api.get<CaptchaInfo>(`${BASE_URL}/captcha`, { params })
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
