package vip.mate.dataagent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vip.mate.dataagent.auth.interceptor.DataAgentWorkspaceInterceptor;
import vip.mate.dataagent.auth.interceptor.UserContextInterceptor;
import vip.mate.dataagent.audit.DataAgentAuditInterceptor;
import vip.mate.dataagent.protection.HighCostRateLimitInterceptor;

/**
 * DataAgent Web MVC 配置
 * <p>
 * 注册用户上下文拦截器和工作区权限拦截器，为 {@code /v1/**} 路径提供身份与权限校验。
 * 与 mateclaw-server 的 WebMvcConfig 共存，各自管理不同路径段。
 */
@Configuration
@RequiredArgsConstructor
public class DataAgentWebMvcConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;
    private final DataAgentWorkspaceInterceptor dataAgentWorkspaceInterceptor;
    private final DataAgentAuditInterceptor dataAgentAuditInterceptor;
    private final HighCostRateLimitInterceptor highCostRateLimitInterceptor;

    /** CORS 允许的来源，逗号分隔 */
    @Value("${mateclaw.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 用户上下文拦截器：填充 UserContextHolder（必须先于权限拦截器执行）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/auth/login", "/error")
                .order(0);

        // 2. 工作区权限拦截器：校验 @RequireWorkspaceRole / @RequireGlobalAdmin 注解
        registry.addInterceptor(dataAgentWorkspaceInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/auth/login", "/error")
                .order(1);

        // 3. 操作审计拦截器：记录写请求（POST/PUT/PATCH/DELETE）到 mate_audit_event
        registry.addInterceptor(dataAgentAuditInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/auth/login", "/error")
                .order(2);

        // 4. 高成本端点限流拦截器：LLM 生成类写请求（提示词优化/洞察生成）按用户限频，
        //    注册于用户上下文拦截器之后，可直接读取 UserContextHolder
        registry.addInterceptor(highCostRateLimitInterceptor)
                .addPathPatterns("/v1/chat/optimize/**", "/v1/insight/**")
                .order(3);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
