package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.MemoryFocusedDreamReq;
import vip.mate.client.model.response.DreamReportResp;
import vip.mate.client.model.response.DreamingStatusResp;
import vip.mate.client.model.response.MemoryRecallResp;
import vip.mate.client.model.response.MemorySummarizeResp;

import java.util.List;

/**
 * 记忆管理客户端
 */
public class MemoryClient extends AbstractApiClient {

    public MemoryClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 触发涌现记忆
     */
    public R<DreamReportResp> triggerEmergence(Long agentId) {
        return post(resolvePath(ApiPathConstants.MEMORY_EMERGENCE, agentId), null,
                new ParameterizedTypeReference<R<DreamReportResp>>() {});
    }

    /**
     * 触发聚焦梦境
     *
     * @param agentId Agent ID
     * @param request 聚焦梦境请求
     * @return 梦境报告
     */
    public R<DreamReportResp> triggerFocusedDream(Long agentId, MemoryFocusedDreamReq request) {
        return post(resolvePath(ApiPathConstants.MEMORY_FOCUSED_DREAM, agentId), request,
                new ParameterizedTypeReference<R<DreamReportResp>>() {});
    }

    /**
     * 触发对话摘要
     *
     * @param agentId        Agent ID
     * @param conversationId 会话 ID
     * @return 摘要结果
     */
    public R<MemorySummarizeResp> triggerSummarize(Long agentId, String conversationId) {
        return post(resolvePath(ApiPathConstants.MEMORY_SUMMARIZE, agentId, conversationId), null,
                new ParameterizedTypeReference<R<MemorySummarizeResp>>() {});
    }

    /**
     * 获取梦境状态
     */
    public R<DreamingStatusResp> getDreamingStatus(Long agentId) {
        return get(resolvePath(ApiPathConstants.MEMORY_DREAMING_STATUS, agentId),
                new ParameterizedTypeReference<R<DreamingStatusResp>>() {});
    }

    /**
     * 获取梦境候选列表
     */
    public R<List<MemoryRecallResp>> getDreamingCandidates(Long agentId) {
        return get(resolvePath(ApiPathConstants.MEMORY_DREAMING_CANDIDATES, agentId),
                new ParameterizedTypeReference<R<List<MemoryRecallResp>>>() {});
    }

    /**
     * 获取梦境列表
     */
    public R<List<DreamReportResp>> getDreams(Long agentId) {
        return get(resolvePath(ApiPathConstants.MEMORY_DREAMS, agentId),
                new ParameterizedTypeReference<R<List<DreamReportResp>>>() {});
    }
}
