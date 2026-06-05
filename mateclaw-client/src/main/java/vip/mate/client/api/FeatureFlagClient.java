package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.FeatureFlagUpdateReq;
import vip.mate.client.model.response.FeatureFlagResp;

import java.util.List;

/**
 * 功能开关客户端
 * <p>
 * 对应服务端 /api/v1/feature-flags 接口，提供功能开关管理功能
 */
public class FeatureFlagClient extends AbstractApiClient {

    public FeatureFlagClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取功能开关列表
     *
     * @return 功能开关列表
     */
    public R<List<FeatureFlagResp>> list() {
        return get(ApiPathConstants.FEATURE_FLAG,
                new ParameterizedTypeReference<R<List<FeatureFlagResp>>>() {});
    }

    /**
     * 更新功能开关
     *
     * @param flagKey 开关 Key
     * @param request 更新请求
     * @return 操作结果
     */
    public R<Void> update(String flagKey, FeatureFlagUpdateReq request) {
        return put(resolvePath(ApiPathConstants.FEATURE_FLAG_BY_KEY, flagKey), request,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
