package vip.mate.sdk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.agent.binding.service.AgentBindingService;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.service.TemplateService;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.service.DatasourceService;
import vip.mate.exception.MateClawException;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.*;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelDiscoveryService;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.sdk.service.WikiRuntime;
import vip.mate.skill.installer.SkillInstaller;
import vip.mate.skill.installer.ZipSkillFetcher;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.runtime.SkillFrontmatterParser;
import vip.mate.skill.service.SkillService;
import vip.mate.system.service.SystemSettingService;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;
import vip.mate.workspace.core.service.WorkspaceService;
import vip.mate.tool.ToolRegistry;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.tool.model.ToolEntity;
import vip.mate.tool.repository.ToolMapper;
import vip.mate.tool.service.AvailableToolService;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;

import java.util.HashMap;
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
    private final EmbeddingModelFactory embeddingModelFactory;
    private final SkillService skillService;
    private final SystemSettingService systemSettingService;
    private final AgentBindingService agentBindingService;
    private final AvailableToolService availableToolService;
    private final WikiRuntime wikiRuntime;
    private final SkillInstaller skillInstaller;
    private final SkillFrontmatterParser skillFrontmatterParser;
    private final WorkspaceService workspaceService;
    private final AuthService authService;

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

    // ==================== 技能导入 ====================

    /**
     * 在 ClawHub 市场搜索可用技能
     */
    @Override
    public List<HubSkillInfo> searchSkillHub(String query, int limit) {
        return skillInstaller.searchHub(query, limit);
    }

    /**
     * 启动一个异步技能安装任务
     */
    @Override
    public InstallTask startInstallSkill(InstallRequest request) {
        return skillInstaller.startInstall(request);
    }

    /**
     * 查询安装任务状态
     */
    @Override
    public InstallTask getInstallTaskStatus(String taskId) {
        return skillInstaller.getTaskStatus(taskId);
    }

    /**
     * 取消正在执行的安装任务
     */
    @Override
    public void cancelInstallTask(String taskId) {
        skillInstaller.cancelTask(taskId);
    }

    /**
     * 通过上传 ZIP 包同步安装技能
     */
    @Override
    public Map<String, Object> installSkillFromZip(MultipartFile zipFile, boolean enable, boolean overwrite,
                                                   String targetName, Long workspaceId) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new MateClawException("err.skill.zip_empty", "ZIP 文件不能为空");
        }
        String filename = zipFile.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new MateClawException("err.skill.zip_invalid", "仅支持 .zip 文件");
        }
        try {
            var bundle = ZipSkillFetcher.parse(zipFile, skillFrontmatterParser);
            return skillInstaller.installFromBundle(bundle, enable, overwrite, targetName, workspaceId);
        } catch (MateClawException e) {
            throw e;
        } catch (Exception e) {
            log.error("ZIP install failed: {}", e.getMessage(), e);
            throw new MateClawException("err.skill.zip_install_failed", "ZIP 安装失败: " + e.getMessage());
        }
    }

    /**
     * 通过名称卸载技能
     */
    @Override
    public void uninstallSkillByName(String skillName, Long workspaceId) {
        skillInstaller.uninstall(skillName, workspaceId);
    }

    // ==================== Agent 能力绑定 ====================

    /**
     * 获取 Agent 已绑定的技能列表
     */
    @Override
    public List<AgentSkillBinding> listAgentSkillBindings(Long agentId) {
        return agentBindingService.listSkillBindings(agentId);
    }

    /**
     * 批量设置 Agent 的技能绑定
     */
    @Override
    public void setAgentSkillBindings(Long agentId, List<Long> skillIds) {
        agentBindingService.setSkillBindings(agentId, skillIds);
        agentService.invalidateAgentCache(agentId);
    }

    /**
     * 获取 Agent 已绑定的工具列表
     */
    @Override
    public List<AgentToolBinding> listAgentToolBindings(Long agentId) {
        return agentBindingService.listToolBindings(agentId);
    }

    /**
     * 批量设置 Agent 的工具绑定
     */
    @Override
    public void setAgentToolBindings(Long agentId, List<String> toolNames) {
        agentBindingService.setToolBindings(agentId, toolNames);
        agentService.invalidateAgentCache(agentId);
    }

    /**
     * 获取 Agent 的偏好 Provider 顺序
     */
    @Override
    public List<AgentProviderPreference> listAgentProviderPreferences(Long agentId) {
        return agentBindingService.listProviderPreferences(agentId);
    }

    /**
     * 批量设置 Agent 的偏好 Provider 顺序
     */
    @Override
    public void setAgentProviderPreferences(Long agentId, List<String> providerIds) {
        agentBindingService.setProviderPreferences(agentId, providerIds);
        agentService.invalidateAgentCache(agentId);
    }

    /**
     * 获取 Agent 的可用工具完整列表
     */
    @Override
    public List<AvailableToolDTO> listAvailableTools() {
        return availableToolService.listAvailable();
    }

    /**
     * 列出指定工作区下可绑定到 Agent 的知识库
     */
    @Override
    public List<WikiKnowledgeBaseEntity> listBindableKnowledgeBases(Long workspaceId) {
        return wikiRuntime.listKBsByWorkspace(workspaceId);
    }

    /**
     * 断言用户在指定工作区具有最低角色权限
     */
    @Override
    public void requireWorkspaceRole(Long workspaceId, Long userId, String minRole) {
        workspaceService.requirePermission(workspaceId, userId, minRole);
    }

    /**
     * 检查用户是否有指定工作区的最低角色权限（带缓存）
     */
    @Override
    public boolean hasWorkspacePermission(Long workspaceId, Long userId, String minRole) {
        return workspaceService.hasPermissionCached(workspaceId, userId, minRole);
    }

    /**
     * 判断用户是否为全局管理员
     */
    @Override
    public boolean isGlobalAdmin(Long userId) {
        UserEntity user = authService.findById(userId);
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    // ==================== 工作区管理 ====================

    /**
     * 查询用户可见的工作区列表（含成员角色与生效角色）
     */
    @Override
    public List<WorkspaceWithRoleVO> listWorkspacesWithRole(Long userId, boolean isGlobalAdmin) {
        return workspaceService.listWithRoleByUserId(userId, isGlobalAdmin);
    }

    /**
     * 根据 ID 获取工作区详情
     */
    @Override
    public WorkspaceEntity getWorkspace(Long id) {
        return workspaceService.getById(id);
    }

    /**
     * 创建工作区
     */
    @Override
    public WorkspaceEntity createWorkspace(WorkspaceEntity entity, Long creatorUserId) {
        return workspaceService.create(entity, creatorUserId);
    }

    /**
     * 更新工作区
     */
    @Override
    public WorkspaceEntity updateWorkspace(WorkspaceEntity entity) {
        return workspaceService.update(entity);
    }

    /**
     * 删除工作区
     */
    @Override
    public void deleteWorkspace(Long id) {
        workspaceService.delete(id);
    }

    /**
     * 获取工作区成员列表（含用户名、昵称）
     */
    @Override
    public List<WorkspaceMemberEntity> listWorkspaceMembers(Long workspaceId) {
        List<WorkspaceMemberEntity> members = workspaceService.listMembers(workspaceId);
        for (WorkspaceMemberEntity m : members) {
            UserEntity user = authService.findById(m.getUserId());
            if (user != null) {
                m.setUsername(user.getUsername());
                m.setNickname(user.getNickname());
            }
        }
        return members;
    }

    /**
     * 添加工作区成员
     * <p>
     * 若用户不存在则创建账号，已有用户直接加入。密码仅用于新账号创建，
     * 不会重置已有用户的密码（避免工作区管理员借此接管其他账号）。
     */
    @Override
    public WorkspaceMemberEntity addWorkspaceMember(Long workspaceId, String username, String nickname,
                                                     String password, String role) {
        UserEntity target = authService.findByUsername(username);
        if (target == null) {
            if (password == null || password.isBlank()) {
                throw new MateClawException("err.workspace.user_not_found",
                        "用户不存在: " + username + "，需提供密码以创建账号");
            }
            UserEntity newUser = new UserEntity();
            newUser.setUsername(username);
            newUser.setPassword(password);
            newUser.setNickname(nickname != null && !nickname.isBlank() ? nickname : username);
            target = authService.createUser(newUser);
        }
        return workspaceService.addMember(workspaceId, target.getId(), role);
    }

    /**
     * 更新成员角色
     */
    @Override
    public WorkspaceMemberEntity updateWorkspaceMemberRole(Long workspaceId, Long userId, String role) {
        return workspaceService.updateMemberRole(workspaceId, userId, role);
    }

    /**
     * 移除工作区成员
     */
    @Override
    public void removeWorkspaceMember(Long workspaceId, Long userId) {
        workspaceService.removeMember(workspaceId, userId);
    }

    /**
     * 根据用户名查询用户 ID
     */
    @Override
    public Long findUserIdByUsername(String username) {
        UserEntity user = authService.findByUsername(username);
        return user != null ? user.getId() : null;
    }
}
