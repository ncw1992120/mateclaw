package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.SubagentPauseReq;
import vip.mate.client.model.response.SubagentInterruptResp;
import vip.mate.client.model.response.SubagentListResp;
import vip.mate.client.model.response.SubagentPauseResp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子 Agent 客户端
 * <p>
 * 对应服务端 /api/v1/subagents 接口，提供子 Agent 管理功能
 */
public class SubagentClient extends AbstractApiClient {

    public SubagentClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 中断子 Agent
     *
     * @param subagentId 子 Agent ID
     * @return 操作结果
     */
    public R<SubagentInterruptResp> interrupt(String subagentId) {
        return post(resolvePath(ApiPathConstants.SUBAGENT_INTERRUPT, subagentId), null,
                new ParameterizedTypeReference<R<SubagentInterruptResp>>() {});
    }

    /**
     * 暂停/恢复生成子 Agent
     *
     * @param request 暂停请求参数
     * @return 操作结果
     */
    public R<SubagentPauseResp> setPaused(SubagentPauseReq request) {
        return post(ApiPathConstants.SUBAGENT_SPAWN_PAUSE, request,
                new ParameterizedTypeReference<R<SubagentPauseResp>>() {});
    }

    /**
     * 获取活跃子 Agent 列表
     *
     * @param parentConversationId 父会话 ID（可选）
     * @return 活跃子 Agent 信息
     */
    public R<SubagentListResp> listActive(String parentConversationId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (parentConversationId != null) {
            params.put("parentConversationId", parentConversationId);
        }
        return get(ApiPathConstants.SUBAGENT_ACTIVE, params,
                new ParameterizedTypeReference<R<SubagentListResp>>() {});
    }
}
