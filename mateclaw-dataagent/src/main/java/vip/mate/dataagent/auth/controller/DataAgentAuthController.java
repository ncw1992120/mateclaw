package vip.mate.dataagent.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.dto.AuthModeVO;
import vip.mate.dataagent.auth.dto.DataAgentLoginRequest;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;
import vip.mate.dataagent.auth.dto.PilotCaptchaVO;
import vip.mate.dataagent.auth.dto.PilotSsoRequest;
import vip.mate.dataagent.auth.service.DataAgentAuthService;

/**
 * DataAgent 认证接口
 * <p>
 * 复用 mateclaw-server 的用户体系（mate_user 表），提供登录、当前用户信息、修改密码接口。
 * 登录响应附带工作区列表，前端登录后可直接渲染工作区切换器。
 * <p>
 * 企业认证模式（mateclaw.pilot.enabled=true）下，/login 自动切换为领航账密代验：
 * 风控触发时返回 HTTP 429，前端调用 /captcha 拉取图形验证码后携带 requestId+validCode 重试。
 */
@Tag(name = "认证管理", description = "用户登录、当前用户信息、修改密码")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class DataAgentAuthController {

    private final DataAgentAuthService dataAgentAuthService;
    private final AuthService authService;

    @Operation(summary = "用户登录", description = "用户名密码登录（本地或企业认证代验），返回 JWT 令牌与可见工作区列表；企业风控要求验证码时返回 HTTP 429")
    @PostMapping("/login")
    public R<DataAgentLoginResponse> login(@RequestBody DataAgentLoginRequest request) {
        return R.ok(dataAgentAuthService.login(request));
    }

    @Operation(summary = "获取企业认证图形验证码",
            description = "登录返回 429 时调用，返回 requestId 与 Base64 PNG 图片；重试登录时回传 requestId + validCode。authnType 与登录时一致（UM/AD）")
    @GetMapping("/captcha")
    public R<PilotCaptchaVO> captcha(@RequestParam(value = "authnType", required = false) String authnType) {
        return R.ok(dataAgentAuthService.fetchCaptcha(authnType));
    }

    @Operation(summary = "查询认证模式", description = "登录页初始化用：provider=local 时隐藏企业认证选择器；pilot 时返回支持的认证类型列表")
    @GetMapping("/mode")
    public R<AuthModeVO> mode() {
        return R.ok(dataAgentAuthService.getAuthMode());
    }

    @Operation(summary = "企业SSO免登", description = "浏览器携带领航共享域 SSO Cookie 时调用，后端经 /v3/assertion/sso 校验后签发本地 JWT 与工作区列表；校验失败统一 401")
    @PostMapping("/sso/login")
    public R<DataAgentLoginResponse> ssoLogin(@RequestBody PilotSsoRequest request) {
        return R.ok(dataAgentAuthService.loginBySso(request));
    }

    @Operation(summary = "领航SSO会话续期", description = "活跃用户保持企业侧会话的透传接口；失败不影响本地 JWT 会话")
    @PostMapping("/sso/renewal")
    public R<Void> ssoRenewal(@RequestBody PilotSsoRequest request) {
        dataAgentAuthService.renewSsoSession(request);
        return R.ok();
    }

    @Operation(summary = "获取当前用户信息", description = "刷新页面后恢复用户状态，返回用户信息与工作区列表")
    @GetMapping("/me")
    public R<DataAgentLoginResponse> me(Authentication auth) {
        DataAgentLoginResponse info = dataAgentAuthService.getCurrentUserInfo(auth.getName());
        if (info == null) {
            return R.fail(401, "用户不存在或已禁用");
        }
        return R.ok(info);
    }

    @Operation(summary = "修改密码", description = "当前用户修改自己的密码，需提供原密码")
    @PutMapping("/password")
    public R<Void> changePassword(@RequestParam String oldPassword,
                                  @RequestParam String newPassword,
                                  Authentication auth) {
        UserEntity user = authService.findByUsername(auth.getName());
        if (user == null) {
            return R.fail(401, "用户不存在");
        }
        authService.changePassword(user.getId(), oldPassword, newPassword);
        return R.ok();
    }
}
