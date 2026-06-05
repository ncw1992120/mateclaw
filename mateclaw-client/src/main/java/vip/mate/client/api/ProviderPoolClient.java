package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.ProviderInfoResp;

import java.util.List;

/**
 * Provider 可用池客户端
 */
public class ProviderPoolClient extends AbstractApiClient {

    public ProviderPoolClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Provider 池快照
     */
    public R<List<ProviderInfoResp>> snapshot() {
        return get(ApiPathConstants.PROVIDER_POOL, new ParameterizedTypeReference<R<List<ProviderInfoResp>>>() {});
    }

    /**
     * 重新探测 Provider
     */
    public R<ProviderInfoResp> reprobe(String providerId) {
        return post(resolvePath(ApiPathConstants.PROVIDER_POOL_REPROBE, providerId), null,
                new ParameterizedTypeReference<R<ProviderInfoResp>>() {});
    }
}
