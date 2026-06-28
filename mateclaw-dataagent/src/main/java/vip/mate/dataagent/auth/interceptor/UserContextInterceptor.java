package vip.mate.dataagent.auth.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;

/**
 * 用户上下文拦截器
 * <p>
 * 在 Spring Security 认证完成后、Controller 执行前，从 SecurityContext 中
 * 解析当前用户身份，并从请求头读取工作区 ID，填充到 {@link UserContextHolder}。
 * 请求结束时清理 ThreadLocal，防止内存泄漏。
 */
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String username) {
            UserEntity user = authService.findByUsername(username);
            if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
                Long workspaceId = parseWorkspaceId(request.getHeader(DataAgentConstants.HEADER_WORKSPACE_ID));
                UserContext context = new UserContext(
                        user.getId(), user.getUsername(), user.getRole(), workspaceId);
                UserContextHolder.set(context);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    /**
     * 解析工作区 ID，为空时回退到默认工作区
     */
    private Long parseWorkspaceId(String headerValue) {
        if (StringUtils.hasText(headerValue)) {
            try {
                return Long.parseLong(headerValue);
            } catch (NumberFormatException e) {
                return DataAgentConstants.DEFAULT_WORKSPACE_ID;
            }
        }
        return DataAgentConstants.DEFAULT_WORKSPACE_ID;
    }
}
