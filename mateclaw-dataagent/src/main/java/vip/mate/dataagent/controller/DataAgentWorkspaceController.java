package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;

import java.util.List;
import java.util.Map;

/**
 * 工作区管理控制器
 * <p>
 * 提供工作区 CRUD 与成员管理接口，通过 SDK 委托给 mateclaw-server。
 * 工作区管理为跨工作区操作，权限校验在方法内显式执行，不使用 @RequireWorkspaceRole。
 */
@RestController
@RequestMapping("/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "工作区管理", description = "工作区 CRUD 与成员管理接口")
public class DataAgentWorkspaceController {

    private final MateClawRuntime runtime;
    private final WorkspaceGuard workspaceGuard;

    // ==================== 工作区 CRUD ====================

    /**
     * 获取当前用户可见的工作区列表（含 memberRole 与 effectiveRole）
     */
    @GetMapping
    @Operation(summary = "工作区列表", description = "获取当前用户可见的工作区列表，含成员角色与生效角色")
    public R<List<WorkspaceWithRoleVO>> list() {
        Long userId = workspaceGuard.currentUserId();
        boolean isGlobalAdmin = workspaceGuard.isCurrentAdmin();
        return R.ok(runtime.listWorkspacesWithRole(userId, isGlobalAdmin));
    }

    /**
     * 获取工作区详情
     * <p>
     * 需要目标工作区的 viewer 权限。
     */
    @GetMapping("/{id}")
    @Operation(summary = "工作区详情", description = "根据 ID 获取工作区详情，需要目标工作区的 viewer 权限")
    public R<WorkspaceEntity> get(@Parameter(description = "工作区 ID") @PathVariable Long id) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_VIEWER);
        }
        return R.ok(runtime.getWorkspace(id));
    }

    /**
     * 创建工作区
     * <p>
     * 仅全局管理员可创建工作区。
     */
    @PostMapping
    @Operation(summary = "创建工作区", description = "创建新工作区，仅全局管理员可操作")
    public R<WorkspaceEntity> create(@RequestBody WorkspaceEntity entity) {
        if (!workspaceGuard.isCurrentAdmin()) {
            throw new MateClawException("err.workspace.insufficient_permission", 403, "仅全局管理员可创建工作区");
        }
        return R.ok(runtime.createWorkspace(entity, workspaceGuard.currentUserId()));
    }

    /**
     * 更新工作区
     * <p>
     * 需要目标工作区的 admin 权限。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新工作区", description = "更新工作区信息，需要目标工作区的 admin 权限")
    public R<WorkspaceEntity> update(@Parameter(description = "工作区 ID") @PathVariable Long id,
                                       @RequestBody WorkspaceEntity entity) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_ADMIN);
        }
        entity.setId(id);
        return R.ok(runtime.updateWorkspace(entity));
    }

    /**
     * 删除工作区
     * <p>
     * 需要目标工作区的 owner 权限。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作区", description = "删除工作区，需要目标工作区的 owner 权限")
    public R<Void> delete(@Parameter(description = "工作区 ID") @PathVariable Long id) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_OWNER);
        }
        runtime.deleteWorkspace(id);
        return R.ok();
    }

    // ==================== 成员管理 ====================

    /**
     * 获取工作区成员列表
     * <p>
     * 需要目标工作区的 viewer 权限。
     */
    @GetMapping("/{id}/members")
    @Operation(summary = "成员列表", description = "获取工作区成员列表，需要目标工作区的 viewer 权限")
    public R<List<WorkspaceMemberEntity>> listMembers(@Parameter(description = "工作区 ID") @PathVariable Long id) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_VIEWER);
        }
        return R.ok(runtime.listWorkspaceMembers(id));
    }

    /**
     * 添加工作区成员
     * <p>
     * 需要目标工作区的 admin 权限。若用户不存在则使用密码创建账号。
     */
    @PostMapping("/{id}/members")
    @Operation(summary = "添加成员", description = "向工作区添加成员，需要 admin 权限。若用户不存在则使用密码创建账号")
    public R<WorkspaceMemberEntity> addMember(@Parameter(description = "工作区 ID") @PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_ADMIN);
        }
        String username = body.containsKey("username") ? body.get("username").toString().trim() : null;
        if (username == null || username.isBlank()) {
            throw new MateClawException("err.workspace.username_required", 400, "用户名不能为空");
        }
        String nickname = body.containsKey("nickname") && body.get("nickname") != null
                ? body.get("nickname").toString() : null;
        String password = body.containsKey("password") && body.get("password") != null
                ? body.get("password").toString().trim() : null;
        String role = body.containsKey("role") ? body.get("role").toString() : DataAgentConstants.WORKSPACE_ROLE_MEMBER;
        return R.ok(runtime.addWorkspaceMember(id, username, nickname, password, role));
    }

    /**
     * 更新成员角色
     * <p>
     * 需要目标工作区的 admin 权限。
     */
    @PutMapping("/{id}/members/{targetUserId}")
    @Operation(summary = "更新成员角色", description = "修改成员角色，需要 admin 权限")
    public R<WorkspaceMemberEntity> updateMemberRole(
            @Parameter(description = "工作区 ID") @PathVariable Long id,
            @Parameter(description = "目标用户 ID") @PathVariable Long targetUserId,
            @RequestBody Map<String, String> body) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_ADMIN);
        }
        String role = body.get("role");
        return R.ok(runtime.updateWorkspaceMemberRole(id, targetUserId, role));
    }

    /**
     * 移除工作区成员
     * <p>
     * 需要目标工作区的 admin 权限。
     */
    @DeleteMapping("/{id}/members/{targetUserId}")
    @Operation(summary = "移除成员", description = "从工作区移除成员，需要 admin 权限")
    public R<Void> removeMember(@Parameter(description = "工作区 ID") @PathVariable Long id,
                                 @Parameter(description = "目标用户 ID") @PathVariable Long targetUserId) {
        Long userId = workspaceGuard.currentUserId();
        if (!workspaceGuard.isCurrentAdmin()) {
            runtime.requireWorkspaceRole(id, userId, DataAgentConstants.WORKSPACE_ROLE_ADMIN);
        }
        runtime.removeWorkspaceMember(id, targetUserId);
        return R.ok();
    }
}
