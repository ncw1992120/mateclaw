package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 对话/执行请求
 */
@Data
public class AgentChatReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息内容 */
    private String message;

    /** 会话 ID（默认 "default"） */
    private String conversationId = "default";
}
