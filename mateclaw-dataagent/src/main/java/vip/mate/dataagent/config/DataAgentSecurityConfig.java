package vip.mate.dataagent.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vip.mate.config.JwtAuthFilter;
import vip.mate.dataagent.constants.DataAgentConstants;

/**
 * DataAgent Spring Security 配置
 * <p>
 * 由于 dataagent 的 context-path 为 {@code /dataagent/api}，实际接口路径为 {@code /v1/**}，
 * mateclaw-server 的 SecurityConfig 仅保护 {@code /api/**}，无法覆盖 dataagent 接口。
 * 本配置以 {@code @Order(1)} + {@code securityMatcher("/v1/**")} 优先匹配 dataagent 路径，
 * 复用 mateclaw-server 的 {@link JwtAuthFilter} 完成 JWT/PAT 认证。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataAgentSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /**
     * DataAgent 接口安全过滤链（优先级高于 mateclaw-server 默认链）
     */
    @Bean
    @Order(1)
    public SecurityFilterChain dataAgentFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v1/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers(DataAgentConstants.AUTH_LOGIN_PATH).permitAll()
                // 企业认证图形验证码：登录 429 后由前端拉取，UUID/requestId 即访问凭证
                .requestMatchers(DataAgentConstants.AUTH_CAPTCHA_PATH).permitAll()
                // 认证模式查询：登录页初始化时判断是否展示企业认证选择器
                .requestMatchers(DataAgentConstants.AUTH_MODE_PATH).permitAll()
                // 企业 SSO 免登：持有效领航票据即可换取本地会话
                .requestMatchers(DataAgentConstants.AUTH_SSO_LOGIN_PATH).permitAll()
                // 工具生成文件下载端点，UUID 即为访问凭证，无需认证
                .requestMatchers("/v1/files/generated/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("[DataAgentSecurity] Authentication failed for {}: {}",
                            request.getRequestURI(), authException.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"Token expired or invalid\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("[DataAgentSecurity] Access denied for {}: {}",
                            request.getRequestURI(), accessDeniedException.getMessage());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"Access Denied\",\"data\":null}");
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
