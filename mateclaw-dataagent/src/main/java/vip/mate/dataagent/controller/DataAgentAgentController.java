package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.agent.binding.model.AgentProviderPreference;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.model.AgentToolBinding;
import vip.mate.agent.model.AgentEntity;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.AgentGuard;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.ApplyTemplateRequest;
import vip.mate.llm.model.ProviderInfoDTO;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.model.SkillEntity;
import vip.mate.tool.model.AvailableToolDTO;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;

import java.util.List;

/**
 * Agent 管理控制器
 * <p>
 * 提供 Agent CRUD、模板应用以及能力绑定（技能 / 工具 / 偏好供应商 / 知识库）相关 API。
 */
@RestController
@RequestMapping("/v1/agents")
@RequiredArgsConstructor
@Tag(name = "Agent 管理", description = "数据分析 Agent 管理及能力绑定接口")
public class DataAgentAgentController {

    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;
    private final AgentGuard agentGuard;

    /**
     * Agent 列表
     */
    @GetMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "Agent 列表", description = "按工作区列出 Agent")
    public R<List<AgentEntity>> list(@RequestParam(required = false) Boolean enabled) {
        return R.ok(runtime.listAgentsByWorkspace(workspaceGuard.currentWorkspaceId(), enabled));
    }

    /**
     * Agent 详情
     */
    @GetMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "Agent 详情", description = "根据 ID 获取 Agent 详情")
    public R<AgentEntity> get(@PathVariable Long id) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        return R.ok(runtime.getAgent(id));
    }

    /**
     * 创建 Agent
     */
    @PostMapping
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "创建 Agent", description = "新增 Agent 配置")
    public R<AgentEntity> create(@RequestBody AgentEntity agent) {
        agent.setWorkspaceId(workspaceGuard.currentWorkspaceId());
        return R.ok(runtime.createAgent(agent));
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "更新 Agent", description = "更新 Agent 配置")
    public R<AgentEntity> update(@PathVariable Long id, @RequestBody AgentEntity agent) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        agent.setId(id);
        return R.ok(runtime.updateAgent(agent));
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "删除 Agent", description = "删除指定 Agent")
    public R<Void> delete(@PathVariable Long id) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        runtime.deleteAgent(id);
        return R.ok(null);
    }

    /**
     * 应用模板
     */
    @PostMapping("/apply-template")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "应用模板", description = "从模板创建 Agent")
    public R<AgentEntity> applyTemplate(@RequestBody ApplyTemplateRequest req) {
        return R.ok(runtime.applyTemplate(req.getTemplateId(), workspaceGuard.currentWorkspaceId(), null));
    }

    // ==================== 能力绑定相关只读资源 ====================

    /**
     * 已启用技能列表（用于编辑器选择）
     */
    @GetMapping("/skills/available")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "可绑定技能列表", description = "获取当前工作区下已启用的技能列表，供 Agent 编辑器使用")
    public R<List<SkillEntity>> listAvailableSkills() {
        return R.ok(runtime.listEnabledSkills(workspaceGuard.currentWorkspaceId()));
    }

    /**
     * 可绑定工具列表（含内置 + MCP）
     */
    @GetMapping("/tools/available")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "可绑定工具列表", description = "获取所有可绑定的工具，含内置工具与 MCP 工具")
    public R<List<AvailableToolDTO>> listAvailableTools() {
        return R.ok(runtime.listAvailableTools());
    }

    /**
     * 已启用 Provider 列表（用于偏好选择）
     */
    @GetMapping("/providers/available")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "可绑定 Provider 列表", description = "获取已启用的供应商，供 Agent 偏好提供商配置使用")
    public R<List<ProviderInfoDTO>> listAvailableProviders() {
        return R.ok(runtime.listProviders());
    }

    /**
     * 可绑定知识库列表
     */
    @GetMapping("/knowledge-bases/available")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "可绑定知识库列表", description = "获取当前工作区下可绑定到 Agent 的知识库")
    public R<List<WikiKnowledgeBaseEntity>> listAvailableKnowledgeBases() {
        return R.ok(runtime.listBindableKnowledgeBases(workspaceGuard.currentWorkspaceId()));
    }

    // ==================== 技能绑定 ====================

    /**
     * Agent 已绑定的技能
     */
    @GetMapping("/{id}/skills")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询已绑定技能", description = "获取指定 Agent 已绑定的技能列表")
    public R<List<AgentSkillBinding>> listAgentSkills(@PathVariable Long id) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        return R.ok(runtime.listAgentSkillBindings(id));
    }

    /**
     * 批量设置 Agent 技能绑定
     */
    @PutMapping("/{id}/skills")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "设置技能绑定", description = "批量替换 Agent 的技能绑定")
    public R<Void> setAgentSkills(@PathVariable Long id, @RequestBody List<Long> skillIds) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        runtime.setAgentSkillBindings(id, skillIds);
        return R.ok();
    }

    // ==================== 工具绑定 ====================

    /**
     * Agent 已绑定的工具
     */
    @GetMapping("/{id}/tools")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询已绑定工具", description = "获取指定 Agent 已绑定的工具列表")
    public R<List<AgentToolBinding>> listAgentTools(@PathVariable Long id) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        return R.ok(runtime.listAgentToolBindings(id));
    }

    /**
     * 批量设置 Agent 工具绑定
     */
    @PutMapping("/{id}/tools")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "设置工具绑定", description = "批量替换 Agent 的工具绑定")
    public R<Void> setAgentTools(@PathVariable Long id, @RequestBody List<String> toolNames) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        runtime.setAgentToolBindings(id, toolNames);
        return R.ok();
    }

    // ==================== 偏好供应商 ====================

    /**
     * Agent 偏好供应商
     */
    @GetMapping("/{id}/provider-preferences")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "查询偏好供应商", description = "获取 Agent 的偏好 Provider 顺序")
    public R<List<AgentProviderPreference>> listAgentProviderPreferences(@PathVariable Long id) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        return R.ok(runtime.listAgentProviderPreferences(id));
    }

    /**
     * 批量设置 Agent 偏好供应商顺序
     */
    @PutMapping("/{id}/provider-preferences")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "设置偏好供应商", description = "按顺序替换 Agent 的偏好 Provider 列表")
    public R<Void> setAgentProviderPreferences(@PathVariable Long id, @RequestBody List<String> providerIds) {
        agentGuard.requireAgentInCurrentWorkspace(id);
        runtime.setAgentProviderPreferences(id, providerIds);
        return R.ok();
    }
}
