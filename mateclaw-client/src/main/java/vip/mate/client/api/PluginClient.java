package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.response.PluginInfoResp;

import java.util.List;
import java.util.Map;

/**
 * 插件客户端
 * <p>
 * 对应服务端 /api/v1/plugins 接口，提供插件管理功能
 */
public class PluginClient extends AbstractApiClient {

    public PluginClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取插件列表
     *
     * @return 插件列表
     */
    public R<List<PluginInfoResp>> list() {
        return get(ApiPathConstants.PLUGIN, new ParameterizedTypeReference<R<List<PluginInfoResp>>>() {});
    }

    /**
     * 获取插件详情
     *
     * @param name 插件名称
     * @return 插件详情
     */
    public R<PluginInfoResp> get(String name) {
        return get(resolvePath(ApiPathConstants.PLUGIN_BY_NAME, name),
                new ParameterizedTypeReference<R<PluginInfoResp>>() {});
    }

    /**
     * 禁用插件
     *
     * @param name 插件名称
     * @return 操作结果
     */
    public R<Void> disable(String name) {
        return post(resolvePath(ApiPathConstants.PLUGIN_DISABLE, name), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 启用插件
     *
     * @param name 插件名称
     * @return 操作结果
     */
    public R<Void> enable(String name) {
        return post(resolvePath(ApiPathConstants.PLUGIN_ENABLE, name), null,
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 更新插件配置
     *
     * @param name   插件名称
     * @param config 插件配置键值对
     * @return 操作结果
     */
    public R<Void> updateConfig(String name, Map<String, Object> config) {
        return put(resolvePath(ApiPathConstants.PLUGIN_CONFIG, name), config,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
