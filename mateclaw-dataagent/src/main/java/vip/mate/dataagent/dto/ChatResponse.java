package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 同步对话响应
 */
@Data
public class ChatResponse {
    /** Agent 回复内容 */
    private String content;
    /** 会话 ID */
    private String conversationId;
}
