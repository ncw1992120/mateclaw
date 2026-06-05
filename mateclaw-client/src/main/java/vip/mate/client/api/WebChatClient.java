package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.ChatReq;
import vip.mate.client.model.response.WebChatConfigResp;

/**
 * WebChat 渠道对话客户端
 * <p>
 * 对应服务端 /api/v1/channels/webchat 接口，提供 API Key 认证的流式对话功能
 */
public class WebChatClient extends AbstractApiClient {

    public WebChatClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * SSE 流式对话
     * <p>
     * 通过回调处理流式事件，使用 X-MC-Key 头认证
     *
     * @param request  对话请求参数
     * @param callback SSE 事件回调
     */
    public void chatStream(ChatReq request, SseStreamCallback callback) {
        postForSseStream(ApiPathConstants.WEBCHAT_STREAM, request, callback);
    }

    /**
     * 获取配置
     *
     * @return 配置信息
     */
    public R<WebChatConfigResp> getConfig() {
        return get(ApiPathConstants.WEBCHAT_CONFIG, new ParameterizedTypeReference<R<WebChatConfigResp>>() {});
    }
}
