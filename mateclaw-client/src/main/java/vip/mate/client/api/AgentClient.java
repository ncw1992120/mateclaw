package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.Agent;
import vip.mate.client.model.R;
import vip.mate.client.model.request.AgentChatReq;
import vip.mate.client.model.response.AgentCapabilitiesResp;
import vip.mate.client.model.response.AgentStateResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 管理客户端
 * <p>
 * 对应服务端 /api/v1/agents 接口，提供 Agent 的增删改查、对话、执行等管理功能
 */
public class AgentClient extends AbstractApiClient {

    public AgentClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Agent 列表
     *
     * @param enabled 是否启用（可选）
     * @return Agent 列表
     */
    public R<List<Agent>> list(Boolean enabled) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (enabled != null) {
            params.put("enabled", enabled);
        }
        return get(ApiPathConstants.AGENT, params, new ParameterizedTypeReference<R<List<Agent>>>() {});
    }

    /**
     * 获取 Agent 详情
     *
     * @param id Agent ID
     * @return Agent 详情
     */
    public R<Agent> get(Long id) {
        return get(resolvePath(ApiPathConstants.AGENT_BY_ID, id), new ParameterizedTypeReference<R<Agent>>() {});
    }

    /**
     * 获取 Agent 能力信息
     *
     * @param id Agent ID
     * @return Agent 能力信息
     */
    public R<AgentCapabilitiesResp> capabilities(Long id) {
        return get(resolvePath(ApiPathConstants.AGENT_CAPABILITIES, id), new ParameterizedTypeReference<R<AgentCapabilitiesResp>>() {});
    }

    /**
     * 创建 Agent
     *
     * @param agent Agent 信息
     * @return 创建的 Agent 信息
     */
    public R<Agent> create(Agent agent) {
        return post(ApiPathConstants.AGENT, agent, new ParameterizedTypeReference<R<Agent>>() {});
    }

    /**
     * 更新 Agent
     *
     * @param id    Agent ID
     * @param agent Agent 更新信息
     * @return 更新后的 Agent 信息
     */
    public R<Agent> update(Long id, Agent agent) {
        return put(resolvePath(ApiPathConstants.AGENT_BY_ID, id), agent, new ParameterizedTypeReference<R<Agent>>() {});
    }

    /**
     * 删除 Agent
     *
     * @param id Agent ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.AGENT_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 与 Agent 对话
     *
     * @param id      Agent ID
     * @param request 对话请求参数
     * @return 对话响应内容
     */
    public R<String> chat(Long id, AgentChatReq request) {
        return post(resolvePath(ApiPathConstants.AGENT_CHAT, id), request, new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 执行 Agent 任务
     *
     * @param id      Agent ID
     * @param request 执行请求参数
     * @return 执行响应内容
     */
    public R<String> execute(Long id, AgentChatReq request) {
        return post(resolvePath(ApiPathConstants.AGENT_EXECUTE, id), request, new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 获取 Agent 状态
     *
     * @param id Agent ID
     * @return Agent 状态信息
     */
    public R<AgentStateResp> getState(Long id) {
        return get(resolvePath(ApiPathConstants.AGENT_STATE, id), new ParameterizedTypeReference<R<AgentStateResp>>() {});
    }
}
