package vip.mate.dataagent.agentscope.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AgentScope Agent 调用响应
 */
@Data
public class AgentCallResponse {

    /** Agent 回复文本内容 */
    private String content;

    /** 使用的模型名称 */
    private String modelName;

    /** 使用的 Provider ID */
    private String modelProvider;

    /** 会话 ID */
    private String sessionId;

    /** 工具调用记录（可选） */
    private List<Map<String, Object>> toolCalls;

    /** Prompt token 用量 */
    private int promptTokens;

    /** Completion token 用量 */
    private int completionTokens;

    /** 是否成功 */
    private boolean success;

    /** 错误信息（失败时填充） */
    private String errorMessage;
}
