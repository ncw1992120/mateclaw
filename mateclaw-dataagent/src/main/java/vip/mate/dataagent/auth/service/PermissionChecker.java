package vip.mate.dataagent.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.service.ResourceGrantService;

/**
 * 统一权限校验器
 * <p>
 * 整合工作区角色权限与资源授权表，提供统一的权限校验入口。
 * 校验优先级：全局管理员 &gt; 工作区角色（admin/owner）&gt; 资源授权表。
 */
@Component
@RequiredArgsConstructor
public class PermissionChecker {

    private final WorkspaceGuard workspaceGuard;
    private final ResourceGrantService resourceGrantService;

    /**
     * 校验当前用户对指定资源是否具有指定权限
     * <p>
     * 校验链：
     * 1. 全局管理员 → 放行
     * 2. 工作区 owner/admin → 放行（工作区管理者拥有资源内所有权限）
     * 3. 资源授权表命中 → 放行
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param permission   权限：use / manage / publish
     * @return true 如果有权限
     */
    public boolean hasPermission(String resourceType, Long resourceId, String permission) {
        // 1. 全局管理员放行
        if (workspaceGuard.isCurrentAdmin()) {
            return true;
        }

        Long workspaceId = workspaceGuard.currentWorkspaceId();
        Long userId = workspaceGuard.currentUserId();

        // 2. 工作区 owner/admin 放行
        if (workspaceGuard.hasRole(DataAgentConstants.WORKSPACE_ROLE_ADMIN)) {
            return true;
        }

        // 3. 资源授权表校验：按用户授权
        if (resourceGrantService.checkPermission(workspaceId, resourceType, resourceId,
                DataAgentConstants.GRANT_TYPE_USER, String.valueOf(userId), permission)) {
            return true;
        }

        // 4. 资源授权表校验：按角色授权（用户在工作区中的角色）
        return false;
    }

    /**
     * 断言当前用户对指定资源具有指定权限，不通过则抛 403
     */
    public void requirePermission(String resourceType, Long resourceId, String permission) {
        if (!hasPermission(resourceType, resourceId, permission)) {
            throw new AccessDeniedException(
                    "权限不足：资源 " + resourceType + "/" + resourceId + " 需要 " + permission + " 权限");
        }
    }
}
