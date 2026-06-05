package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.*;

/**
 * Agent 运行时管理客户端
 * <p>
 * 对应服务端 /api/v1/admin/agent-runtime 接口，提供 Agent 运行时管理功能
 * <p>
 * 注意：此接口需要 GlobalAdmin 权限
 */
public class AgentRuntimeClient extends AbstractApiClient {

    public AgentRuntimeClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取运行时快照
     *
     * @return 运行时快照
     */
    public R<RuntimeSnapshotResp> snapshot() {
        return get(ApiPathConstants.AGENT_RUNTIME_SNAPSHOT,
                new ParameterizedTypeReference<R<RuntimeSnapshotResp>>() {});
    }

    /**
     * 友好停止运行
     *
     * @param conversationId 会话 ID
     * @return 停止结果
     */
    public R<RuntimeStopResp> stopFriendly(String conversationId) {
        return post(resolvePath(ApiPathConstants.AGENT_RUNTIME_STOP, conversationId), null,
                new ParameterizedTypeReference<R<RuntimeStopResp>>() {});
    }

    /**
     * 回收运行资源
     *
     * @param conversationId 会话 ID
     * @return 回收结果
     */
    public R<RuntimeRecycleResp> recycle(String conversationId) {
        return post(resolvePath(ApiPathConstants.AGENT_RUNTIME_RECYCLE, conversationId), null,
                new ParameterizedTypeReference<R<RuntimeRecycleResp>>() {});
    }

    /**
     * 中断子 Agent
     *
     * @param subagentId 子 Agent ID
     * @return 中断结果
     */
    public R<RuntimeInterruptResp> interruptSubagent(String subagentId) {
        return post(resolvePath(ApiPathConstants.AGENT_RUNTIME_SUBAGENT_INTERRUPT, subagentId), null,
                new ParameterizedTypeReference<R<RuntimeInterruptResp>>() {});
    }

    /**
     * 清理运行资源
     *
     * @return 清理结果
     */
    public R<RuntimeSweepResp> sweep() {
        return post(ApiPathConstants.AGENT_RUNTIME_SWEEP, null,
                new ParameterizedTypeReference<R<RuntimeSweepResp>>() {});
    }
}