package vip.mate.dataagent.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.MateClawRuntime;

/**
 * 工作区权限校验守卫
 * <p>
 * 供 Service 层显式调用，校验当前用户在当前工作区的角色权限。
 * 全局管理员自动放行。
 */
@Component
@RequiredArgsConstructor
public class WorkspaceGuard {

    private final MateClawRuntime mateClawRuntime;

    /**
     * 断言当前用户具有指定最低角色权限，不通过则抛 403 异常
     *
     * @param minRole 最低角色要求：owner &gt; admin &gt; member &gt; viewer
     */
    public void requireRole(String minRole) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("用户上下文未初始化");
        }
        if (ctx.isAdmin()) {
            return;
        }
        Long workspaceId = ctx.getWorkspaceId() != null ? ctx.getWorkspaceId() : DataAgentConstants.DEFAULT_WORKSPACE_ID;
        mateClawRuntime.requireWorkspaceRole(workspaceId, ctx.getUserId(), minRole);
    }

    /**
     * 检查当前用户是否具有指定最低角色权限
     *
     * @param minRole 最低角色要求
     * @return true 如果有足够权限
     */
    public boolean hasRole(String minRole) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            return false;
        }
        if (ctx.isAdmin()) {
            return true;
        }
        Long workspaceId = ctx.getWorkspaceId() != null ? ctx.getWorkspaceId() : DataAgentConstants.DEFAULT_WORKSPACE_ID;
        return mateClawRuntime.hasWorkspacePermission(workspaceId, ctx.getUserId(), minRole);
    }

    /**
     * 获取当前工作区 ID
     */
    public Long currentWorkspaceId() {
        UserContext ctx = UserContextHolder.get();
        if (ctx != null && ctx.getWorkspaceId() != null) {
            return ctx.getWorkspaceId();
        }
        return DataAgentConstants.DEFAULT_WORKSPACE_ID;
    }

    /**
     * 获取当前用户 ID
     */
    public Long currentUserId() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("用户上下文未初始化");
        }
        return ctx.getUserId();
    }

    /**
     * 获取当前用户名
     */
    public String currentUsername() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("用户上下文未初始化");
        }
        return ctx.getUsername();
    }

    /**
     * 当前用户是否为全局管理员
     */
    public boolean isCurrentAdmin() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null && ctx.isAdmin();
    }

    /**
     * 获取指定用户在工作区中的成员角色
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @return 角色标识（owner/admin/member/viewer），非成员返回 null
     */
    public String getWorkspaceMemberRole(Long workspaceId, Long userId) {
        return mateClawRuntime.getWorkspaceMemberRole(workspaceId, userId);
    }
}
