package vip.mate.sdk.service.model;

import com.baomidou.mybatisplus.core.metadata.IPage;
import vip.mate.llm.model.*;

import java.util.List;
import java.util.Map;

/**
 * 模型运行时接口
 * <p>
 * 提供 Provider/模型管理、发现、测试等编程式访问能力。
 */
public interface ModelRuntime {

    /**
     * 获取启用的 Provider 列表
     *
     * @return Provider 信息列表
     */
    List<ProviderInfoDTO> listProviders();

    /**
     * 获取 Provider 全量目录（含未启用）
     *
     * @return Provider 信息列表
     */
    List<ProviderInfoDTO> listProviderCatalog();

    /**
     * 启用 Provider
     *
     * @param providerId Provider ID
     * @return 启用结果
     */
    EnableResult enableProvider(String providerId);

    /**
     * 禁用 Provider
     *
     * @param providerId Provider ID
     * @return 禁用结果（含默认模型切换信息）
     */
    EnableResult disableProvider(String providerId);

    /**
     * 更新 Provider 配置
     *
     * @param providerId Provider ID
     * @param request    配置请求
     * @return 更新后的 Provider 信息
     */
    ProviderInfoDTO updateProviderConfig(String providerId, ProviderConfigRequest request);

    /**
     * 创建自定义 Provider
     *
     * @param request 创建请求
     * @return 创建后的 Provider 信息
     */
    ProviderInfoDTO createCustomProvider(CreateCustomProviderRequest request);

    /**
     * 删除自定义 Provider
     *
     * @param providerId Provider ID
     */
    void deleteCustomProvider(String providerId);

    /**
     * 向 Provider 添加模型
     *
     * @param providerId Provider ID
     * @param request    添加模型请求
     * @return 更新后的 Provider 信息
     */
    ProviderInfoDTO addModelToProvider(String providerId, AddProviderModelRequest request);

    /**
     * 从 Provider 删除模型
     *
     * @param providerId Provider ID
     * @param modelId    模型标识
     * @return 更新后的 Provider 信息
     */
    ProviderInfoDTO removeModelFromProvider(String providerId, String modelId);

    /**
     * 获取启用模型列表
     *
     * @return 模型配置实体列表
     */
    List<ModelConfigEntity> listEnabledModels();

    /**
     * 获取所有已启用的模型（含 chat 和 embedding 类型）
     *
     * @return 所有已启用的模型配置实体列表
     */
    List<ModelConfigEntity> listAllEnabledModels();

    /**
     * 获取所有模型（含启用和禁用）
     *
     * @return 所有模型配置实体列表
     */
    List<ModelConfigEntity> listAllModels();

    /**
     * 获取默认模型
     *
     * @return 默认模型配置实体
     */
    ModelConfigEntity getDefaultModel();

    /**
     * 获取当前激活模型
     *
     * @return 激活模型信息
     */
    ActiveModelsInfo getActiveModel();

    /**
     * 设置当前激活模型
     *
     * @param request 模型槽位请求
     * @return 更新后的激活模型信息
     */
    ActiveModelsInfo setActiveModel(ModelSlotRequest request);

    /**
     * 获取模型详情
     *
     * @param id 模型 ID
     * @return 模型配置实体
     */
    ModelConfigEntity getModel(Long id);

    /**
     * 创建模型
     *
     * @param entity 模型配置实体
     * @return 创建后的模型配置实体
     */
    ModelConfigEntity createModel(ModelConfigEntity entity);

    /**
     * 更新模型
     *
     * @param entity 模型配置实体（需包含 ID）
     * @return 更新后的模型配置实体
     */
    ModelConfigEntity updateModel(ModelConfigEntity entity);

    /**
     * 删除模型
     *
     * @param id 模型 ID
     */
    void deleteModel(Long id);

    /**
     * 设置默认模型
     *
     * @param id 模型 ID
     * @return 更新后的模型配置实体
     */
    ModelConfigEntity setDefaultModel(Long id);

    /**
     * 按类型筛选模型
     *
     * @param modelType 模型类型
     * @param modality  模态过滤（可选）
     * @return 模型配置实体列表
     */
    List<ModelConfigEntity> listModelsByType(String modelType, String modality);

    /**
     * 获取默认向量（embedding）模型
     *
     * @return 默认向量模型配置实体，未配置时返回 null
     */
    ModelConfigEntity getDefaultEmbeddingModel();

    /**
     * 设置默认向量（embedding）模型
     *
     * @param id 模型 ID
     * @return 更新后的模型配置实体
     */
    ModelConfigEntity setDefaultEmbeddingModel(Long id);

    /**
     * 发现远端模型
     *
     * @param providerId Provider ID
     * @return 发现结果
     */
    DiscoverResult discoverModels(String providerId);

    /**
     * 批量添加发现的模型
     *
     * @param providerId Provider ID
     * @param modelIds   模型 ID 列表
     * @return 添加数量统计
     */
    Map<String, Integer> applyDiscoveredModels(String providerId, List<String> modelIds);

    /**
     * 测试供应商连接
     *
     * @param providerId Provider ID
     * @return 测试结果
     */
    TestResult testProviderConnection(String providerId);

    /**
     * 测试单个模型可用性
     *
     * @param providerId Provider ID
     * @param modelName  模型名称（如 gpt-4o）
     * @return 测试结果
     */
    TestResult testModel(String providerId, String modelName);

    /**
     * 测试 Embedding 模型连通性
     *
     * @param modelId 模型 ID
     * @return 测试结果（含 success、dimensions、model、message 等字段）
     */
    Map<String, Object> testEmbeddingModel(Long modelId);
}
