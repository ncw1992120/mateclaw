package vip.mate.dataagent.protection;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.service.UserAdmissionService;

/**
 * 高成本端点限流拦截器
 * <p>
 * 对 LLM 生成类写请求（提示词优化、洞察报告/仪表盘 AI 生成）按用户维度限频，
 * 防止单用户高频触发 LLM 调用拖垮服务。GET 浏览/查询请求不拦截。
 * <p>
 * 注册路径见 {@code DataAgentWebMvcConfig}；注册于用户上下文拦截器（order 0）之后，
 * 可直接读取 UserContextHolder。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HighCostRateLimitInterceptor implements HandlerInterceptor {

    private final UserAdmissionService userAdmissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅拦截高成本的写请求（LLM 生成类），GET/DELETE 等不占用 LLM 资源
        if (!HttpMethod.POST.matches(request.getMethod())
                && !HttpMethod.PUT.matches(request.getMethod())) {
            return true;
        }
        String scene = resolveScene(request);
        if (scene == null) {
            return true;
        }
        UserContext user = UserContextHolder.get();
        if (user == null || user.getUsername() == null) {
            // 未登录请求交由工作区权限拦截器处理，此处不重复拦截
            return true;
        }
        userAdmissionService.checkHighCostAccess(user.getUsername(), scene);
        return true;
    }

    /**
     * 按剥离 context-path 后的路径前缀解析限流场景；不在保护范围返回 null
     */
    private String resolveScene(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(DataAgentConstants.PROTECTION_PATH_CHAT_OPTIMIZE)) {
            return DataAgentConstants.RATE_LIMIT_SCENE_CHAT_OPTIMIZE;
        }
        if (path.startsWith(DataAgentConstants.PROTECTION_PATH_INSIGHT)) {
            return DataAgentConstants.RATE_LIMIT_SCENE_INSIGHT;
        }
        return null;
    }
}
