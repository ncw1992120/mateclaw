package vip.mate.dataagent.auth.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vip.mate.dataagent.auth.annotation.RequireGlobalAdmin;
import vip.mate.dataagent.auth.annotation.RequirePermission;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.permission.DataAgentPermission;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.MateClawRuntime;

/**
 * DataAgent Workspace 访问拦截器
 * <p>
 * 支持三种权限注解（优先级从高到低）：
 * <ol>
 *   <li>{@link RequireGlobalAdmin}：仅全局 admin（mate_user.role=admin）可访问</li>
 *   <li>{@link RequirePermission}：按权限点-角色映射矩阵校验（细粒度）</li>
 *   <li>{@link RequireWorkspaceRole}：按工作区角色等级校验（粗粒度）</li>
 * </ol>
 * <p>
 * 依赖 {@link UserContextInterceptor} 先填充 {@link UserContextHolder}，
 * 本拦截器注册顺序必须在其之后。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAgentWorkspaceInterceptor implements HandlerInterceptor {

    private final MateClawRuntime mateClawRuntime;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireGlobalAdmin globalAdmin = handlerMethod.getMethodAnnotation(RequireGlobalAdmin.class);
        RequirePermission permissionAnnotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        RequireWorkspaceRole roleAnnotation = handlerMethod.getMethodAnnotation(RequireWorkspaceRole.class);
        if (globalAdmin == null && permissionAnnotation == null && roleAnnotation == null) {
            return true;
        }

        UserContext userContext = UserContextHolder.get();
        if (userContext == null) {
            sendForbidden(response, "用户未认证");
            return false;
        }

        // @RequireGlobalAdmin：必须是 mate_user.role=admin
        if (globalAdmin != null) {
            if (!userContext.isAdmin()) {
                log.warn("Global admin access denied: user={}, path={}", userContext.getUsername(), request.getRequestURI());
                sendForbidden(response, "需要全局管理员权限");
                return false;
            }
            return true;
        }

        // @RequirePermission：按权限点-角色映射矩阵校验
        if (permissionAnnotation != null) {
            String permCode = permissionAnnotation.value();
            DataAgentPermission permission = DataAgentPermission.fromCode(permCode);
            if (permission == null) {
                log.error("Unknown permission code: {}, path={}", permCode, request.getRequestURI());
                sendForbidden(response, "权限点未定义：" + permCode);
                return false;
            }
            // 获取用户在工作区的生效角色（全局 admin 在 isAllowed 中自动放行）
            String effectiveRole = resolveEffectiveRole(userContext);
            if (!permission.isAllowed(effectiveRole, userContext.isAdmin())) {
                log.warn("Permission denied: user={}, permission={}, role={}, path={}",
                        userContext.getUsername(), permCode, effectiveRole, request.getRequestURI());
                sendForbidden(response, "权限不足：需要 " + permCode + " 权限");
                return false;
            }
            return true;
        }

        // @RequireWorkspaceRole：按角色等级校验，全局 admin 跳过
        if (userContext.isAdmin()) {
            return true;
        }

        Long workspaceId = userContext.getWorkspaceId() != null
                ? userContext.getWorkspaceId()
                : DataAgentConstants.DEFAULT_WORKSPACE_ID;
        String minRole = roleAnnotation.value();

        if (!mateClawRuntime.hasWorkspacePermission(workspaceId, userContext.getUserId(), minRole)) {
            log.warn("Workspace access denied: user={}, workspaceId={}, requiredRole={}",
                    userContext.getUsername(), workspaceId, minRole);
            sendForbidden(response, "工作区权限不足：需要 " + minRole + " 或更高角色");
            return false;
        }

        return true;
    }

    /**
     * 解析当前用户在工作区的生效角色
     * <p>
     * 全局 admin 的生效角色为 owner（在 isAllowed 中会自动放行，此处仅为记录用）；
     * 普通用户取工作区成员角色，无工作区上下文则返回 null。
     */
    private String resolveEffectiveRole(UserContext userContext) {
        if (userContext.isAdmin()) {
            return DataAgentConstants.WORKSPACE_ROLE_OWNER;
        }
        Long workspaceId = userContext.getWorkspaceId() != null
                ? userContext.getWorkspaceId()
                : DataAgentConstants.DEFAULT_WORKSPACE_ID;
        return mateClawRuntime.getWorkspaceMemberRole(workspaceId, userContext.getUserId());
    }

    /**
     * 返回 403 Forbidden 响应
     */
    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"" + message + "\",\"data\":null}");
    }
}
