package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.DreamEditEntryReq;
import vip.mate.client.model.request.MorningCardSeenReq;
import vip.mate.client.model.response.DreamReportPageResp;
import vip.mate.client.model.response.DreamReportResp;
import vip.mate.client.model.response.MorningCardResp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dream 梦境客户端
 * <p>
 * 对应服务端 /api/v1/memory/{agentId}/dream 接口，提供梦境报告、Morning Card 等功能
 */
public class DreamClient extends AbstractApiClient {

    public DreamClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取梦境报告列表
     *
     * @param agentId Agent ID
     * @param page    页码（默认 1）
     * @param size    每页数量（默认 20）
     * @return 报告分页列表
     */
    public R<DreamReportPageResp> listReports(Long agentId, Integer page, Integer size) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        return get(resolvePath(ApiPathConstants.DREAM_REPORTS, agentId), params,
                new ParameterizedTypeReference<R<DreamReportPageResp>>() {});
    }

    /**
     * 获取梦境报告详情
     *
     * @param agentId   Agent ID
     * @param reportId  报告 ID
     * @return 报告详情
     */
    public R<DreamReportResp> getReport(Long agentId, Long reportId) {
        return get(resolvePath(ApiPathConstants.DREAM_REPORT_BY_ID, agentId, reportId),
                new ParameterizedTypeReference<R<DreamReportResp>>() {});
    }

    /**
     * 订阅梦境事件 SSE 流
     *
     * @param agentId  Agent ID
     * @param callback SSE 事件回调
     */
    public void subscribeDreamEvents(Long agentId, SseStreamCallback callback) {
        getForSseStream(resolvePath(ApiPathConstants.DREAM_EVENTS, agentId), callback);
    }

    /**
     * 获取 Morning Card
     *
     * @param agentId Agent ID
     * @return Morning Card 内容
     */
    public R<MorningCardResp> getMorningCard(Long agentId) {
        return get(resolvePath(ApiPathConstants.DREAM_MORNING_CARD, agentId),
                new ParameterizedTypeReference<R<MorningCardResp>>() {});
    }

    /**
     * 标记晨报已读
     *
     * @param agentId Agent ID
     * @param request 已读标记请求
     * @return 操作结果
     */
    public R<Void> markMorningCardSeen(Long agentId, MorningCardSeenReq request) {
        return post(resolvePath(ApiPathConstants.DREAM_MORNING_CARD_SEEN, agentId), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 确认梦境记忆条目
     *
     * @param agentId   Agent ID
     * @param reportId  报告 ID
     * @param key       条目 Key
     * @return 操作结果
     */
    public R<Void> confirmEntry(Long agentId, Long reportId, String key) {
        return post(resolvePath(ApiPathConstants.DREAM_ENTRY_CONFIRM, agentId, reportId, key), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 编辑晨报条目
     *
     * @param agentId  Agent ID
     * @param reportId 报告 ID
     * @param key      条目键
     * @param request  编辑请求
     * @return 操作结果
     */
    public R<Void> editEntry(Long agentId, Long reportId, String key, DreamEditEntryReq request) {
        return post(resolvePath(ApiPathConstants.DREAM_ENTRY_EDIT, agentId, reportId, key), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
