package vip.mate.dataagent.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.agent.model.AgentEntity;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.concurrent.TimeUnit;

/**
 * Agent 资源归属校验守卫
 * <p>
 * 校验指定 Agent 是否属于当前工作区，防止跨工作区越权访问。
 * 使用 Caffeine 本地缓存（60s TTL）减少 DB 查询，提升首次对话响应速度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGuard {

    private final MateClawRuntime mateClawRuntime;
    private final WorkspaceGuard workspaceGuard;

    /**
     * Agent 实体本地缓存（60s TTL）。
     * <p>
     * Agent 配置变更频率低，每次对话请求都查 DB 浪费。缓存 60 秒后自动失效，
     * Agent 配置变更最多延迟 60s 生效，对权限校验场景可接受。
     */
    private final Cache<Long, AgentEntity> agentCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();

    /**
     * 校验 Agent 是否属于当前工作区
     *
     * @param agentId Agent ID
     * @throws BusinessException 当 Agent 不存在或不属于当前工作区时抛出
     */
    public void requireAgentInCurrentWorkspace(Long agentId) {
        AgentEntity agent = agentCache.get(agentId, id -> mateClawRuntime.getAgent(id));
        if (agent == null) {
            // 缓存未命中且 DB 也查不到，清除缓存条目后抛异常
            agentCache.invalidate(agentId);
            throw new BusinessException(404, "Agent 不存在: " + agentId);
        }
        Long currentWorkspaceId = workspaceGuard.currentWorkspaceId();
        if (agent.getWorkspaceId() == null
                || !agent.getWorkspaceId().equals(currentWorkspaceId)) {
            throw new BusinessException(403, "无权访问该 Agent");
        }
    }

    /**
     * 使指定 Agent 的缓存失效（在 Agent 配置变更时调用）
     *
     * @param agentId Agent ID
     */
    public void invalidateCache(Long agentId) {
        agentCache.invalidate(agentId);
    }
}
