package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.PageData;
import vip.mate.client.model.R;
import vip.mate.client.model.response.AuditEventResp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件客户端
 * <p>
 * 对应服务端 /api/v1/audit 接口，提供审计事件查询功能
 */
public class AuditEventClient extends AbstractApiClient {

    public AuditEventClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 查询审计事件列表
     *
     * @param action       操作类型
     * @param resourceType 资源类型
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param page         页码
     * @param size         每页数量
     * @return 审计事件分页列表
     */
    public R<PageData<AuditEventResp>> listEvents(String action, String resourceType,
                                             String startTime, String endTime,
                                             Integer page, Integer size) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (action != null) {
            params.put("action", action);
        }
        if (resourceType != null) {
            params.put("resourceType", resourceType);
        }
        if (startTime != null) {
            params.put("startTime", startTime);
        }
        if (endTime != null) {
            params.put("endTime", endTime);
        }
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        return get(ApiPathConstants.AUDIT_EVENTS, params,
                new ParameterizedTypeReference<R<PageData<AuditEventResp>>>() {});
    }
}
