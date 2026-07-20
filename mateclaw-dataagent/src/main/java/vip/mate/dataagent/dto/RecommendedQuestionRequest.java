package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 推荐问题请求
 */
@Data
public class RecommendedQuestionRequest {
    /** 会话ID */
    private String conversationId;
    /** Agent ID */
    private Long agentId;
    /** 当前用户问题 */
    private String userMessage;
    /** AI回答内容摘要（可选，用于提升推荐质量） */
    private String assistantSummary;
}
