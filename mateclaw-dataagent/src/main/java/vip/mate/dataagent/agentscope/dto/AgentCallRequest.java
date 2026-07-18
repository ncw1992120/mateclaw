package vip.mate.dataagent.agentscope.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AgentScope Agent 调用请求
 */
@Data
public class AgentCallRequest {

    /** 用户消息内容 */
    private String message;

    /** Agent 名称（可选，默认使用 dataagent） */
    private String agentName;

    /** 系统提示词（可选，默认使用内置提示词） */
    private String systemPrompt;

    /** 模型 Provider ID（可选，为空时使用后端已配置的激活模型） */
    private String modelProvider;

    /** 模型名称（可选，与 modelProvider 成对传入） */
    private String modelName;

    /** 会话 ID（可选，为空时使用默认值） */
    private String sessionId;

    /** 用户 ID（可选，为空时使用默认值） */
    private String userId;

    /** 工具定义列表（可选，AgentScope ToolSpec 格式） */
    private List<Map<String, Object>> tools;
}
