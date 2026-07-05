package vip.mate.dataagent.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.agent.model.AgentEntity;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.sdk.service.MateClawRuntime;

/**
 * Agent 资源归属校验守卫
 * <p>
 * 校验指定 Agent 是否属于当前工作区，防止跨工作区越权访问。
 */
@Component
@RequiredArgsConstructor
public class AgentGuard {

    private final MateClawRuntime mateClawRuntime;
    private final WorkspaceGuard workspaceGuard;

    /**
     * 校验 Agent 是否属于当前工作区
     *
     * @param agentId Agent ID
     * @throws BusinessException 当 Agent 不存在或不属于当前工作区时抛出
     */
    public void requireAgentInCurrentWorkspace(Long agentId) {
        AgentEntity agent = mateClawRuntime.getAgent(agentId);
        if (agent == null) {
            throw new BusinessException(404, "Agent 不存在: " + agentId);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (agent.getWorkspaceId() == null
                || !agent.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该 Agent");
        }
    }
}
