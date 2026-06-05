package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.Tool;
import vip.mate.client.model.response.AvailableToolResp;

import java.util.List;

/**
 * 工具管理客户端
 */
public class ToolClient extends AbstractApiClient {

    public ToolClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取工具列表
     */
    public R<List<Tool>> list() {
        return get(ApiPathConstants.TOOL, new ParameterizedTypeReference<R<List<Tool>>>() {});
    }

    /**
     * 获取已启用的工具列表
     */
    public R<List<Tool>> listEnabled() {
        return get(ApiPathConstants.TOOL_ENABLED, new ParameterizedTypeReference<R<List<Tool>>>() {});
    }

    /**
     * 获取可用工具列表
     */
    public R<List<AvailableToolResp>> listAvailable() {
        return get(ApiPathConstants.TOOL_AVAILABLE, new ParameterizedTypeReference<R<List<AvailableToolResp>>>() {});
    }

    /**
     * 获取工具详情
     */
    public R<Tool> get(Long id) {
        return get(resolvePath(ApiPathConstants.TOOL_BY_ID, id), new ParameterizedTypeReference<R<Tool>>() {});
    }

    /**
     * 创建工具
     */
    public R<Tool> create(Tool tool) {
        return post(ApiPathConstants.TOOL, tool, new ParameterizedTypeReference<R<Tool>>() {});
    }

    /**
     * 更新工具
     */
    public R<Tool> update(Long id, Tool tool) {
        return put(resolvePath(ApiPathConstants.TOOL_BY_ID, id), tool,
                new ParameterizedTypeReference<R<Tool>>() {});
    }

    /**
     * 删除工具
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.TOOL_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换工具启用/禁用状态
     */
    public R<Tool> toggle(Long id, boolean enabled) {
        return put(resolvePath(ApiPathConstants.TOOL_TOGGLE, id) + "?enabled=" + enabled, null,
                new ParameterizedTypeReference<R<Tool>>() {});
    }

    /**
     * 设置工具披露层级
     */
    public R<Tool> setDisclosureTier(Long id, String tier) {
        return put(resolvePath(ApiPathConstants.TOOL_DISCLOSURE_TIER, id), tier,
                new ParameterizedTypeReference<R<Tool>>() {});
    }
}
