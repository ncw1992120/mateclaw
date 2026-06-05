package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.ActivityFeedDataResp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动流客户端
 * <p>
 * 对应服务端 /api/v1/activity 接口，提供活动流查询功能
 */
public class ActivityFeedClient extends AbstractApiClient {

    public ActivityFeedClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取活动流
     *
     * @param workspaceId 工作空间 ID
     * @param source      来源
     * @param page        页码
     * @param size        每页数量
     * @return 活动流数据
     */
    public R<ActivityFeedDataResp> feed(Long workspaceId, String source, Integer page, Integer size) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (workspaceId != null) {
            params.put("workspaceId", workspaceId);
        }
        if (source != null) {
            params.put("source", source);
        }
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        return get(ApiPathConstants.ACTIVITY_FEED, params,
                new ParameterizedTypeReference<R<ActivityFeedDataResp>>() {});
    }
}
