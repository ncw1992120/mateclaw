package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.PlanResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划客户端
 * <p>
 * 对应服务端 /api/v1/plans 接口，提供 Agent 计划查询功能
 */
public class PlanningClient extends AbstractApiClient {

    public PlanningClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Agent 计划列表
     *
     * @param agentId Agent ID
     * @return 计划列表
     */
    public R<List<PlanResp>> listByAgent(Long agentId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("agentId", agentId);
        return get(ApiPathConstants.PLAN, params,
                new ParameterizedTypeReference<R<List<PlanResp>>>() {});
    }

    /**
     * 获取计划详情
     *
     * @param id 计划 ID
     * @return 计划详情
     */
    public R<PlanResp> getPlan(Long id) {
        return get(resolvePath(ApiPathConstants.PLAN_BY_ID, id),
                new ParameterizedTypeReference<R<PlanResp>>() {});
    }
}
