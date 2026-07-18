package vip.mate.sdk.service.tool;

import org.springframework.ai.tool.ToolCallback;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;

import java.util.List;

/**
 * 工具运行时接口
 * <p>
 * 提供工具注册、禁用、查询以及 Agent 能力绑定等编程式访问能力。
 */
public interface ToolRuntime {

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
     * 获取所有已启用的工具
     *
     * @return 工具 Bean 列表
     */
    List<Object> getEnabledTools();

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
}
