package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.service.ChatOptimizeService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;
import java.util.UUID;

/**
 * 对话输入优化服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOptimizeServiceImpl implements ChatOptimizeService {

    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;

    @Override
    public String optimizePrompt(String input) {
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        Long agentId = resolveFirstEnabledAgent(workspaceId);
        String conversationId = DataAgentConstants.OPTIMIZE_CONVERSATION_PREFIX + UUID.randomUUID();
        String prompt = DataAgentConstants.OPTIMIZE_PROMPT_TEMPLATE.replace("{0}", input);

        StringBuilder content = new StringBuilder();
        Flux<StreamDelta> stream = runtime.chatStructuredStream(agentId, prompt, conversationId);
        stream.doOnNext(delta -> {
            if (!delta.isEvent() && delta.content() != null && !delta.content().isBlank()) {
                content.append(delta.content());
            }
        }).blockLast();

        return content.toString().trim();
    }

    /**
     * 获取当前工作区下第一个启用的 Agent ID
     *
     * @param workspaceId 工作区 ID
     * @return 第一个启用 Agent 的 ID
     */
    private Long resolveFirstEnabledAgent(Long workspaceId) {
        List<AgentEntity> agents = runtime.listAgentsByWorkspace(workspaceId, true);
        if (agents == null || agents.isEmpty()) {
            throw new IllegalStateException("当前工作区下没有可用的 Agent");
        }
        return agents.get(0).getId();
    }
}
