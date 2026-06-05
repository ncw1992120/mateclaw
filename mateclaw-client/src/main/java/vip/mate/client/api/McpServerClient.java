package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.McpServer;
import vip.mate.client.model.R;
import vip.mate.client.model.response.McpConnectionResp;
import vip.mate.client.model.response.McpToolDescriptorResp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理客户端
 */
public class McpServerClient extends AbstractApiClient {

    public McpServerClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取 MCP Server 列表
     */
    public R<List<McpServer>> list() {
        return get(ApiPathConstants.MCP_SERVER, new ParameterizedTypeReference<R<List<McpServer>>>() {});
    }

    /**
     * 获取 MCP Server 详情
     */
    public R<McpServer> get(Long id) {
        return get(resolvePath(ApiPathConstants.MCP_SERVER_BY_ID, id), new ParameterizedTypeReference<R<McpServer>>() {});
    }

    /**
     * 创建 MCP Server
     */
    public R<McpServer> create(McpServer entity) {
        return post(ApiPathConstants.MCP_SERVER, entity, new ParameterizedTypeReference<R<McpServer>>() {});
    }

    /**
     * 更新 MCP Server
     */
    public R<McpServer> update(Long id, McpServer entity) {
        return put(resolvePath(ApiPathConstants.MCP_SERVER_BY_ID, id), entity, new ParameterizedTypeReference<R<McpServer>>() {});
    }

    /**
     * 删除 MCP Server
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.MCP_SERVER_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换 MCP Server 启用/禁用状态
     */
    public R<McpServer> toggle(Long id, boolean enabled) {
        return put(resolvePath(ApiPathConstants.MCP_SERVER_TOGGLE, id) + "?enabled=" + enabled, null,
                new ParameterizedTypeReference<R<McpServer>>() {});
    }

    /**
     * 设置 MCP Server 披露层级
     *
     * @param id   MCP Server ID
     * @param tier 披露层级
     * @return 更新后的 MCP Server
     */
    public R<McpServer> setDisclosureTier(Long id, String tier) {
        Map<String, String> body = new HashMap<>();
        body.put("tier", tier);
        return put(resolvePath(ApiPathConstants.MCP_SERVER_DISCLOSURE_TIER, id), body,
                new ParameterizedTypeReference<R<McpServer>>() {});
    }

    /**
     * 测试 MCP Server 连接
     */
    public R<McpConnectionResp> test(Long id) {
        return post(resolvePath(ApiPathConstants.MCP_SERVER_TEST, id), null,
                new ParameterizedTypeReference<R<McpConnectionResp>>() {});
    }

    /**
     * 获取 MCP Server 工具列表
     */
    public R<List<McpToolDescriptorResp>> listTools(Long id) {
        return get(resolvePath(ApiPathConstants.MCP_SERVER_TOOLS, id),
                new ParameterizedTypeReference<R<List<McpToolDescriptorResp>>>() {});
    }

    /**
     * 刷新 MCP Server
     */
    public R<Void> refresh() {
        return post(ApiPathConstants.MCP_SERVER_REFRESH, null, new ParameterizedTypeReference<R<Void>>() {});
    }
}
