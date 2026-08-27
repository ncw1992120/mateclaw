package vip.mate.dataagent.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.auth.LoginRateLimiter;
import vip.mate.dataagent.auth.crypto.TransportCryptoService;
import vip.mate.dataagent.auth.dto.AuthModeVO;
import vip.mate.dataagent.auth.dto.DataAgentLoginRequest;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;
import vip.mate.dataagent.auth.dto.PilotCaptchaVO;
import vip.mate.dataagent.auth.dto.PilotSsoRequest;
import vip.mate.dataagent.auth.enterprise.CaptchaChallenge;
import vip.mate.dataagent.auth.enterprise.EnterpriseAuthResult;
import vip.mate.dataagent.auth.enterprise.EnterpriseIdentityProvider;
import vip.mate.dataagent.auth.enterprise.PilotAuthProperties;
import vip.mate.dataagent.auth.enterprise.ShadowAccountService;
import vip.mate.dataagent.auth.service.DataAgentAuthService;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.model.WorkspaceWithRoleVO;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.List;

/**
 * DataAgent 认证服务实现（本地 / 企业认证双轨）
 * <p>
 * 路由规则：
 * <ol>
 *   <li>请求显式携带 channel=local（前端"本地账号登录"表单）→ 仅白名单账号可走本地校验，
 *       白名单外账号返回 403 并引导其使用企业通道；</li>
 *   <li>企业认证未启用（mateclaw.pilot.enabled=false）→ 本地账密校验（现状逻辑）；</li>
 *   <li>企业认证已启用且用户名不在本地兜底白名单 → 领航账密代验 + 影子账号 + 复用 JWT 签发；</li>
 *   <li>命中兜底白名单（默认 admin）→ 仍走本地密码校验，防企业侧故障锁死管理员。</li>
 * </ol>
 * 企业路径的 JWT 通过 {@link AuthService#renewToken(String)} 签发，
 * 与本地登录产出完全一致，下游过滤器/拦截器零感知。
 * <p>
 * 横切能力：登录失败按用户名限速（{@link LoginRateLimiter}，仅凭据类失败计数），
 * 成功登录以 info 日志记录实际生效的认证通道（LOCAL / PILOT_UM / PILOT_AD）供审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentAuthServiceImpl implements DataAgentAuthService {

    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final EnterpriseIdentityProvider enterpriseIdentityProvider;
    private final ShadowAccountService shadowAccountService;
    private final PilotAuthProperties pilotAuthProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final TransportCryptoService transportCryptoService;

    @Override
    public DataAgentLoginResponse login(DataAgentLoginRequest request) {
        // 传输加密：口令为 RSA-OAEP 信封，解密还原为明文后再进入本地/领航校验。
        // 解密失败（格式/过期）返回 400，不计入登录限速。
        request.setPassword(transportCryptoService.unwrapField(request.getPassword()));
        String username = request.getUsername();
        if (loginRateLimiter.isBlocked(username)) {
            throw new MateClawException("err.auth.too_many_attempts", 403, "登录失败次数过多，请稍后再试");
        }
        // channel=local：前端"本地账号登录"表单的显式通道选择，跳过领航直接本地校验
        boolean forceLocal = "local".equalsIgnoreCase(request.getChannel());
        boolean enterpriseOn = enterpriseIdentityProvider.enabled();
        // 本地通道仅向兜底白名单开放：白名单外账号给出明确引导，
        // 而不是落到领航/本地校验后返回误导性的"用户名或密码错误"
        if (forceLocal && enterpriseOn && !isLocalFallbackUser(username)) {
            throw new MateClawException("err.auth.local_channel_forbidden", 403,
                    "该账号未开通本地登录，请使用「开机账号登录」或「UM 账号登录」");
        }
        try {
            DataAgentLoginResponse response =
                    !forceLocal && enterpriseOn && !isLocalFallbackUser(username)
                            ? enterpriseLogin(request)
                            : localLogin(request);
            loginRateLimiter.recordSuccess(username);
            return response;
        } catch (MateClawException e) {
            // 仅凭据类失败（401）计入限速：验证码流程(429)是正常分支、基础设施故障(502)不可归因于用户
            if (e.getCode() == 401) {
                loginRateLimiter.recordFailure(username);
            }
            throw e;
        }
    }

    /**
     * 企业账密代验：领航校验（UM/AD 由请求 authnType 指定）→ 影子账号 → 签发与本地一致的 JWT
     */
    private DataAgentLoginResponse enterpriseLogin(DataAgentLoginRequest request) {
        EnterpriseAuthResult result = enterpriseIdentityProvider.authenticate(
                request.getUsername(), request.getPassword(),
                request.getRequestId(), request.getValidCode(), request.getAuthnType());
        switch (result.status()) {
            case NEED_CAPTCHA:
                // HTTP 429：前端据此展示图形验证码并拉取 /v1/auth/captcha
                throw new MateClawException("err.auth.need_captcha", 429, "需要图形验证码");
            case WRONG_CAPTCHA:
                // 401 且登录接口：前端提示并刷新验证码，不触发登出跳转
                throw new MateClawException("err.auth.wrong_captcha", 401, "图形验证码错误，请重新输入");
            case AUTH_FAILED:
                // 与本地登录保持同一文案，避免泄露"用户不存在/密码错误"差异
                throw new MateClawException("err.auth.invalid_credentials", 401, "用户名或密码错误");
            default:
                break;
        }

        UserEntity user = shadowAccountService.ensureShadowAccount(result.user());
        String token = authService.renewToken(user.getUsername());
        if (token == null) {
            throw new MateClawException("err.auth.user_disabled", 403, "该账号已被禁用，请联系管理员");
        }
        log.info("[Auth] login success user=[{}] channel=[PILOT_{}]",
                user.getUsername(), result.authnType());
        return buildResponse(user.getId(), token, user.getUsername(),
                user.getNickname(), user.getRole());
    }

    /**
     * 本地账密校验（现状逻辑，委托 server 的 AuthService）
     */
    private DataAgentLoginResponse localLogin(DataAgentLoginRequest request) {
        var base = authService.login(request);
        log.info("[Auth] login success user=[{}] channel=[LOCAL]", base.getUsername());
        return buildResponse(base.getId(), base.getToken(), base.getUsername(),
                base.getNickname(), base.getRole());
    }

    @Override
    public DataAgentLoginResponse getCurrentUserInfo(String username) {
        UserEntity user = authService.findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return null;
        }
        return buildResponse(user.getId(), null, user.getUsername(),
                user.getNickname(), user.getRole());
    }

    @Override
    public PilotCaptchaVO fetchCaptcha(String authnType) {
        CaptchaChallenge challenge = enterpriseIdentityProvider.fetchCaptcha(authnType);
        return new PilotCaptchaVO(challenge.requestId(), challenge.imageBase64());
    }

    @Override
    public AuthModeVO getAuthMode() {
        if (enterpriseIdentityProvider.enabled()) {
            return new AuthModeVO("pilot", List.of("UM", "AD"), pilotAuthProperties.getSsoCookieName());
        }
        return new AuthModeVO("local", List.of(), null);
    }

    @Override
    public DataAgentLoginResponse loginBySso(PilotSsoRequest request) {
        EnterpriseAuthResult result = enterpriseIdentityProvider.authenticateBySso(
                transportCryptoService.unwrapField(request.getSsoCookie()), request.getAuthnType());
        if (result.status() != EnterpriseAuthResult.Status.SUCCESS || result.user() == null) {
            // 统一文案：不区分票据无效/过期/账号不存在，防探测
            throw new MateClawException("err.auth.sso_invalid", 401, "企业统一身份校验失败，请重新登录");
        }
        UserEntity user = shadowAccountService.ensureShadowAccount(result.user());
        String token = authService.renewToken(user.getUsername());
        if (token == null) {
            throw new MateClawException("err.auth.user_disabled", 403, "该账号已被禁用，请联系管理员");
        }
        log.info("[Auth] login success user=[{}] channel=[PILOT_SSO]", user.getUsername());
        return buildResponse(user.getId(), token, user.getUsername(),
                user.getNickname(), user.getRole());
    }

    @Override
    public void renewSsoSession(PilotSsoRequest request) {
        String ssoCookie = request.getSsoCookie();
        if (ssoCookie != null && !ssoCookie.isBlank()) {
            ssoCookie = transportCryptoService.unwrapField(ssoCookie);
        }
        enterpriseIdentityProvider.renewSso(ssoCookie, request.getAuthnType());
    }

    /**
     * 本地兜底白名单：企业模式下这些用户名仍走本地密码校验
     */
    private boolean isLocalFallbackUser(String username) {
        List<String> fallback = pilotAuthProperties.getLocalFallbackUsers();
        if (fallback == null || fallback.isEmpty()) {
            return false;
        }
        return fallback.stream().anyMatch(u -> u.equalsIgnoreCase(username));
    }

    /**
     * 组装登录响应（含工作区列表）
     */
    private DataAgentLoginResponse buildResponse(Long id, String token, String username,
                                                  String nickname, String role) {
        boolean isGlobalAdmin = DataAgentConstants.ROLE_ADMIN.equalsIgnoreCase(role);
        List<WorkspaceWithRoleVO> workspaces = workspaceService.listWithRoleByUserId(id, isGlobalAdmin);

        DataAgentLoginResponse response = new DataAgentLoginResponse();
        response.setId(id);
        response.setToken(token);
        response.setUsername(username);
        response.setNickname(nickname);
        response.setRole(role);
        response.setWorkspaces(workspaces);
        return response;
    }
}
