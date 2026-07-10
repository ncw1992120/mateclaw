/**
 * 聊天错误分类与结构化信息
 * <p>
 * 将 HTTP 状态码和网络异常映射为用户可理解的错误类别，
 * 借鉴 mateclaw-ui 的 chatError 模块，适配 dataagent 的后端错误类型。
 */

/** 聊天错误类别 */
export type ChatErrorCategory =
  | 'rate_limit'
  | 'auth_expired'
  | 'provider_auth_error'
  | 'forbidden'
  | 'bad_request'
  | 'server_error'
  | 'service_unavailable'
  | 'timeout'
  | 'network'
  | 'unknown'

/** 结构化错误信息 */
export interface ChatErrorInfo {
  /** 错误类别 */
  category: ChatErrorCategory
  /** HTTP 状态码 */
  httpStatus?: number
  /** 请求 ID */
  requestId?: string
  /** 原始错误消息 */
  rawMessage?: string
  /** 是否可重试 */
  retryable: boolean
  /** 错误时间戳 */
  timestamp: number
}

/**
 * 根据 HTTP 状态码分类错误
 */
export function classifyHttpError(status: number, body?: unknown): ChatErrorInfo {
  const raw = body as Record<string, unknown> | undefined
  const base: ChatErrorInfo = {
    category: 'unknown',
    httpStatus: status,
    rawMessage: (raw?.msg as string) || (raw?.message as string) || undefined,
    requestId: (raw?.requestId as string) || undefined,
    retryable: false,
    timestamp: Date.now(),
  }

  switch (true) {
    case status === 429:
      return { ...base, category: 'rate_limit', retryable: true }
    case status === 401:
      return { ...base, category: 'auth_expired', retryable: false }
    case status === 403:
      return { ...base, category: 'forbidden', retryable: false }
    case status === 400:
      return { ...base, category: 'bad_request', retryable: false }
    case status === 503:
      return { ...base, category: 'service_unavailable', retryable: true }
    case status >= 500:
      return { ...base, category: 'server_error', retryable: true }
    default:
      return { ...base, retryable: true }
  }
}

/**
 * 根据网络层异常分类错误
 */
export function classifyNetworkError(error: Error): ChatErrorInfo {
  const isTimeout = error.name === 'TimeoutError'
    || error.message?.includes('timeout')
    || error.message?.includes('Timeout')

  return {
    category: isTimeout ? 'timeout' : 'network',
    rawMessage: error.message,
    retryable: true,
    timestamp: Date.now(),
  }
}

/**
 * 从 SSE error 事件数据构建 ChatErrorInfo
 */
export function classifySseError(data: Record<string, unknown>): ChatErrorInfo {
  const message = (data.message as string) || '请求失败'
  const patterns: Array<{ pattern: RegExp; category: ChatErrorCategory; retryable: boolean }> = [
    { pattern: /频率|rate.?limit|too.?many|quota|429/i, category: 'rate_limit', retryable: true },
    { pattern: /HTTP 401|登录已过期|session.?expired|凭证.*失效/i, category: 'auth_expired', retryable: false },
    { pattern: /unauthorized|401|invalid.?api.?key|认证失败/i, category: 'provider_auth_error', retryable: false },
    { pattern: /权限|forbidden|403/i, category: 'forbidden', retryable: false },
    { pattern: /超时|timeout/i, category: 'timeout', retryable: true },
    { pattern: /不可用|unavailable|503|502|504|过载|overload/i, category: 'service_unavailable', retryable: true },
    { pattern: /服务器|server.?error|500|internal/i, category: 'server_error', retryable: true },
  ]

  for (const { pattern, category, retryable } of patterns) {
    if (pattern.test(message)) {
      return { category, rawMessage: message, retryable, timestamp: Date.now() }
    }
  }
  return { category: 'unknown', rawMessage: message, retryable: true, timestamp: Date.now() }
}

/**
 * 错误类别的用户友好提示
 */
export function getErrorDisplayMessage(errorInfo: ChatErrorInfo): string {
  switch (errorInfo.category) {
    case 'rate_limit':
      return '请求频率过高，请稍后重试'
    case 'auth_expired':
      return '登录已过期，请重新登录'
    case 'provider_auth_error':
      return '模型认证失败，请联系管理员'
    case 'forbidden':
      return '无权限执行此操作'
    case 'bad_request':
      return errorInfo.rawMessage || '请求参数有误'
    case 'server_error':
      return '服务器内部错误，请稍后重试'
    case 'service_unavailable':
      return '服务暂时不可用，请稍后重试'
    case 'timeout':
      return '请求超时，请检查网络后重试'
    case 'network':
      return '网络连接异常，请检查网络后重试'
    default:
      return errorInfo.rawMessage || '请求出现异常，请稍后重试'
  }
}
