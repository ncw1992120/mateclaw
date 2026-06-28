package vip.mate.dataagent.auth.service;

import vip.mate.auth.model.LoginRequest;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;

/**
 * DataAgent 认证服务
 * <p>
 * 复用 mateclaw-server 的 {@code AuthService} 进行密码校验与 JWT 签发，
 * 在此基础上组装工作区列表，供前端登录后直接渲染工作区切换器。
 */
public interface DataAgentAuthService {

    /**
     * 用户登录
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（含 JWT 令牌与工作区列表）
     */
    DataAgentLoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户信息（含工作区列表）
     * <p>
     * 用于前端刷新页面后恢复用户状态。
     *
     * @param username 当前登录用户名
     * @return 用户信息（含工作区列表）
     */
    DataAgentLoginResponse getCurrentUserInfo(String username);
}
