import JSEncrypt from 'jsencrypt'
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

/** 传输加密公钥 */
export interface PublicKeyInfo {
  /** RSA 公钥 PEM（SPKI，jsencrypt 可直接使用） */
  publicKey: string
  algorithm: string
}

/**
 * 判断错误是否为"需要图形验证码"（后端 HTTP 429）。
 * axios 错误对象携带 response，供登录页据此切换验证码 UI。
 */
export function isNeedCaptchaError(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 429
}

/**
 * 敏感字段传输加密：信封 `base64( RSA-OAEP( base64(UTF-8("毫秒时间戳:明文")) ) )`。
 * 每次发送前实时拉取公钥（避免后端重启换钥后旧缓存失效；登录频次低，成本可忽略）。
 * 明文字符串先做 UTF-8→Base64 再进 OAEP：jsencrypt 的 OAEP 按字节掩码，
 * 对中文等 >255 码位的字符处理有缺陷（会破坏密文结构），Base64 化后输入恒为纯 ASCII。
 * 后端解密后按 base64 → UTF-8 还原并校验时间戳窗口（防重放）。
 */
async function encryptSensitiveField(plain: string): Promise<string> {
  // 响应拦截器已解开 R 信封，返回值即 { publicKey, algorithm } 本体
  const info = await api.get<PublicKeyInfo>(`${BASE_URL}/pubkey`)
  if (!info?.publicKey) {
    throw new Error('获取传输加密公钥失败，请刷新页面重试')
  }
  const enc = new JSEncrypt()
  enc.setPublicKey(info.publicKey)
  // jsencrypt 3.5.x 的 OAEP 方法：RSA_PKCS1_OAEP_PADDING + SHA-256（与后端
  // RSA/ECB/OAEPWithSHA-256AndMGF1Padding 对齐）；返回 false 表示加密失败
  const blob = `${Date.now()}:${plain}`
  const utf8 = new TextEncoder().encode(blob)
  let bin = ''
  for (let i = 0; i < utf8.length; i++) {
    bin += String.fromCharCode(utf8[i])
  }
  const encrypted = enc.encryptOAEP(window.btoa(bin))
  if (!encrypted) {
    throw new Error('敏感字段加密失败，请刷新页面重试')
  }
  return encrypted
}

/** 用户登录（password 加密后传输） */
export async function login(params: LoginParams) {
  const payload: LoginParams = { ...params, password: await encryptSensitiveField(params.password) }
  return api.post<LoginResponse>(`${BASE_URL}/login`, payload)
}

/** 查询认证模式（登录页初始化：决定是否展示企业认证选择器） */
export function getAuthMode() {
  return api.get<AuthMode>(`${BASE_URL}/mode`)
}

/** 企业 SSO 免登：用领航共享域 Cookie 换取本地会话（失败静默处理；ssoCookie 加密后传输） */
export async function ssoLogin(params: { ssoCookie: string; authnType?: PilotAuthType }) {
  return api.post<LoginResponse>(`${BASE_URL}/sso/login`, {
    ...params,
    ssoCookie: await encryptSensitiveField(params.ssoCookie),
  })
}

/** 领航 SSO 会话续期透传（失败不影响本地会话；ssoCookie 加密后传输） */
export async function renewSsoSession(params: { ssoCookie: string; authnType?: PilotAuthType }) {
  return api.post<void>(`${BASE_URL}/sso/renewal`, {
    ...params,
    ssoCookie: await encryptSensitiveField(params.ssoCookie),
  })
}

/** 获取企业认证图形验证码（登录返回 429 后调用），authMechanism 与认证类型传同一值 */
export function getCaptcha(params?: { authnType?: PilotAuthType }) {
  return api.get<CaptchaInfo>(`${BASE_URL}/captcha`, { params })
}

/** 获取当前用户信息（刷新页面后恢复状态） */
export function getCurrentUser() {
  return api.get<CurrentUserInfo>(`${BASE_URL}/me`)
}

/** 修改密码（口令走请求体 RSA-OAEP 加密传输，避免进 URL 被访问日志记录） */
export async function changePassword(oldPassword: string, newPassword: string) {
  return api.put<void>(`${BASE_URL}/password`, {
    oldPassword: await encryptSensitiveField(oldPassword),
    newPassword: await encryptSensitiveField(newPassword),
  })
}