package vip.mate.sdk.service;

import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.llm.model.ActiveModelsInfo;
import vip.mate.llm.model.DiscoverResult;
import vip.mate.llm.model.EnableResult;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelSlotRequest;
import vip.mate.llm.model.ProviderConfigRequest;
import vip.mate.llm.model.ProviderInfoDTO;
import vip.mate.llm.model.TestResult;
import vip.mate.llm.model.CreateCustomProviderRequest;
import vip.mate.llm.model.AddProviderModelRequest;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * MateClaw 嵌入式运行时接口
 * <p>
 * 提供对 MateClaw 核心业务能力的编程式访问，包括 Agent 对话、
 * 模板应用、数据源管理、工具注册等功能。
 * 宿主应用通过注入此接口即可使用 MateClaw 的全部能力，
 * 无需直接依赖内部服务实现。
 */
public interface MateClawRuntime {

    /**
     * 与指定 Agent 进行结构化流式对话
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId);

    /**
     * 与指定 Agent 进行结构化流式对话（完整参数）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param requesterId    请求者 ID
     * @param thinkingLevel  思考深度级别
     * @param origin         对话来源上下文
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                           String requesterId, String thinkingLevel, ChatOrigin origin);

    /**
     * 与指定 Agent 进行结构化流式对话（指定模型名称）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param modelName      模型名称（覆盖 Agent 默认模型）
     * @return 结构化流式响应
     */
    Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                           String modelName);

    /**
     * 应用模板创建 Agent
     *
     * @param templateId  模板 ID
     * @param workspaceId 工作区 ID
     * @param userId      创建者用户 ID
     * @return 创建的 Agent 实体
     */
    AgentEntity applyTemplate(String templateId, Long workspaceId, Long userId);

    /**
     * 列出所有数据源
     *
     * @return 数据源实体列表
     */
    List<DatasourceEntity> listDatasources();

    /**
     * 根据 ID 获取数据源
     *
     * @param id 数据源 ID
     * @return 数据源实体
     */
    DatasourceEntity getDatasource(Long id);

    /**
     * 测试数据源连接
     *
     * @param id 数据源 ID
     * @return 连接是否成功
     */
    boolean testDatasourceConnection(Long id);

    /**
     * 创建数据源
     *
     * @param entity 数据源实体
     * @return 创建后的数据源实体
     */
    DatasourceEntity createDatasource(DatasourceEntity entity);

    /**
     * 更新数据源
     *
     * @param entity 数据源实体（需包含 ID）
     * @return 更新后的数据源实体
     */
    DatasourceEntity updateDatasource(DatasourceEntity entity);

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     */
    void deleteDatasource(Long id);

    /**
     * 切换数据源启停状态
     *
     * @param id      数据源 ID
     * @param enabled 是否启用
     * @return 更新后的数据源实体
     */
    DatasourceEntity toggleDatasource(Long id, boolean enabled);

    /**
     * 注册插件工具
     *
     * @param tool 工具回调实例
     */
    void registerTool(ToolCallback tool);

    /**
     * 按工作区列出 Agent
     *
     * @param workspaceId 工作区 ID
     * @param enabled     是否仅列出已启用的 Agent，null 表示不过滤
     * @return Agent 实体列表
     */
    List<AgentEntity> listAgentsByWorkspace(Long workspaceId, Boolean enabled);

    /**
     * 根据 ID 获取 Agent
     *
     * @param id Agent ID
     * @return Agent 实体
     */
    AgentEntity getAgent(Long id);

    /**
     * 创建 Agent
     *
     * @param agent Agent 实体
     * @return 创建后的 Agent 实体
     */
    AgentEntity createAgent(AgentEntity agent);

    /**
     * 更新 Agent
     *
     * @param agent Agent 实体（需包含 ID）
     * @return 更新后的 Agent 实体
     */
    AgentEntity updateAgent(AgentEntity agent);

    /**
     * 删除 Agent
     *
     * @param id Agent ID
     */
    void deleteAgent(Long id);

    /**
     * 获取所有已启用的工具
     *
     * @return 工具 Bean 列表
     */
    List<Object> getEnabledTools();

    // ==================== 模型配置 ====================

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
     * @param modelId    模型标识
     * @return 测试结果
     */
    TestResult testModel(String providerId, String modelId);
}
