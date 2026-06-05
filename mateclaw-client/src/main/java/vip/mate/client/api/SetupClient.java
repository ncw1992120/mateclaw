package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.request.SetupInitReq;
import vip.mate.client.model.response.OnboardingStatusResp;
import vip.mate.client.model.response.SetupStatusResp;

/**
 * 初始化设置客户端
 */
public class SetupClient extends AbstractApiClient {

    public SetupClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取初始化状态
     *
     * @return 初始化状态
     */
    public R<SetupStatusResp> getStatus() {
        return get(ApiPathConstants.SETUP_STATUS, new ParameterizedTypeReference<R<SetupStatusResp>>() {});
    }

    /**
     * 执行初始化
     *
     * @param request 初始化请求
     * @return 初始化结果
     */
    public R<String> init(SetupInitReq request) {
        return post(ApiPathConstants.SETUP_INIT, request,
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 获取引导状态
     *
     * @return 引导状态
     */
    public R<OnboardingStatusResp> getOnboardingStatus() {
        return get(ApiPathConstants.SETUP_ONBOARDING_STATUS,
                new ParameterizedTypeReference<R<OnboardingStatusResp>>() {});
    }
}
