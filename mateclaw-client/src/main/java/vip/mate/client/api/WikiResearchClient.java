package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.ResearchStartReq;
import vip.mate.client.model.response.ResearchStartResp;

/**
 * Wiki 研究客户端
 * <p>
 * 对应服务端 /api/v1/wiki/research 接口，提供知识库研究功能
 */
public class WikiResearchClient extends AbstractApiClient {

    public WikiResearchClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 开始研究
     *
     * @param request 研究请求参数
     * @return 研究会话信息
     */
    public R<ResearchStartResp> startResearch(ResearchStartReq request) {
        return post(ApiPathConstants.WIKI_RESEARCH_START, request,
                new ParameterizedTypeReference<R<ResearchStartResp>>() {});
    }

    /**
     * 订阅研究 SSE 流
     *
     * @param sessionId 研究会话 ID
     * @param callback  SSE 事件回调
     */
    public void stream(String sessionId, SseStreamCallback callback) {
        getForSseStream(resolvePath(ApiPathConstants.WIKI_RESEARCH_STREAM, sessionId), callback);
    }
}
