package vip.mate.sdk.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.cron.model.CronJobDTO;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.llm.model.*;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.sdk.service.agent.AgentRuntime;
import vip.mate.sdk.service.cron.CronJobRuntime;
import vip.mate.sdk.service.datasource.DatasourceRuntime;
import vip.mate.sdk.service.model.ModelRuntime;
import vip.mate.sdk.service.skill.SkillRuntime;
import vip.mate.sdk.service.tool.ToolRuntime;
import vip.mate.sdk.service.workspace.WorkspaceRuntime;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;
import vip.mate.skill.model.SkillEntity;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;

import java.util.List;
import java.util.Map;

/**
 * MateClaw 嵌入式运行时实现
 * <p>
 * 将所有方法委托给各领域子运行时实现，
 * 为宿主应用提供统一的编程式访问入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MateClawRuntimeImpl implements MateClawRuntime {

    private final AgentRuntime agentRuntime;
    private final DatasourceRuntime datasourceRuntime;
    private final ToolRuntime toolRuntime;
    private final ModelRuntime modelRuntime;
    private final SkillRuntime skillRuntime;
    private final WorkspaceRuntime workspaceRuntime;
    private final CronJobRuntime cronJobRuntime;

    // ==================== Agent ====================

    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId) {
        return agentRuntime.chatStructuredStream(agentId, message, conversationId);
    }

    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String requesterId, String thinkingLevel, ChatOrigin origin) {
        return agentRuntime.chatStructuredStream(agentId, message, conversationId,
                requesterId, thinkingLevel, origin);
    }

    @Override
    public Flux<StreamDelta> chatStructuredStream(Long agentId, String message, String conversationId,
                                                   String modelName) {
        return agentRuntime.chatStructuredStream(agentId, message, conversationId, modelName);
    }

    @Override
    public String chat(Long agentId, String message, String conversationId) {
        return agentRuntime.chat(agentId, message, conversationId);
    }

    @Override
    public AgentEntity applyTemplate(String templateId, Long workspaceId, Long userId) {
        return agentRuntime.applyTemplate(templateId, workspaceId, userId);
    }

    @Override
    public List<AgentEntity> listAgentsByWorkspace(Long workspaceId, Boolean enabled) {
        return agentRuntime.listAgentsByWorkspace(workspaceId, enabled);
    }

    @Override
    public AgentEntity getAgent(Long id) {
        return agentRuntime.getAgent(id);
    }

    @Override
    public AgentEntity createAgent(AgentEntity agent) {
        return agentRuntime.createAgent(agent);
    }

    @Override
    public AgentEntity updateAgent(AgentEntity agent) {
        return agentRuntime.updateAgent(agent);
    }

    @Override
    public void deleteAgent(Long id) {
        agentRuntime.deleteAgent(id);
    }

    // ==================== 数据源 ====================

    @Override
    public List<DatasourceEntity> listDatasources() {
        return datasourceRuntime.listDatasources();
    }

    @Override
    public DatasourceEntity getDatasource(Long id) {
        return datasourceRuntime.getDatasource(id);
    }

    @Override
    public boolean testDatasourceConnection(Long id) {
        return datasourceRuntime.testDatasourceConnection(id);
    }

    @Override
    public DatasourceEntity createDatasource(DatasourceEntity entity) {
        return datasourceRuntime.createDatasource(entity);
    }

    @Override
    public DatasourceEntity updateDatasource(DatasourceEntity entity) {
        return datasourceRuntime.updateDatasource(entity);
    }

    @Override
    public void deleteDatasource(Long id) {
        datasourceRuntime.deleteDatasource(id);
    }

    @Override
    public DatasourceEntity toggleDatasource(Long id, boolean enabled) {
        return datasourceRuntime.toggleDatasource(id, enabled);
    }

    // ==================== 工具 ====================

    @Override
    public void registerTool(ToolCallback tool) {
        toolRuntime.registerTool(tool);
    }

    @Override
    public void disableBuiltinToolByBeanName(String beanName) {
        toolRuntime.disableBuiltinToolByBeanName(beanName);
    }

    @Override
    public List<Object> getEnabledTools() {
        return toolRuntime.getEnabledTools();
    }

    @Override
    public List<AgentSkillBinding> listAgentSkillBindings(Long agentId) {
        return toolRuntime.listAgentSkillBindings(agentId);
    }

    @Override
    public void setAgentSkillBindings(Long agentId, List<Long> skillIds) {
        toolRuntime.setAgentSkillBindings(agentId, skillIds);
    }

    @Override
    public List<AgentToolBinding> listAgentToolBindings(Long agentId) {
        return toolRuntime.listAgentToolBindings(agentId);
    }

    @Override
    public void setAgentToolBindings(Long agentId, List<String> toolNames) {
        toolRuntime.setAgentToolBindings(agentId, toolNames);
    }

    @Override
    public List<AgentProviderPreference> listAgentProviderPreferences(Long agentId) {
        return toolRuntime.listAgentProviderPreferences(agentId);
    }

    @Override
    public void setAgentProviderPreferences(Long agentId, List<String> providerIds) {
        toolRuntime.setAgentProviderPreferences(agentId, providerIds);
    }

    @Override
    public List<AvailableToolDTO> listAvailableTools() {
        return toolRuntime.listAvailableTools();
    }

    @Override
    public List<WikiKnowledgeBaseEntity> listBindableKnowledgeBases(Long workspaceId) {
        return toolRuntime.listBindableKnowledgeBases(workspaceId);
    }

    // ==================== 模型 ====================

    @Override
    public List<ProviderInfoDTO> listProviders() {
        return modelRuntime.listProviders();
    }

    @Override
    public List<ProviderInfoDTO> listProviderCatalog() {
        return modelRuntime.listProviderCatalog();
    }

    @Override
    public EnableResult enableProvider(String providerId) {
        return modelRuntime.enableProvider(providerId);
    }

    @Override
    public EnableResult disableProvider(String providerId) {
        return modelRuntime.disableProvider(providerId);
    }

    @Override
    public ProviderInfoDTO updateProviderConfig(String providerId, ProviderConfigRequest request) {
        return modelRuntime.updateProviderConfig(providerId, request);
    }

    @Override
    public ProviderInfoDTO createCustomProvider(CreateCustomProviderRequest request) {
        return modelRuntime.createCustomProvider(request);
    }

    @Override
    public void deleteCustomProvider(String providerId) {
        modelRuntime.deleteCustomProvider(providerId);
    }

    @Override
    public ProviderInfoDTO addModelToProvider(String providerId, AddProviderModelRequest request) {
        return modelRuntime.addModelToProvider(providerId, request);
    }

    @Override
    public ProviderInfoDTO removeModelFromProvider(String providerId, String modelId) {
        return modelRuntime.removeModelFromProvider(providerId, modelId);
    }

    @Override
    public List<ModelConfigEntity> listEnabledModels() {
        return modelRuntime.listEnabledModels();
    }

    @Override
    public List<ModelConfigEntity> listAllEnabledModels() {
        return modelRuntime.listAllEnabledModels();
    }

    @Override
    public List<ModelConfigEntity> listAllModels() {
        return modelRuntime.listAllModels();
    }

    @Override
    public ModelConfigEntity getDefaultModel() {
        return modelRuntime.getDefaultModel();
    }

    @Override
    public ActiveModelsInfo getActiveModel() {
        return modelRuntime.getActiveModel();
    }

    @Override
    public ActiveModelsInfo setActiveModel(ModelSlotRequest request) {
        return modelRuntime.setActiveModel(request);
    }

    @Override
    public ModelConfigEntity getModel(Long id) {
        return modelRuntime.getModel(id);
    }

    @Override
    public ModelConfigEntity createModel(ModelConfigEntity entity) {
        return modelRuntime.createModel(entity);
    }

    @Override
    public ModelConfigEntity updateModel(ModelConfigEntity entity) {
        return modelRuntime.updateModel(entity);
    }

    @Override
    public void deleteModel(Long id) {
        modelRuntime.deleteModel(id);
    }

    @Override
    public ModelConfigEntity setDefaultModel(Long id) {
        return modelRuntime.setDefaultModel(id);
    }

    @Override
    public List<ModelConfigEntity> listModelsByType(String modelType, String modality) {
        return modelRuntime.listModelsByType(modelType, modality);
    }

    @Override
    public ModelConfigEntity getDefaultEmbeddingModel() {
        return modelRuntime.getDefaultEmbeddingModel();
    }

    @Override
    public ModelConfigEntity setDefaultEmbeddingModel(Long id) {
        return modelRuntime.setDefaultEmbeddingModel(id);
    }

    @Override
    public DiscoverResult discoverModels(String providerId) {
        return modelRuntime.discoverModels(providerId);
    }

    @Override
    public Map<String, Integer> applyDiscoveredModels(String providerId, List<String> modelIds) {
        return modelRuntime.applyDiscoveredModels(providerId, modelIds);
    }

    @Override
    public TestResult testProviderConnection(String providerId) {
        return modelRuntime.testProviderConnection(providerId);
    }

    @Override
    public TestResult testModel(String providerId, String modelName) {
        return modelRuntime.testModel(providerId, modelName);
    }

    @Override
    public Map<String, Object> testEmbeddingModel(Long modelId) {
        return modelRuntime.testEmbeddingModel(modelId);
    }

    // ==================== 技能 ====================

    @Override
    public IPage<SkillEntity> pageSkills(int page, int size, String keyword, String skillType,
                                          Boolean enabled, Long workspaceId,
                                          String sort, String lifecycleState) {
        return skillRuntime.pageSkills(page, size, keyword, skillType, enabled, workspaceId, sort, lifecycleState);
    }

    @Override
    public List<SkillEntity> listSkills(Long workspaceId) {
        return skillRuntime.listSkills(workspaceId);
    }

    @Override
    public List<SkillEntity> listEnabledSkills(Long workspaceId) {
        return skillRuntime.listEnabledSkills(workspaceId);
    }

    @Override
    public SkillEntity getSkill(Long id) {
        return skillRuntime.getSkill(id);
    }

    @Override
    public SkillEntity createSkill(SkillEntity entity) {
        return skillRuntime.createSkill(entity);
    }

    @Override
    public SkillEntity updateSkill(SkillEntity entity) {
        return skillRuntime.updateSkill(entity);
    }

    @Override
    public void hardDeleteSkill(Long id) {
        skillRuntime.hardDeleteSkill(id);
    }

    @Override
    public SkillEntity toggleSkill(Long id, boolean enabled) {
        return skillRuntime.toggleSkill(id, enabled);
    }

    @Override
    public List<HubSkillInfo> searchSkillHub(String query, int limit) {
        return skillRuntime.searchSkillHub(query, limit);
    }

    @Override
    public InstallTask startInstallSkill(InstallRequest request) {
        return skillRuntime.startInstallSkill(request);
    }

    @Override
    public InstallTask getInstallTaskStatus(String taskId) {
        return skillRuntime.getInstallTaskStatus(taskId);
    }

    @Override
    public void cancelInstallTask(String taskId) {
        skillRuntime.cancelInstallTask(taskId);
    }

    @Override
    public Map<String, Object> installSkillFromZip(MultipartFile zipFile, boolean enable, boolean overwrite,
                                                   String targetName, Long workspaceId) {
        return skillRuntime.installSkillFromZip(zipFile, enable, overwrite, targetName, workspaceId);
    }

    @Override
    public void uninstallSkillByName(String skillName, Long workspaceId) {
        skillRuntime.uninstallSkillByName(skillName, workspaceId);
    }

    // ==================== 工作区 ====================

    @Override
    public void requireWorkspaceRole(Long workspaceId, Long userId, String minRole) {
        workspaceRuntime.requireWorkspaceRole(workspaceId, userId, minRole);
    }

    @Override
    public boolean hasWorkspacePermission(Long workspaceId, Long userId, String minRole) {
        return workspaceRuntime.hasWorkspacePermission(workspaceId, userId, minRole);
    }

    @Override
    public boolean isGlobalAdmin(Long userId) {
        return workspaceRuntime.isGlobalAdmin(userId);
    }

    @Override
    public String getWorkspaceMemberRole(Long workspaceId, Long userId) {
        return workspaceRuntime.getWorkspaceMemberRole(workspaceId, userId);
    }

    @Override
    public List<WorkspaceWithRoleVO> listWorkspacesWithRole(Long userId, boolean isGlobalAdmin) {
        return workspaceRuntime.listWorkspacesWithRole(userId, isGlobalAdmin);
    }

    @Override
    public WorkspaceEntity getWorkspace(Long id) {
        return workspaceRuntime.getWorkspace(id);
    }

    @Override
    public WorkspaceEntity createWorkspace(WorkspaceEntity entity, Long creatorUserId) {
        return workspaceRuntime.createWorkspace(entity, creatorUserId);
    }

    @Override
    public WorkspaceEntity updateWorkspace(WorkspaceEntity entity) {
        return workspaceRuntime.updateWorkspace(entity);
    }

    @Override
    public void deleteWorkspace(Long id) {
        workspaceRuntime.deleteWorkspace(id);
    }

    @Override
    public List<WorkspaceMemberEntity> listWorkspaceMembers(Long workspaceId) {
        return workspaceRuntime.listWorkspaceMembers(workspaceId);
    }

    @Override
    public WorkspaceMemberEntity addWorkspaceMember(Long workspaceId, String username, String nickname,
                                                     String password, String role) {
        return workspaceRuntime.addWorkspaceMember(workspaceId, username, nickname, password, role);
    }

    @Override
    public WorkspaceMemberEntity updateWorkspaceMemberRole(Long workspaceId, Long userId, String role) {
        return workspaceRuntime.updateWorkspaceMemberRole(workspaceId, userId, role);
    }

    @Override
    public void removeWorkspaceMember(Long workspaceId, Long userId) {
        workspaceRuntime.removeWorkspaceMember(workspaceId, userId);
    }

    @Override
    public Long findUserIdByUsername(String username) {
        return workspaceRuntime.findUserIdByUsername(username);
    }

    // ==================== 定时任务 ====================

    @Override
    public List<CronJobDTO> listCronJobs(Long workspaceId) {
        return cronJobRuntime.listCronJobs(workspaceId);
    }

    @Override
    public CronJobDTO getCronJob(Long id, Long workspaceId) {
        return cronJobRuntime.getCronJob(id, workspaceId);
    }

    @Override
    public CronJobDTO createCronJob(CronJobDTO dto, Long workspaceId) {
        return cronJobRuntime.createCronJob(dto, workspaceId);
    }

    @Override
    public CronJobDTO updateCronJob(Long id, CronJobDTO dto, Long workspaceId) {
        return cronJobRuntime.updateCronJob(id, dto, workspaceId);
    }

    @Override
    public void deleteCronJob(Long id, Long workspaceId) {
        cronJobRuntime.deleteCronJob(id, workspaceId);
    }

    @Override
    public void toggleCronJob(Long id, boolean enabled, Long workspaceId) {
        cronJobRuntime.toggleCronJob(id, enabled, workspaceId);
    }

    @Override
    public void runCronJobNow(Long id, Long workspaceId) {
        cronJobRuntime.runCronJobNow(id, workspaceId);
    }
}
