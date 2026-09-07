import JSEncrypt from 'jsencrypt'
import api from '@/api'

/** 传输加密公钥接口路径（与后端 DataAgentAuthController 一致） */
const PUB_KEY_PATH = '/dataagent/api/v1/auth/pubkey'

/** 传输加密公钥响应结构 */
export interface PublicKeyInfo {
  /** RSA 公钥 PEM（SPKI，jsencrypt 可直接使用） */
  publicKey: string
  algorithm: string
}

/**
 * 敏感字段传输加密：信封 `base64( RSA-OAEP( base64(UTF-8("毫秒时间戳:明文")) ) )`。
 * 每次发送前实时拉取公钥（避免后端重启换钥后旧缓存失效；调用频次低，成本可忽略）。
 * 明文字符串先做 UTF-8→Base64 再进 OAEP：jsencrypt 的 OAEP 按字节掩码，
 * 对中文等 >255 码位的字符处理有缺陷（会破坏密文结构），Base64 化后输入恒为纯 ASCII。
 * 后端解密后按 base64 → UTF-8 还原并校验时间戳窗口（防重放）。
 *
 * @param plain 明文（登录密码 / 数据源密码 / 查询账号密码 / SSO Cookie 等敏感字段）
 */
export async function encryptSensitiveField(plain: string): Promise<string> {
  // 响应拦截器已解开 R 信封，返回值即 { publicKey, algorithm } 本体
  const info = await api.get<PublicKeyInfo>(PUB_KEY_PATH)
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