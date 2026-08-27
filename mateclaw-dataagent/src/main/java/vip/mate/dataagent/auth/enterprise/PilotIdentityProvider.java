package vip.mate.dataagent.auth.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import vip.mate.exception.MateClawException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平安领航认证提供者（私有 CAS-like 协议，账密代验模式）
 * <p>
 * 协议要点（对接契约）：
 * <ul>
 *   <li>POST {auth-server}：username/password/authnType/lifeTime，
 *       additionalInfo 携带 requestId+validCode（风控要求验证码时）；
 *       authnType 支持 UM（域账号）/ AD（用户主机账号），验证码接口的
 *       authnMechanism 传同一值</li>
 *   <li>成功判定：code == 0 且 message != NEED_RAND_CODE（code 兼容数字 0 与字符串 "0"）</li>
 *   <li>NEED_RAND_CODE → 服务层翻译为 HTTP 429，前端展示验证码；
 *       WRONG_IMAGE_CODE → 401 并刷新验证码</li>
 *   <li>断言接口不返回用户标识——认证通过即以登录表单输入的账号为身份；
 *       用户标识仅由 SSO 校验接口返回（userName/PRINCIPAL_NAME）</li>
 * </ul>
 * 安全约定：口令仅在内存中转发给领航服务，本类任何日志不得输出请求体与口令。
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
public class PilotIdentityProvider implements EnterpriseIdentityProvider {

    /** 领航风控标记：需要图形验证码 */
    static final String MSG_NEED_RAND_CODE = "NEED_RAND_CODE";
    /** 领航风控标记：图形验证码错误 */
    static final String MSG_WRONG_IMAGE_CODE = "WRONG_IMAGE_CODE";

    /** 认证类型：UM = 域账号口令 */
    public static final String AUTH_TYPE_UM = "UM";
    /** 认证类型：AD = 用户主机账号口令 */
    public static final String AUTH_TYPE_AD = "AD";

    private static final java.util.Set<String> SUPPORTED_AUTH_TYPES =
            java.util.Set.of(AUTH_TYPE_UM, AUTH_TYPE_AD);

    /**
     * 全信任 SSL 上下文（联调期策略）：单例创建，避免每请求重建。
     * 生产上线前必须替换为平安 CA 受信方案（导入 cacerts/独立 truststore），
     * 并关闭主机名绕过——TLS 拦截将直接暴露转发的明文口令。
     */
    private static final SSLContext TRUST_ALL_SSL_CONTEXT = createTrustAllSslContext();

    /** 绕过域名校验（与全信任配套，仅联调期使用） */
    private static final HostnameVerifier TRUST_ALL_HOSTNAME_VERIFIER = (hostname, session) -> true;

