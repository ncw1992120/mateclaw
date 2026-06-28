package vip.mate.dataagent.auth.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vip.mate.dataagent.auth.annotation.RequireGlobalAdmin;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.sdk.service.MateClawRuntime;

/**
 * DataAgent Workspace 访问拦截器
 * <p>
 * 对标注了 {@link RequireWorkspaceRole} 或 {@link RequireGlobalAdmin} 的 Controller 方法，
 * 自动校验当前用户的工作区权限或全局管理员身份。
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
        RequireWorkspaceRole roleAnnotation = handlerMethod.getMethodAnnotation(RequireWorkspaceRole.class);
        if (globalAdmin == null && roleAnnotation == null) {
            return true;
        }

        UserContext userContext = UserContextHolder.get();
        if (userContext == null) {
            sendForbidden(response, "用户未认证");
            return false;
        }

        // 全局管理员注解：必须是 mate_user.role=admin
        if (globalAdmin != null) {
            if (!userContext.isAdmin()) {
                log.warn("Global admin access denied: user={}, path={}", userContext.getUsername(), request.getRequestURI());
                sendForbidden(response, "需要全局管理员权限");
                return false;
            }
            return true;
        }

        // @RequireWorkspaceRole 分支：全局 admin 跳过
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
     * 返回 403 Forbidden 响应
     */
    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"" + message + "\",\"data\":null}");
    }
}
