package vip.mate.dataagent.agentscope;

/**
 * AgentScope 相关常量定义
 */
public final class AgentScopeConstants {

    private AgentScopeConstants() {
    }

    /** AgentScope 模型 ID 分隔符，格式为 providerId:modelName */
    public static final String MODEL_ID_SEPARATOR = ":";

    /** DashScope Provider 标识 */
    public static final String PROVIDER_DASHSCOPE = "dashscope";

    /** OpenAI 兼容 Provider 标识 */
    public static final String PROVIDER_OPENAI = "openai";

    /** 默认 Agent 名称 */
    public static final String DEFAULT_AGENT_NAME = "dataagent";

    /** 默认系统提示词 */
    public static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful data analysis assistant.";

    /** 默认会话 ID */
    public static final String DEFAULT_SESSION_ID = "default";

    /** 默认用户 ID */
    public static final String DEFAULT_USER_ID = "system";
}
