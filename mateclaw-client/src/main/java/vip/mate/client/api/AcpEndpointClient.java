package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.AcpEndpoint;
import vip.mate.client.model.R;
import vip.mate.client.model.response.AcpTestResp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ACP 端点客户端
 * <p>
 * 对应服务端 /api/v1/acp/endpoints 接口，提供 ACP 端点管理功能
 */
public class AcpEndpointClient extends AbstractApiClient {

    public AcpEndpointClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取端点列表
     *
     * @return 端点列表
     */
    public R<List<AcpEndpoint>> list() {
        return get(ApiPathConstants.ACP_ENDPOINT,
                new ParameterizedTypeReference<R<List<AcpEndpoint>>>() {});
    }

    /**
     * 获取端点详情
     *
     * @param id 端点 ID
     * @return 端点详情
     */
    public R<AcpEndpoint> get(Long id) {
        return get(resolvePath(ApiPathConstants.ACP_ENDPOINT_BY_ID, id),
                new ParameterizedTypeReference<R<AcpEndpoint>>() {});
    }

    /**
     * 创建端点
     *
     * @param body 端点信息
     * @return 创建的端点信息
     */
    public R<AcpEndpoint> create(AcpEndpoint body) {
        return post(ApiPathConstants.ACP_ENDPOINT, body,
                new ParameterizedTypeReference<R<AcpEndpoint>>() {});
    }

    /**
     * 更新端点
     *
     * @param id   端点 ID
     * @param body 端点更新信息
     * @return 更新后的端点信息
     */
    public R<AcpEndpoint> update(Long id, AcpEndpoint body) {
        return put(resolvePath(ApiPathConstants.ACP_ENDPOINT_BY_ID, id), body,
                new ParameterizedTypeReference<R<AcpEndpoint>>() {});
    }

    /**
     * 删除端点
     *
     * @param id 端点 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.ACP_ENDPOINT_BY_ID, id),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换端点启用/禁用
     *
     * @param id      端点 ID
     * @param enabled 是否启用
     * @return 更新后的端点信息
     */
    public R<AcpEndpoint> toggle(Long id, Boolean enabled) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", enabled);
        return put(buildUrl(resolvePath(ApiPathConstants.ACP_ENDPOINT_TOGGLE, id), params), null,
                new ParameterizedTypeReference<R<AcpEndpoint>>() {});
    }

    /**
     * 测试端点连接
     *
     * @param id 端点 ID
     * @return 测试结果
     */
    public R<AcpTestResp> test(Long id) {
        return post(resolvePath(ApiPathConstants.ACP_ENDPOINT_TEST, id), null,
                new ParameterizedTypeReference<R<AcpTestResp>>() {});
    }
}
