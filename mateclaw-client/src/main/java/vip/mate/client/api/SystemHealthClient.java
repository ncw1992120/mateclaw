package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.BrowserHealthResp;
import vip.mate.client.model.response.HealthResp;

/**
 * 系统健康客户端
 * <p>
 * 对应服务端 /api/v1/system 接口，提供系统健康检查和浏览器诊断功能
 */
public class SystemHealthClient extends AbstractApiClient {

    public SystemHealthClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 系统健康检查
     *
     * @return 健康状态
     */
    public R<HealthResp> getHealth() {
        return get(ApiPathConstants.SYSTEM_HEALTH,
                new ParameterizedTypeReference<R<HealthResp>>() {});
    }

    /**
     * 浏览器健康诊断
     *
     * @return 诊断报告
     */
    public R<BrowserHealthResp> getBrowserHealth() {
        return get(ApiPathConstants.SYSTEM_BROWSER_HEALTH,
                new ParameterizedTypeReference<R<BrowserHealthResp>>() {});
    }
}
