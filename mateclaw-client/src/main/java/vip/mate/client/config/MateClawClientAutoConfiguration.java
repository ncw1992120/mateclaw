package vip.mate.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.MateClawClient;
import vip.mate.client.properties.MateClawClientProperties;
import vip.mate.client.provider.LoginTokenProvider;
import vip.mate.client.provider.StaticTokenProvider;
import vip.mate.client.provider.TokenProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MateClaw 客户端自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(MateClawClientProperties.class)
public class MateClawClientAutoConfiguration {

    /**
     * 配置 RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate mateclawRestTemplate(MateClawClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return new RestTemplate(factory);
    }

    /**
     * 配置 TokenProvider
     * <p>
     * 优先级：username/password > token
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenProvider mateclawTokenProvider(MateClawClientProperties properties) {
        if (properties.getUsername() != null && !properties.getUsername().isBlank()
                && properties.getPassword() != null && !properties.getPassword().isBlank()) {
            // 使用独立的 RestTemplate 登录，避免循环依赖
            RestTemplate loginRestTemplate = new RestTemplate(createClientHttpRequestFactory(properties));
            return new LoginTokenProvider(properties.getBaseUrl(),
                    properties.getUsername(), properties.getPassword(), loginRestTemplate);
        } else if (properties.getToken() != null && !properties.getToken().isBlank()) {
            return new StaticTokenProvider(properties.getToken());
        } else {
            throw new IllegalStateException("mateclaw.client.username/password 或 mateclaw.client.token 必须配置其一");
        }
    }

    /**
     * 配置 MateClawClient
     */
    @Bean
    @ConditionalOnMissingBean
    public MateClawClient mateclawClient(MateClawClientProperties properties,
                                         RestTemplate mateclawRestTemplate,
                                         TokenProvider mateclawTokenProvider) {
        // 添加认证拦截器
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(mateclawRestTemplate.getInterceptors());
        interceptors.add(new TokenAuthInterceptor(mateclawTokenProvider, properties));
        mateclawRestTemplate.setInterceptors(interceptors);

        return new MateClawClient(properties, mateclawRestTemplate);
    }

    /**
     * 创建 ClientHttpRequestFactory
     */
    private ClientHttpRequestFactory createClientHttpRequestFactory(MateClawClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    /**
     * Token 认证拦截器
     * <p>
     * 负责添加 Authorization 头、处理 X-New-Token 续签、401 自动重试
     */
    private record TokenAuthInterceptor(TokenProvider tokenProvider,
                                        MateClawClientProperties properties) implements ClientHttpRequestInterceptor {

        private static final String AUTHORIZATION_HEADER = "Authorization";
        private static final String BEARER_PREFIX = "Bearer ";
        private static final String NEW_TOKEN_HEADER = "X-New-Token";
        private static final String WORKSPACE_HEADER = "X-Workspace-Id";
        private static final int UNAUTHORIZED_STATUS = 401;

        @Override
        public ClientHttpResponse intercept(HttpRequest request,
                                            byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            // 添加 Token
            String token = tokenProvider.getToken();
            if (token != null && !token.isBlank()) {
                request.getHeaders().set(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
            }

            // 添加工作区 ID
            if (properties.getDefaultWorkspaceId() != null) {
                request.getHeaders().set(WORKSPACE_HEADER, String.valueOf(properties.getDefaultWorkspaceId()));
            }

            ClientHttpResponse response = execution.execute(request, body);

            // 检查 X-New-Token 续签
            String newToken = response.getHeaders().getFirst(NEW_TOKEN_HEADER);
            if (newToken != null && !newToken.isBlank()) {
                updateToken(newToken);
            }

            // 检查 401 响应，自动重试
            if (response.getStatusCode().value() == UNAUTHORIZED_STATUS && tokenProvider instanceof LoginTokenProvider) {
                // 关闭原响应
                response.close();
                // 刷新 Token
                tokenProvider.refreshToken();
                String refreshedToken = tokenProvider.getToken();
                if (refreshedToken != null && !refreshedToken.isBlank()) {
                    // 更新请求头并重试
                    request.getHeaders().set(AUTHORIZATION_HEADER, BEARER_PREFIX + refreshedToken);
                    return execution.execute(request, body);
                }
            }

            return response;
        }

        /**
         * 更新 Token
         */
        private void updateToken(String newToken) {
            if (tokenProvider instanceof StaticTokenProvider) {
                ((StaticTokenProvider) tokenProvider).updateToken(newToken);
            } else if (tokenProvider instanceof LoginTokenProvider) {
                ((LoginTokenProvider) tokenProvider).updateToken(newToken);
            }
        }
    }
}
