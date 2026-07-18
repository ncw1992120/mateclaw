package vip.mate.sdk.service.agent.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.service.TemplateService;
import vip.mate.sdk.service.agent.AgentRuntime;

import java.util.List;

/**
 * Agent 运行时实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeImpl implements AgentRuntime {

    private final AgentService agentService;
    private final TemplateService templateService;

    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId) {
        return agentService.chatStructuredStream(agentId, message, conversationId);
    }

    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String requesterId, String thinkingLevel, ChatOrigin origin) {
        return agentService.chatStructuredStream(agentId, message, conversationId,
                requesterId, thinkingLevel, origin);
    }

    /**
     * 与指定 Agent 进行结构化流式对话（指定模型名称）
     * <p>
     * 将 modelName 写入 Agent 实体后刷新缓存，使 Agent 使用指定模型重建。
     */
    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            AgentEntity agent = agentService.getAgent(agentId);
            agent.setModelName(modelName);
            agentService.updateAgent(agent);
            agentService.refreshAgent(agentId);
        }
        return agentService.chatStructuredStream(agentId, message, conversationId);
    }

    @Override
    public String chat(Long agentId, String message, String conversationId) {
        return agentService.chat(agentId, message, conversationId);
    }

    @Override
    public AgentEntity applyTemplate(String templateId, Long workspaceId, Long userId) {
        return templateService.applyTemplate(templateId, workspaceId, userId);
    }

    @Override
    public List<AgentEntity> listAgentsByWorkspace(Long workspaceId, Boolean enabled) {
        return agentService.listAgentsByWorkspace(workspaceId, enabled);
    }

    @Override
    public AgentEntity getAgent(Long id) {
        return agentService.getAgent(id);
    }

    @Override
    public AgentEntity createAgent(AgentEntity agent) {
        return agentService.createAgent(agent);
    }

    @Override
    public AgentEntity updateAgent(AgentEntity agent) {
        return agentService.updateAgent(agent);
    }

    @Override
    public void deleteAgent(Long id) {
        agentService.deleteAgent(id);
    }
}
