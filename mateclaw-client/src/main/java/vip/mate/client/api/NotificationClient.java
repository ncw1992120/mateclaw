package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.NotificationSummaryResp;

/**
 * 通知客户端
 * <p>
 * 对应服务端 /api/v1/notifications 接口，提供通知摘要功能
 */
public class NotificationClient extends AbstractApiClient {

    public NotificationClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取通知摘要（聚合徽章计数）
     *
     * @return 通知摘要
     */
    public R<NotificationSummaryResp> summary() {
        return get(ApiPathConstants.NOTIFICATION_SUMMARY,
                new ParameterizedTypeReference<R<NotificationSummaryResp>>() {});
    }
}
