package vip.mate.client.provider;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录式 Token 提供者
 * <p>
 * 通过用户名密码自动登录获取 JWT，支持自动续签和重新登录
 */
public class LoginTokenProvider implements TokenProvider {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final RestTemplate restTemplate;
    private volatile String token;

    public LoginTokenProvider(String baseUrl, String username, String password, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.restTemplate = restTemplate;
    }

    @Override
    public String getToken() {
        if (token == null) {
            login();
        }
        return token;
    }

    @Override
    public void refreshToken() {
        login();
    }

    /**
     * 更新 Token（用于 X-New-Token 续签场景）
     *
     * @param newToken 新 Token
     */
    public void updateToken(String newToken) {
        this.token = newToken;
    }

    /**
     * 执行登录
     */
    private void login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        R<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + ApiPathConstants.AUTH_LOGIN,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<R<Map<String, Object>>>() {}
        ).getBody();

        if (response != null && response.getData() != null) {
            this.token = (String) response.getData().get("token");
        }
    }
}
