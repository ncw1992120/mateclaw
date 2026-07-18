package vip.mate.sdk.service.agent;

import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;

import java.util.List;

/**
 * Agent 运行时接口
 * <p>
 * 提供 Agent 对话、CRUD、模板应用等编程式访问能力。
 */
public interface AgentRuntime {

    /**
     * 与指定 Agent 进行结构化流式对话
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId);

    /**
     * 与指定 Agent 进行结构化流式对话（完整参数）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param requesterId    请求者 ID
     * @param thinkingLevel  思考深度级别
     * @param origin         对话来源上下文
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                           String requesterId, String thinkingLevel, ChatOrigin origin);

    /**
     * 与指定 Agent 进行结构化流式对话（指定模型名称）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param modelName      模型名称（覆盖 Agent 默认模型）
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                           String modelName);

    /**
     * 与指定 Agent 进行同步对话，阻塞等待完整响应
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @return Agent 完整回复文本
     */
    String chat(Long agentId, String message, String conversationId);

    /**
     * 应用模板创建 Agent
     *
     * @param templateId  模板 ID
     * @param workspaceId 工作区 ID
     * @param userId      创建者用户 ID
     * @return 创建的 Agent 实体
     */
    AgentEntity applyTemplate(String templateId, Long workspaceId, Long userId);

    /**
     * 按工作区列出 Agent
     *
     * @param workspaceId 工作区 ID
     * @param enabled     是否仅列出已启用的 Agent，null 表示不过滤
     * @return Agent 实体列表
     */
    List<AgentEntity> listAgentsByWorkspace(Long workspaceId, Boolean enabled);

    /**
     * 根据 ID 获取 Agent
     *
     * @param id Agent ID
     * @return Agent 实体
     */
    AgentEntity getAgent(Long id);

    /**
     * 创建 Agent
     *
     * @param agent Agent 实体
     * @return 创建后的 Agent 实体
     */
    AgentEntity createAgent(AgentEntity agent);

    /**
     * 更新 Agent
     *
     * @param agent Agent 实体（需包含 ID）
     * @return 更新后的 Agent 实体
     */
    AgentEntity updateAgent(AgentEntity agent);

    /**
     * 删除 Agent
     *
     * @param id Agent ID
     */
    void deleteAgent(Long id);
}
