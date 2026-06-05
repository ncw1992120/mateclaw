package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.ModelConfig;
import vip.mate.client.model.R;
import vip.mate.client.model.request.*;
import vip.mate.client.model.response.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置管理客户端
 */
public class ModelConfigClient extends AbstractApiClient {

    public ModelConfigClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取模型列表
     */
    public R<List<ModelConfig>> list() {
        return get(ApiPathConstants.MODEL, new ParameterizedTypeReference<R<List<ModelConfig>>>() {});
    }

    /**
     * 获取模型目录
     */
    public R<List<ProviderInfoResp>> catalog() {
        return get(ApiPathConstants.MODEL_CATALOG, new ParameterizedTypeReference<R<List<ProviderInfoResp>>>() {});
    }

    /**
     * 启用 Provider
     */
    public R<ProviderEnableResp> enableProvider(String providerId) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_ENABLE, providerId), null,
                new ParameterizedTypeReference<R<ProviderEnableResp>>() {});
    }

    /**
     * 禁用 Provider
     */
    public R<ProviderEnableResp> disableProvider(String providerId) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_DISABLE, providerId), null,
                new ParameterizedTypeReference<R<ProviderEnableResp>>() {});
    }

    /**
     * 获取已启用的 Provider 列表
     */
    public R<List<ModelConfig>> listEnabled() {
        return get(ApiPathConstants.MODEL_ENABLED, new ParameterizedTypeReference<R<List<ModelConfig>>>() {});
    }

    /**
     * 获取默认模型
     */
    public R<ModelConfig> getDefaultModel() {
        return get(ApiPathConstants.MODEL_DEFAULT, new ParameterizedTypeReference<R<ModelConfig>>() {});
    }

    /**
     * 获取当前活跃模型
     */
    public R<ActiveModelInfoResp> getActiveModel() {
        return get(ApiPathConstants.MODEL_ACTIVE, new ParameterizedTypeReference<R<ActiveModelInfoResp>>() {});
    }

    /**
     * 设置当前活跃模型
     */
    public R<ActiveModelInfoResp> setActiveModel(ModelSlotConfigReq body) {
        return put(ApiPathConstants.MODEL_ACTIVE, body,
                new ParameterizedTypeReference<R<ActiveModelInfoResp>>() {});
    }

    /**
     * 更新 Provider 配置
     */
    public R<ProviderInfoResp> updateProviderConfig(String providerId, ProviderConfigReq config) {
        return put(resolvePath(ApiPathConstants.MODEL_PROVIDER_CONFIG, providerId), config,
                new ParameterizedTypeReference<R<ProviderInfoResp>>() {});
    }

    /**
     * 创建自定义 Provider
     */
    public R<ProviderInfoResp> createCustomProvider(CustomProviderReq body) {
        return post(ApiPathConstants.MODEL_CUSTOM_PROVIDER, body,
                new ParameterizedTypeReference<R<ProviderInfoResp>>() {});
    }

    /**
     * 删除自定义 Provider
     */
    public R<Void> deleteCustomProvider(String providerId) {
        return delete(resolvePath(ApiPathConstants.MODEL_CUSTOM_PROVIDER_BY_ID, providerId),
                new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 添加 Provider 模型
     */
    public R<ProviderInfoResp> addProviderModel(String providerId, AddProviderModelReq body) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_MODELS, providerId), body,
                new ParameterizedTypeReference<R<ProviderInfoResp>>() {});
    }

    /**
     * 移除 Provider 模型
     */
    public R<ProviderInfoResp> removeProviderModel(String providerId, String modelId) {
        return delete(resolvePath(ApiPathConstants.MODEL_PROVIDER_MODELS, providerId) + "?modelId=" + modelId,
                new ParameterizedTypeReference<R<ProviderInfoResp>>() {});
    }

    /**
     * 获取模型详情（按模型 ID）
     */
    public R<ModelConfig> get(Long id) {
        return get(resolvePath(ApiPathConstants.MODEL_BY_ID, id), new ParameterizedTypeReference<R<ModelConfig>>() {});
    }

    /**
     * 创建模型
     */
    public R<ModelConfig> create(ModelConfig entity) {
        return post(ApiPathConstants.MODEL, entity, new ParameterizedTypeReference<R<ModelConfig>>() {});
    }

    /**
     * 更新模型
     */
    public R<ModelConfig> update(Long id, ModelConfig entity) {
        return put(resolvePath(ApiPathConstants.MODEL_BY_ID, id), entity,
                new ParameterizedTypeReference<R<ModelConfig>>() {});
    }

    /**
     * 删除模型
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.MODEL_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 设置默认模型
     */
    public R<ModelConfig> setDefault(Long id) {
        return post(resolvePath(ApiPathConstants.MODEL_SET_DEFAULT, id), null,
                new ParameterizedTypeReference<R<ModelConfig>>() {});
    }

    /**
     * 发现 Provider 模型
     */
    public R<DiscoverResp> discoverModels(String providerId) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_DISCOVER, providerId), null,
                new ParameterizedTypeReference<R<DiscoverResp>>() {});
    }

    /**
     * 应用发现的模型
     *
     * @param providerId Provider ID
     * @param request    应用发现的模型请求
     * @return 应用的模型数量
     */
    public R<Integer> applyDiscoveredModels(String providerId, ApplyDiscoveredModelsReq request) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_DISCOVER_APPLY, providerId), request,
                new ParameterizedTypeReference<R<Integer>>() {});
    }

    /**
     * 测试 Provider 连接
     */
    public R<ModelTestResp> testConnection(String providerId) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_TEST, providerId), null,
                new ParameterizedTypeReference<R<ModelTestResp>>() {});
    }

    /**
     * 测试模型
     */
    public R<ModelTestResp> testModel(String providerId, String modelId) {
        return post(resolvePath(ApiPathConstants.MODEL_PROVIDER_MODEL_TEST, providerId) + "?modelId=" + modelId, null,
                new ParameterizedTypeReference<R<ModelTestResp>>() {});
    }

    /**
     * 按类型获取模型列表
     */
    public R<List<ModelConfig>> listByType(String modelType, String modality) {
        Map<String, Object> params = new HashMap<>();
        params.put("modelType", modelType);
        params.put("modality", modality);
        return get(ApiPathConstants.MODEL_BY_TYPE, params, new ParameterizedTypeReference<R<List<ModelConfig>>>() {});
    }

    /**
     * 测试嵌入模型
     */
    public R<EmbeddingTestResp> testEmbedding(Long modelId) {
        return post(resolvePath(ApiPathConstants.MODEL_EMBEDDING_TEST, modelId), null,
                new ParameterizedTypeReference<R<EmbeddingTestResp>>() {});
    }

    /**
     * 获取默认嵌入模型
     */
    public R<DefaultEmbeddingInfoResp> getDefaultEmbedding() {
        return get(ApiPathConstants.MODEL_EMBEDDING_DEFAULT,
                new ParameterizedTypeReference<R<DefaultEmbeddingInfoResp>>() {});
    }

    /**
     * 设置默认嵌入模型
     *
     * @param modelId 嵌入模型 ID
     * @return 操作结果
     */
    public R<Void> setDefaultEmbedding(Object modelId) {
        Map<String, Object> body = new HashMap<>();
        body.put("modelId", modelId);
        return post(ApiPathConstants.MODEL_EMBEDDING_DEFAULT, body,
                new ParameterizedTypeReference<R<Void>>() {});
    }
}
