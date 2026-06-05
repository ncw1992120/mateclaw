package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.FactFeedbackReq;
import vip.mate.client.model.request.FactResolutionReq;
import vip.mate.client.model.response.FactResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fact 事实客户端
 * <p>
 * 对应服务端 /api/v1/memory/{agentId}/facts 接口，提供事实记忆管理功能
 */
public class FactClient extends AbstractApiClient {

    public FactClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Agent 的事实列表
     *
     * @param agentId Agent ID
     * @param keyword 搜索关键词（可选）
     * @return 事实列表
     */
    public R<List<FactResp>> listFacts(Long agentId, String keyword) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (keyword != null) {
            params.put("keyword", keyword);
        }
        return get(resolvePath(ApiPathConstants.FACT, agentId), params,
                new ParameterizedTypeReference<R<List<FactResp>>>() {});
    }

    /**
     * 遗忘事实
     *
     * @param agentId Agent ID
     * @param factId  事实 ID
     * @return 操作结果
     */
    public R<Void> forgetFact(Long agentId, Long factId) {
        return post(resolvePath(ApiPathConstants.FACT_FORGET, agentId, factId), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 对事实进行反馈
     *
     * @param agentId Agent ID
     * @param factId  事实 ID
     * @param request 反馈请求
     * @return 操作结果
     */
    public R<Void> feedbackFact(Long agentId, Long factId, FactFeedbackReq request) {
        return post(resolvePath(ApiPathConstants.FACT_FEEDBACK, agentId, factId), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 获取矛盾事实列表
     *
     * @param agentId Agent ID
     * @return 矛盾事实列表
     */
    public R<List<FactResp>> listContradictions(Long agentId) {
        return get(resolvePath(ApiPathConstants.FACT_CONTRADICTIONS, agentId),
                new ParameterizedTypeReference<R<List<FactResp>>>() {});
    }

    /**
     * 解决事实矛盾
     *
     * @param agentId         Agent ID
     * @param contradictionId 矛盾 ID
     * @param request         解决请求
     * @return 操作结果
     */
    public R<Void> resolveContradiction(Long agentId, Long contradictionId, FactResolutionReq request) {
        return post(resolvePath(ApiPathConstants.FACT_CONTRADICTION_RESOLVE, agentId, contradictionId), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
