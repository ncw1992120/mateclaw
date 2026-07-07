package vip.mate.sdk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.model.AgentEntity;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.llm.model.*;
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
     * 与指定 Agent 进行同步对话，阻塞等待完整响应
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @return Agent 完整回复文本
     */
    String chat(Long agentId, String message, String conversationId);

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
     * 按 Spring Bean 名称禁用内置 @Tool Bean。
     * <p>
     * 用于宿主应用屏蔽 mateclaw-server 中默认启用的内置工具，避免与宿主自定义工具发生同名冲突。
     * 调用后会向 mate_tool 表写入或更新一条 enabled=false 的记录，ToolRegistry 在下一次构建
     * AgentToolSet 时会跳过该 Bean。
     *
     * @param beanName Spring Bean 名称（即 @Component 默认或显式指定的名称）
     */
    void disableBuiltinToolByBeanName(String beanName);

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

    // ==================== 技能管理 ====================

    /**
     * 获取技能分页列表
     *
     * @param page        页码（从 1 开始）
     * @param size        每页条数
     * @param keyword     关键字（可选，模糊匹配名称/描述/标签）
     * @param skillType   技能类型（可选，builtin / mcp / dynamic 等）
     * @param enabled     是否启用（可选）
     * @param workspaceId 工作区 ID（可选）
     * @return 技能分页数据
     */
    IPage<SkillEntity> pageSkills(
            int page, int size, String keyword, String skillType, Boolean enabled,
            Long workspaceId, String sort, String lifecycleState);

    /**
     * 获取所有技能列表（不分页）
     *
     * @param workspaceId 工作区 ID（可选）
     * @return 技能列表
     */
    List<SkillEntity> listSkills(Long workspaceId);

    /**
     * 获取已启用技能列表
     *
     * @param workspaceId 工作区 ID（可选）
     * @return 已启用技能列表
     */
    List<SkillEntity> listEnabledSkills(Long workspaceId);

    /**
     * 获取技能详情
     *
     * @param id 技能 ID
     * @return 技能实体
     */
    SkillEntity getSkill(Long id);

    /**
     * 创建技能
     *
     * @param entity 技能实体
     * @return 创建后的技能实体
     */
    SkillEntity createSkill(SkillEntity entity);

    /**
     * 更新技能
     *
     * @param entity 技能实体（需包含 ID）
     * @return 更新后的技能实体
     */
    SkillEntity updateSkill(SkillEntity entity);

    /**
     * 硬删除技能（admin only）
     *
     * @param id 技能 ID
     */
    void hardDeleteSkill(Long id);

    /**
     * 切换技能启停状态
     *
     * @param id      技能 ID
     * @param enabled 是否启用
     * @return 更新后的技能实体
     */
    SkillEntity toggleSkill(Long id, boolean enabled);

    // ==================== 技能导入 ====================

    /**
     * 在 ClawHub 市场搜索可用技能
     *
     * @param query 搜索关键词
     * @param limit 返回条数上限
     * @return 市场技能信息列表
     */
    List<HubSkillInfo> searchSkillHub(String query, int limit);

    /**
     * 启动一个异步技能安装任务
     * <p>
     * 适用于从 GitHub 仓库或 ClawHub 市场安装，支持取消与任务状态轮询。
     *
     * @param request 安装请求（含 bundleUrl、version、enable、overwrite、targetName 等）
     * @return 安装任务（包含 taskId 与初始状态）
     */
    InstallTask startInstallSkill(InstallRequest request);

    /**
     * 查询安装任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态对象，未找到时返回 null
     */
    InstallTask getInstallTaskStatus(String taskId);

    /**
     * 取消正在执行的安装任务
     *
     * @param taskId 任务 ID
     */
    void cancelInstallTask(String taskId);

    /**
     * 通过上传 ZIP 包同步安装技能
     *
     * @param zipFile     上传的 ZIP 文件
     * @param enable      安装后是否启用
     * @param overwrite   同名技能已存在时是否覆盖
     * @param targetName  指定安装后的 skill 名称（可选）
     * @param workspaceId 所属工作区 ID
     * @return 安装结果摘要（skillId、name、version、filesCount）
     */
    Map<String, Object> installSkillFromZip(MultipartFile zipFile, boolean enable, boolean overwrite,
                                            String targetName, Long workspaceId);

    /**
     * 通过名称卸载技能
     *
     * @param skillName   技能名称
     * @param workspaceId 所属工作区 ID
     */
    void uninstallSkillByName(String skillName, Long workspaceId);

    // ==================== Agent 能力绑定 ====================

    /**
     * 获取 Agent 已绑定的技能列表
     *
     * @param agentId Agent ID
     * @return 已绑定的技能绑定记录列表
     */
    List<AgentSkillBinding> listAgentSkillBindings(Long agentId);

    /**
     * 批量设置 Agent 的技能绑定（替换模式）
     *
     * @param agentId  Agent ID
     * @param skillIds 技能 ID 列表
     */
    void setAgentSkillBindings(Long agentId, List<Long> skillIds);

    /**
     * 获取 Agent 已绑定的工具列表
     *
     * @param agentId Agent ID
     * @return 已绑定的工具绑定记录列表
     */
    List<AgentToolBinding> listAgentToolBindings(Long agentId);

    /**
     * 批量设置 Agent 的工具绑定（替换模式）
     *
     * @param agentId   Agent ID
     * @param toolNames 工具名称列表
     */
    void setAgentToolBindings(Long agentId, List<String> toolNames);

    /**
     * 获取 Agent 的偏好 Provider 顺序
     *
     * @param agentId Agent ID
     * @return 偏好 Provider 列表（按 sortOrder 升序）
     */
    List<AgentProviderPreference> listAgentProviderPreferences(Long agentId);

    /**
     * 批量设置 Agent 的偏好 Provider 顺序（替换模式）
     *
     * @param agentId     Agent ID
     * @param providerIds Provider ID 列表（按偏好顺序）
     */
    void setAgentProviderPreferences(Long agentId, List<String> providerIds);

    /**
     * 获取 Agent 的可用工具完整列表（含内置 + MCP，用于绑定 picker）
     *
     * @return 可用工具 DTO 列表
     */
    List<AvailableToolDTO> listAvailableTools();

    /**
     * 列出指定工作区下可绑定到 Agent 的知识库
     *
     * @param workspaceId 工作区 ID
     * @return 知识库实体列表
     */
    List<WikiKnowledgeBaseEntity> listBindableKnowledgeBases(Long workspaceId);

    // ==================== 工作区权限 ====================

    /**
     * 断言用户在指定工作区具有最低角色权限，不通过则抛 403 异常
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param minRole     最低角色要求：owner &gt; admin &gt; member &gt; viewer
     */
    void requireWorkspaceRole(Long workspaceId, Long userId, String minRole);

    /**
     * 检查用户是否有指定工作区的最低角色权限（带缓存，高频调用场景使用）
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param minRole     最低角色要求
     * @return true 如果用户有足够权限
     */
    boolean hasWorkspacePermission(Long workspaceId, Long userId, String minRole);

    /**
     * 判断用户是否为全局管理员（mate_user.role = admin）
     *
     * @param userId 用户 ID
     * @return true 如果是全局管理员
     */
    boolean isGlobalAdmin(Long userId);

    /**
     * 获取用户在指定工作区的成员角色
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @return 角色字符串（owner/admin/member/viewer），非成员返回 null
     */
    String getWorkspaceMemberRole(Long workspaceId, Long userId);

    // ==================== 工作区管理 ====================

    /**
     * 查询用户可见的工作区列表（含成员角色与生效角色）
     *
     * @param userId        用户 ID
     * @param isGlobalAdmin 是否为全局管理员
     * @return 工作区列表（含角色信息）
     */
    List<WorkspaceWithRoleVO> listWorkspacesWithRole(Long userId, boolean isGlobalAdmin);

    /**
     * 根据 ID 获取工作区详情
     *
     * @param id 工作区 ID
     * @return 工作区实体
     */
    WorkspaceEntity getWorkspace(Long id);

    /**
     * 创建工作区
     *
     * @param entity         工作区实体
     * @param creatorUserId  创建者用户 ID
     * @return 创建后的工作区实体
     */
    WorkspaceEntity createWorkspace(WorkspaceEntity entity, Long creatorUserId);

    /**
     * 更新工作区
     *
     * @param entity 工作区实体（需包含 ID）
     * @return 更新后的工作区实体
     */
    WorkspaceEntity updateWorkspace(WorkspaceEntity entity);

    /**
     * 删除工作区
     *
     * @param id 工作区 ID
     */
    void deleteWorkspace(Long id);

    /**
     * 获取工作区成员列表（含用户名、昵称）
     *
     * @param workspaceId 工作区 ID
     * @return 成员列表
     */
    List<WorkspaceMemberEntity> listWorkspaceMembers(Long workspaceId);

    /**
     * 添加工作区成员
     * <p>
     * 若指定用户名的用户不存在，则使用密码创建账号后再加入工作区。
     *
     * @param workspaceId 工作区 ID
     * @param username    用户名
     * @param nickname    昵称（用户不存在时创建账号使用，可为 null）
     * @param password    密码（用户不存在时必填，已有用户忽略）
     * @param role        角色：admin / member / viewer
     * @return 成员实体
     */
    WorkspaceMemberEntity addWorkspaceMember(Long workspaceId, String username, String nickname,
                                              String password, String role);

    /**
     * 更新成员角色
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标用户 ID
     * @param role        新角色：admin / member / viewer
     * @return 更新后的成员实体
     */
    WorkspaceMemberEntity updateWorkspaceMemberRole(Long workspaceId, Long userId, String role);

    /**
     * 移除工作区成员
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标用户 ID
     */
    void removeWorkspaceMember(Long workspaceId, Long userId);

    /**
     * 根据用户名查询用户 ID
     *
     * @param username 用户名
     * @return 用户 ID，不存在返回 null
     */
    Long findUserIdByUsername(String username);
}
