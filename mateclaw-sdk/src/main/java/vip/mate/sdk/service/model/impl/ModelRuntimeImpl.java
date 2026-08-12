package vip.mate.sdk.service.model.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import vip.mate.exception.MateClawException;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.*;
import vip.mate.llm.rerank.RerankModel;
import vip.mate.llm.rerank.RerankModelFactory;
import vip.mate.llm.rerank.RerankRequest;
import vip.mate.llm.rerank.RerankResult;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelDiscoveryService;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.sdk.service.model.ModelRuntime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型运行时实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRuntimeImpl implements ModelRuntime {

    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;
    private final ModelDiscoveryService modelDiscoveryService;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final RerankModelFactory rerankModelFactory;

    @Override
    public List<ProviderInfoDTO> listProviders() {
        return modelProviderService.listProviders();
    }

    @Override
    public List<ProviderInfoDTO> listProviderCatalog() {
        return modelProviderService.listCatalog();
    }

    @Override
    public EnableResult enableProvider(String providerId) {
        return modelProviderService.setEnabled(providerId, true);
    }

    @Override
    public EnableResult disableProvider(String providerId) {
        return modelProviderService.setEnabled(providerId, false);
    }

    @Override
    public ProviderInfoDTO updateProviderConfig(String providerId, ProviderConfigRequest request) {
        return modelProviderService.updateProviderConfig(providerId, request);
    }

    @Override
    public ProviderInfoDTO createCustomProvider(CreateCustomProviderRequest request) {
        return modelProviderService.createCustomProvider(request);
    }

    @Override
    public void deleteCustomProvider(String providerId) {
        modelProviderService.deleteCustomProvider(providerId);
    }

    @Override
    public ProviderInfoDTO addModelToProvider(String providerId, AddProviderModelRequest request) {
        return modelProviderService.addModel(providerId, request);
    }

    @Override
    public ProviderInfoDTO removeModelFromProvider(String providerId, String modelId) {
        return modelProviderService.removeModel(providerId, modelId);
    }

    @Override
    public List<ModelConfigEntity> listEnabledModels() {
        return modelConfigService.listEnabledModels();
    }

    @Override
    public List<ModelConfigEntity> listAllEnabledModels() {
        return modelConfigService.listModels()
                .stream()
                .filter(ModelConfigEntity::getEnabled)
                .toList();
    }

    @Override
    public List<ModelConfigEntity> listAllModels() {
        return modelConfigService.listModels();
    }

    @Override
    public ModelConfigEntity getDefaultModel() {
        return modelConfigService.getDefaultModel();
    }

    @Override
    public ActiveModelsInfo getActiveModel() {
        ModelConfigEntity model = modelConfigService.getDefaultModel();
        ActiveModelsInfo info = new ActiveModelsInfo();
        info.setActiveLlm(new ModelSlotConfig(model.getProvider(), model.getModelName()));
        return info;
    }

    @Override
    public ActiveModelsInfo setActiveModel(ModelSlotRequest request) {
        ModelConfigEntity model = modelConfigService.setDefaultModel(request.getProviderId(), request.getModel());
        ActiveModelsInfo info = new ActiveModelsInfo();
        info.setActiveLlm(new ModelSlotConfig(model.getProvider(), model.getModelName()));
        return info;
    }

    @Override
    public ModelConfigEntity getModel(Long id) {
        return modelConfigService.getModel(id);
    }

    @Override
    public ModelConfigEntity createModel(ModelConfigEntity entity) {
        return modelConfigService.createModel(entity);
    }

    @Override
    public ModelConfigEntity updateModel(ModelConfigEntity entity) {
        return modelConfigService.updateModel(entity);
    }

    @Override
    public void deleteModel(Long id) {
        modelConfigService.deleteModel(id);
    }

    @Override
    public ModelConfigEntity setDefaultModel(Long id) {
        return modelConfigService.setDefaultModel(id);
    }

    @Override
    public List<ModelConfigEntity> listModelsByType(String modelType, String modality) {
        return modelConfigService.listByType(modelType, modality);
    }

    /**
     * 获取默认向量（embedding）模型
     * <p>
     * 优先取 is_default=1 的 embedding 模型（与 chat 模型共用同一字段、各自独立），
     * 未配置时回退到第一个启用的 embedding 模型。
     */
    @Override
    public ModelConfigEntity getDefaultEmbeddingModel() {
        ModelConfigEntity marked = modelConfigService.listEnabledModels()
                .stream()
                .filter(modelConfigEntity -> modelConfigEntity.getEnabled()
                        && "embedding".equals(modelConfigEntity.getModelType()) && modelConfigEntity.getIsDefault())
                .findFirst()
                .orElse(null);

        if (marked != null) {
            return marked;
        }
        return modelConfigService.findFirstEnabledEmbedding();
    }

    @Override
    public ModelConfigEntity setDefaultEmbeddingModel(Long id) {
        ModelConfigEntity model = modelConfigService.getModel(id);
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new MateClawException("err.llm.only_enabled_default",
                    "只有启用状态的模型才能设为默认向量模型");
        }
        if (!"embedding".equals(model.getModelType())) {
            throw new MateClawException("err.llm.not_embedding_model",
                    "只有向量（embedding）类型的模型才能设为默认向量模型");
        }
        // 通过 is_default 字段设置，统一存储；setDefaultModel 内部已实现按类型分类清旗
        return modelConfigService.setDefaultModel(id);
    }

    @Override
    public DiscoverResult discoverModels(String providerId) {
        return modelDiscoveryService.discoverModels(providerId);
    }

    @Override
    public Map<String, Integer> applyDiscoveredModels(String providerId, List<String> modelIds) {
        int added = modelDiscoveryService.batchAddModels(providerId, modelIds);
        return Map.of("added", added);
    }

    @Override
    public TestResult testProviderConnection(String providerId) {
        return modelDiscoveryService.testConnection(providerId);
    }

    @Override
    public TestResult testModel(String providerId, String modelName) {
        return modelDiscoveryService.testModel(providerId, modelName);
    }

    /**
     * 测试 Embedding 模型连通性
     */
    @Override
    public Map<String, Object> testEmbeddingModel(Long modelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ModelConfigEntity config = modelConfigService.getModel(modelId);
            if (!"embedding".equals(config.getModelType())) {
                result.put("success", false);
                result.put("message", "模型类型不是 embedding: " + config.getModelType());
                return result;
            }
            // 清除缓存，确保本次测试用最新的 API key
            embeddingModelFactory.evict(modelId);
            EmbeddingModel model = embeddingModelFactory.build(config);
            EmbeddingRequest request = new EmbeddingRequest(List.of("test"), null);
            EmbeddingResponse resp = model.call(request);
            float[] vec = resp.getResults().get(0).getOutput();
            result.put("success", true);
            result.put("dimensions", vec.length);
            result.put("model", config.getModelName());
            result.put("message", "连通性测试成功");
        } catch (Exception e) {
            log.warn("[EmbeddingTest] modelId={} test failed", modelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取默认重排（rerank）模型
     * <p>
     * 优先取 is_default=1 的 rerank 模型，未配置时回退到第一个启用的 rerank 模型。
     */
    @Override
    public ModelConfigEntity getDefaultRerankModel() {
        ModelConfigEntity marked = modelConfigService.listModels()
                .stream()
                .filter(modelConfigEntity -> Boolean.TRUE.equals(modelConfigEntity.getEnabled())
                        && "rerank".equals(modelConfigEntity.getModelType())
                        && Boolean.TRUE.equals(modelConfigEntity.getIsDefault()))
                .findFirst()
                .orElse(null);
        if (marked != null) {
            return marked;
        }
        return modelConfigService.findFirstEnabledRerank();
    }

    @Override
    public ModelConfigEntity setDefaultRerankModel(Long id) {
        ModelConfigEntity model = modelConfigService.getModel(id);
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new MateClawException("err.llm.only_enabled_default",
                    "只有启用状态的模型才能设为默认重排模型");
        }
        if (!"rerank".equals(model.getModelType())) {
            throw new MateClawException("err.llm.not_rerank_model",
                    "只有重排（rerank）类型的模型才能设为默认重排模型");
        }
        // 通过 is_default 字段设置，统一存储；setDefaultModel 内部已实现按类型分类清旗
        return modelConfigService.setDefaultModel(id);
    }

    /**
     * 执行 Rerank 重排
     */
    @Override
    public List<RerankResult> rerank(Long modelId, String query, List<String> documents, Integer topN) {
        ModelConfigEntity config = modelConfigService.getModel(modelId);
        if (!"rerank".equals(config.getModelType())) {
            throw new MateClawException("err.llm.not_rerank_model",
                    "只有重排（rerank）类型的模型才能执行 Rerank");
        }
        RerankModel model = rerankModelFactory.build(config);
        RerankRequest request = new RerankRequest(query, documents, topN);
        return model.rerank(request);
    }

    /**
     * 测试 Rerank 模型连通性
     */
    @Override
    public Map<String, Object> testRerankModel(Long modelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ModelConfigEntity config = modelConfigService.getModel(modelId);
            if (!"rerank".equals(config.getModelType())) {
                result.put("success", false);
                result.put("message", "模型类型不是 rerank: " + config.getModelType());
                return result;
            }
            // 清除缓存，确保本次测试用最新的 API key
            rerankModelFactory.evict(modelId);
            RerankModel model = rerankModelFactory.build(config);
            RerankRequest request = new RerankRequest(
                    "连通性测试", List.of("测试文档一", "测试文档二"), 2);
            List<RerankResult> results = model.rerank(request);
            result.put("success", true);
            result.put("results", results);
            result.put("model", config.getModelName());
            result.put("message", "连通性测试成功");
        } catch (Exception e) {
            log.warn("[RerankTest] modelId={} test failed", modelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
