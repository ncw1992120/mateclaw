import api from './index'
import type { Conversation, MessageVO, ContextUsage } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/conversations'

/** 获取会话列表 */
export function listConversations() {
  return api.get<Conversation[]>(`${BASE_URL}`)
}

/** 获取指定会话的消息历史 */
export function listMessages(conversationId: string) {
  return api.get<MessageVO[]>(`${BASE_URL}/${conversationId}/messages`)
}

/** 删除会话 */
export function deleteConversation(conversationId: string) {
  return api.delete(`${BASE_URL}/${conversationId}`)
}

/** 重命名会话标题 */
export function renameConversation(conversationId: string, title: string) {
  return api.put(`${BASE_URL}/${conversationId}/title`, { title })
}

/** 置顶或取消置顶会话 */
export function setPinned(conversationId: string, pinned: boolean) {
  return api.put(`${BASE_URL}/${conversationId}/pin`, { pinned })
}

/** 获取会话上下文使用情况 */
export function getContextUsage(conversationId: string) {
  return api.get<ContextUsage>(`${BASE_URL}/${conversationId}/context-usage`)
}

/** 获取会话实时流状态（兜底探测，对齐 mateclaw-ui 两层判断） */
export function getStatus(conversationId: string) {
  return api.get<{ streamStatus: string }>(`${BASE_URL}/${conversationId}/status`)
}
