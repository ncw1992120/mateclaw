package vip.mate.dataagent.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.auth.model.LoginRequest;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;
import vip.mate.dataagent.auth.service.DataAgentAuthService;

/**
 * DataAgent 认证接口
 * <p>
 * 复用 mateclaw-server 的用户体系（mate_user 表），提供登录、当前用户信息、修改密码接口。
 * 登录响应附带工作区列表，前端登录后可直接渲染工作区切换器。
 */
@Tag(name = "认证管理", description = "用户登录、当前用户信息、修改密码")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class DataAgentAuthController {

    private final DataAgentAuthService dataAgentAuthService;
    private final AuthService authService;

    @Operation(summary = "用户登录", description = "用户名密码登录，返回 JWT 令牌与可见工作区列表")
    @PostMapping("/login")
    public R<DataAgentLoginResponse> login(@RequestBody LoginRequest request) {
        return R.ok(dataAgentAuthService.login(request));
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