    private static SSLContext createTrustAllSslContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create trust-all SSLContext", e);
        }
    }

    private final PilotAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public PilotIdentityProvider(PilotAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConn = (HttpsURLConnection) connection;
                    httpsConn.setSSLSocketFactory(TRUST_ALL_SSL_CONTEXT.getSocketFactory());
                    httpsConn.setHostnameVerifier(TRUST_ALL_HOSTNAME_VERIFIER);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        long timeoutMs = properties.getTimeout() != null
                ? properties.getTimeout().toMillis()
                : 5000L;
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled()
                && properties.getAuthServer() != null
                && !properties.getAuthServer().isBlank();
    }

    @Override
    public EnterpriseAuthResult authenticate(String username, String password,
                                             String requestId, String validCode, String authnType) {
        String effectiveAuthnType = resolveAuthnType(authnType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        // 领航线上的报文字段名为 authnType，取值为 UM/AD
        body.put("authnType", effectiveAuthnType);
        body.put("lifeTime", properties.getLifeTime());
        Map<String, Object> additionalInfo = new LinkedHashMap<>();
        additionalInfo.put("requestId", requestId);
        additionalInfo.put("validCode", validCode);
        body.put("additionalInfo", additionalInfo);

        log.info("[PilotAuth] auth attempt for account [{}] with authnType [{}]", username, effectiveAuthnType);
        JsonNode root = postForJson("auth", properties.getAuthServer(), body);

        int code = readCode(root);
        String message = root.path("message").asText("");

        if (MSG_NEED_RAND_CODE.equalsIgnoreCase(message)) {
            log.info("[PilotAuth] risk control requires captcha for account [{}]", username);
            return EnterpriseAuthResult.needCaptcha();
        }
        if (MSG_WRONG_IMAGE_CODE.equalsIgnoreCase(message)) {
            log.info("[PilotAuth] wrong image code for account [{}]", username);
            return EnterpriseAuthResult.wrongCaptcha();
        }
        if (code != 0) {
            // 统一按凭据无效处理，不向前端泄露领航侧具体 message，防账号探测
            log.warn("[PilotAuth] auth rejected by pilot for account [{}]: code={}, message={}",
                    username, code, message);
            return EnterpriseAuthResult.authFailed();
        }

        // 契约事实：断言接口不返回 PRINCIPAL_NAME（仅 SSO 校验接口返回用户标识）。
        // 认证通过即证明请求账号的口令有效，身份直接取自登录表单输入；
        // 若个别环境在响应中携带 userName/PRINCIPAL_NAME，则优先采用（防御性兼容）。
        String principalName = firstNonBlank(
                root.path("content").path("PRINCIPAL_NAME").asText(null),
                root.path("content").path("userName").asText(null),
                username);
        String displayName = root.path("content").path("DISPLAY_NAME").asText(null);
        log.info("[PilotAuth] login success for account [{}]", principalName);
        return EnterpriseAuthResult.success(new EnterpriseUserInfo(principalName, displayName), effectiveAuthnType);
    }

    @Override
    public CaptchaChallenge fetchCaptcha(String authnType) {
        String effectiveAuthnType = resolveAuthnType(authnType);
        // 领航验证码接口的 authnMechanism 与认证类型传同一值（UM/AD）
        Map<String, Object> body = Map.of("authnMechanism", effectiveAuthnType);
        log.info("[PilotAuth] fetching captcha with authnMechanism [{}]", effectiveAuthnType);
        JsonNode root = postForJson("captcha", properties.getCaptchaServer(), body);
        // 契约未明确 requestId/captchaImage 位于根级还是 content 内，做双层兼容提取
        JsonNode payload = root.hasNonNull("requestId") ? root : root.path("content");
        String requestId = payload.path("requestId").asText("");
        String imageBase64 = payload.path("captchaImage").asText("");
        if (requestId.isBlank() || imageBase64.isBlank()) {
            log.error("[PilotAuth] captcha response missing requestId/captchaImage, raw keys={}",
                    fieldNames(root));
            throw new MateClawException("err.auth.sso_bad_response", 502, "企业认证返回数据异常");
        }
        return new CaptchaChallenge(requestId, imageBase64);
    }

    /** 领航 SSO Cookie 类型（契约固定值） */
    static final String SSO_TYPE_CAS_COOKIE = "CAS_SSO_COOKIE";

    @Override
    public EnterpriseAuthResult authenticateBySso(String ssoCookie, String authnType) {
        if (ssoCookie == null || ssoCookie.isBlank()) {
            throw new MateClawException("err.auth.sso_invalid", 401, "企业统一身份校验失败，请重新登录");
        }
        String effectiveAuthnType = resolveAuthnType(authnType);
        Map<String, Object> body = new LinkedHashMap<>();
        // 领航线上的报文字段名为 authnType
        body.put("authnType", effectiveAuthnType);
        // SSO 断言同样需要 lifeTime（与账密断言一致）
        body.put("lifeTime", properties.getLifeTime());
        // SSO Cookie 属于敏感凭据：不落日志
        body.put("ssoCookie", ssoCookie);
        body.put("ssoType", SSO_TYPE_CAS_COOKIE);

        log.info("[PilotAuth] sso assertion attempt with authnType [{}]", effectiveAuthnType);
        JsonNode root = postForJson("sso", properties.getSsoServer(), body);

        int code = readCode(root);
        // 契约样例带 isSuccess 布尔位；字段缺失时退化为仅以 code 判定
        boolean isSuccess = !root.has("isSuccess") || root.path("isSuccess").asBoolean(false);
        // 用户标识主字段为 userName；防御性回退 content.userName / content.PRINCIPAL_NAME
        String userName = root.path("userName").asText(
                root.path("content").path("userName").asText(
                        root.path("content").path("PRINCIPAL_NAME").asText("")));

        if (code == 0 && isSuccess && !userName.isBlank()) {
            log.info("[PilotAuth] sso login success for principal [{}]", userName);
            return EnterpriseAuthResult.success(new EnterpriseUserInfo(userName, null), effectiveAuthnType);
        }
        log.warn("[PilotAuth] sso assertion rejected: code={}, isSuccess={}", code, isSuccess);
        return EnterpriseAuthResult.authFailed();
    }

    @Override
    public void renewSso(String ssoCookie, String authnType) {
        if (ssoCookie == null || ssoCookie.isBlank()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authnType", resolveAuthnType(authnType));
        body.put("ssoType", SSO_TYPE_CAS_COOKIE);
        body.put("ssoCookie", ssoCookie);
        JsonNode root = postForJson("sso-renewal", properties.getSsoRenewalServer(), body);
        int code = readCode(root);
        if (code != 0) {
            // 续期失败不影响本地 JWT 会话，仅告警
            log.warn("[PilotAuth] sso renewal rejected: code={}, message={}", code, root.path("message").asText(""));
        } else {
            log.info("[PilotAuth] sso renewal ok");
        }
    }

    /**
     * POST JSON 并解析响应；网络/HTTP 异常统一收敛为 502（企业认证服务暂不可用）
     */
    private JsonNode postForJson(String action, String url, Map<String, Object> body) {
        if (url == null || url.isBlank()) {
            throw new MateClawException("err.auth.sso_misconfigured", 502,
                    "企业认证服务地址未配置");
        }
        try {
            String raw = restTemplate.postForObject(
                    url,
                    entity(body),
                    String.class);
            log.info("[PilotAuth] {} call completed", action);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RestClientResponseException e) {
            log.error("[PilotAuth] {} call failed: http {}, body suppressed", action, e.getStatusCode().value());
            throw new MateClawException("err.auth.sso_unavailable", 502, "企业认证服务暂不可用，请稍后重试或联系管理员");
        } catch (Exception e) {
            log.error("[PilotAuth] {} call error: {}", action, e.getClass().getSimpleName());
            throw new MateClawException("err.auth.sso_unavailable", 502, "企业认证服务暂不可用，请稍后重试或联系管理员");
        }
    }

    private HttpEntity<Map<String, Object>> entity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 解析生效的认证类型：请求指定值优先，否则回落配置默认值；不在支持列表内直接拒绝
     */
    private String resolveAuthnType(String requested) {
        String effective = (requested == null || requested.isBlank())
                ? properties.getAuthnType()
                : requested.trim().toUpperCase();
        if (!SUPPORTED_AUTH_TYPES.contains(effective)) {
            throw new MateClawException("err.auth.unsupported_auth_type", 400,
                    "不支持的认证类型: " + effective + "（支持 UM/AD）");
        }
        return effective;
    }

    /**
     * 领航各接口 code 类型不统一（断言接口为数字 0、SSO 接口为字符串 "0"），兼容解析
     */
    private int readCode(JsonNode root) {
        JsonNode code = root.path("code");
        if (code.isNumber()) {
            return code.asInt(-1);
        }
        if (code.isTextual()) {
            try {
                return Integer.parseInt(code.asText().trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** 依序返回第一个非空白值（全空返回 null） */
    private static String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String fieldNames(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        root.fieldNames().forEachRemaining(n -> sb.append(n).append(','));
        return sb.toString();
    }
}
