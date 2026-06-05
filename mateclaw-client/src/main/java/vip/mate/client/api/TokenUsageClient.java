package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.TokenUsageSummaryResp;

import java.util.HashMap;
import java.util.Map;

/**
 * Token 使用统计客户端
 */
public class TokenUsageClient extends AbstractApiClient {

    public TokenUsageClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 Token 使用统计摘要
     */
    public R<TokenUsageSummaryResp> getSummary(String startDate, String endDate, String modelName, String providerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("modelName", modelName);
        params.put("providerId", providerId);
        return get(ApiPathConstants.TOKEN_USAGE, params, new ParameterizedTypeReference<R<TokenUsageSummaryResp>>() {});
    }
}
