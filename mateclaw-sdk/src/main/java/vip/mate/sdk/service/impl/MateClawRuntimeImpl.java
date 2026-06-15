package vip.mate.sdk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.service.TemplateService;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.service.DatasourceService;
import vip.mate.exception.MateClawException;
import vip.mate.llm.model.*;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelDiscoveryService;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.service.SkillService;
import vip.mate.system.service.SystemSettingService;
import vip.mate.tool.ToolRegistry;
import vip.mate.tool.model.ToolEntity;
import vip.mate.tool.repository.ToolMapper;

import java.util.List;
import java.util.Map;

/**
 * MateClaw 嵌入式运行时实现
 * <p>
 * 将所有方法委托给 MateClaw 内部服务实现，
 * 为宿主应用提供统一的编程式访问入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MateClawRuntimeImpl implements MateClawRuntime {

    private final AgentService agentService;
    private final DatasourceService datasourceService;
    private final TemplateService templateService;
    private final ToolRegistry toolRegistry;
    private final ToolMapper toolMapper;
    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;
    private final ModelDiscoveryService modelDiscoveryService;
    private final SkillService skillService;
    private final SystemSettingService systemSettingService;

    /**
     * 与指定 Agent 进行结构化流式对话
     */
    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId) {
        return agentService.chatStructuredStream(agentId, message, conversationId);
    }

    /**
     * 与指定 Agent 进行结构化流式对话（完整参数）
     */
    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String requesterId, String thinkingLevel, ChatOrigin origin) {
        return agentService.chatStructuredStream(agentId, message, conversationId,
                requesterId, thinkingLevel, origin);
    }

    /**
     * 与指定 Agent 进行结构化流式对话（指定模型名称）
     * <p>
     * 将 modelName 写入 Agent 实体后刷新缓存，使 Agent 使用指定模型重建。
     */
    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            AgentEntity agent = agentService.getAgent(agentId);
            agent.setModelName(modelName);
            agentService.updateAgent(agent);
            agentService.refreshAgent(agentId);
        }
        return agentService.chatStructuredStream(agentId, message, conversationId);
    }

    /**
     * 应用模板创建 Agent
     */
    @Override
    public AgentEntity applyTemplate(String templateId, Long workspaceId, Long userId) {
        return templateService.applyTemplate(templateId, workspaceId, userId);
    }

    /**
     * 列出所有数据源
     */
    @Override
    public List<DatasourceEntity> listDatasources() {
        return datasourceService.listAll();
    }

    /**
     * 根据 ID 获取数据源
     */
    @Override
    public DatasourceEntity getDatasource(Long id) {
        return datasourceService.getById(id);
    }

    /**
     * 测试数据源连接
     */
    @Override
    public boolean testDatasourceConnection(Long id) {
        return datasourceService.testConnection(id);
    }

    /**
     * 创建数据源
     */
    @Override
    public DatasourceEntity createDatasource(DatasourceEntity entity) {
        return datasourceService.create(entity);
    }

    /**
     * 更新数据源
     */
    @Override
    public DatasourceEntity updateDatasource(DatasourceEntity entity) {
        return datasourceService.update(entity);
    }

    /**
     * 删除数据源
     */
    @Override
    public void deleteDatasource(Long id) {
        datasourceService.delete(id);
    }

    /**
     * 切换数据源启停状态
     */
    @Override
    public DatasourceEntity toggleDatasource(Long id, boolean enabled) {
        return datasourceService.toggle(id, enabled);
    }

    /**
     * 注册插件工具，可用性检查默认始终返回 true
     */
    @Override
    public void registerTool(ToolCallback tool) {
        toolRegistry.registerPluginTool(tool, () -> true);
    }

    /**
     * 按 Spring Bean 名称禁用内置 @Tool Bean。
     * <p>
     * 通过向 mate_tool 表写入或更新一条 enabled=false 的记录，使 ToolRegistry 在构建
     * AgentToolSet 时跳过该 Bean。若已存在同 beanName 的记录则更新为禁用状态，否则插入新记录。
     */
    @Override
    public void disableBuiltinToolByBeanName(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            throw new MateClawException("err.tool.bean_name_blank", "beanName 不能为空");
        }
        ToolEntity existing = toolMapper.selectOne(
                new LambdaQueryWrapper<ToolEntity>().eq(ToolEntity::getBeanName, beanName));
        if (existing != null) {
            if (Boolean.FALSE.equals(existing.getEnabled())) {
                log.debug("Builtin tool already disabled, skip: beanName={}", beanName);
                return;
            }
            existing.setEnabled(false);
            toolMapper.updateById(existing);
            log.info("Disabled builtin tool: beanName={}, id={}", beanName, existing.getId());
            return;
        }
        ToolEntity entity = new ToolEntity();
        entity.setName(beanName);
        entity.setBeanName(beanName);
        entity.setToolType("builtin");
        entity.setEnabled(false);
        entity.setBuiltin(true);
        toolMapper.insert(entity);
        log.info("Disabled builtin tool by inserting mate_tool row: beanName={}", beanName);
    }

    /**
     * 按工作区列出 Agent
     */
    @Override
    public List<AgentEntity> listAgentsByWorkspace(Long workspaceId, Boolean enabled) {
        return agentService.listAgentsByWorkspace(workspaceId, enabled);
    }

    /**
     * 根据 ID 获取 Agent
     */
    @Override
    public AgentEntity getAgent(Long id) {
        return agentService.getAgent(id);
    }

    /**
     * 创建 Agent
     */
    @Override
    public AgentEntity createAgent(AgentEntity agent) {
        return agentService.createAgent(agent);
    }

    /**
     * 更新 Agent
     */
    @Override
    public AgentEntity updateAgent(AgentEntity agent) {
        return agentService.updateAgent(agent);
    }

    /**
     * 删除 Agent
     */
    @Override
    public void deleteAgent(Long id) {
        agentService.deleteAgent(id);
    }

    /**
     * 获取所有已启用的工具
     */
    @Override
    public List<Object> getEnabledTools() {
        return toolRegistry.getEnabledTools();
    }

    // ==================== 模型配置 ====================

    /**
     * 获取启用的 Provider 列表
     */
    @Override
    public List<ProviderInfoDTO> listProviders() {
        return modelProviderService.listProviders();
    }

    /**
     * 获取 Provider 全量目录（含未启用）
     */
    @Override
    public List<ProviderInfoDTO> listProviderCatalog() {
        return modelProviderService.listCatalog();
    }

    /**
     * 启用 Provider
     */
    @Override
    public EnableResult enableProvider(String providerId) {
        return modelProviderService.setEnabled(providerId, true);
    }

    /**
     * 禁用 Provider
     */
    @Override
    public EnableResult disableProvider(String providerId) {
        return modelProviderService.setEnabled(providerId, false);
    }

    /**
     * 更新 Provider 配置
     */
    @Override
    public ProviderInfoDTO updateProviderConfig(String providerId, ProviderConfigRequest request) {
        return modelProviderService.updateProviderConfig(providerId, request);
    }

    /**
     * 创建自定义 Provider
     */
    @Override
    public ProviderInfoDTO createCustomProvider(CreateCustomProviderRequest request) {
        return modelProviderService.createCustomProvider(request);
    }

    /**
     * 删除自定义 Provider
     */
    @Override
    public void deleteCustomProvider(String providerId) {
        modelProviderService.deleteCustomProvider(providerId);
    }

    /**
     * 向 Provider 添加模型
     */
    @Override
    public ProviderInfoDTO addModelToProvider(String providerId, AddProviderModelRequest request) {
        return modelProviderService.addModel(providerId, request);
    }

    /**
     * 从 Provider 删除模型
     */
    @Override
    public ProviderInfoDTO removeModelFromProvider(String providerId, String modelId) {
        return modelProviderService.removeModel(providerId, modelId);
    }

    /**
     * 获取启用模型列表
     */
    @Override
    public List<ModelConfigEntity> listEnabledModels() {
        return modelConfigService.listEnabledModels();
    }

    /**
     * 获取所有已启用的模型（含 chat 和 embedding 类型）
     */
    @Override
    public List<ModelConfigEntity> listAllEnabledModels() {
        return modelConfigService.listModels()
                .stream()
                .filter(ModelConfigEntity::getEnabled)
                .toList();
    }

    /**
     * 获取所有模型（含启用和禁用）
     */
    @Override
    public List<ModelConfigEntity> listAllModels() {
        return modelConfigService.listModels();
    }

    /**
     * 获取默认模型
     */
    @Override
    public ModelConfigEntity getDefaultModel() {
        return modelConfigService.getDefaultModel();
    }

    /**
     * 获取当前激活模型
     */
    @Override
    public ActiveModelsInfo getActiveModel() {
        ModelConfigEntity model = modelConfigService.getDefaultModel();
        ActiveModelsInfo info = new ActiveModelsInfo();
        info.setActiveLlm(new ModelSlotConfig(model.getProvider(), model.getModelName()));
        return info;
    }

    /**
     * 设置当前激活模型
     */
    @Override
    public ActiveModelsInfo setActiveModel(ModelSlotRequest request) {
        ModelConfigEntity model = modelConfigService.setDefaultModel(request.getProviderId(), request.getModel());
        ActiveModelsInfo info = new ActiveModelsInfo();
        info.setActiveLlm(new ModelSlotConfig(model.getProvider(), model.getModelName()));
        return info;
    }

    /**
     * 获取模型详情
     */
    @Override
    public ModelConfigEntity getModel(Long id) {
        return modelConfigService.getModel(id);
    }

    /**
     * 创建模型
     */
    @Override
    public ModelConfigEntity createModel(ModelConfigEntity entity) {
        return modelConfigService.createModel(entity);
    }

    /**
     * 更新模型
     */
    @Override
    public ModelConfigEntity updateModel(ModelConfigEntity entity) {
        return modelConfigService.updateModel(entity);
    }

    /**
     * 删除模型
     */
    @Override
    public void deleteModel(Long id) {
        modelConfigService.deleteModel(id);
    }

    /**
     * 设置默认模型
     */
    @Override
    public ModelConfigEntity setDefaultModel(Long id) {
        return modelConfigService.setDefaultModel(id);
    }

    /**
     * 按类型筛选模型
     */
    @Override
    public List<ModelConfigEntity> listModelsByType(String modelType, String modality) {
        return modelConfigService.listByType(modelType, modality);
    }

    /**
     * 获取默认向量（embedding）模型
     */
    @Override
    public ModelConfigEntity getDefaultEmbeddingModel() {
        // 优先取 is_default=1 的 embedding 模型（与 chat 模型共用同一字段、各自独立）
        ModelConfigEntity marked = modelConfigService.listEnabledModels()
                .stream()
                .filter(modelConfigEntity -> modelConfigEntity.getEnabled()
                        && "embedding".equals(modelConfigEntity.getModelType()) && modelConfigEntity.getIsDefault())
                .findFirst()
                .orElse(null);

        if (marked != null) {
            return marked;
        }
        // 未配置时回退到第一个启用的 embedding 模型
        return modelConfigService.findFirstEnabledEmbedding();
    }

    /**
     * 设置默认向量（embedding）模型
     */
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

    /**
     * 发现远端模型
     */
    @Override
    public DiscoverResult discoverModels(String providerId) {
        return modelDiscoveryService.discoverModels(providerId);
    }

    /**
     * 批量添加发现的模型
     */
    @Override
    public Map<String, Integer> applyDiscoveredModels(String providerId, List<String> modelIds) {
        int added = modelDiscoveryService.batchAddModels(providerId, modelIds);
        return Map.of("added", added);
    }

    /**
     * 测试供应商连接
     */
    @Override
    public TestResult testProviderConnection(String providerId) {
        return modelDiscoveryService.testConnection(providerId);
    }

    /**
     * 测试单个模型可用性
     */
    @Override
    public TestResult testModel(String providerId, String modelId) {
        return modelDiscoveryService.testModel(providerId, modelId);
    }

    // ==================== 技能管理 ====================

    /**
     * 获取技能分页列表
     */
    @Override
    public IPage<SkillEntity> pageSkills(int page, int size, String keyword, String skillType,
                                          Boolean enabled, Long workspaceId,
                                          String sort, String lifecycleState) {
        return skillService.pageSkills(page, size, keyword, skillType, enabled, null, sort, null, null,
                java.util.Set.of(), workspaceId, lifecycleState);
    }

    /**
     * 获取所有技能列表（不分页）
     */
    @Override
    public List<SkillEntity> listSkills(Long workspaceId) {
        return skillService.listSkills(workspaceId);
    }

    /**
     * 获取已启用技能列表
     */
    @Override
    public List<SkillEntity> listEnabledSkills(Long workspaceId) {
        return skillService.listEnabledSkills(workspaceId);
    }

    /**
     * 获取技能详情
     */
    @Override
    public SkillEntity getSkill(Long id) {
        return skillService.getSkill(id);
    }

    /**
     * 创建技能
     */
    @Override
    public SkillEntity createSkill(SkillEntity entity) {
        return skillService.createSkill(entity);
    }

    /**
     * 更新技能
     */
    @Override
    public SkillEntity updateSkill(SkillEntity entity) {
        return skillService.updateSkill(entity);
    }

    /**
     * 硬删除技能
     */
    @Override
    public void hardDeleteSkill(Long id) {
        skillService.hardDeleteSkill(id);
    }

    /**
     * 切换技能启停状态
     */
    @Override
    public SkillEntity toggleSkill(Long id, boolean enabled) {
        return skillService.toggleSkill(id, enabled);
    }
}
